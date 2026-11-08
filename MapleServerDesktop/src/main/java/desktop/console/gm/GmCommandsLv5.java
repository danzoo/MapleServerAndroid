package desktop.console.gm;

import android.graphics.Rect;
import client.Character;
import client.Client;
import config.YamlConfig;
import constants.game.GameConstants;
import constants.net.ServerConstants;
import net.server.Server;
import net.server.channel.Channel;
import net.server.world.World;
import server.TimerManager;
import server.life.Monster;
import server.life.SpawnPoint;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Reactor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

// Console ports of server-core/client/command/commands/gm5 (rank 5: debug / set / showpackets /
// showmovelife / showsessions / iplist).
//
// These are diagnostics, so the console versions must work with no operator attached:
//   - the two global toggles (showpackets / showmovelife) flip or set a YamlConfig flag and print
//     the resulting state; in this fork they never had a per-client form, only the global flag.
//   - "debug" keeps "<cmd> <type> [角色名]" order because the type is an enumerable keyword; the
//     character name is the subject for all map/player-scoped types (it replaces the typing GM).
//   - showsessions / iplist walk the live world/channel player storages and print one line per
//     session to stdout instead of into an NPC talk window.
public final class GmCommandsLv5 {

    // Same list as DebugCommand.debugTypes (trailing "" entry dropped - it was an NPC-link artifact).
    private static final String[] DEBUG_TYPES = {
            "monster", "packet", "portal", "spawnpoint", "pos", "map", "mobsp", "event",
            "areas", "reactors", "servercoupons", "playercoupons", "timer", "marriage", "buff"
    };

    private GmCommandsLv5() {
    }

    public static void register(GmRegistry r) {
        // was: "@debug <type>" -> everything came out of the typing GM's chat window / NPC talk
        r.add("debug", "<类型> [角色名]", "调试查看：type/help 列出全部类型；地图/角色相关类型需要一个在线角色名", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                printDebugTypes(ctx);
                return;
            }
            String type = args[0].toLowerCase();
            if (type.equals("type") || type.equals("help")) {
                printDebugTypes(ctx);
                return;
            }
            // Types with no subject at all.
            switch (type) {
                case "servercoupons":
                case "coupons":
                    debugServerCoupons(ctx);
                    return;
                case "timer":
                    debugTimer(ctx);
                    return;
                case "packet":
                    ctx.done("原版 debug packet 是空实现（内容只剩一行注释掉的广播），无输出可给");
                    return;
                default:
                    // fall through: the rest need a character as the subject
            }
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: debug " + type + " <角色名>");
                return;
            }
            Character c = ctx.require(args[1]);
            if (c == null) {
                return;
            }
            switch (type) {
                case "monster":
                    debugWithMap(ctx, c, GmCommandsLv5::debugMonsters);
                    break;
                case "portal":
                    debugWithMap(ctx, c, GmCommandsLv5::debugPortal);
                    break;
                case "spawnpoint":
                    debugWithMap(ctx, c, GmCommandsLv5::debugSpawnpoint);
                    break;
                case "pos":
                    ctx.printf("%s 当前坐标: (%d, %d)%n", c.getName(), c.getPosition().x, c.getPosition().y);
                    break;
                case "map":
                    debugWithMap(ctx, c, GmCommandsLv5::debugMap);
                    break;
                case "mobsp":
                    debugWithMap(ctx, c, GmCommandsLv5::debugMobSpawns);
                    break;
                case "event":
                    if (c.getEventInstance() == null) {
                        ctx.done(c.getName() + " 当前不在任何事件中");
                    } else {
                        ctx.done(c.getName() + " 当前事件: " + c.getEventInstance().getName());
                    }
                    break;
                case "areas":
                    debugWithMap(ctx, c, GmCommandsLv5::debugAreas);
                    break;
                case "reactors":
                    debugWithMap(ctx, c, GmCommandsLv5::debugReactors);
                    break;
                case "playercoupons": {
                    StringBuilder sb = new StringBuilder(c.getName() + " 当前生效的 PLAYER 优惠券:");
                    for (Integer i : c.getActiveCoupons()) {
                        sb.append(' ').append(i);
                    }
                    ctx.println(sb.toString());
                    break;
                }
                case "marriage": {
                    Client cl = c.getClient();
                    Channel ch = cl == null ? null : cl.getChannelServer();
                    if (ch == null) {
                        ctx.fail(c.getName() + " 没有可用的频道连接");
                        return;
                    }
                    ch.debugMarriageStatus();
                    ctx.done("婚姻排队/状态已通过 Channel.debugMarriageStatus 写入服务端日志（debug 级）");
                    break;
                }
                case "buff":
                    c.debugListAllBuffs();
                    ctx.done(c.getName() + " 的 buff 明细已通过 Character.debugListAllBuffs 写入服务端日志（debug 级）");
                    break;
                default:
                    ctx.fail("未知的 debug 类型: " + args[0]);
                    printDebugTypes(ctx);
            }
        });

        // was: "!set v1 v2 ..." -> ServerConstants.DEBUG_VALUES[i] = parseInt(vi)
        // In-game, a non-numeric arg or more than 10 args blew up with an uncaught exception;
        // here both are validated up front and the array is only written when the whole line is good.
        r.add("set", "[整数1 整数2 ...]", "把值写入 ServerConstants.DEBUG_VALUES（测试用的 10 格整数槽，从第 0 格开始排）；不带参数时打印当前值", (ctx, args) -> {
            if (args.length == 0) {
                printDebugValues(ctx);
                return;
            }
            if (args.length > ServerConstants.DEBUG_VALUES.length) {
                ctx.fail("最多 " + ServerConstants.DEBUG_VALUES.length + " 个值，收到 " + args.length + " 个");
                return;
            }
            int[] vals = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                Integer v = ctx.num(args[i], "第 " + (i + 1) + " 个值");
                if (v == null) {
                    return;
                }
                vals[i] = v;
            }
            System.arraycopy(vals, 0, ServerConstants.DEBUG_VALUES, 0, vals.length);
            ctx.done("已写入 " + vals.length + " 个值");
            printDebugValues(ctx);
        });

        // was: "!showpackets" -> toggled the single global flag; this fork has no per-client form.
        r.add("showpackets", "[on|off]", "全局开关：把所有连接收到的封包打印到服务端控制台；不带参数时切换（同原版）", (ctx, args) -> {
            boolean on = !YamlConfig.config.server.USE_DEBUG_SHOW_RCVD_PACKET;
            if (args.length > 0) {
                String a = args[0].toLowerCase();
                if (a.equals("on")) {
                    on = true;
                } else if (a.equals("off")) {
                    on = false;
                } else {
                    ctx.fail("showpackets 只接受 on / off 或不带参数（切换），收到: " + args[0]);
                    return;
                }
            }
            YamlConfig.config.server.USE_DEBUG_SHOW_RCVD_PACKET = on;
            ctx.done("全局收包打印(USE_DEBUG_SHOW_RCVD_PACKET) 已" + (on ? "开启" : "关闭") + " —— 作用于所有连接，没有按角色的开关");
        });

        // was: "!showmovelife" -> toggled the single global flag.
        r.add("showmovelife", "[on|off]", "全局开关：把所有连接发来的 life 移动封包打印到服务端控制台；不带参数时切换（同原版）", (ctx, args) -> {
            boolean on = !YamlConfig.config.server.USE_DEBUG_SHOW_RCVD_MVLIFE;
            if (args.length > 0) {
                String a = args[0].toLowerCase();
                if (a.equals("on")) {
                    on = true;
                } else if (a.equals("off")) {
                    on = false;
                } else {
                    ctx.fail("showmovelife 只接受 on / off 或不带参数（切换），收到: " + args[0]);
                    return;
                }
            }
            YamlConfig.config.server.USE_DEBUG_SHOW_RCVD_MVLIFE = on;
            ctx.done("全局 life 移动打印(USE_DEBUG_SHOW_RCVD_MVLIFE) 已" + (on ? "开启" : "关闭"));
        });

        // was: "!showsessions" -> SessionCoordinator.printSessionTrace(c) into an NPC talk window.
        // The coordinator's session maps are private and have no getters, so the console walks the
        // channel player storages instead (one line per live game session, with the IP the original
        // showed via iplist anyway).
        r.add("showsessions", "", "列出当前所有在线连接：角色id/名字/账号id/IP/世界与频道/地图", (ctx, args) -> {
            ctx.printf("%-8s %-16s %-8s %-21s %-8s %-8s%n", "角色id", "名字", "账号id", "IP", "世界/频道", "地图");
            int total = 0;
            for (Channel ch : ctx.channels()) {
                for (Character c : ch.getPlayerStorage().getAllCharacters()) {
                    Client cl = c.getClient();
                    String ip = cl == null ? "-" : String.valueOf(cl.getRemoteAddress());
                    ctx.printf("%-8d %-16s %-8d %-21s %-8s %-8d%n",
                            c.getId(), c.getName(), c.getAccountID(), ip,
                            ch.getWorld() + "/" + ch.getId(), c.getMapId());
                    total++;
                }
            }
            ctx.done("当前游戏会话: " + total + " 个");
            ctx.println("（登录阶段未进游戏的会话与在线 HWID 由 SessionCoordinator 私有持有，无公开接口，控制台列不出来）");
        });

        // was: "@iplist" -> NPC talk window with the player-IP relation per world.
        // The original has no add/remove sub-commands - it is read-only, so this stays read-only.
        r.add("iplist", "", "按世界列出所有在线角色的名字与 IP", (ctx, args) -> {
            ctx.println("玩家-IP 对照:");
            int total = 0;
            for (World w : ctx.worlds()) {
                Collection<Character> chars = w.getPlayerStorage().getAllCharacters();
                if (chars.isEmpty()) {
                    continue;
                }
                ctx.println(worldName(w.getId()) + ":");
                for (Character chr : chars) {
                    Client cl = chr.getClient();
                    String ip = cl == null ? "(已掉线)" : String.valueOf(cl.getRemoteAddress());
                    ctx.println("  " + chr.getName() + " - " + ip);
                    total++;
                }
            }
            ctx.done("共 " + total + " 个在线角色");
        });
    }

    // ------------------------------------------------------------------ debug types

    private interface MapDebug {
        void run(GmContext ctx, Character c, MapleMap map);
    }

    // The map types all share: resolve the character's map, bail with a printed reason if none.
    private static void debugWithMap(GmContext ctx, Character c, MapDebug body) {
        MapleMap map = ctx.mapOfPlayer(c.getName());
        if (map == null) {
            return;
        }
        body.run(ctx, c, map);
    }

    private static void debugMonsters(GmContext ctx, Character c, MapleMap map) {
        List<MapObject> monsters = map.getMapObjectsInRange(c.getPosition(), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.MONSTER));
        for (MapObject monstermo : monsters) {
            Monster monster = (Monster) monstermo;
            Character controller = monster.getController();
            String aggro = controller == null ? "<none>"
                    : controller.getName() + " Has aggro: " + monster.isControllerHasAggro()
                            + " Knowns aggro: " + monster.isControllerKnowsAboutAggro();
            ctx.printf("%d %-16s 仇恨目标: %s%n", monster.getId(), monster.getName(), aggro);
        }
        ctx.done(map.getId() + " 上共 " + monsters.size() + " 只怪（以 " + c.getName() + " 为基准）");
    }

    private static void debugPortal(GmContext ctx, Character c, MapleMap map) {
        Portal portal = map.findClosestPortal(c.getPosition());
        if (portal == null) {
            ctx.done("地图 " + map.getId() + " 上没有传送门");
            return;
        }
        ctx.println("最近传送门: " + portal.getId() + " '" + portal.getName() + "' Type: " + portal.getType()
                + " --> toMap: " + portal.getTargetMapId() + " scriptname: '" + portal.getScriptName()
                + "' state: " + (portal.getPortalState() ? 1 : 0) + "（基准: " + c.getName() + "）");
    }

    private static void debugSpawnpoint(GmContext ctx, Character c, MapleMap map) {
        SpawnPoint sp = map.findClosestSpawnpoint(c.getPosition());
        if (sp == null) {
            ctx.done("地图 " + map.getId() + " 上没有怪物刷新点");
            return;
        }
        ctx.println("最近怪物刷新点: Position: x " + sp.getPosition().x + " y " + sp.getPosition().y
                + " Spawns mobid: '" + sp.getMonsterId() + "' --> canSpawn: " + !sp.getDenySpawn()
                + " canSpawnRightNow: " + sp.shouldSpawn() + "（基准: " + c.getName() + "）");
    }

    private static void debugMap(GmContext ctx, Character c, MapleMap map) {
        ctx.println("地图 id " + map.getId()
                + ", event: '" + (map.getEventInstance() != null ? map.getEventInstance().getName() : "null") + "'"
                + "; Players: " + map.getAllPlayers().size()
                + ", Mobs: " + map.countMonsters()
                + ", Reactors: " + map.countReactors()
                + ", Items: " + map.countItems()
                + ", Objects: " + map.getMapObjects().size()
                + "（来自 " + c.getName() + " 所在地图）");
    }

    // MapleMap keeps its spawn-point list private; the only way to enumerate it is the original's
    // reportMonsterSpawnPoints(chr), which writes into the character's chat window. The console
    // prints what is public and pushes the per-point detail to that (GM) character instead.
    private static void debugMobSpawns(GmContext ctx, Character c, MapleMap map) {
        ctx.println("地图 " + map.getId() + ": 当前存活怪物 " + map.countMonsters()
                + " 只, 全图已占用刷新计数(used): " + map.getSpawnedMonstersOnMap());
        map.reportMonsterSpawnPoints(c);
        ctx.done("每个刷新点的明细（id/canSpawn/numSpawned/x/y/time/team）已推送到 " + c.getName() + " 的聊天窗口（接口私有，控制台拿不到）");
    }

    private static void debugAreas(GmContext ctx, Character c, MapleMap map) {
        ctx.println("地图 " + map.getId() + " 的配置区域:");
        int index = 0;
        for (Rect rect : map.getAreas()) {
            ctx.println("  Id: " + index + " -> posX: " + rect.left + " posY: " + rect.top
                    + " dX: " + rect.width() + " dY: " + rect.height() + ".");
            index++;
        }
        if (index == 0) {
            ctx.println("  （无）");
        }
    }

    private static void debugReactors(GmContext ctx, Character c, MapleMap map) {
        ctx.println("地图 " + map.getId() + " 的reactor状态:");
        for (MapObject mmo : map.getReactors()) {
            Reactor mr = (Reactor) mmo;
            ctx.println("  Id: " + mr.getId() + " Oid: " + mr.getObjectId() + " name: '" + mr.getName()
                    + "' -> Type: " + mr.getReactorType() + " State: " + mr.getState()
                    + " Event State: " + mr.getEventState()
                    + " Position: x " + mr.getPosition().x + " y " + mr.getPosition().y + ".");
        }
    }

    private static void debugServerCoupons(GmContext ctx) {
        StringBuilder sb = new StringBuilder("当前生效的 SERVER 优惠券:");
        for (Integer i : Server.getInstance().getActiveCoupons()) {
            sb.append(' ').append(i);
        }
        ctx.println(sb.toString());
    }

    private static void debugTimer(GmContext ctx) {
        TimerManager tMan = TimerManager.getInstance();
        ctx.println("Total Task: " + tMan.getTaskCount()
                + " Current Task: " + tMan.getQueuedTasks()
                + " Active Task: " + tMan.getActiveCount()
                + " Completed Task: " + tMan.getCompletedTaskCount());
    }

    private static void printDebugTypes(GmContext ctx) {
        ctx.println("可用的 debug 类型（标注 [角色名] 的必须跟在类型后面）:");
        ctx.println("  type / help                    列出本帮助");
        ctx.println("  monster   [角色名]              该角色周围所有怪的仇恨目标");
        ctx.println("  packet                         原版即空实现，无输出");
        ctx.println("  portal    [角色名]              距离该角色最近的传送门");
        ctx.println("  spawnpoint [角色名]             距离该角色最近的怪物刷新点");
        ctx.println("  pos       [角色名]              该角色的坐标");
        ctx.println("  map       [角色名]              该角色所在地图的统计");
        ctx.println("  mobsp     [角色名]              该角色所在地图的刷新点（明细见说明）");
        ctx.println("  event     [角色名]              该角色当前的实例事件");
        ctx.println("  areas     [角色名]              该角色所在地图的区域配置");
        ctx.println("  reactors  [角色名]              该角色所在地图的 reactor 状态");
        ctx.println("  servercoupons / coupons        全局生效的优惠券");
        ctx.println("  playercoupons [角色名]          该角色生效的优惠券");
        ctx.println("  timer                          全局定时器统计");
        ctx.println("  marriage  [角色名]              该角色所在频道的婚姻排队（输出在日志）");
        ctx.println("  buff      [角色名]              该角色的 buff 明细（输出在日志）");
    }

    // ------------------------------------------------------------------ helpers

    private static void printDebugValues(GmContext ctx) {
        StringBuilder sb = new StringBuilder("DEBUG_VALUES:");
        for (int i = 0; i < ServerConstants.DEBUG_VALUES.length; i++) {
            sb.append('[').append(i).append("]=").append(ServerConstants.DEBUG_VALUES[i]).append(' ');
        }
        ctx.println(sb.toString().trim());
    }

    private static String worldName(int world) {
        String[] names = GameConstants.WORLD_NAMES;
        if (names != null && world >= 0 && world < names.length) {
            return names[world] + " (world " + world + ")";
        }
        return "世界 " + world;
    }
}
