/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

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

package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import net.server.Server;
import tools.PacketCreator;

public class BannerCommand extends Command {
    {
        setDescription("Show the banner across the top of everyone's screen for a number of seconds.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();

        if (params.length < 2) {
            player.yellowMessage("Syntax: !banner <seconds> <message>");
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            player.yellowMessage("Syntax: !banner <seconds> <message>");
            return;
        }

        if (seconds < 1 || seconds > 3600) {
            player.yellowMessage("The banner can be held up for between 1 and 3600 seconds.");
            return;
        }

        // The parameters arrive split into words and lowercased, so the message itself is taken back
        // off the command as typed and with the seconds cut off the front of it.
        String message = player.getLastCommandMessage().substring(params[0].length()).trim();

        if (message.isEmpty()) {
            player.yellowMessage("Syntax: !banner <seconds> <message>");
            return;
        }

        Server.getInstance().broadcastMessage(c.getWorld(), PacketCreator.serverMessage(message, seconds));
    }
}
