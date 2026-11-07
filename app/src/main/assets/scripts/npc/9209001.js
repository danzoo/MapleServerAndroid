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

status = -1;
var sel, sel2;

function start() {
    cm.sendOk("你好，冒险岛周末集市目前还没开放。");
    cm.dispose();
    return;

    cm.sendSimple("你好，今天是冒险岛周末集市开放的日子。#b\r\n#L0#送我去周末集市地图\r\n#L1#听听周末集市是怎么回事");
}

function action(mode, type, selection) {
    status++;
    if (status == 6 && mode == 1) {
        sel2 = undefined;
        status = 0;
    }
    if (mode != 1) {
        if (mode == 0 && type == 0) {
            status -= 2;
        } else {
            cm.dispose();
            return;
        }
    }
    if (status == 0) {
        if (sel == undefined) {
            sel = selection;
        }
        if (selection == 0) {
            cm.sendNext("好，这就送你去周末集市地图。");
        } else {
            cm.sendSimple("关于周末集市你想了解什么？#b\r\n#L0#周末集市在哪里办？\r\n#L1#周末集市能做什么？\r\n#L2#我没有别的问题了。");
        }
    } else if (status == 1) {
        if (sel == 0) {
            cm.getPlayer().saveLocation("EVENT");
            cm.warp(680100000 + parseInt(Math.random() * 3));
            cm.dispose();
        } else if (selection == 0) {
            cm.sendNext("冒险岛周末集市只在星期日开放。在射手村、新叶城、神木村、废弃都市、玩具城任何一个城镇找到我就能进场！我几乎无处不在！");
            status -= 2;
        } else if (selection == 1) {
            cm.sendSimple("在周末集市能买到别处少见的稀罕货。#b\r\n#L0#买特价商品\r\n#L1#帮养鸡场场主一把");
        } else {
            cm.sendNext("看来你没有别的问题了。记得我们，好奇什么随时来问。");
            cm.dispose();
        }
    } else if (status == 2) {
        if (sel2 == undefined) {
            sel2 = selection;
        }
        if (sel2 == 0) {
            cm.sendNext("周末集市上有很多道具。价格会变，所以便宜的时候最好下手！");
        } else {
            cm.sendNext("除了商贩，周末集市里还有养鸡场场主那个懒洋洋的女儿。帮帮#b#p9209001##k，把她孵的蛋一路孵成小鸡！");
        }
    } else if (status == 3) {
        if (sel2 == 0) {
            cm.sendNextPrev("在这里买的东西可以卖给中介商阿布杜拉。超过一个星期的他不要，所以务必在星期六之前转卖出去！");
        } else {
            cm.sendNextPrev("她不能把蛋随便交给谁，所以要先收押金。交押金给她，然后把蛋照顾好。");
        }
    } else if (status == 4) {
        if (sel2 == 0) {
            cm.sendNextPrev("阿布杜拉的回收价也会调，所以在最赚钱的时候卖才划算。价格差不多每小时都在变，记得常来看。");
        } else {
            cm.sendNextPrev("真的把蛋孵成小鸡再带回给#p9209001#，她会好好谢你的。她是懒，可不是不懂感恩。");
        }
    } else if (status == 5) {
        if (sel2 == 0) {
            cm.sendNextPrev("在周末集市低价买进，等价高了再卖给中介商，试试你的生意头脑吧！");
        } else {
            cm.sendNextPrev("点一下蛋就能看到它的成长。蛋要勤照看，你赚的经验值和蛋是一起长的。");
        }
    }
}