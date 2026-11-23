package tcraft.api;

// 插件入口 回调在 FX 线程 不要阻塞
public interface TcraftPlugin {

    default void onLoad(PluginContext ctx) throws Exception {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onInstall(PluginContext ctx) {
    }

    default void onUninstall(PluginContext ctx) {
    }
}
