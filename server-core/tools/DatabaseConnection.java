package tools;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import database.MapleDBHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Frz (Big Daddy)
 * @author The Real Spookster - some modifications to this beautiful code
 * @author Ronan - some connection pool to this beautiful code
 */
public class DatabaseConnection {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static final SQLiteDatabase dbConnection = MapleDBHelper.getInstance().getWritableDatabase();

    static {
        // The default rollback journal fsyncs every commit (synchronous=FULL); on Windows that
        // occasionally stalls a write for seconds and everything queued behind it - kill settlement,
        // periodic saves, pickup writes - reads as multi-second lag. WAL keeps readers and writers
        // apart and NORMAL drops the per-commit flush while staying crash-safe for a game server.
        dbConnection.execSQL("PRAGMA journal_mode=WAL;");
        dbConnection.execSQL("PRAGMA synchronous=NORMAL;");
        // The automatic checkpoint runs inside a commit and blocks every writer for as long as it
        // takes - the occasional multi-second stall behind which kill settlement queues up. Check
        // passively from independent database maintenance and after manual saves instead.
        // Maintenance never saves characters; PASSIVE can still leave busy frames in WAL and
        // briefly contend for the desktop shim's connection lock.
        dbConnection.execSQL("PRAGMA wal_autocheckpoint=0;");
    }

    public static void checkpointPassive() {
        dbConnection.execSQL("PRAGMA wal_checkpoint(PASSIVE);");
    }

    public static synchronized SQLiteDatabase getConnection() throws SQLiteException {
        if (dbConnection == null) {
            throw new IllegalStateException("Unable to get connection - connection pool is uninitialized");
        }
        return dbConnection;
    }
    public static void initializeConnectionPool() {

    }
}
