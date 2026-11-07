var status = 0;
var maps = [100000000, 102000000, 101000000, 103000000, 120000000];
var cost = [1000, 1000, 800, 1000, 800];
var selectedMap = -1;
var mesos;

function start() {
    if (cm.hasItem(4032313, 1)) {
        cm.sendNext("看得出你有一张去射手村的票。稍等一下，我马上送你过去！");
    } else {
        cm.sendNext("你好，我开的是普通中巴。想安全又快地在各个城镇之间跑，就坐我们的车。价格不贵，随到随走。");
    }
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (status == 1 && mode == 0) {
            cm.dispose();
            return;
        } else if (status >= 2 && mode == 0) {
            cm.sendNext("这个镇上也有的逛。要去别的城镇再回来找我。");
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 1) {
            if (cm.hasItem(4032313, 1)) {
                cm.gainItem(4032313, -1);
                cm.warp(maps[0], 0);
                cm.dispose();
                return;
            }

            var selStr = "";
            if (cm.getJobId() == 0) {
                selStr += "新手有九折优惠。";
            }
            selStr += "选个目的地吧，车费各地不一样。#b";
            for (var i = 0; i < maps.length; i++) {
                selStr += "\r\n#L" + i + "##m" + maps[i] + "#（" + (cm.getJobId() == 0 ? cost[i] / 10 : cost[i]) + "金币）#l";
            }
            cm.sendSimple(selStr);
        } else if (status == 2) {
            cm.sendYesNo("你在这里没别的事了，对吧？真的要去#b#m" + maps[selection] + "##k吗？这需要#b" + (cm.getJobId() == 0 ? cost[selection] / 10 : cost[selection]) + "金币#k。");
            selectedMap = selection;
        } else if (status == 3) {
            if (cm.getJobId() == 0) {
                mesos = cost[selectedMap] / 10;
            } else {
                mesos = cost[selectedMap];
            }

            if (cm.getMeso() < mesos) {
                cm.sendNext("金币不够。话说得不好听，但没带够钱是真坐不了这趟车。");
                cm.dispose();
                return;
            }

            cm.gainMeso(-mesos);
            cm.warp(maps[selectedMap], 0);
            cm.dispose();
        }
    }
}