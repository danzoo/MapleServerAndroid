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
/* Author: Xterminator
	NPC Name: 		Shanks
	Map(s): 		Maple Road : Southperry (2000000)
	Description: 		Brings you to Orbis (104000000)
*/
function start() {
    cm.sendYesNo("要搭这班船去金银岛的明珠港吗？这趟我不收钱，不过船一开走，短期内可就没有回来的路了。");
}

function action(mode, type, selection) {
    if (mode != 1) {
        if (mode == 0) {
            cm.sendOk("嗯……看来你在这儿还有没做完的事？");
        }
        cm.dispose();
        return;
    }
    // The ship lands on the west dock of Orbis; portal 0 is that map's first spawn point.
    cm.warp(104000000, 0);
    cm.dispose();
}