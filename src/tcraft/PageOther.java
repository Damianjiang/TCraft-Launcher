package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PageOther implements Page {

    private final MainWindow win;
    private final Data data = Data.get();

    public PageOther(MainWindow win) {
        this.win = win;
        if (this.data.otherSection == null || this.data.otherSection.isEmpty()
                || "help".equals(this.data.otherSection)) this.data.otherSection = "about";
        if (this.data.otherSection.startsWith("plugin:")
                && Plugins.get().pluginPage(this.data.otherSection) == null
                && !Plugins.get().list().isEmpty()) {
            this.data.otherSection = "plugins";
        }
    }

    public PageOther(MainWindow win, String section) {
        this.win = win;
        this.data.otherSection = ("help".equals(section) ? "about" : section);
    }

    @Override
    public double leftWidth() {
        return 160;
    }

    @Override
    public String sectionKey() {
        return data.otherSection;
    }

    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(12, 0, 0, 0));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        boolean hideAbout = data.getBool("UiHiddenOtherAbout", false);
        if (data.otherSection.equals("about") && hideAbout) data.otherSection = "tools";
        if (!hideAbout) panel.getChildren().add(nav("关于", "info", "about"));
        panel.getChildren().addAll(
                nav("实用工具", "wrench", "tools"),
                nav("插件", "puzzle", "plugins"));
        for (Plugins.PageEntry entry : Plugins.get().pluginPages()) {
            Ui.ListItem item = new Ui.ListItem(
                    Plugins.get().icon(entry.owner, entry.icon, 17, Theme.text), entry.title, null, 36);
            String key = "plugin:" + entry.key;
            item.setUserData(key);
            item.setSelected(key.equals(data.otherSection));
            item.setOnMouseClicked(e -> {
                if (key.equals(data.otherSection)) return;
                data.otherSection = key;
                win.refreshRight(true);
            });
            panel.getChildren().add(item);
        }
        panel.getChildren().add(nav("Minecraft 新闻", "newspaper", "news"));
        if (!data.getBool("UiHiddenPageSetup", false)) {
            Ui.ListItem setup = new Ui.ListItem(Theme.icon("settings", 17, Theme.text), "设置", null, 36);
            VBox.setMargin(setup, new Insets(8, 0, 0, 0));
            setup.setOnMouseClicked(e -> win.openPage("setup", true));
            panel.getChildren().add(setup);
        }
        return panel;
    }

    private Node nav(String title, String icon, String key) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 17, Theme.text), title, null, 36);
        item.setUserData(key);
        item.setSelected(key.equals(data.otherSection));
        if ("news".equals(key)) {
            StackPane refresh = Ui.iconButton("refresh-cw", 22, 13, "刷新新闻", this::refreshNews);
            refresh.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(refresh);
        }
        if ("plugins".equals(key)) {
            StackPane refresh = Ui.iconButton("refresh-cw", 22, 13, "刷新插件", () -> {
                data.otherSection = "plugins";
                PluginsPage.sidebarRefresh(win);
            });
            refresh.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(refresh);
        }
        item.setOnMouseClicked(e -> {
            if (key.equals(data.otherSection)) return;
            data.otherSection = key;
            win.refreshRight(true);
        });
        return item;
    }

    private void refreshNews() {
        if (newsPage == null) {
            newsCache = null;
            newsCacheAt = 0;
            data.otherSection = "news";
            win.refreshRight(false);
            return;
        }
        if (newsLoading) return;
        newsLoading = true;
        newsCache = null;
        newsCacheAt = 0;
        final int gen = ++newsGen;
        Thread thread = new Thread(() -> {
            try {
                String body = Http.get("https://launchercontent.mojang.com/v2/news.json");
                java.util.List<Object> entries = Json.arr(Json.obj(Json.parse(body)), "entries");
                javafx.application.Platform.runLater(() -> {
                    if (gen != newsGen) return;
                    newsLoading = false;
                    newsCache = entries;
                    newsCacheAt = System.currentTimeMillis();
                    renderNews(newsPage, java.util.Collections.emptyList(), entries);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    if (gen != newsGen) return;
                    newsLoading = false;
                    win.toast("刷新新闻失败", ex.getMessage() == null ? Lang.t("网络错误") : ex.getMessage(), 2);
                });
            }
        }, "TCL-NewsRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox page = new VBox();
        page.setPadding(new Insets(25, 28, 28, 28));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        page.setMinWidth(0);
        scroll.setContent(page);
        newsPage = null;
        newsNodes.clear();
        switch (data.otherSection) {
            case "news" -> buildNews(page);
            case "tools" -> buildTools(page);
            case "plugins" -> PluginsPage.build(win, page);
            default -> {
                if (data.otherSection != null && data.otherSection.startsWith("plugin:")) {
                    Plugins.get().buildPluginPage(data.otherSection, page);
                } else {
                    buildAbout(page);
                }
            }
        }
        return scroll;
    }

    private void header(VBox page, String title, String subtitle) {
        page.getChildren().add(headerRow(title, subtitle, null));
    }

    private HBox headerRow(String title, String subtitle, Node action) {
        VBox head = new VBox(5);
        head.setAlignment(Pos.TOP_LEFT);
        Label sub = Ui.label(subtitle, 13, Theme.text);
        sub.setOpacity(0.55);
        head.getChildren().addAll(Ui.bold(title, 30, Theme.text), sub);
        HBox row = new HBox(12, head);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(head, Priority.ALWAYS);
        if (action != null) row.getChildren().add(action);
        VBox.setMargin(row, new Insets(0, 0, 20, 0));
        return row;
    }

    private void buildAbout(VBox page) {
        Ui.Card legal = Ui.card("法律声明");
        Label en = Ui.label("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.\n非 MINECRAFT 官方产品。未经 MOJANG 或 MICROSOFT 批准，也不与 MOJANG 或 MICROSOFT 关联。", 12.5, Theme.text);
        en.setWrapText(true);
        en.setStyle("-fx-font-weight: bold;");
        Label copy = paragraph("Minecraft、Minecraft 徽标及相关名称、品牌、图像与音效的著作权和商标权，均归 Mojang Synergies AB 与 Microsoft 所有，本启动器不主张任何权利，也不将 Minecraft 名称用作自身的主标题或标识。\n\nTCraft Launcher（简称 TCL）由 TCraft 团队独立开发与维护，与 Mojang Studios、Microsoft、网易（Minecraft 中国代理）均无隶属、赞助、授权、合作或背书关系，也未获其审查或认可。\n\n本启动器不包含、不分发 Minecraft 游戏本体或任何游戏文件，不提供破解、盗版、绕过正版验证或规避付费的功能；游戏本体与更新始终来自官方渠道。TCL 不运营也不代理任何 Minecraft 服务器，联机功能只负责把你已连接的设备互相连通。\n\n使用本启动器即表示：你已通过官方渠道购买 Minecraft: Java Edition 并持有有效账户，已阅读并同意《Minecraft 最终用户许可协议》（EULA）与《Minecraft 使用准则》；未成年人须取得监护人同意。使用本软件的风险由你自行承担。\n\n本启动器完全免费，不出售、不租赁、不设付费解锁，也不将 Minecraft 品牌用于商业推广。任何以 TCL 名义收费的行为均与本团队无关，也不能作为获得 Minecraft 的途径。");
        Label resp = paragraph("开发者、发布者与责任方均为 TCraft 团队。本软件按「现状」提供，不对使用或无法使用本软件造成的直接、间接损失承担责任；对存档、配置等文件的操作请自行备份。\n\n侵权投诉、合规问题与功能反馈：TCraft Launcher 启动器交流群（群号 1131373915，https://qm.qq.com/q/UONlv4VgwE）（本项目唯一对外渠道）。涉及 Minecraft 品牌的投诉，也可通过 https://help.minecraft.net/ 联系 Mojang。\n\nMinecraft EULA：https://www.minecraft.net/eula\nMinecraft 使用准则：https://www.minecraft.net/usage-guidelines\n本启动器使用了 Lucide 图标库（ISC 协议）等开源组件，相关权利归各自作者所有。");
        legal.add(en, copy, resp);
        legal.setCollapsed(true);
        VBox.setMargin(legal, new Insets(0, 0, 15, 0));
        page.getChildren().add(legal);

        Ui.Card about = Ui.card("关于");
        about.add(paragraph("TCraft Launcher 是用 JavaFX 写的开源 Minecraft 启动器，支持微软正版登录、EasyTier 联机，以及游戏、Mod、整合包与资源包的下载管理。"));
        about.add(actionRow("加入 QQ 群", Ui.BTN_HIGHLIGHT, "群号 1131373915 · 获取更新、反馈问题", COMMUNITY_URL));
        Label buildType = Ui.label("当前构建：完整版（已内置微软正版登录）", 12.5, Theme.gray2);
        buildType.setWrapText(true);
        buildType.setStyle("-fx-font-weight: bold;");
        Label version = Ui.label("v" + Build.VERSION, 12, Theme.gray3);
        HBox buildRow = new HBox(8, buildType, version);
        buildRow.setAlignment(Pos.CENTER_LEFT);
        about.add(buildRow);

        HBox updateRow = new HBox(12);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        Label updateHint = Ui.label("从 GitHub Releases 读取最新版本与更新日志", 12, Theme.gray2);
        updateHint.setWrapText(true);
        HBox.setHgrow(updateHint, Priority.ALWAYS);
        StackPane updateBtn = Ui.button("检查更新", Ui.BTN_HIGHLIGHT,
                () -> Updates.check(win, true, updateHint));
        updateBtn.setMinWidth(126);
        updateBtn.setMaxWidth(Region.USE_PREF_SIZE);
        updateRow.getChildren().addAll(updateBtn, updateHint);
        VBox.setMargin(updateRow, new Insets(12, 0, 0, 0));
        about.add(updateRow);
        VBox.setMargin(about, new Insets(0, 0, 15, 0));
        page.getChildren().add(about);

        String osName = System.getProperty("os.name", "");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        long maxMb = Runtime.getRuntime().maxMemory() / 1048576L;
        Ui.Card env = Ui.card("运行环境");
        env.add(Ui.settingRow("系统", Ui.label((osName + " " + osVersion).trim() + " · " + osArch, 12.5, Theme.gray2)));
        env.add(Ui.settingRow("内存上限", Ui.label(maxMb + " MB", 12.5, Theme.gray2)));
        env.add(Ui.settingRow("Java", Ui.label(System.getProperty("java.version", "")
                + " · " + System.getProperty("java.vendor", ""), 12.5, Theme.gray2)));
        env.add(Ui.settingRow("启动器", Ui.label("v" + Build.VERSION, 12.5, Theme.gray2)));
        VBox.setMargin(env, new Insets(0, 0, 15, 0));
        page.getChildren().add(env);

        Ui.Card thanks = Ui.card("参考与致谢");
        thanks.add(paragraph("TCL 的功能规划与交互设计参考了社区启动器的通用做法，界面与代码均为独立编写，与其他启动器无隶属、赞助或背书关系。\n"
                + "图标来自 Lucide（ISC），WebP 解码使用 jwebp（Apache-2.0），联机能力来自 EasyTier，在此一并致谢。"));
        VBox.setMargin(thanks, new Insets(0, 0, 15, 0));
        page.getChildren().add(thanks);

        Ui.Card repo = Ui.card("开源地址");
        repo.add(paragraph("欢迎提交 Issue 与 Pull Request，也欢迎 Star 支持。"));
        repo.add(actionRow("打开 GitHub 仓库", Ui.BTN_NORMAL, "github.com/Damianjiang/TCraft-Launcher", GITHUB_URL));
        page.getChildren().add(repo);
    }

    private static final String COMMUNITY_URL = "https://qm.qq.com/q/UONlv4VgwE";
    private static final String GITHUB_URL = "https://github.com/Damianjiang/TCraft-Launcher";

    private HBox actionRow(String text, int type, String note, String url) {
        StackPane btn = Ui.button(text, type, () -> openUrl(url));
        btn.setMinWidth(126);
        btn.setMaxWidth(Region.USE_PREF_SIZE);
        Label hint = Ui.label(note, 12, Theme.gray2);
        hint.setWrapText(true);
        HBox.setHgrow(hint, Priority.ALWAYS);
        HBox row = new HBox(12, btn, hint);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(row, new Insets(12, 0, 0, 0));
        return row;
    }

    private Label paragraph(String text) {
        Label l = Ui.label(text, 12.5, Theme.text);
        l.setWrapText(true);
        l.setLineSpacing(3);
        VBox.setMargin(l, new Insets(8, 0, 0, 0));
        return l;
    }

    // 30 分钟缓存 切页不重拉
    private VBox newsPage;
    private final java.util.List<Node> newsNodes = new java.util.ArrayList<>();
    private boolean newsLoading;

    private static java.util.List<Object> newsCache;
    private static long newsCacheAt;

    private void buildNews(VBox page) {
        newsPage = page;
        newsLoading = false;
        newsNodes.clear();
        StackPane refresh = Ui.button("刷新新闻", Ui.BTN_HIGHLIGHT, this::refreshNews);
        refresh.setMinWidth(110);
        refresh.setMaxWidth(Region.USE_PREF_SIZE);
        page.getChildren().add(headerRow("新闻", "来自 Minecraft 官方的真实新闻。", refresh));
        stopSkeletons();
        final int gen = ++newsGen;
        java.util.List<Object> cached = newsCache;
        if (cached != null && System.currentTimeMillis() - newsCacheAt < 1800_000L) {
            renderNews(page, java.util.Collections.emptyList(), cached);
            return;
        }
        List<Node> skeletons = newsSkeletons(5);
        page.getChildren().addAll(skeletons);
        Thread thread = new Thread(() -> {
            try {
                // news.json 解析留后台
                String body = Http.get("https://launchercontent.mojang.com/v2/news.json");
                java.util.List<Object> entries = Json.arr(Json.obj(Json.parse(body)), "entries");
                javafx.application.Platform.runLater(() -> {
                    // 重进后丢弃旧请求
                    if (gen != newsGen) return;
                    newsCache = entries;
                    newsCacheAt = System.currentTimeMillis();
                    renderNews(page, skeletons, entries);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    if (gen != newsGen) return;
                    stopSkeletons();
                    page.getChildren().removeAll(skeletons);
                    page.getChildren().add(Ui.hint(Lang.t("获取新闻失败：")
                            + (ex.getMessage() == null ? Lang.t("网络错误") : ex.getMessage()), 2));
                });
            }
        }, "TCL-News");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderNews(VBox page, List<Node> skeletons, java.util.List<Object> entries) {
        stopSkeletons();
        page.getChildren().removeAll(skeletons);
        if (!newsNodes.isEmpty()) {
            page.getChildren().removeAll(newsNodes);
            newsNodes.clear();
        }
        int count = 0;
        for (Object o : entries) {
            if (count++ >= 20) break;
            java.util.Map<String, Object> n = Json.obj(o);
            String title = Json.str(n, "title", "Minecraft 新闻");
            String date = Json.str(n, "date", "");
            String text = Json.str(n, "text", Json.str(n, "shortText", ""));
            String link = Json.str(n, "readMoreLink", "");
            Ui.Card card = Ui.card(title);
            // 配图相对路径 异步拼域名
            String imgPath = Json.str(Json.obj(n, "newsPageImage"), "url", "");
            if (imgPath.isEmpty()) imgPath = Json.str(Json.obj(n, "playPageImage"), "url", "");
            if (!imgPath.isEmpty()) {
                String imgUrl = imgPath.startsWith("http") ? imgPath
                        : "https://launchercontent.mojang.com" + imgPath;
                card.add(newsImage(imgUrl));
            }
            if (!date.isEmpty()) card.add(Ui.label(date, 11.5, Theme.gray3));
            Label desc = Ui.label(text, 12.5, Theme.gray1);
            desc.setWrapText(true);
            card.add(desc);
            if (!link.isEmpty()) {
                Label open = Ui.label("查看原文", 12, Theme.accent);
                open.setCursor(javafx.scene.Cursor.HAND);
                open.setOnMouseClicked(e -> openUrl(link));
                card.add(open);
            }
            VBox.setMargin(card, new Insets(0, 0, 14, 0));
            page.getChildren().add(card);
            newsNodes.add(card);
        }
        if (count == 0) {
            Label empty = Ui.label("暂时没有可显示的新闻。", 12, Theme.gray3);
            page.getChildren().add(empty);
            newsNodes.add(empty);
        }
        // 新卡片单独淡入
        int ms = Ui.aniMs(130);
        for (Node n : newsNodes) {
            if (ms <= 0) break;
            n.setOpacity(0);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(ms), n);
            fade.setToValue(1);
            fade.play();
        }
    }

    // 骨架屏动画
    private List<Node> newsSkeletons(int count) {
        List<Node> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            VBox card = new VBox(10);
            card.setPadding(new Insets(16, 18, 18, 18));
            card.setBackground(Ui.fill(Theme.cardBg, 8));
            VBox.setMargin(card, new Insets(0, 0, 14, 0));

            Region titleBar = skeletonBar(150, 14);
            StackPane img = new StackPane();
            img.setPrefHeight(120);
            img.setMinHeight(120);
            img.setMaxHeight(120);
            img.setBackground(Ui.fill(Theme.gray6, 6));
            Ui.Loading loading = new Ui.Loading(140, 70, null);
            loading.start();
            img.getChildren().add(loading);
            Region line1 = skeletonBar(400, 11);
            Region line2 = skeletonBar(320, 11);
            card.getChildren().addAll(titleBar, img, line1, line2);
            pulseNodes.add(card);
            out.add(card);
        }
        // 骨架呼吸闪烁 离场自停
        javafx.animation.Timeline pulse = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(skeletonOpacity, 1.0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(750),
                        new javafx.animation.KeyValue(skeletonOpacity, 0.45, javafx.animation.Interpolator.EASE_BOTH)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(1500),
                        new javafx.animation.KeyValue(skeletonOpacity, 1.0, javafx.animation.Interpolator.EASE_BOTH)));
        pulse.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        for (Node n : pulseNodes) n.opacityProperty().bind(skeletonOpacity);
        skeletonPulse = pulse;
        pulse.play();
        return out;
    }

    private void stopSkeletons() {
        if (skeletonPulse != null) {
            skeletonPulse.stop();
            skeletonPulse = null;
        }
        for (Node n : pulseNodes) n.opacityProperty().unbind();
        pulseNodes.clear();
    }

    @Override
    public void onHide() {
        stopSkeletons();
    }

    private final List<Node> pulseNodes = new ArrayList<>();
    private final javafx.beans.property.SimpleDoubleProperty skeletonOpacity =
            new javafx.beans.property.SimpleDoubleProperty(1.0);
    private javafx.animation.Timeline skeletonPulse;
    private int newsGen;

    private Region skeletonBar(double w, double h) {
        Region r = new Region();
        r.setPrefSize(w, h);
        r.setMinSize(w, h);
        r.setMaxHeight(h);
        r.setBackground(Ui.fill(Theme.gray6, 4));
        return r;
    }

    private Node newsImage(String url) {
        final double h = 160;
        StackPane box = new StackPane();
        box.setPrefHeight(h);
        box.setMinSize(0, h);
        box.setMaxHeight(h);
        box.setBackground(new Background(new BackgroundFill(Theme.gray6, new CornerRadii(6), Insets.EMPTY)));

        javafx.scene.image.Image cached = RemoteImages.cached(url, 772);
        if (cached != null) {
            showNewsImage(box, cached, h);
            return box;
        }
        if (RemoteImages.failed(url)) return box;
        Ui.Loading loading = new Ui.Loading(140, 70, null);
        loading.start();
        box.getChildren().add(loading);
        RemoteImages.load(url, 772, img -> {
            if (img != null) showNewsImage(box, img, h);
            else box.getChildren().remove(loading);
        });
        return box;
    }

    // UniformToFill
    private void showNewsImage(StackPane box, javafx.scene.image.Image img, double h) {
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        javafx.beans.value.ChangeListener<Number> resize = (o, oldV, newV) -> {
            double cw = box.getWidth();
            double iw = img.getWidth(), ih = img.getHeight();
            if (cw <= 0 || iw <= 0 || ih <= 0) return;
            double scale = Math.max(cw / iw, h / ih);
            iv.setFitWidth(iw * scale);
            iv.setFitHeight(ih * scale);
        };
        box.widthProperty().addListener(resize);
        resize.changed(null, null, box.getWidth());

        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(box.widthProperty());
        clip.heightProperty().bind(box.heightProperty());
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        box.setClip(clip);
        box.getChildren().setAll(iv);
    }

    private void buildTools(VBox page) {
        header(page, "实用工具", "常用工具。");
        Ui.Card utilCard = Ui.card("实用工具");
        FlowPane flow = new FlowPane(15, 15);
        flow.setPadding(new Insets(4, 0, 0, 0));
        flow.getChildren().addAll(
                Ui.button("清理垃圾", Ui.BTN_NORMAL, () -> Dialogs.confirm(win,
                        "清理垃圾", "将删除游戏目录与实例目录下的崩溃报告和日志文件。\n"
                                + "此操作不可撤销，是否继续？", this::cleanJunk, true)),
                Ui.button("今日人品", Ui.BTN_NORMAL, this::showLuck));
        flow.getChildren().addAll(Plugins.get().toolButtons());
        utilCard.add(flow);
        page.getChildren().add(utilCard);
    }

    private void cleanJunk() {
        Threads.start("TCL-CleanJunk", () -> {
            int[] removed = {0};
            String error = null;
            try {
                String[] subdirs = {"crash-reports", "logs"};
                java.nio.file.Path global = java.nio.file.Paths.get(data.gameDir);
                Data.Instance ins = data.instance();
                java.nio.file.Path run = ins == null ? global : Isolation.run(ins);
                java.nio.file.Path[] roots = run.equals(global)
                        ? new java.nio.file.Path[]{global} : new java.nio.file.Path[]{global, run};
                for (java.nio.file.Path root : roots) {
                    for (String s : subdirs) {
                        File dir = new File(root.toFile(), s);
                        if (!dir.isDirectory()) continue;
                        File[] files = dir.listFiles();
                        if (files == null) continue;
                        for (File f : files) {
                            if (f.isFile() && f.delete()) removed[0]++;
                        }
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "未知错误" : e.getMessage();
            }
            final int removedCount = removed[0];
            final String fError = error;
            javafx.application.Platform.runLater(() -> {
                if (fError != null) {
                    win.toast("清理失败", fError, 2);
                } else {
                    win.toast("已清理垃圾", Lang.tf("删除了 %d 个缓存文件。", removedCount), 1);
                }
            });
        });
    }

    private void showLuck() {
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seed = djb2(day + System.getProperty("user.name", "") + System.getProperty("os.name", ""));
        int luck = new java.util.Random(seed).nextInt(101);
        String rating = luckRating(luck);
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"));
        String title = Lang.t("今日人品") + " · " + date;
        if (luck >= 60) {
            Dialogs.message(win, title, Lang.tf("今日人品：%d 分！\n评价：%s", luck, rating), false, Lang.t("确定"));
        } else {
            Dialogs.message(win, title, Lang.tf("今日人品：%d 分。\n评价：%s", luck, rating), luck <= 30, Lang.t("我知道了"));
        }
    }

    private static int djb2(String s) {
        long hash = 5381L;
        for (int i = 0; i < s.length(); i++) {
            hash = (hash * 33 + s.charAt(i)) & 0xFFFFFFFFL;
        }
        return (int) hash;
    }

    private static String luckRating(int v) {
        if (v == 100) return Lang.t("满分！天选之子非你莫属，欧气拉满，掉啥爆啥！");
        if (v >= 95) return Lang.t("鸿运当头，连挖都是稀有货，今天掉块钻石都不奇怪！");
        if (v >= 90) return Lang.t("运气爆棚，下界见龙蛋、刷怪塔爆满，冲就完事了！");
        if (v >= 60) return Lang.t("运势不错，今天是个好日子，正常肝基本不会亏。");
        if (v >= 40) return Lang.t("平平淡淡，掉率回到平均线，稳住就好，别指望逆天。");
        if (v >= 30) return Lang.t("运气偏背，抽卡、开盲盒先缓缓，动手前深呼吸。");
        if (v >= 10) return Lang.t("今天掉的全是沙子和煤炭，背到连刷怪塔都在坑你。");
        return Lang.t("大凶之日，宜躺平、宜频繁存档，出门前多存几份档。");
    }

    private void openUrl(String url) {
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            win.toast("无法打开链接", url, 3);
        }
    }
}
