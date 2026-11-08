package desktop.console.gm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The command table the console dispatches into, keyed by the exact string the operator types.
// Every migrated GM command registers here with a body; the built-in console commands live in
// ConsoleManager and are consulted first, so they win any name clash.
public class GmRegistry {

    public interface Body {
        void run(GmContext ctx, String[] args) throws Exception;
    }

    private static final class Entry {
        final GmCmd cmd;
        final boolean hidden;
        final boolean alias;

        Entry(GmCmd cmd, boolean hidden, boolean alias) {
            this.cmd = cmd;
            this.hidden = hidden;
            this.alias = alias;
        }
    }

    private final Map<String, Entry> byName = new LinkedHashMap<>();

    public void add(String name, String usage, String description, Body body) {
        add(new String[]{name}, usage, description, body, false);
    }

    public void add(String[] names, String usage, String description, Body body) {
        add(names, usage, description, body, false);
    }

    // names[0] is the canonical spelling; the rest are aliases. hidden commands still dispatch but
    // do not show up in help (used for spellings that only exist for muscle memory).
    public void add(String[] names, String usage, String description, Body body, boolean hidden) {
        for (int i = 0; i < names.length; i++) {
            String key = names[i].toLowerCase();
            if (byName.containsKey(key)) {
                continue;
            }
            GmCmd cmd = new Lam(names[i], i == 0 ? null : strip(names, i), usage, description, body);
            byName.put(key, new Entry(cmd, hidden && i == 0, i != 0));
        }
    }

    private static String[] strip(String[] names, int from) {
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (i != from) {
                rest.add(names[i]);
            }
        }
        return rest.toArray(new String[0]);
    }

    private static final class Lam extends GmCmd {
        private final String name;
        private final String[] aliases;
        private final String usage;
        private final String description;
        private final Body body;

        Lam(String name, String[] aliases, String usage, String description, Body body) {
            this.name = name;
            this.aliases = aliases == null ? new String[0] : aliases;
            this.usage = usage;
            this.description = description;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String[] aliases() {
            return aliases;
        }

        @Override
        public String usage() {
            return usage;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public void run(GmContext ctx, String[] args) throws Exception {
            body.run(ctx, args);
        }
    }

    public GmCmd get(String name) {
        Entry e = byName.get(name.toLowerCase());
        return e == null ? null : e.cmd;
    }

    public boolean contains(String name) {
        return byName.containsKey(name.toLowerCase());
    }

    public int size() {
        return byName.size();
    }

    // Distinct commands, in registration order, aliases folded into their owner, hidden spellings
    // left out - what "help" prints.
    public List<GmCmd> listed() {
        List<GmCmd> out = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : byName.entrySet()) {
            Entry en = e.getValue();
            if (en.hidden || en.alias) {
                continue;
            }
            String key = en.cmd.name().toLowerCase();
            if (seen.put(key, Boolean.TRUE) == null) {
                out.add(en.cmd);
            }
        }
        return out;
    }
}
