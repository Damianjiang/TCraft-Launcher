package tcraft;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class DataBackupPanel extends StackPane {

    private static final double WIDTH = 390;
    private final MainWindow win;
    private final BorderPane panel = new BorderPane();
    private final VBox inner = new VBox(0);
    private final Rectangle mask = new Rectangle();

    public DataBackupPanel(MainWindow win) {
        this.win = win;
        setVisible(false);
        setPickOnBounds(false);
        setAlignment(Pos.CENTER_RIGHT);
        mask.setFill(Color.web("#000000", 0.25));
        mask.setOpacity(0);
        mask.widthProperty().bind(widthProperty());
        mask.heightProperty().bind(heightProperty());
        mask.setOnMouseClicked(e -> {
            e.consume();
            close();
        });

        panel.setBackground(Ui.fill(Theme.dialogBg, 0));
        panel.setBorder(new Border(new BorderStroke(Theme.gray3, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 0, 1))));
        panel.setPrefWidth(WIDTH);
        panel.setMaxWidth(0);
        panel.setMinWidth(0);
        panel.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(panel, Pos.CENTER_RIGHT);
        panel.setEffect(new DropShadow(26, -6, 0,
                new Color(Theme.text.getRed(), Theme.text.getGreen(), Theme.text.getBlue(), 0.45)));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);

        inner.setPadding(new Insets(26, 26, 24, 26));
        inner.setPrefWidth(WIDTH);
        inner.setMaxWidth(WIDTH);
        inner.setOpacity(0);
        inner.setTranslateX(24);

        Label title = Ui.bold("数据备份", 24, Theme.accent);
        Label subtitle = Ui.label("导出或导入整个启动器的数据。", 12.5, Theme.gray2);
        subtitle.setWrapText(true);
        VBox head = new VBox(5, title, subtitle);
        StackPane close = Ui.button("关闭", Ui.BTN_NORMAL, this::close);
        close.setMinWidth(78);
        HBox top = new HBox(12, head);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(head, javafx.scene.layout.Priority.ALWAYS);
        top.getChildren().add(close);

        Region line = Ui.line(Theme.accent, 2);
        VBox.setMargin(line, new Insets(12, 0, 0, 0));

        Ui.Card info = Ui.card("导出内容");
        Label content = Ui.label("启动器配置、实例记录、插件、插件工程、皮肤、图标和预设。\n"
                + "不包含游戏本体、资源文件、缓存、日志、运行库和 EasyTier 二进制。", 12.5, Theme.gray2);
        content.setWrapText(true);
        info.add(content);

        Label warning = Ui.label("备份可能包含账户令牌和代理密码，请只保存在可信设备上，不要分享给他人。",
                12, Theme.red);
        warning.setWrapText(true);

        StackPane export = Ui.button("导出全部数据", Ui.BTN_HIGHLIGHT, () -> {
            close();
            DataBackup.chooseExport(win);
        });
        export.setMaxWidth(Double.MAX_VALUE);
        StackPane importBtn = Ui.button("导入备份并重启", Ui.BTN_NORMAL, () -> {
            close();
            DataBackup.chooseImport(win);
        });
        importBtn.setMaxWidth(Double.MAX_VALUE);

        Label footer = Ui.label("导入会覆盖当前配置，旧数据保留为 .tcl.pre-import-*；完成后自动重启。",
                12, Theme.gray3);
        footer.setWrapText(true);

        inner.getChildren().addAll(top, line, info, warning, export, importBtn, footer);
        VBox.setMargin(info, new Insets(18, 0, 10, 0));
        VBox.setMargin(warning, new Insets(4, 0, 14, 0));
        VBox.setMargin(export, new Insets(0, 0, 9, 0));
        VBox.setMargin(importBtn, new Insets(0, 0, 14, 0));

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
        panel.setCenter(scroll);

        getChildren().addAll(mask, panel);
    }

    public void open() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::open);
            return;
        }
        if (isVisible()) return;
        setVisible(true);
        setPickOnBounds(true);
        panel.setMaxWidth(0);
        panel.setMinWidth(0);
        inner.setOpacity(0);
        inner.setTranslateX(24);
        mask.setOpacity(0);

        FadeTransition maskIn = new FadeTransition(Duration.millis(180), mask);
        maskIn.setToValue(1);
        maskIn.play();
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(panel.maxWidthProperty(), WIDTH, Interpolator.EASE_OUT)));
        slide.play();
        Timeline fade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(inner.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(180), new KeyValue(inner.opacityProperty(), 1,
                        Interpolator.EASE_OUT)));
        fade.setDelay(Duration.millis(80));
        fade.play();
        Timeline move = new Timeline(new KeyFrame(Duration.millis(220),
                new KeyValue(inner.translateXProperty(), 0, Interpolator.EASE_OUT)));
        move.setDelay(Duration.millis(80));
        move.play();
    }

    public void close() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::close);
            return;
        }
        if (!isVisible()) return;
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(170),
                new KeyValue(panel.maxWidthProperty(), 0, Interpolator.EASE_IN)));
        FadeTransition fade = new FadeTransition(Duration.millis(170), mask);
        fade.setToValue(0);
        FadeTransition out = new FadeTransition(Duration.millis(140), inner);
        out.setToValue(0);
        slide.play();
        fade.play();
        out.play();
        PauseTransition done = new PauseTransition(Duration.millis(190));
        done.setOnFinished(e -> {
            setVisible(false);
            setPickOnBounds(false);
            panel.setMaxWidth(0);
        });
        done.play();
    }
}
