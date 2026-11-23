package tcraft.api;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

// 页面容器 root 已带内边距和滚动
public final class PageView {

    private final VBox root;
    private final PluginContext ctx;

    public PageView(VBox root, PluginContext ctx) {
        this.root = root;
        this.ctx = ctx;
    }

    public VBox root() {
        return root;
    }

    public PluginContext context() {
        return ctx;
    }

    public void header(String title, String subtitle) {
        VBox head = new VBox(5);
        head.setPadding(new Insets(0, 0, 18, 0));
        Label t = tcraft.Ui.bold(title, 30, tcraft.Theme.text);
        Label s = tcraft.Ui.label(subtitle, 13, tcraft.Theme.text);
        s.setOpacity(0.55);
        head.getChildren().addAll(t, s);
        root.getChildren().add(head);
    }

    public void add(Node... nodes) {
        root.getChildren().addAll(nodes);
    }

    public void text(String text) {
        root.getChildren().add(Widgets.text(text));
    }

    public void space(double height) {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        root.getChildren().add(r);
    }
}
