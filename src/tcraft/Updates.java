package tcraft;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Updates {

    public static final String RELEASES_URL =
            "https://github.com/Damianjiang/TCraft-Launcher/releases";

    private static final String ATOM_URL = RELEASES_URL + ".atom";
    private static final String API_URL =
            "https://api.github.com/repos/Damianjiang/TCraft-Launcher/releases/latest";

    private static volatile boolean checking;

    private Updates() {
    }

    public static void checkOnStartup(MainWindow win) {
        if (!Data.get().getBool("CheckUpdateOnStartup", true)) return;
        if (!Data.get().getBool("ToolUpdateRelease", true)
                && !Data.get().getBool("ToolUpdateSnapshot", false)) return;
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> check(win, false));
        delay.play();
    }

    private static final java.util.concurrent.atomic.AtomicLong GENERATION =
            new java.util.concurrent.atomic.AtomicLong();

    private static volatile Release pendingRelease;
    private static volatile Exception pendingError;
    private static volatile long pendingGen;
    private static Timeline poller;

    private static final java.util.concurrent.ScheduledExecutorService TIMEOUT =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TCL-Update-Timeout");
                t.setDaemon(true);
                return t;
            });

    public static void check(MainWindow win, boolean manual) {
        check(win, manual, null);
    }

    public static void check(MainWindow win, boolean manual, Label hint) {
        if (!manual && !windowAlive(win)) return;
        if (checking && !manual) return;
        long generation = GENERATION.incrementAndGet();
        checking = true;
        pendingRelease = null;
        pendingError = null;
        pendingGen = 0;
        if (manual) win.toast("正在检查更新", "正在读取 GitHub Releases…", 0);
        if (hint != null) hint.setText(Lang.t("正在检查…"));

        Thread thread = new Thread(() -> {
            Release release = null;
            Exception error = null;
            try {
                release = fetchLatest();
            } catch (Exception e) {
                error = e;
            }
            if (generation != GENERATION.get()) return;
            pendingRelease = release;
            pendingError = error;
            pendingGen = generation;
        }, "TCL-Update");
        thread.setDaemon(true);
        thread.start();

        // 超时只在后台打标记，不碰界面
        TIMEOUT.schedule(() -> {
            if (generation != GENERATION.get() || !checking) return;
            if (pendingGen == generation) return;
            pendingError = new IOException("连接超时");
            pendingGen = generation;
            Data.get().log("检查更新超时：12 秒没有返回");
        }, 12, java.util.concurrent.TimeUnit.SECONDS);

        // FX 线程轮询取结果，不依赖 runLater 回调
        stopPoller();
        poller = new Timeline(new KeyFrame(Duration.millis(120), e -> poll(generation, win, manual, hint)));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
    }

    private static void poll(long generation, MainWindow win, boolean manual, Label hint) {
        if (generation != GENERATION.get()) {
            stopPoller();
            return;
        }
        if (pendingGen != generation) return;
        stopPoller();
        Release release = pendingRelease;
        Exception error = pendingError;
        pendingRelease = null;
        pendingError = null;
        pendingGen = 0;
        checking = false;
        try {
            if (!windowAlive(win)) return;
            if (release == null) {
                String why = reason(error);
                Data.get().log("检查更新失败：" + (error == null ? "未知错误" : error));
                if (hint != null) hint.setText(Lang.tf("检查失败：%s", why));
                if (manual) {
                    win.toast("检查更新失败", why, 3);
                    showFailure(win, why);
                }
                return;
            }
            if (hint != null) hint.setText(Lang.tf("GitHub 最新版本 v%s", clean(release.tag)));
            if (manual) win.toast("检查更新", Lang.tf("GitHub 最新版本 v%s", clean(release.tag)), 1);
            handle(win, release, manual);
        } catch (Throwable t) {
            Data.get().log("更新提示失败：" + t);
            try {
                win.toast("检查更新", "读取成功但提示失败，请到 Releases 页面查看", 2);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void stopPoller() {
        if (poller != null) {
            poller.stop();
            poller = null;
        }
    }

    private static void showFailure(MainWindow win, String message) {
        VBox card = Dialogs.msgCard("无法检查更新");
        card.setPrefWidth(440);
        card.setMinWidth(400);
        VBox body = new VBox(8);
        body.setPadding(new Insets(14, 7, 0, 7));
        Label text = Ui.label(message, 13, Theme.text);
        text.setWrapText(true);
        text.setMaxWidth(390);
        body.getChildren().add(text);

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(15, 7, 0, 7));
        actions.getChildren().addAll(
                Ui.button("打开下载页", Ui.BTN_NORMAL, () -> {
                    win.closeModal();
                    if (!Shell.openUrl(RELEASES_URL)) win.toast("打开链接失败", RELEASES_URL, 3);
                }),
                Ui.button("确定", Ui.BTN_HIGHLIGHT, win::closeModal));
        card.getChildren().addAll(body, actions);
        win.showModal(card, true);
    }

    private static String reason(Exception e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage();
        String low = msg.toLowerCase(java.util.Locale.ROOT);
        if (msg.contains("PKIX") || low.contains("certificate")) {
            return Lang.t("证书校验失败，可能是代理或网络工具拦截了 GitHub，关掉代理再试。");
        }
        if (msg.contains("超时") || low.contains("timed out") || low.contains("timeout")) {
            return Lang.t("连接 GitHub 超时，请检查网络后再试。");
        }
        return Lang.t("GitHub 可能连接不稳定，你可以使用瓦特工具箱等工具开个加速或来个 VPN。");
    }

    private static boolean windowAlive(MainWindow win) {
        return win != null && win.stage != null && win.stage.isShowing();
    }

    private static void handle(MainWindow win, Release release, boolean manual) {
        String current = Build.VERSION == null ? "" : Build.VERSION.trim();
        String latest = release.tag == null ? "" : release.tag.trim();
        if (latest.isEmpty()) {
            if (manual) showFailure(win, "GitHub 没有返回可用的版本号。");
            return;
        }
        if (!isComparable(current)) {
            // 不可比版本只记日志，弹窗留给手动检查
            if (manual) {
                showUpdate(win, release);
            } else {
                Data.get().log("当前版本号 \"" + current + "\" 无法比较，跳过自动更新提示");
            }
            return;
        }
        int compared = compare(latest, current);
        if (compared <= 0) {
            if (manual) {
                String message = compared == 0
                        ? Lang.tf("当前版本 v%s，GitHub 最新版本 v%s。", clean(current), clean(latest))
                        : Lang.tf("当前版本 v%s 比 GitHub 上的 v%s 更新。", clean(current), clean(latest));
                Dialogs.message(win, "已是最新版本", message);
            }
            return;
        }
        showUpdate(win, release);
    }

    private static void showUpdate(MainWindow win, Release release) {
        VBox card = Dialogs.msgCard("发现更新");
        card.setPrefWidth(520);
        card.setMinWidth(480);

        VBox body = new VBox(8);
        body.setPadding(new Insets(14, 7, 0, 7));
        Label latest = Ui.bold(Lang.tf("最新版本：v%s", clean(release.tag)), 14, Theme.accentDark);
        Label now = Ui.label(Lang.tf("当前版本：v%s", clean(Build.VERSION)), 13, Theme.text);
        Label logTitle = Ui.semi("更新日志", 12.5, Theme.text);
        Label log = Ui.label(release.changelog == null || release.changelog.isEmpty()
                ? "该版本未填写更新日志。" : release.changelog, 12.5, Theme.gray1);
        log.setWrapText(true);
        log.setMaxWidth(470);
        VBox logContent = new VBox(log);
        logContent.setPadding(new Insets(7, 9, 7, 9));
        logContent.setBackground(Ui.fill(Theme.fieldBg, 6));
        logContent.setBorder(Ui.border(Theme.gray6, 6, 1));
        // 小框滚轮 0.7×全局，对齐 WPF DeltaMult
        ScrollPane logScroll = Ui.scroll(logContent, 2.1);
        logScroll.setPrefViewportHeight(190);
        logScroll.setMinHeight(110);
        logScroll.setMaxHeight(220);
        VBox.setMargin(logScroll, new Insets(3, 0, 0, 0));

        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(15, 7, 0, 7));
        actions.getChildren().addAll(
                Ui.button("前往下载", Ui.BTN_HIGHLIGHT, () -> {
                    win.closeModal();
                    if (!Shell.openUrl(RELEASES_URL)) {
                        win.toast("打开链接失败", RELEASES_URL, 3);
                    }
                }),
                Ui.button("稍后", Ui.BTN_NORMAL, win::closeModal));

        body.getChildren().addAll(latest, now, logTitle, logScroll);
        card.getChildren().addAll(body, actions);
        win.showModal(card, true);
    }

    private static Release fetchLatest() throws Exception {
        Exception atomError = null;
        try {
            return fetchAtom();
        } catch (Exception e) {
            atomError = e;
        }
        try {
            return fetchApi();
        } catch (Exception e) {
            e.addSuppressed(atomError);
            atomError = e;
        }
        try {
            return fetchPage();
        } catch (Exception e) {
            e.addSuppressed(atomError);
            throw e;
        }
    }

    private static Release fetchAtom() throws Exception {
        return parseAtom(fetch(ATOM_URL, "application/atom+xml,application/xml,text/xml"));
    }

    static Release parseAtom(String xml) throws Exception {
        if (xml != null && !xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1); // 有的源带 BOM，String 解析会报 prolog 错误
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList entries = doc.getElementsByTagName("entry");
        if (entries.getLength() == 0) throw new IOException("Atom 中没有 release");
        Element entry = null;
        for (int i = 0; i < entries.getLength(); i++) {
            Element candidate = (Element) entries.item(i);
            String tag = tagOf(candidate);
            if (tag.isEmpty()) continue;
            if (tagAllowed(tag)) {
                entry = candidate;
                break;
            }
        }
        // 无符合条目即报真实原因，不拿首条兜底
        if (entry == null) {
            throw new IOException("GitHub Releases 中没有符合测试版开关设置的版本");
        }

        String tag = tagOf(entry);
        if (tag.isEmpty()) throw new IOException("Atom 中没有 tag");

        String html = textOf(entry, "content");
        return new Release(tag, htmlToText(html));
    }

    private static boolean tagAllowed(String tag) {
        boolean snapshot = isSnapshotTag(tag);
        return snapshot ? Data.get().getBool("ToolUpdateSnapshot", false)
                : Data.get().getBool("ToolUpdateRelease", true);
    }

    private static boolean isSnapshotTag(String tag) {
        String t = tag.toLowerCase(java.util.Locale.ROOT);
        return t.contains("snapshot") || t.contains("beta") || t.contains("alpha")
                || t.contains("rc") || t.contains("dev");
    }

    private static String tagOf(Element entry) {
        String tag = null;
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            if (!rel.isEmpty() && !"alternate".equals(rel)) continue;
            tag = tagFromUrl(link.getAttribute("href"));
            if (tag != null && !tag.isEmpty()) break;
        }
        if (tag == null || tag.isEmpty()) tag = textOf(entry, "title").trim();
        return tag == null ? "" : tag;
    }

    @SuppressWarnings("unchecked")
    private static Release fetchApi() throws Exception {
        String json = fetch(API_URL, "application/vnd.github+json");
        Map<String, Object> obj = Json.obj(Json.parse(json));
        String tag = Json.str(obj, "tag_name", "").trim();
        if (tag.isEmpty()) throw new IOException("API 中没有 tag");
        // latest 只给一个 tag，不满足即跳源
        if (!tagAllowed(tag)) {
            throw new IOException("API 返回的 " + tag + " 不在当前测试版开关允许范围内");
        }
        String body = Json.str(obj, "body", "");
        return new Release(tag, body == null ? "" : body.trim());
    }

    private static Release fetchPage() throws Exception {
        String html = fetch(RELEASES_URL, "text/html");
        Matcher matcher = Pattern.compile("/releases/tag/([^\"'/?#]+)").matcher(html);
        boolean any = false;
        while (matcher.find()) {
            String tag = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
            if (tag.isEmpty()) continue;
            any = true;
            if (tagAllowed(tag)) return new Release(tag, "");
        }
        if (!any) throw new IOException("releases 页面中没有 tag");
        throw new IOException("releases 页面只有测试版，且设置未允许提示测试版");
    }

    private static String fetch(String url, String accept) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(6))
                .header("User-Agent", "TCraftLauncher-JavaFX/" + Build.VERSION)
                .header("Accept", accept)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            // orTimeout 自带定时器，DNS 卡住也能断
            response = Http.client().sendAsync(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .orTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                throw new IOException("连接超时", cause);
            }
            if (cause instanceof IOException io) throw io;
            throw new IOException(cause == null || cause.getMessage() == null
                    ? "请求失败" : cause.getMessage(), cause);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String textOf(Element element, String name) {
        NodeList nodes = element.getElementsByTagName(name);
        if (nodes.getLength() == 0) return "";
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text;
    }

    private static String tagFromUrl(String href) {
        if (href == null || href.isEmpty()) return null;
        int index = href.indexOf("/releases/tag/");
        if (index < 0) return null;
        String tag = href.substring(index + "/releases/tag/".length());
        int cut = tag.indexOf('?');
        if (cut >= 0) tag = tag.substring(0, cut);
        cut = tag.indexOf('#');
        if (cut >= 0) tag = tag.substring(0, cut);
        return URLDecoder.decode(tag, StandardCharsets.UTF_8);
    }

    private static String htmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        String s = html;
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|h[1-6]|ul|ol|tr|blockquote)>", "\n");
        s = s.replaceAll("(?i)<li[^>]*>", "• ");
        s = s.replaceAll("(?i)</li>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (s.length() > 20000) s = s.substring(0, 20000) + "\n…";
        return s;
    }

    private static String clean(String version) {
        String value = normalize(version);
        return value.isEmpty() ? String.valueOf(version) : value;
    }

    private static String normalize(String version) {
        if (version == null) return "";
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        return value.trim();
    }

    private static boolean isComparable(String version) {
        String value = normalize(version);
        return value.matches("\\d+(\\.\\d+)*([._+-].*)?");
    }

    private static int compare(String latest, String current) {
        String[] left = normalize(latest).split("[._+\\-\\s]+");
        String[] right = normalize(current).split("[._+\\-\\s]+");
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            int a = numberAt(left, i);
            int b = numberAt(right, i);
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    private static int numberAt(String[] parts, int index) {
        if (index >= parts.length) return 0;
        Matcher matcher = Pattern.compile("^\\d+").matcher(parts[index]);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class Release {
        final String tag;
        final String changelog;

        Release(String tag, String changelog) {
            this.tag = tag;
            this.changelog = changelog;
        }
    }
}
