package tcraft;

import javafx.application.Platform;
import javafx.scene.Node;
import tcraft.api.CommandContext;
import tcraft.api.Event;
import tcraft.api.PageView;
import tcraft.api.PluginContext;
import tcraft.api.PluginHost;
import tcraft.api.PluginInfo;
import tcraft.api.TcraftPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// 插件管理器 支持目录插件和单 jar
public final class Plugins {

    public static final int API_VERSION = PluginContext.API_VERSION;
    private static final Plugins INSTANCE = new Plugins();
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final long MANIFEST_MAX = 1024L * 1024;
    private static final long IMAGE_MAX = 8L * 1024 * 1024;

    public static Plugins get() {
        return INSTANCE;
    }

    public static final class CommandInfo {
        public final String name;
        public final String description;
        public final String pluginId;
        public final String pluginName;

        CommandInfo(String name, String description, String pluginId, String pluginName) {
            this.name = name;
            this.description = description;
            this.pluginId = pluginId;
            this.pluginName = pluginName;
        }
    }

    public static final class PageEntry {
        public final String key;
        public final String title;
        public final String icon;
        public final String owner;

        PageEntry(String key, String title, String icon, String owner) {
            this.key = key;
            this.title = title;
            this.icon = icon;
            this.owner = owner;
        }
    }

    static final class PageDef {
        final String owner;
        final String title;
        final String icon;
        final Function<PageView, Node> builder;

        PageDef(String owner, String title, String icon, Function<PageView, Node> builder) {
            this.owner = owner;
            this.title = title;
            this.icon = icon;
            this.builder = builder;
        }
    }

    static final class Cmd {
        final String name;
        final String description;
        final String owner;
        final Consumer<CommandContext> handler;

        Cmd(String name, String description, String owner, Consumer<CommandContext> handler) {
            this.name = name;
            this.description = description;
            this.owner = owner;
            this.handler = handler;
        }
    }

    static final class Listener {
        final String event;
        final Consumer<Event> handler;

        Listener(String event, Consumer<Event> handler) {
            this.event = event;
            this.handler = handler;
        }
    }

    static final class Manifest {
        String id;
        String name;
        String version;
        String author = "";
        String description = "";
        String icon = "";
        String thumbnail = "";
        String main;
        String website = "";
        int api = 1;
        List<String> onInstall = new ArrayList<>();

        PluginInfo info(Path dataDir) {
            return new PluginInfo(id, name, version, author, description, icon, main, website, api, dataDir);
        }
    }

    // 插件状态 loaded / disabled / failed
    static final class LoadedPlugin implements PluginHost {
        final Manifest manifest;
        final Path source;
        final boolean directory;
        PluginContext ctx;
        URLClassLoader loader;
        TcraftPlugin instance;
        String state = "disabled";
        String error = "";
        boolean enabled;
        final long order;
        final List<Node> titleButtons = new ArrayList<>();
        final List<Node> toolButtons = new ArrayList<>();
        final List<Node> launchButtons = new ArrayList<>();
        final List<Node> floatButtons = new ArrayList<>();
        final List<String> pageKeys = new ArrayList<>();
        final List<String> commandNames = new ArrayList<>();
        final List<Listener> listeners = new ArrayList<>();
        Properties store;
        private PluginInfo info;

        LoadedPlugin(Manifest manifest, Path source, boolean directory, long order) {
            this.manifest = manifest;
            this.source = source;
            this.directory = directory;
            this.order = order;
        }

        @Override
        public PluginInfo info() {
            if (info == null) info = manifest.info(Plugins.get().dataDir(manifest.id));
            return info;
        }

        @Override
        public void addPage(String id, String title, String icon, Function<PageView, Node> builder) {
            String pid = Plugins.get().safeId(id, "页面 id");
            String key = manifest.id + ":" + pid;
            if (Plugins.get().pages.containsKey(key)) {
                throw new IllegalArgumentException(Lang.t("页面 id 已被占用：") + pid);
            }
            Plugins.get().pages.put(key, new PageDef(manifest.id, title == null ? manifest.name : title,
                    icon == null ? "" : icon, builder));
            pageKeys.add(key);
        }

        @Override
        public void addToolButton(Node node) {
            if (node != null) toolButtons.add(node);
        }

        @Override
        public void addTitleButton(Node node) {
            if (node != null) titleButtons.add(node);
        }

        @Override
        public void addLaunchButton(Node node) {
            if (node != null) launchButtons.add(node);
        }

        @Override
        public void addFloatButton(Node node) {
            if (node != null) floatButtons.add(node);
        }

        @Override
        public void addCommand(String name, String description, Consumer<CommandContext> handler) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException(Lang.t("命令名不能为空"));
            String key = name.trim();
            Plugins.get().commands.put(key, new Cmd(key, description, manifest.id, handler));
            commandNames.add(key);
        }

        @Override
        public void runCommand(String name, String args) {
            Plugins.get().runCommand(name, args);
        }

        @Override
        public void toast(String title, String message, int type) {
            if (Plugins.get().win != null) Plugins.get().win.toast(title, message, type);
        }

        @Override
        public void message(String title, String text, boolean error) {
            Dialogs.message(Plugins.get().win, title, text, error, "确定");
        }

        @Override
        public void confirm(String title, String text, Runnable onOk, boolean danger) {
            Dialogs.confirm(Plugins.get().win, title, text, onOk, danger);
        }

        @Override
        public void custom(Node node) {
            if (node != null) Plugins.get().win.showModal(node);
        }

        @Override
        public void closeModal() {
            if (Plugins.get().win != null) Plugins.get().win.closeModal();
        }

        @Override
        public String input(String title, String hint, String defaultValue) {
            javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(
                    defaultValue == null ? "" : defaultValue);
            d.setTitle(title == null ? manifest.name : title);
            d.setHeaderText(null);
            d.setContentText(hint == null ? "" : hint);
            if (Plugins.get().win != null && Plugins.get().win.stage != null) {
                d.initOwner(Plugins.get().win.stage);
            }
            return d.showAndWait().orElse(null);
        }

        @Override
        public String storageGet(String key, String defaultValue) {
            ensureStore();
            return store.getProperty(key, defaultValue);
        }

        @Override
        public void storageSet(String key, String value) {
            ensureStore();
            store.setProperty(key, value == null ? "" : value);
        }

        @Override
        public void storageSave() {
            if (store == null) return;
            try {
                Path dir = Plugins.get().dataDir(manifest.id);
                Files.createDirectories(dir);
                try (OutputStream out = Files.newOutputStream(dir.resolve("storage.properties"))) {
                    store.store(out, manifest.name);
                }
            } catch (Exception e) {
                Plugins.get().log(manifest.id, "保存配置失败：" + Log.describe(e));
            }
        }

        private void ensureStore() {
            if (store != null) return;
            store = new Properties();
            try {
                Path f = Plugins.get().dataDir(manifest.id).resolve("storage.properties");
                if (Files.isRegularFile(f)) {
                    try (InputStream in = Files.newInputStream(f)) {
                        store.load(in);
                    }
                }
            } catch (Exception e) {
                Plugins.get().log(manifest.id, "读取配置失败：" + Log.describe(e));
            }
        }

        @Override
        public void listen(String event, Consumer<Event> handler) {
            if (event == null || event.isBlank() || handler == null) return;
            listeners.add(new Listener(event.trim(), handler));
        }

        @Override
        public void log(String message) {
            Plugins.get().log(manifest.id, message);
        }

        @Override
        public javafx.stage.Stage stage() {
            return Plugins.get().win == null ? null : Plugins.get().win.stage;
        }

        @Override
        public String launcherVersion() {
            return Build.VERSION;
        }
    }

    private MainWindow win;
    private volatile boolean loaded;
    private final List<LoadedPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Map<String, PageDef> pages = new LinkedHashMap<>();
    private final Map<String, Cmd> commands = new LinkedHashMap<>();
    private long orderSeq;

    private Plugins() {
    }

    public void attach(MainWindow window) {
        this.win = window;
    }

    public Path dir() {
        return Data.get().baseDir().resolve("plugins");
    }

    public Path dataDir(String id) {
        return dir().resolve(".data").resolve(id);
    }

    private void log(String pluginId, String message) {
        String line = "[插件][" + pluginId + "] " + message;
        if (win != null) win.data.log(line);
    }

    public List<LoadedPlugin> list() {
        return Collections.unmodifiableList(plugins);
    }

    public List<PageEntry> pluginPages() {
        List<PageEntry> out = new ArrayList<>();
        for (Map.Entry<String, PageDef> e : pages.entrySet()) {
            PageDef d = e.getValue();
            out.add(new PageEntry(e.getKey(), d.title, d.icon, d.owner));
        }
        return out;
    }

    public PageEntry pluginPage(String section) {
        String key = section != null && section.startsWith("plugin:")
                ? section.substring("plugin:".length()) : section;
        PageDef d = key == null ? null : pages.get(key);
        return d == null ? null : new PageEntry(key, d.title, d.icon, d.owner);
    }

    public List<LoadedPlugin> loaded() {
        List<LoadedPlugin> out = new ArrayList<>();
        for (LoadedPlugin p : plugins) if ("loaded".equals(p.state)) out.add(p);
        return out;
    }

    public List<Node> titleButtons() {
        List<Node> out = new ArrayList<>();
        for (LoadedPlugin p : plugins) if ("loaded".equals(p.state)) out.addAll(p.titleButtons);
        return out;
    }

    public List<Node> toolButtons() {
        List<Node> out = new ArrayList<>();
        for (LoadedPlugin p : plugins) if ("loaded".equals(p.state)) out.addAll(p.toolButtons);
        return out;
    }

    public List<Node> launchButtons() {
        List<Node> out = new ArrayList<>();
        for (LoadedPlugin p : plugins) if ("loaded".equals(p.state)) out.addAll(p.launchButtons);
        return out;
    }

    public List<Node> floatButtons() {
        List<Node> out = new ArrayList<>();
        for (LoadedPlugin p : plugins) if ("loaded".equals(p.state)) out.addAll(p.floatButtons);
        return out;
    }

    public List<CommandInfo> commandsOf(String pluginId) {
        List<CommandInfo> out = new ArrayList<>();
        for (Cmd c : commands.values()) {
            if (c.owner.equals(pluginId)) {
                String name = "";
                for (LoadedPlugin p : plugins) if (p.manifest.id.equals(pluginId)) name = p.manifest.name;
                out.add(new CommandInfo(c.name, c.description, pluginId, name));
            }
        }
        return out;
    }

    public List<CommandInfo> allCommands() {
        List<CommandInfo> out = new ArrayList<>();
        for (LoadedPlugin p : loaded()) out.addAll(commandsOf(p.manifest.id));
        return out;
    }

    public PageDef page(String key) {
        return pages.get(key);
    }

    public Map<String, PageDef> pages() {
        return Collections.unmodifiableMap(pages);
    }


    public boolean isLoaded() {
        return loaded;
    }

    public synchronized void markLoading() {
        loaded = false;
    }

    public synchronized void loadAll() {
        // 扫描期间给页面留加载态
        loaded = false;
        try {
            loadAllNow();
        } finally {
            loaded = true;
        }
    }

    private void loadAllNow() {
        unloadAll();
        plugins.clear();
        pages.clear();
        commands.clear();
        orderSeq = 0;
        try {
            Files.createDirectories(dir());
        } catch (Exception e) {
            return;
        }
        List<Path> dirs = new ArrayList<>();
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir())) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) continue;
                if (Files.isDirectory(p)) {
                    if (Files.isRegularFile(p.resolve("plugin.json"))) dirs.add(p);
                } else if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
                    jars.add(p);
                }
            }
        } catch (Exception ignored) {
        }
        dirs.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
        jars.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
        Map<String, LoadedPlugin> seen = new LinkedHashMap<>();
        for (Path d : dirs) addManifest(seen, d, true);
        for (Path j : jars) addManifest(seen, j, false);
        plugins.addAll(seen.values());
        for (LoadedPlugin lp : plugins) lp.enabled = Data.get().getBool("Plugin." + lp.manifest.id + ".enabled", true);
        for (LoadedPlugin lp : plugins) {
            if (!lp.enabled) {
                lp.state = "disabled";
                continue;
            }
            load(lp, true);
        }
    }

    private void addManifest(Map<String, LoadedPlugin> seen, Path source, boolean directory) {
        Manifest m = null;
        try {
            m = directory ? readManifestFromDir(source) : readManifestFromJar(source);
        } catch (Exception e) {
            LoadedPlugin bad = new LoadedPlugin(new Manifest(), source, directory, orderSeq++);
            bad.manifest.id = "_bad_" + source.getFileName();
            bad.manifest.name = source.getFileName().toString();
            bad.manifest.version = "";
            bad.manifest.api = 0;
            bad.state = "failed";
            bad.error = "读取 plugin.json 失败：" + Log.describe(e);
            seen.put(bad.manifest.id, bad);
            return;
        }
        if (m == null) return;
        try {
            validate(m);
        } catch (Exception e) {
            LoadedPlugin bad = new LoadedPlugin(m, source, directory, orderSeq++);
            bad.state = "failed";
            bad.error = e.getMessage();
            seen.put(m.id + "@" + source.getFileName(), bad);
            return;
        }
        if (seen.containsKey(m.id)) {
            LoadedPlugin bad = new LoadedPlugin(m, source, directory, orderSeq++);
            bad.state = "failed";
            bad.error = "插件 id 重复：" + m.id + "（已被 " + seen.get(m.id).source.getFileName() + " 使用）";
            seen.put(m.id + "@" + source.getFileName(), bad);
            return;
        }
        LoadedPlugin lp = new LoadedPlugin(m, source, directory, orderSeq++);
        lp.state = "disabled";
        seen.put(m.id, lp);
    }

    private void unloadAll() {
        for (LoadedPlugin lp : plugins) unload(lp);
    }

    private void load(LoadedPlugin lp, boolean enabling) {
        unload(lp);
        lp.error = "";
        try {
            List<URL> urls = new ArrayList<>();
            if (lp.directory) {
                collectJars(lp.source, urls);
            } else {
                urls.add(lp.source.toUri().toURL());
            }
            URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), Plugins.class.getClassLoader());
            lp.loader = loader;
            Class<?> type = Class.forName(lp.manifest.main, true, loader);
            Object object = type.getDeclaredConstructor().newInstance();
            if (!(object instanceof TcraftPlugin p)) {
                throw new IllegalArgumentException(Lang.t("main 类未实现 tcraft.api.TcraftPlugin"));
            }
            lp.instance = p;
            lp.ctx = new PluginContext(lp);
            Thread current = Thread.currentThread();
            ClassLoader old = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                p.onLoad(lp.ctx);
                if (enabling) p.onEnable();
            } finally {
                current.setContextClassLoader(old);
            }
            lp.state = enabling ? "loaded" : "disabled";
            lp.enabled = enabling;
            Data.get().set("Plugin." + lp.manifest.id + ".enabled", enabling);
            log(lp.manifest.id, "已加载 " + lp.manifest.name + " " + lp.manifest.version);
        } catch (Throwable t) {
            lp.enabled = false;
            lp.state = "failed";
            lp.error = Log.describe(t);
            log(lp.manifest.id, "加载失败：" + lp.error);
            cleanup(lp);
        }
    }

    private void collectJars(Path dir, List<URL> urls) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path j : ds) urls.add(j.toUri().toURL());
        }
        Path lib = dir.resolve("lib");
        if (Files.isDirectory(lib)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(lib, "*.jar")) {
                for (Path j : ds) urls.add(j.toUri().toURL());
            }
        }
    }

    private void unload(LoadedPlugin lp) {
        if (lp.instance != null) {
            try {
                lp.instance.onDisable();
            } catch (Throwable t) {
                log(lp.manifest.id, "onDisable 异常：" + Log.describe(t));
            }
        }
        if (lp.ctx != null) {
            try {
                tcraft.api.Storage s = lp.ctx.storage();
                s.save();
            } catch (Throwable ignored) {
            }
        }
        cleanup(lp);
        lp.instance = null;
        lp.state = "disabled";
    }

    private void cleanup(LoadedPlugin lp) {
        for (String key : lp.pageKeys) {
            PageDef def = pages.get(key);
            if (def != null && def.owner.equals(lp.manifest.id)) pages.remove(key);
        }
        lp.pageKeys.clear();
        for (String name : lp.commandNames) {
            Cmd c = commands.get(name);
            if (c != null && c.owner.equals(lp.manifest.id)) commands.remove(name);
        }
        lp.commandNames.clear();
        lp.listeners.clear();
        lp.titleButtons.clear();
        lp.toolButtons.clear();
        lp.launchButtons.clear();
        lp.floatButtons.clear();
        if (lp.loader != null) {
            try {
                lp.loader.close();
            } catch (Exception ignored) {
            }
            lp.loader = null;
        }
    }


    public void setEnabled(String id, boolean enabled) {
        LoadedPlugin lp = find(id);
        if (lp == null) return;
        Data.get().set("Plugin." + id + ".enabled", enabled);
        if (enabled) {
            lp.enabled = true;
            load(lp, true);
        } else {
            lp.enabled = false;
            unload(lp);
        }
        refreshUi();
    }

    public void reload(String id) {
        LoadedPlugin lp = find(id);
        if (lp == null) return;
        load(lp, lp.enabled);
        refreshUi();
    }

    // 覆盖前卸载并释放 jar 锁
    public void release(String id) {
        LoadedPlugin lp = find(id);
        if (lp == null) return;
        boolean enabled = lp.enabled;
        unload(lp);
        lp.enabled = enabled;
    }

    // 删除前调用 onUninstall 并释放 jar 锁
    public boolean prepareRemove(String id) {
        LoadedPlugin lp = find(id);
        if (lp == null) return false;
        if (lp.instance != null) {
            try {
                lp.instance.onUninstall(lp.ctx);
            } catch (Throwable t) {
                log(id, "onUninstall 异常：" + Log.describe(t));
            }
        }
        lp.enabled = false;
        unload(lp);
        lp.state = "removed";
        return true;
    }

    public void finishRemove(String id) {
        plugins.removeIf(p -> p.manifest.id.equals(id));
        refreshUi();
    }

    public Path sourceOf(String id) {
        LoadedPlugin lp = find(id);
        return lp == null ? null : lp.source;
    }

    public LoadedPlugin find(String id) {
        for (LoadedPlugin p : plugins) if (p.manifest.id.equals(id)) return p;
        return null;
    }

    // 图标 manifest > icon/logo > 内置
    public Node icon(String pluginId, String iconName, double size, javafx.scene.paint.Color color) {
        LoadedPlugin lp = find(pluginId);
        if (lp != null) {
            java.util.List<String> candidates = new ArrayList<>();
            if (looksLikeImage(iconName)) candidates.add(iconName);
            if (iconName == null || iconName.isBlank() || !looksLikeImage(iconName)) {
                if (looksLikeImage(lp.manifest.icon)) candidates.add(lp.manifest.icon);
                candidates.add("icon.png");
                candidates.add("icon.jpg");
                candidates.add("icon.jpeg");
                candidates.add("icon.gif");
                candidates.add("icon.webp");
                candidates.add("logo.png");
            }
            javafx.scene.image.Image img = readImage(lp, candidates, size);
            if (img != null) return new javafx.scene.image.ImageView(img);
        }
        String name = iconName == null || iconName.isBlank() ? "puzzle" : iconName;
        return Theme.icon(name, size, color);
    }

    // 缩略图 thumbnail/preview > 封面
    public javafx.scene.image.Image thumbnail(String pluginId, double size) {
        LoadedPlugin lp = find(pluginId);
        if (lp == null) return null;
        java.util.List<String> candidates = new ArrayList<>();
        if (looksLikeImage(lp.manifest.thumbnail)) candidates.add(lp.manifest.thumbnail);
        if (looksLikeImage(lp.manifest.icon)) candidates.add(lp.manifest.icon);
        candidates.add("thumbnail.png");
        candidates.add("preview.png");
        candidates.add("cover.png");
        candidates.add("icon.png");
        candidates.add("logo.png");
        return readImage(lp, candidates, size);
    }

    private static boolean looksLikeImage(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private javafx.scene.image.Image readImage(LoadedPlugin lp, java.util.List<String> candidates, double size) {
        for (String name : candidates) {
            if (name == null || name.isBlank()) continue;
            byte[] bytes = readImageBytes(lp, name);
            if (bytes == null) continue;
            javafx.scene.image.Image img = RemoteImages.decodeBytes(bytes, size);
            if (img != null && !img.isError()) return img;
        }
        return null;
    }

    private byte[] readImageBytes(LoadedPlugin lp, String name) {
        String clean = name.replace('\\', '/').trim();
        if (clean.isEmpty() || clean.startsWith("/") || clean.contains("..")) return null;
        try {
            if (lp.directory) {
                Path f = lp.source.resolve(clean).normalize();
                if (!f.startsWith(lp.source) || !Files.isRegularFile(f) || Files.size(f) > IMAGE_MAX) return null;
                return Files.readAllBytes(f);
            }
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(lp.source.toFile())) {
                ZipEntry entry = jar.getEntry(clean);
                if (entry == null || entry.isDirectory() || entry.getSize() > IMAGE_MAX) return null;
                try (InputStream in = jar.getInputStream(entry)) {
                    return Io.read(in, IMAGE_MAX);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
    }


    public void buildPluginPage(String section, javafx.scene.layout.VBox page) {
        String key = section.startsWith("plugin:") ? section.substring("plugin:".length()) : section;
        PageDef def = pages.get(key);
        if (def == null) {
            page.getChildren().add(Ui.hint("这个插件页面不存在，插件可能已被删除或禁用。", 2));
            return;
        }
        LoadedPlugin lp = find(def.owner);
        if (lp == null || !"loaded".equals(lp.state)) {
            page.getChildren().add(Ui.hint("插件未加载，无法显示页面。", 2));
            return;
        }
        try {
            PageView view = new PageView(page, lp.ctx);
            Node node = def.builder.apply(view);
            if (node != null) page.getChildren().add(node);
        } catch (Throwable t) {
            log(def.owner, "页面渲染失败：" + Log.describe(t));
            page.getChildren().add(Ui.hint(Lang.t("插件页面渲染失败：") + Log.describe(t), 3));
        }
    }

    public void refreshUi() {
        if (win != null) win.onPluginsChanged();
    }

    public void openPage(String key) {
        if (win != null) win.openPage(key, true);
    }


    public void runCommand(String name, String args) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> runCommand(name, args));
            return;
        }
        Cmd cmd = commands.get(name);
        if (cmd == null) {
            if (win != null) win.toast("命令不存在", name, 2);
            return;
        }
        LoadedPlugin owner = find(cmd.owner);
        try {
            cmd.handler.accept(new CommandContext(name, args == null ? "" : args,
                    cmd.owner, owner));
        } catch (Throwable t) {
            log(cmd.owner, "命令执行失败 " + name + "：" + Log.describe(t));
            if (win != null) win.toast("命令执行失败", name + Lang.t("：") + Log.describe(t), 3);
        }
    }

    public void fire(String event, Map<String, Object> data) {
        if (event == null) return;
        Runnable task = () -> {
            List<LoadedPlugin> owners = new ArrayList<>(plugins);
            for (LoadedPlugin p : owners) {
                for (Listener l : new ArrayList<>(p.listeners)) {
                    if (!l.event.equals(event)) continue;
                    try {
                        l.handler.accept(new Event(event, data));
                    } catch (Throwable t) {
                        log(p.manifest.id, "事件处理失败 " + event + "：" + Log.describe(t));
                    }
                }
            }
        };
        if (Platform.isFxApplicationThread()) task.run();
        else Platform.runLater(task);
    }


    // 读取包信息 不写磁盘
    public PluginInfo inspect(Path source) throws IOException {
        try (ZipFile zf = new ZipFile(source.toFile())) {
            ZipEntry entry = manifestEntry(zf);
            if (entry == null) throw new IOException(Lang.t("压缩包内没有 plugin.json"));
            Manifest m = readManifest(zf.getInputStream(entry));
            validate(m);
            return m.info(null);
        }
    }

    // 后台可调用 解包到 plugins/<id> 不加载
    public String install(Path source, boolean replace) throws IOException {
        String raw = source.getFileName().toString();
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".jar")) {
            throw new IOException(Lang.t("仅支持 .zip 或 .jar 插件包"));
        }
        Manifest m;
        String manifestText;
        String prefix;
        try (ZipFile zf = new ZipFile(source.toFile())) {
            ZipEntry entry = manifestEntry(zf);
            if (entry == null) throw new IOException(Lang.t("压缩包内没有 plugin.json"));
            manifestText = new String(Io.read(zf.getInputStream(entry), MANIFEST_MAX), StandardCharsets.UTF_8);
            m = readManifest(manifestText);
            validate(m);
            String name = entry.getName();
            prefix = name.equals("plugin.json") ? "" : name.substring(0, name.length() - "plugin.json".length());
        }
        Path target = dir().resolve(m.id).normalize();
        if (!target.startsWith(dir().normalize())) throw new IOException(Lang.t("插件 id 非法：") + m.id);
        if (Files.exists(target) && !replace) throw new IOException(Lang.t("插件 id 已存在：") + m.id);
        // 先解到临时目录 失败不破坏旧插件
        Path staging = target.resolveSibling("." + m.id + ".installing");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            if (lower.endsWith(".jar")) {
                if (Files.size(source) > MAX_BYTES) throw new IOException(Lang.t("插件包超过上限（512MB）"));
                Files.copy(source, staging.resolve(safeFileName(raw)), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(staging.resolve("plugin.json"), manifestText, StandardCharsets.UTF_8);
            } else {
                int[] entries = {0};
                long[] bytes = {0};
                try (ZipFile zf = new ZipFile(source.toFile())) {
                    var en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        String name = e.getName();
                        if (!prefix.isEmpty()) {
                            if (!name.startsWith(prefix)) continue;
                            name = name.substring(prefix.length());
                        }
                        if (name.isEmpty() || name.endsWith("/")) continue;
                        if (++entries[0] > MAX_ENTRIES) throw new IOException(Lang.t("插件包文件过多（上限 ") + MAX_ENTRIES + "）");
                        Path out = staging.resolve(name).normalize();
                        if (!out.startsWith(staging)) throw new IOException(Lang.t("插件包包含非法路径：") + e.getName());
                        Files.createDirectories(out.getParent());
                        try (InputStream in = zf.getInputStream(e);
                             OutputStream os = Files.newOutputStream(out)) {
                            bytes[0] += Io.copy(in, os, MAX_BYTES - bytes[0]);
                        }
                    }
                }
                if (!Files.isRegularFile(staging.resolve("plugin.json"))) {
                    Files.writeString(staging.resolve("plugin.json"), manifestText, StandardCharsets.UTF_8);
                }
            }
            if (Files.exists(target)) deleteTree(target);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);
            throw e;
        }
        return m.id;
    }

    // 导入后重新扫描并执行 onInstall
    public void afterInstall(String id) {
        loadAll();
        LoadedPlugin lp = find(id);
        if (lp != null && "loaded".equals(lp.state)) {
            try {
                lp.instance.onInstall(lp.ctx);
            } catch (Throwable t) {
                log(id, "onInstall 异常：" + Log.describe(t));
            }
            for (String cmd : lp.manifest.onInstall) {
                runCommand(cmd, "");
            }
        }
        fire("plugin.install", Map.of("id", id));
        refreshUi();
    }


    private ZipEntry manifestEntry(ZipFile zf) {
        ZipEntry best = null;
        int bestDepth = Integer.MAX_VALUE;
        var en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String name = e.getName();
            if (e.isDirectory() || !name.toLowerCase(java.util.Locale.ROOT).endsWith("plugin.json")) continue;
            int depth = name.split("/").length;
            if (depth <= 2 && depth < bestDepth) {
                best = e;
                bestDepth = depth;
            }
        }
        return best;
    }

    private Manifest readManifestFromDir(Path dir) throws IOException {
        try (InputStream in = Files.newInputStream(dir.resolve("plugin.json"))) {
            return readManifest(in);
        }
    }

    private Manifest readManifestFromJar(Path jar) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = manifestEntry(zf);
            if (e == null) return null;
            return readManifest(zf.getInputStream(e));
        }
    }

    private Manifest readManifest(InputStream in) throws IOException {
        return readManifest(new String(Io.read(in, MANIFEST_MAX), StandardCharsets.UTF_8));
    }

    private Manifest readManifest(String text) {
        Map<String, Object> root = Json.obj(Json.parse(text));
        Manifest m = new Manifest();
        m.id = Json.str(root, "id", "").trim();
        m.name = Json.str(root, "name", m.id).trim();
        m.version = Json.str(root, "version", "").trim();
        m.author = Json.str(root, "author", "").trim();
        m.description = Json.str(root, "description", "").trim();
        m.icon = Json.str(root, "icon", "").trim();
        m.thumbnail = Json.str(root, "thumbnail", "").trim();
        if (m.thumbnail.isEmpty()) m.thumbnail = Json.str(root, "preview", "").trim();
        m.main = Json.str(root, "main", "").trim();
        m.website = Json.str(root, "website", "").trim();
        Object api = root.get("api");
        m.api = api instanceof Number ? ((Number) api).intValue() : 1;
        for (Object o : Json.arr(root.get("onInstall"))) m.onInstall.add(String.valueOf(o));
        return m;
    }

    private void validate(Manifest m) throws IOException {
        if (m.id.isEmpty()) throw new IOException(Lang.t("plugin.json 缺少 id"));
        if (!m.id.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,63}")) {
            throw new IOException(Lang.t("插件 id 只允许字母、数字、点、下划线与短横线，长度 2-64：") + m.id);
        }
        if (m.name.isEmpty()) throw new IOException(Lang.t("plugin.json 缺少 name"));
        if (m.version.isEmpty()) throw new IOException(Lang.t("plugin.json 缺少 version"));
        if (m.main.isEmpty()) throw new IOException(Lang.t("plugin.json 缺少 main"));
        if (m.api > API_VERSION) {
            throw new IOException(Lang.tf("插件需要 API %d，当前客户端仅支持 %d", m.api, API_VERSION));
        }
    }

    private String safeId(String value, String what) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(what + "只允许字母、数字、点、下划线、短横线，最长 64：" + value);
        }
        return value;
    }

    private static String safeFileName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return clean.isEmpty() ? "plugin.jar" : clean;
    }

    private static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path);
            return;
        }
        Files.walkFileTree(path, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path folder, IOException exc)
                    throws IOException {
                if (exc != null) throw exc;
                Files.deleteIfExists(folder);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
