/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc>
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package server.maps;

import android.graphics.Point;
import client.Character;
import client.Client;
import client.inventory.Item;
import tools.PacketCreator;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.SECONDS;

public class MapItem extends AbstractMapObject {
    protected Client ownerClient;
    protected Item item;
    protected MapObject dropper;
    protected int character_ownerid, party_ownerid, meso, questid = -1;
    protected byte type;
    protected boolean pickedUp = false, playerDrop, partyDrop;
    protected long dropTime;
    // When the toss the clients were told to animate is over. Until then the item is still in the air
    // on their screens and there is nothing lying at its position to pick up. Zero for a drop that was
    // never given one, which is a drop that appeared already at rest.
    private long landedTime;
    private final Lock itemLock = new ReentrantLock();

    public MapItem(Item item, Point position, MapObject dropper, Character owner, Client ownerClient, byte type, boolean playerDrop) {
        setPosition(position);
        this.item = item;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.party_ownerid = owner.getPartyId();
        this.partyDrop = this.party_ownerid != -1;
        this.ownerClient = owner.getClient();
        this.meso = 0;
        this.type = type;
        this.playerDrop = playerDrop;
    }

    public MapItem(Item item, Point position, MapObject dropper, Character owner, Client ownerClient, byte type, boolean playerDrop, int questid) {
        setPosition(position);
        this.item = item;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.party_ownerid = owner.getPartyId();
        this.partyDrop = this.party_ownerid != -1;
        this.ownerClient = owner.getClient();
        this.meso = 0;
        this.type = type;
        this.playerDrop = playerDrop;
        this.questid = questid;
    }

    public MapItem(int meso, Point position, MapObject dropper, Character owner, Client ownerClient, byte type, boolean playerDrop) {
        setPosition(position);
        this.item = null;
        this.dropper = dropper;
        this.character_ownerid = owner.getId();
        this.party_ownerid = owner.getPartyId();
        this.partyDrop = this.party_ownerid != -1;
        this.ownerClient = owner.getClient();
        this.meso = meso;
        this.type = type;
        this.playerDrop = playerDrop;
    }

    public final Item getItem() {
        return item;
    }

    public final int getQuest() {
        return questid;
    }

    public final int getItemId() {
        if (meso > 0) {
            return meso;
        }
        return item.getItemId();
    }

    public final MapObject getDropper() {
        return dropper;
    }

    public final int getOwnerId() {
        return character_ownerid;
    }

    public final int getPartyOwnerId() {
        return party_ownerid;
    }

    public final void setPartyOwnerId(int partyid) {
        party_ownerid = partyid;
    }

    public final int getClientsideOwnerId() {   // thanks nozphex (RedHat) for noting an issue with collecting party items
        if (this.party_ownerid == -1) {
            return this.character_ownerid;
        } else {
            return this.party_ownerid;
        }
    }

    public final boolean hasClientsideOwnership(Character player) {
        return this.character_ownerid == player.getId() || this.party_ownerid == player.getPartyId() || hasExpiredOwnershipTime();
    }

    public final boolean isFFADrop() {
        return type == 2 || type == 3 || hasExpiredOwnershipTime();
    }

    public final boolean hasExpiredOwnershipTime() {
        return System.currentTimeMillis() - dropTime >= SECONDS.toMillis(15);
    }

    public final boolean canBePickedBy(Character chr) {
        if (character_ownerid <= 0 || isFFADrop()) {
            return true;
        }

        if (party_ownerid == -1) {
            if (chr.getId() == character_ownerid) {
                return true;
            } else if (chr.isPartyMember(character_ownerid)) {
                party_ownerid = chr.getPartyId();
                return true;
            }
        } else {
            if (chr.getPartyId() == party_ownerid) {
                return true;
            } else if (chr.getId() == character_ownerid) {
                party_ownerid = chr.getPartyId();
                return true;
            }
        }

        return hasExpiredOwnershipTime();
    }

    public final Client getOwnerClient() {
        return (ownerClient.isLoggedIn() && !ownerClient.getPlayer().isAwayFromWorld()) ? ownerClient : null;
    }

    public final int getMeso() {
        return meso;
    }

    public final boolean isPlayerDrop() {
        return playerDrop;
    }

    public final boolean isPickedUp() {
        return pickedUp;
    }

    public void setPickedUp(final boolean pickedUp) {
        this.pickedUp = pickedUp;
    }

    public long getDropTime() {
        return dropTime;
    }

    public void setDropTime(long time) {
        this.dropTime = time;
    }

    // The arc is the client's own: DROP_TOSS_RISE / DROP_FLIGHT_SECONDS / DROP_MAX_FALL_SPEED in
    // MapleClientAndroid's Constants.h, walked by Gameplay/MapleMap/Drop.cpp. The client waits out its
    // own DROP_DELAY and the packet's trip on top of the delay passed here, so it lands no earlier
    // than this and an honest pickup is never the one refused.
    public void setLandedTime(short delay, Point from, Point to) {
        final double tossRise = 56.0;
        final double flightSeconds = 0.608;
        final double maxFallSpeed = 375.0;
        final double accel = 2.0 * tossRise / Math.pow(flightSeconds / 2.0, 2);
        final double capSeconds = maxFallSpeed / accel;
        final double capDistance = maxFallSpeed * maxFallSpeed / (2.0 * accel);

        // The client draws both ends four pixels above the position it was given, which cancels out
        // of every distance below, so the positions are used as they stand.
        final double apex = Math.min(from.y, to.y) - tossRise;
        final double climb = Math.sqrt(2.0 * (from.y - apex) / accel);
        final double fall = to.y - apex;
        final double descent = fall <= capDistance
                ? Math.sqrt(2.0 * fall / accel)
                : capSeconds + (fall - capDistance) / maxFallSpeed;

        landedTime = System.currentTimeMillis() + delay
                     + (long) Math.ceil((climb + descent) * 1000.0);
    }

    public boolean hasLanded() {
        return System.currentTimeMillis() >= landedTime;
    }

    public byte getDropType() {
        return type;
    }

    public void lockItem() {
        itemLock.lock();
    }

    public void unlockItem() {
        itemLock.unlock();
    }

    @Override
    public final MapObjectType getType() {
        return MapObjectType.ITEM;
    }

    @Override
    public void sendSpawnData(final Client client) {
        Character chr = client.getPlayer();

        if (chr.needQuestItem(questid, getItemId())) {
            this.lockItem();
            try {
                client.sendPacket(PacketCreator.dropItemFromMapObject(chr, this, null, getPosition(),
                        (byte) 2, (short) 0));
            } finally {
                this.unlockItem();
            }
        }
    }

    @Override
    public void sendDestroyData(final Client client) {
        client.sendPacket(PacketCreator.removeItemFromMap(getObjectId(), 1, 0));
    }
}