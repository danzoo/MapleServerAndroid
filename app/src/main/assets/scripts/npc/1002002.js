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
	NPC Name: 		Pison
	Map(s): 		Victoria Road : Lith Harbor (104000000)
	Description: 		Florina Beach Tour Guide
 */
var status = 0;

function start() {
    cm.sendSimple("听说过明珠港附近那片海景特别漂亮的#b黄金海滩#k吗？现在去要么付#b1500金币#k，要么如果你有#b黄金海滩VIP通行证#k，就免费送你过去。\r\n\r\n#L0##b我付 1500 金币。#l\r\n#L1#我有黄金海滩 VIP 通行证。#l\r\n#L2#黄金海滩 VIP 通行证是什么#k？#l");
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        if ((mode == 0 && type == 1) || mode == -1 || (mode == 0 && status == 1)) {
            if (type == 1) {
                cm.sendNext("你在这里一定还有事要办。跑了这么远又打了半天怪也累了吧，先去歇歇，改了主意再来找我。");
            }
            cm.dispose();
            return;
        } else {
            status -= 2;
        }
    }
    if (selection == 0) {
        status++;
    }
    if (status == 1) {
        if (selection == 1) {
            cm.sendYesNo("你有#b黄金海滩VIP通行证#k？有它随时都能去黄金海滩。那好，不过要提醒你，那边也是有怪物的。那么，现在就送你去黄金海滩？");
        } else if (selection == 2) {
            cm.sendNext("你一定很好奇#b黄金海滩VIP通行证#k是什么。哈哈，可以理解。只要身上带着这张通行证，去黄金海滩就不要钱。它稀罕到连我们都要花钱买，可惜我那张几个星期前过暑假的时候弄丢了。");
        }
    } else if (status == 2) {
        if (type != 1 && selection != 0) {
            cm.sendNextPrev("结果我就这样空着手回来了，没有它真难受。希望有人捡到之后收好了。总之这是我的故事，说不定哪天你捡到了还能派上用场。有什么想问的尽管问。");
            cm.dispose();
        } else {
            if (cm.getMeso() < 1500 && selection == 0) {
                cm.sendNext("你的金币好像不太够。攒钱的法子多得很，比如……把装备卖掉……打怪……做任务……你明白我的意思吧。");
            } else if (!cm.haveItem(4031134) && selection != 0) {
                cm.sendNext("嗯……你的#b黄金海滩VIP通行证#k到底在哪里？真的带了？再确认一遍吧。");
            } else {
                if (selection == 0) {
                    cm.gainMeso(-1500);
                }
                cm.getPlayer().saveLocation("FLORINA");
                cm.warp(110000000, "st00");
            }
            cm.dispose();
        }
    }
}