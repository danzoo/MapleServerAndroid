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
/* Credits to: kevintjuh93
    NPC Name:         Jean
    Map(s):         Victoria Road : Lith Harbour (104000000)
    Description:         Event Assistant
*/
var status = 0;

function start() {
    cm.sendNext("嗨，我是#b#p9000001##k。我在等我弟弟#b保罗#k。他应该已经到了……");
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (status >= 2 && mode == 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }
        if (status == 1) {
            cm.sendNextPrev("嗯……怎么办？活动马上就要开始了……好多人已经去参加了，我们得快点……");
        } else if (status == 2) {
            cm.sendSimple("嗨……要不要跟我一起去？我弟弟应该是跟别人一起回来的。\r\n#L0##b1. 这是什么活动？#k#l\r\n#L1##b2. 给我讲讲活动怎么玩。#k#l\r\n#L2##b3. 好了，我们走吧！#k#l");
        } else if (status == 3) {
            if (selection == 0) {
                cm.sendNext("整个这个月，冒险岛都在庆祝三周年！GM 们会在活动期间随时开出 GM 特别活动，都盯紧了，至少参加一个，奖励很丰厚！");
                cm.dispose();
            } else if (selection == 1) {
                cm.sendSimple("这次活动有好几个玩法。进场之前先弄懂怎么玩会轻松很多。想先了解哪一个？#b\r\n#L0#奥力奥力#l\r\n#L1#冒险岛体能测试#l\r\n#L2#打雪球#l\r\n#L3#采椰子#l\r\n#L4#OX 问答#l\r\n#L5#寻宝#l#k");
            } else if (selection == 2) {
                if (cm.getEvent() != null && cm.getEvent().getLimit() > 0) {
                    cm.getPlayer().saveLocation("EVENT");
                    if (cm.getEvent().getMapId() == 109080000 || cm.getEvent().getMapId() == 109060001) {
                        cm.divideTeams();
                    }

                    cm.getEvent().minusLimit();
                    cm.warp(cm.getEvent().getMapId(), 0);
                    cm.dispose();
                } else {
                    cm.sendNext("要么活动还没开始，要么你已经有了#b神秘卷轴#k，要么你 24 小时内已经参加过这个活动。请稍后再试！");
                    cm.dispose();
                }
            }
        } else if (status == 4) {
            if (selection == 0) {
                cm.sendNext("#b【奥力奥力】#k是爬梯子抢到最上面的比赛。一路爬上去，在那么多传送门里挑对的那个进入下一层。\r\n\r\n一共三层，限时#b6 分钟#k。打【奥力奥力】的时候，#b不能跳、不能瞬间移动、不能加速，也不能靠药水提属性#k。还有故意骗人的传送门，会把你去到奇怪的地方，要留神。");
                cm.dispose();
            } else if (selection == 1) {
                cm.sendNext("#b【冒险岛体能测试】是一场跨障碍赛#k，赛道很像忍耐森林。跨过各种障碍，在限时内跑到终点就算赢。\r\n\r\n一共四层，限时#b15 分钟#k。打体能测试的时候不能用瞬间移动和加速。");
                cm.dispose();
            } else if (selection == 2) {
                cm.sendNext("#b【打雪球】#k分两队， Maple 队和 Story 队，比的是#b在限时内谁把雪球推得更远、滚得更大#k。时间到了还分不出胜负，就把雪球推得更远的那队判赢。\r\n\r\n要滚雪球就得按#b攻击键#k打它。这里远程攻击和技能攻击都不管用，#b只有近身攻击有效#k。\r\n\r\n人物碰到雪球会被送回起点。开起点前面的雪人，可以阻止对方往前推雪。这时候战术安排就很关键，全队要商量是打雪球还是打雪人。");
                cm.dispose();
            } else if (selection == 3) {
                cm.sendNext("#b【采椰子】#k分两队，Maple 队和 Story 队，比的是#b哪队采到的椰子最多#k。限时#b5 分钟#k。打平就再加 2 分钟决胜，如果比分还是一样，就算平局。\r\n\r\n这里远程攻击和技能攻击都不管用，#b只有近身攻击有效#k。没有近身武器可以在活动地图里的 NPC 那里买。不管角色等级、武器还是技能，打出去的伤害都一样。\r\n\r\n地图里有障碍和陷阱要小心。比赛中死了就被淘汰。椰子掉下来之前最后打它的那个人得分。只有落到地上的椰子才算，还挂在树上的、以及被炸掉椰子#b都不算#k。地图下面某个椰壳里还藏着一个传送门，好好利用！");
                cm.dispose();
            } else if (selection == 4) {
                cm.sendNext("#b【OX 问答】#k就是用 X 和 O 比谁更懂冒险岛的比赛。进场以后打开#b小地图#k看 X 和 O 各在哪边。一共出#r10 道题#k，全答对的人赢。\r\n\r\n题目一出，就顺着梯子爬到你可能认为正确的那一边，X 或 O。不在限时内做出选择，或者一直挂在梯子上不下来，就会被淘汰。请等到屏幕上的【回答正确】消失之后再动。为了防止作弊，OX 问答期间所有聊天功能都会关掉。");
                cm.dispose();
            } else if (selection == 5) {
                cm.sendNext("#b【寻宝】#k是要你在#r10 分钟#k里把藏在地图各处的#b藏宝图卷轴#k找出来。地图上藏着许多神秘的宝箱，打碎之后会掉出一堆道具，你要从里面把藏宝图卷轴挑出来。\r\n宝箱用#b普通攻击#k就能打碎。拿到藏宝图卷轴后，可以找负责交换的 NPC 换成神秘卷轴。交换的 NPC 就在寻宝地图里，也可以找#b明珠港的船员#k换。\r\n\r\n这个图同样有隐藏传送门和隐藏传送点。在特定的位置按#b上方向键#k就能被传到别的地方。多跳跳看，也可能发现隐藏的楼梯或绳子。还有一个宝箱会把你送到隐蔽的地方，也有只能靠隐藏传送门才找得到的宝箱，多找找。\r\n\r\n寻宝期间所有攻击技能都会#r被禁用#k，所以请用普通攻击砸宝箱。");
                cm.dispose();
            }
        }
    }
}  