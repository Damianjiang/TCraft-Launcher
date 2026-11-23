package tcraft.api;

import javafx.stage.Stage;

import java.util.function.Function;

// UI 扩展 页面和按钮应在 onLoad 中注册
public final class Ui {

    private final PluginHost host;

    Ui(PluginHost host) {
        this.host = host;
    }

    // 在更多添加页面
    public void page(String id, String title, String icon, Function<PageView, javafx.scene.Node> builder) {
        host.addPage(id, title, icon, builder);
    }

    public void page(String id, String title, Function<PageView, javafx.scene.Node> builder) {
        page(id, title, "puzzle", builder);
    }

    public void toolButton(String text, Runnable action) {
        host.addToolButton(Widgets.button(text, action));
    }

    public void toolButton(javafx.scene.Node node) {
        host.addToolButton(node);
    }

    // 标题栏图标按钮 icon 如 star / puzzle
    public void titleButton(String icon, String tooltip, Runnable action) {
        host.addTitleButton(tcraft.Ui.iconButtonWhite(
                icon == null || icon.isBlank() ? "puzzle" : icon, 28, 16, tooltip, action));
    }

    public void launchButton(String text, Runnable action) {
        host.addLaunchButton(Widgets.button(text, action));
    }

    public void launchButton(javafx.scene.Node node) {
        host.addLaunchButton(node);
    }

    public void floatButton(String icon, String tooltip, Runnable action) {
        host.addFloatButton(tcraft.Ui.iconButton(
                icon == null || icon.isBlank() ? "puzzle" : icon, 38, 18, tooltip, action));
    }

    // 重建插件相关界面
    public void refresh() {
        tcraft.Plugins.get().refreshUi();
    }

    public Stage stage() {
        return host.stage();
    }

    public javafx.scene.Scene scene() {
        return host.stage() == null ? null : host.stage().getScene();
    }

    // 打开内置页 参数见文档
    public void openPage(String key) {
        tcraft.Plugins.get().openPage(key);
    }
}
