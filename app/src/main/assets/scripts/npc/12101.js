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
	NPC Name: 		Rain
	Map(s): 		Maple Road : Amherst (1010000)
	Description: 		Talks about Amherst
*/
var status = -1;

function start() {
    cm.sendNext("这里是从彩虹岛往东北去的#b彩虹村#k。彩虹岛是新手待的地方，你知道吧？这里只有弱小的怪物，我就放心了。");
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        if (mode == 0 && status == 2) {
            status -= 2;
            start();
        } else if (mode == 0) {
            status -= 2;
        } else {
            cm.dispose();
        }
    } else {
        if (status == 1) {
            cm.sendNextPrev("想变强的话就去有港口的#b南港#k。坐上那艘大船，去一个叫#b金银岛#k的地方。跟这座小岛比，那边大得没法比。");
        } else if (status == 2) {
            cm.sendPrev("到了金银岛就可以转职。好像有个叫#b勇士部落#k……？听说那是战士住的、光秃秃的荒凉地方。高原……那会是个什么样的地方呢？");
        } else if (status == 3) {
            cm.dispose();
        }
    }
}