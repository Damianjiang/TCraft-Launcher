package tcraft.api;

import javafx.scene.Node;

// 弹窗与输入框 仅 FX 线程
public final class Dialogs {

    private final PluginHost host;

    Dialogs(PluginHost host) {
        this.host = host;
    }

    public void message(String title, String text) {
        host.message(title, text, false);
    }

    public void error(String title, String text) {
        host.message(title, text, true);
    }

    public void confirm(String title, String text, Runnable onOk) {
        host.confirm(title, text, onOk, false);
    }

    public void confirmDanger(String title, String text, Runnable onOk) {
        host.confirm(title, text, onOk, true);
    }

    public void custom(Node content) {
        host.custom(content);
    }

    public void close() {
        host.closeModal();
    }

    // 取消返回 null
    public String input(String title, String hint, String defaultValue) {
        return host.input(title, hint, defaultValue);
    }
}
