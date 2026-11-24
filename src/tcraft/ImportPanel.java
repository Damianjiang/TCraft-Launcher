package tcraft;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class ImportPanel extends StackPane {

    private final BorderPane panel = new BorderPane();
    private final VBox inner = new VBox(0);
    private final Label title = Ui.bold("正在导入", 23, Theme.accent);
    private final Label subtitle = Ui.label("", 13, Theme.gray2);
    private final Label step = Ui.label("准备导入", 12, Theme.text);
    private final Label count = Ui.label("", 12, Theme.gray2);
    private final Label percent = Ui.label("0 %", 12, Theme.gray2);
    private final Ui.Loading loading = new Ui.Loading(260, 76, "");
    private final Rectangle mask = new Rectangle();
    private final Rectangle track = new Rectangle();
    private final Rectangle fill = new Rectangle();
    private final SimpleDoubleProperty progress = new SimpleDoubleProperty(0);
    private final VBox barBox = new VBox();
    private int generation;
    private boolean busy;

    public ImportPanel() {
        setVisible(false);
        setPickOnBounds(false);
        setAlignment(Pos.TOP_LEFT);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        mask.setFill(Color.web("#000000", 0.25));
        mask.setOpacity(0);
        mask.widthProperty().bind(widthProperty());
        mask.heightProperty().bind(heightProperty());
        mask.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        panel.setBackground(Ui.fill(Color.web("#FBFBFB"), 0));
        panel.setBorder(new javafx.scene.layout.Border(new javafx.scene.layout.BorderStroke(
                Theme.gray3, javafx.scene.layout.BorderStrokeStyle.SOLID, javafx.scene.layout.CornerRadii.EMPTY,
                new javafx.scene.layout.BorderWidths(0, 1, 0, 0))));
        panel.setPrefWidth(364);
        panel.setMaxWidth(0);
        panel.setMinWidth(0);
        panel.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(panel, Pos.CENTER_LEFT);
        panel.setEffect(new DropShadow(26, 6, 0,
                new Color(Theme.text.getRed(), Theme.text.getGreen(), Theme.text.getBlue(), 0.45)));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);

        inner.setPadding(new Insets(26, 26, 24, 26));
        inner.setPrefWidth(364);
        inner.setMaxWidth(364);
        inner.setOpacity(0);

        Region line = new Region();
        line.setMinHeight(2);
        line.setMaxHeight(2);
        line.setMaxWidth(Double.MAX_VALUE);
        line.setBackground(Ui.fill(Theme.accent, 0.18));
        VBox.setMargin(line, new Insets(2, 0, 0, 0));

        VBox.setMargin(loading, new Insets(6, 0, 2, 0));

        subtitle.setAlignment(Pos.CENTER);
        subtitle.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(subtitle, new Insets(4, 0, 0, 0));

        track.setHeight(4);
        track.widthProperty().bind(inner.widthProperty().subtract(52));
        track.setFill(Color.web(
                String.format("#%02x%02x%02x",
                        (int) (Theme.gray3.getRed() * 255), (int) (Theme.gray3.getGreen() * 255),
                        (int) (Theme.gray3.getBlue() * 255)), 0.25));
        fill.setHeight(4);
        fill.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.accentLight), new Stop(0.6, Theme.accent)));
        fill.widthProperty().bind(track.widthProperty().multiply(progress));
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        barBox.getChildren().add(bar);
        VBox.setMargin(barBox, new Insets(16, 0, 14, 0));

        HBox stepRow = new HBox(10);
        stepRow.setAlignment(Pos.CENTER_LEFT);
        stepRow.getChildren().addAll(Ui.label("当前文件", 12, Theme.gray2), Ui.spacer(), step);
        step.setMaxWidth(226);
        step.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox countRow = new HBox(10);
        countRow.setAlignment(Pos.CENTER_LEFT);
        countRow.getChildren().addAll(Ui.label("进度", 12, Theme.gray2), Ui.spacer(), count, percent);

        inner.getChildren().addAll(title, line, loading, subtitle, barBox, stepRow, countRow);
        VBox.setVgrow(loading, javafx.scene.layout.Priority.ALWAYS);
        panel.setCenter(inner);

        getChildren().addAll(mask, panel);
        panel.setMaxWidth(0);
    }

    public int begin(String heading, String target, int total) {
        generation++;
        busy = true;
        setVisible(true);
        setPickOnBounds(true);
        title.setText(Lang.t(heading));
        subtitle.setText(target);
        step.setText(Lang.t("准备导入"));
        count.setText(total > 0 ? "0 / " + total : Lang.t("正在读取"));
        percent.setText("0 %");
        progress.set(0);
        barBox.setVisible(total > 0);
        barBox.setManaged(total > 0);
        loading.start();

        panel.setMaxWidth(0);
        panel.setMinWidth(0);
        inner.setOpacity(0);
        inner.setScaleX(0.8);
        inner.setScaleY(0.8);
        mask.setOpacity(0);

        FadeTransition maskIn = new FadeTransition(Duration.millis(200), mask);
        maskIn.setToValue(1);
        maskIn.play();
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(190),
                new KeyValue(panel.maxWidthProperty(), 364, Interpolator.EASE_OUT)));
        slide.play();
        Timeline fade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(inner.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(150), new KeyValue(inner.opacityProperty(), 1,
                        Interpolator.EASE_OUT)));
        fade.setDelay(Duration.millis(100));
        fade.play();
        Timeline grow = new Timeline(new KeyFrame(Duration.millis(500),
                new KeyValue(inner.scaleXProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                new KeyValue(inner.scaleYProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE)));
        grow.setDelay(Duration.millis(100));
        grow.play();
        return generation;
    }

    public void progress(int gen, int done, int total, String text) {
        if (gen != generation || !busy) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> progress(gen, done, total, text));
            return;
        }
        double value = total <= 0 ? 0 : Math.min(1, done / (double) total);
        progress.set(value);
        count.setText(total > 0 ? done + " / " + total : Lang.t("正在读取"));
        percent.setText(Math.round(value * 100) + " %");
        step.setText(text == null ? "" : Lang.t(text));
        barBox.setVisible(total > 0);
        barBox.setManaged(total > 0);
    }

    public void finish(int gen, String text, Runnable after) {
        if (gen != generation) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> finish(gen, text, after));
            return;
        }
        progress.set(1);
        count.setText(Lang.t("完成"));
        percent.setText("100 %");
        step.setText(text == null ? "" : Lang.t(text));
        loading.stop();
        PauseTransition wait = new PauseTransition(Duration.millis(650));
        wait.setOnFinished(e -> {
            close(after);
            Threads.start("TCL-Memory", () -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                Memory.trim();
            });
        });
        wait.play();
    }

    public void close() {
        close(null);
    }

    public void close(Runnable after) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> close(after));
            return;
        }
        if (!isVisible()) {
            if (after != null) after.run();
            return;
        }
        loading.stop();
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(180),
                new KeyValue(panel.maxWidthProperty(), 0, Interpolator.EASE_IN)));
        FadeTransition fade = new FadeTransition(Duration.millis(180), mask);
        fade.setToValue(0);
        FadeTransition out = new FadeTransition(Duration.millis(150), inner);
        out.setToValue(0);
        slide.play();
        fade.play();
        out.play();
        int closing = generation;
        PauseTransition done = new PauseTransition(Duration.millis(210));
        done.setOnFinished(e -> {
            if (closing != generation) return;
            setVisible(false);
            setPickOnBounds(false);
            busy = false;
            if (after != null) after.run();
        });
        done.play();
    }

    public boolean isBusy() {
        return busy;
    }
}
