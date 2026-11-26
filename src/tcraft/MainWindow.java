package tcraft;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Popup;
import javafx.scene.control.Label;
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
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
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

public final class MainWindow {

    private static final double DEFAULT_WINDOW_WIDTH = 850;
    private static final double DEFAULT_WINDOW_HEIGHT = 500;
    private static final double MIN_WINDOW_WIDTH = 760;
    private static final double MIN_WINDOW_HEIGHT = 470;
    private static final double SCREEN_EDGE_MARGIN = 16;

    public final Stage stage;
    public final Data data = Data.get();
    public final StackPane root;
    public StackPane frame;
    public StackPane titleBar;
    public StackPane body;
    private Region frameShadowPlate;
    private Rectangle frameClip;
    private Rectangle frameOverlayClip;
    public StackPane pageHost;
    public StackPane leftHost;
    public StackPane rightHost;
    public StackPane toastLayer;
    public StackPane launchLayer;
    public ImportPanel importPanel;
    public StackPane modalLayer;
    public StackPane searchLayer;

    private VBox toastList;
    private javafx.animation.Timeline toastWatchdog;
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
    private Transition maskFade;
    private Timeline modalExit;
    private Timeline modalEnter;
    private Rotate modalRotate;
    private PauseTransition rightCacheCleanup;
    private Timeline rightRefreshAnim;
    private int rightRefreshToken;
    // 动画打断时补 onHide
    private Page rightPendingHide;
    // onHide 只该各调一次
    private Page lastHiddenPage;
    private Runnable modalCloseHook;

    private Page currentPage;
    private Node curLeft, curRight;
    private double pageLeftWidth;
    private double currentLeftWidth;
    public String currentKey = "launch";
    // 子页面（设置/账户/任务等）返回时回到进入前的那个主页面
    private String lastMainKey = "launch";
    public String pendingSetupSection = "launch";
    public ResourceInfo pendingResource = null;
    private int switchToken;
    private boolean closing;

    private String lastScreenSignature;
    private double baseOpacity = 1;
    private boolean introPrepared;
    private boolean introRunning;
    private boolean pendingAccentRebuild;
    private int resizeEdge = 0;
    private double pressSceneX, pressSceneY, startW, startH, startX, startY;
    private double dragX, dragY;
    private boolean titleDrag;
    private Image bgPaintImage;
    private int bgPaintSuit = -1;
    private double bgPaintW = -1, bgPaintH = -1;
    private Paint bgPaintCache;
    private GaussianBlur bgBlur;
    private double bgBlurRadius = -1;

    private StackPane extraLayer;
    private VBox extraRight;
    private StackPane btnExtraTask;
    private StackPane btnExtraMusic;
    private Arc musicProgressArc;
    private StackPane btnExtraTop;
    private StackPane btnExtraApril;
    private StackPane btnExtraShutdown;
    private HBox pluginActionBox;
    private VBox pluginFloatBox;
    private Arc taskProgressArc;
    private Process gameProcess;
    private boolean musicAvailable;
    private ScrollPane currentScroll;
    private ChangeListener<Number> scrollListener;
    private Timeline shellTimer;
    private Timeline wallpaperTimer;

    private boolean suppressPageEnter;

    private Popup langPopup;

    private StackPane dropLayer;

    private HBox titleInner;
    private javafx.scene.Node leftGroup;
    private Label titleInnerLabel;

    private int extraShown = -1;

    public MainWindow(Stage stage) {
        this.stage = stage;
        long tPhase = System.nanoTime();
        root = new StackPane();
        applyRootFont();
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        Path pictures = Paths.get(data.gameDir, "TCL", "Pictures");
        Theme.onWallpaperReady = this::onWallpaperReady;
        Threads.start("TCL-Wallpaper-Init", () -> {
            if (Theme.loadCustomWallpaper(pictures)) {
                data.log("已从 " + pictures + " 加载自定义背景");
            } else {
                Theme.resetWallpaper();
            }
        });
        loadThemeFromSettings();
        buildFrame();
        long tFrame = System.nanoTime();
        // MediaPlayer 只能 FX 线程建
        Threads.start("TCL-Music-Init", () -> {
            try {
                Music.init(data.gameDir);
            } catch (Throwable ignored) {
            }
            javafx.application.Platform.runLater(() -> {
                musicAvailable = Music.available();
                updateExtras();
                Music.autoStart();
            });
        });
        long tMusic = System.nanoTime();
        double initialW = initialWindowWidth();
        double initialH = initialWindowHeight();
        Scene scene = new Scene(root, initialW, initialH, Color.TRANSPARENT);
        if (Theme.css() != null) scene.getStylesheets().add(Theme.css().toExternalForm());
        long tScene = System.nanoTime();
        scene.widthProperty().addListener((o, a, b) -> {
            updatePageWidth();
            if (cover != null) cover.setWidth(currentLeftWidth);
            updateToastWidths();
        });
        Theme.onCssReload(() -> {
            URL u = Theme.css();
            if (u != null) scene.getStylesheets().setAll(u.toExternalForm());
            applyBackground();
            refreshTitleBar();
        });
        Theme.onAccentApplied = () -> {
            if (closing || !stage.isShowing()) return;
            // 入场动画期间延后重建
            if (introRunning) {
                pendingAccentRebuild = true;
                return;
            }
            rebuildUi();
        };
        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("TCraft Launcher");
        // 图标为空时跳过
        javafx.scene.image.Image winIcon = Theme.loadImage("logo.png");
        if (winIcon != null) stage.getIcons().add(winIcon);
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);
        stage.setAlwaysOnTop(false);
        restoreWindowBounds();
        // 首次显示后按真实屏幕再校一次，避免窗口大于可用桌面被裁掉
        stage.setOnShown(e -> {
            fitWindowToScreen(true);
            Platform.runLater(() -> fitWindowToScreen(true));
        });
        lastScreenSignature = screenSignature();
        installResize(scene);
        installDropTarget(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.K) {
                showGlobalSearch();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                if (searchLayer != null && searchLayer.isVisible()) closeSearch();
                else if (modalLayer != null && modalLayer.isVisible() && modalDismissable) closeModal();
            }
        });
        stage.setOnCloseRequest(e -> {
            e.consume();
            requestClose();
        });
        openPage("launch", false);
        long tPage = System.nanoTime();
        startShellTimer();
        bindVideoWindowState();
        Platform.runLater(() -> refreshVideoBackground(false));
        if (Boolean.getBoolean("tcl.time")) {
            data.log("阶段明细：frame " + (tFrame - tPhase) / 1_000_000
                    + " ms，music " + (tMusic - tFrame) / 1_000_000
                    + " ms，scene " + (tScene - tMusic) / 1_000_000
                    + " ms，page " + (tPage - tScene) / 1_000_000
                    + " ms，tail " + (System.nanoTime() - tPage) / 1_000_000 + " ms");
        }

        // 每次启动累加 SystemCount 每 99 次解锁一个隐藏主题
        int launches = data.getInt("SystemCount", 0) + 1;
        data.set("SystemCount", launches);
        java.util.List<String> unlockedNow = Theme.applyLaunchUnlocks(launches);
        if (!unlockedNow.isEmpty()) {
            // toast 对整串查表 拼接串须先分别查
            String names = String.join(Lang.t("、"), unlockedNow.stream().map(Lang::t).toList());
            data.log("隐藏主题已解锁：" + names + "（累计启动 " + launches + " 次）");
            Platform.runLater(() -> toast("隐藏主题已解锁", names + Lang.t(" —— 可在 设置 → 个性化 中选用。"), 1));
        }
        // 等主窗口就绪再挂错误弹窗
        Errors.attach(this);
    }

    private static double initialWindowWidth() {
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        return Math.min(DEFAULT_WINDOW_WIDTH, Math.max(320, vb.getWidth() - SCREEN_EDGE_MARGIN));
    }

    private static double initialWindowHeight() {
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        return Math.min(DEFAULT_WINDOW_HEIGHT, Math.max(240, vb.getHeight() - SCREEN_EDGE_MARGIN));
    }

    private void restoreWindowBounds() {
        double px = Double.NaN, py = Double.NaN;
        if (data.getBool("UiRememberWindowPos", false)) {
            px = parseDouble(data.get("Window.X", ""), Double.NaN);
            py = parseDouble(data.get("Window.Y", ""), Double.NaN);
        }
        // 多屏按坐标所在屏算边界
        javafx.geometry.Rectangle2D vb = Double.isNaN(px) || Double.isNaN(py)
                ? javafx.stage.Screen.getPrimary().getVisualBounds()
                : screenFor(px, py);
        double maxW = Math.max(320, vb.getWidth() - SCREEN_EDGE_MARGIN);
        double maxH = Math.max(240, vb.getHeight() - SCREEN_EDGE_MARGIN);
        // 最小尺寸不能超过屏幕
        double minW = Math.min(MIN_WINDOW_WIDTH, maxW);
        double minH = Math.min(MIN_WINDOW_HEIGHT, maxH);
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        double defW = Math.min(DEFAULT_WINDOW_WIDTH, maxW);
        double defH = Math.min(DEFAULT_WINDOW_HEIGHT, maxH);
        double w = parseDouble(data.get("Window.Width", ""), defW);
        double h = parseDouble(data.get("Window.Height", ""), defH);
        w = Math.min(Math.max(minW, w), maxW);
        h = Math.min(Math.max(minH, h), maxH);
        root.setPrefSize(w, h);
        stage.setWidth(w);
        stage.setHeight(h);
        if (!Double.isNaN(px) && !Double.isNaN(py)) {
            stage.setX(clamp(px, vb.getMinX(), Math.max(vb.getMinX(), vb.getMaxX() - w)));
            stage.setY(clamp(py, vb.getMinY(), Math.max(vb.getMinY(), vb.getMaxY() - h)));
        } else {
            stage.setX(vb.getMinX() + Math.max(0, (vb.getWidth() - w) / 2));
            stage.setY(vb.getMinY() + Math.max(0, (vb.getHeight() - h) / 2));
        }
    }

    private void fitWindowToScreen(boolean forceVisible) {
        if (!stage.isShowing() || stage.isIconified() || stage.isMaximized() || stage.isFullScreen()) return;
        double x = stage.getX(), y = stage.getY(), w = stage.getWidth(), h = stage.getHeight();
        if (w <= 0 || h <= 0) return;
        javafx.geometry.Rectangle2D vb = screenForWindow(x, y, w, h);
        double maxW = Math.max(320, vb.getWidth() - SCREEN_EDGE_MARGIN);
        double maxH = Math.max(240, vb.getHeight() - SCREEN_EDGE_MARGIN);
        double minW = Math.min(MIN_WINDOW_WIDTH, maxW);
        double minH = Math.min(MIN_WINDOW_HEIGHT, maxH);
        if (Math.abs(stage.getMinWidth() - minW) > 1) stage.setMinWidth(minW);
        if (Math.abs(stage.getMinHeight() - minH) > 1) stage.setMinHeight(minH);
        double nw = Math.min(Math.max(w, minW), maxW);
        double nh = Math.min(Math.max(h, minH), maxH);
        boolean resized = Math.abs(nw - w) > 1 || Math.abs(nh - h) > 1;
        if (Math.abs(nw - w) > 1) {
            root.setPrefWidth(nw);
            stage.setWidth(nw);
        }
        if (Math.abs(nh - h) > 1) {
            root.setPrefHeight(nh);
            stage.setHeight(nh);
        }
        double nx = clamp(x, vb.getMinX(), Math.max(vb.getMinX(), vb.getMaxX() - nw));
        double ny = clamp(y, vb.getMinY(), Math.max(vb.getMinY(), vb.getMaxY() - nh));
        boolean moved = Math.abs(nx - x) > 1 || Math.abs(ny - y) > 1;
        if (!forceVisible && !resized && !moved) return;
        if (Math.abs(nx - x) > 1) stage.setX(nx);
        if (Math.abs(ny - y) > 1) stage.setY(ny);
    }

    private static String screenSignature() {
        List<String> parts = new ArrayList<>();
        for (javafx.stage.Screen s : javafx.stage.Screen.getScreens()) {
            javafx.geometry.Rectangle2D b = s.getVisualBounds();
            parts.add((int) b.getMinX() + "," + (int) b.getMinY() + ","
                    + (int) b.getWidth() + "," + (int) b.getHeight());
        }
        java.util.Collections.sort(parts);
        return String.join("|", parts);
    }

    private static javafx.geometry.Rectangle2D screenForWindow(double x, double y, double w, double h) {
        List<javafx.stage.Screen> screens =
                javafx.stage.Screen.getScreensForRectangle(x, y, w, h);
        if (screens.isEmpty()) return javafx.stage.Screen.getPrimary().getVisualBounds();
        javafx.stage.Screen best = screens.get(0);
        double bestArea = -1;
        for (javafx.stage.Screen s : screens) {
            javafx.geometry.Rectangle2D b = s.getVisualBounds();
            double ow = Math.max(0, Math.min(x + w, b.getMaxX()) - Math.max(x, b.getMinX()));
            double oh = Math.max(0, Math.min(y + h, b.getMaxY()) - Math.max(y, b.getMinY()));
            if (ow * oh > bestArea) {
                bestArea = ow * oh;
                best = s;
            }
        }
        return best.getVisualBounds();
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
        if (stage.isIconified() || stage.isMaximized()) return;
        javafx.geometry.Rectangle2D vb = screenFor(stage.getX() + stage.getWidth() / 2, stage.getY() + 24);
        data.set("Window.Width", Math.min(stage.getWidth(), vb.getWidth() - 8));
        data.set("Window.Height", Math.min(stage.getHeight(), vb.getHeight() - 8));
        if (data.getBool("UiRememberWindowPos", false)) {
            data.set("Window.X", stage.getX());
            data.set("Window.Y", stage.getY());
        }
    }

    private void startShellTimer() {
        shellTimer = new Timeline(new KeyFrame(Duration.millis(250), e -> updateExtras()));
        shellTimer.setCycleCount(Timeline.INDEFINITE);
        shellTimer.play();
        stage.iconifiedProperty().addListener((o, a, b) -> syncShellTimer());
        stage.showingProperty().addListener((o, a, b) -> syncShellTimer());
    }

    private void syncShellTimer() {
        if (shellTimer == null) return;
        if (stage.isIconified() || !stage.isShowing()) shellTimer.pause();
        else shellTimer.play();
    }

    private void updateExtras() {
        String sig = screenSignature();
        boolean screenChanged = !sig.equals(lastScreenSignature);
        lastScreenSignature = sig;
        fitWindowToScreen(screenChanged);
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
        if (musicProgressArc != null && musicAvailable) {
            double p = Music.progress();
            musicProgressArc.setLength(p < 0 ? 0 : -360 * Math.max(0.02, Math.min(1, p)));
        }
        if (btnExtraShutdown != null) setExtraVisible(btnExtraShutdown, gameProcess != null && gameProcess.isAlive());
        if (btnExtraTop != null) {
            boolean top = currentScroll != null && currentScroll.getVvalue() > 0.03;
            setExtraVisible(btnExtraTop, top);
        }
        if (btnExtraApril != null) {
            boolean april = PageLaunch.isAprilEnabled() && "launch".equals(currentKey);
            setExtraVisible(btnExtraApril, april);
        }
        int shown = 0;
        for (Node n : extraRight.getChildren()) if (n.isVisible()) shown++;
        // 250ms 节流 数量变化才改边距
        if (shown != extraShown) {
            extraShown = shown;
            StackPane.setMargin(toastList, new Insets(0, 20, shown > 0 ? 72 : 20, 0));
        }
    }

    public void refreshExtraButtons() {
        musicAvailable = Music.available();
        updateExtras();
    }

    private void setExtraVisible(Node node, boolean visible) {
        if (node.isVisible() == visible) return;
        if (visible) {
            Object pending = node.getProperties().remove("tclExtraHide");
            if (pending instanceof ScaleTransition hide) hide.stop();
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
            node.getProperties().put("tclExtraHide", st);
            st.setOnFinished(e -> {
                // 仅当仍是当前隐藏才 setVisible 新的显示请求优先
                if (node.getProperties().remove("tclExtraHide", st)) {
                    node.setVisible(false);
                    node.setManaged(false);
                }
            });
            st.play();
        }
    }

    private StackPane extraButton(String icon, String tooltip, Runnable action) {
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
        button.setOnMouseEntered(e -> Ui.animateBg(button, Theme.accent, Theme.accentLight, 100));
        button.setOnMouseExited(e -> Ui.animateBg(button, Theme.accentLight, Theme.accent, 200));
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

    public void startWallpaperRotation() {
        if (wallpaperTimer != null) {
            wallpaperTimer.stop();
            wallpaperTimer = null;
        }
        // 0 关 1/5/15/30/60 分钟
        int stored = data.getInt("UiBackgroundRotate", 0);
        // 旧档位 1..5 映射到分钟
        int[] legacy = {0, 1, 5, 15, 30, 60};
        int minutes = (stored == 0 || stored == 1 || stored == 5 || stored == 15
                || stored == 30 || stored == 60) ? stored : (stored >= 1 && stored <= 5 ? legacy[stored] : 0);
        if (minutes <= 0 || data.getInt("UiBackgroundType", 0) == 1) return;
        Path pictures = Paths.get(data.gameDir, "TCL", "Pictures");
        // 防止重复 timer
        Threads.start("TCL-Wallpaper-Rotate", () -> {
            long count;
            try (var s = Files.list(pictures)) {
                count = s.filter(Files::isRegularFile).count();
            } catch (Exception e) {
                return;
            }
            if (count < 2) return;
            javafx.application.Platform.runLater(() -> {
                if (closing) return;
                if (wallpaperTimer != null) wallpaperTimer.stop();
                wallpaperTimer = new Timeline(new KeyFrame(Duration.minutes(minutes), e -> {
                    if (!Theme.loadCustomWallpaper(pictures)) return;
                    if (data.getBool("UiAutoThemeFromBackground", true) && data.getInt("UiThemePreset", -1) < 0) {
                        Theme.applyWallpaperTheme();
                    }
                    applyBackground();
                }));
                wallpaperTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
                wallpaperTimer.play();
            });
        });
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
        rebuildUi();
    }

    private static final String[] VIDEO_EXTS = {"mp4", "m4v", "mov"};

    private final List<Path> vidPlaylist = new ArrayList<>();
    private int vidIndex;
    private int vidErrors;
    private javafx.scene.media.Media vidPreload;
    private Path vidPreloadPath;

    public Path videoFolder() {
        return Paths.get(data.gameDir, "TCL", "Videos");
    }

    private boolean videoMode() {
        return data.getInt("UiBackgroundType", 0) == 1;
    }

    public List<Path> scanVideos() {
        return scanVideosIn(videoFolder());
    }

    public static List<Path> scanVideosIn(Path dir) {
        List<Path> files = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).limit(100).forEach(p -> {
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
            Data.get().log("扫描背景视频文件夹失败：" + Log.describe(e));
        }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
        return files;
    }

    void refreshVideoBackground(boolean showHint) {
        if (!videoMode()) {
            stopVideo();
            if (showHint) toast("背景视频", "当前背景类型为图片，视频已停用。", 2);
            return;
        }
        List<Path> files = scanVideos();
        if (files.isEmpty()) {
            // 视频失败只回落壁纸
            stopVideo();
            applyBackground();
            if (showHint) toast("背景视频", Lang.t("TCL/Videos 里没有可用视频，请放入 mp4/m4v/mov。"), 2);
            return;
        }
        stopVideo();
        if (data.getBool("UiVideoRandom", true)) {
            java.util.Collections.shuffle(files, java.util.concurrent.ThreadLocalRandom.current());
        }
        vidPlaylist.clear();
        vidPlaylist.addAll(files);
        vidIndex = 0;
        vidErrors = 0;
        playVideo();
        if (showHint) toast("背景视频已刷新", vidPlaylist.get(0).getFileName().toString(), 1);
    }

    public void updateVideoAudio() {
        if (vidPlayer == null) return;
        vidPlayer.setMute(data.getBool("UiVideoMute", true));
        vidPlayer.setVolume(data.getInt("UiVideoVolume", 500) / 1000.0);
    }

    // 释放播放器与解码资源
    void stopVideo() {
        detachVideoView();
        if (vidBack != null) {
            vidBack.setMediaPlayer(null);
            vidBack = null;
        }
        javafx.scene.media.MediaPlayer player = vidPlayer;
        vidPlayer = null;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.dispose();
            } catch (Exception ignored) {
            }
        }
        releasePreload();
        vidErrors = 0;
        setVideoModeEffects(false);
    }

    // 只摘下画面 播放器继续跑
    private void detachVideoView() {
        if (vidBack == null) return;
        vidBack.fitWidthProperty().unbind();
        vidBack.fitHeightProperty().unbind();
        if (vidBack.getParent() instanceof Pane parent) parent.getChildren().remove(vidBack);
    }

    private void attachVideoView() {
        if (body == null || vidPlayer == null) return;
        if (vidBack == null) {
            vidBack = new javafx.scene.media.MediaView();
            vidBack.setPreserveRatio(false);
            vidBack.setMouseTransparent(true);
            vidBack.setSmooth(true);
        }
        vidBack.setMediaPlayer(vidPlayer);
        vidBack.setOpacity(data.getInt("UiBackgroundOpacity", 1000) / 1000.0);
        vidBack.fitWidthProperty().unbind();
        vidBack.fitHeightProperty().unbind();
        vidBack.fitWidthProperty().bind(body.widthProperty());
        vidBack.fitHeightProperty().bind(body.heightProperty());
        if (vidBack.getParent() != body) {
            if (vidBack.getParent() instanceof Pane parent) parent.getChildren().remove(vidBack);
            int idx = body.getChildren().indexOf(bgRect);
            body.getChildren().add(idx < 0 ? 0 : idx + 1, vidBack);
        }
        setVideoModeEffects(true);
        Platform.runLater(() -> applyVideoViewport(vidBack));
    }

    // 播放期间关投影
    private void setVideoModeEffects(boolean video) {
        if (frameShadowPlate == null) return;
        frameShadowPlate.setEffect(video ? null : Theme.shadow(22, 6, 0.35, Color.web("#2a3240")));
    }

    private void playVideo() {
        if (vidPlaylist.isEmpty()) return;
        Path file = vidPlaylist.get(Math.floorMod(vidIndex, vidPlaylist.size()));
        String name = file.getFileName().toString();
        javafx.scene.media.MediaPlayer player;
        try {
            player = new javafx.scene.media.MediaPlayer(takePreload(file));
        } catch (Exception e) {
            data.log("背景视频无法播放：" + name + " " + Log.describe(e));
            skipBrokenVideo();
            return;
        }
        player.setMute(data.getBool("UiVideoMute", true));
        player.setVolume(data.getInt("UiVideoVolume", 500) / 1000.0);
        if (vidPlaylist.size() == 1) {
            player.setCycleCount(javafx.scene.media.MediaPlayer.INDEFINITE);
        } else {
            player.setCycleCount(1);
            player.setOnEndOfMedia(() -> Platform.runLater(() -> {
                vidIndex = (vidIndex + 1) % vidPlaylist.size();
                playVideo();
            }));
        }
        player.setOnError(() -> Platform.runLater(() -> {
            data.log("背景视频播放失败：" + name);
            skipBrokenVideo();
        }));
        player.setOnReady(() -> {
            applyVideoViewport(vidBack);
            preloadNext();
        });
        javafx.scene.media.MediaPlayer old = vidPlayer;
        vidPlayer = player;
        if (old != null) {
            try {
                old.stop();
            } catch (Exception ignored) {
            }
            try {
                old.dispose();
            } catch (Exception ignored) {
            }
        }
        attachVideoView();
        boolean background = stage.isShowing() && !stage.isFocused()
                && data.getBool("UiVideoPauseOnBlur", true);
        if (background) player.pause();
        else player.play();
    }

    private void skipBrokenVideo() {
        vidErrors++;
        if (vidErrors >= Math.max(1, vidPlaylist.size())) {
            data.log("所有背景视频都无法播放，已切回图片背景");
            data.set("UiBackgroundType", 0);
            stopVideo();
            applyBackground();
            toast("背景视频无法播放", Lang.t("已切回图片背景，请检查格式（mp4 / m4v / mov）"), 3);
            return;
        }
        vidIndex = (vidIndex + 1) % Math.max(1, vidPlaylist.size());
        playVideo();
    }

    // 预加载下一张
    private void preloadNext() {
        if (vidPlaylist.size() < 2) return;
        Path next = vidPlaylist.get(Math.floorMod(vidIndex + 1, vidPlaylist.size()));
        if (next.equals(vidPreloadPath)) return;
        releasePreload();
        try {
            vidPreload = new javafx.scene.media.Media(next.toUri().toString());
            vidPreloadPath = next;
        } catch (Exception ignored) {
        }
    }

    private javafx.scene.media.Media takePreload(Path file) {
        if (vidPreload != null && file.equals(vidPreloadPath)) {
            javafx.scene.media.Media media = vidPreload;
            vidPreload = null;
            vidPreloadPath = null;
            return media;
        }
        releasePreload();
        return new javafx.scene.media.Media(file.toUri().toString());
    }

    private void releasePreload() {
        vidPreload = null;
        vidPreloadPath = null;
    }

    private void applyVideoViewport(javafx.scene.media.MediaView view) {
        if (view == null || view.getMediaPlayer() == null || body == null) return;
        javafx.scene.media.Media media = view.getMediaPlayer().getMedia();
        if (media == null) return;
        double mw = media.getWidth(), mh = media.getHeight();
        double vw = body.getWidth(), vh = body.getHeight();
        if (mw <= 0 || mh <= 0 || vw <= 0 || vh <= 0) return;
        // UniformToFill
        double scale = Math.max(vw / mw, vh / mh);
        double cw = Math.min(mw, vw / scale);
        double ch = Math.min(mh, vh / scale);
        javafx.geometry.Rectangle2D cur = view.getViewport();
        if (cur != null && Math.abs(cur.getWidth() - cw) < 1 && Math.abs(cur.getHeight() - ch) < 1) return;
        view.setViewport(new javafx.geometry.Rectangle2D((mw - cw) / 2, (mh - ch) / 2, cw, ch));
    }

    private void bindVideoWindowState() {
        stage.iconifiedProperty().addListener((o, a, iconified) -> {
            if (vidPlayer == null) return;
            try {
                if (iconified) vidPlayer.pause();
                else vidPlayer.play();
            } catch (Exception ignored) {
                // 退出过程中媒体引擎可能已销毁
            }
        });
        stage.showingProperty().addListener((o, a, showing) -> {
            if (vidPlayer == null) return;
            try {
                if (showing) vidPlayer.play();
                else vidPlayer.pause();
            } catch (Exception ignored) {
            }
        });
        stage.focusedProperty().addListener((o, a, focused) -> {
            if (vidPlayer == null) return;
            try {
                if (focused) {
                    if (stage.isShowing() && !stage.isIconified()) vidPlayer.play();
                } else if (data.getBool("UiVideoPauseOnBlur", true)) {
                    vidPlayer.pause();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private boolean stageWasHidden;
    private volatile boolean gameStopRequested;

    private void analyzeCrashAsync(Data.Instance ins) {
        if (ins == null) return;
        java.nio.file.Path run = GameLauncher.runDirOf(ins);
        Thread t = new Thread(() -> {
            CrashAnalyzer.Result r = CrashAnalyzer.analyzeInstance(run, 600);
            if (!r.found()) return;
            Platform.runLater(() -> {
                // 模态占用时不顶掉 崩溃写日志
                if (closing || hasOpenModal()) {
                    data.log("游戏异常退出，但弹窗被占用，崩溃报告已写入 latest.log");
                    return;
                }
                PageInstanceSetup.showCrashReport(this, r);
            });
        }, "TCL-CrashAnalyze");
        t.setDaemon(true);
        t.start();
    }

    public boolean isGameRunning() {
        return gameProcess != null && gameProcess.isAlive();
    }

    public void onGameStarted(Process process) {
        gameProcess = process;
        gameStopRequested = false;
        final Data.Instance crashIns = data.instance();
        data.log("游戏进程已启动，PID " + process.pid());
        // UiMusicStop/Start 键义为游戏期暂停/继续
        if (data.getBool("UiMusicStop", false)) Music.pauseForGame();
        else if (data.getBool("UiMusicStart", false)) Music.resumeAfterGame();
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
            // PS 等待进程 勿占 FX
            Shell.setProcessPriority(process.pid(), data.getInt("LaunchPriority", 1));
            int code = -1;
            try {
                code = process.waitFor();
            } catch (Exception e) {
                data.log("等待游戏进程结束失败：" + Log.describe(e));
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
                    // show 不置顶 须 toFront
                    stage.toFront();
                    stageWasHidden = false;
                }
                if (stage.isIconified() && visibility == 4) stage.setIconified(false);
                if (data.getBool("UiMusicStop", false)) Music.resumeAfterGame();
                else if (data.getBool("UiMusicStart", false)) Music.pauseForGame();
                boolean normalExit = exit == 0 || gameStopRequested;
                gameStopRequested = false;
                data.log("游戏进程已退出，返回码 " + exit);
                toast(normalExit ? "游戏已退出" : "游戏异常退出", Lang.t("返回码 ") + exit, normalExit ? 0 : 2);
                if (!normalExit && data.crashAnalysisEnabled()) {
                    analyzeCrashAsync(crashIns);
                }
            });
        }, "TCL-GameWatcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void shutdownGame() {
        if (gameProcess == null) return;
        Dialogs.confirm(this, "关闭 Minecraft", "确定要强制关闭正在运行的 Minecraft 吗？\n未保存的游戏进度可能会丢失。", () -> {
            try {
                gameStopRequested = true;
                gameProcess.destroy();
                data.log("已请求关闭 Minecraft 进程");
                toast("已关闭", "运行中的 Minecraft！", 1);
            } catch (Exception e) {
                data.log("关闭游戏进程失败：" + Log.describe(e));
            }
        }, true);
    }

    private void loadThemeFromSettings() {
        int preset = data.getInt("UiThemePreset", -1);
        if (preset == 14) {
            Theme.applyCustomTheme();
        } else if (preset >= 0) {
            Theme.applyPreset(preset);
        } else if (!Theme.applyCachedAccent()) {
            // preset=-1 跟随壁纸 auto 仅管动态跟随
            Theme.applyWallpaperTheme();
        }
    }

    private void buildFrame() {
        frame = new StackPane();
        frame.setMaxWidth(Double.MAX_VALUE);
        frame.setMaxHeight(Double.MAX_VALUE);
        frame.setMinSize(0, 0);
        Region shadowPlate = new Region();
        frameShadowPlate = shadowPlate;
        shadowPlate.setBackground(Ui.fill(Color.WHITE, windowRadius()));
        shadowPlate.setEffect(Theme.shadow(22, 6, 0.35, Color.web("#2a3240")));
        shadowPlate.setMouseTransparent(true);
        frame.getChildren().add(shadowPlate);
        VBox frameBox = new VBox();
        frameBox.setMinSize(0, 0);
        frameBox.setBackground(null);
        Rectangle clip = new Rectangle();
        frameClip = clip;
        clip.setArcWidth(windowRadius() * 2);
        clip.setArcHeight(windowRadius() * 2);
        frameBox.widthProperty().addListener((o, a, b) -> clip.setWidth(b.doubleValue()));
        frameBox.heightProperty().addListener((o, a, b) -> clip.setHeight(b.doubleValue()));
        frameBox.setClip(clip);
        titleBar = buildTitleBar();
        body = buildBody();
        body.widthProperty().addListener((o, a, b) -> applyVideoViewport(vidBack));
        body.heightProperty().addListener((o, a, b) -> applyVideoViewport(vidBack));
        VBox.setVgrow(body, Priority.ALWAYS);
        frameBox.getChildren().addAll(titleBar, body);

        modalLayer = new StackPane();
        modalLayer.setVisible(false);
        modalLayer.setBackground(Ui.fill(Color.TRANSPARENT, 0));
        modalHost = new StackPane();
        modalHost.setPickOnBounds(false);
        modalLayer.getChildren().add(modalHost);
        modalLayer.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getTarget() == modalLayer && modalDismissable) closeModal();
        });

        searchLayer = new StackPane();
        searchLayer.setVisible(false);
        searchLayer.setBackground(Ui.fill(Color.web("#000000", 0.20), 0));

        dropLayer = new StackPane();
        dropLayer.setVisible(false);
        dropLayer.setMouseTransparent(true);
        dropLayer.setBackground(Ui.fill(Color.web("#000000", 0.10), 0));
        VBox dropCard = new VBox(6);
        dropCard.setPadding(new Insets(16, 26, 18, 26));
        dropCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dropCard.setAlignment(Pos.CENTER);
        dropCard.setBackground(Ui.fill(Theme.cardBg, 14));
        dropCard.setBorder(Ui.border(Theme.accent, 14, 2));
        dropCard.setEffect(Theme.shadow(22, 4, 0.24, Theme.text));
        dropCard.getChildren().addAll(
                Ui.bold("松开鼠标开始导入", 16, Theme.text),
                Ui.label("整合包 .zip / .mrpack，或包含 .minecraft 的游戏目录", 12, Theme.gray2));
        dropLayer.getChildren().add(dropCard);

        StackPane overlayLayer = new StackPane(modalLayer, searchLayer, dropLayer);
        overlayLayer.setPickOnBounds(false);
        Rectangle overlayClip = new Rectangle();
        frameOverlayClip = overlayClip;
        overlayClip.setArcWidth(windowRadius() * 2);
        overlayClip.setArcHeight(windowRadius() * 2);
        overlayLayer.widthProperty().addListener((o, a, b) -> overlayClip.setWidth(b.doubleValue()));
        overlayLayer.heightProperty().addListener((o, a, b) -> overlayClip.setHeight(b.doubleValue()));
        overlayLayer.setClip(overlayClip);
        frame.getChildren().addAll(frameBox, overlayLayer);
        root.getChildren().add(0, frame);
        applyWindowOpacity();
    }

    private double windowRadius() {
        return Math.max(0, data.getInt("UiWindowRadius", 6));
    }

    public void applyWindowRadius() {
        double r = windowRadius();
        if (frameShadowPlate != null) frameShadowPlate.setBackground(Ui.fill(Color.WHITE, r));
        if (frameClip != null) {
            frameClip.setArcWidth(r * 2);
            frameClip.setArcHeight(r * 2);
        }
        if (frameOverlayClip != null) {
            frameOverlayClip.setArcWidth(r * 2);
            frameOverlayClip.setArcHeight(r * 2);
        }
    }

    public void applyWindowOpacity() {
        baseOpacity = Math.max(0.4, Math.min(1, data.getInt("UiLauncherTransparent", 600) / 1000.0 + 0.4));
        if (!closing && !introRunning) root.setOpacity(baseOpacity);
    }

    public void applyBackground() {
        updateBackgroundFill();
    }

    private void onWallpaperReady() {
        if (data.getBool("UiAutoThemeFromBackground", true) && data.getInt("UiThemePreset", -1) < 0) {
            Theme.applyWallpaperTheme();
        }
        applyBackground();
    }

    private void updateBackgroundFill() {
        if (bgRect == null) return;
        Image img = Theme.wallpaper;
        double w = bgRect.getWidth(), h = bgRect.getHeight();
        int op = data.getInt("UiBackgroundOpacity", 1000);
        int blur = data.getInt("UiBackgroundBlur", 0);
        double alpha = Math.max(0, Math.min(1, op / 1000.0));
        boolean show = img != null && w > 0 && h > 0 && alpha > 0 && vidPlayer == null;
        bgRect.setVisible(show);
        bgRect.setOpacity(alpha);
        double radius = blur * 2.0;
        if (radius > 0) {
            if (bgBlur == null || bgBlurRadius != radius) {
                bgBlur = new GaussianBlur(radius);
                bgBlurRadius = radius;
            }
            bgRect.setEffect(bgBlur);
        } else {
            bgRect.setEffect(null);
        }
        // 模糊边缘发白 放大裁溢出
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
            cover.setFill(show ? Color.web("#ffffff", 150 / 255.0) : Color.web("#ffffff", 241 / 255.0));
        }
        if (vidBack != null) vidBack.setOpacity(alpha);
        applyPanelBackground();
    }

    private Paint backgroundPaint(Image img, double w, double h, int suit) {
        if (img == bgPaintImage && suit == bgPaintSuit && w == bgPaintW && h == bgPaintH
                && bgPaintCache != null) {
            return bgPaintCache;
        }
        Paint paint = computeBackgroundPaint(img, w, h, suit);
        bgPaintImage = img;
        bgPaintSuit = suit;
        bgPaintW = w;
        bgPaintH = h;
        bgPaintCache = paint;
        return paint;
    }

    private Paint computeBackgroundPaint(Image img, double w, double h, int suit) {
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

    private void refreshTitleBar() {
        if (titleBar == null) return;
        titleBar.setBackground(new Background(
                new BackgroundFill(titleBarGradient(), CornerRadii.EMPTY, Insets.EMPTY)));
        applyPanelBackground();
    }

    private LinearGradient titleBarGradient() {
        double hue = Theme.accent.getHue(), sat = Theme.accent.getSaturation();
        double topL = 0.54, edgeL = 0.48;
        while (luminance(Theme.hsl(hue, sat * 100, topL * 100)) > 0.2 && topL > 0.26) {
            topL -= 0.02;
            edgeL -= 0.02;
        }
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Theme.hsl(hue, sat * 100, edgeL * 100)),
                new Stop(0.5, Theme.hsl(hue, sat * 100, topL * 100)),
                new Stop(1, Theme.hsl(hue, sat * 100, edgeL * 100)));
    }

    private static double luminance(Color c) {
        double r = linear(c.getRed()), g = linear(c.getGreen()), b = linear(c.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private Color panelGradAccent;
    private LinearGradient panelGradCache;

    private void applyPanelBackground() {
        if (body == null) return;
        if (!data.getBool("UiBackgroundColorful", true)) {
            body.setBackground(new Background(new BackgroundFill(Color.web("#f5f5f5"), CornerRadii.EMPTY, Insets.EMPTY)));
            return;
        }
        // 按 accent 缓存渐变
        if (panelGradCache == null || panelGradAccent != Theme.accent) {
            double hue = Theme.accent.getHue(), sat = Theme.accent.getSaturation() * 0.8;
            panelGradAccent = Theme.accent;
            panelGradCache = new LinearGradient(0.9, 0, 0.1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Theme.hsl(hue - 15, sat * 100, 91)),
                    new Stop(0.4, Theme.hsl(hue, sat * 100, 91)),
                    new Stop(1, Theme.hsl(hue + 15, sat * 100, 91)));
        }
        body.setBackground(new Background(new BackgroundFill(
                panelGradCache, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private StackPane buildTitleBar() {
        StackPane bar = new StackPane();
        bar.setMinWidth(0);
        double barHeight = titleBarHeight();
        bar.setPrefHeight(barHeight);
        bar.setMinHeight(barHeight);
        bar.setMaxHeight(barHeight);
        bar.setBackground(new Background(new BackgroundFill(titleBarGradient(), CornerRadii.EMPTY, Insets.EMPTY)));

        HBox left = new HBox(14);
        left.setMinWidth(0);
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

        navBox = new HBox(10);
        navBox.setMinWidth(0);
        navBox.setAlignment(Pos.CENTER);
        navBox.setPickOnBounds(false);
        navTabs.clear();
        if (!pageHidden("launch")) addNav("启动", "play", "launch");
        if (!pageHidden("instance")) addNav("实例", "box", "instance");
        if (!pageHidden("download")) addNav("下载", "download", "download");
        if (!pageHidden("link")) addNav("联机", "globe", "link");
        if (!pageHidden("other")) addNav("更多", "grid-2x2", "other");

        HBox right = new HBox(4);
        right.setMinWidth(0);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPadding(new Insets(0, 12, 0, 0));
        right.setPickOnBounds(false);
        StackPane langBtn = Ui.iconButtonWhite("languages", 28, 17, "切换界面语言", null);
        langBtn.setOnMouseClicked(e -> {
            showLanguageMenu(langBtn);
            e.consume();
        });
        pluginActionBox = new HBox(4);
        pluginActionBox.setAlignment(Pos.CENTER_RIGHT);
        pluginActionBox.setPickOnBounds(false);
        right.getChildren().addAll(
                Ui.iconButtonWhite("search", 28, 16, "全局搜索（Ctrl+K）", this::showGlobalSearch),
                langBtn,
                pluginActionBox,
                Ui.iconButtonWhite("minus", 28, 16, "最小化", () -> stage.setIconified(true)),
                Ui.iconButtonWhite("x", 28, 16, "关闭", this::requestClose)
        );
        refreshPluginActions();
        right.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getTarget() == right) {
                dragX = e.getScreenX() - stage.getX();
                dragY = e.getScreenY() - stage.getY();
            }
        });

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
        if (data.getBool("UiTitleLeft", false)) {
            StackPane.setAlignment(navBox, Pos.CENTER_LEFT);
            StackPane.setMargin(navBox, new Insets(0, 0, 0, badgeBox.isVisible() ? 156 : 78));
        } else {
            StackPane.setAlignment(navBox, Pos.CENTER);
        }
        StackPane.setAlignment(titleInner, Pos.CENTER_LEFT);
        StackPane.setMargin(titleInner, new Insets(0, 0, 0, 12));
        StackPane.setAlignment(right, Pos.CENTER_RIGHT);
        bar.getChildren().addAll(left, navBox, titleInner, right);
        PauseTransition adaptDebounce = new PauseTransition(Duration.millis(100));
        adaptDebounce.setOnFinished(e -> adaptTitleTabs());
        // 首帧定稿 缩放防抖
        final boolean[] sized = {false};
        bar.widthProperty().addListener((o, a, b) -> {
            if (b.doubleValue() <= 0) return;
            if (!sized[0]) {
                sized[0] = true;
                adaptTitleTabs();
                return;
            }
            adaptDebounce.playFromStart();
        });

        bar.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            titleDrag = !isInteractive(e.getTarget(), bar);
            if (titleDrag) {
                dragX = e.getScreenX() - stage.getX();
                dragY = e.getScreenY() - stage.getY();
            }
        });
        bar.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (titleDrag && e.getButton() == MouseButton.PRIMARY && !closing) {
                moveWindowTo(e.getScreenX() - dragX, e.getScreenY() - dragY);
            }
        });
        bar.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> titleDrag = false);
        return bar;

    }

    private double titleBarHeight() {
        int v = data.getInt("UiTitleBarHeight", 48);
        return Math.max(40, Math.min(64, v));
    }

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

    private void adaptTitleTabs() {
        if (navBox == null || titleBar == null) return;
        try {
            double barW = titleBar.getWidth() > 1 ? titleBar.getWidth() : stage.getWidth();
            if (barW <= 1) return;
            for (NavTab t : navTabs) {
                t.setIconOnly(false);
                t.setCompact(13, false);
            }
            double natural = navBox.prefWidth(-1);
            double leftUsed = 78 + (badgeBox != null && badgeBox.isVisible() ? badgeBox.getWidth() : 0);
            double available = barW - leftUsed - 152 - 2 * 24;
            boolean iconOnly = available < 280 && natural > available;
            if (badgeName != null) {
                badgeName.setVisible(!iconOnly);
                badgeName.setManaged(!iconOnly);
            }
            for (NavTab t : navTabs) t.setIconOnly(iconOnly);
            if (iconOnly) {
                navBox.setSpacing(6);
                return;
            }
            double fs = natural > 0 && natural > available ? Math.max(9, 13 * available / natural) : 13;
            boolean tight = fs < 13 - 0.4;
            for (NavTab t : navTabs) t.setCompact(fs, tight);
            navBox.setSpacing(tight ? Math.max(2, 10 * fs / 13) : 10);
        } catch (Exception ignored) {
        }
    }

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
                    ImageView iv = new ImageView(new Image(p.toUri().toString(), 256, 256, true, true));
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
        badgeName.setMinWidth(0);
        badgeName.setMaxWidth(90);
        badgeName.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        badge.getChildren().addAll(badgeAvatar, badgeName);
        badge.setOnMouseClicked(e -> showAccountDialog());
        badge.setOnMousePressed(e -> e.consume());
        Ui.tooltip(badge, "点击进入账户管理页面，可添加或切换账户");
        badge.setVisible(data.getBool("UiTitleAccountBadge", true));
        badge.setManaged(badge.isVisible());
        return badge;
    }

    // 只刷导航 避免闪窗
    public void refreshNav() {
        if (navBox == null) return;
        navBox.getChildren().clear();
        navTabs.clear();
        if (!pageHidden("launch")) addNav("启动", "play", "launch");
        if (!pageHidden("instance")) addNav("实例", "box", "instance");
        if (!pageHidden("download")) addNav("下载", "download", "download");
        if (!pageHidden("link")) addNav("联机", "globe", "link");
        if (!pageHidden("other")) addNav("更多", "grid-2x2", "other");
        adaptTitleTabs();
    }

    public void refreshAfterHide(String key) {
        java.util.List<double[]> scrolls = captureScrolls();
        refreshNav();
        String nav = switch (key) {
            case "UiHiddenLaunch" -> "launch";
            case "UiHiddenInstance" -> "instance";
            case "UiHiddenDownload" -> "download";
            case "UiHiddenLink" -> "link";
            case "UiHiddenOther" -> "other";
            default -> null;
        };
        if (nav != null && currentPage != null && nav.equals(currentKey)) {
            openPage(firstVisiblePage(), false);
        } else {
            refreshRight(false);
            restoreScrolls(scrolls);
        }
    }

    public void refreshAccountBadge() {
        javafx.application.Platform.runLater(() -> adaptTitleTabs());
        if (badgeName == null || badgeAvatar == null) return;
        Data.Account a = data.account();
        badgeName.setText(a == null ? Lang.t("未登录") : a.name);
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
        private static final Color TRANSPARENT_PILL = Color.web("#eaf2fe", 1 / 255.0);
        private final Node iconNode;
        private final Label label;
        private boolean checked;
        private boolean pressed;
        private Color currentBg = TRANSPARENT_PILL;
        private Color currentFg = Color.WHITE;

        private static final Color HOVER_PILL = Color.web("#eaf2fe", 50 / 255.0);
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
            Ui.tooltip(this, text);
            addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                if (!checked) animateTo(HOVER_PILL, Color.WHITE, 90);
            });
            addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                pressed = false;
                if (!checked) animateTo(TRANSPARENT_PILL, Color.WHITE, 150);
            });
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
            Ui.startExclusive(this, "navTabAnim", t);
        }

        void setCompact(double fontSize, boolean tight) {
            label.setFont(Theme.font(fontSize));
            if (label.isVisible()) {
                if (!tight) {
                    HBox.setMargin(iconNode, new Insets(0, 0, 0, 10));
                    HBox.setMargin(label, new Insets(0, 10, 0, 0));
                } else {
                    double f = Math.max(0.6, fontSize / 13.0);
                    double gap = Math.max(4, 10 * f);
                    HBox.setMargin(iconNode, new Insets(0, 0, 0, gap));
                    HBox.setMargin(label, new Insets(0, gap, 0, 0));
                }
            } else {
                HBox.setMargin(iconNode, new Insets(0, 7, 0, 7));
            }
        }

        void setIconOnly(boolean iconOnly) {
            label.setVisible(!iconOnly);
            label.setManaged(!iconOnly);
            HBox.setMargin(iconNode, new Insets(0, iconOnly ? 7 : 0, 0, iconOnly ? 7 : 10));
        }

        void setChecked(boolean value) {
            checked = value;
            Color bg = value ? Color.WHITE : TRANSPARENT_PILL;
            Color fg = value ? Theme.accent : Color.WHITE;
            animateTo(bg, fg);
        }
    }

    private StackPane buildBody() {
        StackPane b = new StackPane();
        b.setMinSize(0, 0);
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
        // min 归零 防切页撑大窗口
        content.setMinSize(0, 0);
        pageHost.setMinSize(0, 0);
        rightHost.setMinSize(0, 0);
        root.setMinSize(0, 0);
        b.getChildren().add(pageHost);
        importPanel = new ImportPanel();
        b.getChildren().add(importPanel);

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
        startToastWatchdog();
        b.getChildren().add(toastLayer);

        extraLayer = new StackPane();
        extraLayer.setAlignment(Pos.BOTTOM_RIGHT);
        extraLayer.setPickOnBounds(false);
        extraRight = new VBox(8);
        extraRight.setAlignment(Pos.BOTTOM_RIGHT);
        extraRight.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        extraRight.setPickOnBounds(false);
        StackPane.setAlignment(extraRight, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(extraRight, new Insets(0, 15, 14, 0));
        pluginFloatBox = new VBox(8);
        pluginFloatBox.setAlignment(Pos.BOTTOM_RIGHT);
        pluginFloatBox.setPickOnBounds(false);
        pluginFloatBox.setVisible(false);
        pluginFloatBox.setManaged(false);
        extraRight.getChildren().add(pluginFloatBox);
        btnExtraTask = extraButton("layout-list", "任务管理", () -> openPage("tasks", true));
        taskProgressArc = new Arc(0, 0, 18, 18, 90, 0);
        taskProgressArc.setType(ArcType.OPEN);
        taskProgressArc.setFill(Color.TRANSPARENT);
        taskProgressArc.setStroke(Color.web("#ffffff", 0.9));
        taskProgressArc.setStrokeWidth(2);
        taskProgressArc.setMouseTransparent(true);
        btnExtraTask.getChildren().add(taskProgressArc);
        btnExtraMusic = extraButton("music", "背景音乐", Music::toggle);
        musicProgressArc = new Arc(0, 0, 18, 18, 90, 0);
        musicProgressArc.setType(ArcType.OPEN);
        musicProgressArc.setFill(Color.TRANSPARENT);
        musicProgressArc.setStroke(Color.web("#ffd54a", 0.95));
        musicProgressArc.setStrokeWidth(2);
        musicProgressArc.setMouseTransparent(true);
        btnExtraMusic.getChildren().add(musicProgressArc);
        Ui.tooltip(btnExtraMusic, "背景音乐（右键下一首）");
        btnExtraMusic.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) Music.next();
            else Music.toggle();
            toast("背景音乐", Lang.t(Music.isPaused() ? "已暂停 · " : "正在播放 · ") + Music.currentName(), 0);
        });
        btnExtraTop = extraButton("arrow-up", "返回顶部", () -> {
            if (currentScroll != null) {
                Timeline t = new Timeline(new KeyFrame(Duration.millis(260),
                        new KeyValue(currentScroll.vvalueProperty(), 0, Interpolator.EASE_OUT)));
                t.play();
            }
        });
        btnExtraApril = extraButton("flag", "投降", () -> {
            // 愚人节只提示并记年份
            toast("=D", "", 1);
            data.set("AprilYear", java.time.LocalDate.now().getYear());
            updateExtras();
        });
        extraRight.getChildren().addAll(btnExtraTop, btnExtraTask, btnExtraApril, btnExtraMusic);
        extraLayer.getChildren().add(extraRight);
        b.getChildren().add(extraLayer);

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
            case "other" -> new PageOther(this);
            case "setup" -> new PageSetup(this, pendingSetupSection);
            case "versions" -> new PageVersionSelect(this);
            case "instanceSetup" -> new PageInstanceSetup(this);
            case "resourceDetail" -> new PageResourceDetail(this);
            case "tasks" -> new PageTasks(this);
            case "account" -> new PageAccountManager(this);
            default -> new PageLaunch(this);
        };
    }

    public void reloadPage() {
        if (pageHost == null) return;
        String key = currentKey;
        currentKey = "__reload__";
        openPage(key, true);
    }

    // 页面切走后不重建
    public void reloadPageIf(String key) {
        if (pageHost == null || key == null || !key.equals(currentKey)) return;
        reloadPage();
    }

    private void rebuildQuiet() {
        if (pageHost == null || currentPage == null) return;
        String key = currentKey;
        Page page;
        try {
            page = createPage(key);
        } catch (Exception e) {
            Errors.error(Lang.t("重建「") + key + Lang.t("」页面失败"), e);
            return;
        }
        switchToken++;
        commitPage(page, key);
        enterPage(currentPage, false);
        applyNav(key);
    }

    // 插件变动后刷新界面
    public void onPluginsChanged() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(this::onPluginsChanged);
            return;
        }
        refreshPluginActions();
        if ("other".equals(currentKey) || "launch".equals(currentKey)) rebuildQuiet();
    }

    private void refreshPluginActions() {
        if (pluginActionBox != null) {
            pluginActionBox.getChildren().setAll(Plugins.get().titleButtons());
        }
        if (pluginFloatBox != null) {
            pluginFloatBox.getChildren().setAll(Plugins.get().floatButtons());
            boolean has = !pluginFloatBox.getChildren().isEmpty();
            pluginFloatBox.setVisible(has);
            pluginFloatBox.setManaged(has);
            updateExtras();
        }
    }

    public Page currentPageNode() {
        return currentPage;
    }

    public void refreshRight() {
        refreshRight(false);
    }

    public void refreshRightWithLoading() {
        if (importPanel == null || importPanel.isBusy()) {
            refreshRight(true);
            return;
        }
        importPanel.begin("正在刷新", "正在读取本地列表", 0);
        PauseTransition delay = new PauseTransition(Duration.millis(80));
        delay.setOnFinished(e -> {
            refreshRight(true);
            PauseTransition wait = new PauseTransition(Duration.millis(260));
            wait.setOnFinished(x -> importPanel.close());
            wait.play();
        });
        delay.play();
    }

    public void refreshRight(boolean animate) {
        if (pageHost == null) return;
        if (currentPage == null || curLeft == null || curRight == null) {
            rebuildQuiet();
            return;
        }
        Page oldPage = currentPage;
        Page page;
        Node right;
        try {
            page = createPage(currentKey);
            right = page.right();
        } catch (Exception e) {
            Errors.error(Lang.t("构造「") + currentKey + Lang.t("」右栏失败"), e);
            return;
        }
        if (rightRefreshAnim != null) {
            rightRefreshAnim.stop();
            rightRefreshAnim = null;
            // stop 后完成回调不跑 在此补 onHide
            hidePage(rightPendingHide);
            rightPendingHide = null;
        }
        final int token = ++rightRefreshToken;
        if (!animate || !animationsOn()) {
            applyRight(oldPage, page, right);
            return;
        }

        constrainRight(right);
        syncLeftSelection(page.sectionKey());
        if (rightHost.getChildren().size() > 1) {
            rightHost.getChildren().removeIf(n -> n != curRight);
        }
        Node old = curRight;
        // 连点从完整状态退场
        old.setOpacity(1);
        old.setTranslateY(0);
        old.setScaleX(1);
        old.setScaleY(1);
        old.setCache(true);
        old.setCacheHint(javafx.scene.CacheHint.SPEED);
        rightHost.setMouseTransparent(true);

        // 防旧栏先撤时闪空白
        right.setOpacity(0);
        right.setTranslateY(8);
        rightHost.getChildren().add(right);
        Timeline cross = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(old.opacityProperty(), 1),
                        new KeyValue(old.translateYProperty(), 0),
                        new KeyValue(right.opacityProperty(), 0),
                        new KeyValue(right.translateYProperty(), 8)),
                new KeyFrame(Duration.millis(210),
                        new KeyValue(old.opacityProperty(), 0, Ui.fluentIn(2)),
                        new KeyValue(old.translateYProperty(), -7, Ui.fluentIn(2)),
                        new KeyValue(right.opacityProperty(), 1, Ui.EASE_OUT_FLUENT),
                        new KeyValue(right.translateYProperty(), 0, Ui.EASE_OUT_FLUENT)));
        cross.setOnFinished(e -> {
            if (token != rightRefreshToken) {
                rightHost.getChildren().remove(right);
                right.setOpacity(1);
                right.setTranslateY(0);
                return;
            }
            old.setCache(false);
            old.setCacheHint(javafx.scene.CacheHint.DEFAULT);
            hidePage(oldPage);
            rightPendingHide = null;
            currentPage = page;
            lastHiddenPage = null;
            rightHost.getChildren().setAll(right);
            curRight = right;
            rightHost.setOpacity(1);
            rightHost.setTranslateY(0);
            rightHost.setMouseTransparent(false);
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
            right.setOpacity(1);
            right.setTranslateY(0);
            for (Node c : childrenOf(right)) {
                if (c.opacityProperty().isBound()) continue;
                c.setMouseTransparent(false);
                setOpacitySafe(c, 1);
                c.setTranslateY(0);
            }
            rightRefreshAnim = null;
        });
        rightPendingHide = oldPage;
        rightRefreshAnim = cross;
        cross.play();
    }

    // 每页只调一次
    private void hidePage(Page page) {
        if (page == null || page == lastHiddenPage) return;
        lastHiddenPage = page;
        try {
            page.onHide();
        } catch (Exception e) {
            Errors.error("页面退出处理失败", e, Errors.Behavior.NONE);
        }
    }

    // 清最小尺寸 长文本不撑宽
    private void constrainRight(Node right) {
        rightHost.setMinWidth(0);
        rightHost.setMinHeight(0);
        if (right instanceof javafx.scene.layout.Region rr) {
            rr.setMinWidth(0);
            rr.setMinHeight(0);
        }
    }

    private void applyRight(Page oldPage, Page page, Node right) {
        switchToken++;
        hidePage(oldPage);
        rightPendingHide = null;
        currentPage = page;
        lastHiddenPage = null;
        constrainRight(right);
        rightHost.getChildren().setAll(right);
        curRight = right;
        rightHost.setOpacity(1);
        rightHost.setTranslateY(0);
        rightHost.setMouseTransparent(false);
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
        right.setOpacity(1);
        right.setTranslateY(0);
        for (Node c : childrenOf(right)) {
            if (c.opacityProperty().isBound()) continue;
            c.setMouseTransparent(false);
            setOpacitySafe(c, 1);
            c.setTranslateY(0);
        }
    }

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

    private void cancelRightRefresh() {
        if (rightRefreshAnim != null) {
            rightRefreshAnim.stop();
            rightRefreshAnim = null;
            hidePage(rightPendingHide);
            rightPendingHide = null;
        }
        rightRefreshToken++;
        if (rightHost != null) {
            rightHost.setMouseTransparent(false);
            if (curRight != null && rightHost.getChildren().size() > 1) {
                rightHost.getChildren().removeIf(n -> n != curRight);
            }
        }
        if (curRight != null) {
            curRight.setOpacity(1);
            curRight.setTranslateY(0);
        }
    }

    public void openPage(String key, boolean animate) {
        if (pageHidden(key)) key = firstVisiblePage();
        if (Objects.equals(currentKey, key) && currentPage != null && animate) return;
        switch (key) {
            case "launch", "instance", "download", "link", "other" -> lastMainKey = key;
            default -> {
            }
        }
        cancelRightRefresh();
        Page page;
        try {
            page = createPage(key);
        } catch (Exception e) {
            Errors.error(Lang.t("构造「") + key + Lang.t("」页面失败"), e);
            return;
        }
        String oldKey = currentKey;
        boolean samePage = currentPage != null && Objects.equals(oldKey, key);
        boolean firstShow = currentPage == null;
        currentKey = key;
        applyNav(key);
        final int token = ++switchToken;
        final Page nextPage = page;
        final String nextKey = key;
        boolean anim = animate && animationsOn() && pageHost != null && currentPage != null;
        if (!anim) {
            commitPage(page, key);
            enterPage(currentPage, !samePage && !suppressPageEnter && !firstShow);
        } else {
            exitPage(currentPage);
            PauseTransition delay = new PauseTransition(Duration.millis(90));
            delay.setOnFinished(e -> {
                if (token != switchToken) return;
                commitPage(nextPage, nextKey);
                enterPage(currentPage, !suppressPageEnter);
            });
            delay.play();
        }
    }

    private boolean pageHidden(String key) {
        if (key == null) return false;
        String cap = switch (key) {
            case "launch" -> "Launch";
            case "instance" -> "Instance";
            case "download" -> "Download";
            case "link" -> "Link";
            case "other" -> "Other";
            default -> "";
        };
        if (cap.isEmpty()) return false;
        if (!data.getBool("UiHidden" + cap, false)) return false;
        int visible = 0;
        for (String k : new String[]{"Launch", "Instance", "Download", "Link", "Other"}) {
            if (!data.getBool("UiHidden" + k, false)) visible++;
        }
        return visible > 0;
    }

    private String firstVisiblePage() {
        for (String key : new String[]{"launch", "instance", "download", "link", "other"}) {
            if (!pageHidden(key)) return key;
        }
        return "launch";
    }

    private void commitPage(Page page, String key) {
        try {
            hidePage(currentPage);
            rightPendingHide = null;
            lastHiddenPage = null;
            applyPage(page);
            currentPage = page;
            java.util.Map<String, Object> opened = new java.util.HashMap<>();
            opened.put("page", key);
            Plugins.get().fire("page.open", opened);
        } catch (Exception e) {
            data.log("页面构建失败：" + key + " → " + e);
            Errors.error(Lang.t("打开页面「") + key + Lang.t("」失败"), e);
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

    private void applyPage(Page page) {
        Node left = page.left();
        Node right = page.right();
        pageLeftWidth = Math.max(0, page.leftWidth());
        leftHost.getChildren().clear();
        rightHost.getChildren().clear();
        if (left != null) leftHost.getChildren().add(left);
        if (right != null) rightHost.getChildren().add(right);
        Lang.localize(left);
        Lang.localize(right);
        curLeft = left;
        curRight = right;
        pageLeftWidth = fitLeftWidth(left, pageLeftWidth);
        constrainRight(right);
        if (left instanceof javafx.scene.layout.Region lr) {
            lr.setMinWidth(0);
        }
        updatePageWidth();
        // getWidth still reports the previous page before layout; size the cover from the computed width.
        animateCover(currentLeftWidth);
        pageHost.setOpacity(1);
        if (left != null) {
            setOpacitySafe(left, 0);
            left.setTranslateY(0);
            left.setScaleX(0.96);
            left.setScaleY(0.96);
        }
        if (right != null) {
            setOpacitySafe(right, 1);
            right.setTranslateY(0);
            for (Node n : childrenOf(right)) {
                // 透明度由绑定驱动
                if (n.opacityProperty().isBound()) continue;
                setOpacitySafe(n, 0);
                n.setTranslateY(-14);
            }
        }
        bindBackToTop(right);
    }

    /// Fits the sidebar to the widest visible navigation label.
    ///
    /// Long labels keep the declared cap and are ellipsized by `Ui.ListItem`.
    private double fitLeftWidth(Node left, double declared) {
        if (left == null || declared <= 0 || declared > 220) return declared;
        List<Ui.ListItem> items = new ArrayList<>();
        collectListItems(left, items);
        double textWidth = 0;
        for (Ui.ListItem item : items) {
            if (item == null || item.title == null || !item.isVisible()) continue;
            String value = item.title.getText();
            if (value == null || value.isEmpty()) continue;
            Text probe = new Text(value);
            probe.setFont(item.title.getFont());
            textWidth = Math.max(textWidth, probe.getLayoutBounds().getWidth());
        }
        if (textWidth <= 0) return declared;
        double padding = 0;
        if (left instanceof Region region) {
            Insets insets = region.getInsets();
            if (insets != null) padding = insets.getLeft() + insets.getRight();
        }
        // ListItem chrome: 5 selection bar, 5 padding, 34 icon, 11 icon gaps, 22 action, 8 trailing.
        double natural = Math.ceil(textWidth) + padding + 86;
        return Math.max(136, Math.min(declared, natural));
    }

    private void collectListItems(Node node, List<Ui.ListItem> out) {
        if (node == null) return;
        if (node instanceof Ui.ListItem item) {
            out.add(item);
            return;
        }
        if (node instanceof ScrollPane scroll) {
            collectListItems(scroll.getContent(), out);
            return;
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) collectListItems(child, out);
        }
    }

    // 侧栏宽度随窗口实时收缩，避免拉宽后再缩回时右侧被固定侧栏挤没
    private void updatePageWidth() {
        if (leftHost == null) return;
        double stageW = stage.getWidth();
        if (stageW <= 1) stageW = DEFAULT_WINDOW_WIDTH;
        double w = pageLeftWidth;
        if (w > 0) {
            double available = stageW - 340;
            w = Math.min(w, Math.max(160, available));
        }
        currentLeftWidth = w;
        leftHost.setMinWidth(w);
        leftHost.setPrefWidth(w);
        leftHost.setMaxWidth(w);
        leftHost.setVisible(w > 0);
        leftHost.setManaged(w > 0);
    }

    private void exitPage(Page page) {
        if (page == null) return;
        leftHost.setMouseTransparent(true);
        rightHost.setMouseTransparent(true);
        // page.left 重调会生成隐藏新树
        Node left = curLeft;
        Node right = curRight;
        if (left != null) {
            left.setCache(true);
            left.setCacheHint(javafx.scene.CacheHint.SPEED);
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(left.opacityProperty(), left.getOpacity())),
                    new KeyFrame(Duration.millis(90),
                            new KeyValue(left.scaleXProperty(), 0.97, Ui.fluentIn(2)),
                            new KeyValue(left.scaleYProperty(), 0.97, Ui.fluentIn(2)),
                            new KeyValue(left.opacityProperty(), 0, Ui.fluentIn(2))));
            tl.setOnFinished(e -> {
                left.setCache(false);
                left.setCacheHint(javafx.scene.CacheHint.DEFAULT);
            });
            tl.play();
        }
        if (right != null) {
            right.setCache(true);
            right.setCacheHint(javafx.scene.CacheHint.SPEED);
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(right.opacityProperty(), right.getOpacity()),
                            new KeyValue(right.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(90),
                            new KeyValue(right.opacityProperty(), 0, Ui.fluentIn(2)),
                            new KeyValue(right.translateYProperty(), -10, Ui.fluentIn(2))));
            tl.setOnFinished(e -> {
                right.setCache(false);
                right.setTranslateY(0);
            });
            tl.play();
        }
    }

    private void enterPage(Page page, boolean animate) {
        if (page == null) return;
        Node left = curLeft;
        Node right = curRight;
        if (left != null) {
            if (animate && animationsOn() && !left.opacityProperty().isBound()) {
                left.setOpacity(0);
                left.setScaleX(0.96);
                left.setScaleY(0.96);
                left.setCache(true);
                left.setCacheHint(javafx.scene.CacheHint.SPEED);
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.millis(80), new KeyValue(left.opacityProperty(), 1)),
                        new KeyFrame(Duration.millis(260),
                                new KeyValue(left.scaleXProperty(), 1, Ui.EASE_OUT_FLUENT),
                                new KeyValue(left.scaleYProperty(), 1, Ui.EASE_OUT_FLUENT)));
                tl.setOnFinished(e -> left.setCache(false));
                tl.play();
            } else {
                setOpacitySafe(left, 1);
                left.setTranslateY(0);
                left.setScaleX(1);
                left.setScaleY(1);
                left.setCache(false);
            }
        }
        if (animate) {
            enterRight(right);
        } else {
            setOpacitySafe(right, 1);
            right.setTranslateY(0);
            for (Node c : childrenOf(right)) {
                if (c.opacityProperty().isBound()) continue;
                c.setMouseTransparent(false);
                setOpacitySafe(c, 1);
                c.setTranslateY(0);
            }
        }
        leftHost.setMouseTransparent(false);
        rightHost.setMouseTransparent(false);
    }

    private void enterRight(Node right) {
        if (right == null) return;
        setOpacitySafe(right, 1);
        right.setTranslateY(0);
        if (animationsOn()) {
            right.setCache(true);
            right.setCacheHint(javafx.scene.CacheHint.SPEED);
            if (rightCacheCleanup != null) rightCacheCleanup.stop();
            rightCacheCleanup = new PauseTransition(Duration.millis(300));
            rightCacheCleanup.setOnFinished(e -> {
                right.setCache(false);
                right.setCacheHint(javafx.scene.CacheHint.DEFAULT);
                rightCacheCleanup = null;
            });
            rightCacheCleanup.play();
        } else {
            right.setCache(false);
        }
        List<Node> controls = childrenOf(right);
        boolean animate = animationsOn();
        for (int i = 0; i < controls.size(); i++) {
            Node c = controls.get(i);
            c.setMouseTransparent(false);
            if (!animate) {
                setOpacitySafe(c, 1);
                c.setTranslateY(0);
                continue;
            }
            if (c.opacityProperty().isBound()) continue;
            int delay = Math.min(112, i * 14);
            setOpacitySafe(c, 0);
            c.setTranslateY(-12);
            // 级联只做前 8 个
            if (i >= 8) {
                setOpacitySafe(c, 1);
                c.setTranslateY(0);
                continue;
            }
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.millis(delay), new KeyValue(c.opacityProperty(), 0),
                            new KeyValue(c.translateYProperty(), -12)),
                    new KeyFrame(Duration.millis(delay + 140),
                            new KeyValue(c.opacityProperty(), 1, Ui.fluentOut(2))),
                    new KeyFrame(Duration.millis(delay + 260),
                            new KeyValue(c.translateYProperty(), 0, Ui.EASE_OUT_FLUENT)));
            tl.play();
        }
    }

    private void bindBackToTop(Node right) {
        if (scrollListener != null && currentScroll != null) {
            currentScroll.vvalueProperty().removeListener(scrollListener);
            scrollListener = null;
        }
        currentScroll = findScroll(right);
        if (currentScroll != null) {
            scrollListener = (o, a, b) -> {
                if (btnExtraTop != null && btnExtraTop.isVisible() != (currentScroll.getVvalue() > 0.03)) {
                    updateExtras();
                }
            };
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

    // opacity 由绑定驱动 跳过写入
    private static void setOpacitySafe(Node node, double value) {
        if (node == null || node.opacityProperty().isBound()) return;
        node.setOpacity(value);
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
        return data.getInt("UiAnimSpeed", 100) > 0;
    }

    private void applyNav(String key) {
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

    private String subPageTitle(String key) {
        switch (key) {
            case "account":
                return Lang.t("账户管理");
            case "setup":
                return Lang.t("设置");
            case "tasks":
                return Lang.t("任务管理");
            case "instanceSetup": {
                Data.Instance ins = data.instance();
                return Lang.tf("版本设置 - %s", ins == null ? Lang.t("未知版本") : ins.name);
            }
            case "resourceDetail": {
                ResourceInfo ri = pendingResource;
                if (ri == null || ri.name == null || ri.name.isEmpty()) return "资源详情";
                return ri.slug == null || ri.slug.isEmpty() || ri.slug.equals(ri.name)
                        ? ri.name : ri.name + "(" + ri.slug + ")";
            }
            default:
                return null;
        }
    }

    private void switchTitleInner(String text) {
        if (titleInner == null) return;
        titleInnerLabel.setText(text);
        navBox.setMouseTransparent(true);
        titleInner.setMouseTransparent(false);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(130), new KeyValue(navBox.opacityProperty(), 0),
                        new KeyValue(leftGroup.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(150), new KeyValue(titleInner.opacityProperty(), 1)));
        t.play();
    }

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

    private void pageBack() {
        String back = lastMainKey;
        openPage(back == null || back.isEmpty() ? "launch" : back, true);
    }

    // 直接摆终态会先闪一帧
    public void prepareIntro() {
        if (isSoftwareRendering()) {
            root.setOpacity(baseOpacity);
            frame.setRotate(0);
            frame.setTranslateY(0);
            frame.setScaleX(1);
            frame.setScaleY(1);
            if (titleLogoNode != null) titleLogoNode.setOpacity(1);
            return;
        }
        introPrepared = true;
        introRunning = true;
        frame.setRotate(-6);
        frame.setTranslateY(50);
        frame.setScaleX(0.96);
        frame.setScaleY(0.96);
        frame.setOpacity(1);
        root.setOpacity(0.4);
        if (titleLogoNode != null) titleLogoNode.setOpacity(0);
    }

    // 等首帧后再放动画
    public void playIntroWhenReady() {
        if (!introPrepared) return;
        javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
            private int frames;

            @Override
            public void handle(long now) {
                if (++frames < 3) return;
                stop();
                playIntro();
            }
        };
        timer.start();
    }

    // 回弹缓动
    private static final Interpolator EASE_OUT_BACK = new Interpolator() {
        @Override
        protected double curve(double t) {
            double c1 = 0.9;
            double u = t - 1;
            return 1 + (c1 + 1) * u * u * u + c1 * u * u;
        }
    };

    public void playIntro() {
        if (!introPrepared) return;
        Timeline move = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(frame.translateYProperty(), 50),
                        new KeyValue(frame.rotateProperty(), -6),
                        new KeyValue(frame.scaleXProperty(), 0.96),
                        new KeyValue(frame.scaleYProperty(), 0.96)),
                new KeyFrame(Duration.millis(360), new KeyValue(frame.rotateProperty(), 0, EASE_OUT_BACK)),
                new KeyFrame(Duration.millis(420), new KeyValue(frame.translateYProperty(), 0, EASE_OUT_BACK),
                        new KeyValue(frame.scaleXProperty(), 1, EASE_OUT_BACK),
                        new KeyValue(frame.scaleYProperty(), 1, EASE_OUT_BACK)));
        move.setOnFinished(e -> {
            introRunning = false;
            introPrepared = false;
            if (pendingAccentRebuild && !closing) {
                pendingAccentRebuild = false;
                rebuildUi();
            }
        });
        move.play();
        if (titleLogoNode != null) {
            FadeTransition logoFade = new FadeTransition(Duration.millis(340), titleLogoNode);
            logoFade.setFromValue(0);
            logoFade.setToValue(1);
            logoFade.setDelay(Duration.millis(160));
            logoFade.play();
        }
        FadeTransition fade = new FadeTransition(Duration.millis(220), root);
        fade.setFromValue(0.4);
        fade.setToValue(baseOpacity);
        fade.setDelay(Duration.millis(40));
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

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

    private static final class ActiveToast {
        final VBox stack;
        final HBox card;
        final Region progress;
        final StackPane close;
        final String title;
        final String message;
        final int totalMs;
        long startMs;
        double pausedRemaining = -1;
        javafx.animation.Timeline drain;
        javafx.animation.PauseTransition hide;
        javafx.animation.Timeline entry;
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

    private static final double TOAST_DRAG_DEADZONE = 8;
    private static final double TOAST_DISMISS_THRESHOLD_MIN = 80;
    private static final double TOAST_DISMISS_THRESHOLD_RATIO = 0.35;
    private static final double TOAST_DRAG_OPACITY_FLOOR = 0.5;
    private static final double TOAST_MIN_REMAINING_MS = 300;
    private static final int TOAST_RETURN_MS = 150;

    public void toast(String rawTitle, String rawMessage, int type) {
        final String title = Lang.t(rawTitle);
        final String message = Lang.t(rawMessage == null ? "" : rawMessage);
        // 后台调用时切回 FX
        try {
            if (!javafx.application.Platform.isFxApplicationThread()) {
                javafx.application.Platform.runLater(() -> toast(rawTitle, rawMessage, type));
                return;
            }
        } catch (Throwable t) {
            // 图形环境未就绪 错误已由 Errors 落盘
            return;
        }
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
        double toastMax = toastMaxWidth();
        card.setMaxWidth(toastMax);
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
        m.setMaxWidth(Math.max(120, toastMax - 95));
        texts.getChildren().addAll(t, m);
        HBox.setHgrow(texts, Priority.ALWAYS);
        StackPane close = Ui.iconButton("x", 20, 12, null, null);
        card.getChildren().addAll(iconBox, texts, close);
        Region progress = new Region();
        progress.setMaxHeight(3);
        progress.setBackground(Ui.fill(c, 0));
        VBox stack = new VBox(card, progress);
        stack.setMaxWidth(toastMax);
        stack.setOpacity(0);
        stack.setTranslateX(60);
        // 防 aniMs=0 时 toast 即逝
        int duration = 800 + Math.max(5, Math.min(23, (message == null ? 0 : message.length() + title.length())) * 180);
        ActiveToast at = new ActiveToast(stack, card, progress, close, title, message, duration);
        stack.getProperties().put("toast", at);
        close.getProperties().put("toastClose", Boolean.TRUE);
        close.setOnMouseClicked(e -> {
            at.dismiss();
            dismissToast(stack);
        });
        stack.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> at.pauseProgress());
        stack.addEventHandler(MouseEvent.MOUSE_EXITED, e -> at.resumeProgress());
        // toastList 未挂回时重试
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
            javafx.animation.Timeline in = new javafx.animation.Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(stack.opacityProperty(), 0),
                            new KeyValue(stack.translateXProperty(), 60)),
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(stack.opacityProperty(), 1),
                            new KeyValue(stack.translateXProperty(), 0, Ui.EASE_OUT_FLUENT)));
            in.play();
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

    private double toastMaxWidth() {
        double host = body != null && body.getWidth() > 1 ? body.getWidth()
                : (stage.getScene() != null ? stage.getScene().getWidth() : DEFAULT_WINDOW_WIDTH);
        return Math.max(200, Math.min(360, host - 56));
    }

    private void updateToastWidths() {
        if (toastList == null) return;
        double max = toastMaxWidth();
        double textMax = Math.max(120, max - 95);
        for (Node n : toastList.getChildren()) {
            if (n instanceof VBox stack) stack.setMaxWidth(max);
            if (n instanceof VBox stack && !stack.getChildren().isEmpty()
                    && stack.getChildren().get(0) instanceof HBox card) {
                card.setMaxWidth(max);
                for (Node c : card.getChildren()) {
                    if (c instanceof VBox texts && texts.getChildren().size() > 1
                            && texts.getChildren().get(1) instanceof Label message) {
                        message.setMaxWidth(textMax);
                    }
                }
            }
        }
    }

    // 清理过期 toast
    private void startToastWatchdog() {
        if (toastWatchdog != null) toastWatchdog.stop();
        toastWatchdog = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(1), e -> sweepToasts()));
        toastWatchdog.setCycleCount(javafx.animation.Animation.INDEFINITE);
        toastWatchdog.play();
    }

    private void sweepToasts() {
        if (toastList == null) return;
        for (javafx.scene.Node n : new java.util.ArrayList<>(toastList.getChildren())) {
            ActiveToast at = (ActiveToast) n.getProperties().get("toast");
            if (at == null || at.dismissed) continue;
            if (at.pausedRemaining < 0 && at.remainingMs() <= 0) dismissToast(n);
        }
    }

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

    private void installToastDrag(HBox card, VBox stack, ActiveToast at, StackPane close) {
        final double[] dragStartX = {0};
        final double[] dragStartTranslate = {0};
        final boolean[] dragPending = {false};
        final boolean[] dragging = {false};
        card.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
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

    // 自动弹窗不顶掉现有模态
    public boolean hasOpenModal() {
        return modalContent != null;
    }

    public void showModal(Node card, boolean dismissable) {
        showModal(card, dismissable, null);
    }

    public void showModal(Node card, boolean dismissable, Color mask) {
        // 连锁弹窗先停旧动画
        if (maskFade != null) maskFade.stop();
        if (modalExit != null) modalExit.stop();
        if (modalEnter != null) modalEnter.stop();
        Color target = mask == null ? Theme.modalMask : mask;
        Color from = maskColor(modalLayer);
        // 被顶掉的旧弹窗也要放行等待方
        Runnable staleHook = modalCloseHook;
        modalCloseHook = null;
        if (staleHook != null) {
            try {
                staleHook.run();
            } catch (Throwable ignored) {
            }
        }
        modalContent = card;
        modalDismissable = dismissable;
        solidifyModal(card); // 兜底 弹窗一律不透明
        if (card instanceof javafx.scene.layout.Region region) {
            double prefW = region.getPrefWidth();
            if (prefW > 0 && !Double.isNaN(prefW)) region.setMaxWidth(prefW);
            // StackPane 会拉满 Region
            region.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
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
        modalLayer.setOpacity(1);
        // 只渐变遮罩底色
        int maskMs = Ui.aniMs(200);
        if (maskMs > 0) {
            maskFade = maskTransition(modalLayer, from, target, maskMs, 0, null);
            maskFade.play();
        } else {
            modalLayer.setBackground(Ui.fill(target, 0));
        }
        modalRotate = new Rotate(-4, 0, 0);
        modalRotate.pivotYProperty().bind(Bindings.createDoubleBinding(
                () -> card.getLayoutBounds().getHeight() / 2, card.layoutBoundsProperty()));
        card.getTransforms().add(modalRotate);
        card.setOpacity(0);
        card.setTranslateY(40);
        card.setScaleX(1);
        card.setScaleY(1);
        int delay = Ui.aniMs(60);
        int moveMs = Ui.aniMs(300);
        if (moveMs <= 0) {
            card.setOpacity(1);
            card.setTranslateY(0);
            modalRotate.setAngle(0);
        } else {
            modalEnter = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(card.opacityProperty(), 0),
                            new KeyValue(card.translateYProperty(), 40),
                            new KeyValue(modalRotate.angleProperty(), -4)),
                    new KeyFrame(Duration.millis(delay),
                            new KeyValue(card.opacityProperty(), 0),
                            new KeyValue(card.translateYProperty(), 40),
                            new KeyValue(modalRotate.angleProperty(), -4)),
                    new KeyFrame(Duration.millis(delay + Ui.aniMs(120)),
                            new KeyValue(card.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(delay + moveMs),
                            new KeyValue(card.translateYProperty(), 0, Ui.EASE_OUT_BACK),
                            new KeyValue(modalRotate.angleProperty(), 0, Ui.EASE_OUT_FLUENT_WEAK)));
            modalEnter.play();
        }
    }

    private static Color maskColor(Region layer) {
        Background bg = layer.getBackground();
        if (bg == null || bg.getFills().isEmpty()) return Color.TRANSPARENT;
        return bg.getFills().get(0).getFill() instanceof Color c ? c : Color.TRANSPARENT;
    }

    private static Transition maskTransition(Region layer, Color from, Color to, int ms, int delayMs,
                                             Interpolator ease) {
        return new Transition() {
            {
                setDelay(Duration.millis(Math.max(0, delayMs)));
                setCycleDuration(Duration.millis(Math.max(1, ms)));
                if (ease != null) setInterpolator(ease);
            }

            @Override
            protected void interpolate(double frac) {
                layer.setBackground(Ui.fill(from.interpolate(to, frac), 0));
            }
        };
    }

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

    public void setModalCloseHook(Runnable hook) {
        modalCloseHook = hook;
    }

    public void closeModal() {
        // 防退场中重复关闭
        if (modalLayer == null || !modalLayer.isVisible() || modalContent == null) return;
        // 先停入场动画
        if (modalEnter != null) modalEnter.stop();
        if (maskFade != null) maskFade.stop();
        if (modalExit != null) modalExit.stop();
        // 关闭时推进错误队列
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
        final Rotate rot = modalRotate;
        modalContent = null;
        modalRotate = null;
        int moveMs = Ui.aniMs(150);
        if (card != null && moveMs > 0) {
            Timeline out = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(card.opacityProperty(), card.getOpacity()),
                            new KeyValue(card.translateYProperty(), card.getTranslateY()),
                            new KeyValue(rot == null ? card.rotateProperty() : rot.angleProperty(),
                                    rot == null ? card.getRotate() : rot.getAngle())),
                    new KeyFrame(Duration.millis(Ui.aniMs(20)),
                            new KeyValue(card.opacityProperty(), card.getOpacity())),
                    new KeyFrame(Duration.millis(Ui.aniMs(20) + Ui.aniMs(80)),
                            new KeyValue(card.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(moveMs),
                            new KeyValue(card.translateYProperty(), 20, Ui.EASE_OUT_FLUENT),
                            new KeyValue(rot == null ? card.rotateProperty() : rot.angleProperty(),
                                    6, Ui.fluentIn(2))));
            out.setOnFinished(e -> {
                if (modalContent != null) return;
                modalHost.getChildren().clear();
                finishModalHide();
            });
            modalExit = out;
            out.play();
        } else if (card != null) {
            modalHost.getChildren().clear();
        }
        if (modalContent == null) {
            int maskMs = Ui.aniMs(200);
            if (maskMs <= 0) {
                modalLayer.setBackground(Ui.fill(Color.TRANSPARENT, 0));
                finishModalHide();
            } else {
                maskFade = maskTransition(modalLayer, maskColor(modalLayer), Color.TRANSPARENT,
                        maskMs, Ui.aniMs(30), Ui.EASE_OUT_FLUENT_WEAK);
                maskFade.setOnFinished(e -> finishModalHide());
                maskFade.play();
            }
        }
    }

    private void finishModalHide() {
        if (modalContent != null) return;
        if (modalExit != null && modalExit.getStatus() == javafx.animation.Animation.Status.RUNNING) return;
        if (maskFade != null && maskFade.getStatus() == javafx.animation.Animation.Status.RUNNING) return;
        modalLayer.setVisible(false);
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
        scroll.setMaxHeight(330);
        Label empty = Ui.label("没有匹配的结果", 12, Theme.gray3);
        empty.setOpacity(0.7);
        empty.setVisible(false);
        empty.setManaged(false);
        card.getChildren().addAll(field, scroll, empty);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(70, 0, 0, 0));

        List<Object[]> entries = new ArrayList<>();
        // 6 列 隐藏开关过滤页面与分区
        if (!pageHidden("launch")) entries.add(searchEntry("启动", "主页", "play", (Runnable) () -> openPage("launch", true)));
        if (!data.getBool("UiHiddenFunctionSelect", false)) entries.add(searchEntry("版本选择", "启动 → 版本选择", "layers", (Runnable) () -> openPage("versions", true)));
        if (!pageHidden("instance")) entries.add(searchEntry("实例", "实例管理", "box", (Runnable) () -> openPage("instance", true)));
        if (!pageHidden("download")) entries.add(searchEntry("下载", "原版游戏与社区资源", "download", (Runnable) () -> openPage("download", true)));
        if (!pageHidden("link")) entries.add(searchEntry("联机", "EasyTier 虚拟局域网", "globe", (Runnable) () -> openPage("link", true)));
        if (!pageHidden("other")) entries.add(searchEntry("更多", "关于、新闻与实用工具", "layers", (Runnable) () -> openPage("other", true)));
        entries.add(searchEntry("启动游戏", "启动当前实例", "play", (Runnable) this::launchGame));
        entries.add(searchEntry("检查更新", "启动器更新", "refresh-cw", (Runnable) () -> Updates.check(this, true)));
        if (!pageHidden("link")) {
            entries.add(searchEntry("创建房间", "联机", "create-room", (Runnable) () -> openPage("link", true)));
            entries.add(searchEntry("加入房间", "联机", "join-room", (Runnable) () -> openPage("link", true)));
        }
        if (!pageHidden("instance")) {
            entries.add(searchEntry("导入整合包", "实例", "package-plus", (Runnable) () -> openPage("instance", true)));
            entries.add(searchEntry("添加游戏目录", "实例", "folder-plus", (Runnable) () -> openPage("instance", true)));
        }
        entries.add(searchEntry("更换皮肤", "账户管理", "shirt", (Runnable) this::showAccountDialog));
        entries.add(searchEntry("账户管理", "添加或切换账户", "user", (Runnable) this::showAccountDialog));
        entries.add(searchEntry("任务管理", "下载与安装任务", "layout-list", (Runnable) () -> openPage("tasks", true)));
        if (!pageHidden("other")) {
            entries.add(searchEntry("新闻", "更多", "newspaper", (Runnable) () -> openPage("other", true)));
            entries.add(searchEntry("实用工具", "更多", "tool-case", (Runnable) () -> openPage("other", true)));
            entries.add(searchEntry("插件", "更多", "puzzle", (Runnable) () -> {
                data.otherSection = "plugins";
                openPage("other", true);
            }));
            for (Plugins.PageEntry pe : Plugins.get().pluginPages()) {
                final String section = "plugin:" + pe.key;
                entries.add(searchEntry(pe.title, "插件页面", pe.icon, (Runnable) () -> {
                    data.otherSection = section;
                    openPage("other", true);
                }));
            }
            for (Plugins.CommandInfo c : Plugins.get().allCommands()) {
                entries.add(searchEntry(c.name, Lang.t("插件命令 · ") + c.pluginName, "package",
                        (Runnable) () -> Plugins.get().runCommand(c.name, "")));
            }
            entries.add(searchEntry("今日人品", "实用工具", "coffee", (Runnable) () -> openPage("other", true)));
            if (!data.getBool("UiHiddenOtherAbout", false)) {
                entries.add(searchEntry("关于", "更多", "info", (Runnable) () -> openPage("other", true)));
                entries.add(searchEntry("法律声明", "关于", "scroll", (Runnable) () -> openPage("other", true)));
                entries.add(searchEntry("开源地址", "关于", "git-pull-request-arrow", (Runnable) () -> openPage("other", true)));
                entries.add(searchEntry("参考与致谢", "关于", "heart", (Runnable) () -> openPage("other", true)));
            }
        }
        for (Data.Instance ins : data.instances) {
            final Data.Instance target = ins;
            // 实例名不查表
            entries.add(new Object[]{ins.name, "实例 · " + ins.version, "box", (Runnable) () -> {
                data.selectedInstance = data.instances.indexOf(target);
                data.save();
                if (data.getBool("UiHiddenFunctionInstanceModify", false)) launchGame();
                else openPage("instanceSetup", true);
            }, ins.name, Lang.t("实例 · ") + ins.version});
        }
        String[][] setup = {{"启动", "launch"}, {"联机", "link"}, {"个性化", "ui"}, {"系统", "system"},
                {"Java", "java"}, {"日志", "log"}, {"存储与清理", "cache"}, {"网络", "network"}, {"更多", "more"}};
        if (!data.getBool("UiHiddenPageSetup", false)) {
            for (String[] s : setup) {
                if (setupHidden(s[1])) continue;
                entries.add(new Object[]{"设置 - " + s[0], "设置", "settings", (Runnable) () -> {
                    pendingSetupSection = s[1];
                    openPage("setup", true);
                }, Lang.t("设置 - ") + Lang.t(s[0]), Lang.t("设置")});
            }
        }
        String[][] settingNames = {
                {"启动选项", "launch"}, {"默认版本隔离", "launch"}, {"游戏窗口标题", "launch"}, {"内存分配", "launch"},
                {"联机设置", "link"}, {"主题", "ui"}, {"背景", "ui"}, {"背景音乐", "ui"}, {"动画速度", "ui"}, {"窗口圆角", "ui"},
                {"文件下载源", "system"}, {"HTTP 代理", "system"}, {"语言", "system"},
                {"Java 运行时", "java"}, {"启动日志", "log"}, {"存储与清理", "cache"},
                {"下载源测速", "network"}, {"数据备份", "more"}, {"社区", "more"}
        };
        if (!data.getBool("UiHiddenPageSetup", false)) {
            for (String[] s : settingNames) {
                String label = s[0], section = s[1];
                if (setupHidden(section)) continue;
                if (entries.stream().anyMatch(o -> label.equals(o[0]))) continue;
                entries.add(new Object[]{"设置 - " + label, "设置", "settings", (Runnable) () -> {
                    pendingSetupSection = section;
                    openPage("setup", true);
                }, Lang.t("设置 - ") + Lang.t(label), Lang.t("设置")});
            }
        }

        final List<Runnable> visibleActions = new ArrayList<>();
        Consumer<String> filter = q -> {
            results.getChildren().clear();
            visibleActions.clear();
            String query = q == null ? "" : q.trim().toLowerCase();
            int count = 0;
            for (Object[] entry : entries) {
                // 原文与中文都参与匹配
                String title = (String) entry[4];
                String sub = (String) entry[5];
                String titleRaw = (String) entry[0];
                String subRaw = (String) entry[1];
                if (!query.isEmpty()
                        && !(title.toLowerCase().contains(query) || titleRaw.toLowerCase().contains(query))
                        && !(sub.toLowerCase().contains(query) || subRaw.toLowerCase().contains(query)))
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
            empty.setManaged(count == 0);
            scroll.setVisible(count > 0);
            scroll.setManaged(count > 0);
            // 最多 7 行 行高 40
            int rows = Math.min(7, Math.max(1, count));
            scroll.setPrefViewportHeight(rows * 40.0 - 2);
        };
        filter.accept("");
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(130));
        searchDebounce.setOnFinished(e -> filter.accept(field.getText()));
        field.textProperty().addListener((o, a, b) -> searchDebounce.playFromStart());
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
    }

    // 条目 6 列
    private Object[] searchEntry(String title, String sub, String icon, Runnable action) {
        return new Object[]{title, sub, icon, action, Lang.t(title), Lang.t(sub)};
    }

    // 隐藏分区不进搜索
    private boolean setupHidden(String section) {
        String gate = switch (section) {
            case "launch" -> "UiHiddenSetupLaunch";
            case "link" -> "UiHiddenSetupLink";
            case "ui" -> "UiHiddenSetupUi";
            case "system" -> "UiHiddenSetupSystem";
            default -> null;
        };
        return gate != null && data.getBool(gate, false);
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

    public void showAccountDialog() {
        Dialogs.accountManager(this);
    }

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
            rows[i] = Ui.MenuRow.of((Lang.mode == i ? "✓  " : "") + Lang.t(names[i]), "languages", () -> {
                if (Lang.mode == mode) return;
                Lang.setMode(mode);
                data.set("SystemLanguage", mode);
                rebuildUi();
                toast("界面语言", Lang.t("已切换为 ") + Lang.langName(), 1);
            });
        }
        langPopup = Ui.menuCard(anchor, rows);
    }

    // 字体族按系统选
    private void applyRootFont() {
        root.setStyle("-fx-background-color: transparent; -fx-font-family: '" + Theme.fontFamily() + "';");
    }

    public void rebuildUi() {
        // 启动中不重建
        if (launching) {
            pendingRebuild = true;
            return;
        }
        java.util.List<double[]> scrolls = captureScrolls();
        javafx.scene.image.WritableImage snap = snapshotRoot();
        javafx.scene.image.ImageView snapCover = snapshotCover(snap);
        if (snapCover != null) {
            // Cover the old UI so clearing the rebuild never exposes an empty frame.
            root.getChildren().add(snapCover);
        }
        String keep = currentKey;
        hidePage(currentPage);
        rightPendingHide = null;
        lastHiddenPage = null;
        // 重建前放行错误队列与等待方
        boolean hadModal = modalContent != null;
        Runnable staleHook = modalCloseHook;
        modalContent = null;
        modalCloseHook = null;
        maskFade = null;
        modalExit = null;
        modalEnter = null;
        modalRotate = null;
        if (hadModal) {
            Errors.onModalDismissed();
            if (staleHook != null) {
                try {
                    staleHook.run();
                } catch (Throwable ignored) {
                }
            }
        }
        // 播放器不重建 只摘画面再挂回
        detachVideoView();
        // Keep the old UI snapshot and remove everything else.
        root.getChildren().removeIf(n -> n != snapCover);
        // 按悬浮按钮数量重算边距
        extraShown = -1;
        loadThemeFromSettings();
        applyRootFont();
        buildFrame();
        currentPage = null;
        switchToken++;
        boolean prev = suppressPageEnter;
        suppressPageEnter = true;
        try {
            openPage(keep, false);
        } finally {
            suppressPageEnter = prev;
        }
        if (videoMode() && vidPlayer != null) attachVideoView();
        else refreshVideoBackground(false);
        startWallpaperRotation();
        restoreScrolls(scrolls);
        commitSnapshot(snapCover);
    }

    private javafx.scene.image.WritableImage snapshotRoot() {
        try {
            if (root.getScene() == null || root.getWidth() < 2 || root.getHeight() < 2) return null;
            javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
            sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
            return root.snapshot(sp, null);
        } catch (Throwable t) {
            return null;
        }
    }

    /// Creates an unmanaged cover for the pre-rebuild snapshot.
    ///
    /// The snapshot already includes root padding; offsetting it would shift the old UI.
    private javafx.scene.image.ImageView snapshotCover(javafx.scene.image.WritableImage snap) {
        if (snap == null) return null;
        javafx.scene.image.ImageView cover = new javafx.scene.image.ImageView(snap);
        cover.setMouseTransparent(true);
        cover.setManaged(false);
        cover.resize(snap.getWidth(), snap.getHeight());
        // The snapshot already includes root padding; any offset would shift the old UI.
        cover.setTranslateX(0);
        cover.setTranslateY(0);
        return cover;
    }

    private void commitSnapshot(javafx.scene.image.ImageView cover) {
        if (cover == null) return;
        // Lay out the new UI before removing the cover; no fade, so the window never appears to move.
        root.applyCss();
        root.layout();
        root.getChildren().remove(cover);
    }

    private java.util.List<double[]> captureScrolls() {
        java.util.List<javafx.scene.control.ScrollPane> panes = new java.util.ArrayList<>();
        collectScrollPanes(root, panes);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (javafx.scene.control.ScrollPane sp : panes) {
            out.add(new double[]{sp.getVvalue(), sp.getHvalue()});
        }
        return out;
    }

    private static void collectScrollPanes(javafx.scene.Node node,
                                           java.util.List<javafx.scene.control.ScrollPane> out) {
        if (node instanceof javafx.scene.control.ScrollPane sp) out.add(sp);
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) collectScrollPanes(child, out);
        }
    }

    private void restoreScrolls(java.util.List<double[]> scrolls) {
        if (scrolls.isEmpty()) return;
        javafx.application.Platform.runLater(() -> {
            java.util.List<javafx.scene.control.ScrollPane> panes = new java.util.ArrayList<>();
            collectScrollPanes(root, panes);
            int n = Math.min(scrolls.size(), panes.size());
            for (int i = 0; i < n; i++) {
                panes.get(i).setVvalue(scrolls.get(i)[0]);
                panes.get(i).setHvalue(scrolls.get(i)[1]);
            }
        });
    }

    private static boolean isInteractive(Object target, javafx.scene.Node stop) {
        javafx.scene.Node node = target instanceof javafx.scene.Node n ? n : null;
        while (node != null && node != stop) {
            if (node instanceof javafx.scene.control.Control) return true;
            if (node.getCursor() == javafx.scene.Cursor.HAND) return true;
            if (node.getOnMouseClicked() != null) return true;
            node = node.getParent();
        }
        return false;
    }

    public void applyThemeFrom(double sceneX, double sceneY) {
        javafx.geometry.Point2D p = root.sceneToLocal(sceneX, sceneY);
        rebuildUi();

        double w = root.getWidth(), h = root.getHeight();
        if (!animationsOn() || w <= 1 || h <= 1) return;

        double maxR = Math.hypot(Math.max(p.getX(), w - p.getX()), Math.max(p.getY(), h - p.getY())) + 8;
        javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(p.getX(), p.getY(), 0);
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
    // 启动代次 旧线程凭 token 退出
    private volatile int launchToken;
    private boolean pendingRebuild;

    private String launchServerIp;

    public void launchGame() {
        launchGame(null);
    }

    public void launchGame(String serverIp) {
        if (launching) return;
        if (!loginRequirementOk()) return;
        java.util.Map<String, Object> ev = new java.util.HashMap<>();
        Data.Instance eventInstance = data.instance();
        ev.put("instance", eventInstance == null ? "" : eventInstance.name);
        ev.put("version", eventInstance == null ? "" : eventInstance.version);
        Plugins.get().fire("game.launch", ev);
        this.launchServerIp = serverIp;
        if (launchServerIp == null && data.instance() != null) {
            String auto = data.getInstance("ServerEnter", "").trim();
            if (!auto.isEmpty()) launchServerIp = auto;
        }
        launching = true;
        Data.Instance ins = data.instance();
        String version = ins == null ? "未选择版本" : ins.version;
        launchLayer.getChildren().clear();
        launchLayer.setVisible(true);
        launchLayer.setPickOnBounds(true);

        launchMask = new Rectangle();
        launchMask.widthProperty().bind(body.widthProperty());
        launchMask.heightProperty().bind(body.heightProperty());
        launchMask.setFill(Color.web("#000000", 0.25));
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
                        (int) (Theme.gray3.getBlue() * 255)), 0.25));
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

        VBox hintBox = createHintBox();
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

        launchInner.setScaleX(0.8);
        launchInner.setScaleY(0.8);
        Timeline slide = new Timeline(new KeyFrame(Duration.millis(190),
                new KeyValue(launchPanel.maxWidthProperty(), 364, Interpolator.EASE_OUT)));
        slide.play();
        Timeline fade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(launchInner.opacityProperty(), 0.0)),
                new KeyFrame(Duration.millis(150), new KeyValue(launchInner.opacityProperty(), 1,
                        Interpolator.EASE_OUT)));
        fade.setDelay(Duration.millis(100));
        fade.play();
        Timeline grow = new Timeline(new KeyFrame(Duration.millis(500),
                new KeyValue(launchInner.scaleXProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE),
                new KeyValue(launchInner.scaleYProperty(), 1, Ui.EASE_OUT_BACK_MIDDLE)));
        grow.setDelay(Duration.millis(100));
        grow.play();
        PauseTransition startProgress = new PauseTransition(Duration.millis(350));
        startProgress.setOnFinished(e -> {
            if (launching) runLaunchProgress(ins);
        });
        startProgress.play();
    }

    static VBox createHintBox() {
        VBox hintBox = new VBox(0);
        hintBox.setAlignment(Pos.TOP_CENTER);
        hintBox.setMaxWidth(300);
        hintBox.setVisible(false);
        hintBox.setManaged(false);
        Border border = new Border(new BorderStroke(
                new Color(Theme.gray1.getRed(), Theme.gray1.getGreen(), Theme.gray1.getBlue(), 0.5),
                BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(1)));
        Region frame = new Region();
        frame.setBorder(border);
        frame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Label title = Ui.label("你知道吗", 12.5, Theme.gray1);
        title.setOpacity(0.5);
        title.setPadding(new Insets(0, 8, 0, 8));
        // 标题底色盖住边框 形成缺口
        title.setBackground(Ui.fill(Color.web("#FBFBFB"), 0));
        title.setTranslateY(-8);
        StackPane.setAlignment(title, Pos.TOP_CENTER);
        Label text = Ui.label(randomHint(), 12.5, Theme.text);
        text.setWrapText(true);
        text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        text.setMinWidth(0);
        text.setMaxWidth(278);
        VBox body = new VBox(text);
        body.setPadding(new Insets(21, 11, 10, 11));
        StackPane stack = new StackPane(frame, body, title);
        stack.setMaxWidth(Double.MAX_VALUE);
        hintBox.getChildren().add(stack);
        return hintBox;
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

    private boolean loginRequirementOk() {
        int require = data.getInstanceInt("LoginRequire", 0);
        if (require <= 0) return true;
        Data.Account account = data.account();
        boolean ok;
        String message;
        if (require == 1) {
            ok = account != null && account.online && "msa".equals(account.userType);
            message = Lang.t("该实例要求正版登录，请先切换为正版账户。");
        } else if (require == 2) {
            ok = account != null && ("nide".equals(account.userType)
                    || "legacy".equals(account.userType) && account.online);
            message = Lang.t("该实例要求第三方登录，请先切换为第三方账户。");
        } else {
            ok = account != null && account.online;
            message = Lang.t("该实例不允许离线登录，请先切换为在线账户。");
        }
        if (!ok) {
            toast("登录要求", message, 3);
            return false;
        }
        return true;
    }

    private String loginMethodText() {
        Data.Account a = data.account();
        if (a == null) return "离线登录";
        if (a.online && "msa".equals(a.userType)) return "正版登录";
        if ("nide".equals(a.userType)) return "统一通行证";
        if ("legacy".equals(a.userType)) return "第三方登录";
        return "离线登录";
    }

    private static String randomHint() {
        return Lang.t("你知道吗：") + Lang.t(HINTS[java.util.concurrent.ThreadLocalRandom.current().nextInt(HINTS.length)]);
    }

    private void runLaunchProgress(Data.Instance ins) {
        final int token = ++launchToken;
        launchTimeline = null;
        launchCancelled = false;
        if (ins == null) {
            Errors.alert("Minecraft 启动失败", new Exception("还没有选择版本，请先在「实例」页选一个版本再启动。"));
            launching = false;
            closeLaunchPanel();
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                data.launchLog.clear();
                launchStepText("检查版本文件", 0.08);
                if (stale(token)) return;
                // 版本 JSON 在基础目录
                java.nio.file.Path json = Isolation.base(ins).resolve("versions")
                        .resolve(ins.version).resolve(ins.version + ".json");
                if (!java.nio.file.Files.isRegularFile(json)) {
                    throw new Exception(Lang.t("未找到版本文件 ") + json + Lang.t("，请先在「下载」页安装 ") + ins.version);
                }
                data.launchLog("游戏目录：" + Isolation.run(ins) + (Isolation.on(ins) ? "（已隔离）" : "（共用）"));
                hintIsolation(ins);
                launchStepText("校验资源完整性", 0.3);
                if (stale(token)) return;
                java.util.List<GameLauncher.MissingFile> missing = GameLauncher.scanMissing(ins);
                if (!missing.isEmpty()) {
                    data.launchLog("启动前补全：" + missing.size() + " 个缺失文件");
                    launchStepText(Lang.tf("下载缺失文件 %d / %d", 0, missing.size()), 0.35);
                    final long[] lastPush = {0};
                    GameLauncher.downloadMissing(missing, p -> {
                        // 100ms 合并回调
                        long now = System.currentTimeMillis();
                        if (p < 1 && now - lastPush[0] < 100) return;
                        lastPush[0] = now;
                        Platform.runLater(() -> {
                            if (stale(token)) return;
                            setLaunchProgress(0.35 + p * 0.2);
                            int done = (int) Math.round(p * missing.size());
                            if (launchStep != null) {
                                launchStep.setText(Lang.tf("下载缺失文件 %d / %d",
                                        Math.min(done + 1, missing.size()), missing.size()));
                            }
                        });
                    }, () -> stale(token));
                }
                launchStepText("检查游戏需要的 Java", 0.58);
                if (stale(token)) return;
                String resolvedJava = GameLauncher.resolveJava(ins,
                        s -> {
                            data.launchLog(s);
                            Platform.runLater(() -> {
                                if (launchStep != null && !stale(token)) launchStep.setText(s);
                            });
                        },
                        () -> stale(token),
                        p -> Platform.runLater(() -> {
                            if (!stale(token)) setLaunchProgress(0.58 + p * 0.18);
                        }));
                data.launchLog("Java：" + resolvedJava);
                launchStepText("构建启动参数", 0.8);
                if (stale(token)) return;
                launchStepText("启动游戏", 0.88);
                Process process = GameLauncher.launch(ins, launchServerIp, resolvedJava);
                launchServerIp = null;
                data.launchLog("游戏已启动：" + ins.name);
                Platform.runLater(() -> {
                    if (stale(token)) return;
                    setLaunchProgress(1);
                    if (launchStep != null) launchStep.setText(Lang.t("启动完成"));
                    if (launchPercent != null) launchPercent.setText("100 %");
                    if (launchInfoBox != null) launchInfoBox.setVisible(false);
                    if (launchHintBox != null) {
                        launchHintBox.setVisible(true);
                        launchHintBox.setManaged(true);
                    }
                    ins.lastPlayed = System.currentTimeMillis();
                    data.set("SystemLaunchCount", String.valueOf(data.getInt("SystemLaunchCount", 0) + 1));
                    data.save();
                    onGameStarted(process);
                    toast("游戏已启动", ins.name, 1);
                    PauseTransition wait = new PauseTransition(Duration.millis(700));
                    wait.setOnFinished(ev -> closeLaunchPanel());
                    wait.play();
                });
            } catch (Exception ex) {
                // 取消不报错
                if (ex instanceof java.util.concurrent.CancellationException || stale(token)) return;
                String msg = ex.getMessage() == null ? "未知错误" : ex.getMessage();
                data.launchLog("启动失败：" + msg);
                Errors.alert("Minecraft 启动失败", ex);
                Platform.runLater(() -> {
                    if (stale(token)) return;
                    if (launchStep != null) launchStep.setText(Lang.t("启动失败"));
                    if (launchPercent != null) launchPercent.setText("");
                    PauseTransition wait = new PauseTransition(Duration.millis(1800));
                    wait.setOnFinished(ev -> closeLaunchPanel());
                    wait.play();
                });
            }
        }, "TCL-Launch");
        thread.setDaemon(true);
        thread.start();
    }

    // 取消或新一轮时旧线程不回写
    private boolean stale(int token) {
        return launchCancelled || token != launchToken;
    }

    private final java.util.Set<String> isolationHinted = new java.util.HashSet<>();

    private void hintIsolation(Data.Instance ins) {
        if (!Isolation.on(ins)) return;
        java.nio.file.Path run = Isolation.run(ins);
        java.nio.file.Path sharedMods = Isolation.base(ins).resolve("mods");
        if (java.nio.file.Files.isDirectory(run.resolve("mods"))) return;
        if (!java.nio.file.Files.isDirectory(sharedMods)) return;
        if (!isolationHinted.add(ins.name + "|" + run)) return;
        data.launchLog("提示：已开启版本隔离，共享目录的 mods 不会自动复制到 " + run);
        Platform.runLater(() -> toast("版本隔离已开启",
                Lang.tf("本版本的 mods 在 %s 下，共享 mods 未复制", run.getFileName()), 2));
    }

    private void launchStepText(String step, double progress) {
        final String display = Lang.t(step);
        Platform.runLater(() -> {
            if (launchStep == null) return;
            launchStep.setText(display);
            setLaunchProgress(progress);
            data.launchLog(display);
        });
    }

    private long lastLaunchProgressAt;
    private double lastLaunchProgressValue = -1;

    private void setLaunchProgress(double value) {
        if (launchProgressFill == null || launchInner == null) return;
        long now = System.currentTimeMillis();
        // 节流建 Timeline
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
        // 让旧启动线程立即过期
        launchToken++;
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
            flushPendingRebuild();
            return;
        }
        final int token = launchToken;
        final BorderPane closing = launchPanel;
        FadeTransition maskOut = new FadeTransition(Duration.millis(180), launchMask);
        maskOut.setToValue(0);
        maskOut.play();
        Timeline close = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(launchInner.opacityProperty(), launchInner.getOpacity()),
                        new KeyValue(launchInner.scaleXProperty(), launchInner.getScaleX()),
                        new KeyValue(launchInner.scaleYProperty(), launchInner.getScaleY())),
                new KeyFrame(Duration.millis(80), new KeyValue(launchInner.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(150),
                        new KeyValue(launchInner.scaleXProperty(), 0.8, Ui.EASE_OUT_FLUENT_WEAK),
                        new KeyValue(launchInner.scaleYProperty(), 0.8, Ui.EASE_OUT_FLUENT_WEAK)),
                new KeyFrame(Duration.millis(240), new KeyValue(launchPanel.maxWidthProperty(), 0,
                        Interpolator.EASE_IN)));
        close.setOnFinished(e -> {
            // 新一轮已开始时不清面板
            if (launchPanel != closing || token != launchToken) return;
            launchLayer.getChildren().clear();
            launchLayer.setVisible(false);
            launchPanel = null;
            launchInner = null;
            flushPendingRebuild();
        });
        close.play();
    }

    private void flushPendingRebuild() {
        if (!pendingRebuild) return;
        pendingRebuild = false;
        rebuildUi();
    }

    public void requestClose() {
        if (closing) return;
        List<Tasks.Task> active = Tasks.INSTANCE.active();
        boolean transfer = importPanel != null && importPanel.isBusy();
        int count = active.size() + (transfer ? 1 : 0);
        if (count == 0) {
            closeWindow();
            return;
        }
        if (hasOpenModal()) return;
        StringBuilder msg = new StringBuilder(Lang.tf("当前有 %d 个任务正在运行：\n", count));
        int shown = 0;
        for (Tasks.Task task : active) {
            if (shown >= 5) break;
            msg.append("· ").append(Lang.t(task.title));
            if (task.stage != null && !task.stage.isBlank()) msg.append("（").append(Lang.t(task.stage)).append("）");
            msg.append('\n');
            shown++;
        }
        if (active.size() > shown) msg.append(Lang.tf("· 还有 %d 个任务\n", active.size() - shown));
        if (transfer) msg.append(Lang.t("· 正在导入或导出数据\n"));
        msg.append(Lang.t("\n退出会中断这些任务，未完成的下载、安装或导入可能留下不完整文件。确定要退出吗？"));
        Dialogs.confirm(this, "退出启动器", msg.toString(), this::closeWindow, true, () -> {
        });
    }

    public void closeWindow() {
        if (closing) return;
        closing = true;
        data.log("关闭启动器");
        saveWindowBounds();
        Timeline exit = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), root.getOpacity()),
                        new KeyValue(frame.scaleXProperty(), 1),
                        new KeyValue(frame.scaleYProperty(), 1),
                        new KeyValue(frame.translateYProperty(), 0),
                        new KeyValue(frame.rotateProperty(), 0)),
                new KeyFrame(Duration.millis(140),
                        new KeyValue(root.opacityProperty(), 0, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(frame.scaleXProperty(), 0.88, Interpolator.EASE_OUT),
                        new KeyValue(frame.scaleYProperty(), 0.88, Interpolator.EASE_OUT),
                        new KeyValue(frame.translateYProperty(), 20, Interpolator.EASE_OUT),
                        new KeyValue(frame.rotateProperty(), 0.6, Interpolator.EASE_OUT)));
        exit.setOnFinished(e -> {
            data.save();
            stopVideo();
            if (shellTimer != null) shellTimer.stop();
            Platform.exit();
            // Platform.exit 不退后台线程 延迟强退
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
        exit.play();
    }

    private void installDropTarget(Scene scene) {
        scene.addEventFilter(javafx.scene.input.DragEvent.DRAG_OVER, e -> {
            var authServer = AuthlibInjectorDnD.parse(e.getDragboard());
            if (authServer.isPresent() && !hasOpenModal()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                e.consume();
            }
        });
        scene.addEventFilter(javafx.scene.input.DragEvent.DRAG_DROPPED, e -> {
            var authServer = AuthlibInjectorDnD.parse(e.getDragboard());
            if (authServer.isPresent() && !hasOpenModal()) {
                e.setDropCompleted(true);
                e.consume();
                Dialogs.yggdrasilCard(this, authServer.get());
            }
        });
        scene.setOnDragOver(e -> {
            var authServer = AuthlibInjectorDnD.parse(e.getDragboard());
            if (authServer.isPresent() && !hasOpenModal()) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                e.consume();
                return;
            }
            var files = e.getDragboard().getFiles();
            if (!files.isEmpty() && isDropCandidate(files.get(0))) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                if (dropLayer != null) dropLayer.setVisible(true);
                e.consume();
            }
        });
        scene.setOnDragExited(e -> hideDropHint());
        scene.setOnDragDropped(e -> {
            hideDropHint();
            var authServer = AuthlibInjectorDnD.parse(e.getDragboard());
            if (authServer.isPresent() && !hasOpenModal()) {
                e.setDropCompleted(true);
                e.consume();
                Dialogs.yggdrasilCard(this, authServer.get());
                return;
            }
            var files = e.getDragboard().getFiles();
            if (files.isEmpty()) return;
            boolean done = false;
            java.io.File file = files.get(0);
            if (file.isDirectory()) {
                PageInstance.addGameFolder(this, file.toPath());
                done = true;
            } else {
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".zip") || name.endsWith(".mrpack")) {
                    PageInstance.importModpack(this, file.toPath());
                    done = true;
                } else {
                    toast("无法识别拖入的文件", "支持 .zip / .mrpack 整合包或游戏目录", 2);
                }
            }
            e.setDropCompleted(done);
            e.consume();
        });
    }

    private static boolean isDropCandidate(java.io.File file) {
        if (file.isDirectory()) return true;
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".mrpack");
    }

    private void hideDropHint() {
        if (dropLayer != null) dropLayer.setVisible(false);
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
            javafx.geometry.Rectangle2D vb = screenForWindow(startX, startY, startW, startH);
            double minW = Math.max(320, stage.getMinWidth()), minH = Math.max(240, stage.getMinHeight());
            double maxW = Math.max(minW, vb.getWidth() - SCREEN_EDGE_MARGIN);
            double maxH = Math.max(minH, vb.getHeight() - SCREEN_EDGE_MARGIN);
            if ((resizeEdge & 1) != 0) {
                stage.setWidth(Math.min(maxW, Math.max(minW, startW + dx)));
            }
            if ((resizeEdge & 2) != 0) {
                double nw = Math.min(maxW, Math.max(minW, startW - dx));
                stage.setX(Math.max(vb.getMinX(), startX + (startW - nw)));
                stage.setWidth(nw);
            }
            if ((resizeEdge & 4) != 0) {
                stage.setHeight(Math.min(maxH, Math.max(minH, startH + dy)));
            }
            if ((resizeEdge & 8) != 0) {
                double nh = Math.min(maxH, Math.max(minH, startH - dy));
                stage.setY(Math.max(vb.getMinY(), startY + (startH - nh)));
                stage.setHeight(nh);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizeEdge != 0) {
                resizeEdge = 0;
                fitWindowToScreen(true);
            }
        });
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

    public void openResourceDetail(ResourceInfo info) {
        pendingResource = info;
        openPage("resourceDetail", true);
    }
}
