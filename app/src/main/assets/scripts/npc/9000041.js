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
/* NPC: Donation Box (9000041)
	Victoria Road : Henesys
	
	NPC Bazaar:
        * @author Ronan Lana
*/

var options = ["装备", "消耗", "布置", "其他"];
var name;
var status;
var selectedType = 0;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        cm.dispose();
        return;
    }

    if (status == 0) {
        importPackage(Packages.config);
        if (!YamlConfig.config.server.USE_ENABLE_CUSTOM_NPC_SCRIPT) {
            cm.sendOk("勋章排行系统目前还没开放……");
            cm.dispose();
            return;
        }

        var selStr = "你好，我是#b回收商人#k！道具栏里不用的都可以卖给我。#r注意#b：请把要卖的道具摆在所选道具的#r后面#b的格子里。#k选中道具#b下面#k的道具会被一并卖掉。";
        for (var i = 0; i < options.length; i++) {
            selStr += "\r\n#L" + i + "# " + options[i] + "#l";
        }
        cm.sendSimple(selStr);
    } else if (status == 1) {
        selectedType = selection;
        cm.sendGetText("从你#r" + options[selectedType] + "#k栏的第几个道具开始交易？");
    } else if (status == 2) {
        name = cm.getText();
        var res = cm.getPlayer().sellAllItemsFromName(selectedType + 1, name);

        if (res > -1) {
            cm.sendOk("交易完成！这次你收到#r" + cm.numberWithCommas(res) + "金币#k。");
        } else {
            cm.sendOk("你的道具栏里没有名叫‘" + name + "’的#b" + options[selectedType] + "#k！");
        }

        cm.dispose();
    }
}