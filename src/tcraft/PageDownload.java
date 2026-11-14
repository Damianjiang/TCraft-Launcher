package tcraft;

import javafx.animation.RotateTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 下载页：原版游戏版本列表和社区资源分类。 */
public class PageDownload implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private PauseTransition debounce;

    public PageDownload(MainWindow win) {
        this.win = win;
    }

    @Override
    public double leftWidth() {
        return 160;
    }

    @Override
    public String sectionKey() {
        return data.downloadCategory;
    }

    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(18, 0, 0, 0));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        String cat = data.downloadCategory;
        panel.getChildren().add(category("原版游戏", "box", "原版游戏"));
        Label group = Ui.label("社区资源", 12, Theme.text);
        group.setOpacity(0.6);
        VBox.setMargin(group, new Insets(20, 5, 4, 13));
        panel.getChildren().add(group);
        panel.getChildren().add(category("Mod", "puzzle", "Mod"));
        panel.getChildren().add(category("整合包", "package", "整合包"));
        panel.getChildren().add(category("数据包", "file-box", "数据包"));
        panel.getChildren().add(category("资源包", "image", "资源包"));
        panel.getChildren().add(category("光影包", "sun", "光影包"));
        panel.getChildren().add(category("世界", "earth", "世界"));
        panel.getChildren().add(category("收藏夹", "heart", "收藏夹"));
        return panel;
    }

    private Node category(String title, String icon, String key) {
        Ui.ListItem item = new Ui.ListItem(Theme.icon(icon, 17, Theme.text), title, null, 36);
        item.setUserData(key);
        item.setSelected(key.equals(data.downloadCategory));
        StackPane refresh = Ui.iconButton("refresh-cw", 22, 13, "刷新", () -> {
            // 切到该分类并强制重新加载
            data.downloadCategory = key;
            win.toast("正在刷新", title, 0);
            if ("原版游戏".equals(key)) {
                data.refreshVersions(true, () -> {
                    if ("download".equals(win.currentKey) && key.equals(data.downloadCategory)) {
                        win.refreshRight();
                    }
                });
            }
            win.refreshRight();
        });
        refresh.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        refresh.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            RotateTransition rt = new RotateTransition(Duration.millis(520), refresh);
            rt.setByAngle(360);
            rt.play();
        });
        item.addAction(refresh);
        item.setOnMouseClicked(e -> {
            if (key.equals(data.downloadCategory)) return;
            data.downloadCategory = key;
            win.refreshRight();
        });
        VBox.setMargin(item, new Insets(0, 0, 1, 0));
        return item;
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        scroll.setPadding(new Insets(0));
        VBox page = new VBox();
        page.setPadding(new Insets(22, 25, 24, 25));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(page);
        buildContent(page);
        if (startLayer == null) return scroll;
        // 开始下载是页面级浮层，不随内容滚动
        StackPane root = new StackPane(scroll, startLayer);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);
        startLayer.setPickOnBounds(false);
        StackPane.setAlignment(startLayer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(startLayer, new Insets(0, 0, 20, 0));
        return root;
    }

    private void buildContent(VBox page) {
        page.getChildren().clear();
        switch (data.downloadCategory) {
            case "原版游戏" -> buildVanilla(page);
            case "世界" -> buildResources(page, "世界");
            case "收藏夹" -> buildFavorites(page);
            default -> buildResources(page, data.downloadCategory);
        }
    }

    private VBox vanillaBox;
    private VBox selectBox;
    /** 列表和选择页叠放，切换时交叉淡化。 */
    private StackPane pagesOverlap;
    private Data.McVersion selectVersion;
    private TextField selectNameField;
    private StackPane selectIcon;
    private Label renameHint;
    private StackPane restoreBtn;
    private boolean inSelectPage;
    private boolean nameEdited;
    /** 程序写名称时抑制 TextChanged。 */
    private boolean settingName;
    private VBox hintBox;
    private final java.util.List<LoaderCard> loaderCardList = new java.util.ArrayList<>();
    /** 各加载器选中的版本，空串表示没选。 */
    private final Map<String, String> sel = new java.util.HashMap<>();
    private StackPane startLayer;
    private StackPane startButton;

    /** 一张加载器卡。 */
    private static class LoaderCard {
        String key;
        String title;
        String blockImg;   // 图标文件名
        String loaderKey;   // Loaders 常量，空串表示没法自动拉列表
        boolean installerOnly; // 只下载安装器
        Ui.Card card;
        Node statusIcon;
        Label statusLabel;
        StackPane clearBtn;
        StackPane restoreBtn;
        VBox list;
        String error;  // 错误文案，null 表示正常
        boolean available = true;
        java.util.List<String> versions; // null 表示还没拉
        boolean fetched;
    }

    private void buildVanilla(VBox page) {
        // 官方清单在后台拉，完成后重建列表
        data.refreshVersions(false, () -> {
            // 选择页或已切页时不重建，避免打乱动画
            if (win.currentPageNode() != this) return;
            if (inSelectPage) return;
            if ("download".equals(win.currentKey) && "原版游戏".equals(data.downloadCategory)) {
                win.refreshRight();
            }
        });
        vanillaBox = new VBox(0);
        vanillaBox.setMaxWidth(Double.MAX_VALUE);
        if (data.versions.isEmpty()) {
            // 还没清单，先放加载卡
            StackPane box = new StackPane(Ui.loadingCard("正在获取 Minecraft 版本列表…"));
            box.setAlignment(Pos.TOP_CENTER);
            box.setPadding(new Insets(30, 0, 0, 0));
            box.setPickOnBounds(false);
            vanillaBox.getChildren().add(box);
        }
        Data.McVersion release = data.firstOf("release");
        Data.McVersion snapshot = data.firstOf("snapshot");
        if (data.versions.isEmpty()) {
            page.getChildren().add(vanillaBox);
            return;
        }
        if (data.isFallbackVersions()) {
            // 兜底数据要标注出来
            vanillaBox.getChildren().add(Ui.hint("当前是离线兜底数据：官方版本清单拉取失败，版本与时间可能不完整。"
                    + "联网后点「刷新」即可获取真实清单。", 2));
        }
        Ui.Card latest = Ui.card("最新版本");
        latest.setSpacing(0);
        latest.setContentPadding(new Insets(4, 12, 10, 12));
        if (release != null) {
            // McVersion 是共享对象，改 lore 会污染全局
            Data.McVersion copy = new Data.McVersion(release.id, release.type, release.time);
            copy.lore = data.isFallbackVersions() ? "离线兜底数据 · " + release.time
                    : "最新正式版，发布于 " + release.time;
            latest.add(versionRow(copy, true));
        }
        if (snapshot != null) {
            Data.McVersion copy = new Data.McVersion(snapshot.id, snapshot.type, snapshot.time);
            copy.lore = data.isFallbackVersions() ? "离线兜底数据 · " + snapshot.time
                    : "最新预览版，发布于 " + snapshot.time;
            latest.add(versionRow(copy, true));
        }
        VBox.setMargin(latest, new Insets(0, 0, 15, 0));
        vanillaBox.getChildren().add(latest);

        for (String group : new String[]{"正式版", "预览版", "远古版", "愚人节版"}) {
            List<Data.McVersion> list = data.versionsOf(group);
            if (list.isEmpty()) continue;
            Ui.Card card = Ui.card(group);
            card.setContentPadding(new Insets(0, 12, 10, 12));
            // 折叠时不建行，展开再构建，几百行一次建完会卡
            card.setLazyContent(() -> {
                for (Data.McVersion v : list) card.add(versionRow(v, false));
            });
            card.setCollapsed(true);
            VBox.setMargin(card, new Insets(0, 0, 15, 0));
            vanillaBox.getChildren().add(card);
        }

        buildSelectPage();
        pagesOverlap = new StackPane(vanillaBox, selectBox);
        pagesOverlap.setPickOnBounds(false);
        StackPane.setAlignment(vanillaBox, Pos.TOP_LEFT);
        StackPane.setAlignment(selectBox, Pos.TOP_LEFT);
        pagesOverlap.setMaxWidth(Double.MAX_VALUE);
        page.getChildren().add(pagesOverlap);
    }

    // 点版本只记录选择并切到选择态，真正安装要等「开始下载」

    /** 建选择页和各加载器卡。 */
    private void buildSelectPage() {
        selectBox = new VBox(0);
        selectBox.setMaxWidth(Double.MAX_VALUE);
        selectBox.setVisible(false);
        selectBox.setManaged(false);

        StackPane nameCard = new StackPane();
        nameCard.setBackground(Ui.fill(Theme.cardBg, 14));
        nameCard.setEffect(Theme.shadow(5, 0, 0.07, Color.web("#343d4a")));
        nameCard.setPadding(new Insets(12, 12, 10, 12));
        nameCard.setMaxWidth(Double.MAX_VALUE);

        StackPane back = Ui.iconButton("arrow-left", 30, 16, "返回版本列表", this::exitSelectPage);
        selectIcon = new StackPane();
        selectIcon.setMinSize(30, 30);
        selectIcon.setPrefSize(30, 30);
        selectIcon.setMaxSize(30, 30);
        selectIcon.getChildren().add(Ui.image("Grass.png", 30, 30, true));

        selectNameField = new TextField();
        selectNameField.setPromptText("实例名称（必填）");
        selectNameField.setFont(Theme.font(15));
        selectNameField.setStyle("-fx-background-color: transparent;"
                + "-fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 2 4 2 4;"
                + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
        renameHint = Ui.label("", 11, Theme.gray2);
        renameHint.setVisible(false);
        restoreBtn = Ui.iconButton("rotate-ccw", 20, 11, "撤销更改（恢复默认名称）", () -> {
            if (selectVersion != null) setName(getSelectName());
        });
        restoreBtn.setVisible(false);
        HBox renameRow = new HBox(4, renameHint, restoreBtn);
        renameRow.setAlignment(Pos.CENTER_LEFT);
        VBox nameCol = new VBox(2, selectNameField, renameRow);
        HBox.setHgrow(nameCol, Priority.ALWAYS);
        HBox nameRow = new HBox(10, back, selectIcon, nameCol);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        nameCard.getChildren().add(nameRow);

        hintBox = new VBox(8);
        hintBox.setVisible(false);
        hintBox.setManaged(false);

        loaderCardList.add(makeLoaderCard(Loaders.FORGE, "Forge", "Anvil.png", Loaders.FORGE, false));
        loaderCardList.add(makeLoaderCard(Loaders.NEOFORGE, "NeoForge", "NeoForge.png", Loaders.NEOFORGE, false));
        loaderCardList.add(makeLoaderCard(Loaders.FABRIC, "Fabric", "Fabric.png", Loaders.FABRIC, false));
        loaderCardList.add(makeLoaderCard(Loaders.LEGACYFABRIC, "LegacyFabric", "Fabric.png", Loaders.LEGACYFABRIC, false));
        loaderCardList.add(makeLoaderCard(Loaders.CLEANROOM, "Cleanroom（仅支持 1.12.2，点击下载安装器）", "Anvil.png", Loaders.CLEANROOM, true));
        loaderCardList.add(makeLoaderCard(Loaders.LABYMOD, "LabyMod（点击下载安装器）", "Egg.png", Loaders.LABYMOD, true));
        loaderCardList.add(makeLoaderCard(Loaders.FABRICAPI, "Fabric API", "Fabric.png", Loaders.FABRICAPI, false));
        loaderCardList.add(makeLoaderCard(Loaders.OPTIFINE, "OptiFine", "GrassPath.png", Loaders.OPTIFINE, false));
        loaderCardList.add(makeLoaderCard(Loaders.OPTIFABRIC, "OptiFabric", "OptiFabric.png", Loaders.OPTIFABRIC, false));
        loaderCardList.add(makeLoaderCard(Loaders.LITELOADER, "LiteLoader", "Egg.png", Loaders.LITELOADER, false));

        startButton = Ui.extraTextButton("download", "开始下载", this::startInstallFromSelect);
        startButton.setVisible(false);
        startLayer = new StackPane(startButton);
        startLayer.setAlignment(Pos.BOTTOM_CENTER);
        startLayer.setPadding(new Insets(0, 0, 20, 0));
        startLayer.setVisible(false);

        selectNameField.setOnAction(e -> {
            if (inSelectPage && startButton != null && !startButton.isDisabled()) startInstallFromSelect();
        });
        selectNameField.textProperty().addListener((o, a, b) -> {
            if (!settingName) nameEdited = true;   // 程序赋值不算用户编辑
            refreshRenameHint();
            updateStartButton();
        });

        VBox selectContent = new VBox(10);
        selectContent.getChildren().add(nameCard);
        VBox.setMargin(nameCard, new Insets(0, 15, 0, 15));
        selectContent.getChildren().add(hintBox);
        for (LoaderCard lc : loaderCardList) selectContent.getChildren().add(lc.card);
        selectContent.setPadding(new Insets(2, 2, 0, 2));
        selectBox.getChildren().add(selectContent);

        // 初始显隐由 refreshAll 决定
        refreshAll();
    }

    /** 建加载器卡，展开后异步拉版本列表。 */
    private LoaderCard makeLoaderCard(String key, String title, String blockImg, String loaderKey, boolean installerOnly) {
        LoaderCard lc = new LoaderCard();
        lc.key = key;
        lc.title = title;
        lc.blockImg = blockImg;
        lc.loaderKey = loaderKey;
        lc.installerOnly = installerOnly;

        Ui.Card card = Ui.card(title);
        lc.card = card;
        card.setSpacing(0);
        card.setCollapsible(true);
        card.setCollapsed(true);
        card.header.setPadding(new Insets(0, 15, 0, 20));
        card.header.setMinHeight(40);
        card.header.setPrefHeight(40);
        card.header.setMaxHeight(40);
        Label t = card.titleLabel();
        t.setMinWidth(112);
        t.setPrefWidth(112);
        lc.statusIcon = Ui.image(blockImg, 18, 18, true);
        lc.statusIcon.setVisible(false);
        lc.statusLabel = Ui.label("可以添加", 11.5, Theme.gray4);
        lc.clearBtn = Ui.iconButton("x", 22, 11, "清除选择", () -> clearCard(lc));
        lc.clearBtn.setVisible(false);
        // header 顺序是标题、弹簧、箭头
        card.header.getChildren().add(1, lc.statusIcon);
        card.header.getChildren().add(2, lc.statusLabel);
        card.header.getChildren().add(4, lc.clearBtn);

        lc.list = new VBox(2);
        card.setContentPadding(new Insets(0, 12, 10, 12));
        card.add(lc.list);
        card.setLazyContent(() -> loadCardList(lc));
        return lc;
    }

    /** 有缓存直接建行，否则后台拉。 */
    private void loadCardList(LoaderCard lc) {
        if (lc.versions != null) {
            rebuildList(lc);
            return;
        }
        lc.list.getChildren().setAll(Ui.loadingCard("正在获取 " + lc.title + " 版本列表…"));
        prefetchOne(lc);
    }

    /** 后台拉版本列表，安装器类卡也拉真实列表。 */
    private void prefetchOne(LoaderCard lc) {
        if (lc.fetched) return;   // 预取和展开只发一次请求
        lc.fetched = true;
        String mc = selectVersion == null ? "" : selectVersion.id;
        Thread t = new Thread(() -> {
            try {
                java.util.List<String> v = Loaders.versions(lc.loaderKey, mc);
                javafx.application.Platform.runLater(() -> {
                    lc.versions = v;
                    lc.error = null;
                    lc.fetched = true;
                    if (autoSelect(lc)) { refreshAll(); }
                    rebuildList(lc);
                    refreshAll();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    lc.versions = null;  // 下次展开时重试
                    lc.error = e.getMessage() == null ? "获取失败" : e.getMessage();
                    lc.fetched = false;
                    lc.list.getChildren().setAll(Ui.hint(lc.title + " 版本列表获取失败：" + lc.error, 2));
                    refreshAll();
                });
            }
        }, "TCL-Loader-" + lc.key);
        t.setDaemon(true);
        t.start();
    }

    /** 预拉所有卡的版本列表。 */
    private void prefetchAll() {
        for (LoaderCard lc : loaderCardList) prefetchOne(lc);
    }

    /** 重建版本列表行。 */
    private void rebuildList(LoaderCard lc) {
        if (lc.list == null) return;
        lc.list.getChildren().clear();
        if (lc.versions == null) {
            lc.list.getChildren().add(Ui.loadingCard("正在获取 " + lc.title + " 版本列表…"));
            return;
        }
        if (lc.versions.isEmpty()) {
            if (lc.error != null) {
                lc.list.getChildren().add(Ui.hint(lc.title + " 版本列表获取失败：" + lc.error, 2));
            } else {
                lc.list.getChildren().add(Ui.hint("该 Minecraft 版本没有可用的 " + lc.title, 1));
            }
            return;
        }
        for (String v : lc.versions) {
            String info = lc.installerOnly ? installerRowInfo(lc, v) : null;
            addVersionRow(lc, v, info);
        }
    }

    private String installerRowInfo(LoaderCard lc, String version) {
        if (Loaders.LABYMOD.equals(lc.loaderKey)) {
            return "snapshot".equals(version) ? "快照版（安装器）" : "正式版（安装器）";
        }
        return "GitHub Releases（安装器）";
    }

    /** 点一行即选中并折叠。 */
    private void addVersionRow(LoaderCard lc, String version, String info) {
        boolean installerOnly = lc.installerOnly;
        Ui.ListItem row = new Ui.ListItem(Ui.image(lc.blockImg, 22, 22, true), version, info, 40);
        String chosen = sel.getOrDefault(lc.key, "");
        row.setSelected(version.equals(chosen));
        row.setOnMouseClicked(e -> {
            if (installerOnly) {
                downloadInstaller(lc, version);
            } else {
                String cur = sel.getOrDefault(lc.key, "");
                if (cur.equals(version)) clearCard(lc);   // 再点一次取消
                else selectVersionFor(lc, version);
            }
        });
        lc.list.getChildren().add(row);
    }

    /** 选中后折叠。 */
    private void selectVersionFor(LoaderCard lc, String version) {
        sel.put(lc.key, version);
        if (lc.card != null) lc.card.setCollapsed(true);
        refreshAll();
    }

    private void clearCard(LoaderCard lc) {
        sel.remove(lc.key);
        if (lc.card != null) lc.card.setCollapsed(true);
        refreshAll();
    }

    /** 选了 Fabric 自动带 Fabric API，Fabric 加 OptiFine 自动带 OptiFabric。 */
    private boolean autoSelect(LoaderCard lc) {
        if (!Loaders.FABRICAPI.equals(lc.key) && !Loaders.OPTIFABRIC.equals(lc.key)) return false;
        if (lc.versions == null || lc.versions.isEmpty()) return false;
        boolean already = sel.containsKey(lc.key);
        if (already) return false;
        boolean pick = Loaders.FABRICAPI.equals(lc.key)
                ? !sel.getOrDefault(Loaders.FABRIC, "").isEmpty()
                : !sel.getOrDefault(Loaders.FABRIC, "").isEmpty() && !sel.getOrDefault(Loaders.OPTIFINE, "").isEmpty();
        if (!pick) return false;
        if (Loaders.OPTIFABRIC.equals(lc.key) && dropOf(selectVersion.id) >= 140 && dropOf(selectVersion.id) <= 150) return false;
        sel.put(lc.key, lc.versions.get(0));
        return true;
    }

    /** 刷新各卡显隐、状态、提示和开始下载按钮。 */
    private void refreshAll() {
        String mc = selectVersion == null ? "" : selectVersion.id;
        int drop = dropOf(mc);
        for (LoaderCard lc : loaderCardList) {
            boolean vis = visibilityOf(lc, mc, drop);
            lc.card.setVisible(vis);
            lc.card.setManaged(vis);
            if (!vis) continue;
            String chosen = sel.getOrDefault(lc.key, "");
            if (lc.installerOnly) {
                collapse(lc.statusIcon, false);
                collapse(lc.statusLabel, false);
                collapse(lc.clearBtn, false);
                continue;
            }
            if (!chosen.isEmpty()) {
                collapse(lc.statusIcon, true);
                lc.statusLabel.setText(displayVersion(lc, chosen));
                lc.statusLabel.setTextFill(Theme.gray1);
                collapse(lc.clearBtn, true);
            } else {
                collapse(lc.statusIcon, false);
                String status = (lc.error != null) ? lc.error : "可以添加";
                lc.statusLabel.setText(status);
                lc.statusLabel.setTextFill("获取失败".equals(status) || "无".equals(status) ? Theme.red : Theme.gray4);
                collapse(lc.clearBtn, false);
            }
            // 「无」错误时保持折叠
            if (lc.error != null && lc.error.equals("无")) lc.card.setCollapsed(true);
        }
        refreshHints();
        autoName();
        updateStartButton();
        refreshSelectIcon();
    }

    /** 名称卡图标跟随主加载器。 */
    private void refreshSelectIcon() {
        if (selectIcon == null) return;
        String img;
        if (!sel.getOrDefault(Loaders.FABRIC, "").isEmpty()) img = "Fabric.png";
        else if (!sel.getOrDefault(Loaders.FORGE, "").isEmpty()) img = "Anvil.png";
        else if (!sel.getOrDefault(Loaders.NEOFORGE, "").isEmpty()) img = "NeoForge.png";
        else if (!sel.getOrDefault(Loaders.LITELOADER, "").isEmpty()) img = "Egg.png";
        else if (!sel.getOrDefault(Loaders.OPTIFINE, "").isEmpty()) img = "GrassPath.png";
        else img = selectVersion != null ? selectVersion.icon() : "Grass.png";
        selectIcon.getChildren().setAll(Ui.image(img, 30, 30, true));
    }

    private boolean visibilityOf(LoaderCard lc, String mc, int drop) {
        switch (lc.key) {
            case Loaders.FORGE: return isStandard(mc);
            case Loaders.NEOFORGE: return gte(mc, "1.20.1");
            case Loaders.FABRIC: return drop > 130;  // 1.13 以上
            case Loaders.LITELOADER: return !mc.isEmpty() && drop < 130;  // 1.13 以下
            case Loaders.LEGACYFABRIC:
                // 确认没有可用版本才折叠，获取中或失败仍显示
                return !(lc.versions != null && lc.versions.isEmpty() && lc.error == null);
            case Loaders.FABRICAPI: return !sel.getOrDefault(Loaders.FABRIC, "").isEmpty();
            case Loaders.OPTIFABRIC:
                return !sel.getOrDefault(Loaders.FABRIC, "").isEmpty()
                        && !sel.getOrDefault(Loaders.OPTIFINE, "").isEmpty();
            default: return true; // 一直显示
        }
    }

    /** 按已选组件合成默认实例名。 */
    private String getSelectName() {
        if (selectVersion == null) return "";
        String name = selectVersion.id;
        String fabric = sel.getOrDefault(Loaders.FABRIC, "");
        String forge = sel.getOrDefault(Loaders.FORGE, "");
        String neoforge = sel.getOrDefault(Loaders.NEOFORGE, "");
        String lite = sel.getOrDefault(Loaders.LITELOADER, "");
        String optiFine = sel.getOrDefault(Loaders.OPTIFINE, "");
        if (!fabric.isEmpty()) name += "-Fabric " + fabric.split("\\+")[0];
        if (!forge.isEmpty()) name += "-Forge_" + forge;
        if (!neoforge.isEmpty()) name += "-NeoForge_" + neoforge.split("\\+")[0];
        if (!lite.isEmpty()) name += "-LiteLoader";
        if (!optiFine.isEmpty()) name += "-OptiFine_" + optiFine.replace(" ", "_");
        return name;
    }

    /** 名称没被改过时，跟着组件选择自动命名。 */
    private void autoName() {
        if (nameEdited || selectVersion == null || selectNameField == null) {
            refreshRenameHint();
            return;
        }
        String composed = getSelectName();
        if (!composed.equals(selectNameField.getText())) setName(composed);
        else refreshRenameHint();
    }

    /** 程序写名称，不算用户编辑。 */
    private void setName(String text) {
        settingName = true;
        try {
            selectNameField.setText(text);
        } finally {
            settingName = false;
        }
        nameEdited = false;
        refreshRenameHint();
        updateStartButton();
    }

    /** 改过名才显示原名提示和恢复按钮。 */
    private void refreshRenameHint() {
        if (renameHint == null || selectVersion == null) return;
        String cur = selectNameField.getText() == null ? "" : selectNameField.getText();
        boolean show = nameEdited && !cur.isEmpty();
        renameHint.setText(show ? "原名称：" + getSelectName() : "");
        renameHint.setVisible(show);
        restoreBtn.setVisible(show);
    }

    private String displayVersion(LoaderCard lc, String version) {
        return version;
    }

    /** 连 managed 一起切，避免留下空位。 */
    private static void collapse(Node n, boolean on) {
        if (n == null) return;
        n.setVisible(on);
        n.setManaged(on);
    }

    private void updateStartButton() {
        if (startButton == null) return;
        boolean valid = selectNameField != null
                && selectNameField.getText() != null && !selectNameField.getText().trim().isEmpty();
        startButton.setDisable(!valid);
        startButton.setOpacity(valid ? 1 : 0.55);
    }

    /** 刷新提示条。 */
    private void refreshHints() {
        if (hintBox == null) return;
        hintBox.getChildren().clear();
        String fabric = sel.getOrDefault(Loaders.FABRIC, "");
        String optiFine = sel.getOrDefault(Loaders.OPTIFINE, "");
        String fabricApi = sel.getOrDefault(Loaders.FABRICAPI, "");
        String optiFabric = sel.getOrDefault(Loaders.OPTIFABRIC, "");
        int drop = dropOf(selectVersion == null ? "" : selectVersion.id);
        if (!fabric.isEmpty() && fabricApi.isEmpty()) {
            hintBox.getChildren().add(Ui.hint("如果不安装 Fabric API，大多数 Mod 都会无法使用！", 2));
        }
        if (!fabric.isEmpty() && !optiFine.isEmpty() && optiFabric.isEmpty()) {
            if (drop >= 140 && drop <= 150) {
                hintBox.getChildren().add(Ui.hint("安装结束后，请在 Mod 下载中搜索 OptiFabric Origins 并下载，否则 OptiFine 会无法使用！", 1));
            } else {
                hintBox.getChildren().add(Ui.hint("必须安装 OptiFabric 才能正常使用 OptiFine！", 2));
            }
        }
        if (drop >= 160 && !optiFine.isEmpty()
                && (!sel.getOrDefault(Loaders.FORGE, "").isEmpty() || !fabric.isEmpty())) {
            hintBox.getChildren().add(Ui.hint("OptiFine 与一部分 Mod 的兼容性不佳，请谨慎安装。", 1));
        }
        boolean show = !hintBox.getChildren().isEmpty();
        hintBox.setVisible(show);
        hintBox.setManaged(show);
    }

    /** 把版本转成序数用于显隐判断，旧版返回 0。 */
    private int dropOf(String id) {
        if (id == null || id.isEmpty()) return 0;
        String s = id.toLowerCase();
        try {
            if (s.startsWith("1.")) {
                String rest = s.substring(2);
                int dot = rest.indexOf('.');
                String minor = dot >= 0 ? rest.substring(0, dot) : rest;
                int m = parseLeadInt(minor);
                return m * 10;
            }
            String[] p = s.split("[^0-9.]");
            for (String seg : p) {
                if (!seg.isEmpty()) {
                    int major = parseLeadInt(seg);
                    if (major >= 26) return major * 10;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int parseLeadInt(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') sb.append(c); else break;
        }
        return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
    }

    /** 是不是标准版本格式（1.x / 2x.x），决定 Forge 卡显不显示。 */
    private boolean isStandard(String id) {
        if (id == null) return false;
        if (id.matches("1\\.\\d.*")) return true;
        if (id.matches("[2-9]\\d\\..*")) return dropOf(id) >= 260;
        return false;
    }

    /** 按 drop 序数比较版本。 */
    private boolean gte(String id, String other) {
        return dropOf(id) >= dropOf(other);
    }

    /** 点版本：记录选择并切到选择态，不下载。 */
    private void minecraftSelected(Data.McVersion v) {
        if (inSelectPage) return;
        selectVersion = v;
        selectIcon.getChildren().setAll(Ui.image(v.icon(), 30, 30, true));
        setName(v.id);
        javafx.application.Platform.runLater(() -> {
            selectNameField.deselect();
            if (selectNameField.getText() != null) selectNameField.positionCaret(selectNameField.getText().length());
        });
        renameHint.setVisible(false);
        restoreBtn.setVisible(false);
        sel.clear();
        // 清空缓存，按新版本重拉
        for (LoaderCard lc : loaderCardList) {
            lc.versions = null;
            lc.error = "获取中……";
            lc.fetched = false;
        }
        refreshAll();
        enterSelectPage();
        prefetchAll();
    }

    private void enterSelectPage() {
        if (inSelectPage) return;
        inSelectPage = true;
        vanillaBox.setVisible(true);
        vanillaBox.setManaged(true);
        selectBox.setVisible(true);
        selectBox.setManaged(true);
        vanillaBox.setOpacity(1);
        vanillaBox.setTranslateX(0);
        selectBox.setOpacity(0);
        selectBox.setTranslateX(40);
        // KeyFrame 是绝对时间点，要补保持帧，否则插值会提前开始
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(10),
                        new KeyValue(vanillaBox.opacityProperty(), 1),
                        new KeyValue(vanillaBox.translateXProperty(), 0),
                        new KeyValue(selectBox.opacityProperty(), 0),
                        new KeyValue(selectBox.translateXProperty(), 40)),
                new KeyFrame(Duration.millis(80),
                        new KeyValue(vanillaBox.opacityProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(vanillaBox.translateXProperty(), -50, Ui.fluentOut(5)),
                        new KeyValue(selectBox.opacityProperty(), 0),   // 保持到 100ms
                        new KeyValue(selectBox.translateXProperty(), 40)),
                new KeyFrame(Duration.millis(170),
                        new KeyValue(selectBox.opacityProperty(), 1, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(selectBox.translateXProperty(), 0, Ui.fluentOut(5))));
        t.setOnFinished(e -> {
            // 退场完成后隐藏
            vanillaBox.setVisible(false);
            vanillaBox.setManaged(false);
        });
        t.play();
        showStartButton(true);
    }

    /** 回到版本列表，反向动画。 */
    private void exitSelectPage() {
        if (!inSelectPage) return;
        inSelectPage = false;
        sel.clear();
        refreshAll();
        vanillaBox.setVisible(true);
        vanillaBox.setManaged(true);
        vanillaBox.setOpacity(0);
        vanillaBox.setTranslateX(-50);
        selectBox.setOpacity(1);
        selectBox.setTranslateX(0);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(10),
                        new KeyValue(selectBox.opacityProperty(), 1),
                        new KeyValue(selectBox.translateXProperty(), 0),
                        new KeyValue(vanillaBox.opacityProperty(), 0),
                        new KeyValue(vanillaBox.translateXProperty(), -50)),
                new KeyFrame(Duration.millis(80),
                        new KeyValue(selectBox.opacityProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(selectBox.translateXProperty(), 50, Ui.fluentOut(5)),
                        new KeyValue(vanillaBox.opacityProperty(), 0),  // 保持到 100ms
                        new KeyValue(vanillaBox.translateXProperty(), -50)),
                new KeyFrame(Duration.millis(170),
                        new KeyValue(vanillaBox.opacityProperty(), 1, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(260),
                        new KeyValue(vanillaBox.translateXProperty(), 0, Ui.fluentOut(5))));
        t.setOnFinished(e -> {
            selectBox.setVisible(false);
            selectBox.setManaged(false);
        });
        t.play();
        showStartButton(false);
    }

    /** 校验名称后开始安装。 */
    private void startInstallFromSelect() {
        if (selectVersion == null) return;
        String name = selectNameField.getText() == null ? "" : selectNameField.getText().trim();
        if (name.isEmpty()) {
            win.toast("实例名称不能为空", "", 2);
            return;
        }
        Inst inst = buildInst(name);
        Runnable begin = () -> {
            Downloader.startInstall(win, inst);
            exitSelectPage(); // 装完回版本列表
        };
        // 有加载器且未开版本隔离时确认一次
        int indie = data.getInt("LaunchArgumentIndieV2", 0);
        if (inst.hasLoader() && (indie == 0 || indie == 2)) {
            Dialogs.confirm(win, "版本隔离提示",
                    "你尚未开启版本隔离，多个 MC 版本会共用同一个 Mod 文件夹。\n"
                            + "因此，游戏可能会因为读取到与当前版本不符的 Mod 而崩溃。\n"
                            + "推荐先在 设置 → 启动选项 → 默认版本隔离 中开启版本隔离！",
                    begin);
            return;
        }
        begin.run();
    }

    /** 汇总选择生成安装请求。 */
    private Inst buildInst(String name) {
        Inst inst = new Inst();
        inst.name = name;
        inst.vanillaId = selectVersion.id;
        inst.vanillaIcon = selectVersion.icon();
        inst.forge = sel.getOrDefault(Loaders.FORGE, "");
        inst.neoforge = sel.getOrDefault(Loaders.NEOFORGE, "");
        inst.fabric = sel.getOrDefault(Loaders.FABRIC, "");
        inst.legacyFabric = sel.getOrDefault(Loaders.LEGACYFABRIC, "");
        inst.liteLoader = sel.getOrDefault(Loaders.LITELOADER, "");
        inst.optiFine = sel.getOrDefault(Loaders.OPTIFINE, "");
        inst.fabricApi = sel.getOrDefault(Loaders.FABRICAPI, "");
        inst.optiFabric = sel.getOrDefault(Loaders.OPTIFABRIC, "");
        return inst;
    }

    /** 安装器卡：下安装器到 TCL/installers，不自动安装。 */
    private void downloadInstaller(LoaderCard lc, String version) {
        String url = Loaders.installerUrl(lc.loaderKey, version);
        if (url.isEmpty()) {
            win.toast("无法下载", "未找到 " + lc.title + " 的安装器地址", 3);
            return;
        }
        String name = Loaders.installerName(lc.loaderKey, version);
        if (name.isEmpty()) name = lc.key.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + version + ".jar";
        java.nio.file.Path target = java.nio.file.Paths.get(data.gameDir, "TCL", "installers", name);
        win.toast("正在下载安装器", lc.title + " · " + version, 0);
        Thread t = new Thread(() -> {
            try {
                Downloader.downloadFileTo(null, url, target, lc.title + " 安装器");
                javafx.application.Platform.runLater(() ->
                        win.toast("安装器已下载", "已保存到 " + target.getFileName(), 1));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        win.toast("下载失败", e.getMessage() == null ? "网络错误" : e.getMessage(), 3));
            }
        }, "TCL-Installer-" + lc.key);
        t.setDaemon(true);
        t.start();
    }

    /** 显示时从 0.6 弹到 1。 */
    private void showStartButton(boolean show) {
        if (startLayer == null || startButton == null) return;
        startLayer.setVisible(show);
        startButton.setVisible(show);
        if (!show) {
            startButton.setScaleX(0.6);
            startButton.setScaleY(0.6);
            return;
        }
        startButton.setScaleX(0.6);
        startButton.setScaleY(0.6);
        Timeline t = new Timeline(
                new KeyFrame(Duration.millis(180), new KeyValue(startButton.scaleXProperty(), 1, Ui.EASE_OUT_BACK),
                        new KeyValue(startButton.scaleYProperty(), 1, Ui.EASE_OUT_BACK)));
        t.play();
    }

    /** 调试用：按版本号进选择态，找不到退回最新正式版。 */
    void debugSelectVersion(String versionId) {
        for (Data.McVersion v : data.versions) {
            if (v.id.equalsIgnoreCase(versionId)) {
                minecraftSelected(v);
                return;
            }
        }
        Data.McVersion any = data.firstOf("release");
        if (any != null) minecraftSelected(any);
    }

    private Node versionRow(Data.McVersion v, boolean pinned) {
        Node icon = Ui.image(v.icon(), 30, 30, true);
        String info = v.lore != null ? v.lore : v.time;
        Ui.ListItem row = new Ui.ListItem(icon, v.id, info, pinned ? 44 : 42);
        // 行右侧的下载图标只进选择态
        StackPane download = Ui.iconButton("download", 28, 15, "选择并配置安装", () -> minecraftSelected(v));
        download.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        row.addAction(download);
        row.setOnMouseClicked(e -> minecraftSelected(v));
        return row;
    }

    private static final int RESOURCE_PAGE_SIZE = 40;
    private static final String[] WPF_VERSIONS = {"26.2", "26.1", "1.21.11", "1.21.8", "1.21.4", "1.21.1",
            "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.7.10"};
    /** Mod 类型表，列为名称、CurseForge 分类和 Modrinth slug。 */
    private static final String[][] MOD_TAGS = {
            {"全部", "", ""}, {"世界元素", "406", "worldgen"}, {"生物群系", "407", ""},
            {"维度", "410", ""}, {"矿物与资源", "408", ""}, {"天然结构", "409", ""},
            {"科技", "412", "technology"}, {"管道与物流", "415", ""}, {"自动化", "4843", ""},
            {"能源", "417", ""}, {"红石", "4558", ""}, {"食物与烹饪", "436", "food"},
            {"农业", "416", ""}, {"游戏机制", "", "game-mechanics"}, {"运输", "414", "transportation"},
            {"仓储", "420", ""}, {"魔法", "419", "magic"}, {"冒险", "422", "adventure"},
            {"装饰", "424", "decoration"}, {"生物", "411", "mobs"}, {"实用", "5191", "utility"},
            {"装备与工具", "434", "equipment"}, {"创造模式", "9026", ""}, {"性能优化", "6814", "optimization"},
            {"信息显示", "423", ""}, {"服务器", "435", "social"}, {"支持库", "421", "library"},
    };

    /** 首次加载时挂的加载卡，数据回来移除。 */
    private Node resourceLoadingHost;

    /** 双源部分失败时的提示。 */
    private HBox hintError;
    private Label hintErrorText;

    private int resourcePage = 0;

    private void buildResources(VBox page, String category) {
        final String projectType = projectTypeOf(category);
        final int classId = classIdOf(category);
        final boolean isMod = category.equals("Mod");

        TextField search = Ui.field("搜索 " + category);
        search.setMaxWidth(Double.MAX_VALUE);
        search.setMinWidth(0);
        Label nameLabel = Ui.label("名称", 13, Theme.text);
        Label srcLabel = Ui.label("来源", 13, Theme.text);
        ComboBox<String> sourceCombo = Ui.combo(new String[]{"全部", "CurseForge", "Modrinth"}, 0);
        sourceCombo.setMaxWidth(Double.MAX_VALUE);

        Label verLabel = Ui.label("版本", 13, Theme.text);
        ComboBox<String> versionCombo = new ComboBox<>();
        versionCombo.setEditable(true);
        versionCombo.getItems().add("全部 (也可自行输入)");
        // 自动带上当前实例的原版版本
        Data.Instance cur = data.instance();
        if (cur != null) {
            String vanilla = cur.version;
            for (String sfx : new String[]{"-forge", "-fabric", "-neoforge", "-quilt", "-optifine"}) {
                int i = vanilla.toLowerCase().indexOf(sfx);
                if (i > 0) vanilla = vanilla.substring(0, i);
            }
            if (!vanilla.isEmpty()) versionCombo.getItems().add(vanilla);
        }
        for (String v : WPF_VERSIONS) versionCombo.getItems().add(v);
        versionCombo.getSelectionModel().select(0);
        versionCombo.setMaxWidth(Double.MAX_VALUE);
        versionCombo.setMinWidth(0);
        ComboBox<String> loaderCombo = Ui.combo(new String[]{"任意 Mod 加载器", "Forge", "NeoForge", "Fabric", "LiteLoader"}, 0);
        loaderCombo.setMaxWidth(Double.MAX_VALUE);
        Label tagLabel = Ui.label("类型", 13, Theme.text);
        ComboBox<String> tagCombo = Ui.combo(tagNames(category), 0);
        tagCombo.setMaxWidth(Double.MAX_VALUE);

        for (Label l : new Label[]{nameLabel, srcLabel, verLabel, tagLabel}) {
            l.setMinWidth(Region.USE_PREF_SIZE);
            GridPane.setMargin(l, new Insets(0, 18, 0, 0));
        }

        GridPane filters = new GridPane();
        filters.setVgap(9);
        ColumnConstraints cLabel = new ColumnConstraints();
        ColumnConstraints cWide = new ColumnConstraints();
        cWide.setHgrow(Priority.ALWAYS);
        cWide.setFillWidth(true);
        ColumnConstraints cLoader = new ColumnConstraints();
        // 非 Mod 不参与 grow，否则版本框会被压窄
        if (isMod) {
            cLoader.setHgrow(Priority.ALWAYS);
            cLoader.setFillWidth(true);
        }
        ColumnConstraints cLabel2 = new ColumnConstraints();
        ColumnConstraints cCombo = new ColumnConstraints();
        cCombo.setPrefWidth(130);
        cCombo.setMinWidth(110);
        filters.getColumnConstraints().addAll(cLabel, cWide, cLoader, cLabel2, cCombo);

        filters.add(nameLabel, 0, 0);
        filters.add(search, 1, 0, 2, 1);
        filters.add(srcLabel, 3, 0);
        filters.add(sourceCombo, 4, 0);
        filters.add(verLabel, 0, 1);
        filters.add(versionCombo, 1, 1);
        if (isMod) filters.add(loaderCombo, 2, 1);
        filters.add(tagLabel, 3, 1);
        filters.add(tagCombo, 4, 1);

        // 版本有效时才显示加载器，隐藏时版本框跨列
        Runnable updateLoaderVisibility = () -> {
            String v = versionCombo.getEditor().getText() == null ? "" : versionCombo.getEditor().getText().trim();
            boolean show = isMod && (v.contains(".") || v.contains("w"));
            loaderCombo.setVisible(show);
            loaderCombo.setManaged(show);
            GridPane.setColumnSpan(versionCombo, show ? 1 : 2);
        };
        versionCombo.getEditor().textProperty().addListener((o, a, b) -> updateLoaderVisibility.run());
        updateLoaderVisibility.run();

        final Label status = Ui.label("", 12, Theme.gray2);
        status.setVisible(false);
        status.setManaged(false);
        // 一路源失败但仍有结果时提示结果可能不全
        hintError = Ui.hint("", 1);
        // Ui.hint 子节点顺序是图标、文字
        hintErrorText = (Label) hintError.getChildren().get(1);
        hintError.setVisible(false);
        hintError.setManaged(false);
        resourceLoadingHost = Ui.loadingCard("正在加载 " + category + " 列表…");
        page.getChildren().addAll(hintError, resourceLoadingHost, status);

        Ui.Card card = Ui.card(category + " 资源");
        card.setSpacing(0);
        card.setContentPadding(new Insets(4, 12, 10, 12));
        final VBox rows = new VBox(0);
        card.add(rows);
        page.getChildren().add(card);

        // 分页：上一页、页码、下一页
        HBox pager = new HBox(10);
        pager.setAlignment(Pos.CENTER);
        StackPane prev = Ui.iconButton("chevron-left", 28, 16, "上一页", null);
        Label pageLab = Ui.label("1", 15, Theme.accent);
        StackPane next = Ui.iconButton("chevron-right", 28, 16, "下一页", null);
        pager.getChildren().addAll(prev, pageLab, next);
        Ui.Card pageCard = Ui.card(null);
        pageCard.setContentPadding(new Insets(7, 12, 7, 12));
        pageCard.add(pager);
        pageCard.setMaxWidth(Region.USE_PREF_SIZE);
        HBox pagerRow = new HBox(pageCard);
        pagerRow.setAlignment(Pos.CENTER);
        page.getChildren().add(pagerRow);
        VBox.setMargin(pagerRow, new Insets(15, 0, 12, 0));

        final Runnable[] fetchRef = new Runnable[1];
        Runnable searchAction = () -> {
            resourcePage = 0;
            fetchRef[0].run();
        };
        // 来源、类型、加载器变了自动搜，名称和版本按 Enter
        sourceCombo.setOnAction(e -> searchAction.run());
        tagCombo.setOnAction(e -> searchAction.run());
        loaderCombo.setOnAction(e -> {
            updateLoaderVisibility.run();
            searchAction.run();
        });
        search.setOnAction(e -> searchAction.run());
        versionCombo.setOnAction(e -> updateLoaderVisibility.run());

        prev.setOnMouseClicked(e -> {
            if (resourcePage > 0) {
                resourcePage--;
                fetchRef[0].run();
            }
        });
        next.setOnMouseClicked(e -> {
            resourcePage++;
            fetchRef[0].run();
        });

        fetchRef[0] = () -> fetchResources(search.getText(), category, projectType, classId,
                sourceCombo, versionCombo, loaderCombo, tagCombo, status, rows, pageLab, prev, next);
        HBox actions = new HBox(10);
        StackPane searchBtn = Ui.button("搜索", Ui.BTN_HIGHLIGHT, searchAction);
        StackPane resetBtn = Ui.button("重置条件", Ui.BTN_NORMAL, () -> {
            search.setText("");
            versionCombo.getEditor().setText("全部 (也可自行输入)");
            versionCombo.getSelectionModel().select(0);
            sourceCombo.getSelectionModel().select(0);
            tagCombo.getSelectionModel().select(0);
            loaderCombo.getSelectionModel().select(0);
            updateLoaderVisibility.run();
            searchAction.run();
        });
        searchBtn.setMinWidth(140);
        resetBtn.setMinWidth(140);
        actions.getChildren().addAll(searchBtn, resetBtn);
        // 只有整合包页显示这个按钮
        if ("整合包".equals(category)) {
            actions.getChildren().add(
                    Ui.button("安装已有整合包", Ui.BTN_NORMAL, () -> PageInstance.importModpack(win)));
        }
        Ui.Card searchCard = Ui.card("搜索 " + category);
        searchCard.setContentPadding(new Insets(2, 15, 12, 15));
        VBox searchBody = new VBox(10, filters, actions);
        searchBody.setMaxWidth(Double.MAX_VALUE);
        searchCard.add(searchBody);
        page.getChildren().add(0, searchCard);
        VBox.setMargin(searchCard, new Insets(0, 0, 12, 0));
        fetchRef[0].run();
    }

    private String[] tagNames(String category) {
        if (!category.equals("Mod")) return new String[]{"全部"};
        String[] names = new String[MOD_TAGS.length];
        for (int i = 0; i < MOD_TAGS.length; i++) names[i] = MOD_TAGS[i][0];
        return names;
    }

    private int classIdOf(String category) {
        return switch (category) {
            case "整合包" -> CurseForge.CLASS_MODPACK;
            case "数据包" -> CurseForge.CLASS_DATAPACK;
            case "资源包" -> CurseForge.CLASS_RESOURCEPACK;
            case "光影包" -> CurseForge.CLASS_SHADER;
            case "世界" -> CurseForge.CLASS_WORLD;
            default -> CurseForge.CLASS_MOD;
        };
    }

    /** 双源搜索，合并后按下载量排序。 */
    private void fetchResources(String q, String category, String projectType, int classId,
                                ComboBox<String> sourceCombo, ComboBox<String> versionCombo,
                                ComboBox<String> loaderCombo, ComboBox<String> tagCombo,
                                Label status, VBox rows, Label pageLab, StackPane prev, StackPane next) {
        String source = sourceCombo.getValue();
        String version = versionCombo.getEditor().getText() == null ? "" : versionCombo.getEditor().getText().trim();
        boolean versionValid = !version.isEmpty() && !version.startsWith("全部")
                && (version.contains(".") || version.contains("w"));
        int tagIdx = tagCombo.getSelectionModel().getSelectedIndex();
        String[][] tagSet = category.equals("Mod") ? MOD_TAGS : null;
        String mrCategory = tagSet != null && tagIdx > 0 ? tagSet[tagIdx][2] : "";
        int cfCategoryId = 0;
        if (tagSet != null && tagIdx > 0 && !tagSet[tagIdx][1].isEmpty()) {
            try {
                cfCategoryId = Integer.parseInt(tagSet[tagIdx][1]);
            } catch (Exception ignored) {
            }
        }
        final int fCfCategoryId = cfCategoryId;
        String loader = loaderCombo.isVisible() && loaderCombo.getSelectionModel().getSelectedIndex() > 0
                ? loaderCombo.getValue().toLowerCase() : null;
        int offset = resourcePage * RESOURCE_PAGE_SIZE;
        boolean wantMr = !source.equals("CurseForge");
        boolean wantCf = !source.equals("Modrinth");
        if (resourceLoadingHost != null && resourceLoadingHost.getParent() instanceof javafx.scene.layout.Pane host) {
            // 移除顶部加载卡
            host.getChildren().remove(resourceLoadingHost);
        }
        rows.getChildren().setAll(Ui.loadingCard(resourcePage == 0 ? "正在搜索…"
                : "正在加载第 " + (resourcePage + 1) + " 页…"));
        new Thread(() -> {
            // 两个源并发请求，串行要等两倍
            List<Resource> list = java.util.Collections.synchronizedList(new ArrayList<>());
            String[] err = new String[2]; // 0 是 Modrinth，1 是 CurseForge
            java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch((wantMr ? 1 : 0) + (wantCf ? 1 : 0));

            if (wantMr) {
                Http.pool().execute(() -> {
                    try {
                        List<String> versions = versionValid ? List.of(version) : null;
                        List<String> loaders = loader == null ? null : List.of(loader);
                        List<Resource> got = new ArrayList<>();
                        for (Object h : Modrinth.search(q, projectType, RESOURCE_PAGE_SIZE, versions, loaders,
                                mrCategory, offset, q.isEmpty() ? "downloads" : "relevance")) {
                            got.add(mrResource(h, projectType));
                        }
                        list.addAll(got);
                    } catch (Exception e) {
                        err[0] = e.getMessage() == null ? "网络错误" : e.getMessage();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (wantCf) {
                Http.pool().execute(() -> {
                    try {
                        if (CurseForge.apiKey().isEmpty()) {
                            throw new java.io.IOException("CurseForge 需要 API Key（请在 设置 → 系统 → CurseForge Key 填入，在 https://console.curseforge.com/ 生成）");
                        }
                        List<Resource> got = new ArrayList<>();
                        for (CurseForge.Project p : CurseForge.search(q, classId, RESOURCE_PAGE_SIZE, fCfCategoryId, offset)) {
                            Resource r = new Resource();
                            r.name = p.name;
                            r.slug = p.slug;
                            r.icon = p.icon;
                            r.desc = p.summary;
                            r.projectType = projectType;
                            r.website = p.website;
                            r.cfId = p.id;
                            r.downloads = (int) Math.min(Integer.MAX_VALUE, p.downloads);
                            got.add(r);
                        }
                        list.addAll(got);
                    } catch (Exception e) {
                        err[1] = e.getMessage() == null ? "网络错误" : e.getMessage();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            String mrError = err[0];
            String cfError = err[1];
            list.sort((a, b) -> Long.compare(b.downloads, a.downloads));
            List<Resource> fList = list;
            String fMrError = mrError;
            String fCfError = cfError;
            String fError = mrError != null ? mrError : cfError;
            boolean hasMore = list.size() >= RESOURCE_PAGE_SIZE;
            javafx.application.Platform.runLater(() -> {
                rows.getChildren().clear();
                // 一路失败时提示结果可能不全
                String partial = null;
                if (!fList.isEmpty()) {
                    if (fMrError != null && wantCf) {
                        partial = "无法连接到 Modrinth，所以目前仅显示了来自 CurseForge 的内容，搜索结果可能不全。\n"
                                + "请稍后再试，或使用 VPN 改善网络环境。";
                    } else if (fCfError != null && wantMr) {
                        // 原始错误已带括号指引，给一句干净说明
                        String why = fCfError.contains("API Key")
                                ? "缺少 CurseForge API Key，可在 设置 → 系统 中配置"
                                : fCfError;
                        partial = "CurseForge 的内容没有取到（" + why + "），当前仅显示 Modrinth 的结果。";
                    }
                }
                if (hintError != null) {
                    boolean show = partial != null;
                    if (show && hintErrorText != null) hintErrorText.setText(partial);
                    hintError.setVisible(show);
                    hintError.setManaged(show);
                    VBox.setMargin(hintError, new Insets(0, 0, show ? 12 : 0, 0));
                }
                if (fList.isEmpty()) {
                    status.setText(fError == null
                            ? (resourcePage == 0 ? "该分类暂无资源" : "没有更多结果")
                            : "无法获取" + category + "列表：" + fError);
                    rows.getChildren().add(Ui.label(fError == null ? "换个关键词或放宽筛选试试。" : fError, 12.5, Theme.gray2));
                    prev.setDisable(resourcePage <= 0);
                    next.setDisable(true);
                    pageLab.setText(String.valueOf(resourcePage + 1));
                    return;
                }
                status.setText((q == null || q.isEmpty() ? category + " · 按下载量" : "「" + q + "」")
                        + " · 第 " + (resourcePage + 1) + " 页（" + fList.size() + "）");
                for (Resource r : fList) rows.getChildren().add(resourceRow(r));
                prev.setDisable(resourcePage <= 0);
                next.setDisable(!hasMore);
                pageLab.setText(String.valueOf(resourcePage + 1));
            });
        }, "TCL-Resource-Search").start();
    }

    /** Modrinth 结果转 Resource，并提取加载器。 */
    private Resource mrResource(Object h, String projectType) {
        java.util.Map<String, Object> m = Json.obj(h);
        Resource r = new Resource();
        r.pid = Json.str(m, "project_id", "");
        r.name = Json.str(m, "title", Json.str(m, "slug", "?"));
        r.slug = Json.str(m, "slug", "");
        r.icon = Json.str(m, "icon_url", "");
        r.desc = Json.str(m, "short_description", Json.str(m, "description", ""));
        r.downloads = (int) Json.num(m, "downloads", 0);
        r.projectType = projectType;
        for (Object c : Json.arr(m.get("categories"))) {
            String cat = String.valueOf(c).toLowerCase();
            if ((cat.equals("fabric") || cat.equals("forge") || cat.equals("quilt") || cat.equals("neoforge"))
                    && !r.loaders.contains(cat)) {
                r.loaders.add(cat);
            }
        }
        return r;
    }

    private Node resourceRow(Resource r) {
        Node icon = iconNode(r, 30);
        Ui.ListItem row = new Ui.ListItem(icon, r.name, r.desc.isEmpty() ? "下载量 " + r.downloads : r.desc, 46);
        row.setOnMouseClicked(e -> openDetail(r, "world".equals(r.projectType) ? "curseforge" : "modrinth"));
        return row;
    }

    /** 打开资源详情整页。 */
    private void openDetail(Resource r, String platform) {
        ResourceInfo info = new ResourceInfo();
        info.platform = platform;
        info.projectId = platform.equals("curseforge")
                ? String.valueOf(r.cfId)
                : (r.pid.isEmpty() ? r.slug : r.pid);
        info.slug = r.slug;
        info.name = r.name;
        info.desc = r.desc;
        info.icon = r.icon;
        info.website = r.website;
        info.downloads = r.downloads;
        info.projectType = r.projectType;
        info.resourceType = r.projectType;
        info.loaders.addAll(r.loaders);
        // 版本和加载器来自当前实例
        Data.Instance ins = data.instance();
        if (ins != null) {
            info.targetVersion = ins.version == null ? "" : ins.version;
            String loaderKey = ins.loader == null ? "" : ins.loader.toLowerCase();
            if (loaderKey.equals("forge") || loaderKey.equals("fabric")
                    || loaderKey.equals("quilt") || loaderKey.equals("neoforge")) {
                info.targetLoader = loaderKey;
            }
        }
        win.openResourceDetail(info);
    }

    private Node iconNode(Resource r, int size) {
        String url = r.icon == null ? "" : r.icon.trim();
        if (!url.startsWith("http")) {
            return Ui.image(url.isEmpty() ? "NoIcon.png" : url, size, size, true);
        }
        // 远程图走 RemoteImages，避免每张图一条连接
        StackPane box = new StackPane();
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setBackground(Ui.fill(Theme.halfWhite, 6));

        javafx.scene.image.Image cached = RemoteImages.cached(url, size);
        if (cached != null) {
            box.getChildren().add(imageView(cached, size));
            return box;
        }
        // 先放占位，图到了再替换
        box.getChildren().add(Ui.image("NoIcon.png", size, size, true));
        if (RemoteImages.failed(url)) return box;
        RemoteImages.load(url, size, img -> {
            if (img != null) box.getChildren().setAll(imageView(img, size));
        });
        return box;
    }

    private static javafx.scene.image.ImageView imageView(javafx.scene.image.Image img, double size) {
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        return iv;
    }

    private Data.Favorite findFavorite(String type, String slug) {
        for (Data.Favorite f : data.favorites) {
            if (f.type.equals(type) && f.slug.equals(slug)) return f;
        }
        return null;
    }

            /** 收藏夹列表。 */
    private void buildFavorites(VBox page) {
        if (data.favorites.isEmpty()) {
            Ui.Card card = Ui.card("资源收藏夹");
            VBox box = new VBox(10);
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(46, 20, 46, 20));
            box.getChildren().addAll(
                    Theme.icon("heart", 38, Theme.gray4),
                    Ui.bold("收藏夹是空的", 18, Theme.text),
                    Ui.label("在资源详情页点击收藏，之后就能在这里快速找到。", 12.5, Theme.gray2));
            card.add(box);
            page.getChildren().add(card);
            return;
        }
        Ui.Card card = Ui.card("资源收藏夹 · " + data.favorites.size());
        card.setSpacing(0);
        card.setContentPadding(new Insets(4, 12, 10, 12));
        for (Data.Favorite f : new ArrayList<>(data.favorites)) card.add(favoriteRow(f));
        page.getChildren().add(card);
    }

    /** 收藏项，可看详情、开网页、取消收藏。 */
    private Node favoriteRow(Data.Favorite f) {
        Resource r = new Resource();
        r.name = f.name.isEmpty() ? f.slug : f.name;
        r.desc = (typeName(f.type) + (f.desc.isEmpty() ? "" : " · " + f.desc));
        r.icon = f.icon;
        r.slug = f.slug;
        r.projectType = f.type;
        r.website = f.website;
        Node icon = iconNode(r, 34);
        Ui.ListItem row = new Ui.ListItem(icon, r.name, r.desc, 48);
        StackPane detail = Ui.iconButton("info", 28, 14, "详情", () -> {
            ResourceInfo info = new ResourceInfo();
            info.platform = f.platform == null || f.platform.isEmpty()
                    ? (f.website != null && f.website.contains("curseforge") ? "curseforge" : "modrinth")
                    : f.platform;
            info.projectId = f.projectId == null || f.projectId.isEmpty() ? f.slug : f.projectId;
            info.slug = f.slug;
            info.name = f.name;
            info.desc = f.desc;
            info.icon = f.icon;
            info.website = f.website;
            info.projectType = f.type;
            info.resourceType = f.type;
            win.openResourceDetail(info);
        });
        detail.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        StackPane open = Ui.iconButton("external-link", 28, 14, "打开网页", () -> {
            if (f.website != null && !f.website.isEmpty()) openUrl(f.website);
            else win.toast("没有网页地址", f.name, 2);
        });
        open.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        StackPane remove = Ui.iconButton("trash-2", 28, 14, "取消收藏", () -> {
            data.favorites.remove(f);
            data.save();
            win.toast("已取消收藏", r.name, 1);
            win.refreshRight();
        });
        remove.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
        row.addAction(detail);
        row.addAction(open);
        row.addAction(remove);
        return row;
    }

    private String typeName(String type) {
        return switch (type) {
            case "modpack" -> "整合包";
            case "datapack" -> "数据包";
            case "resourcepack" -> "资源包";
            case "shader" -> "光影包";
            case "world" -> "世界";
            default -> "Mod";
        };
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            win.toast("没有网页地址", "该资源未提供链接", 2);
            return;
        }
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            win.toast("无法打开网页", url, 2);
        }
    }

    private static class Resource {
        String name, desc, icon;
        String pid = "";  // Modrinth project_id
        String slug = "";
        String website = "";
        String projectType = "mod";
        java.util.List<String> loaders = new ArrayList<>();
        long cfId;
        int downloads;

        Resource() {
        }

    }

    private String projectTypeOf(String category) {
        return switch (category) {
            case "整合包" -> "modpack";
            case "数据包" -> "datapack";
            case "资源包" -> "resourcepack";
            case "光影包" -> "shader";
            case "世界" -> "map"; // Modrinth 没有 Map 类型，世界数据来自 CurseForge
            default -> "mod";
        };
    }

    private static class SeparatorLine extends Region {
        SeparatorLine() {
            setMinHeight(1);
            setMaxHeight(1);
            setBackground(Ui.fill(Color.web("#343d4a", 0.10), 0));
            VBox.setMargin(this, new Insets(2, 0, 2, 0));
        }
    }
}
