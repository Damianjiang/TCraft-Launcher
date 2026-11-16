package tcraft;

import javafx.animation.PauseTransition;
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

/** GitHub Releases 更新检查：Atom → API → releases 页面。 */
public final class Updates {

    public static final String RELEASES_URL =
            "https://github.com/Damianjiang/TCraft-Launcher/releases";

    private static final String ATOM_URL = RELEASES_URL + ".atom";
    private static final String API_URL =
            "https://api.github.com/repos/Damianjiang/TCraft-Launcher/releases/latest";

    /** 正在检查时禁止重复发起。 */
    private static volatile boolean checking;

    private Updates() {
    }

    /** 启动后自动检查，受设置项 CheckUpdateOnStartup 控制。 */
    public static void checkOnStartup(MainWindow win) {
        if (App.SNAPSHOT_MODE || App.PERF_MODE) return;
        if (!Data.get().getBool("CheckUpdateOnStartup", true)) return;
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> check(win, false));
        delay.play();
    }

    /** 本次检查是否已经有结果，线程和超时定时器只能有一个收尾。 */
    private static final java.util.concurrent.atomic.AtomicBoolean SETTLED =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /** manual=true 时无论结果都会给反馈；自动检查只在有新版本时弹窗。 */
    public static void check(MainWindow win, boolean manual) {
        if (!manual && !windowAlive(win)) return;
        if (checking) {
            if (manual) win.toast("正在检查更新", "请稍候…", 0);
            return;
        }
        checking = true;
        SETTLED.set(false);
        if (manual) win.toast("正在检查更新", "正在读取 GitHub Releases…", 0);

        Thread thread = new Thread(() -> {
            Release release = null;
            Exception error = null;
            try {
                release = fetchLatest();
            } catch (Exception e) {
                error = e;
            }
            settle(win, release, error, manual);
        }, "TCL-Update");
        thread.setDaemon(true);
        thread.start();

        // DNS 解析卡住时 request timeout 不会生效，兜一个总超时，避免一直卡在「正在检查」
        PauseTransition timeout = new PauseTransition(Duration.seconds(15));
        timeout.setOnFinished(e -> settle(win, null, new IOException("连接超时"), manual));
        timeout.play();
    }

    /** 收尾只执行一次：复位状态，再按结果给反馈。 */
    private static void settle(MainWindow win, Release release, Exception error, boolean manual) {
        if (!SETTLED.compareAndSet(false, true)) return;
        checking = false;   // 先复位，界面反馈再排队
        Platform.runLater(() -> {
            if (App.SNAPSHOT_MODE || App.PERF_MODE) return;
            if (!windowAlive(win)) return;
            if (release == null) {
                Data.get().log("检查更新失败：" + (error == null ? "未知错误" : error));
                // 自动检查失败不打扰，手动检查才弹窗
                if (manual) Dialogs.message(win, "无法检查更新", reason(error), true, "确定");
                return;
            }
            try {
                handle(win, release, manual);
            } catch (Throwable t) {
                Data.get().log("处理更新信息失败：" + t);
                if (manual) {
                    Dialogs.message(win, "无法检查更新",
                            reason(t instanceof Exception e ? e : null), true, "确定");
                }
            }
        });
    }

    /** 把常见网络错误翻成一句能看懂的话。 */
    private static String reason(Exception e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage();
        String low = msg.toLowerCase();
        if (msg.contains("PKIX") || low.contains("certificate")) {
            return "证书校验失败，可能是代理或网络工具拦截了 GitHub，关掉代理再试。";
        }
        if (msg.contains("超时") || low.contains("timed out") || low.contains("timeout")) {
            return "连接 GitHub 超时，请检查网络后再试。";
        }
        return "GitHub 可能连接不稳定，你可以使用瓦特工具箱等工具开个加速或来个 VPN。";
    }

    private static boolean windowAlive(MainWindow win) {
        return win != null && win.stage != null && win.stage.isShowing();
    }

    private static void handle(MainWindow win, Release release, boolean manual) {
        String current = Build.VERSION == null ? "" : Build.VERSION.trim();
        String latest = release.tag == null ? "" : release.tag.trim();
        if (latest.isEmpty()) {
            if (manual) Dialogs.message(win, "检查更新失败", "GitHub 没有返回可用的版本号。", true, "确定");
            return;
        }
        // 开发版/无法解析的版本号：直接展示最新版信息，方便本地构建也能验证
        if (!isComparable(current)) {
            showUpdate(win, release);
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

    /** 有更新时的弹窗：版本号、更新日志、前往下载。 */
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
        ScrollPane logScroll = Ui.scroll(logContent);
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

    /** 依次尝试 Atom、API、Releases 页面。 */
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

    /** releases.atom 带完整更新日志，且不受 API 限流影响。 */
    private static Release fetchAtom() throws Exception {
        return parseAtom(fetch(ATOM_URL, "application/atom+xml,application/xml,text/xml"));
    }

    /** 解析 Atom 文本，单独抽出来方便自检。 */
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
        Element entry = (Element) entries.item(0);

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
        if (tag.isEmpty()) throw new IOException("Atom 中没有 tag");

        String html = textOf(entry, "content");
        return new Release(tag, htmlToText(html));
    }

    /** GitHub API 兜底，更新日志是 Markdown 原文。 */
    @SuppressWarnings("unchecked")
    private static Release fetchApi() throws Exception {
        String json = fetch(API_URL, "application/vnd.github+json");
        Map<String, Object> obj = Json.obj(Json.parse(json));
        String tag = Json.str(obj, "tag_name", "").trim();
        if (tag.isEmpty()) throw new IOException("API 中没有 tag");
        String body = Json.str(obj, "body", "");
        return new Release(tag, body == null ? "" : body.trim());
    }

    /** 最后直接抓 releases 页面里的 tag 链接，更新日志留空。 */
    private static Release fetchPage() throws Exception {
        String html = fetch(RELEASES_URL, "text/html");
        Matcher matcher = Pattern.compile("/releases/tag/([^\"'/?#]+)").matcher(html);
        if (!matcher.find()) throw new IOException("releases 页面中没有 tag");
        String raw = matcher.group(1);
        String tag = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        if (tag.isEmpty()) throw new IOException("releases 页面中没有 tag");
        return new Release(tag, "");
    }

    private static String fetch(String url, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(8))
                .header("User-Agent", "TCraftLauncher-JavaFX/" + Build.VERSION)
                .header("Accept", accept)
                .GET()
                .build();
        HttpResponse<String> response = Http.client().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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

    /** Atom 里的更新日志是 HTML，转成可读纯文本。 */
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

    /** 去掉 tag 前缀的 v，仅用于展示。 */
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

    /** 是否是可比较的数字版本号（dev、空串、纯文字都不算）。 */
    private static boolean isComparable(String version) {
        String value = normalize(version);
        return value.matches("\\d+(\\.\\d+)*([._+-].*)?");
    }

    /** 逐段比较数字版本，返回 latest 相对 current 的大小。 */
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
