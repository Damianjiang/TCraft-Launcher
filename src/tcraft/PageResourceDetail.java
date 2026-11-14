package tcraft;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** 资源详情整页：简介、筛选、版本文件列表。 */
public class PageResourceDetail implements Page {

    private final MainWindow win;
    private final Data data = Data.get();
    private final ResourceInfo info;

    private List<ResourceVersion> allVersions; // null 表示还没加载完
    private String loadError = "";
    private boolean loadFailed = false;

    private String versionFilter;
    private boolean groupedDrop, groupedFold;

    private static final Map<String, String> CACHED_FOLDER = new ConcurrentHashMap<>();
    /** 前置项目信息，按 id 存 名称、slug、简介、图标、类型。 */
    private final Map<String, String[]> depProjects = new ConcurrentHashMap<>();
    private final Map<String, CurseForge.Project> depProjectsCf = new ConcurrentHashMap<>();

    private VBox panResults;
    private HBox panFilter;
    private Ui.Card cardFilter;
    private StackPane panLoadHost;
    private HBox hintAlternative;
    private Label hintAlternativeText;
    private StackPane favButton;

    public PageResourceDetail(MainWindow win) {
        this.win = win;
        this.info = win.pendingResource != null ? win.pendingResource : new ResourceInfo();
    }

    @Override
    public double leftWidth() {
        return 160;
    }

    /** 左栏沿用下载分类侧栏。 */
    @Override
    public Node left() {
        VBox panel = new VBox(0);
        panel.setPadding(new Insets(18, 0, 0, 0));
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        String[][] cats = {
                {"原版游戏", "box"}, {"Mod", "puzzle"}, {"整合包", "package"}, {"数据包", "file-box"},
                {"资源包", "image"}, {"光影包", "sun"}, {"世界", "earth"}, {"收藏夹", "heart"}};
        for (String[] c : cats) {
            Label group = c[0].equals("Mod") ? Ui.label("社区资源", 12, Theme.text) : null;
            if (group != null) {
                group.setOpacity(0.6);
                VBox.setMargin(group, new Insets(20, 5, 4, 13));
                panel.getChildren().add(group);
            }
            Ui.ListItem item = new Ui.ListItem(Theme.icon(c[1], 17, Theme.text), c[0], null, 36);
            item.setOnMouseClicked(e -> {
                data.downloadCategory = c[0];
                win.openPage("download", true);
            });
            VBox.setMargin(item, new Insets(0, 0, 1, 0));
            panel.getChildren().add(item);
        }
        return panel;
    }

    @Override
    public Node right() {
        ScrollPane scroll = Ui.scroll(new StackPane());
        VBox page = new VBox();
        page.setPadding(new Insets(22, 25, 24, 25));
        page.setFillWidth(true);
        page.setMaxWidth(Double.MAX_VALUE);
        scroll.setContent(page);

        // 用 add() 加内容，直接加子节点会叠在一起
        Ui.Card intro = Ui.card(null);
        intro.setContentPadding(new Insets(14, 20, 14, 20));
        intro.add(introResourceItem(), introButtons());
        VBox.setMargin(intro, new Insets(0, 0, 15, 0));
        page.getChildren().add(intro);

        // Mod 与数据包互跳提示
        hintAlternativeText = Ui.label("", 12.5, Color.web("#1c5aa8"));
        hintAlternativeText.setWrapText(true);
        hintAlternative = Ui.hint("", 0);
        hintAlternative.getChildren().setAll(Theme.icon("info", 16, Color.web("#1c5aa8")), hintAlternativeText);
        hintAlternative.setCursor(Cursor.HAND);
        hintAlternative.setOnMouseClicked(e -> hintAlternativeClick());
        hintAlternative.setManaged(false);
        hintAlternative.setVisible(false);
        VBox.setMargin(hintAlternative, new Insets(0, 0, 15, 0));
        page.getChildren().add(hintAlternative);

        cardFilter = Ui.card(null);
        cardFilter.setContentPadding(new Insets(8, 10, 8, 10));
        panFilter = new HBox(4);
        panFilter.setAlignment(Pos.CENTER_LEFT);
        cardFilter.add(panFilter);
        cardFilter.setManaged(false);
        cardFilter.setVisible(false);
        VBox.setMargin(cardFilter, new Insets(0, 0, 15, 0));
        page.getChildren().add(cardFilter);

        panResults = new VBox();
        panResults.setSpacing(15);
        page.getChildren().add(panResults);

        panLoadHost = new StackPane();
        panLoadHost.getChildren().add(loadingCard());
        page.getChildren().add(panLoadHost);

        startLoad();
        return scroll;
    }

    /** 资源项放在简介卡里，不可交互。 */
    private Node introResourceItem() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(iconNode(info.icon, 42));

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.BASELINE_LEFT);
        titleRow.getChildren().add(Ui.bold(
                info.name == null || info.name.isEmpty() ? "未命名资源" : info.name, 16, Theme.text));
        if (info.slug != null && !info.slug.isEmpty() && !info.slug.equals(info.name)) {
            titleRow.getChildren().add(Ui.label(info.slug, 11.5, Theme.gray2));
        }

        VBox texts = new VBox(3, titleRow);
        texts.setMinWidth(0);
        if (info.desc != null && !info.desc.isEmpty()) {
            Label desc = Ui.label(info.desc, 11.5, Theme.gray2);
            // 单行截断，否则长描述会挤掉平台名
            desc.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            desc.setWrapText(false);
            desc.setMinWidth(0);
            desc.setMaxWidth(420); // 窗口 842 宽时不会顶出卡片
            texts.getChildren().add(desc);
        }
        texts.getChildren().add(Ui.label(metaLine(), 11.5, Theme.gray2));
        HBox.setHgrow(texts, Priority.ALWAYS);
        row.getChildren().add(texts);

        Label platform = Ui.label(info.isCurseForge() ? "CurseForge" : "Modrinth", 11.5, Theme.gray2);
        platform.setAlignment(Pos.TOP_RIGHT);
        HBox.setMargin(platform, new Insets(2, 0, 0, 10));
        row.getChildren().add(platform);
        VBox.setMargin(row, new Insets(0, 0, 12, 0));
        return row;
    }

    /** 简介卡的元信息行：加载器 + 下载量。 */
    private String metaLine() {
        StringBuilder sb = new StringBuilder();
        List<String> ls = info.loaders();
        if (ls != null && !ls.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String l : ls) {
                String c = cap(l);
                if (c != null && !c.isEmpty()) names.add(c);
            }
            if (!names.isEmpty()) sb.append(String.join(" / ", names)).append("  ·  ");
        }
        sb.append(downloadText(info.downloads)).append(" 次下载");
        return sb.toString();
    }

    /** 下载量文案，亿或万。 */
    private static String downloadText(long n) {
        if (n >= 100_000_000L) return String.format(java.util.Locale.ROOT, "%.2f 亿", n / 100_000_000d);
        if (n >= 10_000L) return Math.round(n / 10_000d) + " 万";
        return String.valueOf(n);
    }

    /** 简介卡按钮行。 */
    private Node introButtons() {
        HBox row = new HBox(12);
        // 按钮等宽
        StackPane web = Ui.button("转到 " + (info.isCurseForge() ? "CurseForge" : "Modrinth"),
                Ui.BTN_THEME, () -> openUrl(websiteOf()));
        StackPane copy = Ui.button("复制名称", Ui.BTN_NORMAL, this::copyName);
        favButton = Ui.button(favoriteNow() ? "取消收藏" : "收藏", Ui.BTN_NORMAL, this::toggleFavorite);
        web.setMinWidth(120);
        copy.setMinWidth(120);
        favButton.setMinWidth(120);
        row.getChildren().addAll(web, copy, favButton);
        return row;
    }

    private void startLoad() {
        allVersions = null;
        loadFailed = false;
        loadError = "";
        panLoadHost.getChildren().setAll(loadingCard());
        panLoadHost.setVisible(true);
        panLoadHost.setManaged(true);
        panResults.getChildren().clear();
        String pid = info.projectId.isEmpty() ? info.slug : info.projectId;
        new Thread(() -> {
            try {
                List<ResourceVersion> list = new ArrayList<>();
                if (info.isCurseForge()) {
                    // CurseForge 走 /mods/{id}/files
                    for (Object o : CurseForge.projectFiles(Long.parseLong(pid))) {
                        ResourceVersion v = ResourceVersion.fromCurseForgeJson(Json.obj(o), expectedType());
                        if (!v.downloadUrls.isEmpty()) list.add(v);
                    }
                } else {
                    // Modrinth 走 /project/{id}/version
                    for (Object o : Modrinth.projectVersions(pid)) {
                        ResourceVersion v = ResourceVersion.fromModrinthJson(Json.obj(o), expectedType());
                        if (!v.downloadUrls.isEmpty()) list.add(v);
                    }
                }
                // 解析前置并填进 Dependencies
                resolveDependencies(list);
                Platform.runLater(() -> {
                    allVersions = list;
                    loadFinished();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadFailed = true;
                    loadError = e.getMessage() == null ? "网络错误" : e.getMessage();
                    showLoadError();
                });
            }
        }, "TCL-ResourceDetail-Files").start();
    }

    /** 加载态卡片。 */
    private Ui.Card loadingCard() {
        Ui.Card card = Ui.card(null);
        card.setContentPadding(new Insets(18, 22, 16, 22));
        card.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.add(new Ui.Loading(220, 90, "正在获取版本列表"));
        return card;
    }

    /** 失败状态。 */
    private void showLoadError() {
        VBox err = new VBox(12);
        err.setAlignment(Pos.CENTER);
        err.setPadding(new Insets(30, 20, 30, 20));
        err.setMaxWidth(470);
        err.getChildren().add(Theme.icon("triangle-alert", 34, Color.web("#9a6512")));
        Label msg = Ui.label("获取版本列表失败：" + loadError, 13, Theme.text);
        msg.setWrapText(true);
        msg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        String suffix = info.isCurseForge()
                ? "该资源来自 CurseForge：请检查网络，或在「设置 → 系统 → CurseForge Key」配置密钥后重试。"
                : "请检查网络后重试；也可点上方「转到 Modrinth」手动查看。";
        Label tip = Ui.label(suffix, 12, Theme.gray2);
        tip.setWrapText(true);
        tip.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        actions.getChildren().add(Ui.button("重试", Ui.BTN_THEME, this::startLoad));
        if (info.isCurseForge() && CurseForge.apiKey().isEmpty()) {
            actions.getChildren().add(Ui.button("前往设置", Ui.BTN_NORMAL, () -> win.openSetup("system")));
        }
        err.getChildren().addAll(msg, tip, actions);
        err.setBackground(Ui.fill(Theme.cardBg, 14));
        panLoadHost.getChildren().setAll(err);
        panLoadHost.setVisible(true);
        panLoadHost.setManaged(true);
    }

    /** 类型筛选基准，初始值就是搜索分类。 */
    private String expectedType() {
        String t = info.resourceType == null || info.resourceType.isEmpty() ? info.projectType : info.resourceType;
        return t.isEmpty() ? "mod" : t;
    }

    /** 解析前置项目。 */
    private void resolveDependencies(List<ResourceVersion> list) {
        List<String> ids = new ArrayList<>();
        for (ResourceVersion v : list) {
            for (String d : v.rawDependencies) {
                if (!ids.contains(d)) ids.add(d);
            }
        }
        if (ids.isEmpty()) return;
        if (info.isCurseForge()) {
            // CurseForge 没有批量接口，只能并发逐个查
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(ids.size());
            for (String id : ids) {
                Http.pool().execute(() -> {
                    try {
                        depProjectsCf.put(id, CurseForge.projectInfo(Long.parseLong(id)));
                    } catch (Exception e) {
                        data.log("未找到 ID " + id + " 的前置信息：" + e.getMessage());
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
            // 拉完再串行填，并发写列表会丢数据
            for (String id : ids) {
                if (depProjectsCf.containsKey(id)) fillDependencies(list, id);
            }
        } else {
            try {
                for (Object o : Modrinth.projectsInfo(ids)) {
                    Map<String, Object> m = Json.obj(o);
                    String id = Json.str(m, "project_id", "");
                    if (id.isEmpty()) continue;
                    depProjects.put(id, new String[]{
                            Json.str(m, "title", Json.str(m, "slug", id)),
                            Json.str(m, "slug", ""),
                            Json.str(m, "description", ""),
                            Json.str(m, "icon_url", ""),
                            firstType(m, info.projectType)});
                    fillDependencies(list, id);
                }
            } catch (Exception e) {
                data.log("前置项目信息获取失败：" + e.getMessage());
            }
            for (String id : ids) {
                if (!depProjects.containsKey(id)) data.log("未找到 ID " + id + " 的前置信息");
            }
        }
    }

    /** 把前置 ID 填进文件的 Dependencies。 */
    private void fillDependencies(List<ResourceVersion> list, String depId) {
        for (ResourceVersion v : list) {
            if (v.rawDependencies.contains(depId) && !v.dependencies.contains(depId)) {
                v.dependencies.add(depId);
            }
        }
    }

    private String firstType(Map<String, Object> hit, String fallback) {
        for (Object c : Json.arr(hit.get("project_type"))) {
            String t = String.valueOf(c);
            if (!t.isEmpty()) return t;
        }
        return fallback;
    }

    /** 当前类型的版本集合。 */
    private List<ResourceVersion> getResults() {
        List<ResourceVersion> results = allVersions;
        String target = expectedType();
        List<ResourceVersion> out = new ArrayList<>();
        if (target.equals("shader") || target.equals("resourcepack")) {
            return new ArrayList<>(results); // 光影和资源包不筛选，筛了会滤掉原版光影
        }
        for (ResourceVersion r : results) {
            if (r.resourceType.equals(target)) out.add(r);
        }
        return out;
    }

    private void loadFinished() {
        List<ResourceVersion> results = getResults();
        panLoadHost.setVisible(false);
        panLoadHost.setManaged(false);

        // 筛选器达到 9 个就升级分组规则
        boolean[] dropFlags = {false, false, true, true};
        boolean[] foldFlags = {false, true, false, true};
        int chosen = dropFlags.length - 1;
        for (int i = 0; i < dropFlags.length; i++) {
            if (buildFilters(results, dropFlags[i], foldFlags[i]).size() < 9) {
                chosen = i;
                break;
            }
        }
        groupedDrop = dropFlags[chosen];
        groupedFold = foldFlags[chosen];
        List<String> filters = buildFilters(results, groupedDrop, groupedFold);

        if (filters.size() < 2) {
            cardFilter.setVisible(false);
            cardFilter.setManaged(false);
            versionFilter = null;
        } else {
            cardFilter.setVisible(true);
            cardFilter.setManaged(true);
            filters.add(0, "全部");
            panFilter.getChildren().clear();
            final List<String> chosenFilters = filters;
            // 优先选目标版本所在分组
            String autoGroup = null;
            if (!info.targetVersion.isEmpty()) {
                boolean has = false;
                for (ResourceVersion v : results) {
                    if (v.gameVersions.contains(info.targetVersion)) {
                        has = true;
                        break;
                    }
                }
                if (has) autoGroup = ResourceVersion.groupedName(info.targetVersion, groupedDrop, groupedFold);
            }
            String toCheck = autoGroup != null ? autoGroup : "全部";
            versionFilter = toCheck.equals("全部") ? null : toCheck;
            for (String f : chosenFilters) {
                panFilter.getChildren().add(filterButton(f, f.equals(toCheck)));
            }
        }
        updateFilterResult();
    }

    private List<String> buildFilters(List<ResourceVersion> results, boolean byDrop, boolean foldOld) {
        List<String> out = new ArrayList<>();
        for (ResourceVersion v : results) {
            for (String gv : v.gameVersions) {
                String g = ResourceVersion.groupedName(gv, byDrop, foldOld);
                if (!out.contains(g)) out.add(g);
            }
        }
        out.sort((a, b) -> ResourceVersion.compareVersion(b, a));
        return out;
    }

    private Node filterButton(String text, boolean checked) {
        StackPane btn = new StackPane();
        Label lab = Ui.label(text, 12, checked ? Color.WHITE : Theme.text);
        btn.getChildren().add(lab);
        btn.setPadding(new Insets(5, 12, 5, 12));
        btn.setCursor(Cursor.HAND);
        applyFilterBg(btn, checked);
        btn.setOnMouseClicked(e -> {
            versionFilter = text.equals("全部") ? null : text;
            for (Node n : panFilter.getChildren()) {
                applyFilterBg(n, n == btn);
                if (n instanceof StackPane sp && sp.getChildren().get(0) instanceof Label l) {
                    l.setTextFill(n == btn ? Color.WHITE : Theme.text);
                }
            }
            updateFilterResult();
        });
        return btn;
    }

    private void applyFilterBg(Node n, boolean checked) {
        if (n instanceof Region r) {
            r.setBackground(Ui.fill(checked ? Theme.accent : Theme.cardBg, 7));
        }
    }

    private void updateFilterResult() {
        List<ResourceVersion> results = getResults();
        String targetCardName = "";
        if (!info.targetVersion.isEmpty() || !info.targetLoader.isEmpty()) {
            targetCardName = (info.targetLoader.isEmpty() ? "" : cap(info.targetLoader) + " ")
                    + info.targetVersion + "（所选版本）";
        }
        Map<String, List<ResourceVersion>> dict = new TreeMap<>(cardSorter(targetCardName));
        dict.put("其他", new ArrayList<>());
        for (ResourceVersion v : results) {
            for (String gv : v.gameVersions) {
                if (versionFilter != null
                        && !ResourceVersion.groupedName(gv, groupedDrop, groupedFold).equals(versionFilter)) {
                    continue;
                }
                String verName = ResourceVersion.groupedName(gv, false, false);
                // 至少两个加载器且是 Mod 时才按加载器拆卡
                List<String> loaders = new ArrayList<>();
                if (info.loaders().size() > 1 && v.resourceType.equals("mod") && ResourceVersion.isStandard(verName)) {
                    for (String l : v.loaders) {
                        if (info.loaders().contains(l.toLowerCase())) loaders.add(cap(l) + " ");
                    }
                }
                if (loaders.isEmpty()) loaders.add("");
                for (String loader : loaders) {
                    String key = loader + verName;
                    List<ResourceVersion> bucket = dict.computeIfAbsent(key, k -> new ArrayList<>());
                    if (!bucket.contains(v)) bucket.add(v);
                }
            }
        }
        if (!targetCardName.isEmpty()
                && (versionFilter == null
                || ResourceVersion.groupedName(info.targetVersion, groupedDrop, groupedFold).startsWith(versionFilter))) {
            List<ResourceVersion> targetList = dict.computeIfAbsent(targetCardName, k -> new ArrayList<>());
            for (ResourceVersion v : results) {
                if (!v.gameVersions.contains(info.targetVersion)) continue;
                if (!info.targetLoader.isEmpty()
                        && !v.loaders.contains(info.targetLoader)
                        && !v.loaders.isEmpty()) continue;
                if (!targetList.contains(v)) targetList.add(v);
            }
        }
        panResults.getChildren().clear();
        List<Ui.Card> cards = new ArrayList<>();
        for (Map.Entry<String, List<ResourceVersion>> pair : dict.entrySet()) {
            if (pair.getValue().isEmpty()) continue;
            // 跳过与目标卡同名的基础卡
            if (pair.getKey().equals(targetCardName.replace("（所选版本）", ""))) continue;
            Ui.Card card = Ui.card(pair.getKey());
            card.setCollapsible(true);
            card.setContentPadding(new Insets(4, 18, 10, 18));
            VBox stack = new VBox();
            stack.setSpacing(2);
            buildCardContent(stack, pair.getValue());
            card.add(stack);
            // 目标卡展开，其余折叠
            if (pair.getKey().equals(targetCardName)) card.setCollapsed(false);
            else card.setCollapsed(true);
            cards.add(card);
            if (pair.getKey().equals("其他")) {
                ((VBox) card.content.getChildren().get(0)).getChildren().add(0,
                        Ui.hint("由于版本信息更新缓慢，可能无法识别刚更新的 MC 版本。几天后即可正常识别。", 1));
            }
        }
        if (cards.size() == 1) cards.get(0).setCollapsed(false);
        panResults.getChildren().addAll(cards);
        if (cards.isEmpty()) {
            panResults.getChildren().add(Ui.hint("没有适用于当前筛选的版本。", 0));
        }
        updateHintAlternative();
    }

    /** 排序：置顶、特殊版本后置、版本号降序。 */
    private Comparator<String> cardSorter(String topmost) {
        return (x, y) -> {
            if (x.equals(y)) return 0;
            if (x.equals(topmost)) return -1;
            if (y.equals(topmost)) return 1;
            boolean sx = !x.contains(".");
            boolean sy = !y.contains(".");
            if (sx && sy) return x.compareTo(y);
            if (sx) return 1;
            if (sy) return -1;
            int c = ResourceVersion.compareVersion(stripFirstWord(y), stripFirstWord(x));
            if (c != 0) return c;
            return y.compareTo(x);
        };
    }

    /** 去掉首个空格前的词。 */
    private static String stripFirstWord(String s) {
        int i = s.indexOf(' ');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private void buildCardContent(VBox stack, List<ResourceVersion> versions) {
        List<ResourceVersion> sorted = new ArrayList<>(versions);
        sorted.sort((a, b) -> b.releaseDate.compareToIgnoreCase(a.releaseDate));
        boolean hasDuplicates = distinctCount(sorted, v -> v.display) != sorted.size();
        // 前置信息，整合包跳过
        List<String> deps = new ArrayList<>();
        if (!expectedType().equals("modpack")) {
            for (ResourceVersion v : sorted) {
                for (String d : v.dependencies) {
                    if (!deps.contains(d)) deps.add(d);
                }
            }
        }
        if (!deps.isEmpty()) {
            stack.getChildren().add(sectionLabel("前置资源", new Insets(6, 2, 5, 6)));
            for (String d : deps) {
                Node item = depRow(d);
                if (item != null) stack.getChildren().add(item);
            }
            stack.getChildren().add(sectionLabel("版本列表", new Insets(12, 2, 5, 6)));
        }
        for (ResourceVersion v : sorted) {
            stack.getChildren().add(versionRow(v, hasDuplicates));
        }
        Region tail = new Region();
        tail.setPrefHeight(10);
        stack.getChildren().add(tail);
    }

    private Label sectionLabel(String text, Insets margin) {
        Label lab = Ui.label(text, 14, Theme.text);
        VBox.setMargin(lab, margin);
        return lab;
    }

    /** 版本文件行。 */
    private Node versionRow(ResourceVersion v, boolean isBadDisplay) {
        Node icon = releaseIcon(v.releaseType);
        Ui.ListItem row = new Ui.ListItem(icon, v.title(isBadDisplay), v.infoLine(isBadDisplay), 42);
        if (expectedType().equals("modpack")) {
            // 整合包点整行即安装
            row.setOnMouseClicked(e -> installModpack(v));
            StackPane save = Ui.iconButton("save", 28, 15, "另存为", () -> save(v));
            save.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            row.addAction(save);
        } else {
            row.setOnMouseClicked(e -> save(v));
        }
        return row;
    }

    private Node depRow(String depId) {
        String name, slug = "", desc = "", icon = "", type = info.projectType;
        if (info.isCurseForge()) {
            CurseForge.Project p = depProjectsCf.get(depId);
            if (p == null) return null;
            name = p.name;
            slug = p.slug;
            desc = p.summary;
            icon = p.icon;
        } else {
            String[] d = depProjects.get(depId);
            if (d == null) return null;
            name = d[0];
            slug = d[1];
            desc = d[2];
            icon = d[3];
            type = d[4].isEmpty() ? "mod" : d[4];
        }
        String fSlug = slug, fName = name, fType = type, fIcon = icon, fDesc = desc;
        Ui.ListItem row = new Ui.ListItem(iconNode(fIcon, 30), fName, fDesc, 44);
        row.setOnMouseClicked(e -> {
            ResourceInfo dep = info.isCurseForge()
                    ? ResourceInfo.curseforge(Long.parseLong(depId), fSlug, fName, fType)
                    : ResourceInfo.modrinth(depId, fSlug, fName, fType);
            dep.targetVersion = info.targetVersion;
            dep.targetLoader = info.targetLoader;
            win.pendingResource = dep;
            win.reloadPage();
        });
        return row;
    }

    private Node releaseIcon(int type) {
        return switch (type) {
            case 0 -> Theme.icon("circle-check", 17, Color.web("#1f7a45"));
            case 1 -> Theme.icon("flask-conical", 17, Color.web("#9a6512"));
            default -> Theme.icon("flask-conical", 17, Color.web("#a52a2a"));
        };
    }

    private void updateHintAlternative() {
        String target = expectedType();
        String other = target.equals("mod") ? "datapack" : target.equals("datapack") ? "mod" : "";
        boolean show = false;
        if (!other.isEmpty() && allVersions != null) {
            // 另一类型有版本才显示
            for (ResourceVersion v : allVersions) {
                if (v.resourceType.equals(other)) {
                    show = true;
                    break;
                }
            }
        }
        hintAlternative.setVisible(show);
        hintAlternative.setManaged(show);
        if (show) {
            hintAlternativeText.setText(target.equals("mod")
                    ? "以下是该项目的 Mod 版本。点击这里查看其数据包版本。"
                    : "以下是该项目的数据包版本。点击这里查看其 Mod 版本。");
        }
    }

    private void hintAlternativeClick() {
        String target = expectedType();
        info.resourceType = target.equals("mod") ? "datapack" : "mod";
        win.reloadPage();
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

    /** 保存子目录，数据包为空串。 */
    private String subFolder(String type) {
        return switch (type) {
            case "modpack" -> "modpacks";
            case "datapack" -> "";
            case "resourcepack" -> "resourcepacks";
            case "shader" -> "shaderpacks";
            case "world" -> "saves";
            default -> "mods";
        };
    }

    /** 实例加载器转 Modrinth 名，原版返回空串。 */
    private String instanceLoaderKey(Data.Instance ins) {
        if (ins == null || ins.loader == null || ins.loader.isEmpty() || ins.loader.equals("原版")) return "";
        return ins.loader.toLowerCase();
    }

    /** 任一支持版本匹配即可。 */
    private boolean versionCompatible(List<String> gameVersions, String vanilla) {
        if (gameVersions.isEmpty() || gameVersions.contains("未知版本")) return true;
        if (vanilla == null || vanilla.isEmpty()) return true;
        if (gameVersions.contains(vanilla)) return true;
        int drop = ResourceVersion.versionDrop(vanilla);
        if (drop == 209) return false;
        for (String v : gameVersions) {
            if (ResourceVersion.versionDrop(v) == drop) return true;
        }
        return false;
    }

    /** 实例文件夹，folder 为空时回退全局游戏目录。 */
    private String folderOf(Data.Instance ins) {
        return ins.folder != null && !ins.folder.isEmpty() ? ins.folder : data.gameDir;
    }

    private static long lastModified(String dir, String sub) {
        File f = new File(dir, sub);
        File[] arr = f.listFiles();
        long t = f.lastModified();
        if (arr != null) {
            for (File c : arr) t = Math.max(t, c.lastModified());
        }
        return t;
    }

    private boolean instanceSuitable(Data.Instance ins, ResourceVersion file, List<String> allowed) {
        if ((file.resourceType.equals("mod") || file.resourceType.equals("datapack"))
                && !versionCompatible(file.gameVersions, ins.version)) {
            return false;
        }
        if (allowed.isEmpty()) return true;
        String il = instanceLoaderKey(ins);
        if (il.isEmpty()) return false;
        for (String l : allowed) {
            if (l.equalsIgnoreCase(il)) return true;
        }
        return false;
    }

    /** 默认保存位置依次取缓存、当前实例、最近实例、MC 根目录。 */
    private String findDefaultFolder(ResourceVersion file) {
        String type = file.resourceType;
        String sub = subFolder(type);
        String cached = CACHED_FOLDER.get(type);
        if (cached != null) return cached;
        List<String> allowed = !file.loaders.isEmpty() ? file.loaders : info.loaders();
        Data.Instance current = data.instance();
        if (current != null && instanceSuitable(current, file, allowed)) {
            return Paths.get(folderOf(current), sub).toString();
        }
        // 按最近修改时间排序
        List<Data.Instance> suitable = new ArrayList<>();
        for (Data.Instance ins : data.instances) {
            if (instanceSuitable(ins, file, allowed)) suitable.add(ins);
        }
        if (!suitable.isEmpty()) {
            suitable.sort((a, b) -> Long.compare(lastModified(folderOf(b), sub), lastModified(folderOf(a), sub)));
            return Paths.get(folderOf(suitable.get(0)), sub).toString();
        }
        Platform.runLater(() -> win.toast("没有合适的版本", "当前 MC 文件夹中没有找到适配的游戏版本，已定位到根目录。", 2));
        return data.gameDir;
    }

    /** 保存或另存为。 */
    private void save(ResourceVersion file) {
        String type = file.resourceType;
        String desc = typeName(type);
        if (type.equals("modpack")) {
            // 整合包直接选保存位置
            Platform.runLater(() -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("选择保存位置");
                chooser.setInitialFileName(file.fileName);
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc + "文件", "*." + extOf(file)));
                File target = chooser.showSaveDialog(win.stage);
                if (target == null) return;
                startDownloadTask(desc, file, target.toPath(), new ArrayList<>());
            });
            return;
        }
        win.toast("正在查找适合的游戏版本……", info.name, 0);
        new Thread(() -> {
            try {
                String defaultFolder = findDefaultFolder(file);
                // 只对 Mod 检查前置
                List<DepDownload> depFiles = new ArrayList<>();
                List<String> unresolvedNames = new ArrayList<>();
                if (type.equals("mod") && !file.rawDependencies.isEmpty()) {
                    try {
                        collectMissingDependencies(file, defaultFolder, new java.util.HashSet<>(),
                                depFiles, unresolvedNames, 0);
                        if (!depFiles.isEmpty() || !unresolvedNames.isEmpty()) {
                            data.log("必需前置检查完成：" + depFiles.size() + " 个可自动下载，"
                                    + unresolvedNames.size() + " 个无法解析");
                        }
                    } catch (Exception e) {
                        data.log("检查 Mod 必需前置失败，跳过检查：" + e.getMessage());
                    }
                }
                // Mod 文件名里的 ~ 会导致 Mixin 加载失败
                String fileName = type.equals("mod") ? file.fileName.replace("~", "-") : file.fileName;
                final String folder = defaultFolder;
                final List<DepDownload> deps = depFiles;
                final List<String> unresolved = unresolvedNames;
                Platform.runLater(() -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("选择保存位置");
                    File init = new File(folder);
                    if (init.isDirectory()) chooser.setInitialDirectory(init);
                    chooser.setInitialFileName(fileName);
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc + "文件", "*." + extOf(file)));
                    File target = chooser.showSaveDialog(win.stage);
                    if (target == null) return;
                    if (!deps.isEmpty()) {
                        askDownloadDeps(deps, unresolved, file, target.toPath());
                    } else {
                        if (!unresolved.isEmpty()) {
                            win.toast("前置无法自动解析", "请手动下载：" + String.join("、", unresolved), 3);
                        }
                        startDownloadTask(desc, file, target.toPath(), new ArrayList<>());
                    }
                });
            } catch (Exception e) {
                data.log("保存资源文件失败：" + e.getMessage());
                Platform.runLater(() -> win.toast("保存失败", e.getMessage() == null ? "未知错误" : e.getMessage(), 3));
            }
        }, "TCL-ResourceDetail-Save").start();
    }

    /** 缺前置时的弹窗，可一并下载。 */
    private void askDownloadDeps(List<DepDownload> deps, List<String> unresolved, ResourceVersion file, Path target) {
        StringBuilder msg = new StringBuilder("该资源需要以下前置，但在目标文件夹中未发现它们：\n");
        for (int i = 0; i < deps.size(); i++) {
            msg.append(deps.get(i).name);
            if (i < deps.size() - 1) msg.append("、");
        }
        if (!unresolved.isEmpty()) {
            msg.append("；另有以下前置无法自动解析，需要手动下载：").append(String.join("、", unresolved));
        }
        msg.append("\n是否一并下载这些前置？");
        VBox card = new VBox(12);
        card.setPadding(new Insets(18, 20, 16, 20));
        card.setPrefWidth(430);
        card.setBackground(Ui.fill(Theme.dialogBg, 12));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label title = Ui.bold("发现缺失的前置", 17, Theme.text);
        Label body = Ui.label(msg.toString(), 12.5, Theme.gray1);
        body.setWrapText(true);
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        StackPane cancel = Ui.button("取消", Ui.BTN_NORMAL, win::closeModal);
        StackPane skip = Ui.button("仅下载原资源", Ui.BTN_NORMAL, () -> {
            win.closeModal();
            startDownloadTask(typeName(file.resourceType), file, target, new ArrayList<>());
        });
        StackPane ok = Ui.button("一并下载", Ui.BTN_THEME, () -> {
            win.closeModal();
            startDownloadTask(typeName(file.resourceType), file, target, deps);
        });
        buttons.getChildren().addAll(cancel, skip, ok);
        card.getChildren().addAll(title, body, buttons);
        win.showModal(card);
    }

    private static class DepDownload {
        final String name;
        final ResourceVersion version;

        DepDownload(String name, ResourceVersion version) {
            this.name = name;
            this.version = version;
        }
    }

    /** 建下载任务并启动。 */
    private void startDownloadTask(String desc, ResourceVersion file, Path target, List<DepDownload> deps) {
        // 记住这次的位置，下次默认用
        if (target.getParent() != null) {
            CACHED_FOLDER.put(file.resourceType, target.getParent().toString());
        }
        boolean openAfterDone = !file.resourceType.equals("modpack");
        Tasks.Task task = new Tasks.Task(desc + "下载：" + ResourceVersion.stripExtension(target.getFileName().toString()));
        task.subs.add(new Tasks.SubTask("下载文件"));
        boolean hasDeps = !deps.isEmpty();
        if (hasDeps) task.subs.add(new Tasks.SubTask("下载前置文件"));
        String ext = extOf(file).toLowerCase();
        boolean worldZip = file.resourceType.equals("world") && (ext.equals("zip") || ext.equals("mca"));
        if (worldZip) task.subs.add(new Tasks.SubTask("安装世界存档"));
        Tasks.INSTANCE.add(task);
        win.openPage("tasks", true);
        new Thread(() -> {
            try {
                task.subs.get(0).state = Tasks.State.RUNNING;
                task.stage = "下载 " + file.fileName;
                tryDownload(task, file.downloadUrls, target, file.hash);
                task.subs.get(0).state = Tasks.State.DONE;
                if (hasDeps) {
                    task.subs.get(1).state = Tasks.State.RUNNING;
                    task.stage = "下载前置文件";
                    Path dir = target.getParent();
                    int i = 0;
                    for (DepDownload d : deps) {
                        if (task.canceled) throw new java.io.IOException("已取消");
                        Path depTarget = dir.resolve(d.version.fileName.replace("~", "-"));
                        tryDownload(task, d.version.downloadUrls, depTarget, d.version.hash);
                        i++;
                        task.subs.get(1).progress = i / (double) deps.size();
                    }
                    task.subs.get(1).state = Tasks.State.DONE;
                }
                if (worldZip) {
                    // 解到 saves 后删压缩包，MC 只认解开的目录
                    task.subs.get(2).state = Tasks.State.RUNNING;
                    task.stage = "解压世界存档";
                    unzipTo(target, target.getParent());
                    Files.deleteIfExists(target);
                    task.subs.get(2).state = Tasks.State.DONE;
                }
                task.state = Tasks.State.DONE;
                task.progress = 1;
                task.stage = "下载完成";
                data.log("已下载资源：" + file.fileName + " → " + target);
                Platform.runLater(() -> {
                    win.toast("下载完成", file.fileName, 1);
                    if (openAfterDone) openFolderQuiet(target.getParent());
                });
            } catch (Exception e) {
                if (task.canceled) {
                    task.state = Tasks.State.CANCELED;
                    task.stage = "已取消";
                } else {
                    task.state = Tasks.State.FAILED;
                    task.stage = e.getMessage() == null ? "下载失败" : e.getMessage();
                    task.error = task.stage;
                    Platform.runLater(() -> win.toast("下载失败", task.stage, 3));
                }
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-ResourceSave-" + file.fileName).start();
    }

    /** 多地址回退下载。 */
    private void tryDownload(Tasks.Task task, List<String> urls, Path target, String sha1) throws Exception {
        Exception last = null;
        for (String url : urls) {
            try {
                Modrinth.downloadUrl(task, url, target, sha1);
                return;
            } catch (Exception e) {
                if (task.canceled) throw e;
                last = e;
            }
        }
        if (last != null) throw last;
        throw new java.io.IOException("没有可用的下载地址");
    }

    /** 解压 zip 到目录。 */
    private static void unzipTo(Path zip, Path targetDir) throws java.io.IOException {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) continue; // 防路径穿越
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (var in = zf.getInputStream(entry); var outStream = Files.newOutputStream(out)) {
                        in.transferTo(outStream);
                    }
                }
            }
        }
    }

    private static String extOf(ResourceVersion file) {
        String n = file.fileName == null ? "" : file.fileName;
        int i = n.lastIndexOf('.');
        if (file.resourceType.equals("mod")) return i >= 0 ? n.substring(i + 1) : "jar";
        return i >= 0 ? n.substring(i + 1) : "zip";
    }

    private void collectMissingDependencies(ResourceVersion version, String targetFolder,
                                            java.util.Set<String> visited, List<DepDownload> depFiles,
                                            List<String> unresolvedNames, int depth) {
        if (depth > 32) return; // 防循环依赖
        for (String depId : version.rawDependencies) {
            if (depId == null || depId.trim().isEmpty()) continue;
            String key = info.platform + ":" + depId;
            if (!visited.add(key)) continue; // 访问去重
            try {
                String depName;
                if (info.isCurseForge()) {
                    CurseForge.Project p = depProjectsCf.get(depId);
                    if (p == null) {
                        unresolvedNames.add(depId);
                        continue;
                    }
                    depName = p.name;
                } else {
                    String[] p = depProjects.get(depId);
                    if (p == null) {
                        unresolvedNames.add(depId);
                        continue;
                    }
                    depName = p[0];
                }
                if (isDependencyInstalled(targetFolder, depId, depName)) continue; // 已装就算满足
                ResourceVersion depVersion = selectDependencyVersion(depId, version, targetFolder);
                if (depVersion == null) {
                    data.log("前置 " + depName + " 没有适用于目标的版本，视为无法解析");
                    unresolvedNames.add(depName);
                    continue;
                }
                boolean exists = false;
                for (DepDownload d : depFiles) {
                    if (d.version.id.equals(depVersion.id)) exists = true;
                }
                if (!exists) depFiles.add(new DepDownload(depName, depVersion));
                collectMissingDependencies(depVersion, targetFolder, visited, depFiles, unresolvedNames, depth + 1);
            } catch (Exception e) {
                data.log("解析前置失败，已跳过：" + depId + " " + e.getMessage());
            }
        }
    }

    /** 用文件名相似度判断前置是否已装，宁可漏判。 */
    private boolean isDependencyInstalled(String folder, String depId, String depName) {
        try {
            if (folder == null || folder.isEmpty()) return false;
            File dir = new File(folder);
            if (!dir.isDirectory()) return false;
            String slug = "";
            if (info.isCurseForge()) {
                CurseForge.Project p = depProjectsCf.get(depId);
                if (p != null) slug = p.slug;
            } else {
                String[] p = depProjects.get(depId);
                if (p != null) slug = p[1];
            }
            List<String> keys = new ArrayList<>();
            for (String raw : new String[]{slug, depName}) {
                if (raw == null || raw.isEmpty()) continue;
                String k = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (k.length() >= 4) keys.add(k); // 过短的键容易误匹配
            }
            if (keys.isEmpty()) return false;
            File[] files = dir.listFiles();
            if (files == null) return false;
            for (File f : files) {
                String name = f.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                for (String k : keys) {
                    if (name.contains(k)) return true;
                }
            }
            return false;
        } catch (Exception e) {
            data.log("检查前置是否已安装失败：" + e.getMessage());
            return false;
        }
    }

    /** 按发行类型和时间挑前置版本。 */
    private ResourceVersion selectDependencyVersion(String depId, ResourceVersion mainFile, String targetFolder) throws Exception {
        List<ResourceVersion> versions = new ArrayList<>();
        if (info.isCurseForge()) {
            for (Object o : CurseForge.projectFiles(Long.parseLong(depId))) {
                ResourceVersion v = ResourceVersion.fromCurseForgeJson(Json.obj(o), expectedType());
                if (!v.downloadUrls.isEmpty()) versions.add(v);
            }
        } else {
            for (Object o : Modrinth.projectVersions(depId)) {
                versions.add(ResourceVersion.fromModrinthJson(Json.obj(o), expectedType()));
            }
        }
        // 按目标文件夹反查所属实例
        String instanceVanilla = null;
        String instanceLoader = "";
        if (targetFolder != null && !targetFolder.isEmpty()) {
            String absTarget = Paths.get(targetFolder).toAbsolutePath().toString();
            for (Data.Instance ins : data.instances) {
                String modsDir = Paths.get(folderOf(ins), "mods").toAbsolutePath().toString();
                if (modsDir.equals(absTarget)) {
                    instanceVanilla = ins.version;
                    instanceLoader = instanceLoaderKey(ins);
                    break;
                }
            }
        }
        String effectiveLoader = !instanceLoader.isEmpty() ? instanceLoader : mainFile.loaders.isEmpty() ? "" : mainFile.loaders.get(0);
        ResourceVersion best = null;
        for (ResourceVersion v : versions) {
            if (v.downloadUrls.isEmpty()) continue;
            if (!v.loaders.isEmpty() && !effectiveLoader.isEmpty() && !v.loaders.contains(effectiveLoader)) continue;
            boolean ok;
            if (instanceVanilla == null) {
                ok = false; // 没实例时要求和主文件支持同一版本
                for (String gv : v.gameVersions) {
                    if (mainFile.gameVersions.contains(gv)) {
                        ok = true;
                        break;
                    }
                }
            } else {
                ok = versionCompatible(v.gameVersions, instanceVanilla);
            }
            if (!ok) continue;
            if (best == null) {
                best = v;
                continue;
            }
            int rankC = Integer.compare(ResourceVersion.releaseTypeRank(v.releaseType), ResourceVersion.releaseTypeRank(best.releaseType));
            if (rankC < 0 || (rankC == 0 && v.releaseDate.compareToIgnoreCase(best.releaseDate) > 0)) {
                best = v;
            }
        }
        return best;
    }

    private void installModpack(ResourceVersion file) {
        // 清洗版本名
        String packName = info.name
                .replaceAll("\\.(zip|rar|mrpack)$", "")
                .replace("\\", "＼").replace("/", "／").replace("|", "｜")
                .replace(":", "：").replace("<", "＜").replace(">", "＞")
                .replace("*", "＊").replace("?", "？").replace("\"", "")
                .replace("： ", "：");
        Dialogs.input(win, "输入版本名称", "", packName, name -> {
            if (name == null || name.trim().isEmpty()) return;
            Modpacks.installFromNetwork(win, file, info, name.trim());
        });
    }

    private String websiteOf() {
        if (info.website != null && !info.website.isEmpty()) return info.website;
        if (info.isCurseForge()) {
            String kind = switch (info.projectType) {
                case "world" -> "worlds";
                case "resourcepack" -> "texture-packs";
                case "shader" -> "shaders";
                case "modpack" -> "modpacks";
                default -> "mc-mods";
            };
            return "https://www.curseforge.com/minecraft/" + kind + "/" + (info.slug.isEmpty() ? info.projectId : info.slug);
        }
        return "https://modrinth.com/" + (info.projectType.isEmpty() ? "mod" : info.projectType) + "/"
                + (info.slug.isEmpty() ? info.projectId : info.slug);
    }

    private void openUrl(String url) {
        try {
            Shell.openUrl(url);
        } catch (Exception e) {
            win.toast("无法打开网页", url, 2);
        }
    }

    private void copyName() {
        String text = info.name + (info.slug.isEmpty() || info.slug.equals(info.name) ? "" : " " + info.slug);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null);
        win.toast("已复制名称", text, 1);
    }

    private Data.Favorite findFavorite() {
        String type = info.projectType.isEmpty() ? "mod" : info.projectType;
        String slug = info.slug.isEmpty() ? info.name : info.slug;
        for (Data.Favorite f : data.favorites) {
            if (f.type.equals(type) && f.slug.equals(slug)) return f;
        }
        return null;
    }

    private boolean favoriteNow() {
        return findFavorite() != null;
    }

    private void toggleFavorite() {
        Data.Favorite existing = findFavorite();
        if (existing != null) {
            data.favorites.remove(existing);
            data.save();
            win.toast("已取消收藏", info.name, 1);
            setButtonText(favButton, "收藏");
        } else {
            Data.Favorite fav = Data.Favorite.of(info.projectType, info.slug, info.name, info.desc, info.icon, websiteOf());
            fav.platform = info.platform == null ? "" : info.platform;
            fav.projectId = info.projectId == null ? "" : info.projectId;
            data.favorites.add(fav);
            data.save();
            win.toast("已加入收藏夹", info.name, 1);
            setButtonText(favButton, "取消收藏");
        }
    }

    private void setButtonText(StackPane button, String text) {
        for (Node n : button.getChildren()) {
            if (n instanceof Label l) l.setText(text);
        }
    }

    private Node iconNode(String url, double size) {
        // 远程图走 RemoteImages，本地图直接读资源
        String u = url == null ? "" : url.trim();
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setBackground(Ui.fill(Theme.halfWhite, 8));
        if (!u.startsWith("http")) {
            box.getChildren().add(Ui.image(u.isEmpty() ? "NoIcon.png" : u, size, size, true));
            return box;
        }
        javafx.scene.image.Image cached = RemoteImages.cached(u, size);
        if (cached != null) {
            box.getChildren().add(imageView(cached, size));
            return box;
        }
        box.getChildren().add(Ui.image("NoIcon.png", size, size, true));
        if (RemoteImages.failed(u)) return box;
        RemoteImages.load(u, size, img -> {
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

    /** 静默打开文件所在文件夹。 */
    private void openFolderQuiet(Path dir) {
        if (dir == null) return;
        try {
            Shell.openFolder(dir.toString());
        } catch (Exception e) {
            data.log("打开文件夹失败：" + dir + " " + e.getMessage());
        }
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static int distinctCount(List<ResourceVersion> list, java.util.function.Function<ResourceVersion, String> key) {
        List<String> seen = new ArrayList<>();
        for (ResourceVersion v : list) {
            String k = key.apply(v);
            if (!seen.contains(k)) seen.add(k);
        }
        return seen.size();
    }
}
