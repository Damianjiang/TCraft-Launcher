package tcraft;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.stage.Popup;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 主窗口：标题栏、页面切换、背景、启动侧边栏、Toast、弹窗和全局搜索。 */
public class MainWindow {

    public final Stage stage;
    public final Data data = Data.get();
    public final StackPane root;
    public StackPane frame;
    public StackPane titleBar;
    public StackPane body;
    public StackPane pageHost;
    public StackPane leftHost;
    public StackPane rightHost;
    public StackPane toastLayer;
    public StackPane launchLayer;
    public StackPane modalLayer;
    public StackPane searchLayer;

    private VBox toastList;
    private Rectangle bgRect;
    private javafx.scene.media.MediaView vidBack;
    private javafx.scene.media.MediaPlayer vidPlayer;
    private Rectangle cover;
    private Region coverShadow;
    private Pane coverLayer;
    private HBox navBox;
    private HBox badgeBox;
    private final List<NavTab> navTabs = new ArrayList<>();
    private StackPane badgeAvatar;
    private Label badgeName;
    private Node titleLogoNode;
    private StackPane modalHost;
    private boolean modalDismissable = true;
    private Node modalContent;
    private FadeTransition maskFade;
    private Timeline modalExit;
    /** 弹窗关闭回调，只触发一次。 */
    private Runnable modalCloseHook;
    private TextFieldBridge searchField;

    private Page currentPage;
    /** 当前挂在 leftHost/rightHost 上的节点，动画作用在它们身上。 */
    private Node curLeft, curRight;
    public String currentKey = "launch";
    public String pendingSetupSection = "launch";
    /** 资源详情页的入参。 */
    public ResourceInfo pendingResource = null;
    private int switchToken;
    private boolean closing;

    private double baseOpacity = 1;
    private int resizeEdge = 0;
    private double pressSceneX, pressSceneY, startW, startH, startX, startY;
    private double dragX, dragY;

    private StackPane extraLayer;
    private VBox extraRight;
    private StackPane btnExtraTask;
    private StackPane btnExtraMusic;
    private StackPane btnExtraTop;
    private StackPane btnExtraApril;
    private StackPane btnExtraShutdown;
    private Arc taskProgressArc;
    private Process gameProcess;
    private boolean musicAvailable;
    private ScrollPane currentScroll;
    private ChangeListener<Number> scrollListener;
    private Timeline shellTimer;

    /** 重建期间跳过页面入场动画。 */
    private boolean suppressPageEnter;

    /** 顶部语言弹层。 */
    private Popup langPopup;

    /** 次级页标题层：返回键 + 页名，只切透明度。 */
    private HBox titleInner;
    private javafx.scene.Node leftGroup;
    private Label titleInnerLabel;

    /** 语言按钮，也是弹层的锚点。 */
    private StackPane langButton;

    /** 截图自检用：打开语言弹层并回报状态。 */
    void debugLanguageMenu() {
        showLanguageMenu(langButton);
        if (langPopup == null) return;
        Node card = langPopup.getContent().isEmpty() ? null : langPopup.getContent().get(0);
        if (card == null) return;
        javafx.geometry.Bounds a = langButton.localToScreen(langButton.getBoundsInLocal());
        // Popup 是独立窗口，root.snapshot 抓不到，只能量窗口坐标再截卡片。
        javafx.animation.PauseTransition geom = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(350));
        geom.setOnFinished(e -> System.out.printf(
                "[MENU] popup=(%.1f,%.1f) 右边缘=%.1f 锚点右=%.1f 上边=%.1f 锚点下=%.1f 卡片=%.1fx%.1f%n",
                langPopup.getX(), langPopup.getY(), langPopup.getX() + card.getBoundsInLocal().getWidth(), a.getMaxX(),
                langPopup.getY(), a.getMaxY(),
                card.getBoundsInLocal().getWidth(), card.getBoundsInLocal().getHeight()));
        geom.play();
        // 展开动画采样：0 / 60 / 130 / 220ms 各取一次 scale 和 opacity
        for (int t : new int[]{0, 60, 130, 220}) {
            javafx.animation.PauseTransition pt =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(t));
            pt.setOnFinished(e -> System.out.printf("[MENU t=%dms] scale=%.3f translateY=%.2f opacity=%.2f%n",
                    t, card.getScaleX(), card.getTranslateY(), card.getOpacity()));
            pt.play();
        }
        javafx.animation.PauseTransition shot =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        shot.setOnFinished(e -> {
            try {
                javafx.scene.image.WritableImage img = card.snapshot(null, null);
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
                        (int) img.getWidth(), (int) img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bi.getHeight(); y++) {
                    for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
                java.io.File out = Theme.root().resolve("snapshots/menu_card.png").toFile();
                javax.imageio.ImageIO.write(bi, "png", out);
                System.out.println("[MENU] 卡片快照已存 " + out);
            } catch (Exception ex) {
                System.out.println("[MENU] 卡片快照失败 " + ex);
            }
        });
        shot.play();
    }
    private int extraShown = 0;

    public MainWindow(Stage stage) {
        this.stage = stage;
        root = new StackPane();
        // modena 给根节点铺了 #f4f4f4 白底，透明窗口会露白，清掉。
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        Path pictures = Paths.get(data.gameDir, "TCL", "Pictures");
        if (Theme.loadCustomWallpaper(pictures)) {
            data.log("已从 " + pictures + " 加载自定义背景");
        }
        loadThemeFromSettings();
        buildFrame();
        try {
            Music.init(data.gameDir);
            musicAvailable = Music.available();
        } catch (Throwable ignored) {
            musicAvailable = false;
        }
        Scene scene = new Scene(root, 850, 500, Color.TRANSPARENT);
        if (Theme.css() != null) scene.getStylesheets().add(Theme.css().toExternalForm());
        // 主题色一换就重挂整张样式表，弹层和菜单这些走 CSS 的才会跟着换色
        Theme.onCssReload(() -> {
            URL u = Theme.css();
            if (u != null) scene.getStylesheets().setAll(u.toExternalForm());
        });
        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("TCraft Launcher");
        // 图标可能加载不到，add(null) 会抛 NPE。
        javafx.scene.image.Image winIcon = Theme.loadImage("logo.png");
        if (winIcon != null) stage.getIcons().add(winIcon);
        stage.setMinWidth(810);
        stage.setMinHeight(470);
        stage.setAlwaysOnTop(false);
        restoreWindowBounds();
        installResize(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.K) {
                showGlobalSearch();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                if (searchLayer != null && searchLayer.isVisible()) closeSearch();
                else if (modalLayer != null && modalLayer.isVisible()) closeModal();
            }
        });
        stage.setOnCloseRequest(e -> {
            e.consume();
            closeWindow();
        });
        openPage("launch", false);
        startShellTimer();
        bindVideoWindowState();
        refreshVideoBackground(false); // 首次加载后刷新播放

        // 每次启动累加 SystemCount，每 99 次解锁一个隐藏主题。
        int launches = data.getInt("SystemCount", 0) + 1;
        data.set("SystemCount", launches);
        java.util.List<String> unlockedNow = Theme.applyLaunchUnlocks(launches);
        if (!unlockedNow.isEmpty()) {
            String names = String.join("、", unlockedNow);
            data.log("隐藏主题已解锁：" + names + "（累计启动 " + launches + " 次）");
            Platform.runLater(() -> toast("隐藏主题已解锁", names + " —— 可在 设置 → 个性化 中选用。", 1));
        }
        // 等主窗口就绪再挂错误弹窗。
        Errors.attach(this);
    }

    private void restoreWindowBounds() {
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        // 最小尺寸不能超过屏幕，小屏缩放时 810x470 会超。
        double minW = Math.min(810, Math.max(320, vb.getWidth() - 8));
        double minH = Math.min(470, Math.max(240, vb.getHeight() - 8));
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        double w = parseDouble(data.get("Window.Width", ""), 850);
        double h = parseDouble(data.get("Window.Height", ""), 500);
        // 保存的尺寸可能来自更大的显示器，夹到当前屏幕内。
        w = Math.min(Math.max(minW, w), Math.max(minW, vb.getWidth() - 8));
        h = Math.min(Math.max(minH, h), Math.max(minH, vb.getHeight() - 8));
        stage.setWidth(w);
        stage.setHeight(h);
        double x = Double.NaN, y = Double.NaN;
        if (data.getBool("UiRememberWindowPos", false)) {
            x = parseDouble(data.get("Window.X", ""), Double.NaN);
            y = parseDouble(data.get("Window.Y", ""), Double.NaN);
        }
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            stage.setX(clamp(x, vb.getMinX(), Math.max(vb.getMinX(), vb.getMaxX() - w)));
            stage.setY(clamp(y, vb.getMinY(), Math.max(vb.getMinY(), vb.getMaxY() - h)));
        } else {
            stage.setX(vb.getMinX() + Math.max(0, (vb.getWidth() - w) / 2));
            stage.setY(vb.getMinY() + Math.max(0, (vb.getHeight() - h) / 2));
        }
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }

    private void saveWindowBounds() {
        if (App.SNAPSHOT_MODE) return;   // 截图和自检运行不改用户配置
        if (stage.isIconified() || stage.isMaximized()) return;
        javafx.geometry.Rectangle2D vb = screenFor(stage.getX() + stage.getWidth() / 2, stage.getY() + 24);
        data.set("Window.Width", Math.min(stage.getWidth(), vb.getWidth() - 8));
        data.set("Window.Height", Math.min(stage.getHeight(), vb.getHeight() - 8));
        if (data.getBool("UiRememberWindowPos", false)) {
            data.set("Window.X", stage.getX());
            data.set("Window.Y", stage.getY());
        }
    }

    /** 用 Timeline 每 250ms 刷新，比 AnimationTimer 空转省 CPU。 */
    private void startShellTimer() {
        shellTimer = new Timeline(new KeyFrame(Duration.millis(250), e -> updateExtras()));
        shellTimer.setCycleCount(Timeline.INDEFINITE);
        shellTimer.play();
    }

    /** 刷新右下角按钮的可见性。 */
    private void updateExtras() {
        if (extraLayer == null) return;
        boolean taskVisible = Tasks.INSTANCE.hasActive() && !"tasks".equals(currentKey);
        if (btnExtraTask != null) {
            setExtraVisible(btnExtraTask, taskVisible);
        }
        if (taskVisible) {
            double p = Tasks.INSTANCE.overall();
            taskProgressArc.setLength(-360 * Math.max(0.02, Math.min(1, p)));
        }
        if (btnExtraMusic != null) setExtraVisible(btnExtraMusic, musicAvailable);
        if (btnExtraShutdown != null) setExtraVisible(btnExtraShutdown, gameProcess != null && gameProcess.isAlive());
        if (btnExtraTop != null) {
            boolean top = currentScroll != null && currentScroll.getVvalue() > 0.03;
            setExtraVisible(btnExtraTop, top);
        }
        if (btnExtraApril != null) {
            // 愚人节按钮只在当天、且停在启动页时显示。
            boolean april = PageLaunch.isAprilEnabled() && "launch".equals(currentKey);
            setExtraVisible(btnExtraApril, april);
        }
        extraShown = 0;
        for (Node n : extraRight.getChildren()) if (n.isVisible()) extraShown++;
        StackPane.setMargin(toastList, new Insets(0, 20, extraShown > 0 ? 72 : 20, 0));
    }

    /** 立即重算右下角按钮可见性。 */
    public void refreshExtraButtons() {
        updateExtras();
    }

    private void setExtraVisible(Node node, boolean visible) {
        if (node.isVisible() == visible) return;
        if (visible) {
            node.setVisible(true);
            node.setManaged(true);
            ScaleTransition st = new ScaleTransition(Duration.millis(220), node);
            st.setFromX(0);
            st.setFromY(0);
            st.setToX(1);
            st.setToY(1);
            st.setInterpolator(Ui.EASE_OUT_BACK);
            st.play();
        } else {
            ScaleTransition st = new ScaleTransition(Duration.millis(140), node);
            st.setToX(0);
            st.setToY(0);
            st.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
            });
            st.play();
        }
    }

    private StackPane extraButton(String icon, String tooltip, Runnable action) {
        // 40 圆形实心底，图标 16，阴影模糊 10。
        StackPane button = new StackPane(Theme.icon(icon, 16, Theme.accentPale3));
        button.setPrefSize(40, 40);
        button.setMinSize(40, 40);
        button.setMaxSize(40, 40);
        button.setBackground(Ui.fill(Theme.accent, 20));
        button.setEffect(Theme.shadow(10, 0, 0.2, Color.BLACK));
        button.setCursor(Cursor.HAND);
        button.setVisible(false);
        button.setManaged(false);
        button.setScaleX(0);
        button.setScaleY(0);
        Ui.tooltip(button, tooltip);
        // 悬停提亮一档。
        button.setOnMouseEntered(e -> Ui.animateBg(button, Theme.accent, Theme.accentLight, 100));
        button.setOnMouseExited(e -> Ui.animateBg(button, Theme.accentLight, Theme.accent, 200));
        // 按住缩到 0.85，松开回弹到 1。
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(Ui.aniMs(800)),
                    new javafx.animation.KeyValue(button.scaleXProperty(), 0.85, Ui.EASE_OUT_FLUENT_STRONG),
                    new javafx.animation.KeyValue(button.scaleYProperty(), 0.85, Ui.EASE_OUT_FLUENT_STRONG)));
            tl.play();
        });
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(Ui.aniMs(300)),
                    new javafx.animation.KeyValue(button.scaleXProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                    new javafx.animation.KeyValue(button.scaleYProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE)));
            tl.play();
        });
        if (action != null) button.setOnMouseClicked(e -> action.run());
        return button;
    }

    public void refreshWallpaper(boolean reset) {
        if (reset) Theme.resetWallpaper();
        else {
            Path pictures = Paths.get(data.gameDir, "TCL", "Pictures");
            if (!Theme.loadCustomWallpaper(pictures)) {
                Theme.resetWallpaper();
                toast("背景图片", "背景文件夹为空，已使用内置壁纸。", 2);
            }
        }
        if (data.getBool("UiAutoThemeFromBackground", true) && data.getInt("UiThemePreset", -1) < 0) {
            Theme.applyWallpaperTheme();
        }
        rebuildUi(); // 重建时重挂视频层
    }

    /** JavaFX 只认得 MPEG-4 系容器：mp4 / m4v / mov。 */
    private static final String[] VIDEO_EXTS = {"mp4", "m4v", "mov"};

    /** 当前播放列表和游标。 */
    private final List<Path> vidPlaylist = new ArrayList<>();
    private int vidIndex = 0;

    /** 背景视频文件夹，跟图片同级。 */
    public Path videoFolder() {
        return Paths.get(data.gameDir, "TCL", "Videos");
    }

    /** 图片和视频二选一：0 图片，1 视频。 */
    private boolean videoMode() {
        return data.getInt("UiBackgroundType", 0) == 1;
    }

    public List<Path> scanVideos() {
        return scanVideosIn(videoFolder());
    }

    /** 扫描目录下的视频，按文件名排序。 */
    public static List<Path> scanVideosIn(Path dir) {
        List<Path> files = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    for (String ext : VIDEO_EXTS) {
                        if (n.endsWith("." + ext)) {
                            files.add(p);
                            break;
                        }
                    }
                });
            }
        } catch (Exception e) {
            Data.get().log("扫描背景视频文件夹失败：" + e.getMessage());
        }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
        return files;
    }

    /** 重载播放列表并播放，非视频模式停播。 */
    void refreshVideoBackground(boolean showHint) {
        stopVideo();
        if (!videoMode()) {
            if (showHint) toast("背景视频", "当前背景类型为图片，视频已停用。", 3);
            return;
        }
        List<Path> files = scanVideos();
        if (files.isEmpty()) {
            if (showHint) toast("背景视频", "未检测到可用的背景视频！支持格式：mp4 / m4v / mov。", 2);
            return;
        }
        // UiVideoRandom 决定是否打乱顺序。
        if (data.getBool("UiVideoRandom", true)) {
            java.util.Collections.shuffle(files, java.util.concurrent.ThreadLocalRandom.current());
        }
        vidPlaylist.clear();
        vidPlaylist.addAll(files);
        vidIndex = 0;
        playVideo();
        if (showHint) toast("背景视频已刷新", vidPlaylist.get(0).getFileName().toString(), 1);
    }

    /** 同步静音和音量设置。 */
    public void updateVideoAudio() {
        if (vidPlayer == null) return;
        vidPlayer.setMute(data.getBool("UiVideoMute", true));
        vidPlayer.setVolume(data.getInt("UiVideoVolume", 500) / 1000.0);
    }

    /** 停止并释放视频层。 */
    void stopVideo() {
        if (vidPlayer != null) {
            try {
                vidPlayer.stop();
                vidPlayer.dispose(); // 释放解码线程和文件句柄
            } catch (Exception ignored) {
            }
            vidPlayer = null;
        }
        if (vidBack != null) {
            body.getChildren().remove(vidBack);
            vidBack = null;
        }
    }

    /** 播当前游标处的视频：单片循环，多片播完切下一片。 */
    private void playVideo() {
        if (vidPlaylist.isEmpty()) return;
        if (vidPlayer != null) stopVideo(); // 轮播切换时释放上一段。
        Path file = vidPlaylist.get(Math.floorMod(vidIndex, vidPlaylist.size()));
        String name = file.getFileName().toString();
        try {
            javafx.scene.media.Media media = new javafx.scene.media.Media(file.toUri().toString());
            javafx.scene.media.MediaPlayer player = new javafx.scene.media.MediaPlayer(media);
            player.setMute(data.getBool("UiVideoMute", true));
            player.setVolume(data.getInt("UiVideoVolume", 500) / 1000.0);
            if (vidPlaylist.size() == 1) {
                player.setCycleCount(javafx.scene.media.MediaPlayer.INDEFINITE);
            } else {
                player.setCycleCount(1);
                player.setOnEndOfMedia(() -> {
                    vidIndex = (vidIndex + 1) % vidPlaylist.size();
                    playVideo();
                });
            }
            player.setOnError(() -> {
                data.log("背景视频播放失败：" + name + (media.getError() != null ? " " + media.getError() : ""));
                Platform.runLater(() -> toast("背景视频播放失败", name, 2));
            });
            MediaView view = new MediaView(player);
            view.setPreserveRatio(true);
            view.fitWidthProperty().bind(body.widthProperty());
            view.fitHeightProperty().bind(body.heightProperty());
            view.setMouseTransparent(true);
            // UniformToFill 效果，JavaFX 得用 viewport 模拟。
            player.setOnReady(() -> applyVideoViewport(view));
            body.widthProperty().addListener((o, a, b) -> applyVideoViewport(view));
            body.heightProperty().addListener((o, a, b) -> applyVideoViewport(view));
            // 视频层跟随背景不透明度。
            view.setOpacity(data.getInt("UiBackgroundOpacity", 1000) / 1000.0);
            // 视频层夹在背景图和内容之间。
            int idx = body.getChildren().indexOf(bgRect);
            body.getChildren().add(Math.max(1, idx + 1), view);
            vidBack = view;
            vidPlayer = player;
            player.play();
        } catch (Exception e) {
            data.log("背景视频无法播放：" + name + " " + e.getMessage());
            toast("背景视频无法播放", name + "：" + e.getMessage(), 2);
        }
    }

    /** UniformToFill：按容器比例居中裁切。 */
    private void applyVideoViewport(MediaView view) {
        if (view == null || view.getMediaPlayer() == null) return;
        javafx.scene.media.Media media = view.getMediaPlayer().getMedia();
        if (media == null) return;
        double mw = media.getWidth(), mh = media.getHeight();
        double vw = body.getWidth(), vh = body.getHeight();
        if (mw <= 0 || mh <= 0 || vw <= 0 || vh <= 0) return;
        double scale = Math.max(vw / mw, vh / mh);
        double cw = vw / scale, ch = vh / scale;
        view.setViewport(new javafx.geometry.Rectangle2D((mw - cw) / 2, (mh - ch) / 2, cw, ch));
    }

    /** 窗口最小化或隐藏时暂停解码。 */
    private void bindVideoWindowState() {
        stage.iconifiedProperty().addListener((o, a, iconified) -> {
            if (vidPlayer == null) return;
            if (iconified) vidPlayer.pause();
            else vidPlayer.play();
        });
        stage.showingProperty().addListener((o, a, showing) -> {
            if (vidPlayer == null) return;
            if (showing) vidPlayer.play();
            else vidPlayer.pause();
        });
    }

    private boolean stageWasHidden;

    public void onGameStarted(Process process) {
        gameProcess = process;
        data.log("游戏进程已启动，PID " + process.pid());
        // 进程优先级：0 高 / 1 中 / 2 低 / 3 极高 / 4 实时
        int priority = data.getInt("LaunchPriority", 1);
        String priorityValue = switch (priority) {
            case 0 -> "32768";   // ABOVE_NORMAL_PRIORITY_CLASS
            case 2 -> "16384";   // BELOW_NORMAL_PRIORITY_CLASS
            case 3 -> "128";  // HIGH_PRIORITY_CLASS
            case 4 -> "256";  // REALTIME_PRIORITY_CLASS
            default -> null;  // 中（平衡）：保持系统默认
        };
        if (priorityValue != null) {
            try {
                new ProcessBuilder("wmic", "process", "where", "processid=" + process.pid(),
                        "call", "setpriority", priorityValue).redirectErrorStream(true).start();
            } catch (Exception ignored) {
            }
        }
        // 0 立即关闭 / 2 隐藏且退出关闭 / 3 隐藏且退出恢复 / 4 最小化 / 5 不变
        int visibility = data.getInt("LaunchVisibility", 5);
        boolean closeOnExit = visibility == 2;
        if (visibility == 4) {
            stage.setIconified(true);
        } else if (visibility == 2 || visibility == 3) {
            stageWasHidden = true;
            stage.hide();
        } else if (visibility == 0) {
            Platform.runLater(() -> {
                data.log("已根据设置，在启动后关闭启动器");
                Platform.exit();
            });
        }
        Thread watcher = new Thread(() -> {
            int code = -1;
            try {
                code = process.waitFor();
            } catch (Exception ignored) {
            }
            final int exit = code;
            Platform.runLater(() -> {
                gameProcess = null;
                if (stageWasHidden) {
                    if (closeOnExit) {
                        data.log("已根据设置，在游戏退出后关闭启动器");
                        Platform.exit();
                        return;
                    }
                    stage.show();
                    stageWasHidden = false;
                }
                if (stage.isIconified() && visibility == 4) stage.setIconified(false);
                data.log("游戏进程已退出，返回码 " + exit);
                toast(exit == 0 ? "游戏已退出" : "游戏异常退出", "返回码 " + exit, exit == 0 ? 0 : 2);
            });
        }, "TCL-GameWatcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void shutdownGame() {
        if (gameProcess == null) return;
        Dialogs.confirm(this, "关闭 Minecraft", "确定要强制关闭正在运行的 Minecraft 吗？\n未保存的游戏进度可能会丢失。", () -> {
            try {
                gameProcess.destroy();
                data.log("已请求关闭 Minecraft 进程");
                toast("已关闭", "运行中的 Minecraft！", 1);
            } catch (Exception ignored) {
            }
        }, true);
    }

    private void loadThemeFromSettings() {
        int preset = data.getInt("UiThemePreset", -1);
        if (preset >= 0) {
            Theme.applyPreset(preset);
        } else if (data.getBool("UiAutoThemeFromBackground", true)) {
            Theme.applyWallpaperTheme();
        } else {
            Theme.setAccent(Color.web("#1370f3"), false);
        }
    }

    private void buildFrame() {
        frame = new StackPane();
        // 主体铺满根节点。
        frame.setMaxWidth(Double.MAX_VALUE);
        frame.setMaxHeight(Double.MAX_VALUE);
        frame.setEffect(Theme.shadow(22, 6, 0.35, Color.web("#2a3240")));
        VBox frameBox = new VBox();
        // 标题栏和正文各自上色，整块刷纯色会把正文染成标题栏颜色。
        frameBox.setBackground(null);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(windowRadius() * 2);
        clip.setArcHeight(windowRadius() * 2);
        frameBox.widthProperty().addListener((o, a, b) -> clip.setWidth(b.doubleValue()));
        frameBox.heightProperty().addListener((o, a, b) -> clip.setHeight(b.doubleValue()));
        frameBox.setClip(clip);
        titleBar = buildTitleBar();
        body = buildBody();
        VBox.setVgrow(body, Priority.ALWAYS);
        frameBox.getChildren().addAll(titleBar, body);

        // 弹窗层盖住标题栏和正文
        modalLayer = new StackPane();
        modalLayer.setVisible(false);
        // 遮罩几乎透明，靠卡片投影分层。
        modalLayer.setBackground(Ui.fill(Color.web("#000000", 0.02), 0));
        modalHost = new StackPane();
        modalHost.setPickOnBounds(false);
        modalLayer.getChildren().add(modalHost);
        modalLayer.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getTarget() == modalLayer && modalDismissable) closeModal();
        });

        searchLayer = new StackPane();
        searchLayer.setVisible(false);
        searchLayer.setBackground(Ui.fill(Color.web("#000000", 0.20), 0));

        StackPane overlayLayer = new StackPane(modalLayer, searchLayer);
        overlayLayer.setPickOnBounds(false);
        Rectangle overlayClip = new Rectangle();
        overlayClip.setArcWidth(windowRadius() * 2);
        overlayClip.setArcHeight(windowRadius() * 2);
        overlayLayer.widthProperty().addListener((o, a, b) -> overlayClip.setWidth(b.doubleValue()));
        overlayLayer.heightProperty().addListener((o, a, b) -> overlayClip.setHeight(b.doubleValue()));
        overlayLayer.setClip(overlayClip);
        frame.getChildren().addAll(frameBox, overlayLayer);
        root.getChildren().setAll(frame);
        applyWindowOpacity();
    }

    private double windowRadius() {
        return Math.max(0, data.getInt("UiWindowRadius", 6));
    }

    public void applyWindowOpacity() {
        baseOpacity = Math.max(0.4, Math.min(1, data.getInt("UiLauncherTransparent", 600) / 1000.0 + 0.4));
        if (!closing) root.setOpacity(baseOpacity);
    }

    public void applyBackground() {
        updateBackgroundFill();
    }

    /** 用 ImagePattern 铺满 body，不依赖 ImageView 尺寸。 */
    private void updateBackgroundFill() {
        if (bgRect == null) return;
        Image img = Theme.wallpaper;
        double w = bgRect.getWidth(), h = bgRect.getHeight();
        int op = data.getInt("UiBackgroundOpacity", 1000);
        int blur = data.getInt("UiBackgroundBlur", 0);
        double alpha = Math.max(0, Math.min(1, op / 1000.0));
        boolean show = img != null && w > 0 && h > 0 && alpha > 0;
        bgRect.setVisible(show);
        bgRect.setOpacity(alpha);
        // 毛玻璃：设置 0~20 映射到 0~40px 高斯半径。
        double radius = blur * 2.0;
        bgRect.setEffect(radius > 0 ? new GaussianBlur(radius) : null);
        // 高斯模糊会让边缘发白，按半径放大背景层再裁掉溢出。
        double pad = radius * 1.5;
        if (radius > 0 && w > 2 * pad && h > 2 * pad) {
            bgRect.setScaleX((w + 2 * pad) / w);
            bgRect.setScaleY((h + 2 * pad) / h);
        } else {
            bgRect.setScaleX(1);
            bgRect.setScaleY(1);
        }
        bgRect.setFill(show ? backgroundPaint(img, w, h, data.getInt("UiBackgroundSuit", 0)) : null);
        if (cover != null) {
            // 有背景时侧栏 alpha 150，没背景时 241
            cover.setFill(show ? Color.web("#ffffff", 150 / 255.0) : Color.web("#ffffff", 241 / 255.0));
        }
        // 视频层共用背景不透明度。
        if (vidBack != null) vidBack.setOpacity(alpha);
        applyPanelBackground(); // 底色随主题变化
    }

    /** 背景适配模式：0 智能 / 1 居中 / 2 适应 / 3 拉伸 / 4 平铺 / 5~12 对齐。 */
    private Paint backgroundPaint(Image img, double w, double h, int suit) {
        double iw = img.getWidth(), ih = img.getHeight();
        if (iw <= 0 || ih <= 0) return Color.TRANSPARENT;
        if (suit == 0) suit = (iw < w / 2 && ih < h / 2) ? 4 : 2;
        if (suit == 1) {
            return new ImagePattern(img, (w - iw) / 2, (h - ih) / 2, iw, ih, false);
        }
        if (suit == 3) return new ImagePattern(img, 0, 0, w, h, false);
        if (suit == 4) return new ImagePattern(img, 0, 0, iw, ih, false);
        double sc = Math.max(w / iw, h / ih);
        double fw = iw * sc, fh = ih * sc;
        double x = switch (suit) {
            case 5, 7, 9 -> 0;
            case 6, 8, 10 -> w - fw;
            default -> (w - fw) / 2;
        };
        double y = switch (suit) {
            case 5, 6, 11 -> 0;
            case 7, 8, 12 -> h - fh;
            default -> (h - fh) / 2;
        };
        return new ImagePattern(img, x, y, fw, fh, false);
    }

    /** 标题栏三停渐变，中间亮两侧暗。 */
    private LinearGradient titleBarGradient() {
        double hue = Theme.accent.getHue(), sat = Theme.accent.getSaturation();
        double topL = 0.54, edgeL = 0.48; // 54 / 48 是 HSL 的 L 百分比
        // 相对亮度 > 0.2 就压暗，保证白字对比度。
        while (luminance(hsl(hue, sat, topL)) > 0.2 && topL > 0.26) {
            topL -= 0.02;
            edgeL -= 0.02;
        }
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, hsl(hue, sat, edgeL)),
                new Stop(0.5, hsl(hue, sat, topL)),
                new Stop(1, hsl(hue, sat, edgeL)));
    }

    /** HSL 转 Color，JavaFX 的 HSB 不同。 */
    static Color hsl(double h, double s, double l) {
        double hue = ((h % 360) + 360) % 360;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = hue / 60.0;
        double x = c * (1 - Math.abs(hp % 2 - 1));
        double r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        double m = l - c / 2;
        return Color.color(Math.min(1, r + m), Math.min(1, g + m), Math.min(1, b + m));
    }

    /** WCAG 相对亮度。 */
    private static double luminance(Color c) {
        double r = linear(c.getRed()), g = linear(c.getGreen()), b = linear(c.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** 正文底色：彩色主题走 91% 亮度三停渐变，其余 #f5f5f5。 */
    private void applyPanelBackground() {
        if (body == null) return;
        if (!data.getBool("UiBackgroundColorful", true)) {
            body.setBackground(new Background(new BackgroundFill(Color.web("#f5f5f5"), CornerRadii.EMPTY, Insets.EMPTY)));
            return;
        }
        double hue = Theme.accent.getHue(), sat = Theme.accent.getSaturation() * 0.8;
        body.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0.9, 0, 0.1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, hsl(hue - 15, sat, 0.91)), // L=91%，是 HSL 的亮度
                        new Stop(0.4, hsl(hue, sat, 0.91)),
                        new Stop(1, hsl(hue + 15, sat, 0.91))),
                CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private StackPane buildTitleBar() {
        StackPane bar = new StackPane();
        bar.setPrefHeight(48);
        bar.setMinHeight(48);
        bar.setMaxHeight(48);
        bar.setBackground(new Background(new BackgroundFill(titleBarGradient(), CornerRadii.EMPTY, Insets.EMPTY)));

        HBox left = new HBox(14);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setPadding(new Insets(0, 0, 0, 19));
        left.setPickOnBounds(false);
        Node logo = titleLogo();
        badgeAvatar = null;
        badgeName = null;
        badgeBox = buildAccountBadge();
        left.getChildren().addAll(logo, badgeBox);
        left.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == left) {
                dragX = e.getScreenX() - stage.getX();
                dragY = e.getScreenY() - stage.getY();
            }
        });

        navBox = new HBox(10); // 相邻项间隔 10px
        navBox.setAlignment(Pos.CENTER);
        navBox.setPickOnBounds(false);
        navTabs.clear();
        addNav("启动", "play", "launch");
        addNav("实例", "box", "instance");
        addNav("下载", "download", "download");
        addNav("联机", "globe", "link");
        addNav("更多", "grid-2x2", "other");

        HBox right = new HBox(4);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPadding(new Insets(0, 12, 0, 0));
        right.setPickOnBounds(false);
        // 语言按钮当弹层锚点，先建再挂。
        StackPane langBtn = Ui.iconButtonWhite("languages", 28, 17, "切换界面语言", null);
        langBtn.setOnMouseClicked(e -> {
            showLanguageMenu(langBtn);
            e.consume();
        });
        langButton = langBtn;
        right.getChildren().addAll(
                Ui.iconButtonWhite("search", 28, 16, "全局搜索（Ctrl+K）", this::showGlobalSearch),
                langBtn,
                Ui.iconButtonWhite("minus", 28, 16, "最小化", () -> stage.setIconified(true)),
                Ui.iconButtonWhite("x", 28, 16, "关闭", this::closeWindow)
        );
        right.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == right) {
                dragX = e.getScreenX() - stage.getX();
                dragY = e.getScreenY() - stage.getY();
            }
        });

        // 子页面标题层盖住 Logo 和导航，只切透明度。
        titleInner = new HBox(7);
        titleInner.setAlignment(Pos.CENTER_LEFT);
        titleInner.setPickOnBounds(false);
        titleInner.setMouseTransparent(true);
        titleInner.setOpacity(0);
        StackPane backBtn = Ui.iconButtonWhite("arrow-left", 28, 17, "返回", this::pageBack);
        titleInnerLabel = Ui.label("", 15, Theme.accentPale3);
        titleInner.getChildren().addAll(backBtn, titleInnerLabel);

        leftGroup = left;
        StackPane.setAlignment(left, Pos.CENTER_LEFT);
        StackPane.setAlignment(navBox, Pos.CENTER);
        StackPane.setAlignment(titleInner, Pos.CENTER_LEFT);
        StackPane.setMargin(titleInner, new Insets(0, 0, 0, 12));
        StackPane.setAlignment(right, Pos.CENTER_RIGHT);
        bar.getChildren().addAll(left, navBox, titleInner, right);
        // 窗口变窄时缩导航字号和留白，下限 9。
        bar.widthProperty().addListener((o, a, b) -> adaptTitleTabs());

        bar.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            dragX = e.getScreenX() - stage.getX();
            dragY = e.getScreenY() - stage.getY();
        });
        bar.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && !closing) {
                moveWindowTo(e.getScreenX() - dragX, e.getScreenY() - dragY);
            }
        });
        return bar; // titleBar 已在上层赋值，别重复赋

    }

    /** 拖动时保证标题栏还留在屏幕里。 */
    private void moveWindowTo(double x, double y) {
        javafx.geometry.Rectangle2D vb = screenFor(x + stage.getWidth() / 2, y + 24);
        double w = stage.getWidth();
        stage.setX(clamp(x, vb.getMinX() - w + 120, vb.getMaxX() - 120));
        stage.setY(clamp(y, vb.getMinY(), Math.max(vb.getMinY(), vb.getMaxY() - 48)));
    }

    private static javafx.geometry.Rectangle2D screenFor(double sx, double sy) {
        for (javafx.stage.Screen s : javafx.stage.Screen.getScreens()) {
            if (s.getVisualBounds().contains(sx, sy)) return s.getVisualBounds();
        }
        return javafx.stage.Screen.getPrimary().getVisualBounds();
    }

    /** 随宽度重算导航字号和留白。 */
    private void adaptTitleTabs() {
        if (navBox == null || titleBar == null) return;
        try {
            // 先复位字号 13，量按钮组自然宽度。
            for (NavTab t : navTabs) t.setCompact(13, false);
            double natural = navBox.prefWidth(-1);
            // 可用宽 = 标题栏宽 - 左侧占用 - 152 - 48。
            double leftUsed = 78 + (badgeBox != null && badgeBox.isVisible() ? badgeBox.getWidth() : 0);
            double available = titleBar.getWidth() - leftUsed - 152 - 2 * 24;
            // 超宽按比例缩字号，下限 9。
            double fs = natural > 0 && natural > available ? Math.max(9, 13 * available / natural) : 13;
            boolean tight = fs < 13 - 0.4;
            for (NavTab t : navTabs) t.setCompact(fs, tight);
            navBox.setSpacing(tight ? Math.max(2, 10 * fs / 13) : 10);
        } catch (Exception ignored) {
        }
    }

    /** Logo 类型：0 无 / 1 默认文字 TCL / 2 自定义文字 / 3 图片 TCL/Logo.png。 */
    private Node titleLogo() {
        int type = data.getInt("UiLogoType", 1);
        if (type == 0) {
            titleLogoNode = null;
            Pane empty = new Pane();
            empty.setPrefSize(0, 21);
            empty.setMinSize(0, 21);
            empty.setMouseTransparent(true);
            return empty;
        }
        if (type == 3) {
            try {
                Path p = Paths.get(data.gameDir, "TCL", "Logo.png");
                if (Files.exists(p)) {
                    ImageView iv = new ImageView(new Image(p.toUri().toString(), false));
                    iv.setFitHeight(22);
                    iv.setPreserveRatio(true);
                    iv.setMouseTransparent(true);
                    titleLogoNode = iv;
                    return iv;
                }
            } catch (Exception ignored) {
            }
        }
        String text = type == 2 ? data.get("UiLogoText", "TCL") : "TCL";
        Label lab = new Label(text == null || text.isEmpty() ? "TCL" : text);
        lab.setFont(Theme.font(17));
        lab.setTextFill(Color.WHITE);
        lab.setMouseTransparent(true);
        lab.setAlignment(Pos.CENTER_LEFT);
        titleLogoNode = lab;
        return lab;
    }

    private HBox buildAccountBadge() {
        HBox badge = new HBox(7);
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.setPadding(new Insets(3, 10, 3, 5));
        badge.setMinHeight(26);
        badge.setMaxHeight(26);
        badge.setBackground(Ui.fill(Color.web("#ffffff", 0.18), 13));
        badge.setCursor(Cursor.HAND);
        badgeAvatar = Ui.avatar(data.account(), 18);
        badgeName = Ui.label(data.account() == null ? "未登录" : data.account().name, 12.5, Color.WHITE);
        badgeName.setMaxWidth(110);
        badge.getChildren().addAll(badgeAvatar, badgeName);
        badge.setOnMouseClicked(e -> showAccountDialog());
        badge.setOnMousePressed(e -> e.consume());
        Ui.tooltip(badge, "点击进入账户管理页面，可添加或切换账户");
        badge.setVisible(data.getBool("UiTitleAccountBadge", true));
        badge.setManaged(badge.isVisible());
        return badge;
    }

    public void refreshAccountBadge() {
        javafx.application.Platform.runLater(() -> adaptTitleTabs());
        if (badgeName == null || badgeAvatar == null) return;
        Data.Account a = data.account();
        badgeName.setText(a == null ? "未登录" : a.name);
        StackPane newAvatar = Ui.avatar(a, 18);
        HBox parent = (HBox) badgeName.getParent();
        parent.getChildren().set(0, newAvatar);
        badgeAvatar = newAvatar;
    }

    private void addNav(String text, String icon, String key) {
        NavTab tab = new NavTab(text, icon);
        tab.setUserData(key);
        tab.setOnMouseClicked(e -> {
            openPage(key, true);
            e.consume();
        });
        tab.setOnMousePressed(e -> e.consume());
        navTabs.add(tab);
        navBox.getChildren().add(tab);
    }

    private class NavTab extends HBox {
        /** 近乎透明的药丸底，alpha 1/255。 */
        private static final Color TRANSPARENT_PILL = Color.web("#eaf2fe", 1 / 255.0);
        private final Node iconNode;
        private final Label label;
        private boolean checked;
        private boolean pressed;
        private Color currentBg = TRANSPARENT_PILL;
        private Color currentFg = Color.WHITE;

        /** 悬停底：accentPale3 加 50/255。 */
        private static final Color HOVER_PILL = Color.web("#eaf2fe", 50 / 255.0);
        /** 按下底：accentPale3 加 120/255。 */
        private static final Color PRESS_PILL = Color.web("#eaf2fe", 120 / 255.0);

        NavTab(String text, String icon) {
            super(8);
            setAlignment(Pos.CENTER);
            setPadding(new Insets(0, 2, 0, 2));
            setMinHeight(27);
            setMaxHeight(27);
            setCursor(Cursor.HAND);
            setBackground(Ui.fill(TRANSPARENT_PILL, 13.5));
            iconNode = Theme.icon(icon, 16, Color.WHITE);
            label = Ui.label(text, 13, Color.WHITE);
            HBox.setMargin(iconNode, new Insets(0, 0, 0, 10));
            HBox.setMargin(label, new Insets(0, 10, 0, 0));
            getChildren().addAll(iconNode, label);
            addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                if (!checked) animateTo(HOVER_PILL, Color.WHITE, 90);
            });
            addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                pressed = false;
                if (!checked) animateTo(TRANSPARENT_PILL, Color.WHITE, 150);
            });
            // 未选中按下加深，松开回悬停态。
            addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if (!checked) {
                    pressed = true;
                    animateTo(PRESS_PILL, Color.WHITE, 60);
                }
            });
            addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                if (pressed && !checked) animateTo(HOVER_PILL, Color.WHITE, 90);
                pressed = false;
            });
        }

        private void animateTo(Color bg, Color fg) {
            animateTo(bg, fg, 120);
        }

        private void animateTo(Color bg, Color fg, int ms) {
            Color fromBg = currentBg;
            Color fromFg = currentFg;
            currentBg = bg;
            currentFg = fg;
            javafx.animation.Transition t = new javafx.animation.Transition() {
                {
                    setCycleDuration(Duration.millis(ms));
                }

                @Override
                protected void interpolate(double f) {
                    setBackground(Ui.fill(fromBg.interpolate(bg, f), 13.5));
                    Color c = fromFg.interpolate(fg, f);
                    label.setTextFill(c);
                    Ui.tint(iconNode, c);
                }
            };
            t.play();
        }

        /** 收紧字号和留白。 */
        void setCompact(double fontSize, boolean tight) {
            label.setFont(Theme.font(fontSize));
            if (!tight) {
                HBox.setMargin(iconNode, new Insets(0, 0, 0, 10));
                HBox.setMargin(label, new Insets(0, 10, 0, 0));
                return;
            }
            double f = Math.max(0.6, fontSize / 13.0);
            double gap = Math.max(4, 10 * f);
            HBox.setMargin(iconNode, new Insets(0, 0, 0, gap));
            HBox.setMargin(label, new Insets(0, gap, 0, 0));
        }

        void setChecked(boolean value) {
            checked = value;
            // 选中态：白底 + accent 文字，不用 accentLight
            Color bg = value ? Color.WHITE : TRANSPARENT_PILL;
            Color fg = value ? Theme.accent : Color.WHITE;
            animateTo(bg, fg);
        }
    }

    private StackPane buildBody() {
        StackPane b = new StackPane();
        b.setAlignment(Pos.TOP_LEFT);
        bgRect = new Rectangle();
        bgRect.setManaged(false);
        bgRect.setMouseTransparent(true);
        bgRect.setX(0);
        bgRect.setY(0);
        bgRect.widthProperty().bind(b.widthProperty());
        bgRect.heightProperty().bind(b.heightProperty());
        b.getChildren().add(bgRect);
        b.widthProperty().addListener((o, a, c) -> updateBackgroundFill());
        b.heightProperty().addListener((o, a, c) -> updateBackgroundFill());

        coverLayer = new Pane();
        coverLayer.setMouseTransparent(true);
        cover = new Rectangle();
        cover.setFill(Theme.sidebarBg);
        cover.setWidth(0);
        cover.heightProperty().bind(b.heightProperty());
        coverShadow = new Region();
        coverShadow.setPrefWidth(5);
        coverShadow.setMaxWidth(5);
        coverShadow.prefHeightProperty().bind(b.heightProperty());
        coverShadow.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#000000", 0.05)), new Stop(1, Color.TRANSPARENT)),
                CornerRadii.EMPTY, Insets.EMPTY)));
        coverShadow.setOpacity(0.9);
        coverShadow.layoutXProperty().bind(cover.widthProperty());
        coverLayer.getChildren().addAll(cover, coverShadow);
        b.getChildren().add(coverLayer);

        leftHost = new StackPane();
        leftHost.setAlignment(Pos.TOP_LEFT);
        rightHost = new StackPane();
        rightHost.setAlignment(Pos.TOP_LEFT);
        HBox content = new HBox();
        content.setFillHeight(true);
        content.getChildren().addAll(leftHost, rightHost);
        HBox.setHgrow(rightHost, Priority.ALWAYS);
        pageHost = new StackPane(content);
        pageHost.setAlignment(Pos.TOP_LEFT);
        pageHost.setPickOnBounds(false);
        // 页面容器 min 归零，避免切页时窗口被撑大。
        content.setMinSize(0, 0);
        pageHost.setMinSize(0, 0);
        rightHost.setMinSize(0, 0);
        // root 也不能有 min，会被内容撑开溢出窗口。
        root.setMinSize(0, 0);
        b.getChildren().add(pageHost);
        // pageHost 别绑 body 宽度，会循环依赖把窗口越推越大。

        toastLayer = new StackPane();
        toastLayer.setAlignment(Pos.BOTTOM_RIGHT);
        toastLayer.setPickOnBounds(false);
        toastList = new VBox(8);
        toastList.setAlignment(Pos.BOTTOM_RIGHT);
        toastList.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        toastList.setPickOnBounds(false);
        StackPane.setAlignment(toastList, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastList, new Insets(0, 20, 20, 0));
        toastLayer.getChildren().add(toastList);
        b.getChildren().add(toastLayer);

        // 右下角悬浮按钮：任务管理 / 音乐 / 返回顶部
        extraLayer = new StackPane();
        extraLayer.setAlignment(Pos.BOTTOM_RIGHT);
        extraLayer.setPickOnBounds(false);
        extraRight = new VBox(8);
        extraRight.setAlignment(Pos.BOTTOM_RIGHT);
        // VBox 不限尺寸会被拉满整页，挡住所有点击。
        extraRight.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        extraRight.setPickOnBounds(false);
        StackPane.setAlignment(extraRight, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(extraRight, new Insets(0, 15, 14, 0));
        btnExtraTask = extraButton("layout-list", "任务管理", () -> openPage("tasks", true));
        taskProgressArc = new Arc(0, 0, 18, 18, 90, 0);
        taskProgressArc.setType(ArcType.OPEN);
        taskProgressArc.setFill(Color.TRANSPARENT);
        taskProgressArc.setStroke(Color.web("#ffffff", 0.9));
        taskProgressArc.setStrokeWidth(2);
        taskProgressArc.setMouseTransparent(true);
        btnExtraTask.getChildren().add(taskProgressArc);
        btnExtraMusic = extraButton("music", "背景音乐", Music::toggle);
        Ui.tooltip(btnExtraMusic, "背景音乐（右键下一首）");
        btnExtraMusic.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) Music.next();
            else Music.toggle();
            toast("背景音乐", (Music.isPaused() ? "已暂停 · " : "正在播放 · ") + Music.currentName(), 0);
        });
        btnExtraTop = extraButton("arrow-up", "返回顶部", () -> {
            if (currentScroll != null) {
                Timeline t = new Timeline(new KeyFrame(Duration.millis(260),
                        new KeyValue(currentScroll.vvalueProperty(), 0, Interpolator.EASE_OUT)));
                t.play();
            }
        });
        btnExtraApril = extraButton("flag", "投降", () -> {
            // 愚人节放弃：只提示并记年份，不解锁主题。
            toast("=D", "", 1);
            data.set("AprilYear", java.time.LocalDate.now().getYear());
            updateExtras();
        });
        extraRight.getChildren().addAll(btnExtraTop, btnExtraTask, btnExtraApril, btnExtraMusic);
        extraLayer.getChildren().add(extraRight);
        b.getChildren().add(extraLayer);

        // 左下角：关闭 Minecraft
        btnExtraShutdown = extraButton("power", "关闭 Minecraft", this::shutdownGame);
        btnExtraShutdown.setBackground(Ui.fill(Color.web("#d24a4a"), 20));
        btnExtraShutdown.setOnMouseEntered(e -> btnExtraShutdown.setBackground(Ui.fill(Color.web("#e85c5c"), 20)));
        btnExtraShutdown.setOnMouseExited(e -> btnExtraShutdown.setBackground(Ui.fill(Color.web("#d24a4a"), 20)));
        StackPane shutdownHost = new StackPane(btnExtraShutdown);
        shutdownHost.setAlignment(Pos.BOTTOM_LEFT);
        shutdownHost.setPickOnBounds(false);
        StackPane.setAlignment(shutdownHost, Pos.BOTTOM_LEFT);
        StackPane.setMargin(shutdownHost, new Insets(0, 0, 14, 15));
        b.getChildren().add(shutdownHost);

        launchLayer = new StackPane();
        launchLayer.setVisible(false);
        launchLayer.setPickOnBounds(false);
        b.getChildren().add(launchLayer);

        applyPanelBackground();
        applyBackground();
        return b;
    }

    private Page createPage(String key) {
        return switch (key) {
            case "launch" -> new PageLaunch(this);
            case "instance" -> new PageInstance(this);
            case "download" -> new PageDownload(this);
            case "link" -> new PageLink(this);
            case "other" -> new PageOther(this); // 分区状态在 data.otherSection，别写死 about
            case "setup" -> new PageSetup(this, pendingSetupSection);
            case "versions" -> new PageVersionSelect(this);
            case "instanceSetup" -> new PageInstanceSetup(this);
            case "resourceDetail" -> new PageResourceDetail(this);
            case "tasks" -> new PageTasks(this);
            case "account" -> new PageAccountManager(this);
            default -> new PageLaunch(this);
        };
    }

    /** 原地重建当前页，带动画。 */
    public void reloadPage() {
        if (pageHost == null) return;
        String key = currentKey;
        currentKey = "__reload__";
        openPage(key, true);
    }

    /** 原地重建当前页，不播入场动画。 */
    private void rebuildQuiet() {
        if (pageHost == null || currentPage == null) return;
        String key = currentKey;
        Page page;
        try {
            page = createPage(key);
        } catch (Exception e) {
            Errors.error("重建「" + key + "」页面失败", e);
            return;
        }
        switchToken++;
        commitPage(page, key);
        enterPage(currentPage, false);
        applyNav(key);
    }

    /** 当前页面对象，回调方自行判断是否属于它。 */
    public Page currentPageNode() {
        return currentPage;
    }

    public void refreshRight() {
        if (pageHost == null) return;
        if (currentPage == null || curLeft == null || curRight == null) { // 没有左栏的页面走原地重建
            rebuildQuiet();
            return;
        }
        Page page;
        Node right;
        try {
            page = createPage(currentKey);
            right = page.right();
        } catch (Exception e) {
            // 构造失败走错误反馈
            Errors.error("构造「" + currentKey + "」右栏失败", e);
            return;
        }
        switchToken++; // 停掉在飞的切换动画
        currentPage.onHide();
        currentPage = page;
        rightHost.getChildren().setAll(right);
        curRight = right;
        rightHost.setOpacity(1);
        rightHost.setTranslateY(0);
        rightHost.setMouseTransparent(false);
        // 左栏只复位残留的退场态。
        leftHost.setOpacity(1);
        leftHost.setMouseTransparent(false);
        if (curLeft != null) {
            curLeft.setOpacity(1);
            curLeft.setTranslateY(0);
            curLeft.setScaleX(1);
            curLeft.setScaleY(1);
        }
        syncLeftSelection(page.sectionKey());
        bindBackToTop(right);
        // 刷新右栏不播级联入场，入场只给切页。
        right.setOpacity(1);
        right.setTranslateY(0);
        for (Node c : childrenOf(right)) {
            c.setMouseTransparent(false);
            c.setOpacity(1);
            c.setTranslateY(0);
        }
    }

    /** 就地同步左栏选中态，保住悬停态。 */
    private void syncLeftSelection(String key) {
        if (key == null || curLeft == null) return;
        syncLeftSelection(curLeft, key);
    }

    private static void syncLeftSelection(Node node, String key) {
        if (node instanceof Ui.ListItem item) {
            if (item.getUserData() instanceof String s) item.setSelected(s.equals(key));
            return;
        }
        if (node instanceof ScrollPane sp) {
            if (sp.getContent() != null) syncLeftSelection(sp.getContent(), key);
            return;
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) syncLeftSelection(child, key);
        }
    }

    /** 截图验证用：切到下载页的安装选择态。 */
    void debugSelectVersion(String versionId) {
        if (currentPage instanceof PageDownload pd) pd.debugSelectVersion(versionId);
    }

    public void openPage(String key, boolean animate) {
        if (Objects.equals(currentKey, key) && currentPage != null && animate) return;
        Page page;
        try {
            page = createPage(key);
        } catch (Exception e) {
            Errors.error("构造「" + key + "」页面失败", e);
            return;
        }
        String oldKey = currentKey;
        // 同页刷新不播入场动画
        boolean samePage = currentPage != null && Objects.equals(oldKey, key);
        boolean firstShow = currentPage == null;   // 首屏直接落终态，不播入场
        currentKey = key;
        applyNav(key);
        final int token = ++switchToken;
        boolean anim = animate && animationsOn() && pageHost != null && currentPage != null;
        if (!anim) {
            commitPage(page, key);
            // 同页刷新不播入场，左栏不会重新缩放。
            enterPage(currentPage, !samePage && !suppressPageEnter && !firstShow);
        } else {
            // 110ms 退场，换页后 30ms 入场
            exitPage(currentPage);
            PauseTransition delay = new PauseTransition(Duration.millis(110));
            delay.setOnFinished(e -> {
                if (token != switchToken) return;
                commitPage(page, key);
                PauseTransition next = new PauseTransition(Duration.millis(30));
                next.setOnFinished(ev -> {
                    if (token != switchToken) return;
                    enterPage(currentPage, !suppressPageEnter);
                });
                next.play();
            });
            delay.play();
        }
        if (!oldKey.equals(key)) data.log("切换页面：" + key);
    }

    /** 真正换页；失败也要恢复两栏可交互。 */
    private void commitPage(Page page, String key) {
        try {
            if (currentPage != null) currentPage.onHide();
            applyPage(page);
            currentPage = page;
        } catch (Exception e) {
            // 构建失败弹错误窗，别只弹 toast。
            data.log("页面构建失败：" + key + " → " + e);
            Errors.error("打开页面「" + key + "」失败", e);
        } finally {
            leftHost.setOpacity(1);
            rightHost.setOpacity(1);
            leftHost.setTranslateY(0);
            rightHost.setTranslateY(0);
            leftHost.setMouseTransparent(false);
            rightHost.setMouseTransparent(false);
            pageHost.setOpacity(1);
        }
    }

    /** 诊断用：body 宽度。 */
    private String bodyWidth() {
        try {
            javafx.scene.Node f = root.getChildrenUnmodifiable().get(0);
            if (f instanceof javafx.scene.Parent p && !p.getChildrenUnmodifiable().isEmpty()) {
                javafx.scene.Node bd = p.getChildrenUnmodifiable().get(0);
                if (bd instanceof javafx.scene.layout.Region r) {
                    return Math.round(r.getWidth()) + "/min" + Math.round(r.minWidth(-1));
                }
            }
        } catch (Exception ignored) {
        }
        return "-";
    }

    String debugRightInfo() {
        if (rightHost == null) return "rightHost=null";
        StringBuilder sb = new StringBuilder();
        sb.append("root=").append(Math.round(root.getWidth())).append("x").append(Math.round(root.getHeight()))
                .append(" rootMin=").append(Math.round(root.minWidth(-1))).append("x").append(Math.round(root.minHeight(-1)))
                .append(" pageHost=").append(Math.round(((javafx.scene.layout.Region) pageHost).getWidth()))
                .append(" contentMinW=").append(Math.round(((javafx.scene.layout.Region) ((javafx.scene.layout.StackPane) pageHost).getChildrenUnmodifiable().get(0)).minWidth(-1)))
                .append(" | frame=").append(root.getChildrenUnmodifiable().isEmpty() ? "-"
                        : Math.round(((javafx.scene.layout.Region) root.getChildrenUnmodifiable().get(0)).getWidth()))
                .append(" frameMin=").append(root.getChildrenUnmodifiable().isEmpty() ? "-"
                        : Math.round(((javafx.scene.layout.Region) root.getChildrenUnmodifiable().get(0)).minWidth(-1)))
                .append(" body=").append(bodyWidth())
                .append(" | rightChildren=").append(rightHost.getChildren().size())
                .append(" hostSize=").append(Math.round(rightHost.getWidth())).append("x")
                .append(Math.round(rightHost.getHeight()))
                .append(" hostOp=").append(String.format("%.2f", rightHost.getOpacity()));
        if (!rightHost.getChildren().isEmpty()) {
            javafx.scene.Node c = rightHost.getChildren().get(0);
            sb.append(" child=").append(c.getClass().getSimpleName())
                    .append(" op=").append(String.format("%.2f", c.getOpacity()))
                    .append(" vis=").append(c.isVisible())
                    .append(" managed=").append(c.isManaged())
                    .append(" wh=").append(Math.round(((javafx.scene.layout.Region) c).getWidth())).append("x")
                    .append(Math.round(((javafx.scene.layout.Region) c).getHeight()))
                    .append(" pref=").append(Math.round(((javafx.scene.layout.Region) c).prefWidth(-1))).append("x")
                    .append(Math.round(((javafx.scene.layout.Region) c).prefHeight(-1)))
                    .append(" bounds=").append(Math.round(c.getBoundsInParent().getWidth())).append("x")
                    .append(Math.round(c.getBoundsInParent().getHeight()));
            if (c instanceof javafx.scene.Parent p && !p.getChildrenUnmodifiable().isEmpty()) {
                sb.append(" grandOp=").append(String.format("%.2f", p.getChildrenUnmodifiable().get(0).getOpacity()));
            }
        }
        return sb.toString();
    }

    private void applyPage(Page page) {
        Node left = page.left();
        Node right = page.right();
        double w = page.leftWidth();
        leftHost.getChildren().clear();
        rightHost.getChildren().clear();
        if (left != null) leftHost.getChildren().add(left);
        if (right != null) rightHost.getChildren().add(right);
        Lang.localize(left);
        Lang.localize(right);
        curLeft = left;
        curRight = right;
        rightHost.setMinWidth(0);   // 别让页面内容撑大宿主，切页窗口会跳。
        rightHost.setMinHeight(0);
        // 页面根的 min 也要压掉，ScrollPane 会一路顶到 Scene
        if (right instanceof javafx.scene.layout.Region rr) {
            rr.setMinWidth(0);
            rr.setMinHeight(0);
        }
        if (left instanceof javafx.scene.layout.Region lr) {
            lr.setMinWidth(0);
        }
        leftHost.setMinWidth(w);
        leftHost.setPrefWidth(w);
        leftHost.setMaxWidth(w);
        leftHost.setVisible(w > 0);
        leftHost.setManaged(w > 0);
        animateCover(w);
        pageHost.setOpacity(1);
        // 透明度先归零，入场交给 enterPage
        if (left != null) {
            left.setOpacity(0);
            left.setTranslateY(0);
            left.setScaleX(0.96);
            left.setScaleY(0.96);
        }
        if (right != null) {
            right.setOpacity(1);
            right.setTranslateY(0);
            for (Node n : childrenOf(right)) {
                n.setOpacity(0);
                n.setTranslateY(-14);
            }
        }
        bindBackToTop(right);
    }

    /** 退场：左页缩到 0.95 淡出；右页逐控件上移淡出。 */
    private void exitPage(Page page) {
        if (page == null) return;
        leftHost.setMouseTransparent(true);
        rightHost.setMouseTransparent(true);
        // 必须用挂上去的节点，重调 page.left() 会造出看不见的新树。
        Node left = curLeft;
        Node right = curRight;
        if (left != null) {
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(left.opacityProperty(), left.getOpacity())),
                    new KeyFrame(Duration.millis(110),
                            new KeyValue(left.scaleXProperty(), 0.95, Ui.fluentIn(2)),
                            new KeyValue(left.scaleYProperty(), 0.95, Ui.fluentIn(2))),
                    new KeyFrame(Duration.millis(30 + 80), new KeyValue(left.opacityProperty(), 0)));
            tl.play();
        }
        if (right != null) {
            List<Node> controls = childrenOf(right);
            int i = 0;
            for (Node n : controls) {
                final Node c = n;
                int delay = Math.min(200, i * 12);
                c.setMouseTransparent(true);
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.millis(delay),
                                new KeyValue(c.opacityProperty(), c.getOpacity()),
                                new KeyValue(c.translateYProperty(), c.getTranslateY())),
                        new KeyFrame(Duration.millis(delay + 110),
                                new KeyValue(c.opacityProperty(), 0, Ui.fluentIn(3)),
                                new KeyValue(c.translateYProperty(), -18, Ui.fluentIn(3))));
                tl.play();
                i++;
            }
        }
    }

    /** 入场：左页放大淡入，右页交给 enterRight；animate=false 直接落终态。 */
    private void enterPage(Page page, boolean animate) {
        if (page == null) return;
        Node left = curLeft;
        Node right = curRight;
        if (left != null) {
            if (animate && animationsOn()) {
                left.setOpacity(0);
                left.setScaleX(0.96);
                left.setScaleY(0.96);
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.millis(100), new KeyValue(left.opacityProperty(), 1)),
                        new KeyFrame(Duration.millis(400),
                                new KeyValue(left.scaleXProperty(), 1, Ui.EASE_OUT_BACK),
                                new KeyValue(left.scaleYProperty(), 1, Ui.EASE_OUT_BACK)));
                tl.play();
            } else {
                left.setOpacity(1);
                left.setTranslateY(0);
                left.setScaleX(1);
                left.setScaleY(1);
            }
        }
        if (animate) {
            enterRight(right);
        } else {
            right.setOpacity(1);
            right.setTranslateY(0);
            for (Node c : childrenOf(right)) {
                c.setMouseTransparent(false);
                c.setOpacity(1);
                c.setTranslateY(0);
            }
        }
        leftHost.setMouseTransparent(false);
        rightHost.setMouseTransparent(false);
    }

    /** 右栏入场：逐控件上移淡入，级联 26ms。 */
    private void enterRight(Node right) {
        if (right == null) return;
        right.setOpacity(1);
        right.setTranslateY(0);
        List<Node> controls = childrenOf(right);
        int i = 0;
        for (Node n : controls) {
            final Node c = n;
            if (animationsOn()) {
                int delay = i * 26;
                c.setMouseTransparent(false);
                c.setOpacity(0);
                c.setTranslateY(-14);
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.millis(delay),
                                new KeyValue(c.opacityProperty(), 0),
                                new KeyValue(c.translateYProperty(), -14)),
                        new KeyFrame(Duration.millis(delay + 150),
                                new KeyValue(c.opacityProperty(), 1, Ui.fluentOut(2))),
                        new KeyFrame(Duration.millis(delay + 320),
                                new KeyValue(c.translateYProperty(), 0, Ui.EASE_OUT_FLUENT)));
                tl.play();
            } else {
                c.setMouseTransparent(false);
                c.setOpacity(1);
                c.setTranslateY(0);
            }
            i++;
        }
    }

    private void bindBackToTop(Node right) {
        if (scrollListener != null && currentScroll != null) {
            currentScroll.vvalueProperty().removeListener(scrollListener);
            scrollListener = null;
        }
        currentScroll = findScroll(right);
        if (currentScroll != null) {
            scrollListener = (o, a, b) -> updateExtras();
            currentScroll.vvalueProperty().addListener(scrollListener);
        }
    }

    private ScrollPane findScroll(Node node) {
        if (node instanceof ScrollPane sp) return sp;
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                ScrollPane found = findScroll(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private List<Node> childrenOf(Node node) {
        List<Node> out = new ArrayList<>();
        Node target = node;
        if (target instanceof ScrollPane sp) target = sp.getContent();
        if (target instanceof Pane p) {
            if (p.getChildren().size() == 1 && p.getChildren().get(0) instanceof Pane inner) {
                out.addAll(inner.getChildren());
            } else {
                out.addAll(p.getChildren());
            }
        } else if (target != null) {
            out.add(target);
        }
        return out.size() > 24 ? out.subList(0, 24) : out;
    }

    private void animateCover(double target) {
        if (cover == null) return;
        double from = cover.getWidth();
        if (!animationsOn()) {
            cover.setWidth(target);
            return;
        }
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(cover.widthProperty(), from)),
                new KeyFrame(Duration.millis(180), new KeyValue(cover.widthProperty(), target,
                        Interpolator.EASE_OUT)));
        t.play();
    }

    private boolean animationsOn() {
        return data.getInt("UiAnimSpeed", 100) > 0 && !data.getBool("UiSkipIntroAnim", false);
    }

    private void applyNav(String key) {
        // 次级页面：导航淡出，标题层淡入。
        String sub = subPageTitle(key);
        if (sub != null) {
            switchTitleInner(sub);
            return;
        }
        hideTitleInner();
        String nav = switch (key) {
            case "instance", "instanceSetup" -> "instance";
            case "resourceDetail" -> "download";
            case "setup", "versions" -> "launch";
            case "tasks" -> "";
            default -> key;
        };
        for (NavTab tab : navTabs) {
            tab.setChecked(Objects.equals(tab.getUserData(), nav));
        }
    }

    /** 次级页面标题，null 表示顶级页面。 */
    private String subPageTitle(String key) {
        switch (key) {
            case "account":
                return "账户管理";
            case "tasks":
                return "任务管理";
            case "instanceSetup": {
                Data.Instance ins = data.instance();
                return "版本设置 - " + (ins == null ? "未知版本" : ins.name);
            }
            case "resourceDetail": {
                // 标题显示资源名，不写笼统的「资源详情」。
                ResourceInfo ri = pendingResource;
                if (ri == null || ri.name == null || ri.name.isEmpty()) return "资源详情";
                return ri.slug == null || ri.slug.isEmpty() || ri.slug.equals(ri.name)
                        ? ri.name : ri.name + "(" + ri.slug + ")";
            }
            default:
                return null;
        }
    }

    /** 切次级页标题：两层透明度互换，不改布局。 */
    private void switchTitleInner(String text) {
        if (titleInner == null) return;
        titleInnerLabel.setText(text);
        navBox.setMouseTransparent(true);
        titleInner.setMouseTransparent(false);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(130), new KeyValue(navBox.opacityProperty(), 0),
                        new KeyValue(leftGroup.opacityProperty(), 0)),   // Logo 和账号徽章一起隐去
                new KeyFrame(Duration.millis(150), new KeyValue(titleInner.opacityProperty(), 1)));
        t.play();
    }

    /** 回到顶级页面：标题层淡出、导航恢复。 */
    private void hideTitleInner() {
        if (titleInner == null || titleInner.getOpacity() == 0) return;
        navBox.setMouseTransparent(false);
        titleInner.setMouseTransparent(true);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(150), new KeyValue(titleInner.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(130), new KeyValue(navBox.opacityProperty(), 1),
                        new KeyValue(leftGroup.opacityProperty(), 1)));
        t.play();
    }

    /** 返回键，子页面一律回启动页。 */
    private void pageBack() {
        openPage("launch", true);
    }

    public void playIntro() {
        boolean skip = data.getBool("UiSkipIntroAnim", false) || isSoftwareRendering();
        if (skip) {
            // 关了动画或软件渲染时直接落终态
            root.setOpacity(baseOpacity);
            frame.setRotate(0);
            frame.setTranslateY(0);
            if (titleLogoNode != null) titleLogoNode.setOpacity(1);
            return;
        }
        frame.setRotate(-4);
        frame.setTranslateY(60);
        frame.setOpacity(1);
        root.setOpacity(0.4);
        Timeline move = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(frame.translateYProperty(), 60),
                        new KeyValue(frame.rotateProperty(), -4)),
                new KeyFrame(Duration.millis(460), new KeyValue(frame.rotateProperty(), 0,
                        Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(480), new KeyValue(frame.translateYProperty(), 0,
                        Interpolator.EASE_OUT)));
        move.setDelay(Duration.millis(100));
        move.play();
        if (titleLogoNode != null) {
            titleLogoNode.setOpacity(0);
            FadeTransition logoFade = new FadeTransition(Duration.millis(420), titleLogoNode);
            logoFade.setFromValue(0);
            logoFade.setToValue(1);
            logoFade.setDelay(Duration.millis(220));
            logoFade.play();
        }
        FadeTransition fade = new FadeTransition(Duration.millis(260), root);
        fade.setFromValue(0.4);
        fade.setToValue(baseOpacity);
        fade.setDelay(Duration.millis(100));
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    /** 反射查 GraphicsPipeline，判断是否软件渲染。 */
    private static boolean isSoftwareRendering() {
        try {
            Class<?> cls = Class.forName("com.sun.prism.GraphicsPipeline");
            Object pipeline = cls.getMethod("getPipeline").invoke(null);
            if (pipeline != null) {
                String name = pipeline.getClass().getName().toLowerCase(java.util.Locale.ROOT);
                if (name.contains(".sw.") || name.endsWith("swpipeline")) return true;
            }
        } catch (Throwable ignored) {
        }
        return "sw".equalsIgnoreCase(System.getProperty("prism.order", ""));
    }

    public void toast(String title, String message) {
        toast(title, message, 0);
    }

    /** Toast 运行状态，拖拽暂停和重排要用。 */
    private static final class ActiveToast {
        final VBox stack;
        final HBox card;
        final Region progress;
        final StackPane close;
        final String title;
        final String message;
        final int totalMs;
        long startMs;
        double pausedRemaining = -1; // -1 = 运行中
        javafx.animation.Timeline drain;
        javafx.animation.PauseTransition hide;
        javafx.animation.Timeline entry; // 入场动画，拖拽前先停
        boolean dismissed = false;

        ActiveToast(VBox stack, HBox card, Region progress, StackPane close,
                    String title, String message, int totalMs) {
            this.stack = stack;
            this.card = card;
            this.progress = progress;
            this.close = close;
            this.title = title;
            this.message = message == null ? "" : message;
            this.totalMs = totalMs;
            this.startMs = System.currentTimeMillis();
        }

        /** 剩余显示时长，暂停时取暂停值。 */
        double remainingMs() {
            if (pausedRemaining >= 0) return pausedRemaining;
            return Math.max(0, totalMs - (System.currentTimeMillis() - startMs));
        }

        void pauseProgress() {
            if (pausedRemaining >= 0) return;
            pausedRemaining = remainingMs();
            if (entry != null) entry.stop();
            if (drain != null) drain.stop();
            if (hide != null) hide.stop();
        }

        /** 从暂停处恢复排空和隐藏。 */
        void resumeProgress() {
            if (pausedRemaining < 0 || dismissed) return;
            double remaining = Math.max(1, pausedRemaining);
            double currentWidth = progress.getPrefWidth();
            pausedRemaining = -1;
            startMs = System.currentTimeMillis();
            if (currentWidth > 0) {
                drain = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        javafx.util.Duration.millis(remaining),
                        new javafx.animation.KeyValue(progress.prefWidthProperty(), 0)));
                drain.play();
            }
            hide = new javafx.animation.PauseTransition(javafx.util.Duration.millis(remaining));
            hide.setOnFinished(e -> dismiss());
            hide.play();
        }

        void dismiss() {
            if (dismissed) return;
            dismissed = true;
            if (drain != null) drain.stop();
            if (hide != null) hide.stop();
        }
    }

    /** 拖拽常量：死区、关闭阈值、透明度下限、最短剩余、回弹时长。 */
    private static final double TOAST_DRAG_DEADZONE = 8;
    private static final double TOAST_DISMISS_THRESHOLD_MIN = 80;
    private static final double TOAST_DISMISS_THRESHOLD_RATIO = 0.35;
    private static final double TOAST_DRAG_OPACITY_FLOOR = 0.5;
    private static final double TOAST_MIN_REMAINING_MS = 300;
    private static final int TOAST_RETURN_MS = 150;

    public void toast(String title, String message, int type) {
        // toast 可能被后台线程调用，统一切回 FX 线程。
        try {
            if (!javafx.application.Platform.isFxApplicationThread()) {
                javafx.application.Platform.runLater(() -> toast(title, message, type));
                return;
            }
        } catch (Throwable t) {
            // 图形环境未就绪，错误已由 Errors 落盘
            return;
        }
        // 按标题加内容去重，重复时晃一下并重新计时
        for (javafx.scene.Node n : toastList.getChildren()) {
            ActiveToast at = (ActiveToast) n.getProperties().get("toast");
            if (at != null && !at.dismissed && at.title.equals(title) && at.message.equals(message == null ? "" : message)) {
                emphasizeToast(at);
                return;
            }
        }
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 12, 13, 12));
        card.setMaxWidth(360);
        // 纯色底，半透明白叠壁纸上文字会糊
        card.setBackground(Ui.fill(Theme.dialogBg, 8));
        card.setEffect(Theme.shadow(18, 3, 0.2, Theme.text));
        Color c;
        String icon;
        switch (type) {
            // 1 绿 / 2 橙警告 / 3 红错误 / 默认主题蓝
            case 1 -> {
                c = Color.web("#4de68c");
                icon = "circle-check";
            }
            case 2 -> {
                c = Theme.orange;
                icon = "triangle-alert";
            }
            case 3 -> {
                c = Color.web("#e64d59");
                icon = "circle-minus";
            }
            default -> {
                c = Theme.accentLight;
                icon = "info";
            }
        }
        StackPane iconBox = new StackPane(Theme.icon(icon, 17, c));
        iconBox.setMinSize(28, 28);
        iconBox.setMaxSize(28, 28);
        iconBox.setBackground(Ui.fill(Theme.alpha(c, 0.14), 8));
        VBox texts = new VBox(2);
        Label t = Ui.bold(title, 12.5, Theme.gray1);
        Label m = Ui.label(message == null ? "" : message, 11.5, Theme.gray2);
        m.setWrapText(true);
        m.setMaxWidth(265);
        texts.getChildren().addAll(t, m);
        HBox.setHgrow(texts, Priority.ALWAYS);
        StackPane close = Ui.iconButton("x", 20, 12, null, null);
        card.getChildren().addAll(iconBox, texts, close);
        // 底部 3px 倒计时进度条
        Region progress = new Region();
        progress.setMaxHeight(3);
        progress.setBackground(Ui.fill(c, 0));
        VBox stack = new VBox(card, progress);
        stack.setMaxWidth(360);
        stack.setOpacity(0);
        stack.setTranslateX(60);
        // 显示时长 = (800 + 字数(5~23)×180) × 动画速度
        int duration = Ui.aniMs(800 + Math.max(5, Math.min(23, (message == null ? 0 : message.length() + title.length())) * 180));
        ActiveToast at = new ActiveToast(stack, card, progress, close, title, message, duration);
        stack.getProperties().put("toast", at);
        close.getProperties().put("toastClose", Boolean.TRUE);
        close.setOnMouseClicked(e -> {
            at.dismiss();
            dismissToast(stack);
        });
        // toastList 还没挂回 Scene 时延迟重试
        Runnable[] addRef = new Runnable[1];
        addRef[0] = () -> {
            if (toastList.getScene() == null) {
                javafx.animation.PauseTransition retry = new PauseTransition(Duration.millis(120));
                retry.setOnFinished(e -> addRef[0].run());
                retry.play();
                return;
            }
            toastList.getChildren().add(stack);
            while (toastList.getChildren().size() > 4) {
                javafx.scene.Node oldest = toastList.getChildren().remove(0);
                ActiveToast old = (ActiveToast) oldest.getProperties().get("toast");
                if (old != null) old.dismiss();
            }
            // 入场：X 从 60 到 0 并淡入
            javafx.animation.Timeline in = new javafx.animation.Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(stack.opacityProperty(), 0),
                            new KeyValue(stack.translateXProperty(), 60)),
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(stack.opacityProperty(), 1),
                            new KeyValue(stack.translateXProperty(), 0, Ui.EASE_OUT_FLUENT)));
            in.play();
            // 进度条先量宽，再从满宽收到 0
            stack.widthProperty().addListener((o, a, b) -> {
                if (at.pausedRemaining < 0 && at.drain == null) {
                    progress.setPrefWidth(b.doubleValue());
                    progress.setMaxWidth(b.doubleValue());
                }
            });
            PauseTransition measure = new PauseTransition(Duration.millis(50));
            measure.setOnFinished(ev -> {
                if (at.dismissed || at.pausedRemaining >= 0) return;
                double w = stack.getWidth() > 0 ? stack.getWidth() : 300;
                progress.setPrefWidth(w);
                progress.setMaxWidth(w);
                at.drain = new javafx.animation.Timeline(
                        new KeyFrame(Duration.millis(Math.max(1, duration)),
                                new KeyValue(progress.prefWidthProperty(), 0)));
                at.drain.play();
            });
            measure.play();
            at.hide = new PauseTransition(Duration.millis(Math.max(1, duration)));
            at.hide.setOnFinished(e -> dismissToast(stack));
            at.hide.play();
        };
        addRef[0].run();
        installToastDrag(card, stack, at, close);
    }

    /** 重复提示：晃动并重新计时。 */
    private void emphasizeToast(ActiveToast at) {
        at.stack.toFront();
        at.pauseProgress();
        at.stack.setOpacity(1);
        at.stack.setTranslateX(0);
        javafx.animation.Timeline wobble = new javafx.animation.Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(at.stack.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(70), new KeyValue(at.stack.translateXProperty(), -8, Ui.EASE_OUT_FLUENT)),
                new KeyFrame(Duration.millis(140), new KeyValue(at.stack.translateXProperty(), 16, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(210), new KeyValue(at.stack.translateXProperty(), -8, Ui.EASE_OUT_FLUENT)),
                new KeyFrame(Duration.millis(230), new KeyValue(at.stack.translateXProperty(), 0)));
        wobble.setOnFinished(e -> at.resumeProgress());
        wobble.play();
    }

    /** 向右拖动关闭，超过 max(80, 宽×0.35) 即关，未达阈值回弹。 */
    private void installToastDrag(HBox card, VBox stack, ActiveToast at, StackPane close) {
        final double[] dragStartX = {0};
        final double[] dragStartTranslate = {0};
        final boolean[] dragPending = {false};
        final boolean[] dragging = {false};
        card.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            // 关闭按钮上的按下不算拖拽
            javafx.scene.Node hit = e.getPickResult().getIntersectedNode();
            while (hit != null) {
                if (hit == close) return;
                hit = hit.getParent();
            }
            if (at.dismissed) return;
            dragPending[0] = true;
            dragging[0] = false;
            dragStartX[0] = e.getSceneX();
            dragStartTranslate[0] = stack.getTranslateX();
            e.consume();
        });
        card.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
            if (at.dismissed) return;
            double dx = e.getSceneX() - dragStartX[0];
            if (!dragging[0]) {
                if (!dragPending[0] || dx < TOAST_DRAG_DEADZONE) return;
                // 开始拖拽，停动画并暂停倒计时
                dragging[0] = true;
                dragPending[0] = false;
                at.pauseProgress();
            }
            double newX = dragStartTranslate[0] + Math.max(0, dx);
            stack.setTranslateX(newX);
            double width = Math.max(1, stack.getWidth());
            stack.setOpacity(Math.max(TOAST_DRAG_OPACITY_FLOOR,
                    1 - (newX / width) * (1 - TOAST_DRAG_OPACITY_FLOOR)));
            e.consume();
        });
        card.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            if (!dragging[0]) {
                dragPending[0] = false;
                return;
            }
            dragging[0] = false;
            dragPending[0] = false;
            e.consume();
            double currentX = stack.getTranslateX();
            double threshold = Math.max(TOAST_DISMISS_THRESHOLD_MIN,
                    Math.max(1, stack.getWidth()) * TOAST_DISMISS_THRESHOLD_RATIO);
            if (currentX >= threshold) {
                at.dismiss();
                dismissToast(stack);
                return;
            }
            // 剩余不足直接关，够长就回弹并恢复倒计时。
            double remaining = at.remainingMs();
            if (remaining < TOAST_MIN_REMAINING_MS) {
                at.dismiss();
                dismissToast(stack);
                return;
            }
            double currentOpacity = stack.getOpacity();
            javafx.animation.Timeline back = new javafx.animation.Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(stack.translateXProperty(), currentX),
                            new KeyValue(stack.opacityProperty(), currentOpacity)),
                    new KeyFrame(Duration.millis(TOAST_RETURN_MS),
                            new KeyValue(stack.translateXProperty(), 0, Ui.EASE_OUT_FLUENT),
                            new KeyValue(stack.opacityProperty(), 1)));
            back.setOnFinished(ev -> at.resumeProgress());
            back.play();
        });
    }

    private void dismissToast(Node card) {
        if (!toastList.getChildren().contains(card)) return;
        ActiveToast at = (ActiveToast) card.getProperties().get("toast");
        if (at != null) at.dismiss();
        // 退场：X 到 +60，淡出后移除
        javafx.animation.Timeline out = new javafx.animation.Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(card.opacityProperty(), card.getOpacity()),
                        new KeyValue(card.translateXProperty(), card.getTranslateX())),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.translateXProperty(), 60, Ui.fluentIn(3))));
        out.setOnFinished(e -> toastList.getChildren().remove(card));
        out.play();
    }

    public void showModal(Node card) {
        showModal(card, true);
    }

    /** 截图自检用：弹窗实际尺寸，无弹窗返回 "-"。 */
    public String debugModalSize() {
        if (modalContent == null) return "-";
        return Math.round(modalContent.getBoundsInLocal().getWidth()) + "x"
                + Math.round(modalContent.getBoundsInLocal().getHeight());
    }

    public void showModal(Node card, boolean dismissable) {
        showModal(card, dismissable, null);
    }

    /** 带警告遮罩的弹窗。 */
    public void showModal(Node card, boolean dismissable, Color mask) {
        // 连锁弹窗先停旧退场动画，避免清掉新卡片。
        if (maskFade != null) maskFade.stop();
        if (modalExit != null) modalExit.stop();
        modalLayer.setBackground(Ui.fill(mask == null ? Theme.modalMask : mask, 0));
        modalContent = card;
        modalDismissable = dismissable;
        solidifyModal(card); // 兜底：弹窗一律不透明
        if (card instanceof javafx.scene.layout.Region region) {
            double prefW = region.getPrefWidth();
            if (prefW > 0 && !Double.isNaN(prefW)) region.setMaxWidth(prefW);
            // StackPane 会把 Region 拉满，弹窗会变成全高白条
            region.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            // 首次测量拿不到换行宽度，布局完再重算高度
            Platform.runLater(() -> {
                if (region.getWidth() > 0) {
                    double h = region.prefHeight(region.getWidth());
                    if (h > 0 && !Double.isNaN(h)) region.setPrefHeight(h);
                }
            });
        }
        modalHost.getChildren().setAll(card);
        Lang.localize(card);
        StackPane.setAlignment(card, Pos.CENTER);
        modalLayer.setVisible(true);
        // 遮罩淡入，卡片淡入并归位
        modalLayer.setOpacity(0);
        maskFade = new FadeTransition(Duration.millis(200), modalLayer);
        maskFade.setToValue(1);
        maskFade.play();
        card.setOpacity(0);
        card.setScaleX(0.92);
        card.setScaleY(0.92);
        card.setTranslateY(18);
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.scaleXProperty(), 0.92), new KeyValue(card.scaleYProperty(), 0.92),
                        new KeyValue(card.translateYProperty(), 18)),
                new KeyFrame(Duration.millis(140 + 40), new KeyValue(card.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(300 + 50),
                        new KeyValue(card.translateYProperty(), 0, Ui.EASE_OUT_FLUENT)),
                new KeyFrame(Duration.millis(340 + 50),
                        new KeyValue(card.scaleXProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                        new KeyValue(card.scaleYProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE)));
        tl.play();
    }

    /** 弹窗底色补成不透明，圆角和内边距保留。 */
    static void solidifyModal(Node card) {
        if (!(card instanceof javafx.scene.layout.Region region)) return;
        Background bg = region.getBackground();
        if (bg == null || bg.getFills().isEmpty()) {
            region.setBackground(Ui.fill(Theme.dialogBg, 10));
            return;
        }
        List<BackgroundFill> fills = new ArrayList<>();
        boolean changed = false;
        for (BackgroundFill f : bg.getFills()) {
            if (f.getFill() instanceof Color c && c.getOpacity() < 0.999) {
                fills.add(new BackgroundFill(Color.color(c.getRed(), c.getGreen(), c.getBlue()),
                        f.getRadii(), f.getInsets()));
                changed = true;
            } else {
                fills.add(f);
            }
        }
        if (changed) region.setBackground(new Background(fills.toArray(new BackgroundFill[0])));
    }

    /** 弹窗关闭回调，触发一次后清空。 */
    public void setModalCloseHook(Runnable hook) {
        modalCloseHook = hook;
    }

    public void closeModal() {
        if (modalLayer == null || !modalLayer.isVisible()) return;
        // 外部关闭也要推进错误弹窗队列
        Errors.onModalDismissed();
        Runnable hook = modalCloseHook;
        modalCloseHook = null;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable ignored) {
            }
        }
        final Node card = modalContent;
        modalContent = null;
        // 卡片淡出下移，遮罩 200ms 淡出
        if (card != null) {
            Timeline out = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(card.opacityProperty(), card.getOpacity()),
                            new KeyValue(card.translateYProperty(), card.getTranslateY()),
                            new KeyValue(card.scaleXProperty(), card.getScaleX()),
                            new KeyValue(card.scaleYProperty(), card.getScaleY())),
                    new KeyFrame(Duration.millis(10 + 90), new KeyValue(card.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(130),
                            new KeyValue(card.translateYProperty(), 12, Ui.EASE_OUT_FLUENT)),
                    new KeyFrame(Duration.millis(130),
                            new KeyValue(card.scaleXProperty(), 1.04, Ui.fluentIn(2)),
                            new KeyValue(card.scaleYProperty(), 1.04, Ui.fluentIn(2))));
            out.setOnFinished(e -> {
                // 已挂上新弹窗就别清场，会把新弹窗清掉。
                if (modalContent != null) return;
                modalHost.getChildren().clear();
                modalLayer.setVisible(false);
            });
            modalExit = out;
            out.play();
        } else {
            if (modalContent == null) {
                modalHost.getChildren().clear();
                modalLayer.setVisible(false);
            }
        }
        if (modalContent == null) {
            maskFade = new FadeTransition(Duration.millis(200), modalLayer);
            maskFade.setToValue(0);
            maskFade.play();
        }
    }

    private void showGlobalSearch() {
        if (searchLayer == null) return;
        searchLayer.getChildren().clear();
        VBox card = new VBox(8);
        card.setMaxWidth(430);
        card.setPadding(new Insets(14, 14, 12, 14));
        card.setBackground(Ui.fill(Theme.dialogBg, 10));
        card.setEffect(Theme.shadow(18, 3, 0.3, Theme.text));
        javafx.scene.control.TextField field = Ui.field("搜索页面、实例与设置（Ctrl+K）");
        field.setPrefHeight(34);
        VBox results = new VBox(2);
        ScrollPane scroll = Ui.scroll(results);
        // 视口高度跟结果条数走（行高 38 + 间距 2），结果不满一屏时卡片不留空挡
        scroll.setMaxHeight(330);
        Label empty = Ui.label("没有匹配的结果", 12, Theme.gray3);
        empty.setOpacity(0.7);
        empty.setVisible(false);
        card.getChildren().addAll(field, scroll, empty);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(70, 0, 0, 0));

        List<Object[]> entries = new ArrayList<>();
        entries.add(new Object[]{"启动", "主页", "play", (Runnable) () -> openPage("launch", true)});
        entries.add(new Object[]{"版本选择", "启动 → 版本选择", "layers", (Runnable) () -> openPage("versions", true)});
        entries.add(new Object[]{"实例", "实例管理", "box", (Runnable) () -> openPage("instance", true)});
        entries.add(new Object[]{"下载", "原版游戏与社区资源", "download", (Runnable) () -> openPage("download", true)});
        entries.add(new Object[]{"联机", "EasyTier 虚拟局域网", "globe", (Runnable) () -> openPage("link", true)});
        entries.add(new Object[]{"更多", "关于、帮助、新闻与测试", "layers", (Runnable) () -> openPage("other", true)});
        String[][] setup = {{"启动", "launch"}, {"联机", "link"}, {"个性化", "ui"}, {"系统", "system"},
                {"Java", "java"}, {"日志", "log"}, {"存储与清理", "cache"}, {"网络", "network"}, {"更多", "more"}};
        for (String[] s : setup) {
            entries.add(new Object[]{"设置 - " + s[0], "设置", "settings", (Runnable) () -> {
                pendingSetupSection = s[1];
                openPage("setup", true);
            }});
        }
        entries.add(new Object[]{"账户管理", "添加或切换账户", "user", (Runnable) this::showAccountDialog});
        entries.add(new Object[]{"任务管理", "下载与安装任务", "layout-list", (Runnable) () -> openPage("tasks", true)});
        for (Data.Instance ins : data.instances) {
            final Data.Instance target = ins;
            entries.add(new Object[]{ins.name, "实例 · " + ins.version, "box", (Runnable) () -> {
                data.selectedInstance = data.instances.indexOf(target);
                data.save();
                openPage("instanceSetup", true);
            }});
        }
        String[][] settingNames = {
                {"启动选项", "launch"}, {"默认版本隔离", "launch"}, {"游戏窗口标题", "launch"}, {"内存分配", "launch"},
                {"联机设置", "link"}, {"主题", "ui"}, {"背景", "ui"}, {"动画速度", "ui"}, {"窗口圆角", "ui"},
                {"文件下载源", "system"}, {"HTTP 代理", "system"}, {"语言", "system"},
                {"Java 运行时", "java"}, {"启动日志", "log"}, {"存储与清理", "cache"},
                {"下载源测速", "network"}, {"社区", "more"}
        };
        for (String[] s : settingNames) {
            String label = s[0], section = s[1];
            if (entries.stream().anyMatch(o -> label.equals(o[0]))) continue;
            entries.add(new Object[]{"设置 - " + label, "设置", "settings", (Runnable) () -> {
                pendingSetupSection = section;
                openPage("setup", true);
            }});
        }

        final List<Runnable> visibleActions = new ArrayList<>();
        Consumer<String> filter = q -> {
            results.getChildren().clear();
            visibleActions.clear();
            String query = q == null ? "" : q.trim().toLowerCase();
            int count = 0;
            for (Object[] entry : entries) {
                String title = (String) entry[0];
                String sub = (String) entry[1];
                if (!query.isEmpty() && !title.toLowerCase().contains(query) && !sub.toLowerCase().contains(query))
                    continue;
                Node icon = Theme.icon((String) entry[2], 16, Theme.text);
                Ui.ListItem item = new Ui.ListItem(icon, title, sub, 38);
                Runnable action = (Runnable) entry[3];
                item.setOnMouseClicked(e -> {
                    closeSearch();
                    action.run();
                });
                results.getChildren().add(item);
                visibleActions.add(action);
                count++;
                if (count >= 30) break;
            }
            empty.setVisible(count == 0);
            scroll.setPrefViewportHeight(Math.max(38, Math.min(300, count * 40.0 - 2)));
        };
        filter.accept("");
        field.textProperty().addListener((o, a, b) -> filter.accept(b));
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) closeSearch();
            if (e.getCode() == KeyCode.ENTER && !visibleActions.isEmpty()) {
                Runnable action = visibleActions.get(0);
                closeSearch();
                action.run();
            }
        });

        searchLayer.getChildren().add(card);
        searchLayer.setVisible(true);
        searchLayer.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(120), searchLayer);
        ft.setToValue(1);
        ft.play();
        card.setOpacity(0);
        card.setScaleX(0.96);
        card.setScaleY(0.96);
        card.setTranslateY(8);
        Timeline in = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), 0),
                        new KeyValue(card.scaleXProperty(), 0.96), new KeyValue(card.scaleYProperty(), 0.96),
                        new KeyValue(card.translateYProperty(), 8)),
                new KeyFrame(Duration.millis(180), new KeyValue(card.opacityProperty(), 1),
                        new KeyValue(card.scaleXProperty(), 1), new KeyValue(card.scaleYProperty(), 1),
                        new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT)));
        in.play();
        Platform.runLater(field::requestFocus);
        searchLayer.setOnMouseClicked(e -> {
            if (e.getTarget() == searchLayer) closeSearch();
        });
        searchField = new TextFieldBridge(field);
    }

    private void closeSearch() {
        if (searchLayer == null || !searchLayer.isVisible()) return;
        FadeTransition ft = new FadeTransition(Duration.millis(120), searchLayer);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            searchLayer.setVisible(false);
            searchLayer.getChildren().clear();
        });
        ft.play();
    }

    private static class TextFieldBridge {
        TextFieldBridge(javafx.scene.control.TextField field) {
        }
    }

    public void showAccountDialog() {
        Dialogs.accountManager(this);
    }

    /** 语言选择弹层，自绘 Popup。 */
    private void showLanguageMenu(Node anchor) {
        if (langPopup != null && langPopup.isShowing()) {
            Ui.closeMenuCard(langPopup);
            langPopup = null;
            return;
        }
        String[] names = {"简体中文", "English", "繁體中文"};
        Ui.MenuRow[] rows = new Ui.MenuRow[names.length];
        for (int i = 0; i < names.length; i++) {
            final int mode = i;
            rows[i] = Ui.MenuRow.of((Lang.mode == i ? "✓  " : "") + names[i], "languages", () -> {
                if (Lang.mode == mode) return;
                Lang.setMode(mode);
                data.set("SystemLanguage", mode);
                rebuildUi();
                toast("界面语言", "已切换为 " + Lang.langName(), 1);
            });
        }
        // 外壳和定位交给 Ui.menuCard，按 prefWidth 算右对齐会差 34px。
        langPopup = Ui.menuCard(anchor, rows);
    }

    /** 关闭语言弹层：85ms 淡出后再 hide。 */
    private void closeLanguageMenu() {
        if (langPopup == null) return;
        Popup popup = langPopup;
        langPopup = null;
        Node card = popup.getContent().isEmpty() ? null : popup.getContent().get(0);
        if (card == null) {
            popup.hide();
            return;
        }
        Timeline out = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(card.opacityProperty(), card.getOpacity())),
                new KeyFrame(Duration.millis(85), new KeyValue(card.opacityProperty(), 0)));
        out.setOnFinished(e -> popup.hide());
        out.play();
    }

    public void rebuildUi() {
        String keep = currentKey;
        if (currentPage != null) currentPage.onHide();
        stopVideo(); // 重建会销毁 body，先释放视频层
        root.getChildren().clear();
        loadThemeFromSettings();
        buildFrame();
        currentPage = null;
        switchToken++;
        // 原地重建不播入场动画
        boolean prev = suppressPageEnter;
        suppressPageEnter = true;
        try {
            openPage(keep, false);
        } finally {
            suppressPageEnter = prev;
        }
        refreshVideoBackground(false);
    }

    /** 换主题：静默重建整页，再从点击点扩散一圈波纹。 */
    public void applyThemeFrom(double sceneX, double sceneY) {
        // 先静默重建整页
        javafx.geometry.Point2D p = root.sceneToLocal(sceneX, sceneY);
        rebuildUi();

        // 再从点击点扩散一圈主题色波纹
        double w = root.getWidth(), h = root.getHeight();
        if (!animationsOn() || w <= 1 || h <= 1) return;

        double maxR = Math.hypot(Math.max(p.getX(), w - p.getX()), Math.max(p.getY(), h - p.getY())) + 8;
        javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(p.getX(), p.getY(), 0);
        // 填充和描边一起淡出，纯描边在这儿看不清
        ring.setFill(Theme.accent);
        ring.setStroke(Theme.accentDark);
        ring.setStrokeWidth(2.5);
        ring.setOpacity(0.22);
        ring.setMouseTransparent(true);
        ring.setManaged(false);
        root.getChildren().add(ring);

        javafx.animation.Transition wave = new javafx.animation.Transition() {
            {
                setCycleDuration(Duration.millis(420));
                setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            }

            @Override
            protected void interpolate(double f) {
                ring.setRadius(maxR * f);
                ring.setOpacity(0.22 * (1 - f) * (1 - f));
                ring.setStrokeWidth(2.5 * (1 - 0.6 * f));
            }
        };
        wave.setOnFinished(e -> root.getChildren().remove(ring));
        wave.play();
    }

    private Timeline launchTimeline;
    private volatile boolean launchCancelled;
    private Timeline launchProgressAnim;
    private Rectangle launchProgressFill;
    private Label launchPercent;
    private Label launchStep;
    private GridPane launchInfoBox;
    private VBox launchHintBox;
    private BorderPane launchPanel;
    private VBox launchInner;
    private Rectangle launchMask;
    private Ui.Loading launchLoading;
    private boolean launching;

    /** 直连服务器地址，null 表示不直连。 */
    private String launchServerIp;

    public void launchGame() {
        launchGame(null);
    }

    /** 有 serverIp 就启动后直连。 */
    public void launchGame(String serverIp) {
        this.launchServerIp = serverIp;
        if (launching) return;
        launching = true;
        Data.Instance ins = data.instance();
        String version = ins == null ? "未选择版本" : ins.version;
        launchLayer.getChildren().clear();
        launchLayer.setVisible(true);
        launchLayer.setPickOnBounds(true);

        launchMask = new Rectangle();
        launchMask.widthProperty().bind(body.widthProperty());
        launchMask.heightProperty().bind(body.heightProperty());
        launchMask.setFill(Color.web("#000000", 0.25)); // 遮罩 #40000000
        launchMask.setOpacity(0);
        FadeTransition maskIn = new FadeTransition(Duration.millis(200), launchMask);
        maskIn.setToValue(1);
        maskIn.play();

        launchPanel = new BorderPane();
        launchPanel.setBackground(Ui.fill(Color.web("#FBFBFB"), 0));
        launchPanel.setBorder(new Border(new BorderStroke(Theme.gray3, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                new BorderWidths(0, 1, 0, 0))));
        launchPanel.setPrefWidth(364);
        launchPanel.setMaxWidth(0);
        launchPanel.setMinWidth(0);
        launchPanel.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(launchPanel, Pos.CENTER_LEFT);
        // 启动卡片阴影：模糊 26、向右 6、透明度 0.45
        launchPanel.setEffect(new DropShadow(26, 6, 0,
                new Color(Theme.text.getRed(), Theme.text.getGreen(), Theme.text.getBlue(), 0.45)));
        Rectangle launchClip = new Rectangle();
        launchClip.widthProperty().bind(launchPanel.widthProperty());
        launchClip.heightProperty().bind(launchPanel.heightProperty());
        launchPanel.setClip(launchClip);

        launchInner = new VBox(0);
        launchInner.setPadding(new Insets(26, 26, 24, 26));
        launchInner.setPrefWidth(364);
        launchInner.setMaxWidth(364);
        launchInner.setOpacity(0);

        Label title = Ui.bold("正在启动游戏", 23, Theme.accent);
        Region line = new Region();
        line.setMinHeight(2);
        line.setMaxHeight(2);
        line.setMaxWidth(Double.MAX_VALUE);
        line.setBackground(Ui.fill(Theme.accent, 0));
        VBox.setMargin(line, new Insets(2, 0, 0, 0));

        Ui.Loading load = new Ui.Loading(260, 76, "");
        load.start();
        launchLoading = load;
        VBox.setMargin(load, new Insets(6, 0, 2, 0));

        Label name = Ui.label(ins == null ? "未选择版本" : ins.name, 13, Theme.gray2);
        name.setAlignment(Pos.CENTER);
        name.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(name, new Insets(4, 0, 0, 0));

        StackPane bar = new StackPane();
        bar.setMaxWidth(Double.MAX_VALUE);
        Rectangle track = new Rectangle();
        track.setHeight(4);
        track.widthProperty().bind(launchInner.widthProperty().subtract(52));
        track.setFill(Color.web(
                String.format("#%02x%02x%02x",
                        (int) (Theme.gray3.getRed() * 255), (int) (Theme.gray3.getGreen() * 255),
                        (int) (Theme.gray3.getBlue() * 255)), 0.25)); // 底槽：gray3，0.25 透明
        launchProgressFill = new Rectangle();
        launchProgressFill.setHeight(4);
        launchProgressFill.setWidth(0);
        launchProgressFill.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.accentLight), new Stop(0.6, Theme.accent)));
        StackPane.setAlignment(launchProgressFill, Pos.CENTER_LEFT);
        bar.getChildren().addAll(track, launchProgressFill);
        VBox.setMargin(bar, new Insets(16, 0, 14, 0));

        GridPane info = new GridPane();
        info.setHgap(8);
        info.setVgap(5);
        info.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHalignment(javafx.geometry.HPos.RIGHT);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setMinWidth(15);
        ColumnConstraints c4 = new ColumnConstraints();
        c4.setHgrow(Priority.ALWAYS);
        info.getColumnConstraints().addAll(c1, c2, c3, c4);
        info.add(keyLabel("当前步骤"), 1, 0);
        launchStep = valueLabel("准备启动");
        info.add(launchStep, 3, 0);
        info.add(keyLabel("登录方式"), 1, 1);
        info.add(valueLabel(loginMethodText()), 3, 1);
        info.add(keyLabel("启动进度"), 1, 2);
        launchPercent = valueLabel("0 %");
        info.add(launchPercent, 3, 2);
        // 没有真实下载速率，不显示下载速度占位。

        // 启动成功后显示一条随机「你知道吗」
        VBox hintBox = new VBox(0);
        hintBox.setAlignment(Pos.TOP_CENTER);
        hintBox.setMaxWidth(300);
        hintBox.setVisible(false);
        hintBox.setManaged(false);
        Border hintBorder = new Border(new BorderStroke(
                new Color(Theme.gray1.getRed(), Theme.gray1.getGreen(), Theme.gray1.getBlue(), 0.5),
                BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(1)));
        Region hintFrame = new Region();
        hintFrame.setBorder(hintBorder);
        hintFrame.setPrefHeight(56);
        Label hintTitle = Ui.label("你知道吗", 12.5, Theme.gray1);
        hintTitle.setOpacity(0.5);
        StackPane hintStack = new StackPane(hintFrame, hintTitle);
        Label hintText = Ui.label(randomHint(), 12.5, Theme.text);
        hintText.setWrapText(true);
        hintText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        hintText.setPadding(new Insets(21, 11, 10, 11));
        hintStack.setMaxWidth(Double.MAX_VALUE);
        hintBox.getChildren().addAll(hintStack, hintText);
        VBox.setMargin(hintBox, new Insets(14, 0, 2, 0));
        launchHintBox = hintBox;
        launchInfoBox = info;

        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        StackPane cancel = Ui.button("取消", Ui.BTN_NORMAL, this::cancelLaunch);
        cancel.setMinWidth(70);
        actions.getChildren().add(cancel);
        VBox.setMargin(actions, new Insets(18, 0, 0, 0));

        launchInner.getChildren().addAll(title, line, load, name, bar, info, hintBox, spacerBox(), actions);
        VBox.setVgrow(load, Priority.ALWAYS);
        launchPanel.setCenter(launchInner);
        launchLayer.getChildren().addAll(launchMask, launchPanel);

        Timeline open = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(launchPanel.maxWidthProperty(), 0),
                        new KeyValue(launchPanel.minWidthProperty(), 0.0),
                        new KeyValue(launchInner.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(190), new KeyValue(launchPanel.maxWidthProperty(), 364,
                        Interpolator.EASE_OUT), new KeyValue(launchPanel.minWidthProperty(), 0.0)),
                new KeyFrame(Duration.millis(320), new KeyValue(launchInner.opacityProperty(), 1,
                        Interpolator.EASE_OUT)));
        open.setOnFinished(e -> runLaunchProgress(ins));
        open.play();
    }

    private Region spacerBox() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        r.setMinHeight(30);
        return r;
    }

    private Label keyLabel(String text) {
        Label l = Ui.label(text, 12.5, Theme.text);
        l.setOpacity(0.5);
        return l;
    }

    private Label valueLabel(String text) {
        Label l = Ui.label(text, 12.5, Theme.text);
        return l;
    }

    /** 启动侧边栏的随机「你知道吗」。 */
    private static final String[] HINTS = {
            "Minecraft 的钻石剑其实是参考了经典传说中石中剑的造型！(๑•̀ㅂ•́)و",
            "苦力怕是因为代码 Bug 而诞生的，原本作者只是想制作一头猪……",
            "Minecraft 最初只是一个洞穴游戏，后来才变成了沙盒生存游戏。",
            "末影人英文名 Enderman，名字来源于 Slender Man 和 Minecraft 的合成。",
            "金苹果曾经需要八个金块来合成，后来才改为八个金锭。",
            "村庄与掠夺更新让村庄拥有了职业与工作站，这一切都源于村民 AI 重写。",
            "红石电路的设计灵感来自于现实中的数字逻辑电路。",
            "Minecraft 的世界生成基于柏林噪声，这就是地形看起来自然的原因……",
            "史蒂夫的默认名字其实来自社区投票，而亚历克斯则来自另一场投票。",
            "在基岩版中，玩家可以利用一些特性实现快速移动，社区称之为管道飞行。",
            "1.16 下界更新重做了整个下界群系，是史上最大的一次维度更新之一。",
            "Minecraft 是全球销量最高的电子游戏，没有之一。٩(ˊᗜˋ*)۶",
            "创造模式的物品栏曾经非常简陋，连搜索框都没有。",
            "潜影贝的设定参考了现实中的装饰性贝壳与收纳箱的混合体。",
            "养蜂人村民的蜂箱会吸引蜜蜂，而且蜜蜂会记住自己的家。(•̀ᴗ•́)"
    };

    /** 按登录类型显示：正版 / 统一通行证 / 第三方 / 离线。 */
    private String loginMethodText() {
        Data.Account a = data.account();
        if (a == null) return "离线登录";
        if (a.online && "msa".equals(a.userType)) return "正版登录";
        if ("nide".equals(a.userType)) return "统一通行证";
        if ("legacy".equals(a.userType)) return "第三方登录";
        return "离线登录";
    }

    private static String randomHint() {
        return "你知道吗：" + HINTS[java.util.concurrent.ThreadLocalRandom.current().nextInt(HINTS.length)];
    }

    private void runLaunchProgress(Data.Instance ins) {
        launchTimeline = null;
        launchCancelled = false;
        Thread thread = new Thread(() -> {
            try {
                data.launchLog.clear(); // 新启动先清空旧日志
                launchStepText("检查版本文件", 0.08);
                if (launchCancelled) return;
                // 版本 JSON 按实例目录找，隔离实例优先
                String folder = ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir;
                java.nio.file.Path json = Paths.get(folder, "versions",
                        ins.version, ins.version + ".json");
                if (!java.nio.file.Files.isRegularFile(json)) {
                    throw new Exception("未找到版本文件 " + json + "，请先在「下载」页安装 " + ins.version);
                }
                launchStepText("校验资源完整性", 0.3);
                if (launchCancelled) return;
                // 启动前扫描缺失文件并补齐
                java.util.List<GameLauncher.MissingFile> missing = GameLauncher.scanMissing(ins);
                if (!missing.isEmpty()) {
                    data.launchLog("启动前补全：" + missing.size() + " 个缺失文件");
                    launchStepText("下载缺失文件 0 / " + missing.size(), 0.35);
                    GameLauncher.downloadMissing(missing, p -> Platform.runLater(() -> {
                        setLaunchProgress(0.35 + p * 0.2);
                        int done = (int) Math.round(p * missing.size());
                        if (launchStep != null) {
                            launchStep.setText("下载缺失文件 " + Math.min(done + 1, missing.size())
                                    + " / " + missing.size());
                        }
                    }));
                }
                launchStepText("构建启动参数", 0.58);
                if (launchCancelled) return;
                launchStepText("启动游戏", 0.82);
                Process process = GameLauncher.launch(ins, launchServerIp);
                launchServerIp = null;
                data.launchLog("游戏已启动：" + ins.name);
                Platform.runLater(() -> {
                    if (launchCancelled) return;
                    setLaunchProgress(1);
                    launchStep.setText("启动完成");
                    launchPercent.setText("100 %");
                    // 成功后隐藏进度行，显示「你知道吗」
                    if (launchInfoBox != null) launchInfoBox.setVisible(false);
                    if (launchHintBox != null) {
                        launchHintBox.setVisible(true);
                        launchHintBox.setManaged(true);
                    }
                    ins.lastPlayed = System.currentTimeMillis();
                    // 累加 SystemLaunchCount，只算游戏启动。
                    data.set("SystemLaunchCount", String.valueOf(data.getInt("SystemLaunchCount", 0) + 1));
                    data.save();
                    onGameStarted(process);
                    toast("游戏已启动", ins.name, 1);
                    PauseTransition wait = new PauseTransition(Duration.millis(700));
                    wait.setOnFinished(ev -> closeLaunchPanel());
                    wait.play();
                });
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "未知错误" : ex.getMessage();
                data.launchLog("启动失败：" + msg);
                // 失败弹错误窗给真实原因，不用一闪而过的 toast。
                Errors.alert("Minecraft 启动失败", ex);
                Platform.runLater(() -> {
                    launchStep.setText("启动失败");
                    launchPercent.setText("");
                    PauseTransition wait = new PauseTransition(Duration.millis(1800));
                    wait.setOnFinished(ev -> closeLaunchPanel());
                    wait.play();
                });
            }
        }, "TCL-Launch");
        thread.setDaemon(true);
        thread.start();
    }

    private void launchStepText(String step, double progress) {
        Platform.runLater(() -> {
            launchStep.setText(step);
            setLaunchProgress(progress);
            data.launchLog(step); // 步骤写进首页调试日志卡
        });
    }

    private long lastLaunchProgressAt;
    private double lastLaunchProgressValue = -1;

    private void setLaunchProgress(double value) {
        if (launchProgressFill == null) return;
        long now = System.currentTimeMillis();
        // 节流：进度回调太密集，频繁 new Timeline 会卡 FX 线程
        if (value < 1 && now - lastLaunchProgressAt < 120) {
            if (Math.abs(value - lastLaunchProgressValue) < 0.03) return;
        }
        lastLaunchProgressAt = now;
        lastLaunchProgressValue = value;
        double w = launchInner.getWidth() - 52;
        if (w <= 0) w = 260;
        if (launchProgressAnim != null) launchProgressAnim.stop();
        launchProgressAnim = new Timeline(new KeyFrame(Duration.millis(250),
                new KeyValue(launchProgressFill.widthProperty(), Math.max(0, value * w), Interpolator.EASE_BOTH)));
        launchProgressAnim.play();
        if (launchPercent != null) launchPercent.setText(String.format("%.1f %%", value * 100));
    }

    public void cancelLaunch() {
        launchCancelled = true;
        if (launchTimeline != null) launchTimeline.stop();
        data.log("已取消启动");
        toast("已取消启动", "启动进程已停止", 0);
        closeLaunchPanel();
    }

    private void closeLaunchPanel() {
        launching = false;
        if (launchLoading != null) {
            launchLoading.stop();
            launchLoading = null;
        }
        if (launchPanel == null) {
            launchLayer.setVisible(false);
            return;
        }
        FadeTransition maskOut = new FadeTransition(Duration.millis(180), launchMask);
        maskOut.setToValue(0);
        maskOut.play();
        Timeline close = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(launchInner.opacityProperty(), launchInner.getOpacity())),
                new KeyFrame(Duration.millis(80), new KeyValue(launchInner.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(240), new KeyValue(launchPanel.maxWidthProperty(), 0,
                        Interpolator.EASE_IN)));
        close.setOnFinished(e -> {
            launchLayer.getChildren().clear();
            launchLayer.setVisible(false);
            launchPanel = null;
            launchInner = null;
        });
        close.play();
    }

    public void closeWindow() {
        if (closing) return;
        closing = true;
        saveWindowBounds();
        data.save();
        stopVideo();
        if (shellTimer != null) shellTimer.stop();
        FadeTransition ft = new FadeTransition(Duration.millis(180), root);
        ft.setToValue(0);
        TranslateTransition tt = new TranslateTransition(Duration.millis(180), frame);
        tt.setToY(18);
        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setOnFinished(e -> {
            Platform.exit();
            // Platform.exit() 只关 JavaFX，后台线程可能让 JVM 不退，延迟后强退。
            Thread killer = new Thread(() -> {
                try {
                    Thread.sleep(350);
                } catch (InterruptedException ignored) {
                }
                System.exit(0);
            }, "TCL-Exit");
            killer.setDaemon(true);
            killer.start();
        });
        pt.play();
    }

    private void installResize(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (closing) return;
            int edge = edgeAt(e.getSceneX(), e.getSceneY());
            scene.setCursor(cursorFor(edge));
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            int edge = edgeAt(e.getSceneX(), e.getSceneY());
            if (edge != 0 && e.getButton() == MouseButton.PRIMARY) {
                resizeEdge = edge;
                pressSceneX = e.getScreenX();
                pressSceneY = e.getScreenY();
                startW = stage.getWidth();
                startH = stage.getHeight();
                startX = stage.getX();
                startY = stage.getY();
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizeEdge == 0) return;
            double dx = e.getScreenX() - pressSceneX;
            double dy = e.getScreenY() - pressSceneY;
            double minW = Math.max(320, stage.getMinWidth()), minH = Math.max(240, stage.getMinHeight());
            if ((resizeEdge & 1) != 0) { // 右边缘
                stage.setWidth(Math.max(minW, startW + dx));
            }
            if ((resizeEdge & 2) != 0) { // 左边缘
                double nw = Math.max(minW, startW - dx);
                stage.setX(startX + (startW - nw));
                stage.setWidth(nw);
            }
            if ((resizeEdge & 4) != 0) { // 下边缘
                stage.setHeight(Math.max(minH, startH + dy));
            }
            if ((resizeEdge & 8) != 0) { // 上边缘
                double nh = Math.max(minH, startH - dy);
                stage.setY(startY + (startH - nh));
                stage.setHeight(nh);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> resizeEdge = 0);
    }

    private int edgeAt(double x, double y) {
        double w = stage.getScene().getWidth(), h = stage.getScene().getHeight();
        boolean left = x < 8, right = x > w - 8, top = y < 8, bottom = y > h - 8;
        int e = 0;
        if (right) e |= 1;
        if (left) e |= 2;
        if (bottom) e |= 4;
        if (top) e |= 8;
        return e;
    }

    private Cursor cursorFor(int edge) {
        return switch (edge) {
            case 1, 2 -> Cursor.H_RESIZE;
            case 4, 8 -> Cursor.V_RESIZE;
            case 1 | 4, 2 | 8 -> Cursor.SE_RESIZE;
            case 1 | 8, 2 | 4 -> Cursor.NE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    public void openSetup(String section) {
        pendingSetupSection = section;
        openPage("setup", true);
    }

    public void openVersionSelect() {
        openPage("versions", true);
    }

    public void openInstanceSetup() {
        openPage("instanceSetup", true);
    }

    /** 打开资源详情整页。 */
    public void openResourceDetail(ResourceInfo info) {
        pendingResource = info;
        openPage("resourceDetail", true);
    }

    public Data.Instance selectedInstance() {
        return data.instance();
    }
}
