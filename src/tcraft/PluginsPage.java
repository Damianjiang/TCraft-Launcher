package tcraft;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import tcraft.api.PluginInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PluginsPage {

    public static final String DOCS = "https://damianjiang.github.io/TCraft-Launcher/wiki/";
    private static boolean sidebarScan;

    private PluginsPage() {
    }

    public static void sidebarRefresh(MainWindow win) {
        Plugins.get().markLoading();
        sidebarScan = true;
        win.refreshRight(true);
    }

    public static void build(MainWindow win, VBox page) {
        List<Plugins.LoadedPlugin> all = new ArrayList<>(Plugins.get().list());
        int loaded = 0;
        for (Plugins.LoadedPlugin p : all) if ("loaded".equals(p.state)) loaded++;
        header(page, "插件管理", Lang.tf("共 %d 个，已启用 %d 个。插件可以添加页面、按钮、命令与弹窗。", all.size(), loaded));

        if (sidebarScan) {
            sidebarScan = false;
            PauseTransition scan = new PauseTransition(Duration.millis(80));
            scan.setOnFinished(e -> {
                Plugins.get().loadAll();
                win.refreshRight();
            });
            scan.play();
        }

        Ui.Card card = Ui.card("");
        card.setSpacing(10);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));
        page.getChildren().add(card);

        VBox rows = new VBox(0);
        Ui.SearchBox search = Ui.searchBox("搜索插件");
        StackPane importBtn = Ui.button("导入插件", Ui.BTN_HIGHLIGHT, () -> importPlugin(win));
        importBtn.setMinWidth(84);
        HBox searchRow = new HBox(8, search, importBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        card.add(searchRow);

        ComboBox<String> state = Ui.combo(new String[]{"全部", "已启用", "已禁用", "加载失败"}, 0);
        state.setPrefWidth(100);
        state.setMinWidth(100);
        StackPane docs = Ui.iconButton("external-link", 28, 14, "插件开发文档站", () -> Shell.openUrl(DOCS));
        StackPane folder = Ui.iconButton("folder-open", 28, 14, "打开插件文件夹", () -> {
            try {
                Files.createDirectories(Plugins.get().dir());
                Shell.openFolder(Plugins.get().dir().toString());
            } catch (Exception e) {
                win.toast("无法打开", e.getMessage(), 3);
            }
        });
        StackPane refresh = Ui.iconButton("refresh-cw", 28, 14, "刷新插件", () -> refresh(win, rows));
        HBox tools = new HBox(6, state, Ui.spacer(), docs, folder, refresh);
        tools.setAlignment(Pos.CENTER_LEFT);
        card.add(tools);

        int[] pageSize = {Math.max(10, Data.get().getInt("UiPageSizePlugins", 10))};
        ComboBox<String> sizeBox = Ui.pageSizeBox(pageSize[0]);
        HBox sizeRow = new HBox(6, Ui.label("每页", 11.5, Theme.gray2), sizeBox);
        card.add(sizeRow);

        VBox warn = new VBox(Ui.hint("插件拥有完整权限，可以读写文件、联网并执行任意代码。只安装你信任的插件。", 1));
        VBox.setMargin(warn, new Insets(2, 0, 4, 0));
        card.add(warn);
        card.add(rows);

        String[] filter = {"all"};
        String[] query = {""};
        int[] pageNo = {0};
        Runnable[] render = new Runnable[1];
        sizeBox.setOnAction(e -> {
            pageSize[0] = Integer.parseInt(sizeBox.getValue());
            Data.get().set("UiPageSizePlugins", pageSize[0]);
            pageNo[0] = 0;
            render[0].run();
        });
        render[0] = () -> {
            rows.getChildren().clear();
            if (!Plugins.get().isLoaded()) {
                showLoading(rows);
                return;
            }
            List<Plugins.LoadedPlugin> filtered = new ArrayList<>();
            for (Plugins.LoadedPlugin p : all) {
                if (!"all".equals(filter[0]) && !filter[0].equals(normalState(p.state))) continue;
                if (!query[0].isEmpty() && !searchText(p).contains(query[0])) continue;
                filtered.add(p);
            }
            if (filtered.isEmpty()) {
                String text;
                if (all.isEmpty()) {
                    text = "还没有安装任何插件。\n点击「导入插件」选择 .zip 或 .jar；也可以把插件文件夹放进 "
                            + Plugins.get().dir() + " 后点击刷新。";
                } else {
                    text = "没有匹配的插件。";
                }
                Label empty = Ui.label(text, 12.5, Theme.gray2);
                empty.setWrapText(true);
                empty.setMaxWidth(Double.MAX_VALUE);
                rows.getChildren().add(empty);
                return;
            }
            int pages = Math.max(1, (filtered.size() + pageSize[0] - 1) / pageSize[0]);
            pageNo[0] = Math.min(pageNo[0], pages - 1);
            int from = pageNo[0] * pageSize[0];
            int to = Math.min(from + pageSize[0], filtered.size());
            for (int i = from; i < to; i++) rows.getChildren().add(pluginRow(win, filtered.get(i)));
            if (pages > 1) {
                rows.getChildren().add(Ui.pager(pageNo[0], pages, filtered.size(), targetPage -> {
                    pageNo[0] = targetPage;
                    render[0].run();
                }));
            }
        };
        state.setOnAction(e -> {
            filter[0] = switch (state.getSelectionModel().getSelectedIndex()) {
                case 1 -> "loaded";
                case 2 -> "disabled";
                case 3 -> "failed";
                default -> "all";
            };
            pageNo[0] = 0;
            render[0].run();
        });
        search.textProperty().addListener((o, a, b) -> {
            query[0] = b == null ? "" : b.trim().toLowerCase(java.util.Locale.ROOT);
            pageNo[0] = 0;
            render[0].run();
        });
        render[0].run();
        Ui.dropFiles(page, files -> {
            if (files.isEmpty()) return;
            if (files.size() > 1) win.toast("插件导入", "一次拖入一个插件包", 2);
            importPlugin(win, files.get(0));
        });
    }

    private static String searchText(Plugins.LoadedPlugin p) {
        String name = p.manifest.name == null ? "" : p.manifest.name;
        String id = p.manifest.id == null ? "" : p.manifest.id;
        String author = p.manifest.author == null ? "" : p.manifest.author;
        String desc = p.manifest.description == null ? "" : p.manifest.description;
        String version = p.manifest.version == null ? "" : p.manifest.version;
        return (name + " " + id + " " + author + " " + desc + " " + version).toLowerCase(java.util.Locale.ROOT);
    }

    private static void showLoading(VBox rows) {
        Ui.Loading load = new Ui.Loading(200, 88, "正在加载插件");
        StackPane host = new StackPane(load);
        host.setMinHeight(130);
        host.setPrefHeight(130);
        host.setMaxWidth(Double.MAX_VALUE);
        rows.getChildren().add(host);
        load.start();
    }

    private static void refresh(MainWindow win, VBox rows) {
        rows.getChildren().clear();
        showLoading(rows);
        // 先让镐子动画出一帧再扫描
        PauseTransition delay = new PauseTransition(Duration.millis(80));
        delay.setOnFinished(e -> {
            Plugins.get().loadAll();
            win.onPluginsChanged();
            win.toast("插件已刷新", Lang.tf("共 %d 个插件", Plugins.get().list().size()), 1);
        });
        delay.play();
    }

    private static String normalState(String state) {
        if ("loaded".equals(state)) return "loaded";
        if ("failed".equals(state)) return "failed";
        return "disabled";
    }

    private static String stateLabel(String state) {
        return switch (state) {
            case "loaded" -> Lang.t("已启用");
            case "failed" -> Lang.t("加载失败");
            case "removed" -> Lang.t("已删除");
            default -> Lang.t("已禁用");
        };
    }

    private static Node pluginRow(MainWindow win, Plugins.LoadedPlugin p) {
        boolean loaded = "loaded".equals(p.state);
        boolean failed = "failed".equals(p.state);
        String iconName = failed ? "triangle-alert" : p.manifest.icon;
        Node icon = Plugins.get().icon(p.manifest.id, iconName, 30,
                failed ? Theme.orange : loaded ? Theme.accent : Theme.gray4);
        if (icon instanceof javafx.scene.image.ImageView iv) {
            iv.setFitWidth(30);
            iv.setFitHeight(30);
            iv.setPreserveRatio(false);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(30, 30);
            clip.setArcWidth(9);
            clip.setArcHeight(9);
            iv.setClip(clip);
        }
        StringBuilder info = new StringBuilder();
        if (p.manifest.version != null && !p.manifest.version.isEmpty()) info.append("v").append(p.manifest.version);
        if (p.manifest.author != null && !p.manifest.author.isEmpty()) {
            if (info.length() > 0) info.append(" · ");
            info.append(p.manifest.author);
        }
        if (info.length() > 0) info.append(" · ");
        info.append(stateLabel(p.state));
        if (failed && p.error != null && !p.error.isEmpty()) {
            info.append(" · ").append(p.error.length() > 60 ? p.error.substring(0, 60) + "…" : p.error);
        }
        Ui.ListItem item = new Ui.ListItem(icon, p.manifest.name, info.toString(), 46);
        StackPane commands = Ui.listAction("package", 26, 15, "命令", () -> commands(win, p));
        StackPane toggle = Ui.listAction("power", 26, 15, loaded ? "禁用" : "启用",
                () -> toggle(win, p));
        StackPane delete = Ui.listAction("trash-2", 26, 15, "删除", () -> delete(win, p));
        if (!Plugins.get().commandsOf(p.manifest.id).isEmpty()) item.addAction(commands);
        item.addAction(toggle);
        item.addAction(delete);
        item.setOnMouseClicked(e -> detail(win, p));
        return item;
    }

    private static void toggle(MainWindow win, Plugins.LoadedPlugin p) {
        boolean loaded = "loaded".equals(p.state);
        Plugins.get().setEnabled(p.manifest.id, !loaded);
        win.toast(loaded ? "已禁用插件" : "已启用插件", p.manifest.name,
                loaded ? 0 : 1);
    }

    private static void openFolder(MainWindow win, Plugins.LoadedPlugin p) {
        try {
            Path target = p.directory ? p.source : p.source.getParent();
            Shell.openFolder(target.toString());
        } catch (Exception e) {
            win.toast("无法打开", String.valueOf(e.getMessage()), 3);
        }
    }

    private static void delete(MainWindow win, Plugins.LoadedPlugin p) {
        Dialogs.confirm(win, "删除插件", Lang.tf("确定要删除插件「%s」吗？\n插件目录会移入回收站，数据目录保留。", p.manifest.name),
                () -> {
                    String id = p.manifest.id;
                    Path src = Plugins.get().sourceOf(id);
                    Plugins.get().prepareRemove(id);
                    Thread worker = new Thread(() -> {
                        boolean ok = src != null && Shell.deleteToRecycleBin(src.toString());
                        Platform.runLater(() -> {
                            if (ok) {
                                Plugins.get().finishRemove(id);
                                win.toast("已删除", p.manifest.name, 1);
                            } else {
                                Plugins.get().refreshUi();
                                win.toast("删除失败", "文件被占用或回收站不可用，插件已禁用。", 2);
                            }
                        });
                    }, "TCL-Plugin-Delete");
                    worker.setDaemon(true);
                    worker.start();
                }, true);
    }

    private static void importPlugin(MainWindow win) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.t("选择插件包"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(Lang.t("插件包"), "*.zip", "*.jar"),
                new FileChooser.ExtensionFilter(Lang.t("所有文件"), "*.*"));
        File file = chooser.showOpenDialog(win.stage);
        if (file == null) return;
        importPlugin(win, file.toPath());
    }

    private static void importPlugin(MainWindow win, Path source) {
        PluginInfo info;
        try {
            info = Plugins.get().inspect(source);
        } catch (Exception e) {
            win.toast("无法读取插件", Log.describe(e), 3);
            return;
        }
        boolean exists = Plugins.get().find(info.id) != null;
        String text = Lang.t("插件：") + info.name + " " + info.version
                + "\n" + Lang.t("标识：") + info.id
                + (info.author.isEmpty() ? "" : "\n" + Lang.t("作者：") + info.author)
                + "\n\n" + Lang.t("插件拥有完整权限，可以读写文件、联网并执行任意代码。只安装你信任的插件。")
                + (exists ? "\n\n" + Lang.t("已存在同 id 插件，导入后将覆盖旧版本。") : "");
        Dialogs.confirm(win, exists ? Lang.t("覆盖安装插件") : Lang.t("导入插件"), text,
                () -> startInstall(win, source, info, exists), true);
    }

    private static void startInstall(MainWindow win, Path source, PluginInfo info, boolean replace) {
        if (replace) Plugins.get().release(info.id);
        int gen = win.importPanel.begin("正在导入插件", info.name, 0);
        win.importPanel.progress(gen, 0, 0, info.name);
        Thread worker = new Thread(() -> {
            try {
                String id = Plugins.get().install(source, replace);
                Platform.runLater(() -> win.importPanel.finish(gen, "插件已导入", () -> {
                    Plugins.get().afterInstall(id);
                    win.toast("插件已导入", info.name + " " + info.version, 1);
                }));
            } catch (Throwable t) {
                Platform.runLater(() -> win.importPanel.finish(gen, "插件导入失败", () ->
                        win.toast("导入插件失败", Log.describe(t), 3)));
            }
        }, "TCL-Plugin-Install");
        worker.setDaemon(true);
        worker.start();
    }

    private static void commands(MainWindow win, Plugins.LoadedPlugin p) {
        List<Plugins.CommandInfo> list = Plugins.get().commandsOf(p.manifest.id);
        if (list.isEmpty()) {
            win.toast("没有命令", Lang.tf("插件「%s」没有注册命令。", p.manifest.name), 0);
            return;
        }
        VBox card = dialogCard("插件命令 · " + p.manifest.name, 560);
        for (Plugins.CommandInfo c : list) {
            VBox row = new VBox(3);
            HBox top = new HBox(10);
            top.setAlignment(Pos.CENTER_LEFT);
            Label name = Ui.semi(c.name, 13, Theme.text);
            name.setMinWidth(190);
            TextField args = Ui.field("参数（可选）");
            HBox.setHgrow(args, Priority.ALWAYS);
            args.setPrefWidth(190);
            top.getChildren().addAll(name, args,
                    Ui.button("运行", Ui.BTN_NORMAL, () -> Plugins.get().runCommand(c.name, args.getText())));
            Label desc = Ui.label(c.description.isEmpty() ? " " : c.description, 11.5, Theme.gray2);
            row.getChildren().addAll(top, desc);
            row.setPadding(new Insets(7, 0, 7, 0));
            card.getChildren().add(row);
        }
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        VBox.setMargin(actions, new Insets(12, 0, 0, 0));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private static void detail(MainWindow win, Plugins.LoadedPlugin p) {
        VBox card = dialogCard(p.manifest.name, 600);
        javafx.scene.image.Image thumb = Plugins.get().thumbnail(p.manifest.id, 256);
        if (thumb != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(thumb);
            iv.setFitWidth(160);
            iv.setFitHeight(90);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(160, 90);
            clip.setArcWidth(10);
            clip.setArcHeight(10);
            iv.setClip(clip);
            StackPane cover = new StackPane(iv);
            cover.setMaxSize(Double.MAX_VALUE, 90);
            cover.setPrefHeight(90);
            cover.setAlignment(Pos.CENTER);
            VBox.setMargin(cover, new Insets(0, 0, 12, 0));
            card.getChildren().add(cover);
        }
        Label id = Ui.label(p.manifest.id, 11.5, Theme.gray3);
        card.getChildren().add(id);
        if (p.manifest.description != null && !p.manifest.description.isEmpty()) {
            Label desc = Ui.label(p.manifest.description, 12.5, Theme.gray2);
            desc.setWrapText(true);
            VBox.setMargin(desc, new Insets(10, 0, 12, 0));
            card.getChildren().add(desc);
        }
        card.getChildren().addAll(
                meta("版本", p.manifest.version),
                meta("作者", p.manifest.author.isEmpty() ? "—" : p.manifest.author),
                meta("API", String.valueOf(p.manifest.api) + " / " + Plugins.API_VERSION),
                meta("状态", stateLabel(p.state)),
                meta("路径", p.source.toString()));
        if ("failed".equals(p.state) && p.error != null && !p.error.isEmpty()) {
            Label err = Ui.label(p.error, 11.5, Theme.orange);
            err.setWrapText(true);
            VBox.setMargin(err, new Insets(10, 0, 0, 0));
            card.getChildren().add(err);
        }
        List<Plugins.CommandInfo> cmds = Plugins.get().commandsOf(p.manifest.id);
        if (!cmds.isEmpty()) {
            Label section = Ui.semi("已注册命令", 14, Theme.text);
            VBox.setMargin(section, new Insets(14, 0, 6, 0));
            card.getChildren().add(section);
            for (Plugins.CommandInfo c : cmds) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label n = Ui.label(c.name, 12.5, Theme.text);
                n.setMinWidth(200);
                Label d = Ui.label(c.description, 11.5, Theme.gray2);
                HBox.setHgrow(d, Priority.ALWAYS);
                row.getChildren().addAll(n, d,
                        Ui.button("运行", Ui.BTN_NORMAL, () -> Plugins.get().runCommand(c.name, "")));
                card.getChildren().add(row);
            }
        }
        List<Plugins.PageEntry> pages = new ArrayList<>();
        for (Plugins.PageEntry e : Plugins.get().pluginPages()) {
            if (e.owner.equals(p.manifest.id)) pages.add(e);
        }
        if (!pages.isEmpty()) {
            Label section = Ui.semi("已注册页面", 14, Theme.text);
            VBox.setMargin(section, new Insets(14, 0, 6, 0));
            card.getChildren().add(section);
            for (Plugins.PageEntry e : pages) {
                Label row = Ui.label("· " + e.title + "  (" + e.key + ")", 12, Theme.gray2);
                card.getChildren().add(row);
            }
        }
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(actions, new Insets(16, 0, 0, 0));
        actions.getChildren().addAll(
                Ui.button("重新加载", Ui.BTN_NORMAL, () -> {
                    Plugins.get().reload(p.manifest.id);
                    win.closeModal();
                    win.toast("已重新加载", p.manifest.name, 1);
                }),
                Ui.button("打开文件夹", Ui.BTN_NORMAL, () -> openFolder(win, p)),
                Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private static VBox dialogCard(String title, double width) {
        VBox card = new VBox(0);
        card.setPadding(new Insets(22, 22, 20, 22));
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.setBackground(Ui.fill(Theme.dialogBg, 9));
        card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
        Label t = Ui.semi(title, 22, Theme.accentDark);
        Region line = Ui.line(Theme.accentDark, 2);
        line.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(line, new Insets(8, 0, 12, 0));
        card.getChildren().addAll(t, line);
        return card;
    }

    private static Node meta(String key, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label k = Ui.label(key, 12, Theme.gray2);
        k.setMinWidth(70);
        Label v = Ui.label(value == null || value.isEmpty() ? "—" : value, 12, Theme.text);
        v.setWrapText(true);
        HBox.setHgrow(v, Priority.ALWAYS);
        row.getChildren().addAll(k, v);
        VBox.setMargin(row, new Insets(3, 0, 3, 0));
        return row;
    }

    private static void header(VBox page, String title, String subtitle) {
        VBox head = new VBox(5);
        Label sub = Ui.label(subtitle, 13, Theme.text);
        sub.setOpacity(0.55);
        head.getChildren().addAll(Ui.bold(title, 30, Theme.text), sub);
        VBox.setMargin(head, new Insets(0, 0, 18, 0));
        page.getChildren().add(head);
    }
}
