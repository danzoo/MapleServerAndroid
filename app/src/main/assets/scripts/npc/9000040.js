/*
    This file is part of the HeavenMS MapleStory Server
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
/* Dalair
	Medal NPC.

        NPC Equipment Merger:
        * @author Ronan Lana
 */

var status;
var mergeFee = 50000;
var name;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            importPackage(Packages.config);
            if (!YamlConfig.config.server.USE_ENABLE_CUSTOM_NPC_SCRIPT) {
                cm.sendOk("勋章排行系统目前还没开放……");
                cm.dispose();
                return;
            }

            var levelLimit = !cm.getPlayer().isCygnus() ? 160 : 110;
            var selStr = "勋章排行系统目前还没开放……所以我改提供#b装备合成#k服务！";

            importPackage(Packages.client.processor.action);
            if (!YamlConfig.config.server.USE_STARTER_MERGE && (cm.getPlayer().getLevel() < levelLimit || MakerProcessor.getMakerSkillLevel(cm.getPlayer()) < 3)) {
                selStr += "不过要用这个服务，需要#r合成等级 3#k、至少#r110 级#k（骑士团）或#r160 级#k（其他职业），还要带上#r" + cm.numberWithCommas(mergeFee) + "金币#k。";
                cm.sendOk(selStr);
                cm.dispose();
            } else if (cm.getMeso() < mergeFee) {
                selStr += "抱歉，这项服务的手续费是#r" + cm.numberWithCommas(mergeFee) + "金币#k，看来你现在带得不够……下次再来吧。";
                cm.sendOk(selStr);
                cm.dispose();
            } else {
                selStr += "只要 #r" + cm.numberWithCommas(mergeFee) + "#k 金币，就能把道具栏里不用的装备合成进身上穿的装备，按被合成道具的属性给装备加上能力值！";
                cm.sendNext(selStr);
            }
        } else if (status == 1) {
            selStr = "#r注意#b：请把要拿去合成的装备摆在所选装备的#r后面#b的格子里。#k选中装备#b下面#k的道具会被一并合成掉。\r\n\r\n要注意，吃过合成加成的装备从此会变成#r无法交易#k，已经有合成加成的装备#r不能拿来当材料#k。\r\n\r\n";
            cm.sendGetText(selStr);
        } else if (status == 2) {
            name = cm.getText();

            if (cm.getPlayer().mergeAllItemsFromName(name)) {
                cm.gainMeso(-mergeFee);
                cm.sendOk("合成完成！谢谢使用，好好享受新装备的属性吧。");
            } else {
                cm.sendOk("#b装备#k栏里没有名叫‘" + name + "’！");
            }

            cm.dispose();
        }
    }
}
