package android.content;

import android.content.res.AssetManager;

import java.io.File;

// Desktop stand-in for android.content.Context. The server core only ever calls getAssets(),
// getFilesDir() and getDatabasePath(name), so this concrete class backs those three with plain
// filesystem directories instead of the Android runtime.
public class Context {
    private final AssetManager assetManager;
    private final File filesDir;
    private final File databasesDir;

    public Context(File assetsDir, File dataDir) {
        this.assetManager = new AssetManager(assetsDir);
        this.filesDir = new File(dataDir, "files");
        this.databasesDir = new File(dataDir, "databases");
        this.filesDir.mkdirs();
        this.databasesDir.mkdirs();
    }

    public AssetManager getAssets() {
        return assetManager;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public File getDatabasePath(String name) {
        return new File(databasesDir, name);
    }
}
