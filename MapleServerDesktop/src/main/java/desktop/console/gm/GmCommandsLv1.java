package desktop.console.gm;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import server.ItemInformationProvider;
import server.StatEffect;
import server.life.Monster;
import server.life.MonsterDropEntry;
import server.life.MonsterInformationProvider;
import server.maps.MapFactory;
import server.maps.MapleMap;
import server.maps.Portal;
import tools.DatabaseConnection;
import tools.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Console ports of server-core/client/command/commands/gm1.
//
// This file is the style reference for the other groups:
//   - a player-scoped in-game command becomes "<cmd> <角色名> ..." (the operator is gone, so the
//     subject has to be named); a map-scoped one becomes "<cmd> <地图id> ...".
//   - every read-out that in-game pushed into an NPC talk window is printed to stdout instead.
//   - failures use ctx.fail()/ctx.needArgs() and return early; nothing here relies on exceptions.
public final class GmCommandsLv1 {

    private GmCommandsLv1() {
    }

    public static void register(GmRegistry r) {
        // was: "@whatdropsfrom <monster name>" -> NPC talk window
        r.add("whatdropsfrom", "<怪物名> [世界id=0]", "列出这只怪掉落哪些道具（按该世界的掉落倍率换算成 1/N）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: whatdropsfrom <怪物名> [世界id]");
                return;
            }
            String name = ctx.rest(args, 0);
            int world = 0;
            if (args.length > 1) {
                Integer w = ctx.num(args, args.length - 1, "世界id");
                if (w == null) {
                    return;
                }
                world = w;
                name = ctx.rest(args, 0, args.length - 1);
            }
            ArrayList<Pair<Integer, String>> mobs = MonsterInformationProvider.getMobsIDsFromName(name);
            if (mobs.isEmpty()) {
                ctx.fail("没有名字匹配 " + name + " 的怪物");
                return;
            }
            int rate = worldDropRate(ctx, world);
            if (rate < 0) {
                return;
            }
            int use = rate == 0 ? 1 : rate;
            int shown = Math.min(3, mobs.size());
            for (int i = 0; i < shown; i++) {
                Pair<Integer, String> m = mobs.get(i);
                int mobId = m.getLeft();
                boolean boss = MonsterInformationProvider.getInstance().isBoss(mobId);
                long divisor = boss ? bossDropRate(ctx, world, use) : use;
                ctx.printf("%n%s (%d) 的掉落：%n", m.getRight(), mobId);
                for (MonsterDropEntry drop : MonsterInformationProvider.getInstance().retrieveDrop(mobId)) {
                    String iname = ItemInformationProvider.getInstance().getName(drop.itemId);
                    if (iname == null || "null".equals(iname) || drop.chance == 0) {
                        continue;
                    }
                    long oneIn = Math.max(1000000L / drop.chance / divisor, 1);
                    ctx.printf("  - %s (1/%d)%n", iname, oneIn);
                }
            }
            if (mobs.size() > shown) {
                ctx.println("（同名怪物还有 " + (mobs.size() - shown) + " 只，用完整名字再查）");
            }
        });

        // was: "@whodrops <item name>" -> NPC talk window, 3 items x 50 mobs
        r.add("whodrops", "<道具名>", "列出哪些怪物掉落这个道具", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: whodrops <道具名>");
                return;
            }
            String name = ctx.rest(args, 0);
            ArrayList<Pair<Integer, String>> items = ItemInformationProvider.getInstance().getItemDataByName(name);
            if (items.isEmpty()) {
                ctx.fail("没有名字匹配 " + name + " 的道具");
                return;
            }
            for (int i = 0; i < Math.min(3, items.size()); i++) {
                Pair<Integer, String> it = items.get(i);
                ctx.printf("%n%s (%d) 由以下怪物掉落：%n", it.getRight(), it.getLeft());
                List<String> droppers = new ArrayList<>();
                try (PreparedStatement ps = DatabaseConnection.getConnection()
                        .prepareStatement("SELECT dropperid FROM drop_data WHERE itemid = ? LIMIT 50")) {
                    ps.setInt(1, it.getLeft());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String mobName = MonsterInformationProvider.getInstance().getMobNameFromId(rs.getInt(1));
                            if (mobName != null) {
                                droppers.add(mobName);
                            }
                        }
                    }
                } catch (Exception e) {
                    ctx.fail("读取掉落数据失败: " + e);
                    return;
                }
                if (droppers.isEmpty()) {
                    ctx.println("  （掉落表里没有这个道具）");
                } else {
                    ctx.println("  " + String.join(", ", droppers));
                }
            }
        });

        // was: "@buffme" -> buffs the typing GM
        r.add("buffme", "<角色名>", "给这个角色上 GM 增益（四连祝福 + 血量蓝量补满）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: buffme <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            int[] skills = {4101004, 2311003, 1301007, 2301004, 1005};
            int applied = 0;
            for (int id : skills) {
                Skill sk = SkillFactory.getSkill(id);
                if (sk == null) {
                    continue;
                }
                StatEffect eff = sk.getEffect(sk.getMaxLevel());
                if (eff != null && eff.applyTo(c)) {
                    applied++;
                }
            }
            c.healHpMp();
            ctx.done(c.getName() + ": 上了 " + applied + " 个增益并补满 HP/MP");
        });

        // was: "@goto <area>" -> warps the typing GM; "@goto" alone listed the areas
        r.add("goto", "[<角色名> <地区名|地图id>]", "不带参数时列出所有可跳地区；带参数时把角色传送到那里", (ctx, args) -> {
            if (args.length == 0) {
                printGotoAreas(ctx);
                return;
            }
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: goto <角色名> <地区名|地图id>   （不带参数是列出全部地区）");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            MapleMap target = resolveGotoTarget(ctx, args[1]);
            if (target == null) {
                return;
            }
            Portal pto = target.getRandomPlayerSpawnpoint();
            if (pto == null) {
                ctx.fail("地图 " + target.getId() + " 没有玩家出生点，传送不过去");
                return;
            }
            c.saveLocationOnWarp();
            c.changeMap(target, pto);
            ctx.done(c.getName() + " 已传送到 " + target.getId() + " " + safeName(target));
        });

        // was: "!bosshp" -> the typing GM's current map
        r.add("bosshp", "<地图id>", "列出这张地图上 Boss 的剩余血量百分比", (ctx, args) -> {
            Integer mapid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "地图id");
            if (mapid == null) {
                ctx.println("用法: bosshp <地图id>");
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int shown = 0;
            for (Monster m : map.getAllMonsters()) {
                if (m != null && m.isBoss() && m.getHp() > 0) {
                    long percent = m.getHp() * 100L / m.getMaxHp();
                    ctx.printf("%s (%d) 剩余 %d%%%n", m.getName(), m.getId(), percent);
                    ctx.println("HP: [" + "#".repeat((int) percent) + ".".repeat(100 - (int) percent) + "]");
                    shown++;
                }
            }
            if (shown == 0) {
                ctx.done("地图 " + mapid + " 上没有活着的 Boss");
            }
        });

        // was: "!mobhp" -> the typing GM's current map
        r.add("mobhp", "<地图id>", "列出这张地图上每只怪的具体血量", (ctx, args) -> {
            Integer mapid = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "地图id");
            if (mapid == null) {
                ctx.println("用法: mobhp <地图id>");
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            int shown = 0;
            for (Monster m : map.getAllMonsters()) {
                if (m != null && m.getHp() > 0) {
                    ctx.printf("  %-20s (%-8d) %d / %d%n", m.getName(), m.getId(), m.getHp(), m.getMaxHp());
                    shown++;
                }
            }
            ctx.done("地图 " + mapid + " 上活着 " + shown + " 只怪");
        });
    }

    // In-game the rate came from the typing character; the console has none, so it reads the world
    // rates directly, which is the same number a fresh character (dropRate 10, no coupon) sees.
    private static int worldDropRate(GmContext ctx, int world) {
        net.server.world.World w = ctx.requireWorld(world);
        return w == null ? -1 : w.getDropRate();
    }

    private static int bossDropRate(GmContext ctx, int world, int fallback) {
        net.server.world.World w = ctx.world(world);
        int v = w == null ? 0 : w.getBossDropRate();
        return v <= 0 ? fallback : v;
    }

    private static MapleMap resolveGotoTarget(GmContext ctx, String key) {
        Map<String, Integer> areas = new java.util.HashMap<>(GameConstants.GOTO_AREAS);
        areas.putAll(GameConstants.GOTO_TOWNS);
        Integer mapid = areas.get(key.toLowerCase());
        if (mapid == null) {
            Integer raw = ctx.num(key, "地区名或地图id");
            if (raw == null) {
                ctx.println("（不是数字，也不是下面任何一个地区名：）");
                printGotoAreas(ctx);
                return null;
            }
            mapid = raw;
        }
        MapleMap m = ctx.requireMap(mapid);
        return m == null ? null : m;
    }

    private static void printGotoAreas(GmContext ctx) {
        ctx.println("城镇:");
        printGotoMap(ctx, GameConstants.GOTO_TOWNS);
        ctx.println("地区:");
        printGotoMap(ctx, GameConstants.GOTO_AREAS);
    }

    private static void printGotoMap(GmContext ctx, Map<String, Integer> src) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(src.entrySet());
        entries.sort((a, b) -> a.getValue().compareTo(b.getValue()));
        for (Map.Entry<String, Integer> e : entries) {
            ctx.printf("  %-16s %d  %s%n", e.getKey(), e.getValue(), safeName(e.getValue()));
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

    private static String safeName(int mapid) {
        try {
            String n = MapFactory.loadPlaceName(mapid);
            return n == null ? "" : n;
        } catch (Exception e) {
            return "(无名)";
        }
    }
}
