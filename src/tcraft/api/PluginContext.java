package tcraft.api;

import javafx.stage.Stage;

import java.nio.file.Path;

/// Stable entry point for a plugin. UI, commands, dialogs, storage, events,
/// network, files, processes, desktop control, scheduler, JSON, and launcher
/// access are exposed here.
public final class PluginContext {

    public static final int API_VERSION = 1;

    private final PluginHost host;
    private final Ui ui;
    private final Commands commands;
    private final Dialogs dialogs;
    private final Storage storage;
    private final Events events;
    private final Network network;
    private final Files files;
    private final SystemInfo system;
    private final Processes processes;
    private final Desktop desktop;
    private final Scheduler scheduler;
    private final Json json;
    private final Launcher launcher;
    private volatile boolean closed;

    public PluginContext(PluginHost host) {
        this.host = host;
        this.ui = new Ui(host);
        this.commands = new Commands(host);
        this.dialogs = new Dialogs(host);
        this.storage = new Storage(host);
        this.events = new Events(host, this);
        this.network = new Network(this);
        this.files = new Files();
        this.system = new SystemInfo(host);
        this.processes = new Processes(this);
        this.desktop = new Desktop();
        this.scheduler = new Scheduler(this);
        this.json = new Json();
        this.launcher = new Launcher(host);
    }

    /// Returns the manifest snapshot.
    public PluginInfo info() {
        return host.info();
    }

    /// Returns the plugin id.
    public String id() {
        return host.info().id;
    }

    /// Returns the plugin display name.
    public String name() {
        return host.info().name;
    }

    /// Returns the plugin version.
    public String version() {
        return host.info().version;
    }

    /// Returns the running launcher version.
    public String launcherVersion() {
        return host.launcherVersion();
    }

    /// Returns the plugin data directory `.tcl/plugins/.data/<id>`.
    public Path dataDir() {
        return host.info().dir;
    }

    /// Returns the UI extension API.
    public Ui ui() {
        return ui;
    }

    /// Returns the command API.
    public Commands commands() {
        return commands;
    }

    /// Returns the dialog API.
    public Dialogs dialogs() {
        return dialogs;
    }

    /// Returns the key-value storage API.
    public Storage storage() {
        return storage;
    }

    /// Returns the event API.
    public Events events() {
        return events;
    }

    /// Returns HTTP, WebSocket, TCP, and UDP access.
    public Network network() {
        return network;
    }

    /// Returns file-system helpers.
    public Files files() {
        return files;
    }

    /// Returns host information, clipboard, and desktop-open helpers.
    public SystemInfo system() {
        return system;
    }

    /// Returns subprocess helpers.
    public Processes processes() {
        return processes;
    }

    /// Returns screen capture and mouse/keyboard control.
    public Desktop desktop() {
        return desktop;
    }

    /// Returns the per-plugin background scheduler.
    public Scheduler scheduler() {
        return scheduler;
    }

    /// Returns JSON parsing and serialization helpers.
    public Json json() {
        return json;
    }

    /// Returns instance selection and launch helpers.
    public Launcher launcher() {
        return launcher;
    }

    /// Returns the main window stage.
    public Stage stage() {
        return host.stage();
    }

    /// Writes a message to the launcher log.
    public void log(String message) {
        host.log(message);
    }

    /// Shows a launcher toast.
    public void toast(String title, String message) {
        host.toast(title, message, 1);
    }

    /// Runs another registered command.
    public void run(String command, String args) {
        host.runCommand(command, args);
    }

    /// Releases network connections, subprocesses, and scheduler tasks.
    public void close() {
        if (closed) return;
        closed = true;
        // one failing subsystem must not keep the others alive
        try {
            scheduler.close();
        } catch (Throwable ignored) {
        }
        try {
            network.close();
        } catch (Throwable ignored) {
        }
        try {
            processes.close();
        } catch (Throwable ignored) {
        }
    }

    /// Indicates whether the plugin context has been closed.
    public boolean isClosed() {
        return closed;
    }
}
