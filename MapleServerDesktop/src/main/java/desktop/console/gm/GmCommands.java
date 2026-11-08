package desktop.console.gm;

// Single entry point that fills the registry with every GM command ported to the console.
//
// The ports are grouped by the server-core package they came from (commands/gm0 .. commands/gm6),
// so a console command can always be traced back to the in-game implementation it mirrors.
// Registration order decides help order; a name already taken keeps the first spelling.
public final class GmCommands {

    private GmCommands() {
    }

    public static void registerAll(GmRegistry r) {
        GmCommandsLv0.register(r);
        GmCommandsLv1.register(r);
        GmCommandsLv2.register(r);
        GmCommandsLv3.register(r);
        GmCommandsLv4.register(r);
        GmCommandsLv5.register(r);
        GmCommandsLv6.register(r);
    }
}
