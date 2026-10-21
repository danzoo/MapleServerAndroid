package android.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Desktop stand-in for android.content.ContentValues: an insertion-ordered String->Object map with
// the typed put()/getAs*() surface the server core uses. The SQLiteDatabase shim reads valueSet()
// to build INSERT/UPDATE statements.
public class ContentValues {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public void put(String key, String value) { values.put(key, value); }
    public void put(String key, Integer value) { values.put(key, value); }
    public void put(String key, Long value) { values.put(key, value); }
    public void put(String key, Short value) { values.put(key, value); }
    public void put(String key, Byte value) { values.put(key, value); }
    public void put(String key, Float value) { values.put(key, value); }
    public void put(String key, Double value) { values.put(key, value); }
    public void put(String key, Boolean value) { values.put(key, value); }
    public void put(String key, byte[] value) { values.put(key, value); }
    public void putNull(String key) { values.put(key, null); }

    public Object get(String key) { return values.get(key); }

    public String getAsString(String key) {
        Object o = values.get(key);
        return o == null ? null : o.toString();
    }

    public Integer getAsInteger(String key) {
        Object o = values.get(key);
        return o == null ? null : ((Number) o).intValue();
    }

    public Long getAsLong(String key) {
        Object o = values.get(key);
        return o == null ? null : ((Number) o).longValue();
    }

    public Double getAsDouble(String key) {
        Object o = values.get(key);
        return o == null ? null : ((Number) o).doubleValue();
    }

    public Boolean getAsBoolean(String key) {
        Object o = values.get(key);
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        return Boolean.parseBoolean(o.toString());
    }

    public byte[] getAsByteArray(String key) {
        Object o = values.get(key);
        return o instanceof byte[] ? (byte[]) o : null;
    }

    public Set<String> keySet() { return values.keySet(); }
    public Set<Map.Entry<String, Object>> valueSet() { return values.entrySet(); }
    public boolean containsKey(String key) { return values.containsKey(key); }
    public int size() { return values.size(); }
    public void clear() { values.clear(); }
    public void remove(String key) { values.remove(key); }

    @Override
    public String toString() { return values.toString(); }
}
