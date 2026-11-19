package tcraft;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class Errors {

    public enum Behavior {
        NONE,
        ALERT,
        ALERT_FEEDBACK,
        ALERT_CRASH
    }

    private Errors() {
    }

    private static volatile MainWindow window;
    private static final ArrayDeque<Report> QUEUE = new ArrayDeque<>();
    private static volatile boolean showing;
    private static volatile boolean internalClose;
    private static volatile long lastShownAt;
    private static volatile String lastKey;

    private static final Color WARN_MASK = Theme.warnMask;

    private static final class Report {
        final String title;
        final String headline;
        final String detail;
        final Behavior behavior;
        final boolean warn;
        final boolean feedbackCard;

        Report(String title, String headline, String detail, Behavior behavior,
               boolean warn, boolean feedbackCard) {
            this.title = title;
            this.headline = headline == null ? "" : headline;
            this.detail = detail == null ? "" : detail;
            this.behavior = behavior;
            this.warn = warn;
            this.feedbackCard = feedbackCard;
        }
    }

    public static void attach(MainWindow w) {
        window = w;
    }

    public static void error(String brief, Throwable ex) {
        error(brief, ex, Behavior.ALERT_FEEDBACK);
    }

    public static void error(String brief, Throwable ex, Behavior behavior) {
        if (behavior == Behavior.NONE) {
            logOnly(brief, ex);
            return;
        }
        String head = briefText(brief, ex);
        String detail = detailText(brief, ex);
        enqueue(new Report("错误", head, detail, behavior, true, false));
    }

    public static void alert(String brief, Throwable ex) {
        error(brief, ex, Behavior.ALERT);
    }

    public static void panic(String brief, Throwable ex) {
        String head = briefText(brief, ex);
        String detail = detailText(brief, ex);
        // 标题是拼接串，localize 查不到，两段分别查表
        enqueue(new Report(Lang.t("联机出错：") + (brief == null ? "" : Lang.t(brief)), head, detail,
                Behavior.ALERT, true, false));
    }

    public static void hint(String brief, Throwable ex) {
        logOnly(brief, ex);
        String msg = ex == null ? "" : briefOf(ex);
        MainWindow w = window;
        if (w == null) return;
        onFx(() -> w.toast(brief == null ? "出错了" : brief, msg.isEmpty() ? "未知错误" : msg, 2));
    }

    public static void uncaught(Thread thread, Throwable ex, boolean fxThread) {
        // brief 带着 @线程名，整串查不到表，先译前缀再拼
        String brief = Lang.t(fxThread ? "界面线程未捕获异常" : "后台线程未捕获异常")
                + (thread == null ? "" : " @" + thread.getName());
        if (!fxThread) {
            logOnly(brief, ex);
            return;
        }
        error(brief, ex, Behavior.ALERT_FEEDBACK);
    }

    public static void onModalDismissed() {
        if (internalClose || !showing) return;
        showing = false;
        scheduleDrain();
    }

    private static void enqueue(Report r) {
        // 2 秒内同一错误不重复弹
        String key = r.title + "|" + r.headline + "|" + r.detail;
        long now = System.currentTimeMillis();
        log(r.title, r.headline, r.detail);
        synchronized (QUEUE) {
            if (key.equals(lastKey) && now - lastShownAt < 2000) return;
            lastKey = key;
            lastShownAt = now;
            QUEUE.addLast(r);
        }
        onFx(Errors::drain);
    }

    private static void drain() {
        if (showing) return;
        Report r;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) return;
            r = QUEUE.pollFirst();
        }
        MainWindow w = window;
        if (w == null || w.stage == null || !w.stage.isShowing() || w.modalLayer == null) {
            fallback(r);
            return;
        }
        showing = true;
        try {
            w.showModal(buildCard(r), false, r.warn ? WARN_MASK : null);
        } catch (Throwable t) {
            showing = false;
            logOnly("错误弹窗构建失败", t);
            fallback(r);
        }
    }

    private static void scheduleDrain() {
        try {
            PauseTransition wait = new PauseTransition(Duration.millis(240));
            wait.setOnFinished(e -> drain());
            wait.play();
        } catch (Throwable ignored) {
            synchronized (QUEUE) {
                QUEUE.clear();
            }
        }
    }

    private static void finish(Runnable after) {
        MainWindow w = window;
        if (w != null) {
            internalClose = true;
            try {
                w.closeModal();
            } finally {
                internalClose = false;
            }
        }
        showing = false;
        if (after != null) {
            try {
                after.run();
            } catch (Throwable t) {
                logOnly("错误弹窗回调失败", t);
            }
        }
        scheduleDrain();
    }

    private static VBox buildCard(Report r) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 18, 22));
        card.setPrefWidth(520);
        card.setMinWidth(400);
        card.setBackground(Ui.fill(Theme.dialogBg, 7));
        card.setEffect(Theme.shadow(20, 4, 0.8, Theme.text));

        Color accent = r.warn ? Theme.red : Theme.accentDark;
        Label title = Ui.semi(r.title, 23, accent);
        title.setPadding(new Insets(0, 70, 9, 7));
        Region line = Ui.line(accent, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().addAll(title, line);

        if (!r.feedbackCard) {
            Label head = Ui.label(r.headline, 15, Theme.text);
            head.setWrapText(true);
            head.setMaxWidth(470);
            VBox.setMargin(head, new Insets(14, 7, 8, 7));
            card.getChildren().add(head);

            Label detail = new Label(r.detail);
            detail.setWrapText(true);
            detail.setFont(Font.font("Monospaced", 11));
            detail.setTextFill(Theme.gray2);
            detail.setMaxWidth(468);
            ScrollPane scroll = Ui.scroll(detail);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(170);
            scroll.setMaxHeight(230);
            VBox.setMargin(scroll, new Insets(0, 7, 10, 7));
            card.getChildren().add(scroll);
        } else {
            Label body = Ui.label(r.headline, 15, Theme.text);
            body.setWrapText(true);
            body.setMaxWidth(470);
            VBox.setMargin(body, new Insets(15, 7, 16, 7));
            card.getChildren().add(body);
        }

        if (r.behavior == Behavior.ALERT_FEEDBACK || r.behavior == Behavior.ALERT_CRASH) {
            Label ask = Ui.label("是否反馈此问题？如果不反馈，这个问题可能永远无法得到解决！",
                    13, Theme.gray2);
            ask.setWrapText(true);
            ask.setMaxWidth(470);
            VBox.setMargin(ask, new Insets(0, 7, 8, 7));
            card.getChildren().add(ask);
        }

        Label copyWarn = Ui.label("请不要发送截图。点「导出日志」保存错误报告，再把文件发到 TCL 交流群（1131373915）。",
                12, Theme.red);
        copyWarn.setWrapText(true);
        copyWarn.setMaxWidth(470);
        VBox.setMargin(copyWarn, new Insets(0, 7, 12, 7));
        card.getChildren().add(copyWarn);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = Ui.spacer();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().add(spacer);

        if (r.feedbackCard) {
            actions.getChildren().add(Ui.button("确定", r.warn ? Ui.BTN_RED : Ui.BTN_HIGHLIGHT,
                    () -> finish(null)));
        } else if (r.behavior == Behavior.ALERT_FEEDBACK || r.behavior == Behavior.ALERT_CRASH) {
            actions.getChildren().addAll(
                    Ui.button("导出日志", Ui.BTN_NORMAL, () -> exportAndToast(r)),
                    Ui.button("反馈", Ui.BTN_RED, () -> finish(() -> doFeedback(r))),
                    Ui.button(r.behavior == Behavior.ALERT_CRASH ? "退出启动器" : "取消",
                            Ui.BTN_NORMAL, () -> finish(() -> {
                                if (r.behavior == Behavior.ALERT_CRASH) Platform.exit();
                            })));
        } else {
            actions.getChildren().addAll(
                    Ui.button("导出日志", Ui.BTN_NORMAL, () -> exportAndToast(r)),
                    Ui.button("确定", Ui.BTN_RED, () -> finish(null)));
        }
        card.getChildren().add(actions);
        return card;
    }

    private static void exportAndToast(Report r) {
        MainWindow w = window;
        if (w == null) return;
        onFx(() -> w.toast(Lang.t("正在导出日志"), Lang.t("导出完成后会自动打开所在文件夹"), 1));
        // 完整日志可能较大，读取与写盘放到后台
        Thread worker = new Thread(() -> {
            Path file = exportReport(r);
            onFx(() -> {
                if (file == null) {
                    w.toast("导出失败", "无法写入错误报告，请改用「设置 → 日志」导出。", 3);
                } else {
                    w.toast("错误报告已导出",
                            file.getFileName() + Lang.t(" · 请把文件发到 TCL 交流群（1131373915）"), 2);
                }
            });
        }, "TCL-ExportReport");
        worker.setDaemon(true);
        worker.start();
    }

    private static Path exportReport(Report r) {
        Path file = null;
        try {
            Path dir = Resources.home().resolve("feedback");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            file = dir.resolve("TCraft-错误报告-" + stamp + ".log");
            // 带 BOM，记事本和 QQ 里直接看不会乱码
            Files.writeString(file, "\uFEFF" + exportText(r), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            logOnly("导出错误报告失败", t);
            return null;
        }
        try {
            Shell.openFolder(file.getParent().toString());
        } catch (Throwable ignored) {
        }
        return file;
    }

    private static String exportText(Report r) {
        return exportText(r.title, r.headline, r.detail);
    }

    static String exportText(String title, String headline, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("TCraft Launcher ").append(Build.VERSION)
                .append("（").append(Build.EDITION).append("）错误报告\n");
        sb.append("时间：").append(LocalDateTime.now().withNano(0)).append('\n');
        sb.append("\n===== 报错 =====\n").append(title).append('\n').append(headline).append('\n');
        if (detail != null && !detail.isEmpty()) {
            sb.append("\n===== 详细信息 =====\n").append(detail).append('\n');
        }
        sb.append("\n===== 诊断信息 =====\n").append(systemInfo()).append('\n');
        appendHistory(sb);
        return sanitize(sb.toString());
    }

    // 导出完整历史：错误记录 + 运行日志，不再只给最后几百行
    private static void appendHistory(StringBuilder sb) {
        String errors = cap(Log.exportErrors(), "错误记录");
        String runtime = cap(Log.exportCurrent(), "运行日志");
        if (!errors.isEmpty()) {
            sb.append("\n===== 错误记录（完整）=====\n").append(errors).append('\n');
        }
        if (!runtime.isEmpty()) {
            sb.append("\n===== 运行日志（完整）=====\n").append(runtime).append('\n');
        }
        if (!errors.isEmpty() || !runtime.isEmpty()) return;
        try {
            // 日志文件不可用时退回内存
            List<String> logs = new ArrayList<>();
            for (String line : Data.get().log) logs.add(line);
            int from = Math.max(0, logs.size() - 200);
            sb.append("\n===== 最近日志（最后 ").append(logs.size() - from).append(" 行）=====\n");
            for (int i = from; i < logs.size(); i++) sb.append(logs.get(i)).append('\n');
        } catch (Throwable ignored) {
        }
    }

    // 反馈文件不宜过大，单段超过 4MB 截断并注明
    private static String cap(String text, String label) {
        int max = 4 * 1024 * 1024;
        if (text == null || text.length() <= max) return text == null ? "" : text;
        return text.substring(0, max) + "\n… " + label + "超过 4 MB，其余部分已省略\n";
    }

    private static void doFeedback(Report r) {
        Thread worker = new Thread(() -> {
            feedbackInfo();
            Path file = exportReport(r);
            String text = file == null
                    ? "错误报告导出失败，可在 设置 → 日志 中手动导出，再把文件发到 TCL 交流群（1131373915）。"
                    : "错误报告已导出，所在文件夹已自动打开。请把 .log 文件发到 TCraft Launcher 启动器交流群（群号 1131373915），不要只发截图。";
            enqueue(new Report("反馈", text, "", Behavior.ALERT, true, true));
        }, "TCL-Feedback");
        worker.setDaemon(true);
        worker.start();
    }

    private static String systemInfo() {
        StringBuilder sb = new StringBuilder("诊断信息：\n");
        try {
            sb.append("操作系统：").append(System.getProperty("os.name")).append(' ')
                    .append(System.getProperty("os.version")).append(" (")
                    .append(System.getProperty("os.arch")).append(")\n");
            sb.append("Java：").append(System.getProperty("java.version")).append(" / ")
                    .append(System.getProperty("java.vendor")).append('\n');
            long max = Runtime.getRuntime().maxMemory() / 1024 / 1024;
            long free = Runtime.getRuntime().freeMemory() / 1024 / 1024;
            sb.append("剩余内存：").append(free).append(" M / ").append(max).append(" M\n");
            sb.append("MC 文件夹：").append(sanitize(String.valueOf(Data.get().gameDir))).append('\n');
            sb.append("文件位置：").append(sanitize(String.valueOf(Theme.root())));
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static void feedbackInfo() {
        try {
            Data.get().log(systemInfo());
        } catch (Throwable ignored) {
        }
    }

    private static void fallback(Report r) {
        try {
            onFx(() -> {
                try {
                    showFallbackStage(r);
                } catch (Throwable t) {
                    // 构建失败也要放行队列，后续错误不能被卡死
                    showing = false;
                    logOnly("错误弹窗降级失败", t);
                    scheduleDrain();
                }
            });
        } catch (Throwable t) {
            // 没有图形环境，错误已落盘
            showing = false;
            logOnly("无图形环境，错误弹窗降级失败", t);
            synchronized (QUEUE) {
                QUEUE.clear();
            }
        }
    }

    private static void showFallbackStage(Report r) {
        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle(Lang.t(r.title));

        Label head = new Label(r.headline);
        head.setWrapText(true);
        head.setStyle("-fx-font-size: 13px; -fx-text-fill: #1f2430;");

        String detail = r.detail.length() > 1600 ? r.detail.substring(0, 1600) + "…" : r.detail;
        Label body = new Label(detail);
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b6472; -fx-font-family: Consolas, monospace;");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label warn = new Label(Lang.t("请不要发送截图。点「导出日志」保存错误报告，再把文件发到 TCL 交流群（1131373915）。"));
        warn.setWrapText(true);
        warn.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #ce2111;");

        Button export = new Button(Lang.t("导出日志"));
        Button close = new Button(Lang.t("关闭"));
        Runnable done = () -> {
            showing = false;
            scheduleDrain();
        };
        export.setOnAction(e -> {
            Path file = exportReport(r);
            if (file != null) {
                export.setText(Lang.t("已导出：") + file.getFileName());
            } else {
                try {
                    ClipboardContent c = new ClipboardContent();
                    c.put(DataFormat.PLAIN_TEXT, sanitize(exportText(r)));
                    Clipboard.getSystemClipboard().setContent(c);
                } catch (Throwable ignored) {
                }
                export.setText(Lang.t("导出失败"));
            }
        });
        close.setOnAction(e -> {
            stage.close();
            done.run();
        });
        stage.setOnCloseRequest(e -> done.run());

        HBox actions = new HBox(10, export, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, head, scroll, warn, actions);
        box.setPadding(new Insets(18, 20, 16, 20));
        box.setStyle("-fx-background-color: #ffffff;");
        Scene scene = new Scene(box, 560, 420);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        stage.toFront();
    }

    public static String briefOf(Throwable ex) {
        if (ex == null) return Lang.t("无可用错误信息！");
        List<String> lines = new ArrayList<>();
        for (Throwable cur = ex; cur != null && lines.size() < 8; cur = cur.getCause()) {
            String m = cur.getMessage();
            if (m != null && !m.isEmpty() && !lines.contains(m)) lines.add(m.replace("\r", " ").replace("\n", " "));
        }
        if (lines.isEmpty()) lines.add(ex.getClass().getSimpleName());
        java.util.Collections.reverse(lines);
        String reason = commonReason(ex);
        String joined = String.join(" ← ", lines);
        return reason == null ? joined : reason + "（" + lines.get(lines.size() - 1) + "）";
    }

    public static String detailOf(Throwable ex) {
        if (ex == null) return "无可用错误信息！";
        StringBuilder sb = new StringBuilder();
        boolean inner = false;
        int depth = 0;
        for (Throwable cur = ex; cur != null && depth < 8; cur = cur.getCause(), depth++) {
            sb.append(inner ? "由 " : "").append(cur.getClass().getName()).append("：")
                    .append(cur.getMessage() == null ? "(无消息)" : cur.getMessage()).append('\n');
            StackTraceElement[] st = cur.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 24); i++) sb.append("    ").append(st[i]).append('\n');
            if (st.length > 24) sb.append("    … 其余 ").append(st.length - 24).append(" 帧省略\n");
            sb.append("   错误类型：").append(cur.getClass().getName()).append('\n');
            inner = true;
        }
        String reason = commonReason(ex);
        String detail = sanitize(sb.toString());
        if (reason == null) return detail;
        return reason + "\n\n详细错误信息：\n" + detail;
    }

    private static String commonReason(Throwable ex) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable c = ex; c != null && chain.size() < 8; c = c.getCause()) chain.add(c);
        for (Throwable c : chain) {
            String cls = c.getClass().getName();
            if (c instanceof OutOfMemoryError) return Lang.t("系统的运行内存不足。请在关闭一部分不需要的程序后再试。");
            if (c instanceof ExceptionInInitializerError || c instanceof NoClassDefFoundError
                    || c instanceof UnsupportedClassVersionError || c instanceof NoSuchMethodError
                    || cls.endsWith("TypeInitializationException"))
                return Lang.t("运行环境存在问题，可能是 Java 版本过旧或程序文件不完整。请尝试重新安装最新 Java 与完整版启动器后再试。");
            if (c instanceof SecurityException || c instanceof java.nio.file.AccessDeniedException
                    || cls.contains("UnauthorizedAccess"))
                return Lang.t("程序权限不足。请尝试右键程序，选择以管理员身份运行，或将文件移动到其他文件夹。");
        }
        for (Throwable c : chain) {
            if (c instanceof java.net.SocketTimeoutException || c instanceof java.net.UnknownHostException
                    || c instanceof java.net.ConnectException || c instanceof javax.net.ssl.SSLException
                    || c instanceof java.util.concurrent.TimeoutException
                    || (c instanceof java.io.IOException && c.getMessage() != null
                    && (c.getMessage().contains("timed out") || c.getMessage().contains("connect"))))
                return Lang.t("你的网络环境不佳，请稍后再试，或使用 VPN 改善网络环境。");
        }
        return null;
    }

    private static String briefText(String brief, Throwable ex) {
        // headline 是拼接串，brief/分隔符分别翻译；异常原文不译
        String b = brief == null ? Lang.t("发生错误") : Lang.t(brief);
        String m = briefOf(ex);
        return sanitize(b + Lang.t("：") + m);
    }

    private static String detailText(String brief, Throwable ex) {
        String b = brief == null ? "" : Lang.t(brief) + Lang.t("：");
        return sanitize(b + detailOf(ex));
    }

    static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = Redact.apply(s);
        String home = System.getProperty("user.home");
        if (home != null && !home.isEmpty()) out = out.replace(home, "<HOME>");
        return out;
    }

    private static void logOnly(String brief, Throwable ex) {
        log("错误", brief == null ? "发生错误" : brief, detailOf(ex));
    }

    private static void log(String title, String headline, String detail) {
        String entry = sanitize("[" + LocalDateTime.now().withNano(0) + "] " + title + " — " + headline
                + " @ " + Thread.currentThread().getName());
        try {
            // 内存兜底；错误进 errors.log 与 launcher.log，均带完整堆栈
            Data.get().log.add(entry);
            if (Data.get().getBool("SystemDebugMode", false)) System.out.println("[TCL] " + entry);
        } catch (Throwable ignored) {
        }
        Log.error(detail == null || detail.isEmpty() ? entry : entry + "\n" + detail);
    }

    private static void onFx(Runnable r) {
        try {
            if (Platform.isFxApplicationThread()) r.run();
            else Platform.runLater(r);
        } catch (Throwable t) {
            // JavaFX 没初始化或已关闭，直接跑
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
