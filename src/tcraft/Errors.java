package tcraft;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 统一错误反馈：UI 线程弹窗，后台线程只写日志。 */
public final class Errors {

    /** 反馈档位。 */
    public enum Behavior {
        /** 只写日志不弹窗，后台线程异常走这儿。 */
        NONE,
        /** 只弹一次「错误」窗，游戏启动失败这类。 */
        ALERT,
        /** 弹错误窗并询问是否反馈。 */
        ALERT_FEEDBACK,
        /** 严重错误：问完反馈就关启动器。 */
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

    /** 警告遮罩色，统一取 Theme.warnMask。 */
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

    /** 主窗口建好后注册，用来承载模态层。 */
    public static void attach(MainWindow w) {
        window = w;
    }

    public static void detach() {
        window = null;
        synchronized (QUEUE) {
            QUEUE.clear();
        }
        showing = false;
    }

    /** 默认档，出错并询问是否反馈。 */
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

    /** 只弹错误窗，不询问反馈。 */
    public static void alert(String brief, Throwable ex) {
        error(brief, ex, Behavior.ALERT);
    }

    /** 联机模块专用，标题带「联机出错：」。 */
    public static void panic(String brief, Throwable ex) {
        String head = briefText(brief, ex);
        String detail = detailText(brief, ex);
        enqueue(new Report("联机出错：" + (brief == null ? "" : brief), head, detail,
                Behavior.ALERT, true, false));
    }

    /** 轻微错误只弹个提示，不打断用户。 */
    public static void hint(String brief, Throwable ex) {
        logOnly(brief, ex);
        String msg = ex == null ? "" : briefOf(ex);
        MainWindow w = window;
        if (w == null) return;
        onFx(() -> w.toast(brief == null ? "出错了" : brief, msg.isEmpty() ? "未知错误" : msg, 2));
    }

    /** 未捕获异常入口，UI 线程弹窗，后台线程只记日志。 */
    public static void uncaught(Thread thread, Throwable ex, boolean fxThread) {
        String brief = (fxThread ? "界面线程未捕获异常" : "后台线程未捕获异常")
                + (thread == null ? "" : " @" + thread.getName());
        if (!fxThread) {
            logOnly(brief, ex);
            return;
        }
        // 标记已处理，不退出
        error(brief, ex, Behavior.ALERT_FEEDBACK);
    }

    /** 弹窗被外部关闭时推进队列。 */
    public static void onModalDismissed() {
        if (internalClose || !showing) return;
        showing = false;
        scheduleDrain();
    }

    private static void enqueue(Report r) {
        // 2 秒内同一错误不重复弹。
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
            // 图形环境关了，剩下的只记日志
            synchronized (QUEUE) {
                QUEUE.clear();
            }
        }
    }

    /** 关旧弹窗、执行动作、推进队列。 */
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

            // 正文放滚动容器，长堆栈直接滚。
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
            VBox.setMargin(ask, new Insets(0, 7, 12, 7));
            card.getChildren().add(ask);
        }

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = Ui.spacer();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().add(spacer);

        if (r.feedbackCard) {
            actions.getChildren().add(Ui.button("确定", r.warn ? Ui.BTN_RED : Ui.BTN_HIGHLIGHT,
                    () -> finish(null)));
        } else if (r.behavior == Behavior.ALERT_FEEDBACK || r.behavior == Behavior.ALERT_CRASH) {
            // 主按钮在左，次按钮在右
            String detail = r.detail;
            actions.getChildren().addAll(
                    Ui.button("反馈", Ui.BTN_RED, () -> finish(() -> doFeedback(detail))),
                    Ui.button(r.behavior == Behavior.ALERT_CRASH ? "退出启动器" : "取消",
                            Ui.BTN_NORMAL, () -> finish(() -> {
                                if (r.behavior == Behavior.ALERT_CRASH) Platform.exit();
                            })));
        } else {
            actions.getChildren().add(Ui.button("确定", Ui.BTN_RED, () -> finish(null)));
        }
        card.getChildren().add(actions);
        return card;
    }

    /** 写诊断信息、复制详情、弹「反馈」窗。 */
    private static void doFeedback(String detail) {
        feedbackInfo();
        copy(detail);
        MainWindow w = window;
        if (w != null) {
            onFx(() -> w.toast("错误详情已复制", "可直接粘贴到反馈群或发给开发者。", 1));
        }
        String text = "请加入 TCraft Launcher 启动器交流群（群号 1131373915）提交问题。\n"
                + "错误信息已复制到剪贴板，可直接粘贴到群内。\n"
                + "你也可以在 设置 → 日志 中查看并复制错误信息。";
        enqueue(new Report("反馈", text, "", Behavior.ALERT, true, true));
    }

    /** 把系统诊断信息写进日志，不弹窗。 */
    private static void feedbackInfo() {
        try {
            StringBuilder sb = new StringBuilder("诊断信息：\n");
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
            Data.get().log(sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void copy(String text) {
        try {
            onFx(() -> {
                try {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(sanitize(text));
                    Clipboard.getSystemClipboard().setContent(content);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** 主窗口还没加载时降级成系统原生对话框。 */
    private static void fallback(Report r) {
        try {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    String text = r.headline + "\n\n"
                            + (r.detail.length() > 1200 ? r.detail.substring(0, 1200) + "…" : r.detail);
                    int option = (r.behavior == Behavior.ALERT_FEEDBACK || r.behavior == Behavior.ALERT_CRASH)
                            ? javax.swing.JOptionPane.YES_NO_OPTION
                            : javax.swing.JOptionPane.OK_OPTION;
                    int res = javax.swing.JOptionPane.showConfirmDialog(null, text, r.title,
                            option, javax.swing.JOptionPane.ERROR_MESSAGE);
                    if (res == javax.swing.JOptionPane.YES_OPTION) {
                        try {
                            java.awt.datatransfer.StringSelection sel =
                                    new java.awt.datatransfer.StringSelection(sanitize(r.detail));
                            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                                    .setContents(sel, sel);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    showing = false;
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

    /** 把内层异常串成一行。 */
    public static String briefOf(Throwable ex) {
        if (ex == null) return "无可用错误信息！";
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

    /** 白话原因加完整堆栈。 */
    public static String detailOf(Throwable ex) {
        if (ex == null) return "无可用错误信息！";
        StringBuilder sb = new StringBuilder();
        boolean inner = false;
        int depth = 0;
        for (Throwable cur = ex; cur != null && depth < 8; cur = cur.getCause(), depth++) {
            sb.append(inner ? "→ " : "").append(cur.getClass().getName()).append("：")
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
        return reason + "\n\n——————— 详细错误信息 ———————\n" + detail;
    }

    /** 常见错误原因分析，按 Java 异常类型映射。 */
    private static String commonReason(Throwable ex) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable c = ex; c != null && chain.size() < 8; c = c.getCause()) chain.add(c);
        for (Throwable c : chain) {
            String cls = c.getClass().getName();
            if (c instanceof OutOfMemoryError) return "系统的运行内存不足。请在关闭一部分不需要的程序后再试。";
            if (c instanceof ExceptionInInitializerError || c instanceof NoClassDefFoundError
                    || c instanceof UnsupportedClassVersionError || c instanceof NoSuchMethodError
                    || cls.endsWith("TypeInitializationException"))
                return "运行环境存在问题，可能是 Java 版本过旧或程序文件不完整。请尝试重新安装最新 Java 与完整版启动器后再试。";
            if (c instanceof SecurityException || c instanceof java.nio.file.AccessDeniedException
                    || cls.contains("UnauthorizedAccess"))
                return "程序权限不足。请尝试右键程序，选择以管理员身份运行，或将文件移动到其他文件夹。";
        }
        for (Throwable c : chain) {
            if (c instanceof java.net.SocketTimeoutException || c instanceof java.net.UnknownHostException
                    || c instanceof java.net.ConnectException || c instanceof javax.net.ssl.SSLException
                    || c instanceof java.util.concurrent.TimeoutException
                    || (c instanceof java.io.IOException && c.getMessage() != null
                    && (c.getMessage().contains("timed out") || c.getMessage().contains("connect"))))
                return "你的网络环境不佳，请稍后再试，或使用 VPN 改善网络环境。";
        }
        return null;
    }

    private static String briefText(String brief, Throwable ex) {
        String b = brief == null ? "发生错误" : brief;
        String m = briefOf(ex);
        return sanitize(b + "：" + m);
    }

    private static String detailText(String brief, Throwable ex) {
        String b = brief == null ? "" : brief + "：";
        return sanitize(b + detailOf(ex));
    }

    /** 令牌和本机用户名不能进日志或剪贴板。 */
    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = s;
        String home = System.getProperty("user.home");
        if (home != null && !home.isEmpty()) out = out.replace(home, "<HOME>");
        out = out.replaceAll("(?i)(access[_-]?token[=:]{1,3})[A-Za-z0-9._-]{12,}", "$1<TOKEN>");
        out = out.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._-]{12,}", "$1<TOKEN>");
        return out;
    }

    private static void logOnly(String brief, Throwable ex) {
        log("错误", brief == null ? "发生错误" : brief, detailOf(ex));
    }

    private static void log(String title, String headline, String detail) {
        try {
            Data.get().log("[" + title + "] " + sanitize(headline));
        } catch (Throwable ignored) {
        }
        try {
            Path dir = Resources.home();
            Files.createDirectories(dir);
            String entry = "[" + LocalDateTime.now() + "] " + title + " — " + headline
                    + "\n" + detail + "\n\n";
            Files.writeString(dir.resolve("errors.log"), sanitize(entry), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // crash.txt 单独留一份堆栈。
            Files.writeString(dir.resolve("crash.txt"),
                    "[" + LocalDateTime.now() + "] " + title + " — " + headline + "\n" + detail + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
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
