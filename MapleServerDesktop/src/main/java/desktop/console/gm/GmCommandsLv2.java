package desktop.console.gm;

import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.Pet;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.id.MapId;
import constants.id.MobId;
import constants.inventory.ItemConstants;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.ItemInformationProvider;
import server.life.LifeFactory;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.NPC;
import server.life.PlayerNPC;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.SavedLocationType;
import server.quest.Quest;
import tools.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Console ports of server-core/client/command/commands/gm2.
//
// The console has no logged-in operator, so every command that in-game acted on the typing GM
// (or on that GM's current map) now names its subject explicitly:
//   - player-scoped      -> "<cmd> <角色名> ..."
//   - map-scoped         -> "<cmd> <地图id> ..."
//   - "drop a bomb next to whoever typed it" -> "<cmd> <角色名>" (the target names the victim)
// Commands that already took a victim's name keep their original meaning; only the feedback moved
// from NPC dialogs / dropMessage popups to stdout.
//
// Two in-game side effects cannot be reproduced faithfully and are handled as noted per command:
//   - "open a shop / talk to an NPC windows" (gmshop, gachalist) - there is no client to show them
//     to, so the console prints a hint instead.
//   - GM announcements aimed at other players (bomb) - the console has no announcing operator, so
//     the effect happens and the console line replaces the announcement.
public final class GmCommandsLv2 {

    private GmCommandsLv2() {
    }

    public static void register(GmRegistry r) {
        // was: "@recharge" - refilled the typing GM's USE slot
        r.add("recharge", "<角色名>", "把该角色背包里所有消耗品（箭、子弹、投掷武器、药水等）补满到单格上限", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: recharge <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client client = ctx.requireClient(args[0]);
            if (client == null) {
                return;
            }
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            int fixed = 0;
            for (Item it : c.getInventory(InventoryType.USE).list()) {
                if (it == null) {
                    continue;
                }
                int id = it.getItemId();
                if (ItemConstants.isThrowingStar(id) || ItemConstants.isArrow(id)
                        || ItemConstants.isBullet(id) || ItemConstants.isConsumable(id)) {
                    it.setQuantity(ii.getSlotMax(client, id));
                    c.forceUpdateItem(it);
                    fixed++;
                }
            }
            ctx.done(c.getName() + ": 补充了 " + fixed + " 格消耗品");
        });

        // was: "@whereami" - listed the objects on the typing GM's current map
        r.add("whereami", "<地图id>", "列出这张地图上的玩家、NPC、假人、怪物和掉落物", (ctx, args) -> {
            Integer mapid = firstMapArg(ctx, "whereami", args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            List<Character> chars = new ArrayList<>(map.getAllPlayers());
            List<Monster> mobs = new ArrayList<>();
            List<MapItem> items = new ArrayList<>();
            List<NPC> npcs = new ArrayList<>();
            List<PlayerNPC> playerNpcs = new ArrayList<>();
            for (Monster mo : map.getAllMonsters()) {
                if (mo != null && mo.isAlive()) {
                    mobs.add(mo);
                }
            }
            // 图上的东西只有一个总容器：按 MapObjectType 分流，假人 NPC 是独立的类型，别算进真 NPC。
            for (MapObject o : map.getMapObjects()) {
                if (o == null) {
                    continue;
                }
                switch (o.getType()) {
                    case ITEM:
                        if (o instanceof MapItem mi) {
                            items.add(mi);
                        }
                        break;
                    case NPC:
                        if (o instanceof NPC npc) {
                            npcs.add(npc);
                        }
                        break;
                    case PLAYER_NPC:
                        if (o instanceof PlayerNPC pnpc) {
                            playerNpcs.add(pnpc);
                        }
                        break;
                    default:
                        break;
                }
            }
            ctx.printf("地图 %d %s%n", mapid, mapName(map));
            summarize(ctx, "玩家", chars.size());
            summarize(ctx, "NPC", npcs.size());
            summarize(ctx, "玩家假人 NPC", playerNpcs.size());
            summarize(ctx, "怪物", mobs.size());
            summarize(ctx, "掉落物", items.size());
            if (!chars.isEmpty()) {
                ctx.println("玩家明细：");
                for (Character ch : chars) {
                    ctx.printf("  - %s (id=%d, oid=%d)%n", ch.getName(), ch.getId(), ch.getObjectId());
                }
            }
            if (!npcs.isEmpty()) {
                ctx.println("NPC 明细：");
                for (NPC npc : npcs) {
                    ctx.printf("  - %s (id=%d, oid=%d)%n", npc.getName(), npc.getId(), npc.getObjectId());
                }
            }
            if (!playerNpcs.isEmpty()) {
                ctx.println("玩家假人 NPC 明细：");
                for (PlayerNPC pnpc : playerNpcs) {
                    ctx.printf("  - %s (script=%d, oid=%d)%n", pnpc.getName(), pnpc.getScriptId(), pnpc.getObjectId());
                }
            }
            if (!mobs.isEmpty()) {
                ctx.println("怪物明细：");
                for (Monster mo : mobs) {
                    ctx.printf("  - %s (%d) 血量 %d/%d oid=%d%n",
                            mo.getName(), mo.getId(), mo.getHp(), mo.getMaxHp(), mo.getObjectId());
                }
            }
            if (!items.isEmpty()) {
                ctx.println("掉落物明细：");
                ItemInformationProvider ii = ItemInformationProvider.getInstance();
                for (MapItem it : items) {
                    ctx.printf("  - %s (%d) x%d%n", ii.getName(it.getItemId()), it.getItemId(),
                            it.getItem().getQuantity());
                }
            }
        });

        // was: "@hide" / "@unhide" - both cast invisibility on the typist
        r.add(new String[]{"hide", "unhide"}, "<角色名>", "给该角色施加隐身效果（游戏内这两个命令做的是同一件事）", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: hide|unhide <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Skill invis = SkillFactory.getSkill(9101004);
            if (invis == null) {
                ctx.fail("找不到隐身技能 9101004");
                return;
            }
            if (invis.getEffect(invis.getMaxLevel()) == null) {
                ctx.fail("隐身技能 9101004 没有可用的效果数据");
                return;
            }
            invis.getEffect(invis.getMaxLevel()).applyTo(c);
            ctx.done(c.getName() + ": 已施加隐身效果（游戏内 hide/unhide 都是再施放一次这个技能）");
        });

        // was: "@sp [玩家名] <新sp>"
        r.add("sp", "<角色名> <新sp>", "把该角色的剩余技能点设成指定值（0 ~ MAX_AP 限幅）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: sp <角色名> <新sp>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "技能点数");
            if (value == null) {
                return;
            }
            int newSp = clampStat(value);
            c.updateRemainingSp(newSp);
            ctx.done(c.getName() + ": 剩余技能点已设为 " + newSp);
        });

        // was: "@ap [玩家名] <新ap>"
        r.add("ap", "<角色名> <新ap>", "把该角色的剩余属性点设成指定值（0 ~ MAX_AP 限幅）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: ap <角色名> <新ap>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "属性点数");
            if (value == null) {
                return;
            }
            int newAp = clampStat(value);
            c.changeRemainingAp(newAp, false);
            ctx.done(c.getName() + ": 剩余属性点已设为 " + newAp);
        });

        // was: "@empowerme" - buffed the typist
        r.add("empowerme", "<角色名>", "给该角色施放一组常用增益技能", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: empowerme <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            final int[] ids = {2311003, 2301004, 1301007, 4101004, 2001002, 1101007, 1005, 2301003,
                    5121009, 1111002, 4111001, 4111002, 4211003, 4211005, 1321000, 2321004, 3121002};
            int applied = 0;
            for (int id : ids) {
                Skill sk = SkillFactory.getSkill(id);
                if (sk == null) {
                    continue;
                }
                if (sk.getEffect(sk.getMaxLevel()) != null && sk.getEffect(sk.getMaxLevel()).applyTo(c)) {
                    applied++;
                }
            }
            ctx.done(c.getName() + ": 成功施放 " + applied + "/" + ids.length + " 个增益");
        });

        // was: "@buffmap" - buffed everyone on the typing GM's map
        r.add("buffmap", "<地图id>", "给这张地图上所有人施放 GM 范围增益", (ctx, args) -> {
            Integer mapid = firstMapArg(ctx, "buffmap", args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            List<Character> chars = new ArrayList<>(map.getAllPlayers());
            if (chars.isEmpty()) {
                ctx.fail("地图 " + mapid + " 上没有玩家，增益没有作用对象");
                return;
            }
            final int[] ids = {9101001, 9101002, 9101003, 9101008, 1005};
            int applied = 0;
            for (int id : ids) {
                Skill sk = SkillFactory.getSkill(id);
                if (sk == null || sk.getEffect(sk.getMaxLevel()) == null) {
                    continue;
                }
                // useMaxRange 是游戏内 @buffmap 的原样写法：以每个角色为中心、按最大范围铺满全图。
                for (Character ch : chars) {
                    sk.getEffect(sk.getMaxLevel()).applyTo(ch, true);
                }
                applied++;
            }
            ctx.done("地图 " + mapid + ": 对 " + chars.size() + " 名玩家施放了 " + applied + "/" + ids.length + " 个 GM 增益");
        });

        // was: "@buff <buffid>"
        r.add("buff", "<角色名> <技能id>", "给该角色施放指定技能的满级效果", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: buff <角色名> <技能id>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer skillid = ctx.num(args, 1, "技能id");
            if (skillid == null) {
                return;
            }
            Skill sk = SkillFactory.getSkill(skillid);
            if (sk == null) {
                ctx.fail("技能 " + skillid + " 不存在");
                return;
            }
            if (sk.getEffect(sk.getMaxLevel()) == null) {
                ctx.fail("技能 " + skillid + " 没有满级效果数据");
                return;
            }
            boolean ok = sk.getEffect(sk.getMaxLevel()).applyTo(c);
            if (ok) {
                ctx.done(c.getName() + ": 已施放技能 " + skillid);
            } else {
                ctx.done(c.getName() + ": 技能 " + skillid + " 施放返回失败（可能不是增益类技能）");
            }
        });

        // was: "@bomb [玩家名]" - dropped a Shroom bomb next to the victim (or next to the typist)
        r.add("bomb", "[角色名]", "在该角色脚下放一颗蘑菇炸弹；不填角色名时，在每个有玩家的地图各放一颗", (ctx, args) -> {
            Monster bomb = LifeFactory.getMonster(MobId.ARPQ_BOMB);
            if (bomb == null) {
                ctx.fail("蘑菇炸弹怪物 " + MobId.ARPQ_BOMB + " 无法生成");
                return;
            }
            if (!GmContext.missing(args, 1)) {
                Character victim = ctx.require(args[0]);
                if (victim == null) {
                    return;
                }
                MapleMap map = ctx.mapOf(victim);
                if (map == null) {
                    ctx.fail(victim.getName() + " 当前不在任何地图上（可能正在传送）");
                    return;
                }
                map.spawnMonsterOnGroundBelow(bomb, victim.getPosition());
                ctx.done(victim.getName() + ": 已在其脚下放置一颗蘑菇炸弹");
                return;
            }
            int placed = 0;
            for (MapleMap map : ctx.loadedMapsWithPlayers()) {
                if (map == null) {
                    continue;
                }
                for (Object o : map.getAllPlayers()) {
                    if (o instanceof Character ch) {
                        map.spawnMonsterOnGroundBelow(bomb, ch.getPosition());
                        placed++;
                        break;
                    }
                }
            }
            ctx.done("已在 " + placed + " 张地图各放置一颗蘑菇炸弹");
        });

        // was: "@dc <玩家名>" - disconnected the victim
        r.add("dc", "<角色名>", "强制断开该角色的连接", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: dc <角色名>");
                return;
            }
            Character victim = ctx.require(args[0]);
            if (victim == null) {
                return;
            }
            Client client = victim.getClient();
            if (client == null) {
                ctx.fail(victim.getName() + " 没有可用的连接");
                return;
            }
            client.disconnect(false, false);
            ctx.done(victim.getName() + ": 已断开连接");
        });

        // was: "@cleardrops" - cleared the typing GM's own drops on its current map
        r.add("cleardrops", "<地图id>", "清除这张地图上所有玩家掉落的道具", (ctx, args) -> {
            Integer mapid = firstMapArg(ctx, "cleardrops", args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            map.clearDrops();
            ctx.done("地图 " + mapid + ": 已清除掉落物");
        });

        // was: "@clearslot <all|equip|use|setup|etc|cash>" - emptied the typist's bag
        r.add("clearslot", "<角色名> <all|equip|use|setup|etc|cash>", "清空该角色指定背包栏（或全部）的物品", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: clearslot <角色名> <all|equip|use|setup|etc|cash>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client client = ctx.requireClient(args[0]);
            if (client == null) {
                return;
            }
            List<InventoryType> types = resolveInventoryTypes(args[1]);
            if (types == null) {
                ctx.fail("背包栏 " + args[1] + " 不存在，可选: all, equip, use, setup, etc, cash");
                return;
            }
            int removed = 0;
            for (InventoryType type : types) {
                for (int i = 0; i < 101; i++) {
                    Item temp = c.getInventory(type).getItem((byte) i);
                    if (temp == null) {
                        continue;
                    }
                    InventoryManipulator.removeFromSlot(client, type, (byte) i, temp.getQuantity(), false, false);
                    removed++;
                }
            }
            ctx.done(c.getName() + ": 清空了 " + removed + " 格物品");
        });

        // was: "@clearsavelocs [玩家名]"
        r.add("clearsavelocs", "<角色名>", "清空该角色保存的全部传送点", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: clearsavelocs <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            for (SavedLocationType type : SavedLocationType.values()) {
                c.clearSavedLocation(type);
            }
            ctx.done(c.getName() + ": 已清除全部保存的传送点");
        });

        // was: "@warp <mapid>" - warped the typing GM
        r.add("warp", "<角色名> <地图id>", "把该角色传送到指定地图的随机出生点（只检查目标地图是否存在）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warp <角色名> <地图id>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer mapid = ctx.num(args, 1, "地图id");
            if (mapid == null) {
                return;
            }
            MapleMap target = ctx.requireMap(mapid);
            if (target == null) {
                return;
            }
            Portal pto = target.getRandomPlayerSpawnpoint();
            if (pto == null) {
                ctx.fail("地图 " + mapid + " 没有玩家出生点，传送不过去");
                return;
            }
            c.saveLocationOnWarp();
            c.changeMap(target, pto);
            ctx.done(c.getName() + " 已传送到 " + mapid + " " + mapName(target));
        });

        // was: "@warphere <玩家名>" / "@summon <玩家名>" - dragged the typist's target to the GM
        r.add(new String[]{"warphere", "summon"}, "<被传送角色> <参照角色>", "把前者传送到后者的脚下（两人必须同频道），控制台里没有“我”，所以需要两个名字", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warphere|summon <被传送角色> <参照角色>");
                return;
            }
            Character victim = ctx.require(args[0]);
            if (victim == null) {
                return;
            }
            Character anchor = ctx.require(args[1]);
            if (anchor == null) {
                return;
            }
            if (victim == anchor) {
                ctx.fail("被传送角色和参照角色是同一个");
                return;
            }
            if (!victim.isLoggedinWorld()) {
                ctx.fail(victim.getName() + " 当前不在游戏世界里（可能正在登录或切线）");
                return;
            }
            // 游戏内这条命令会先把人 changeChannel 过去再睡等重连；控制台是同步执行的，
            // 不适合阻塞等待切线，所以跨频道时直接拒绝，让操作者先手动把人搬到同一频道。
            int victimChannel = ctx.channelOf(victim);
            int anchorChannel = ctx.channelOf(anchor);
            if (victimChannel != anchorChannel) {
                ctx.fail(victim.getName() + " 在频道 " + victimChannel + "，" + anchor.getName()
                        + " 在频道 " + anchorChannel + "，控制台不做跨频道搬运，请先让对方手动切到频道 "
                        + anchorChannel);
                return;
            }
            MapleMap map = ctx.mapOf(anchor);
            if (map == null) {
                ctx.fail(anchor.getName() + " 当前不在任何地图上（可能正在传送）");
                return;
            }
            Portal pto = map.findClosestPortal(anchor.getPosition());
            if (pto == null) {
                ctx.fail("目标地图没有可用传送点");
                return;
            }
            victim.saveLocationOnWarp();
            victim.forceChangeMap(map, pto);
            ctx.done(victim.getName() + " 已被传送到 " + anchor.getName() + " 所在的 " + map.getId() + " " + mapName(map));
        });

        // was: "@warpto" / "@reach" / "@follow" - warped the typing GM to someone else
        r.add(new String[]{"warpto", "reach", "follow"}, "<移动角色> <目标角色>", "把前者传送到后者所在地图的最近传送点，需要跨频道时只提示，不自动换线", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: warpto|reach|follow <移动角色> <目标角色>");
                return;
            }
            Character mover = ctx.require(args[0]);
            if (mover == null) {
                return;
            }
            Character target = ctx.require(args[1]);
            if (target == null) {
                return;
            }
            if (mover == target) {
                ctx.fail("两个角色是同一个");
                return;
            }
            MapleMap map = ctx.mapOf(target);
            if (map == null) {
                ctx.fail(target.getName() + " 当前不在任何地图上（可能正在传送）");
                return;
            }
            if (mover.getClient() != null && target.getClient() != null
                    && mover.getClient().getChannel() != target.getClient().getChannel()) {
                ctx.fail(mover.getName() + " 和 " + target.getName() + " 不在同一频道，控制台不自动切线，请用 warp 直接传送到地图 " + map.getId());
                return;
            }
            Portal pto = map.findClosestPortal(target.getPosition());
            if (pto == null) {
                ctx.fail("目标地图没有可用传送点");
                return;
            }
            mover.saveLocationOnWarp();
            mover.forceChangeMap(map, pto);
            ctx.done(mover.getName() + " 已传送到 " + target.getName() + " 所在的 " + map.getId() + " " + mapName(map));
        });

        // was: "@gmshop" - opened shop 1337 for the typist
        r.add("gmshop", "", "打开 GM 商店：控制台没有客户端界面，只能提示在游戏里用 @gmshop", (ctx, args) -> {
            ctx.println("GM 商店是客户端界面，控制台无法打开。");
            ctx.println("如需给角色发道具，请用 item <角色名> <道具id> [数量]。");
        });

        // was: "@heal"
        r.add("heal", "<角色名>", "把该角色的 HP/MP 补满", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: heal <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            c.healHpMp();
            ctx.done(c.getName() + ": HP/MP 已补满");
        });

        // was: "@item <itemid> [数量]" - put the item in the typist's bag
        r.add("item", "<角色名> <道具id> [数量|天数]", "直接发道具到该角色背包；道具 id 是宠物时第二个参数是有效天数", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: item <角色名> <道具id> [数量]");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Client client = ctx.requireClient(args[0]);
            if (client == null) {
                return;
            }
            Integer itemId = ctx.num(args, 1, "道具id");
            if (itemId == null) {
                return;
            }
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            if (ii.getName(itemId) == null) {
                ctx.fail("道具 id " + itemId + " 不存在");
                return;
            }
            // 顺序和游戏内一致：先查现金道具开关，再走宠物分支。
            if (YamlConfig.config.server.BLOCK_GENERATE_CASH_ITEM && ii.isCash(itemId)) {
                ctx.fail("该配置禁止用此命令生成现金道具");
                return;
            }
            if (ItemConstants.isPet(itemId)) {
                Integer days = ctx.num(args, 2, "有效天数");
                if (days == null) {
                    return;
                }
                long expiration = System.currentTimeMillis()
                        + java.util.concurrent.TimeUnit.DAYS.toMillis(Math.max(1, days));
                InventoryManipulator.addById(client, itemId, (short) 1, c.getName(),
                        Pet.createPet(itemId), expiration);
                ctx.done(c.getName() + ": 已发放 " + ii.getName(itemId) + "，有效期 " + Math.max(1, days) + " 天");
                return;
            }
            Integer quantity = null;
            if (args.length > 2) {
                quantity = ctx.num(args, 2, "数量");
                if (quantity == null) {
                    return;
                }
            }
            short amount = (short) (quantity == null ? 1 : Math.max(1, Math.min(quantity, Short.MAX_VALUE)));
            // 游戏内 rank<3 的 GM 发东西会被打上账号共享/不可交易标记；控制台的操作者是超级用户，
            // 相当于 rank>=3，所以这里不加标记。
            short flag = 0;
            InventoryManipulator.addById(client, itemId, amount, c.getName(), -1, flag, -1);
            ctx.done(c.getName() + ": 已发放 " + ii.getName(itemId) + " x" + amount);
        });

        // was: "@drop <itemid> [数量]" - dropped the item at the typist's feet
        r.add("drop", "<地图id> <道具id> [数量|天数]", "在指定地图上生成掉落物（装备会随机出属性）；道具 id 是宠物时第二个参数是有效天数", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: drop <地图id> <道具id> [数量]");
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
            // 掉落物一定要记在一个真实角色名下（MapItem 构造里就读 owner.getId()/getClient()），
            // 控制台没有操作者，只能取图上任意一名在线玩家充当掉落来源。
            List<Character> owners = new ArrayList<>(map.getAllPlayers());
            if (owners.isEmpty()) {
                ctx.fail("地图 " + mapid + " 上没有玩家：控制台生成掉落物需要一个归属角色");
                return;
            }
            Character dropper = owners.get(0);
            Integer itemId = ctx.num(args, 1, "道具id");
            if (itemId == null) {
                return;
            }
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            if (ii.getName(itemId) == null) {
                ctx.fail("道具 id " + itemId + " 不存在");
                return;
            }
            // 顺序和游戏内一致：先查现金道具开关，再走宠物分支。
            if (YamlConfig.config.server.BLOCK_GENERATE_CASH_ITEM && ii.isCash(itemId)) {
                ctx.fail("该配置禁止用此命令生成现金道具");
                return;
            }
            Portal spawnPoint = map.getRandomPlayerSpawnpoint();
            if (spawnPoint == null) {
                ctx.fail("地图 " + mapid + " 没有可用出生点，无法放置掉落物");
                return;
            }
            if (ItemConstants.isPet(itemId)) {
                Integer days = ctx.num(args, 2, "有效天数");
                if (days == null) {
                    return;
                }
                Item toDrop = new Item(itemId, (short) 0, (short) 1, Pet.createPet(itemId));
                toDrop.setExpiration(System.currentTimeMillis()
                        + java.util.concurrent.TimeUnit.DAYS.toMillis(Math.max(1, days)));
                toDrop.setOwner("");
                map.spawnItemDrop(dropper, dropper, toDrop, spawnSpot(spawnPoint), true, true);
                ctx.done("地图 " + mapid + ": 已生成 " + ii.getName(itemId) + "（有效期 " + Math.max(1, days) + " 天，归属 " + dropper.getName() + "）");
                return;
            }
            Integer quantity = null;
            if (args.length > 2) {
                quantity = ctx.num(args, 2, "数量");
                if (quantity == null) {
                    return;
                }
            }
            short amount = (short) (quantity == null ? 1 : Math.max(1, Math.min(quantity, Short.MAX_VALUE)));
            Item toDrop = ItemConstants.getInventoryType(itemId) == InventoryType.EQUIP
                    ? ii.getEquipById(itemId)
                    : new Item(itemId, (short) 0, amount);
            // 游戏内把掉落物记在施法 GM 名下，这里记在那个被当作掉落来源的玩家名下；
            // 低 rank GM 才会被套上 TRIAL-MODE，控制台相当于超级用户，所以不打那个标记。
            toDrop.setOwner(dropper.getName());
            map.spawnItemDrop(dropper, dropper, toDrop, spawnSpot(spawnPoint), true, true);
            ctx.done("地图 " + mapid + ": 已生成 " + ii.getName(itemId) + " x" + amount + "，归属 " + dropper.getName());
        });

        // was: "@level <新等级>"
        r.add("level", "<角色名> <新等级>", "把该角色的等级设成指定值（受职业上限约束），会掉光当前经验后升上去", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: level <角色名> <新等级>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "等级");
            if (value == null) {
                return;
            }
            applyLevel(ctx, c, value);
        });

        // was: "@levelpro <新等级>" - levelled one at a time
        r.add("levelpro", "<角色名> <新等级>", "逐级升级该角色（每级都走一次完整的升级流程，等级差很大时会比较慢）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: levelpro <角色名> <新等级>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "等级");
            if (value == null) {
                return;
            }
            int target = Math.min(c.getMaxClassLevel(), value);
            int guard = 0;
            while (c.getLevel() < target && guard++ < 512) {
                c.levelUp(false);
            }
            ctx.done(c.getName() + ": 已逐级升到 " + c.getLevel() + (guard > 512 ? "（触发了保护上限，请检查）" : ""));
        });

        // was: "@setslot <格子数>"
        r.add("setslot", "<角色名> <格子数>", "把该角色各背包栏的格子数调整为指定值（按 4 的倍数向下取整）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: setslot <角色名> <格子数>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "格子数");
            if (value == null) {
                return;
            }
            int slots = (value / 4) * 4;
            List<String> changes = new ArrayList<>();
            for (int i = 1; i < 5; i++) {
                int curSlots = c.getSlots(i);
                if (slots <= -curSlots) {
                    continue;
                }
                c.gainSlots(i, slots - curSlots, true);
                changes.add("栏位" + i + ": " + curSlots + "->" + c.getSlots(i));
            }
            ctx.done(c.getName() + ": " + String.join(", ", changes));
        });

        // was: "@setstat <属性值>"
        r.add("setstat", "<角色名> <属性值>", "把该角色的力量/敏捷/智力/运气全部设为指定值（4 ~ 32767 限幅）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: setstat <角色名> <属性值>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer value = ctx.num(args, 1, "属性值");
            if (value == null) {
                return;
            }
            int x = Math.min(value, Short.MAX_VALUE);
            if (x < 4) {
                x = 4;
            }
            c.updateStrDexIntLuk(x);
            ctx.done(c.getName() + ": 力量/敏捷/智力/运气已设为 " + x);
        });

        // was: "@maxstat"
        r.add("maxstat", "<角色名>", "把该角色练满：等级 255、四维拉满、名声 13337、HP/MP 30000", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: maxstat <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            c.loseExp(c.getExp(), false, false);
            c.setLevel(255);
            c.resetPlayerRates();
            if (YamlConfig.config.server.USE_ADD_RATES_BY_LEVEL) {
                c.setPlayerRates();
            }
            c.setWorldRates();
            c.updateStrDexIntLuk(Short.MAX_VALUE);
            c.setFame(13337);
            c.updateMaxHpMaxMp(30000, 30000);
            c.updateSingleStat(client.Stat.LEVEL, 255);
            c.updateSingleStat(client.Stat.FAME, 13337);
            ctx.done(c.getName() + ": 属性已练满");
        });

        // was: "@maxskill"
        r.add("maxskill", "<角色名>", "把该角色所有技能点满", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: maxskill <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            int maxed = 0;
            for (Data skillData : skillEntries()) {
                Skill skill = parseSkill(skillData);
                if (skill == null) {
                    continue;
                }
                c.changeSkillLevel(skill, (byte) skill.getMaxLevel(), skill.getMaxLevel(), -1);
                maxed++;
            }
            applyCompanionSkill(c);
            ctx.done(c.getName() + ": 已点满 " + maxed + " 个技能");
        });

        // was: "@resetskill"
        r.add("resetskill", "<角色名>", "把该角色所有技能等级清零", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: resetskill <角色名>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            int reset = 0;
            for (Data skillData : skillEntries()) {
                Skill skill = parseSkill(skillData);
                if (skill == null) {
                    continue;
                }
                c.changeSkillLevel(skill, (byte) 0, skill.getMaxLevel(), -1);
                reset++;
            }
            applyCompanionSkill(c);
            ctx.done(c.getName() + ": 已重置 " + reset + " 个技能");
        });

        // was: "@search <type> <name>" - answered through an NPC dialog window
        r.add("search", "<类型> <名称>", "在 String.wz / 物品表 / 任务表里按名字搜索，类型可以是 map/quest/npc/item/mob/monster/skill", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: search <类型> <名称>   类型: MAP, QUEST, NPC, ITEM, MOB, MONSTER, SKILL");
                return;
            }
            String type = args[0].toUpperCase();
            String search = ctx.rest(args, 1);
            long start = System.currentTimeMillis();
            int hits = 0;
            switch (type) {
                case "NPC":
                    hits = searchStrings(ctx, "Npc.img", search, 200);
                    break;
                case "MOB":
                case "MONSTER":
                    hits = searchStrings(ctx, "Mob.img", search, 200);
                    break;
                case "SKILL":
                    hits = searchStrings(ctx, "Skill.img", search, 200);
                    break;
                case "MAP":
                    hits = searchMaps(ctx, search, 200);
                    break;
                case "QUEST":
                    hits = searchQuests(ctx, search, 200);
                    break;
                case "ITEM":
                    hits = searchItems(ctx, search, 200);
                    break;
                default:
                    ctx.fail("无效类型，可用: MAP, QUEST, NPC, ITEM, MOB, MONSTER, SKILL");
                    return;
            }
            ctx.printf("共 %d 条结果，耗时 %.3f 秒（结果超过 200 条时只打印前 200 条）%n",
                    hits, (System.currentTimeMillis() - start) / 1000.0);
        });

        // was: "@jail <玩家名> [分钟]"
        r.add("jail", "<角色名> [分钟=5]", "把该角色关进监狱指定分钟数", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: jail <角色名> [分钟]");
                return;
            }
            Character victim = ctx.require(args[0]);
            if (victim == null) {
                return;
            }
            int minutes = 5;
            if (args.length > 1) {
                Integer parsed = ctx.num(args, 1, "关押分钟数");
                if (parsed == null) {
                    return;
                }
                if (parsed <= 0) {
                    ctx.fail("关押分钟数必须大于 0");
                    return;
                }
                minutes = parsed;
            }
            victim.addJailExpirationTime(java.util.concurrent.TimeUnit.MINUTES.toMillis(minutes));
            if (victim.getMapId() == MapId.JAIL) {
                ctx.done(victim.getName() + ": 已在监狱中，刑期追加 " + minutes + " 分钟");
                return;
            }
            MapleMap jail = ctx.requireMap(MapId.JAIL);
            if (jail == null) {
                return;
            }
            Portal portal = jail.getPortal(0);
            victim.saveLocationOnWarp();
            victim.changeMap(jail, portal);
            ctx.done(victim.getName() + ": 已关入监狱 " + minutes + " 分钟");
        });

        // was: "@unjail <玩家名>"
        r.add("unjail", "<角色名>", "解除该角色的监禁", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: unjail <角色名>");
                return;
            }
            Character victim = ctx.require(args[0]);
            if (victim == null) {
                return;
            }
            if (victim.getJailExpirationTimeLeft() <= 0) {
                ctx.fail(victim.getName() + " 本来就没被关");
                return;
            }
            victim.removeJailExpirationTime();
            victim.message("By lack of concrete proof you are now unjailed. Enjoy freedom!");
            ctx.done(victim.getName() + ": 已解除监禁");
        });

        // was: "@job <职业id> [玩家名]"
        r.add("job", "<角色名> <职业id>", "把该角色转职成指定职业（0 ~ 2199）", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: job <角色名> <职业id>");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            Integer jobid = ctx.num(args, 1, "职业id");
            if (jobid == null) {
                return;
            }
            if (jobid < 0 || jobid >= 2200) {
                ctx.fail("职业 id " + jobid + " 不可用（有效范围 0 ~ 2199）");
                return;
            }
            c.changeJob(Job.getById(jobid));
            c.equipChanged();
            ctx.done(c.getName() + ": 已转职为职业 " + jobid);
        });

        // was: "@unbug" - re-enabled actions for everyone on the typist's map
        r.add("unbug", "<地图id>", "让这张地图上的客户端重新接收操作许可（卡住时可用）", (ctx, args) -> {
            Integer mapid = firstMapArg(ctx, "unbug", args);
            if (mapid == null) {
                return;
            }
            MapleMap map = ctx.requireMap(mapid);
            if (map == null) {
                return;
            }
            map.broadcastMessage(tools.PacketCreator.enableActions());
            ctx.done("地图 " + mapid + ": 已向 " + map.getAllPlayers().size() + " 名玩家发送解除卡住指令");
        });

        // was: "@id <type> <query>" - handbook lookup answered through an NPC dialog
        r.add("id", "<类型> <关键词...>", "在手册文件里按名字查 id，类型可以是 map/etc/npc/use/weapon，最多返回 100 条", (ctx, args) -> {
            if (GmContext.missing(args, 2)) {
                ctx.println("用法: id <类型> <关键词>   类型: map, etc, npc, use, weapon");
                return;
            }
            String type = args[0];
            String query = ctx.rest(args, 1);
            String relative = handbookPath(type);
            if (relative == null) {
                ctx.fail("不支持的类型 " + type + "，可用: map, etc, npc, use, weapon");
                return;
            }
            List<String> lines = readHandbook(relative);
            if (lines == null) {
                ctx.fail("找不到手册文件 " + relative
                        + "（游戏内 @id 读的是同一个路径，这个仓库里没有导出这些文件，所以游戏里也查不到）");
                ctx.println("可以改用 search <类型> <名称>，它直接查 String.wz 和物品表，不依赖手册文件。");
                return;
            }
            int printed = 0;
            int found = 0;
            String needle = query.toLowerCase();
            for (String line : lines) {
                String[] parts = line.split(" - ", 2);
                if (parts.length < 2 || parts[1].isBlank()) {
                    continue;
                }
                // 游戏内的手册匹配是不区分大小写的子串查找。
                if (!parts[1].toLowerCase().contains(needle)) {
                    continue;
                }
                found++;
                if (printed < 100) {
                    ctx.printf("  %-10s %s%n", parts[0], parts[1]);
                    printed++;
                }
            }
            // 游戏内那条 NPC 对话的总结语是 "Results found: X | Returned: Y/100"。
            ctx.done("类型 " + type + " 中匹配 \"" + query + "\" 共 " + found + " 条，已打印 " + printed + "/100"
                    + (found > printed ? "（其余请缩小关键词后再查）" : ""));
        });

        // was: "@gachalist" - opened the gachapon guide NPC dialog
        r.add("gachalist", "", "扭蛋机说明：游戏内是打开一个 NPC 对话窗口，控制台没有客户端界面，只能给出对应的编号", (ctx, args) -> {
            ctx.println("扭蛋机说明在控制台里看不到：它原本是给客户端弹 NPC 对话窗口的。");
            ctx.println("游戏内等价调用是 openNpc(" + GACHA_INFO_NPC + ", \"gachaponInfo\")，"
                    + "即 NPC " + GACHA_INFO_NPC + " 的 gachaponInfo 脚本。");
            ctx.println("本仓库的脚本目录里没有 gachaponInfo.js，所以游戏内执行这条命令也只会开一个空的 NPC 窗口。");
            ctx.println("要查扭蛋奖品，请在游戏里找扭蛋 NPC，或用 search item <关键词> 按名字查道具。");
        });

        // was: "@loot" - picked up the typist's own drops
        r.add("loot", "<角色名> [地图id]", "让该角色把地图上属于他的掉落物全部捡起来；不填地图时用他当前所在的地图", (ctx, args) -> {
            if (GmContext.missing(args, 1)) {
                ctx.println("用法: loot <角色名> [地图id]");
                return;
            }
            Character c = ctx.require(args[0]);
            if (c == null) {
                return;
            }
            MapleMap map;
            if (args.length > 1) {
                Integer mapid = ctx.num(args, 1, "地图id");
                if (mapid == null) {
                    return;
                }
                map = ctx.requireMap(mapid);
            } else {
                map = ctx.mapOf(c);
            }
            if (map == null) {
                return;
            }
            int picked = 0;
            List<MapObject> items = map.getMapObjectsInRange(c.getPosition(), Double.POSITIVE_INFINITY,
                    Arrays.asList(server.maps.MapObjectType.ITEM));
            for (MapObject item : items) {
                if (!(item instanceof MapItem mapItem)) {
                    continue;
                }
                // 和游戏内 @loot 一样：自己的 or 队伍的掉落物都能捡
                if (mapItem.getOwnerId() == c.getId() || mapItem.getOwnerId() == c.getPartyId()) {
                    c.pickupItem(mapItem);
                    picked++;
                }
            }
            ctx.done(c.getName() + " 在地图 " + map.getId() + " 拾取了 " + picked + " 件属于自己的掉落物");
        });

        // was: "@mobskill <技能id> <等级>" - applied a mob skill to every monster on the typist's map
        r.add("mobskill", "<地图id> <技能id> <等级>", "对这张地图上的所有怪释放指定的怪物技能", (ctx, args) -> {
            if (GmContext.missing(args, 3)) {
                ctx.println("用法: mobskill <地图id> <怪物技能id> <等级>");
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
            Integer skillId = ctx.num(args, 1, "怪物技能id");
            if (skillId == null) {
                return;
            }
            Integer level = ctx.num(args, 2, "技能等级");
            if (level == null) {
                return;
            }
            Optional<MobSkillType> type = MobSkillType.from(skillId);
            Optional<MobSkill> skill = type.flatMap(t -> MobSkillFactory.getMobSkill(t, level));
            if (skill.isEmpty()) {
                ctx.fail("怪物技能 " + skillId + " 等级 " + level + " 不存在");
                return;
            }
            MobSkill mobSkill = skill.get();
            // 游戏内是把敲命令的 GM 本人当成 DISPEL/BANISH 的承受者，控制台没有操作者，
            // 只能取图上第一个玩家顶上；没有玩家时这两类技能会空指针，所以直接拒绝。
            List<Character> players = new ArrayList<>(map.getAllPlayers());
            Character subject = players.isEmpty() ? null : players.get(0);
            MobSkillType skillType = mobSkill.getId().type();
            if (subject == null && (skillType == MobSkillType.DISPEL || skillType == MobSkillType.BANISH)) {
                ctx.fail("怪物技能 " + skillId + " 需要一个承受效果的玩家，地图 " + mapid + " 上没有玩家");
                return;
            }
            int applied = 0;
            for (Monster mob : map.getAllMonsters()) {
                if (mob == null) {
                    continue;
                }
                if (subject == null) {
                    mobSkill.applyEffect(mob);
                } else {
                    mobSkill.applyEffect(subject, mob, false, new ArrayList<>());
                }
                applied++;
            }
            ctx.done("地图 " + mapid + ": 已对 " + applied + " 只怪释放怪物技能 " + skillId + " 等级 " + level
                    + (subject == null ? "" : "（承受者按 " + subject.getName() + " 计算）"));
        });
    }

    // ---------------------------------------------------------------- helpers

    /** The NPC id the in-game @gachalist hands to openNpc(...). */
    private static final int GACHA_INFO_NPC = 10000;

    /**
     * The first argument is a map id for the map-scoped rewrites; prints a usage line when it is
     * missing or not a number. The caller hands over the command name so the hint reads correctly
     * even when the operator already typed a bad first argument.
     */
    private static Integer firstMapArg(GmContext ctx, String cmd, String[] args) {
        if (GmContext.missing(args, 1)) {
            ctx.println("用法: " + cmd + " <地图id>");
            ctx.println("这个命令在控制台中需要显式给出地图 id。");
            return null;
        }
        return ctx.num(args, 0, "地图id");
    }

    private static int clampStat(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, YamlConfig.config.server.MAX_AP);
    }

    private static String mapName(MapleMap map) {
        try {
            String n = map.getMapName();
            return n == null ? "" : n;
        } catch (Exception e) {
            return "";
        }
    }

    private static void summarize(GmContext ctx, String label, int count) {
        ctx.printf("  %-12s %d%n", label, count);
    }

    /** 出生点坐标就是掉落位置；Portal 自己给的是 android.graphics.Point。 */
    private static android.graphics.Point spawnSpot(Portal portal) {
        return portal.getPosition();
    }

    private static void applyLevel(GmContext ctx, Character c, int requested) {
        c.loseExp(c.getExp(), false, false);
        c.setLevel(Math.min(requested, c.getMaxClassLevel()) - 1);
        c.resetPlayerRates();
        if (YamlConfig.config.server.USE_ADD_RATES_BY_LEVEL) {
            c.setPlayerRates();
        }
        c.setWorldRates();
        c.levelUp(false);
        ctx.done(c.getName() + ": 等级已设为 " + c.getLevel());
    }

    private static List<InventoryType> resolveInventoryTypes(String label) {
        String key = label.toLowerCase();
        switch (key) {
            case "all":
                return Arrays.asList(InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC,
                        InventoryType.SETUP, InventoryType.CASH);
            case "equip":
                return Arrays.asList(InventoryType.EQUIP);
            case "use":
                return Arrays.asList(InventoryType.USE);
            case "setup":
                return Arrays.asList(InventoryType.SETUP);
            case "etc":
                return Arrays.asList(InventoryType.ETC);
            case "cash":
                return Arrays.asList(InventoryType.CASH);
            default:
                return null;
        }
    }

    /** Every numeric child of String.img/Skill.img, which is what the in-game loop iterated. */
    private static List<Data> skillEntries() {
        Data data = DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Skill.img");
        return data == null ? new ArrayList<>() : data.getChildren();
    }

    private static Skill parseSkill(Data entry) {
        int id;
        try {
            id = Integer.parseInt(entry.getName());
        } catch (NumberFormatException e) {
            return null;
        }
        return SkillFactory.getSkill(id);
    }

    /** The in-game commands also reset one combo/skill-book entry; kept for parity. */
    private static void applyCompanionSkill(Character c) {
        boolean legend = (c.getJob() != null && (c.getJob().isA(Job.ARAN1) || c.getJob().isA(Job.LEGEND)));
        Skill skill = SkillFactory.getSkill(legend ? 5001005 : 21001001);
        if (skill != null) {
            c.changeSkillLevel(skill, (byte) -1, -1, -1);
        }
    }

    private static int searchStrings(GmContext ctx, String fileName, String search, int cap) {
        Data data = DataProviderFactory.getDataProvider(WZFiles.STRING).getData(fileName);
        if (data == null) {
            ctx.fail(fileName + " 读取失败");
            return 0;
        }
        String needle = search.toLowerCase();
        int shown = 0;
        int total = 0;
        for (Data entry : data.getChildren()) {
            String name = DataTool.getString(entry.getChildByPath("name"), "NO-NAME");
            if (name.toLowerCase().contains(needle)) {
                total++;
                if (shown < cap) {
                    ctx.printf("  %s - %s%n", entry.getName(), name);
                    shown++;
                }
            }
        }
        if (total == 0) {
            ctx.println("没有匹配 \"" + search + "\" 的结果");
        }
        return total;
    }

    private static int searchMaps(GmContext ctx, String search, int cap) {
        Data data = DataProviderFactory.getDataProvider(WZFiles.STRING).getData("Map.img");
        if (data == null) {
            ctx.fail("Map.img 读取失败");
            return 0;
        }
        String needle = search.toLowerCase();
        int shown = 0;
        int total = 0;
        for (Data street : data.getChildren()) {
            for (Data entry : street.getChildren()) {
                String mapName = DataTool.getString(entry.getChildByPath("mapName"), "NO-NAME");
                String streetName = DataTool.getString(entry.getChildByPath("streetName"), "NO-NAME");
                if (mapName.toLowerCase().contains(needle) || streetName.toLowerCase().contains(needle)) {
                    total++;
                    if (shown < cap) {
                        ctx.printf("  %s - %s - %s%n", entry.getName(), streetName, mapName);
                        shown++;
                    }
                }
            }
        }
        if (total == 0) {
            ctx.println("没有匹配 \"" + search + "\" 的地图");
        }
        return total;
    }

    private static int searchQuests(GmContext ctx, String search, int cap) {
        int shown = 0;
        int total = 0;
        for (Quest quest : Quest.getMatchedQuests(search)) {
            if (quest == null) {
                continue;
            }
            total++;
            if (shown >= cap) {
                continue;
            }
            String parent = quest.getParentName();
            ctx.printf("  %d - %s%s%n", quest.getId(),
                    (parent == null || parent.isEmpty()) ? "" : parent + " - ", quest.getName());
            shown++;
        }
        if (total == 0) {
            ctx.println("没有匹配 \"" + search + "\" 的任务");
        }
        return total;
    }

    private static int searchItems(GmContext ctx, String search, int cap) {
        String needle = search.toLowerCase();
        int shown = 0;
        int total = 0;
        for (Pair<Integer, String> pair : ItemInformationProvider.getInstance().getAllItems()) {
            if (pair == null || pair.getRight() == null) {
                continue;
            }
            if (pair.getRight().toLowerCase().contains(needle)) {
                total++;
                if (shown < cap) {
                    ctx.printf("  %d - %s%n", pair.getLeft(), pair.getRight());
                    shown++;
                }
            }
        }
        if (total == 0) {
            ctx.println("没有匹配 \"" + search + "\" 的道具");
        }
        return total;
    }

    private static String handbookPath(String type) {
        switch (type.toLowerCase()) {
            case "map":
                return "handbook/Map.txt";
            case "etc":
                return "handbook/Etc.txt";
            case "npc":
                return "handbook/NPC.txt";
            case "use":
                return "handbook/Use.txt";
            case "weapon":
                return "handbook/Equip/Weapon.txt";
            default:
                return null;
        }
    }

    /**
     * The in-game command read these straight from the working directory, so the console tries the
     * same relative path plus the usual places a checkout keeps them - including the assets folder
     * the desktop launcher points at with -Dmaple.assets.
     */
    private static List<String> readHandbook(String relative) {
        String assets = System.getProperty("maple.assets");
        List<String> candidates = new ArrayList<>(Arrays.asList(
                relative, "../" + relative, "../../" + relative, "data/" + relative));
        if (assets != null && !assets.isEmpty()) {
            candidates.add(assets + "/" + relative);
        }
        for (String candidate : candidates) {
            try {
                Path path = Paths.get(candidate);
                if (Files.isRegularFile(path)) {
                    return Files.readAllLines(path);
                }
            } catch (IOException ignored) {
                // try the next candidate
            }
        }
        return null;
    }
}
