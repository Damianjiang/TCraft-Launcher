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

public class PageSetup implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private VBox ramDisplayHost;

    public PageSetup(MainWindow win, String section) {
        this.win = win;
        String safe = section == null || section.isEmpty() ? "launch" : section;
        data.setupSection = safe;
        win.pendingSetupSection = safe;
    }

    @Override
    public double leftWidth() {
        return Lang.mode == 1 ? 190 : 160;
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
        List<String[]> visibleSections = new ArrayList<>();
        for (String[] s : sections) if (!setupSectionHidden(s[3])) visibleSections.add(s);
        if (visibleSections.isEmpty()) visibleSections.add(sections[0]);
        boolean sectionVisible = false;
        for (String[] s : visibleSections) if (s[3].equals(data.setupSection)) sectionVisible = true;
        if (!sectionVisible) {
            data.setupSection = visibleSections.get(0)[3];
            win.pendingSetupSection = data.setupSection;
        }
        for (String[] s : visibleSections) items.getChildren().add(navItem(s[1], s[2], s[3], s[0]));

        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.toLowerCase(java.util.Locale.ROOT);
            for (Node n : items.getChildren()) {
                if (n instanceof Ui.ListItem li) {
                    boolean match = q.isEmpty() || li.title.getText().toLowerCase(java.util.Locale.ROOT).contains(q);
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
        if (java.util.Set.of("launch", "link", "ui", "system", "java", "log").contains(key)) {
            StackPane reset = Ui.iconButton("rotate-ccw", 22, 13, "初始化本页设置", () -> resetSection(key));
            reset.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(reset);
        }
        item.setOnMouseClicked(e -> {
            if (key.equals(data.setupSection)) return;
            data.setupSection = key;
            win.pendingSetupSection = key;
            win.refreshRight(true);
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
                data.set("LaunchAdvanceRenderer", 0);
                data.set("LaunchCrashAnalysis", true);
                data.set("LaunchAdvanceDisableCrashAnalysis", false);
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
                data.set("UiMenuAnimation", true);
                data.set("UiListButtonsAlwaysShow", false);
                data.set("UiAnimSpeed", 100);
                data.set("UiTitleAccountBadge", true);
                data.set("UiRememberWindowPos", false);
                data.set("UiBackgroundType", 0);
                data.set("UiBackgroundSuit", 0);
                data.set("UiBackgroundRotate", 0);
                data.set("UiBackgroundColorful", true);
                data.set("UiVolume", 60);
                data.set("UiMusicVolume", 600);
                Theme.applyComponentOpacity();
                win.rebuildUi();
            }
            case "system" -> {
                data.set("SystemDownloadSource", 0);
                data.set("SystemDownloadMode", 0);
                data.set("SystemVersionSource", 1);
                data.set("SystemBmclRoot", "");
                data.set("SystemMaxThreads", "64");
                data.set("SystemSpeedLimit", "");
                data.set("SystemCurseKey", "");
                data.set("SystemMirrorMode", 3);
                data.set("SystemMirrorUrl", "");
                data.set("SystemUpdateMirrorMode", 1);
                data.set("SystemUpdateMirrorUrl", "");
                data.set("SystemHttpProxyType", 1);
                data.set("SystemProxyAddress", "");
                data.set("SystemProxyHost", "");
                data.set("SystemProxyPort", "7890");
                data.set("SystemProxyUser", "");
                data.set("SystemProxyPass", "");
                data.set("SystemHttpProxyForGame", false);
                data.applyProxy();
                data.set("CheckUpdateOnStartup", true);
            }
            case "java" -> {
                data.invalidateJavaScan();
                // detectJava 放后台
                Threads.start("TCL-JavaDetect", () -> {
                    String detected = data.detectJava();
                    javafx.application.Platform.runLater(() -> {
                        data.javaPath = detected;
                        data.save();
                        win.refreshRight();
                    });
                });
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

    private boolean setupSectionHidden(String id) {
        String key = switch (id) {
            case "launch" -> "UiHiddenSetupLaunch";
            case "link" -> "UiHiddenSetupLink";
            case "ui" -> "UiHiddenSetupUi";
            case "system" -> "UiHiddenSetupSystem";
            default -> "";
        };
        return !key.isEmpty() && data.getBool(key, false);
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

    private static final String[] ISOLATION_ITEMS = {
            "关闭", "隔离可安装 Mod 的版本", "隔离非正式版",
            "隔离可安装 Mod 的版本与非正式版", "隔离所有版本"};
    private static final int[] VISIBILITY_VALUES = {0, 2, 3, 4, 5};
    private static final String[] VISIBILITY_ITEMS = {
            "游戏启动后立即关闭", "游戏启动后隐藏，游戏退出后自动关闭",
            "游戏启动后隐藏，游戏退出后重新打开", "游戏启动后最小化", "游戏启动后仍保持不变"};
    private static final String[] PRIORITY_ITEMS = {
            "高（优先保证游戏运行，游戏性能更佳，但可能造成其他程序卡顿）", "中（平衡）",
            "低（优先保证其他程序运行，但可能让游戏卡顿，适合挂机时使用）",
            "极高（比 高 更优先，可能造成整机卡顿）", "实时（最高，可能导致整个系统卡死，极度不推荐）"};
    private static final String[] WINDOW_TYPE_ITEMS = {"全屏", "默认", "与启动器尺寸一致", "自定义尺寸", "最大化"};
    private static final String[] GC_ITEMS = {
            "尽量使用 ZGC", "尽量使用分代 ZGC", "标准 G1GC", "不指定（可自定义）", "调优 G1GC"};

    private void buildLaunch(VBox page) {
        header(page, "启动", "版本、窗口、内存与高级启动选项。");
        Ui.Card arg = addCard(page, "启动选项");
        comboRow(arg, "默认版本隔离", ISOLATION_ITEMS, data.getInt("LaunchIsolation", 4),
                i -> data.set("LaunchIsolation", i));
        arg.add(Ui.hint("开启后每个版本用 versions/版本号 当游戏目录，mods、saves、config 各自独立；"
                + "libraries 与 assets 仍然共用，不会重复占空间。", 0));
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
        if (Shell.windows()) {
            Ui.Check gpu = Ui.checkBox("优先使用独立显卡", false);
            gpu.setSelected(data.getBool("LaunchHighPerformanceGpu", false));
            gpu.setOnAction(() -> data.set("LaunchHighPerformanceGpu", gpu.isSelected()));
            arg.add(gpu);
            arg.add(Ui.hint("仅 Windows 生效，写入系统显卡首选项；笔记本切换独显时可减少核显占用。", 0));
        }

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
        maxRam.setMinWidth(120);
        Label initVal = Ui.bold(initialRamLabel(), 13, Theme.text);
        Ui.SliderBar initRam = Ui.sliderBar(0, data.getInt("LaunchRam", 4096),
                data.getInt("LaunchRamInitial", 0));
        initRam.setPrefWidth(260);
        initRam.setMinWidth(120);
        maxVal.setMinWidth(Region.USE_PREF_SIZE);
        initVal.setMinWidth(Region.USE_PREF_SIZE);
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
            initVal.setText(v == 0 ? Lang.t("自动") : v + " MB");
            data.set("LaunchRamInitial", v);
        });
        HBox maxRow = new HBox(12, maxRam, maxVal);
        HBox initRow = new HBox(12, initRam, initVal);
        maxRow.setAlignment(Pos.CENTER_LEFT);
        initRow.setAlignment(Pos.CENTER_LEFT);
        // 滑条常驻 自动配置时置灰
        mem.add(radios, Ui.settingRow("最大内存", maxRow), Ui.settingRow("初始内存", initRow));
        auto.setOnAction(() -> {
            data.set("LaunchRamType", 0);
            syncRamEnabled.run();
        });
        manual.setOnAction(() -> {
            data.set("LaunchRamType", 1);
            syncRamEnabled.run();
        });

        Ui.Check optimize = Ui.checkBox("启动游戏前进行内存优化", false);
        optimize.setSelected(data.getBool("LaunchOptimizeRam", true));
        optimize.setOnAction(() -> data.set("LaunchOptimizeRam", optimize.isSelected()));
        mem.add(optimize);

        ramDisplayHost = new VBox(0);
        mem.add(ramDisplayHost);
        refreshMemoryDisplay(data.getInt("LaunchRam", 4096));
        final Timeline[] ramTick = new Timeline[1];
        ramTick[0] = new Timeline(new KeyFrame(javafx.util.Duration.seconds(2), e -> {
            if (page.getScene() == null) {
                ramTick[0].stop();
            } else {
                refreshMemoryDisplay(data.getInt("LaunchRam", 4096));
            }
        }));
        ramTick[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
        ramTick[0].play();

        Ui.Card adv = addCard(page, "高级选项");
        adv.setCollapsible(true);
        adv.setCollapsed(true);
        TextArea jvm = new TextArea(data.get("LaunchJvmArgs", ""));
        jvm.setPromptText(Lang.t("留空则使用默认值"));
        jvm.setWrapText(true);
        jvm.setPrefHeight(100);
        jvm.setPrefWidth(360);
        HBox.setHgrow(jvm, Priority.ALWAYS);
        jvm.setStyle("-fx-font-size: 12; -fx-control-inner-background: transparent;");
        jvm.textProperty().addListener((o, a, b) -> data.set("LaunchJvmArgs", b));
        adv.add(Ui.settingRow("Java 虚拟机参数", jvm));
        StackPane restoreJvm = Ui.button("还原为默认", Ui.BTN_NORMAL, () -> {
            jvm.setText("");
            data.set("LaunchJvmArgs", "");
            win.toast("已还原", "JVM 参数已恢复为空（使用游戏默认）", 1);
        });
        HBox jvmActions = new HBox(8, restoreJvm);
        jvmActions.setAlignment(Pos.CENTER_LEFT);
        adv.add(jvmActions);
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

        // 无缓存先占位
        List<String> javas;
        if (data.hasJavaScanCache()) {
            javas = findJavas();
        } else {
            javas = new ArrayList<>(List.of(data.javaPath.isEmpty() ? "未找到 Java" : data.javaPath));
            Threads.start("TCL-JavaScan", () -> {
                data.scanJavaRuntimes();
                javafx.application.Platform.runLater(() -> {
                    if (win.currentKey == null || !win.currentKey.equals("setup")) return;
                    if (!"launch".equals(data.setupSection)) return;
                    win.refreshRight();
                });
            });
        }
        ComboBox<String> javaCombo = Ui.combo(javas.toArray(new String[0]), Math.max(0, javas.indexOf(data.javaPath)));
        javaCombo.setOnAction(e -> {
            String path = javaCombo.getSelectionModel().getSelectedItem();
            if (path == null || path.equals("未找到 Java")) return;
            if (data.javaPath.equals(path)) return;
            // javaMajor 放后台
            win.toast("正在识别 Java 版本…", path, 0);
            Threads.start("TCL-Java-Switch", () -> {
                int major = Data.javaMajor(path);
                javafx.application.Platform.runLater(() -> {
                    if (major <= 0) {
                        win.toast("无法识别该 Java", path, 2);
                        return;
                    }
                    data.setJavaRuntime(path, major);
                    win.toast("Java 已切换", path, 1);
                });
            });
        });
        adv.add(Ui.settingRow("Java 列表", javaCombo));

        comboRow(adv, "内存管理", GC_ITEMS, data.getInt("LaunchAdvanceGC", 4),
                i -> data.set("LaunchAdvanceGC", i));
        comboRow(adv, "IP 栈偏好", new String[]{"IPv4 优先", "Java 默认", "IPv6 优先"},
                data.getInt("LaunchIpStack", 1), i -> data.set("LaunchIpStack", i));
        textRow(adv, "类路径头部", "留空则不添加", data.get("LaunchAdvanceClasspathHead", ""),
                s -> data.set("LaunchAdvanceClasspathHead", s));
        adv.add(directCheck("锁定内存分配（-Xms = -Xmx）", "LaunchAdvanceLockMemory", false));
        if (Shell.windows()) {
            adv.add(directCheck("显示控制台窗口（java.exe）", "LaunchAdvanceNoJavaw", false));
        }
        Ui.Check crash = Ui.checkBox("游戏异常退出时自动分析崩溃原因", false);
        crash.setSelected(data.crashAnalysisEnabled());
        crash.setOnAction(() -> {
            data.set("LaunchCrashAnalysis", crash.isSelected());
            // 同步写旧配置反向键
            data.set("LaunchAdvanceDisableCrashAnalysis", !crash.isSelected());
        });
        adv.add(crash);
        if (Shell.windows()) {
            comboRow(adv, "渲染器（下次启动生效）", new String[]{"默认（游戏自带渲染器）", "软件渲染（llvmpipe）",
                    "DirectX 12（d3d12）", "Vulkan（zink）"}, data.getInt("LaunchAdvanceRenderer", 0),
                    i -> {
                        data.set("LaunchAdvanceRenderer", i);
                        if (i > 0) {
                            // 首次选择后台拉 mesa-loader
                            Threads.start("TCL-Mesa", () -> {
                                try {
                                    Mesa.ensure();
                                    javafx.application.Platform.runLater(
                                            () -> win.toast("渲染器组件已就绪", "下次启动游戏时生效", 1));
                                } catch (Exception ex) {
                                    javafx.application.Platform.runLater(
                                            () -> win.toast("渲染器组件下载失败", String.valueOf(ex.getMessage()), 2));
                                }
                            });
                        }
                    });
        }
        adv.add(Ui.hint("版本独立设置中还有更多高级选项可供调整。", 0));

        StackPane switchBtn = Ui.extraTextButton("arrow-right", "版本独立设置", win::openInstanceSetup);
        StackPane switchLayer = new StackPane(switchBtn);
        switchLayer.setAlignment(Pos.CENTER);
        switchLayer.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(switchLayer, new Insets(-5, 0, 15, 0));
        page.getChildren().add(switchLayer);
    }

    private Ui.Check directCheck(String text, String key, boolean def) {
        Ui.Check box = Ui.checkBox(text, false);
        box.setSelected(data.getBool(key, def));
        box.setOnAction(() -> data.set(key, box.isSelected()));
        return box;
    }

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
        return v == 0 ? Lang.t("自动") : v + " MB";
    }

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
                memoryBox("游戏分配", gameRamMb >= 1024 ? String.format(java.util.Locale.ROOT, "%.1f GB", gameRamMb / 1024.0)
                                : gameRamMb + " MB",
                        gameRamMb / (double) Math.max(1, totalMb)));
        host.getChildren().setAll(bars);
    }

    private static String gb(long mb) {
        return mb >= 1024 ? String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0) : mb + " MB";
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
        TextField customPeer = textRow(conn, "自定义节点", "tcp://mc.yqst.top:11010,tcp://225284.xyz:11010",
                data.get("LinkCustomPeer", ""), s -> data.set("LinkCustomPeer", s));
        comboRow(conn, "节点", new String[]{
                "自动选择", "etnode（陶瓦官方节点）", "225284.xyz（上海）",
                "et1.fuis.top（武汉）", "自定义（见上方输入框）"},
                data.getInt("LinkPeer", 0), i -> {
                    data.set("LinkPeer", i);
                    customPeer.setDisable(i != 4);
                });
        customPeer.setDisable(data.getInt("LinkPeer", 0) != 4);

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

    private void buildUi(VBox page) {
        header(page, "个性化", "主题、背景、声音与外观。改动自动保存，外观类设置即时生效。");
        Ui.Card theme = addCard(page, "主题与配色");
        theme.add(Ui.label("主题的强调色会应用到按钮、滑块与选中态，切换后立即生效。", 12, Theme.gray2));
        if (!Theme.nextHiddenThemeName().isEmpty()) {
            int left = Theme.launchesToNextUnlock(data.getInt("SystemCount", 0));
            // 键须与 res 表完全一致
            Label prog = Ui.label(Lang.tf("隐藏主题 %d/%d · 每累计启动 %d 次解锁一个，再启动 %d 次可解锁「%s」",
                            Theme.unlockedHiddenIds().size(), Theme.HIDDEN_THEMES.length,
                            Theme.UNLOCK_EVERY_LAUNCHES, left, Lang.t(Theme.nextHiddenThemeName())),
                    12, Theme.gray3);
            prog.setWrapText(true);
            theme.add(prog);
        }
        FlowPane swatches = new FlowPane(18, 10);
        swatches.setPadding(new Insets(6, 0, 4, 0));
        int current = data.getInt("UiThemePreset", -1);
        for (int pass = 0; pass < 2; pass++) {
            for (int i0 = 0; i0 < Theme.THEME_NAMES.length; i0++) {
                final int i = i0;
                if (i == 14) continue;
                boolean locked = !Theme.isThemeUnlocked(i);
                if (locked != (pass == 1)) continue;
                boolean selected = current == i;
                Ui.RadioBox rb = Ui.radioBox(Theme.THEME_NAMES[i], selected);
                if (locked) {
                    rb.setDisable(true);
                    rb.setOpacity(0.55);
                }
                rb.setOnAction(() -> {
                    data.set("UiThemePreset", i);
                    data.set("UiAutoThemeFromBackground", false);
                    javafx.geometry.Bounds b = rb.localToScene(rb.getBoundsInLocal());
                    win.applyThemeFrom(b.getCenterX(), b.getCenterY());
                    win.toast("主题已切换", Theme.THEME_NAMES[i], 1);
                });
                swatches.getChildren().add(rb);
            }
        }
        theme.add(swatches);
        Ui.SliderBar componentSlider = sliderRow(theme, "组件半透明", 0, 100, data.getInt("UiComponentOpacity", 50), v -> {
            data.set("UiComponentOpacity", v.intValue());
            Theme.applyComponentOpacity();
        }, "%");
        componentSlider.setOnMouseReleased(e -> win.rebuildUi());
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
        buttons.setOnAction(() -> {
            data.set("UiListButtonsAlwaysShow", buttons.isSelected());
            win.rebuildUi();
        });
        listBtn.add(buttons, Ui.label("开启后按钮会一直占住右侧空间，列表标题可显示的宽度会略微变窄。", 12, Theme.gray3));

        Ui.Card detail = addCard(page, "界面细节");
        sliderRow(detail, "窗口圆角", 0, 24, data.getInt("UiWindowRadius", 6), v -> {
            data.set("UiWindowRadius", v.intValue());
            win.applyWindowRadius();
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

        comboRow(detail, "标题栏高度", new String[]{"40", "44", "48（默认）", "54", "60"},
                Math.max(0, Math.min(4, switch (data.getInt("UiTitleBarHeight", 48)) {
                    case 40 -> 0;
                    case 44 -> 1;
                    case 54 -> 3;
                    case 60 -> 4;
                    default -> 2;
                })), i -> {
                    data.set("UiTitleBarHeight", new int[]{40, 44, 48, 54, 60}[i]);
                    win.rebuildUi();
                });
        comboRow(detail, "标题栏 Logo", new String[]{"不显示", "默认文字 TCL", "自定义文字", "自定义图片（TCL/Logo.png）"},
                Math.max(0, Math.min(3, data.getInt("UiLogoType", 1))), i -> {
                    data.set("UiLogoType", i);
                    win.rebuildUi();
                });
        TextField logoText = textRow(detail, "标题栏文字", "TCL", data.get("UiLogoText", "TCL"), s -> {
            data.set("UiLogoText", s);
        });
        // 失焦/回车才重建
        logoText.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) win.rebuildUi();
        });
        logoText.setOnAction(e -> win.rebuildUi());
        Ui.Check leftTitle = Ui.checkBox("标题栏内容居左", false);
        leftTitle.setSelected(data.getBool("UiTitleLeft", false));
        leftTitle.setOnAction(() -> {
            data.set("UiTitleLeft", leftTitle.isSelected());
            win.rebuildUi();
        });
        detail.add(leftTitle);
        FlowPane logoButtons = new FlowPane(10, 10);
        logoButtons.getChildren().addAll(
                Ui.button("更改图片", Ui.BTN_NORMAL, this::pickLogoFile),
                Ui.button("清空图片", Ui.BTN_RED, () -> {
                    Path p = Paths.get(data.gameDir, "TCL", "Logo.png");
                    try {
                        Files.deleteIfExists(p);
                        if (data.getInt("UiLogoType", 1) == 3) data.set("UiLogoType", 1);
                        win.rebuildUi();
                        win.toast("已清空标题栏图片", "", 1);
                    } catch (Exception e) {
                        win.toast("清空失败", e.getMessage(), 3);
                    }
                }));
        detail.add(logoButtons);
        sliderRow(detail, "弹出菜单不透明度", 0, 100, data.getInt("UiMenuOpacity", 100), v -> {
            data.set("UiMenuOpacity", v.intValue());
        }, "%");
        comboRow(detail, "最大帧率（下次启动生效）", new String[]{"不限制", "30 FPS", "60 FPS", "90 FPS",
                "120 FPS", "144 FPS", "240 FPS"}, Math.max(0, data.getInt("UiMaxFps", 0) == 0 ? 0
                : data.getInt("UiMaxFps", 0) == 30 ? 1 : data.getInt("UiMaxFps", 0) == 60 ? 2
                : data.getInt("UiMaxFps", 0) == 90 ? 3 : data.getInt("UiMaxFps", 0) == 120 ? 4
                : data.getInt("UiMaxFps", 0) == 144 ? 5 : 6), i -> {
                    data.set("UiMaxFps", new int[]{0, 30, 60, 90, 120, 144, 240}[i]);
                });

        Ui.Card fontCard = addCard(page, "字体");
        Theme.ensureUserFonts();
        java.util.List<String> fontOpts = new ArrayList<>();
        fontOpts.add(Lang.tf("跟随系统默认（%s）", Theme.fontFamily()));
        for (String family : Theme.userFonts()) fontOpts.add(family);
        String currentFont = data.get("UiCustomFont", "");
        int fontSel = 0;
        for (int i = 0; i < Theme.userFonts().size(); i++) {
            if (Theme.userFonts().get(i).equals(currentFont)) fontSel = i + 1;
        }
        ComboBox<String> fontCombo = Ui.combo(fontOpts.toArray(new String[0]), fontSel);
        fontCombo.setPrefWidth(300);
        fontCombo.setOnAction(e -> {
            int i = fontCombo.getSelectionModel().getSelectedIndex();
            data.set("UiCustomFont", i <= 0 ? "" : Theme.userFonts().get(i - 1));
            Theme.loadUserFonts();
            win.rebuildUi();
            win.toast("字体已切换", i <= 0 ? Lang.t("跟随系统默认") : Theme.userFonts().get(i - 1), 1);
        });
        fontCard.add(Ui.settingRow("界面字体", fontCombo));
        Label fontPreview = new Label(Lang.t("字体预览 The quick brown fox 中文 123"));
        fontPreview.setFont(Theme.font(16));
        fontPreview.setWrapText(true);
        fontCard.add(fontPreview);
        FlowPane fontButtons = new FlowPane(10, 10);
        fontButtons.getChildren().addAll(
                Ui.button("选择字体文件", Ui.BTN_NORMAL, this::pickFontFile),
                Ui.button("打开字体文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(Theme.fontDir());
                        Shell.openFolder(Theme.fontDir().toString());
                    } catch (Exception e) {
                        win.toast("无法打开文件夹", e.getMessage(), 3);
                    }
                }),
                Ui.button("重新加载字体", Ui.BTN_NORMAL, () -> {
                    Theme.loadUserFonts();
                    win.openSetup("ui");
                    win.toast("字体已重新加载", Lang.tf("共 %d 个自定义字体", Theme.userFonts().size()), 1);
                }),
                Ui.button("恢复默认字体", Ui.BTN_RED, () -> {
                    data.set("UiCustomFont", "");
                    Theme.loadUserFonts();
                    win.rebuildUi();
                    win.toast("已恢复默认字体", "", 1);
                }));
        fontCard.add(fontButtons);
        fontCard.add(Ui.hint("把 .ttf / .otf 字体放进 游戏目录/TCL/Fonts 后点「重新加载字体」，"
                + "即可在列表中选择；macOS / Linux 会自动使用系统自带的苹方 / Noto Sans CJK。", 0));

        Ui.Card hidden = addCard(page, "功能隐藏");
        String[][] pages = {
                {"启动页", "UiHiddenLaunch"},
                {"实例页", "UiHiddenInstance"},
                {"下载页", "UiHiddenDownload"},
                {"联机页", "UiHiddenLink"},
                {"更多页", "UiHiddenOther"},
                {"设置入口", "UiHiddenPageSetup"},
                {"版本选择", "UiHiddenFunctionSelect"},
                {"Mod 更新", "UiHiddenFunctionModUpdate"},
                {"版本修改", "UiHiddenFunctionInstanceModify"},
                {"设置-启动", "UiHiddenSetupLaunch"},
                {"设置-联机", "UiHiddenSetupLink"},
                {"设置-个性化", "UiHiddenSetupUi"},
                {"设置-系统", "UiHiddenSetupSystem"},
        };
        FlowPane hiddenBox = new FlowPane(14, 8);
        for (String[] pageDef : pages) {
            Ui.Check box = Ui.checkBox(pageDef[0], false);
            box.setSelected(data.getBool(pageDef[1], false));
            box.setOnAction(() -> {
                boolean willHide = box.isSelected();
                boolean isPage = pageDef[1].equals("UiHiddenLaunch") || pageDef[1].equals("UiHiddenInstance")
                        || pageDef[1].equals("UiHiddenDownload") || pageDef[1].equals("UiHiddenLink")
                        || pageDef[1].equals("UiHiddenOther");
                if (isPage) {
                    int visible = 0;
                    for (String[] p : pages) {
                        boolean otherPage = p[1].equals("UiHiddenLaunch") || p[1].equals("UiHiddenInstance")
                                || p[1].equals("UiHiddenDownload") || p[1].equals("UiHiddenLink")
                                || p[1].equals("UiHiddenOther");
                        if (!otherPage) continue;
                        boolean isHidden = p[1].equals(pageDef[1]) ? willHide : data.getBool(p[1], false);
                        if (!isHidden) visible++;
                    }
                    if (visible == 0) {
                        box.setSelected(false);
                        win.toast("至少保留一个页面", "不能隐藏全部导航页", 2);
                        return;
                    }
                }
                if (pageDef[1].startsWith("UiHiddenSetup")) {
                    int visible = 0;
                    for (String[] p : pages) {
                        if (!p[1].startsWith("UiHiddenSetup")) continue;
                        boolean isHidden = p[1].equals(pageDef[1]) ? willHide : data.getBool(p[1], false);
                        if (!isHidden) visible++;
                    }
                    if (visible == 0) {
                        box.setSelected(false);
                        win.toast("至少保留一个设置分类", "不能隐藏全部设置分类", 2);
                        return;
                    }
                }
                data.set(pageDef[1], willHide);
                win.refreshAfterHide(pageDef[1]);
            });
            hiddenBox.getChildren().add(box);
        }
        hidden.add(hiddenBox);
        if (data.getBool("UiHiddenFunctionHidden", false)) {
            hidden.setVisible(false);
            hidden.setManaged(false);
        }

        int preset = data.getInt("UiThemePreset", -1);
        if (preset == 14) {
            Ui.Card customTheme = addCard(page, "自定义主题色");
            Ui.SliderBar hueBar = sliderRowDebounced(customTheme, "色调", 0, 360, data.getInt("UiLauncherHue", 210), v -> {
                data.set("UiLauncherHue", v.intValue());
                Theme.applyCustomTheme();
            }, "°");
            hueBar.setOnMouseReleased(e -> win.rebuildUi());
            Ui.SliderBar satBar = sliderRowDebounced(customTheme, "饱和度", 0, 100, data.getInt("UiLauncherSat", 72), v -> {
                data.set("UiLauncherSat", v.intValue());
                Theme.applyCustomTheme();
            }, "%");
            satBar.setOnMouseReleased(e -> win.rebuildUi());
            Ui.SliderBar lightBar = sliderRowDebounced(customTheme, "亮度", 0, 100, data.getInt("UiLauncherLight", 55), v -> {
                data.set("UiLauncherLight", v.intValue());
                Theme.applyCustomTheme();
            }, "%");
            lightBar.setOnMouseReleased(e -> win.rebuildUi());
            Ui.SliderBar deltaBar = sliderRowDebounced(customTheme, "标题栏色相偏移", 0, 180, data.getInt("UiLauncherDelta", 20), v -> {
                data.set("UiLauncherDelta", v.intValue());
                Theme.applyCustomTheme();
            }, "°");
            deltaBar.setOnMouseReleased(e -> win.rebuildUi());
        }

        boolean videoMode = data.getInt("UiBackgroundType", 0) == 1;
        int videoCount = win.scanVideos().size();
        int imageCount = countPictures();

        Ui.Card bg = addCard(page, "背景");
        VBox imageBox = new VBox(2);
        VBox videoBox = new VBox(2);
        Runnable applyMode = () -> {
            boolean video = data.getInt("UiBackgroundType", 0) == 1;
            imageBox.setVisible(!video);
            imageBox.setManaged(!video);
            videoBox.setVisible(video);
            videoBox.setManaged(video);
        };
        comboRow(bg, "背景类型", new String[]{"图片（静态背景）", "视频（动态背景）"},
                videoMode ? 1 : 0, i -> {
                    data.set("UiBackgroundType", i);
                    applyMode.run();
                    win.refreshVideoBackground(false);
                    if (i == 0) win.applyBackground();
                });

        int mark = bg.content.getChildren().size();
        if (imageCount > 0) {
            // 下拉顺序对应存储值
            String[] suitNames = {"自动", "平铺", "居中", "拉伸", "左上 (保持长宽比)",
                    "左中 (保持长宽比)", "左下 (保持长宽比)", "中上 (保持长宽比)",
                    "居中 (保持长宽比)", "中下 (保持长宽比)", "右上 (保持长宽比)",
                    "右中 (保持长宽比)", "右下 (保持长宽比)"};
            int[] suitVals = {0, 4, 1, 3, 5, 9, 7, 11, 2, 12, 6, 10, 8};
            int suitStored = data.getInt("UiBackgroundSuit", 0);
            int suitIndex = 0;
            for (int i = 0; i < suitVals.length; i++) if (suitVals[i] == suitStored) suitIndex = i;
            ComboBox<String> suitCombo = Ui.combo(suitNames, suitIndex);
            suitCombo.setPrefWidth(220);
            suitCombo.setOnAction(e -> {
                int i = suitCombo.getSelectionModel().getSelectedIndex();
                data.set("UiBackgroundSuit", suitVals[Math.max(0, i)]);
                win.applyBackground();
            });
            bg.add(Ui.settingRow("自适应方式", suitCombo));

            String[] rotateNames = {"关闭", "每 1 分钟", "每 5 分钟", "每 15 分钟", "每 30 分钟", "每 60 分钟"};
            int[] rotateVals = {0, 1, 5, 15, 30, 60};
            int rotStored = data.getInt("UiBackgroundRotate", 0);
            int rotIndex = 0;
            for (int i = 0; i < rotateVals.length; i++) if (rotateVals[i] == rotStored) rotIndex = i;
            ComboBox<String> rotateCombo = Ui.combo(rotateNames, rotIndex);
            rotateCombo.setPrefWidth(220);
            rotateCombo.setOnAction(e -> {
                int i = rotateCombo.getSelectionModel().getSelectedIndex();
                data.set("UiBackgroundRotate", rotateVals[Math.max(0, i)]);
                win.startWallpaperRotation();
            });
            bg.add(Ui.settingRow("自动轮播", rotateCombo));

            sliderRow(bg, "不透明度", 0, 100, data.getInt("UiBackgroundOpacity", 1000) / 10, v -> {
                data.set("UiBackgroundOpacity", (int) (v * 10));
                win.applyBackground();
            }, "%");
            sliderRow(bg, "背景模糊", 0, 40, data.getInt("UiBackgroundBlur", 0), v -> {
                data.set("UiBackgroundBlur", v.intValue());
                win.applyBackground();
            }, " px");
        }
        Ui.Check colorful = Ui.checkBox("叠加彩色背景", false);
        colorful.setSelected(data.getBool("UiBackgroundColorful", true));
        colorful.setOnAction(() -> {
            data.set("UiBackgroundColorful", colorful.isSelected());
            win.applyBackground();
        });
        bg.add(colorful);
        Ui.Check autoTheme = Ui.checkBox("根据背景图自动调整主题色", false);
        autoTheme.setSelected(data.getBool("UiAutoThemeFromBackground", true));
        autoTheme.setOnAction(() -> {
            data.set("UiAutoThemeFromBackground", autoTheme.isSelected());
            if (autoTheme.isSelected()) {
                data.set("UiThemePreset", -1);
                Theme.applyWallpaperTheme();
            } else {
                // 关闭跟随时固化当前取色
                Theme.saveCurrentAsCustom(data);
                data.set("UiThemePreset", 14);
            }
            win.rebuildUi();
        });
        bg.add(autoTheme);
        FlowPane bgButtons = new FlowPane(10, 10);
        bgButtons.getChildren().addAll(
                Ui.button("打开文件夹", Ui.BTN_NORMAL, this::openBackgroundFolder),
                Ui.button("刷新背景图片", Ui.BTN_NORMAL, () -> {
                    win.refreshWallpaper(false);
                    win.toast("背景图片已刷新", "", 1);
                }),
                Ui.button("清空背景图片", Ui.BTN_RED, () -> Dialogs.confirm(win,
                        "清空背景图片", Lang.t("即将删除背景图片文件夹中的所有文件。\n此操作不可撤销，是否确定？"), () -> {
                            clearBackgroundFolder();
                            win.refreshWallpaper(true);
                            win.refreshRight();
                        }, true)));
        bg.add(bgButtons);
        moveRows(bg, mark, imageBox);

        mark = bg.content.getChildren().size();
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
        Ui.Check vidPause = Ui.checkBox("启动器失去焦点时暂停（省 CPU）", false);
        vidPause.setSelected(data.getBool("UiVideoPauseOnBlur", true));
        vidPause.setOnAction(() -> data.set("UiVideoPauseOnBlur", vidPause.isSelected()));
        bg.add(vidPause);
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
        bg.add(Ui.hint("支持 mp4 / m4v / mov（H.264）。mkv、avi、wmv、webm 等 JavaFX 无法解码，需要先转成 mp4；"
                + "多个视频循环播放。建议使用 720p 以内的素材，分辨率越高 CPU 占用越大。", 0));
        moveRows(bg, mark, videoBox);

        bg.add(imageBox, videoBox);
        applyMode.run();

        Ui.Card sound = addCard(page, "背景音乐");
        sliderRow(sound, "音量", 0, 100, Music.volumePercent(), v -> {
            data.set("UiVolume", v.intValue());
            data.set("UiMusicVolume", v.intValue() * 10);
            try {
                Music.setVolume(v.intValue());
            } catch (Throwable ignored) {
            }
        }, "%");
        sound.add(directCheck("随机播放", "UiMusicRandom", false));
        sound.add(directCheck("启动时自动播放", "UiMusicAuto", false));
        Ui.Check musicStart = directCheck("游戏启动后自动开始播放，游戏退出后自动暂停播放", "UiMusicStart", false);
        Ui.Check musicStop = directCheck("游戏启动后自动暂停播放，游戏退出后自动开始播放", "UiMusicStop", false);
        musicStart.setOnAction(() -> {
            data.set("UiMusicStart", musicStart.isSelected());
            if (musicStart.isSelected()) {
                musicStop.setSelected(false);
                data.set("UiMusicStop", false);
            }
        });
        musicStop.setOnAction(() -> {
            data.set("UiMusicStop", musicStop.isSelected());
            if (musicStop.isSelected()) {
                musicStart.setSelected(false);
                data.set("UiMusicStart", false);
            }
        });
        sound.add(musicStart);
        sound.add(musicStop);
        FlowPane musicButtons = new FlowPane(10, 10);
        musicButtons.getChildren().addAll(
                Ui.button("打开文件夹", Ui.BTN_NORMAL, this::openMusicFolder),
                Ui.button("刷新背景音乐", Ui.BTN_NORMAL, () -> {
                    Music.reload(data.gameDir);
                    win.refreshExtraButtons();
                    win.toast("背景音乐已刷新",
                            Lang.tf("共 %d 首", Music.trackCount()), 1);
                }),
                Ui.button("清空背景音乐", Ui.BTN_RED, () -> Dialogs.confirm(win,
                        "清空背景音乐", Lang.t("即将删除背景音乐文件夹中的所有文件。\n此操作不可撤销，是否确定？"), () -> {
                            // Windows 播放中的文件删不掉
                            Music.clear();
                            clearMusicFolder();
                            win.refreshExtraButtons();
                            win.refreshRight();
                        }, true)));
        sound.add(musicButtons);
        sound.add(Ui.hint("将 .mp3 / .wav 音乐放入 游戏目录/TCL/Music 后，右下角会出现音乐按钮。", 0));
    }

    private void pickLogoFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择标题栏图片"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Lang.t("图片文件"), "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        try {
            if (Files.size(file.toPath()) > 8L * 1024 * 1024) {
                win.toast("图片过大", "标题栏图片不能超过 8MB。", 3);
                return;
            }
            Path dir = Paths.get(data.gameDir, "TCL");
            Files.createDirectories(dir);
            Path dst = dir.resolve("Logo.png");
            Files.copy(file.toPath(), dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            data.set("UiLogoType", 3);
            win.rebuildUi();
            win.toast("标题栏图片已更换", file.getName(), 1);
        } catch (Exception e) {
            win.toast("更换图片失败", e.getMessage(), 3);
        }
    }

    private void pickFontFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择字体文件"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Lang.t("字体文件"), "*.ttf", "*.otf", "*.ttc"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        try {
            if (Files.size(file.toPath()) > 32L * 1024 * 1024) {
                win.toast("字体过大", "字体文件不能超过 32MB。", 3);
                return;
            }
            Files.createDirectories(Theme.fontDir());
            Path dst = Theme.fontDir().resolve(file.getName());
            Files.copy(file.toPath(), dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Theme.loadUserFonts();
            String family = null;
            try (var in = Files.newInputStream(dst)) {
                javafx.scene.text.Font loaded = javafx.scene.text.Font.loadFont(in, 13);
                if (loaded != null) family = loaded.getFamily();
            } catch (Exception ignored) {
            }
            if (family != null) data.set("UiCustomFont", family);
            win.openSetup("ui");
            win.toast("字体已安装", family == null ? file.getName() : family, 1);
        } catch (Exception e) {
            win.toast("安装字体失败", e.getMessage(), 3);
        }
    }

    private void clearBackgroundFolder() {
        int removed = 0;
        try {
            Path dir = Paths.get(data.gameDir, "TCL", "Pictures");
            if (Files.isDirectory(dir) && !Files.isSymbolicLink(dir)) {
                try (var s = Files.list(dir)) {
                    var it = s.filter(Files::isRegularFile).iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        try {
                            Files.delete(p);
                            removed++;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            win.toast("已清空背景图片", Lang.tf("删除了 %d 个文件", removed), 1);
        } catch (Exception e) {
            win.toast("清空失败", e.getMessage(), 3);
        }
    }

    private void openMusicFolder() {
        try {
            Path dir = Music.folder(data.gameDir);
            Files.createDirectories(dir);
            Shell.openFolder(dir.toString());
        } catch (Exception e) {
            win.toast("无法打开文件夹", e.getMessage(), 3);
        }
    }

    private void clearMusicFolder() {
        int removed = 0;
        try {
            Path dir = Music.folder(data.gameDir);
            if (Files.isDirectory(dir) && !Files.isSymbolicLink(dir)) {
                try (var s = Files.list(dir)) {
                    var it = s.filter(Files::isRegularFile).iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        try {
                            Files.delete(p);
                            removed++;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            win.toast("已清空背景音乐", Lang.tf("删除了 %d 个文件", removed), 1);
        } catch (Exception e) {
            win.toast("清空失败", e.getMessage(), 3);
        }
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

    private int countPictures() {
        try (var s = Files.list(Paths.get(data.gameDir, "TCL", "Pictures"))) {
            return (int) s.filter(Files::isRegularFile).filter(Theme::isWallpaperImage).count();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void moveRows(Ui.Card card, int mark, VBox target) {
        var children = card.content.getChildren();
        List<Node> rows = new ArrayList<>(children.subList(mark, children.size()));
        children.removeAll(rows);
        target.getChildren().addAll(rows);
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

    private void clearVideoFolder() {
        int removed = 0;
        try {
            Path dir = win.videoFolder();
            if (Files.isDirectory(dir) && !Files.isSymbolicLink(dir)) {
                try (var s = Files.list(dir)) {
                    var it = s.filter(Files::isRegularFile).iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        try {
                            Files.delete(p);
                            removed++;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            win.toast("已清空背景视频", Lang.tf("删除了 %d 个文件", removed), 1);
        } catch (Exception e) {
            win.toast("清空失败", e.getMessage(), 3);
        }
    }

    private void buildSystem(VBox page) {
        header(page, "系统", "下载、网络、语言与启动器行为。");
        Ui.Card download = addCard(page, "下载");
        String[] sourceNames = {"自动选择（官方 / 镜像测速择优）", "仅使用官方源", "镜像源优先（BMCLAPI / 自定义，失败回退官方）"};
        int fileCur = Math.max(0, Math.min(2, data.getInt("SystemDownloadSource", 0)));
        comboRow(download, "文件下载源", sourceNames, fileCur,
                i -> data.set("SystemDownloadSource", Math.max(0, Math.min(2, i))));
        Label speedHint = Ui.label("自动模式会同时测试官方源与镜像源，按当前网络选择更快的源；"
                + "仅官方源不会经过镜像，仅镜像源会优先走 BMCLAPI 或自定义镜像，失败时再回退官方。", 12, Theme.gray3);
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

        comboRow(download, "下载方式", new String[]{
                        "自动（并发下载，速度优先）", "串行（一次一个文件，稳定优先）"},
                Math.max(0, Math.min(1, data.getInt("SystemDownloadMode", 0))),
                i -> data.set("SystemDownloadMode", Math.max(0, Math.min(1, i))));
        Label modeHint = Ui.label("自动模式按最大线程数并发；串行模式只保留一个文件下载，"
                + "适合网络不稳定、并发下载容易超时或磁盘占用过高的情况，速度会更慢但更容易完成。", 12, Theme.gray3);
        modeHint.setWrapText(true);
        download.add(modeHint);
        textRow(download, "最大线程数", "64", data.get("SystemMaxThreads", "64"), s -> data.set("SystemMaxThreads", s));
        textRow(download, "速度限制", "不限", data.get("SystemSpeedLimit", ""), s -> data.set("SystemSpeedLimit", s));
        TextField cacheField = textRow(download, "缓存文件夹", "留空使用默认", data.get("SystemSystemCache", ""), s -> {
            // 路径检查通过后再写缓存
        });
        cacheField.setOnAction(e -> commitCachePath(cacheField));
        cacheField.focusedProperty().addListener((o, was, now) -> {
            if (!now) commitCachePath(cacheField);
        });
        download.add(Ui.hint("下载缓存与社区图片都会放到这里；输入后按回车或点击其它位置生效，改到剩余空间更大的盘可以避免 C 盘被占满。", 0));
        Label target = Ui.label("请在 实例页左侧「文件夹列表」中更改下载目标文件夹。"
                + "在某个文件夹或游戏版本上右键，即可选择打开对应文件夹。", 12, Theme.gray3);
        target.setWrapText(true);
        download.add(Ui.settingRow("目标文件夹", target));

        Ui.Card communityRes = addCard(page, "社区资源");
        comboRow(communityRes, "默认资源来源", new String[]{"全部来源", "优先 CurseForge", "优先 Modrinth"},
                Math.max(0, Math.min(2, data.getInt("ToolDownloadMod", 0))),
                i -> data.set("ToolDownloadMod", i));
        comboRow(communityRes, "Mod 管理样式", new String[]{"显示文件名", "优先显示 Mod 自带名称"},
                Math.max(0, Math.min(1, data.getInt("ToolModLocalNameStyle", 0))),
                i -> data.set("ToolModLocalNameStyle", i));
        comboRow(communityRes, "下载文件名格式", new String[]{
                "保留原始文件名", "标题-版本", "仅标题", "原文-版本", "标题-原文"},
                Math.max(0, Math.min(4, data.getInt("ToolDownloadTranslateV2", 0))),
                i -> data.set("ToolDownloadTranslateV2", i));
        textRow(communityRes, "CurseForge Key", "默认", data.get("SystemCurseKey", ""),
                s -> {
                    data.set("SystemCurseKey", s == null ? "" : s.trim());
                    CurseForge.clearCache();
                });
        communityRes.add(Ui.label("留空时使用内置公共 Key；OptiFabric 等 CurseForge 资源需要有效 Key 才能获取列表。",
                12, Theme.gray3));


        Ui.Card language = addCard(page, "辅助功能");
        String[] langNames = {"简体中文", "English", "繁體中文", "跟随系统"};
        ComboBox<String> lang = Ui.combo(langNames, Math.max(0, Math.min(3, Lang.setting)));
        lang.setOnAction(e -> {
            int index = lang.getSelectionModel().getSelectedIndex();
            if (index == Lang.setting) return;
            Lang.setMode(index);
            data.set("SystemLanguage", index);
            win.rebuildUi();
            win.toast("语言已切换", Lang.langName(), 1);
        });
        language.add(Ui.settingRow("语言", lang));
        FlowPane i18n = new FlowPane(10, 10);
        java.nio.file.Path i18nFile = Resources.home().resolve("i18n.txt");
        i18n.getChildren().addAll(
                Ui.button("导出翻译文件", Ui.BTN_NORMAL, () -> {
                    try {
                        copyLangFile(i18nFile);
                        win.toast("翻译文件", Lang.t("已导出到 ") + i18nFile, 1);
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
                        if (!Files.exists(i18nFile)) copyLangFile(i18nFile);
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
                    chooser.setTitle(Lang.t("导出全部数据"));
                    chooser.setInitialFileName("tcl-config.properties");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("配置文件"), "*.properties"));
                    File file = chooser.showSaveDialog(win.stage);
                    if (file != null) {
                        boolean ok = data.exportConfig(file.toPath());
                        win.toast(ok ? "导出完成（不含令牌与认证服务器）" : "导出失败", file.getName(), ok ? 1 : 3);
                    }
                }),
                Ui.button("导入全部数据", Ui.BTN_NORMAL, () -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle(Lang.t("导入全部数据"));
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("配置文件"), "*.properties"));
                    File file = chooser.showOpenDialog(win.stage);
                    if (file == null) return;
                    Dialogs.confirm(win, "导入全部数据",
                            // 四段拼接用完整键
                            Lang.tf("将导入 %s 中的配置，同名设置会被文件内容覆盖。\n\n"
                                    + "出于安全考虑，导入时不会应用启动前命令、Java 路径、代理、镜像、"
                                    + "更新源、自动进服、代理密码与第三方 Key、账号令牌与认证服务器，"
                                    + "以及 JVM / 游戏附加参数，"
                                    + "这些只能在本机手动设置。\n\n确认导入吗？",
                                    file.getName()),
                            () -> {
                                boolean ok = data.importConfig(file.toPath());
                                if (ok) {
                                    win.rebuildUi();
                                    win.toast("导入完成", "配置已重新加载，敏感项已忽略", 1);
                                } else {
                                    win.toast("导入失败", file.getName(), 3);
                                }
                            });
                }),
                Ui.button("复制识别码", Ui.BTN_NORMAL, () -> {
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(Data.offlineUuid(System.getProperty("user.name")));
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    win.toast("已复制识别码", Data.offlineUuid(System.getProperty("user.name")), 1);
                }));
        other.add(dataBtns);
        other.add(directCheck("每次启动时检查更新", "CheckUpdateOnStartup", true));
        other.add(directCheck("接收正式版更新提示", "ToolUpdateRelease", true));
        other.add(directCheck("接收测试版更新提示", "ToolUpdateSnapshot", false));
        other.add(directCheck("游戏语言自动设为中文（仅当游戏内为英文时）", "ToolHelpChinese", true));
        other.add(directCheck("禁用硬件加速（下次启动生效，远程桌面/老显卡可用）",
                "SystemDisableHardwareAcceleration", false));
        other.add(Ui.hint("启动后从 GitHub Releases 读取最新版本与更新日志；发现新版本会弹窗提示，无法连接时会提示网络问题。", 0));
        HBox updateRow = new HBox(12);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        updateRow.getChildren().addAll(
                standaloneButton("检查更新", Ui.BTN_HIGHLIGHT, 120, () -> Updates.check(win, true)),
                Ui.label(Lang.tf("当前版本：v%s", Build.VERSION), 12, Theme.gray2));
        VBox.setMargin(updateRow, new Insets(6, 0, 0, 0));
        other.add(updateRow);

        communityCard(page);
    }

    private void communityCard(VBox page) {
        Ui.Card community = addCard(page, "社区");
        community.add(Ui.hint("有问题反馈？想获取更新？加入我们的启动器交流群吧！", 0));
        community.add(standaloneButton("加入 QQ 群", Ui.BTN_HIGHLIGHT, 150, this::openCommunity));
    }

    private StackPane standaloneButton(String text, int type, int minWidth, Runnable action) {
        StackPane btn = Ui.button(text, type, action);
        btn.setMinWidth(minWidth);
        btn.setMaxWidth(Region.USE_PREF_SIZE);
        return btn;
    }

    private void openCommunity() {
        if (!Shell.openUrl("https://qm.qq.com/q/UONlv4VgwE")) {
            win.toast("打开链接失败", "https://qm.qq.com/q/UONlv4VgwE", 3);
        }
    }

    private void buildJava(VBox page) {
        header(page, "Java", "管理可用的 Java 运行时，并设置优先级。");
        Ui.Card manage = addCard(page, "Java 管理");
        Label intro = Ui.label("TCL 需要 Java 才能启动游戏。列表中的第一个可用 Java 将被默认使用，"
                + "点击列表项可切换当前使用的 Java。", 12.5, Theme.gray2);
        intro.setWrapText(true);
        manage.add(intro);
        Label status = Ui.label(Lang.t("当前：") + (data.javaPath.isEmpty()
                ? Lang.t("未找到 Java") : data.javaPath), 12, Theme.gray3);
        status.setWrapText(true);
        manage.add(status);
        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(
                Ui.button("导入 Java…", Ui.BTN_HIGHLIGHT, this::importJava),
                Ui.button("下载 Java…", Ui.BTN_NORMAL, this::chooseJavaDownload),
                Ui.button("搜索目录…", Ui.BTN_NORMAL, this::pickJavaFolder),
                Ui.button("重新搜索", Ui.BTN_NORMAL, () -> {
                    data.invalidateJavaScan();
                    win.refreshRight();
                }),
                Ui.button("打开数据文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Path dir = Resources.home();
                        Files.createDirectories(dir);
                        Shell.openFolder(dir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开文件夹", e.getMessage(), 3);
                    }
                }));
        manage.add(actions);

        Ui.Card found = addCard(page, "已发现的 Java");
        Label orderHint = Ui.label("用右侧箭头调整优先级：列表越靠前的 Java 越优先被自动选用。", 12, Theme.gray3);
        orderHint.setWrapText(true);
        found.add(orderHint);
        if (!data.hasJavaScanCache()) {
            // 首次扫描后台 完成刷新
            found.add(Ui.label("正在扫描本机的 Java…", 12.5, Theme.gray2));
            Threads.start("TCL-JavaScan", () -> {
                data.scanJavaRuntimes();
                javafx.application.Platform.runLater(() -> {
                    if (win.currentKey == null || !win.currentKey.equals("setup")) return;
                    if (!"java".equals(data.setupSection)) return;
                    win.refreshRight();
                });
            });
            return;
        }
        List<Data.JavaRuntime> javas = orderedJavas();
        VBox list = new VBox(2);
        for (Data.JavaRuntime rt : javas) {
            boolean selected = rt.path().equals(data.javaPath);
            String name = "Java " + rt.major() + (rt.folder().isEmpty() ? "" : " · " + rt.folder());
            Ui.ListItem item = new Ui.ListItem(Theme.icon("coffee", 17, selected ? Theme.accent : Theme.text),
                    name, rt.path(), 44);
            item.setSelected(selected);
            // 顺序写入 LaunchJavaOrder
            StackPane up = Ui.iconButton("arrow-up", 26, 15, "上移", () -> moveJava(rt, -1));
            StackPane down = Ui.iconButton("arrow-down", 26, 15, "下移", () -> moveJava(rt, 1));
            up.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    javafx.scene.input.MouseEvent::consume);
            down.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    javafx.scene.input.MouseEvent::consume);
            item.addAction(up);
            item.addAction(down);
            item.setOnMouseClicked(e -> {
                if (data.javaPath.equals(rt.path())) return;
                data.setJavaRuntime(rt.path(), rt.major());
                win.refreshRight();
                win.toast("Java 已选择", rt.path(), 1);
            });
            list.getChildren().add(item);
        }
        if (javas.isEmpty()) {
            list.getChildren().add(Ui.label("没有检测到 Java，请手动选择或先安装 Java 17。", 12, Theme.gray3));
        }
        found.add(list);
    }

    private List<Data.JavaRuntime> orderedJavas() {
        List<Data.JavaRuntime> all = data.scanJavaRuntimes();
        String raw = data.get("LaunchJavaOrder", "");
        if (raw == null || raw.isBlank()) return all;
        List<String> order = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            if (!part.isBlank()) order.add(part);
        }
        List<Data.JavaRuntime> sorted = new ArrayList<>();
        for (String path : order) {
            for (Data.JavaRuntime rt : all) {
                if (rt.path().equals(path) && !sorted.contains(rt)) sorted.add(rt);
            }
        }
        for (Data.JavaRuntime rt : all) if (!sorted.contains(rt)) sorted.add(rt);
        return sorted;
    }

    private void moveJava(Data.JavaRuntime rt, int dir) {
        // orderedJavas 放后台
        Threads.start("TCL-Java-Order", () -> {
            List<Data.JavaRuntime> list = orderedJavas();
            int index = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).path().equals(rt.path())) {
                    index = i;
                    break;
                }
            }
            int to = index + dir;
            if (index < 0 || to < 0 || to >= list.size()) return;
            list.add(to, list.remove(index));
            javafx.application.Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                for (Data.JavaRuntime r : list) {
                    if (sb.length() > 0) sb.append('|');
                    sb.append(r.path());
                }
                data.set("LaunchJavaOrder", sb.toString());
                Data.JavaRuntime first = list.get(0);
                data.setJavaRuntime(first.path(), first.major());
                win.openSetup("java");
                win.toast("优先级已调整", Lang.t("首选 ") + first.path(), 1);
            });
        });
    }

    private void chooseJavaDownload() {
        String[] options = new String[JavaInstaller.COMPONENTS.length];
        for (int i = 0; i < options.length; i++) options[i] = JavaInstaller.COMPONENTS[i][0];
        Dialogs.select(win, "下载 Java 运行时",
                options, "开始下载", "取消", false,
                index -> JavaInstaller.startInstallAsync(win, JavaInstaller.COMPONENTS[index][1]));
    }

    private static void copyLangFile(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (java.io.InputStream in = Theme.resStream("LangTables.txt")) {
            if (in == null) throw new Exception(Lang.t("找不到语言表"));
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void importJava() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择 Java 可执行文件"));
        boolean winOs = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Java 可执行文件"),
                winOs ? new String[]{"javaw.exe", "java.exe"} : new String[]{"java"}));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("所有文件"), "*.*"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        // 探测整段放后台
        Threads.start("TCL-Java-Import", () -> {
            int major = Data.javaMajor(file.getAbsolutePath());
            javafx.application.Platform.runLater(() -> {
                if (major <= 0) {
                    win.toast("无法识别该 Java", Lang.tf("请选择 bin 目录下的 %s", winOs ? "javaw.exe" : "java"), 2);
                    return;
                }
                data.setJavaRuntime(file.getAbsolutePath(), major);
                win.refreshRight();
                win.toast("Java 已导入", "Java " + major + " · " + file.getAbsolutePath(), 1);
            });
        });
    }

    // 手动指定非常规路径
    private void pickJavaFolder() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle(Lang.t("选择要搜索 Java 的文件夹"));
        java.io.File dir = chooser.showDialog(win.stage);
        if (dir == null) return;
        win.toast("正在搜索", dir.getAbsolutePath(), 0);
        Threads.start("TCL-JavaDirScan", () -> {
            List<Data.JavaRuntime> found = data.scanJavaIn(dir.toPath(), 6);
            javafx.application.Platform.runLater(() -> {
                win.refreshRight();
                if (found.isEmpty()) {
                    win.toast("没有找到 Java", Lang.tf("换个目录再试，或直接选择 %s",
                            Shell.windows() ? "bin\\java.exe" : "bin/java"), 3);
                } else {
                    win.toast(Lang.tf("找到 %d 个 Java", found.size()), found.get(0).path(), 1);
                }
            });
        });
    }

    private List<String> findJavas() {
        List<String> out = new ArrayList<>();
        for (Data.JavaRuntime rt : data.scanJavaRuntimes()) out.add(rt.path());
        if (!data.javaPath.isEmpty() && !out.contains(data.javaPath)) out.add(0, data.javaPath);
        return out;
    }

    private static String toHex(javafx.scene.paint.Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void buildLog(VBox page) {
        header(page, "日志", "查看、导出与清理运行日志。");
        Path logDir = Resources.logDir();
        Path logFile = logDir.resolve("launcher.log");

        Ui.Card ops = addCard(page, "日志操作");
        FlowPane buttons = new FlowPane(10, 10);
        buttons.getChildren().addAll(
                Ui.button("导出当前日志", Ui.BTN_HIGHLIGHT, () -> exportLog(false)),
                Ui.button("导出全部日志", Ui.BTN_NORMAL, () -> exportLog(true)),
                Ui.button("打开日志文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(logDir);
                        Shell.openFolder(logDir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开文件夹", e.getMessage(), 3);
                    }
                }),
                Ui.button("清理历史日志", Ui.BTN_RED, () ->
                        Dialogs.confirm(win, "清理历史日志", Lang.tf("将删除 %s 并清空当前日志显示。是否继续？", logDir), () -> {
                            Log.cleanup();
                            data.log.clear();
                            win.refreshRight();
                            win.toast("日志已清理", "", 1);
                        }, true)));
        ops.add(buttons);
        ops.add(directCheck("调试模式（重启生效，排查问题时开启，会把日志同步输出到控制台）", "SystemDebugMode", false));

        Ui.Card live = addCard(page, "实时日志");
        ComboBox<String> level = Ui.combo(new String[]{"全部级别", "警告及错误", "仅错误"}, 0);
        TextField filter = Ui.field("搜索日志内容");
        Ui.Check autoScroll = Ui.checkBox("自动滚动", true);
        Ui.Check wrap = Ui.checkBox("自动换行", true);
        wrap.setSelected(data.getBool("UiLogWrap", true));
        HBox controls = new HBox(10, level, filter, autoScroll, wrap);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filter, Priority.ALWAYS);
        live.add(controls);
        TextArea view = new TextArea();
        view.setEditable(false);
        view.setWrapText(wrap.isSelected());
        wrap.setOnAction(() -> {
            view.setWrapText(wrap.isSelected());
            data.set("UiLogWrap", wrap.isSelected());
        });
        view.setPrefHeight(300);
        view.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei UI'; -fx-font-size: 11.5;"
                + " -fx-control-inner-background: transparent;"
                + " -fx-background-color: transparent;"
                + " -fx-text-fill: " + toHex(Theme.text) + ";"
                + " -fx-highlight-fill: " + toHex(Theme.accent) + ";"
                + " -fx-highlight-text-fill: #ffffff;"
                + " -fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        live.add(view);
        Label status = Ui.label(Lang.t("实时监听 launcher.log…"), 11.5, Theme.gray3);
        live.add(status);
        final Timeline[] tick = new Timeline[1];
        final String[] lastKey = {""};
        final java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean();
        tick[0] = new Timeline(new KeyFrame(javafx.util.Duration.millis(600), e -> {
            if (page.getScene() == null) {
                tick[0].stop();
                return;
            }
            if (busy.get()) return;
            long size;
            try {
                size = Files.size(logFile);
            } catch (Exception ex) {
                size = 0;
            }
            final int levelIdx = level.getSelectionModel().getSelectedIndex();
            final String query = filter.getText() == null ? "" : filter.getText();
            String key = size + "|" + levelIdx + "|" + query;
            if (key.equals(lastKey[0])) return;
            lastKey[0] = key;
            busy.set(true);
            // 读盘过滤放后台
            Threads.start("TCL-Log-View", () -> {
                String[] lines = new String[0];
                String text = "";
                try {
                    lines = readLogLines(logFile);
                    text = filterLog(lines, levelIdx, query);
                } catch (Exception ignored) {
                } finally {
                    busy.set(false);
                }
                final String[] fLines = lines;
                final String fText = text;
                javafx.application.Platform.runLater(() -> {
                    if (page.getScene() == null) return;
                    if (!fText.equals(view.getText())) {
                        view.setText(fText);
                        if (autoScroll.isSelected()) view.setScrollTop(Double.MAX_VALUE);
                    }
                    status.setText(lastLogReadError.isEmpty()
                            ? Lang.tf("实时监听 %s · %d 行", logFile.getFileName(), fLines.length)
                            : Lang.tf("读取失败：%s（当前显示内存日志）", lastLogReadError));
                });
            });
        }));
        tick[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
        tick[0].play();

        Ui.Card all = addCard(page, "全部日志");
        List<Path> files = new ArrayList<>();
        try (var s = Files.list(logDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return n.endsWith(".log") || n.endsWith(".txt");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(files::add);
        } catch (Exception ignored) {
        }
        if (files.isEmpty()) {
            all.add(Ui.label("暂无日志文件", 12, Theme.gray3));
        } else {
            for (Path f : files) {
                Ui.ListItem row = new Ui.ListItem(Theme.icon("scroll-text", 16, Theme.text),
                        f.getFileName().toString(), bytes(fileSize(f)) + " · " + Lang.t("点击打开"), 36);
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

    private volatile String lastLogReadError = "";

    private String[] readLogLines(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                // 只读末尾 384 KiB
                long size = Files.size(file);
                long start = Math.max(0, size - 384 * 1024L);
                byte[] bytes = new byte[(int) (size - start)];
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                        file, java.nio.file.StandardOpenOption.READ)) {
                    ch.position(start);
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
                    while (bb.hasRemaining()) {
                        if (ch.read(bb) < 0) break;
                    }
                }
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (start > 0) {
                    int firstBreak = text.indexOf('\n');
                    if (firstBreak < 0) return new String[0];
                    text = text.substring(firstBreak + 1);
                }
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                if (text.isEmpty()) return new String[0];
                String[] all = text.split("\n", -1);
                int from = Math.max(0, all.length - 500);
                String[] tail = new String[all.length - from];
                for (int i = from; i < all.length; i++) {
                    String line = all[i];
                    if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                    tail[i - from] = line;
                }
                lastLogReadError = "";
                return tail;
            }
        } catch (Exception e) {
            // 内存日志要在状态栏说明
            lastLogReadError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return data.log.toArray(new String[0]);
    }

    private String filterLog(String[] lines, int level, String query) {
        StringBuilder sb = new StringBuilder();
        String q = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        for (String line : lines) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (level == 1 && !(lower.contains("warn") || lower.contains("error") || lower.contains("失败"))) continue;
            if (level == 2 && !(lower.contains("error") || lower.contains("失败"))) continue;
            if (!q.isEmpty() && !lower.contains(q)) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void exportLog(boolean all) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(all ? Lang.t("导出全部日志") : Lang.t("导出当前日志"));
        chooser.setInitialFileName(all ? "launcher-full.log" : "launcher-current.log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("日志文件"), "*.log"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        Thread worker = new Thread(() -> {
            try {
                String content = all ? Log.exportAll() : Log.exportCurrent();
                if (content.isEmpty()) content = String.join("\n", data.log);
                Files.writeString(file.toPath(), Redact.apply(content), java.nio.charset.StandardCharsets.UTF_8);
                javafx.application.Platform.runLater(() -> win.toast("导出完成", file.getName(), 1));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("导出失败", msg, 3));
            }
        }, "TCL-ExportLog");
        worker.setDaemon(true);
        worker.start();
    }

    private void buildCache(VBox page) {
        header(page, "存储与清理", "看清启动器与游戏各自的磁盘占用，安全释放空间。");
        Path base = Resources.home();
        Path cacheDir = data.cacheDir();

        Ui.Card overview = addCard(page, "存储总览");
        Label note = Ui.label("后台计算中…", 12, Theme.gray3);
        note.setWrapText(true);
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

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> Threads.start("TCL-Cache-Scan", () -> {
            long game = dirSize(Paths.get(data.gameDir), 30000);
            long cache = dirSize(cacheDir, 20000);
            long logs = logSize(Resources.logDir());
            long temp = tempSize(cacheDir);
            Platform.runLater(() -> {
                bars.getChildren().setAll(
                        sizeBar("游戏目录", game, game + cache + logs),
                        sizeBar("启动器缓存", cache, game + cache + logs),
                        sizeBar("日志文件", logs, game + cache + logs));
                note.setText(Lang.t("仅统计磁盘占用；清理操作只作用于下方勾选的可再生缓存。"));
                totalLabel.setText(Lang.t("可清理合计") + " " + bytes(cache + temp + logs));
                list.getChildren().setAll(
                        cacheRow("启动器下载缓存", cacheCheck, cache),
                        cacheRow("临时日志文件", logCheck, logs),
                        cacheRow("临时文件（.tmp / .part）", tempCheck, temp));
            });
        });
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
                                Lang.t("将删除：\n· ") + (cacheCheck.isSelected() ? Lang.t("启动器下载缓存") : Lang.t("（跳过）启动器下载缓存"))
                                        + "\n· " + (logCheck.isSelected() ? Lang.t("临时日志文件") : Lang.t("（跳过）临时日志文件"))
                                        + "\n· " + (tempCheck.isSelected() ? Lang.t("临时文件（.tmp / .part）") : Lang.t("（跳过）临时文件"))
                                        + Lang.t("\n\n不会删除游戏存档与已安装版本。是否继续？"),
                                () -> {
                                    boolean clearCache = cacheCheck.isSelected();
                                    boolean clearTemp = tempCheck.isSelected();
                                    boolean clearLog = logCheck.isSelected();
                                    Threads.start("TCL-Cache-Clean", () -> {
                                        long freed = 0;
                                        if (clearCache) {
                                            freed += dirSize(cacheDir, 20000);
                                            deleteContents(cacheDir);
                                        }
                                        if (clearTemp) freed += deleteTemp(cacheDir);
                                        if (clearLog) freed += deleteLogs(Resources.logDir());
                                        long total = freed;
                                        Platform.runLater(() -> {
                                            win.toast("清理完成", Lang.t("已释放 ") + bytes(total), 1);
                                            if (refresh[0] != null) refresh[0].run();
                                        });
                                    });
                                }, true)),
                Ui.button("检查无用文件", Ui.BTN_NORMAL, () -> Threads.start("TCL-Junk-Scan", () -> {
                    long[] result = scanJunk(cacheDir, base);
                    Platform.runLater(() -> win.toast("检查完成",
                            result[0] == 0 ? Lang.t("没有发现无用文件")
                                    : Lang.tf("发现 %d 个无用文件，共 %s", result[0], bytes(result[1])),
                            result[0] == 0 ? 1 : 0));
                })),
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
        Label lab = Ui.label(Lang.t(title) + "    " + bytes(size), 12.5, Theme.text);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox row = new HBox(10, lab, sp, box);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox sizeBar(String title, long value, long total) {
        Label lab = Ui.label(Lang.t(title) + "    " + bytes(value), 12, Theme.gray2);
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
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".log"))
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
        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".tmp") || n.endsWith(".part");
    }

    private long[] scanJunk(Path cacheDir, Path base) {
        long count = 0, size = 0;
        long cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        for (Path root : new Path[]{cacheDir, base}) {
            if (!Files.isDirectory(root)) continue;
            try (var s = Files.walk(root, 4)) {
                var it = s.filter(Files::isRegularFile).iterator();
                while (it.hasNext()) {
                    Path p = it.next();
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
            var it = s.filter(Files::isRegularFile).filter(this::isTempFile).iterator();
            while (it.hasNext()) {
                Path p = it.next();
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
                    .filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".log")).toList()) {
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

    private static java.util.List<String> deleteDirCollect(Path dir, java.util.List<String> failedSink) {
        if (!Files.isDirectory(dir)) return failedSink;
        Path running = Resources.jar(); // 运行中的 jar 被 JVM 锁定 跳过
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path p,
                        java.nio.file.attribute.BasicFileAttributes attrs) {
                    try {
                        Path name = p.getFileName();
                        if (name != null && "TCraftLauncher.jar".equals(name.toString())) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        if (running != null && p.toAbsolutePath().normalize().equals(running)) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        Files.deleteIfExists(p);
                    } catch (Exception e) {
                        failedSink.add(p.toString());
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path folder, java.io.IOException exc) {
                    try {
                        Files.deleteIfExists(folder);
                    } catch (Exception e) {
                        failedSink.add(folder.toString());
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            failedSink.add(dir.toString() + "（目录遍历失败）");
        }
        return failedSink;
    }

    private void deleteDir(Path dir) {
        if (Files.isSymbolicLink(dir)) {
            try {
                Files.deleteIfExists(dir);
            } catch (Exception ignored) {
            }
            return;
        }
        deleteDirCollect(dir, new java.util.ArrayList<>());
    }

    private void deleteContents(Path dir) {
        if (!Files.isDirectory(dir) || Files.isSymbolicLink(dir)) return;
        try (var stream = Files.list(dir)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                try {
                    deleteDir(it.next());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String bytes(long b) {
        if (b > 1024L * 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.2f GB", b / 1024.0 / 1024 / 1024);
        if (b > 1024L * 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", b / 1024.0 / 1024);
        if (b > 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", b / 1024.0);
        return b + " B";
    }

    private void buildNetwork(VBox page) {
        header(page, "网络", "代理、下载源连通性与测速。");
        Ui.Card proxy = addCard(page, "代理");
        String[] modes = {"不使用代理", "跟随系统代理", "HTTP 代理", "SOCKS5 代理"};
        int mode = Math.max(0, Math.min(3, data.getInt("SystemHttpProxyType", 1)));
        ComboBox<String> modeCombo = Ui.combo(modes, mode);
        proxy.add(Ui.settingRow("代理模式", modeCombo));
        javafx.animation.PauseTransition proxyApply =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
        proxyApply.setOnFinished(e -> data.applyProxy());
        java.util.function.Consumer<String> touchProxy = v -> proxyApply.playFromStart();
        TextField host = textRow(proxy, "主机", "例如 127.0.0.1", data.get("SystemProxyHost", ""),
                s -> { data.set("SystemProxyHost", s); touchProxy.accept(s); });
        TextField port = textRow(proxy, "端口", "7890", data.get("SystemProxyPort", "7890"),
                s -> { data.set("SystemProxyPort", s); touchProxy.accept(s); });
        TextField user = textRow(proxy, "用户名", "可选", data.get("SystemProxyUser", ""),
                s -> { data.set("SystemProxyUser", s); touchProxy.accept(s); });
        javafx.scene.control.PasswordField pass = Ui.password("可选");
        pass.setText(data.get("SystemProxyPass", ""));
        pass.setPrefWidth(260);
        pass.textProperty().addListener((o, a, b) -> {
            data.set("SystemProxyPass", b);
            touchProxy.accept(b);
        });
        proxy.add(Ui.settingRow("密码", pass));
        Ui.Check forGame = directCheck("启动游戏时使用此代理", "SystemHttpProxyForGame", false);
        proxy.add(forGame);
        Label proxyNote = Ui.label("", 12, Theme.gray3);
        proxyNote.setWrapText(true);
        proxy.add(proxyNote);
        StackPane testProxyBtn = Ui.button("测试当前代理", Ui.BTN_NORMAL, () -> {
            proxyNote.setText(Lang.t("正在测试代理…"));
            Threads.start("TCL-Proxy-Test", () -> {
                String result = testProxy();
                Platform.runLater(() -> {
                    proxyNote.setText(result);
                    win.toast(result.startsWith("连接成功") ? "代理可用" : "代理不可用", result,
                            result.startsWith("连接成功") ? 1 : 3);
                });
            });
        });
        Runnable applyEnabled = () -> {
            int i = modeCombo.getSelectionModel().getSelectedIndex();
            boolean needHost = i >= 2;
            host.setDisable(!needHost);
            port.setDisable(!needHost);
            user.setDisable(!needHost);
            pass.setDisable(!needHost);
            testProxyBtn.setDisable(i == 0);
            forGame.setDisable(i == 0);
        };
        modeCombo.setOnAction(e -> {
            int i = modeCombo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            data.set("SystemHttpProxyType", i);
            data.applyProxy();
            applyEnabled.run();
        });
        applyEnabled.run();
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
                testProxyBtn);
        proxy.add(actions);
        proxyNote.setText(Data.get().describeProxy());

        Ui.Card update = addCard(page, "TCL 检查更新");
        update.add(directCheck("启动时自动检查更新", "CheckUpdateOnStartup", true));
        String[] updateNames = {"自动测速选最快（默认）", "官方源", "gh-proxy.com", "gh-proxy.org",
                "ghfast.top", "ghproxy.net", "ghproxy.imciel.com", "自定义镜像"};
        int[] updateModes = {1, 0, 2, 3, 4, 5, 6, 7};
        int updateMode = Math.max(0, Math.min(7, data.getInt("SystemUpdateMirrorMode", 1)));
        int updateIndex = 0;
        for (int i = 0; i < updateModes.length; i++) {
            if (updateModes[i] == updateMode) {
                updateIndex = i;
                break;
            }
        }
        ComboBox<String> combo = Ui.combo(updateNames, updateIndex);
        combo.setPrefWidth(220);
        combo.setMaxWidth(Double.MAX_VALUE);
        update.add(Ui.settingRow("更新源", combo));
        TextField updateUrl = textRow(update, "自定义地址", "https://gh-proxy.com/",
                data.get("SystemUpdateMirrorUrl", ""),
                s1 -> data.set("SystemUpdateMirrorUrl", s1 == null ? "" : s1.trim()));
        updateUrl.setDisable(updateMode != 7);
        combo.setOnAction(e -> {
            int i = combo.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            int chosenMode = updateModes[i];
            data.set("SystemUpdateMirrorMode", chosenMode);
            updateUrl.setDisable(chosenMode != 7);
        });
        update.add(Ui.hint("默认自动测速选最快节点；只影响版本号与更新日志的读取；点「前往下载」统一跳官网，由用户自己选节点下载。", 0));

        Ui.Card mirror = addCard(page, "资源镜像");
        TextField mirrorUrl = textRow(mirror, "镜像地址", "https://mod.mcimirror.top",
                data.get("SystemMirrorUrl", ""), s1 -> {
                    data.set("SystemMirrorUrl", s1 == null ? "" : s1.trim());
                    clearMirrorCaches();
                });
        comboRow(mirror, "Mod 镜像源",
                new String[]{"官方源", "内置镜像（mod.mcimirror.top）", "手动填写", "自动测速（官方 / 内置镜像，默认）"},
                Math.max(0, Math.min(3, data.getInt("SystemMirrorMode", 3))),
                i -> {
                    int mirrorModeValue = Math.max(0, Math.min(3, i));
                    data.set("SystemMirrorMode", mirrorModeValue);
                    mirrorUrl.setDisable(mirrorModeValue != 2);
                    clearMirrorCaches();
                    data.save();
                });
        mirrorUrl.setDisable(data.getInt("SystemMirrorMode", 3) != 2);
        Label mirrorHint = Ui.label("影响 Modrinth / CurseForge 的接口、图标与文件下载。默认自动测速，官方与内置镜像同时作为候选，下载时按测速结果择优；某个源失败会自动换另一个。"
                + "内置镜像为第三方公益服务，也可以填自己的反代地址。", 12, Theme.gray3);
        mirrorHint.setWrapText(true);
        mirror.add(mirrorHint);

        Ui.Card speed = addCard(page, "下载源测速");
        Label speedNote = Ui.label("测速结果只代表当前网络到该源的连通质量，不影响已下载的内容。", 12, Theme.gray3);
        speedNote.setWrapText(true);
        speed.add(speedNote);
        TextField customRoot = textRow(speed, "自定义源", "留空用内置源", data.get("SystemBmclRoot", ""),
                s -> data.set("SystemBmclRoot", s == null ? "" : s.trim()));
        Label customHint = Ui.label("填自建或内网的 BMCLAPI 兼容镜像根（如 http://192.168.1.10:8080），"
                + "填了会一起测速；下载时「镜像优先」会自动走它。留空则只测官方源与内置 BMCLAPI。", 12, Theme.gray3);
        customHint.setWrapText(true);
        speed.add(customHint);
        String[] sources = {"官方源 (piston-meta.mojang.com)", "BMCLAPI (bmclapi2.bangbang93.com)", "自定义源"};
        int[] sourceIndex = {1, 2};
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
        Runnable syncCustomRow = () -> {
            String c = customRoot.getText() == null ? "" : customRoot.getText().trim();
            rows[2].setVisible(!c.isEmpty());
            names[2].setText(Lang.t("自定义源") + " (" + (c.isEmpty() ? Lang.t("未设置") : c) + ")");
        };
        customRoot.textProperty().addListener((o, a, b) -> syncCustomRow.run());
        syncCustomRow.run();
        speed.add(results);
        Label summary = Ui.label("点「开始测速」检查各下载源的连通质量。", 12, Theme.gray3);
        speed.add(summary);
        final int[] recommended = {-1};
        final boolean[] tested = {false};
        final int[] customOn = {0};
        FlowPane speedActions = new FlowPane(10, 10);
        StackPane applyBtn = Ui.button("应用推荐源", Ui.BTN_NORMAL, () -> {
            if (!tested[0] || recommended[0] < 0) {
                win.toast("请先测速", "还没有可用的测速结果，点「开始测速」后再应用", 0);
                return;
            }
            int idx = recommended[0];
            if (idx >= 2) {
                data.set("SystemDownloadSource", 2);
                data.set("SystemVersionSource", 0);
                win.toast("已应用", Lang.t("下载源已切换为 ") + names[2].getText(), 1);
            } else {
                int value = sourceIndex[idx];
                data.set("SystemDownloadSource", value);
                data.set("SystemVersionSource", idx == 0 ? 1 : 0);
                win.toast("已应用", Lang.t("下载源已切换为 ") + Lang.t(sources[idx]), 1);
            }
            data.save();
        });
        final StackPane[] testBtn = new StackPane[1];
        testBtn[0] = Ui.button("开始测速", Ui.BTN_HIGHLIGHT, () -> {
            summary.setText(Lang.t("测速中…（单次超时 5 秒）"));
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
            Threads.start("TCL-SourceTest", () -> {
                long[] ms = new long[liveUrls.length];
                customOn[0] = customUrl.isEmpty() ? 0 : 1;
                for (int i = 0; i < liveUrls.length; i++) {
                    if (i == 2 && customUrl.isEmpty()) { ms[i] = -2; continue; }
                    long start = System.currentTimeMillis();
                    java.net.HttpURLConnection conn = null;
                    try {
                        conn = (java.net.HttpURLConnection)
                                java.net.URI.create(liveUrls[i]).toURL().openConnection();
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestMethod("HEAD");
                        conn.getResponseCode();
                        ms[i] = System.currentTimeMillis() - start;
                    } catch (Exception e) {
                        ms[i] = -1;
                    } finally {
                        if (conn != null) conn.disconnect();
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
                            values[i].setText(Lang.t("未设置"));
                            values[i].setTextFill(Theme.gray3);
                            continue;
                        }
                        values[i].setText(ms[i] < 0 ? Lang.t("超时") : ms[i] + " ms");
                        values[i].setTextFill(ms[i] < 0 ? Theme.red
                                : ms[i] < 100 ? Theme.green : ms[i] < 300 ? Theme.orange : Theme.red);
                    }
                    summary.setText(bestIndex < 0 ? Lang.t("全部下载源均不可用，请检查网络或代理设置。")
                            : Lang.t("推荐：") + bestName);
                    testBtn[0].setDisable(false);
                });
            });
        });
        speedActions.getChildren().addAll(testBtn[0], applyBtn);
        speed.add(speedActions);
    }

    private String testProxy() {
        Data d = Data.get();
        int mode = d.getInt("SystemHttpProxyType", 1);
        String host = d.get("SystemProxyHost", "");
        String port = d.get("SystemProxyPort", "7890");
        java.net.Proxy proxy;
        try {
            if (mode == 0 || mode == 1) {
                proxy = null; // 无代理或跟随系统 由 JVM 系统属性决定
            } else if (mode == 3) {
                proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                        new java.net.InetSocketAddress(host, Integer.parseInt(port)));
            } else {
                proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(host, Integer.parseInt(port)));
            }
        } catch (Exception e) {
            return Lang.tf("代理参数无效：%s", e.getMessage());
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
        Ui.Card backup = addCard(page, "数据备份");
        Label backupText = Ui.label("导出启动器配置、实例记录、插件、皮肤和预设；换电脑或重装前先做一份备份。"
                + "导入备份会自动解压并重启，旧数据保留为 .tcl.pre-import-*。", 12.5, Theme.gray2);
        backupText.setWrapText(true);
        backup.add(backupText);
        FlowPane backupActions = new FlowPane(10, 10);
        backupActions.getChildren().addAll(
                Ui.button("导出全部数据", Ui.BTN_HIGHLIGHT, () -> DataBackup.chooseExport(win)),
                Ui.button("导入备份并重启", Ui.BTN_NORMAL, () -> DataBackup.chooseImport(win)));
        backup.add(backupActions);
        communityCard(page);
    }

    private static void clearMirrorCaches() {
        Modrinth.clearCache();
        CurseForge.clearCache();
        RemoteImages.clearFailures();
    }

    private ComboBox<String> comboRow(Ui.Card card, String label, String[] items, int selected, Consumer<Integer> onChange) {
        ComboBox<String> combo = Ui.combo(items, selected);
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
        return combo;
    }

    private TextField textRow(Ui.Card card, String label, String hint, String value, Consumer<String> onChange) {
        TextField field = Ui.field(hint);
        field.setText(value == null ? "" : value);
        field.setPrefWidth(260);
        field.textProperty().addListener((o, a, b) -> onChange.accept(b));
        card.add(Ui.settingRow(label, field));
        return field;
    }

    private void commitCachePath(TextField field) {
        String typed = field.getText() == null ? "" : field.getText().trim();
        data.set("SystemSystemCache", typed);
        try {
            Path actual = data.cacheDir();
            if (!typed.isEmpty()) {
                Path wanted = Paths.get(typed).toAbsolutePath().normalize();
                if (!wanted.equals(actual.toAbsolutePath().normalize())) {
                    // 非空且无缓存标记的目录不接管 防止清理缓存误删用户文件
                    field.clear();
                    data.set("SystemSystemCache", "");
                    win.toast("缓存目录未启用", "该目录非空且不是缓存目录，已回退到默认目录。", 3);
                }
            }
        } catch (Exception e) {
            field.clear();
            data.set("SystemSystemCache", "");
            win.toast("缓存目录无效", "已回退到默认目录。", 3);
        }
        RemoteImages.resetCacheDir();
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

    // 拖动更新数字 80ms 后重建
    private Ui.SliderBar sliderRowDebounced(Ui.Card card, String label, double min, double max, double value,
                                            Consumer<Double> onChange, String suffix) {
        Ui.SliderBar slider = Ui.sliderBar(min, max, value);
        slider.setPrefWidth(240);
        Label val = Ui.label(Math.round(value) + suffix, 12, Theme.text);
        val.setMinWidth(54);
        final double[] pending = {value};
        final boolean[] waiting = {false};
        final javafx.animation.PauseTransition debounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(80));
        debounce.setOnFinished(e -> {
            if (!waiting[0]) return;
            waiting[0] = false;
            onChange.accept(pending[0]);
        });
        slider.setOnChanged(v -> {
            val.setText(Math.round(v) + suffix);
            pending[0] = v;
            waiting[0] = true;
            debounce.playFromStart();
        });
        HBox box = new HBox(10, slider, val);
        box.setAlignment(Pos.CENTER_LEFT);
        card.add(Ui.settingRow(label, box));
        return slider;
    }
}
