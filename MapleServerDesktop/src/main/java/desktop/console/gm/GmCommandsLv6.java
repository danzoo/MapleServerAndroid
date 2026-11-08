package desktop.console.gm;

import client.Character;
import client.Client;
import config.YamlConfig;
import net.server.Server;
import net.server.world.World;
import server.TimerManager;
import server.life.PlayerNPC;
import server.quest.Quest;
import tools.PacketCreator;

import java.net.InetAddress;
import java.util.List;

// Console ports of server-core/client/command/commands/gm6 (rank 6 / highest GM).
//
// These commands are admin / plumbing: most have NO player subject at all and act server-wide, so
// they read the current world / channel layout from ctx.worlds() / ctx.world(id) and print a
// before/after summary instead of relying on an acting GM. Where the in-game version moved the
// typing player (setgmlevel / getacc / warpworld), the console version names the target character
// explicitly. The destructive ones (dcall / removechannel / removeworld / eraseallpnpcs) print the
// full blast radius to stdout BEFORE acting - the console is a scriptable stdin loop, so there is
// no interactive "are you sure" read that would stall the dispatch loop.
//
// Note: the gm6 folder also holds DevtestCommand, but CommandsExecutor.registerLv6Commands() does
// not register a "devtest" name, so it is not part of this rank and is intentionally not ported.
public final class GmCommandsLv6 {

    private GmCommandsLv6() {
    }

    public static void register(GmRegistry r) {

        // was: "!setgmlevel <playername> <newlevel>" -> acted via the typing GM's channel storage
        r.add("setgmlevel", "<角色名> <等级>", "把某个在线角色的 GM 等级设为指定值（0=普通玩家，6=最高）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: setgmlevel <角色名> <等级>");
                return;
            }
            Integer lvl = ctx.num(args, 1, "等级");
            if (lvl == null) {
                return;
            }
            Character target = ctx.require(args[0]);
            if (target == null) {
                return;
            }
            target.setGMLevel(lvl);
            Client cl = target.getClient();
            if (cl != null) {
                cl.setGMLevel(lvl);
            }
            if (cl != null) {
                target.dropMessage("You are now a level " + lvl + " GM. See @commands for a list of available commands.");
            }
            ctx.done(target.getName() + " 的 GM 等级已设为 " + lvl + (cl == null ? "（该角色连接已断开，仅改了内存值）" : ""));
        });

        // was: "!warpworld <worldid>" -> moved the TYPING player to another world (kept channel).
        // Console has no typing player, so the subject becomes an explicit online character name.
        // The original performs a single per-player world transition; we preserve its exact call
        // sequence and make sure one failure prints and returns instead of throwing.
        r.add("warpworld", "<角色名> <世界id>", "把某个在线角色切换到另一个世界（保持频道不变，向客户端下发换线封包）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warpworld <角色名> <世界id>");
                return;
            }
            Integer worldid = ctx.num(args, 1, "世界id");
            if (worldid == null) {
                return;
            }
            Server server = Server.getInstance();
            if (worldid < 0 || worldid >= server.getWorldsSize() || ctx.world(worldid) == null) {
                ctx.fail("世界 " + worldid + " 不存在（现有: " + ctx.worldIds() + "）");
                return;
            }
            Character target = ctx.require(args[0]);
            if (target == null) {
                return;
            }
            Client c = target.getClient();
            if (c == null) {
                ctx.fail(target.getName() + " 已经掉线，没有可用的连接");
                return;
            }
            try {
                String[] socket = server.getInetSocket(c, worldid, c.getChannel());
                if (socket == null || socket.length < 2) {
                    ctx.fail("无法为目标世界 " + worldid + " 解析接入地址，切换中止");
                    return;
                }
                World from = c.getWorldServer();
                if (from != null) {
                    from.removePlayer(target);
                }
                if (target.getMap() != null) {
                    target.getMap().removePlayer(target);
                }
                target.setSessionTransitionState();
                target.setWorld(worldid);
                target.saveCharToDB();
                c.sendPacket(PacketCreator.getChannelChange(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1])));
                ctx.done(target.getName() + " 已切换到世界 " + worldid + "（频道 " + c.getChannel() + " 不变）");
            } catch (Exception e) {
                ctx.fail("切换世界时出错（目标世界频道数是否与来源世界一致？）: " + e);
            }
        });

        // was: "!saveall" -> saved every character in every world, then GM-broadcast
        r.add("saveall", "", "立刻把全服所有在线角色存档到数据库", (ctx, args) -> {
            List<Character> online = ctx.allOnline();
            int saved = 0;
            int failed = 0;
            for (Character chr : online) {
                try {
                    chr.saveCharToDB();
                    saved++;
                } catch (Exception e) {
                    failed++;
                    ctx.fail("存档失败 " + chr.getName() + ": " + e);
                }
            }
            ctx.done("世界 [" + ctx.worldIds() + "]，在线 " + online.size()
                    + " 人：已保存 " + saved + " 个角色" + (failed > 0 ? "，失败 " + failed + " 个" : ""));
        });

        // was: "!dcall" -> disconnected every non-GM across all worlds.
        // Destructive: print the exact blast radius BEFORE acting; no interactive confirm.
        r.add("dcall", "", "断开全服所有非 GM 玩家（含已登录但不在游戏中的）。GM 不受影响", (ctx, args) -> {
            List<Character> online = ctx.allOnline();
            int victims = 0;
            for (Character chr : online) {
                if (!chr.isGM()) {
                    victims++;
                }
            }
            ctx.println("即将断开全服所有非 GM 玩家。");
            ctx.println("  当前世界: [" + ctx.worldIds() + "]，在线 " + online.size() + " 人，其中非 GM " + victims + " 人（GM " + (online.size() - victims) + " 人保留）");
            if (victims == 0) {
                ctx.done("没有需要断开的非 GM 玩家");
                return;
            }
            int dc = 0;
            for (Character chr : online) {
                if (chr.isGM()) {
                    continue;
                }
                try {
                    Client cl = chr.getClient();
                    if (cl != null) {
                        cl.disconnect(false, false);
                    }
                    dc++;
                } catch (Exception e) {
                    ctx.fail("断开失败 " + chr.getName() + ": " + e);
                }
            }
            ctx.done("已断开 " + dc + " 个非 GM 玩家");
        });

        // was: "!mapplayers" -> listed everyone on the typing GM's current map
        r.add("mapplayers", "<地图id>", "列出这张地图上的所有玩家（名字 + 当前HP/最大HP），跨所有世界频道", (ctx, args) -> {
            Integer mapid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "地图id");
            if (mapid == null) {
                ctx.println("用法: mapplayers <地图id>");
                return;
            }
            int shown = 0;
            for (Character chr : ctx.allOnline()) {
                if (chr.getMapId() == mapid) {
                    ctx.printf("  %s: %d/%d%n", chr.getName(), chr.getHp(), chr.getCurrentMaxHp());
                    shown++;
                }
            }
            if (shown == 0) {
                ctx.done("地图 " + mapid + " 上没有玩家");
            } else {
                ctx.done("地图 " + mapid + " 上共 " + shown + " 名玩家");
            }
        });

        // was: "!getacc <playername>" -> resolved the victim in the typing GM's world
        r.add("getacc", "<角色名>", "显示这个在线角色所属的账号名", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: getacc <角色名>");
                return;
            }
            Character victim = ctx.require(args[0]);
            if (victim == null) {
                return;
            }
            Client cl = victim.getClient();
            if (cl == null) {
                ctx.fail(victim.getName() + " 已经掉线，无法读取账号名");
                return;
            }
            ctx.println(victim.getName() + " 的账号名是: " + cl.getAccountName());
        });

        // was: "!shutdown [<分钟>|NOW]" -> schedules the core's own graceful shutdown.
        // Reuse Server.getInstance().shutdown(false), the same Runnable ConsoleManager's
        // shutdownServer() runs; nothing here calls System.exit / halt.
        r.add("shutdown", "[<分钟>|NOW]", "延时关闭服务器；参数是分钟数，NOW 表示立即。会先向全服玩家发维护倒计时提示", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: shutdown <分钟|NOW>");
                return;
            }
            long time = 60000L;
            if ("now".equalsIgnoreCase(args[0])) {
                time = 1;
            } else {
                Integer mins = ctx.num(args[0], "分钟");
                if (mins == null) {
                    return;
                }
                time = 60000L * mins;
            }

            if (time > 1) {
                long secs = (time / 1000) % 60;
                long minutes = (time / 60000) % 60;
                long hours = (time / 3600000) % 24;
                long days = (time / 86400000);
                StringBuilder str = new StringBuilder();
                if (days > 0) {
                    str.append(days).append(" days, ");
                }
                if (hours > 0) {
                    str.append(hours).append(" hours, ");
                }
                str.append(minutes).append(" minutes, ").append(secs).append(" seconds");

                String warn = "Server is undergoing maintenance process, and will be shutdown in " + str + ". Prepare yourself to quit safely in the mean time.";
                int warned = 0;
                for (Character chr : ctx.allOnline()) {
                    try {
                        chr.dropMessage(warn);
                        warned++;
                    } catch (Exception e) {
                        ctx.fail("通知失败 " + chr.getName() + ": " + e);
                    }
                }
                ctx.done("已向 " + warned + " 名玩家发送维护倒计时，将在 " + str + " 后关闭服务器");
            } else {
                ctx.done("立即关闭服务器（NOW）");
            }
            // identical to the in-game command and to ConsoleManager's graceful path
            TimerManager.getInstance().schedule(Server.getInstance().shutdown(false), time);
        });

        // was: "!clearquestcache" -> Quest.clearCache() (whole cache)
        r.add("clearquestcache", "", "清空全部任务缓存（所有任务的内存状态）", (ctx, args) -> {
            Quest.clearCache();
            ctx.done("已清空全部任务缓存");
        });

        // was: "!clearquest <questid>" -> Quest.clearCache(questid)
        r.add("clearquest", "<任务id>", "清空指定任务的缓存", (ctx, args) -> {
            Integer questid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "任务id");
            if (questid == null) {
                ctx.println("用法: clearquest <任务id>");
                return;
            }
            Quest.clearCache(questid);
            ctx.done("已清空任务 " + questid + " 的缓存");
        });

        // was: "!supplyratecoupon <yes|no>" -> toggled a global YamlConfig flag (server-wide, no subject)
        r.add("supplyratecoupon", "<yes|no>", "开关现金商店里的倍率优惠券是否可购买（全服生效）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: supplyratecoupon <yes|no>");
                return;
            }
            YamlConfig.config.server.USE_SUPPLY_RATE_COUPONS = args[0].compareToIgnoreCase("no") != 0;
            ctx.println("倍率优惠券在现金商店的售卖现在已"
                    + (YamlConfig.config.server.USE_SUPPLY_RATE_COUPONS ? "开启" : "关闭"));
        });

        // was: "!spawnallpnpcs" -> spawned all players' PNPCs on the typing GM's map in the GM's world
        r.add("spawnallpnpcs", "<地图id> [世界id]", "把该世界所有玩家的角色 NPC 摆放（召唤）到指定地图；世界id 缺省时自动定位该地图所在频道", (ctx, args) -> {
            Integer mapid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "地图id");
            if (mapid == null) {
                ctx.println("用法: spawnallpnpcs <地图id> [世界id]");
                return;
            }
            int world;
            if (args.length >= 2) {
                Integer w = ctx.num(args, 1, "世界id");
                if (w == null) {
                    return;
                }
                world = w;
            } else {
                net.server.channel.Channel ch = ctx.channelFor(mapid);
                if (ch == null) {
                    ctx.fail("没有任何频道，无法定位地图 " + mapid + " 所在世界，请显式指定世界id");
                    return;
                }
                world = ch.getWorld();
            }
            if (ctx.requireWorld(world) == null) {
                return;
            }
            PlayerNPC.multicastSpawnPlayerNPC(mapid, world);
            ctx.done("世界 " + world + " 的所有玩家角色 NPC 已摆放到地图 " + mapid);
        });

        // was: "!eraseallpnpcs" -> removeAllPlayerNPC() clears every player NPC on every map/world.
        // Destructive & global (original has no id concept): print the blast radius, then act.
        r.add("eraseallpnpcs", "", "移除全服所有地图上的角色 NPC（影响所有世界所有频道，不可逆）。会先打印影响范围再执行", (ctx, args) -> {
            ctx.println("即将移除全服所有角色 NPC（每个世界、每张地图）。");
            ctx.println("  受影响世界: [" + ctx.worldIds() + "]，频道总数: " + ctx.channels().size());
            PlayerNPC.removeAllPlayerNPC();
            ctx.done("已移除全服所有角色 NPC");
        });

        // was: "@addchannel <worldid>" -> async ThreadManager; console runs it synchronously and
        // prints the channel count before/after.
        r.add("addchannel", "<世界id>", "给指定世界新增一个频道", (ctx, args) -> {
            Integer worldid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "世界id");
            if (worldid == null) {
                ctx.println("用法: addchannel <世界id>");
                return;
            }
            World world = ctx.requireWorld(worldid);
            if (world == null) {
                ctx.println("  addchannel 需要该世界已存在才能新增频道");
                return;
            }
            int before = world.getChannelsSize();
            int chid = Server.getInstance().addChannel(worldid);
            if (chid >= 0) {
                World w2 = ctx.world(worldid);
                int after = w2 == null ? before : w2.getChannelsSize();
                ctx.done("世界 " + worldid + " 新增频道 " + chid + "（频道数 " + before + " -> " + after + "）");
            } else {
                ctx.fail("频道创建失败，返回码 " + chid + channelFailReason(worldid, chid)
                        + "（世界 " + worldid + " 频道数仍为 " + (ctx.world(worldid) == null ? "?" : ctx.world(worldid).getChannelsSize()) + "）");
            }
        });

        // was: "@addworld" -> async ThreadManager; no id needed (the new world id is chosen by the server)
        r.add("addworld", "", "新增一个世界（id 由服务端自动分配为下一个）", (ctx, args) -> {
            String before = ctx.worldIds();
            int wid = Server.getInstance().addWorld();
            if (wid >= 0) {
                ctx.done("新增世界 " + wid + "（世界列表: " + before + " -> " + ctx.worldIds() + "）");
            } else {
                ctx.fail("世界创建失败，返回码 " + wid + "："
                        + (wid == -2 ? "加载 world.ini 出错" : "端口被占用或已达世界数量上限")
                        + "（现有世界: " + ctx.worldIds() + "）");
            }
        });

        // was: "@removechannel <worldid>" -> removed the LAST channel of that world.
        // Destructive: require explicit worldid (no implicit current) and print what was removed.
        r.add("removechannel", "<世界id>", "移除指定世界的最后一个频道。会先打印影响范围再执行", (ctx, args) -> {
            Integer worldid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "世界id");
            if (worldid == null) {
                ctx.println("用法: removechannel <世界id>");
                return;
            }
            World world = ctx.requireWorld(worldid);
            if (world == null) {
                return;
            }
            int before = world.getChannelsSize();
            int players = 0;
            for (net.server.channel.Channel ch : ctx.channels()) {
                if (ch.getWorld() == worldid) {
                    players += ch.getPlayerStorage().getAllCharacters().size();
                }
            }
            ctx.println("即将移除世界 " + worldid + " 的最后一个频道。");
            ctx.println("  该世界现有频道数: " + before + "，该世界在线人数: " + players + "（有玩家在线时移除会失败）");
            if (Server.getInstance().removeChannel(worldid)) {
                World w2 = ctx.world(worldid);
                int after = w2 == null ? 0 : w2.getChannelsSize();
                ctx.done("已移除世界 " + worldid + " 的最后一个频道（频道数 " + before + " -> " + after + "）");
            } else {
                ctx.fail("移除世界 " + worldid + " 的最后一个频道失败：该世界不存在或仍有玩家在线");
            }
        });

        // was: "@removeworld" -> removed the LAST world implicitly (no argument).
        // Destructive: require explicit worldid; only the highest (last) world can actually be removed.
        r.add("removeworld", "<世界id>", "移除一个世界（服务端只能移除编号最大的那个世界，必须显式给出并匹配）。会先打印影响范围再执行", (ctx, args) -> {
            Integer worldid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "世界id");
            if (worldid == null) {
                ctx.println("用法: removeworld <世界id>");
                return;
            }
            int last = Server.getInstance().getWorldsSize() - 1;
            if (worldid < 0 || worldid > last) {
                ctx.fail("世界 " + worldid + " 不存在（现有: " + ctx.worldIds() + "）");
                return;
            }
            if (worldid != last) {
                ctx.fail("只能移除编号最大的世界：请求 " + worldid + "，但当前最大世界是 " + last);
                return;
            }
            if (last <= 0) {
                ctx.fail("无法移除世界 0");
                return;
            }
            World world = ctx.world(last);
            int players = 0;
            for (net.server.channel.Channel ch : ctx.channels()) {
                if (ch.getWorld() == last) {
                    players += ch.getPlayerStorage().getAllCharacters().size();
                }
            }
            ctx.println("即将移除世界 " + last + "。");
            ctx.println("  当前世界列表: [" + ctx.worldIds() + "]，该世界频道数: "
                    + (world == null ? "?" : world.getChannelsSize()) + "，在线人数: " + players + "（有玩家在线时移除会失败）");
            if (Server.getInstance().removeWorld()) {
                ctx.done("已移除世界 " + last + "（世界列表: " + ctx.worldIds() + "，剩余世界数: " + Server.getInstance().getWorldsSize() + "）");
            } else {
                ctx.fail("移除世界 " + last + " 失败：该世界上仍有玩家在线");
            }
        });
    }

    private static String channelFailReason(int worldid, int code) {
        if (code == -3) {
            return "：世界id 无效（世界 " + worldid + " 不存在）";
        } else if (code == -2) {
            return "：世界 " + worldid + " 已达频道上限";
        } else if (code == -1) {
            return "：加载 world.ini 出错";
        }
        return "：端口被占用或存在其他限制";
    }
}
