package android.database.sqlite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Desktop stand-in for android.database.sqlite.SQLiteStatement, backed by a JDBC PreparedStatement.
// Bind indices are 1-based, matching Android. Used via compileStatement(), often in
// try-with-resources, so it is AutoCloseable.
public class SQLiteStatement implements AutoCloseable {
    private final PreparedStatement stmt;

    SQLiteStatement(PreparedStatement stmt) {
        this.stmt = stmt;
    }

    public void bindNull(int index) {
        try {
            stmt.setObject(index, null);
        } catch (SQLException e) {
            throw new SQLiteException("bindNull failed", e);
        }
    }

    public void bindLong(int index, long value) {
        try {
            stmt.setLong(index, value);
        } catch (SQLException e) {
            throw new SQLiteException("bindLong failed", e);
        }
    }

    public void bindDouble(int index, double value) {
        try {
            stmt.setDouble(index, value);
        } catch (SQLException e) {
            throw new SQLiteException("bindDouble failed", e);
        }
    }

    public void bindString(int index, String value) {
        try {
            if (value == null) {
                stmt.setObject(index, null);
            } else {
                stmt.setString(index, value);
            }
        } catch (SQLException e) {
            throw new SQLiteException("bindString failed", e);
        }
    }

    public void bindAllArgsAsStrings(String[] bindArgs) {
        if (bindArgs != null) {
            for (int i = 0; i < bindArgs.length; i++) {
                bindString(i + 1, bindArgs[i]);
            }
        }
    }

    public void bindBlob(int index, byte[] value) {
        try {
            stmt.setBytes(index, value);
        } catch (SQLException e) {
            throw new SQLiteException("bindBlob failed", e);
        }
    }

    public void clearBindings() {
        try {
            stmt.clearParameters();
        } catch (SQLException e) {
            throw new SQLiteException("clearBindings failed", e);
        }
    }

    public void execute() {
        try {
            stmt.execute();
        } catch (SQLException e) {
            throw new SQLiteException("execute failed", e);
        }
    }

    public long executeInsert() {
        try {
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new SQLiteException("executeInsert failed", e);
        }
    }

    public int executeUpdateDelete() {
        try {
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new SQLiteException("executeUpdateDelete failed", e);
        }
    }

    public long simpleQueryForLong() {
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            throw new SQLiteException("simpleQueryForLong returned no rows");
        } catch (SQLException e) {
            throw new SQLiteException("simpleQueryForLong failed", e);
        }
    }

    public String simpleQueryForString() {
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
            throw new SQLiteException("simpleQueryForString returned no rows");
        } catch (SQLException e) {
            throw new SQLiteException("simpleQueryForString failed", e);
        }
    }

    public void close() {
        try {
            stmt.close();
        } catch (SQLException e) {
            throw new SQLiteException("close failed", e);
        }
    }
}
