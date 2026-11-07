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
	NPC Name: 		Mr. Goldstein
	Map(s): 		Victoria Road : Lith Harbour (104000000)
	Description:		Extends Buddy List
*/
var status = 0;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (status == 0 && mode == 0) {
            cm.sendNext("这样啊……我还以为你朋友很多呢。哈哈哈，开玩笑的！要是改了主意，随时回来谈生意。朋友交多了嘛……嘿嘿……");
            cm.dispose();
            return;
        } else if (status >= 1 && mode == 0) {
            cm.sendNext("这样啊……看来你带的钱没我想的那么多。现在身上没有 240,000 金币吧？总之改了主意就回来谈生意。当然，那得等你手头宽裕一些……嘿嘿……");
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 0) {
            cm.sendYesNo("希望今天能赚得跟昨天一样多……哟，你好！不想扩充好友列表吗？你看着就是朋友很多的人……怎么样？出点钱我就能帮你办。不过要记住，这个只作用在一个角色上，你账号里的其他角色不受影响。要扩充好友列表吗？");
        } else if (status == 1) {
            cm.sendYesNo("好眼力！其实不算贵。#b240,000 金币，我给你加 5 个好友栏位#k。而且我不零卖，买一次就一直有效。所以真觉得不够用，就干脆办了。怎么样？花 240,000 金币办这个？");
        } else if (status == 2) {
            var capacity = cm.getPlayer().getBuddylist().getCapacity();
            if (capacity >= 50 || cm.getMeso() < 240000) {
                cm.sendNext("喂……你确定带了#b240,000 金币#k吗？确定的话，看看好友列表是不是已经扩到上限了。就算肯付钱，好友最多也只能存#b50#k个。");
                cm.dispose();
            } else {
                var newcapacity = capacity + 5;
                cm.gainMeso(-240000);
                cm.getPlayer().setBuddyCapacity(newcapacity)
                cm.sendOk("好了！你的好友列表现在多了 5 个栏位，自己去看一眼。还嫌不够就再来找我。当然，那还是要钱的……那就再见……");
                cm.dispose();
            }
        }
    }
}