package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** 更多页，默认选中关于。 */
public class PageOther implements Page {

    private final MainWindow win;
    private final Data data = Data.get();

    public PageOther(MainWindow win) {
        this.win = win;
        if (this.data.otherSection == null || this.data.otherSection.isEmpty()) this.data.otherSection = "about";
    }

    public PageOther(MainWindow win, String section) {
        this.win = win;
        this.data.otherSection = section;
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
        panel.getChildren().addAll(
                nav("帮助", "circle-help", "help"),
                nav("关于", "info", "about"),
                nav("实用工具", "wrench", "tools"),
                nav("Minecraft 新闻", "newspaper", "news"));
        // 直接跳到全局设置页
        Ui.ListItem setup = new Ui.ListItem(Theme.icon("settings", 17, Theme.text), "设置", null, 36);
        VBox.setMargin(setup, new Insets(8, 0, 0, 0));
        setup.setOnMouseClicked(e -> win.openPage("setup", true));
        panel.getChildren().add(setup);
        return panel;
    }

    private Node nav(String title, String icon, String key) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 17, Theme.text), title, null, 36);
        item.setUserData(key);
        item.setSelected(key.equals(data.otherSection));
        item.setOnMouseClicked(e -> {
            if (key.equals(data.otherSection)) return;
            data.otherSection = key;
            // 左栏不动，只换右栏
            win.refreshRight();
        });
        return item;
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox page = new VBox();
        page.setPadding(new Insets(25, 28, 28, 28));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(page);
        switch (data.otherSection) {
            case "help" -> buildHelp(page);
            case "news" -> buildNews(page);
            case "tools" -> buildTools(page);
            default -> buildAbout(page);
        }
        return scroll;
    }

    private void header(VBox page, String title, String subtitle) {
        VBox head = new VBox(5);
        head.setAlignment(Pos.TOP_LEFT);
        head.getChildren().addAll(Ui.bold(title, 30, Theme.text), Ui.label(subtitle, 13, Theme.text));
        ((Label) head.getChildren().get(1)).setOpacity(0.55);
        VBox.setMargin(head, new Insets(0, 0, 20, 0));
        page.getChildren().add(head);
    }

    private void buildAbout(VBox page) {
        Ui.Card legal = Ui.card("法律声明");
        Label en = Ui.label("NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.\n非 MINECRAFT 官方产品。未经 MOJANG 或 MICROSOFT 批准，也不与 MOJANG 或 MICROSOFT 关联。", 12.5, Theme.text);
        en.setWrapText(true);
        en.setStyle("-fx-font-weight: bold;");
        Label copy = paragraph("Minecraft、Minecraft 徽标及相关名称、品牌、图像与音效的著作权和商标权，均归 Mojang Synergies AB 与 Microsoft 所有，本启动器不主张任何权利，也不将 Minecraft 名称用作自身的主标题或标识。\n\nTCraft Launcher（简称 TCL）由 TCraft 团队独立开发与维护，与 Mojang Studios、Microsoft、网易（Minecraft 中国代理）均无隶属、赞助、授权、合作或背书关系，也未获其审查或认可。\n\n本启动器不包含、不分发 Minecraft 游戏本体或任何游戏文件，不提供破解、盗版、绕过正版验证或规避付费的功能；游戏本体与更新始终来自官方渠道。TCL 不运营也不代理任何 Minecraft 服务器，联机功能只负责把你已连接的设备互相连通。\n\n使用本启动器即表示：你已通过官方渠道购买 Minecraft: Java Edition 并持有有效账户，已阅读并同意《Minecraft 最终用户许可协议》（EULA）与《Minecraft 使用准则》；未成年人须取得监护人同意。使用本软件的风险由你自行承担。\n\n本启动器完全免费，不出售、不租赁、不设付费解锁，也不将 Minecraft 品牌用于商业推广。任何以 TCL 名义收费的行为均与本团队无关，也不能作为获得 Minecraft 的途径。");
        Label resp = paragraph("开发者、发布者与责任方均为 TCraft 团队。本软件按「现状」提供，不对使用或无法使用本软件造成的直接、间接损失承担责任；对存档、配置等文件的操作请自行备份。\n\n侵权投诉、合规问题与功能反馈：TCraft Launcher 启动器交流群（群号 1131373915，https://qm.qq.com/q/UONlv4VgwE）（本项目唯一对外渠道）。涉及 Minecraft 品牌的投诉，也可通过 https://help.minecraft.net/ 联系 Mojang。\n\nMinecraft EULA：https://www.minecraft.net/eula\nMinecraft 使用准则：https://www.minecraft.net/usage-guidelines\n本启动器使用了 Lucide 图标库（ISC 协议）等开源组件，相关权利归各自作者所有。");
        legal.add(en, copy, resp);
        legal.setCollapsed(true); // 默认收起
        VBox.setMargin(legal, new Insets(0, 0, 15, 0));
        page.getChildren().add(legal);

        Ui.Card about = Ui.card("关于");
        about.add(paragraph("TCraft Launcher 1.0.0 由 TCraft 团队开发与维护，持续更新，并已在 GitHub 开源。\n提供游戏与模组、整合包、资源包、光影的下载与管理，支持原版、Forge / NeoForge / Fabric / OptiFine / LiteLoader。\n同时提供登录、启动、联机、实例与整合包导出等完整能力。\n点击链接加入群聊【TCraft Launcher启动器交流群】：https://qm.qq.com/q/UONlv4VgwE，获取更新、反馈问题。"));
        Label buildType = Ui.label("当前构建：完整版（已内置微软正版登录）", 12.5, Theme.gray2);
        buildType.setWrapText(true);
        buildType.setStyle("-fx-font-weight: bold;");
        about.add(buildType);
        VBox.setMargin(about, new Insets(0, 0, 15, 0));
        page.getChildren().add(about);

        Ui.Card thanks = Ui.card("参考与致谢");
        thanks.add(paragraph("TCL 的功能规划与交互设计参考了 HMCL（Hello Minecraft! Launcher）的成熟方案；版本安装、资源下载、启动参数构建等核心流程，参考了 PCL（Plain Craft Launcher，作者龙腾猫跃）的主体代码。\n上述参考仅用于学习与兼容性实现：TCL 的界面与代码由 TCraft 团队使用 VB.NET / C# 重新编写，与 HMCL、PCL 均无隶属、赞助或背书关系，也不会与其混淆。在此向 HMCL 开发者与龙腾猫跃致谢。\n同时感谢 Lucide 图标库（ISC）等开源组件，相关权利归各自作者所有。"));
        VBox.setMargin(thanks, new Insets(0, 0, 15, 0));
        page.getChildren().add(thanks);

        Ui.Card repo = Ui.card("开源地址");
        repo.add(paragraph("https://github.com/Damianjiang/TCraft-Launcher\n欢迎提交 Issue 与 Pull Request，也欢迎 Star 支持。"));
        page.getChildren().add(repo);
    }

    /** 关于页正文，自动换行。 */
    private Label paragraph(String text) {
        Label l = Ui.label(text, 12.5, Theme.text);
        l.setWrapText(true);
        l.setLineSpacing(3);
        VBox.setMargin(l, new Insets(8, 0, 0, 0));
        return l;
    }

    private void buildHelp(VBox page) {
        header(page, "帮助", "常见问题与使用说明。");
        Ui.SearchBox search = Ui.searchBox("搜索帮助");
        search.setMaxWidth(430);
        VBox.setMargin(search, new Insets(0, 0, 14, 0));
        page.getChildren().add(search);

        String[][] faq = {
                {"如何安装 Java？", "启动游戏需要 Java 运行环境。可以在「设置 → Java」中点击「自动检测」，或手动选择 javaw.exe。\n"
                        + "TCL 会为不同版本的 Minecraft 自动匹配合适的 Java 版本。"},
                {"如何导入整合包？", "在「实例」页左侧点击「导入整合包」，选择 .zip 或 .mrpack 文件即可。\n"
                        + "导入完成后会在实例列表中生成一个新的实例。"},
                {"如何更换皮肤？", "点击标题栏的账户徽章进入账户管理，选择账户后可以更换皮肤。\n"
                        + "离线账户支持在本地皮肤列表中选择，也可以导入自定义皮肤图片。"},
                {"启动失败怎么办？", "请先检查「设置 → Java」中的 Java 路径是否正确，再尝试在版本设置中降低内存分配。\n"
                        + "如果仍然失败，可以打开「设置 → 日志」查看详细错误信息。"},
                {"什么是版本隔离？", "版本隔离会让每个游戏版本拥有独立的 mods、saves 与配置文件，避免不同版本之间互相影响。\n"
                        + "默认隔离所有版本，也可以在启动设置中修改。"}
        };
        VBox rows = new VBox(0);
        Ui.Card card = Ui.card("常见问题");
        card.setSpacing(0);
        card.add(rows);
        page.getChildren().add(card);
        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.toLowerCase();
            rows.getChildren().clear();
            for (String[] item : faq) {
                if (q.isEmpty() || item[0].toLowerCase().contains(q) || item[1].toLowerCase().contains(q)) {
                    Ui.ListItem r = new Ui.ListItem(Theme.icon("circle-help", 16, Theme.gray2), item[0], null, 42);
                    r.setOnMouseClicked(e -> showHelp(item[0], item[1]));
                    rows.getChildren().add(r);
                }
            }
        });
        for (String[] item : faq) {
            Ui.ListItem r = new Ui.ListItem(Theme.icon("circle-help", 16, Theme.gray2), item[0], null, 42);
            r.setOnMouseClicked(e -> showHelp(item[0], item[1]));
            rows.getChildren().add(r);
        }
    }

    private void showHelp(String title, String text) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20, 22, 16, 22));
        card.setPrefWidth(460);
        card.setBackground(Ui.fill(Theme.dialogBg, 12));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label body = Ui.label(text, 12.5, Theme.gray1);
        body.setWrapText(true);
        body.setMaxWidth(420);
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(Ui.button("知道了", Ui.BTN_THEME, win::closeModal));
        card.getChildren().addAll(Ui.bold(title, 16, Theme.text), body, actions);
        win.showModal(card);
    }

    private void buildNews(VBox page) {
        header(page, "新闻", "来自 Minecraft 官方的真实新闻。");
        Node status = Ui.loadingCard("正在获取 Minecraft 官方新闻…");
        page.getChildren().add(status);
        Thread thread = new Thread(() -> {
            try {
                // news.json 有 1MB+，靠 gzip 压
                String body = Http.get("https://launchercontent.mojang.com/v2/news.json");
                java.util.List<Object> entries = Json.arr(Json.obj(Json.parse(body)), "entries");
                javafx.application.Platform.runLater(() -> {
                    page.getChildren().remove(status);
                    int count = 0;
                    for (Object o : entries) {
                        if (count++ >= 20) break;
                        java.util.Map<String, Object> n = Json.obj(o);
                        String title = Json.str(n, "title", "Minecraft 新闻");
                        String date = Json.str(n, "date", "");
                        String text = Json.str(n, "text", Json.str(n, "shortText", ""));
                        String link = Json.str(n, "readMoreLink", "");
                        Ui.Card card = Ui.card(title);
                        if (!date.isEmpty()) card.add(Ui.label(date, 11.5, Theme.gray3));
                        Label desc = Ui.label(text, 12.5, Theme.gray1);
                        desc.setWrapText(true);
                        card.add(desc);
                        if (!link.isEmpty()) {
                            Label open = Ui.label("查看原文 →", 12, Theme.accent);
                            open.setCursor(javafx.scene.Cursor.HAND);
                            open.setOnMouseClicked(e -> openUrl(link));
                            card.add(open);
                        }
                        VBox.setMargin(card, new Insets(0, 0, 14, 0));
                        page.getChildren().add(card);
                    }
                    page.getChildren().remove(status);
                    if (count == 0) page.getChildren().add(Ui.label("暂时没有可显示的新闻。", 12, Theme.gray3));
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    page.getChildren().remove(status);
                    page.getChildren().add(Ui.hint("获取新闻失败："
                            + (ex.getMessage() == null ? "网络错误" : ex.getMessage()), 2));
                });
            }
        }, "TCL-News");
        thread.setDaemon(true);
        thread.start();
    }

    /** 实用工具页。 */
    private void buildTools(VBox page) {
        header(page, "实用工具", "联机、清理、下载与皮肤相关的辅助功能。");

        Ui.Card linkCard = Ui.card("游戏联机");
        VBox linkBody = new VBox(15);
        linkBody.setPadding(new Insets(10, 0, 4, 0));
        linkBody.getChildren().addAll(
                Ui.label("创建一个联机房间，把单人存档分享给朋友一起玩；也可以输入收到的邀请码，加入别人的房间。", 12.5, Theme.gray1),
                Ui.button("打开联机", Ui.BTN_HIGHLIGHT, () -> win.openPage("link", true)));
        ((Label) linkBody.getChildren().get(0)).setWrapText(true);
        linkCard.add(linkBody);
        VBox.setMargin(linkCard, new Insets(0, 0, 15, 0));
        page.getChildren().add(linkCard);

        Ui.Card utilCard = Ui.card("实用工具");
        FlowPane flow = new FlowPane(15, 15);
        flow.setPadding(new Insets(4, 0, 0, 0));
        flow.getChildren().addAll(
                Ui.button("清理垃圾", Ui.BTN_NORMAL, this::cleanJunk),
                Ui.button("今日人品", Ui.BTN_NORMAL, this::showLuck),
                Ui.button("创建快捷方式", Ui.BTN_NORMAL, this::createShortcut),
                Ui.button("启动次数", Ui.BTN_NORMAL, this::showLaunchCount));
        utilCard.add(flow);
        VBox.setMargin(utilCard, new Insets(0, 0, 15, 0));
        page.getChildren().add(utilCard);

        Ui.Card dlCard = Ui.card("自定义下载");
        VBox dlBody = new VBox(10);
        dlBody.getChildren().add(Ui.label("输入一个网址，将文件下载到指定位置。", 12.5, Theme.gray1));
        TextField dlUrl = Ui.field("https://example.com/file.jar");
        dlUrl.setPrefHeight(32);
        TextField dlName = Ui.field("文件名");
        dlName.setPrefHeight(32);
        HBox dlBtns = new HBox(10);
        dlBtns.setAlignment(Pos.CENTER_RIGHT);
        dlBtns.getChildren().addAll(
                Ui.button("打开文件夹", Ui.BTN_NORMAL, () -> openFolder(data.gameDir)),
                Ui.button("开始下载", Ui.BTN_HIGHLIGHT, () -> customDownload(dlUrl, dlName)));
        dlBody.getChildren().addAll(Ui.hint("下载网址", 0), dlUrl, Ui.hint("文件名", 0), dlName, dlBtns);
        dlCard.add(dlBody);
        VBox.setMargin(dlCard, new Insets(0, 0, 15, 0));
        page.getChildren().add(dlCard);

        Ui.Card skinCard = Ui.card("正版皮肤保存");
        VBox skinBody = new VBox(10);
        skinBody.getChildren().add(Ui.label("输入正版玩家名，获取并保存其皮肤。", 12.5, Theme.gray1));
        TextField skinId = Ui.field("玩家名");
        skinId.setPrefHeight(32);
        skinBody.getChildren().addAll(skinId, Ui.button("获取并保存", Ui.BTN_THEME, () -> {
            String p = skinId.getText() == null ? "" : skinId.getText().trim();
            if (p.isEmpty()) {
                win.toast("玩家名为空", "请输入一个玩家名。", 2);
                return;
            }
            downloadSkin(p);
        }));
        skinCard.add(skinBody);
        VBox.setMargin(skinCard, new Insets(0, 0, 15, 0));
        page.getChildren().add(skinCard);

        Ui.Card serverCard = Ui.card("服务器查询");
        Label serverResult = Ui.label("输入服务器地址后点击查询。", 12.5, Theme.gray2);
        serverResult.setWrapText(true);
        HBox serverRow = new HBox(10);
        serverRow.setAlignment(Pos.CENTER_LEFT);
        TextField serverAddr = Ui.field("例如 mc.hypixel.net 或 127.0.0.1:25565");
        serverAddr.setPrefHeight(32);
        HBox.setHgrow(serverAddr, Priority.ALWAYS);
        serverRow.getChildren().addAll(serverAddr, Ui.button("查询", Ui.BTN_NORMAL, () -> queryServer(serverAddr, serverResult)));
        serverCard.add(serverRow, serverResult);
        VBox.setMargin(serverCard, new Insets(0, 0, 15, 0));
        page.getChildren().add(serverCard);

        Ui.Card headCard = Ui.card("头像生成");
        VBox headBody = new VBox(10);
        HBox sizeRow = new HBox(10);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        sizeRow.getChildren().add(Ui.label("头像尺寸", 12.5, Theme.gray2));
        javafx.scene.control.ComboBox<String> size = Ui.combo(new String[]{"64x64", "96x96", "128x128"}, 0);
        size.setPrefWidth(120);
        size.setPrefHeight(28);
        sizeRow.getChildren().add(size);
        HBox headBtns = new HBox(10);
        headBtns.setAlignment(Pos.CENTER_LEFT);
        Data.Account acct = data.account();
        headBtns.getChildren().addAll(
                Ui.button("选择皮肤", Ui.BTN_NORMAL, () -> Dialogs.skinPicker(win, acct)),
                Ui.button("保存头像", Ui.BTN_THEME, () -> saveHead(size.getSelectionModel().getSelectedItem() == null ? "64x64"
                        : size.getSelectionModel().getSelectedItem())));
        headBody.getChildren().addAll(Ui.label("根据当前账户皮肤生成头像并保存为 PNG。", 12.5, Theme.gray1), sizeRow, headBtns);
        headCard.add(headBody);
        page.getChildren().add(headCard);
    }

    private void cleanJunk() {
        int[] removed = {0};
        try {
            String[] subdirs = {"crash-reports", "logs"};
            for (String s : subdirs) {
                File dir = new File(data.gameDir, s);
                if (!dir.isDirectory()) continue;
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && f.delete()) removed[0]++;
                }
            }
            win.toast("已清理垃圾", "删除了 " + removed[0] + " 个缓存文件。", 1);
        } catch (Exception e) {
            win.toast("清理失败", e.getMessage() == null ? "未知错误" : e.getMessage(), 2);
        }
    }

    /** 截图用：直接弹今日人品。 */
    void debugLuck() {
        showLuck();
    }

    /** 截图用：直接弹启动次数。 */
    void debugLaunchCount() {
        showLaunchCount();
    }

    private void showLuck() {
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seed = djb2(day + System.getProperty("user.name", "") + System.getProperty("os.name", ""));
        int luck = new java.util.Random(seed).nextInt(101);
        String rating = luckRating(luck);
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"));
        String title = "今日人品 · " + date + " (◕‿◕)";
        if (luck >= 60) {
            Dialogs.message(win, title, "今日人品：" + luck + " 分！\n评价：" + rating, false, "确定");
        } else {
            Dialogs.message(win, title, "今日人品：" + luck + " 分……\n评价：" + rating, luck <= 30, "我知道了");
        }
    }

    /** 每日种子的 DJB2。 */
    private static int djb2(String s) {
        long hash = 5381L;
        for (int i = 0; i < s.length(); i++) {
            hash = (hash * 33 + s.charAt(i)) & 0xFFFFFFFFL;
        }
        return (int) hash;
    }

    /** 评级文案。 */
    private static String luckRating(int v) {
        if (v == 100) return "满分！天选之子非你莫属，欧气拉满、掉啥爆啥！(๑•̀ㅂ•́)و✧";
        if (v >= 95) return "鸿运当头，连挖都是稀有货，今天掉块钻石都不奇怪！(≧▽≦)";
        if (v >= 90) return "运气爆棚，下界见龙蛋、刷怪塔爆满，冲就完事了！(•̀ᴗ•́)و";
        if (v >= 60) return "运势不错，今天是个好日子，正常肝基本不会亏。٩(ˊᗜˋ*)۶";
        if (v >= 40) return "平平淡淡，掉率回到平均线，稳住就好、别指望逆天。(•̀_•́)";
        if (v >= 30) return "运气偏背，抽卡、开盲盒先缓缓，动手前深呼吸。(；´д｀)";
        if (v >= 10) return "今天掉的全是沙子和煤炭，背到连刷怪塔都在坑你……(╯°□°)╯";
        return "大凶之日，宜躺平、宜频繁存档，出门前多存几份档。Orz";
    }

    /** 选位置后生成快捷方式。 */
    private void createShortcut() {
        Dialogs.select(win, "创建快捷方式", new String[]{"桌面", "开始菜单 / 应用程序菜单"},
                "创建", "取消", false, this::createShortcutAt);
    }

    private void createShortcutAt(int where) {
        try {
            java.nio.file.Path root = Theme.root();
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            String targetName = windows ? "TCraftLauncher.bat" : "TCraftLauncher.sh";
            java.nio.file.Path target = root.resolve(targetName);
            if (!Files.isRegularFile(target)) {
                throw new Exception("找不到启动脚本 " + target + "，请确认使用的是完整版程序。");
            }
            java.nio.file.Path home = java.nio.file.Paths.get(System.getProperty("user.home"));
            java.nio.file.Path dir;
            java.nio.file.Path link;
            String icon = root.resolve("res").resolve("images")
                    .resolve(windows ? "icon.ico" : "icon-256.png").toString();
            if (windows) {
                String appData = System.getenv("APPDATA");
                dir = (where == 1 && appData != null)
                        ? java.nio.file.Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs")
                        : home.resolve("Desktop");
                Files.createDirectories(dir);
                link = dir.resolve("TCraft Launcher.lnk");
                String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + q(link.toString()) + "');"
                        + "$s.TargetPath='" + q(target.toString()) + "';"
                        + "$s.WorkingDirectory='" + q(root.toString()) + "';"
                        + "$s.IconLocation='" + q(icon) + "';"
                        + "$s.Description='TCraft Launcher';$s.Save()";
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                        "-Command", ps);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.waitFor() != 0 || !Files.isRegularFile(link)) {
                    throw new Exception("WScript.Shell 未能生成快捷方式："
                            + (output.isBlank() ? "无输出" : output.trim()));
                }
            } else if (mac) {
                dir = home.resolve("Desktop");
                Files.createDirectories(dir);
                link = dir.resolve("TCraft Launcher.command");
                Files.writeString(link, "#!/bin/sh\ncd '" + q(root.toString())
                        + "'\nexec ./TCraftLauncher.sh\n", StandardCharsets.UTF_8);
                link.toFile().setExecutable(true);
            } else {
                dir = where == 1 ? home.resolve(".local/share/applications") : home.resolve("Desktop");
                Files.createDirectories(dir);
                link = dir.resolve("tcraft-launcher.desktop");
                String desktop = "[Desktop Entry]\nType=Application\nName=TCraft Launcher\n"
                        + "Exec=" + target + "\nPath=" + root + "\nIcon=" + icon + "\n"
                        + "Terminal=false\nCategories=Game;\n";
                Files.writeString(link, desktop, StandardCharsets.UTF_8);
                link.toFile().setExecutable(true);
            }
            win.toast("快捷方式已创建", link.toString(), 1);
        } catch (Exception e) {
            Errors.error("创建快捷方式失败", e);
        }
    }

    /** PowerShell 单引号转义。 */
    private static String q(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** 启动次数弹窗，读的是游戏启动计数。 */
    private void showLaunchCount() {
        int count = data.getInt("SystemLaunchCount", 0);
        Dialogs.message(win, "启动次数", "你已经启动了 " + count + " 次 Minecraft！");
    }

    private void customDownload(TextField url, TextField name) {
        String u = url.getText() == null ? "" : url.getText().trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            win.toast("网址无效", "请输入以 http:// 或 https:// 开头的网址。", 2);
            return;
        }
        String n = name.getText() == null ? "" : name.getText().trim();
        if (n.isEmpty()) n = u.substring(u.lastIndexOf('/') + 1);
        if (n.isEmpty()) n = "download";
        final String nameFinal = n;
        final String urlFinal = u;
        Thread dl = new Thread(() -> {
            try {
                java.nio.file.Path target = java.nio.file.Paths.get(data.gameDir, "downloads", nameFinal);
                java.nio.file.Files.createDirectories(target.getParent());
                downloadRaw(urlFinal, target, () -> win.toast("下载完成", nameFinal, 1), err ->
                        win.toast("下载失败", err == null ? "未知错误" : err, 2));
            } catch (Exception e) {
                win.toast("下载失败", e.getMessage() == null ? "未知错误" : e.getMessage(), 2);
            }
        }, "TCL-DL");
        dl.setDaemon(true);
        dl.start();
    }

    private void downloadRaw(String source, java.nio.file.Path target, Runnable onDone, java.util.function.Consumer<String> onErr) {
        Thread worker = new Thread(() -> {
            try {
                Fetch.file(null, source, target, null, 0, 1);
                javafx.application.Platform.runLater(onDone);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
                javafx.application.Platform.runLater(() -> onErr.accept(msg));
            }
        }, "TCL-DL-" + System.nanoTime());
        worker.setDaemon(true);
        worker.start();
    }

    private void downloadSkin(String player) {
        Thread worker = new Thread(() -> {
            try {
                String skin = "https://crafatar.com/skins/" + java.net.URLEncoder.encode(player, "UTF-8") + ".png";
                java.nio.file.Path target = java.nio.file.Paths.get(data.gameDir, "TCL", "Skins", player + ".png");
                java.nio.file.Files.createDirectories(target.getParent());
                downloadRaw(skin, target, () -> {
                    Data.Account a = data.account();
                    if (a != null) {
                        a.skin = player + ".png";
                        data.save();
                        win.refreshAccountBadge();
                    }
                    win.toast("皮肤已保存", player + ".png", 1);
                }, err -> win.toast("获取失败", "无法下载 " + player + " 的皮肤。", 2));
            } catch (Exception e) {
                win.toast("获取失败", e.getMessage() == null ? "未知错误" : e.getMessage(), 2);
            }
        }, "TCL-Skin");
        worker.setDaemon(true);
        worker.start();
    }

    private void saveHead(String sizeText) {
        Data.Account a = data.account();
        if (a == null) {
            win.toast("无账户", "请先添加一个账户。", 2);
            return;
        }
        int px = 64;
        if (sizeText.contains("96")) px = 96;
        else if (sizeText.contains("128")) px = 128;
        javafx.scene.image.Image skin = Theme.loadImage(a.skin == null ? "Steve.png" : a.skin);
        if (skin == null) {
            win.toast("无法生成", "找不到皮肤文件。", 2);
            return;
        }
        try {
            int w = (int) skin.getWidth(), h = (int) skin.getHeight();
            int s = Math.min(8, Math.min(w, h));
            java.awt.image.BufferedImage face = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) face.setRGB(x, y, skin.getPixelReader().getArgb(8 + x, 8 + y));
            }
            java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(px, px, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(face, 0, 0, px, px, null);
            g.dispose();
            File dir = new File(data.gameDir + "/TCL/Heads");
            dir.mkdirs();
            File out = new File(dir, a.name + "_" + px + "x" + px + ".png");
            javax.imageio.ImageIO.write(scaled, "png", out);
            win.toast("头像已保存", out.getAbsolutePath(), 1);
        } catch (Throwable t) {
            win.toast("生成失败", t.getMessage() == null ? "未知错误" : t.getMessage(), 2);
        }
    }

    private void queryServer(TextField addr, Label result) {
        String host = addr.getText() == null ? "" : addr.getText().trim();
        if (host.isEmpty()) return;
        result.setText("正在查询 " + host + " …");
        final String hostFinal = host;
        // 真实 SLP 查询：握手、状态响应、延迟
        Thread worker = new Thread(() -> {
            Servers.PingResult ping = Servers.ping(hostFinal, 3000);
            String info;
            if (ping.online) {
                info = "在线 · " + ping.version + " · " + ping.players + "/" + ping.maxPlayers
                        + " 人 · " + ping.latency + " ms" + (ping.motd.isEmpty() ? "" : "\n" + ping.motd);
            } else {
                info = "离线 / 无法连接：" + (ping.errorMessage == null || ping.errorMessage.isEmpty()
                        ? "服务器没有响应状态查询" : ping.errorMessage);
            }
            javafx.application.Platform.runLater(() -> result.setText(info));
        }, "TCL-ServerQuery");
        worker.setDaemon(true);
        worker.start();
    }

    private void openUrl(String url) {
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            win.toast("无法打开链接", url, 3);
        }
    }

    private void openFolder(String path) {
            Shell.openFolder(path);
        }
}
