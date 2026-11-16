package tcraft;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 任务管理页。 */
public class PageTasks implements Page {

    private static final Pattern RUN_WITH_LOG = Pattern.compile("^运行 (.+) 安装器：(.+)$");
    private static final Pattern RUN_WITH_TIME = Pattern.compile("^运行 (.+) 安装器（(\\d+) 秒，下载依赖较慢请稍候）$");
    private static final String[] COUNT_PREFIXES = {
            "下载依赖库", "下载资源文件", "补抓失败文件", "下载整合包内容", "正在压缩", "正在复制", "正在备份"
    };
    private static final String[] PREFIXES = {
            "整合包安装：", "Mod 更新 · ", "导出存档 · ", "下载整合包资源 ", "复制实例 · ",
            "导出整合包：", "备份实例 · ", "安装游戏版本 ", "正在运行 ", "预下载 ",
            "安装 ", "下载 ", "运行 "
    };

    private final MainWindow win;
    private final Data data = Data.get();
    private VBox taskList;
    private Label progressLabel;
    private Label speedLabel;
    private Label fileLabel;
    private Label threadLabel;
    private final List<TaskCard> cards = new ArrayList<>();
    private Timeline timer;

    public PageTasks(MainWindow win) {
        this.win = win;
    }

    @Override
    public double leftWidth() {
        return 200;
    }

    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(30, 18, 20, 18));
        panel.setAlignment(Pos.TOP_CENTER);
        progressLabel = bigStat(panel, "总进度", "0.00 %");
        speedLabel = bigStat(panel, "下载速度", "0 B/s");
        fileLabel = bigStat(panel, "剩余文件", "0");
        threadLabel = bigStat(panel, "剩余线程", "0");
        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        panel.getChildren().add(sp);
        Ui.ListItem back = new Ui.ListItem(Theme.icon("arrow-left", 16, Theme.text), "返回", null, 34);
        back.setOnMouseClicked(e -> win.openPage("launch", true));
        panel.getChildren().add(back);
        updateStats();
        return panel;
    }

    private Label bigStat(VBox panel, String title, String value) {
        Label t = Ui.label(title, 14, Theme.accent);
        t.setAlignment(Pos.CENTER);
        t.setMaxWidth(Double.MAX_VALUE);
        Rectangle line = new Rectangle();
        line.setHeight(2);
        line.setFill(Theme.accent);
        line.widthProperty().bind(panel.widthProperty().subtract(36));
        Label v = Ui.bold(value, 20, Theme.text);
        v.setAlignment(Pos.CENTER);
        v.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(7, t, line, v);
        box.setAlignment(Pos.CENTER);
        VBox.setMargin(box, new Insets(0, 0, 26, 0));
        panel.getChildren().add(box);
        return v;
    }

    @Override
    public Node right() {
        VBox page = new VBox(10);
        page.setPadding(new Insets(20, 25, 12, 25));
        page.setMaxWidth(Double.MAX_VALUE);
        page.setMaxHeight(Double.MAX_VALUE);

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setMaxWidth(Double.MAX_VALUE);
        Label title = Ui.bold("任务管理", 22, Theme.text);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        StackPane clear = Ui.button("清除已完成", Ui.BTN_NORMAL, () -> {
            Tasks.INSTANCE.clearFinished();
            rebuild();
        });
        clear.setScaleX(0.9);
        clear.setScaleY(0.9);
        head.getChildren().addAll(title, sp, clear);

        taskList = new VBox(12);
        ScrollPane scroll = Ui.scroll(taskList);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getChildren().addAll(head, scroll);
        rebuild();

        timer = new Timeline(new KeyFrame(Duration.millis(450), e -> tick()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
        return page;
    }

    @Override
    public void onHide() {
        if (timer != null) timer.stop();
    }

    private void rebuild() {
        if (taskList == null) return;
        cards.clear();
        taskList.getChildren().clear();
        if (Tasks.INSTANCE.list.isEmpty()) {
            Ui.Card empty = Ui.card("");
            VBox box = new VBox(10);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(60, 20, 60, 20));
            box.getChildren().addAll(Theme.icon("circle-check", 40, Theme.gray4),
                    Ui.bold("暂无任务", 18, Theme.text),
                    Ui.label("下载或安装游戏时，任务会显示在这里。", 12.5, Theme.gray2));
            empty.add(box);
            taskList.getChildren().add(empty);
            return;
        }
        for (Tasks.Task task : Tasks.INSTANCE.list) {
            TaskCard card = new TaskCard(task);
            cards.add(card);
            taskList.getChildren().add(card.root);
        }
    }

    private int lastCount = -1;

    private void tick() {
        if (taskList == null) return;
        int count = Tasks.INSTANCE.list.size();
        if (count != lastCount) {
            lastCount = count;
            rebuild();
        }
        for (TaskCard card : cards) card.update();
        updateStats();
    }

    private void updateStats() {
        if (progressLabel == null) return;
        double overall = Tasks.INSTANCE.overall();
        progressLabel.setText(String.format("%.2f %%", overall * 100));
        String speed = "0 B/s";
        int files = 0;
        int threads = 0;
        for (Tasks.Task t : Tasks.INSTANCE.list) {
            if (t.state == Tasks.State.RUNNING) {
                if (t.speed != null && !t.speed.isEmpty()) speed = t.speed;
                files += t.remainingFiles;
                threads += t.threads;
            }
        }
        speedLabel.setText(speed);
        fileLabel.setText(String.valueOf(files));
        threadLabel.setText(String.valueOf(threads));
    }

    private class TaskCard {
        final Tasks.Task task;
        final VBox root;
        final Label stateLabel;
        final Rectangle bar;
        final Rectangle track;
        final Label stageLabel;
        final Label speedLabel;
        final VBox subs;
        final StackPane cancel;

        TaskCard(Tasks.Task task) {
            this.task = task;
            root = new VBox(8);
            root.setMaxWidth(Double.MAX_VALUE);
            root.setPadding(new Insets(14, 16, 14, 16));
            root.setBackground(Ui.fill(Theme.cardBg, 14));
            root.setEffect(Theme.shadow(16, 3, 0.14, Theme.text));

            Label title = Ui.bold(taskText(task.title), 14, Theme.text);
            stateLabel = Ui.label(stateText(task.state), 11.5, stateColor(task.state));
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            cancel = Ui.iconButton("x", 24, 13, "取消任务", () -> {
                task.cancel();
                update();
            });
            HBox head = new HBox(8, title, stateLabel, sp, cancel);
            head.setAlignment(Pos.CENTER_LEFT);

            track = new Rectangle();
            track.setHeight(6);
            track.setFill(Color.web("#343d4a", 0.10));
            track.widthProperty().bind(root.widthProperty().subtract(32));
            bar = new Rectangle();
            bar.setHeight(6);
            bar.setFill(Theme.accent);
            bar.widthProperty().bind(track.widthProperty().multiply(task.progress));
            StackPane progress = new StackPane(track, bar);
            StackPane.setAlignment(bar, Pos.CENTER_LEFT);
            progress.setMaxWidth(Double.MAX_VALUE);

            stageLabel = Ui.label(taskText(task.stage), 11.5, Theme.gray2);
            speedLabel = Ui.label(task.speed == null ? "" : task.speed, 11.5, Theme.gray3);

            subs = new VBox(3);
            for (Tasks.SubTask sub : task.subs) {
                subs.getChildren().add(subRow(sub));
            }
            if (!task.subs.isEmpty()) VBox.setMargin(subs, new Insets(4, 0, 0, 0));
            root.getChildren().addAll(head, progress, stageLabel, subs);
        }

        private Node subRow(Tasks.SubTask sub) {
            HBox row = new HBox(7);
            row.setAlignment(Pos.CENTER_LEFT);
            Node icon;
            switch (sub.state) {
                case DONE -> icon = Theme.icon("circle-check", 14, Theme.green);
                case FAILED -> icon = Theme.icon("circle-minus", 14, Theme.red);
                case CANCELED -> icon = Theme.icon("x", 14, Theme.gray3);
                default -> icon = Theme.icon("refresh-cw", 14, Theme.accent);
            }
            Label name = Ui.label(taskText(sub.name), 11.5, sub.state == Tasks.State.RUNNING ? Theme.text : Theme.gray2);
            row.getChildren().addAll(icon, name);
            return row;
        }

        void update() {
            bar.widthProperty().unbind();
            bar.widthProperty().bind(track.widthProperty().multiply(task.progress));
            stateLabel.setText(stateText(task.state));
            stateLabel.setTextFill(stateColor(task.state));
            stageLabel.setText(taskText(task.state == Tasks.State.FAILED && task.error != null ? task.error : task.stage));
            speedLabel.setText(task.speed == null ? "" : task.speed);
            cancel.setVisible(task.state == Tasks.State.RUNNING);
            subs.getChildren().clear();
            for (Tasks.SubTask sub : task.subs) subs.getChildren().add(subRow(sub));
        }
    }

    /** 任务标题/阶段/子任务在运行时写入，展示时统一补翻译。 */
    static String taskText(String s) {
        if (s == null || s.isEmpty()) return "";
        String exact = Lang.t(s);
        if (!exact.equals(s)) return exact;

        Matcher m = RUN_WITH_LOG.matcher(s);
        if (m.matches()) return Lang.tf("运行 %s 安装器：%s", m.group(1), m.group(2));
        m = RUN_WITH_TIME.matcher(s);
        if (m.matches()) return Lang.tf("运行 %s 安装器（%d 秒，下载依赖较慢请稍候）",
                m.group(1), Integer.parseInt(m.group(2)));

        if (s.startsWith("下载 ") && s.endsWith(" 安装器")) {
            return Lang.t("下载 ") + s.substring(3, s.length() - 4) + Lang.t(" 安装器");
        }
        if (s.startsWith("正在运行 ") && s.endsWith(" 安装器")) {
            return Lang.t("正在运行 ") + s.substring(5, s.length() - 4) + Lang.t(" 安装器");
        }
        if (s.startsWith("预下载 ") && s.contains(" 依赖")) {
            int i = s.indexOf(" 依赖");
            return Lang.t("预下载 ") + s.substring(4, i) + Lang.t(" 依赖") + s.substring(i + 3);
        }
        int dl = s.indexOf("下载：");
        if (dl > 0 && dl <= 6) {
            return Lang.tf("%s下载：%s", Lang.t(s.substring(0, dl)), s.substring(dl + 3));
        }
        for (String p : COUNT_PREFIXES) {
            if (s.startsWith(p + " ")) return Lang.t(p) + s.substring(p.length());
        }
        for (String p : PREFIXES) {
            if (s.startsWith(p)) return Lang.t(p) + s.substring(p.length());
        }
        return s;
    }

    private String stateText(Tasks.State state) {
        return switch (state) {
            case RUNNING -> Lang.t("进行中");
            case DONE -> Lang.t("已完成");
            case FAILED -> Lang.t("失败");
            default -> Lang.t("已取消");
        };
    }

    private Color stateColor(Tasks.State state) {
        return switch (state) {
            case RUNNING -> Theme.accent;
            case DONE -> Theme.green;
            case FAILED -> Theme.red;
            default -> Theme.gray3;
        };
    }
}
