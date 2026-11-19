/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.server.channel.handlers;

import android.graphics.Point;
import client.Character;
import client.Client;
import client.inventory.Pet;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.maps.MapItem;
import server.maps.MapObject;
import tools.PacketCreator;

import java.util.Set;

/**
 * @author TheRamon
 * @author Ronan
 */
public final class PetLootHandler extends AbstractPacketHandler {
    private static void rejectPickup(Client c, Character chr, int oid, int retry, boolean reportRejection) {
        if (reportRejection) {
            c.sendPacket(PacketCreator.pickupRejected(chr.getMapId(), oid, retry));
        }
        c.sendPacket(PacketCreator.enableActions());
    }

    @Override
    public final void handlePacket(InPacket p, Client c) {
        int petId = p.readInt();
        p.skip(4); // High half of the pet's wire ID; legacy skipped 13 bytes through position.
        p.readInt(); // Timestamp
        boolean reportRejection = p.readByte() == 0x7f;
        p.readPos(); // Client pet position is not authoritative.
        int oid = p.readInt();

        Character chr = c.getPlayer();
        int petIndex = chr.getPetIndex(petId);
        Pet pet = chr.getPet(petIndex);
        if (pet == null || !pet.isSummoned()) {
            rejectPickup(c, chr, oid, 5000, reportRejection);
            return;
        }

        MapObject ob = chr.getMap().getMapObject(oid);
        if (!(ob instanceof MapItem)) {
            rejectPickup(c, chr, oid, 500, reportRejection);
            return;
        }
        MapItem mapitem = (MapItem) ob;
        if (mapitem.getMeso() > 0) {
            if (!chr.isEquippedMesoMagnet()) {
                rejectPickup(c, chr, oid, 5000, reportRejection);
                return;
            }

            if (chr.isEquippedPetItemIgnore()) {
                final Set<Integer> petIgnore = chr.getExcludedItems();
                if (petIgnore.contains(Integer.MAX_VALUE)) {
                    rejectPickup(c, chr, oid, 5000, reportRejection);
                    return;
                }
            }
        } else {
            if (!chr.isEquippedItemPouch()) {
                rejectPickup(c, chr, oid, 5000, reportRejection);
                return;
            }

            if (chr.isEquippedPetItemIgnore()) {
                final Set<Integer> petIgnore = chr.getExcludedItems();
                if (petIgnore.contains(mapitem.getItem().getItemId())) {
                    rejectPickup(c, chr, oid, 5000, reportRejection);
                    return;
                }
            }
        }

        // Match the loose character pickup safety bound, not client icon-edge geometry.
        Point petPos = pet.getPos();
        Point obPos = ob.getPosition();
        if (Math.abs((long) petPos.x - obPos.x) > 800 || Math.abs((long) petPos.y - obPos.y) > 600) {
            rejectPickup(c, chr, oid, 500, reportRejection);
            return;
        }

        chr.pickupItem(ob, petIndex, reportRejection);
    }
}
