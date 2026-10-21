package android.util;

// Desktop stand-in for android.util.Log. Routes the tag/message calls to stdout/stderr so the one
// core file that still uses it (server.maps.MapManager) compiles and its diagnostics stay visible.
public final class Log {
    private Log() {}

    public static int v(String tag, String msg) { return println("V", tag, msg, null); }
    public static int v(String tag, String msg, Throwable tr) { return println("V", tag, msg, tr); }
    public static int d(String tag, String msg) { return println("D", tag, msg, null); }
    public static int d(String tag, String msg, Throwable tr) { return println("D", tag, msg, tr); }
    public static int i(String tag, String msg) { return println("I", tag, msg, null); }
    public static int i(String tag, String msg, Throwable tr) { return println("I", tag, msg, tr); }
    public static int w(String tag, String msg) { return println("W", tag, msg, null); }
    public static int w(String tag, String msg, Throwable tr) { return println("W", tag, msg, tr); }
    public static int e(String tag, String msg) { return println("E", tag, msg, null); }
    public static int e(String tag, String msg, Throwable tr) { return println("E", tag, msg, tr); }
    public static int wtf(String tag, String msg) { return println("E", tag, msg, null); }
    public static int wtf(String tag, String msg, Throwable tr) { return println("E", tag, msg, tr); }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static int println(String level, String tag, String msg, Throwable tr) {
        StringBuilder sb = new StringBuilder();
        sb.append(level).append('/').append(tag).append(": ").append(msg);
        if (tr != null) {
            sb.append('\n').append(getStackTraceString(tr));
        }
        String line = sb.toString();
        if ("E".equals(level) || "W".equals(level)) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
        return line.length();
    }
}
