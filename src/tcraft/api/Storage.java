package tcraft.api;

/// Plugin-private key-value storage persisted in
/// `.tcl/plugins/.data/<id>/storage.properties`.
///
/// Values are stored as strings; use the typed getters for numbers and booleans.
public final class Storage {

    private final PluginHost host;

    Storage(PluginHost host) {
        this.host = host;
    }

    /// Reads a string value.
    public String get(String key, String defaultValue) {
        return host.storageGet(key, defaultValue);
    }

    /// Reads a string value, returning an empty string when missing.
    public String get(String key) {
        return host.storageGet(key, "");
    }

    /// Reads an integer value.
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(host.storageGet(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /// Reads a long value.
    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(host.storageGet(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /// Reads a double value.
    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(host.storageGet(key, String.valueOf(defaultValue)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /// Reads a boolean value.
    public boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(host.storageGet(key, String.valueOf(defaultValue)));
    }

    /// Writes a value to memory; call `save` to persist it immediately.
    public void set(String key, Object value) {
        host.storageSet(key, value == null ? "" : String.valueOf(value));
    }

    /// Persists the current values to disk.
    public void save() {
        host.storageSave();
    }
}
