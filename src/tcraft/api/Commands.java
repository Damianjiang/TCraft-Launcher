package tcraft.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

// 插件命令注册表 建议用 插件id.动作 命名
public final class Commands {

    private final PluginHost host;

    Commands(PluginHost host) {
        this.host = host;
    }

    public void register(String name, String description, Consumer<CommandContext> handler) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("命令名不能为空");
        host.addCommand(name.trim(), description == null ? "" : description, handler);
    }

    public void run(String name) {
        run(name, "");
    }

    public void run(String name, String args) {
        host.runCommand(name, args == null ? "" : args);
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        for (tcraft.Plugins.CommandInfo c : tcraft.Plugins.get().commandsOf(host.info().id)) {
            out.add(c.name);
        }
        return Collections.unmodifiableList(out);
    }
}
