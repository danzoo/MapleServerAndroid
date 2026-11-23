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

/*      Athena Pierce
	Bowman Job Advancement
	Victoria Road : Bowman Instructional School (100000201)
*/

status = -1;
actionx = {"1stJob": false, "2ndjob": false, "3thJobI": false, "3thJobC": false};
job = 310;

spawnPnpc = false;
spawnPnpcFee = 7000000;
jobType = 3;

function start() {
    importPackage(Packages.constants.game);
    if (parseInt(cm.getJobId() / 100) == jobType && cm.canSpawnPlayerNpc(GameConstants.getHallOfFameMapid(cm.getJob()))) {
        spawnPnpc = true;

        var sendStr = "一路走到今天，你攒下了力量、智慧和勇气，对吧？要不要让#r名人堂里的 NPC 现在就记下你角色当前的样子#k？喜欢吗？";
        if (spawnPnpcFee > 0) {
            sendStr += " 我可以帮你办，费用是 #b " + cm.numberWithCommas(spawnPnpcFee) + " 金币。#k";
        }

        cm.sendYesNo(sendStr);
    } else {
        if (cm.getJobId() == 0) {
            actionx["1stJob"] = true;
            cm.sendNext("决定要当#r弓箭手#k了吗？要达标才行哦……#b等级至少 10，敏捷至少 " + cm.getFirstJobStatRequirement(jobType) + "#k。我来看看。");   // thanks Vcoc for noticing a need to state and check requirements on first job adv starting message
        } else if (cm.getLevel() >= 30 && cm.getJobId() == 300) {
            actionx["2ndJob"] = true;
            if (cm.haveItem(4031012)) {
                cm.sendNext("哈哈……我就知道你能轻松通过那个测试。我承认，你是个出色的弓箭手。我会让你比现在强得多。不过在那之前……你得在给你的两条路里选一条。这决定不好做，但……有什么想问的，尽管问。");
            } else if (cm.haveItem(4031011)) {
                cm.sendOk("去见#b#p1072002##k吧。");
                cm.dispose();
            } else {
                cm.sendYesNo("嗯……上次见你之后你成长了不少。以前那个弱小的样子不见了，现在更像个弓箭手了。怎么样？不想变得更强吗？通过一个简单的测试，我就帮你做到。要试试吗？");
            }
        } else if (actionx["3thJobI"] || (cm.getPlayer().gotPartyQuestItem("JB3") && cm.getLevel() >= 70 && cm.getJobId() % 10 == 0 && parseInt(cm.getJobId() / 100) == 3 && !cm.getPlayer().gotPartyQuestItem("JBP"))) {
            actionx["3thJobI"] = true;
            cm.sendNext("你来了。前几天，神秘岛的#b#p2020010##k跟我提起过你。看来你有兴趣跃入弓箭手三转的精彩世界。要达成这个目标，我得先试试你的实力，看你配不配得上这次转职。金银岛的一片深林中央有个入口，通向一条密道。进去之后，你会面对我的分身。你的任务是打倒她，把#b#t4031059##k带回来。");
        } else if (cm.getPlayer().gotPartyQuestItem("JBP") && !cm.haveItem(4031059)) {
            cm.sendNext("请把#b#t4031059##k带给我。");
            cm.dispose();
        } else if (cm.haveItem(4031059) && cm.getPlayer().gotPartyQuestItem("JBP")) {
            actionx["3thJobC"] = true;
            cm.sendNext("干得漂亮。你打倒了我的分身，把#b#t4031059##k平安带了回来。从实力上说，你已经配得上三转了。接下来把这条项链交给神秘岛的#b#p2020011##k，接受测试的第二部分。祝你好运，你会用得上它的。");
        } else {
            cm.sendOk("你的选择很明智。");
            cm.dispose();
        }
    }
}

function action(mode, type, selection) {
    status++;
    if (mode == -1 && selection == -1) {
        cm.dispose();
        return;
    } else if (mode == 0 && type != 1) {
        status -= 2;
    }

    if (status == -1) {
        start();
        return;
    } else {
        if (spawnPnpc) {
            if (mode > 0) {
                if (cm.getMeso() < spawnPnpcFee) {
                    cm.sendOk("抱歉，你的金币不够买名人堂的位置。");
                    cm.dispose();
                    return;
                }

                importPackage(Packages.server.life);
                importPackage(Packages.constants.game);
                if (PlayerNPC.spawnPlayerNPC(GameConstants.getHallOfFameMapid(cm.getJob()), cm.getPlayer())) {
                    cm.sendOk("好了！希望你喜欢。");
                    cm.gainMeso(-spawnPnpcFee);
                } else {
                    cm.sendOk("抱歉，名人堂现在满了……");
                }
            }

            cm.dispose();
            return;
        } else {
            if (mode != 1 || status == 7 && type != 1 || (actionx["1stJob"] && status == 4) || (cm.haveItem(4031008) && status == 2) || (actionx["3thJobI"] && status == 1)) {
                if (mode == 0 && status == 2 && type == 1) {
                    cm.sendOk("你知道的，没有别的选择……");
                }
                if (!(mode == 0 && type != 1)) {
                    cm.dispose();
                    return;
                }
            }
        }
    }

    if (actionx["1stJob"]) {
        if (status == 0) {
            if (cm.getLevel() >= 10 && cm.canGetFirstJob(jobType)) {
                cm.sendNextPrev("这是重要且最终的选择。选了就不能回头。");
            } else {
                cm.sendOk("再练练，达到基本要求后，我再给你指#r弓箭手#k的路。");
                cm.dispose();
            }
        } else if (status == 1) {
            if (cm.canHold(1452051) && cm.canHold(2070000)) {
                if (cm.getJobId() == 0) {
                    cm.changeJobById(300);
                    cm.gainItem(1452051, 1);
                    cm.gainItem(2060000, 1000);
                    cm.resetStats();
                }
                cm.sendNext("好，从现在起你就是我们的一员了！一开始会过着漂泊的日子……但耐心点，很快就能出人头地。好，不多，但我分一点我的能力给你……哈啊啊！！！");
            } else {
                cm.sendNext("背包腾点空间再来找我。");
                cm.dispose();
            }
        } else if (status == 2) {
            cm.sendNextPrev("你现在强多了。而且每个背包栏都加了格子，准确说是整整一行。自己去看看吧。我刚给了你一点#bSP#k。打开屏幕左下角的#b技能#k菜单，里面有可以用 SP 学的技能。不过提醒一句：不能一次全加满。还有些技能要先学会别的技能才能学。");
        } else if (status == 3) {
            cm.sendNextPrev("再提醒一句。选定之后就不能反悔换别的路了。去吧，作为一名骄傲的弓箭手活下去。");
        } else {
            cm.dispose();
        }
    } else if (actionx["2ndJob"]) {
        if (status == 0) {
            if (cm.haveItem(4031012)) {
                cm.sendSimple("好，决定之后点下面的「我要选择职业」。#b\r\n#L0#给我讲讲猎人是怎么回事。\r\n#L1#给我讲讲弩弓手是怎么回事。\r\n#L3#我要选择职业！");
            } else {
                cm.sendNext("好决定。你看着挺强，但我得确认你是不是真的强到能通过测试。测试不难，你没问题的。来，先拿着我的信……别弄丢了！");
                if (!cm.isQuestStarted(100000)) {
                    cm.startQuest(100000);
                }
            }
        } else if (status == 1) {
            if (!cm.haveItem(4031012)) {
                if (cm.canHold(4031010)) {
                    if (!cm.haveItem(4031010)) {
                        cm.gainItem(4031010, 1);
                    }
                    cm.sendNextPrev("请把信交给射手村附近#b#m106010000##k一带的#b#p1072002##k。她替我担任教官的工作。把信给她，她会替我测试你。祝你好运。");
                    cm.dispose();
                } else {
                    cm.sendNext("请腾点背包空间。");
                    cm.dispose();
                }
            } else {
                if (selection < 3) {
                    if (selection == 0) {    //hunter
                        cm.sendNext("精通#r弓#k的弓箭手。\r\n\r\n#b猎人#k前期每分钟输出更高，攻击节奏快，但单发比弩弓手略弱。#b猎人#k拥有#r爆炸箭#k，威力稍弱但最多能晕住 6 个敌人。");
                    } else if (selection == 1) {    //crossbowman
                        cm.sendNext("精通#r弩#k的弓箭手。\r\n\r\n#b弩弓手#k的攻击力随等级提升比猎人更明显。#b弩弓手#k拥有#r穿透箭#k，威力更强、不追踪敌人但能穿墙。");
                    }

                    status -= 2;
                } else {
                    cm.sendSimple("那么……决定了吗？请选择二转的职业。#b\r\n#L0#猎人\r\n#L1#弩弓手");
                }
            }
        } else if (status == 2) {
            job += selection * 10;
            cm.sendYesNo("确定要二转成" + (job == 310 ? "#b猎人#k" : "#b弩弓手#k") + "吗？在这里选定之后，二转就不能换别的职业了，知道吧？");
        } else if (status == 3) {
            if (cm.haveItem(4031012)) {
                cm.gainItem(4031012, -1);
            }

            cm.sendNext("好，从现在起你就是" + (job == 310 ? "#b猎人#k" : "#b弩弓手#k") + "了。" + (job == 310 ? "#b猎人#k" : "#b弩弓手#k") + "是聪慧的一群，眼力惊人，能轻松一箭穿进怪物的心脏……请每天坚持修炼。我会帮你变得比现在更强。");
            if (cm.getJobId() != job) {
                cm.changeJobById(job);
            }
        } else if (status == 4) {
            cm.sendNextPrev("我刚给了你一本技能书，上面列着你作为" + (job == 310 ? "猎人" : "弩弓手") + "能学的技能。其他栏也加了一行格子。最大 HP 和 MP 也提升了。自己去看看吧。");
        } else if (status == 5) {
            cm.sendNextPrev("我还给了你一点#bSP#k。打开左下角的#b技能菜单#k，就能提升新学到的二转技能。不过提醒一句：不能一次全加满。有些技能要先学会别的技能才能学。记住这一点。");
        } else if (status == 6) {
            cm.sendNextPrev((job == 310 ? "猎人" : "弩弓手") + "需要强大。但记住，不能滥用力量去欺负弱者。请把强大的力量用在正道上，因为……用对力量比单纯变强难得多。等你更进一步之后再来找我。我等你。");
        }
    } else if (actionx["3thJobI"]) {
        if (status == 0) {
            if (cm.getPlayer().gotPartyQuestItem("JB3")) {
                cm.getPlayer().removePartyQuestItem("JB3");
                cm.getPlayer().setPartyQuestItemObtained("JBP");
            }
            cm.sendNextPrev("她是我的分身，所以前面会是一场硬仗。她会使出许多你从没见过的特殊攻击技能，你的任务就是一对一打赢她。密道里有时间限制，务必在限时内打倒她。祝你好运，希望你能把#b#t4031059##k带回来。");
        }
    } else if (actionx["3thJobC"]) {
        cm.getPlayer().removePartyQuestItem("JBP");
        cm.gainItem(4031059, -1);
        cm.gainItem(4031057, 1);
        cm.dispose();
    }
}
