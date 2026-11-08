package desktop.console.gm;

import client.Character;
import client.Client;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import constants.id.MapId;
import constants.id.NpcId;
import net.server.Server;
import net.server.channel.Channel;
import net.server.coordinator.login.LoginBypassCoordinator;
import net.server.world.World;
import scripting.AbstractScriptManager;
import scripting.npc.NPCScriptManager;
import scripting.quest.QuestScriptManager;
import server.ItemInformationProvider;
import server.events.gm.Event;
import server.gachapon.Gachapon;
import server.maps.FieldLimit;
import server.maps.MapleMap;
import tools.PacketCreator;
import tools.Pair;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

// Console ports of the gm0 command group
// (server-core/client/command/CommandsExecutor.registerLv0Commands + commands/gm0).
//
// The console has no logging-in operator, so every command here is the "player-free" version:
//   - what used to act on the character that typed the message now takes that character's name as
//     its first argument (ctx.require(args[0]));
//   - what used to act on the typing character's current map now takes a map id as its first
//     argument (ctx.num + ctx.requireMap);
//   - world/server wide commands need no subject at all.
// Anything the in-game version pushed into an NPC talk window / hint / dropMessage is printed to
// stdout instead.
//
// Note: "bosshp" and "mobhp" are registered under registerLv0Commands but implemented in commands/gm1
// and already ported in GmCommandsLv1, so they are deliberately NOT registered again here.
public final class GmCommandsLv0 {

    private GmCommandsLv0() {
    }

    public static void register(GmRegistry r) {
        // ------------------------------------------------------------------ help (aliases: commands)
        // was: "@help" -> opened the Steward NPC with the in-game command list.
        // The console already has a built-in `help`, which wins on dispatch; this only mirrors the
        // command name and points at it.
        r.add(new String[]{"help", "commands"}, "[命令名]", "查看命令说明（控制台的内置 help 优先接管；本条只作名称对齐）", (ctx, args) -> {
            ctx.println("控制台的内置 help 会列出全部可用命令（内置命令 + 迁移进来的 GM 命令）。");
            ctx.println("这里迁移的是游戏内 gm0 组的命令：droplimit/time/credits/uptime/gacha/dispose/changel/"
                    + "equiplv/showrates/rates/online/gm/reportbug/points/joinevent/leaveevent/ranks/"
                    + "str/dex/int/luk/enableauth/toggleexp/mylawn/devtest。");
        }, false);

        // ------------------------------------------------------------------ droplimit
        // was: "@droplimit" -> hint about the typing GM's current map.
        r.add("droplimit", "<地图id>", "查看这张地图上的掉落物数量与地图掉落上限", (ctx, args) -> {
            Integer mapid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "地图id");
            if (mapid == null) {
                ctx.println("用法: droplimit <地图id>");
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int dropCount = map.getDroppedItemCount();
            int limit = YamlConfig.config.server.ITEM_LIMIT_ON_MAP;
            ctx.printf("地图 %d 当前掉落物: %d / 上限 %d%n", mapid, dropCount, limit);
            if (((float) dropCount) / limit < 0.75f) {
                ctx.done("掉落数量在安全范围内");
            } else {
                ctx.fail("掉落数量已接近上限（可能触发掉落清理）");
            }
        });

        // ------------------------------------------------------------------ time
        // was: "!time" -> yellowMessage of the server clock.
        r.add("time", "", "显示服务器当前时间", (ctx, args) -> {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            fmt.setTimeZone(TimeZone.getDefault());
            ctx.println("Cosmic Server Time: " + fmt.format(new Date()));
        });

        // ------------------------------------------------------------------ credits (StaffCommand)
        // was: "@credits" -> opened the Heracle NPC "credits" dialogue. There is no data source for
        // this beyond that NPC script, so the console just notes it. See the report.
        r.add("credits", "", "显示制作人员名单（游戏内是 Heracle 的 NPC 对话，控制台仅提示）", (ctx, args) -> {
            ctx.println("credits 在游戏内是一段 NPC 对话脚本，没有可读取的结构化数据。");
            ctx.println("如需查看，请在游戏内使用 @credits。");
        });

        // ------------------------------------------------------------------ uptime
        // was: "!uptime" -> yellowMessage of how long the server has been online.
        r.add("uptime", "", "显示服务器已运行时长", (ctx, args) -> {
            long ms = System.currentTimeMillis() - Server.uptime;
            long seconds = (ms / 1000) % 60;
            long minutes = (ms / 60000) % 60;
            long hours = (ms / 3600000) % 24;
            long days = ms / 86400000;
            ctx.println("Server has been online for " + days + " days " + hours + " hours "
                    + minutes + " minutes and " + seconds + " seconds.");
        });

        // ------------------------------------------------------------------ gacha
        // was: "@gacha <name>" -> searched c.getPlayer().getLastCommandMessage(); listed the gacha's items.
        // The console takes the gacha name as arguments instead (case preserved, matched leniently).
        r.add("gacha", "[扭蛋名]", "不带参数时列出所有扭蛋机；带参数时列出该扭蛋机的奖品", (ctx, args) -> {
            String[] names = {"Henesys", "Ellinia", "Perion", "Kerning City", "Sleepywood", "Mushroom Shrine",
                    "Showa Spa Male", "Showa Spa Female", "New Leaf City", "Nautilus Harbor"};
            int[] ids = {NpcId.GACHAPON_HENESYS, NpcId.GACHAPON_ELLINIA, NpcId.GACHAPON_PERION, NpcId.GACHAPON_KERNING,
                    NpcId.GACHAPON_SLEEPYWOOD, NpcId.GACHAPON_MUSHROOM_SHRINE, NpcId.GACHAPON_SHOWA_MALE,
                    NpcId.GACHAPON_SHOWA_FEMALE, NpcId.GACHAPON_NLC, NpcId.GACHAPON_NAUTILUS};
            if (args.length == 0) {
                ctx.println("可用扭蛋机:");
                for (String n : names) {
                    ctx.println("  - " + n);
                }
                ctx.println("用法: gacha <扭蛋名>");
                return;
            }
            String search = ctx.rest(args, 0);
            Gachapon.GachaponType gacha = null;
            String gachaName = "";
            for (int i = 0; i < names.length; i++) {
                if (search.equalsIgnoreCase(names[i])) {
                    gachaName = names[i];
                    gacha = Gachapon.GachaponType.getByNpcId(ids[i]);
                }
            }
            if (gacha == null) {
                ctx.fail("没有找到名为 " + search + " 的扭蛋机（见 gacha 无参数时的列表）");
                return;
            }
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            ctx.printf("%s 扭蛋机包含以下奖品：%n", gachaName);
            for (int tier = 0; tier < 2; tier++) {
                ctx.println("  [tier " + tier + "]");
                for (int id : gacha.getItems(tier)) {
                    ctx.println("    - " + ii.getName(id));
                }
            }
            ctx.println("（注：所有扭蛋机共通的奖品未在此列出）");
        });

        // ------------------------------------------------------------------ dispose
        // was: "@dispose" -> disposed the typing GM's NPC/quest scripts and re-enabled their actions.
        r.add("dispose", "<角色名>", "清理该角色的 NPC/任务对话状态，让其界面操作恢复可用", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: dispose <角色名>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            NPCScriptManager.getInstance().dispose(cl);
            QuestScriptManager.getInstance().dispose(cl);
            cl.sendPacket(PacketCreator.enableActions());
            cl.removeClickedNPC();
            ctx.done(cl.getPlayer().getName() + ": 已清理对话状态");
        });

        // ------------------------------------------------------------------ changel (ChangeLanguageCommand)
        // was: "!changel <0|1|2>" -> set the typing GM's client language.
        r.add("changel", "<角色名> <0|1|2>", "修改该角色的客户端语言（0=葡语, 1=西语, 2=英语）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: changel <角色名> <0|1|2>   (0=ptb, 1=esp, 2=eng)");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            Integer lang = ctx.num(args, 1, "语言编号");
            if (lang == null) {
                return;
            }
            if (lang < 0 || lang > 2) {
                ctx.fail("语言编号只能是 0(ptb)、1(esp)、2(eng)，收到的是 " + lang);
                return;
            }
            cl.setLanguage(lang);
            ctx.done(cl.getPlayer().getName() + ": 语言已设为 " + lang);
        });

        // ------------------------------------------------------------------ equiplv
        // was: "!equiplv" -> showHint listing the typing GM's equipped items' enhancement levels.
        r.add("equiplv", "<角色名>", "列出该角色身上可穿戴装备的强化等级/经验", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: equiplv <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client cl = c.getClient();
            if (cl == null) {
                ctx.fail(c.getName() + " 已经掉线，无法读取装备信息");
                return;
            }
            ctx.println(c.getName() + " 的装备：");
            int shown = 0;
            for (Item item : c.getInventory(InventoryType.EQUIPPED).list()) {
                if (!(item instanceof Equip)) {
                    continue;
                }
                Equip eq = (Equip) item;
                String line = eq.showEquipFeatures(cl);
                if (line == null || line.isEmpty()) {
                    continue;
                }
                ctx.println("  " + line.replace("\r\n", "").replace("\n", "").trim());
                shown++;
            }
            if (shown == 0) {
                ctx.done("没有可显示的（可强化）装备");
            }
        });

        // ------------------------------------------------------------------ showrates
        // was: "!showrates" -> showHint of both the world rates and the typing character's rates.
        r.add("showrates", "<角色名>", "显示该角色所在世界的倍率以及该角色自身的倍率", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: showrates <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            World w = ctx.world(c.getWorld());
            ctx.println("== EXP ==");
            ctx.println("  世界 EXP: " + rateOrNa(w == null ? 0 : w.getExpRate()));
            ctx.println("  角色 EXP: " + c.getRawExpRate() + "x");
            if (c.getCouponExpRate() != 1) {
                ctx.println("  券 EXP:   " + c.getCouponExpRate() + "x");
            }
            ctx.println("  最终 EXP: " + c.getExpRate() + "x" + (c.hasNoviceExpRate() ? " (新手倍率)" : ""));

            ctx.println("== MESO ==");
            ctx.println("  世界 MESO: " + rateOrNa(w == null ? 0 : w.getMesoRate()));
            ctx.println("  角色 MESO: " + c.getRawMesoRate() + "x");
            if (c.getCouponMesoRate() != 1) {
                ctx.println("  券 MESO:   " + c.getCouponMesoRate() + "x");
            }
            ctx.println("  最终 MESO: " + c.getMesoRate() + "x");

            ctx.println("== DROP ==");
            ctx.println("  世界 DROP: " + rateOrNa(w == null ? 0 : w.getDropRate()));
            ctx.println("  角色 DROP: " + c.getRawDropRate() + "x");
            if (c.getCouponDropRate() != 1) {
                ctx.println("  券 DROP:   " + c.getCouponDropRate() + "x");
            }
            ctx.println("  最终 DROP: " + c.getDropRate() + "x");

            ctx.println("== BOSS DROP ==");
            ctx.println("  世界 BOSS DROP: " + rateOrNa(w == null ? 0 : w.getBossDropRate()));
            ctx.println("  最终 BOSS DROP: " + c.getBossDropRate() + "x");

            if (YamlConfig.config.server.USE_QUEST_RATE) {
                ctx.println("== QUEST ==");
                ctx.println("  世界 QUEST: " + rateOrNa(w == null ? 0 : w.getQuestRate()));
            }
            ctx.println("  世界 TRAVEL: " + rateOrNa(w == null ? 0 : w.getTravelRate()));
            if (w == null) {
                ctx.fail("找不到该角色所在世界 " + c.getWorld() + "，世界倍率显示为 0");
            }
        });

        // ------------------------------------------------------------------ rates
        // was: "!rates" -> showHint of only the typing character's rates.
        r.add("rates", "<角色名>", "显示该角色自身的倍率（EXP/MESO/DROP/BOSS DROP，及开启时的 QUEST）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: rates <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            ctx.println(c.getName() + " 的角色倍率：");
            ctx.println("  EXP:       " + c.getExpRate() + "x" + (c.hasNoviceExpRate() ? " (新手倍率)" : ""));
            ctx.println("  MESO:      " + c.getMesoRate() + "x");
            ctx.println("  DROP:      " + c.getDropRate() + "x");
            ctx.println("  BOSS DROP: " + c.getBossDropRate() + "x");
            if (YamlConfig.config.server.USE_QUEST_RATE) {
                World w = ctx.world(c.getWorld());
                ctx.println("  QUEST:     " + rateOrNa(w == null ? 0 : w.getQuestRate()));
            }
        });

        // ------------------------------------------------------------------ online
        // was: "@online" -> listed non-GM players in the typing GM's world. Built-in console `online`
        // wins on dispatch; this mirrors the name and, having no operator world, lists every channel
        // (optionally narrowed by a world id argument).
        r.add("online", "[世界id]", "列出在线的非 GM 玩家；不带参数时列出所有世界的频道", (ctx, args) -> {
            List<Channel> chs;
            if (args.length >= 1) {
                Integer w = ctx.num(args, 0, "世界id");
                if (w == null) {
                    return;
                }
                chs = Server.getInstance().getChannelsFromWorld(w);
            } else {
                chs = ctx.channels();
            }
            if (chs == null || chs.isEmpty()) {
                ctx.done("没有可列出的频道");
                return;
            }
            for (Channel ch : chs) {
                ctx.println("世界 " + ch.getWorld() + " 频道 " + ch.getId() + ":");
                for (Character chr : ch.getPlayerStorage().getAllCharacters()) {
                    if (chr.isGM()) {
                        continue;
                    }
                    MapleMap m = ctx.mapOf(chr);
                    String mapName = safeMapName(m);
                    ctx.println("    " + Character.makeMapleReadable(chr.getName()) + " 在 " + mapName);
                }
            }
        });

        // ------------------------------------------------------------------ gm
        // was: "@gm <text>" -> broadcast the message to GMs of the typing player's world.
        // No operator/author here, so it broadcasts to every world, attributed to "console".
        r.add("gm", "<消息文本>", "把这条消息以黄字/公告广播给所有在线 GM", (ctx, args) -> {
            String message = ctx.rest(args, 0);
            if (message.length() < 3) {
                ctx.fail("消息太短，未发送（请提供尽量详细的内容）");
                return;
            }
            broadcastToGMs(ctx, "[GM Message]:console: " + message);
            ctx.done("已向所有世界广播 GM 消息");
        });

        // ------------------------------------------------------------------ reportbug
        // was: "@bug/reportbug <text>" -> broadcast a bug report to the GMs of the typing player's world.
        r.add("reportbug", "<缺陷描述>", "把这条缺陷报告以黄字/公告广播给所有在线 GM", (ctx, args) -> {
            String message = ctx.rest(args, 0);
            if (message.isEmpty()) {
                ctx.fail("消息为空，未提交");
                return;
            }
            broadcastToGMs(ctx, "[Bug]:console: " + message);
            ctx.done("已提交缺陷报告并广播给在线 GM");
        });

        // ------------------------------------------------------------------ points (ReadPointsCommand)
        // was: "@points [rp|vp|all]" -> the typing player's reward/vote points.
        r.add("points", "<角色名> [rp|vp|all]", "显示该角色的奖励点(RP)/投票点(VP)", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: points <角色名> [rp|vp|all]");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client cl = c.getClient();
            int rp = c.getRewardPoints();
            Integer vp = cl == null ? null : cl.getVotePoints();
            String what = args.length > 1 ? args[1].toLowerCase() : "all";
            switch (what) {
                case "rp":
                    ctx.println(c.getName() + " RewardPoints: " + rp);
                    break;
                case "vp":
                    ctx.println(c.getName() + " VotePoints: " + (vp == null ? "(角色已掉线)" : vp));
                    break;
                default:
                    ctx.println(c.getName() + " RewardPoints: " + rp + " | VotePoints: "
                            + (vp == null ? "(角色已掉线)" : vp));
                    break;
            }
        });

        // ------------------------------------------------------------------ joinevent
        // was: "@joinevent" -> put the typing player into the active event of their channel.
        r.add("joinevent", "<角色名>", "把该角色传送进当前频道正在进行的 GM 活动", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: joinevent <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client cl = c.getClient();
            MapleMap map = ctx.mapOf(c);
            if (cl == null || map == null) {
                ctx.fail(c.getName() + " 已掉线或正在传送中，无法加入活动");
                return;
            }
            if (FieldLimit.CANNOTMIGRATE.check(map.getFieldLimit())) {
                ctx.fail(c.getName() + " 当前所在地图不允许加入活动");
                return;
            }
            Event event = cl.getChannelServer().getEvent();
            if (event == null) {
                ctx.fail("当前频道没有正在进行的活动");
                return;
            }
            if (event.getMapId() == c.getMapId()) {
                ctx.fail(c.getName() + " 已经在活动地图里了");
                return;
            }
            if (event.getLimit() <= 0) {
                ctx.fail("活动人数已满");
                return;
            }
            c.saveLocation("EVENT");
            if (event.getMapId() == MapId.EVENT_COCONUT_HARVEST || event.getMapId() == MapId.EVENT_SNOWBALL_ENTRANCE) {
                c.setTeam(event.getLimit() % 2);
            }
            event.minusLimit();
            c.saveLocationOnWarp();
            c.changeMap(event.getMapId());
            ctx.done(c.getName() + " 已加入活动地图 " + event.getMapId());
        });

        // ------------------------------------------------------------------ leaveevent
        // was: "@leaveevent" -> send the typing player back from the active event.
        r.add("leaveevent", "<角色名>", "让该角色退出当前活动，回到进入活动前的地图", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: leaveevent <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            int returnMap = c.getSavedLocation("EVENT");
            if (returnMap == -1) {
                ctx.fail(c.getName() + " 当前不在活动中");
                return;
            }
            if (c.getOla() != null) {
                c.getOla().resetTimes();
                c.setOla(null);
            }
            if (c.getFitness() != null) {
                c.getFitness().resetTimes();
                c.setFitness(null);
            }
            c.saveLocationOnWarp();
            c.changeMap(returnMap);
            Client cl = c.getClient();
            if (cl != null && cl.getChannelServer().getEvent() != null) {
                cl.getChannelServer().getEvent().addLimit();
            }
            ctx.done(c.getName() + " 已退出活动，回到地图 " + returnMap);
        });

        // ------------------------------------------------------------------ ranks
        // was: "@ranks" -> sent the typing player's world ranking popup.
        // World-scoped, so the console takes an optional world id (default the first world) and prints.
        r.add("ranks", "[世界id=0]", "列出该世界的玩家排行榜（等级榜）", (ctx, args) -> {
            int world = 0;
            if (args.length >= 1) {
                Integer w = ctx.num(args, 0, "世界id");
                if (w == null) {
                    return;
                }
                world = w;
            }
            if (ctx.world(world) == null) {
                ctx.fail("世界 " + world + " 不存在（现有: " + ctx.worldIds() + "）");
                return;
            }
            List<Pair<String, Integer>> ranking = Server.getInstance().getWorldPlayerRanking(world);
            if (ranking == null || ranking.isEmpty()) {
                ctx.done("世界 " + world + " 没有排行数据");
                return;
            }
            ctx.println("世界 " + world + " 排行榜：");
            int rank = 1;
            for (Pair<String, Integer> e : ranking) {
                ctx.printf("  #%d  %s  (%d)%n", rank++, e.getLeft(), e.getRight());
            }
        });

        // ------------------------------------------------------------------ str / dex / int / luk
        r.add("str", "<角色名> [数量]", "把该角色的可分配点(AP)加到力量 STR；不给数量时按剩余上限分配",
                (ctx, args) -> statCmd(ctx, args, "STR", Character::getStr, Character::assignStr));
        r.add("dex", "<角色名> [数量]", "把该角色的可分配点(AP)加到敏捷 DEX；不给数量时按剩余上限分配",
                (ctx, args) -> statCmd(ctx, args, "DEX", Character::getDex, Character::assignDex));
        r.add("int", "<角色名> [数量]", "把该角色的可分配点(AP)加到智力 INT；不给数量时按剩余上限分配",
                (ctx, args) -> statCmd(ctx, args, "INT", Character::getInt, Character::assignInt));
        r.add("luk", "<角色名> [数量]", "把该角色的可分配点(AP)加到运气 LUK；不给数量时按剩余上限分配",
                (ctx, args) -> statCmd(ctx, args, "LUK", Character::getLuk, Character::assignLuk));

        // ------------------------------------------------------------------ enableauth
        // was: "@enableauth" -> reset the typing player's login-bypass (re-enable PIC).
        r.add("enableauth", "<角色名>", "重置该角色账号的登录豁免，重新启用 PIC 安全码", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: enableauth <角色名>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            if (cl.tryacquireClient()) {
                try {
                    LoginBypassCoordinator.getInstance().unregisterLoginBypassEntry(cl.getHwid(), cl.getAccID());
                    ctx.done(cl.getPlayer().getName() + ": 登录豁免已重置，PIC 重新启用");
                } finally {
                    cl.releaseClient();
                }
            } else {
                ctx.fail("该角色连接正忙（tryacquire 失败），请稍后重试");
            }
        });

        // ------------------------------------------------------------------ toggleexp
        // was: "!toggleexp" -> toggled the server-wide exp-gain switch via the typing player.
        r.add("toggleexp", "<角色名>", "切换该角色的经验获取开关（获得/暂停）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: toggleexp <角色名>");
                return;
            }
            Client cl = ctx.requireClient(args[0]);
            if (cl == null) {
                return;
            }
            if (cl.tryacquireClient()) {
                try {
                    cl.getPlayer().toggleExpGain();
                    ctx.done(cl.getPlayer().getName() + ": 经验获取已切换");
                } finally {
                    cl.releaseClient();
                }
            } else {
                ctx.fail("该角色连接正忙（tryacquire 失败），请稍后重试");
            }
        });

        // ------------------------------------------------------------------ mylawn (MapOwnerClaimCommand)
        // was: "!mylawn" -> the typing player leased ownership of their current map.
        r.add("mylawn", "<角色名>", "让该角色租赁其当前所在地图（草地归属），需开启地图归属系统", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: mylawn <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            if (!YamlConfig.config.server.USE_MAP_OWNERSHIP_SYSTEM) {
                ctx.fail("地图归属系统未开启（Feature unavailable）");
                return;
            }
            if (c.getEventInstance() != null) {
                ctx.fail(c.getName() + " 在活动实例地图里，无法租赁");
                return;
            }
            MapleMap map = ctx.mapOf(c);
            if (map == null) {
                ctx.fail(c.getName() + " 正在传送中，没有当前地图");
                return;
            }
            if (map.countBosses() != 0) {
                ctx.fail("该地图正被 Boss 占据，无法租赁");
                return;
            }
            MapleMap ownedMap = c.getOwnedMap();
            if (ownedMap != null) {
                ownedMap.unclaimOwnership(c);
                if (map == ownedMap) {
                    ctx.done("这块草地已释放，成为无主之地");
                    return;
                }
            }
            if (map.claimOwnership(c)) {
                ctx.done(c.getName() + " 已租赁地图 " + map.getId() + "（离开或闲置 1 分钟后失效）");
            } else {
                ctx.fail("这块草地已被其他玩家租赁");
            }
        });

        // ------------------------------------------------------------------ devtest
        // was: devtest (rank 6, implemented in commands/gm6). Runs devtest.js whose run(chr) is passed
        // a Character, so the console needs a target online character.
        r.add("devtest", "<角色名>", "把该角色传给 devtest.js 的 run() 执行（开发者脚本，不重启调试）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: devtest <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            DevtestScriptManager scriptManager = new DevtestScriptManager();
            ScriptEngine scriptEngine = scriptManager.getInvocableScriptEngine("devtest.js");
            if (scriptEngine == null) {
                ctx.fail("无法加载 devtest.js 脚本引擎");
                return;
            }
            try {
                Invocable invocable = (Invocable) scriptEngine;
                invocable.invokeFunction("run", c);
                ctx.done("devtest.js 已针对 " + c.getName() + " 执行完成");
            } catch (ScriptException | NoSuchMethodException e) {
                ctx.fail("devtest.js 的 run() 抛出异常: " + e);
            }
        });
    }

    // ---------------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface ApAssigner {
        boolean assign(Character c, int value);
    }

    // Shared body for str/dex/int/luk. In-game it acted on the typing GM's own AP; here the subject
    // is args[0] (a named online character) and args[1] is an optional amount.
    private static void statCmd(GmContext ctx, String[] args, String label,
                                java.util.function.ToIntFunction<Character> current, ApAssigner assigner) {
        if (GmContext.missing(args, 1)) {
            ctx.println("用法: " + label.toLowerCase() + " <角色名> [数量]");
            return;
        }
        Character c = ctx.require(args[0]);
        if (c == null) {
            return;
        }
        int remainingAp = c.getRemainingAp();
        int amount;
        if (args.length > 1) {
            Integer n = ctx.num(args, 1, label + " 数量");
            if (n == null) {
                return;
            }
            amount = Math.min(n, remainingAp);
        } else {
            amount = Math.min(remainingAp, YamlConfig.config.server.MAX_AP - current.applyAsInt(c));
        }
        amount = Math.max(amount, 0);
        if (assigner.assign(c, amount)) {
            ctx.done(c.getName() + ": " + label + " +" + amount + "（现为 " + current.applyAsInt(c)
                    + "，剩余 AP " + c.getRemainingAp() + "）");
        } else {
            ctx.fail(c.getName() + ": 分配失败，请确认可分配点(AP)未超过 "
                    + YamlConfig.config.server.MAX_AP + " 且余额充足");
        }
    }

    // The in-game @gm/@bug broadcast only reached the typing player's world; with no operator here,
    // push to every configured world so someone actually sees it in-game.
    private static void broadcastToGMs(GmContext ctx, String message) {
        Server srv = Server.getInstance();
        int worlds = 0;
        for (World w : ctx.worlds()) {
            srv.broadcastGMMessage(w.getId(), PacketCreator.sendYellowTip(message));
            srv.broadcastGMMessage(w.getId(), PacketCreator.serverNotice(1, message));
            worlds++;
        }
        if (worlds == 0) {
            ctx.fail("当前没有任何世界，无法广播（消息内容: " + message + "）");
        } else {
            ctx.println("已向 " + worlds + " 个世界广播: " + message);
        }
    }

    private static String rateOrNa(int rate) {
        return rate + "x";
    }

    private static String safeMapName(MapleMap map) {
        if (map == null) {
            return "(传送中/无地图)";
        }
        try {
            String n = map.getMapName();
            return n == null ? ("#" + map.getId()) : n;
        } catch (Exception e) {
            return "#" + map.getId();
        }
    }

    // Mirrors DevtestCommand's private script manager so we can reach the protected
    // getInvocableScriptEngine(String) without a Client.
    private static final class DevtestScriptManager extends AbstractScriptManager {
        @Override
        public ScriptEngine getInvocableScriptEngine(String path) {
            return super.getInvocableScriptEngine(path);
        }
    }
}
