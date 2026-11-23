package tcraft.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// 启动器事件 data 为只读
public final class Event {

    public final String name;
    private final Map<String, Object> data;

    public Event(String name, Map<String, Object> data) {
        this.name = name;
        this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    public Map<String, Object> data() {
        return data;
    }

    public String string(String key) {
        Object v = data.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    public boolean bool(String key) {
        Object v = data.get(key);
        return v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(String.valueOf(v));
    }

    public int integer(String key, int def) {
        Object v = data.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    public List<Object> list(String key) {
        Object v = data.get(key);
        return v instanceof List ? (List<Object>) v : Collections.emptyList();
    }
}
