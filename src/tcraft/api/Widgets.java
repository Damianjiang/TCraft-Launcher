package tcraft.api;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

// 启动器风格基础组件
public final class Widgets {

    private Widgets() {
    }

    public static Label title(String text) {
        return tcraft.Ui.bold(text, 26, tcraft.Theme.text);
    }

    public static Label text(String text) {
        return tcraft.Ui.label(text, 13, tcraft.Theme.gray2);
    }

    public static Label label(String text, double size) {
        return tcraft.Ui.label(text, size, tcraft.Theme.gray2);
    }

    public static javafx.scene.layout.StackPane button(String text, Runnable action) {
        return tcraft.Ui.button(text, tcraft.Ui.BTN_NORMAL, action);
    }

    public static javafx.scene.layout.StackPane primaryButton(String text, Runnable action) {
        return tcraft.Ui.button(text, tcraft.Ui.BTN_THEME, action);
    }

    public static javafx.scene.layout.StackPane dangerButton(String text, Runnable action) {
        return tcraft.Ui.button(text, tcraft.Ui.BTN_RED, action);
    }

    public static tcraft.Ui.Card card(String title) {
        return tcraft.Ui.card(title);
    }

    public static HBox row(double spacing, Node... nodes) {
        HBox box = new HBox(spacing, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... nodes) {
        VBox box = new VBox(spacing, nodes);
        box.setFillWidth(true);
        return box;
    }

    public static VBox header(String title, String subtitle) {
        VBox head = new VBox(5);
        head.setPadding(new Insets(0, 0, 18, 0));
        Label t = tcraft.Ui.bold(title, 30, tcraft.Theme.text);
        Label s = tcraft.Ui.label(subtitle, 13, tcraft.Theme.text);
        s.setOpacity(0.55);
        head.getChildren().addAll(t, s);
        return head;
    }

    public static Region spacer() {
        return tcraft.Ui.spacer();
    }

    public static TextField field(String hint) {
        return tcraft.Ui.field(hint);
    }

    public static tcraft.Ui.Check check(String text, boolean checked) {
        return tcraft.Ui.checkBox(text, checked);
    }

    public static ComboBox<String> combo(String[] items, int selected) {
        return tcraft.Ui.combo(items, selected);
    }

    public static ImageView image(String path, double width, double height) {
        return tcraft.Ui.image(path, width, height, true);
    }

    public static void grow(Node node) {
        if (node instanceof Region r) {
            HBox.setHgrow(r, Priority.ALWAYS);
        }
    }
}
