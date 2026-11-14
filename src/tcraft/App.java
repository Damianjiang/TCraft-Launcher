package tcraft;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** 启动入口：先出启动画面，再进主界面。 */
public class App extends Application {

    /** 截图/自检模式，会改窗口尺寸，不写用户配置。 */
    public static boolean SNAPSHOT_MODE;

    private MainWindow window;

    public static void main(String[] args) {
        Boot.main(args); // 走 Boot，JavaFX 起不来还能提示
    }

    /** crash.txt 路径。 */
    private static java.nio.file.Path configCrashPath() {
        return java.nio.file.Paths.get(System.getProperty("user.home"),
                ".tcraft-launcher-javafx", "crash.txt");
    }

    @Override
    public void start(Stage stage) {
        // 先把启动画面放出来
        java.awt.Window splash = createSplash();

        try {
            // 全局异常兜底：FX 线程弹窗，后台线程只记日志。
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    Errors.uncaught(thread, throwable, false));
            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
                    Errors.uncaught(thread, throwable, true));
            Theme.init();
            Lang.reload();
            Theme.applyComponentOpacity(); // 按 UiComponentOpacity 重算半透明色
            EasyTier.killLeftovers(); // 清掉上次残留的 easytier-core
        } catch (Throwable t) {
            showFatal(splash, t);
            return;
        }

        try {
            window = new MainWindow(stage);
            stage.show();
            window.playIntro();
        } catch (Throwable t) {
            showFatal(splash, t);
            return;
        }
        if (splash != null) splash.toFront();

        // 提前握手，第一次进资源页能省 1~2 秒。
        Http.warmup(
                "https://api.modrinth.com/v2/tag/game_version",
                "https://cdn.modrinth.com/",
                "https://api.curseforge.com/v1/mods/search",
                "https://launchercontent.mojang.com/v2/news.json",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

        // 等官方清单回来再刷新本页。
        Data.get().refreshVersions(false, () -> {
            if (window != null) window.refreshRight();
        });

        // 停 300ms 再淡出
        Thread fader = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            if (splash == null) return;
            for (float o = 1f; o >= 0f; o -= 0.1f) {
                try {
                    splash.setOpacity(o);
                    Thread.sleep(30);
                } catch (Exception ignored) {
                }
            }
            splash.dispose();
            javafx.application.Platform.runLater(() -> stage.setAlwaysOnTop(true));
        }, "TCL-Splash-Fade");
        fader.setDaemon(true);
        fader.start();

        handleSnapshotArgs();
        handlePerfArgs();
        startStartupInstance();
    }

    /** --launch-instance "<实例名>"：首帧后选中该实例并启动。 */
    private void startStartupInstance() {
        String name = null;
        List<String> args = getParameters().getRaw();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equalsIgnoreCase("--launch-instance") && i + 1 < args.size()) {
                name = args.get(i + 1);
                break;
            }
            if (a.toLowerCase(java.util.Locale.ROOT).startsWith("--launch-instance=")) {
                name = a.substring(a.indexOf('=') + 1);
                break;
            }
        }
        if (name == null || name.trim().isEmpty()) return;
        final String wanted = name.trim();
        PauseTransition wait = new PauseTransition(Duration.millis(1200));
        wait.setOnFinished(e -> {
            Data.Instance target = null;
            for (Data.Instance ins : Data.get().instances) {
                if (ins.name != null && ins.name.equalsIgnoreCase(wanted)) {
                    target = ins;
                    break;
                }
            }
            if (target == null) {
                window.toast("快捷方式启动失败", "未找到实例「" + wanted + "」", 2);
                return;
            }
            Data data = Data.get();
            data.selectedInstance = data.instances.indexOf(target);
            data.save();
            window.openPage("launch", false);
            // 等切页动画走完再点启动
            PauseTransition delay = new PauseTransition(Duration.millis(900));
            delay.setOnFinished(x -> {
                try {
                    window.launchGame();
                } catch (Exception ex) {
                    Errors.error("快捷方式自动启动失败", ex);
                }
            });
            delay.play();
        });
        wait.play();
    }

    /** --perf=秒数：空闲后采样，打印 CPU 和堆。 */
    private void handlePerfArgs() {
        int secs = -1;
        for (String a : getParameters().getRaw()) {
            if (a.startsWith("--perf=")) {
                try {
                    secs = Integer.parseInt(a.substring(7));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (secs <= 0) return;
        final int window = secs;
        // 空转 5 秒再采样
        PauseTransition warm = new PauseTransition(Duration.seconds(5));
        warm.setOnFinished(e -> {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long cpu0 = os.getProcessCpuTime();
            long wall0 = System.nanoTime();
            PauseTransition sample = new PauseTransition(Duration.seconds(window));
            sample.setOnFinished(ev -> {
                long cpu1 = os.getProcessCpuTime();
                double wallSec = (System.nanoTime() - wall0) / 1e9;
                double corePercent = (cpu1 - cpu0) / 1e9 / wallSec * 100;
                Runtime rt = Runtime.getRuntime();
                System.out.printf("[PERF] 空闲 %.0fs：CPU %.1f%% 单核（%.3f 核）· 堆 %.1f/%.1f MB · 线程 %d%n",
                        wallSec, corePercent, corePercent / 100.0,
                        (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.totalMemory() / 1048576.0,
                        java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
                Platform.exit();
            });
            sample.play();
        });
        warm.play();
    }

    /** 启动画面的 AWT 窗口。 */
    private java.awt.Window splash;

    /** 200×200 纯 Logo，屏幕居中，AWT 裁圆角。 */
    private java.awt.Window createSplash() {
        try {
            java.awt.Window w = new java.awt.Window((java.awt.Frame) null);
            w.setBackground(new java.awt.Color(0, 0, 0, 0));
            java.awt.Image img = java.awt.Toolkit.getDefaultToolkit()
                    .getImage(Theme.res("images/Splash.png").toUri().toURL());
            java.awt.Canvas canvas = new java.awt.Canvas() {
                @Override
                public void paint(java.awt.Graphics g) {
                    g.drawImage(img, 0, 0, 200, 200, null);
                }
            };
            w.add(canvas, java.awt.BorderLayout.CENTER);
            w.setSize(200, 200);
            w.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, 200, 200, 40, 40)); // 圆角半径 20
            w.setLocationRelativeTo(null);
            w.setAlwaysOnTop(true);
            w.setVisible(true);
            return w;
        } catch (Exception e) {
            return null; // 失败也不拦启动
        }
    }

    /** 启动失败：写 crash.txt、关启动画面、弹窗。 */
    private void showFatal(java.awt.Window splash, Throwable t) {
        Path crash = Boot.writeCrash(t);

        try {
            if (splash != null) splash.dispose();
        } catch (Exception ignored) {
        }

        String root = String.valueOf(t);
        StringBuilder detail = new StringBuilder();
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(6, st.length); i++) detail.append("    ").append(st[i]).append('\n');
        Throwable cause = t.getCause();
        if (cause != null) {
            detail.append("  由 ").append(cause).append('\n');
            StackTraceElement[] cs = cause.getStackTrace();
            for (int i = 0; i < Math.min(4, cs.length); i++) detail.append("    ").append(cs[i]).append('\n');
        }

        Stage fail = new Stage(StageStyle.DECORATED);
        fail.setTitle("TCraft Launcher 启动失败");

        Label title = new Label("启动器没能进入主界面");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1f2430;");

        Label msg = new Label(root);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 12px; -fx-text-fill: #d24a4a;");

        Label stack = new Label(detail.toString().trim());
        stack.setWrapText(true);
        stack.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b6472; -fx-font-family: Consolas, monospace;");

        Label where = new Label(crash == null ? "崩溃日志写入失败" : "完整堆栈已写入：\n" + crash);
        where.setWrapText(true);
        where.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b6472;");

        Button openDir = new Button("打开日志文件夹");
        Button close = new Button("关闭");
        String crashPath = crash == null ? null : crash.getParent().toString();
        openDir.setOnAction(e -> {
            if (crashPath != null) {
                try {
                    Shell.openFolder(crashPath);
                } catch (Exception ignored) {
                }
            }
        });
        close.setOnAction(e -> {
            fail.close();
            Platform.exit();
        });
        HBox buttons = new HBox(10, openDir, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, title, msg, stack, where, buttons);
        box.setPadding(new javafx.geometry.Insets(18, 20, 16, 20));
        box.setStyle("-fx-background-color: #ffffff;");
        Scene scene = new Scene(box, 520, 340);
        fail.setScene(scene);
        fail.setOnCloseRequest(e -> Platform.exit());
        fail.centerOnScreen();
        fail.show();
        fail.toFront();
    }

    private void handleSnapshotArgs() {
        List<String> args = getParameters().getRaw();
        String page = null, dialog = null, selArg = null, themeArg = null, blurArg = null;
        boolean langMenu = false;
        for (String a : args) {
            if (a.startsWith("--page=")) page = a.substring(7);
            if (a.startsWith("--dialog=")) dialog = a.substring(9);
            if (a.startsWith("--sel=")) selArg = a.substring(6);   // 切到第 N 个账户
            if (a.startsWith("--theme=")) themeArg = a.substring(8); // 换主题，验证扩散动画
            if (a.equals("--langmenu")) langMenu = true;  // 打开语言弹层
            if (a.startsWith("--blur=")) blurArg = a.substring(7);  // 改背景模糊半径
        }
        final boolean openLang = langMenu;
        SNAPSHOT_MODE = true; // 截图链路，不回写用户状态
        if (page == null && dialog == null && !openLang) return;
        final String targetPage = page;
        final String targetDialog = dialog;
        final String targetSel = selArg;
        final String targetTheme = themeArg;
        final String targetBlur = blurArg;
        PauseTransition wait = new PauseTransition(Duration.millis(1600));
        wait.setOnFinished(e -> {
            if (targetBlur != null) {
                // 只改内存，不写配置
                Data.get().setRuntime("UiBackgroundBlur", targetBlur);
                window.rebuildUi();
            }
            if (targetPage != null) {
                if (targetPage.startsWith("setup:")) {
                    window.pendingSetupSection = targetPage.substring(6);
                    window.openPage("setup", false);
                } else if (targetPage.startsWith("other:")) {
                    Data.get().otherSection = targetPage.substring(6);
                    window.openPage("other", false);
                } else if (targetPage.startsWith("download:")) {
                    Data.get().downloadCategory = targetPage.substring(9);
                    window.openPage("download", false);
                } else if (targetPage.startsWith("link:")) {
                    PageLink.debugState(targetPage.substring(5));
                    window.openPage("link", false);
                } else if (targetPage.startsWith("select:")) {
                    // 直接进版本选择页
                    Data.get().downloadCategory = "原版游戏";
                    window.openPage("download", false);
                    PauseTransition sel = new PauseTransition(Duration.millis(300));
                    sel.setOnFinished(x -> window.debugSelectVersion(targetPage.substring(7)));
                    sel.play();
                } else if (targetPage.startsWith("detail:")) {
                    // 同步搜索，异步的话会先拍到启动页
                    final String q = targetPage.substring(7);
                    ResourceInfo ri = null;
                    try {
                        java.util.List<Object> hits = Modrinth.search(q, "mod", 1);
                        if (!hits.isEmpty()) {
                            java.util.Map<String, Object> m = Json.obj(hits.get(0));
                            ri = ResourceInfo.modrinth(Json.str(m, "project_id", ""), Json.str(m, "slug", ""),
                                    Json.str(m, "title", ""), "mod");
                            ri.desc = Json.str(m, "description", "");
                            ri.icon = Json.str(m, "icon_url", "");
                            ri.downloads = (long) Json.num(m, "downloads", 0);
                            String cats = String.valueOf(m.get("categories")).toLowerCase();
                            for (String loader : new String[]{"fabric", "forge", "neoforge", "quilt"}) {
                                if (cats.contains(loader)) ri.loaders.add(loader);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (ri == null) {
                        window.toast("详情页自检失败", "没搜到「" + q + "」", 2);
                    } else {
                        window.pendingResource = ri;
                        window.openPage("resourceDetail", false);
                    }
                } else {
                    window.openPage(targetPage, false);
                }
            }
            if ("account".equals(targetDialog)) window.showAccountDialog();
            else if ("skin".equals(targetDialog) || "skinview".equals(targetDialog)) {
                // 有真实账户就用，没有临时离线账户
                boolean view = "skinview".equals(targetDialog);
                Data.Account a = null;
                Data dd = Data.get();
                if (!dd.accounts.isEmpty()) {
                    a = dd.accounts.get(Math.max(0, Math.min(dd.selectedAccount, dd.accounts.size() - 1)));
                }
                if (a == null) {
                    a = new Data.Account("TestSkin", Data.offlineUuid("TestSkin"), "Steve.png", false);
                }
                if (view) a.online = true; else a.online = false;
                Dialogs.skinPicker(window, a);
            }
            else if ("nide".equals(targetDialog)) Dialogs.nideCard(window);
            else if ("luck".equals(targetDialog) || "count".equals(targetDialog)) {
                window.openPage("other", false);
                PauseTransition after = new PauseTransition(Duration.millis(400));
                after.setOnFinished(x -> {
                    if (window.currentPageNode() instanceof PageOther po) {
                        if ("luck".equals(targetDialog)) po.debugLuck(); else po.debugLaunchCount();
                    }
                });
                after.play();
            }
            else if ("confirm".equals(targetDialog)) Dialogs.confirm(window, "关闭 Minecraft",
                    "确定要强制关闭正在运行的 Minecraft 吗？\n未保存的游戏进度可能会丢失。", () -> {}, true);
            if (targetSel != null) {
                // 切到第 N 个账户
                PauseTransition pick = new PauseTransition(Duration.millis(500));
                pick.setOnFinished(x -> {
                    if (window.currentPageNode() instanceof PageAccountManager pam) {
                        pam.select(Integer.parseInt(targetSel));
                    }
                });
                pick.play();
            }
            if (openLang) window.debugLanguageMenu();
            final String shotName = targetPage != null ? targetPage.replace(':', '_')
                    : targetDialog != null ? "dialog_" + targetDialog : "home";
            if (targetTheme != null) {
                // 换主题后立刻拍，抓扩散中间帧
                PauseTransition pick = new PauseTransition(Duration.millis(1400));
                pick.setOnFinished(x -> {
                    Data.get().set("UiThemePreset", Integer.parseInt(targetTheme));
                    Data.get().set("UiAutoThemeFromBackground", false);
                    window.applyThemeFrom(window.root.getWidth() * 0.55, window.root.getHeight() * 0.42);
                    PauseTransition mid = new PauseTransition(Duration.millis(120));
                    mid.setOnFinished(m -> {
                        snapshot(shotName + "_ripple.png");
                        Platform.exit();
                    });
                    mid.play();
                });
                pick.play();
                return;
            }
            // 默认等 3500ms，可用 -Dtcl.shot.delay 覆盖
            PauseTransition shot = new PauseTransition(
                    Duration.millis(Long.getLong("tcl.shot.delay", 3500L)));
            shot.setOnFinished(ev -> {
                snapshot(shotName + ".png");
                Platform.exit();
            });
            shot.play();
        });
        wait.play();
    }

    private void snapshot(String name) {
        try {
            // 先跑 CSS 和布局再量尺寸
            window.root.applyCss();
            window.root.layout();
            System.out.println("[SNAPSHOT] scene=" + window.stage.getScene().getWidth() + "x"
                    + window.stage.getScene().getHeight() + " modal=" + window.debugModalSize()
                    + " | " + window.debugRightInfo());
            Path dir = Theme.root().resolve("snapshots");
            Files.createDirectories(dir);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#20242c"));
            // 不设 viewport，按 root 自己的 bounds 拍
            WritableImage image = window.root.snapshot(params, null);
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = image.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            }
            ImageIO.write(bi, "png", dir.resolve(name).toFile());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        EasyTier.stop();
        // 残留 utun 交给下次 killLeftovers。
    }
}
