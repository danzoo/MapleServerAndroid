package desktop.console.gm;

import client.Character;
import client.Client;
import net.server.Server;
import net.server.channel.Channel;
import server.maps.MapManager;
import server.maps.MapleMap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Everything a console-side command needs that the in-game side used to get from a Client:
// output, subject resolution, map lookup, argument parsing.
//
// The helpers are deliberately chatty: on bad input they print the reason and return null (or -1),
// so an implementation is just "resolve, bail if null, act, print the result".
public class GmContext {

    private final PrintStream out;

    public GmContext() {
        this(System.out);
    }

    public GmContext(PrintStream out) {
        this.out = out;
    }

    // ------------------------------------------------------------------ output

    public void println(String s) {
        out.println(s);
    }

    public void printf(String fmt, Object... args) {
        out.printf(fmt, args);
    }

    // One-line failure, used by the helpers below and by commands that reject their input.
    public void fail(String reason) {
        out.println("! " + reason);
    }

    // One-line success/summary.
    public void done(String what) {
        out.println("= " + what);
    }

    public void syntax(GmCmd cmd) {
        String u = cmd.usage();
        out.println("用法: " + cmd.name() + (u.isEmpty() ? "" : " " + u));
        if (!cmd.description().isEmpty()) {
            out.println("  " + cmd.description());
        }
    }

    // Not enough arguments - the most common bail-out.
    public void needArgs(GmCmd cmd) {
        syntax(cmd);
    }

    public static boolean missing(String[] args, int need) {
        return args.length < need;
    }

    // ------------------------------------------------------------------ subjects (characters)

    public List<Channel> channels() {
        List<Channel> all = Server.getInstance().getAllChannels();
        return all != null ? all : new ArrayList<>(0);
    }

    public List<Character> allOnline() {
        List<Character> out = new ArrayList<>();
        for (Channel ch : channels()) {
            out.addAll(ch.getPlayerStorage().getAllCharacters());
        }
        return out;
    }

    // Online character by exact name, across every world and channel; null when nobody by that
    // name is connected.
    public Character online(String name) {
        for (Channel ch : channels()) {
            Character c = ch.getPlayerStorage().getCharacterByName(name);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    // Prints "no online character" and returns null when the name resolves to nobody, so the
    // caller only has to write `if (c == null) return;`.
    public Character require(String name) {
        Character c = online(name);
        if (c == null) {
            fail("没有在线的角色叫 " + name + "（用 online 看在线列表）");
        }
        return c;
    }

    public Client requireClient(String name) {
        Character c = require(name);
        if (c == null) {
            return null;
        }
        Client cl = c.getClient();
        if (cl == null) {
            fail(name + " 已经掉线，没有可用的连接");
        }
        return cl;
    }

    public void printCharacter(Character c) {
        MapleMap m = mapOf(c);
        printf("    %-16s Lv%-4d map=%-10d id=%-8d [world %d/ch %d]%n",
                c.getName(), c.getLevel(), m != null ? m.getId() : -1, c.getId(),
                worldOf(c), channelOf(c));
    }

    public MapleMap mapOf(Character c) {
        try {
            return c.getMap();
        } catch (Exception e) {
            return null;
        }
    }

    public int worldOf(Character c) {
        for (Channel ch : channels()) {
            if (ch.getPlayerStorage().getCharacterByName(c.getName()) != null) {
                return ch.getWorld();
            }
        }
        return -1;
    }

    public int channelOf(Character c) {
        for (Channel ch : channels()) {
            if (ch.getPlayerStorage().getCharacterByName(c.getName()) != null) {
                return ch.getId();
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ maps

    // The channel that should host a map: one where it is loaded and populated, else one where it
    // is loaded, else the first channel (getMap loads it from wz there).
    public Channel channelFor(int mapid) {
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

    // Null only when no channel exists at all; a missing map id yields whatever MapManager returns
    // (null in this fork, which is the documented "map not found" answer).
    public MapleMap map(int mapid) {
        Channel ch = channelFor(mapid);
        return ch == null ? null : ch.getMapFactory().getMap(mapid);
    }

    public MapleMap requireMap(int mapid) {
        Channel ch = channelFor(mapid);
        if (ch == null) {
            fail("服务端还没有任何频道，无法定位地图 " + mapid);
            return null;
        }
        MapleMap m = ch.getMapFactory().getMap(mapid);
        if (m == null) {
            fail("地图 " + mapid + " 不存在（服务端资源里没有这张图）");
        }
        return m;
    }

    // Every map that currently holds at least one player, de-duplicated across channels. Used by
    // the "affects the whole server" commands that in-game could only reach one map at a time.
    public List<MapleMap> loadedMapsWithPlayers() {
        List<MapleMap> out = new ArrayList<>();
        for (Channel ch : channels()) {
            for (Character c : ch.getPlayerStorage().getAllCharacters()) {
                MapleMap m = mapOf(c);
                if (m != null && !containsId(out, m.getId())) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static boolean containsId(List<MapleMap> list, int id) {
        for (MapleMap m : list) {
            if (m.getId() == id) {
                return true;
            }
        }
        return false;
    }

    // The one map a named character is standing on, resolved for the player-scoped commands that
    // still need its geometry.
    public MapleMap mapOfPlayer(String name) {
        Character c = require(name);
        if (c == null) {
            return null;
        }
        MapleMap m = mapOf(c);
        if (m == null) {
            fail(c.getName() + " 正在传送中，没有当前地图");
        }
        return m;
    }

    // ------------------------------------------------------------------ worlds

    public List<net.server.world.World> worlds() {
        List<net.server.world.World> all = Server.getInstance().getWorlds();
        return all != null ? all : new ArrayList<>(0);
    }

    public net.server.world.World world(int id) {
        for (net.server.world.World w : worlds()) {
            if (w.getId() == id) {
                return w;
            }
        }
        return null;
    }

    public net.server.world.World requireWorld(int id) {
        net.server.world.World w = world(id);
        if (w == null) {
            fail("世界 " + id + " 不存在（现有: " + worldIds() + "）");
        }
        return w;
    }

    public String worldIds() {
        StringBuilder sb = new StringBuilder();
        for (net.server.world.World w : worlds()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(w.getId());
        }
        return sb.length() == 0 ? "无" : sb.toString();
    }

    // ------------------------------------------------------------------ arguments

    // Parses args[i], prints the reason and returns null when it is absent or not a number.
    public Integer num(String[] args, int i, String what) {
        if (i >= args.length) {
            fail("缺少参数: " + what);
            return null;
        }
        return num(args[i], what);
    }

    public Integer num(String s, String what) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            fail(what + " 需要是一个整数，收到的是: " + s);
            return null;
        }
    }

    public Long num64(String s, String what) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            fail(what + " 需要是一个整数，收到的是: " + s);
            return null;
        }
    }

    // Joins args[from..] back into one string with single spaces - for free-text arguments such as
    // notice bodies. Case is preserved.
    public String rest(String[] args, int from) {
        return rest(args, from, args.length);
    }

    // Same, over args[from, to).
    public String rest(String[] args, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    public void printPlayers(Collection<Character> chars) {
        for (Character c : chars) {
            printCharacter(c);
        }
    }
}
