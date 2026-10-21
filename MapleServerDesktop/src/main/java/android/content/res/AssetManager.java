package android.content.res;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

// Desktop stand-in for android.content.res.AssetManager. Assets (wz/, scripts/, sql/, config.yaml)
// are read straight from a filesystem directory instead of the APK's asset store. Only open() and
// list() are used by the server core.
public class AssetManager {
    private final File root;

    public AssetManager(File root) {
        this.root = root;
    }

    public InputStream open(String path) throws IOException {
        File f = new File(root, path);
        if (!f.exists() || f.isDirectory()) {
            throw new FileNotFoundException("Asset not found: " + path);
        }
        return new FileInputStream(f);
    }

    // Mirrors Android: returns the child names of a directory, or an empty array for a file or a
    // path that does not exist.
    public String[] list(String path) throws IOException {
        File f = (path == null || path.isEmpty()) ? root : new File(root, path);
        if (!f.exists() || !f.isDirectory()) {
            return new String[0];
        }
        String[] children = f.list();
        return children != null ? children : new String[0];
    }
}
