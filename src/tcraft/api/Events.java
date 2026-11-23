package tcraft.api;

import java.util.function.Consumer;

// 事件订阅 插件卸载时自动取消
public final class Events {

    private final PluginHost host;

    Events(PluginHost host) {
        this.host = host;
    }

    // 事件名 回调在 FX 线程
    public void on(String name, Consumer<Event> handler) {
        host.listen(name, handler);
    }
}
