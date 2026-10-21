package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

// Desktop stand-in for android.database.sqlite.SQLiteDatabase, backed by a single JDBC connection
// (Xerial sqlite-jdbc). Android serializes access to one database object, so every operation here
// takes the same fair ReentrantLock; a transaction holds that lock from beginTransaction() until
// endTransaction(), which keeps other threads out for the duration, exactly like the platform.
public class SQLiteDatabase implements AutoCloseable {
    public static final int CONFLICT_NONE = 0;
    public static final int CONFLICT_ROLLBACK = 1;
    public static final int CONFLICT_ABORT = 2;
    public static final int CONFLICT_FAIL = 3;
    public static final int CONFLICT_IGNORE = 4;
    public static final int CONFLICT_REPLACE = 5;

    private static final String[] CONFLICT_VALUES = {
            "", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "
    };

    // Marker interface mirroring android.database.sqlite.SQLiteDatabase.CursorFactory. The core
    // always passes null, so no factory behaviour is needed.
    public interface CursorFactory {
    }

    private final Connection conn;
    private final ReentrantLock lock = new ReentrantLock(true);
    private int txDepth = 0;
    private boolean txSuccessful = false;
    private volatile boolean open = true;

    public SQLiteDatabase(Connection conn) {
        this.conn = conn;
    }

    public Connection getConnection() {
        return conn;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        lock.lock();
        try {
            if (open) {
                conn.close();
                open = false;
            }
        } catch (SQLException e) {
            throw new SQLiteException("close failed", e);
        } finally {
            lock.unlock();
        }
    }

    // ---- transactions ----

    public void beginTransaction() {
        lock.lock();
        try {
            if (txDepth == 0) {
                conn.setAutoCommit(false);
                txSuccessful = false;
            }
            txDepth++;
        } catch (SQLException e) {
            lock.unlock();
            throw new SQLiteException("beginTransaction failed", e);
        }
    }

    public void setTransactionSuccessful() {
        txSuccessful = true;
    }

    public void endTransaction() {
        try {
            txDepth--;
            if (txDepth <= 0) {
                txDepth = 0;
                if (txSuccessful) {
                    conn.commit();
                } else {
                    conn.rollback();
                }
                conn.setAutoCommit(true);
                txSuccessful = false;
            }
        } catch (SQLException e) {
            throw new SQLiteException("endTransaction failed", e);
        } finally {
            lock.unlock();
        }
    }

    public boolean inTransaction() {
        return txDepth > 0;
    }

    // ---- queries ----

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        lock.lock();
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (selectionArgs != null) {
                for (int i = 0; i < selectionArgs.length; i++) {
                    ps.setString(i + 1, selectionArgs[i]);
                }
            }
            ResultSet rs = ps.executeQuery();
            return new Cursor(ps, rs);
        } catch (SQLException e) {
            throw new SQLiteException("rawQuery failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs,
                        String groupBy, String having, String orderBy) {
        return query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs,
                        String groupBy, String having, String orderBy, String limit) {
        StringBuilder sql = new StringBuilder("SELECT ");
        appendColumns(sql, columns);
        sql.append(" FROM ").append(table);
        if (selection != null) {
            sql.append(" WHERE ").append(selection);
        }
        if (groupBy != null) {
            sql.append(" GROUP BY ").append(groupBy);
        }
        if (having != null) {
            sql.append(" HAVING ").append(having);
        }
        if (orderBy != null) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }
        return rawQuery(sql.toString(), selectionArgs);
    }

    private static void appendColumns(StringBuilder sql, String[] columns) {
        if (columns == null || columns.length == 0) {
            sql.append("*");
            return;
        }
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns[i]);
        }
    }

    // ---- statements ----

    public void execSQL(String sql) {
        lock.lock();
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new SQLiteException("execSQL failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    public void execSQL(String sql, Object[] bindArgs) {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (bindArgs != null) {
                for (int i = 0; i < bindArgs.length; i++) {
                    bind(ps, i + 1, bindArgs[i]);
                }
            }
            ps.execute();
        } catch (SQLException e) {
            throw new SQLiteException("execSQL failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    public SQLiteStatement compileStatement(String sql) {
        lock.lock();
        try {
            return new SQLiteStatement(conn.prepareStatement(sql));
        } catch (SQLException e) {
            throw new SQLiteException("compileStatement failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    // Escape hatch for the few core paths that use the raw JDBC API directly (GM commands, session
    // DAO). The returned statement is owned by the caller; like the platform's single connection it
    // is not safe to use concurrently with other database calls.
    public PreparedStatement prepareStatement(String sql) {
        lock.lock();
        try {
            return conn.prepareStatement(sql);
        } catch (SQLException e) {
            throw new SQLiteException("prepareStatement failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
        lock.lock();
        try {
            return conn.prepareStatement(sql, autoGeneratedKeys);
        } catch (SQLException e) {
            throw new SQLiteException("prepareStatement failed: " + sql, e);
        } finally {
            lock.unlock();
        }
    }

    // ---- writes ----

    public long insert(String table, String nullColumnHack, ContentValues values) {
        try {
            return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE);
        } catch (SQLiteException e) {
            return -1;
        }
    }

    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE);
    }

    public long replace(String table, String nullColumnHack, ContentValues initialValues) {
        return insertWithOnConflict(table, nullColumnHack, initialValues, CONFLICT_REPLACE);
    }

    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues initialValues,
                                     int conflictAlgorithm) {
        lock.lock();
        try {
            StringBuilder sql = new StringBuilder("INSERT");
            sql.append(CONFLICT_VALUES[conflictAlgorithm]);
            sql.append(" INTO ").append(table);

            List<Object> args = new ArrayList<>();
            int size = initialValues != null ? initialValues.size() : 0;
            if (size > 0) {
                sql.append(" (");
                StringBuilder placeholders = new StringBuilder();
                int i = 0;
                for (Map.Entry<String, Object> entry : initialValues.valueSet()) {
                    if (i++ > 0) {
                        sql.append(", ");
                        placeholders.append(", ");
                    }
                    sql.append(entry.getKey());
                    placeholders.append("?");
                    args.add(entry.getValue());
                }
                sql.append(") VALUES (").append(placeholders).append(")");
            } else {
                sql.append(" (").append(nullColumnHack).append(") VALUES (NULL)");
            }

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) {
                    bind(ps, i + 1, args.get(i));
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                return -1;
            }
        } catch (SQLException e) {
            throw new SQLiteException("insert into " + table + " failed", e);
        } finally {
            lock.unlock();
        }
    }

    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        lock.lock();
        try {
            StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
            List<Object> args = new ArrayList<>();
            int i = 0;
            for (Map.Entry<String, Object> entry : values.valueSet()) {
                if (i++ > 0) {
                    sql.append(", ");
                }
                sql.append(entry.getKey()).append(" = ?");
                args.add(entry.getValue());
            }
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
                if (whereArgs != null) {
                    for (String a : whereArgs) {
                        args.add(a);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int j = 0; j < args.size(); j++) {
                    bind(ps, j + 1, args.get(j));
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLiteException("update " + table + " failed", e);
        } finally {
            lock.unlock();
        }
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        lock.lock();
        try {
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
            if (whereClause != null) {
                sql.append(" WHERE ").append(whereClause);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                if (whereArgs != null) {
                    for (int i = 0; i < whereArgs.length; i++) {
                        ps.setString(i + 1, whereArgs[i]);
                    }
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SQLiteException("delete from " + table + " failed", e);
        } finally {
            lock.unlock();
        }
    }

    private static void bind(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else if (value instanceof String) {
            ps.setString(index, (String) value);
        } else if (value instanceof Long) {
            ps.setLong(index, (Long) value);
        } else if (value instanceof Integer) {
            ps.setInt(index, (Integer) value);
        } else if (value instanceof Short) {
            ps.setShort(index, (Short) value);
        } else if (value instanceof Byte) {
            ps.setByte(index, (Byte) value);
        } else if (value instanceof Double) {
            ps.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            ps.setFloat(index, (Float) value);
        } else if (value instanceof Boolean) {
            ps.setInt(index, ((Boolean) value) ? 1 : 0);
        } else if (value instanceof byte[]) {
            ps.setBytes(index, (byte[]) value);
        } else {
            ps.setObject(index, value);
        }
    }
}
