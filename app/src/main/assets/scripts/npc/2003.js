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
	NPC Name: 		Robin
	Map(s): 		Maple Road : Snail Hunting Ground I (40000)
	Description: 		Beginner Helper
*/
var status;
var sel;

function start() {
    status = -1;
    sel = -1;
    cm.sendSimple("那么……关于冒险，有什么想问的尽管问我！！\r\n#L0##b怎么移动？#l\r\n#L1#怎么打怪？#l\r\n#L2#怎么捡东西？#l\r\n#L3#死了会怎么样？#l\r\n#L4#什么时候可以转职？#l\r\n#L5#给我讲讲这座岛！#l\r\n#L6#要成为战士该怎么做？#l\r\n#L7#要成为弓箭手该怎么做？#l\r\n#L8#要成为魔法师该怎么做？#l\r\n#L9#要成为飞侠该怎么做？#l\r\n#L10#怎么提升能力值？#l\r\n#L11#刚捡到的东西在哪里看？#l\r\n#L12#怎么穿上装备？#l\r\n#L13#身上穿了什么怎么看？#l\r\n#L14#什么是技能？#l\r\n#L15#怎么去金银岛？#l\r\n#L16#什么是金币？#l#k");
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        if (mode == 0 && type != 4) {
            status -= 2;
        } else {
            cm.dispose();
            return;
        }
    }
    if (status == 0) {
        if (sel == -1) {
            sel = selection;
        }
        if (sel == 0) {
            cm.sendNext("移动是这样的。用#b左、右方向键#k在平地和斜坡上走动，按#b跳跃键#k起跳，方向键加跳跃还能斜着跳。有些鞋子可以提升移动速度和跳跃能力。");
        } else if (sel == 1) {
            cm.sendNext("打怪是这样的。每只怪物都有自己的血量，用武器或者魔法攻击把它们清空就打倒了。当然，怪越强就越难缠。");
        } else if (sel == 2) {
            cm.sendNext("捡东西是这样的。打倒怪物后东西会掉在地上，走到它前面按#b拾取键#k就能拿起来。");
        } else if (sel == 3) {
            cm.sendNext("想知道死了会怎么样？血量降到 0 你就会变成幽灵，倒下的地方会立起一块墓碑，那时候动不了，不过还能说话。");
        } else if (sel == 4) {
            cm.sendNext("什么时候可以转职？哈哈，别急，朋友。每个职业都有自己的要求，一般等级 8 到 10 级就够了，好好练吧。");
        } else if (sel == 5) {
            cm.sendNext("想了解这座岛？它叫彩虹岛，是浮在空中的。飘在天上已经很久了，所以没有什么凶恶的怪物。非常安宁，最适合新手！");
        } else if (sel == 6) {
            cm.sendNext("想当#b战士#k？嗯，那我建议你去金银岛。去战士的城镇#r勇士部落#k，找#b武术教练#k。他会教你怎么成为一名真正的战士。哦，还有一件非常重要的事：要转职战士至少得 10 级！！");
        } else if (sel == 7) {
            cm.sendNext("想当#b弓箭手#k？那要去金银岛转职。去弓箭手的城镇#r射手村#k，找漂亮的#b赫丽娜#k聊聊，她会教你弓箭手的门道。哦，还有一件非常重要的事：要转职弓箭手至少得 10 级！！");
        } else if (sel == 8) {
            cm.sendNext("想当#b魔法师#k？那要去金银岛转职。去魔法师的城镇#r魔法密林#k，最上面就是魔法图书馆。在里面你会见到所有魔法师的首领#b汉斯#k，她会教你怎么成为一名魔法师。");
        } else if (sel == 9) {
            cm.sendNext("想当#b飞侠#k？那要去金银岛转职。去飞侠的城镇#r废弃都市#k，在城里见不得光的那一角就是飞侠的据点。在那里你会见到#b达克鲁#k，他会教你怎么成为一名飞侠。哦，还有一件非常重要的事：要转职飞侠至少得 10 级！！");
        } else if (sel == 10) {
            cm.sendNext("想知道怎么提升能力值？打开#b能力值窗口#k看看。每次升级都会给你 5 点能力点，想加哪项就加哪项，就这么简单。");
        } else if (sel == 11) {
            cm.sendNext("想知道捡到的东西在哪里看？打倒怪物会掉东西，按#b拾取键#k捡起来，就会放进#b道具窗口#k，打开道具窗口就能看到。");
        } else if (sel == 12) {
            cm.sendNext("想知道怎么穿装备？打开#b道具窗口#k，点一下装备栏里的格子，再点要穿的道具就穿上了。穿不上的话，多半是等级或者能力值还不够。在#b装备窗口#k里点已穿的装备就能把它脱下来。");
        } else if (sel == 13) {
            cm.sendNext("想看身上穿了什么？打开#b装备窗口#k，现在穿着的都在里面。点已穿的装备就能脱下来，脱下来的会回到道具窗口。");
        } else if (sel == 14) {
            cm.sendNext("转职之后获得的特殊能力就是技能，每个职业都有自己的技能。你还没转职，所以现在一个技能也没有。记住以后技能都要在#b技能窗口#k里看，对你很有用。");
        } else if (sel == 15) {
            cm.sendNext("怎么去金银岛？这座岛的东边有个港口叫南港。那里停着一艘会飞的船，船前面站着船长，去问他吧。");
        } else if (sel == 16) {
            cm.sendNext("金币就是冒险岛用的钱。买东西要花金币。想挣金币，就靠打怪、把东西卖给商店，或者做任务……");
        }
    } else if (status == 1) {
        if (sel == 0) {
            cm.sendNextPrev("要打怪得先装备武器。装好以后按#b攻击键#k就行，掌握节奏就能轻松打倒怪物。");
        } else if (sel == 1) {
            cm.sendNextPrev("转职之后你会学到各种技能，可以把攻击技能绑到快捷栏上。绑好以后就不用按攻击键了，直接按快捷键就能放。");
        } else if (sel == 2) {
            cm.sendNextPrev("不过要记住，道具栏放满了就捡不进东西。不需要的就卖掉换点金币。转职之后道具栏还会扩充。");
        } else if (sel == 3) {
            cm.sendNextPrev("新手死了没什么好损失的。但转职以后就不一样了，死了会掉经验值，所以千万要小心，别轻易送命。");
        } else if (sel == 4) {
            cm.sendNextPrev("不过转职不光看等级，还要看能力值。每个职业要求的能力不一样，比如战士要力量 35 以上，别的职业也各有要求。跟你要转的职业有关的能力一定要点上去。");
        } else if (sel == 5) {
            cm.sendNextPrev("但是想变强的话别在这座岛上赖太久，在这里根本转不了职。这座岛下面有一块叫金银岛的大陆，比这里大得多，完全不是一个量级。");
        } else if (sel == 8) {
            cm.sendNextPrev("哦对了，魔法师跟别的职业不一样，8 级就能转职。早转职有早转职的好处，但要成为强大的魔法师要下的功夫也更多。选路之前想清楚。");
        } else if (sel == 10) {
            cm.sendNextPrev("每一项能力值都有说明可以看。比如战士看力量，弓箭手看敏捷，魔法师看智力，飞侠看运气。光知道这些还不够，怎么分配点数把角色练出自己的强项，要好好盘算。");
        } else if (sel == 15) {
            cm.sendNextPrev("哦对了！走之前再说一件事。不知道自己在哪里的话就打开#b世界地图#k，上面会标出你站的位置，这样就不怕迷路了。");
        } else {
            start();
        }
    } else {
        start();
    }
}