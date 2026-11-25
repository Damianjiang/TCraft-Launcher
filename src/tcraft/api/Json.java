package tcraft.api;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// JSON parsing and serialization helpers.
public final class Json {

    /// Parses JSON into `Map`, `List`, `String`, `Number`, `Boolean`, or `null`.
    public Object parse(String text) {
        return tcraft.Json.parse(text);
    }

    /// Serializes maps, lists, strings, numbers, booleans, and `null`.
    public String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, new IdentityHashMap<>());
        return out.toString();
    }

    /// Returns a map view or an empty map.
    public Map<String, Object> object(Object value) {
        return tcraft.Json.obj(value);
    }

    /// Returns a nested map view or an empty map.
    public Map<String, Object> object(Object value, String key) {
        return tcraft.Json.obj(tcraft.Json.obj(value).get(key));
    }

    /// Returns a list view or an empty list.
    public List<Object> array(Object value) {
        return tcraft.Json.arr(value);
    }

    /// Returns a nested list view or an empty list.
    public List<Object> array(Object value, String key) {
        return tcraft.Json.arr(tcraft.Json.obj(value).get(key));
    }

    /// Reads a string value, returning `def` when missing.
    public String string(Object value, String key, String def) {
        Object raw = tcraft.Json.obj(value).get(key);
        return raw == null ? def : String.valueOf(raw);
    }

    /// Reads a numeric value, returning `def` when missing or invalid.
    public double number(Object value, String key, double def) {
        Object raw = tcraft.Json.obj(value).get(key);
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception e) {
            return def;
        }
    }

    /// Reads an integer value, returning `def` when missing or invalid.
    public int integer(Object value, String key, int def) {
        Object raw = tcraft.Json.obj(value).get(key);
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception e) {
            return def;
        }
    }

    /// Reads a boolean value, returning `def` when missing.
    public boolean bool(Object value, String key, boolean def) {
        Object raw = tcraft.Json.obj(value).get(key);
        return raw instanceof Boolean b ? b : raw == null ? def : Boolean.parseBoolean(String.valueOf(raw));
    }

    private static void write(Object value, StringBuilder out, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String s) {
            out.append(quote(s));
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (seen.put(map, Boolean.TRUE) != null) throw new IllegalArgumentException("JSON contains a cycle");
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':');
                write(entry.getValue(), out, seen);
            }
            out.append('}');
            seen.remove(map);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            if (seen.put(iterable, Boolean.TRUE) != null) throw new IllegalArgumentException("JSON contains a cycle");
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                write(item, out, seen);
            }
            out.append(']');
            seen.remove(iterable);
            return;
        }
        if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                write(java.lang.reflect.Array.get(value, i), out, seen);
            }
            out.append(']');
            return;
        }
        out.append(quote(String.valueOf(value)));
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}
