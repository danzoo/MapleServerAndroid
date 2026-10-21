package android.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// Desktop stand-in for android.database.Cursor. The JDBC ResultSet is fully materialized into an
// in-memory list on construction and the statement/result set are closed immediately, which gives
// the random-access, know-your-count semantics Android code expects (getCount, moveToFirst,
// getColumnIndex) without depending on a scrollable driver. Server result sets are small, so the
// copy is cheap. Column indices are 0-based, matching Android.
public class Cursor implements AutoCloseable {
    private final List<String> columns = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();
    private int pos = -1;
    private boolean closed = false;

    public Cursor(PreparedStatement stmt, ResultSet rs) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            while (rs.next()) {
                Object[] row = new Object[count];
                for (int i = 1; i <= count; i++) {
                    row[i - 1] = rs.getObject(i);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new android.database.sqlite.SQLiteException("Failed to read query result", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    public int getCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columns.size();
    }

    public String getColumnName(int index) {
        return columns.get(index);
    }

    public int getColumnIndex(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public int getColumnIndexOrThrow(String name) {
        int idx = getColumnIndex(name);
        if (idx < 0) {
            throw new IllegalArgumentException("No column named " + name);
        }
        return idx;
    }

    public boolean moveToFirst() {
        if (rows.isEmpty()) {
            pos = -1;
            return false;
        }
        pos = 0;
        return true;
    }

    public boolean moveToLast() {
        if (rows.isEmpty()) {
            pos = -1;
            return false;
        }
        pos = rows.size() - 1;
        return true;
    }

    public boolean moveToNext() {
        if (pos + 1 < rows.size()) {
            pos++;
            return true;
        }
        pos = rows.size();
        return false;
    }

    public boolean moveToPosition(int position) {
        if (position < 0 || position >= rows.size()) {
            pos = rows.size();
            return false;
        }
        pos = position;
        return true;
    }

    public boolean move(int offset) {
        return moveToPosition(pos + offset);
    }

    public int getPosition() {
        return pos;
    }

    public boolean isFirst() {
        return pos == 0 && !rows.isEmpty();
    }

    public boolean isLast() {
        return pos == rows.size() - 1 && !rows.isEmpty();
    }

    public boolean isAfterLast() {
        return pos >= rows.size();
    }

    public boolean isBeforeFirst() {
        return pos < 0;
    }

    private Object value(int index) {
        if (pos < 0 || pos >= rows.size()) {
            throw new IllegalStateException("Cursor not positioned on a valid row");
        }
        return rows.get(pos)[index];
    }

    public boolean isNull(int index) {
        return value(index) == null;
    }

    public int getInt(int index) {
        Object o = value(index);
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public long getLong(int index) {
        Object o = value(index);
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public short getShort(int index) {
        return (short) getInt(index);
    }

    public float getFloat(int index) {
        Object o = value(index);
        if (o == null) {
            return 0f;
        }
        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }
        try {
            return Float.parseFloat(o.toString());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    public double getDouble(int index) {
        Object o = value(index);
        if (o == null) {
            return 0d;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    public String getString(int index) {
        Object o = value(index);
        return o == null ? null : o.toString();
    }

    public byte[] getBlob(int index) {
        Object o = value(index);
        if (o == null) {
            return null;
        }
        if (o instanceof byte[]) {
            return (byte[]) o;
        }
        return o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
