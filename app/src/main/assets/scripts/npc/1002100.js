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
// Jane the Alchemist
var status = -1;
var amount = -1;
var items = [[2000002, 310], [2022003, 1060], [2022000, 1600], [2001000, 3120]];
var item;

function start() {
    if (cm.isQuestCompleted(2013)) {
        cm.sendNext("是你啊……多亏你我才能做完那么多事。最近我一直在做各种道具，需要什么都可以跟我说。");
    } else {
        if (cm.isQuestCompleted(2010)) {
            cm.sendNext("你好像还不够强，买不了我的药水……");
        } else {
            cm.sendOk("我的梦想是像你一样到处旅行。可我爸爸不让我去，他觉得太危险了。不过只要我能证明我不是他心里那么弱，他也许会答应……");
        }
        cm.dispose();
    }
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        if (mode == 0 && type == 1) {
            cm.sendNext("你之前帮我找的材料我还留着不少。道具都在里面，慢慢挑。");
        }
        cm.dispose();
        return;
    }
    if (status == 0) {
        var selStr = "想买哪个道具？#b";
        for (var i = 0; i < items.length; i++) {
            selStr += "\r\n#L" + i + "##i" + items[i][0] + "#（售价：" + items[i][1] + "金币）#l";
        }
        cm.sendSimple(selStr);
    } else if (status == 1) {
        item = items[selection];
var recHpMp = ["300 点 HP。", "1000 点 HP。", "800 点 MP", "1000 点 HP 和 MP。"];
        cm.sendGetNumber("你想买#b#t" + item[0] + "##k吗？#t" + item[0] + "# 可以帮你恢复 " + recHpMp[selection] + " 要买多少个？", 1, 1, 100);
    } else if (status == 2) {
        cm.sendYesNo("要买#r" + selection + "#k 个#b#t" + item[0] + "##k吗？#t" + item[0] + " 每个售价 " + item[1] + " 金币，一共是#r" + (item[1] * selection) + "#k金币。");
        amount = selection;
    } else if (status == 3) {
        if (cm.getMeso() < item[1] * amount) {
            cm.sendNext("是不是金币不够？看看其他道具栏有没有空位，身上有没有带够#r" + (item[1] * selectedItem) + "#k金币。");
        } else {
            if (cm.canHold(item[0])) {
                cm.gainMeso(-item[1] * amount);
                cm.gainItem(item[0], amount);
                cm.sendNext("谢谢光临。这里的东西都能做，需要就再来。");
            } else {
                cm.sendNext("看看其他道具栏有没有空位。");
            }
        }
        cm.dispose();
    }
}