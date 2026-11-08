package desktop.console;

import android.graphics.Point;
import client.Character;
import client.inventory.manipulator.InventoryManipulator;
import net.server.Server;
import net.server.channel.Channel;
import server.ItemInformationProvider;
import server.life.LifeFactory;
import server.life.Monster;
import server.maps.MapManager;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// stdin-driven admin console for the Windows desktop server (the Android build has no console).
// Reuses the same core APIs the in-game GM commands use, so nothing here touches server-core.
// Started from DesktopServerMain after Server.main(); runs on a daemon thread and exits quietly
// when stdin reaches EOF (e.g. when the process is launched without an attached console).
public class ConsoleManager {

    private interface Handler {
        void run(String[] args);
    }

    private static final class Command {
        final String usage;
        final String description;
        final Handler handler;

        Command(String usage, String description, Handler handler) {
            this.usage = usage;
            this.description = description;
            this.handler = handler;
        }
    }

    private final Map<String, Command> commands = new LinkedHashMap<>();

    // The GM commands migrated from the in-game chat entry point (server-core/client/command).
    // Built-in console commands above are consulted first, so they win a name clash.
    private final desktop.console.gm.GmRegistry gm = new desktop.console.gm.GmRegistry();
    private final desktop.console.gm.GmContext gmctx = new desktop.console.gm.GmContext();

    // True only when a real terminal is attached. Closing that terminal (or Ctrl+Z / Ctrl+D on it)
    // ends stdin, which is the signal to take the whole server down with it; a launch without any
    // console (background/service) also sees stdin end immediately and must keep serving instead.
    private final boolean consoleAttached = System.console() != null;

    // How long the graceful shutdown gets to finish after the console closes before the process is
    // halted outright.
    private static final long SHUTDOWN_GRACE_MILLIS = 10_000;

    public ConsoleManager() {
        register("help", "", "List all console commands", this::cmdHelp);
        register("online", "", "List online characters (name, level, map, world/channel)", this::cmdOnline);
        register("where", "<name>", "Show an online character's current map id", this::cmdWhere);
        register("spawn", "<mapid> <mobid> [qty]", "Summon mob(s) on a map", this::cmdSpawn);
        register("clearmob", "<mapid>", "Kill all mobs on a map (no drops)", this::cmdClearMob);
        register("announce", "<text...>", "Broadcast a server notice to all online players", this::cmdAnnounce);
        register("banner", "<seconds> <text...>", "Show the banner across the top of every screen for a number of seconds", this::cmdBanner);
        register("kick", "<name>", "Disconnect an online character", this::cmdKick);
        register("give", "<name> <itemid> [qty]", "Give item(s) to an online character", this::cmdGive);
        register("giveexp", "<name> <amount>", "Give experience to an online character", this::cmdGiveExp);
        // aliases
        commands.put("killall", commands.get("clearmob"));
        commands.put("clear", commands.get("clearmob"));
        commands.put("whereis", commands.get("where"));
        commands.put("getmap", commands.get("where"));

        desktop.console.gm.GmCommands.registerAll(gm);
    }

    private void register(String name, String usage, String description, Handler handler) {
        commands.put(name, new Command(usage, description, handler));
    }

    public void start() {
        Thread t = new Thread(this::loop, "ServerConsole");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        System.out.println();
        System.out.println("Admin console ready. Type 'help' for commands.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, consoleCharset()));
        try {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String line = in.readLine();
                if (line == null) {
                    if (consoleAttached) {
                        shutdownServer();
                    }
                    break; // stdin closed
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                String name = tokens[0].toLowerCase();
                String[] args = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, args, 0, args.length);

                Command cmd = commands.get(name);
                if (cmd != null) {
                    run(name, () -> cmd.handler.run(args));
                    continue;
                }
                desktop.console.gm.GmCmd g = gm.get(name);
                if (g != null) {
                    run(name, () -> g.run(gmctx, args));
                    continue;
                }
                System.out.println("未知命令: " + name + "（help 看内置命令，help gm 看从游戏内迁移过来的 "
                        + gm.listed().size() + " 条 GM 命令）");
            }
        } catch (Exception e) {
            System.out.println("Console stopped: " + e);
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    // One command per line, and a crashed command must never take the console down with it.
    private void run(String name, Action action) {
        try {
            action.run();
        } catch (Exception e) {
            System.out.println("命令 '" + name + "' 执行失败: " + e);
            e.printStackTrace(System.out);
        }
    }

    // ------------------------------------------------------------------ commands

    // help            -> the console's own commands
    // help gm         -> every GM command migrated from the in-game chat entry point
    // help <keyword>  -> anything (built-in or migrated) whose name contains the keyword
    private void cmdHelp(String[] args) {
        String filter = args.length > 0 ? args[0].toLowerCase() : null;
        boolean gmOnly = "gm".equals(filter);

        if (filter == null || !gmOnly) {
            System.out.println("控制台内置命令:");
            for (Map.Entry<String, Command> e : commands.entrySet()) {
                String key = e.getKey();
                Command c = e.getValue();
                if (filter != null && !key.contains(filter)) {
                    continue;
                }
                String head = c.usage.isEmpty() ? key : key + " " + c.usage;
                System.out.printf("  %-28s %s%n", head, c.description);
            }
        }

        if (filter == null) {
            System.out.println();
            System.out.println("另有从游戏内 @/! 迁移过来的 " + gm.listed().size()
                    + " 条 GM 命令：help gm 看全部，help <关键字> 只看相关的几条。");
            System.out.println("注：help/online/spawn/banner 这四个名字游戏内也有同名命令，"
                    + "输入时一律走上面的内置实现（内置版本按 mapid/全世界来指定主体，功能覆盖游戏内那条）。");
            return;
        }
        if (!gmOnly && !filter.isEmpty()) {
            listGm(gmcmdsMatching(filter));
        } else {
            listGm(gm.listed());
        }
    }

    private java.util.List<desktop.console.gm.GmCmd> gmcmdsMatching(String keyword) {
        java.util.List<desktop.console.gm.GmCmd> out = new java.util.ArrayList<>();
        for (desktop.console.gm.GmCmd c : gm.listed()) {
            if (c.names().toLowerCase().contains(keyword)) {
                out.add(c);
            }
        }
        return out;
    }

    private void listGm(java.util.List<desktop.console.gm.GmCmd> list) {
        if (list.isEmpty()) {
            System.out.println("没有匹配的命令。");
            return;
        }
        System.out.println("GM 命令（迁移自游戏内 @/!，参数里的角色名要在线，地图id 直接给数字）:");
        for (desktop.console.gm.GmCmd c : list) {
            String head = c.name() + (c.usage().isEmpty() ? "" : " " + c.usage());
            System.out.printf("  %-34s %s%n", head, c.description());
            if (c.aliases().length > 0) {
                System.out.println("        别名: " + String.join(", ", c.aliases()));
            }
        }
    }

    private void cmdOnline(String[] args) {
        int total = 0;
        for (Channel ch : channels()) {
            Collection<Character> chars = ch.getPlayerStorage().getAllCharacters();
            if (chars.isEmpty()) {
                continue;
            }
            System.out.println("World " + ch.getWorld() + " / Channel " + ch.getId() + ":");
            for (Character c : chars) {
                MapleMap map = safeMap(c);
                System.out.println("    " + c.getName() + "  Lv" + c.getLevel()
                        + "  map=" + (map != null ? map.getId() : "?")
                        + "  id=" + c.getId());
                total++;
            }
        }
        System.out.println("Online: " + total);
    }

    private void cmdWhere(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: where <name>");
            return;
        }
        for (Channel ch : channels()) {
            Character c = ch.getPlayerStorage().getCharacterByName(args[0]);
            if (c != null) {
                MapleMap map = safeMap(c);
                System.out.println(c.getName() + " is on map " + (map != null ? map.getId() : "?")
                        + "  Lv" + c.getLevel()
                        + "  [world " + ch.getWorld() + "/ch " + ch.getId() + "]");
                return;
            }
        }
        System.out.println("No online character named: " + args[0]);
    }

    private void cmdSpawn(String[] args) {
        if (args.length < 2) {
            System.out.println("Syntax: spawn <mapid> <mobid> [qty]");
            return;
        }
        int mapid = parseInt(args[0], "mapid");
        int mobid = parseInt(args[1], "mobid");
        int qty = args.length >= 3 ? parseInt(args[2], "qty") : 1;
        if (qty < 1) {
            qty = 1;
        }

        Monster probe = LifeFactory.getMonster(mobid);
        if (probe == null) {
            System.out.println("Unknown mob id: " + mobid);
            return;
        }

        Channel ch = resolveChannelForMap(mapid);
        if (ch == null) {
            System.out.println("No channel available to host map " + mapid);
            return;
        }
        MapleMap map = ch.getMapFactory().getMap(mapid);

        Point pos = spawnPosition(map);
        for (int i = 0; i < qty; i++) {
            map.spawnMonsterOnGroundBelow(mobid, pos.x, pos.y);
        }
        System.out.println("Spawned " + qty + "x mob " + mobid + " on map " + mapid
                + " at (" + pos.x + "," + pos.y + ") [world " + ch.getWorld() + "/ch " + ch.getId() + "]");
    }

    private void cmdClearMob(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: clearmob <mapid>");
            return;
        }
        int mapid = parseInt(args[0], "mapid");
        Channel ch = resolveChannelForMap(mapid);
        if (ch == null) {
            System.out.println("No channel available to host map " + mapid);
            return;
        }
        MapleMap map = ch.getMapFactory().getMap(mapid);
        int before = countMobs(map);
        map.killAllMonsters();
        System.out.println("Cleared " + before + " mob(s) on map " + mapid
                + " [world " + ch.getWorld() + "/ch " + ch.getId() + "]");
    }

    private void cmdAnnounce(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: announce <text...>");
            return;
        }
        String text = String.join(" ", args);
        Server server = Server.getInstance();
        for (net.server.world.World w : server.getWorlds()) {
            server.broadcastMessage(w.getId(), PacketCreator.serverNotice(6, text));
        }
        System.out.println("Announced to all worlds: " + text);
    }

    private void cmdBanner(String[] args) {
        if (args.length < 2) {
            System.out.println("Syntax: banner <seconds> <text...>");
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Syntax: banner <seconds> <text...>");
            return;
        }

        if (seconds < 1 || seconds > 3600) {
            System.out.println("The banner can be held up for between 1 and 3600 seconds.");
            return;
        }

        String text = String.join(" ", args).substring(args[0].length()).trim();
        if (text.isEmpty()) {
            System.out.println("Syntax: banner <seconds> <text...>");
            return;
        }

        Server server = Server.getInstance();
        for (net.server.world.World w : server.getWorlds()) {
            server.broadcastMessage(w.getId(), PacketCreator.serverMessage(text, seconds));
        }
        System.out.println("Banner shown for " + seconds + "s on all worlds: " + text);
    }

    private void cmdKick(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: kick <name>");
            return;
        }
        Character victim = findPlayer(args[0]);
        if (victim == null) {
            System.out.println("No online character named: " + args[0]);
            return;
        }
        MapleMap map = safeMap(victim);
        victim.getClient().disconnect(true, false);
        if (map != null) {
            map.removePlayer(victim);
        }
        System.out.println("Kicked: " + victim.getName());
    }

    private void cmdGive(String[] args) {
        if (args.length < 2) {
            System.out.println("Syntax: give <name> <itemid> [qty]");
            return;
        }
        Character victim = findPlayer(args[0]);
        if (victim == null) {
            System.out.println("No online character named: " + args[0]);
            return;
        }
        int itemid = parseInt(args[1], "itemid");
        short qty = (short) (args.length >= 3 ? parseInt(args[2], "qty") : 1);

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (ii.getName(itemid) == null) {
            System.out.println("Unknown item id: " + itemid);
            return;
        }
        boolean ok = InventoryManipulator.addById(victim.getClient(), itemid, qty);
        System.out.println((ok ? "Gave " : "Failed to give ") + qty + "x item " + itemid
                + " to " + victim.getName());
    }

    private void cmdGiveExp(String[] args) {
        if (args.length < 2) {
            System.out.println("Syntax: giveexp <name> <amount>");
            return;
        }
        Character victim = findPlayer(args[0]);
        if (victim == null) {
            System.out.println("No online character named: " + args[0]);
            return;
        }
        int amount = parseInt(args[1], "amount");
        victim.gainExp(amount, true, true, true);
        System.out.println("Gave " + amount + " exp to " + victim.getName() + " (now Lv" + victim.getLevel() + ")");
    }

    // Closing the console window ends stdin. Take the JVM down through the core's own shutdown path
    // so worlds, channels, listening sockets and worker threads stop before the process exits,
    // instead of leaving a headless server holding the ports. The graceful path is raced against a
    // grace period: if some shutdown wait (e.g. a channel that never reports finished) hangs, the
    // process is halted anyway so closing the console always ends every server process.
    private static void shutdownServer() {
        System.out.println("Console closed - shutting the server down.");
        System.out.flush();

        Thread graceful = new Thread(() -> Server.getInstance().shutdown(false).run(), "ConsoleShutdown");
        graceful.setDaemon(true);
        graceful.start();

        try {
            graceful.join(SHUTDOWN_GRACE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Runtime.getRuntime().halt(0);
    }

    // ------------------------------------------------------------------ helpers

    // The console speaks the OS codepage (GBK on a Chinese Windows terminal), not UTF-8. Reading
    // stdin as UTF-8 there turns every typed CJK character into U+FFFD, which then rides the packet
    // to the client as a tofu box. stdin.encoding is the attached console's charset on JDK 19+.
    private static Charset consoleCharset() {
        String name = System.getProperty("stdin.encoding",
                System.getProperty("native.encoding", "UTF-8"));
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static List<Channel> channels() {
        List<Channel> all = Server.getInstance().getAllChannels();
        return all != null ? all : new ArrayList<>(0);
    }

    // Pick the channel that should host a map: prefer one where the map is already loaded and has
    // players on it, then any channel where it is loaded, then fall back to the first channel
    // (getMap will load it from wz there).
    private static Channel resolveChannelForMap(int mapid) {
        Channel loaded = null;
        for (Channel ch : channels()) {
            MapManager mm = ch.getMapFactory();
            if (mm.isMapLoaded(mapid)) {
                MapleMap m = mm.getMap(mapid);
                if (m != null && !m.getCharacters().isEmpty()) {
                    return ch;
                }
                if (loaded == null) {
                    loaded = ch;
                }
            }
        }
        if (loaded != null) {
            return loaded;
        }
        List<Channel> all = channels();
        return all.isEmpty() ? null : all.get(0);
    }

    // Spawn on top of a player when one is present, otherwise at the map origin (snapped to ground).
    private static Point spawnPosition(MapleMap map) {
        Collection<Character> chars = map.getCharacters();
        if (chars != null && !chars.isEmpty()) {
            Character c = chars.iterator().next();
            Point p = c.getPosition();
            if (p != null) {
                return new Point(p.x, p.y);
            }
        }
        return new Point(0, 0);
    }

    private static int countMobs(MapleMap map) {
        return map.countMonsters();
    }

    private static Character findPlayer(String name) {
        for (Channel ch : channels()) {
            Character c = ch.getPlayerStorage().getCharacterByName(name);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static MapleMap safeMap(Character c) {
        try {
            return c.getMap();
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s, String what) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + what + ": " + s);
        }
    }
}
