package android.database.sqlite;

import android.content.Context;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Desktop stand-in for android.database.sqlite.SQLiteOpenHelper. The platform opens (and creates if
// missing) a SQLite file in the app's databases directory; here the file lives under the configured
// data dir, resolved through Context.getDatabasePath(name). MapleDBHelper subclasses this and copies
// the asset 'sql/cosmic' onto that path itself, so the driver only needs to attach to whatever file
// is present by the time getWritableDatabase() runs.
public abstract class SQLiteOpenHelper {
    private final Context context;
    private final String name;
    private final SQLiteDatabase.CursorFactory factory;
    private final int version;

    private SQLiteDatabase database;

    public SQLiteOpenHelper(Context context, String name,
                            SQLiteDatabase.CursorFactory factory, int version) {
        this.context = context;
        this.name = name;
        this.factory = factory;
        this.version = version;
    }

    public String getDatabaseName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public synchronized SQLiteDatabase getWritableDatabase() {
        if (database != null && database.isOpen()) {
            return database;
        }

        File dbFile = context.getDatabasePath(name);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            database = new SQLiteDatabase(conn);
            return database;
        } catch (SQLException e) {
            throw new SQLiteException("Failed to open database '" + name + "' at " + dbFile, e);
        }
    }

    public synchronized SQLiteDatabase getReadableDatabase() {
        return getWritableDatabase();
    }

    public synchronized void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public abstract void onCreate(SQLiteDatabase db);

    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
}
