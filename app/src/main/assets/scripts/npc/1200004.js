/**
 ----------------------------------------------------------------------------------
 Whale Between Lith harbor and Rien.

 1200004 Puro

 Credits to: MapleSanta
 ----------------------------------------------------------------------------------
 **/

var menu = new Array("里恩");
var method;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();

    } else {
        if (mode == 0 && status == 0) {
            cm.dispose();
            return;
        } else if (mode == 0) {
            cm.sendNext("好吧。改了主意再告诉我。");
            cm.dispose();
            return;
        }
        status++;
        if (status == 0) {
            var display = "";
            for (var i = 0; i < menu.length; i++) {
                display += "\r\n#L" + i + "##b 里恩（800 金币）#k";
            }
            cm.sendSimple("你是想离开金银岛去我们那边看看吗？坐上这条船，我可以带你从#b明珠港#k到#b里恩#k再回来。不过要收#b800 金币#k的船票。要去里恩吗？一会儿就到了。\r\n" + display);

        } else if (status == 1) {
            if (cm.getMeso() < 800) {
                cm.sendNext("嗯……你确定带了#b800#k金币吗？打开道具栏确认够数。不付船票我可不能让你上船……");
                cm.dispose();
            } else {
                cm.gainMeso(-800);
                cm.warp(200090060);
                cm.dispose();
            }
        }
    }
}