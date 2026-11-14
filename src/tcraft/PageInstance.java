package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 实例页，左文件夹右列表，支持网格视图。 */
public class PageInstance implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private final boolean selectMode;
    private boolean gridView;
    private String query = "";
    private int sortMode = 0;
    /** 当前合集筛选，空串表示全部。 */
    private String collectionFilter = "";
    private HBox collectionChips;
    private ScrollPane collectionBar;

    public PageInstance(MainWindow win) {
        this(win, false);
    }

    public PageInstance(MainWindow win, boolean selectMode) {
        this.win = win;
        this.selectMode = selectMode;
        // 视图模式和排序存配置
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
            refreshList();
        });
        ComboBox<String> sort = Ui.combo(new String[]{"最近游玩", "名称 A→Z", "版本 新→旧", "安装时间"}, sortMode);
        sort.setPrefWidth(116);
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
        // 搜索框自带卡底，别再包圆角容器
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMinWidth(120);
        search.setPrefWidth(260);
        toolbar.getChildren().addAll(search, sort, toolIcons);

        // 没有合集时整条隐藏
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

        StackPane listBack = new StackPane();
        listBack.setBackground(Ui.fill(Theme.cardBg, 8));
        listBack.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(listBack);
        listBack.getProperties().put("scroll", scroll);

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

    private void refreshList() {
        if (refreshRunner != null) refreshRunner.run();
    }

    /** 重建合集标签栏。 */
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

    /** 一个合集标签，value 为空表示全部。 */
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
            Ui.tooltip(chip, value + "（右键可重命名或删除）");
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
                "确定要删除合集「" + name + "」吗？\n只会删掉这个标签，实例本身与其文件不受影响。",
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
        Label header = Ui.bold(name + " · " + list.size(), 12, Theme.gray2);
        VBox.setMargin(header, new Insets(7, 8, 3, 8));
        parent.getChildren().add(header);
        for (Data.Instance ins : list) parent.getChildren().add(instanceRow(ins));
    }

    /** 实例属于当前游戏目录或它的隔离子目录。 */
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
            case 3 -> list.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
            default -> list.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        }
        return list;
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
        // 颜色标签：左侧 4px 竖条
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

    /** 十六进制转 Color，非法值退回灰色。 */
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
        // 颜色标签：卡片顶部一条 4px 横条
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
            win.toast("版本选择", "已选择 " + ins.name, 1);
            win.openPage("launch", true);
        } else {
            win.openInstanceSetup();
        }
    }

    /** 实例「更多」菜单。 */
    private void showInstanceMenu(Node anchor, Data.Instance ins) {
        Ui.menuCard(anchor,
                Ui.MenuRow.of("启动游戏", "play", () -> {
                    data.selectedInstance = data.instances.indexOf(ins);
                    data.save();
                    win.launchGame();
                }),
                Ui.MenuRow.separator(),
                Ui.MenuRow.of("实例设置", "settings", () -> {
                    data.selectedInstance = data.instances.indexOf(ins);
                    data.instanceSection = "overview";
                    data.save();
                    win.openInstanceSetup();
                }),
                Ui.MenuRow.of("重命名", "pencil", () -> renameInstance(ins)),
                Ui.MenuRow.of("复制实例", "copy", () -> duplicateInstance(ins)),
                Ui.MenuRow.of("备份为压缩包", "archive", () -> backupInstance(ins)),
                Ui.MenuRow.of("导出整合包", "package", () -> exportInstance(ins)),
                Ui.MenuRow.of("更换图标", "image", () -> changeIcon(ins)),
                Ui.MenuRow.of("颜色标签", "palette", () -> openSubMenu(() -> showColorMenu(anchor, ins))),
                Ui.MenuRow.of("加入合集", "layers", () -> openSubMenu(() -> showCollectionMenu(anchor, ins))),
                Ui.MenuRow.of("打开实例文件夹", "folder-open", () -> openFolder(ins.folder)),
                Ui.MenuRow.of("创建桌面快捷方式", "external-link", () -> createInstanceShortcut(ins)),
                Ui.MenuRow.of(ins.favorite ? "取消收藏" : "加入收藏夹", "heart", () -> {
                    ins.favorite = !ins.favorite;
                    data.save();
                    rebuild();
                }),
                Ui.MenuRow.separator(),
                Ui.MenuRow.of("删除", "trash-2", () -> Dialogs.confirm(win, "删除实例",
                        "确定要删除实例「" + ins.name + "」吗？\n只会从列表中移除，不会删除磁盘文件。", () -> {
                            data.instances.remove(ins);
                            data.selectedInstance = 0;
                            data.save();
                            rebuild();
                            win.toast("实例已删除", ins.name, 1);
                        }, true)));
    }

    /** 等一级卡片收起再开二级卡片。 */
    private void openSubMenu(Runnable action) {
        javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
        wait.setOnFinished(e -> action.run());
        wait.play();
    }

    /** 颜色标签二级卡片。 */
    private void showColorMenu(Node anchor, Data.Instance ins) {
        VBox content = new VBox(0);
        Label head = Ui.label("颜色标签", 12, Theme.gray2);
        head.setOpacity(0.6);
        VBox.setMargin(head, new Insets(4, 10, 2, 10));
        FlowPane wrap = new FlowPane(6, 6);
        wrap.setPadding(new Insets(2, 6, 6, 6));
        final javafx.stage.Popup[] holder = new javafx.stage.Popup[1];
        for (String[] preset : InstanceMeta.COLOR_TAGS) {
            StackPane dot = new StackPane();
            dot.setPrefSize(30, 30);
            dot.setMinSize(30, 30);
            dot.setMaxSize(30, 30);
            dot.setBackground(Ui.fill(colorOf(preset[1]), 15));
            dot.setCursor(Cursor.HAND);
            Ui.tooltip(dot, preset[0]);
            dot.setOnMouseClicked(e -> {
                Ui.closeMenuCard(holder[0]);
                InstanceMeta.setColorTag(ins, preset[1]);
                refreshList();
                win.toast("已设置颜色标签", preset[0], 1);
            });
            wrap.getChildren().add(dot);
        }
        content.getChildren().addAll(head, wrap);
        Ui.ListItem clear = new Ui.ListItem(Theme.icon("x", 16, Theme.text), "清除标签", null, 34);
        clear.setOnMouseClicked(e -> {
            Ui.closeMenuCard(holder[0]);
            InstanceMeta.setColorTag(ins, "");
            refreshList();
            win.toast("已清除颜色标签", ins.name, 1);
        });
        content.getChildren().add(clear);
        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setBackground(Ui.fill(Theme.gray6, 0));
        VBox.setMargin(sep, new Insets(5, 8, 5, 8));
        content.getChildren().add(sep);
        content.getChildren().add(backRow(holder, anchor, ins));
        holder[0] = Ui.menuCardNode(anchor, content, 232);
    }

    /** 加入合集二级卡片。 */
    private void showCollectionMenu(Node anchor, Data.Instance ins) {
        VBox content = new VBox(0);
        List<String> all = InstanceMeta.CollectionsStore.listAll();
        List<String> owned = InstanceMeta.collections(ins);
        final javafx.stage.Popup[] holder = new javafx.stage.Popup[1];
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
            Ui.closeMenuCard(holder[0]);
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
        content.getChildren().add(backRow(holder, anchor, ins));
        holder[0] = Ui.menuCardNode(anchor, content, 232);
    }

    /** 返回行，收起当前卡片再开一级。 */
    private Ui.ListItem backRow(javafx.stage.Popup[] holder, Node anchor, Data.Instance ins) {
        Ui.ListItem back = new Ui.ListItem(Theme.icon("arrow-left", 16, Theme.text), "返回", null, 34);
        back.setOnMouseClicked(e -> {
            Ui.closeMenuCard(holder[0]);
            javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
            wait.setOnFinished(x -> showInstanceMenu(anchor, ins));
            wait.play();
        });
        return back;
    }

    /** 只改记录名，不动磁盘目录。 */
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

    /** 换图标，按实例名复制进 res/images/Icons。 */
    private void changeIcon(Data.Instance ins) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择实例图标");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        try {
            String safe = ins.name == null || ins.name.isEmpty() ? "instance" : ins.name;
            for (char c : "\\/:*?\"<>|".toCharArray()) safe = safe.replace(String.valueOf(c), "_");
            java.nio.file.Path dir = Theme.res("images/Icons");
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

    /** 复制实例并重写版本 json 的 id。 */
    private void duplicateInstance(Data.Instance ins) {
        String defaultName = ins.name + "-副本";
        Dialogs.input(win, "复制实例", "新实例会得到独立的版本文件、Mod 与存档。", defaultName, name -> {
            if (name == null) return;
            String error = validateInstanceName(name, ins.folder);
            if (!error.isEmpty()) {
                win.toast("无法复制实例", error, 2);
                return;
            }
            java.nio.file.Path root = java.nio.file.Paths.get(
                    ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir, "versions");
            java.nio.file.Path src = root.resolve(ins.version);
            java.nio.file.Path dest = root.resolve(name);
            final String targetFolder = ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir;
            win.toast("正在复制实例", ins.name + " → " + name, 0);
            Thread worker = new Thread(() -> {
                try {
                    copyDirectory(src, dest);
                    rewriteVersionJson(dest, ins.version, name);
                    javafx.application.Platform.runLater(() -> {
                        Data.Instance copy = new Data.Instance(name, name, ins.loader, ins.loaderVersion,
                                ins.description, ins.icon, System.currentTimeMillis(), false, targetFolder);
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

    /** 复制整个目录，返回文件数。 */
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

    /** 版本 json 改名并把 id 改成新实例名。 */
    private static void rewriteVersionJson(java.nio.file.Path dir, String oldId, String newId) throws Exception {
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

    /** 校验新实例名。 */
    private String validateInstanceName(String name, String folder) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "名称不能为空。";
        if (trimmed.length() > 64) return "名称不能超过 64 个字符。";
        for (char c : "\\/:*?\"<>|".toCharArray()) {
            if (trimmed.indexOf(c) >= 0) return "名称不能包含字符：" + c;
        }
        if (trimmed.equals(".") || trimmed.equals("..")) return "名称不合法。";
        String base = folder != null && !folder.isEmpty() ? folder : data.gameDir;
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(base, "versions", trimmed))) {
            return "已存在同名实例。";
        }
        for (Data.Instance other : data.instances) {
            if (trimmed.equalsIgnoreCase(other.name)) return "已存在同名实例。";
        }
        return "";
    }

    /** 跳到实例设置的导出页。 */
    private void exportInstance(Data.Instance ins) {
        data.selectedInstance = data.instances.indexOf(ins);
        data.instanceSection = "export";
        data.save();
        win.openInstanceSetup();
    }

    /** 建直接启动该实例的桌面快捷方式。 */
    private void createInstanceShortcut(Data.Instance ins) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            boolean windows = os.contains("win");
            boolean mac = os.contains("mac");
            java.nio.file.Path root = Theme.root();
            java.nio.file.Path script = root.resolve(windows ? "TCraftLauncher.bat" : "TCraftLauncher.sh");
            if (!java.nio.file.Files.isRegularFile(script)) {
                throw new Exception("找不到启动脚本 " + script + "，请确认使用的是完整版程序。");
            }
            java.nio.file.Path desktop = java.nio.file.Paths.get(
                    System.getProperty("user.home"), "Desktop");
            java.nio.file.Files.createDirectories(desktop);
            String baseName = "启动 " + safeFileName(ins.name);
            java.nio.file.Path link;
            if (windows) {
                link = uniquePath(desktop, baseName, ".lnk");
                String icon = root.resolve("res").resolve("images").resolve("icon.ico").toString();
                String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('" + q(link.toString()) + "');"
                        + "$s.TargetPath='" + q(script.toString()) + "';"
                        + "$s.Arguments='--launch-instance \"" + q(ins.name) + "\"';"
                        + "$s.WorkingDirectory='" + q(root.toString()) + "';"
                        + "$s.IconLocation='" + q(icon) + "';"
                        + "$s.Description='启动 " + q(ins.name) + " - TCraft Launcher';"
                        + "$s.WindowStyle=1;$s.Save()";
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                        "-Command", ps);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (p.waitFor() != 0 || !java.nio.file.Files.isRegularFile(link)) {
                    throw new Exception("WScript.Shell 未能生成快捷方式："
                            + (output.isBlank() ? "无输出" : output.trim()));
                }
            } else if (mac) {
                link = uniquePath(desktop, baseName, ".command");
                java.nio.file.Files.writeString(link,
                        "#!/bin/sh\ncd " + shellQuote(root.toString())
                                + "\nexec ./TCraftLauncher.sh --launch-instance " + shellQuote(ins.name) + "\n",
                        java.nio.charset.StandardCharsets.UTF_8);
                link.toFile().setExecutable(true);
            } else {
                link = uniquePath(desktop, baseName, ".desktop");
                String entry = "[Desktop Entry]\nType=Application\nName=" + baseName + "\n"
                        + "Exec=" + desktopQuote(script.toString()) + " --launch-instance "
                        + desktopQuote(ins.name) + "\n"
                        + "Path=" + root + "\n"
                        + "Icon=" + root.resolve("res").resolve("images").resolve("icon-256.png") + "\n"
                        + "Terminal=false\nCategories=Game;\n";
                java.nio.file.Files.writeString(link, entry, java.nio.charset.StandardCharsets.UTF_8);
                link.toFile().setExecutable(true);
            }
            win.toast("快捷方式已创建", link.toString(), 1);
        } catch (Exception e) {
            Errors.error("创建实例快捷方式失败", e);
        }
    }

    /** 非法文件名字符换成下划线。 */
    private static String safeFileName(String name) {
        String result = name == null ? "" : name.trim();
        for (char c : "\\/:*?\"<>|".toCharArray()) result = result.replace(c, '_');
        return result.isEmpty() ? "实例" : result;
    }

    /** 同名文件依次加序号。 */
    private static java.nio.file.Path uniquePath(java.nio.file.Path dir, String base, String ext) {
        java.nio.file.Path candidate = dir.resolve(base + ext);
        int index = 2;
        while (java.nio.file.Files.exists(candidate) && index <= 999) {
            candidate = dir.resolve(base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }

    /** PowerShell 单引号字面量转义。 */
    private static String q(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** POSIX shell 单引号字面量转义。 */
    private static String shellQuote(String s) {
        return "'" + (s == null ? "" : s.replace("'", "'\\''")) + "'";
    }

    /** .desktop 的 Exec 字段转义。 */
    private static String desktopQuote(String s) {
        String value = s == null ? "" : s;
        value = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("`", "\\`").replace("$", "\\$");
        return "\"" + value + "\"";
    }

    /** 把实例目录打成 zip，后台跑。 */
    private void backupInstance(Data.Instance ins) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("备份为压缩包");
        chooser.setInitialFileName(ins.name + "-备份-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("压缩包", "*.zip"));
        File out = chooser.showSaveDialog(win.stage);
        if (out == null) return;
        java.nio.file.Path src = java.nio.file.Paths.get(ins.folder);
        if (!java.nio.file.Files.isDirectory(src)) {
            win.toast("备份失败", "实例目录不存在：" + ins.folder, 2);
            return;
        }
        win.toast("开始备份", ins.name + " → " + out.getName(), 1);
        Thread worker = new Thread(() -> {
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(out.toPath())))) {
                java.nio.file.Files.walk(src).filter(java.nio.file.Files::isRegularFile).forEach(f -> {
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
            // 用标准列表项，不用白底卡片
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

        Ui.ListItem addFolder = new Ui.ListItem(Theme.icon("folder-plus", 17, Theme.text), "添加已有文件夹", "导入现有的 .minecraft", 38);
        Ui.tooltip(addFolder, "将一个已有的 Minecraft 文件夹添加到列表");
        addFolder.setOnMouseClicked(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择 .minecraft 文件夹");
            File file = chooser.showDialog(win.stage);
            if (file != null) {
                data.folders.add(new Data.FolderItem(file.getName(), file.getAbsolutePath()));
                data.gameDir = file.getAbsolutePath();
                scanVersions(data, file.toPath());
                data.save();
                win.openPage(win.currentKey, false);
                win.toast("已添加文件夹", file.getAbsolutePath(), 1);
            }
        });
        Ui.ListItem importPack = new Ui.ListItem(Theme.icon("package-plus", 17, Theme.text), "导入整合包", "支持 .zip 整合包", 38);
        Ui.tooltip(importPack, "在当前选择的 Minecraft 文件夹下安装整合包");
        importPack.setOnMouseClicked(e -> importModpack(win));
        panel.getChildren().addAll(addFolder, importPack);

        Label note = Ui.label("游戏目录\n" + data.gameDir, 10.5, Theme.gray3);
        note.setWrapText(true);
        note.setMaxWidth(240);
        VBox.setMargin(note, new Insets(14, 4, 8, 4));
        panel.getChildren().add(note);
        ScrollPane scroll = Ui.scroll(panel);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /** 扫描 versions 生成实例条目。 */
    private static void scanVersions(Data data, java.nio.file.Path gameDir) {
        java.nio.file.Path versions = gameDir.resolve("versions");
        if (!java.nio.file.Files.isDirectory(versions)) return;
        try (var stream = java.nio.file.Files.list(versions)) {
            stream.filter(java.nio.file.Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                if (!java.nio.file.Files.isRegularFile(dir.resolve(name + ".json"))) return;
                boolean exists = false;
                for (Data.Instance i : data.instances) {
                    if (i.name.equals(name) && i.folder.equals(gameDir.toString())) exists = true;
                }
                if (exists) return;
                String lower = name.toLowerCase();
                String loader = lower.contains("fabric") ? "Fabric" : lower.contains("forge") ? "Forge"
                        : lower.contains("neoforge") ? "NeoForge" : lower.contains("optifine") ? "OptiFine" : "原版";
                String icon = "Fabric".equals(loader) ? "Fabric.png" : "Forge".equals(loader) ? "CobbleStone.png"
                        : "NeoForge".equals(loader) ? "NeoForge.png" : "Grass.png";
                data.instances.add(new Data.Instance(name, name, loader, "",
                        "已导入的版本", icon, System.currentTimeMillis(), false, gameDir.toString()));
            });
        } catch (Exception ignored) {
        }
    }

    /** 导入整合包：预读、确认、命名再安装。 */
    static void importModpack(MainWindow win) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择整合包");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Minecraft 整合包", "*.zip", "*.mrpack"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        win.toast("正在预读整合包", file.getName(), 0);
        new Thread(() -> {
            Modpacks.PackInfo info = Modpacks.readInfo(file.toPath());
            javafx.application.Platform.runLater(() -> {
                if (!info.valid) {
                    Dialogs.confirm(win, "无法识别整合包",
                            "无法从该压缩包中识别出整合包清单。\n当前支持 Modrinth (.mrpack)、CurseForge (.zip) 与 MCBBS (mcbbs.packmeta) 格式。",
                            () -> {
                            });
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("类型：").append(info.typeName).append("\n");
                sb.append("名称：").append(info.name).append("\n");
                sb.append("游戏版本：").append(info.mcVersion).append("\n");
                sb.append("加载器：").append(info.loader.isEmpty() ? "无" : info.loader + " " + info.loaderVersion);
                if (!info.unsupportedLoaders.isEmpty()) {
                    Dialogs.confirm(win, "暂不支持的加载器",
                            "该整合包使用了 TCL 不支持的加载器：" + String.join("、", info.unsupportedLoaders)
                                    + "\n无法完成安装。", () -> {
                            });
                    return;
                }
                sb.append("\n\n是否开始安装？");
                Dialogs.confirm(win, "导入整合包", sb.toString(), () ->
                        Dialogs.input(win, "输入版本名称", "", info.name, name -> {
                            if (name == null) return;
                            Modpacks.installLocal(win, file.toPath(), info, name);
                        }));
            });
        }, "TCL-Modpack-Preview").start();
    }

    private static void openFolder(String path) {
            Shell.openFolder(path);
        }

}
