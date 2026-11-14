package tcraft;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.time.LocalTime;

/** 主页右侧：日志卡、问候语、启动按钮和下拉菜单。 */
public class PageLaunch implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    /** 自动关闭后的 250ms 防误触窗口。 */
    private long menuClosedTick = 0;
    private Popup menuPopup;
    /** 箭头旋转动画和当前朝向。 */
    private javafx.animation.Animation chevronSpin;
    private Node menuChevron;

    private javafx.animation.AnimationTimer aprilTimer;
    private long aprilLastTick = 0;
    private final javafx.geometry.Point2D[] aprilMouse = {null};
    private final javafx.geometry.Point2D[] aprilSpeed = {new javafx.geometry.Point2D(0, 0)};
    private final javafx.geometry.Point2D[] aprilOffset = {new javafx.geometry.Point2D(0, 0)};
    private double aprilTraveled;
    /** 鼠标静止的 tick 计数。 */
    private int aprilIdleCount = 0;
    private javafx.geometry.Point2D aprilMouseLast = null;
    /** 累计位移超过 2500 弹一次「放弃吧」。 */
    private double aprilGiveUpDistance = -1500;
    private Node aprilButtonNode;

    /** 4 月 1 日且今年还没触发过。 */
    public static boolean isAprilEnabled() {
        java.time.LocalDate now = java.time.LocalDate.now();
        return now.getMonthValue() == 4 && now.getDayOfMonth() == 1
                && Data.get().getInt("AprilYear", 0) != now.getYear();
    }

    private boolean aprilGiveup() {
        return data.getInt("AprilYear", 0) == java.time.LocalDate.now().getYear();
    }

    public PageLaunch(MainWindow win) {
        this.win = win;
    }

    @Override
    public Node right() {
        StackPane root = new StackPane();
        root.setAlignment(Pos.TOP_LEFT);
        root.setPickOnBounds(false);

        // 下边留 150 给启动按钮
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox panMain = new VBox(15);
        panMain.setPadding(new Insets(25, 25, 150, 25));
        panMain.setFillWidth(true);
        panMain.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(panMain);

        // 启动日志卡只在调试模式显示
        if (data.getBool("SystemDebugMode", false)) {
            Ui.Card logCard = Ui.card("启动日志");
            logCard.setMaxWidth(Double.MAX_VALUE);
            VBox lines = new VBox(3);
            lines.setPadding(new Insets(0, 15, 13, 15));
            java.util.List<String> logs = data.launchLog.isEmpty()
                    ? java.util.List.of("暂无启动日志。")
                    : data.launchLog;
            int from = Math.max(0, logs.size() - 30);
            for (String line : logs.subList(from, logs.size())) {
                Label l = Ui.label(line, 12, Theme.gray2);
                l.setWrapText(true);
                l.setMaxWidth(Double.MAX_VALUE);
                lines.getChildren().add(l);
            }
            logCard.add(lines);
            panMain.getChildren().add(logCard);
        }
        root.getChildren().add(scroll);

        // 右下角按钮区
        VBox bottom = new VBox(10);
        bottom.setAlignment(Pos.BOTTOM_RIGHT);
        bottom.setPrefWidth(330);
        bottom.setMaxWidth(330);
        bottom.setPickOnBounds(false);
        StackPane.setAlignment(bottom, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(bottom, new Insets(0, 30, 25, 0));

        // 问候语按小时分段
        VBox greetBox = new VBox(3);
        greetBox.setAlignment(Pos.BOTTOM_RIGHT);
        String greeting = greeting();
        Data.Account account = data.account();
        String name = account == null ? "" : account.name;
        // 阴影偏移约 (0.7, 0.7)
        Label greet = name.isEmpty() ? Ui.semi(greeting, 19, Color.WHITE)
                : Ui.semi(greeting + "，" + name, 19, Color.WHITE);
        greet.setEffect(new DropShadow(4, 0.7, 0.7, Color.web("#000000", 0.65)));
        greet.setMaxWidth(330);
        greet.setAlignment(Pos.CENTER_RIGHT);
        Label sub = Ui.label(name.isEmpty() ? "欢迎回到 TCL" : "欢迎回来，今天也要玩得开心",
                12, Color.web("#ffffff", 0.95));
        sub.setEffect(new DropShadow(3, 0.7, 0.7, Color.web("#000000", 0.6)));
        sub.setMaxWidth(330);
        sub.setAlignment(Pos.CENTER_RIGHT);
        greetBox.getChildren().addAll(greet, sub);
        bottom.getChildren().add(greetBox);

        // 启动按钮和 48px 连体下拉
        HBox launchRow = new HBox(6);
        launchRow.setAlignment(Pos.CENTER_RIGHT);
        Data.Instance instance = data.instance();
        StackPane launchBtn = buildLaunchButton(instance);
        StackPane menuBtn = buildMenuButton(instance);
        HBox.setHgrow(launchBtn, Priority.ALWAYS);
        launchBtn.setMaxWidth(Double.MAX_VALUE);
        launchRow.getChildren().addAll(launchBtn, menuBtn);
        bottom.getChildren().add(launchRow);
        root.getChildren().add(bottom);
        return root;
    }

    /** 问候语按小时分段。 */
    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour <= 5) return "夜深了";
        if (hour <= 11) return "早上好";
        if (hour <= 17) return "下午好";
        return "晚上好";
    }

    /** 启动按钮，文案和可用状态由状态机决定。 */
    private StackPane buildLaunchButton(Data.Instance instance) {
        StackPane btn = new StackPane();
        btn.setPrefHeight(54);
        btn.setMinHeight(54);
        btn.setMaxHeight(54);
        btn.setCursor(Cursor.HAND);
        btn.setEffect(Theme.shadow(10, 3, 0.25, Color.web("#1a2233")));

        // 没实例走下载，有实例走启动
        boolean has = instance != null;
        Label main = Ui.semi(has ? "启动游戏" : "下载游戏", 14, Color.WHITE);
        main.setTranslateY(-7.5); // padding 下 15，文字中心上移 7.5
        // 版本名贴底居中
        Label version = Ui.label(has ? instance.name : "未找到可用的游戏版本", 11, Color.web("#ffffff", 0.95));
        version.setMaxWidth(Double.MAX_VALUE);
        version.setAlignment(Pos.CENTER);
        version.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        StackPane.setAlignment(version, Pos.BOTTOM_CENTER);
        StackPane.setMargin(version, new Insets(0, 15, 10, 15));
        btn.getChildren().addAll(main, version);

        // 常态主题色，悬停提亮一档
        applyBtnBg(btn, Theme.accentDark);
        Ui.animateBg(btn, Theme.accentDark, Theme.accentDark, 1); // 初始化背景，幂等
        btn.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> Ui.animateBg(btn, Theme.accentDark, Theme.accent, 100));
        btn.addEventHandler(MouseEvent.MOUSE_EXITED, e -> Ui.animateBg(btn, Theme.accent, Theme.accentDark, 100));
        btn.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> Ui.scale(btn, 0.96)); // 按下缩放 0.96
        btn.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> Ui.scale(btn, 1));

        btn.setOnMouseClicked(e -> {
            if (isAprilEnabled() && !aprilGiveup()) {
                // 愚人节分支，复位后继续启动
                aprilSurrender();
            }
            if (!has) {
                // 没实例跳下载页
                win.openPage("download", true);
            } else {
                win.launchGame();
            }
        });
        aprilButtonNode = btn;
        if (isAprilEnabled() && !aprilGiveup()) startAprilEscape(btn);
        return btn;
    }

    private static void applyBtnBg(StackPane btn, Color c) {
        btn.setBackground(new Background(new BackgroundFill(c, new CornerRadii(8), Insets.EMPTY)));
    }

    /** 连体下拉按钮，48×54。 */
    private StackPane buildMenuButton(Data.Instance instance) {
        Node chevron = Theme.icon("chevron-down", 30, Color.WHITE);
        menuChevron = chevron;
        // 背景和阴影单独一层，旋转时圆角不会被切
        Region bg = new Region();
        bg.setBackground(Ui.fill(Theme.accentDark, 10));
        bg.setEffect(Theme.shadow(10, 3, 0.25, Color.web("#1a2233")));
        StackPane menu = new StackPane(bg, chevron);
        menu.setPrefSize(48, 54);
        menu.setMinSize(48, 54);
        menu.setMaxSize(48, 54);
        menu.setCursor(Cursor.HAND);
        Ui.tooltip(menu, "实例管理与版本设置");
        menu.setOnMouseClicked(e -> toggleLaunchMenu(menu));
        return menu;
    }

    /** 箭头跟菜单状态走：展开朝上、收起朝下，连点不会和状态脱节。 */
    private void spinChevron(boolean menuOpen) {
        if (menuChevron == null) return;
        if (chevronSpin != null) chevronSpin.stop();
        RotateTransition rt = new RotateTransition(Duration.millis(240), menuChevron);
        rt.setFromAngle(menuChevron.getRotate());
        rt.setToAngle(menuOpen ? 180 : 0);
        rt.setInterpolator(OUT_BACK);
        rt.play();
        chevronSpin = rt;
    }

    /** 回弹缓动。 */
    private static final Interpolator OUT_BACK = Ui.EASE_OUT_BACK;

    /** 开关菜单，250ms 内挡掉误触。 */
    private void toggleLaunchMenu(Node anchor) {
        if (menuPopup != null && menuPopup.isShowing()) {
            closeLaunchMenu(menuPopup);
            return;
        }
        if (System.currentTimeMillis() - menuClosedTick < 250) return;
        openLaunchMenu(anchor);
    }

    private void openLaunchMenu(Node anchor) {
        Data.Instance instance = data.instance();
        // 没实例时禁用版本设置
        boolean hasInstance = instance != null;
        // 连点兜底：上一张还挂着就先收掉，防止叠两层
        if (menuPopup != null && menuPopup.isShowing()) menuPopup.hide();
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        menuPopup = popup;
        VBox card = new VBox(2);
        card.setPadding(new Insets(8));
        card.setPrefWidth(204);
        card.setMinWidth(204);
        card.setBackground(Ui.fill(Theme.cardBg, 10));
        card.setEffect(Theme.shadow(18, 3, 0.35, Theme.text));
        Ui.ListItem select = new Ui.ListItem(Theme.icon("layers", 17, Theme.text), "版本选择", null, 42);
        select.setOnMouseClicked(e -> {
            // 走统一收起，箭头才跟得上状态
            closeLaunchMenu(popup);
            win.openVersionSelect();
        });
        Ui.ListItem setup = new Ui.ListItem(Theme.icon("settings", 17, Theme.text), "版本设置", null, 42);
        if (hasInstance) {
            setup.setOnMouseClicked(e -> {
                closeLaunchMenu(popup);
                win.openInstanceSetup();
            });
        } else {
            setup.setDisable(true);
            setup.setOpacity(0.45);
        }
        card.getChildren().addAll(select, setup);
        // 展开动画初值
        card.setOpacity(0);
        card.setScaleX(0.94);
        card.setScaleY(0.94);
        card.setTranslateY(10);
        popup.getContent().add(card);
        popup.setOnHidden(e -> menuClosedTick = System.currentTimeMillis());
        // 卡片贴在按钮上方，高度按内容实测，内容变了也不会飘
        javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        double cardH = Math.max(90, card.prefHeight(-1));
        popup.show(anchor, b.getMinX() + b.getWidth() - 204, b.getMinY() - 6 - cardH);
        spinChevron(true);
        Timeline in = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.scaleXProperty(), 0.94), new KeyValue(card.scaleYProperty(), 0.94),
                        new KeyValue(card.translateYProperty(), 10)),
                new KeyFrame(Duration.millis(130), new KeyValue(card.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(card.scaleXProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                        new KeyValue(card.scaleYProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                        new KeyValue(card.translateYProperty(), 0, Ui.EASE_OUT_FLUENT)));
        in.play();
    }

    /** 收起动画。 */
    private void closeLaunchMenu(Popup popup) {
        spinChevron(false);
        Node card = popup.getContent() != null && !popup.getContent().isEmpty()
                ? popup.getContent().get(0) : null;
        if (card == null) {
            popup.hide();
            return;
        }
        Timeline out = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), card.getOpacity())),
                new KeyFrame(Duration.millis(80), new KeyValue(card.opacityProperty(), 0)));
        out.setOnFinished(e -> popup.hide());
        out.play();
        Timeline scale = new Timeline(new KeyFrame(Duration.millis(90),
                new KeyValue(card.scaleXProperty(), 0.96), new KeyValue(card.scaleYProperty(), 0.96)));
        scale.play();
    }

    /** 进入逃跑模式。 */
    private void startAprilEscape(StackPane btn) {
        if (aprilTimer != null) return;
        data.log("愚人节彩蛋：启动按钮开始躲避鼠标");
        javafx.scene.Scene scene = btn.getScene();
        if (scene == null) {
            btn.sceneProperty().addListener((o, s2, sc) -> {
                if (sc != null) startAprilEscape(btn);
            });
            return;
        }
        scene.getRoot().setOnMouseMoved(e -> {
            aprilMouse[0] = new javafx.geometry.Point2D(e.getSceneX(), e.getSceneY());
        });
        aprilTimer = new javafx.animation.AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - aprilLastTick < 150_000_000L) return;
                aprilLastTick = now;
                aprilStep(btn, scene);
            }
        };
        aprilTimer.start();
    }

    /** 单步物理。 */
    private void aprilStep(StackPane btn, javafx.scene.Scene scene) {
        javafx.geometry.Point2D mouse = aprilMouse[0];
        if (mouse == null) return;
        // 静止 5 秒后回归起始位
        if (aprilMouseLast != null && aprilMouseLast.distance(mouse) < 0.5) {
            aprilIdleCount++;
        } else {
            aprilMouseLast = mouse;
            aprilIdleCount = 0;
        }
        boolean idle = aprilIdleCount >= 64 * 5;

        double w = btn.getWidth() / 2, h = btn.getHeight() / 2;
        javafx.geometry.Bounds b = btn.localToScene(btn.getBoundsInLocal());
        javafx.geometry.Point2D center = new javafx.geometry.Point2D(
                b.getMinX() + btn.getWidth() / 2 + aprilOffset[0].getX(),
                b.getMinY() + btn.getHeight() / 2 + aprilOffset[0].getY());
        javafx.geometry.Point2D vec = mouse.subtract(center);
        javafx.geometry.Point2D dir = vec.normalize();
        javafx.geometry.Point2D direction = dir.multiply(-1);
        double dist = Math.hypot(Math.max(0, Math.abs(vec.getX()) - w), Math.max(0, Math.abs(vec.getY()) - h));
        double difficulty = 1 / (1 + Math.pow(aprilTraveled / 6000, 2)) + 0.25;
        double accMag = Math.max(0, -0.65 - Math.log((dist / difficulty + 0.4) / 200)) * difficulty;
        javafx.geometry.Point2D acc = direction.multiply(accMag);
        if (idle) {
            // 离原位超过 250 才拉回
            javafx.geometry.Bounds bodyScene = win.body.localToScene(win.body.getBoundsInLocal());
            javafx.geometry.Point2D safe = mouse.subtract(
                    bodyScene.getMinX() + w, bodyScene.getMinY() + win.body.getHeight() - h * 3);
            javafx.geometry.Point2D back = aprilOffset[0].multiply(-1);
            if (safe.magnitude() > 250 && back.magnitude() > 0.4) {
                acc = acc.add(back.multiply(0.0005)).add(back.normalize().multiply(0.15));
            }
        }
        javafx.geometry.Point2D speed = aprilSpeed[0].multiply(0.8).add(acc);
        double v = speed.magnitude();
        if (v < 0.01) return;
        if (v > 60) {
            speed = speed.normalize().multiply(60);
            v = 60;
        }
        aprilSpeed[0] = speed;
        aprilTraveled += v;
        javafx.geometry.Point2D offset = aprilOffset[0].add(speed);

        // 按移动方向做挤压拉伸
        double sx = clamp(1 - (Math.abs(direction.getX()) - Math.abs(direction.getY())) * (v / 160), 0.2, 1.8);
        double sy = clamp(1 - (Math.abs(direction.getY()) - Math.abs(direction.getX())) * (v / 100), 0.2, 1.8);
        btn.setScaleX(sx);
        btn.setScaleY(sy);

        // 每 2500 位移提示一次放弃
        aprilGiveUpDistance += v;
        if (aprilGiveUpDistance > 2500) {
            aprilGiveUpDistance = 0;
            String[] hints = {
                    "放弃吧！只需要点一下右下角的小白旗……",
                    "看到右下角的那面小白旗了吗？",
                    "这里建议点一下右下角的小白旗投降呢.jpg",
                    "右下角的小白旗永远等着你……"
            };
            win.toast(hints[java.util.concurrent.ThreadLocalRandom.current().nextInt(hints.length)], "", 2);
        }

        // 越界后从对侧进入并反向踢一脚
        javafx.geometry.Bounds bodyScene = win.body.localToScene(win.body.getBoundsInLocal());
        double bodyW = bodyScene.getWidth(), bodyH = bodyScene.getHeight();
        javafx.geometry.Point2D abs = center.add(speed);
        if (abs.getX() < bodyScene.getMinX() - w * 2) {
            offset = offset.add(bodyW + w * 2, 0);
            aprilSpeed[0] = aprilSpeed[0].subtract(80, 0);
        } else if (abs.getX() > bodyScene.getMinX() + bodyW) {
            offset = offset.subtract(bodyW + w * 2, 0);
            aprilSpeed[0] = aprilSpeed[0].add(80, 0);
        } else if (abs.getY() < bodyScene.getMinY() - h * 2) {
            offset = offset.add(0, bodyH + h * 2);
            aprilSpeed[0] = aprilSpeed[0].subtract(0, 25);
        } else if (abs.getY() > bodyScene.getMinY() + bodyH) {
            offset = offset.subtract(0, bodyH + h * 2);
            aprilSpeed[0] = aprilSpeed[0].add(0, 25);
        }
        aprilOffset[0] = offset;
        if (aprilButtonNode != null) {
            aprilButtonNode.setTranslateX(offset.getX());
            aprilButtonNode.setTranslateY(offset.getY());
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** 投降：记年份并复位按钮。 */
    private void aprilSurrender() {
        stopAprilEscape();
        if (aprilButtonNode != null) {
            aprilButtonNode.setTranslateX(0);
            aprilButtonNode.setTranslateY(0);
            aprilButtonNode.setScaleX(1);
            aprilButtonNode.setScaleY(1);
        }
        aprilOffset[0] = new javafx.geometry.Point2D(0, 0);
        aprilSpeed[0] = new javafx.geometry.Point2D(0, 0);
        data.set("AprilYear", java.time.LocalDate.now().getYear());
        win.refreshExtraButtons(); // 刷新右下角悬浮按钮
    }

    private void stopAprilEscape() {
        if (aprilTimer != null) {
            aprilTimer.stop();
            aprilTimer = null;
        }
    }

    @Override
    public void onHide() {
        stopAprilEscape();
    }
}
