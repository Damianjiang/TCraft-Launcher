package tcraft.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// args 为命令名后的整串参数 argv() 按空白切分
public final class CommandContext {

    public final String name;
    public final String args;
    public final String pluginId;
    private final PluginHost host;

    public CommandContext(String name, String args, String pluginId, PluginHost host) {
        this.name = name;
        this.args = args == null ? "" : args;
        this.pluginId = pluginId;
        this.host = host;
    }

    public List<String> argv() {
        if (args.isBlank()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String s : args.trim().split("\\s+")) {
            if (!s.isEmpty()) out.add(s);
        }
        return Collections.unmodifiableList(out);
    }

    public void reply(String message) {
        host.toast(name, message, 1);
    }

    public void run(String command, String moreArgs) {
        host.runCommand(command, moreArgs == null ? "" : moreArgs);
    }

    public void log(String message) {
        host.log(message);
    }
}
