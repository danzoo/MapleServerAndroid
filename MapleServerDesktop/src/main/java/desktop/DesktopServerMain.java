package desktop;

import android.content.Context;
import desktop.console.ConsoleManager;
import net.server.Server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

// Windows/desktop entry point for the server fork. The Android app boots the core through
// com.mapleserver.ServerService (Server.getInstance(applicationContext) + Server.main(args)); this
// does the same against a filesystem-backed Context instead of the Android runtime.
//
// Two paths are configurable so the 609MB asset tree (wz/scripts/sql/config) is referenced in place
// rather than copied into the build output:
//   -Dmaple.assets=<dir>  default ../MapleServerAndroid/app/src/main/assets
//   -Dmaple.data=<dir>    default ./data   (holds files/server.log and databases/cosmic)
public class DesktopServerMain {
    public static void main(String[] args) {
        File assetsDir = resolve(
                System.getProperty("maple.assets"),
                new File("../app/src/main/assets"));
        File dataDir = resolve(
                System.getProperty("maple.data"),
                new File("./data"));

        if (!assetsDir.isDirectory()) {
            System.err.println("Assets directory not found: " + assetsDir.getAbsolutePath());
            System.err.println("Pass -Dmaple.assets=<path to MapleServerAndroid/app/src/main/assets>.");
            System.exit(1);
        }

        System.out.println("Assets: " + assetsDir.getAbsolutePath());
        System.out.println("Data:   " + dataDir.getAbsolutePath());

        Context context = new Context(assetsDir, dataDir);

        // The Android app copies config.yaml out of the assets into filesDir on first run (see
        // com.mapleserver.MainActivity.copyAssetFileApplication). Do the same here; the cosmic
        // database is copied separately by MapleDBHelper.createDataBase().
        seedConfigFile(assetsDir, context.getFilesDir());

        Server.getInstance(context);

        Server.main(new String[]{"-Xmx2048m", "-Dwz-path=wz", "-Djava.net.preferIPv4Stack=true"});

        // Server.main() returns once the core reports "online", so start the stdin admin console
        // afterwards: every command then runs against a fully-initialised server. The console is a
        // daemon thread and exits quietly if stdin is not attached (background launch).
        new ConsoleManager().start();
    }

    private static void seedConfigFile(File assetsDir, File filesDir) {
        File target = new File(filesDir, "config.yaml");
        if (target.exists()) {
            return;
        }
        File source = new File(assetsDir, "config.yaml");
        if (!source.isFile()) {
            System.err.println("config.yaml not found in assets: " + source.getAbsolutePath());
            System.exit(1);
        }
        try (InputStream in = Files.newInputStream(source.toPath());
             OutputStream out = Files.newOutputStream(target.toPath())) {
            in.transferTo(out);
        } catch (IOException e) {
            System.err.println("Failed to seed config.yaml: " + e.getMessage());
            System.exit(1);
        }
    }

    private static File resolve(String override, File fallback) {
        File dir = (override != null && !override.isEmpty()) ? new File(override) : fallback;
        return dir.getAbsoluteFile();
    }
}
