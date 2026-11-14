package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 版本设置页：概览、日志、选项、文件管理和导出。 */
public class PageInstanceSetup implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private VBox leftList;

    /** 实例根目录，folder 为空时回退全局游戏目录。 */
    private java.nio.file.Path instanceRoot() {
        Data.Instance ins = data.instance();
        String folder = ins != null && ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir;
        return java.nio.file.Paths.get(folder);
    }

    public PageInstanceSetup(MainWindow win) {
        this.win = win;
    }

    @Override
    public double leftWidth() {
        return 250;
    }

    @Override
    public String sectionKey() {
        return data.instanceSection;
    }

    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(12, 8, 10, 8));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        Data.Instance ins = data.instance();
        if (ins != null) {
            Label head = Ui.bold(ins.name, 14, Theme.text);
            head.setWrapText(true);
            head.setPadding(new Insets(2, 6, 8, 6));
            panel.getChildren().add(head);
        }
        leftList = new VBox(0);
        leftList.getChildren().addAll(
                nav("概览", "box", "overview"),
                nav("诊断", "activity", "diagnostics"),
                nav("游戏选项", "sliders-horizontal", "options"),
                nav("历史", "history", "history"),
                nav("设置", "settings", "settings"),
                nav("Mod 管理", "puzzle", "mods"),
                nav("资源包", "palette", "resourcepacks"),
                nav("光影", "sun", "shaders"),
                nav("存档", "map", "saves"),
                nav("存档数据包", "file-box", "savesdatapack"),
                nav("截图", "image", "screenshots"),
                nav("服务器", "server", "server"),
                nav("嵌套依赖", "boxes", "jarinjar"),
                nav("整合包清单", "package", "complist"),
                nav("导出", "upload", "export")
        );
        Region gap = new Region();
        gap.setMinHeight(6);
        leftList.getChildren().add(gap);
        leftList.getChildren().addAll(
                clickable("安装新版本", "download", () -> win.openPage("download", true)),
                clickable("复制实例", "copy", this::copyInstance),
                clickable("备份与恢复", "archive", this::backupInstance)
        );
        ScrollPane scroll = Ui.scroll(leftList);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        panel.getChildren().add(scroll);
        Ui.ListItem back = new Ui.ListItem(Theme.icon("arrow-left", 16, Theme.text), "返回", null, 34);
        back.setOnMouseClicked(e -> win.openPage("instance", true));
        panel.getChildren().add(back);
        return panel;
    }

    private Node nav(String title, String icon, String key) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 16, Theme.text), title, null, 34);
        item.setUserData(key); // refreshRight 用来同步左栏选中态
        item.setSelected(key.equals(data.instanceSection));
        item.setOnMouseClicked(e -> {
            if (key.equals(data.instanceSection)) return;
            data.instanceSection = key;
            win.refreshRight();
        });
        if ("settings".equals(key)) {
            StackPane reset = Ui.iconButton("rotate-ccw", 22, 13, "初始化本页设置", this::resetSettings);
            reset.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(reset);
        }
        if ("mods".equals(key) || "saves".equals(key) || "screenshots".equals(key)) {
            StackPane refresh = Ui.iconButton("refresh-cw", 22, 13, "刷新", () -> win.refreshRight());
            refresh.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(refresh);
        }
        return item;
    }

    private Node clickable(String title, String icon, Runnable action) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 16, Theme.text), title, null, 34);
        item.setOnMouseClicked(e -> action.run());
        return item;
    }

    private void resetSettings() {
        data.set("InstanceIsolation", 0);
        data.set("InstanceRam", 4096);
        data.set("InstanceJvmArgs", "");
        data.set("InstanceWindowTitle", "");
        win.refreshRight();
        win.toast("已重置", "版本设置已恢复默认", 1);
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox page = new VBox();
        page.setPadding(new Insets(22, 28, 28, 28));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(page);
        switch (data.instanceSection) {
            case "diagnostics" -> buildDiagnostics(page);
            case "options" -> buildOptions(page);
            case "history" -> buildHistory(page);
            case "settings" -> buildSettings(page);
            case "mods" -> buildMods(page);
            case "resourcepacks" -> buildPacks(page, "resourcepacks", "资源包");
            case "shaders" -> buildPacks(page, "shaderpacks", "光影");
            case "saves" -> buildSaves(page);
            case "savesdatapack" -> buildSavesDatapack(page);
            case "screenshots" -> buildScreenshots(page);
            case "server" -> buildServer(page);
            case "jarinjar" -> buildJarInJar(page);
            case "complist" -> buildCompManifest(page);
            case "export" -> buildExport(page);
            default -> buildOverview(page);
        }
        return scroll;
    }

    private void header(VBox page, String title, String subtitle) {
        VBox h = new VBox(5);
        Label sub = Ui.label(subtitle, 13, Theme.text);
        sub.setOpacity(0.55);
        h.getChildren().addAll(Ui.bold(title, 30, Theme.text), sub);
        VBox.setMargin(h, new Insets(0, 0, 18, 0));
        page.getChildren().add(h);
    }

    private Ui.Card addCard(VBox page, String title) {
        Ui.Card card = Ui.card(title);
        card.setSpacing(2);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));
        page.getChildren().add(card);
        return card;
    }

    private void buildOverview(VBox page) {
        Data.Instance ins = data.instance();
        if (ins == null) {
            page.getChildren().add(Ui.hint("当前没有选中的实例。", 1));
            return;
        }
        HBox hero = new HBox(18);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setMaxWidth(Double.MAX_VALUE);
        hero.setPadding(new Insets(4, 0, 16, 0));
        StackPane iconBox = new StackPane(Ui.image(ins.icon, 64, 64, true));
        iconBox.setMinSize(64, 64);
        VBox info = new VBox(4);
        Label name = Ui.bold(ins.name, 25, Theme.text);
        Label sub = Ui.label(ins.subtitle(), 12.5, Theme.gray2);
        HBox tags = new HBox(6);
        tags.getChildren().add(tag(ins.version, Theme.accent));
        if (ins.loader != null && !ins.loader.isEmpty() && !"原版".equals(ins.loader)) {
            tags.getChildren().add(tag(ins.loader + (ins.loaderVersion == null || ins.loaderVersion.isEmpty() ? "" : " " + ins.loaderVersion), Theme.green));
        }
        if (ins.favorite) tags.getChildren().add(tag("收藏", Theme.orange));
        info.getChildren().addAll(name, sub, tags);
        hero.getChildren().addAll(iconBox, info);

        hero.getChildren().add(Ui.spacer());
        page.getChildren().add(hero);

        FlowPane buttons = new FlowPane(10, 8);
        buttons.getChildren().addAll(
                Ui.button("启动游戏", Ui.BTN_THEME, () -> {
                    data.selectedInstance = data.instances.indexOf(ins);
                    data.save();
                    win.launchGame();
                }),
                Ui.button("打开文件夹", Ui.BTN_NORMAL, () -> openFolder(ins.folder)),
                Ui.button("复制实例", Ui.BTN_NORMAL, this::copyInstance),
                Ui.button("删除实例", Ui.BTN_RED, () -> Dialogs.confirm(win, "删除实例", "确定要删除实例「" + ins.name + "」吗？",
                        () -> {
                            data.instances.remove(ins);
                            data.selectedInstance = 0;
                            data.save();
                            win.openPage("instance", true);
                            win.toast("实例已删除", ins.name, 1);
                        }, true))
        );
        VBox.setMargin(buttons, new Insets(0, 0, 16, 0));
        page.getChildren().add(buttons);

        Ui.Card stats = addCard(page, "实例信息");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        stats.add(row("游戏版本", ins.version),
                row("加载器", ins.loader == null || ins.loader.isEmpty() ? "原版" : ins.loader + (ins.loaderVersion == null || ins.loaderVersion.isEmpty() ? "" : " " + ins.loaderVersion)),
                row("上次游玩", ins.lastPlayed <= 0 ? "从未启动" : Instant.ofEpochMilli(ins.lastPlayed).atZone(ZoneId.systemDefault()).format(fmt)),
                row("游戏目录", ins.folder),
                row("Java 路径", data.javaPath.isEmpty() ? "未找到" : data.javaPath));

        Ui.Card actions = addCard(page, "快捷操作");
        HBox quick = new HBox(10);
        quick.getChildren().addAll(
                Ui.button("安装新版本", Ui.BTN_NORMAL, () -> win.openPage("download", true)),
                Ui.button("查看存档", Ui.BTN_NORMAL, () -> {
                    data.instanceSection = "saves";
                    win.refreshRight();
                }),
                Ui.button("管理 Mod", Ui.BTN_NORMAL, () -> {
                    data.instanceSection = "mods";
                    win.refreshRight();
                }),
                Ui.button("打开日志", Ui.BTN_NORMAL, () -> win.openSetup("log")));
        actions.add(quick);
    }

    private Node tag(String text, Color color) {
        Label t = Ui.label(text, 11, Color.WHITE);
        t.setPadding(new Insets(2, 8, 2, 8));
        t.setBackground(Ui.fill(color, 8));
        return t;
    }

    private Node row(String k, String v) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(34);
        Label key = Ui.label(k, 12.5, Theme.gray2);
        key.setMinWidth(110);
        Label value = Ui.label(v == null ? "" : v, 12.5, Theme.text);
        value.setWrapText(true);
        row.getChildren().addAll(key, value);
        return row;
    }

    private static final int DIAG_MAX_LINES = 4000;
    private static final long DIAG_MAX_BYTES = 8L * 1024 * 1024;
    private final java.util.List<String> diagSourceNames = new ArrayList<>();
    private final java.util.List<Path> diagSourcePaths = new ArrayList<>();
    private final java.util.List<String[]> diagAll = new ArrayList<>();   // {text, level}
    private final java.util.List<String[]> diagDisplay = new ArrayList<>();
    private ComboBox<String> diagSourceCombo;
    private Label diagStatus;
    private Label diagPath;
    private javafx.scene.control.ListView<String[]> diagList;
    private Ui.Check diagChkError;
    private Ui.Check diagChkWarn;
    private Ui.Check diagChkInfo;
    private Ui.Check diagChkDebug;
    private TextField diagSearch;

    private void buildDiagnostics(VBox page) {
        header(page, "诊断", "查看游戏日志、崩溃报告与启动器日志，定位启动失败的原因。");
        diagAll.clear();
        diagDisplay.clear();
        diagSourceNames.clear();
        diagSourcePaths.clear();
        // 来源：latest.log、最近 12 份崩溃报告、启动器日志
        Path latest = instanceRoot().resolve("logs").resolve("latest.log");
        if (Files.isRegularFile(latest)) diagAddSource("游戏最新日志（latest.log）", latest);
        Path crashDir = instanceRoot().resolve("crash-reports");
        if (Files.isDirectory(crashDir)) {
            try (java.util.stream.Stream<Path> st = Files.list(crashDir)) {
                st.filter(Files::isRegularFile)
                        .sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                        .limit(12)
                        .forEach(p -> diagAddSource("崩溃报告 · " + p.getFileName(), p));
            } catch (Exception ignored) {
            }
        }
        Path launcherLog = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx", "launcher.log");
        if (Files.isRegularFile(launcherLog)) diagAddSource("启动器日志（当前）", launcherLog);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        diagSourceCombo = Ui.combo(diagSourceNames.toArray(new String[0]), 0);
        diagSourceCombo.setPrefWidth(300);
        diagSourceCombo.setOnAction(e -> diagLoadCurrent(page));
        controls.getChildren().add(diagSourceCombo);
        controls.getChildren().add(Ui.button("刷新", Ui.BTN_NORMAL, () -> {
            win.openSetup("diagnostics");
            win.toast("日志已刷新。", "", 1);
        }));
        diagChkError = Ui.checkBox("错误", true);
        diagChkWarn = Ui.checkBox("警告", true);
        diagChkInfo = Ui.checkBox("信息", true);
        diagChkDebug = Ui.checkBox("调试", true);
        for (Ui.Check c : new Ui.Check[]{diagChkError, diagChkWarn, diagChkInfo, diagChkDebug}) {
            c.setOnAction(() -> diagApplyFilter());
            controls.getChildren().add(c);
        }
        page.getChildren().add(controls);
        VBox.setMargin(controls, new Insets(0, 0, 10, 0));

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        diagSearch = Ui.field("搜索日志关键字");
        diagSearch.textProperty().addListener((o, a, b) -> diagApplyFilter());
        HBox.setHgrow(diagSearch, Priority.ALWAYS);
        searchRow.getChildren().addAll(diagSearch,
                Ui.button("复制", Ui.BTN_NORMAL, this::diagCopy),
                Ui.button("导出", Ui.BTN_NORMAL, this::diagExport),
                Ui.button("崩溃分析", Ui.BTN_NORMAL, this::diagAnalyze));
        page.getChildren().add(searchRow);
        VBox.setMargin(searchRow, new Insets(0, 0, 10, 0));

        diagList = new javafx.scene.control.ListView<>();
        diagList.setFixedCellSize(17);
        diagList.setPrefHeight(360);
        diagList.setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas'; -fx-font-size: 12;");
        diagList.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            private final Label lab = new Label();

            {
                lab.setWrapText(false);
                lab.setMaxWidth(Double.MAX_VALUE);
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                lab.setText(item[0]);
                int level = Integer.parseInt(item[1]);
                lab.setTextFill(switch (level) {
                    case 0 -> Color.web("#c62828");
                    case 1 -> Color.web("#b26a00");
                    case 3 -> Color.web("#8c8c8c");
                    default -> Color.web("#3a3a3a");
                });
                setGraphic(lab);
            }
        });
        Ui.Card listCard = addCard(page, "日志内容");
        listCard.setContentPadding(new Insets(4, 8, 8, 8));
        listCard.add(diagList);

        diagStatus = Ui.label("没有可用的日志来源。", 12, Theme.gray2);
        diagPath = Ui.label("", 11.5, Theme.gray3);
        page.getChildren().addAll(diagStatus, diagPath);
        diagLoadCurrent(page);
    }

    private void diagAddSource(String name, Path path) {
        diagSourceNames.add(name);
        diagSourcePaths.add(path);
    }

    /** 读当前来源，尾部 8MB、最多 4000 行。 */
    private void diagLoadCurrent(VBox page) {
        diagAll.clear();
        diagDisplay.clear();
        int index = diagSourceCombo == null ? -1 : diagSourceCombo.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= diagSourcePaths.size()) {
            diagStatus.setText("没有可用的日志来源。");
            diagPath.setText("");
            diagApplyFilter();
            return;
        }
        Path target = diagSourcePaths.get(index);
        diagPath.setText(target.toString());
        diagStatus.setText("正在读取…");
        Thread t = new Thread(() -> {
            java.util.List<String[]> result = new ArrayList<>();
            String failed = null;
            try {
                if (Files.isRegularFile(target)) {
                    long length = Files.size(target);
                    java.io.RandomAccessFile raf = new java.io.RandomAccessFile(target.toFile(), "r");
                    if (length > DIAG_MAX_BYTES) raf.seek(length - DIAG_MAX_BYTES);
                    java.util.List<String> raw = new ArrayList<>();
                    String line;
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(new java.io.FileInputStream(raf.getFD()), StandardCharsets.UTF_8))) {
                        while ((line = reader.readLine()) != null) {
                            raw.add(line);
                            if (raw.size() > DIAG_MAX_LINES * 2) raw.subList(0, raw.size() - DIAG_MAX_LINES).clear();
                        }
                    }
                    raf.close();
                    int start = Math.max(0, raw.size() - DIAG_MAX_LINES);
                    for (int i = start; i < raw.size(); i++) {
                        result.add(new String[]{raw.get(i), String.valueOf(diagLevel(raw.get(i)))});
                    }
                }
            } catch (Exception e) {
                failed = e.getMessage();
            }
            java.util.List<String[]> fResult = result;
            String fFailed = failed;
            javafx.application.Platform.runLater(() -> {
                diagAll.clear();
                for (String[] l : fResult) diagAll.add(l);
                if (fFailed != null) diagStatus.setText("读取失败：" + fFailed);
                diagApplyFilter();
                if (fFailed == null && diagList != null) diagList.scrollTo(diagDisplay.size() - 1);
            });
        }, "TCL-DiagRead");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private static int diagLevel(String line) {
        if (line.contains("/FATAL]") || line.contains("/ERROR]") || line.contains("[ERROR]")
                || line.startsWith("Caused by:")) return 0;
        if (line.contains("/WARN]") || line.contains("[WARN]")) return 1;
        if (line.contains("/DEBUG]") || line.contains("/TRACE]")) return 3;
        return 2;
    }

    /** 级别筛选 + 关键字过滤。 */
    private void diagApplyFilter() {
        if (diagList == null) return;
        diagDisplay.clear();
        String keyword = diagSearch == null || diagSearch.getText() == null ? "" : diagSearch.getText().trim().toLowerCase();
        for (String[] item : diagAll) {
            int level = Integer.parseInt(item[1]);
            if (level == 0 && !diagChkError.isSelected()) continue;
            if (level == 1 && !diagChkWarn.isSelected()) continue;
            if (level == 2 && !diagChkInfo.isSelected()) continue;
            if (level == 3 && !diagChkDebug.isSelected()) continue;
            if (!keyword.isEmpty() && !item[0].toLowerCase().contains(keyword)) continue;
            diagDisplay.add(item);
        }
        diagList.getItems().setAll(diagDisplay);
        boolean empty = diagDisplay.isEmpty();
        diagStatus.setText(diagAll.isEmpty() ? "这个来源还没有内容。"
                : (empty ? "没有匹配当前筛选的日志。" : "共 " + diagAll.size() + " 行，显示 " + diagDisplay.size() + " 行"));
    }

    private void diagCopy() {
        if (diagDisplay.isEmpty()) {
            win.toast("没有可复制的日志。", "", 3);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] item : diagDisplay) sb.append(item[0]).append(System.lineSeparator());
        javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
        c.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
        win.toast("已复制 " + diagDisplay.size() + " 行日志。", "", 1);
    }

    private void diagExport() {
        if (diagAll.isEmpty()) {
            win.toast("没有可导出的日志。", "", 3);
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("导出日志");
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        chooser.setInitialFileName("diagnostics-" + stamp + ".log");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("日志文件", "*.log"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("文本文件", "*.txt"));
        java.io.File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (String[] item : diagAll) sb.append(diagDesensitize(item[0])).append(System.lineSeparator());
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            win.toast("日志已导出（已脱敏）。", "", 1);
            openFolder(file.getParent().toString());
        } catch (Exception e) {
            win.toast("导出失败：" + e.getMessage(), "", 3);
        }
    }

    /** 脱敏，替换路径、用户目录和 UUID。 */
    private String diagDesensitize(String line) {
        try {
            Data.Instance ins = data.instance();
            String root = instanceRoot().toString();
            line = line.replace(root, "<GAME>").replace(root + "\\", "<GAME>\\");
            line = line.replace(System.getProperty("user.home"), "<HOME>");
            line = line.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<UUID>");
        } catch (Exception ignored) {
        }
        return line;
    }

    /** 按关键字规则分析崩溃原因，不联网。 */
    private void diagAnalyze() {
        if (diagAll.isEmpty()) {
            win.toast("请先加载一份日志。", "", 3);
            return;
        }
        String[][] rules = {
                {"OutOfMemoryError", "内存不足", "把最大内存调高（设置 › 启动 › 内存），并检查是否安装了过多大型 Mod。"},
                {"Java heap space", "内存不足", "把最大内存调高，或减少同时加载的 Mod / 光影。"},
                {"UnsupportedClassVersionError", "Java 版本不匹配", "这个版本需要更高版本的 Java；到 设置 › Java 重新选择运行时。"},
                {"requires Java", "Java 版本不匹配", "按崩溃提示的版本要求更换 Java。"},
                {"Missing or unsupported mandatory dependencies", "缺少前置 Mod", "按日志列出的 modid 补齐前置 Mod，或换用兼容版本。"},
                {"Mod resolution failed", "Mod 依赖解析失败", "检查缺失依赖、重复 Mod 与版本冲突。"},
                {"Duplicate mods", "Mod 重复", "同一个 Mod 放了多个版本，删掉多余的 jar。"},
                {"Mixin apply failed", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"},
                {"MixinApplyError", "Mixin 注入失败", "常见于 Mod 与游戏版本不匹配，逐一停用最近添加的 Mod。"},
                {"java.lang.ClassNotFoundException", "缺少类 / 依赖", "确认 Forge / Fabric 等加载器安装完整，必要时修复游戏文件。"},
                {"NoSuchMethodError", "Mod 与游戏版本不匹配", "把 Mod 换成对应游戏版本的版本。"},
                {"GLFW error", "图形环境异常", "更新显卡驱动；笔记本切换到独显运行。"},
                {"Pixel format not accelerated", "显卡驱动不支持 OpenGL", "安装厂商驱动，不要用系统自带的基础驱动。"},
                {"Failed to create window", "无法创建游戏窗口", "检查显卡驱动与远程桌面 / 虚拟机环境。"},
                {"AccessDeniedException", "文件被占用或无权限", "关闭占用文件的程序，或以管理员身份运行启动器。"},
                {"The game crashed whilst", "游戏崩溃", "结合下方错误行定位；也可以在「日志」里换崩溃报告查看完整堆栈。"},
                {"Exception in thread \"main\"", "主线程异常", "查看紧随其后的 Caused by 行。"},
        };
        StringBuilder hits = new StringBuilder();
        StringBuilder evidence = new StringBuilder();
        int hitCount = 0;
        for (String[] rule : rules) {
            String found = null;
            for (String[] item : diagAll) {
                if (item[0].toLowerCase().contains(rule[0].toLowerCase())) {
                    found = item[0].trim();
                    break;
                }
            }
            if (found != null) {
                hitCount++;
                hits.append("· ").append(rule[1]).append("：").append(rule[2]).append(System.lineSeparator());
                evidence.append("    ").append(found).append(System.lineSeparator());
            }
        }
        String text;
        if (hitCount == 0) {
            StringBuilder fallback = new StringBuilder();
            int count = 0;
            for (String[] item : diagAll) {
                if (item[1].equals("0")) {
                    fallback.append("    ").append(item[0].trim()).append(System.lineSeparator());
                    if (++count >= 6) break;
                }
            }
            text = "没有匹配到已知的崩溃特征。" + System.lineSeparator() + System.lineSeparator()
                    + (count > 0 ? "最近的错误行：" + System.lineSeparator() + fallback
                    : "这份日志里也没有错误级别的行。");
        } else {
            text = "可能的原因与建议：" + System.lineSeparator() + hits
                    + System.lineSeparator() + "证据（日志原文）：" + System.lineSeparator() + evidence;
        }
        VBox card = Dialogs.msgCard("崩溃分析");
        Label body = Ui.label(text, 13, hitCount > 0 ? Theme.redDark : Theme.text);
        body.setWrapText(true);
        VBox.setMargin(body, new Insets(13, 0, 17, 0));
        card.getChildren().add(body);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private static final String[] OPT_GROUPS = {"画面", "声音", "操作", "聊天与语言"};

    /** 列依次是键、标题、分组、控件类型、最小、最大、百分比、选项。 */
    private static final Object[][] OPT_DEFS = {
            {"renderDistance", "渲染距离", "画面", 1, 2, 32, false, null},
            {"simulationDistance", "模拟距离", "画面", 1, 5, 32, false, null},
            {"graphics", "画质", "画面", 2, 0, 0, false, new String[]{"fast", "fancy", "fabulous"}},
            {"guiScale", "界面缩放（0 = 自动）", "画面", 1, 0, 4, false, null},
            {"maxFps", "最大帧率", "画面", 1, 10, 260, false, null},
            {"fov", "视野", "画面", 1, 30, 110, false, null},
            {"gamma", "亮度", "画面", 1, 0, 100, true, null},
            {"particles", "粒子效果", "画面", 2, 0, 0, false, new String[]{"all", "decreased", "minimal"}},
            {"soundCategory_master", "主音量", "声音", 1, 0, 100, true, null},
            {"soundCategory_music", "音乐", "声音", 1, 0, 100, true, null},
            {"soundCategory_blocks", "方块", "声音", 1, 0, 100, true, null},
            {"soundCategory_hostile", "敌对生物", "声音", 1, 0, 100, true, null},
            {"soundCategory_players", "玩家", "声音", 1, 0, 100, true, null},
            {"mouseSensitivity", "鼠标灵敏度", "操作", 1, 0, 200, true, null},
            {"invertYMouse", "反转鼠标 Y 轴", "操作", 0, 0, 0, false, null},
            {"autoJump", "自动跳跃", "操作", 0, 0, 0, false, null},
            {"toggleCrouch", "切换潜行", "操作", 0, 0, 0, false, null},
            {"toggleSprint", "切换疾跑", "操作", 0, 0, 0, false, null},
            {"chatVisibility", "聊天可见性", "聊天与语言", 2, 0, 0, false,
                    new String[]{"full", "system", "commands", "hidden"}},
            {"chatOpacity", "聊天不透明度", "聊天与语言", 1, 0, 100, true, null},
            {"chatScale", "聊天缩放", "聊天与语言", 1, 0, 100, true, null},
            {"lang", "游戏语言", "聊天与语言", 2, 0, 0, false,
                    new String[]{"zh_cn", "zh_tw", "en_us", "ja_jp", "ko_kr", "ru_ru", "de_de", "fr_fr", "es_es"}},
    };

    /** options.txt 键值，保持顺序，未知键原样保留。 */
    private final java.util.LinkedHashMap<String, String> optionValues = new java.util.LinkedHashMap<>();
    private boolean optionDirty = false;

    private void buildOptions(VBox page) {
        header(page, "游戏选项", "直接编辑 options.txt；保存后下次启动生效。");
        optionValues.clear();
        optionDirty = false;
        Path options = instanceRoot().resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            Ui.Card notice = addCard(page, "提示");
            notice.add(Ui.label("这个实例还没有 options.txt。先启动一次游戏，让游戏生成默认选项后再回来修改。", 13, Theme.gray2));
            return;
        }
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                optionValues.put(line.substring(0, idx), line.substring(idx + 1));
            }
        } catch (Exception e) {
            Ui.Card notice = addCard(page, "读取失败");
            notice.add(Ui.label("读取 options.txt 失败：" + e.getMessage(), 13, Theme.gray2));
            return;
        }

        for (String groupName : OPT_GROUPS) {
            java.util.List<Object[]> groupDefs = new ArrayList<>();
            for (Object[] def : OPT_DEFS) {
                if (groupName.equals(def[2]) && optionValues.containsKey((String) def[0])) groupDefs.add(def);
            }
            if (groupDefs.isEmpty()) continue;
            Ui.Card card = addCard(page, groupName);
            for (Object[] def : groupDefs) card.add(buildOptionRow(def));
        }
        if (page.getChildren().isEmpty() || page.getChildren().size() <= 1) {
            Ui.Card notice = addCard(page, "提示");
            notice.add(Ui.label("options.txt 存在，但没有包含可编辑的常用选项。可以在游戏内修改，或直接用文本编辑器打开它。", 13, Theme.gray2));
        }

        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("保存修改", Ui.BTN_HIGHLIGHT, () -> saveOptions(options)),
                Ui.button("重新载入", Ui.BTN_NORMAL, () -> {
                    if (optionDirty) {
                        Dialogs.confirm(win, "重新载入", "有未保存的修改，重新载入会丢弃它们。是否继续？", () -> {
                            win.openSetup("options");
                            win.toast("已重新载入", "", 1);
                        }, true);
                        return;
                    }
                    win.openSetup("options");
                    win.toast("已重新载入", "", 1);
                }),
                Ui.button("存为预设", Ui.BTN_NORMAL, this::saveOptionPreset),
                Ui.button("套用预设", Ui.BTN_NORMAL, this::applyOptionPreset),
                Ui.button("打开游戏选项文件夹", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().toString())));
        page.getChildren().add(actions);
    }

    /** 标题、控件和当前值一行。 */
    private HBox buildOptionRow(Object[] def) {
        String key = (String) def[0];
        String title = (String) def[1];
        int kind = (int) def[3];
        int min = (int) def[4];
        int max = (int) def[5];
        boolean percent = (boolean) def[6];
        String[] choices = (String[]) def[7];
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 0, 0));
        Label lab = Ui.label(title, 13, Theme.text);
        lab.setMinWidth(190);
        row.getChildren().add(lab);
        switch (kind) {
            case 0 -> {
                Ui.Check cb = Ui.checkBox("", "true".equalsIgnoreCase(optionValues.get(key)));
                cb.setOnAction(() -> {
                    optionValues.put(key, cb.isSelected() ? "true" : "false");
                    optionDirty = true;
                });
                row.getChildren().add(cb);
            }
            case 1 -> {
                Ui.SliderBar slider = Ui.sliderBar(0, Math.max(max - min, 1), toSlider(min, max, percent, optionValues.get(key)));
                Label valText = Ui.label(displayValue(min, percent, slider.getValue()), 13, Theme.accentDark);
                valText.setMinWidth(90);
                valText.setAlignment(Pos.CENTER_RIGHT);
                slider.setOnChanged(v -> {
                    optionValues.put(key, fromSlider(min, percent, (int) v));
                    valText.setText(displayValue(min, percent, v));
                    optionDirty = true;
                });
                slider.setPrefWidth(220);
                HBox.setMargin(slider, new Insets(0, 16, 0, 0));
                row.getChildren().addAll(slider, valText);
            }
            default -> {
                ComboBox<String> combo = new ComboBox<>();
                for (String choice : choices) combo.getItems().add(choice);
                int idx = java.util.Arrays.asList(choices).indexOf(optionValues.get(key));
                if (idx >= 0) combo.getSelectionModel().select(idx);
                combo.setPrefWidth(200);
                combo.setPrefHeight(32);
                combo.setOnAction(e -> {
                    String sel = combo.getSelectionModel().getSelectedItem();
                    if (sel == null) return;
                    optionValues.put(key, sel);
                    optionDirty = true;
                });
                row.getChildren().add(combo);
            }
        }
        return row;
    }

    /** options.txt 原始值转滑块整数。 */
    private static int toSlider(int min, int max, boolean percent, String raw) {
        double num;
        try {
            num = Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return 0;
        }
        double scaled = percent ? num * 100 : num;
        return Math.max(0, Math.min(max - min, (int) Math.round(scaled) - min));
    }

    /** 滑块整数转 options.txt 值，percent 用 0.0### 格式。 */
    private static String fromSlider(int min, boolean percent, int sliderValue) {
        int actual = sliderValue + min;
        if (percent) return String.format(java.util.Locale.ROOT, "%.4f", actual / 100.0).replaceAll("0+$", "").replaceAll("\\.$", "");
        return String.valueOf(actual);
    }

    private static String displayValue(int min, boolean percent, double sliderValue) {
        int v = (int) Math.round(sliderValue) + min;
        return percent ? v + "%" : String.valueOf(v);
    }

    /** 写回 options.txt，保持顺序和未知键，UTF-8 无 BOM。 */
    private void saveOptions(Path options) {
        if (!Files.isRegularFile(options)) {
            win.toast("没有可保存的 options.txt。", "", 3);
            return;
        }
        if (!optionDirty) {
            win.toast("没有修改需要保存。", "", 0);
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : optionValues.entrySet()) {
                sb.append(e.getKey()).append(':').append(e.getValue()).append(System.lineSeparator());
            }
            Files.writeString(options, sb.toString(), StandardCharsets.UTF_8);
            optionDirty = false;
            win.toast("游戏选项已保存，下次启动生效。", "", 1);
        } catch (Exception e) {
            win.toast("保存失败：" + e.getMessage(), "", 3);
        }
    }

    /** 选项预设目录，JSON 格式。 */
    private Path optionPresetDir() {
        Path dir = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx", "option-presets");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    /** 存预设，只存本页认识的键。 */
    private void saveOptionPreset() {
        Dialogs.input(win, "存为选项预设", "保存当前这张表里的全部选项，之后可以一键套用到其他实例。", "", name -> {
            if (name == null || name.trim().isEmpty()) return;
            String n = name.trim();
            if (n.matches(".*[\\\\/:*?\"<>|].*") || n.startsWith(".")) {
                win.toast("预设名称不合法", "名称不能包含 \\/:*?\"<>| 或以点开头。", 3);
                return;
            }
            try {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Object[] def : OPT_DEFS) {
                    String k = (String) def[0];
                    String v = optionValues.get(k);
                    if (v == null) continue;
                    if (!first) sb.append(",");
                    first = false;
                    sb.append(Json.quote(k)).append(":").append(Json.quote(v));
                }
                sb.append("}");
                Files.writeString(optionPresetDir().resolve(n + ".json"), sb.toString(), StandardCharsets.UTF_8);
                win.toast("预设已保存：" + n, "", 1);
            } catch (Exception e) {
                win.toast("保存选项预设失败", e.getMessage(), 3);
            }
        });
    }

    /** 套用或删除预设。 */
    private void applyOptionPreset() {
        java.util.List<Path> presets = new ArrayList<>();
        try (java.util.stream.Stream<Path> st = Files.list(optionPresetDir())) {
            st.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(presets::add);
        } catch (Exception ignored) {
        }
        if (presets.isEmpty()) {
            win.toast("还没有任何预设。", "先在选项页调好一套，再点「存为预设」。", 3);
            return;
        }
        VBox card = Dialogs.msgCard("选项预设");
        VBox rows = new VBox(2);
        rows.setPadding(new Insets(15, 0, 6, 0));
        for (Path preset : presets) {
            Ui.ListItem applyRow = new Ui.ListItem(Theme.icon("sliders-horizontal", 15, Theme.text),
                    preset.getFileName().toString().replace(".json", ""), null, 40);
            applyRow.setOnMouseClicked(e -> {
                try {
                    java.util.Map<String, Object> m = Json.obj(Json.parse(Files.readString(preset, StandardCharsets.UTF_8)));
                    int applied = 0;
                    for (Object[] def : OPT_DEFS) {
                        String k = (String) def[0];
                        String v = Json.str(m, k, null);
                        if (v != null && optionValues.containsKey(k)) {
                            optionValues.put(k, v);
                            applied++;
                        }
                    }
                    optionDirty = true;
                    win.closeModal();
                    win.openSetup("options");
                    win.toast("已套用预设「" + preset.getFileName().toString().replace(".json", "") + "」",
                            "共 " + applied + " 项。记得点「保存修改」写入 options.txt。", 1);
                } catch (Exception ex) {
                    win.toast("套用预设失败", ex.getMessage(), 3);
                }
            });
            rows.getChildren().add(applyRow);
            Ui.ListItem deleteRow = new Ui.ListItem(Theme.icon("trash-2", 15, Theme.red),
                    "删除预设 · " + preset.getFileName().toString().replace(".json", ""), null, 40);
            deleteRow.setOnMouseClicked(e -> {
                try {
                    Files.deleteIfExists(preset);
                    win.closeModal();
                    win.toast("预设已删除", "", 1);
                } catch (Exception ex) {
                    win.toast("删除失败", ex.getMessage(), 3);
                }
            });
            rows.getChildren().add(deleteRow);
        }
        card.getChildren().add(rows);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(6, 7, 0, 7));
        actions.getChildren().add(Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private void buildHistory(VBox page) {
        header(page, "历史", "最近的启动记录。");
        Ui.Card card = addCard(page, "启动记录");
        List<String> lines = new ArrayList<>();
        for (String l : data.log) {
            if (l.contains("启动") || l.contains("游戏") || l.contains("安装")) lines.add(l);
        }
        if (lines.isEmpty()) {
            card.add(Ui.label("还没有启动记录。", 12.5, Theme.gray2));
        } else {
            int from = Math.max(0, lines.size() - 30);
            for (int i = lines.size() - 1; i >= from; i--) {
                card.add(new Ui.ListItem(Theme.icon("history", 15, Theme.gray2), lines.get(i), null, 34));
            }
        }
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开日志页", Ui.BTN_NORMAL, () -> win.openSetup("log")),
                Ui.button("清空记录", Ui.BTN_RED, () -> {
                    data.log.removeIf(l -> l.contains("启动") || l.contains("游戏"));
                    win.refreshRight();
                    win.toast("已清空", "启动记录已清除", 1);
                }));
        page.getChildren().add(actions);
    }

    private void buildSettings(VBox page) {
        header(page, "版本设置", "仅作用于当前实例的启动选项。");
        Ui.Card basic = addCard(page, "基础设置");
        ComboBox<String> isolation = Ui.combo(new String[]{"使用全局设置", "隔离所有版本", "不隔离"}, data.getInt("InstanceIsolation", 0));
        isolation.setOnAction(e -> data.set("InstanceIsolation", isolation.getSelectionModel().getSelectedIndex()));
        basic.add(Ui.settingRow("版本隔离", isolation));
        TextField title = Ui.field("默认");
        title.setText(data.get("InstanceWindowTitle", ""));
        title.textProperty().addListener((o, a, b) -> data.set("InstanceWindowTitle", b));
        basic.add(Ui.settingRow("游戏窗口标题", title));
        Ui.SliderBar memory = Ui.sliderBar(1024, 16384, data.getInt("InstanceRam", 4096));
        memory.setPrefWidth(260);
        Label memVal = Ui.label(data.getInt("InstanceRam", 4096) + " MB", 12, Theme.text);
        memory.setOnChanged(b -> {
            int v = (int) (Math.round(b / 512.0) * 512);
            memVal.setText(v + " MB");
            data.set("InstanceRam", v);
        });
        HBox memBox = new HBox(10, memory, memVal);
        memBox.setAlignment(Pos.CENTER_LEFT);
        basic.add(Ui.settingRow("内存分配", memBox));

        Ui.Card java = addCard(page, "Java 与高级");
        ComboBox<String> javaCombo = Ui.combo(new String[]{javaName(), "跟随全局设置"}, 0);
        javaCombo.setPrefWidth(300);
        java.add(Ui.settingRow("Java 运行时", javaCombo));
        TextField args = Ui.field("留空则使用全局设置");
        args.setText(data.get("InstanceJvmArgs", ""));
        args.textProperty().addListener((o, a, b) -> data.set("InstanceJvmArgs", b));
        java.add(Ui.settingRow("JVM 参数", args));
        Ui.Check console = Ui.checkBox("显示控制台窗口", false);
        console.setSelected(data.getBool("InstanceConsole", false));
        console.setOnAction(() -> data.set("InstanceConsole", console.isSelected()));
        java.add(console);

        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("重置本页设置", Ui.BTN_NORMAL, this::resetSettings),
                Ui.button("打开版本文件", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().resolve("versions").toString())));
        page.getChildren().add(actions);
    }

    private String javaName() {
        return data.javaPath.isEmpty() ? "未找到 Java" : Paths.get(data.javaPath).getFileName().toString();
    }

    private void buildMods(VBox page) {
        Path modsDir = instanceRoot().resolve("mods");
        // 识别 .jar、.zip、.litemod，.disabled 为禁用
        List<Path> mods = listModFiles(modsDir);
        long enabledCount = mods.stream().filter(p -> !isDisabledPack(p)).count();
        header(page, "Mod 管理", "共 " + mods.size() + " 个，已启用 " + enabledCount + " 个。");
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开 Mod 文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(modsDir);
                        openFolder(modsDir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }),
                Ui.button("安装 Mod", Ui.BTN_NORMAL, () -> installMods(modsDir)),
                Ui.button("下载 Mod", Ui.BTN_NORMAL, () -> {
                    data.downloadCategory = "Mod";
                    win.openPage("download", true);
                }),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRight()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "已安装 Mod");
        if (mods.isEmpty()) {
            card.add(Ui.label("这个实例的 mods 文件夹是空的。\n点击「安装 Mod」选择文件，或把 .jar 放入 mods 文件夹后点击刷新。", 12.5, Theme.gray2));
        } else {
            // 筛选：0 全部，1 启用，2 禁用
            String[] filter = {"0"};
            HBox filterRow = new HBox(6);
            Runnable[] renderRef = new Runnable[1];
            renderRef[0] = () -> {
                filterRow.getChildren().clear();
                long en = mods.stream().filter(p -> !isDisabledPack(p)).count();
                String[] opts = {"全部", "启用 (" + en + ")", "禁用 (" + (mods.size() - en) + ")"};
                for (int i = 0; i < opts.length; i++) {
                    String label = opts[i];
                    int idx = i;
                    boolean active = filter[0].equals(String.valueOf(idx));
                    filterRow.getChildren().add(Ui.button(label, active ? Ui.BTN_THEME : Ui.BTN_NORMAL, () -> {
                        filter[0] = String.valueOf(idx);
                        renderRef[0].run();
                    }));
                }
            };
            renderRef[0].run();
            card.add(filterRow);
            VBox.setMargin(filterRow, new Insets(0, 0, 6, 0));
            VBox rows = new VBox(0);
            card.add(rows);
            int f = Integer.parseInt(filter[0]);
            for (Path mod : mods) {
                boolean enabled = !isDisabledPack(mod);
                if (f == 1 && !enabled) continue;
                if (f == 2 && enabled) continue;
                rows.getChildren().add(modRow(modsDir, mod));
            }
        }
    }

    /** Mod 行操作按钮。 */
    private Node modRow(Path modsDir, Path mod) {
        String name = mod.getFileName().toString();
        boolean disabled = isDisabledPack(mod);
        String display = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
        Node icon = Theme.icon(disabled ? "link-2-off" : "puzzle", 16, disabled ? Theme.gray4 : Theme.accent);
        Ui.ListItem item = new Ui.ListItem(icon, display, disabled ? "已禁用 · " + sizeOf(mod) : sizeOf(mod), 40);
        StackPane info = Ui.iconButton("info", 26, 15, "详情", () -> modDetail(mod, display, disabled));
        info.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
        StackPane open = Ui.iconButton("folder-open", 26, 15, "打开文件位置", () -> openFolder(mod.getParent().toString()));
        open.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
        StackPane toggle = Ui.iconButton("power", 26, 15, disabled ? "启用" : "禁用", () -> toggleMod(mod, disabled));
        toggle.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
        StackPane update = Ui.iconButton("git-pull-request-arrow", 26, 15, "检查更新", () -> checkModUpdate(mod, display));
        update.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
        StackPane delete = Ui.iconButton("trash-2", 26, 15, "删除", () -> deleteFile(mod, "Mod"));
        delete.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
        item.addAction(info);
        item.addAction(open);
        item.addAction(toggle);
        item.addAction(update);
        item.addAction(delete);
        item.setOnMouseClicked(e -> modDetail(mod, display, disabled)); // 点整行看详情
        return item;
    }

    /** 复制到 mods，文件名去掉 .disabled。 */
    private void installMods(Path modsDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要安装的 Mod");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mod 文件",
                "*.jar", "*.litemod", "*.zip", "*.disabled"));
        List<File> files = chooser.showOpenMultipleDialog(win.stage);
        if (files == null || files.isEmpty()) return;
        try {
            Files.createDirectories(modsDir);
            for (File f : files) {
                String enabledName = f.getName();
                if (enabledName.toLowerCase().endsWith(".disabled")) {
                    enabledName = enabledName.substring(0, enabledName.length() - ".disabled".length());
                }
                if (!enabledName.contains(".")) enabledName += ".jar";
                Path target = modsDir.resolve(enabledName);
                Files.copy(f.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            win.toast("已安装 " + files.size() + " 个 Mod", "", 1);
            win.refreshRight();
        } catch (Exception e) {
            win.toast("安装失败", e.getMessage(), 3);
        }
    }

    /** 重命名 .disabled，重名时提示冲突。 */
    private void toggleMod(Path mod, boolean disabled) {
        try {
            String name = mod.getFileName().toString();
            Path target = disabled ? mod.resolveSibling(name.substring(0, name.length() - ".disabled".length()))
                    : mod.resolveSibling(name + ".disabled");
            if (Files.exists(target)) {
                Dialogs.confirm(win, "存在文件冲突", "目前同时存在启用和禁用的两个 Mod 文件：\n - "
                        + target.getFileName() + "\n - " + name
                        + "\n\n注意，这两个文件的内容并不相同。请手动删除或重命名其中一个后再试。", () -> {
                });
                return;
            }
            Files.move(mod, target);
            win.toast(disabled ? "已启用：" : "已禁用：", target.getFileName().toString(), 1);
            win.refreshRight();
        } catch (Exception e) {
            win.toast("操作失败", e.getMessage(), 3);
        }
    }

    /** 读 mcmod.info 或 fabric.mod.json 展示详情。 */
    private void modDetail(Path mod, String display, boolean disabled) {
        String[] meta = null;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mod.toFile())) {
            java.util.zip.ZipEntry mcmod = zip.getEntry("mcmod.info");
            if (mcmod != null) {
                var root = Json.obj(Json.parse(new String(zip.getInputStream(mcmod).readAllBytes(), StandardCharsets.UTF_8)));
                var first = Json.obj(Json.arr(root.get("modList")).isEmpty() ? null : Json.arr(root.get("modList")).get(0));
                meta = new String[]{Json.str(first, "name", display), Json.str(first, "version", ""),
                        Json.str(first, "description", "")};
            }
            if (meta == null) {
                java.util.zip.ZipEntry fabric = zip.getEntry("fabric.mod.json");
                if (fabric != null) {
                    var root = Json.obj(Json.parse(new String(zip.getInputStream(fabric).readAllBytes(), StandardCharsets.UTF_8)));
                    meta = new String[]{Json.str(root, "name", Json.str(root, "id", display)),
                            Json.str(root, "version", ""), Json.str(root, "description", "")};
                }
            }
        } catch (Exception ignored) {
        }
        String time = "";
        try {
            time = Files.getLastModifiedTime(mod).toInstant().atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        } catch (Exception ignored) {
        }
        VBox card = new VBox(10);
        card.setPadding(new Insets(18, 20, 16, 20));
        card.setPrefWidth(430);
        card.setBackground(Ui.fill(Theme.dialogBg, 12));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        card.getChildren().add(Ui.bold(meta != null && !meta[0].isEmpty() ? meta[0] : display, 16, Theme.text));
        VBox rows = new VBox(5);
        if (meta != null && !meta[1].isEmpty()) rows.getChildren().add(Ui.label("版本：" + meta[1], 12.5, Theme.gray1));
        if (meta != null && !meta[2].isEmpty()) {
            Label desc = Ui.label(meta[2], 12, Theme.gray2);
            desc.setWrapText(true);
            rows.getChildren().add(desc);
        }
        rows.getChildren().add(Ui.label("文件：" + mod.getFileName(), 12, Theme.gray2));
        rows.getChildren().add(Ui.label("大小：" + sizeOf(mod) + " · 修改于 " + time, 12, Theme.gray2));
        rows.getChildren().add(Ui.label("状态：" + (disabled ? "已禁用" : "已启用"), 12, Theme.gray2));
        card.getChildren().addAll(rows, Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        win.showModal(card);
    }

    /** 确认后移入回收站。 */
    private void deleteFile(Path file, String kind) {
        Dialogs.confirm(win, "删除" + kind, "确定要删除 " + file.getFileName() + " 吗？文件将移入回收站。", () -> {
            // 回收站要等 PowerShell 进程，不能放 FX 线程
            Thread worker = new Thread(() -> {
                boolean ok = Shell.deleteToRecycleBin(file.toString());
                javafx.application.Platform.runLater(() -> {
                    if (ok) {
                        win.toast("已删除", file.getFileName().toString(), 1);
                        win.refreshRight();
                    } else {
                        Errors.error("删除" + kind + "失败",
                                new Exception("系统回收站操作失败：" + file));
                    }
                });
            }, "TCL-Recycle");
            worker.setDaemon(true);
            worker.start();
        }, true);
    }

    /** 按 SHA-1 查 Modrinth，再取项目最新版本比较。 */
    private void checkModUpdate(Path mod, String display) {
        win.toast("正在检查更新", display, 0);
        new Thread(() -> {
            try {
                String localHash = GameLauncher.sha1Of(mod);
                String[] cur = Modrinth.checkUpdateBySha1(localHash);
                if (cur == null) {
                    javafx.application.Platform.runLater(() ->
                            win.toast("未发现更新", display + " 未被 Modrinth 收录（或网络失败）。", 0));
                    return;
                }
                List<Object> versions = Modrinth.projectVersions(cur[0]);
                ResourceVersion latest = versions.isEmpty() ? null
                        : ResourceVersion.fromModrinthJson(Json.obj(versions.get(0)), "mod");
                if (latest == null || latest.downloadUrls.isEmpty()) {
                    javafx.application.Platform.runLater(() -> win.toast("未发现更新", display, 0));
                    return;
                }
                if (latest.hash != null && latest.hash.equalsIgnoreCase(localHash)) {
                    javafx.application.Platform.runLater(() ->
                            win.toast("已是最新版本", display + " · " + cur[1], 1));
                    return;
                }
                javafx.application.Platform.runLater(() -> Dialogs.confirm(win, "发现更新",
                        display + " 有新版本：" + latest.title(false)
                                + "\n新文件：" + latest.fileName
                                + "\n\n新版本 Mod 可能不兼容旧存档或者其他 Mod，这可能导致游戏崩溃，甚至永久损坏存档。"
                                + "\n如果你在游玩整合包，请千万不要自行更新 Mod。更新前请先备份存档。"
                                + "\n\n是否更新？旧文件将移入回收站。",
                        () -> applyModUpdate(mod, latest, display), true));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        win.toast("检查更新失败", e.getMessage() == null ? "网络错误" : e.getMessage(), 3));
            }
        }, "TCL-ModUpdate-Check").start();
    }

    /** 下载新文件，旧文件移入回收站。 */
    private void applyModUpdate(Path oldMod, ResourceVersion latest, String display) {
        Path modsDir = oldMod.getParent();
        String fileName = latest.fileName.replace("~", "-");
        Path target = modsDir.resolve(fileName).normalize();
        if (!target.startsWith(modsDir.toAbsolutePath().normalize())) {
            Errors.error("Mod 更新失败", new Exception("新文件名包含非法路径：" + fileName));
            return;
        }
        Tasks.Task task = new Tasks.Task("Mod 更新 · " + display);
        task.subs.add(new Tasks.SubTask("下载新版本"));
        Tasks.INSTANCE.add(task);
        final Path[] backup = new Path[1];
        Thread worker = new Thread(() -> {
            try {
                task.subs.get(0).state = Tasks.State.RUNNING;
                task.stage = "下载 " + fileName;
                // 同名时先挪开旧文件，否则回收站会连新文件一起删
                if (Files.exists(oldMod) && target.equals(oldMod.toAbsolutePath().normalize())) {
                    backup[0] = modsDir.resolve(oldMod.getFileName() + ".tcl-old");
                    Files.move(oldMod, backup[0], java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                Modrinth.downloadUrl(task, latest.downloadUrls.get(0), target, latest.hash);
                task.subs.get(0).state = Tasks.State.DONE;
                Path recycle = backup[0] != null ? backup[0] : oldMod;
                if (Files.exists(recycle)) Shell.deleteToRecycleBin(recycle.toString());
                backup[0] = null;
                task.state = Tasks.State.DONE;
                task.progress = 1;
                task.stage = "更新完成";
                data.log("Mod 已更新：" + display + " → " + fileName);
                javafx.application.Platform.runLater(() -> {
                    win.toast("Mod 已更新", display + " → " + latest.title(false), 1);
                    win.refreshRight();
                });
            } catch (Exception e) {
                // 失败时还原备份，别让 Mod 消失
                if (backup[0] != null && !Files.exists(oldMod)) {
                    try {
                        Files.move(backup[0], oldMod, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                }
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "更新失败" : e.getMessage();
                Errors.error("Mod 更新失败", e);
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-ModUpdate-" + display);
        worker.setDaemon(true);
        worker.start();
    }

    private void buildPacks(VBox page, String folder, String label) {
        Path root = instanceRoot();
        Path dir = root.resolve(folder);
        boolean isShader = folder.equals("shaderpacks");
        // 文件和文件夹都算，.disabled 表示禁用
        List<Path> packs = listPacks(dir);
        long enabledCount = packs.stream().filter(p -> !isDisabledPack(p)).count();
        header(page, label, "共 " + packs.size() + " 个，已启用 " + enabledCount + " 个。");
        if (isShader) {
            String current = currentShader(root);
            if (!current.isEmpty()) {
                page.getChildren().add(Ui.hint("当前使用光影包：" + current, 0));
                VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
            }
            if (!hasShaderLoader()) {
                page.getChildren().add(Ui.hint("mods 文件夹中没有检测到 Iris / Oculus / OptiFine，光影包不会生效。", 1));
                VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
            }
        }
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(dir);
                        openFolder(dir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRight()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, label + "列表");
        if (packs.isEmpty()) {
            card.add(Ui.label("这个文件夹是空的。把 " + label + " 的 .zip 或文件夹放入 " + folder + " 后点击刷新。", 12.5, Theme.gray2));
        } else {
            for (Path pack : packs) {
                String name = pack.getFileName().toString();
                boolean disabled = isDisabledPack(pack);
                boolean isFolderPack = Files.isDirectory(pack);
                String display = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
                String info = disabled ? "已禁用" : isFolderPack ? "文件夹包" : sizeOf(pack);
                Ui.ListItem item = new Ui.ListItem(Theme.icon(disabled ? "link-2-off" : isFolderPack ? "folder" : "image", 16,
                        disabled ? Theme.gray4 : Theme.accent), display, info, 40);
                StackPane toggle = Ui.iconButton("power", 26, 15, disabled ? "启用" : "禁用",
                        () -> togglePack(pack, disabled, folder));
                toggle.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                item.addAction(toggle);
                if (isShader && !disabled) {
                    StackPane use = Ui.iconButton("circle-check", 26, 15, "设为当前使用", () -> {
                        writeOption(root, "shaderPack", display);
                        win.toast("已设为当前光影包", display, 1);
                        win.refreshRight();
                    });
                    use.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                    item.addAction(use);
                }
                StackPane open = Ui.iconButton("folder-open", 26, 15, "打开文件位置", () -> openFolder(dir.toString()));
                open.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                item.addAction(open);
                if (!isFolderPack) {
                    StackPane delete = Ui.iconButton("trash-2", 26, 15, "删除", () -> deleteFile(pack, label));
                    delete.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                    item.addAction(delete);
                }
                card.add(item);
            }
        }
    }

    /** 文件和文件夹都算，.disabled 为禁用。 */
    private List<Path> listPacks(Path dir) {
        List<Path> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.forEach(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        if (Files.isDirectory(p) || n.endsWith(".zip") || n.endsWith(".zip.disabled")) out.add(p);
                    });
                }
            }
        } catch (Exception ignored) {
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        return out;
    }

    /** .jar、.zip、.litemod，.disabled 为禁用。 */
    private List<Path> listModFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".jar") || n.endsWith(".zip") || n.endsWith(".litemod");
                    }).forEach(out::add);
                }
            }
        } catch (Exception ignored) {
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        return out;
    }

    private boolean isDisabledPack(Path p) {
        return p.getFileName().toString().toLowerCase().endsWith(".disabled");
    }

    /** 当前使用的光影包，去掉引号，off 视为空。 */
    private String currentShader(Path root) {
        String raw = readOption(root, "shaderPack").trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        return raw.equalsIgnoreCase("off") ? "" : raw;
    }

    /** mods 里有没有 Iris / Oculus / OptiFine。 */
    private boolean hasShaderLoader() {
        Path mods = instanceRoot().resolve("mods");
        try {
            if (Files.isDirectory(mods)) {
                try (var stream = Files.list(mods)) {
                    for (Path p : stream.toList()) {
                        String n = p.getFileName().toString().toLowerCase();
                        if (n.endsWith(".jar") && (n.contains("iris") || n.contains("oculus") || n.contains("optifine"))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 读 options.txt 单个键。 */
    private String readOption(Path root, String key) {
        Path options = root.resolve("options.txt");
        if (!Files.isRegularFile(options)) return "";
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                int idx = line.indexOf(':');
                if (idx > 0 && line.substring(0, idx).equals(key)) return line.substring(idx + 1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 写单个键，其他行不动。 */
    private void writeOption(Path root, String key, String value) {
        Path options = root.resolve("options.txt");
        try {
            List<String> lines = Files.exists(options)
                    ? Files.readAllLines(options, StandardCharsets.UTF_8) : new ArrayList<>();
            boolean replaced = false;
            List<String> out = new ArrayList<>();
            for (String line : lines) {
                int idx = line.indexOf(':');
                if (!replaced && idx > 0 && line.substring(0, idx).equals(key)) {
                    out.add(key + ":" + value);
                    replaced = true;
                } else {
                    out.add(line);
                }
            }
            if (!replaced) out.add(key + ":" + value);
            Files.write(options, out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            win.toast("写入 options.txt 失败", e.getMessage(), 3);
        }
    }

    /** 把启用的资源包同步到 options.txt，兼容 1.12 旧格式。 */
    private void syncResourcePacks(Path root) {
        try {
            List<Path> packs = listPacks(root.resolve("resourcepacks"));
            String raw = readOption(root, "resourcePacks").trim();
            boolean legacy = raw.length() > 2 && !raw.contains("\"");
            List<String> items = new ArrayList<>();
            for (Path p : packs) {
                if (isDisabledPack(p)) continue;
                String n = p.getFileName().toString();
                boolean isFolder = Files.isDirectory(p);
                if (!isFolder && n.toLowerCase().endsWith(".zip")) n = n.substring(0, n.length() - 4);
                if (legacy) {
                    items.add(isFolder ? n : n + ".zip");
                } else {
                    items.add("file/" + (isFolder ? n : n + ".zip"));
                }
            }
            List<String> parts = new ArrayList<>();
            for (String one : items) {
                parts.add(legacy ? one : "\"" + one.replace("\"", "").replace(",", "") + "\"");
            }
            writeOption(root, "resourcePacks", "[" + String.join(",", parts) + "]");
        } catch (Exception e) {
            data.log("同步资源包到 options.txt 失败：" + e.getMessage());
        }
    }

    private void renameSave(Path save) {
        Dialogs.input(win, "重命名存档", "", save.getFileName().toString(), name -> {
            if (name == null || name.equals(save.getFileName().toString())) return;
            Path target = save.resolveSibling(name);
            if (Files.exists(target)) {
                win.toast("已存在同名存档", name, 2);
                return;
            }
            try {
                Files.move(save, target);
                win.toast("已重命名", name, 1);
                win.refreshRight();
            } catch (Exception e) {
                win.toast("重命名失败", e.getMessage(), 3);
            }
        });
    }

    /** 导出存档为 zip。 */
    private void exportSave(Path save) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出存档");
        chooser.setInitialFileName(save.getFileName() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip 压缩包", "*.zip"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        Tasks.Task task = new Tasks.Task("导出存档 · " + save.getFileName());
        task.subs.add(new Tasks.SubTask("写入压缩包"));
        Tasks.INSTANCE.add(task);
        win.toast("开始导出", save.getFileName().toString(), 0);
        new Thread(() -> {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                List<Path> files = new ArrayList<>();
                try (var stream = Files.walk(save)) {
                    stream.filter(Files::isRegularFile).forEach(files::add);
                }
                task.remainingFiles = files.size();
                int done = 0;
                for (Path p : files) {
                    if (task.canceled) throw new Exception("已取消");
                    zip.putNextEntry(new ZipEntry(save.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, zip);
                    zip.closeEntry();
                    done++;
                    task.progress = done / (double) files.size();
                    task.stage = "正在压缩 " + done + " / " + files.size();
                    task.remainingFiles = files.size() - done;
                }
                task.subs.get(0).state = Tasks.State.DONE;
                task.state = Tasks.State.DONE;
                task.progress = 1;
                task.stage = "导出完成";
                javafx.application.Platform.runLater(() -> win.toast("导出完成", file.getName(), 1));
            } catch (Exception e) {
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "导出失败" : e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("导出失败", task.stage, 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-SaveExport").start();
    }

    /** 确认后删除存档。 */
    private void deleteSave(Path save) {
        Dialogs.confirm(win, "删除存档", "确定要删除存档 " + save.getFileName() + " 吗？\n该世界将永久丢失，无法恢复。", () -> {
            try (var stream = Files.walk(save)) {
                for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
                win.toast("已删除存档", save.getFileName().toString(), 1);
                win.refreshRight();
            } catch (Exception e) {
                win.toast("删除失败", e.getMessage(), 3);
            }
        }, true);
    }

    private List<Path> listFiles(Path dir, String... suffixes) {
        List<Path> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        for (String s : suffixes) if (n.endsWith(s)) return true;
                        return false;
                    }).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).forEach(out::add);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 重命名 .disabled，资源包再同步 options.txt。 */
    private void togglePack(Path file, boolean disabled, String folder) {
        try {
            String name = file.getFileName().toString();
            Path target = disabled ? file.resolveSibling(name.substring(0, name.length() - ".disabled".length()))
                    : file.resolveSibling(name + ".disabled");
            if (Files.exists(target)) {
                win.toast("目标路径已存在文件", target.getFileName().toString(), 2);
                return;
            }
            Files.move(file, target);
            if ("resourcepacks".equals(folder)) syncResourcePacks(instanceRoot());
            win.toast(disabled ? "已启用" : "已禁用", target.getFileName().toString(), 1);
            win.refreshRight();
        } catch (Exception e) {
            win.toast("操作失败", e.getMessage(), 3);
        }
    }

    private String sizeOf(Path p) {
        try {
            long size = Files.size(p);
            if (size > 1024 * 1024) return String.format("%.1f MB", size / 1024.0 / 1024);
            return size / 1024 + " KB";
        } catch (Exception e) {
            return "";
        }
    }

    private void buildSaves(VBox page) {
        Path savesDir = instanceRoot().resolve("saves");
        List<Path> saves = new ArrayList<>();
        try {
            if (Files.isDirectory(savesDir)) {
                try (var s = Files.list(savesDir)) {
                    s.filter(Files::isDirectory).sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                            .forEach(saves::add);
                }
            }
        } catch (Exception ignored) {
        }
        header(page, "存档", "共 " + saves.size() + " 个世界。");
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开存档文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(savesDir);
                        openFolder(savesDir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRight()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "世界列表");
        if (saves.isEmpty()) {
            card.add(Ui.label("还没有存档。在游戏中创建世界后，这里会显示你的存档。", 12.5, Theme.gray2));
        } else {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            for (Path save : saves) {
                String time = "";
                try {
                    time = Files.getLastModifiedTime(save).toInstant().atZone(ZoneId.systemDefault()).format(fmt);
                } catch (Exception ignored) {
                }
                String fTime = time;
                Ui.ListItem item = new Ui.ListItem(Theme.icon("map", 16, Theme.text), save.getFileName().toString(), "最后修改 " + time, 40);
                // 打开、重命名、导出、删除，点条目打开文件夹
                StackPane open = Ui.iconButton("folder-open", 26, 15, "打开存档文件夹", () -> openFolder(save.toString()));
                open.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                StackPane rename = Ui.iconButton("pencil", 26, 15, "重命名存档", () -> renameSave(save));
                rename.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                StackPane export = Ui.iconButton("upload", 26, 15, "导出为 .zip", () -> exportSave(save));
                export.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                StackPane delete = Ui.iconButton("trash-2", 26, 15, "删除存档", () -> deleteSave(save));
                delete.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
                item.addAction(open);
                item.addAction(rename);
                item.addAction(export);
                item.addAction(delete);
                item.setOnMouseClicked(e -> openFolder(save.toString()));
                item.getProperties().put("time", fTime);
                card.add(item);
            }
        }
    }

    private void buildScreenshots(VBox page) {
        Path dir = instanceRoot().resolve("screenshots");
        List<Path> shots = listFiles(dir, ".png", ".jpg", ".jpeg");
        header(page, "截图", "共 " + shots.size() + " 张截图。");
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开截图文件夹", Ui.BTN_NORMAL, () -> {
                    try {
                        Files.createDirectories(dir);
                        openFolder(dir.toString());
                    } catch (Exception e) {
                        win.toast("无法打开", e.getMessage(), 3);
                    }
                }),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRight()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        if (shots.isEmpty()) {
            Ui.Card card = addCard(page, "截图列表");
            card.add(Ui.label("还没有截图。在游戏中按 F2 截图后会显示在这里。", 12.5, Theme.gray2));
            return;
        }
        FlowPane flow = new FlowPane(12, 12);
        flow.setPadding(new Insets(4));
        for (Path shot : shots) {
            VBox box = new VBox(5);
            box.setPrefWidth(180);
            try {
                ImageView iv = new ImageView(new javafx.scene.image.Image(shot.toUri().toString(), 170, 96, false, true, true));
                iv.setFitWidth(170);
                iv.setFitHeight(96);
                Rectangle clip = new Rectangle(170, 96);
                clip.setArcWidth(8);
                clip.setArcHeight(8);
                iv.setClip(clip);
                box.getChildren().add(iv);
            } catch (Exception ignored) {
            }
            Label name = Ui.label(shot.getFileName().toString(), 11, Theme.gray2);
            name.setMaxWidth(170);
            box.getChildren().add(name);
            box.setCursor(javafx.scene.Cursor.HAND);
            StackPane rowBtns = new StackPane();
            HBox btns = new HBox(6);
            btns.setAlignment(Pos.CENTER);
            StackPane open = Ui.iconButton("folder-open", 24, 14, "打开图片", () -> openFolder(shot.toString()));
            open.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
            StackPane del = Ui.iconButton("trash-2", 24, 14, "删除截图", () -> deleteFile(shot, "截图"));
            del.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.scene.input.MouseEvent::consume);
            btns.getChildren().addAll(open, del);
            rowBtns.getChildren().add(btns);
            box.getChildren().add(rowBtns);
            box.setOnMouseClicked(e -> openFolder(shot.toString()));
            box.setPadding(new Insets(6));
            box.setBackground(Ui.fill(Theme.cardBg, 8));
            flow.getChildren().add(box);
        }
        Ui.Card card = addCard(page, "截图列表");
        card.add(flow);
    }

    private final java.util.List<Servers.Entry> serverList = new ArrayList<>();
    private final java.util.List<Label> serverStatusLabels = new ArrayList<>();
    private boolean pingLock = false;

    private void buildServer(VBox page) {
        header(page, "服务器", "在游戏中添加的多人服务器会显示在这里。");
        try {
            serverList.clear();
            serverList.addAll(Servers.listRead(instanceRoot()));
        } catch (Exception e) {
            win.toast("读取服务器列表失败", e.getMessage(), 3);
        }
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("添加服务器", Ui.BTN_HIGHLIGHT, this::addServerDialog),
                Ui.button("刷新状态", Ui.BTN_NORMAL, this::pingAll),
                Ui.button("打开游戏目录", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().toString())));
        page.getChildren().add(actions);
        VBox.setMargin(actions, new Insets(0, 0, 12, 0));

        if (serverList.isEmpty()) {
            Ui.Card card = addCard(page, "服务器列表");
            card.add(Ui.label("暂无服务器。点击「添加服务器」，或启动游戏后在「多人游戏 → 添加服务器」中保存。", 12.5, Theme.gray2));
            return;
        }
        serverStatusLabels.clear();
        for (Servers.Entry entry : serverList) {
            Ui.Card card = serverCard(entry);
            page.getChildren().add(card);
        }
        pingAll();
    }

    /** 服务器卡片。 */
    private Ui.Card serverCard(Servers.Entry entry) {
        Ui.Card card = Ui.card(null);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox layout = new HBox(10);
        layout.setPadding(new Insets(15, 20, 15, 20));
        layout.setAlignment(Pos.CENTER_LEFT);
        StackPane iconHost = new StackPane();
        iconHost.setMinSize(48, 48);
        iconHost.setPrefSize(48, 48);
        iconHost.setMaxSize(48, 48);
        javafx.scene.image.Image icon = decodeIcon(entry.icon);
        if (icon != null) {
            ImageView iv = new ImageView(icon);
            iv.setFitWidth(48);
            iv.setFitHeight(48);
            iv.setPreserveRatio(true);
            iconHost.getChildren().add(iv);
        } else {
            iconHost.getChildren().add(Theme.icon("server", 32, Theme.accentDark));
        }
        VBox info = new VBox(3);
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = Ui.semi(entry.name == null || entry.name.isEmpty() ? "未命名服务器" : entry.name, 16, Theme.text);
        name.setMaxWidth(Double.MAX_VALUE);
        Label ip = Ui.label(entry.ip, 12.5, Theme.gray2);
        ip.setOpacity(0.7);
        ip.setMaxWidth(Double.MAX_VALUE);
        info.getChildren().addAll(name, ip);
        Label status = Ui.label("查询中……", 12.5, Theme.gray2);
        status.setOpacity(0.75);
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getChildren().addAll(
                Ui.iconTextButton("activity", "查询", false, () -> pingSingle(entry, status)),
                Ui.iconTextButton("play", "进入", false, () -> launchToServer(entry)),
                Ui.iconButton("more-vertical", 26, 15, "更多操作", () -> serverMoreMenu(entry, () -> win.openSetup("server"))));
        layout.getChildren().addAll(iconHost, info, buttons);
        card.add(layout);
        HBox statusRow = new HBox(status);
        statusRow.setPadding(new Insets(0, 20, 14, 78));
        status.getProperties().put("entry", entry);
        serverStatusLabels.add(status);
        card.add(statusRow);
        return card;
    }

    /** 服务器更多操作。 */
    private void serverMoreMenu(Servers.Entry entry, Runnable refresh) {
        VBox card = Dialogs.msgCard("服务器操作");
        VBox rows = new VBox(2);
        rows.setPadding(new Insets(15, 0, 6, 0));
        String[][] ops = {
                {"copy", "复制服务器地址"},
                {"arrow-up", "上移"},
                {"arrow-down", "下移"},
                {"pencil", "编辑"},
                {"trash-2", "删除"},
        };
        for (String[] op : ops) {
            Ui.ListItem row = new Ui.ListItem(Theme.icon(op[0], 15, op[0].equals("trash-2") ? Theme.red : Theme.text), op[1], null, 40);
            row.setOnMouseClicked(e -> {
                win.closeModal();
                switch (op[1]) {
                    case "复制服务器地址" -> {
                        javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
                        c.putString(entry.ip == null ? "" : entry.ip);
                        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
                        win.toast("已复制地址：" + entry.ip, "", 1);
                    }
                    case "上移" -> moveServer(entry, -1, refresh);
                    case "下移" -> moveServer(entry, 1, refresh);
                    case "编辑" -> editServerDialog(entry, refresh);
                    case "删除" -> Dialogs.confirm(win, "删除服务器", "确定要删除服务器“" + entry.name + "”吗？", () -> {
                        serverList.remove(entry);
                        saveServers();
                        refresh.run();
                        win.toast("服务器已删除！", "", 1);
                    }, true);
                }
            });
            rows.getChildren().add(row);
        }
        card.getChildren().add(rows);
        win.showModal(card);
    }

    private void saveServers() {
        try {
            Servers.listWrite(instanceRoot(), serverList);
        } catch (Exception e) {
            win.toast("保存服务器列表失败", e.getMessage(), 3);
        }
    }

    private void addServerDialog() {
        Dialogs.input(win, "添加服务器", "请输入服务器名称：", "新的服务器", name -> {
            if (name == null || name.trim().isEmpty()) return;
            Dialogs.input(win, "添加服务器", "请输入服务器地址（IP 或 域名，可带端口）：", "", ip -> {
                if (ip == null || ip.trim().isEmpty()) return;
                Servers.Entry e = new Servers.Entry();
                e.name = name.trim();
                e.ip = ip.trim();
                serverList.add(e);
                saveServers();
                win.openSetup("server");
                win.toast("服务器已添加！", "", 1);
            });
        });
    }

    private void editServerDialog(Servers.Entry entry, Runnable refresh) {
        Dialogs.input(win, "编辑服务器", "请输入服务器名称：", entry.name, name -> {
            if (name == null || name.trim().isEmpty()) return;
            Dialogs.input(win, "编辑服务器", "请输入服务器地址（IP 或 域名，可带端口）：", entry.ip, ip -> {
                if (ip == null || ip.trim().isEmpty()) return;
                entry.name = name.trim();
                entry.ip = ip.trim();
                saveServers();
                refresh.run();
                win.toast("服务器信息已更新！", "", 1);
            });
        });
    }

    private void moveServer(Servers.Entry entry, int delta, Runnable refresh) {
        int index = serverList.indexOf(entry);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= serverList.size()) return;
        serverList.remove(index);
        serverList.add(target, entry);
        saveServers();
        refresh.run();
    }

    /** 启动并直连（--server [port]）。 */
    private void launchToServer(Servers.Entry entry) {
        if (entry.ip == null || entry.ip.trim().isEmpty()) {
            win.toast("这个服务器没有填写地址。", "", 3);
            return;
        }
        win.launchGame(entry.ip.trim());
    }

    private void pingSingle(Servers.Entry entry, Label status) {
        status.setText("查询中……");
        new Thread(() -> {
            Servers.PingResult r = Servers.ping(entry.ip, 3000);
            javafx.application.Platform.runLater(() -> applyPing(status, r));
        }, "TCL-Ping-One").start();
    }

    /** 并发查询服务器状态。 */
    private void pingAll() {
        if (pingLock) return;
        pingLock = true;
        List<Label> statuses = new ArrayList<>(serverStatusLabels);
        for (Label l : statuses) l.setText("查询中……");
        if (statuses.isEmpty()) {
            pingLock = false;
            return;
        }
        Thread t = new Thread(() -> {
            try {
                statuses.parallelStream().limit(64).forEach(label -> {
                    Servers.Entry entry = (Servers.Entry) label.getProperties().get("entry");
                    try {
                        Servers.PingResult r = Servers.ping(entry.ip, 3000);
                        javafx.application.Platform.runLater(() -> applyPing(label, r));
                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> {
                            label.setText("离线（查询异常）");
                            label.setTextFill(Theme.gray3);
                            label.setOpacity(0.8);
                        });
                    }
                });
            } finally {
                javafx.application.Platform.runLater(() -> pingLock = false);
            }
        }, "TCL-Ping-All");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private void applyPing(Label status, Servers.PingResult r) {
        if (r.online) {
            status.setText("在线 · " + r.players + "/" + r.maxPlayers + " · " + r.latency + "ms · "
                    + r.version + "  " + (r.motd == null ? "" : r.motd));
            status.setTextFill(Theme.text);
            status.setOpacity(1);
        } else {
            status.setText("离线" + (r.errorMessage == null || r.errorMessage.isEmpty() ? "" : "（" + r.errorMessage + "）"));
            status.setTextFill(Theme.gray3);
            status.setOpacity(0.8);
        }
    }

    /** base64 服务器图标解码。 */
    private javafx.scene.image.Image decodeIcon(String icon) {
        if (icon == null || icon.isEmpty()) return null;
        try {
            String base64 = icon;
            int comma = base64.indexOf(',');
            if (comma >= 0) base64 = base64.substring(comma + 1);
            byte[] buffer = java.util.Base64.getDecoder().decode(base64);
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                    new java.io.ByteArrayInputStream(buffer));
            if (img.isError()) return null;
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private void buildSavesDatapack(VBox page) {
        Path dir = instanceRoot().resolve("saves");
        java.util.List<Path> packs = new java.util.ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).forEach(world -> {
                    Path dp = world.resolve("datapacks");
                    if (Files.isDirectory(dp)) {
                        try (var s2 = Files.list(dp)) {
                            s2.filter(Files::isDirectory).forEach(packs::add);
                        } catch (Exception ignored) {
                        }
                    }
                });
            } catch (Exception ignored) {
            }
        }
        header(page, "存档数据包", "共 " + packs.size() + " 个数据包。");
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开存档文件夹", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().resolve("saves").toString())),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRight()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "数据包列表");
        if (packs.isEmpty()) {
            card.add(Ui.label("还没有数据包。把数据包放到存档的 datapacks 文件夹中即可。", 12.5, Theme.gray2));
        } else {
            for (Path p : packs) {
                Ui.ListItem item = new Ui.ListItem(Theme.icon("file-box", 16, Theme.text), p.getFileName().toString(), null, 40);
                card.add(item);
            }
        }
    }

    private void buildCompManifest(VBox page) {
        header(page, "整合包清单", "显示实例目录中整合包的组件清单，绿色表示文件已存在。");
        // 优先 原始整合包，其次任意 .mrpack
        Path packPath = null;
        for (String candidate : new String[]{"原始整合包.mrpack", "原始整合包.zip"}) {
            Path p = instanceRoot().resolve(candidate);
            if (Files.isRegularFile(p)) {
                packPath = p;
                break;
            }
        }
        if (packPath == null) {
            try (var stream = Files.list(instanceRoot())) {
                packPath = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".mrpack"))
                        .findFirst().orElse(null);
            } catch (Exception ignored) {
            }
        }
        if (packPath == null || !Files.isRegularFile(packPath)) {
            Ui.Card card = addCard(page, "清单");
            card.add(Ui.label("实例目录中没有整合包文件（.mrpack）。如果该实例来自整合包导入，把原始整合包文件放进实例目录即可在这里查看清单。", 12.5, Theme.gray2));
            card.add(Ui.button("打开实例文件夹", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().toString())));
            return;
        }
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(packPath))) {
            java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                // 只读清单，overrides 跳过
                if (e.getName().equals("modrinth.index.json") || e.getName().equals("manifest.json")) {
                    entries.put(e.getName(), zip.readAllBytes());
                }
                if (entries.size() == 2) break;
            }
            if (packPath.getFileName().toString().toLowerCase().endsWith(".mrpack")) {
                buildCompManifestModrinth(page, packPath, entries.get("modrinth.index.json"));
            } else {
                buildCompManifestCurseForge(page, packPath, entries.get("manifest.json"));
            }
        } catch (Exception ex) {
            Ui.Card card = addCard(page, "读取失败");
            card.add(Ui.label("读取失败：" + ex.getMessage(), 13, Theme.gray2));
        }
    }

    /** Modrinth 清单视图。 */
    private void buildCompManifestModrinth(VBox page, Path packPath, byte[] indexBytes) {
        if (indexBytes == null) {
            Ui.Card card = addCard(page, "提示");
            card.add(Ui.label("整合包内缺少 modrinth.index.json。", 13, Theme.gray2));
            return;
        }
        java.util.Map<String, Object> json = Json.obj(Json.parse(new String(indexBytes, StandardCharsets.UTF_8)));
        String name = Json.str(json, "name", packPath.getFileName().toString());
        String versionId = Json.str(json, "versionId", "");
        java.util.Map<String, Object> deps = Json.obj(json.get("dependencies"));
        String mc = Json.str(deps, "minecraft", "");
        StringBuilder loader = new StringBuilder();
        for (String k : deps.keySet()) {
            if (!k.equals("minecraft")) loader.append(" ").append(k).append(" ").append(Json.str(deps, k, ""));
        }
        Ui.Card head = addCard(page, name);
        head.add(Ui.label("Modrinth 整合包 · 版本 " + versionId + " · Minecraft " + mc + loader, 13, Theme.gray2));
        List<Object> files = Json.arr(json.get("files"));
        Ui.Card list = addCard(page, "共 " + files.size() + " 个文件，颜色深表示已存在：");
        for (Object o : files) {
            java.util.Map<String, Object> item = Json.obj(o);
            String relativePath = Json.str(item, "path", "");
            if (relativePath.isEmpty()) continue;
            long size = (long) Json.num(item, "fileSize", 0);
            List<Object> downloads = Json.arr(item.get("downloads"));
            String url = downloads.isEmpty() ? null : String.valueOf(downloads.get(0));
            compManifestRow(list, relativePath, size, url);
        }
    }

    /** CurseForge 清单视图。 */
    private void buildCompManifestCurseForge(VBox page, Path packPath, byte[] manifestBytes) {
        if (manifestBytes == null) {
            Ui.Card card = addCard(page, "提示");
            card.add(Ui.label("整合包内缺少 manifest.json。", 13, Theme.gray2));
            return;
        }
        java.util.Map<String, Object> json = Json.obj(Json.parse(new String(manifestBytes, StandardCharsets.UTF_8)));
        String name = Json.str(json, "name", packPath.getFileName().toString());
        java.util.Map<String, Object> mc = Json.obj(json.get("minecraft"));
        Ui.Card head = addCard(page, name);
        head.add(Ui.label("CurseForge 整合包 · 版本 " + Json.str(json, "version", "")
                + " · 作者 " + Json.str(json, "author", "")
                + " · Minecraft " + Json.str(mc, "version", ""), 13, Theme.gray2));
        List<Object> files = Json.arr(json.get("files"));
        Ui.Card list = addCard(page, "共 " + files.size() + " 个文件（CurseForge 仅记录项目与文件 ID）：");
        for (Object o : files) {
            java.util.Map<String, Object> item = Json.obj(o);
            String row = "project " + Json.str(item, "projectID", "") + " / file " + Json.str(item, "fileID", "");
            compManifestRow(list, row, 0, null);
        }
    }

    /** 清单文件行，缺失时可下载。 */
    private void compManifestRow(Ui.Card list, String relativePath, long size, String url) {
        Path target = instanceRoot().resolve(relativePath.replace('/', '\\'));
        boolean exists = Files.isRegularFile(target);
        Ui.ListItem row = new Ui.ListItem(Theme.icon(exists ? "file-check" : "file-x", 15,
                exists ? Theme.text : Theme.gray4), relativePath, null, 40);
        row.title.setTextFill(exists ? Theme.text : Theme.gray4);
        String info = (size > 0 ? (size / 1024) + " KB · " : "") + (exists ? "已存在" : "缺失");
        row.info.setText(info);
        row.info.setVisible(true);
        row.info.setManaged(true);
        if (!exists && url != null && !url.isEmpty()) {
            StackPane download = Ui.button("下载", Ui.BTN_NORMAL, () -> compManifestDownload(url, target));
            row.addAction(download);
        }
        list.add(row);
    }

    /** 下载缺失文件到实例目录。 */
    private void compManifestDownload(String url, Path target) {
        Tasks.Task task = new Tasks.Task("下载整合包资源 " + target.getFileName());
        task.subs.add(new Tasks.SubTask("下载文件"));
        Tasks.INSTANCE.add(task);
        win.toast("开始下载", target.getFileName().toString(), 0);
        new Thread(() -> {
            try {
                Files.createDirectories(target.getParent());
                Downloader.downloadFileTo(p -> task.progress = p, url, target, target.getFileName().toString());
                task.subs.get(0).state = Tasks.State.DONE;
                javafx.application.Platform.runLater(() -> {
                    win.toast("下载完成：" + target.getFileName(), "", 1);
                    win.openSetup("complist");
                });
            } catch (Exception e) {
                task.subs.get(0).state = Tasks.State.FAILED;
                task.error = e.getMessage();
                javafx.application.Platform.runLater(() ->
                        win.toast("下载失败：" + target.getFileName(), e.getMessage(), 3));
            }
        }, "TCL-CompDownload").start();
    }

    private ComboBox<String> jijCombo;
    private Label jijTip;
    private VBox jijList;
    private StackPane jijExportAll;
    private Path jijCurrentMod;

    private void buildJarInJar(VBox page) {
        header(page, "嵌套依赖", "查看 Mod 内嵌的 Jar-in-Jar 依赖，可单独导出。");
        Path mods = instanceRoot().resolve("mods");
        List<Path> modJars = new ArrayList<>();
        if (Files.isDirectory(mods)) {
            try (var stream = Files.list(mods)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                        .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(modJars::add);
            } catch (Exception ignored) {
            }
        }
        HBox actions = new HBox(10);
        actions.getChildren().add(Ui.button("刷新", Ui.BTN_NORMAL, () -> win.openSetup("jarinjar")));
        page.getChildren().add(actions);
        VBox.setMargin(actions, new Insets(0, 0, 10, 0));
        if (modJars.isEmpty()) {
            Ui.Card card = addCard(page, "提示");
            card.add(Ui.label("mods 目录中没有 Mod。Jar-in-Jar 是 Fabric 系 Mod 把依赖 jar 打进自身的形式（META-INF/jars/）。", 12.5, Theme.gray2));
            return;
        }
        Ui.Card picker = addCard(page, "选择 Mod");
        jijCombo = new ComboBox<>();
        for (Path p : modJars) jijCombo.getItems().add(p.getFileName().toString());
        jijCombo.setPrefWidth(340);
        jijCombo.setOnAction(e -> {
            int idx = jijCombo.getSelectionModel().getSelectedIndex();
            jijCurrentMod = idx >= 0 && idx < modJars.size() ? modJars.get(idx) : null;
            jijLoadJars();
        });
        picker.add(jijCombo);
        jijTip = Ui.label("请选择一个 Mod。", 12.5, Theme.gray2);
        page.getChildren().add(jijTip);
        jijList = new VBox(8);
        page.getChildren().add(jijList);
        jijExportAll = Ui.button("导出全部内嵌依赖", Ui.BTN_HIGHLIGHT, this::jijExportAll);
        jijExportAll.setVisible(false);
        page.getChildren().add(jijExportAll);
        jijCombo.getSelectionModel().select(0);
        if (!modJars.isEmpty()) {
            jijCurrentMod = modJars.get(0);
            jijLoadJars();
        }
    }

    /** 后台读 META-INF/jars 下的内嵌 jar。 */
    private void jijLoadJars() {
        jijList.getChildren().clear();
        jijExportAll.setVisible(false);
        if (jijCurrentMod == null || !Files.isRegularFile(jijCurrentMod)) {
            jijTip.setText("请选择一个 Mod。");
            return;
        }
        Path modPath = jijCurrentMod;
        jijTip.setText("正在读取…");
        new Thread(() -> {
            List<String[]> jars = new ArrayList<>(); // {entryName, size, nestedName, nestedVersion}
            String error = null;
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(modPath))) {
                java.util.zip.ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    String n = e.getName();
                    if (!n.startsWith("META-INF/jars/") || !n.endsWith(".jar")) continue;
                    String[] info = jijReadNestedInfo(zip.readAllBytes());
                    jars.add(new String[]{n, String.valueOf(e.getSize()), info[0], info[1]});
                }
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            List<String[]> fJars = jars;
            String fError = error;
            javafx.application.Platform.runLater(() -> jijApplyJars(modPath, fError, fJars));
        }, "TCL-JarInJar").start();
    }

    /** 读内嵌 jar 的名称和版本。 */
    private static String[] jijReadNestedInfo(byte[] nestedJar) {
        try (java.util.zip.ZipInputStream nested = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(nestedJar))) {
            java.util.zip.ZipEntry e;
            while ((e = nested.getNextEntry()) != null) {
                if (!e.getName().equals("fabric.mod.json")) continue;
                java.util.Map<String, Object> json = Json.obj(Json.parse(
                        new String(nested.readAllBytes(), StandardCharsets.UTF_8)));
                String name = Json.str(json, "name", "");
                if (name.isEmpty()) name = Json.str(json, "id", "");
                return new String[]{name, Json.str(json, "version", "")};
            }
        } catch (Exception ignored) {
        }
        return new String[]{"", ""};
    }

    /** 按读到的结果建卡片。 */
    private void jijApplyJars(Path modPath, String error, List<String[]> jars) {
        if (error != null) {
            jijTip.setText("读取失败：" + error);
            return;
        }
        if (jars.isEmpty()) {
            jijTip.setText("该 Mod 不包含内嵌依赖（Jar-in-Jar）。");
            return;
        }
        jijTip.setText("共找到 " + jars.size() + " 个内嵌依赖：");
        for (String[] jar : jars) {
            String entryName = jar[0];
            long size;
            try {
                size = Long.parseLong(jar[1]);
            } catch (Exception e) {
                size = 0;
            }
            String display = jar[2].isEmpty() ? entryName.substring(entryName.lastIndexOf('/') + 1) : jar[2];
            String sub = entryName.substring(entryName.lastIndexOf('/') + 1) + " · " + (size / 1024) + " KB"
                    + (jar[3].isEmpty() ? "" : " · v" + jar[3]);
            Ui.Card card = Ui.card(null);
            HBox layout = new HBox(10);
            layout.setPadding(new Insets(10, 15, 10, 15));
            layout.setAlignment(Pos.CENTER_LEFT);
            VBox text = new VBox(3);
            Label t = Ui.semi(display, 13, Theme.text);
            t.setMaxWidth(Double.MAX_VALUE);
            Label info = Ui.label(sub, 11, Theme.gray2);
            info.setOpacity(0.65);
            text.getChildren().addAll(t, info);
            HBox.setHgrow(text, Priority.ALWAYS);
            layout.getChildren().addAll(text,
                    Ui.button("导出", Ui.BTN_NORMAL, () -> jijExportEntry(modPath, entryName)));
            card.add(layout);
            jijList.getChildren().add(card);
        }
        jijExportAll.setVisible(true);
    }

    /** 导出单个内嵌 jar。 */
    private void jijExportEntry(Path modPath, String entryName) {
        try {
            Path folder = jijExportFolder(modPath);
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(modPath))) {
                java.util.zip.ZipEntry e;
                boolean found = false;
                while ((e = zip.getNextEntry()) != null) {
                    if (!e.getName().equals(entryName)) continue;
                    found = true;
                    Path target = folder.resolve(entryName.substring(entryName.lastIndexOf('/') + 1));
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
                if (!found) {
                    win.toast("导出失败：条目已不存在。", "", 3);
                    return;
                }
            }
            win.toast("已导出：" + entryName.substring(entryName.lastIndexOf('/') + 1), "", 1);
            openFolder(folder.toString());
        } catch (Exception ex) {
            win.toast("导出内嵌依赖失败", ex.getMessage(), 3);
        }
    }

    private void jijExportAll() {
        if (jijCurrentMod == null) return;
        try {
            Path folder = jijExportFolder(jijCurrentMod);
            int count = 0;
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(jijCurrentMod))) {
                java.util.zip.ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (!e.getName().startsWith("META-INF/jars/") || !e.getName().endsWith(".jar")) continue;
                    Files.copy(zip, folder.resolve(e.getName().substring(e.getName().lastIndexOf('/') + 1)),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
            win.toast("已导出 " + count + " 个内嵌依赖！", "", 1);
            openFolder(folder.toString());
        } catch (Exception ex) {
            win.toast("导出全部内嵌依赖失败", ex.getMessage(), 3);
        }
    }

    /** 导出目录 mods/JiJ导出/{mod 名}。 */
    private Path jijExportFolder(Path modPath) throws Exception {
        Path folder = modPath.getParent().resolve("JiJ导出")
                .resolve(modPath.getFileName().toString().replaceAll("\\.jar$", ""));
        Files.createDirectories(folder);
        return folder;
    }

    /** 复制实例文件夹。 */
    private void copyInstance() {
        Data.Instance ins = data.instance();
        if (ins == null) return;
        String name = ins.name + "-副本";
        int n = 2;
        while (true) {
            boolean exists = false;
            for (Data.Instance i : data.instances) if (i.name.equals(name)) exists = true;
            if (!exists) break;
            name = ins.name + "-副本" + (n++);
        }
        final String copyName = name;
        Path srcRoot = instanceRoot();
        Path newFolder = instanceRoot().getParent() != null
                ? instanceRoot().getParent().resolve("versions").resolve(copyName) : srcRoot.resolve(copyName);
        // 隔离目录放 versions/{name}
        final String folderStr = ins.folder != null && !ins.folder.isEmpty()
                ? newFolder.toString() : data.gameDir;
        Tasks.Task task = new Tasks.Task("复制实例 · " + ins.name);
        task.subs.add(new Tasks.SubTask("复制文件"));
        Tasks.INSTANCE.add(task);
        win.toast("正在复制实例", name, 0);
        new Thread(() -> {
            try {
                if (ins.folder != null && !ins.folder.isEmpty()) {
                    Files.createDirectories(newFolder);
                    try (var stream = Files.walk(srcRoot)) {
                        List<Path> files = stream.filter(Files::isRegularFile).toList();
                        task.remainingFiles = files.size();
                        int done = 0;
                        for (Path f : files) {
                            if (task.canceled) throw new Exception("已取消");
                            Path dest = newFolder.resolve(srcRoot.relativize(f).toString());
                            Files.createDirectories(dest.getParent());
                            Files.copy(f, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            done++;
                            task.progress = done / (double) files.size();
                            task.stage = "正在复制 " + done + " / " + files.size();
                        }
                    }
                } else {
                    // 非隔离实例只复制登记
                }
                javafx.application.Platform.runLater(() -> {
                    Data.Instance copy = new Data.Instance(copyName, ins.version, ins.loader, ins.loaderVersion,
                            ins.description, ins.icon, System.currentTimeMillis(), false, folderStr);
                    data.instances.add(copy);
                    data.selectedInstance = data.instances.size() - 1;
                    data.save();
                    task.state = Tasks.State.DONE;
                    task.progress = 1;
                    task.stage = "复制完成";
                    Tasks.INSTANCE.changed();
                    win.toast("已复制实例", copy.name, 1);
                    win.openPage("instance", true);
                });
            } catch (Exception e) {
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "复制失败" : e.getMessage();
                Tasks.INSTANCE.changed();
                javafx.application.Platform.runLater(() -> win.toast("复制失败", task.stage, 3));
            }
        }, "TCL-InstanceCopy").start();
    }

    /** 导出选项，rules 为空时按 showRules 判显隐。 */
    private record ExportOption(String key, String title, String rules, String showRules,
                                boolean defaultChecked, boolean requireModLoader, boolean requireOptiFine,
                                boolean hidden) {
    }

    private static final ExportOption[] EXPORT_OPTIONS = {
            new ExportOption("basic", "游戏本体", null, null, true, false, false, true),
            new ExportOption("options", "游戏本体设置", "options.txt|configureddefaults/", null, true, false, false, false),
            new ExportOption("personal", "游戏本体个人信息", "hotbar.nbt|command_history.txt", null, false, false, false, false),
            new ExportOption("optifine", "OptiFine 设置", "optionsof.txt|optionsshaders.txt", null, true, false, true, false),
            new ExportOption("mod", "Mod", "mods/|!mods/*.disabled|!mods/*.old|!mods/.connector/|coremods/|lib/", null, true, true, false, false),
            new ExportOption("moddisabled", "已禁用的 Mod", "mods/*.disabled|mods/*.old", null, false, true, false, false),
            new ExportOption("packdata", "整合包重要数据", "hotai/|bansoukou/|addons/|multiblocked/|modpack-update-checker/|global_packs/|global_resource_packs/|global_data_packs/|optional_data_packs/|moonlight-global-datapacks/|maps/|icon.png|mods-resourcepacks/|matmos/|resource_assorts/|resource_assorts.json|patchouli_books/|datapacks/|kubejs*/|!kubejs*/probe/|!kubejs*/exported/|!kubejs*/jsconfig.json|!kubejs*/README.txt|openloader/|worldshape/|resources/|scripts/|structures/|fontfiles/|oresources/|packmenu/|craftpresence/|pointblanks/|template*/|!template*/playerdata/|!template*/stats/", null, true, true, false, false),
            new ExportOption("modconfig", "Mod 设置", "config/|!config/jei/world/|!config/worldedit/|config/worldedit/worldedit.properties|!config/spark/|config/spark/config.json|defaultconfigs/|journeymap/config/|journeymap/server/|TrashSlotSaveState.json|customfov.txt|gg.essential.mod/|essential/|!essential/*/|!essential/*.jar*|!essential/screenshot-checksum-caches.json|!essential/microsoft_accounts.json|paragliderSettings.nbt|local/client_config.json|local/ftbl.json|local/client/sidebar_buttons.json|local/client/ftblib.cfg|local/client/xencraft.cfg|liteloader.properties|default_reference.xml|CustomSkinLoader/CustomSkinLoader.json|!config/tacz/custom", null, true, true, false, false),
            new ExportOption("tacz", "TaCZ 枪包", "tacz/|config/tacz/custom/", null, true, true, false, false),
            new ExportOption("immersive", "已上传的沉浸画", "immersive_paintings/", null, true, true, false, false),
            new ExportOption("journeys", "已绘制的地图", "journeymap/data/|xaero/|XaeroWaypoints/|XaeroWorldMap/", null, false, true, false, false),
            new ExportOption("jei", "JEI 个人信息", "config/jei/world/", null, false, true, false, false),
            new ExportOption("emi", "EMI 个人信息", "emi.json", null, false, true, false, false),
            new ExportOption("patchouli", "帕秋莉手册个人信息", "patchouli_data.json", null, false, true, false, false),
            new ExportOption("resourcepacks", "资源包", null, "resourcepacks/|texturepacks/", true, false, false, false),
            new ExportOption("shaderpacks", "光影包", null, "shaderpacks/", true, false, false, false),
    };

    /** 兜底排除规则。 */
    private static final String[] EXPORT_EXCLUDES = {"!*.log", "!*.dat_old", "!*.BakaCoreInfo", "!hmclversion.cfg", "!log4j2.xml"};

    private final java.util.Map<String, Boolean> exportChecked = new java.util.LinkedHashMap<>();
    /** 从配置导入的名称、版本和路径。 */
    private String exportConfiguredName = "";
    private String exportConfiguredVersion = "";
    private String exportConfiguredPackPath = null;
    private TextField exportNameField;
    private TextField exportVersionField;
    private final java.util.List<ExportOption> exportSubOptions = new ArrayList<>();
    private boolean exportModrinth = false;

    private void buildExport(VBox page) {
        Data.Instance ins = data.instance();
        if (ins == null) return;
        header(page, "导出", "把当前实例打成整合包（Modrinth .mrpack 或普通 zip）。");
        Path game = instanceRoot();

        TextField name = Ui.field("");
        name.setPromptText(ins.name);
        name.setMaxWidth(280);
        name.setText(exportConfiguredName); // 配置导入的名称
        TextField version = Ui.field("");
        version.setPromptText("1.0.0");
        version.setMaxWidth(280);
        version.setText(exportConfiguredVersion);
        exportNameField = name;
        exportVersionField = version;
        Ui.Card meta = addCard(page, "整合包信息");
        meta.add(Ui.settingRow("名称", name), Ui.settingRow("版本号", version));

        // 存档、资源包、光影包的具体项
        exportSubOptions.clear();
        addExportSubOption("saves", "存档", true);
        addExportSubOption("resourcepacks", "资源包", true);
        addExportSubOption("shaderpacks", "光影包", true);

        Ui.Card options = addCard(page, "导出内容列表");
        exportChecked.clear();
        for (ExportOption opt : EXPORT_OPTIONS) {
            if (opt.hidden()) continue; // 游戏本体始终包含，不能取消
            boolean hasLoader = hasModLoader(ins);
            if (opt.requireModLoader() && !hasLoader) continue;
            if (opt.requireOptiFine() && !hasOptiFine(ins)) continue;
            // rules 非空直接显示，否则查目录是否存在
            boolean visible;
            if (opt.rules() != null) visible = true;
            else {
                String[] dirs = opt.showRules().split("\\|");
                visible = false;
                for (String d : dirs) {
                    String first = d.replaceAll("[*?].*$", "").split("/")[0];
                    if (Files.isDirectory(game.resolve(first))) {
                        visible = true;
                        break;
                    }
                }
            }
            if (!visible) continue;
            Ui.Check cb = Ui.checkBox(opt.title(), opt.defaultChecked());
            exportChecked.put(opt.key(), opt.defaultChecked() && visible);
            cb.setOnAction(() -> exportChecked.put(opt.key(), cb.isSelected()));
            options.add(cb);
        }
        for (ExportOption sub : exportSubOptions) {
            Ui.Check cb = Ui.checkBox("存档 · " + sub.title(), true);
            exportChecked.put(sub.key(), true);
            cb.setOnAction(() -> exportChecked.put(sub.key(), cb.isSelected()));
            options.add(cb);
        }
        Ui.Check modrinth = Ui.checkBox("导出为 Modrinth 格式（.mrpack，大文件联网引用）", exportModrinth);
        modrinth.setOnAction(() -> exportModrinth = modrinth.isSelected());
        options.add(modrinth);
        if (exportRulesOverride != null) {
            // 配置覆盖时固定导出内容
            options.add(Ui.label("导出内容列表：从配置文件中读取（" + exportRulesOverride.size() + " 行）。", 12.5, Theme.gray2));
        }

        Ui.Card adv = addCard(page, "高级选项");
        Ui.Check includeLauncher = Ui.checkBox("携带启动器（打包启动脚本与资源文件）", exportIncludeLauncher);
        includeLauncher.setOnAction(() -> exportIncludeLauncher = includeLauncher.isSelected());
        adv.add(includeLauncher);
        Ui.Check includeCustom = Ui.checkBox("携带启动器个性化（背景图片/视频/音乐等）", exportIncludeLauncherCustom);
        includeCustom.setOnAction(() -> exportIncludeLauncherCustom = includeCustom.isSelected());
        adv.add(includeCustom);
        Ui.Check dontCheck = Ui.checkBox("直接打包资源文件（不联网查询托管；二次分发可能违反使用协议）", exportDontCheckHosted);
        dontCheck.setOnAction(() -> exportDontCheckHosted = dontCheck.isSelected());
        adv.add(dontCheck);
        HBox configActions = new HBox(10);
        configActions.getChildren().addAll(
                Ui.button("导出配置", Ui.BTN_NORMAL, this::exportExportConfig),
                Ui.button("导入配置", Ui.BTN_NORMAL, this::importExportConfig),
                Ui.button("取消配置覆盖", Ui.BTN_NORMAL, this::cancelExportOverride));
        ((StackPane) configActions.getChildren().get(2)).setDisable(exportRulesOverride == null);
        adv.add(configActions);

        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("开始导出", Ui.BTN_HIGHLIGHT, () -> startExport(name, version)),
                Ui.button("打开实例目录", Ui.BTN_NORMAL, () -> openFolder(game.toString())));
        page.getChildren().add(actions);
    }

    /** 枚举 zip、rar 和子文件夹。 */
    private void addExportSubOption(String folder, String label, boolean acceptFolder) {
        Path dir = instanceRoot().resolve(folder);
        if (!Files.isDirectory(dir)) return;
        String[] blacklist = {"Quark Programmer Art.zip", "+ EuphoriaPatches_", "TCL Skin.zip"};
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                String n = p.getFileName().toString();
                for (String b : blacklist) if (n.contains(b)) return;
                if (Files.isRegularFile(p) && (n.toLowerCase().endsWith(".zip") || n.toLowerCase().endsWith(".rar"))) {
                    exportSubOptions.add(new ExportOption(folder + "/" + n, n,
                            globEscape(folder + "/" + n), null, true, false, false, false));
                } else if (acceptFolder && Files.isDirectory(p)) {
                    exportSubOptions.add(new ExportOption(folder + "/" + n, n,
                            globEscape(folder + "/" + n + "/"), null, true, false, false, false));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean hasModLoader(Data.Instance ins) {
        String v = ins.version.toLowerCase();
        return v.contains("-forge") || v.contains("-fabric") || v.contains("-neoforge") || v.contains("-quilt");
    }

    private boolean hasOptiFine(Data.Instance ins) {
        return ins.version.toLowerCase().contains("optifine");
    }

    /** 通配符转正则。 */
    private static String globToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String globEscape(String s) {
        return s.replace("*", "[*]").replace("?", "[?]");
    }

    /** 规则匹配，! 开头反选，后行覆盖前行。 */
    private static boolean matchesRules(java.util.List<String> rules, String relativePath) {
        boolean keep = false;
        String lower = relativePath.toLowerCase();
        for (String rule : rules) {
            String r = rule.trim().toLowerCase();
            if (r.isEmpty()) continue;
            boolean revert = r.startsWith("!");
            if (revert) r = r.substring(1);
            if (lower.matches(globToRegex(r))) keep = !revert;
        }
        return keep;
    }

    /** 选位置、复制文件、查 hash、写 index、打包。 */
    private void startExport(TextField nameField, TextField versionField) {
        startExport(nameField, versionField, exportRulesOverride != null ? exportRulesOverride : null, false);
    }

    private void startExport(TextField nameField, TextField versionField, java.util.List<String> ruleOverride) {
        startExport(nameField, versionField, ruleOverride, false);
    }

    private void startExport(TextField nameField, TextField versionField, java.util.List<String> ruleOverride, boolean startExportBypass) {
        Data.Instance ins = data.instance();
        if (ins == null) return;
        String packName = nameField.getText() == null || nameField.getText().trim().isEmpty()
                ? (nameField.getPromptText() == null ? ins.name : nameField.getPromptText()) : nameField.getText().trim();
        String packVersion = versionField.getText() == null || versionField.getText().trim().isEmpty()
                ? "1.0.0" : versionField.getText().trim();
        Path game = instanceRoot();

        // 配置覆盖优先，否则合并勾选项和排除段
        if (exportRulesOverride != null) {
            java.util.List<String> override = new ArrayList<>(exportRulesOverride);
            startExport(nameField, versionField, override, false);
            return;
        }
        java.util.List<String> rules = new ArrayList<>();
        rules.add("");
        rules.add("# 修改下方的规则以控制需要导出的内容。");
        rules.add("# 以 ! 开头以反选。可以使用 *、?、[] 通配符。靠后的行覆盖靠前的。");
        rules.add("");
        for (ExportOption opt : EXPORT_OPTIONS) {
            if (opt.hidden()) continue;
            Boolean checked = exportChecked.get(opt.key());
            if (checked == null || !checked) continue;
            if (opt.rules() == null) continue;
            rules.add("# " + opt.title());
            for (String r : opt.rules().split("\\|")) rules.add(r);
            rules.add("");
        }
        for (ExportOption sub : exportSubOptions) {
            Boolean checked = exportChecked.get(sub.key());
            if (checked == null || !checked) continue;
            rules.add("# " + sub.title());
            rules.add(sub.rules());
            rules.add("");
        }
        for (String ex : EXPORT_EXCLUDES) rules.add(ex);
        if (ruleOverride != null) {
            rules = new ArrayList<>(ruleOverride);
        }
        final java.util.List<String> effRules = rules;

        // 配置有 PackPath 就直接用，无效再弹窗
        java.io.File target = null;
        boolean bypassConfiguredPath = startExportBypass;
        if (!bypassConfiguredPath && exportConfiguredPackPath != null
                && !exportConfiguredPackPath.endsWith("/") && !exportConfiguredPackPath.endsWith("\\")) {
            try {
                Path parent = Paths.get(exportConfiguredPackPath).getParent();
                if (parent != null) Files.createDirectories(parent);
                target = Paths.get(exportConfiguredPackPath).toFile();
            } catch (Exception ex) {
                // 路径无效时提示，再手动选择
                String msg = "指定的路径：" + exportConfiguredPackPath + "\n\n" + ex.getMessage();
                Dialogs.confirm(win, "无法使用配置文件中指定的导出路径", msg + "\n\n仍要手动选择导出位置吗？",
                        () -> startExport(nameField, versionField, ruleOverride, true));
                return;
            }
        }
        if (target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择导出位置");
            chooser.setInitialFileName(packName + " " + packVersion);
            if (exportModrinth) {
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Modrinth 整合包文件", "*.mrpack"));
            }
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("压缩文件", "*.zip"));
            target = chooser.showSaveDialog(win.stage);
            if (target == null) return;
        }

        Tasks.Task task = new Tasks.Task("导出整合包：" + packName);
        task.subs.add(new Tasks.SubTask("复制导出内容"));
        task.subs.add(new Tasks.SubTask("联网查询文件信息"));
        task.subs.add(new Tasks.SubTask("生成压缩包"));
        Tasks.INSTANCE.add(task);
        win.toast("开始导出", packName, 0);

        final String fPackName = packName;
        final String fPackVersion = packVersion;
        final java.io.File fTarget = target;
        new Thread(() -> {
            try {
                Path cache = Files.createTempDirectory("tcl-export-");
                Path overrides = cache.resolve("modpack").resolve("overrides");
                Files.createDirectories(overrides);
                task.subs.get(0).state = Tasks.State.RUNNING;

                // 跳过 assets、versions、libraries 等目录
                java.util.List<Path> hostedCandidates = new ArrayList<>();
                final int[] copied = {0};
                try (var walk = Files.walk(game, 20)) {
                    walk.filter(Files::isRegularFile).forEach(file -> {
                        String relative = game.relativize(file).toString().replace('\\', '/');
                        for (String skip : new String[]{"assets/", "versions/", "libraries/",
                                "structureCacheV1/", ".fabric/", ".git/", "avatar-cache/", "cosmetic-cache/"}) {
                            if (relative.startsWith(skip) && !relative.contains("/")) return;
                            if (relative.startsWith(skip)) return;
                        }
                        if (!matchesRules(effRules, relative)) return;
                        try {
                            Path dest = overrides.resolve(relative);
                            Files.createDirectories(dest.getParent());
                            Files.copy(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            copied[0]++;
                            task.remainingFiles = copied[0];
                            String lower = relative.toLowerCase();
                            boolean hostedFolder = lower.contains("mods") || lower.contains("packs")
                                    || lower.contains("openloader") || lower.contains("resource");
                            if (!exportIncludeLauncher && hostedFolder
                                    && (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".jar")
                                    || lower.endsWith(".disabled") || lower.endsWith(".old"))) {
                                hostedCandidates.add(dest);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
                task.subs.get(0).state = Tasks.State.DONE;

                // 用 sha1 查 Modrinth 托管地址
                task.subs.get(1).state = Tasks.State.RUNNING;
                java.util.Map<Path, String> downloadUrls = new java.util.LinkedHashMap<>();
                if (exportDontCheckHosted) {
                    data.launchLog("导出：已选择直接打包资源文件，跳过联网托管查询");
                } else if (!hostedCandidates.isEmpty()) {
                    try {
                        StringBuilder hashes = new StringBuilder("[");
                        java.util.Map<String, Path> byHash = new java.util.LinkedHashMap<>();
                        boolean first = true;
                        for (Path file : hostedCandidates) {
                            String sha1 = sha1Hex(file);
                            byHash.put(sha1, file);
                            if (!first) hashes.append(",");
                            first = false;
                            hashes.append("\"").append(sha1).append("\"");
                        }
                        hashes.append("]");
                        String body = "{\"hashes\": " + hashes + ", \"algorithm\": \"sha1\"}";
                        java.util.Map<String, Object> root = Json.obj(Json.parse(Modrinth.post(
                                "https://api.modrinth.com/v2/version_files", body)));
                        for (Map.Entry<String, Path> e : byHash.entrySet()) {
                            Map<String, Object> hit = Json.obj(root.get(e.getKey()));
                            if (hit.isEmpty()) continue;
                            // 校验返回的 sha1
                            String confirmed = Json.str(Json.obj(hit.get("hashes")), "sha1", "");
                            if (!confirmed.equalsIgnoreCase(e.getKey())) continue;
                            List<Object> files = Json.arr(hit.get("files"));
                            if (files.isEmpty()) continue;
                            String url = Json.str(Json.obj(files.get(0)), "url", "");
                            if (!url.isEmpty()) downloadUrls.put(e.getValue(), url);
                        }
                    } catch (Exception e) {
                        // 联网失败时询问是否继续
                        final Exception fe = e;
                        final boolean[] proceed = {false};
                        CountDownLatch latch = new CountDownLatch(1);
                        javafx.application.Platform.runLater(() -> {
                            Dialogs.confirm(win, "部分文件信息获取失败",
                                    "联网获取部分文件信息失败（" + fe.getMessage() + "），是否继续导出？\n\n"
                                            + "若继续，无法获取信息的文件将被直接打包。由于二次分发可能违反使用协议，请尽量不要公开发布导出的整合包！",
                                    () -> {
                                        proceed[0] = true;
                                        latch.countDown();
                                    });
                            latch.countDown(); // 关掉弹窗也继续
                        });
                        latch.await();
                        if (!proceed[0]) throw new Exception("导出已取消");
                    }
                }
                task.subs.get(1).state = Tasks.State.DONE;

                Path payloadRoot = cache.resolve("modpack");
                copyExtraFiles(payloadRoot);
                if (exportIncludeLauncherCustom) packLauncherCustom(payloadRoot);

                task.subs.get(2).state = Tasks.State.RUNNING;
                if (exportModrinth) {
                    StringBuilder filesJson = new StringBuilder("[");
                    boolean first = true;
                    for (Map.Entry<Path, String> e : downloadUrls.entrySet()) {
                        Path file = e.getKey();
                        if (!first) filesJson.append(",");
                        first = false;
                        String relative = overrides.relativize(file).toString().replace('\\', '/');
                        filesJson.append("{\"path\":").append(Json.quote(relative))
                                .append(",\"hashes\":{\"sha1\":").append(Json.quote(sha1Hex(file).toLowerCase()))
                                .append(",\"sha512\":").append(Json.quote(sha512Hex(file).toLowerCase()))
                                .append("},\"env\":{\"client\":\"required\",\"server\":\"required\"},\"downloads\":[")
                                .append(Json.quote(e.getValue()))
                                .append("],\"fileSize\":").append(Files.size(file)).append("}");
                        Files.deleteIfExists(file); // 命中的文件不打包进 overrides
                    }
                    filesJson.append("]");
                    StringBuilder deps = new StringBuilder("{\"minecraft\":").append(Json.quote(vanillaName(ins)));
                    String loaderVer = loaderVersion(ins);
                    if (loaderVer != null) {
                        if (ins.version.toLowerCase().contains("-forge")) deps.append(",\"forge\":").append(Json.quote(loaderVer));
                        else if (ins.version.toLowerCase().contains("-neoforge")) deps.append(",\"neoforge\":").append(Json.quote(loaderVer));
                        else deps.append(",\"fabric-loader\":").append(Json.quote(loaderVer));
                    }
                    deps.append("}");
                    String index = new StringBuilder("{\"game\":\"minecraft\",\"formatVersion\":1,\"versionId\":")
                            .append(Json.quote(fPackVersion))
                            .append(",\"name\":").append(Json.quote(fPackName))
                            .append(",\"files\":").append(filesJson)
                            .append(",\"dependencies\":").append(deps)
                            .append("}").toString();
                    Files.writeString(cache.resolve("modpack").resolve("modrinth.index.json"), index, StandardCharsets.UTF_8);
                } else {
                    // 普通 zip 附一份规则说明
                    StringBuilder conf = new StringBuilder();
                    for (String r : effRules) conf.append(r).append(System.lineSeparator());
                    Files.writeString(cache.resolve("modpack").resolve("TCraft-Export-Config.txt"), conf.toString(), StandardCharsets.UTF_8);
                }

                // 压到目标文件
                task.threads = 1;
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fTarget.toPath()))) {
                    try (var walk = Files.walk(cache.resolve("modpack"))) {
                        walk.filter(Files::isRegularFile).forEach(file -> {
                            try {
                                String entryName = cache.resolve("modpack").relativize(file).toString().replace('\\', '/');
                                zip.putNextEntry(new ZipEntry(entryName));
                                Files.copy(file, zip);
                                zip.closeEntry();
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
                Files.walk(cache).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                task.subs.get(2).state = Tasks.State.DONE;
                javafx.application.Platform.runLater(() -> {
                    win.toast("导出完成", fTarget.getName(), 1);
                    Shell.openFolder(fTarget.getParent().toString());
                });
            } catch (Exception e) {
                task.subs.forEach(sub -> {
                    if (sub.state == Tasks.State.RUNNING) sub.state = Tasks.State.FAILED;
                });
                task.error = e.getMessage() == null ? "未知错误" : e.getMessage();
                javafx.application.Platform.runLater(() ->
                        win.toast("导出失败", task.error, 3));
            }
        }, "TCL-Export").start();
    }

    /** 剥掉加载器后缀得到原版版本名。 */
    private static String vanillaName(Data.Instance ins) {
        String v = ins.version;
        for (String sfx : new String[]{"-forge", "-fabric", "-neoforge", "-quilt"}) {
            int i = v.toLowerCase().indexOf(sfx);
            if (i > 0) v = v.substring(0, i);
        }
        return v;
    }

    /** 从版本串截加载器版本，如 1.20.1-forge-47.2.0 取 47.2.0。 */
    private static String loaderVersion(Data.Instance ins) {
        String v = ins.version;
        for (String sfx : new String[]{"-forge-", "-fabric-", "-neoforge-", "-quilt-"}) {
            int i = v.toLowerCase().indexOf(sfx);
            if (i > 0) return v.substring(i + sfx.length());
        }
        return null;
    }

    private static String sha1Hex(Path file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String sha512Hex(Path file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void backupInstance() {
        Data.Instance ins = data.instance();
        if (ins == null) return;
        Path backups = instanceRoot().resolve("TCL").resolve("Backups");
        try {
            Files.createDirectories(backups);
        } catch (Exception ignored) {
        }
        String stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        FileChooser chooser = new FileChooser();
        chooser.setTitle("备份实例");
        chooser.setInitialDirectory(backups.toFile());
        chooser.setInitialFileName(ins.name + "-" + stamp + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip 压缩包", "*.zip"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        win.toast("开始备份", file.getName(), 0);
        exportTo(ins, file, "备份实例 · " + ins.name);
    }

    private void exportTo(Data.Instance ins, File file, String title) {
        Tasks.Task task = new Tasks.Task(title);
        task.subs.add(new Tasks.SubTask("写入压缩包"));
        Tasks.INSTANCE.add(task);
        new Thread(() -> {
            try {
                Path game = instanceRoot();
                List<Path> files = new ArrayList<>();
                for (String sub : new String[]{"mods", "config", "saves", "resourcepacks", "shaderpacks", "options.txt"}) {
                    Path p = game.resolve(sub);
                    if (Files.isDirectory(p)) {
                        try (var stream = Files.walk(p)) {
                            stream.filter(Files::isRegularFile).forEach(files::add);
                        }
                    } else if (Files.isRegularFile(p)) files.add(p);
                }
                task.remainingFiles = files.size();
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                    int done = 0;
                    for (Path p : files) {
                        if (task.canceled) throw new Exception("已取消");
                        zip.putNextEntry(new ZipEntry(game.relativize(p).toString().replace('\\', '/')));
                        Files.copy(p, zip);
                        zip.closeEntry();
                        done++;
                        task.progress = done / (double) Math.max(1, files.size());
                        task.stage = "正在备份 " + done + " / " + files.size();
                    }
                }
                task.state = Tasks.State.DONE;
                task.subs.get(0).state = Tasks.State.DONE;
                task.stage = "备份完成";
                javafx.application.Platform.runLater(() -> win.toast("备份完成", file.getName(), 1));
            } catch (Exception e) {
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "备份失败" : e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("备份失败", task.stage, 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-Backup").start();
    }

    private void regionGrow(Region r) {
        HBox.setHgrow(r, Priority.ALWAYS);
    }

    private void openFolder(String path) {
            Shell.openFolder(path);
        }

    /** 目录打包成 zip。 */
    private static void zipDirectory(Path dir, Path target) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            try (var walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        String entryName = dir.relativize(file).toString().replace("\\", "/");
                        zip.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    /** 配置导入的规则覆盖，非 null 时替代勾选规则。 */
    private java.util.List<String> exportRulesOverride = null;
    /** 配置导入的追加内容，/ 结尾表示文件夹。 */
    private java.util.List<String> exportExtraFiles = null;
    private boolean exportIncludeLauncher = false;
    private boolean exportIncludeLauncherCustom = false;
    /** 资源文件直接打包，不联网查托管。 */
    private boolean exportDontCheckHosted = false;

    /** 保存导出配置，段之间用 ===== 分隔。 */
    private void exportExportConfig() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择文件位置");
        chooser.setInitialFileName("export_config.txt");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("整合包导出配置", "*.txt"));
        java.io.File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Name:").append(exportNameField == null ? "" : exportNameField.getText()).append(System.lineSeparator());
            sb.append("Version:").append(exportVersionField == null ? "" : exportVersionField.getText()).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 是否打包启动器本体，以便没有启动器的玩家安装整合包。").append(System.lineSeparator());
            sb.append("IncludeLauncher:").append(exportIncludeLauncher).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 是否打包启动器个性化内容，例如背景图片、视频与音乐。").append(System.lineSeparator());
            sb.append("IncludeLauncherCustom:").append(exportIncludeLauncherCustom).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 是否将 Mod、资源包、光影包的文件直接放入整合包中，这样在导入时就无需联网下载它们。").append(System.lineSeparator());
            sb.append("# 建议仅在无法稳定连接 CurseForge 或 Modrinth 时才考虑启用。").append(System.lineSeparator());
            sb.append("# 二次分发可能违反使用协议，请尽量不要公开发布包含资源文件的整合包！").append(System.lineSeparator());
            sb.append("DontCheckHostedAssets:").append(exportDontCheckHosted).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 若资源未在 Modrinth 上托管，则将其原始文件直接放入整合包中。").append(System.lineSeparator());
            sb.append("# 此选项与 DontCheckHostedAssets 冲突。").append(System.lineSeparator());
            sb.append("ModrinthUploadMode:").append(exportModrinth).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 导出的文件的存放位置。若设置了此项，导出时直接放到此路径，不再弹窗选择。").append(System.lineSeparator());
            sb.append("# 若携带启动器应以 .zip 结尾；否则应以 .mrpack 结尾。").append(System.lineSeparator());
            sb.append("PackPath:").append(exportConfiguredPackPath == null ? "" : exportConfiguredPackPath).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            String sep = "==============================================================";
            sb.append(sep).append(System.lineSeparator());
            for (String r : exportRulesOverride != null ? exportRulesOverride : currentExportRules()) {
                sb.append(r).append(System.lineSeparator());
            }
            sb.append(sep).append(System.lineSeparator());
            sb.append(System.lineSeparator());
            sb.append("# 如果想将额外的文件自动放到压缩包根目录中，可以将它们的路径写在下方。").append(System.lineSeparator());
            sb.append("# 必须是完整路径。每行中，若以 / 结尾则代表是文件夹，不以 / 结尾则代表是文件。").append(System.lineSeparator());
            if (exportExtraFiles != null) {
                for (String f : exportExtraFiles) sb.append(f).append(System.lineSeparator());
            }
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            win.toast("已保存配置文件：" + file.getName(), "", 1);
            openFolder(file.getParent().toString());
        } catch (Exception e) {
            win.toast("保存配置失败", e.getMessage(), 3);
        }
    }

    /** 读导出配置，规则段会覆盖勾选。 */
    private void importExportConfig() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("选择配置文件");
        chooser.setInitialFileName("export_config.txt");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("整合包导出配置", "*.txt"));
        java.io.File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String sep = "==============================================================";
            String[] segments = text.split(sep);
            java.util.Map<String, String> ini = new java.util.LinkedHashMap<>();
            for (String rawLine : segments[0].split("\r?\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("=")) continue;
                int idx = line.indexOf(':');
                if (idx > 0) ini.put(line.substring(0, idx), line.substring(idx + 1));
            }
            exportIncludeLauncher = Boolean.parseBoolean(ini.getOrDefault("IncludeLauncher", "true"));
            exportIncludeLauncherCustom = Boolean.parseBoolean(ini.getOrDefault("IncludeLauncherCustom", "true"));
            exportDontCheckHosted = Boolean.parseBoolean(ini.getOrDefault("DontCheckHostedAssets", "false"));
            if (!exportDontCheckHosted) {
                exportModrinth = Boolean.parseBoolean(ini.getOrDefault("ModrinthUploadMode", "false"));
            }
            exportConfiguredName = ini.getOrDefault("Name", "");
            exportConfiguredVersion = ini.getOrDefault("Version", "");
            String packPath = ini.getOrDefault("PackPath", "");
            exportConfiguredPackPath = packPath.isEmpty() ? null : packPath;
            // 页面重建时由 buildExport 回填
            if (exportNameField != null) {
                exportNameField.setText(exportConfiguredName);
                exportVersionField.setText(exportConfiguredVersion);
            }
            java.util.List<String> rules = new ArrayList<>();
            for (String line : segments.length > 1 ? segments[1].split("\r?\n") : new String[0]) rules.add(line);
            exportRulesOverride = rules;
            exportExtraFiles = null;
            if (segments.length > 2) {
                java.util.List<String> extra = new ArrayList<>();
                for (String line : segments[2].split("\r?\n")) extra.add(line);
                exportExtraFiles = extra;
            }
            win.toast("已读取配置文件：" + file.getName(), "导出内容列表已按配置文件固定，点「取消配置覆盖」恢复勾选。", 1);
            win.openSetup("export");
        } catch (Exception e) {
            win.toast("读取配置失败", e.getMessage(), 3);
        }
    }

    private void cancelExportOverride() {
        exportRulesOverride = null;
        exportExtraFiles = null;
        win.openSetup("export");
        win.toast("已恢复手动勾选。", "", 1);
    }

    /** 勾选生成的规则列表。 */
    private java.util.List<String> currentExportRules() {
        java.util.List<String> rules = new ArrayList<>();
        rules.add("");
        rules.add("# 修改下方的规则以控制需要导出的内容。");
        rules.add("# 以 ! 开头以反选。可以使用 *、?、[] 通配符。靠后的行覆盖靠前的。");
        rules.add("");
        for (ExportOption opt : EXPORT_OPTIONS) {
            if (opt.hidden()) continue;
            Boolean checked = exportChecked.get(opt.key());
            if (checked == null || !checked) continue;
            if (opt.rules() == null) continue;
            rules.add("# " + opt.title());
            for (String r : opt.rules().split("\\|")) rules.add(r);
            rules.add("");
        }
        for (ExportOption sub : exportSubOptions) {
            Boolean checked = exportChecked.get(sub.key());
            if (checked == null || !checked) continue;
            rules.add("# " + sub.title());
            rules.add(sub.rules());
            rules.add("");
        }
        for (String ex : EXPORT_EXCLUDES) rules.add(ex);
        return rules;
    }

    /** 把启动脚本和资源打到根目录。 */
    private void packLauncherFiles(Path payloadRoot) throws Exception {
        Path base = Theme.root();
        for (String script : new String[]{"TCraftLauncher.bat", "TCraftLauncher-debug.bat", "TCraftLauncher.sh"}) {
            Path src = base.resolve(script);
            if (Files.isRegularFile(src)) {
                Files.copy(src, payloadRoot.resolve(script), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // 资源目录
        Path res = base.resolve("res");
        if (Files.isDirectory(res)) copyDirectory(res, payloadRoot.resolve("res"));
        // 说明文件
        String readme = "TCraft Launcher JavaFX 版整合包附带的启动器文件说明" + System.lineSeparator()
                + System.lineSeparator()
                + "本压缩包内含启动脚本与资源文件。运行启动器还需要：" + System.lineSeparator()
                + "1. 已安装 Java 25 或更高版本（含 JavaFX，可直接使用启动器发行包）；" + System.lineSeparator()
                + "2. 启动器程序目录（out/ 与 lib/），请从发行包补齐，或直接使用完整安装的启动器。" + System.lineSeparator()
                + System.lineSeparator()
                + "整合包游戏文件位于 overrides 目录（Modrinth 格式请先导入 .mrpack）。" + System.lineSeparator();
        Files.writeString(payloadRoot.resolve("启动器文件说明.txt"), readme, StandardCharsets.UTF_8);
    }

    /** 打包背景、视频、音乐和 Logo。 */
    private void packLauncherCustom(Path payloadRoot) throws Exception {
        Path gameTcl = Paths.get(data.gameDir, "TCL");
        if (!Files.isDirectory(gameTcl)) return;
        for (String sub : new String[]{"Pictures", "Videos", "Musics", "Music", "Help"}) {
            Path src = gameTcl.resolve(sub);
            if (Files.isDirectory(src)) copyDirectory(src, payloadRoot.resolve("TCL").resolve(sub));
        }
        for (String f : new String[]{"Logo.png", "Setup.ini", "hints.txt"}) {
            Path src = gameTcl.resolve(f);
            if (Files.isRegularFile(src)) {
                Files.copy(src, payloadRoot.resolve("TCL").resolve(f), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void copyDirectory(Path src, Path dest) throws Exception {
        try (var walk = Files.walk(src)) {
            walk.forEach(p -> {
                try {
                    Path t = dest.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(t);
                    else {
                        Files.createDirectories(t.getParent());
                        Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    /** 把追加内容复制到根目录。 */
    private void copyExtraFiles(Path payloadRoot) {
        if (exportExtraFiles == null) return;
        for (String line : exportExtraFiles) {
            String p = line.trim();
            if (p.isEmpty() || p.startsWith("#") || p.startsWith("=")) continue;
            try {
                boolean isFolder = p.endsWith("/") || p.endsWith("\\");
                Path src = Paths.get(p);
                if (!Files.exists(src)) {
                    win.toast("未找到配置文件中指定的单个文件：" + p, "", 3);
                    continue;
                }
                if (isFolder) {
                    copyDirectory(src, payloadRoot.resolve(src.getFileName().toString()));
                } else {
                    Files.copy(src, payloadRoot.resolve(src.getFileName().toString()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                win.toast("复制追加内容失败：" + p, e.getMessage(), 3);
            }
        }
    }

}
