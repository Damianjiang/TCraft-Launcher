package tcraft;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class App extends Application {

    private MainWindow window;

    public static void main(String[] args) {
        Boot.main(args); // 走 Boot，JavaFX 起不来还能提示
    }

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);   // 启动游戏后可能隐藏主窗，隐式退出会把整个程序带崩
        Log.startSession();
        long tPhase = System.nanoTime();

        try {
            // 全局异常：FX 线程弹窗，后台只记日志
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    Errors.uncaught(thread, throwable, false));
            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
                    Errors.uncaught(thread, throwable, true));
            Theme.init();
            Lang.reload();
            Theme.applyComponentOpacity();
            applyInitialTheme();
            if (Boolean.getBoolean("tcl.time")) {
                Data.get().log("阶段：主题与语言 " + (System.nanoTime() - tPhase) / 1_000_000 + " ms");
            }
            Thread cleanup = new Thread(EasyTier::killLeftovers, "TCL-Cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        } catch (Throwable t) {
            showFatal(t);
            return;
        }

        try {
            window = new MainWindow(stage);
            if (Boolean.getBoolean("tcl.time")) {
                Data.get().log("阶段：界面构建 " + (System.nanoTime() - tPhase) / 1_000_000 + " ms");
            }
            // 首帧优先：扫 Java/预取运行库留到空闲
            stage.show();
            if (Boolean.getBoolean("tcl.time")) {
                Data.get().log("阶段：显示 " + (System.nanoTime() - tPhase) / 1_000_000 + " ms");
            }
            stage.setAlwaysOnTop(false);
            // 告诉首次运行留下的旧进程：主界面已经出来了，可以把启动窗收掉
            try {
                Files.writeString(Resources.logDir().resolve(".window-ready"), "1");
            } catch (Exception ignored) {
            }
            Resources.cleanupNoiseLogs();
            if (!Data.get().getBool("SystemDebugMode", false)) {
                // 关掉重定向的 stdout/stderr 才能删 java-error.log
                try {
                    System.out.close();
                    System.err.close();
                } catch (Exception ignored) {
                }
                Resources.cleanupNoiseLogs();
            }
            long ms = (System.nanoTime() - Boot.STARTED_AT) / 1_000_000L;
            Data.get().log("启动完成，用时 " + ms + " ms");
        } catch (Throwable t) {
            showFatal(t);
            return;
        }
        stage.setAlwaysOnTop(false);
        javafx.application.Platform.runLater(() -> {
            stage.setAlwaysOnTop(false);
            stage.toFront();
        });
        PauseTransition top = new PauseTransition(Duration.millis(1500));
        top.setOnFinished(e -> stage.setAlwaysOnTop(false));
        top.play();

        // 空闲后再做这两件事，让界面首帧先吃满磁盘与网络
        PauseTransition idle = new PauseTransition(Duration.millis(2000));
        idle.setOnFinished(e -> {
            Thread javaScan = new Thread(() -> {
                try {
                    Data.get().scanJavaRuntimes();
                } catch (Exception ex) {
                    Data.get().log("Java 扫描失败：" + Log.describe(ex));
                }
            }, "TCL-Java-Scan");
            javaScan.setDaemon(true);
            javaScan.start();
            if (Shell.windows() && Data.get().getInt("LaunchAdvanceRenderer", 0) > 0) {
                Thread mesa = new Thread(() -> {
                    try {
                        Mesa.ensure();
                    } catch (Exception ignored) {
                    }
                }, "TCL-Mesa-Prefetch");
                mesa.setDaemon(true);
                mesa.start();
            }
        });
        idle.play();

        Http.warmup(
                "https://api.modrinth.com/v2/tag/game_version",
                "https://cdn.modrinth.com/",
                "https://api.curseforge.com/v1/mods/search",
                "https://launchercontent.mojang.com/v2/news.json",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

        Data.get().refreshVersions(false, () -> {
            if (window != null) window.refreshRight();
        });

        Updates.checkOnStartup(window);
        startStartupInstance();
    }

    private void applyInitialTheme() {
        // 先定主题色再建界面，避免启动闪一下默认蓝
        Theme.prepareStartupAccent(java.nio.file.Paths.get(Data.get().gameDir, "TCL", "Pictures"));
    }

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
                window.toast("快捷方式启动失败", Lang.tf("未找到实例「%s」", wanted), 2);
                return;
            }
            final Data.Instance chosen = target;
            // usableJava 起子进程等 5s，勿在 FX 线程调
            Threads.start("TCL-QuickLaunch", () -> {
                boolean ok = Data.usableJava(Data.get().javaPath);
                Platform.runLater(() -> {
                    if (!ok) {
                        window.toast("需要先设置 Java", "请在 设置 → Java 中选择运行时后再启动。", 2);
                        return;
                    }
                    Data data = Data.get();
                    data.selectedInstance = data.instances.indexOf(chosen);
                    data.save();
                    window.openPage("launch", false);
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
            });
        });
        wait.play();
    }

    private void showFatal(Throwable t) {
        Path crash = Boot.writeCrash(t);

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
        fail.setTitle(Lang.t("TCraft Launcher 启动失败"));

        Label title = new Label(Lang.t("启动器没能进入主界面"));
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1f2430;");

        Label msg = new Label(root);
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 12px; -fx-text-fill: #d24a4a;");

        Label stack = new Label(detail.toString().trim());
        stack.setWrapText(true);
        stack.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b6472; -fx-font-family: Consolas, monospace;");

        Label where = new Label(crash == null ? Lang.t("崩溃日志写入失败")
                : Lang.t("完整堆栈已写入：\n") + crash);
        where.setWrapText(true);
        where.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b6472;");

        Label warn = new Label(Lang.t("请不要发送截图。点「导出错误报告」保存日志，再把文件发到 TCL 交流群（1131373915）。"));
        warn.setWrapText(true);
        warn.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #ce2111;");

        String report = "TCraft Launcher 启动失败\n" + root + "\n\n" + detail
                + (crash == null ? "" : "\n日志：" + crash);
        Button copy = new Button(Lang.t("导出错误报告"));
        Button openDir = new Button(Lang.t("打开日志文件夹"));
        Button close = new Button(Lang.t("关闭"));
        copy.setOnAction(e -> {
            try {
                String stamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                java.nio.file.Path dir = crash != null && crash.getParent() != null
                        ? crash.getParent()
                        : java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"));
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path file = dir.resolve("TCraft-启动失败-" + stamp + ".log");
                java.nio.file.Files.writeString(file, "\uFEFF" + Redact.apply(report),
                        java.nio.charset.StandardCharsets.UTF_8);
                Shell.openFolder(dir.toString());
                copy.setText(Lang.t("已导出：") + file.getFileName());
            } catch (Exception ex) {
                copy.setText(Lang.t("导出失败"));
            }
        });
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
        HBox buttons = new HBox(10, copy, openDir, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, title, msg, stack, where, warn, buttons);
        box.setPadding(new javafx.geometry.Insets(18, 20, 16, 20));
        box.setStyle("-fx-background-color: #ffffff;");
        Scene scene = new Scene(box, 560, 420);
        fail.setScene(scene);
        fail.setOnCloseRequest(e -> Platform.exit());
        fail.centerOnScreen();
        fail.show();
        fail.toFront();
    }

    @Override
    public void stop() {
        // FX 线程上同步等 easytier 会卡住退出，交给后台线程收尾
        EasyTier.stopAsync();
    }
}
