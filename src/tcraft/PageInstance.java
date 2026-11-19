package tcraft;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PageInstance implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private final boolean selectMode;
    private boolean gridView;
    private String query = "";
    private PauseTransition searchDebounce;
    private int sortMode = 0;
    private String collectionFilter = "";
    private HBox collectionChips;
    private ScrollPane collectionBar;

    public PageInstance(MainWindow win) {
        this(win, false);
    }

    public PageInstance(MainWindow win, boolean selectMode) {
        this.win = win;
        this.selectMode = selectMode;
        this.gridView = data.getBool("UiInstanceGridView", false);
        this.sortMode = Math.max(0, Math.min(3, data.getInt("UiInstanceSort", 0)));
    }

    @Override
    public double leftWidth() {
        return 300;
    }

    @Override
    public Node left() {
        return buildFolderPanel(win);
    }

    @Override
    public Node right() {
        VBox page = new VBox();
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        page.setMaxHeight(Double.MAX_VALUE);

        Ui.SearchBox search = Ui.searchBox("搜索实例");
        search.textProperty().addListener((o, a, b) -> {
            query = b == null ? "" : b.toLowerCase();
            // 连续输入时只重建一次列表，不必每个按键都重排
            if (searchDebounce == null) {
                searchDebounce = new PauseTransition(Duration.millis(180));
                searchDebounce.setOnFinished(e -> refreshList());
            }
            searchDebounce.playFromStart();
        });
        ComboBox<String> sort = Ui.combo(new String[]{"最近游玩", "名称 A→Z", "版本 新→旧", "安装时间"}, sortMode);
        double sortWidth = Lang.mode == 1 ? 175 : 116;
        sort.setPrefWidth(sortWidth);
        sort.setMinWidth(sortWidth);
        sort.setPrefHeight(32);
        sort.setOnAction(e -> {
            sortMode = Math.max(0, sort.getSelectionModel().getSelectedIndex());
            data.set("UiInstanceSort", sortMode);
            refreshList();
        });

        HBox toolIcons = new HBox(0);
        toolIcons.setAlignment(Pos.CENTER);
        toolIcons.setPadding(new Insets(2));
        toolIcons.setBackground(Ui.fill(Theme.cardBg, 18));
        toolIcons.getChildren().addAll(
                Ui.iconButton("grid-2x2", 30, 14, "切换为网格视图", () -> {
                    gridView = !gridView;
                    data.set("UiInstanceGridView", gridView);
                    refreshList();
                }),
                Ui.iconButton("refresh-cw", 30, 14, "刷新实例列表", () -> {
                    win.toast("实例列表", "已刷新", 1);
                    rebuild();
                }),
                Ui.iconButton("download", 30, 14, "下载新版本", () -> win.openPage("download", true)),
                Ui.iconButton("package", 30, 14, "导入整合包", () -> importModpack(win)),
                Ui.iconButton("folder-open", 30, 14, "打开游戏目录", () -> openFolder(data.gameDir))
        );

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setMaxWidth(Double.MAX_VALUE);
        toolbar.setPadding(new Insets(14, 25, 4, 25));
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMinWidth(120);
        search.setPrefWidth(260);
        toolbar.getChildren().addAll(search, sort, toolIcons);

        collectionChips = new HBox(6);
        collectionChips.setAlignment(Pos.CENTER_LEFT);
        collectionChips.setPadding(new Insets(2, 0, 2, 0));
        collectionBar = Ui.scroll(collectionChips);
        collectionBar.setFitToHeight(true);
        collectionBar.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        collectionBar.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        collectionBar.setMinHeight(36);
        collectionBar.setPrefHeight(36);
        collectionBar.setMaxHeight(36);
        collectionBar.setVisible(false);
        collectionBar.setManaged(false);
        VBox.setMargin(collectionBar, new Insets(0, 25, 0, 25));

        ScrollPane scroll = Ui.scroll(new StackPane());
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setMargin(scroll, new Insets(4, 25, 14, 25));

        page.getChildren().addAll(toolbar, collectionBar, scroll);
        this.refreshRunner = () -> {
            refreshCollectionBar();
            StackPane back = new StackPane();
            back.setBackground(Ui.fill(Theme.cardBg, 8));
            back.setMaxWidth(Double.MAX_VALUE);
            back.getChildren().add(buildList());
            scroll.setContent(back);
        };
        refreshRunner.run();
        return page;
    }

    private Runnable refreshRunner;

    @Override
    public void onHide() {
        if (searchDebounce != null) searchDebounce.stop();
    }

    private void refreshList() {
        if (refreshRunner != null) refreshRunner.run();
    }

    private void refreshCollectionBar() {
        if (collectionBar == null || collectionChips == null) return;
        List<String> all = InstanceMeta.CollectionsStore.listAll();
        if (!collectionFilter.isEmpty() && !all.contains(collectionFilter)) collectionFilter = "";
        collectionChips.getChildren().clear();
        if (all.isEmpty()) {
            collectionBar.setVisible(false);
            collectionBar.setManaged(false);
            return;
        }
        collectionBar.setVisible(true);
        collectionBar.setManaged(true);
        collectionChips.getChildren().add(collectionChip("全部", ""));
        for (String one : all) collectionChips.getChildren().add(collectionChip(one, one));
    }

    private Node collectionChip(String text, String value) {
        boolean selected = collectionFilter.equals(value);
        Label lab = Ui.label(text, 12.5, selected ? Color.WHITE : Theme.text);
        lab.setMaxWidth(160);
        StackPane chip = new StackPane(lab);
        chip.setPadding(new Insets(4, 13, 5, 13));
        chip.setBackground(Ui.fill(selected ? Theme.accent : Theme.cardBg, 13));
        chip.setCursor(Cursor.HAND);
        if (selected) chip.setEffect(Theme.shadow(8, 1, 0.18, Theme.text));
        chip.setOnMouseClicked(e -> {
            if (collectionFilter.equals(value)) return;
            collectionFilter = value;
            refreshList();
        });
        // 合集标签支持右键改名删除
        if (!value.isEmpty()) {
            Ui.tooltip(chip, value + Lang.t("（右键可重命名或删除）"));
            chip.setOnContextMenuRequested(e -> {
                Ui.menuCard(chip,
                        Ui.MenuRow.of("重命名合集…", "pencil", () -> renameCollection(value)),
                        Ui.MenuRow.of("删除合集", "trash-2", () -> deleteCollection(value)));
                e.consume();
            });
        } else {
            Ui.tooltip(chip, "显示全部实例");
        }
        return chip;
    }

    private void renameCollection(String oldName) {
        Dialogs.input(win, "重命名合集", "所有实例上的这个标签会一起改。", oldName, name -> {
            if (name == null || name.equals(oldName)) return;
            String error = InstanceMeta.CollectionsStore.validateName(name, oldName);
            if (!error.isEmpty()) {
                win.toast("重命名失败", error, 2);
                return;
            }
            InstanceMeta.CollectionsStore.rename(oldName, name);
            if (oldName.equals(collectionFilter)) collectionFilter = name;
            refreshList();
            win.toast("合集已重命名", name, 1);
        });
    }

    private void deleteCollection(String name) {
        Dialogs.confirm(win, "删除合集",
                Lang.tf("确定要删除合集「%s」吗？\n只会删掉这个标签，实例本身与其文件不受影响。", name),
                () -> {
                    InstanceMeta.CollectionsStore.delete(name);
                    if (name.equals(collectionFilter)) collectionFilter = "";
                    refreshList();
                    win.toast("合集已删除", name, 1);
                }, true);
    }

    private void rebuild() {
        win.openPage(win.currentKey, false);
    }

    private Node buildList() {
        VBox all = new VBox(1);
        all.setMaxWidth(Double.MAX_VALUE);
        all.setPadding(new Insets(4, 6, 8, 6));
        List<Data.Instance> visible = filtered();
        if (visible.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setPadding(new Insets(60, 20, 60, 20));
            boolean folderEmpty = data.instances.stream()
                    .noneMatch(i -> belongsTo(i.folder, data.gameDir));
            Label title = Ui.bold(folderEmpty ? "无可用版本" : "没有匹配的实例", 19, Theme.accent);
            Label sub = Ui.label(folderEmpty
                    ? "未找到任何版本的游戏，请先下载任意版本的游戏。\n若有已存在的游戏，请在左边的列表中选择「添加文件夹」。"
                    : "换个关键词试试吧。", 12.5, Theme.gray2);
            sub.setWrapText(true);
            sub.setMaxWidth(420);
            empty.getChildren().addAll(title, sub);
            if (folderEmpty) {
                empty.getChildren().add(Ui.button("下载游戏", Ui.BTN_HIGHLIGHT, () -> win.openPage("download", true)));
            }
            all.getChildren().add(empty);
            return all;
        }
        if (gridView) {
            FlowPane flow = new FlowPane(10, 10);
            flow.setPadding(new Insets(8));
            for (Data.Instance ins : visible) flow.getChildren().add(instanceCard(ins));
            all.getChildren().add(flow);
            return all;
        }
        List<Data.Instance> fav = new ArrayList<>();
        List<Data.Instance> modded = new ArrayList<>();
        List<Data.Instance> normal = new ArrayList<>();
        for (Data.Instance ins : visible) {
            if (ins.favorite) fav.add(ins);
            else if (ins.loader != null && !"原版".equals(ins.loader) && !ins.loader.isEmpty()) modded.add(ins);
            else normal.add(ins);
        }
        addGroup(all, "收藏夹", fav);
        addGroup(all, "可安装 Mod", modded);
        addGroup(all, "未分类", normal);
        return all;
    }

    private void addGroup(VBox parent, String name, List<Data.Instance> list) {
        if (list.isEmpty()) return;
        Label header = Ui.bold(Lang.t(name) + " · " + list.size(), 12, Theme.gray2);
        VBox.setMargin(header, new Insets(7, 8, 3, 8));
        parent.getChildren().add(header);
        for (Data.Instance ins : list) parent.getChildren().add(instanceRow(ins));
    }

    private static boolean belongsTo(String folder, String gameDir) {
        if (folder == null || folder.isEmpty() || gameDir == null || gameDir.isEmpty()) return true;
        try {
            java.nio.file.Path f = java.nio.file.Paths.get(folder).toAbsolutePath().normalize();
            java.nio.file.Path g = java.nio.file.Paths.get(gameDir).toAbsolutePath().normalize();
            if (f.equals(g)) return true;
            if (!f.startsWith(g)) return false;
            String rel = g.relativize(f).toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            return rel.startsWith("tcl/instances/") || rel.equals("tcl/instances");
        } catch (Exception e) {
            return false;
        }
    }

    private List<Data.Instance> filtered() {
        List<Data.Instance> list = new ArrayList<>();
        for (Data.Instance ins : data.instances) {
            if (!belongsTo(ins.folder, data.gameDir)) continue;
            if (query.isEmpty() || ins.name.toLowerCase().contains(query)) {
                if (collectionFilter.isEmpty() || InstanceMeta.collections(ins).contains(collectionFilter)) {
                    list.add(ins);
                }
            }
        }
        switch (sortMode) {
            case 1 -> list.sort(Comparator.comparing(a -> a.name.toLowerCase()));
            case 2 -> list.sort((a, b) -> b.version.compareTo(a.version));
            case 3 -> list.sort((a, b) -> Long.compare(addedAtOf(b), addedAtOf(a)));
            default -> list.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        }
        return list;
    }

    // 旧配置没有 addedAt，退回 lastPlayed
    private static long addedAtOf(Data.Instance ins) {
        return ins.addedAt > 0 ? ins.addedAt : ins.lastPlayed;
    }

    private Node instanceRow(Data.Instance ins) {
        Node icon = Ui.image(ins.icon, 30, 30, true);
        Ui.ListItem item = new Ui.ListItem(icon, ins.name, ins.subtitle(), 46);
        StackPane more = Ui.iconButton("more-vertical", 26, 15, "更多", null);
        more.setOnMouseClicked(e -> {
            showInstanceMenu(more, ins);
            e.consume();
        });
        HBox.setMargin(more, new Insets(0, 4, 0, 0));
        item.addAction(more);
        item.setOnMouseClicked(e -> chooseInstance(ins));
        if (data.instances.indexOf(ins) == data.selectedInstance) item.setSelected(true);
        String tag = InstanceMeta.colorTag(ins);
        if (tag.isEmpty()) return item;
        javafx.scene.shape.Rectangle bar = new javafx.scene.shape.Rectangle(4, 28);
        bar.setArcWidth(4);
        bar.setArcHeight(4);
        bar.setFill(colorOf(tag));
        bar.setMouseTransparent(true);
        HBox host = new HBox(4, bar, item);
        host.setAlignment(Pos.CENTER_LEFT);
        HBox.setMargin(bar, new Insets(0, 0, 0, 2));
        HBox.setHgrow(item, Priority.ALWAYS);
        item.setMaxWidth(Double.MAX_VALUE);
        return host;
    }

    private static Color colorOf(String hex) {
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return Theme.gray3;
        }
    }

    private Node instanceCard(Data.Instance ins) {
        VBox card = new VBox(0);
        card.setPadding(Insets.EMPTY);
        card.setPrefSize(180, 108);
        card.setBackground(Ui.fill(Theme.cardBg, 9));
        card.setBorder(Ui.border(Theme.accentPale, 9, 1));
        card.setCursor(Cursor.HAND);
        VBox inner = new VBox(5);
        inner.setPadding(new Insets(12));
        StackPane iconBox = new StackPane(Ui.image(ins.icon, 38, 38, true));
        iconBox.setAlignment(Pos.CENTER_LEFT);
        Label name = Ui.bold(ins.name, 13, Theme.text);
        Label sub = Ui.label(ins.subtitle(), 11, Theme.gray2);
        sub.setWrapText(true);
        sub.setMaxWidth(156);
        inner.getChildren().addAll(iconBox, name, sub);
        String tag = InstanceMeta.colorTag(ins);
        if (!tag.isEmpty()) {
            Region bar = new Region();
            bar.setMinHeight(4);
            bar.setPrefHeight(4);
            bar.setMaxHeight(4);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setMouseTransparent(true);
            bar.setBackground(new Background(new BackgroundFill(colorOf(tag),
                    new CornerRadii(9, 9, 0, 0, false), Insets.EMPTY)));
            card.getChildren().add(bar);
        }
        card.getChildren().add(inner);
        card.setOnMouseClicked(e -> chooseInstance(ins));
        card.setOnMouseEntered(e -> card.setBackground(
                Ui.fill(Color.web("#ffffff", Math.min(0.92, Theme.componentOpacity + 0.3)), 9)));
        card.setOnMouseExited(e -> card.setBackground(Ui.fill(Theme.cardBg, 9)));
        return card;
    }

    private void chooseInstance(Data.Instance ins) {
        data.selectedInstance = Math.max(0, data.instances.indexOf(ins));
        data.save();
        if (selectMode) {
            win.toast("版本选择", Lang.t("已选择 ") + ins.name, 1);
            win.openPage("launch", true);
        } else if (data.getBool("UiHiddenFunctionInstanceModify", false)) {
            win.launchGame();
        } else {
            win.openInstanceSetup();
        }
    }

    private void showInstanceMenu(Node anchor, Data.Instance ins) {
        Ui.menuCard(anchor, instanceRows(anchor, ins).toArray(new Ui.MenuRow[0]));
    }

    // 一级菜单的行；二级菜单「返回」时原地重建同一份内容
    private List<Ui.MenuRow> instanceRows(Node anchor, Data.Instance ins) {
        List<Ui.MenuRow> rows = new ArrayList<>();
        rows.add(Ui.MenuRow.of("启动游戏", "play", () -> {
            data.selectedInstance = data.instances.indexOf(ins);
            data.save();
            win.launchGame();
        }));
        rows.add(Ui.MenuRow.separator());
        if (!data.getBool("UiHiddenFunctionInstanceModify", false)) {
            rows.add(Ui.MenuRow.of("实例设置", "settings", () -> {
                data.selectedInstance = data.instances.indexOf(ins);
                data.instanceSection = "overview";
                data.save();
                win.openInstanceSetup();
            }));
        }
        rows.add(Ui.MenuRow.of("重命名", "pencil", () -> renameInstance(ins)));
        rows.add(Ui.MenuRow.of("复制实例", "copy", () -> duplicateInstance(ins)));
        rows.add(Ui.MenuRow.of("备份为压缩包", "archive", () -> backupInstance(ins)));
        rows.add(Ui.MenuRow.of("导出整合包", "package", () -> exportInstance(ins)));
        rows.add(Ui.MenuRow.of("更换图标", "image", () -> changeIcon(ins)));
        rows.add(Ui.MenuRow.of("颜色标签", "palette", () -> Dialogs.colorTag(win, ins, this::refreshList)));
        rows.add(Ui.MenuRow.submenu("加入合集", "layers", () -> showCollectionMenu(Ui.activeMenu(), anchor, ins)));
        rows.add(Ui.MenuRow.of("打开实例文件夹", "folder-open", () -> openFolder(Isolation.run(ins).toString())));
        rows.add(Ui.MenuRow.of("创建桌面快捷方式", "external-link", () -> createInstanceShortcut(ins)));
        rows.add(Ui.MenuRow.of(ins.favorite ? "取消收藏" : "加入收藏夹", "heart", () -> {
            ins.favorite = !ins.favorite;
            data.save();
            rebuild();
        }));
        rows.add(Ui.MenuRow.separator());
        rows.add(Ui.MenuRow.of("删除", "trash-2", () -> Dialogs.confirm(win, "删除实例",
                Lang.tf("确定要删除实例「%s」吗？\n只会从列表中移除，不会删除磁盘文件。", ins.name), () -> {
                    data.instances.remove(ins);
                    data.selectedInstance = 0;
                    data.save();
                    rebuild();
                    win.toast("实例已删除", ins.name, 1);
                }, true)));
        return rows;
    }

    private void showCollectionMenu(javafx.stage.Popup popup, Node anchor, Data.Instance ins) {
        VBox content = new VBox(0);
        List<String> all = InstanceMeta.CollectionsStore.listAll();
        List<String> owned = InstanceMeta.collections(ins);
        if (all.isEmpty()) {
            Label empty = Ui.label("还没有合集，先新建一个吧。", 12, Theme.gray2);
            empty.setWrapText(true);
            empty.setMaxWidth(190);
            VBox.setMargin(empty, new Insets(6, 10, 8, 10));
            content.getChildren().add(empty);
        }
        for (String name : all) {
            Ui.Check check = Ui.checkBox(name, owned.contains(name));
            VBox.setMargin(check, new Insets(6, 12, 6, 12));
            check.setOnAction(() -> {
                if (check.isSelected()) InstanceMeta.addTo(ins, name);
                else InstanceMeta.removeFrom(ins, name);
                refreshList();
            });
            content.getChildren().add(check);
        }
        Ui.ListItem create = new Ui.ListItem(Theme.icon("circle-plus", 16, Theme.text), "新建合集…", null, 34);
        create.setOnMouseClicked(e -> {
            Ui.closeMenuCard(popup);
            Dialogs.input(win, "新建合集", "实例会加入这个新合集。", "", name -> {
                if (name == null) return;
                String error = InstanceMeta.CollectionsStore.validateName(name, "");
                if (!error.isEmpty()) {
                    win.toast("新建失败", error, 2);
                    return;
                }
                InstanceMeta.CollectionsStore.ensure(name);
                InstanceMeta.addTo(ins, name);
                refreshList();
                win.toast("已加入合集", name, 1);
            });
        });
        content.getChildren().add(create);
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setBackground(Ui.fill(Theme.gray6, 0));
        VBox.setMargin(sep, new Insets(5, 8, 5, 8));
        content.getChildren().add(sep);
        content.getChildren().add(backRow(popup, anchor, ins));
        Ui.swapMenuCard(popup, content, 210);
    }

    // 返回行：同一张卡片里换回一级菜单，不关窗、不重播动画
    private Ui.ListItem backRow(javafx.stage.Popup popup, Node anchor, Data.Instance ins) {
        Ui.ListItem back = new Ui.ListItem(Theme.icon("arrow-left", 16, Theme.text), "返回", null, 34);
        back.setOnMouseClicked(e -> Ui.swapMenuCard(popup,
                Ui.menuRows(instanceRows(anchor, ins).toArray(new Ui.MenuRow[0])), 210));
        return back;
    }

    private void renameInstance(Data.Instance ins) {
        Dialogs.input(win, "重命名实例", "新的实例名称", ins.name, name -> {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) return;
            for (Data.Instance other : data.instances) {
                if (other != ins && n.equalsIgnoreCase(other.name)) {
                    win.toast("名称重复", "已经有同名实例了。", 2);
                    return;
                }
            }
            ins.name = n;
            data.save();
            rebuild();
            win.toast("已重命名", n, 1);
        });
    }

    private void changeIcon(Data.Instance ins) {
        FileChooser chooser = new FileChooser();
        // OS 对话框不会经过 localize，创建处直接取翻译
        chooser.setTitle(Lang.t("选择实例图标"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("PNG 图片"), "*.png"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        try {
            String safe = ins.name == null || ins.name.isEmpty() ? "instance" : ins.name;
            for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
            java.nio.file.Path dir = Theme.iconDir();
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(safe + ".png");
            java.nio.file.Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ins.icon = "Icons/" + safe + ".png";
            data.save();
            rebuild();
            win.toast("图标已更换", ins.name, 1);
        } catch (Exception e) {
            Errors.error("更换图标失败", e);
        }
    }

    private void duplicateInstance(Data.Instance ins) {
        String defaultName = ins.name + "-副本";
        Dialogs.input(win, "复制实例", "新实例会得到独立的版本文件、Mod 与存档。", defaultName, name -> {
            if (name == null) return;
            String error = validateInstanceName(name, ins.folder);
            if (!error.isEmpty()) {
                win.toast("无法复制实例", error, 2);
                return;
            }
            java.nio.file.Path base = java.nio.file.Paths.get(
                    ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir);
            java.nio.file.Path src = base.resolve("versions").resolve(ins.version);
            java.nio.file.Path dest = base.resolve("versions").resolve(name);
            final String targetFolder = base.toString();
            final boolean isolated = Isolation.on(ins);
            win.toast("正在复制实例", ins.name + " → " + name, 0);
            Thread worker = new Thread(() -> {
                try {
                    copyDirectory(src, dest);
                    rewriteVersionJson(dest, ins.version, name);
                    javafx.application.Platform.runLater(() -> {
                        Data.Instance copy = new Data.Instance(name, name, ins.loader, ins.loaderVersion,
                                ins.description, ins.icon, System.currentTimeMillis(), false, targetFolder,
                                isolated ? Isolation.ON : Isolation.OFF);
                        copy.addedAt = System.currentTimeMillis();
                        data.instances.add(copy);
                        data.selectedInstance = data.instances.size() - 1;
                        data.save();
                        data.log("[TCL] 已复制实例：" + src + " → " + dest);
                        rebuild();
                        win.toast("实例已复制为", name, 1);
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> Errors.error("复制实例失败", e));
                }
            }, "TCL-InstanceDuplicate");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static int copyDirectory(java.nio.file.Path src, java.nio.file.Path dest) throws Exception {
        if (!java.nio.file.Files.isDirectory(src)) {
            throw new java.io.FileNotFoundException("版本目录不存在：" + src);
        }
        final int[] count = {0};
        try (var walk = java.nio.file.Files.walk(src)) {
            walk.forEach(p -> {
                try {
                    java.nio.file.Path target = dest.resolve(src.relativize(p).toString());
                    if (java.nio.file.Files.isDirectory(p)) {
                        java.nio.file.Files.createDirectories(target);
                    } else {
                        java.nio.file.Files.createDirectories(target.getParent());
                        java.nio.file.Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        count[0]++;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return count[0];
    }

    static void rewriteVersionJson(java.nio.file.Path dir, String oldId, String newId) throws Exception {
        java.nio.file.Path oldJson = dir.resolve(oldId + ".json");
        if (!java.nio.file.Files.isRegularFile(oldJson)) {
            try (var list = java.nio.file.Files.list(dir)) {
                List<java.nio.file.Path> candidates = list
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                        .toList();
                if (candidates.size() != 1) return;
                oldJson = candidates.get(0);
            }
        }
        String text = java.nio.file.Files.readString(oldJson, java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\"id\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"").matcher(text);
        String updated = m.find()
                ? m.replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(Json.quote(newId)))
                : text;
        java.nio.file.Path newJson = dir.resolve(newId + ".json");
        java.nio.file.Files.writeString(newJson, updated, java.nio.charset.StandardCharsets.UTF_8);
        if (!oldJson.equals(newJson)) java.nio.file.Files.delete(oldJson);
    }

    private String validateInstanceName(String name, String folder) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return Lang.t("名称不能为空。");
        if (trimmed.length() > 64) return Lang.t("名称不能超过 64 个字符。");
        for (char c : "\\/:*?\"<>|".toCharArray()) {
            if (trimmed.indexOf(c) >= 0) return Lang.tf("名称不能包含字符：%s", c);
        }
        if (trimmed.equals(".") || trimmed.equals("..")) return Lang.t("名称不合法。");
        String base = folder != null && !folder.isEmpty() ? folder : data.gameDir;
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(base, "versions", trimmed))) {
            return Lang.t("已存在同名实例。");
        }
        for (Data.Instance other : data.instances) {
            if (trimmed.equalsIgnoreCase(other.name)) return Lang.t("已存在同名实例。");
        }
        return "";
    }

    private void exportInstance(Data.Instance ins) {
        data.selectedInstance = data.instances.indexOf(ins);
        data.instanceSection = "export";
        data.save();
        win.openInstanceSetup();
    }

    private void createInstanceShortcut(Data.Instance ins) {
        // PowerShell 要等进程返回，留在 FX 线程会把整个界面卡住
        Threads.start("TCL-Shortcut", () -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
                boolean windows = os.contains("win");
                boolean mac = os.contains("mac");
                java.nio.file.Path script = Resources.launchTarget();
                // mac 裸 jar 无脚本，用当前 java 直接拉起
                java.nio.file.Path jar = script == null && mac ? Resources.jar() : null;
                if (script == null && jar == null) {
                    // 异常消息最终进 Errors 报告弹窗，Errors UI 不经过 localize
                    throw new Exception(Lang.t("找不到启动器程序（TCraftLauncher.exe / TCraftLauncher.sh），请确认使用的是完整版程序。"));
                }
                java.nio.file.Path appDir = script != null && script.getParent() != null ? script.getParent()
                        : jar != null && jar.getParent() != null ? jar.getParent() : Theme.root();
                if (!windows && script != null) script.toFile().setExecutable(true);
                java.nio.file.Path desktop = Shell.desktopDir();
                java.nio.file.Files.createDirectories(desktop);
                String baseName = "启动 " + safeFileName(ins.name);
                java.nio.file.Path link;
                if (windows) {
                    link = uniquePath(desktop, baseName, ".lnk");
                    java.nio.file.Path icon = Resources.iconFile("icon.ico");
                    String ps = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                            + "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + q(link.toString()) + "');"
                            + "$s.TargetPath='" + q(script.toString()) + "';"
                            + "$s.Arguments='--launch-instance \"" + q(ins.name) + "\"';"
                            + "$s.WorkingDirectory='" + q(appDir.toString()) + "';"
                            + (icon == null ? "" : "$s.IconLocation='" + q(icon.toString()) + "';")
                            + "$s.Description='启动 " + q(ins.name) + " - TCraft Launcher';"
                            + "$s.WindowStyle=1;$s.Save()";
                    ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                            "-Command", ps);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (java.io.InputStream in = p.getInputStream()) {
                        // 先等退出再读输出，防挂死拖死线程
                        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                            p.destroyForcibly();
                            throw new Exception("WScript.Shell 创建快捷方式超时");
                        }
                        String output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        if (p.exitValue() != 0 || !java.nio.file.Files.isRegularFile(link)) {
                            throw new Exception("WScript.Shell 未能生成快捷方式："
                                    + (output.isBlank() ? "无输出" : output.trim()));
                        }
                    }
                } else if (mac) {
                    link = uniquePath(desktop, baseName, ".command");
                    // exec 不带斜杠只查 PATH 不看当前目录，必须写绝对路径
                    String run = script != null
                            ? shellQuote(script.toAbsolutePath().toString())
                            : shellQuote(ProcessHandle.current().info().command().orElse("java"))
                                    + " -jar " + shellQuote(jar.toAbsolutePath().toString());
                    java.nio.file.Files.writeString(link,
                            "#!/bin/sh\ncd " + shellQuote(appDir.toString())
                                    + "\nexec " + run
                                    + " --launch-instance " + shellQuote(ins.name) + "\n",
                            java.nio.charset.StandardCharsets.UTF_8);
                    link.toFile().setExecutable(true);
                } else {
                    link = uniquePath(desktop, baseName, ".desktop");
                    java.nio.file.Path icon = Resources.iconFile("icon-256.png");
                    String entry = "[Desktop Entry]\nType=Application\nName=" + baseName + "\n"
                            + "Exec=" + desktopQuote(script.toString()) + " --launch-instance "
                            + desktopQuote(ins.name) + "\n"
                            + "Path=" + appDir.toString() + "\n"
                            + (icon == null ? "" : "Icon=" + icon.toString() + "\n")
                            + "Terminal=false\nCategories=Game;\n";
                    java.nio.file.Files.writeString(link, entry, java.nio.charset.StandardCharsets.UTF_8);
                    link.toFile().setExecutable(true);
                }
                javafx.application.Platform.runLater(() -> win.toast("快捷方式已创建", link.toString(), 1));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> Errors.error("创建实例快捷方式失败", e));
            }
        });
    }

    private static String safeFileName(String name) {
        String result = name == null ? "" : name.trim();
        for (char c : "\\/:*?\"<>|".toCharArray()) result = result.replace(c, '_');
        return result.isEmpty() ? "实例" : result;
    }

    private static java.nio.file.Path uniquePath(java.nio.file.Path dir, String base, String ext) {
        java.nio.file.Path candidate = dir.resolve(base + ext);
        int index = 2;
        while (java.nio.file.Files.exists(candidate) && index <= 999) {
            candidate = dir.resolve(base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }

    private static String q(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    private static String shellQuote(String s) {
        return "'" + (s == null ? "" : s.replace("'", "'\\''")) + "'";
    }

    private static String desktopQuote(String s) {
        String value = s == null ? "" : s;
        value = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("`", "\\`").replace("$", "\\$");
        return "\"" + value + "\"";
    }

    private void backupInstance(Data.Instance ins) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("备份为压缩包"));
        chooser.setInitialFileName(ins.name + "-备份-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".zip");
        // 过滤器描述显示在 OS 对话框里
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("压缩包"), "*.zip"));
        File out = chooser.showSaveDialog(win.stage);
        if (out == null) return;
        java.nio.file.Path src = java.nio.file.Paths.get(ins.folder);
        if (!java.nio.file.Files.isDirectory(src)) {
            win.toast("备份失败", Lang.t("实例目录不存在：") + ins.folder, 2);
            return;
        }
        win.toast("开始备份", ins.name + " → " + out.getName(), 1);
        Thread worker = new Thread(() -> {
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(out.toPath())));
                 var walk = java.nio.file.Files.walk(src)) {
                walk.filter(java.nio.file.Files::isRegularFile).forEach(f -> {
                    try {
                        String rel = src.relativize(f).toString().replace('\\', '/');
                        zos.putNextEntry(new java.util.zip.ZipEntry(rel));
                        java.nio.file.Files.copy(f, zos);
                        zos.closeEntry();
                    } catch (Exception e) {
                        data.log("备份跳过文件：" + f + " " + e);
                    }
                });
                javafx.application.Platform.runLater(() -> win.toast("备份完成", out.getName(), 1));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> Errors.error("备份实例失败", e));
            }
        }, "TCL-Backup");
        worker.setDaemon(true);
        worker.start();
    }

    public static Node buildFolderPanel(MainWindow win) {
        Data data = Data.get();
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(16, 12, 14, 12));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);

        Label folderHead = Ui.label("文件夹列表", 12, Theme.gray2);
        folderHead.setOpacity(0.85);
        VBox.setMargin(folderHead, new Insets(0, 0, 8, 4));
        panel.getChildren().add(folderHead);

        for (Data.FolderItem folder : data.folders) {
            boolean current = folder.path.equals(data.gameDir);
            Ui.ListItem item = new Ui.ListItem(
                    Theme.icon(current ? "folder-open" : "folder", 18, current ? Theme.accent : Theme.text),
                    folder.name, folder.path, 40);
            item.setSelected(current);
            Ui.tooltip(item, folder.path);
            item.setOnMouseClicked(e -> {
                if (!folder.path.equals(data.gameDir)) {
                    data.gameDir = folder.path;
                    data.save();
                    win.openPage(win.currentKey, false);
                    win.toast("已切换文件夹", folder.path, 1);
                }
            });
            VBox.setMargin(item, new Insets(0, 0, 2, 0));
            panel.getChildren().add(item);
        }

        Label addHead = Ui.label("添加或导入", 12, Theme.gray2);
        addHead.setOpacity(0.85);
        VBox.setMargin(addHead, new Insets(18, 0, 6, 4));
        panel.getChildren().add(addHead);

        Ui.ListItem addFolder = new Ui.ListItem(Theme.icon("folder-plus", 17, Theme.text), "添加游戏目录", "导入现有的 .minecraft", 38);
        Ui.tooltip(addFolder, "将一个已有的 Minecraft 文件夹添加到列表，也可以直接把文件夹拖进窗口");
        addFolder.setOnMouseClicked(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(Lang.t("选择 .minecraft 文件夹"));
            File file = chooser.showDialog(win.stage);
            if (file != null) addGameFolder(win, file.toPath());
        });
        Ui.ListItem importPack = new Ui.ListItem(Theme.icon("package-plus", 17, Theme.text), "导入整合包", "支持 .zip / .mrpack，可直接拖入窗口", 38);
        Ui.tooltip(importPack, "在当前选择的 Minecraft 文件夹下安装整合包");
        importPack.setOnMouseClicked(e -> importModpack(win));
        panel.getChildren().addAll(addFolder, importPack);

        Label note = Ui.label(Lang.t("游戏目录") + "\n" + data.gameDir, 10.5, Theme.gray3);
        note.setWrapText(true);
        note.setMaxWidth(240);
        VBox.setMargin(note, new Insets(14, 4, 8, 4));
        panel.getChildren().add(note);
        ScrollPane scroll = Ui.scroll(panel);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static String scanVersions(Data data, java.nio.file.Path gameDir) {
        java.nio.file.Path versions = gameDir.resolve("versions");
        if (!java.nio.file.Files.isDirectory(versions)) return "";
        String folderKey = gameDir.toAbsolutePath().normalize().toString();
        try (var stream = java.nio.file.Files.list(versions)) {
            stream.filter(java.nio.file.Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                if (!java.nio.file.Files.isRegularFile(dir.resolve(name + ".json"))) return;
                boolean exists = false;
                for (Data.Instance i : data.instances) {
                    String folder = i.folder == null || i.folder.isEmpty() ? "" : java.nio.file.Paths.get(i.folder)
                            .toAbsolutePath().normalize().toString();
                    if (folder.equalsIgnoreCase(folderKey)
                            && (i.name.equals(name) || name.equals(i.version))) exists = true;
                }
                if (exists) return;
                String lower = name.toLowerCase();
                String loader = lower.contains("fabric") ? "Fabric" : lower.contains("forge") ? "Forge"
                        : lower.contains("neoforge") ? "NeoForge" : lower.contains("optifine") ? "OptiFine" : "原版";
                String icon = "Fabric".equals(loader) ? "Fabric.png" : "Forge".equals(loader) ? "CobbleStone.png"
                        : "NeoForge".equals(loader) ? "NeoForge.png" : "Grass.png";
                Data.Instance imported = new Data.Instance(name, name, loader, "",
                        "已导入的版本", icon, System.currentTimeMillis(), false, folderKey);
                imported.addedAt = System.currentTimeMillis();
                data.instances.add(imported);
            });
        } catch (Exception e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return "";
    }

    static void importModpack(MainWindow win) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择整合包"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Lang.t("Minecraft 整合包"), "*.zip", "*.mrpack"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        importModpack(win, file.toPath());
    }

    static void importModpack(MainWindow win, java.nio.file.Path packPath) {
        File file = packPath.toFile();
        win.toast("正在预读整合包", file.getName(), 0);
        Thread thread = new Thread(() -> {
            Modpacks.PackInfo info;
            try {
                info = Modpacks.readInfo(packPath);
            } catch (Throwable t) {
                // 深层嵌套可抛 SOE，须兜底防 UI 卡预读
                javafx.application.Platform.runLater(() ->
                        Dialogs.confirm(win, "预读整合包失败",
                                Lang.tf("读取整合包时出错：%s", t.getMessage() == null ? t.toString() : t.getMessage()),
                                () -> {
                                }));
                return;
            }
            javafx.application.Platform.runLater(() -> {
                if (!info.valid) {
                    // 前缀判断随语言变化，须与「无法识别」串恒等比较；空串同未识别
                    boolean broken = info.readError != null && !info.readError.isEmpty()
                            && !info.readError.equals(Modpacks.unrecognizedError());
                    Dialogs.confirm(win, broken ? "整合包读取失败" : "无法识别整合包",
                            broken ? info.readError
                                    : "无法从该压缩包中识别出整合包清单。\n当前支持 Modrinth (.mrpack)、CurseForge (.zip)、MultiMC、HMCL 与 MCBBS 格式。",
                            () -> {
                            });
                    return;
                }
                // 拼接整串不会命中表键，各标签段创建时逐段翻译
                StringBuilder sb = new StringBuilder();
                sb.append(Lang.t("类型：")).append(info.typeName).append("\n");
                sb.append(Lang.t("名称：")).append(info.name).append("\n");
                sb.append(Lang.t("游戏版本：")).append(info.mcVersion).append("\n");
                sb.append(Lang.t("加载器：")).append(info.loader.isEmpty() ? Lang.t("无") : info.loader + " " + info.loaderVersion);
                if (!info.unsupportedLoaders.isEmpty()) {
                    Dialogs.confirm(win, "暂不支持的加载器",
                            Lang.t("该整合包使用了 TCL 不支持的加载器：") + String.join("、", info.unsupportedLoaders)
                                    + Lang.t("\n无法完成安装。"), () -> {
                            });
                    return;
                }
                sb.append(Lang.t("\n\n是否开始安装？"));
                Dialogs.confirm(win, "导入整合包", sb.toString(), () ->
                        Dialogs.input(win, "输入版本名称", "", info.name, name -> {
                            if (name == null) return;
                            Modpacks.installLocal(win, packPath, info, name);
                        }));
            });
        }, "TCL-Modpack-Preview");
        thread.setDaemon(true);
        thread.start();
    }

    static void addGameFolder(MainWindow win, java.nio.file.Path dragged) {
        Data data = Data.get();
        java.nio.file.Path target = resolveGameDir(dragged);
        if (target == null) {
            win.toast("无法识别游戏目录", "请选择包含 .minecraft、versions 或 mods/saves 的文件夹。", 2);
            return;
        }
        String path = target.toAbsolutePath().normalize().toString();
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Data.FolderItem existing = null;
        for (Data.FolderItem folder : data.folders) {
            String known = java.nio.file.Paths.get(folder.path).toAbsolutePath().normalize().toString();
            // Windows 路径大小写不敏感，其他平台必须精确相等
            if (windows ? known.equalsIgnoreCase(path) : known.equals(path)) {
                existing = folder;
                break;
            }
        }
        if (existing == null) {
            String name = target.getFileName() == null ? "游戏目录" : target.getFileName().toString();
            data.folders.add(new Data.FolderItem(name, path));
        }
        data.gameDir = path;
        String scanError = scanVersions(data, target);
        data.save();
        win.openPage(win.currentKey, false);
        if (scanError.isEmpty()) {
            win.toast(existing == null ? "已添加游戏目录" : "已切换游戏目录", path, 1);
        } else {
            win.toast(Lang.tf("版本扫描失败：%s", scanError), path, 2);
        }
    }

    private static java.nio.file.Path resolveGameDir(java.nio.file.Path path) {
        if (path == null || !java.nio.file.Files.isDirectory(path)) return null;
        if (java.nio.file.Files.isDirectory(path.resolve("versions"))) return path;
        java.nio.file.Path nested = path.resolve(".minecraft");
        if (java.nio.file.Files.isDirectory(nested.resolve("versions"))) return nested;
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (".minecraft".equalsIgnoreCase(name)) return path;
        if (java.nio.file.Files.isRegularFile(path.resolve("options.txt"))
                || java.nio.file.Files.isRegularFile(path.resolve("launcher_profiles.json"))
                || java.nio.file.Files.isDirectory(path.resolve("mods"))
                || java.nio.file.Files.isDirectory(path.resolve("saves"))) {
            return path;
        }
        return null;
    }

    private static void openFolder(String path) {
        Shell.openFolder(path);
    }
}
