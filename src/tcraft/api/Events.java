package tcraft.api;

import java.util.Map;
import java.util.function.Consumer;

/// Launcher events and a plugin-to-plugin event bus.
///
/// Subscriptions are removed when the plugin is disabled or reloaded.
public final class Events {

    private final PluginHost host;
    private final PluginContext ctx;

    Events(PluginHost host, PluginContext ctx) {
        this.host = host;
        this.ctx = ctx;
    }

    /// Subscribes to a launcher or custom event. The callback runs on the JavaFX thread.
    public void on(String name, Consumer<Event> handler) {
        host.listen(name, handler);
    }

    /// Fires a custom event to every enabled plugin, including this one.
    public void emit(String name, Map<String, Object> data) {
        tcraft.Plugins.get().fire(name, data == null ? Map.of() : data);
    }

    /// Fires a custom event with no payload.
    public void emit(String name) {
        emit(name, Map.of());
    }

    /// Writes a message to the launcher log.
    public void log(String message) {
        ctx.log(message);
    }
}
