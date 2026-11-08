package desktop.console.gm;

import android.graphics.Point;
import client.BuffStat;
import client.Character;
import client.Client;
import client.Disease;
import client.Stat;
import client.inventory.Item;
import client.autoban.AutobanFactory;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import constants.id.ItemId;
import constants.id.MapId;
import constants.id.MobId;
import io.netty.buffer.Unpooled;
import net.PacketHandler;
import net.PacketProcessor;
import net.net.logging.MonitoredChrLogger;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import net.server.HostPort;
import net.server.Server;
import net.server.channel.Channel;
import scripting.AbstractPlayerInteraction;
import scripting.portal.PortalScriptManager;
import server.CashShop;
import server.ItemInformationProvider;
import server.ShopFactory;
import server.TimerManager;
import server.expeditions.Expedition;
import server.life.LifeFactory;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.MonsterInformationProvider;
import server.life.NPC;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.MapManager;
import server.maps.Portal;
import server.quest.Quest;
import tools.DatabaseConnection;
import tools.HexTool;
import tools.PacketCreator;
import tools.Pair;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// Console ports of server-core/client/command/commands/gm3 (registered by CommandsExecutor
// .registerLv3Commands()).
//
// Rules followed here (see console_gm_style.md):
//   - map-scoped commands take the map id as their first argument;
//   - commands that used to act on the typing GM take an online character name instead;
//   - every in-game read-out (NPC talk window / dropMessage / yellowMessage) becomes console output;
//   - world-scoped side effects that in-game were limited to the operator's world are broadcast to
//     all worlds, since the console has no operator world.
public final class GmCommandsLv3 {

    private GmCommandsLv3() {
    }

    public static void register(GmRegistry r) {
        // ---------------------------------------------------------------- debuff
        r.add("debuff", "<地图id> <SLOW|SEDUCE|ZOMBIFY|CONFUSE|STUN|POISON|SEAL|DARKNESS|WEAKEN|CURSE>",
                "给这张地图上的所有玩家上一个负面状态（原来是给打字玩家周围的人上的）", (ctx, args) -> {
                    if (GmContext.missing(args, 2)) {
                        ctx.println("用法: debuff <地图id> <负面状态名>");
                        return;
                    }
                    Integer mapid = ctx.num(args, 0, "地图id");
                    if (mapid == null) {
                        return;
                    }
                    MapleMap map = ctx.requireMap(mapid);
                    if (map == null) {
                        return;
                    }
                    Pair<Disease, MobSkill> d = resolveDebuff(args[1]);
                    if (d == null) {
                        ctx.println("可选状态: SLOW SEDUCE ZOMBIFY CONFUSE STUN POISON SEAL DARKNESS WEAKEN CURSE");
                        return;
                    }
                    int done = 0;
                    for (Character chr : map.getCharacters()) {
                        if (chr != null) {
                            chr.giveDebuff(d.getLeft(), d.getRight());
                            done++;
                        }
                    }
                    ctx.done("地图 " + mapid + " 上 " + done + " 个玩家中了 " + args[1].toUpperCase());
                });

        // ---------------------------------------------------------------- fly
        r.add("fly", "<角色名> <on|off>", "开关这个账号的飞行功能（F4 那类 GM 飞行）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: fly <角色名> <on|off>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client cl = c.getClient();
            if (cl == null) {
                ctx.fail(c.getName() + " 已经掉线，没有可用的连接");
                return;
            }
            boolean on = !args[1].equalsIgnoreCase("off");
            if (!on && !args[1].equalsIgnoreCase("on")) {
                ctx.println("第二个参数只认 on 或 off，收到的是: " + args[1]);
                return;
            }
            int accId = cl.getAccID();
            boolean before = Server.getInstance().canFly(accId);
            Server.getInstance().changeFly(accId, on);
            String msg = on ? "已为账号 " + accId + " 开启飞行（飞行状态下不能攻击）"
                    : "已为账号 " + accId + " 关闭飞行，可以正常攻击";
            if (before != on) {
                msg += "，需要重新登录才生效";
            }
            ctx.done(msg);
        });

        // ---------------------------------------------------------------- spawn (also a console built-in)
        r.add("spawn", "<地图id> <怪物id> [数量=1]", "在这张地图上刷怪（落在地图里第一个玩家脚下，没有玩家就落在原点）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: spawn <地图id> <怪物id> [数量]");
                return;
            }
            Integer mapid = ctx.num(args, 0, "地图id");
            Integer mobid = ctx.num(args, 1, "怪物id");
            if (mapid == null || mobid == null) {
                return;
            }
            int qty = 1;
            if (args.length > 2) {
                Integer q = ctx.num(args, 2, "数量");
                if (q == null) {
                    return;
                }
                qty = Math.max(1, q);
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            if (LifeFactory.getMonster(mobid) == null) {
                ctx.fail("怪物 id " + mobid + " 不存在");
                return;
            }
            Point pos = spawnPosition(map);
            for (int i = 0; i < qty; i++) {
                Monster m = LifeFactory.getMonster(mobid);
                if (m == null) {
                    break;
                }
                map.spawnMonsterOnGroundBelow(m, pos);
            }
            ctx.done("在地图 " + mapid + " 的 (" + pos.x + "," + pos.y + ") 刷了 " + qty + " 只怪 " + mobid);
        });

        // ---------------------------------------------------------------- mutemap
        r.add("mutemap", "<地图id>", "切换这张地图的禁言状态", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            boolean muted = !map.isMuted();
            map.setMuted(muted);
            ctx.done("地图 " + mapid + " 现在" + (muted ? "已禁言" : "已解除禁言"));
        });

        // ---------------------------------------------------------------- checkdmg
        r.add("checkdmg", "<角色名>", "查看这个角色的面板与最大基础伤害", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: checkdmg <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            int maxBase = v.calculateMaxBaseDamage(v.getTotalWatk());
            Integer watkBuff = v.getBuffedValue(BuffStat.WATK);
            Integer matkBuff = v.getBuffedValue(BuffStat.MATK);
            int blessing = v.getSkillLevel(10000000 * v.getJobType() + 12);
            ctx.printf("%s 的面板：%n", v.getName());
            ctx.println("  力量: " + v.getTotalStr() + "  敏捷: " + v.getTotalDex() + "  智力: " + v.getTotalInt() + "  运气: " + v.getTotalLuk());
            ctx.println("  物攻(WATK): " + v.getTotalWatk() + "  魔攻(MATK): " + v.getTotalMagic());
            ctx.println("  物攻 buff: " + (watkBuff == null ? 0 : watkBuff) + "  魔攻 buff: " + (matkBuff == null ? 0 : matkBuff) + "  祝福等级: " + blessing);
            ctx.println("  不打技能时的最大基础伤害: " + maxBase);
        });

        // ---------------------------------------------------------------- inmap
        r.add("inmap", "<地图id>", "列出这张地图上的所有玩家", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            List<Character> chars = map.getAllPlayers();
            if (chars == null || chars.isEmpty()) {
                ctx.done("地图 " + mapid + " 上没有玩家");
                return;
            }
            ctx.println("地图 " + mapid + " " + safeName(map) + " 共 " + chars.size() + " 人：");
            for (Character c : chars) {
                ctx.printCharacter(c);
            }
        });

        // ---------------------------------------------------------------- reloadevents
        r.add("reloadevents", "", "重载所有频道的事件脚本", (ctx, args) -> {
            List<Channel> channels = ctx.channels();
            int reloaded = 0, uninitialized = 0;
            for (Channel ch : channels) {
                if (ch == null) {
                    continue;
                }
                // 频道的事件管理器要 Channel 初始化好才有；没初始化时不能在这里炸掉控制台。
                if (ch.getEventSM() == null) {
                    uninitialized++;
                    continue;
                }
                ch.reloadEventScriptManager();
                reloaded++;
            }
            ctx.done("已重载 " + reloaded + "/" + channels.size() + " 个频道的事件脚本"
                    + (uninitialized == 0 ? "" : "，另有 " + uninitialized + " 个频道尚未初始化事件脚本，已跳过"));
        });

        // ---------------------------------------------------------------- reloaddrops
        r.add("reloaddrops", "", "重载所有掉落数据", (ctx, args) -> {
            MonsterInformationProvider.getInstance().clearDrops();
            ctx.done("已重载掉落数据");
        });

        // ---------------------------------------------------------------- reloadportals
        r.add("reloadportals", "", "重载所有传送点脚本", (ctx, args) -> {
            PortalScriptManager.getInstance().reloadPortalScripts();
            ctx.done("已重载传送点脚本");
        });

        // ---------------------------------------------------------------- reloadmap
        r.add("reloadmap", "<地图id>", "重载这张地图，并把图上的人重新放进去", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            Channel ch = ctx.channelFor(mapid);
            if (ch == null) {
                ctx.fail("服务端还没有可用的频道，无法重载地图 " + mapid);
                return;
            }
            MapManager mm = ch.getMapFactory();
            if (mm.getMap(mapid) == null) {
                ctx.fail("地图 " + mapid + " 不存在（服务端资源里没有这张图）");
                return;
            }
            List<Character> chars = mm.getMap(mapid).getAllPlayers();
            MapleMap newMap = mm.resetMap(mapid);
            if (newMap == null) {
                ctx.fail("地图 " + mapid + " 重载之后仍然拿不到，玩家没有搬运");
                return;
            }
            int moved = 0;
            for (Character chr : new ArrayList<>(chars)) {
                chr.saveLocationOnWarp();
                chr.changeMap(newMap);
                moved++;
            }
            newMap.respawn();
            ctx.done("地图 " + mapid + " 已重载，搬运了 " + moved + " 个玩家（原来游戏内会给每个人发一条提示，这里省略）");
        });

        // ---------------------------------------------------------------- reloadshops
        r.add("reloadshops", "", "重载弹窗商店与 NPC 商店", (ctx, args) -> {
            ShopFactory.getInstance().reloadShops();
            ctx.done("已重载商店数据");
        });

        // ---------------------------------------------------------------- hpmp
        r.add("hpmp", "<角色名> <数值>", "把这个角色的 HP/MP 同时设成这个值", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: hpmp <角色名> <数值>");
                return;
            }
            Character c = ctx.require(args[0]);
            Integer v = ctx.num(args, 1, "数值");
            if (c == null || v == null) {
                return;
            }
            c.updateHpMp(v);
            ctx.done(c.getName() + " 的 HP/MP 已设为 " + v);
        });

        // ---------------------------------------------------------------- maxhpmp
        r.add("maxhpmp", "<角色名> <数值>", "把这个角色的上限 HP/MP 设为该值（会自动算上 buff 占的额度）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: maxhpmp <角色名> <数值>");
                return;
            }
            Character c = ctx.require(args[0]);
            Integer raw = ctx.num(args, 1, "数值");
            if (c == null || raw == null) {
                return;
            }
            int extraHp = c.getCurrentMaxHp() - c.getClientMaxHp();
            int extraMp = c.getCurrentMaxMp() - c.getClientMaxMp();
            int statUpdate = Math.max(1 + Math.max(extraHp, extraMp), raw);
            c.updateMaxHpMaxMp(statUpdate - extraHp, statUpdate - extraMp);
            ctx.done(c.getName() + " 的上限已设为 HP " + (statUpdate - extraHp) + " / MP " + (statUpdate - extraMp)
                    + "（buff 额外占用 HP " + extraHp + " / MP " + extraMp + "）");
        });

        // ---------------------------------------------------------------- music
        r.add("music", "<地图id> [歌名]", "不带歌名时列出所有可用歌曲；带歌名时让这张地图播放这首歌", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: music <地图id> [歌名]   （只有地图id 时是列出歌单）");
                return;
            }
            if (args.length == 1) {
                printSongs(ctx);
                return;
            }
            Integer mapid = ctx.num(args, 0, "地图id");
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            String song = ctx.rest(args, 1);
            String found = null;
            for (String s : GameConstants.GAME_SONGS) {
                if (s.equalsIgnoreCase(song)) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                ctx.fail("歌单里没有这首歌: " + song);
                printSongs(ctx);
                return;
            }
            map.broadcastMessage(PacketCreator.musicChange(found));
            ctx.done("地图 " + mapid + " 开始播放 " + found);
        });

        // ---------------------------------------------------------------- monitor
        r.add("monitor", "<角色名>", "切换是否记录这个角色的封包日志", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: monitor <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            boolean monitored = MonitoredChrLogger.toggleMonitored(v.getId());
            ctx.done(v.getName() + " (id " + v.getId() + ") " + (monitored ? "已开始监控封包" : "已停止监控封包"));
        });

        // ---------------------------------------------------------------- monitors
        r.add("monitors", "", "列出所有被监控封包的角色", (ctx, args) -> {
            int shown = 0;
            for (int chrId : MonitoredChrLogger.getMonitoredChrIds()) {
                ctx.println("  " + safeNameById(chrId) + " (id " + chrId + ") 正在被监控");
                shown++;
            }
            if (shown == 0) {
                ctx.done("当前没有监控任何角色");
            }
        });

        // ---------------------------------------------------------------- ignore
        r.add("ignore", "<角色名>", "切换是否让这个角色的自动封禁提醒静音", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: ignore <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            boolean ignored = AutobanFactory.toggleIgnored(v.getId());
            ctx.done(v.getName() + " (id " + v.getId() + ") " + (ignored ? "已忽略自动封禁提醒" : "恢复自动封禁提醒"));
        });

        // ---------------------------------------------------------------- ignored
        r.add("ignored", "", "列出所有被忽略自动封禁提醒的角色", (ctx, args) -> {
            int shown = 0;
            for (int chrId : AutobanFactory.getIgnoredChrIds()) {
                ctx.println("  " + safeNameById(chrId) + " (id " + chrId + ") 已被忽略");
                shown++;
            }
            if (shown == 0) {
                ctx.done("当前没有忽略任何角色");
            }
        });

        // ---------------------------------------------------------------- pos
        r.add("pos", "<角色名>", "显示这个角色当前坐标和所站的地面编号", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: pos <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            MapleMap map = ctx.mapOf(c);
            if (map == null) {
                ctx.fail(c.getName() + " 正在传送中，没有当前地图");
                return;
            }
            Point p = c.getPosition();
            ctx.println(c.getName() + " 坐标: (" + p.x + ", " + p.y + ")  地图 " + map.getId());
            Foothold fh = map.getFootholds().findBelow(p);
            ctx.println("  地面 foothold id: " + (fh == null ? "无（脚下没有地面）" : String.valueOf(fh.getId())));
        });

        // ---------------------------------------------------------------- togglecoupon
        r.add("togglecoupon", "<道具id>", "启用/停用一张倍率优惠券", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: togglecoupon <道具id>");
                return;
            }
            Integer itemId = ctx.num(args, 0, "道具id");
            if (itemId == null) {
                return;
            }
            if (!ItemConstants.isRateCoupon(itemId)) {
                ctx.fail("道具 " + itemId + " 不是倍率优惠券");
                return;
            }
            Server.getInstance().toggleCoupon(itemId);
            boolean active = Server.getInstance().getActiveCoupons().contains(itemId);
            ctx.done("优惠券 " + itemId + " (" + itemName(itemId) + ") 现在" + (active ? "生效中" : "已停用"));
        });

        // ---------------------------------------------------------------- togglewhitechat (ChatCommand)
        r.add("togglewhitechat", "<角色名>", "切换这个角色 GM 聊天的白字模式", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: togglewhitechat <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            c.toggleWhiteChat();
            ctx.done(c.getName() + " 的 GM 聊天现在是" + (c.getWhiteChat() ? "白色" : "普通") + "模式");
        });

        // ---------------------------------------------------------------- fame
        r.add("fame", "<角色名> <数值>", "把这个角色的名声设为该值（是设定，不是增加）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: fame <角色名> <数值>");
                return;
            }
            Character v = ctx.require(args[0]);
            Integer fame = ctx.num(args, 1, "名声值");
            if (v == null || fame == null) {
                return;
            }
            v.setFame(fame);
            v.updateSingleStat(Stat.FAME, v.getFame());
            ctx.done(v.getName() + " 的名声已设为 " + v.getFame());
        });

        // ---------------------------------------------------------------- givenx (GiveNxCommand)
        r.add("givenx", "<角色名> [nx|mp|np] <数量>", "给这个角色点券。nx=点券(默认)，mp= MaplePoint，np= 预付点券", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: givenx <角色名> [nx|mp|np] <数量>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            int type = 1;
            String typeStr = "nx";
            int valueIdx = args.length - 1;
            if (args.length == 3) {
                String t = args[1].toLowerCase();
                if (t.equals("mp")) {
                    type = 2;
                    typeStr = "mp";
                } else if (t.equals("np")) {
                    type = 4;
                    typeStr = "np";
                } else if (!t.equals("nx")) {
                    ctx.fail("点券类型只认 nx / mp / np，收到的是: " + args[1]);
                    return;
                } else {
                    typeStr = "nx";
                }
            }
            Integer value = ctx.num(args, valueIdx, "数量");
            if (value == null) {
                return;
            }
            CashShop cs = v.getCashShop();
            if (cs == null) {
                ctx.fail(v.getName() + " 的商店数据还没加载好，暂时加不了点券");
                return;
            }
            cs.gainCash(type, value);
            ctx.done("已给 " + v.getName() + " 加 " + value + " " + typeStr.toUpperCase());
        });

        // ---------------------------------------------------------------- givevp
        r.add("givevp", "<角色名> <数量>", "给这个角色所属账号加充值点（VP）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: givevp <角色名> <数量>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "数量");
            if (value == null) {
                return;
            }
            cl.addVotePoints(value);
            ctx.done("已给 " + cl.getPlayer().getName() + " 的账号加 " + value + " VP");
        });

        // ---------------------------------------------------------------- givems
        r.add("givems", "<角色名> <数量|max|min>", "给这个角色加游戏币，数量可用 max / min", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: givems <角色名> <数量|max|min>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            long mesos;
            String value = args[1];
            try {
                mesos = Long.parseLong(value);
                if (mesos > Integer.MAX_VALUE) {
                    mesos = Integer.MAX_VALUE;
                } else if (mesos < Integer.MIN_VALUE) {
                    mesos = Integer.MIN_VALUE;
                }
            } catch (NumberFormatException nfe) {
                if (value.equalsIgnoreCase("max")) {
                    mesos = Integer.MAX_VALUE;
                } else if (value.equalsIgnoreCase("min")) {
                    mesos = Integer.MIN_VALUE;
                } else {
                    ctx.fail("数量需要是整数或 max/min，收到的是: " + value);
                    return;
                }
            }
            v.gainMeso((int) mesos, true);
            ctx.done("已给 " + v.getName() + " 加 " + mesos + " 游戏币");
        });

        // ---------------------------------------------------------------- giverp
        r.add("giverp", "<角色名> <数量>", "给这个角色加奖励点（RP）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: giverp <角色名> <数量>");
                return;
            }
            Character v = ctx.require(args[0]);
            Integer value = ctx.num(args, 1, "数量");
            if (v == null || value == null) {
                return;
            }
            v.setRewardPoints(v.getRewardPoints() + value);
            ctx.done("已给 " + v.getName() + " 加 " + value + " RP，现在共 " + v.getRewardPoints());
        });

        // ---------------------------------------------------------------- expeds
        r.add("expeds", "[世界id]", "列出正在进行的所有 Boss 远征队（不给世界 id 时列出所有世界）", (ctx, args) -> {
            List<Channel> channels = channelsInWorld(ctx, args);
            if (channels == null) {
                return;
            }
            int total = 0;
            for (Channel ch : channels) {
                List<Expedition> expeds = ch.getExpeditions();
                ctx.println("世界 " + ch.getWorld() + " / 频道 " + ch.getId() + ": "
                        + (expeds.isEmpty() ? "没有远征队" : "共 " + expeds.size() + " 支远征队"));
                for (Expedition exped : expeds) {
                    total++;
                    Character leader = exped.getLeader();
                    ctx.println("  > " + exped.getType() + "  状态: "
                            + (exped.isRegistering() ? "招募中" : "已开始")
                            + "  人数: " + exped.getMembers().size()
                            + "  队长: " + (leader == null ? "?" : leader.getName()));
                    for (Map.Entry<Integer, String> e : exped.getMembers().entrySet()) {
                        ctx.println("      - " + e.getValue() + " (id " + e.getKey() + ")");
                    }
                }
            }
            ctx.done("共 " + total + " 支远征队");
        });

        // ---------------------------------------------------------------- kill
        r.add("kill", "<角色名>", "把这个角色的 HP 打成 0（击杀）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: kill <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            v.updateHp(0);
            ctx.done(v.getName() + " 已被击杀");
        });

        // ---------------------------------------------------------------- seed
        r.add("seed", "<角色名>", "把这个玩家脚下（必须在大树种田 HPQ 图里）的 6 颗花种掉出来", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: seed <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            if (c.getMapId() != MapId.HENESYS_PQ) {
                ctx.fail(c.getName() + " 现在在地图 " + c.getMapId() + "，这个命令只在大树种田(910010000)里有效");
                return;
            }
            MapleMap map = ctx.mapOf(c);
            if (map == null) {
                ctx.fail(c.getName() + " 正在传送中，没有当前地图");
                return;
            }
            Point[] pos = {new Point(7, -207), new Point(179, -447), new Point(-3, -687),
                    new Point(-357, -687), new Point(-538, -447), new Point(-359, -207)};
            int[] seeds = {ItemId.PINK_PRIMROSE_SEED, ItemId.PURPLE_PRIMROSE_SEED, ItemId.GREEN_PRIMROSE_SEED,
                    ItemId.BLUE_PRIMROSE_SEED, ItemId.YELLOW_PRIMROSE_SEED, ItemId.BROWN_PRIMROSE_SEED};
            for (int i = 0; i < pos.length; i++) {
                final int idx = i;
                // In-game this slept 100 ms between drops on the command thread; the console thread
                // must not block, so the drops are staggered through TimerManager instead.
                TimerManager.getInstance().schedule(() -> {
                    Item item = new Item(seeds[idx], (short) 0, (short) 1);
                    map.spawnItemDrop(c, c, item, pos[idx], false, true);
                }, i * 100L);
            }
            ctx.done("已在 " + map.getId() + " 的 6 个固定点掉落花种（归属 " + c.getName() + "）");
        });

        // ---------------------------------------------------------------- maxenergy
        r.add("maxenergy", "<角色名>", "把这个角色的 dojo 气槽充满", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: maxenergy <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            c.setDojoEnergy(10000);
            Client cl = c.getClient();
            if (cl != null) {
                cl.sendPacket(PacketCreator.getEnergy("energy", 10000));
            }
            ctx.done(c.getName() + " 的气槽已设为 10000");
        });

        // ---------------------------------------------------------------- killall (also a console built-in alias)
        r.add("killall", "<地图id>", "杀掉这张地图上的所有怪（不掉东西，友好怪和 HornTail 阶段怪不动）", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int count = 0;
            for (Monster monster : map.getAllMonsters()) {
                if (monster == null || monster.getStats().isFriendly()) {
                    continue;
                }
                if (monster.getId() >= MobId.DEAD_HORNTAIL_MIN && monster.getId() <= MobId.HORNTAIL) {
                    continue;
                }
                map.killMonster(monster, null, false, (short) 0);
                count++;
            }
            ctx.done("地图 " + mapid + " 杀了 " + count + " 只怪（控制台没有击杀者，所以不掉经验和掉落）");
        });

        // ---------------------------------------------------------------- banner (also a console built-in)
        r.add("banner", "<秒> <文字...>", "在所有世界顶部横幅显示一段文字（1~3600 秒）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: banner <秒> <文字...>");
                return;
            }
            Integer seconds = ctx.num(args, 0, "秒数");
            if (seconds == null) {
                return;
            }
            if (seconds < 1 || seconds > 3600) {
                ctx.fail("横幅时间只能在 1 到 3600 秒之间");
                return;
            }
            String message = ctx.rest(args, 1);
            if (message.isEmpty()) {
                ctx.println("用法: banner <秒> <文字...>");
                return;
            }
            broadcastAllWorlds(ctx, PacketCreator.serverMessage(message, seconds));
            ctx.done("已向所有世界广播横幅 " + seconds + " 秒: " + message);
        });

        // ---------------------------------------------------------------- notice
        r.add("notice", "<文字...>", "在所有世界发一条蓝色公告（[Notice] 开头）", (ctx, args) -> {
            String message = ctx.rest(args, 0);
            if (message.isEmpty()) {
                ctx.println("用法: notice <文字...>");
                return;
            }
            broadcastAllWorlds(ctx, PacketCreator.serverNotice(6, "[Notice] " + message));
            ctx.done("已广播公告: " + message);
        });

        // ---------------------------------------------------------------- rip
        r.add("rip", "<文字...>", "在所有世界发一条 [RIP] 公告", (ctx, args) -> {
            String message = ctx.rest(args, 0);
            if (message.isEmpty()) {
                ctx.println("用法: rip <文字...>");
                return;
            }
            broadcastAllWorlds(ctx, PacketCreator.serverNotice(6, "[RIP]: " + message));
            ctx.done("已广播 RIP 公告: " + message);
        });

        // ---------------------------------------------------------------- openportal
        r.add("openportal", "<地图id> <门名>", "打开这张地图上的一个传送点", (ctx, args) -> {
            Pair<Integer, String> p = parseMapAndName(ctx, args);
            if (p == null) {
                return;
            }
            MapleMap map = ctx.requireMap(p.getLeft());
            if (map == null) {
                return;
            }
            Portal portal = map.getPortal(p.getRight());
            if (portal == null) {
                ctx.fail("地图 " + p.getLeft() + " 上没有叫 " + p.getRight() + " 的传送点");
                return;
            }
            portal.setPortalState(true);
            ctx.done("地图 " + p.getLeft() + " 的传送点 " + p.getRight() + " 已打开");
        });

        // ---------------------------------------------------------------- closeportal
        r.add("closeportal", "<地图id> <门名>", "关闭这张地图上的一个传送点", (ctx, args) -> {
            Pair<Integer, String> p = parseMapAndName(ctx, args);
            if (p == null) {
                return;
            }
            MapleMap map = ctx.requireMap(p.getLeft());
            if (map == null) {
                return;
            }
            Portal portal = map.getPortal(p.getRight());
            if (portal == null) {
                ctx.fail("地图 " + p.getLeft() + " 上没有叫 " + p.getRight() + " 的传送点");
                return;
            }
            portal.setPortalState(false);
            ctx.done("地图 " + p.getLeft() + " 的传送点 " + p.getRight() + " 已关闭");
        });

        // ---------------------------------------------------------------- pe
        r.add("pe", "<角色名>", "读取 pe.txt 里的十六进制封包，用这个角色的连接当作是他发出来的来走一遍处理流程", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: pe <角色名>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            String packet;
            try (BufferedReader br = Files.newBufferedReader(Paths.get("pe.txt"))) {
                Properties props = new Properties();
                props.load(br);
                packet = props.getProperty("pe");
            } catch (Exception e) {
                ctx.fail("读取 pe.txt 失败: " + e);
                return;
            }
            if (packet == null || packet.trim().isEmpty()) {
                ctx.fail("pe.txt 里没有 pe= 这一项");
                return;
            }
            // Same processor the in-game command used (login-side opcode table, operator's channel).
            PacketProcessor processor = PacketProcessor.getProcessor(0, cl.getChannel());
            if (processor == null) {
                ctx.fail("拿不到频道 " + cl.getChannel() + " 的封包处理器");
                return;
            }
            InPacket inPacket = new ByteBufInPacket(Unpooled.wrappedBuffer(toBytes(packet)));
            short packetId = inPacket.readShort();
            PacketHandler handler = processor.getHandler(packetId);
            if (handler == null) {
                ctx.fail("封包 id " + packetId + " 没有对应的处理器");
                return;
            }
            if (!handler.validateState(cl)) {
                ctx.fail(cl.getPlayer().getName() + " 当前状态不接受这个封包（处理器 "
                        + handler.getClass().getSimpleName() + "）");
                return;
            }
            ctx.println("收到的封包: " + packet.trim());
            handler.handlePacket(inPacket, cl);
            ctx.done("已用 " + cl.getPlayer().getName() + " 的连接处理完封包 " + packetId);
        });

        // ---------------------------------------------------------------- startevent
        r.add("startevent", "<地图id> [人数=50]", "在这张地图开一个报名事件，并在全世界公告（玩家用 @joinevent 报名）", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            int players = 50;
            if (args.length > 1) {
                Integer p = ctx.num(args, 1, "人数");
                if (p == null) {
                    return;
                }
                players = p;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            Channel ch = ctx.channelFor(mapid);
            if (ch == null) {
                ctx.fail("服务端还没有可用的频道，开不了事件");
                return;
            }
            ch.setEvent(new server.events.gm.Event(mapid, players));
            String name = safeName(map);
            String text = "[Event] 地图 " + mapid + " " + name + " 的活动已开启，最多 " + players
                    + " 人可以参加，输入 @joinevent 报名。";
            Server.getInstance().broadcastMessage(ch.getWorld(), PacketCreator.earnTitleMessage(text));
            Server.getInstance().broadcastMessage(ch.getWorld(), PacketCreator.serverNotice(6, text));
            ctx.done("已开启事件（世界 " + ch.getWorld() + "/频道 " + ch.getId() + "）: " + text);
        });

        // ---------------------------------------------------------------- endevent
        r.add("endevent", "<世界id> [频道id]", "关闭某个世界（或其中一个频道）的活动报名入口，不给频道时关掉整个世界", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: endevent <世界id> [频道id]");
                return;
            }
            Integer world = ctx.num(args, 0, "世界id");
            Integer channel = args.length > 1 ? ctx.num(args, 1, "频道id") : null;
            if (world == null || (args.length > 1 && channel == null)) {
                return;
            }
            int closed = 0;
            for (Channel ch : ctx.channels()) {
                if (ch.getWorld() != world) {
                    continue;
                }
                if (channel != null && ch.getId() != channel) {
                    continue;
                }
                ch.setEvent(null);
                closed++;
            }
            if (closed == 0) {
                ctx.fail("世界 " + world + (channel == null ? "" : " 频道 " + channel) + " 没有匹配到频道");
                return;
            }
            ctx.done("已关闭 " + closed + " 个频道的事件报名入口");
        });

        // ---------------------------------------------------------------- startmapevent
        r.add("startmapevent", "<地图id> [角色名]", "在这张地图上开一个「经典」小游戏事件（枫叶/举重/OX/滑雪/雪球需要给一个在场角色名）", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            Character anchor = null;
            if (args.length > 1) {
                anchor = ctx.require(args[1]);
                if (anchor == null) {
                    return;
                }
            } else if (needsAnchorPlayer(mapid)) {
                ctx.println("用法: startmapevent <地图id> <角色名>   （这类事件要有一个在场角色当锚点）");
                return;
            }
            map.startEvent(anchor);
            ctx.done("地图 " + mapid + " 的事件已启动" + (anchor == null ? "" : "（锚点角色 " + anchor.getName() + "）"));
        });

        // ---------------------------------------------------------------- stopmapevent
        r.add("stopmapevent", "<地图id>", "停掉这张地图上的「经典」事件标记", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            map.setEventStarted(false);
            ctx.done("地图 " + mapid + " 的事件已停止");
        });

        // ---------------------------------------------------------------- online2
        r.add("online2", "[世界id]", "按频道列出在线角色（不给世界 id 时列出所有世界）", (ctx, args) -> {
            List<Channel> channels = channelsInWorld(ctx, args);
            if (channels == null) {
                return;
            }
            int total = 0;
            for (Channel ch : channels) {
                Collection<Character> chars = ch.getPlayerStorage().getAllCharacters();
                total += chars.size();
                ctx.println("世界 " + ch.getWorld() + " / 频道 " + ch.getId() + "（" + chars.size() + " 人）:");
                for (Character c : chars) {
                    ctx.printCharacter(c);
                }
            }
            ctx.done("共 " + total + " 人在线");
        });

        // ---------------------------------------------------------------- ban
        r.add("ban", "<角色名> <原因...>", "封禁角色：在线就顺带封 IP/MAC 并踢下线，不在线则按名字封号", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: ban <角色名> <原因...>");
                return;
            }
            String ign = args[0];
            String reason = ctx.rest(args, 1);
            Character target = ctx.online(ign);
            if (target != null) {
                Client tc = target.getClient();
                if (tc == null) {
                    ctx.fail(target.getName() + " 已经掉线，改用离线封号流程");
                    target = null;
                } else {
                    String ip = tc.getRemoteAddress();
                    try {
                        SQLiteDatabase con = DatabaseConnection.getConnection();
                        if (ip != null && HostPort.isIpLiteral(ip)) {
                            ContentValues values = new ContentValues();
                            values.put("ip", HostPort.normalizeHost(ip));
                            values.put("aid", String.valueOf(tc.getAccID()));
                            con.insert("ipbans", null, values);
                        }
                    } catch (Exception e) {
                        ctx.println("! 封 IP 时出错，" + target.getName() + " 的 IP 没有被封: " + e);
                    }
                    try {
                        tc.banMacs();
                    } catch (Exception e) {
                        ctx.println("! 封 MAC 时出错: " + e);
                    }
                    String fullReason = "CONSOLE banned " + Character.makeMapleReadable(target.getName())
                            + " for " + reason + (ip == null ? "" : " (IP: " + ip + ")");
                    target.ban(fullReason);
                    broadcastAllWorlds(ctx, PacketCreator.serverNotice(6, "[RIP]: " + ign + " has been banned."));
                    final Character rip = target;
                    TimerManager.getInstance().schedule(() -> {
                        Client rc = rip.getClient();
                        if (rc != null) {
                            rc.disconnect(false, false);
                        }
                    }, 5000);
                    ctx.done("已封禁 " + target.getName() + "，原因: " + fullReason + "（5 秒后断开连接）");
                    return;
                }
            }
            if (Character.ban(ign, reason, false)) {
                broadcastAllWorlds(ctx, PacketCreator.serverNotice(6, "[RIP]: " + ign + " has been banned."));
                ctx.done("已按名字封禁 " + ign + "，原因: " + reason);
            } else {
                ctx.fail("封禁 " + ign + " 失败（角色名不存在或写库失败）");
            }
        });

        // ---------------------------------------------------------------- unban
        r.add("unban", "<角色名>", "解封这个角色（清掉封禁标记、IP 封禁和 MAC 封禁）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: unban <角色名>");
                return;
            }
            try {
                int aid = Character.getAccountIdByName(args[0]);
                SQLiteDatabase con = DatabaseConnection.getConnection();
                ContentValues values = new ContentValues();
                values.put("banned", -1);
                con.update("accounts", values, "id = ?", new String[]{String.valueOf(aid)});
                con.delete("ipbans", "aid = ?", new String[]{String.valueOf(aid)});
                con.delete("macbans", "aid = ?", new String[]{String.valueOf(aid)});
            } catch (Exception e) {
                ctx.fail("解封 " + args[0] + " 失败: " + e);
                return;
            }
            ctx.done("已解封 " + args[0]);
        });

        // ---------------------------------------------------------------- healmap
        r.add("healmap", "<地图id>", "回满这张地图上所有玩家的 HP/MP", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int healed = 0;
            for (Character c : map.getCharacters()) {
                if (c != null) {
                    c.healHpMp();
                    healed++;
                }
            }
            ctx.done("地图 " + mapid + " 已回满 " + healed + " 个玩家");
        });

        // ---------------------------------------------------------------- healperson
        r.add("healperson", "<角色名>", "回满这个角色的 HP/MP", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: healperson <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            v.healHpMp();
            ctx.done(v.getName() + " 已回满 HP/MP");
        });

        // ---------------------------------------------------------------- hurt
        r.add("hurt", "<角色名>", "把这个角色的 HP 打到 1（半死不活）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: hurt <角色名>");
                return;
            }
            Character v = ctx.require(args[0]);
            if (v == null) {
                return;
            }
            v.updateHp(1);
            ctx.done(v.getName() + " 的 HP 已打到 1");
        });

        // ---------------------------------------------------------------- killmap
        r.add("killmap", "<地图id>", "杀掉这张地图上的所有玩家", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int killed = 0;
            for (Character c : map.getCharacters()) {
                if (c != null) {
                    c.updateHp(0);
                    killed++;
                }
            }
            ctx.done("地图 " + mapid + " 上死了 " + killed + " 个玩家");
        });

        // ---------------------------------------------------------------- night
        r.add("night", "<地图id>", "给这张地图的天空压成黑夜效果", (ctx, args) -> {
            Integer mapid = parseMapId(ctx, args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            map.broadcastNightEffect();
            ctx.done("地图 " + mapid + " 已切成黑夜");
        });

        // ---------------------------------------------------------------- npc
        r.add("npc", "<地图id> <npcid>", "在这张地图上放一个 NPC（落点和 spawn 一样取图上第一个玩家脚下）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: npc <地图id> <npcid>");
                return;
            }
            Integer mapid = ctx.num(args, 0, "地图id");
            Integer npcid = ctx.num(args, 1, "npcid");
            if (mapid == null || npcid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            NPC npc = LifeFactory.getNPC(npcid);
            if (npc == null) {
                ctx.fail("NPC id " + npcid + " 不存在");
                return;
            }
            Point pos = spawnPosition(map);
            Foothold fh = map.getFootholds().findBelow(pos);
            if (fh == null) {
                ctx.fail("落点 (" + pos.x + "," + pos.y + ") 脚下没有地面，放不下 NPC，请先让一个玩家站在这张图上");
                return;
            }
            npc.setPosition(pos);
            npc.setCy(pos.y);
            npc.setRx0(pos.x + 50);
            npc.setRx1(pos.x - 50);
            npc.setFh(fh.getId());
            map.addMapObject(npc);
            map.broadcastMessage(PacketCreator.spawnNPC(npc));
            ctx.done("已在地图 " + mapid + " 的 (" + pos.x + "," + pos.y + ") 放下 NPC " + npcid);
        });

        // ---------------------------------------------------------------- face
        r.add("face", "<角色名> <脸id>", "换这张脸（脸必须是真实存在的脸型道具）", (ctx, args) -> {
            changeLook(ctx, args, "face", "脸");
        });

        // ---------------------------------------------------------------- hair
        r.add("hair", "<角色名> <发型id>", "换这个发型（发型必须是真实存在的发型道具）", (ctx, args) -> {
            changeLook(ctx, args, "hair", "发型");
        });

        // ---------------------------------------------------------------- startquest
        r.add("startquest", "<角色名> <任务id>", "直接给这个角色接一个任务", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: startquest <角色名> <任务id>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            Integer questid = ctx.num(args, 1, "任务id");
            if (cl == null || questid == null) {
                return;
            }
            Character c = cl.getPlayer();
            if (c.getQuestStatus(questid) != 0) {
                ctx.done(c.getName() + " 已经接了或完成了任务 " + questid + "，没有重复接");
                return;
            }
            AbstractPlayerInteraction api = new AbstractPlayerInteraction(cl);
            Quest quest = Quest.getInstance(questid);
            boolean ok = quest != null && quest.getNpcRequirement(false) != -1
                    ? api.forceStartQuest(questid, quest.getNpcRequirement(false))
                    : api.forceStartQuest(questid);
            ctx.done((ok ? "已给 " : "接取失败: ") + c.getName() + " 任务 " + questid);
        });

        // ---------------------------------------------------------------- completequest
        r.add("completequest", "<角色名> <任务id>", "直接完成这个角色正在进行的那个任务", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: completequest <角色名> <任务id>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            Integer questid = ctx.num(args, 1, "任务id");
            if (cl == null || questid == null) {
                return;
            }
            Character c = cl.getPlayer();
            if (c.getQuestStatus(questid) != 1) {
                ctx.done(c.getName() + " 没有正在进行任务 " + questid + "（状态 " + c.getQuestStatus(questid) + "），没法完成");
                return;
            }
            AbstractPlayerInteraction api = new AbstractPlayerInteraction(cl);
            Quest quest = Quest.getInstance(questid);
            boolean ok = quest != null && quest.getNpcRequirement(true) != -1
                    ? api.forceCompleteQuest(questid, quest.getNpcRequirement(true))
                    : api.forceCompleteQuest(questid);
            ctx.done((ok ? "已完成 " : "完成失败: ") + c.getName() + " 任务 " + questid);
        });

        // ---------------------------------------------------------------- resetquest
        r.add("resetquest", "<角色名> <任务id>", "把这个角色的某个任务进度清掉", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: resetquest <角色名> <任务id>");
                return;
            }
            Character c = ctx.require(args[0]);
            Integer questid = ctx.num(args, 1, "任务id");
            if (c == null || questid == null) {
                return;
            }
            if (c.getQuestStatus(questid) == 0) {
                ctx.done(c.getName() + " 本来就没有任务 " + questid + " 的进度");
                return;
            }
            Quest quest = Quest.getInstance(questid);
            if (quest == null) {
                ctx.fail("任务 " + questid + " 不存在");
                return;
            }
            quest.reset(c);
            ctx.done("已清掉 " + c.getName() + " 的任务 " + questid + " 进度");
        });

        // ---------------------------------------------------------------- timer
        r.add("timer", "<角色名> <秒|remove>", "给这个角色挂一个倒计时钟，remove 是撤掉", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: timer <角色名> <秒|remove>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Packet clock = clockPacket(ctx, args[1]);
            if (clock == null) {
                return;
            }
            c.sendPacket(clock);
            ctx.done(c.getName() + " 的计时器已设为 " + args[1]);
        });

        // ---------------------------------------------------------------- timermap
        r.add("timermap", "<地图id> <秒|remove>", "给这张地图上所有人挂倒计时钟，remove 是撤掉", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: timermap <地图id> <秒|remove>");
                return;
            }
            Integer mapid = ctx.num(args, 0, "地图id");
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            Packet clock = clockPacket(ctx, args[1]);
            if (map == null || clock == null) {
                return;
            }
            int n = 0;
            for (Character c : map.getCharacters()) {
                c.sendPacket(clock);
                n++;
            }
            ctx.done("地图 " + mapid + " 上 " + n + " 人的计时器已设为 " + args[1]);
        });

        // ---------------------------------------------------------------- timerall
        r.add("timerall", "<秒|remove> [世界id]", "给某个世界（不给就是所有世界）所有人挂倒计时钟", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: timerall <秒|remove> [世界id]");
                return;
            }
            Packet clock = clockPacket(ctx, args[0]);
            if (clock == null) {
                return;
            }
            int world = -1;
            if (args.length > 1) {
                Integer w = ctx.num(args, 1, "世界id");
                if (w == null) {
                    return;
                }
                world = w;
            }
            int n = 0;
            for (Channel ch : ctx.channels()) {
                if (world >= 0 && ch.getWorld() != world) {
                    continue;
                }
                for (Character c : ch.getPlayerStorage().getAllCharacters()) {
                    c.sendPacket(clock);
                    n++;
                }
            }
            ctx.done((world < 0 ? "所有世界" : "世界 " + world) + " 共 " + n + " 人的计时器已设为 " + args[0]);
        });

        // ---------------------------------------------------------------- warpmap
        r.add("warpmap", "<源地图id> <目标地图id>", "把一张地图上的所有玩家传到另一张地图（只在同一频道内搬运）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warpmap <源地图id> <目标地图id>");
                return;
            }
            Integer from = ctx.num(args, 0, "源地图id");
            Integer to = ctx.num(args, 1, "目标地图id");
            if (from == null || to == null) {
                return;
            }
            MapleMap source = ctx.requireMap(from);
            MapleMap target = ctx.requireMap(to);
            if (source == null || target == null) {
                return;
            }
            Portal pto = target.getRandomPlayerSpawnpoint();
            if (pto == null) {
                ctx.fail("目标地图 " + to + " 没有玩家出生点，传送不过去");
                return;
            }
            int n = warpAll(ctx, source, target, pto, null);
            ctx.done("地图 " + from + " 上 " + n + " 人已传到 " + to + " " + safeName(target));
        });

        // ---------------------------------------------------------------- warparea
        r.add("warparea", "<角色名> <目标地图id> [半径=50000]", "把和这个角色同一地图、且离他不远的玩家传到目标地图", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warparea <角色名> <目标地图id> [半径]");
                return;
            }
            Character anchor = ctx.require(args[0]);
            Integer to = ctx.num(args, 1, "目标地图id");
            if (anchor == null || to == null) {
                return;
            }
            MapleMap source = ctx.mapOf(anchor);
            MapleMap target = ctx.requireMap(to);
            if (source == null || target == null) {
                return;
            }
            Portal pto = target.getRandomPlayerSpawnpoint();
            if (pto == null) {
                ctx.fail("目标地图 " + to + " 没有玩家出生点，传送不过去");
                return;
            }
            double radius = 50000;
            if (args.length > 2) {
                Integer rad = ctx.num(args, 2, "半径");
                if (rad == null) {
                    return;
                }
                radius = rad;
            }
            final Point pos = anchor.getPosition();
            final double maxDist = radius;
            int n = warpAll(ctx, source, target, pto, chr -> {
                Point p = chr.getPosition();
                if (p == null || pos == null) {
                    return true;
                }
                double dx = pos.x - p.x;
                double dy = pos.y - p.y;
                return Math.sqrt(dx * dx + dy * dy) <= maxDist;
            });
            ctx.done("已把 " + anchor.getName() + " 周围 " + (int) maxDist + " 内的 " + n + " 人传到 " + to + " " + safeName(target));
        });
    }

    // ------------------------------------------------------------------ helpers

    private interface WarpFilter {
        boolean accept(Character chr);
    }

    private static int warpAll(GmContext ctx, MapleMap source, MapleMap target, Portal pto, WarpFilter filter) {
        int n = 0;
        for (Character chr : new ArrayList<>(source.getAllPlayers())) {
            if (filter != null && !filter.accept(chr)) {
                continue;
            }
            chr.saveLocationOnWarp();
            chr.changeMap(target, pto);
            n++;
        }
        return n;
    }

    private static Integer parseMapId(GmContext ctx, String[] args) {
        if (GmContext.missing(args, 1)) {
            ctx.println("缺少地图id 参数");
            return null;
        }
        return ctx.num(args, 0, "地图id");
    }

    private static Pair<Integer, String> parseMapAndName(GmContext ctx, String[] args) {
        if (GmContext.missing(args, 2)) {
            ctx.println("需要两个参数: 地图id 和 传送点名");
            return null;
        }
        Integer mapid = ctx.num(args, 0, "地图id");
        if (mapid == null) {
            return null;
        }
        return new Pair<>(mapid, ctx.rest(args, 1));
    }

    private static Packet clockPacket(GmContext ctx, String value) {
        if (value.equalsIgnoreCase("remove")) {
            return PacketCreator.removeClock();
        }
        Integer seconds = ctx.num(value, "秒数");
        if (seconds == null) {
            return null;
        }
        return PacketCreator.getClock(seconds);
    }

    private static List<Channel> channelsInWorld(GmContext ctx, String[] args) {
        if (GmContext.missing(args, 1)) {
            return ctx.channels();
        }
        Integer world = ctx.num(args, 0, "世界id");
        if (world == null) {
            return null;
        }
        if (ctx.requireWorld(world) == null) {
            return null;
        }
        List<Channel> out = new ArrayList<>();
        for (Channel ch : ctx.channels()) {
            if (ch.getWorld() == world) {
                out.add(ch);
            }
        }
        return out;
    }

    private static void broadcastAllWorlds(GmContext ctx, Packet packet) {
        for (net.server.world.World w : ctx.worlds()) {
            Server.getInstance().broadcastMessage(w.getId(), packet);
        }
    }

    private static void changeLook(GmContext ctx, String[] args, String kind, String what) {
        if (GmContext.missing(args, 2)) {
            ctx.println("用法: " + kind + " <角色名> <" + what + "id>");
            return;
        }
        Character v = ctx.require(args[0]);
        Integer itemId = ctx.num(args, 1, what + "id");
        if (v == null || itemId == null) {
            return;
        }
        boolean valid = "face".equals(kind) ? ItemConstants.isFace(itemId) : ItemConstants.isHair(itemId);
        if (!valid || ItemInformationProvider.getInstance().getName(itemId) == null) {
            ctx.fail(what + " id " + itemId + " 不存在");
            return;
        }
        if ("face".equals(kind)) {
            v.setFace(itemId);
            v.updateSingleStat(Stat.FACE, itemId);
        } else {
            v.setHair(itemId);
            v.updateSingleStat(Stat.HAIR, itemId);
        }
        v.equipChanged();
        ctx.done(v.getName() + " 的" + what + "已换成 " + itemId);
    }

    private static Pair<Disease, MobSkill> resolveDebuff(String key) {
        Disease disease;
        MobSkillType type;
        int level;
        switch (key.toUpperCase()) {
            case "SLOW":
                disease = Disease.SLOW;
                type = MobSkillType.SLOW;
                level = 7;
                break;
            case "SEDUCE":
                disease = Disease.SEDUCE;
                type = MobSkillType.SEDUCE;
                level = 7;
                break;
            case "ZOMBIFY":
                disease = Disease.ZOMBIFY;
                type = MobSkillType.UNDEAD;
                level = 1;
                break;
            case "CONFUSE":
                disease = Disease.CONFUSE;
                type = MobSkillType.REVERSE_INPUT;
                level = 2;
                break;
            case "STUN":
                disease = Disease.STUN;
                type = MobSkillType.STUN;
                level = 7;
                break;
            case "POISON":
                disease = Disease.POISON;
                type = MobSkillType.POISON;
                level = 5;
                break;
            case "SEAL":
                disease = Disease.SEAL;
                type = MobSkillType.SEAL;
                level = 1;
                break;
            case "DARKNESS":
                disease = Disease.DARKNESS;
                type = MobSkillType.DARKNESS;
                level = 1;
                break;
            case "WEAKEN":
                disease = Disease.WEAKEN;
                type = MobSkillType.WEAKNESS;
                level = 1;
                break;
            case "CURSE":
                disease = Disease.CURSE;
                type = MobSkillType.CURSE;
                level = 1;
                break;
            default:
                return null;
        }
        MobSkill skill = MobSkillFactory.getMobSkill(type, level).orElse(null);
        if (skill == null) {
            return null;
        }
        return new Pair<>(disease, skill);
    }

    // MapleMap.startEvent(chr) only really needs a character for the fitness / ola / snowball games.
    private static boolean needsAnchorPlayer(int mapid) {
        if (mapid == MapId.EVENT_PHYSICAL_FITNESS || mapid == MapId.EVENT_SNOWBALL) {
            return true;
        }
        return mapid == MapId.EVENT_OLA_OLA_0 || mapid == MapId.EVENT_OLA_OLA_1
                || mapid == MapId.EVENT_OLA_OLA_2 || mapid == MapId.EVENT_OLA_OLA_3
                || mapid == MapId.EVENT_OLA_OLA_4;
    }

    private static void printSongs(GmContext ctx) {
        ctx.println("可用歌曲（music <地图id> <歌名>）:");
        for (String s : GameConstants.GAME_SONGS) {
            ctx.println("  " + s);
        }
    }

    // Same convention as the console's built-in spawn: stand on the first player of the map,
    // otherwise fall back to the map origin.
    private static Point spawnPosition(MapleMap map) {
        for (Character c : map.getCharacters()) {
            Point p = c.getPosition();
            if (p != null) {
                return new Point(p.x, p.y);
            }
        }
        return new Point(0, 0);
    }

    private static String safeNameById(int chrId) {
        try {
            String n = Character.getNameById(chrId);
            return n == null ? "(已删除角色)" : n;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String itemName(int itemId) {
        try {
            String n = ItemInformationProvider.getInstance().getName(itemId);
            return n == null ? "?" : n;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String safeName(MapleMap map) {
        try {
            String n = map.getMapName();
            return n == null ? "" : n;
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] toBytes(String hex) {
        return HexTool.toBytes(hex);
    }
}
