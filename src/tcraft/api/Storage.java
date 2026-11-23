package tcraft.api;

// 插件键值存储 路径见文档
public final class Storage {

    private final PluginHost host;

    Storage(PluginHost host) {
        this.host = host;
    }

    public String get(String key, String defaultValue) {
        return host.storageGet(key, defaultValue);
    }

    public String get(String key) {
        return host.storageGet(key, "");
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(host.storageGet(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(host.storageGet(key, String.valueOf(defaultValue)));
    }

    public void set(String key, Object value) {
        host.storageSet(key, value == null ? "" : String.valueOf(value));
    }

    public void save() {
        host.storageSave();
    }
}
