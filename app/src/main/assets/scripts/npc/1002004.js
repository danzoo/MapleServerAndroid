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
/**
 -- Odin JavaScript --------------------------------------------------------------------------------
 VIP Cab - Victoria Road : Lith Harbor (104000000)
 -- By ---------------------------------------------------------------------------------------------
 Xterminator
 -- Version Info -----------------------------------------------------------------------------------
 1.0 - First Version by Xterminator
 ---------------------------------------------------------------------------------------------------
 **/

var status = 0;
var cost = 10000;

function start() {
    cm.sendNext("你好！这趟车只服务贵宾客人。不像普通的中巴只送几个镇子，我们提供配得上贵宾的服务。价格是贵了点，不过……只要 10,000 金币，就安全送你到\r\n#b蚂蚁洞#k。");
}

function action(mode, type, selection) {
    status++;
    if (mode == -1) {
        cm.dispose();
        return;
    } else if (mode == 0) {
        cm.sendOk("这个镇子也有不少可逛的。什么时候想去蚂蚁洞公园，就来找我。");
        cm.dispose();
        return;
    }
    if (status == 1) {
        cm.sendYesNo(cm.getJobId() == 0 ? "新手有九折优惠。蚂蚁洞在金银岛正中央那条地下城的最深处，就是 24 小时便利店那里。花#b1,000 金币#k送你去，好吗？" : "不是新手就按原价。蚂蚁洞在金银岛正中央那条地下城的最深处，就是 24 小时便利店那里。花#b10,000 金币#k送你去，好吗？");
        cost /= ((cm.getJobId() == 0) ? 10 : 1);
    } else if (status == 2) {
        if (cm.getMeso() < cost) {
            cm.sendNext("你的金币好像不够。没带够的话，抱歉了，这趟车坐不了。")
        } else {
            cm.gainMeso(-cost);
            cm.warp(105070001);
        }
        cm.dispose();
    }
}
