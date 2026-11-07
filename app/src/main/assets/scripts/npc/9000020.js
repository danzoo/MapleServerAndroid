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


var travelFrom = [777777777, 541000000];
var travelFee = [3000, 10000];

var travelMap = [800000000, 550000000];
var travelPlace = ["日本的蘑菇神社", "马来西亚的潮流区"];
var travelPlaceShort = ["蘑菇神社", "潮流都市"];
var travelPlaceCountry = ["日本", "马来西亚"];
var travelAgent = ["我", "#r#p9201135##k"];

var travelDescription = ["想感受日本的精髓，去被称为日本文化熔炉的神社再合适不过。蘑菇神社是从远古时期就供奉独一无二的蘑菇神的圣地。",
    "想在轻快的氛围里感受热带的热烈，马来西亚的居民会热情欢迎你。而且潮流都市本身就是当地的经济中心，那里随时都有得玩、有得逛。"];

var travelDescription2 = ["去看供奉蘑菇神的女巫祝吧，我还强烈推荐你尝尝章鱼烧、炒面这些日本街头小吃。那么，我们出发去#b蘑菇神社#k吧，那里可是传说中的圣地。",
    "到了那里，我强烈建议你去卡姆邦村走一趟。为什么？幻想主题公园鬼屋乐园你总该听说过吧？没有？那说白了就是当地最棒的游乐园，很值得去！那么，我们出发去#b马来西亚的潮流区#k。"];

var travelType;
var travelStatus;

function start() {
    travelStatus = getTravelingStatus(cm.getPlayer().getMapId());
    action(1, 0, 0);
}

function getTravelingStatus(mapid) {
    for (var i = 0; i < travelMap.length; i++) {
        if (mapid == travelMap[i]) {
            return i;
        }
    }

    return -1;
}

function getTravelType(mapid) {
    for (var i = 0; i < travelFrom.length; i++) {
        if (mapid == travelFrom[i]) {
            return i;
        }
    }

    return 0;
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        if (mode == 0 && status == 4) {
            status -= 2;
        } else {
            cm.dispose();
            return;
        }
    }

    if (travelStatus != -1) {
        if (status == 0) {
            cm.sendSimple("旅行怎么样？玩得开心吗？#b\r\n#L0#是的，旅行到此为止，能送我回#m" + cm.getPlayer().peekSavedLocation("WORLDTOUR") + "#k吗？\r\n#L1#不，我还想继续逛逛这里。");
        } else if (status == 1) {
            if (selection == 0) {
                cm.sendNext("好的。我送你回这次出门旅行之前待的地方。以后还想再出去旅行，随时来找我！");
            } else if (selection == 1) {
                cm.sendOk("好吧。改了主意再告诉我。");
                cm.dispose();
            }
        } else if (status == 2) {
            var map = cm.getPlayer().getSavedLocation("WORLDTOUR");
            if (map == -1) {
                map = 104000000;
            }

            cm.warp(map);
            cm.dispose();
        }
    } else {
        if (status == 0) {
            travelType = getTravelType(cm.getPlayer().getMapId());
            cm.sendNext("过腻了一成不变的日常，何不出去换换心情？沉浸在陌生的文化里、随时学到新东西，那种感觉最棒了。是时候出门旅行了。我们#b枫叶旅行社#k为你推荐#b世界旅行#k！担心旅费？不用怕！我们#b枫叶旅行社#k精心设计了这套方案，只要#b" + cm.numberWithCommas(travelFee[travelType]) + "金币#k！");
        } else if (status == 1) {
            cm.sendSimple("现在为你开放的旅行目的地是：#b" + travelPlace[travelType] + "#k。" + travelAgent[travelType] + "会在那里担任你的导游为你服务。放心，目的地以后会越来越多。那么，要去" + travelPlaceShort[travelType] + "吗？#b\r\n#L0#是的，送我去" + travelPlaceShort[travelType] + "（" + travelPlaceCountry[travelType] + "）");
        } else if (status == 2) {
            cm.sendNext("要去#b" + travelPlace[travelType] + "#k旅行吗？" + travelDescription[travelType]);
        } else if (status == 3) {
            if (cm.getMeso() < travelFee[travelType]) {
                cm.sendNext("金币不够，没法去旅行。");
                cm.dispose();
                return;
            }
            cm.sendNextPrev(travelDescription2[travelType]);
        } else if (status == 4) {
            cm.gainMeso(-travelFee[travelType]);
            cm.getPlayer().saveLocation("WORLDTOUR");
            cm.warp(travelMap[travelType], 0);
            cm.dispose();
        }
    }
}