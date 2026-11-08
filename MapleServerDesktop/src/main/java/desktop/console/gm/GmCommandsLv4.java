package desktop.console.gm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import client.Character;
import client.Client;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.Pet;
import client.inventory.manipulator.InventoryManipulator;
import constants.id.ItemId;
import constants.id.MobId;
import constants.inventory.ItemConstants;
import net.server.channel.Channel;
import net.server.world.World;
import server.ItemInformationProvider;
import server.life.LifeFactory;
import server.life.Monster;
import server.life.NPC;
import server.life.PlayerNPC;
import server.maps.Foothold;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import server.maps.Portal;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

// Console ports of the gm4 command group
// (server-core/client/command/CommandsExecutor.registerLv4Commands + commands/gm4).
//
// Same rules as GmCommandsLv0/GmCommandsLv1:
//   - what used to act on the character that typed the message now takes that character's name;
//   - what used to act on the typing character's current map/position now takes a map id, and the
//     spawn position is picked off that map (a player standing there, else a portal, else origin),
//     always validated so the engine never gets a foothold-less point;
//   - what used to hit "the typing GM's world" now takes an explicit world id;
//   - every dropMessage / yellowMessage of the originals is a stdout line here, and nothing relies
//     on an exception to reject bad input.
//
// Rank-4 has no instance-starting command: zakum/horntail/pinkbean/pap/pianus/cake are plain
// "spawn this boss on my spot" commands in this codebase, so they became map-scoped spawns.
// The plife (permanent life) commands keep the original (world, map) keying, hence the explicit
// world id: the wz map id alone does not tell which world owns the database row.
//
// Two things worth knowing about this group:
//   - the in-game entry point refused anything below GM level 4 (CommandsExecutor.handleInternal).
//     The console dispatch has no level concept at all, so `CommandsExecutor` stays authoritative
//     for the names while `Character.gmLevel()` is deliberately not consulted here - whoever owns
//     the terminal already owns the whole process.
//   - a running server can host more than one world, so every "my world" in the in-game originals
//     became a mandatory <世界id> argument (servermessage and the seven rate commands).
public final class GmCommandsLv4 {

    private GmCommandsLv4() {
    }

    public static void register(GmRegistry r) {
        // ------------------------------------------------------------------ server message

        // was: "@servermessage <text>" -> c.getWorldServer().setServerMessage(player.getLastCommandMessage())
        r.add("servermessage", "<世界id> [文字...]", "设置这个世界滚动公告栏的文字；不带文字时查看各频道当前的公告（文字用 - 表示清空）", (ctx, args) -> {
            Integer worldId = GmContext.missing(args, 1) ? null : ctx.num(args, 0, "世界id");
            if (worldId == null) {
                ctx.println("用法: servermessage <世界id> [文字...]");
                return;
            }
            World w = ctx.requireWorld(worldId);
            if (w == null) {
                return;
            }
            String text = args.length > 1 ? ctx.rest(args, 1) : null;

            if (text == null) {
                ctx.println("世界 " + worldId + " 的各频道公告:");
                for (Channel ch : w.getChannels()) {
                    ctx.printf("  频道 %-3d %s%n", ch.getId(), showEmpty(ch.getServerMessage()));
                }
                return;
            }

            String msg = "-".equals(text) ? "" : text;
            w.setServerMessage(msg);
            ctx.done("世界 " + worldId + " 的滚动公告已设为: " + showEmpty(msg));
        });

        // ------------------------------------------------------------------ item / equip stats

        // was: "!proitem <itemid> <stat> [spdjmp]" -> the typing GM received the item
        r.add("proitem", "<角色名> <道具id|道具名> <属性值> [速度跳跃值]",
                "造一件把全部属性都写成指定数值的装备，直接放进这个角色的背包（原版是给打字的 GM）", (ctx, args) -> {
                    if (GmContext.missing(args, 3)) {
                        ctx.println("用法: proitem <角色名> <道具id|道具名> <属性值> [速度跳跃值]");
                        return;
                    }
                    Character target = ctx.require(args[0]);
                    if (target == null) {
                        return;
                    }
                    Client cl = target.getClient();
                    if (cl == null) {
                        ctx.fail(target.getName() + " 已经掉线，没有可用的连接");
                        return;
                    }
                    Integer itemid = resolveItemId(ctx, args[1]);
                    if (itemid == null) {
                        return;
                    }
                    Integer stat = ctx.num(args, 2, "属性值");
                    if (stat == null) {
                        return;
                    }
                    Integer spdjmp = null;
                    if (args.length >= 4) {
                        spdjmp = ctx.num(args, 3, "速度跳跃值");
                        if (spdjmp == null) {
                            return;
                        }
                    }

                    ItemInformationProvider ii = ItemInformationProvider.getInstance();
                    if (ii.getName(itemid) == null) {
                        ctx.fail("道具 id " + itemid + " 不存在");
                        return;
                    }
                    if (!ItemConstants.getInventoryType(itemid).equals(InventoryType.EQUIP)) {
                        ctx.fail("只有可装备的道具才能写属性，" + itemid + "（" + ii.getName(itemid) + "）不是装备");
                        return;
                    }
                    short s = clampShort(ctx, stat, "属性值");
                    short sp = clampShort(ctx, spdjmp == null ? 0 : spdjmp, "速度跳跃值");

                    Item it = ii.getEquipById(itemid);
                    if (!(it instanceof Equip)) {
                        ctx.fail("道具 " + itemid + " 读不出装备数据，放弃了");
                        return;
                    }
                    hardsetItemStats((Equip) it, s, sp);
                    it.setOwner(target.getName());

                    if (InventoryManipulator.addFromDrop(cl, it)) {
                        ctx.done(target.getName() + " 获得了 " + itemid + " " + ii.getName(itemid)
                                + "（属性 " + s + "，速度/跳跃 " + sp + "，制作者写成 " + target.getName() + "）");
                    } else {
                        ctx.fail(target.getName() + " 的背包放不下 " + itemid + " " + ii.getName(itemid));
                    }
                });

        // was: "!seteqstat <stat> [spdjmp]" -> the typing GM's own equip inventory
        r.add("seteqstat", "<角色名> <属性值> [速度跳跃值]",
                "把这个角色背包里所有装备的属性改写成指定数值（原版改的是打字的 GM 自己）", (ctx, args) -> {
                    if (GmContext.missing(args, 2)) {
                        ctx.println("用法: seteqstat <角色名> <属性值> [速度跳跃值]");
                        return;
                    }
                    Character target = ctx.require(args[0]);
                    if (target == null) {
                        return;
                    }
                    Integer stat = ctx.num(args, 1, "属性值");
                    if (stat == null) {
                        return;
                    }
                    Integer spdjmp = null;
                    if (args.length >= 3) {
                        spdjmp = ctx.num(args, 2, "速度跳跃值");
                        if (spdjmp == null) {
                            return;
                        }
                    }
                    short s = clampShort(ctx, stat, "属性值");
                    short sp = clampShort(ctx, spdjmp == null ? 0 : spdjmp, "速度跳跃值");

                    Inventory equip = target.getInventory(InventoryType.EQUIP);
                    int changed = 0, failed = 0;
                    for (short slot = 1; slot <= equip.getSlotLimit(); slot++) {
                        Item raw = equip.getItem(slot);
                        if (!(raw instanceof Equip)) {
                            continue;
                        }
                        Equip eq = (Equip) raw;
                        try {
                            eq.setWdef(s);
                            eq.setAcc(s);
                            eq.setAvoid(s);
                            eq.setJump(sp);
                            eq.setMatk(s);
                            eq.setMdef(s);
                            eq.setHp(s);
                            eq.setMp(s);
                            eq.setSpeed(sp);
                            eq.setWatk(s);
                            eq.setDex(s);
                            eq.setInt(s);
                            eq.setStr(s);
                            eq.setLuk(s);

                            short flag = eq.getFlag();
                            flag |= ItemConstants.UNTRADEABLE;
                            eq.setFlag(flag);

                            target.forceUpdateItem(eq);
                            changed++;
                        } catch (Exception e) {
                            failed++;
                            ctx.fail("槽位 " + slot + " 的 " + eq.getItemId() + " 改属性失败: " + e);
                        }
                    }
                    ctx.done(target.getName() + " 的装备背包里改了 " + changed + " 件（属性 " + s
                            + "，速度/跳跃 " + sp + "）" + (failed > 0 ? "，失败 " + failed + " 件" : ""));
                });

        // ------------------------------------------------------------------ world rates
        // All of them used to read "the typing GM's world" out of the Client; here the world id is
        // an explicit argument, because a running server can host more than one.

        r.add("exprate", "<世界id> <倍率>", "设置这个世界的经验倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "exprate", "经验", "[Rate] Exp Rate has been changed to %dx.",
                        World::getExpRate, World::setExpRate));

        r.add("mesorate", "<世界id> <倍率>", "设置这个世界的金币倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "mesorate", "金币", "[Rate] Meso Rate has been changed to %dx.",
                        World::getMesoRate, World::setMesoRate));

        r.add("droprate", "<世界id> <倍率>", "设置这个世界的普通掉落倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "droprate", "掉落", "[Rate] Drop Rate has been changed to %dx.",
                        World::getDropRate, World::setDropRate));

        r.add("bossdroprate", "<世界id> <倍率>", "设置这个世界的 Boss 掉落倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "bossdroprate", "Boss 掉落", "[Rate] Boss Drop Rate has been changed to %dx.",
                        World::getBossDropRate, World::setBossDropRate));

        r.add("questrate", "<世界id> <倍率>", "设置这个世界的任务掉落倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "questrate", "任务", "[Rate] Quest Rate has been changed to %dx.",
                        World::getQuestRate, World::setQuestRate));

        r.add("travelrate", "<世界id> <倍率>", "设置这个世界的交通工具速度倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "travelrate", "交通工具", "[Rate] Travel Rate has been changed to %dx.",
                        World::getTravelRate, World::setTravelRate));

        r.add("fishrate", "<世界id> <倍率>", "设置这个世界的钓鱼倍率（并向该世界在线玩家广播一条公告）", (ctx, args) ->
                setWorldRate(ctx, args, "fishrate", "钓鱼", "[Rate] Fishing Rate has been changed to %dx.",
                        World::getFishingRate, World::setFishingRate));

        // ------------------------------------------------------------------ loot vacuum

        // was: "!itemvac" -> the typing GM picked up every drop on their own map
        r.add("itemvac", "<地图id> [领取角色名]",
                "捡起这张地图上的所有掉落物；不给角色名时控制台这边收不了货，就把掉落物原地重新落回地面（不销毁任何东西）", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    if (args.length >= 2) {
                        Character target = ctx.require(args[1]);
                        if (target == null) {
                            return;
                        }
                        MapleMap their = ctx.mapOf(target);
                        if (their == null || their.getId() != map.getId()) {
                            ctx.fail(target.getName() + " 不在地图 " + map.getId() + " 上，捡不了这张图的掉落物");
                            return;
                        }
                        int before = countDrops(their);
                        for (MapObject o : their.getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY,
                                Arrays.asList(MapObjectType.ITEM))) {
                            try {
                                target.pickupItem(o);
                            } catch (Exception e) {
                                ctx.fail("掉落物 " + o.getObjectId() + " 拾取失败: " + e);
                            }
                        }
                        int after = countDrops(their);
                        ctx.done(map.getId() + ": " + target.getName() + " 拿走了 " + (before - after)
                                + " 个掉落物，地上还剩 " + after + " 个（没捡起来的多半是别人的归属物或还在保护期）");
                        return;
                    }

                    ctx.println("没有领取角色的话控制台没有背包可以收货，所以这里只做「落回地面」：把这张图上的掉落物按原位重新掉落一次，掉落时间和归属都会刷新，不会销毁任何东西。");
                    int before = countDrops(map);
                    int[] res = reDropAllDrops(map);
                    int after = countDrops(map);
                    ctx.println("重新落地 " + res[0] + " 个，原样没动 " + res[1] + " 个（重刷需要掉落者还在这张图上）。");
                    ctx.done("地图 " + map.getId() + ": 掉落物数量 " + before + " -> " + after
                            + (before == after ? "，一个都没少" : "，数量变了，检查一下有没有丢东西"));
                });

        // was: "!forcevac" -> same, bypassing ownership / protection / inventory-space checks
        r.add("forcevac", "<地图id> <领取角色名>",
                "无视归属和保护时间，把这张地图上的所有掉落物强塞进这个角色（背包放不下的会留在地上）", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    if (GmContext.missing(args, 2)) {
                        ctx.println("用法: forcevac <地图id> <领取角色名>");
                        return;
                    }
                    Character target = ctx.require(args[1]);
                    if (target == null) {
                        return;
                    }
                    Client cl = target.getClient();
                    if (cl == null) {
                        ctx.fail(target.getName() + " 已经掉线，没有可用的连接");
                        return;
                    }
                    MapleMap their = ctx.mapOf(target);
                    if (their == null || their.getId() != map.getId()) {
                        ctx.fail(target.getName() + " 不在地图 " + map.getId() + " 上，强拾取会写到别的地图，先拒绝了");
                        return;
                    }

                    int picked = 0, left = 0;
                    for (MapObject o : their.getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY,
                            Arrays.asList(MapObjectType.ITEM))) {
                        if (!(o instanceof MapItem)) {
                            continue;
                        }
                        MapItem drop = (MapItem) o;
                        drop.lockItem();
                        try {
                            if (drop.isPickedUp()) {
                                left++;
                                continue;
                            }
                            if (drop.getMeso() > 0) {
                                target.gainMeso(drop.getMeso(), true);
                            } else if (target.applyConsumeOnPickup(drop.getItemId())) {   // consumed on pickup
                                // nothing to store, the pickup effect already ran
                            } else if (ItemId.isNxCard(drop.getItemId())) {
                                target.getCashShop().gainCash(1, drop.getItemId() == ItemId.NX_CARD_100 ? 100 : 250);
                            } else if (drop.getItemId() >= 5000000 && drop.getItemId() <= 5000100) {
                                int petId = Pet.createPet(drop.getItemId());
                                if (petId == -1) {
                                    left++;
                                    continue;
                                }
                                InventoryManipulator.addById(cl, drop.getItemId(), drop.getItem().getQuantity(), null, petId);
                            } else if (!InventoryManipulator.addFromDrop(cl, drop.getItem(), true)) {
                                // 原版这里照样把掉落物删掉了（东西就丢了），控制台选择留在地上
                                left++;
                                continue;
                            } else if (drop.getItemId() == ItemId.NX_CARD_100) {
                                target.updateAriantScore();
                            }

                            their.pickItemDrop(PacketCreator.removeItemFromMap(drop.getObjectId(), 2, target.getId()), drop);
                            picked++;
                        } catch (Exception e) {
                            left++;
                            ctx.fail("掉落物 " + drop.getObjectId() + " 处理失败: " + e);
                        } finally {
                            drop.unlockItem();
                        }
                    }
                    ctx.done("地图 " + map.getId() + ": 强拾取 " + picked + " 个给 " + target.getName()
                            + "，有 " + left + " 个没动（已 picked / 背包放不下 / 数据异常）");
                });

        // ------------------------------------------------------------------ boss spawns
        // In rank 4 these are plain spawns on the typing GM's spot, not instance starts, so the
        // console version is a map-scoped spawn.

        // was: "!zakum"
        r.add("zakum", "<地图id> [角色名]",
                "在这张地图上召唤扎昆：本体用假怪刷出，再补 8 只手臂（给了角色名就落在他脚边）", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    Point pos = spawnPointArg(ctx, args, map, 1);
                    if (pos == null) {
                        return;
                    }

                    Monster body = LifeFactory.getMonster(MobId.ZAKUM_1);
                    if (body == null) {
                        ctx.fail("扎昆本体 " + MobId.ZAKUM_1 + " 读不出来（这份客户端里没有这只怪）");
                        return;
                    }
                    map.spawnFakeMonsterOnGroundBelow(body, pos);

                    int arms = 0;
                    for (int mobId = MobId.ZAKUM_ARM_1; mobId <= MobId.ZAKUM_ARM_8; mobId++) {
                        Monster arm = LifeFactory.getMonster(mobId);
                        if (arm == null) {
                            continue;
                        }
                        map.spawnMonsterOnGroundBelow(arm, pos);
                        arms++;
                    }
                    ctx.done("地图 " + map.getId() + " 的 (" + pos.x + ", " + pos.y + ") 刷出扎昆本体 + "
                            + arms + " 只手臂（手臂少于 8 只说明这些 id 在本机 wz 里缺失）");
                });

        // was: "!horntail"
        r.add("horntail", "<地图id> [角色名]",
                "在这张地图上召唤恶魔龙（含出场动画怪和头/尾等部位，全都在同一个落点）", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    Point pos = spawnPointArg(ctx, args, map, 1);
                    if (pos == null) {
                        return;
                    }
                    try {
                        map.spawnHorntailOnGroundBelow(pos);
                        ctx.done("地图 " + map.getId() + " 的 (" + pos.x + ", " + pos.y + ") 刷出了恶魔龙");
                    } catch (Exception e) {
                        ctx.fail("召唤恶魔龙失败（部位怪 id 缺失或落点没有地面）: " + e);
                    }
                });

        // was: "!pinkbean"
        r.add("pinkbean", "<地图id> [角色名]", "在这张地图上召唤 Pink Bean", (ctx, args) ->
                spawnSingleBoss(ctx, args, MobId.PINK_BEAN, "Pink Bean"));

        // was: "!pap"
        r.add("pap", "<地图id> [角色名]", "在这张地图上召唤班·雷昂（时钟塔 Papulatus）", (ctx, args) ->
                spawnSingleBoss(ctx, args, MobId.PAPULATUS_CLOCK, "Papulatus"));

        // was: "!pianus"
        r.add("pianus", "<地图id> [角色名]", "在这张地图上召唤品克拉斯（R 形态）", (ctx, args) ->
                spawnSingleBoss(ctx, args, MobId.PIANUS_R, "Pianus(R)"));

        // was: "!cake [hp]"
        r.add("cake", "<地图id> [血量] [角色名]",
                "在这张地图上召唤蛋糕，可以先把它初始血量写成指定值（<=0 视为无限血）", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    long hp = Integer.MAX_VALUE;
                    boolean hpGiven = false;
                    if (args.length >= 2) {
                        Integer v = ctx.num(args[1], "血量");
                        if (v == null) {
                            ctx.println("用法: cake <地图id> [血量] [角色名]");
                            return;
                        }
                        hp = v;
                        hpGiven = true;
                    }

                    Point pos = spawnPointArg(ctx, args, map, 2);
                    if (pos == null) {
                        return;
                    }
                    Monster cake = LifeFactory.getMonster(MobId.GIANT_CAKE);
                    if (cake == null) {
                        ctx.fail("蛋糕 " + MobId.GIANT_CAKE + " 读不出来（这份客户端里没有这只怪）");
                        return;
                    }
                    if (hpGiven) {
                        cake.setStartingHp(hp <= 0 || hp > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) hp);
                    }
                    map.spawnMonsterOnGroundBelow(cake, pos);
                    ctx.done("地图 " + map.getId() + " 的 (" + pos.x + ", " + pos.y + ") 刷出了蛋糕"
                            + (hpGiven ? "，初始血量 " + (hp <= 0 ? "无限" : String.valueOf(hp)) : "（按 wz 默认血量）"));
                });

        // ------------------------------------------------------------------ player NPCs (hall of fame)

        // was: "!playernpc <playername>" -> the typing GM's own map and position
        r.add("playernpc", "<地图id> <角色名>",
                "把这名在线玩家做成永久玩家 NPC（名人堂那种）放到指定地图；位置由服务端挑空位", (ctx, args) -> {
                    MapleMap map = mapArg(ctx, args, 0);
                    if (map == null) {
                        return;
                    }
                    if (GmContext.missing(args, 2)) {
                        ctx.println("用法: playernpc <地图id> <角色名>");
                        return;
                    }
                    Character target = ctx.require(args[1]);
                    if (target == null) {
                        return;
                    }
                    Client cl = target.getClient();
                    if (cl == null || cl.getChannelServer() == null) {
                        ctx.fail(target.getName() + " 已经掉线，没有可用的连接");
                        return;
                    }
                    if (cl.getChannelServer().getMapFactory().getMap(map.getId()) == null) {
                        ctx.fail("地图 " + map.getId() + " 在 " + target.getName() + " 所在的频道 "
                                + cl.getChannelServer().getId() + " 里不存在，放置不了");
                        return;
                    }

                    if (!PlayerNPC.spawnPlayerNPC(map.getId(), target)) {
                        ctx.fail("无法部署玩家 NPC：这张图没有空位，或者这个区段的脚本 id 已经用尽");
                        return;
                    }
                    Pair<Point, Integer> at = findPlayerNpcOn(map, target.getName());
                    ctx.done("已把 " + target.getName() + " 做成玩家 NPC 放到地图 " + map.getId()
                            + (at == null ? "" : " 的 (" + at.getLeft().x + ", " + at.getLeft().y + ")，脚本 id " + at.getRight()));
                });

        // was: "!playernpcremove <playername>"
        r.add("playernpcremove", "<角色名>",
                "移除这个玩家名对应的所有玩家 NPC（连同数据库记录）", (ctx, args) -> {
                    if (GmContext.missing(args, 1)) {
                        ctx.println("用法: playernpcremove <角色名>");
                        return;
                    }
                    Character target = ctx.require(args[0]);
                    if (target == null) {
                        return;
                    }
                    int rows = countRows("SELECT id FROM playernpcs WHERE name LIKE ?", new String[]{target.getName()});
                    if (rows < 0) {
                        ctx.fail("读取 playernpcs 表失败，先不动手移除");
                        return;
                    }
                    if (rows == 0) {
                        ctx.done(target.getName() + " 名下没有玩家 NPC 记录");
                        return;
                    }
                    PlayerNPC.removePlayerNPC(target);
                    ctx.done("已移除 " + target.getName() + " 的 " + rows + " 个玩家 NPC 记录，并从各频道地图上摘掉");
                });

        // ------------------------------------------------------------------ permanent life (plife table)

        // was: "!pnpc <npcid>" -> the typing GM's map / position / world
        r.add("pnpc", "<地图id> <npcid> <世界id> [角色名]",
                "在这张地图上永久放一只 NPC（写进 plife 表，重启后还在），并当场刷到该世界的每个频道", (ctx, args) -> {
                    PlifePlacement p = plifeCommon(ctx, args, "pnpc", "<地图id> <npcid> <世界id> [角色名]", 3);
                    if (p == null) {
                        return;
                    }
                    Integer npcId = ctx.num(args, 1, "npcid");
                    if (npcId == null) {
                        return;
                    }
                    World w = ctx.requireWorld(p.worldId);
                    if (w == null) {
                        return;
                    }
                    if (p.map.containsNPC(npcId)) {
                        ctx.fail("地图 " + p.map.getId() + " 上已经有 id 为 " + npcId + " 的 NPC 了");
                        return;
                    }
                    NPC probe = LifeFactory.getNPC(npcId);
                    if (probe == null || "MISSINGNO".equals(probe.getName())) {
                        ctx.fail("NPC id " + npcId + " 不存在（本机 wz 里查不到名字）");
                        return;
                    }

                    ContentValues values = plifeValues(npcId, "n", p, w.getId());
                    try {
                        DatabaseConnection.getConnection().insert("plife", null, values);
                    } catch (Exception e) {
                        ctx.fail("写入 plife 表失败: " + e);
                        return;
                    }

                    int spawned = 0, missing = 0;
                    for (Channel ch : w.getChannels()) {
                        MapleMap m = ch.getMapFactory().getMap(p.map.getId());
                        if (m == null) {
                            missing++;
                            continue;
                        }
                        NPC npc = LifeFactory.getNPC(npcId);
                        if (npc == null) {
                            missing++;
                            continue;
                        }
                        applyPlifeSpot(npc, p);
                        m.addMapObject(npc);
                        m.broadcastMessage(PacketCreator.spawnNPC(npc));
                        spawned++;
                    }
                    ctx.done("NPC " + npcId + "（" + probe.getName() + "）已永久放到世界 " + w.getId()
                            + " 地图 " + p.map.getId() + " 的 (" + p.x + ", " + p.y + ")，fh=" + p.fh
                            + "，当场刷出 " + spawned + " 个频道" + (missing > 0 ? "，" + missing + " 个频道没有这张图" : ""));
                });

        // was: "!pnpcremove [npcid]" -> the typing GM's map / position box / world
        r.add("pnpcremove", "<地图id> <npcid|-1=整张图> <世界id>",
                "撤掉这张地图上永久放置的 NPC（删 plife 记录并从各频道摘掉）；npcid 给 -1 就是整张图都撤",
                (ctx, args) -> removePlife(ctx, args, "pnpcremove", "n", "NPC"));

        // was: "!pmob <mobid> [mobtime]" -> the typing GM's map / position / world
        r.add("pmob", "<地图id> <mobid> <世界id> [mobtime] [角色名]",
                "在这张地图上永久放一只怪（写进 plife 表），并当场刷到该世界的每个频道", (ctx, args) -> {
                    PlifePlacement p = plifeCommon(ctx, args, "pmob", "<地图id> <mobid> <世界id> [mobtime] [角色名]", 4);
                    if (p == null) {
                        return;
                    }
                    Integer mobId = ctx.num(args, 1, "mobid");
                    if (mobId == null) {
                        return;
                    }
                    int mobTime = -1;
                    if (args.length >= 4) {
                        Integer t = ctx.num(args, 3, "mobtime");
                        if (t == null) {
                            return;
                        }
                        mobTime = t;
                    }
                    World w = ctx.requireWorld(p.worldId);
                    if (w == null) {
                        return;
                    }
                    Monster probe = LifeFactory.getMonster(mobId);
                    if (probe == null || "MISSINGNO".equals(probe.getName())) {
                        ctx.fail("怪物 id " + mobId + " 不存在（本机 wz 里查不到名字）");
                        return;
                    }

                    ContentValues values = plifeValues(mobId, "m", p, w.getId());
                    values.put("mobtime", mobTime);
                    try {
                        DatabaseConnection.getConnection().insert("plife", null, values);
                    } catch (Exception e) {
                        ctx.fail("写入 plife 表失败: " + e);
                        return;
                    }

                    int spawned = 0, failed = 0;
                    for (Channel ch : w.getChannels()) {
                        MapleMap m = ch.getMapFactory().getMap(p.map.getId());
                        if (m == null) {
                            failed++;
                            continue;
                        }
                        // one fresh instance per channel: LifeFactory hands out a new Monster every
                        // call, and sharing a single object across channels would let the last
                        // channel win ownership of it (the in-game command did exactly that)
                        Monster mob = LifeFactory.getMonster(mobId);
                        if (mob == null) {
                            failed++;
                            continue;
                        }
                        applyPlifeSpot(mob, p);
                        try {
                            m.addMonsterSpawn(mob, mobTime, -1);
                            m.addAllMonsterSpawn(mob, mobTime, -1);
                            spawned++;
                        } catch (Exception e) {
                            failed++;
                            ctx.fail("频道 " + ch.getId() + " 上放置怪物出生点失败: " + e);
                        }
                    }
                    ctx.done("怪物 " + mobId + "（" + probe.getName() + "）已永久放到世界 " + w.getId()
                            + " 地图 " + p.map.getId() + " 的 (" + p.x + ", " + p.y + ")，fh=" + p.fh
                            + "，重生时间 " + mobTime + "，生效 " + spawned + " 个频道"
                            + (failed > 0 ? "，" + failed + " 个频道没生效" : ""));
                });

        // was: "!pmobremove [mobid]" -> the typing GM's map / position box / world
        r.add("pmobremove", "<地图id> <mobid|-1=整张图> <世界id>",
                "撤掉这张地图上永久放置的怪（删 plife 记录并摘掉各频道上的出生点）；mobid 给 -1 就是整张图都撤",
                (ctx, args) -> removePlife(ctx, args, "pmobremove", "m", "怪物"));
    }

    // ------------------------------------------------------------------ shared plumbing

    private static final class PlifePlacement {
        private MapleMap map;
        private int worldId;
        private int x;
        private int y;
        private int fh;
    }

    // world id / map id / spot, the three things the in-game versions took from the typing GM.
    // nameIndex is where the optional "spawn next to this character" argument lives (it differs
    // between pnpc and pmob because pmob has a mobtime slot in front of it).
    private static PlifePlacement plifeCommon(GmContext ctx, String[] args, String cmd, String usage, int nameIndex) {
        if (GmContext.missing(args, 3)) {
            ctx.println("用法: " + cmd + " " + usage);
            return null;
        }
        MapleMap map = mapArg(ctx, args, 0);
        if (map == null) {
            return null;
        }
        Integer worldId = ctx.num(args, 2, "世界id");
        if (worldId == null) {
            return null;
        }
        Point pos = spawnPointArg(ctx, args, map, nameIndex);
        if (pos == null) {
            return null;
        }
        Foothold foothold = map.getFootholds().findBelow(pos);
        if (foothold == null) {
            ctx.fail("落点 (" + pos.x + ", " + pos.y + ") 下面没有可站立的地板，放置不了");
            return null;
        }
        PlifePlacement p = new PlifePlacement();
        p.map = map;
        p.worldId = worldId;
        p.x = pos.x;
        p.y = pos.y;
        p.fh = foothold.getId();
        return p;
    }

    private static ContentValues plifeValues(int lifeId, String type, PlifePlacement p, int worldId) {
        ContentValues values = new ContentValues();
        values.put("life", lifeId);
        values.put("f", 0);
        values.put("fh", p.fh);
        values.put("cy", p.y);
        values.put("rx0", p.x + 50);
        values.put("rx1", p.x - 50);
        values.put("type", type);
        values.put("x", p.x);
        values.put("y", p.y);
        values.put("world", worldId);
        values.put("map", p.map.getId());
        values.put("mobtime", -1);
        values.put("hide", 0);
        return values;
    }

    private static void applyPlifeSpot(server.life.AbstractLoadedLife life, PlifePlacement p) {
        life.setPosition(new Point(p.x, p.y));
        life.setCy(p.y);
        life.setRx0(p.x + 50);
        life.setRx1(p.x - 50);
        life.setFh(p.fh);
    }

    // pnpcremove / pmobremove: same table, same (world, map, type) key, only the life filter differs.
    private static void removePlife(GmContext ctx, String[] args, String cmd, String type, String label) {
        if (GmContext.missing(args, 3)) {
            ctx.println("用法: " + cmd + " <地图id> <lifeid|-1=整张图> <世界id>");
            return;
        }
        MapleMap map = mapArg(ctx, args, 0);
        if (map == null) {
            return;
        }
        Integer lifeId = ctx.num(args, 1, "lifeid");
        if (lifeId == null) {
            return;
        }
        Integer worldId = ctx.num(args, 2, "世界id");
        if (worldId == null) {
            return;
        }
        World w = ctx.requireWorld(worldId);
        if (w == null) {
            return;
        }

        List<Pair<Integer, Pair<Integer, Integer>>> toRemove = new ArrayList<>();
        String select;
        String[] selectArgs;
        if (lifeId >= 0) {
            select = "SELECT life, x, y FROM plife WHERE world = ? AND map = ? AND type LIKE ? AND life = ?";
            selectArgs = new String[]{String.valueOf(worldId), String.valueOf(map.getId()), type, String.valueOf(lifeId)};
        } else {
            select = "SELECT life, x, y FROM plife WHERE world = ? AND map = ? AND type LIKE ?";
            selectArgs = new String[]{String.valueOf(worldId), String.valueOf(map.getId()), type};
        }

        try {
            SQLiteDatabase con = DatabaseConnection.getConnection();
            try (Cursor cursor = con.rawQuery(select, selectArgs)) {
                while (cursor.moveToNext()) {
                    int lifeIdx = cursor.getColumnIndex("life");
                    int xIdx = cursor.getColumnIndex("x");
                    int yIdx = cursor.getColumnIndex("y");
                    if (lifeIdx < 0 || xIdx < 0 || yIdx < 0) {
                        continue;
                    }
                    toRemove.add(new Pair<>(cursor.getInt(lifeIdx), new Pair<>(cursor.getInt(xIdx), cursor.getInt(yIdx))));
                }
            }

            // the rows are collected before anything is deleted: this Cursor sits on an open JDBC
            // statement, so writing to plife mid-iteration is not an option
            for (Pair<Integer, Pair<Integer, Integer>> e : toRemove) {
                con.delete("plife", "life = ? AND x = ? AND y = ?",
                        new String[]{String.valueOf(e.getLeft()), String.valueOf(e.getRight().getLeft()),
                                String.valueOf(e.getRight().getRight())});
            }
        } catch (Exception e) {
            ctx.fail("读写 plife 表失败: " + e);
            return;
        }

        if (toRemove.isEmpty()) {
            ctx.done("世界 " + worldId + " 地图 " + map.getId() + " 上没有永久" + label
                    + (lifeId >= 0 ? " " + lifeId : "") + " 的放置记录");
            return;
        }

        int touched = 0, missing = 0;
        for (Channel ch : w.getChannels()) {
            MapleMap m = ch.getMapFactory().getMap(map.getId());
            if (m == null) {
                missing++;
                continue;
            }
            for (Pair<Integer, Pair<Integer, Integer>> e : toRemove) {
                try {
                    if ("n".equals(type)) {
                        m.destroyNPC(e.getLeft());
                    } else {
                        m.removeMonsterSpawn(e.getLeft(), e.getRight().getLeft(), e.getRight().getRight());
                        m.removeAllMonsterSpawn(e.getLeft(), e.getRight().getLeft(), e.getRight().getRight());
                    }
                } catch (Exception ex) {
                    ctx.fail("频道 " + ch.getId() + " 上撤掉 " + e.getLeft() + " 失败: " + ex);
                }
            }
            touched++;
        }

        for (Pair<Integer, Pair<Integer, Integer>> e : toRemove) {
            ctx.printf("  - %s %d @ (%d, %d)%n", label, e.getLeft(), e.getRight().getLeft(), e.getRight().getRight());
        }
        ctx.done("世界 " + worldId + " 地图 " + map.getId() + ": 撤掉了 " + toRemove.size() + " 个永久" + label
                + " 放置，处理了 " + touched + " 个频道" + (missing > 0 ? "，" + missing + " 个频道没有这张图" : ""));
    }

    // args[0] is a map id, resolved the same way every map-scoped console command does it.
    private static MapleMap mapArg(GmContext ctx, String[] args, int i) {
        Integer mapid = GmContext.missing(args, i + 1) ? null : ctx.num(args, i, "地图id");
        if (mapid == null) {
            return null;
        }
        return ctx.requireMap(mapid);
    }

    private static void setWorldRate(GmContext ctx, String[] args, String cmd, String label, String notice,
                                     ToIntFunction<World> getter, ObjIntConsumer<World> setter) {
        if (GmContext.missing(args, 2)) {
            ctx.println("用法: " + cmd + " <世界id> <倍率>   （例: " + cmd + " 0 10；现有世界: " + ctx.worldIds() + "）");
            return;
        }
        Integer worldId = ctx.num(args, 0, "世界id");
        if (worldId == null) {
            ctx.println("用法: " + cmd + " <世界id> <倍率>   （现有世界: " + ctx.worldIds() + "）");
            return;
        }
        Integer value = ctx.num(args, 1, "倍率");
        if (value == null) {
            return;
        }
        World w = ctx.requireWorld(worldId);
        if (w == null) {
            return;
        }

        int old = getter.applyAsInt(w);
        int rate = Math.max(value, 1);   // same clamp the in-game command used
        setter.accept(w, rate);
        w.broadcastPacket(PacketCreator.serverNotice(6, String.format(notice, rate)));
        ctx.done("世界 " + worldId + " 的" + label + "倍率: " + old + " -> " + rate + "（已广播公告；重启后回到配置文件里的值）");
    }

    // was: "!<boss>" on the typing GM's own spot.
    private static void spawnSingleBoss(GmContext ctx, String[] args, int mobId, String label) {
        MapleMap map = mapArg(ctx, args, 0);
        if (map == null) {
            return;
        }
        Point pos = spawnPointArg(ctx, args, map, 1);
        if (pos == null) {
            return;
        }
        Monster mob = LifeFactory.getMonster(mobId);
        if (mob == null) {
            ctx.fail("怪物 id " + mobId + " 读不出来（这份客户端里没有 " + label + "）");
            return;
        }
        map.spawnMonsterOnGroundBelow(mob, pos);
        ctx.done(label + " (" + mobId + ") 已刷在地图 " + map.getId() + " 的 (" + pos.x + ", " + pos.y + ")");
    }

    // The optional trailing "[角色名]" of the spawn commands: use that character's own spot, but only
    // when he really stands on the map that was asked for. Null means the reason is already printed.
    private static Point spawnPointArg(GmContext ctx, String[] args, MapleMap map, int idx) {
        Character near = null;
        if (args.length > idx) {
            near = ctx.require(args[idx]);
            if (near == null) {
                return null;
            }
            if (near.getMapId() != map.getId()) {
                ctx.fail(near.getName() + " 现在在地图 " + near.getMapId() + "，不在 " + map.getId()
                        + " 上，所以没法拿他的位置当落点");
                return null;
            }
        }
        return spawnPoint(ctx, map, near);
    }

    // The console has no cursor: pick a spot on the map that actually has ground under it.
    private static Point spawnPoint(GmContext ctx, MapleMap map, Character near) {
        List<Point> candidates = new ArrayList<>();
        if (near != null) {
            if (near.getMapId() != map.getId()) {
                ctx.println("（" + near.getName() + " 现在在地图 " + near.getMapId() + "，不是 " + map.getId()
                        + "，所以这个位置不作数，改用 " + map.getId() + " 上的落点）");
            } else {
                Point p = near.getPosition();
                if (p != null) {
                    candidates.add(p);
                }
            }
        }
        Collection<Character> onMap = map.getCharacters();
        if (onMap != null) {
            for (Character c : onMap) {
                Point p = c.getPosition();
                if (p != null) {
                    candidates.add(p);
                }
            }
        }
        // MapleMap.getPortals() is commented out in this codebase, so the fall-back spots are the
        // random player spawnpoint (which throws when the map has none) and the low-numbered portals.
        Portal random = null;
        try {
            random = map.getRandomPlayerSpawnpoint();
        } catch (Exception e) {
            random = null;
        }
        if (random != null && random.getPosition() != null) {
            candidates.add(random.getPosition());
        }
        for (int i = 0; i < 8; i++) {
            Portal p = map.getPortal(i);
            if (p != null && p.getPosition() != null) {
                candidates.add(p.getPosition());
            }
        }
        candidates.add(new Point(0, 0));

        for (Point c : candidates) {
            Point ground = groundPoint(map, c);
            if (ground != null) {
                return ground;
            }
        }
        ctx.fail("地图 " + map.getId() + " 上找不到一个脚下有地板的落点（这张图没有玩家、传送点也没法站人）");
        return null;
    }

    // getGroundBelow() dereferences a possibly-null foothold in this codebase, so probe first.
    private static Point groundPoint(MapleMap map, Point pos) {
        try {
            Point g = map.getGroundBelow(pos);
            if (g == null || map.getFootholds().findBelow(g) == null) {
                return null;
            }
            return g;
        } catch (Exception e) {
            return null;
        }
    }

    private static int countDrops(MapleMap map) {
        return map.getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.ITEM)).size();
    }

    // was: "!itemvac" without a receiver - there is no inventory on this side, so the loot is put
    // back on the ground instead of being eaten: the same Item / meso amount is re-dropped from the
    // same dropper at the same spot, and only the original MapItem is taken off afterwards.
    // Anything that cannot be re-dropped is simply left exactly where it is.
    private static int[] reDropAllDrops(MapleMap map) {
        int redropped = 0, untouched = 0;

        for (MapObject o : map.getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.ITEM))) {
            if (!(o instanceof MapItem)) {
                continue;
            }
            MapItem drop = (MapItem) o;
            MapObject dropper = drop.getDropper();
            Character owner = map.getCharacterById(drop.getOwnerId());

            if (dropper == null || owner == null || owner.getClient() == null) {
                // 掉落者已经不在这张图上了，重刷一次会把东西弄丢，所以原样留着
                untouched++;
                continue;
            }

            Point pos = drop.getPosition();
            byte dropType = drop.getDropType();
            boolean playerDrop = drop.isPlayerDrop();
            int meso = drop.getMeso();
            Item item = drop.getItem();

            drop.lockItem();
            try {
                if (drop.isPickedUp()) {
                    untouched++;
                    continue;
                }

                if (meso > 0) {
                    map.spawnMesoDrop(meso, pos, dropper, owner, playerDrop, dropType, (short) 0);
                } else {
                    if (item == null) {
                        untouched++;
                        continue;
                    }
                    map.spawnItemDrop(dropper, owner, item, pos, dropType, playerDrop);
                }
            } catch (Exception e) {
                untouched++;
                continue;
            } finally {
                drop.unlockItem();
            }

            // the fresh drop is on the ground by now, so retiring the old one cannot lose anything
            try {
                drop.lockItem();
                try {
                    map.pickItemDrop(PacketCreator.silentRemoveItemFromMap(drop.getObjectId()), drop);
                } finally {
                    drop.unlockItem();
                }
                redropped++;
            } catch (Exception e) {
                untouched++;
            }
        }

        return new int[]{redropped, untouched};
    }

    private static Pair<Point, Integer> findPlayerNpcOn(MapleMap map, String name) {
        for (MapObject o : map.getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY,
                Arrays.asList(MapObjectType.PLAYER_NPC))) {
            if (o instanceof PlayerNPC) {
                PlayerNPC pn = (PlayerNPC) o;
                if (name.equals(pn.getName())) {
                    return new Pair<>(pn.getPosition(), pn.getScriptId());
                }
            }
        }
        return null;
    }

    private static void hardsetItemStats(Equip equip, short stat, short spdjmp) {
        equip.setStr(stat);
        equip.setDex(stat);
        equip.setInt(stat);
        equip.setLuk(stat);
        equip.setMatk(stat);
        equip.setWatk(stat);
        equip.setAcc(stat);
        equip.setAvoid(stat);
        equip.setJump(spdjmp);
        equip.setSpeed(spdjmp);
        equip.setWdef(stat);
        equip.setMdef(stat);
        equip.setHp(stat);
        equip.setMp(stat);

        short flag = equip.getFlag();
        flag |= ItemConstants.UNTRADEABLE;
        equip.setFlag(flag);
    }

    // Accepts either a raw item id or an item name (the console is hand-typed, the in-game path
    // only ever took the id); the resolved id is printed by the caller either way. The integer try
    // is done quietly on purpose, so that a plain item name does not print a parse error first.
    private static Integer resolveItemId(GmContext ctx, String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException e) {
            ctx.println("（" + key + " 不是数字，按道具名查一次……）");
        }
        ArrayList<Pair<Integer, String>> found = ItemInformationProvider.getInstance().getItemDataByName(key);
        if (found.isEmpty()) {
            ctx.fail("既不是道具 id，也没有名字匹配 " + key + " 的道具");
            return null;
        }
        Pair<Integer, String> first = found.get(0);
        ctx.println("按名字匹配到: " + first.getRight() + " (" + first.getLeft() + ")"
                + (found.size() > 1 ? "，同名还有 " + (found.size() - 1) + " 个，建议直接用 id" : ""));
        return first.getLeft();
    }

    private static int countRows(String select, String[] args) {
        try {
            SQLiteDatabase con = DatabaseConnection.getConnection();
            int n = 0;
            try (Cursor cursor = con.rawQuery(select, args)) {
                while (cursor.moveToNext()) {
                    n++;
                }
            }
            return n;
        } catch (Exception e) {
            return -1;
        }
    }

    private static short clampShort(GmContext ctx, int value, String what) {
        if (value < 0) {
            ctx.println(what + " 是负数（" + value + "），按 0 处理");
            return 0;
        }
        if (value > Short.MAX_VALUE) {
            ctx.println(what + " " + value + " 超过了 32767，按 32767 处理");
            return Short.MAX_VALUE;
        }
        return (short) value;
    }

    private static String showEmpty(String s) {
        if (s == null) {
            return "(空)";
        }
        return s.trim().isEmpty() ? "(空)" : s;
    }
}
