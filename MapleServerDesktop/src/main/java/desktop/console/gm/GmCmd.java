package desktop.console.gm;

// One GM command ported to the stdin console.
//
// The in-game commands (server-core/client/command) all run as "the character that typed the
// message", so they need a Client and a GM level. A console command has no operator at all: it
// takes every subject as an explicit argument (a map id, a character name, nothing at all for
// server-wide commands) and reports straight to stdout.
//
// Conventions every implementation follows:
//   - name() is lowercase and is what the operator types; aliases() are extra spellings.
//   - usage() describes the arguments WITHOUT the command name, e.g. "<name> <level>".
//   - run() must not throw for ordinary bad input: use the GmContext helpers (they print the
//     failure and return null/0) and return early.
//   - Parameters keep their exact case, unlike the in-game path which lowercases everything, so
//     character names and free text work as typed.
public abstract class GmCmd {

    public abstract String name();

    public String[] aliases() {
        return new String[0];
    }

    public abstract String usage();

    public abstract String description();

    public abstract void run(GmContext ctx, String[] args) throws Exception;

    // Used by help listings: the primary name plus every alias, comma separated.
    public String names() {
        String[] al = aliases();
        if (al.length == 0) {
            return name();
        }
        StringBuilder sb = new StringBuilder(name());
        for (String a : al) {
            sb.append(", ").append(a);
        }
        return sb.toString();
    }
}
