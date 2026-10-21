package android.database.sqlite;

// Desktop stand-in for android.database.sqlite.SQLiteException. Android's version is unchecked
// (it descends from android.database.SQLException -> RuntimeException), and the core throws it
// without declaring it, so this mirrors that by extending RuntimeException.
public class SQLiteException extends RuntimeException {
    public SQLiteException() {
        super();
    }

    public SQLiteException(String message) {
        super(message);
    }

    public SQLiteException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQLiteException(Throwable cause) {
        super(cause);
    }
}
