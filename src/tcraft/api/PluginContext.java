package tcraft.api;

import javafx.stage.Stage;

import java.nio.file.Path;

// 插件上下文 UI 命令 弹窗 存储 事件
public final class PluginContext {

    public static final int API_VERSION = 1;

    private final PluginHost host;
    private final Ui ui;
    private final Commands commands;
    private final Dialogs dialogs;
    private final Storage storage;
    private final Events events;

    public PluginContext(PluginHost host) {
        this.host = host;
        this.ui = new Ui(host);
        this.commands = new Commands(host);
        this.dialogs = new Dialogs(host);
        this.storage = new Storage(host);
        this.events = new Events(host);
    }

    public PluginInfo info() {
        return host.info();
    }

    public String id() {
        return host.info().id;
    }

    public String name() {
        return host.info().name;
    }

    public String version() {
        return host.info().version;
    }

    public String launcherVersion() {
        return host.launcherVersion();
    }

    // 数据目录 .tcl/plugins/.data/<id>
    public Path dataDir() {
        return host.info().dir == null ? null : host.info().dir;
    }

    public Ui ui() {
        return ui;
    }

    public Commands commands() {
        return commands;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public Storage storage() {
        return storage;
    }

    public Events events() {
        return events;
    }

    public Stage stage() {
        return host.stage();
    }

    public void log(String message) {
        host.log(message);
    }

    public void run(String command, String args) {
        host.runCommand(command, args);
    }
}
