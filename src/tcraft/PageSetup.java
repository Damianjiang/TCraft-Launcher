package tcraft;

import javafx.application.Platform;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** 设置页：左侧分类，右侧设置卡片。 */
public class PageSetup implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private final String initialSection;
    private VBox navPanel;
    /** 启动页的内存占用条容器。 */
    private VBox ramDisplayHost;

    public PageSetup(MainWindow win, String section) {
        this.win = win;
        this.initialSection = section;
        data.setupSection = section;
        win.pendingSetupSection = section;
    }

    @Override
    public double leftWidth() {
        return 160;
    }

    @Override
    public String sectionKey() {
        return data.setupSection;
    }

    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(14, 10, 8, 12));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        Label title = Ui.bold("设置", 21, Theme.text);
        VBox.setMargin(title, new Insets(0, 0, 10, 2));
        TextField search = Ui.field("搜索设置");
        search.setPrefHeight(30);
        VBox.setMargin(search, new Insets(0, 2, 8, 0));

        VBox items = new VBox(2);
        ScrollPane scroll = Ui.scroll(items);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        String[][] sections = {
                {"0", "启动", "play", "launch"},
                {"1", "联机", "globe", "link"},
                {"2", "个性化", "palette", "ui"},
                {"3", "系统", "cpu", "system"},
                {"4", "Java", "coffee", "java"},
                {"5", "日志", "scroll-text", "log"},
                {"7", "存储与清理", "trash-2", "cache"},
                {"8", "网络", "network", "network"},
                {"6", "更多", "tool-case", "more"}
        };
        navPanel = items;
        for (String[] s : sections) items.getChildren().add(navItem(s[1], s[2], s[3], s[0]));
        items.getChildren().add(new Region() {{ setMinHeight(8); }});
        Ui.ListItem back = new Ui.ListItem(Theme.icon("arrow-left", 16, Theme.text), "返回", null, 34);
        back.setOnMouseClicked(e -> win.openPage("launch", true));
        items.getChildren().add(back);

        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.toLowerCase();
            for (Node n : items.getChildren()) {
                if (n instanceof Ui.ListItem li) {
                    boolean match = q.isEmpty() || li.title.getText().toLowerCase().contains(q);
                    li.setVisible(match);
                    li.setManaged(match);
                }
            }
        });
        panel.getChildren().addAll(title, search, scroll);
        return panel;
    }

    private Node navItem(String title, String icon, String key, String tag) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 17, Theme.text), title, null, 34);
        item.setSelected(key.equals(data.setupSection));
        item.setUserData(key);
        // 这些分区才有初始化按钮
        if (java.util.Set.of("launch", "link", "ui", "system", "java", "log").contains(key)) {
            StackPane reset = Ui.iconButton("rotate-ccw", 22, 13, "初始化本页设置", () -> resetSection(key));
            reset.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(reset);
        }
        item.setOnMouseClicked(e -> {
            if (key.equals(data.setupSection)) return;
            data.setupSection = key;
            win.pendingSetupSection = key;
            win.refreshRight();
        });
        return item;
    }

    private void resetSection(String key) {
        switch (key) {
            case "launch" -> {
                data.set("LaunchIsolation", 4);
                data.set("LaunchWindowTitle", "");
                data.set("LaunchCustomInfo", "TCL");
                data.set("LaunchVisibility", 5);
                data.set("LaunchPriority", 1);
                data.set("LaunchWindowType", 1);
                data.set("LaunchWindowWidth", "854");
                data.set("LaunchWindowHeight", "480");
                data.set("LaunchRamType", 0);
                data.set("LaunchRam", 4096);
                data.set("LaunchRamInitial", 0);
                data.set("LaunchOptimizeRam", true);
                data.set("LaunchJvmArgs", "");
                data.set("LaunchGameArgs", "");
                data.set("LaunchPreCommand", "");
                data.set("LaunchPreCommandWait", true);
                data.set("LaunchAdvanceGC", 4);
                data.set("LaunchIpStack", 1);
                data.set("LaunchAdvanceClasspathHead", "");
                data.set("LaunchAdvanceLockMemory", false);
                data.set("LaunchAdvanceNoJavaw", false);
            }
            case "link" -> {
                data.set("LinkLatencyMode", 0);
                data.set("LinkPeer", 0);
                data.set("LinkCustomPeer", "");
                data.set("LinkDownloadMode", 0);
                data.set("LinkDownloadNode", 0);
            }
            case "ui" -> {
                data.set("UiThemePreset", -1);
                data.set("UiAutoThemeFromBackground", true);
                data.set("UiBackgroundOpacity", 1000);
                data.set("UiBackgroundBlur", 0);
                data.set("UiWindowRadius", 6);
                data.set("UiComponentOpacity", 50);
                data.set("UiLauncherTransparent", 600);
                data.set("UiLauncherLogo", true);
                data.set("UiMenuAnimation", true);
                data.set("UiListButtonsAlwaysShow", false);
                data.set("UiAnimSpeed", 100);
                data.set("UiTitleAccountBadge", true);
                data.set("UiRememberWindowPos", false);
                data.set("UiSkipIntroAnim", false);
                data.set("UiBackgroundType", 0);
                data.set("UiBackgroundSuit", 0);
                data.set("UiBackgroundRotate", 0);
                data.set("UiBackgroundColorful", true);
                data.set("UiVolume", 60);
                Theme.applyComponentOpacity();
                win.rebuildUi();
            }
            case "system" -> {
                data.set("SystemDownloadSource", 0);
                data.set("SystemVersionSource", 1);
                data.set("SystemBmclRoot", "");
                data.set("SystemMaxThreads", "64");
                data.set("SystemSpeedLimit", "");
                data.set("SystemCurseKey", "");
                data.set("SystemHttpProxyType", 1);
                data.set("SystemProxyAddress", "");
                data.set("SystemProxyHost", "");
                data.set("SystemProxyPort", "7890");
                data.set("SystemProxyUser", "");
                data.set("SystemProxyPass", "");
                data.set("SystemHttpProxyForGame", false);
                data.applyProxy();
            }
            case "java" -> {
                data.javaPath = data.detectJava();
                data.save();
            }
            case "log" -> data.set("SystemDebugMode", false);
            default -> {
                win.refreshRight();
                return;
            }
        }
        win.toast("已初始化", "本页设置已恢复默认", 1);
        win.refreshRight();
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox page = new VBox();
        page.setPadding(new Insets(22, 28, 30, 28));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(page);
        switch (data.setupSection) {
            case "link" -> buildLink(page);
            case "ui" -> buildUi(page);
            case "system" -> buildSystem(page);
            case "java" -> buildJava(page);
            case "log" -> buildLog(page);
            case "cache" -> buildCache(page);
            case "network" -> buildNetwork(page);
            case "more" -> buildMore(page);
            default -> buildLaunch(page);
        }
        return scroll;
    }

    private void header(VBox page, String title, String subtitle) {
        VBox head = new VBox(5);
        Label sub = Ui.label(subtitle, 13, Theme.text);
        sub.setOpacity(0.55);
        head.getChildren().addAll(Ui.bold(title, 30, Theme.text), sub);
        VBox.setMargin(head, new Insets(0, 0, 20, 0));
        page.getChildren().add(head);
    }

    private Ui.Card addCard(VBox page, String title) {
        Ui.Card card = Ui.card(title);
        card.setSpacing(2);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));
        page.getChildren().add(card);
        return card;
    }

    /** 5 项，索引就是设置值。 */
    private static final String[] ISOLATION_ITEMS = {
            "关闭", "隔离可安装 Mod 的版本", "隔离非正式版",
            "隔离可安装 Mod 的版本与非正式版", "隔离所有版本"};
    /** 界面 5 项映射到 0/2/3/4/5 五个真实值。 */
    private static final int[] VISIBILITY_VALUES = {0, 2, 3, 4, 5};
    private static final String[] VISIBILITY_ITEMS = {
            "游戏启动后立即关闭", "游戏启动后隐藏，游戏退出后自动关闭",
            "游戏启动后隐藏，游戏退出后重新打开", "游戏启动后最小化", "游戏启动后仍保持不变"};
    /** 索引 0 到 4 依次是高、中、低、极高、实时。 */
    private static final String[] PRIORITY_ITEMS = {
            "高（优先保证游戏运行，游戏性能更佳，但可能造成其他程序卡顿）", "中（平衡）",
            "低（优先保证其他程序运行，但可能让游戏卡顿，适合挂机时使用）",
            "极高（比 高 更优先，可能造成整机卡顿）", "实时（最高，可能导致整个系统卡死，极度不推荐）"};
    private static final String[] WINDOW_TYPE_ITEMS = {"全屏", "默认", "与启动器尺寸一致", "自定义尺寸", "最大化"};
    /** 索引 0/1/2/3/4，3 表示不指定，4 表示调优 G1GC。 */
    private static final String[] GC_ITEMS = {
            "尽量使用 ZGC", "尽量使用分代 ZGC", "标准 G1GC", "不指定（可自定义）", "调优 G1GC"};

    private void buildLaunch(VBox page) {
        header(page, "启动", "版本、窗口、内存与高级启动选项。");
        Ui.Card arg = addCard(page, "启动选项");
        comboRow(arg, "默认版本隔离", ISOLATION_ITEMS, data.getInt("LaunchIsolation", 4),
                i -> data.set("LaunchIsolation", i));
        textRow(arg, "游戏窗口标题", "默认", data.get("LaunchWindowTitle", ""), s -> data.set("LaunchWindowTitle", s));
        textRow(arg, "自定义信息", "TCL", data.get("LaunchCustomInfo", "TCL"), s -> data.set("LaunchCustomInfo", s));
        int visValue = data.getInt("LaunchVisibility", 5);
        int visIndex = 4;
        for (int i = 0; i < VISIBILITY_VALUES.length; i++) {
            if (VISIBILITY_VALUES[i] == visValue) visIndex = i;
        }
        comboRow(arg, "启动器可见性", VISIBILITY_ITEMS, visIndex,
                i -> data.set("LaunchVisibility", VISIBILITY_VALUES[i]));
        comboRow(arg, "进程优先级", PRIORITY_ITEMS, data.getInt("LaunchPriority", 1),
                i -> data.set("LaunchPriority", i));

        // 只有自定义尺寸才显示宽高输入框
        int winType = data.getInt("LaunchWindowType", 1);
        HBox sizeRow = new HBox(6);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        TextField w = Ui.field("854");
        w.setPrefWidth(70);
        w.setText(data.get("LaunchWindowWidth", "854"));
        w.textProperty().addListener((o, a, b) -> data.set("LaunchWindowWidth", b));
        TextField h = Ui.field("480");
        h.setPrefWidth(70);
        h.setText(data.get("LaunchWindowHeight", "480"));
        h.textProperty().addListener((o, a, b) -> data.set("LaunchWindowHeight", b));
        sizeRow.getChildren().addAll(w, Ui.label(" × ", 15, Theme.text), h);
        boolean custom = winType == 3;
        sizeRow.setVisible(custom);
        sizeRow.setManaged(custom);
        ComboBox<String> winCombo = Ui.combo(WINDOW_TYPE_ITEMS, winType);
        winCombo.setPrefWidth(170);
        winCombo.setOnAction(e -> {
            int i = winCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            data.set("LaunchWindowType", i);
            boolean show = i == 3;
            sizeRow.setVisible(show);
            sizeRow.setManaged(show);
        });
        HBox winBox = new HBox(8, winCombo, sizeRow);
        winBox.setAlignment(Pos.CENTER_LEFT);
        arg.add(Ui.settingRow("窗口大小", winBox));

        Ui.Card mem = addCard(page, "内存分配");
        int ramType = data.getInt("LaunchRamType", 0);
        Ui.RadioBox auto = Ui.radioBox("自动配置", ramType == 0);
        Ui.RadioBox manual = Ui.radioBox("自定义", ramType == 1);
        HBox radios = new HBox(20, auto, manual);
        Label maxVal = Ui.bold(data.getInt("LaunchRam", 4096) + " MB", 13, Theme.text);
        Ui.SliderBar maxRam = Ui.sliderBar(512, physicalRamMb(), data.getInt("LaunchRam", 4096));
        maxRam.setPrefWidth(260);
        Label initVal = Ui.bold(initialRamLabel(), 13, Theme.text);
        Ui.SliderBar initRam = Ui.sliderBar(0, data.getInt("LaunchRam", 4096),
                data.getInt("LaunchRamInitial", 0));
        initRam.setPrefWidth(260);
        Runnable syncRamEnabled = () -> {
            boolean editable = manual.isSelected();
            maxRam.setDisable(!editable);
            initRam.setDisable(!editable);
        };
        syncRamEnabled.run();
        maxRam.setOnChanged(b -> {
            int v = (int) (Math.round(b / 512.0) * 512);
            maxVal.setText(v + " MB");
            data.set("LaunchRam", v);
            if (initRam.getValue() > v) initRam.setValue(v, false);
            refreshMemoryDisplay(v);
        });
        initRam.setOnChanged(b -> {
            int v = (int) (Math.round(b / 256.0) * 256);
            initVal.setText(v == 0 ? "自动（最大内存的 1/4）" : v + " MB");
            data.set("LaunchRamInitial", v);
        });
        HBox maxRow = new HBox(12, maxRam, maxVal);
        HBox initRow = new HBox(12, initRam, initVal);
        maxRow.setAlignment(Pos.CENTER_LEFT);
        initRow.setAlignment(Pos.CENTER_LEFT);
        // 用 Ui.animateExpand 展开，直接 setVisible 会跳
        VBox ramDetail = new VBox(0);
        ramDetail.getChildren().addAll(Ui.settingRow("最大内存", maxRow), Ui.settingRow("初始内存", initRow));
        boolean manualOn = manual.isSelected();
        ramDetail.setVisible(manualOn);
        ramDetail.setManaged(manualOn);
        if (!manualOn) {
            ramDetail.setMaxHeight(0);
            ramDetail.setOpacity(0);
        }
        auto.setOnAction(() -> {
            data.set("LaunchRamType", 0);
            syncRamEnabled.run();
            Ui.animateExpand(ramDetail, false);
        });
        manual.setOnAction(() -> {
            data.set("LaunchRamType", 1);
            syncRamEnabled.run();
            Ui.animateExpand(ramDetail, true);
        });
        mem.add(radios, ramDetail);

        Ui.Check optimize = Ui.checkBox("启动游戏前进行内存优化", false);
        optimize.setSelected(data.getBool("LaunchOptimizeRam", true));
        optimize.setOnAction(() -> data.set("LaunchOptimizeRam", optimize.isSelected()));
        mem.add(optimize);

        // 用真实物理内存，2 秒刷新一次
        ramDisplayHost = new VBox(0);
        mem.add(ramDisplayHost);
        refreshMemoryDisplay(data.getInt("LaunchRam", 4096));
        final Timeline[] ramTick = new Timeline[1];
        ramTick[0] = new Timeline(new KeyFrame(javafx.util.Duration.seconds(2), e -> {
            if (page.getScene() == null) {
                ramTick[0].stop(); // 页面换掉就停
            } else {
                refreshMemoryDisplay(data.getInt("LaunchRam", 4096));
            }
        }));
        ramTick[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
        ramTick[0].play();

        Ui.Card adv = addCard(page, "高级选项");
        adv.setCollapsible(true);
        adv.setCollapsed(true);
        // 支持 ${library_directory} 这类替换标记
        TextArea jvm = new TextArea(data.get("LaunchJvmArgs", ""));
        jvm.setPromptText("留空则使用默认值");
        jvm.setWrapText(true);
        jvm.setPrefHeight(100);
        jvm.setPrefWidth(360);
        HBox.setHgrow(jvm, Priority.ALWAYS);
        jvm.setStyle("-fx-font-size: 12; -fx-control-inner-background: transparent;");
        jvm.textProperty().addListener((o, a, b) -> data.set("LaunchJvmArgs", b));
        adv.add(Ui.settingRow("Java 虚拟机参数", jvm));
        textRow(adv, "游戏参数", "留空则使用默认值", data.get("LaunchGameArgs", ""),
                s -> data.set("LaunchGameArgs", s));
        TextField pre = textRow(adv, "启动前执行命令", "留空则不执行", data.get("LaunchPreCommand", ""),
                s -> data.set("LaunchPreCommand", s));
        Ui.Check waitCmd = Ui.checkBox("等待命令执行完成后再继续启动", false);
        waitCmd.setSelected(data.getBool("LaunchPreCommandWait", true));
        waitCmd.setOnAction(() -> data.set("LaunchPreCommandWait", waitCmd.isSelected()));
        boolean hasPre = pre.getText() != null && !pre.getText().isBlank();
        waitCmd.setVisible(hasPre);
        waitCmd.setManaged(hasPre);
        pre.textProperty().addListener((o, a, b) -> {
            boolean show = b != null && !b.isBlank();
            waitCmd.setVisible(show);
            waitCmd.setManaged(show);
        });
        adv.add(waitCmd);

        // 决定启动用哪个 javaw.exe
        List<String> javas = findJavas();
        if (javas.isEmpty()) javas = new ArrayList<>(List.of(data.javaPath.isEmpty() ? "未找到 Java" : data.javaPath));
        ComboBox<String> javaCombo = Ui.combo(javas.toArray(new String[0]), Math.max(0, javas.indexOf(data.javaPath)));
        javaCombo.setOnAction(e -> {
            String path = javaCombo.getSelectionModel().getSelectedItem();
            if (path == null || path.equals("未找到 Java")) return;
            if (data.javaPath.equals(path)) return;
            data.javaPath = path;
            data.save();
            win.toast("Java 已切换", path, 1);
        });
        adv.add(Ui.settingRow("Java 列表", javaCombo));

        comboRow(adv, "内存管理", GC_ITEMS, data.getInt("LaunchAdvanceGC", 4),
                i -> data.set("LaunchAdvanceGC", i));
        comboRow(adv, "IP 栈偏好", new String[]{"IPv4 优先", "Java 默认", "IPv6 优先"},
                data.getInt("LaunchIpStack", 1), i -> data.set("LaunchIpStack", i));
        textRow(adv, "类路径头部", "留空则不添加", data.get("LaunchAdvanceClasspathHead", ""),
                s -> data.set("LaunchAdvanceClasspathHead", s));
        adv.add(directCheck("锁定内存分配（-Xms = -Xmx）", "LaunchAdvanceLockMemory", false));
        adv.add(directCheck("显示控制台窗口（java.exe）", "LaunchAdvanceNoJavaw", false));
        adv.add(Ui.hint("版本独立设置中还有更多高级选项可供调整。", 0));

        StackPane switchBtn = Ui.extraTextButton("arrow-right", "版本独立设置", win::openInstanceSetup);
        StackPane switchLayer = new StackPane(switchBtn);
        switchLayer.setAlignment(Pos.CENTER);
        switchLayer.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(switchLayer, new Insets(-5, 0, 15, 0));
        page.getChildren().add(switchLayer);
    }

    /** 直接绑定配置键的复选框。 */
    private Ui.Check directCheck(String text, String key, boolean def) {
        Ui.Check box = Ui.checkBox(text, false);
        box.setSelected(data.getBool(key, def));
        box.setOnAction(() -> data.set(key, box.isSelected()));
        return box;
    }

    /** 物理内存总量 MB，取不到回退 4096。 */
    private static long physicalRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalMemorySize();
            if (total > 0) return Math.max(2048, Math.min(65536, total / 1024 / 1024));
        } catch (Throwable ignored) {
        }
        return 4096;
    }

    private String initialRamLabel() {
        int v = data.getInt("LaunchRamInitial", 0);
        return v == 0 ? "自动（最大内存的 1/4）" : v + " MB";
    }

    /** 用物理内存和游戏分配值填占用条。 */
    private void refreshMemoryDisplay(int gameRamMb) {
        VBox host = ramDisplayHost;
        if (host == null) return;
        long totalMb = physicalRamMb();
        long freeMb;
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            freeMb = Math.max(0, os.getFreeMemorySize() / 1024 / 1024);
        } catch (Throwable e) {
            freeMb = totalMb / 2;
        }
        long usedMb = Math.max(0, totalMb - freeMb);
        HBox bars = new HBox(24);
        bars.getChildren().addAll(
                memoryBox("已使用内存", gb(usedMb) + " / " + gb(totalMb),
                        usedMb / (double) Math.max(1, totalMb)),
                memoryBox("游戏分配", gameRamMb >= 1024 ? String.format("%.1f GB", gameRamMb / 1024.0)
                                : gameRamMb + " MB",
                        gameRamMb / (double) Math.max(1, totalMb)));
        host.getChildren().setAll(bars);
    }

    private static String gb(long mb) {
        return mb >= 1024 ? String.format("%.1f GB", mb / 1024.0) : mb + " MB";
    }

    private VBox memoryBox(String title, String value, double ratio) {
        VBox box = new VBox(4);
        box.setMinWidth(180);
        Label t = Ui.label(title, 11, Theme.gray2);
        Label v = Ui.bold(value, 15, Theme.text);
        ProgressBar bar = new ProgressBar(Math.max(0.02, Math.min(1, ratio)));
        bar.setPrefWidth(170);
        bar.setPrefHeight(7);
        box.getChildren().addAll(t, v, bar);
        return box;
    }

    private void buildLink(VBox page) {
        header(page, "联机", "P2P 联机连接与节点下载选项。");
        Ui.Card conn = addCard(page, "连接选项");
        comboRow(conn, "模式", new String[]{"优先直连", "优先低延迟"},
                data.getInt("LinkLatencyMode", 0), i -> data.set("LinkLatencyMode", i));
        comboRow(conn, "节点", new String[]{
                "自动选择", "etnode（陶瓦官方节点）", "225284.xyz（上海）",
                "et1.fuis.top（武汉）", "自定义（见下方输入框）"},
                data.getInt("LinkPeer", 0), i -> data.set("LinkPeer", i));
        textRow(conn, "自定义节点", "tcp://mc.yqst.top:11010,tcp://225284.xyz:11010",
                data.get("LinkCustomPeer", ""), s -> data.set("LinkCustomPeer", s));

        Ui.Card dl = addCard(page, "下载节点");
        String[] hosts = EasyTier.mirrorHosts();
        ComboBox<String> nodeCombo = Ui.combo(hosts, Math.max(0, Math.min(hosts.length - 1,
                data.getInt("LinkDownloadNode", 0))));
        int mode = data.getInt("LinkDownloadMode", 0);
        nodeCombo.setDisable(mode != 1);
        ComboBox<String> modeCombo = Ui.combo(new String[]{"自动选择最快节点", "指定节点"}, mode);
        modeCombo.setOnAction(e -> {
            int i = modeCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            data.set("LinkDownloadMode", i);
            nodeCombo.setDisable(i != 1);
        });
        nodeCombo.setOnAction(e -> {
            int i = nodeCombo.getSelectionModel().getSelectedIndex();
            if (i >= 0) data.set("LinkDownloadNode", i);
        });
        dl.add(Ui.settingRow("下载方式", modeCombo), Ui.settingRow("指定节点", nodeCombo));
        Label note = Ui.label("「自动选择最快节点」会在首次下载联机核心时并行测速各节点，记下最快的一条，"
                + "之后直接沿用。无论哪种方式，下载都是多线程进行，并且保留其余节点作为备用，"
                + "因此单个节点失效也不会中断下载。",
                12, Theme.gray3);
        note.setWrapText(true);
        dl.add(note);
    }

    /** 未解锁隐藏主题的提示文案，点锁定项时用。 */
    private String unlockHint() {
        int launches = data.getInt("SystemCount", 0);
        String next = Theme.nextHiddenThemeName();
        if (next.isEmpty()) return "隐藏主题已全部解锁。";
        return "隐藏主题每累计启动 " + Theme.UNLOCK_EVERY_LAUNCHES + " 次解锁一个（当前已启动 " + launches
                + " 次，再启动 " + Theme.launchesToNextUnlock(launches) + " 次可解锁「" + next + "」）。";
    }

    private void buildUi(VBox page) {
        header(page, "个性化", "主题、背景、声音与外观。每项改动即时生效。");
        Ui.Card theme = addCard(page, "主题与配色");
        theme.add(Ui.label("主题的强调色会应用到按钮、滑块与选中态，切换后立即生效。", 12, Theme.gray2));
        // 未全解锁时显示进度
        if (!Theme.nextHiddenThemeName().isEmpty()) {
            int left = Theme.launchesToNextUnlock(data.getInt("SystemCount", 0));
            Label prog = Ui.label("隐藏主题 " + Theme.unlockedHiddenIds().size() + "/" + Theme.HIDDEN_THEMES.length
                            + " · 每累计启动 " + Theme.UNLOCK_EVERY_LAUNCHES + " 次解锁一个，再启动 "
                            + left + " 次可解锁「" + Theme.nextHiddenThemeName() + "」",
                    12, Theme.gray3);
            prog.setWrapText(true);
            theme.add(prog);
        }
        FlowPane swatches = new FlowPane(18, 10);
        swatches.setPadding(new Insets(6, 0, 4, 0));
        int current = data.getInt("UiThemePreset", -1);
        // 未解锁时半透明并加标记，点击只提示
        for (int i0 = 0; i0 < Theme.THEME_NAMES.length; i0++) {
            final int i = i0;
            if (i == 14) continue;
            boolean selected = current == i;
            Ui.RadioBox rb = Ui.radioBox(Theme.THEME_NAMES[i], selected);
            if (!Theme.isThemeUnlocked(i)) {
                rb.setOpacity(0.55);
                rb.getChildren().add(Theme.icon("sparkles", 12, Theme.gray3));
                rb.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                    e.consume(); // 没解锁不能选
                    win.toast("隐藏主题未解锁", unlockHint(), 0);
                });
            } else {
                rb.setOnAction(() -> {
                    data.set("UiThemePreset", i);
                    data.set("UiAutoThemeFromBackground", false);
                    // 不调 applyPreset，否则七曜彩每次会叠两次色相
                    // 整页静默重建，从按钮位置扩散揭开
                    javafx.geometry.Bounds b = rb.localToScene(rb.getBoundsInLocal());
                    win.applyThemeFrom(b.getCenterX(), b.getCenterY());
                    win.toast("主题已切换", Theme.THEME_NAMES[i], 1);
                });
            }
            swatches.getChildren().add(rb);
        }
        theme.add(swatches);
        Ui.Check autoTheme = Ui.checkBox("根据背景图自动调整主题色", false);
        autoTheme.setSelected(data.getBool("UiAutoThemeFromBackground", true));
        autoTheme.setOnAction(() -> {
            data.set("UiAutoThemeFromBackground", autoTheme.isSelected());
            if (autoTheme.isSelected()) {
                data.set("UiThemePreset", -1);
                Theme.applyWallpaperTheme();
                win.rebuildUi();
            }
        });
        theme.add(autoTheme);
        Ui.Check logo = Ui.checkBox("打开启动器时显示 TCL 图标", false);
        logo.setSelected(data.getBool("UiLauncherLogo", true));
        logo.setOnAction(() -> data.set("UiLauncherLogo", logo.isSelected()));
        theme.add(logo);
        // 拖动时实时重算表面色
        Ui.SliderBar componentSlider = sliderRow(theme, "组件半透明", 0, 100, data.getInt("UiComponentOpacity", 50), v -> {
            data.set("UiComponentOpacity", v.intValue());
            Theme.applyComponentOpacity();
        }, "%");
        componentSlider.setOnMouseReleased(e -> win.rebuildUi()); // 松开后全局重建
        sliderRow(theme, "不透明度", 40, 100, Math.round((data.getInt("UiLauncherTransparent", 600) + 400) / 10.0), v -> {
            data.set("UiLauncherTransparent", (int) (v * 10 - 400));
            win.applyWindowOpacity();
        }, "%");

        // JavaFX 没有独立的弹卡透明度
        Ui.Card menu = addCard(page, "弹出菜单");
        Ui.Check menuAnim = Ui.checkBox("启用弹出菜单动画效果", false);
        menuAnim.setSelected(data.getBool("UiMenuAnimation", true));
        menuAnim.setOnAction(() -> data.set("UiMenuAnimation", menuAnim.isSelected()));
        menu.add(menuAnim, Ui.label("弹出菜单卡片独立于「组件半透明」——组件半透明作用于全局卡片。", 12, Theme.gray3));

        Ui.Card listBtn = addCard(page, "列表按钮");
        Ui.Check buttons = Ui.checkBox("所有列表的操作按钮常驻显示", false);
        buttons.setSelected(data.getBool("UiListButtonsAlwaysShow", false));
        buttons.setOnAction(() -> data.set("UiListButtonsAlwaysShow", buttons.isSelected()));
        listBtn.add(buttons, Ui.label("开启后按钮会一直占住右侧空间，列表标题可显示的宽度会略微变窄。", 12, Theme.gray3));

        Ui.Card detail = addCard(page, "界面细节");
        sliderRow(detail, "窗口圆角", 0, 24, data.getInt("UiWindowRadius", 6), v -> {
            data.set("UiWindowRadius", v.intValue());
            win.rebuildUi();
        }, " px");
        sliderRow(detail, "动画速度", 0, 200, data.getInt("UiAnimSpeed", 100), v -> {
            data.set("UiAnimSpeed", v.intValue());
        }, "%");
        Ui.Check badge = Ui.checkBox("在标题栏显示账户头像与名字", false);
        badge.setSelected(data.getBool("UiTitleAccountBadge", true));
        badge.setOnAction(() -> {
            data.set("UiTitleAccountBadge", badge.isSelected());
            win.rebuildUi();
        });
        detail.add(badge);
        Ui.Check remember = Ui.checkBox("记住窗口位置", false);
        remember.setSelected(data.getBool("UiRememberWindowPos", false));
        remember.setOnAction(() -> data.set("UiRememberWindowPos", remember.isSelected()));
        detail.add(remember);
        Ui.Check skip = Ui.checkBox("跳过启动时的窗口入场动画（下次启动生效）", false);
        skip.setSelected(data.getBool("UiSkipIntroAnim", false));
        skip.setOnAction(() -> data.set("UiSkipIntroAnim", skip.isSelected()));
        detail.add(skip);

        boolean videoMode = data.getInt("UiBackgroundType", 0) == 1;
        int videoCount = videoMode ? win.scanVideos().size() : 0;
        int imageCount = 0;
        if (!videoMode) {
            try (var s = Files.list(Paths.get(data.gameDir, "TCL", "Pictures"))) {
                imageCount = (int) s.filter(Files::isRegularFile).count();
            } catch (Exception ignored) {
            }
        }
        // 标题带上真实数量
        String bgTitle = videoMode
                ? (videoCount > 0 ? "背景（视频 " + videoCount + " 个）" : "背景")
                : (imageCount > 0 ? "背景（图片 " + imageCount + " 张）" : "背景");
        Ui.Card bg = addCard(page, bgTitle);
        comboRow(bg, "背景类型", new String[]{"图片", "视频"}, data.getInt("UiBackgroundType", 0), i -> {
            data.set("UiBackgroundType", i);
            win.refreshVideoBackground(false);
            win.refreshRight(); // 两套面板互换，只换右栏
        });
        if (!videoMode) {
            comboRow(bg, "自适应方式", new String[]{"填充", "适应", "拉伸", "平铺"}, data.getInt("UiBackgroundSuit", 0), i -> {
                data.set("UiBackgroundSuit", i);
                win.applyBackground();
            });
            comboRow(bg, "自动轮播", new String[]{"关闭", "每 5 分钟", "每 15 分钟", "每 30 分钟", "每 60 分钟"},
                    data.getInt("UiBackgroundRotate", 0), i -> data.set("UiBackgroundRotate", i));
            sliderRow(bg, "不透明度", 0, 100, data.getInt("UiBackgroundOpacity", 1000) / 10, v -> {
                data.set("UiBackgroundOpacity", (int) (v * 10));
                win.applyBackground();
            }, "%");
            sliderRow(bg, "背景模糊", 0, 20, data.getInt("UiBackgroundBlur", 0), v -> {
                data.set("UiBackgroundBlur", v.intValue());
                win.applyBackground();
            }, " px");
            Ui.Check colorful = Ui.checkBox("叠加彩色背景", false);
            colorful.setSelected(data.getBool("UiBackgroundColorful", true));
            colorful.setOnAction(() -> data.set("UiBackgroundColorful", colorful.isSelected()));
            bg.add(colorful);
            FlowPane bgButtons = new FlowPane(10, 10);
            bgButtons.getChildren().addAll(
                    Ui.button("打开文件夹", Ui.BTN_NORMAL, this::openBackgroundFolder),
                    Ui.button("刷新背景图片", Ui.BTN_NORMAL, () -> {
                        win.refreshWallpaper(false);
                        win.toast("背景已刷新", "已重新加载壁纸", 1);
                    }),
                    Ui.button("清空背景图片", Ui.BTN_RED, () -> {
                        win.refreshWallpaper(true);
                        win.toast("已恢复内置背景", "背景图片已重置", 1);
                    }));
            bg.add(bgButtons);
        } else {
            // 有视频时才显示这些
            if (videoCount > 0) {
                sliderRow(bg, "音量", 0, 100, data.getInt("UiVideoVolume", 500) / 10, v -> {
                    data.set("UiVideoVolume", (int) Math.round(v * 10));
                    win.updateVideoAudio();
                }, "%");
            }
            Ui.Check vidRandom = Ui.checkBox("随机播放", false);
            vidRandom.setSelected(data.getBool("UiVideoRandom", true));
            vidRandom.setOnAction(() -> {
                data.set("UiVideoRandom", vidRandom.isSelected());
                win.refreshVideoBackground(false);
            });
            bg.add(vidRandom);
            Ui.Check vidMute = Ui.checkBox("静音（仅作为背景画面）", false);
            vidMute.setSelected(data.getBool("UiVideoMute", true));
            vidMute.setOnAction(() -> {
                data.set("UiVideoMute", vidMute.isSelected());
                win.updateVideoAudio();
            });
            bg.add(vidMute);
            FlowPane vidButtons = new FlowPane(10, 10);
            vidButtons.getChildren().addAll(
                    Ui.button("打开文件夹", Ui.BTN_NORMAL, this::openVideoFolder),
                    Ui.button("刷新背景视频", Ui.BTN_NORMAL, () -> win.refreshVideoBackground(true)),
                    Ui.button("清空背景视频", Ui.BTN_RED, () -> Dialogs.confirm(win,
                            "清空背景视频", "即将删除背景视频文件夹中的所有文件。\n此操作不可撤销，是否确定？", () -> {
                                clearVideoFolder();
                                win.refreshVideoBackground(false);
                                win.refreshRight();
                            }, true)));
            bg.add(vidButtons);
        }

        Ui.Card sound = addCard(page, "背景音乐");
        sliderRow(sound, "音量", 0, 100, data.getInt("UiVolume", 60), v -> {
            data.set("UiVolume", v.intValue());
            try {
                Music.setVolume(v.intValue());
            } catch (Throwable ignored) {
            }
        }, "%");
        // 静音是视频专属的，音乐没有静音开关
        sound.add(Ui.hint("将 .mp3 / .wav 音乐放入 游戏目录/TCL/Music 后，右下角会出现音乐按钮。", 0));
    }

    private void openBackgroundFolder() {
        try {
            Path dir = Paths.get(data.gameDir, "TCL", "Pictures");
            Files.createDirectories(dir);
            Shell.openFolder(dir.toString());
        } catch (Exception e) {
            win.toast("无法打开文件夹", e.getMessage(), 3);
        }
    }

    private void openVideoFolder() {
        try {
            Path dir = win.videoFolder();
            Files.createDirectories(dir);
            Shell.openFolder(dir.toString());
        } catch (Exception e) {
            win.toast("无法打开文件夹", e.getMessage(), 3);
        }
    }

    /** 删视频文件夹里的文件，保留子目录。 */
    private void clearVideoFolder() {
        int removed = 0;
        try {
            Path dir = win.videoFolder();
            if (Files.isDirectory(dir)) {
                for (Path p : win.scanVideos()) {
                    try {
                        Files.delete(p);
                        removed++;
                    } catch (Exception ignored) {
                    }
                }
            }
            win.toast("已清空背景视频", "删除了 " + removed + " 个文件", 1);
        } catch (Exception e) {
            win.toast("清空失败", e.getMessage(), 3);
        }
    }

    private void buildSystem(VBox page) {
        header(page, "系统", "下载、网络、语言与启动器行为。");
        Ui.Card download = addCard(page, "下载");
        // 0 和 2 只是首选顺序，下载时还会按速度择优
        String[] sourceNames = {"镜像优先（下载时按实测速度择优）", "官方优先（下载时按实测速度择优）", "仅使用官方源"};
        int fileCur = data.getInt("SystemDownloadSource", 0);
        int fileIndex = 1;
        for (int i = 0; i < FILE_SOURCES.length; i++) if (FILE_SOURCES[i] == fileCur) fileIndex = i;
        comboRow(download, "文件下载源", sourceNames, fileIndex,
                i -> data.set("SystemDownloadSource", FILE_SOURCES[i]));
        Label speedHint = Ui.label("首次下载时会各抓 128KB 测一次速（每个域名只测一次，结果缓存 10 分钟），"
                + "实际按最快的源下载；某个源变慢或连不上会自动换另一个。", 12, Theme.gray3);
        speedHint.setWrapText(true);
        download.add(speedHint);
        comboRow(download, "版本列表源", new String[]{
                "尽量使用镜像源（可能缺少刚刚更新的版本）",
                "优先使用官方源，在加载缓慢时换用镜像源", "尽量使用官方源"},
                data.getInt("SystemVersionSource", 1), i -> data.set("SystemVersionSource", i));
        textRow(download, "自定义镜像根", "留空用内置源", data.get("SystemBmclRoot", ""), s -> data.set("SystemBmclRoot", s == null ? "" : s.trim()));
        Label bmclHint = Ui.label("填自建或内网的 BMCLAPI 兼容镜像根（如 http://192.168.1.10:8080），版本清单 / 文件 / 资源 / 安装器会全部走这个根；适合 Win7 老电脑或公司内网环境。留空则用官方 + 内置 BMCLAPI。", 12, Theme.gray3);
        bmclHint.setWrapText(true);
        download.add(bmclHint);
        textRow(download, "最大线程数", "64", data.get("SystemMaxThreads", "64"), s -> data.set("SystemMaxThreads", s));
        textRow(download, "速度限制", "不限", data.get("SystemSpeedLimit", ""), s -> data.set("SystemSpeedLimit", s));
        // 目标文件夹只读，改入口在实例页左侧的文件夹列表
        Label target = Ui.label("请在 实例页左侧「文件夹列表」中更改下载目标文件夹。"
                + "在某个文件夹或游戏版本上右键，即可选择打开对应文件夹。", 12, Theme.gray3);
        target.setWrapText(true);
        download.add(Ui.settingRow("目标文件夹", target));

        Ui.Card communityRes = addCard(page, "社区资源");
        textRow(communityRes, "CurseForge Key", "默认", data.get("SystemCurseKey", ""),
                s -> data.set("SystemCurseKey", s));
        communityRes.add(Ui.label("留空时使用内置公共 Key；OptiFabric 等 CurseForge 资源需要有效 Key 才能获取列表。",
                12, Theme.gray3));

        Ui.Card proxy = addCard(page, "网络");
        Ui.RadioBox none = Ui.radioBox("不代理", false);
        Ui.RadioBox system = Ui.radioBox("系统代理", false);
        Ui.RadioBox custom = Ui.radioBox("自定义代理", false);
        int mode = data.getInt("SystemHttpProxyType", 1);
        (mode == 0 ? none : mode == 2 ? custom : system).setSelected(true);
        HBox radios = new HBox(18, none, system, custom);
        proxy.add(radios);
        textRow(proxy, "代理地址", "例如：127.0.0.1:1080", data.get("SystemProxyAddress", ""), s -> data.set("SystemProxyAddress", s));
        textRow(proxy, "账号", "可选", data.get("SystemProxyUser", ""), s -> data.set("SystemProxyUser", s));
        textRow(proxy, "密码", "可选", data.get("SystemProxyPass", ""), s -> data.set("SystemProxyPass", s));
        none.setOnAction(() -> {
            data.set("SystemHttpProxyType", 0);
            data.applyProxy();
        });
        system.setOnAction(() -> {
            data.set("SystemHttpProxyType", 1);
            data.applyProxy();
        });
        custom.setOnAction(() -> {
            data.set("SystemHttpProxyType", 2);
            data.applyProxy();
        });
        proxy.add(directCheck("启动游戏时使用此代理", "SystemHttpProxyForGame", false));

        Ui.Card language = addCard(page, "辅助功能");
        ComboBox<String> lang = Ui.combo(new String[]{"简体中文", "English", "繁體中文"}, Lang.mode);
        lang.setOnAction(e -> {
            int index = lang.getSelectionModel().getSelectedIndex();
            if (index == Lang.mode) return;
            Lang.setMode(index);
            data.set("SystemLanguage", index);
            win.rebuildUi();
            win.toast("语言已切换", Lang.langName(), 1);
        });
        language.add(Ui.settingRow("语言", lang));
        FlowPane i18n = new FlowPane(10, 10);
        java.nio.file.Path i18nFile = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx", "i18n.txt");
        i18n.getChildren().addAll(
                Ui.button("导出翻译文件", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(i18nFile.getParent());
                        Files.copy(Theme.res("LangTables.txt"), i18nFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        win.toast("翻译文件", "已导出到 " + i18nFile, 1);
                    } catch (Exception e) {
                        win.toast("导出失败", e.getMessage(), 3);
                    }
                }),
                Ui.button("重载翻译", Ui.BTN_NORMAL, () -> {
                    Lang.reload();
                    win.rebuildUi();
                    win.toast("翻译已重载", "界面文案已更新", 1);
                }),
                Ui.button("打开文件", Ui.BTN_NORMAL, () -> {
                    try {
                        if (!Files.exists(i18nFile)) {
                            Files.createDirectories(i18nFile.getParent());
                            Files.copy(Theme.res("LangTables.txt"), i18nFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        Shell.openFile(i18nFile.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }));
        language.add(i18n);

        Ui.Card other = addCard(page, "启动器");
        FlowPane dataBtns = new FlowPane(10, 10);
        dataBtns.getChildren().addAll(
                Ui.button("导出全部数据", Ui.BTN_NORMAL, () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("导出全部数据");
                    chooser.setInitialFileName("tcl-config.properties");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("配置文件", "*.properties"));
                    File file = chooser.showSaveDialog(win.stage);
                    if (file != null) {
                        boolean ok = data.exportConfig(file.toPath());
                        win.toast(ok ? "导出完成" : "导出失败", file.getName(), ok ? 1 : 3);
                    }
                }),
                Ui.button("导入全部数据", Ui.BTN_NORMAL, () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("导入全部数据");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("配置文件", "*.properties"));
                    File file = chooser.showOpenDialog(win.stage);
                    if (file != null) {
                        boolean ok = data.importConfig(file.toPath());
                        if (ok) {
                            win.rebuildUi();
                            win.toast("导入完成", "配置已重新加载", 1);
                        } else {
                            win.toast("导入失败", file.getName(), 3);
                        }
                    }
                }),
                Ui.button("复制识别码", Ui.BTN_NORMAL, () -> {
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(Data.offlineUuid(System.getProperty("user.name")));
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    win.toast("已复制识别码", Data.offlineUuid(System.getProperty("user.name")), 1);
                }));
        other.add(dataBtns);

        communityCard(page);
        uninstallCard(page);
    }

    /** 社区卡片。 */
    private void communityCard(VBox page) {
        Ui.Card community = addCard(page, "社区");
        community.add(Ui.hint("有问题反馈？想获取更新？加入我们的启动器交流群吧！", 0));
        community.add(standaloneButton("加入 QQ 群", Ui.BTN_HIGHLIGHT, 150, this::openCommunity));
    }

    /** 不撑满整行的按钮。 */
    private StackPane standaloneButton(String text, int type, int minWidth, Runnable action) {
        StackPane btn = Ui.button(text, type, action);
        btn.setMinWidth(minWidth);
        btn.setMaxWidth(Region.USE_PREF_SIZE);
        return btn;
    }

    /** 官方 QQ 群邀请链接。 */
    private void openCommunity() {
        if (!Shell.openUrl("https://qm.qq.com/q/UONlv4VgwE")) {
            win.toast("打开链接失败", "https://qm.qq.com/q/UONlv4VgwE", 3);
        }
    }

    /** 卸载启动器数据，先停 EasyTier 再删。 */
    private void uninstallCard(VBox page) {
        Ui.Card danger = addCard(page, "卸载");
        Path appData = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
        Label desc = Ui.label("仅清理启动器自建的内容（配置、日志、缓存、联机内核），不会删除游戏目录、存档与已安装的 Minecraft。"
                + "确认后停止联机内核并退出；便携版程序文件需手动删除。", 12, Theme.gray2);
        desc.setWrapText(true);
        danger.add(desc);
        danger.add(standaloneButton("卸载 TCraft Launcher", Ui.BTN_RED, 170, () ->
                Dialogs.confirm(win, "确认卸载 TCraft Launcher",
                        "即将删除：\n· " + appData + "（配置、日志、缓存、联机内核）\n\n"
                                + "游戏目录与存档不会被触碰。此操作不可撤销，确认后启动器立即退出。",
                        () -> {
                            // 停掉残留进程，释放内核二进制
                            try {
                                EasyTier.killLeftovers();
                                EasyTier.stop();
                            } catch (Throwable ignored) {
                            }
                            // 趁 JVM 还在时删除，失败项收集起来
                            List<String> failed = new ArrayList<>();
                            deleteDirCollect(appData, failed);
                            if (!failed.isEmpty()) {
                                Errors.error("卸载：有 " + failed.size() + " 项删除失败（详见 errors.log）",
                                        new RuntimeException("删除失败：" + String.join("；", failed)),
                                        Errors.Behavior.NONE);
                                Platform.runLater(() -> win.toast("部分文件删除失败",
                                        "已记录到日志，请重启系统后重试", 3));
                            }
                            Platform.exit();
                            Thread t = new Thread(() -> {
                                try {
                                    Thread.sleep(500);
                                } catch (Exception ignored) {
                                }
                                System.exit(0);
                            }, "TCL-Uninstall");
                            t.setDaemon(true);
                            t.start();
                        }, true)));
    }

    private void buildJava(VBox page) {
        header(page, "Java", "管理可用的 Java 运行时，并设置优先级。");
        Ui.Card manage = addCard(page, "Java 管理");
        Label intro = Ui.label("TCL 需要 Java 才能启动游戏。列表中的第一个可用 Java 将被默认使用，"
                + "点击列表项可切换当前使用的 Java。", 12.5, Theme.gray2);
        intro.setWrapText(true);
        manage.add(intro);
        Label status = Ui.label(data.javaPath.isEmpty() ? "当前：未找到 Java" : "当前：" + data.javaPath,
                12, Theme.gray3);
        status.setWrapText(true);
        manage.add(status);
        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(
                Ui.button("导入 Java…", Ui.BTN_HIGHLIGHT, () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("选择 javaw.exe");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java 可执行文件", "javaw.exe", "java.exe"));
                    File file = chooser.showOpenDialog(win.stage);
                    if (file != null) {
                        data.javaPath = file.getAbsolutePath();
                        data.save();
                        win.refreshRight();
                        win.toast("Java 已导入", data.javaPath, 1);
                    }
                }),
                Ui.button("下载 Java…", Ui.BTN_NORMAL, () -> {
                    String url = "https://learn.microsoft.com/java/openjdk/download#openjdk-17";
                    if (!Shell.openUrl(url)) {
                        win.toast("打开链接失败", url, 3);
                    }
                }),
                Ui.button("重新搜索", Ui.BTN_NORMAL, () -> {
                    data.javaPath = data.detectJava();
                    data.save();
                    win.refreshRight();
                    win.toast("搜索完成", data.javaPath.isEmpty() ? "未找到 Java" : data.javaPath,
                            data.javaPath.isEmpty() ? 2 : 1);
                }),
                Ui.button("打开数据文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Path dir = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
                        Files.createDirectories(dir);
                        Shell.openFolder(dir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开文件夹", e.getMessage(), 3);
                    }
                }),
                Ui.button("初始化此项", Ui.BTN_RED, () -> {
                    data.javaPath = data.detectJava();
                    data.save();
                    win.refreshRight();
                    win.toast("已初始化", "Java 列表已恢复为自动搜索结果", 1);
                }));
        manage.add(actions);

        Ui.Card found = addCard(page, "已发现的 Java");
        List<String> javas = findJavas();
        VBox list = new VBox(2);
        for (String path : javas) {
            boolean selected = path.equals(data.javaPath);
            Ui.ListItem item = new Ui.ListItem(Theme.icon("coffee", 17, selected ? Theme.accent : Theme.text),
                    shortJavaName(path), path, 40);
            item.setSelected(selected);
            item.setOnMouseClicked(e -> {
                if (data.javaPath.equals(path)) return;
                data.javaPath = path;
                data.save();
                win.refreshRight();
                win.toast("Java 已选择", path, 1);
            });
            list.getChildren().add(item);
        }
        if (javas.isEmpty()) list.getChildren().add(Ui.label("没有检测到 Java，请手动选择 javaw.exe。", 12, Theme.gray3));
        found.add(list);
    }

    private List<String> findJavas() {
        List<String> out = new ArrayList<>();
        String detected = data.detectJava();
        if (!detected.isEmpty()) out.add(detected);
        if (!data.javaPath.isEmpty() && !out.contains(data.javaPath)) out.add(0, data.javaPath);
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean win = os.contains("win");
        String exe = win ? "javaw.exe" : "java";
        String[] roots = win
                ? new String[]{"C:\\Program Files\\Java", "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files\\BellSoft",
                "C:\\Program Files\\Zulu", "C:\\Program Files (x86)\\Java"}
                : new String[]{"/usr/lib/jvm", "/usr/local/openjdk", "/opt/homebrew/opt",
                "/usr/local/opt", "/Library/Java/JavaVirtualMachines"};
        for (String r : roots) {
            Path base = Paths.get(r);
            if (!Files.isDirectory(base)) continue;
            try (var stream = Files.list(base)) {
                stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).forEach(p -> {
                    Path homeDir = win ? p : (Files.isDirectory(p.resolve("Contents/Home")) ? p.resolve("Contents/Home") : p);
                    Path exePath = homeDir.resolve("bin").resolve(exe);
                    if (Files.isRegularFile(exePath) && !out.contains(exePath.toString())) out.add(exePath.toString());
                });
            } catch (Exception ignored) {
            }
        }
        if (!win) {
            for (String c : new String[]{"/usr/bin/java", "/usr/local/bin/java"}) {
                if (new java.io.File(c).canExecute() && !out.contains(c)) out.add(c);
            }
        }
        return out;
    }

    private String shortJavaName(String path) {
        try {
            String version = path.contains("jdk-") ? path.substring(path.indexOf("jdk-")) : "Java";
            if (version.contains("\\")) version = version.substring(0, version.indexOf('\\'));
            return version + "  ·  " + Paths.get(path).getParent().getParent().getFileName();
        } catch (Exception e) {
            return "Java";
        }
    }

    /** Color 转 #rrggbb，给内联 CSS 用。 */
    private static String toHex(javafx.scene.paint.Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void buildLog(VBox page) {
        header(page, "日志", "查看、导出与清理运行日志。");
        Path logDir = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
        Path logFile = logDir.resolve("launcher.log");

        Ui.Card ops = addCard(page, "日志操作");
        FlowPane buttons = new FlowPane(10, 10);
        buttons.getChildren().addAll(
                Ui.button("导出当前日志", Ui.BTN_HIGHLIGHT, () -> exportLog(logFile, false)),
                Ui.button("导出全部日志", Ui.BTN_NORMAL, () -> exportLog(logFile, true)),
                Ui.button("打开日志文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(logDir);
                        Shell.openFolder(logDir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开文件夹", e.getMessage(), 3);
                    }
                }),
                Ui.button("清理历史日志", Ui.BTN_RED, () ->
                        Dialogs.confirm(win, "清理历史日志", "将删除 " + logFile + " 并清空当前日志显示。是否继续？", () -> {
                            try {
                                Files.deleteIfExists(logFile);
                            } catch (Exception ignored) {
                            }
                            data.log.clear();
                            win.refreshRight();
                            win.toast("日志已清理", "", 1);
                        }, true)));
        ops.add(buttons);
        ops.add(directCheck("调试模式（排查问题时开启，会把日志同步输出到控制台）", "SystemDebugMode", false));

        Ui.Card live = addCard(page, "实时日志");
        ComboBox<String> level = Ui.combo(new String[]{"全部级别", "警告及错误", "仅错误"}, 0);
        TextField filter = Ui.field("搜索日志内容");
        Ui.Check autoScroll = Ui.checkBox("自动滚动", true);
        HBox controls = new HBox(10, level, filter, autoScroll);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filter, Priority.ALWAYS);
        live.add(controls);
        TextArea view = new TextArea();
        view.setEditable(false);
        view.setWrapText(true);
        view.setPrefHeight(300);
        // 不设 -fx-text-fill 会继承主题色，浅色卡片上变白字
        view.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei UI'; -fx-font-size: 11.5;"
                + " -fx-control-inner-background: transparent;"
                + " -fx-background-color: transparent;"
                + " -fx-text-fill: " + toHex(Theme.text) + ";"
                + " -fx-highlight-fill: " + toHex(Theme.accent) + ";"
                + " -fx-highlight-text-fill: #ffffff;"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        live.add(view);
        Label status = Ui.label("实时监听 launcher.log…", 11.5, Theme.gray3);
        live.add(status);
        final Timeline[] tick = new Timeline[1];
        tick[0] = new Timeline(new KeyFrame(javafx.util.Duration.millis(600), e -> {
            if (page.getScene() == null) {
                tick[0].stop();
                return;
            }
            String[] lines = readLogLines(logFile);
            String text = filterLog(lines, level.getSelectionModel().getSelectedIndex(), filter.getText());
            if (!text.equals(view.getText())) {
                view.setText(text);
                if (autoScroll.isSelected()) view.setScrollTop(Double.MAX_VALUE);
            }
            status.setText("实时监听 " + logFile.getFileName() + " · " + lines.length + " 行");
        }));
        tick[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
        tick[0].play();

        Ui.Card all = addCard(page, "全部日志");
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(logDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".log"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(files::add);
        } catch (Exception ignored) {
        }
        if (files.isEmpty()) {
            all.add(Ui.label("暂无日志文件", 12, Theme.gray3));
        } else {
            for (Path f : files) {
                Ui.ListItem row = new Ui.ListItem(Theme.icon("scroll-text", 16, Theme.text),
                        f.getFileName().toString(), bytes(fileSize(f)) + " · 点击打开", 36);
                row.setOnMouseClicked(ev -> openLogFile(f));
                all.add(row);
            }
        }
    }

    private static long fileSize(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0;
        }
    }

    private void openLogFile(Path p) {
        try {
            Shell.openFile(p.toString());
        } catch (Exception e) {
            win.toast("无法打开", e.getMessage(), 3);
        }
    }

    /** 读日志尾部 500 行，读不到就用内存日志。 */
    private String[] readLogLines(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                List<String> lines = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
                if (lines.size() > 500) lines = lines.subList(lines.size() - 500, lines.size());
                return lines.toArray(new String[0]);
            }
        } catch (Exception ignored) {
        }
        return data.log.toArray(new String[0]);
    }

    private String filterLog(String[] lines, int level, String query) {
        StringBuilder sb = new StringBuilder();
        String q = query == null ? "" : query.toLowerCase();
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (level == 1 && !(lower.contains("warn") || lower.contains("error") || lower.contains("失败"))) continue;
            if (level == 2 && !(lower.contains("error") || lower.contains("失败"))) continue;
            if (!q.isEmpty() && !lower.contains(q)) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** 导出当前会话日志或完整 launcher.log。 */
    private void exportLog(Path source, boolean all) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(all ? "导出全部日志" : "导出当前日志");
        chooser.setInitialFileName(all ? "launcher-full.log" : "launcher-current.log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("日志文件", "*.log"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        try {
            String content = all && Files.isRegularFile(source)
                    ? Files.readString(source, java.nio.charset.StandardCharsets.UTF_8)
                    : String.join("\n", data.log);
            Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            win.toast("导出完成", file.getName(), 1);
        } catch (Exception e) {
            win.toast("导出失败", e.getMessage(), 3);
        }
    }

    private void buildCache(VBox page) {
        header(page, "存储与清理", "看清启动器与游戏各自的磁盘占用，安全释放空间。");
        Path base = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
        Path cacheDir = base.resolve("cache");

        Ui.Card overview = addCard(page, "存储总览");
        Label note = Ui.label("后台计算中…", 12, Theme.gray3);
        overview.add(note);
        VBox bars = new VBox(8);
        overview.add(bars);

        Ui.Card usage = addCard(page, "缓存占用");
        Label totalLabel = Ui.bold("统计中…", 14, Theme.text);
        usage.add(totalLabel);
        Ui.Check cacheCheck = Ui.checkBox("启动器下载缓存", true);
        Ui.Check logCheck = Ui.checkBox("临时日志文件", false);
        Ui.Check tempCheck = Ui.checkBox("临时文件（.tmp / .part）", true);
        VBox list = new VBox(4);
        usage.add(list);

        // 只清可再生缓存，不碰配置、背景、音乐和存档
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> new Thread(() -> {
            long game = dirSize(Paths.get(data.gameDir), 30000);
            long cache = dirSize(cacheDir, 20000);
            long logs = logSize(base);
            long temp = tempSize(cacheDir);
            Platform.runLater(() -> {
                bars.getChildren().setAll(
                        sizeBar("游戏目录", game, game + cache + logs),
                        sizeBar("启动器缓存", cache, game + cache + logs),
                        sizeBar("日志文件", logs, game + cache + logs));
                note.setText("仅统计磁盘占用；清理操作只作用于下方勾选的可再生缓存。");
                totalLabel.setText("可清理合计 " + bytes(cache + temp + logs));
                list.getChildren().setAll(
                        cacheRow("启动器下载缓存", cacheCheck, cache),
                        cacheRow("临时日志文件", logCheck, logs),
                        cacheRow("临时文件（.tmp / .part）", tempCheck, temp));
            });
        }, "TCL-Cache-Scan").start();
        refresh[0].run();

        Ui.Card ops = addCard(page, "操作");
        Label desc = Ui.label("只清理可重新生成或重新下载的内容；不会删除你的配置、背景与音乐、皮肤、"
                + "翻译文件或游戏数据。", 12, Theme.gray2);
        desc.setWrapText(true);
        ops.add(desc);
        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(
                Ui.button("清理所选缓存", Ui.BTN_RED, () ->
                        Dialogs.confirm(win, "清理所选缓存",
                                "将删除：\n· " + (cacheCheck.isSelected() ? cacheDir : "（跳过）启动器下载缓存")
                                        + "\n· " + (logCheck.isSelected() ? "临时日志文件" : "（跳过）临时日志文件")
                                        + "\n· " + (tempCheck.isSelected() ? "临时文件（.tmp / .part）" : "（跳过）临时文件")
                                        + "\n\n不会删除游戏存档与已安装版本。是否继续？",
                                () -> {
                                    long freed = 0;
                                    if (cacheCheck.isSelected()) {
                                        freed += dirSize(cacheDir, 20000);
                                        deleteDir(cacheDir);
                                    }
                                    if (tempCheck.isSelected()) freed += deleteTemp(cacheDir);
                                    if (logCheck.isSelected()) freed += deleteLogs(base);
                                    win.toast("清理完成", "已释放 " + bytes(freed), 1);
                                    if (refresh[0] != null) refresh[0].run();
                                }, true)),
                Ui.button("检查无用文件", Ui.BTN_NORMAL, () -> new Thread(() -> {
                    long[] result = scanJunk(cacheDir, base);
                    Platform.runLater(() -> win.toast("检查完成",
                            result[0] == 0 ? "没有发现无用文件"
                                    : "发现 " + result[0] + " 个无用文件，共 " + bytes(result[1]),
                            result[0] == 0 ? 1 : 0));
                }, "TCL-Junk-Scan").start()),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> {
                    if (refresh[0] != null) refresh[0].run();
                }),
                Ui.button("打开缓存目录", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(cacheDir);
                        Shell.openFolder(cacheDir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }));
        ops.add(actions);
    }

    private HBox cacheRow(String title, Ui.Check box, long size) {
        Label lab = Ui.label(title + "    " + bytes(size), 12.5, Theme.text);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(10, lab, sp, box);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox sizeBar(String title, long value, long total) {
        Label lab = Ui.label(title + "    " + bytes(value), 12, Theme.gray2);
        ProgressBar bar = new ProgressBar(Math.max(0.01, Math.min(1, value / (double) Math.max(1, total))));
        bar.setPrefHeight(7);
        bar.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(3, lab, bar);
        HBox.setHgrow(box, Priority.ALWAYS);
        return new HBox(box);
    }

    private long logSize(Path base) {
        if (!Files.isDirectory(base)) return 0;
        try (var s = Files.list(base)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".log"))
                    .mapToLong(PageSetup::fileSize).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private long tempSize(Path cacheDir) {
        if (!Files.isDirectory(cacheDir)) return 0;
        try (var s = Files.walk(cacheDir, 4)) {
            return s.filter(Files::isRegularFile).filter(this::isTempFile)
                    .mapToLong(PageSetup::fileSize).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isTempFile(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".tmp") || n.endsWith(".part");
    }

    /** 30 天没改、0 字节或 .tmp/.part 的文件。 */
    private long[] scanJunk(Path cacheDir, Path base) {
        long count = 0, size = 0;
        long cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        for (Path root : new Path[]{cacheDir, base}) {
            if (!Files.isDirectory(root)) continue;
            try (var s = Files.walk(root, 4)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    boolean junk = isTempFile(p);
                    try {
                        if (Files.size(p) == 0 || Files.getLastModifiedTime(p).toMillis() < cutoff) junk = true;
                    } catch (Exception ignored) {
                    }
                    if (junk) {
                        count++;
                        size += fileSize(p);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new long[]{count, size};
    }

    private long deleteTemp(Path cacheDir) {
        if (!Files.isDirectory(cacheDir)) return 0;
        long freed = 0;
        try (var s = Files.walk(cacheDir, 4)) {
            for (Path p : s.filter(Files::isRegularFile).filter(this::isTempFile).toList()) {
                freed += fileSize(p);
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return freed;
    }

    private long deleteLogs(Path base) {
        if (!Files.isDirectory(base)) return 0;
        long freed = 0;
        try (var s = Files.list(base)) {
            for (Path p : s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".log")).toList()) {
                freed += fileSize(p);
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return freed;
    }

    private long dirSize(Path dir, int maxFiles) {
        if (!Files.isDirectory(dir)) return 0;
        final long[] size = {0};
        final int[] count = {0};
        try (var stream = Files.walk(dir, 3)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                if (count[0]++ > maxFiles) return;
                try {
                    size[0] += Files.size(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return size[0];
    }

    /** 递归删目录，失败项收进 failedSink。 */
    private static java.util.List<String> deleteDirCollect(Path dir, java.util.List<String> failedSink) {
        if (!Files.isDirectory(dir)) return failedSink;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    failedSink.add(p.toString());
                }
            }
        } catch (Exception e) {
            failedSink.add(dir.toString() + "（目录遍历失败）");
        }
        return failedSink;
    }

    /** 尽力而为地删，失败忽略。 */
    private void deleteDir(Path dir) {
        deleteDirCollect(dir, new java.util.ArrayList<>());
    }

    private String bytes(long b) {
        if (b > 1024L * 1024 * 1024) return String.format("%.2f GB", b / 1024.0 / 1024 / 1024);
        if (b > 1024L * 1024) return String.format("%.1f MB", b / 1024.0 / 1024);
        if (b > 1024) return String.format("%.1f KB", b / 1024.0);
        return b + " B";
    }

    /** 三项界面选项映射到 0 自动、1 官方、2 镜像优先。 */
    private static final int[] FILE_SOURCES = {2, 0, 1};

    private void buildNetwork(VBox page) {
        header(page, "网络", "代理、下载源连通性与测速。");
        Ui.Card proxy = addCard(page, "代理");
        String[] modes = {"不使用代理", "跟随系统代理", "HTTP 代理", "SOCKS5 代理"};
        int mode = Math.max(0, Math.min(3, data.getInt("SystemHttpProxyType", 1)));
        ComboBox<String> modeCombo = Ui.combo(modes, mode);
        modeCombo.setOnAction(e -> {
            int i = modeCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            data.set("SystemHttpProxyType", i);
            data.applyProxy();
        });
        proxy.add(Ui.settingRow("代理模式", modeCombo));
        TextField host = textRow(proxy, "主机", "例如 127.0.0.1", data.get("SystemProxyHost", ""),
                s -> data.set("SystemProxyHost", s));
        TextField port = textRow(proxy, "端口", "7890", data.get("SystemProxyPort", "7890"),
                s -> data.set("SystemProxyPort", s));
        textRow(proxy, "用户名", "可选", data.get("SystemProxyUser", ""), s -> data.set("SystemProxyUser", s));
        textRow(proxy, "密码", "可选", data.get("SystemProxyPass", ""), s -> data.set("SystemProxyPass", s));
        Label proxyNote = Ui.label("", 12, Theme.gray3);
        proxyNote.setWrapText(true);
        proxy.add(proxyNote);
        Label socksHint = Ui.label("提示：SOCKS5 只对游戏进程生效，启动器自身的下载 / 搜索走的是 HTTP 代理通道"
                + "（Clash、v2rayN 的混合端口都同时支持 HTTP，填那个端口即可）。", 12, Theme.gray3);
        socksHint.setWrapText(true);
        proxy.add(socksHint);
        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(
                Ui.button("保存", Ui.BTN_HIGHLIGHT, () -> {
                    data.applyProxy();
                    data.save();
                    proxyNote.setText(Data.get().describeProxy());
                    win.toast("代理设置", "已保存并应用", 1);
                }),
                Ui.button("测试当前代理", Ui.BTN_NORMAL, () -> {
                    proxyNote.setText("正在测试代理…");
                    new Thread(() -> {
                        String result = testProxy();
                        Platform.runLater(() -> {
                            proxyNote.setText(result);
                            win.toast(result.startsWith("连接成功") ? "代理可用" : "代理不可用", result,
                                    result.startsWith("连接成功") ? 1 : 3);
                        });
                    }, "TCL-Proxy-Test").start();
                }));
        proxy.add(actions);
        proxyNote.setText(Data.get().describeProxy());

        Ui.Card speed = addCard(page, "下载源测速");
        Label speedNote = Ui.label("测速结果只代表当前网络到该源的连通质量，不影响已下载的内容。", 12, Theme.gray3);
        speedNote.setWrapText(true);
        speed.add(speedNote);
        // 自定义源与系统页共用 SystemBmclRoot
        TextField customRoot = textRow(speed, "自定义源", "留空用内置源", data.get("SystemBmclRoot", ""),
                s -> data.set("SystemBmclRoot", s == null ? "" : s.trim()));
        Label customHint = Ui.label("填自建或内网的 BMCLAPI 兼容镜像根（如 http://192.168.1.10:8080），"
                + "填了会一起测速；下载时「镜像优先」会自动走它。留空则只测官方源与内置 BMCLAPI。", 12, Theme.gray3);
        customHint.setWrapText(true);
        speed.add(customHint);
        String[] sources = {"官方源 (piston-meta.mojang.com)", "BMCLAPI (bmclapi2.bangbang93.com)", "自定义源"};
        // 对应 2 官方、0 镜像和自定义
        int[] wpfIndex = {2, 0};
        VBox results = new VBox(2);
        Label[] values = new Label[sources.length];
        Label[] names = new Label[sources.length];
        HBox[] rows = new HBox[sources.length];
        for (int i = 0; i < sources.length; i++) {
            Label value = Ui.label("— ms", 12, Theme.gray3);
            values[i] = value;
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinHeight(32);
            Label name = Ui.label(sources[i], 12.5, Theme.text);
            names[i] = name;
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            row.getChildren().addAll(name, sp, value);
            rows[i] = row;
            results.getChildren().add(row);
        }
        // 自定义源没填就隐藏第 3 行
        Runnable syncCustomRow = () -> {
            String c = customRoot.getText() == null ? "" : customRoot.getText().trim();
            rows[2].setVisible(!c.isEmpty());
            names[2].setText("自定义源 (" + (c.isEmpty() ? "未设置" : c) + ")");
        };
        customRoot.textProperty().addListener((o, a, b) -> syncCustomRow.run());
        syncCustomRow.run();
        speed.add(results);
        Label summary = Ui.label("点「开始测速」检查各下载源的连通质量。", 12, Theme.gray3);
        speed.add(summary);
        final int[] recommended = {-1};
        final boolean[] tested = {false};
        final int[] customOn = {0}; // 1 = 测速时自定义源参与
        FlowPane speedActions = new FlowPane(10, 10);
        StackPane applyBtn = Ui.button("应用推荐源", Ui.BTN_NORMAL, () -> {
            if (!tested[0] || recommended[0] < 0) {
                win.toast("请先测速", "还没有可用的测速结果，点「开始测速」后再应用", 0);
                return;
            }
            int idx = recommended[0];
            if (idx >= 2) {
                // 自定义源走镜像优先，Downloader 会读 SystemBmclRoot
                data.set("SystemDownloadSource", 2);
                data.set("SystemVersionSource", 0);
                win.toast("已应用", "下载源已切换为 " + names[2].getText(), 1);
            } else {
                int value = FILE_SOURCES[wpfIndex[idx]];
                data.set("SystemDownloadSource", value);
                data.set("SystemVersionSource", wpfIndex[idx]);
                win.toast("已应用", "下载源已切换为 " + sources[idx], 1);
            }
            data.save();
        });
        final StackPane[] testBtn = new StackPane[1];
        testBtn[0] = Ui.button("开始测速", Ui.BTN_HIGHLIGHT, () -> {
            summary.setText("测速中…（单次超时 5 秒）");
            String raw = customRoot.getText() == null ? "" : customRoot.getText().trim();
            while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
            final String customUrl;
            if (raw.isEmpty()) customUrl = "";
            else if (!raw.startsWith("http://") && !raw.startsWith("https://")) customUrl = "https://" + raw;
            else customUrl = raw;
            String[] liveUrls = {
                    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                    "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
                    customUrl + "/mc/game/version_manifest_v2.json"
            };
            testBtn[0].setDisable(true);
            for (int i = 0; i < values.length; i++) {
                values[i].setText("— ms");
                values[i].setTextFill(Theme.gray3);
            }
            new Thread(() -> {
                long[] ms = new long[liveUrls.length];
                customOn[0] = customUrl.isEmpty() ? 0 : 1;
                for (int i = 0; i < liveUrls.length; i++) {
                    if (i == 2 && customUrl.isEmpty()) { ms[i] = -2; continue; } // -2 = 未参与
                    long start = System.currentTimeMillis();
                    try {
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                java.net.URI.create(liveUrls[i]).toURL().openConnection();
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestMethod("HEAD");
                        conn.getResponseCode();
                        conn.disconnect();
                        ms[i] = System.currentTimeMillis() - start;
                    } catch (Exception e) {
                        ms[i] = -1;
                    }
                }
                int best = -1;
                for (int i = 0; i < ms.length; i++) {
                    if (ms[i] >= 0 && (best < 0 || ms[i] < ms[best])) best = i;
                }
                recommended[0] = best;
                tested[0] = true;
                final int bestIndex = best;
                final String bestName = bestIndex < 0 ? "" : sources[bestIndex];
                Platform.runLater(() -> {
                    for (int i = 0; i < values.length; i++) {
                        if (i == 2 && customOn[0] == 0) {
                            values[i].setText("未设置");
                            values[i].setTextFill(Theme.gray3);
                            continue;
                        }
                        values[i].setText(ms[i] < 0 ? "超时" : ms[i] + " ms");
                        values[i].setTextFill(ms[i] < 0 ? Theme.red
                                : ms[i] < 100 ? Theme.green : ms[i] < 300 ? Theme.orange : Theme.red);
                    }
                    summary.setText(bestIndex < 0 ? "全部下载源均不可用，请检查网络或代理设置。"
                            : "推荐：" + bestName);
                    testBtn[0].setDisable(false);
                });
            }, "TCL-SourceTest").start();
        });
        speedActions.getChildren().addAll(testBtn[0], applyBtn);
        speed.add(speedActions);
    }

    /** 请求官方版本清单，测当前代理延迟。 */
    private String testProxy() {
        Data d = Data.get();
        int mode = d.getInt("SystemHttpProxyType", 1);
        String host = d.get("SystemProxyHost", "");
        String port = d.get("SystemProxyPort", "7890");
        java.net.Proxy proxy;
        try {
            if (mode == 0 || mode == 1) {
                proxy = null; // 无代理或跟随系统，由 JVM 系统属性决定
            } else if (mode == 3) {
                proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                        new java.net.InetSocketAddress(host, Integer.parseInt(port)));
            } else {
                proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(host, Integer.parseInt(port)));
            }
        } catch (Exception e) {
            return "代理参数无效：" + e.getMessage();
        }
        long start = System.currentTimeMillis();
        try {
            java.net.URL probe = java.net.URI
                .create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL();
        java.net.HttpURLConnection conn = mode == 0
                ? (java.net.HttpURLConnection) probe.openConnection(java.net.Proxy.NO_PROXY)
                : mode == 1 ? (java.net.HttpURLConnection) probe.openConnection()
                : (java.net.HttpURLConnection) probe.openConnection(proxy);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            conn.disconnect();
            return "连接成功，HTTP " + code + "，延迟 " + (System.currentTimeMillis() - start) + " ms";
        } catch (Exception e) {
            return "连接失败：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
        }
    }

    private void buildMore(VBox page) {
        header(page, "更多", "社区与启动器维护。");
        communityCard(page);
        uninstallCard(page);
    }

    private void comboRow(Ui.Card card, String label, String[] items, int selected, Consumer<Integer> onChange) {
        ComboBox<String> combo = Ui.combo(items, selected);
        // 占满右侧整列，长文案不截断
        combo.setPrefWidth(220);
        combo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(combo, Priority.ALWAYS);
        combo.setOnAction(e -> {
            int i = combo.getSelectionModel().getSelectedIndex();
            if (i >= 0 && i < items.length) onChange.accept(i);
        });
        Label name = Ui.label(label, 13, Theme.text);
        name.setMinWidth(150);
        HBox row = new HBox(12, name, combo);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(38);
        card.add(row);
    }

    private TextField textRow(Ui.Card card, String label, String hint, String value, Consumer<String> onChange) {
        TextField field = Ui.field(hint);
        field.setText(value == null ? "" : value);
        field.setPrefWidth(260);
        field.textProperty().addListener((o, a, b) -> onChange.accept(b));
        card.add(Ui.settingRow(label, field));
        return field;
    }

    private Ui.SliderBar sliderRow(Ui.Card card, String label, double min, double max, double value,
                                   Consumer<Double> onChange, String suffix) {
        Ui.SliderBar slider = Ui.sliderBar(min, max, value);
        slider.setPrefWidth(240);
        Label val = Ui.label(Math.round(value) + suffix, 12, Theme.text);
        val.setMinWidth(54);
        slider.setOnChanged(v -> {
            val.setText(Math.round(v) + suffix);
            onChange.accept(v);
        });
        HBox box = new HBox(10, slider, val);
        box.setAlignment(Pos.CENTER_LEFT);
        card.add(Ui.settingRow(label, box));
        return slider;
    }

}
