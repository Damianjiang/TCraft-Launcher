package tcraft.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Reads and selects Minecraft instances, and starts the normal launch flow.
///
/// `launch` must run on the JavaFX application thread because the launcher owns
/// the login and progress UI. Use `ctx.scheduler().ui(...)` when needed.
public final class Launcher {

    private final PluginHost host;

    Launcher(PluginHost host) {
        this.host = host;
    }

    /// Returns a snapshot of all registered instances.
    public List<Instance> instances() {
        List<Instance> out = new ArrayList<>();
        tcraft.Data data = tcraft.Data.get();
        for (int i = 0; i < data.instances.size(); i++) {
            out.add(convert(data.instances.get(i), i));
        }
        return List.copyOf(out);
    }

    /// Returns the currently selected instance, or `null` when none exists.
    public Instance selected() {
        tcraft.Data data = tcraft.Data.get();
        int index = data.selectedInstance;
        if (index < 0 || index >= data.instances.size()) return null;
        return convert(data.instances.get(index), index);
    }

    /// Selects an instance by list index, saves the choice, and refreshes the UI.
    public Instance select(int index) {
        // mutating shared launcher state and refreshing the UI belongs on the FX thread
        if (!javafx.application.Platform.isFxApplicationThread()) {
            return onFx(() -> select(index));
        }
        tcraft.Data data = tcraft.Data.get();
        if (index < 0 || index >= data.instances.size()) {
            throw new IllegalArgumentException("instance index out of range: " + index);
        }
        data.selectedInstance = index;
        data.save();
        host.refreshLauncher();
        return convert(data.instances.get(index), index);
    }

    /// Selects the first instance whose name or version equals the argument.
    public Instance select(String nameOrVersion) {
        tcraft.Data data = tcraft.Data.get();
        for (int i = 0; i < data.instances.size(); i++) {
            tcraft.Data.Instance instance = data.instances.get(i);
            if (nameOrVersion != null && (nameOrVersion.equals(instance.name)
                    || nameOrVersion.equals(instance.version))) {
                return select(i);
            }
        }
        throw new IllegalArgumentException("instance not found: " + nameOrVersion);
    }

    /// Starts the currently selected instance through the normal launcher UI.
    public void launch() {
        // the launch flow owns login and progress UI; always run it on FX
        if (javafx.application.Platform.isFxApplicationThread()) {
            host.launchGame();
        } else {
            javafx.application.Platform.runLater(host::launchGame);
        }
    }

    /// Selects an instance by name or version, then starts it.
    public void launch(String nameOrVersion) {
        select(nameOrVersion);
        launch();
    }

    /// Returns the current game directory.
    public Path gameDir() {
        return Path.of(tcraft.Data.get().gameDir);
    }

    /// Returns the launcher configuration directory (the `.tcl` folder).
    public Path baseDir() {
        return tcraft.Data.get().baseDir();
    }

    /// Returns the directory where installed plugins are scanned.
    public Path pluginsDir() {
        return tcraft.Plugins.get().dir();
    }

    /// Opens a built-in page such as `launch`, `instance`, `download`, or `other`.
    public void openPage(String key) {
        tcraft.Plugins.get().openPage(key);
    }

    /// Shows a launcher toast.
    public void toast(String title, String message) {
        host.toast(title, message, 1);
    }

    private static Instance convert(tcraft.Data.Instance instance, int index) {
        return new Instance(index, instance.name, instance.version, instance.loader,
                instance.loaderVersion, instance.folder, instance.favorite);
    }

    private static <T> T onFx(java.util.function.Supplier<T> action) {
        // Running on the FX thread already; queuing again would deadlock.
        if (javafx.application.Platform.isFxApplicationThread()) {
            return action.get();
        }
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(action::get);
        javafx.application.Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the JavaFX thread", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

    /// Immutable instance information exposed to plugins.
    public record Instance(int index, String name, String version, String loader,
                           String loaderVersion, String folder, boolean favorite) {
    }
}
