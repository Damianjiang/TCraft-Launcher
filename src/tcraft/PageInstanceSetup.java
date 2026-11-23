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

public class PageInstanceSetup implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private VBox leftList;

    private java.nio.file.Path instanceRoot() {
        Data.Instance ins = data.instance();
        if (ins == null) return java.nio.file.Paths.get(data.gameDir);
        return Isolation.run(ins);
    }

    private java.nio.file.Path baseRoot() {
        Data.Instance ins = data.instance();
        return ins == null ? java.nio.file.Paths.get(data.gameDir) : Isolation.base(ins);
    }

    public PageInstanceSetup(MainWindow win) {
        this.win = win;
    }

    @Override
    public double leftWidth() {
        return 210;
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
        item.setUserData(key); // refreshRight 同步左栏选中态用
        item.setSelected(key.equals(data.instanceSection));
        item.setOnMouseClicked(e -> {
            if (key.equals(data.instanceSection)) return;
            data.instanceSection = key;
            win.refreshRight(true);
        });
        if ("settings".equals(key)) {
            StackPane reset = Ui.iconButton("rotate-ccw", 22, 13, "初始化本页设置", this::resetSettings);
            reset.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
            item.addAction(reset);
        }
        if ("mods".equals(key) || "saves".equals(key) || "screenshots".equals(key)) {
            StackPane refresh = Ui.iconButton("refresh-cw", 22, 13, "刷新", () -> win.refreshRightWithLoading());
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
        Data.Instance ins = data.instance();
        if (ins != null) {
            ins.isolation = Isolation.FOLLOW;
            data.save();
        }
        data.setInstance("Ram", 4096);
        data.setInstance("RamType", 0);
        data.setInstance("RamInitial", 0);
        data.setInstance("JvmArgs", "");
        data.setInstance("GameArgs", "");
        data.setInstance("WindowTitle", "");
        data.setInstance("Info", "");
        data.setInstance("ServerEnter", "");
        data.setInstance("LoginRequire", 0);
        data.setInstance("Java", "");
        data.setInstance("Renderer", -1);
        data.setInstance("PreCommand", "");
        data.setInstance("PreCommandWait", true);
        data.setInstance("Console", false);
        data.setInstance("DisableModUpdate", false);
        data.setInstance("GC", -1);
        data.setInstance("ClasspathHead", "");
        data.setInstance("WindowType", -1);
        data.setInstance("WindowWidth", "854");
        data.setInstance("WindowHeight", "480");
        data.save();
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
                Ui.button("打开文件夹", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().toString())),
                Ui.button("复制实例", Ui.BTN_NORMAL, this::copyInstance),
                Ui.button("删除实例", Ui.BTN_RED, () -> Dialogs.confirm(win, "删除实例",
                        Lang.tf("确定要删除实例「%s」吗？", ins.name),
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
                row("游戏目录", instanceRoot().toString() + (Isolation.on(ins) ? Lang.t("（版本隔离）") : "")),
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
    private final java.util.List<String[]> diagAll = new ArrayList<>();
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
    private int diagLoadGen;

    private void buildDiagnostics(VBox page) {
        header(page, "诊断", "查看游戏日志、崩溃报告与启动器日志，定位启动失败的原因。");
        diagAll.clear();
        diagDisplay.clear();
        diagSourceNames.clear();
        diagSourcePaths.clear();
        Path latest = instanceRoot().resolve("logs").resolve("latest.log");
        if (Files.isRegularFile(latest)) diagAddSource("游戏最新日志（latest.log）", latest);
        Path crashDir = instanceRoot().resolve("crash-reports");
        if (Files.isDirectory(crashDir)) {
            try (java.util.stream.Stream<Path> st = Files.list(crashDir)) {
                st.filter(Files::isRegularFile)
                        .sorted(java.util.Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                        .limit(12)
                        // 下拉项创建处翻译
                        .forEach(p -> diagAddSource(Lang.tf("崩溃报告 · %s", p.getFileName()), p));
            } catch (Exception ignored) {
            }
        }
        Path launcherLog = Resources.logDir().resolve("launcher.log");
        if (Files.isRegularFile(launcherLog)) diagAddSource("启动器运行日志（当前）", launcherLog);
        Path errorLog = Resources.errorLogFile();
        if (Files.isRegularFile(errorLog)) diagAddSource("启动器错误日志（当前）", errorLog);

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
        javafx.animation.PauseTransition diagSearchDebounce =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
        diagSearchDebounce.setOnFinished(e -> diagApplyFilter());
        diagSearch.textProperty().addListener((o, a, b) -> diagSearchDebounce.playFromStart());
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

    private void diagLoadCurrent(VBox page) {
        diagAll.clear();
        diagDisplay.clear();
        int index = diagSourceCombo == null ? -1 : diagSourceCombo.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= diagSourcePaths.size()) {
            // 异步状态文本创建处翻译
            diagStatus.setText(Lang.t("没有可用的日志来源。"));
            diagPath.setText("");
            diagApplyFilter();
            return;
        }
        Path target = diagSourcePaths.get(index);
        diagPath.setText(target.toString());
        diagStatus.setText(Lang.t("正在读取…"));
        // 代次防旧结果覆盖
        final int gen = ++diagLoadGen;
        Thread t = new Thread(() -> {
            java.util.List<String[]> result = new ArrayList<>();
            String failed = null;
            try {
                if (Files.isRegularFile(target)) {
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(target.toFile(), "r")) {
                        long length = Files.size(target);
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
                        int start = Math.max(0, raw.size() - DIAG_MAX_LINES);
                        for (int i = start; i < raw.size(); i++) {
                            result.add(new String[]{raw.get(i), String.valueOf(diagLevel(raw.get(i)))});
                        }
                    }
                }
            } catch (Exception e) {
                failed = e.getMessage();
            }
            java.util.List<String[]> fResult = result;
            String fFailed = failed;
            javafx.application.Platform.runLater(() -> {
                if (gen != diagLoadGen) return;
                diagAll.clear();
                for (String[] l : fResult) diagAll.add(l);
                if (fFailed != null) diagStatus.setText(Lang.t("读取失败：") + fFailed);
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
        diagStatus.setText(diagAll.isEmpty() ? Lang.t("这个来源还没有内容。")
                : (empty ? Lang.t("没有匹配当前筛选的日志。")
                : Lang.tf("共 %d 行，显示 %d 行", diagAll.size(), diagDisplay.size())));
    }

    private void diagCopy() {
        if (diagDisplay.isEmpty()) {
            win.toast("没有可复制的日志。", "", 3);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] item : diagDisplay) sb.append(Redact.apply(item[0])).append(System.lineSeparator());
        javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
        c.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
        win.toast(Lang.tf("已复制 %d 行日志。", diagDisplay.size()), "", 1);
    }

    private void diagExport() {
        if (diagAll.isEmpty()) {
            win.toast("没有可导出的日志。", "", 3);
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(Lang.t("导出日志"));
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        chooser.setInitialFileName("diagnostics-" + stamp + ".log");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(Lang.t("日志文件"), "*.log"));
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(Lang.t("文本文件"), "*.txt"));
        java.io.File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (String[] item : diagAll) sb.append(Redact.apply(diagDesensitize(item[0]))).append(System.lineSeparator());
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            win.toast("日志已导出（已脱敏）。", "", 1);
            openFolder(file.getParent().toString());
        } catch (Exception e) {
            win.toast(Lang.t("导出失败：") + e.getMessage(), "", 3);
        }
    }

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

    private void diagAnalyze() {
        if (diagAll.isEmpty()) {
            win.toast("请先加载一份日志。", "", 3);
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String[] item : diagAll) lines.add(item[0]);
        showCrashReport(CrashAnalyzer.analyze(lines));
    }

    static void showCrashReport(MainWindow win, CrashAnalyzer.Result result) {
        String text = CrashAnalyzer.describe(result);
        boolean bad = result != null && result.found();
        VBox card = Dialogs.msgCard("崩溃分析");
        Label body = Ui.label(text, 13, bad ? Theme.redDark : Theme.text);
        body.setWrapText(true);
        VBox.setMargin(body, new Insets(13, 0, 17, 0));
        card.getChildren().add(body);
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private void showCrashReport(CrashAnalyzer.Result result) {
        showCrashReport(win, result);
    }

    private static final String[] OPT_GROUPS = {"画面", "声音", "操作", "聊天与语言"};

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
            java.nio.charset.Charset cs = GameLauncher.optionsCharset(options);
            for (String line : Files.readAllLines(options, cs)) {
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                optionValues.put(line.substring(0, idx), line.substring(idx + 1));
            }
        } catch (Exception e) {
            Ui.Card notice = addCard(page, "读取失败");
            notice.add(Ui.label(Lang.tf("读取 options.txt 失败：%s", e.getMessage()), 13, Theme.gray2));
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

    private static String fromSlider(int min, boolean percent, int sliderValue) {
        int actual = sliderValue + min;
        if (percent) return String.format(java.util.Locale.ROOT, "%.4f", actual / 100.0).replaceAll("0+$", "").replaceAll("\\.$", "");
        return String.valueOf(actual);
    }

    private static String displayValue(int min, boolean percent, double sliderValue) {
        int v = (int) Math.round(sliderValue) + min;
        return percent ? v + "%" : String.valueOf(v);
    }

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
            Files.writeString(options, sb.toString(), GameLauncher.optionsCharset(options));
            optionDirty = false;
            win.toast("游戏选项已保存，下次启动生效。", "", 1);
        } catch (Exception e) {
            win.toast(Lang.t("保存失败：") + e.getMessage(), "", 3);
        }
    }

    private Path optionPresetDir() {
        Path dir = Resources.home().resolve("option-presets");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    private void saveOptionPreset() {
        Dialogs.input(win, "存为选项预设", "保存当前这张表里的全部选项，之后可以套用到其他实例。", "", name -> {
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
                win.toast(Lang.t("预设已保存：") + n, "", 1);
            } catch (Exception e) {
                win.toast("保存选项预设失败", e.getMessage(), 3);
            }
        });
    }

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
                    // toast 拼接串创建处翻译
                    win.toast(Lang.t("已套用预设「") + preset.getFileName().toString().replace(".json", "") + Lang.t("」"),
                            Lang.t("共 ") + applied + Lang.t("项。记得点「保存修改」写入 options.txt。"), 1);
                } catch (Exception ex) {
                    win.toast("套用预设失败", ex.getMessage(), 3);
                }
            });
            rows.getChildren().add(applyRow);
            Ui.ListItem deleteRow = new Ui.ListItem(Theme.icon("trash-2", 15, Theme.red),
                    Lang.tf("删除预设 · %s", preset.getFileName().toString().replace(".json", "")), null, 40);
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
                    data.log.removeIf(l -> l.contains("启动") || l.contains("游戏") || l.contains("安装"));
                    win.refreshRight();
                    win.toast("已清空", "启动记录已清除", 1);
                }));
        page.getChildren().add(actions);
    }

    private void buildSettings(VBox page) {
        header(page, "版本设置", "仅作用于当前实例的启动选项，每个实例各自保存。");
        Data.Instance current = data.instance();
        Ui.Card basic = addCard(page, "基础设置");
        Label isolationHint = Ui.label(current == null ? "" : Isolation.describe(current), 11.5, Theme.gray3);
        isolationHint.setWrapText(true);
        isolationHint.setMaxWidth(430);
        ComboBox<String> isolation = Ui.combo(new String[]{"使用全局设置", "隔离（独立 mods、saves）", "不隔离"},
                current == null ? 0 : Math.max(0, Math.min(2, current.isolation)));
        isolation.setPrefWidth(280);
        isolation.setOnAction(e -> {
            if (current == null) return;
            current.isolation = isolation.getSelectionModel().getSelectedIndex();
            data.save();
            isolationHint.setText(Isolation.describe(current));
            win.toast("版本隔离", Isolation.describe(current), 1);
        });
        basic.add(Ui.settingRow("版本隔离", isolation));
        basic.add(isolationHint);

        TextField title = Ui.field("默认");
        title.setText(data.getInstance("WindowTitle", ""));
        title.textProperty().addListener((o, a, b) -> data.setInstance("WindowTitle", b));
        basic.add(Ui.settingRow("游戏窗口标题", title));

        TextField info = Ui.field("TCL");
        info.setText(data.getInstance("Info", ""));
        info.textProperty().addListener((o, a, b) -> data.setInstance("Info", b));
        basic.add(Ui.settingRow("自定义信息", info));

        TextField serverEnter = Ui.field("留空则不自动进入服务器");
        serverEnter.setText(data.getInstance("ServerEnter", ""));
        serverEnter.textProperty().addListener((o, a, b) -> data.setInstance("ServerEnter", b));
        basic.add(Ui.settingRow("自动进入服务器", serverEnter));

        ComboBox<String> loginRequire = Ui.combo(new String[]{"不要求", "必须正版登录", "必须第三方登录",
                        "不允许离线登录"},
                Math.max(0, Math.min(3, data.getInstanceInt("LoginRequire", 0))));
        loginRequire.setPrefWidth(240);
        loginRequire.setOnAction(e -> data.setInstance("LoginRequire",
                loginRequire.getSelectionModel().getSelectedIndex()));
        basic.add(Ui.settingRow("登录要求", loginRequire));

        int ramType = data.getInstanceInt("RamType", 0);
        Ui.RadioBox autoRam = Ui.radioBox("自动配置", ramType == 0);
        Ui.RadioBox customRam = Ui.radioBox("自定义", ramType == 1);
        HBox ramRadios = new HBox(20, autoRam, customRam);
        int ram = data.getInstanceInt("Ram", 4096);
        Ui.SliderBar ramBar = Ui.sliderBar(512, physicalRamMb(), ram);
        ramBar.setPrefWidth(250);
        Label ramVal = Ui.label(ramType == 0 ? "自动" : ram + " MB", 12, Theme.text);
        ramBar.setOnChanged(b -> {
            int v = (int) (Math.round(b / 512.0) * 512);
            ramVal.setText(v + " MB");
            data.setInstance("Ram", v);
        });
        HBox ramBox = new HBox(10, ramBar, ramVal);
        ramBox.setAlignment(Pos.CENTER_LEFT);
        basic.add(ramRadios);
        basic.add(Ui.settingRow("内存分配", ramBox));
        Runnable syncRam = () -> {
            boolean on = customRam.isSelected();
            ramBar.setDisable(!on);
            ramVal.setOpacity(on ? 1 : 0.55);
            int v = (int) (Math.round(ramBar.getValue() / 512.0) * 512);
            ramVal.setText(on ? v + " MB" : "自动");
        };
        syncRam.run();
        autoRam.setOnAction(() -> {
            customRam.setSelected(false);
            data.setInstance("RamType", 0);
            syncRam.run();
        });
        customRam.setOnAction(() -> {
            autoRam.setSelected(false);
            data.setInstance("RamType", 1);
            syncRam.run();
        });
        Ui.Check ramOptimize = Ui.checkBox("启动前进行内存优化", false);
        ramOptimize.setSelected(data.getInstanceBool("OptimizeRam", true));
        ramOptimize.setOnAction(() -> data.setInstance("OptimizeRam", ramOptimize.isSelected()));
        basic.add(ramOptimize);

        int initialRam = data.getInstanceInt("RamInitial", data.getInt("LaunchRamInitial", 0));
        if (initialRam > ram) initialRam = 0;
        Ui.SliderBar initialBar = Ui.sliderBar(0, Math.max(512, ram), Math.min(Math.max(512, ram), initialRam));
        initialBar.setPrefWidth(250);
        Label initialVal = Ui.label(initialRam <= 0 ? "自动" : initialRam + " MB", 12, Theme.text);
        initialBar.setOnChanged(b -> {
            int v = (int) (Math.round(b / 512.0) * 512);
            initialVal.setText(v <= 0 ? "自动" : v + " MB");
            data.setInstance("RamInitial", v);
        });
        HBox initialBox = new HBox(10, initialBar, initialVal);
        initialBox.setAlignment(Pos.CENTER_LEFT);
        basic.add(Ui.settingRow("初始内存", initialBox));

        int winType = data.getInstanceInt("WindowType", -1);
        ComboBox<String> winCombo = Ui.combo(new String[]{"跟随全局设置", "全屏", "默认", "自定义尺寸", "最大化"},
                Math.max(0, Math.min(4, winType + 1)));
        winCombo.setPrefWidth(240);
        TextField winW = Ui.field("854");
        TextField winH = Ui.field("480");
        winW.setPrefWidth(70);
        winH.setPrefWidth(70);
        winW.setText(data.getInstance("WindowWidth", "854"));
        winH.setText(data.getInstance("WindowHeight", "480"));
        HBox sizeRow = new HBox(6, winW, Ui.label(" × ", 15, Theme.text), winH);
        sizeRow.setAlignment(Pos.CENTER_LEFT);
        Runnable syncSize = () -> {
            boolean show = winCombo.getSelectionModel().getSelectedIndex() == 3;
            sizeRow.setVisible(show);
            sizeRow.setManaged(show);
        };
        syncSize.run();
        winCombo.setOnAction(e -> {
            int idx = winCombo.getSelectionModel().getSelectedIndex();
            data.setInstance("WindowType", idx - 1);
            syncSize.run();
        });
        winW.textProperty().addListener((o, a, b) -> data.setInstance("WindowWidth", b));
        winH.textProperty().addListener((o, a, b) -> data.setInstance("WindowHeight", b));
        basic.add(Ui.settingRow("窗口大小", winCombo));
        basic.add(sizeRow);

        Ui.Card java = addCard(page, "Java 与高级");
        java.util.List<Data.JavaRuntime> runtimes;
        if (data.hasJavaScanCache()) {
            runtimes = data.scanJavaRuntimes();
        } else {
            runtimes = new ArrayList<>();
            Threads.start("TCL-JavaScan", () -> {
                data.scanJavaRuntimes();
                javafx.application.Platform.runLater(() -> {
                    if (win.currentKey == null || !win.currentKey.equals("instanceSetup")) return;
                    if (!"settings".equals(data.instanceSection)) return;
                    win.refreshRight();
                });
            });
        }
        java.util.List<String> javaOpts = new ArrayList<>();
        java.util.List<String> javaPaths = new ArrayList<>();
        // 下拉项创建处翻译
        String followLabel = Lang.tf("跟随全局设置（%s）", data.javaPath.isEmpty() ? Lang.t("未找到 Java")
                : Paths.get(data.javaPath).getFileName());
        javaOpts.add(followLabel);
        javaPaths.add("");
        javaOpts.add("自动选择（按版本要求）");
        javaPaths.add("@auto");
        String insJava = data.getInstance("Java", "");
        int javaSel = 0;
        if ("@auto".equals(insJava)) javaSel = 1;
        for (Data.JavaRuntime rt : runtimes) {
            javaOpts.add("Java " + rt.major() + " · " + rt.path());
            javaPaths.add(rt.path());
            if (rt.path().equals(insJava)) javaSel = javaOpts.size() - 1;
        }
        ComboBox<String> javaCombo = Ui.combo(javaOpts.toArray(new String[0]), javaSel);
        javaCombo.setPrefWidth(340);
        javaCombo.setOnAction(e -> {
            int i = javaCombo.getSelectionModel().getSelectedIndex();
            if (i < 0 || i >= javaPaths.size()) return;
            data.setInstance("Java", javaPaths.get(i));
            String toastTitle = i == 0 ? "已跟随全局 Java" : i == 1 ? "已使用自动 Java" : "已指定实例 Java";
            String detail = i == 0 ? data.javaPath : i == 1 ? "按版本要求选择" : javaPaths.get(i);
            win.toast(toastTitle, detail, 1);
        });
        java.add(Ui.settingRow("Java 运行时", javaCombo));

        if (Shell.windows()) {
            ComboBox<String> renderer = Ui.combo(new String[]{"跟随全局设置", "默认（游戏自带渲染器）",
                    "软件渲染（llvmpipe）", "DirectX 12（d3d12）", "Vulkan（zink）"},
                    Math.max(0, Math.min(4, data.getInstanceInt("Renderer", -1) + 1)));
            renderer.setPrefWidth(260);
            renderer.setOnAction(e -> data.setInstance("Renderer",
                    renderer.getSelectionModel().getSelectedIndex() - 1));
            java.add(Ui.settingRow("渲染器", renderer));
        }

        TextField jvm = Ui.field("留空则使用全局设置");
        jvm.setText(data.getInstance("JvmArgs", ""));
        jvm.textProperty().addListener((o, a, b) -> data.setInstance("JvmArgs", b));
        java.add(Ui.settingRow("JVM 参数", jvm));

        TextField gameArgs = Ui.field("留空则使用全局设置");
        gameArgs.setText(data.getInstance("GameArgs", ""));
        gameArgs.textProperty().addListener((o, a, b) -> data.setInstance("GameArgs", b));
        java.add(Ui.settingRow("游戏参数", gameArgs));

        TextField pre = Ui.field("留空则不执行");
        pre.setText(data.getInstance("PreCommand", ""));
        pre.textProperty().addListener((o, a, b) -> data.setInstance("PreCommand", b));
        java.add(Ui.settingRow("启动前执行命令", pre));
        Ui.Check preWait = Ui.checkBox("等待命令执行完成后再继续启动", false);
        preWait.setSelected(data.getInstanceBool("PreCommandWait", true));
        preWait.setOnAction(() -> data.setInstance("PreCommandWait", preWait.isSelected()));
        java.add(preWait);

        ComboBox<String> gc = Ui.combo(new String[]{"跟随全局设置", "尽量使用 ZGC", "尽量使用分代 ZGC",
                "标准 G1GC", "不指定（可自定义）", "调优 G1GC"},
                Math.max(0, Math.min(5, data.getInstanceInt("GC", -1) + 1)));
        gc.setPrefWidth(240);
        gc.setOnAction(e -> data.setInstance("GC", gc.getSelectionModel().getSelectedIndex() - 1));
        java.add(Ui.settingRow("内存管理", gc));

        TextField classpathHead = Ui.field("留空则使用全局设置");
        classpathHead.setText(data.getInstance("ClasspathHead", ""));
        classpathHead.textProperty().addListener((o, a, b) -> data.setInstance("ClasspathHead", b));
        java.add(Ui.settingRow("类路径头部", classpathHead));

        Ui.Check console = Ui.checkBox("显示控制台窗口（java.exe，占用一个命令行窗口）", false);
        console.setSelected(data.getInstanceBool("Console", false));
        console.setOnAction(() -> data.setInstance("Console", console.isSelected()));
        java.add(console);

        Ui.Check disableModUpdate = Ui.checkBox("禁止更新 Mod", false);
        disableModUpdate.setSelected(data.getInstanceBool("DisableModUpdate", false));
        disableModUpdate.setOnAction(() -> data.setInstance("DisableModUpdate", disableModUpdate.isSelected()));
        java.add(disableModUpdate);

        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("重置本页设置", Ui.BTN_NORMAL, this::resetSettings),
                Ui.button("打开版本文件", Ui.BTN_NORMAL, () -> openFolder(baseRoot().resolve("versions").toString())));
        page.getChildren().add(actions);
    }

    private static long physicalRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalMemorySize() / (1024 * 1024);
            return total > 0 ? total : 4096;
        } catch (Throwable t) {
            return 4096;
        }
    }

    private void buildMods(VBox page) {
        Path modsDir = instanceRoot().resolve("mods");
        List<Path> mods = listModFiles(modsDir);
        long enabledCount = mods.stream().filter(p -> !isDisabledPack(p)).count();
        header(page, "Mod 管理", Lang.tf("共 %d 个，已启用 %d 个。", mods.size(), enabledCount));
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
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRightWithLoading()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "已安装 Mod");
        if (mods.isEmpty()) {
            Label empty = Ui.label("这个实例的 mods 文件夹是空的。\n点击「安装 Mod」选择文件，或把 .jar 直接拖进来。", 12.5, Theme.gray2);
            empty.setWrapText(true);
            card.add(empty);
        } else {
            String[] filter = {"0"};
            int[] pageNo = {0};
            int[] pageSize = {pageSize("UiPageSizeMods", 20)};
            HBox filterRow = new HBox(6);
            VBox rows = new VBox(0);
            ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
            sizeBox.setOnAction(e -> {
                pageSize[0] = Integer.parseInt(sizeBox.getValue());
                data.set("UiPageSizeMods", pageSize[0]);
                pageNo[0] = 0;
                renderModList(page, mods, filter, pageNo, pageSize, filterRow, rows, modsDir);
            });
            HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
            renderModList(page, mods, filter, pageNo, pageSize, filterRow, rows, modsDir);
            card.add(filterRow);
            VBox.setMargin(filterRow, new Insets(0, 0, 6, 0));
            card.add(sizeRow);
            card.add(rows);
        }
        Ui.dropFiles(page, files -> importMods(modsDir, files));
    }

    private void renderModList(VBox page, List<Path> mods, String[] filter, int[] pageNo, int[] pageSize,
                               HBox filterRow, VBox rows, Path modsDir) {
        filterRow.getChildren().clear();
        long en = mods.stream().filter(p -> !isDisabledPack(p)).count();
        String[] opts = {"全部 (" + mods.size() + ")", "启用 (" + en + ")", "禁用 (" + (mods.size() - en) + ")"};
        for (int i = 0; i < opts.length; i++) {
            String label = opts[i];
            int idx = i;
            boolean active = filter[0].equals(String.valueOf(idx));
            filterRow.getChildren().add(Ui.button(label, active ? Ui.BTN_THEME : Ui.BTN_NORMAL, () -> {
                filter[0] = String.valueOf(idx);
                pageNo[0] = 0;
                renderModList(page, mods, filter, pageNo, pageSize, filterRow, rows, modsDir);
            }));
        }
        List<Path> visible = new java.util.ArrayList<>();
        int f = Integer.parseInt(filter[0]);
        for (Path mod : mods) {
            boolean enabled = !isDisabledPack(mod);
            if (f == 1 && !enabled) continue;
            if (f == 2 && enabled) continue;
            visible.add(mod);
        }
        int pages = Math.max(1, (visible.size() + pageSize[0] - 1) / pageSize[0]);
        pageNo[0] = Math.min(pageNo[0], pages - 1);
        int from = pageNo[0] * pageSize[0];
        int to = Math.min(from + pageSize[0], visible.size());
        rows.getChildren().clear();
        for (int i = from; i < to; i++) rows.getChildren().add(modRow(modsDir, visible.get(i)));
        if (pages > 1) {
            rows.getChildren().add(Ui.pager(pageNo[0], pages, visible.size(), targetPage -> {
                pageNo[0] = targetPage;
                renderModList(page, mods, filter, pageNo, pageSize, filterRow, rows, modsDir);
            }));
        }
    }

    private int pageSize(String key, int fallback) {
        int value = data.getInt(key, fallback);
        return value > 0 && value <= 100 ? value : fallback;
    }

    private Node modRow(Path modsDir, Path mod) {
        String name = mod.getFileName().toString();
        boolean disabled = isDisabledPack(mod);
        String fileDisplay = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
        boolean wantLocalName = Data.get().getInt("ToolModLocalNameStyle", 0) == 1;
        String cacheKey = mod.toAbsolutePath().toString();
        String cached = wantLocalName ? MOD_NAME_CACHE.get(cacheKey) : null;
        String display = cached != null && !cached.isBlank() ? cached.trim() : fileDisplay;
        Node icon = Theme.icon(disabled ? "link-2-off" : "puzzle", 16, disabled ? Theme.gray4 : Theme.accent);
        Ui.ListItem item = new Ui.ListItem(icon, display,
                (disabled ? "已禁用 · " : "") + (display.equals(fileDisplay) ? "" : fileDisplay + " · ") + sizeOf(mod), 40);
        if (wantLocalName && cached == null) {
            MOD_NAME_POOL.execute(() -> {
                String local = readLocalModName(mod);
                if (local == null || local.isBlank()) return;
                cacheModName(cacheKey, local.trim());
                javafx.application.Platform.runLater(() -> {
                    item.title.setText(local.trim());
                    item.info.setText((disabled ? "已禁用 · " : "") + fileDisplay + " · " + sizeOf(mod));
                });
            });
        }
        StackPane info = Ui.listAction("info", 26, 15, "详情", () -> modDetail(mod, item.title.getText(), disabled));
        StackPane open = Ui.listAction("folder-open", 26, 15, "打开文件位置", () -> openFolder(mod.getParent().toString()));
        StackPane toggle = Ui.listAction("power", 26, 15, disabled ? "启用" : "禁用", () -> toggleMod(mod, disabled));
        StackPane update = Ui.listAction("git-pull-request-arrow", 26, 15, "检查更新", () -> checkModUpdate(mod, item.title.getText()));
        StackPane delete = Ui.listAction("trash-2", 26, 15, "删除", () -> deleteFile(mod, "Mod"));
        item.addAction(info);
        item.addAction(open);
        item.addAction(toggle);
        if (!data.getInstanceBool("DisableModUpdate", false)
                && !data.getBool("UiHiddenFunctionModUpdate", false)) item.addAction(update);
        item.addAction(delete);
        item.setOnMouseClicked(e -> modDetail(mod, item.title.getText(), disabled));
        return item;
    }

    // 读盘须后台 不读缓存
    private static String readLocalModName(Path mod) {
        String found = "";
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mod.toFile())) {
            java.util.zip.ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                String text = new String(zip.getInputStream(fabric).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
                if (m.find()) found = m.group(1);
            }
            if (found.isEmpty()) {
                java.util.zip.ZipEntry forge = zip.getEntry("META-INF/mods.toml");
                if (forge == null) forge = zip.getEntry("META-INF/neoforge.mods.toml");
                if (forge != null) {
                    String text = new String(zip.getInputStream(forge).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "(?m)^\\s*displayName\\s*=\\s*\"([^\"]+)\"").matcher(text);
                    if (m.find()) found = m.group(1);
                }
            }
            if (found.isEmpty()) {
                java.util.zip.ZipEntry old = zip.getEntry("mcmod.info");
                if (old != null) {
                    String text = new String(zip.getInputStream(old).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
                    if (m.find()) found = m.group(1);
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    private static final java.util.Map<String, String> MOD_NAME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MOD_NAME_CACHE_LIMIT = 2000;

    private static final java.util.concurrent.ExecutorService MOD_NAME_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "TCL-Mod-Name");
                t.setDaemon(true);
                return t;
            });

    private static void cacheModName(String key, String name) {
        if (MOD_NAME_CACHE.size() >= MOD_NAME_CACHE_LIMIT) MOD_NAME_CACHE.clear();
        MOD_NAME_CACHE.put(key, name);
    }

    private void installMods(Path modsDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择要安装的 Mod"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Mod 文件"),
                "*.jar", "*.litemod", "*.zip", "*.disabled"));
        List<File> files = chooser.showOpenMultipleDialog(win.stage);
        if (files == null || files.isEmpty()) return;
        importMods(modsDir, files.stream().map(File::toPath).toList());
    }

    private void importMods(Path modsDir, List<Path> files) {
        if (files == null || files.isEmpty()) return;
        Imports.run(win, "正在导入 Mod", "mods", modsDir, files, name ->
                name.endsWith(".jar") || name.endsWith(".litemod") || name.endsWith(".zip"),
                false, () -> win.refreshRight());
    }

    private void toggleMod(Path mod, boolean disabled) {
        try {
            String name = mod.getFileName().toString();
            Path target = disabled ? mod.resolveSibling(name.substring(0, name.length() - ".disabled".length()))
                    : mod.resolveSibling(name + ".disabled");
            if (Files.exists(target)) {
                Dialogs.confirm(win, "存在文件冲突", Lang.tf("目前同时存在启用和禁用的两个 Mod 文件：\n - %s\n - %s"
                        + "\n\n注意，这两个文件的内容并不相同。请手动删除或重命名其中一个后再试。",
                        target.getFileName(), name), () -> {
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

    private void modDetail(Path mod, String display, boolean disabled) {
        MOD_NAME_POOL.execute(() -> {
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
            String[] fMeta = meta;
            String fTime = time;
            javafx.application.Platform.runLater(() -> showModDetail(mod, display, disabled, fMeta, fTime));
        });
    }

    private void showModDetail(Path mod, String display, boolean disabled, String[] meta, String time) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(18, 20, 16, 20));
        card.setPrefWidth(430);
        card.setBackground(Ui.fill(Theme.dialogBg, 12));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        card.getChildren().add(Ui.bold(meta != null && !meta[0].isEmpty() ? meta[0] : display, 16, Theme.text));
        VBox rows = new VBox(5);
        if (meta != null && !meta[1].isEmpty()) rows.getChildren().add(Ui.label(Lang.t("版本：") + meta[1], 12.5, Theme.gray1));
        if (meta != null && !meta[2].isEmpty()) {
            Label desc = Ui.label(meta[2], 12, Theme.gray2);
            desc.setWrapText(true);
            rows.getChildren().add(desc);
        }
        rows.getChildren().add(Ui.label(Lang.t("文件：") + mod.getFileName(), 12, Theme.gray2));
        rows.getChildren().add(Ui.label(Lang.tf("大小：%s · 修改于 %s", sizeOf(mod), time), 12, Theme.gray2));
        rows.getChildren().add(Ui.label(Lang.t("状态：") + Lang.t(disabled ? "已禁用" : "已启用"), 12, Theme.gray2));
        card.getChildren().addAll(rows, Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        win.showModal(card);
    }

    private void deleteFile(Path file, String kind) {
        Dialogs.confirm(win, Lang.tf("删除%s", Lang.t(kind)), Lang.tf("确定要删除 %s 吗？文件将移入回收站。", file.getFileName()), () -> {
            // 回收站等待 PS 勿占 FX
            Thread worker = new Thread(() -> {
                boolean ok = Shell.deleteToRecycleBin(file.toString());
                javafx.application.Platform.runLater(() -> {
                    if (ok) {
                        win.toast("已删除", file.getFileName().toString(), 1);
                        win.refreshRight();
                    } else {
                        Dialogs.message(win, Lang.tf("删除%s失败", Lang.t(kind)),
                                "移入回收站失败（系统回收站不可用或权限不足），文件未删除。", true, "知道了");
                    }
                });
            }, "TCL-Recycle");
            worker.setDaemon(true);
            worker.start();
        }, true);
    }

    private void checkModUpdate(Path mod, String display) {
        win.toast("正在检查更新", display, 0);
        Threads.start("TCL-ModUpdate-Check", () -> {
            try {
                String localHash = GameLauncher.sha1Of(mod);
                String[] cur = Modrinth.checkUpdateBySha1(localHash);
                if (cur == null) {
                    javafx.application.Platform.runLater(() ->
                            win.toast("未发现更新", Lang.tf("%s 未被 Modrinth 收录。", display), 0));
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
                // 四段拼接用完整键
                javafx.application.Platform.runLater(() -> Dialogs.confirm(win, "发现更新",
                        Lang.tf("%s 有新版本：%s\n新文件：%s\n\n新版本 Mod 可能不兼容旧存档或者其他 Mod，这可能导致游戏崩溃，甚至永久损坏存档。"
                                + "\n如果你在游玩整合包，请千万不要自行更新 Mod。更新前请先备份存档。"
                                + "\n\n是否更新？旧文件将移入回收站。",
                        display, latest.title(false), latest.fileName),
                        () -> applyModUpdate(mod, latest, display), true));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        win.toast("检查更新失败", e.getMessage() == null ? "网络错误" : e.getMessage(), 3));
            }
        });
    }

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
                // 同名先挪旧文件
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
        List<Path> packs = listPacks(dir);
        long enabledCount = packs.stream().filter(p -> !isDisabledPack(p)).count();
        header(page, label, Lang.tf("共 %d 个，已启用 %d 个。", packs.size(), enabledCount));
        if (isShader) {
            String current = currentShader(root);
            if (!current.isEmpty()) {
                page.getChildren().add(Ui.hint(Lang.t("当前使用光影包：") + current, 0));
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
                Ui.button("导入 " + label, Ui.BTN_NORMAL, () -> installPacks(dir, label)),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRightWithLoading()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, Lang.tf("%s列表", Lang.t(label)));
        if (packs.isEmpty()) {
            Label empty = Ui.label(Lang.tf("这个文件夹是空的。把 %s 的 .zip 直接拖进来，或点「导入 %s」。",
                    Lang.t(label), Lang.t(label)), 12.5, Theme.gray2);
            empty.setWrapText(true);
            card.add(empty);
        } else {
            int[] pageNo = {0};
            int[] pageSize = {pageSize("UiPageSizePacks", 20)};
            VBox rows = new VBox(0);
            ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
            sizeBox.setOnAction(e -> {
                pageSize[0] = Integer.parseInt(sizeBox.getValue());
                data.set("UiPageSizePacks", pageSize[0]);
                pageNo[0] = 0;
                renderPacks(root, dir, folder, label, isShader, packs, pageNo, pageSize, rows);
            });
            HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
            card.add(sizeRow);
            card.add(rows);
            renderPacks(root, dir, folder, label, isShader, packs, pageNo, pageSize, rows);
        }
        Ui.dropFiles(page, files -> importPacks(dir, label, files));
    }

    private void renderPacks(Path root, Path dir, String folder, String label, boolean isShader,
                              List<Path> packs, int[] pageNo, int[] pageSize, VBox rows) {
        int pages = Math.max(1, (packs.size() + pageSize[0] - 1) / pageSize[0]);
        pageNo[0] = Math.min(pageNo[0], pages - 1);
        int from = pageNo[0] * pageSize[0];
        int to = Math.min(from + pageSize[0], packs.size());
        rows.getChildren().clear();
        for (int i = from; i < to; i++) {
            Path pack = packs.get(i);
            String name = pack.getFileName().toString();
            boolean disabled = isDisabledPack(pack);
            boolean isFolderPack = Files.isDirectory(pack);
            String display = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
            String info = disabled ? "已禁用" : isFolderPack ? "文件夹包" : sizeOf(pack);
            Ui.ListItem item = new Ui.ListItem(Theme.icon(disabled ? "link-2-off" : isFolderPack ? "folder" : "image", 16,
                    disabled ? Theme.gray4 : Theme.accent), display, info, 40);
            StackPane toggle = Ui.listAction("power", 26, 15, disabled ? "启用" : "禁用",
                    () -> togglePack(pack, disabled, folder));
            item.addAction(toggle);
            if (isShader && !disabled) {
                StackPane use = Ui.listAction("circle-check", 26, 15, "设为当前使用", () -> {
                    Threads.start("TCL-ShaderUse", () -> {
                        writeOption(root, "shaderPack", display);
                        javafx.application.Platform.runLater(() -> {
                            win.toast("已设为当前光影包", display, 1);
                            win.refreshRight();
                        });
                    });
                });
                item.addAction(use);
            }
            StackPane open = Ui.listAction("folder-open", 26, 15, "打开文件位置", () -> openFolder(dir.toString()));
            item.addAction(open);
            if (!isFolderPack) {
                StackPane delete = Ui.listAction("trash-2", 26, 15, "删除", () -> deleteFile(pack, label));
                item.addAction(delete);
            }
            rows.getChildren().add(item);
        }
        if (pages > 1) {
            rows.getChildren().add(Ui.pager(pageNo[0], pages, packs.size(), targetPage -> {
                pageNo[0] = targetPage;
                renderPacks(root, dir, folder, label, isShader, packs, pageNo, pageSize, rows);
            }));
        }
    }

    private void installPacks(Path dir, String label) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择要导入的 ") + label);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Zip 压缩包"), "*.zip"));
        List<File> files = chooser.showOpenMultipleDialog(win.stage);
        if (files == null || files.isEmpty()) return;
        importPacks(dir, label, files.stream().map(File::toPath).toList());
    }

    private void importPacks(Path dir, String label, List<Path> files) {
        if (files == null || files.isEmpty()) return;
        Imports.run(win, "正在导入 " + label, label, dir, files, name -> name.endsWith(".zip"),
                true, () -> win.refreshRight());
    }

    private List<Path> listPacks(Path dir) {
        List<Path> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.forEach(p -> {
                        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        if (Files.isDirectory(p) || n.endsWith(".zip") || n.endsWith(".zip.disabled")) out.add(p);
                    });
                }
            }
        } catch (Exception ignored) {
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        return out;
    }

    private List<Path> listModFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try {
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile).filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".jar") || n.endsWith(".jar.disabled")
                                || n.endsWith(".zip") || n.endsWith(".zip.disabled")
                                || n.endsWith(".litemod") || n.endsWith(".litemod.disabled");
                    }).forEach(out::add);
                }
            }
        } catch (Exception ignored) {
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        return out;
    }

    private boolean isDisabledPack(Path p) {
        return p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".disabled");
    }

    private String currentShader(Path root) {
        String raw = readOption(root, "shaderPack").trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        return raw.equalsIgnoreCase("off") ? "" : raw;
    }

    private boolean hasShaderLoader() {
        Path mods = instanceRoot().resolve("mods");
        try {
            if (Files.isDirectory(mods)) {
                try (var stream = Files.list(mods)) {
                    for (Path p : stream.toList()) {
                        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
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

    private String readOption(Path root, String key) {
        Path options = root.resolve("options.txt");
        if (!Files.isRegularFile(options)) return "";
        try {
            for (String line : Files.readAllLines(options, GameLauncher.optionsCharset(options))) {
                int idx = line.indexOf(':');
                if (idx > 0 && line.substring(0, idx).equals(key)) return line.substring(idx + 1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void writeOption(Path root, String key, String value) {
        Path options = root.resolve("options.txt");
        try {
            java.nio.charset.Charset cs = Files.exists(options)
                    ? GameLauncher.optionsCharset(options) : StandardCharsets.UTF_8;
            List<String> lines = Files.exists(options)
                    ? Files.readAllLines(options, cs) : new ArrayList<>();
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
            Files.write(options, out, cs);
        } catch (Exception e) {
            javafx.application.Platform.runLater(() -> win.toast("写入 options.txt 失败", e.getMessage(), 3));
        }
    }

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
            data.log("同步资源包到 options.txt 失败：" + Log.describe(e));
        }
    }

    private void renameSave(Path save) {
        Dialogs.input(win, "重命名存档", "", save.getFileName().toString(), name -> {
            if (name == null || name.equals(save.getFileName().toString())) return;
            String error = saveNameError(name);
            if (error != null) {
                win.toast("存档名不合法", error, 3);
                return;
            }
            Path target;
            try {
                target = save.resolveSibling(name.trim()).toAbsolutePath().normalize();
                // normalize 后须仍落在存档目录内
                if (!target.startsWith(save.getParent().toAbsolutePath().normalize())) {
                    win.toast("重命名失败", "目标路径超出了存档目录。", 3);
                    return;
                }
            } catch (Exception ex) {
                win.toast("存档名不合法", ex.getMessage(), 3);
                return;
            }
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

    private static String saveNameError(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "名称不能为空。";
        if (value.length() > 64) return "名称不能超过 64 个字符。";
        if (value.equals(".") || value.equals("..")) return "名称不合法。";
        for (char c : "\\/:*?\"<>|".toCharArray()) {
            if (value.indexOf(c) >= 0) return Lang.tf("名称不能包含字符：%s", c);
        }
        if (value.endsWith(".") || value.endsWith(" ")) return "名称不能以点或空格结尾。";
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        if (upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return "这是系统保留名称。";
        return null;
    }

    private void exportSave(Path save) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("导出存档"));
        chooser.setInitialFileName(save.getFileName() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Zip 压缩包"), "*.zip"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        Tasks.Task task = new Tasks.Task("导出存档 · " + save.getFileName());
        task.subs.add(new Tasks.SubTask("写入压缩包"));
        Tasks.INSTANCE.add(task);
        win.toast("开始导出", save.getFileName().toString(), 0);
        Threads.start("TCL-SaveExport", () -> {
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
                task.subs.forEach(sub -> {
                    if (sub.state == Tasks.State.RUNNING) sub.state = Tasks.State.FAILED;
                });
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "导出失败" : e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("导出失败", task.stage, 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        });
    }

    private void deleteSave(Path save) {
        Dialogs.confirm(win, "删除存档", Lang.tf("确定要删除存档 %s 吗？\n该世界将永久丢失，无法恢复。", save.getFileName()), () -> {
            Threads.start("TCL-SaveDelete", () -> {
                String error = null;
                try (var stream = Files.walk(save)) {
                    for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                final String fError = error;
                javafx.application.Platform.runLater(() -> {
                    if (fError != null) {
                        win.toast("删除失败", fError, 3);
                    } else {
                        win.toast("已删除存档", save.getFileName().toString(), 1);
                        win.refreshRight();
                    }
                });
            });
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

    private void togglePack(Path file, boolean disabled, String folder) {
        String name = file.getFileName().toString();
        Path target = disabled ? file.resolveSibling(name.substring(0, name.length() - ".disabled".length()))
                : file.resolveSibling(name + ".disabled");
        if (Files.exists(target)) {
            win.toast("目标路径已存在文件", target.getFileName().toString(), 2);
            return;
        }
        Threads.start("TCL-PackToggle", () -> {
            String error = null;
            try {
                Files.move(file, target);
                if ("resourcepacks".equals(folder)) syncResourcePacks(instanceRoot());
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            final String fError = error;
            javafx.application.Platform.runLater(() -> {
                if (fError != null) {
                    win.toast("操作失败", fError, 3);
                    return;
                }
                win.toast(disabled ? "已启用" : "已禁用", target.getFileName().toString(), 1);
                win.refreshRight();
            });
        });
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
        header(page, "存档", Lang.tf("共 %d 个世界。", saves.size()));
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
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRightWithLoading()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "世界列表");
        if (saves.isEmpty()) {
            card.add(Ui.label("还没有存档。在游戏中创建世界后，这里会显示你的存档。", 12.5, Theme.gray2));
        } else {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            int[] pageSize = {pageSize("UiPageSizeSaves", 20)};
            int[] pageNo = {0};
            VBox rows = new VBox(1);
            ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
            sizeBox.setOnAction(e -> {
                pageSize[0] = Integer.parseInt(sizeBox.getValue());
                data.set("UiPageSizeSaves", pageSize[0]);
                pageNo[0] = 0;
                renderSaves(saves, fmt, pageNo, pageSize, rows);
            });
            HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
            card.add(sizeRow);
            card.add(rows);
            renderSaves(saves, fmt, pageNo, pageSize, rows);
        }
    }

    private void renderSaves(List<Path> saves, DateTimeFormatter fmt, int[] pageNo, int[] pageSize, VBox rows) {
        int pages = Math.max(1, (saves.size() + pageSize[0] - 1) / pageSize[0]);
        pageNo[0] = Math.min(pageNo[0], pages - 1);
        int from = pageNo[0] * pageSize[0];
        int to = Math.min(from + pageSize[0], saves.size());
        rows.getChildren().clear();
        for (int i = from; i < to; i++) {
            Path save = saves.get(i);
            String time = "";
            try {
                time = Files.getLastModifiedTime(save).toInstant().atZone(ZoneId.systemDefault()).format(fmt);
            } catch (Exception ignored) {
            }
            String fTime = time;
            Ui.ListItem item = new Ui.ListItem(Theme.icon("map", 16, Theme.text), save.getFileName().toString(), "最后修改 " + time, 40);
            StackPane open = Ui.listAction("folder-open", 26, 15, "打开存档文件夹", () -> openFolder(save.toString()));
            StackPane rename = Ui.listAction("pencil", 26, 15, "重命名存档", () -> renameSave(save));
            StackPane export = Ui.listAction("upload", 26, 15, "导出为 .zip", () -> exportSave(save));
            StackPane delete = Ui.listAction("trash-2", 26, 15, "删除存档", () -> deleteSave(save));
            item.addAction(open);
            item.addAction(rename);
            item.addAction(export);
            item.addAction(delete);
            item.setOnMouseClicked(e -> openFolder(save.toString()));
            item.getProperties().put("time", fTime);
            rows.getChildren().add(item);
        }
        if (pages > 1) {
            rows.getChildren().add(Ui.pager(pageNo[0], pages, saves.size(), targetPage -> {
                pageNo[0] = targetPage;
                renderSaves(saves, fmt, pageNo, pageSize, rows);
            }));
        }
    }

    private void buildScreenshots(VBox page) {
        Path dir = instanceRoot().resolve("screenshots");
        List<Path> shots = listFiles(dir, ".png", ".jpg", ".jpeg");
        header(page, "截图", Lang.tf("共 %d 张截图。", shots.size()));
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
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRightWithLoading()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        if (shots.isEmpty()) {
            Ui.Card card = addCard(page, "截图列表");
            card.add(Ui.label("还没有截图。在游戏中按 F2 截图后会显示在这里。", 12.5, Theme.gray2));
            return;
        }
        Ui.Card card = addCard(page, "截图列表");
        int[] pageSize = {pageSize("UiPageSizeShots", 20)};
        VBox content = new VBox(6);
        ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
        HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
        card.add(sizeRow);
        card.add(content);
        int[] pageNo = {0};
        Runnable[] render = new Runnable[1];
        sizeBox.setOnAction(e -> {
            pageSize[0] = Integer.parseInt(sizeBox.getValue());
            data.set("UiPageSizeShots", pageSize[0]);
            pageNo[0] = 0;
            render[0].run();
        });
        render[0] = () -> {
            int pages = Math.max(1, (shots.size() + pageSize[0] - 1) / pageSize[0]);
            pageNo[0] = Math.min(pageNo[0], pages - 1);
            int from = pageNo[0] * pageSize[0];
            int to = Math.min(from + pageSize[0], shots.size());
            FlowPane flow = new FlowPane(12, 12);
            flow.setPadding(new Insets(4));
            for (int i = from; i < to; i++) {
                Path shot = shots.get(i);
                VBox box = new VBox(5);
                box.setPrefWidth(180);
                try {
                    ImageView iv = new ImageView(cachedScreenshot(shot));
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
                StackPane open = Ui.listAction("folder-open", 24, 14, "打开图片", () -> openFolder(shot.toString()));
                StackPane del = Ui.listAction("trash-2", 24, 14, "删除截图", () -> deleteFile(shot, "截图"));
                btns.getChildren().addAll(open, del);
                rowBtns.getChildren().add(btns);
                box.getChildren().add(rowBtns);
                box.setOnMouseClicked(e -> openFolder(shot.toString()));
                box.setPadding(new Insets(6));
                box.setBackground(Ui.fill(Theme.cardBg, 8));
                flow.getChildren().add(box);
            }
            content.getChildren().setAll(flow);
            if (pages > 1) {
                content.getChildren().add(Ui.pager(pageNo[0], pages, shots.size(), targetPage -> {
                    pageNo[0] = targetPage;
                    render[0].run();
                }));
            }
        };
        render[0].run();
    }

    private static final java.util.LinkedHashMap<String, javafx.scene.image.Image> SCREENSHOT_CACHE =
            new java.util.LinkedHashMap<>(32, 0.75f, true);
    private static final int SCREENSHOT_CACHE_LIMIT = 60;

    private static javafx.scene.image.Image cachedScreenshot(Path p) {
        try {
            String key = p.toAbsolutePath() + "|" + Files.getLastModifiedTime(p).toMillis();
            synchronized (SCREENSHOT_CACHE) {
                javafx.scene.image.Image hit = SCREENSHOT_CACHE.get(key);
                if (hit != null) return hit;
            }
            javafx.scene.image.Image img = new javafx.scene.image.Image(p.toUri().toString(), 170, 96, false, true, true);
            synchronized (SCREENSHOT_CACHE) {
                while (SCREENSHOT_CACHE.size() >= SCREENSHOT_CACHE_LIMIT) {
                    SCREENSHOT_CACHE.remove(SCREENSHOT_CACHE.keySet().iterator().next());
                }
                SCREENSHOT_CACHE.put(key, img);
            }
            return img;
        } catch (Exception e) {
            return new javafx.scene.image.Image(p.toUri().toString(), 170, 96, false, true, true);
        }
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
                        win.toast(Lang.t("已复制地址：") + entry.ip, "", 1);
                    }
                    case "上移" -> moveServer(entry, -1, refresh);
                    case "下移" -> moveServer(entry, 1, refresh);
                    case "编辑" -> editServerDialog(entry, refresh);
                    case "删除" -> Dialogs.confirm(win, "删除服务器", Lang.tf("确定要删除服务器“%s”吗？", entry.name), () -> {
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

    private void launchToServer(Servers.Entry entry) {
        if (entry.ip == null || entry.ip.trim().isEmpty()) {
            win.toast("这个服务器没有填写地址。", "", 3);
            return;
        }
        win.launchGame(entry.ip.trim());
    }

    private void pingSingle(Servers.Entry entry, Label status) {
        status.setText("查询中……");
        Threads.start("TCL-Ping-One", () -> {
            Servers.PingResult r = Servers.ping(entry.ip, 3000);
            javafx.application.Platform.runLater(() -> applyPing(status, r));
        });
    }

    // 固定 8 线程
    private static final java.util.concurrent.ExecutorService PING_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "TCL-Ping");
                t.setDaemon(true);
                return t;
            });

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
                java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(statuses.size());
                for (Label label : statuses) {
                    PING_POOL.execute(() -> {
                        try {
                            Servers.Entry entry = (Servers.Entry) label.getProperties().get("entry");
                            Servers.PingResult r = Servers.ping(entry.ip, 3000);
                            javafx.application.Platform.runLater(() -> applyPing(label, r));
                        } catch (Exception e) {
                            javafx.application.Platform.runLater(() -> {
                                if (label.getScene() == null) return;
                                label.setText("离线（查询异常）");
                                label.setTextFill(Theme.gray3);
                                label.setOpacity(0.8);
                            });
                        } finally {
                            done.countDown();
                        }
                    });
                }
                done.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                javafx.application.Platform.runLater(() -> pingLock = false);
            }
        }, "TCL-Ping-All");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private void applyPing(Label status, Servers.PingResult r) {
        // 旧标签脱场景后不回写
        if (status.getScene() == null) return;
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
        header(page, "存档数据包", Lang.tf("共 %d 个数据包。", packs.size()));
        HBox actions = new HBox(10);
        actions.getChildren().addAll(
                Ui.button("打开存档文件夹", Ui.BTN_NORMAL, () -> openFolder(instanceRoot().resolve("saves").toString())),
                Ui.button("刷新", Ui.BTN_NORMAL, () -> win.refreshRightWithLoading()));
        page.getChildren().add(actions);
        VBox.setMargin(page.getChildren().get(page.getChildren().size() - 1), new Insets(0, 0, 12, 0));
        Ui.Card card = addCard(page, "数据包列表");
        if (packs.isEmpty()) {
            card.add(Ui.label("还没有数据包。把数据包放到存档的 datapacks 文件夹中即可。", 12.5, Theme.gray2));
        } else {
            int[] pageSize = {pageSize("UiPageSizeDatapacks", 20)};
            int[] pageNo = {0};
            VBox rows = new VBox(0);
            ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
            sizeBox.setOnAction(e -> {
                pageSize[0] = Integer.parseInt(sizeBox.getValue());
                data.set("UiPageSizeDatapacks", pageSize[0]);
                pageNo[0] = 0;
                renderDatapacks(packs, pageNo, pageSize, rows);
            });
            HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
            card.add(sizeRow);
            card.add(rows);
            renderDatapacks(packs, pageNo, pageSize, rows);
        }
    }

    private void renderDatapacks(List<Path> packs, int[] pageNo, int[] pageSize, VBox rows) {
        int pages = Math.max(1, (packs.size() + pageSize[0] - 1) / pageSize[0]);
        pageNo[0] = Math.min(pageNo[0], pages - 1);
        int from = pageNo[0] * pageSize[0];
        int to = Math.min(from + pageSize[0], packs.size());
        rows.getChildren().clear();
        for (int i = from; i < to; i++) {
            Path p = packs.get(i);
            rows.getChildren().add(new Ui.ListItem(Theme.icon("file-box", 16, Theme.text),
                    p.getFileName().toString(), null, 40));
        }
        if (pages > 1) {
            rows.getChildren().add(Ui.pager(pageNo[0], pages, packs.size(), targetPage -> {
                pageNo[0] = targetPage;
                renderDatapacks(packs, pageNo, pageSize, rows);
            }));
        }
    }

    private void buildCompManifest(VBox page) {
        header(page, "整合包清单", "显示实例目录中整合包的组件清单，绿色表示文件已存在。");
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
        final Path scanPack = packPath;
        Threads.start("TCL-CompScan", () -> {
            java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
            String error = null;
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(scanPack))) {
                java.util.zip.ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    if (e.getName().equals("modrinth.index.json") || e.getName().equals("manifest.json")) {
                        entries.put(e.getName(), zip.readAllBytes());
                    }
                    if (entries.size() == 2) break;
                }
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            final byte[] modrinthBytes = entries.get("modrinth.index.json");
            final byte[] curseBytes = entries.get("manifest.json");
            final String fError = error;
            javafx.application.Platform.runLater(() -> {
                if (fError != null) {
                    Ui.Card card = addCard(page, "读取失败");
                    card.add(Ui.label(Lang.tf("读取失败：%s", fError), 13, Theme.gray2));
                    return;
                }
                if (scanPack.getFileName().toString().toLowerCase().endsWith(".mrpack")) {
                    buildCompManifestModrinth(page, scanPack, modrinthBytes);
                } else {
                    buildCompManifestCurseForge(page, scanPack, curseBytes);
                }
            });
        });
    }

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
        head.add(Ui.label(Lang.t("Modrinth 整合包 · 版本 ") + versionId + " · Minecraft " + mc + loader, 13, Theme.gray2));
        List<Object> files = Json.arr(json.get("files"));
        Ui.Card list = addCard(page, Lang.tf("共 %d 个文件，颜色深表示已存在：", files.size()));
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
        head.add(Ui.label(Lang.t("CurseForge 整合包 · 版本 ") + Json.str(json, "version", "")
                + Lang.t(" · 作者 ") + Json.str(json, "author", "")
                + " · Minecraft " + Json.str(mc, "version", ""), 13, Theme.gray2));
        List<Object> files = Json.arr(json.get("files"));
        Ui.Card list = addCard(page, Lang.tf("共 %d 个文件（CurseForge 仅记录项目与文件 ID）：", files.size()));
        for (Object o : files) {
            java.util.Map<String, Object> item = Json.obj(o);
            String row = "project " + Json.str(item, "projectID", "") + " / file " + Json.str(item, "fileID", "");
            compManifestRow(list, row, 0, null);
        }
    }

    private void compManifestRow(Ui.Card list, String relativePath, long size, String url) {
        Path root = instanceRoot().toAbsolutePath().normalize();
        Path target = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root)) {
            Ui.ListItem bad = new Ui.ListItem(Theme.icon("file-x", 15, Theme.gray4),
                    relativePath, null, 40);
            bad.title.setTextFill(Theme.gray4);
            bad.info.setText(Lang.t("路径不合法"));
            bad.info.setVisible(true);
            bad.info.setManaged(true);
            list.add(bad);
            return;
        }
        boolean exists = Files.isRegularFile(target);
        Ui.ListItem row = new Ui.ListItem(Theme.icon(exists ? "file-check" : "file-x", 15,
                exists ? Theme.text : Theme.gray4), relativePath, null, 40);
        row.title.setTextFill(exists ? Theme.text : Theme.gray4);
        String info = (size > 0 ? (size / 1024) + " KB · " : "") + (exists ? Lang.t("已存在") : Lang.t("缺失"));
        row.info.setText(info);
        row.info.setVisible(true);
        row.info.setManaged(true);
        if (!exists && url != null && !url.isEmpty()) {
            StackPane download = Ui.button("下载", Ui.BTN_NORMAL, () -> compManifestDownload(url, target));
            row.addAction(download);
        }
        list.add(row);
    }

    private void compManifestDownload(String url, Path target) {
        compManifestDownload(url, target, false);
    }

    private void compManifestDownload(String url, Path target, boolean confirmed) {
        if (!confirmed) {
            String lower = url.toLowerCase(java.util.Locale.ROOT);
            if (!lower.startsWith("https://")) {
                Dialogs.confirm(win, "不安全的下载地址",
                        // 整句成键 半句拼接查不到
                        Lang.t(lower.startsWith("http://")
                                ? "这个文件的下载地址是 http 明文链接，下载内容可能被中途篡改。\n\n仍要继续下载吗？"
                                : "这个文件的下载地址不是 https，下载内容可能被中途篡改。\n\n仍要继续下载吗？"),
                        () -> compManifestDownload(url, target, true));
                return;
            }
        }
        Tasks.Task task = new Tasks.Task("下载整合包资源 " + target.getFileName());
        task.subs.add(new Tasks.SubTask("下载文件"));
        Tasks.INSTANCE.add(task);
        win.toast("开始下载", target.getFileName().toString(), 0);
        Threads.start("TCL-CompDownload", () -> {
            try {
                Files.createDirectories(target.getParent());
                Downloader.downloadFileTo(p -> task.progress = p, url, target, target.getFileName().toString());
                task.subs.get(0).state = Tasks.State.DONE;
                task.state = Tasks.State.DONE;
                task.progress = 1;
                javafx.application.Platform.runLater(() -> {
                    win.toast(Lang.t("下载完成：") + target.getFileName(), "", 1);
                    win.openSetup("complist");
                });
            } catch (Exception e) {
                task.subs.get(0).state = Tasks.State.FAILED;
                task.error = e.getMessage() == null ? "下载失败" : e.getMessage();
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                javafx.application.Platform.runLater(() ->
                        win.toast(Lang.t("下载失败：") + target.getFileName(), e.getMessage(), 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        });
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
        // 先 select 再挂监听
        jijCombo.getSelectionModel().select(0);
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
        jijCurrentMod = modJars.get(0);
        jijLoadJars();
    }

    private void jijLoadJars() {
        jijList.getChildren().clear();
        jijExportAll.setVisible(false);
        if (jijCurrentMod == null || !Files.isRegularFile(jijCurrentMod)) {
            jijTip.setText("请选择一个 Mod。");
            return;
        }
        Path modPath = jijCurrentMod;
        jijTip.setText("正在读取…");
        Threads.start("TCL-JarInJar", () -> {
            List<String[]> jars = new ArrayList<>();
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
        });
    }

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

    private void jijApplyJars(Path modPath, String error, List<String[]> jars) {
        // 入口比对防旧结果
        if (modPath != jijCurrentMod) return;
        if (error != null) {
            jijTip.setText("读取失败：" + error);
            return;
        }
        if (jars.isEmpty()) {
            jijTip.setText("该 Mod 不包含内嵌依赖（Jar-in-Jar）。");
            return;
        }
        jijTip.setText(Lang.tf("共找到 %d 个内嵌依赖：", jars.size()));
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

    private void jijExportEntry(Path modPath, String entryName) {
        Threads.start("TCL-JiJ-Export", () -> {
            String error = null;
            Path folder = null;
            boolean found = false;
            try {
                folder = jijExportFolder(modPath);
                try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(modPath))) {
                    java.util.zip.ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        if (!e.getName().equals(entryName)) continue;
                        found = true;
                        Path target = folder.resolve(entryName.substring(entryName.lastIndexOf('/') + 1));
                        Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                }
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            final Path fFolder = folder;
            final boolean fFound = found;
            final String fError = error;
            javafx.application.Platform.runLater(() -> {
                if (fError != null) {
                    win.toast("导出内嵌依赖失败", fError, 3);
                    return;
                }
                if (!fFound) {
                    win.toast("导出失败：条目已不存在。", "", 3);
                    return;
                }
                win.toast(Lang.t("已导出：") + entryName.substring(entryName.lastIndexOf('/') + 1), "", 1);
                openFolder(fFolder.toString());
            });
        });
    }

    private void jijExportAll() {
        if (jijCurrentMod == null) return;
        final Path modPath = jijCurrentMod;
        Threads.start("TCL-JiJ-ExportAll", () -> {
            String error = null;
            Path folder = null;
            int count = 0;
            try {
                folder = jijExportFolder(modPath);
                try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(Files.newInputStream(modPath))) {
                    java.util.zip.ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        if (!e.getName().startsWith("META-INF/jars/") || !e.getName().endsWith(".jar")) continue;
                        Files.copy(zip, folder.resolve(e.getName().substring(e.getName().lastIndexOf('/') + 1)),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    }
                }
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            final Path fFolder = folder;
            final int fCount = count;
            final String fError = error;
            javafx.application.Platform.runLater(() -> {
                if (fError != null) {
                    win.toast("导出全部内嵌依赖失败", fError, 3);
                    return;
                }
                win.toast(Lang.tf("已导出 %d 个内嵌依赖！", fCount), "", 1);
                openFolder(fFolder.toString());
            });
        });
    }

    private Path jijExportFolder(Path modPath) throws Exception {
        Path folder = modPath.getParent().resolve("JiJ导出")
                .resolve(modPath.getFileName().toString().replaceAll("\\.jar$", ""));
        Files.createDirectories(folder);
        return folder;
    }

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
        Path base = baseRoot();
        Path srcRoot = base.resolve("versions").resolve(ins.version);
        Path newFolder = base.resolve("versions").resolve(copyName);
        boolean isolated = Isolation.on(ins);
        if (!Files.isDirectory(srcRoot)) {
            win.toast("复制失败", Lang.t("版本目录不存在：") + srcRoot, 3);
            return;
        }
        Tasks.Task task = new Tasks.Task("复制实例 · " + ins.name);
        task.subs.add(new Tasks.SubTask("复制文件"));
        Tasks.INSTANCE.add(task);
        win.toast("正在复制实例", name, 0);
        Threads.start("TCL-InstanceCopy", () -> {
            try {
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
                PageInstance.rewriteVersionJson(newFolder, ins.version, copyName);
                javafx.application.Platform.runLater(() -> {
                    Data.Instance copy = new Data.Instance(copyName, copyName, ins.loader, ins.loaderVersion,
                            ins.description, ins.icon, System.currentTimeMillis(), false, base.toString(),
                            isolated ? Isolation.ON : Isolation.OFF);
                    copy.addedAt = System.currentTimeMillis();
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
        });
    }

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

    private static final String[] EXPORT_EXCLUDES = {"!*.log", "!*.dat_old", "!*.BakaCoreInfo", "!hmclversion.cfg", "!log4j2.xml"};

    private final java.util.Map<String, Boolean> exportChecked = new java.util.LinkedHashMap<>();
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
        name.setText(exportConfiguredName);
        TextField version = Ui.field("");
        version.setPromptText("1.0.0");
        version.setMaxWidth(280);
        version.setText(exportConfiguredVersion);
        exportNameField = name;
        exportVersionField = version;
        Ui.Card meta = addCard(page, "整合包信息");
        meta.add(Ui.settingRow("名称", name), Ui.settingRow("版本号", version));

        exportSubOptions.clear();
        addExportSubOption("saves", "存档", true);
        addExportSubOption("resourcepacks", "资源包", true);
        addExportSubOption("shaderpacks", "光影包", true);

        Ui.Card options = addCard(page, "导出内容列表");
        exportChecked.clear();
        for (ExportOption opt : EXPORT_OPTIONS) {
            if (opt.hidden()) continue; // 游戏本体始终包含 不能取消
            boolean hasLoader = hasModLoader(ins);
            if (opt.requireModLoader() && !hasLoader) continue;
            if (opt.requireOptiFine() && !hasOptiFine(ins)) continue;
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
            Ui.Check cb = Ui.checkBox(sub.title(), true);
            exportChecked.put(sub.key(), true);
            cb.setOnAction(() -> exportChecked.put(sub.key(), cb.isSelected()));
            options.add(cb);
        }
        Ui.Check modrinth = Ui.checkBox("导出为 Modrinth 格式（.mrpack，大文件联网引用）", exportModrinth);
        modrinth.setOnAction(() -> exportModrinth = modrinth.isSelected());
        options.add(modrinth);
        if (exportRulesOverride != null) {
            options.add(Ui.label(Lang.tf("导出内容列表：从配置文件中读取（%d 行）。", exportRulesOverride.size()), 12.5, Theme.gray2));
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

    private void addExportSubOption(String folder, String label, boolean acceptFolder) {
        Path dir = instanceRoot().resolve(folder);
        if (!Files.isDirectory(dir)) return;
        String[] blacklist = {"Quark Programmer Art.zip", "+ EuphoriaPatches_", "TCL Skin.zip"};
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                String n = p.getFileName().toString();
                for (String b : blacklist) if (n.contains(b)) return;
                if (Files.isRegularFile(p) && (n.toLowerCase().endsWith(".zip") || n.toLowerCase().endsWith(".rar"))) {
                    exportSubOptions.add(new ExportOption(folder + "/" + n, label + " · " + n,
                            globEscape(folder + "/" + n), null, true, false, false, false));
                } else if (acceptFolder && Files.isDirectory(p)) {
                    exportSubOptions.add(new ExportOption(folder + "/" + n, label + " · " + n,
                            globEscape(folder + "/" + n + "/"), null, true, false, false, false));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean hasModLoader(Data.Instance ins) {
        String v = ins.version.toLowerCase(java.util.Locale.ROOT);
        return v.contains("-forge") || v.contains("-fabric") || v.contains("-neoforge") || v.contains("-quilt");
    }

    private boolean hasOptiFine(Data.Instance ins) {
        return ins.version.toLowerCase(java.util.Locale.ROOT).contains("optifine");
    }

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

    private static boolean matchesRules(java.util.List<String> rules, String relativePath) {
        boolean keep = false;
        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        for (String rule : rules) {
            String r = rule.trim().toLowerCase(java.util.Locale.ROOT);
            // # 是注释行
            if (r.isEmpty() || r.startsWith("#")) continue;
            boolean revert = r.startsWith("!");
            if (revert) r = r.substring(1);
            // 目录规则按前缀匹配
            String pattern = r.endsWith("/") ? globToRegex(r) + ".*" : globToRegex(r);
            if (lower.matches(pattern)) keep = !revert;
        }
        return keep;
    }

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
        // 2 参重载防无限递归
        if (ruleOverride != null) {
            rules = new ArrayList<>(ruleOverride);
        }
        final java.util.List<String> effRules = rules;

        java.io.File target = null;
        boolean bypassConfiguredPath = startExportBypass;
        if (!bypassConfiguredPath && exportConfiguredPackPath != null
                && !exportConfiguredPackPath.endsWith("/") && !exportConfiguredPackPath.endsWith("\\")) {
            try {
                Path parent = Paths.get(exportConfiguredPackPath).getParent();
                if (parent != null) Files.createDirectories(parent);
                target = Paths.get(exportConfiguredPackPath).toFile();
            } catch (Exception ex) {
                String msg = "指定的路径：" + exportConfiguredPackPath + "\n\n" + ex.getMessage();
                Dialogs.confirm(win, "无法使用配置文件中指定的导出路径", msg + Lang.t("\n\n仍要手动选择导出位置吗？"),
                        () -> startExport(nameField, versionField, ruleOverride, true));
                return;
            }
        }
        if (target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Lang.t("选择导出位置"));
            chooser.setInitialFileName(packName + " " + packVersion);
            if (exportModrinth) {
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Modrinth 整合包文件"), "*.mrpack"));
            }
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("压缩文件"), "*.zip"));
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
        Threads.start("TCL-Export", () -> {
            Path cache = null;
            try {
                cache = Files.createTempDirectory("tcl-export-");
                Path overrides = cache.resolve("modpack").resolve("overrides");
                Files.createDirectories(overrides);
                task.subs.get(0).state = Tasks.State.RUNNING;

                java.util.List<Path> hostedCandidates = new ArrayList<>();
                final int[] copied = {0};
                try (var walk = Files.walk(game, 20)) {
                    walk.filter(Files::isRegularFile).forEach(file -> {
                        if (task.canceled) throw new java.io.UncheckedIOException(new java.io.IOException("导出已取消"));
                        String relative = game.relativize(file).toString().replace('\\', '/');
                        for (String skip : new String[]{"assets/", "versions/", "libraries/",
                                "structureCacheV1/", ".fabric/", ".git/", "avatar-cache/", "cosmetic-cache/"}) {
                            if (relative.startsWith(skip)) return;
                        }
                        if (!matchesRules(effRules, relative)) return;
                        try {
                            Path dest = overrides.resolve(relative);
                            Files.createDirectories(dest.getParent());
                            Files.copy(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            copied[0]++;
                            task.remainingFiles = copied[0];
                            String lower = relative.toLowerCase(java.util.Locale.ROOT);
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
                            String confirmed = Json.str(Json.obj(hit.get("hashes")), "sha1", "");
                            if (!confirmed.equalsIgnoreCase(e.getKey())) continue;
                            List<Object> files = Json.arr(hit.get("files"));
                            if (files.isEmpty()) continue;
                            String url = Json.str(Json.obj(files.get(0)), "url", "");
                            if (!url.isEmpty()) downloadUrls.put(e.getValue(), url);
                        }
                    } catch (Exception e) {
                        // 取消也放行等待线程
                        final Exception fe = e;
                        final boolean[] proceed = {false};
                        CountDownLatch latch = new CountDownLatch(1);
                        javafx.application.Platform.runLater(() -> {
                            Dialogs.confirm(win, "部分文件信息获取失败",
                                    Lang.tf("联网获取部分文件信息失败（%s），是否继续导出？\n\n"
                                            + "若继续，无法获取信息的文件将被直接打包。由于二次分发可能违反使用协议，请尽量不要公开发布导出的整合包！",
                                            fe.getMessage()),
                                    () -> {
                                        proceed[0] = true;
                                        latch.countDown();
                                    },
                                    false, latch::countDown);
                        });
                        boolean confirmed;
                        try {
                            confirmed = latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            confirmed = false;
                        }
                        if (!confirmed) javafx.application.Platform.runLater(win::closeModal);
                        if (!proceed[0]) throw new Exception("导出已取消");
                    }
                }
                task.subs.get(1).state = Tasks.State.DONE;

                Path payloadRoot = cache.resolve("modpack");
                copyExtraFiles(payloadRoot);
                if (exportIncludeLauncher) packLauncherFiles(payloadRoot);
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
                        Files.deleteIfExists(file);
                    }
                    filesJson.append("]");
                    StringBuilder deps = new StringBuilder("{\"minecraft\":").append(Json.quote(vanillaName(ins)));
                    String loaderVer = loaderVersion(ins);
                    if (loaderVer != null) {
                        if (ins.version.toLowerCase(java.util.Locale.ROOT).contains("-forge")) deps.append(",\"forge\":").append(Json.quote(loaderVer));
                        else if (ins.version.toLowerCase(java.util.Locale.ROOT).contains("-neoforge")) deps.append(",\"neoforge\":").append(Json.quote(loaderVer));
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
                    StringBuilder conf = new StringBuilder();
                    for (String r : effRules) conf.append(r).append(System.lineSeparator());
                    Files.writeString(cache.resolve("modpack").resolve("TCraft-Export-Config.txt"), conf.toString(), StandardCharsets.UTF_8);
                }

                Path packRoot = cache.resolve("modpack");
                task.threads = 1;
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(fTarget.toPath()))) {
                    try (var walk = Files.walk(packRoot)) {
                        walk.filter(Files::isRegularFile).forEach(file -> {
                            try {
                                String entryName = packRoot.relativize(file).toString().replace('\\', '/');
                                zip.putNextEntry(new ZipEntry(entryName));
                                Files.copy(file, zip);
                                zip.closeEntry();
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
                task.subs.get(2).state = Tasks.State.DONE;
                task.state = Tasks.State.DONE;
                task.progress = 1;
                javafx.application.Platform.runLater(() -> {
                    win.toast("导出完成", fTarget.getName(), 1);
                    Shell.openFolder(fTarget.getParent().toString());
                });
            } catch (Exception e) {
                task.subs.forEach(sub -> {
                    if (sub.state == Tasks.State.RUNNING) sub.state = Tasks.State.FAILED;
                });
                task.error = e.getMessage() == null ? "未知错误" : e.getMessage();
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                javafx.application.Platform.runLater(() ->
                        win.toast(task.canceled ? "导出已取消" : "导出失败", task.error, 3));
            } finally {
                if (cache != null) {
                    try (var walk = Files.walk(cache)) {
                        walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                    } catch (Exception ignored) {
                    }
                }
                Tasks.INSTANCE.changed();
            }
        });
    }

    private static String vanillaName(Data.Instance ins) {
        String v = ins.version;
        for (String sfx : new String[]{"-forge", "-fabric", "-neoforge", "-quilt"}) {
            int i = v.toLowerCase(java.util.Locale.ROOT).indexOf(sfx);
            if (i > 0) v = v.substring(0, i);
        }
        return v;
    }

    private static String loaderVersion(Data.Instance ins) {
        String v = ins.version;
        for (String sfx : new String[]{"-forge-", "-fabric-", "-neoforge-", "-quilt-"}) {
            int i = v.toLowerCase(java.util.Locale.ROOT).indexOf(sfx);
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
        Path backups = baseRoot().resolve("TCL").resolve("Backups");
        try {
            Files.createDirectories(backups);
        } catch (Exception ignored) {
        }
        String stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("备份实例"));
        chooser.setInitialDirectory(backups.toFile());
        chooser.setInitialFileName(ins.name + "-" + stamp + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Zip 压缩包"), "*.zip"));
        File file = chooser.showSaveDialog(win.stage);
        if (file == null) return;
        win.toast("开始备份", file.getName(), 0);
        exportTo(ins, file, "备份实例 · " + ins.name);
    }

    private void exportTo(Data.Instance ins, File file, String title) {
        Tasks.Task task = new Tasks.Task(title);
        task.subs.add(new Tasks.SubTask("写入压缩包"));
        Tasks.INSTANCE.add(task);
        Threads.start("TCL-Backup", () -> {
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
                task.subs.forEach(sub -> {
                    if (sub.state == Tasks.State.RUNNING) sub.state = Tasks.State.FAILED;
                });
                task.state = task.canceled ? Tasks.State.CANCELED : Tasks.State.FAILED;
                task.stage = e.getMessage() == null ? "备份失败" : e.getMessage();
                javafx.application.Platform.runLater(() -> win.toast("备份失败", task.stage, 3));
            } finally {
                Tasks.INSTANCE.changed();
            }
        });
    }

    private void openFolder(String path) {
        Shell.openFolder(path);
    }

    private java.util.List<String> exportRulesOverride = null;
    private java.util.List<String> exportExtraFiles = null;
    private boolean exportIncludeLauncher = false;
    private boolean exportIncludeLauncherCustom = false;
    private boolean exportDontCheckHosted = false;

    private void exportExportConfig() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(Lang.t("选择文件位置"));
        chooser.setInitialFileName("export_config.txt");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(Lang.t("整合包导出配置"), "*.txt"));
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
            win.toast(Lang.t("已保存配置文件：") + file.getName(), "", 1);
            openFolder(file.getParent().toString());
        } catch (Exception e) {
            win.toast("保存配置失败", e.getMessage(), 3);
        }
    }

    private void importExportConfig() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(Lang.t("选择配置文件"));
        chooser.setInitialFileName("export_config.txt");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(Lang.t("整合包导出配置"), "*.txt"));
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
            final String packPath = ini.getOrDefault("PackPath", "").trim();
            if (exportNameField != null) {
                exportNameField.setText(exportConfiguredName);
                exportVersionField.setText(exportConfiguredVersion);
            }
            java.util.List<String> rules = new ArrayList<>();
            for (String line : segments.length > 1 ? segments[1].split("\r?\n") : new String[0]) rules.add(line);
            final java.util.List<String> extraFiles = new ArrayList<>();
            if (segments.length > 2) {
                for (String line : segments[2].split("\r?\n")) extraFiles.add(line);
            }
            // 外部输入先确认再生效
            StringBuilder warn = new StringBuilder();
            if (!packPath.isEmpty()) {
                warn.append(Lang.t("· 导出位置被固定为：")).append(packPath).append('\n');
            }
            int realExtras = 0;
            for (String line : extraFiles) {
                String p = line.trim();
                if (p.isEmpty() || p.startsWith("#") || p.startsWith("=")) continue;
                if (realExtras < 5) warn.append(Lang.t("· 会打包本地文件：")).append(p).append('\n');
                realExtras++;
            }
            if (realExtras > 5) warn.append(Lang.tf("· 以及另外 %d 个本地路径\n", realExtras - 5));
            final Runnable apply = () -> {
                exportConfiguredPackPath = packPath.isEmpty() ? null : packPath;
                exportRulesOverride = rules;
                exportExtraFiles = extraFiles.isEmpty() ? null : extraFiles;
                win.toast(Lang.t("已读取配置文件：") + file.getName(),
                        "导出内容列表已按配置文件固定，点「取消配置覆盖」恢复勾选。", 1);
                win.openSetup("export");
            };
            if (warn.length() == 0) {
                apply.run();
            } else {
                Dialogs.confirm(win, "配置文件会改动本机内容", warn.toString()
                                + Lang.t("\n这些设置来自外部文件，确认按此配置导出吗？"),
                        apply);
            }
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

    private void packLauncherFiles(Path payloadRoot) throws Exception {
        Path base = Resources.appDir();
        for (String script : new String[]{"TCraftLauncher.bat", "TCraftLauncher-debug.bat", "TCraftLauncher.sh"}) {
            Path src = base.resolve(script);
            if (Files.isRegularFile(src)) {
                Files.copy(src, payloadRoot.resolve(script),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path self = Resources.jar();
        if (self != null && Files.isRegularFile(self)) {
            String name = self.getFileName().toString().toLowerCase();
            String target = name.endsWith(".exe") ? "TCraftLauncher.exe" : "TCraftLauncher.jar";
            Files.copy(self, payloadRoot.resolve(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Path res = base.resolve("res");
        if (Files.isDirectory(res)) copyDirectory(res, payloadRoot.resolve("res"));
        String readme = "TCraft Launcher JavaFX 版整合包附带的启动器文件说明" + System.lineSeparator()
                + System.lineSeparator()
                + "本压缩包已包含启动器程序、启动脚本与资源文件。" + System.lineSeparator()
                + "运行需要 Java 17 或更高版本；首次启动会自动下载 JavaFX 运行库。" + System.lineSeparator()
                + "zip 不保存可执行位：Linux/macOS 下若 .sh 无法直接运行，请用 sh TCraftLauncher.sh 启动。" + System.lineSeparator()
                + System.lineSeparator()
                + "整合包游戏文件位于 overrides 目录（Modrinth 格式请先导入 .mrpack）。" + System.lineSeparator();
        Files.writeString(payloadRoot.resolve("启动器文件说明.txt"), readme, StandardCharsets.UTF_8);
    }

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

    private void copyExtraFiles(Path payloadRoot) {
        if (exportExtraFiles == null) return;
        for (String line : exportExtraFiles) {
            String p = line.trim();
            if (p.isEmpty() || p.startsWith("#") || p.startsWith("=")) continue;
            try {
                boolean isFolder = p.endsWith("/") || p.endsWith("\\");
                Path src = Paths.get(p);
                if (!Files.exists(src)) {
                    win.toast(Lang.t("未找到配置文件中指定的单个文件：") + p, "", 3);
                    continue;
                }
                if (isFolder) {
                    copyDirectory(src, payloadRoot.resolve(src.getFileName().toString()));
                } else {
                    Files.copy(src, payloadRoot.resolve(src.getFileName().toString()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                win.toast(Lang.t("复制追加内容失败：") + p, e.getMessage(), 3);
            }
        }
    }
}
