package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class Data {

    public static class Account {
        public String name;
        public String uuid;
        public String skin;
        public boolean online;
        public String accessToken = "";
        public String clientToken = "";
        public String userType = "";
        public String server = "";
        public String refreshToken = "";
        public long expiresAt = 0;

        public Account(String name, String uuid, String skin, boolean online) {
            this.name = name;
            this.uuid = uuid;
            this.skin = skin;
            this.online = online;
        }
    }

    public static class Instance {
        public String name;
        public String version;
        public String loader;
        public String loaderVersion;
        public String description;
        public String icon;
        public long lastPlayed;
        public long addedAt;
        public boolean favorite;
        public String folder;
        public int isolation;

        public Instance(String name, String version, String loader, String loaderVersion,
                        String description, String icon, long lastPlayed, boolean favorite, String folder) {
            this(name, version, loader, loaderVersion, description, icon, lastPlayed, favorite, folder, 0);
        }

        public Instance(String name, String version, String loader, String loaderVersion,
                        String description, String icon, long lastPlayed, boolean favorite, String folder,
                        int isolation) {
            this.name = name;
            this.version = version;
            this.loader = loader;
            this.loaderVersion = loaderVersion;
            this.description = description;
            this.icon = icon;
            this.lastPlayed = lastPlayed;
            this.addedAt = System.currentTimeMillis();
            this.favorite = favorite;
            this.folder = folder;
            this.isolation = isolation;
        }

        public String subtitle() {
            if (description != null && !description.isEmpty()) return renderedSummary(description);
            StringBuilder sb = new StringBuilder();
            sb.append(Lang.t("正式版 ")).append(version);
            if (loader != null && !loader.isEmpty() && !"原版".equals(loader)) {
                sb.append(Lang.t("，")).append(loader);
                if (loaderVersion != null) sb.append(" ").append(loaderVersion);
            }
            return sb.toString();
        }

        // description 摘要持久化为中文，显示层重拼（键同写入端）
        private static final java.util.regex.Pattern SUMMARY_P =
                java.util.regex.Pattern.compile("^(正式版|预览版|愚人节版|远古版) (\\S+)，已安装 (.+)$");

        private String renderedSummary(String raw) {
            java.util.regex.Matcher m = SUMMARY_P.matcher(raw);
            if (!m.matches()) return raw;
            return Lang.t(m.group(1)) + " " + m.group(2) + Lang.t("，已安装 ") + m.group(3);
        }
    }

    public static class McVersion {
        public String id;
        public String type; // release / snapshot / old / special
        public String time;
        public String lore;

        public McVersion(String id, String type, String time) {
            this.id = id;
            this.type = type;
            this.time = time;
        }

        public String group() {
            return switch (type) {
                case "release" -> "正式版";
                case "snapshot" -> "预览版";
                case "special" -> "愚人节版";
                default -> "远古版";
            };
        }

        public String icon() {
            return switch (type) {
                case "release" -> "Grass.png";
                case "snapshot" -> "CommandBlock.png";
                case "special" -> "GoldBlock.png";
                default -> "CobbleStone.png";
            };
        }
    }

    public static class FolderItem {
        public String name;
        public String path;

        public FolderItem(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    public static class Favorite {
        public String type = "mod";
        public String slug = "";
        public String name = "";
        public String desc = "";
        public String icon = "";
        public String website = "";
        public String platform = "";
        public String projectId = "";

        public static Favorite of(String type, String slug, String name, String desc, String icon, String website) {
            Favorite f = new Favorite();
            f.type = type == null ? "mod" : type;
            f.slug = slug == null ? "" : slug;
            f.name = name == null ? "" : name;
            f.desc = desc == null ? "" : desc;
            f.icon = icon == null ? "" : icon;
            f.website = website == null ? "" : website;
            return f;
        }

        public static String esc(String s) {
            return s == null ? "" : s.replace("|", " ").replace("\r", " ").replace("\n", " ");
        }

        public String encode() {
            return esc(type) + "|" + esc(slug) + "|" + esc(name) + "|" + esc(desc) + "|" + esc(icon)
                    + "|" + esc(website) + "|" + esc(platform) + "|" + esc(projectId);
        }

        public static Favorite decode(String raw) {
            String[] p = raw.split("\\|", -1);
            Favorite f = new Favorite();
            f.type = p.length > 0 && !p[0].isEmpty() ? p[0] : "mod";
            f.slug = p.length > 1 ? p[1] : "";
            f.name = p.length > 2 ? p[2] : f.slug;
            f.desc = p.length > 3 ? p[3] : "";
            f.icon = p.length > 4 ? p[4] : "";
            f.website = p.length > 5 ? p[5] : "";
            f.platform = p.length > 6 ? p[6] : "";
            f.projectId = p.length > 7 ? p[7] : "";
            return f;
        }
    }

    private static final java.util.Set<String> SPECIAL_IDS = java.util.Set.of(
            "20w14infinite", "20w14∞", "2.0", "15w14a", "23w13a_or_b", "22w13oneblockatatime",
            "24w14potato", "25w14craftmine", "1.rv-pre1", "3d shareware v1.34", "26w14a");

    private static String foolName(String id) {
        String name = id.toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("2.0")) return "2013 | 这个秘密计划了两年的更新将游戏推向了一个新高度！";
        if (name.equals("15w14a")) return "2015 | 作为一款全年龄向的游戏，我们需要和平，需要爱与拥抱。";
        if (name.equals("1.rv-pre1")) return "2016 | 是时候将现代科技带入 Minecraft 了！";
        if (name.equals("3d shareware v1.34")) return "2019 | 我们从地下室的废墟里找到了这个开发于 1994 年的杰作！";
        if (name.startsWith("20w14inf") || name.equals("20w14∞"))
            return "2020 | 我们加入了 20 亿个新的维度，让无限的想象变成了现实！";
        if (name.equals("22w13oneblockatatime")) return "2022 | 一次一个方块更新！迎接全新的挖掘、合成与骑乘玩法吧！";
        if (name.equals("23w13a_or_b")) return "2023 | 研究表明：玩家喜欢作出选择——越多越好！";
        if (name.equals("24w14potato")) return "2024 | 毒马铃薯一直都被大家忽视和低估，于是我们超级加强了它！";
        if (name.equals("25w14craftmine")) return "2025 | 你可以合成任何东西——包括合成你的世界！";
        if (name.equals("26w14a")) return "2026 | 为什么需要物品栏？让方块们跟着你走吧！";
        return "";
    }

    private static String classifyVersion(String id, String type, String releaseTimeIso) {
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "release":
                return "release";
            case "snapshot":
                if (lower.startsWith("1.") && !lower.contains("combat") && !lower.contains("rc")
                        && !lower.contains("experimental") && !lower.contains("pre")) {
                    return "release";
                }
                if (SPECIAL_IDS.contains(lower)) return "special";
                try {
                    java.time.LocalDateTime t = java.time.LocalDateTime.parse(releaseTimeIso,
                            java.time.format.DateTimeFormatter.ISO_DATE_TIME).plusHours(2);
                    if (t.getMonthValue() == 4 && t.getDayOfMonth() == 1) return "special";
                } catch (Exception ignored) {
                }
                return "snapshot";
            case "special":
                return "special";
            default:
                return "old";
        }
    }

    public static final Data INSTANCE = new Data();

    public final List<Account> accounts = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<Instance> instances = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<FolderItem> folders = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<McVersion> versions = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<Favorite> favorites = new java.util.concurrent.CopyOnWriteArrayList<>();
    public int selectedAccount = 0;
    public int selectedInstance = 0;
    public String gameDir;
    // 后台扫描线程会补写，跨线程可见性靠 volatile
    public volatile String javaPath = "";
    public String downloadCategory = "原版游戏";
    public String otherSection = "about";
    public String setupSection = "launch";
    public String instanceSection = "overview";
    public final List<String> log = new LogRing(2000);
    public final LogRing launchLog = new LogRing(500);
    private volatile long lastVersionRefresh;
    private boolean versionRefreshRunning;
    private final java.util.List<Runnable> versionWaiters = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void launchLog(String line) {
        // 500 行供崩溃分析
        launchLog.add(Redact.apply("[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line));
    }

    private final Properties props = new Properties();
    private final Path configPath;

    // 上次写配置崩溃会留下 .tmp，里面是明文 token，不能过夜
    private void cleanupConfigTmp() {
        String name = configPath.getFileName().toString();
        for (String suffix : new String[]{".tmp", ".import.tmp"}) {
            try {
                Files.deleteIfExists(configPath.resolveSibling(name + suffix));
            } catch (Exception ignored) {
            }
        }
    }

    private Data() {
        Path home = Resources.home();
        configPath = home.resolve("config.properties");
        gameDir = detectGameDir();
        load();
        cleanupConfigTmp();
        pruneInstances();
        if (instances.isEmpty()) seedInstances();
        if (folders.isEmpty()) folders.add(new FolderItem("当前文件夹", gameDir));
        // exe 外壳选定的 Java 优先级最高
        String forcedJava = System.getProperty("tcraft.java", "");
        if (!forcedJava.isEmpty() && usableJavaFast(forcedJava)) {
            javaPath = forcedJava;
        } else {
            // Windows 外壳把选择结果写在 launcher.cfg
            try {
                Path cfg = configPath.getParent().resolve("launcher.cfg");
                if (Files.isRegularFile(cfg)) {
                    String picked = Files.readString(cfg, StandardCharsets.UTF_8).trim();
                    if (usableJavaFast(picked)) javaPath = picked;
                }
            } catch (Exception e) {
                log("读取 launcher.cfg 失败：" + Log.describe(e));
            }
        }
        if (javaPath == null || javaPath.isEmpty()) {
            // 构造期不逐盘探测，先取缓存
            javaPath = cachedJavaPath();
            if (javaPath.isEmpty()) startJavaScanFill();
        }
        log.add("[TCL] TCraft Launcher " + Build.VERSION + " (JavaFX Edition)");
        log.add("[TCL] 当前游戏目录：" + gameDir);
        log.add("[TCL] Java：" + (javaPath.isEmpty() ? "未找到" : javaPath));
        applyProxy();
    }

    private final java.util.List<Instance> orphanInstances = new java.util.concurrent.CopyOnWriteArrayList<>();

    private void pruneInstances() {
        if (instances.isEmpty()) return;
        List<Instance> keep = new ArrayList<>();
        int hidden = 0;
        for (Instance ins : instances) {
            String folder = ins.folder != null && !ins.folder.isEmpty() ? ins.folder : gameDir;
            Path json = Paths.get(folder, "versions", ins.version, ins.version + ".json");
            if (Files.isRegularFile(json)) {
                keep.add(ins);
            } else {
                orphanInstances.add(ins);
                hidden++;
                log.add("[TCL] 本次会话隐藏无版本文件的实例（配置保留，磁盘恢复后自动出现）：" + ins.name);
            }
        }
        if (hidden > 0) {
            instances.clear();
            instances.addAll(keep);
            if (selectedInstance >= instances.size()) selectedInstance = Math.max(0, instances.size() - 1);
            dataLogHidden(hidden);
        }
    }

    private void dataLogHidden(int hidden) {
        log("已在本次会话隐藏 " + hidden + " 个暂时不可用的实例（配置未删除）");
    }

    public static Data get() {
        return INSTANCE;
    }

    public Path baseDir() {
        return configPath.getParent();
    }

    public static final String CACHE_MARKER = ".tcl-cache";

    public Path cacheDir() {
        String custom = get("SystemSystemCache", "");
        if (custom != null && !custom.isBlank()) {
            try {
                Path p = Paths.get(custom.trim()).toAbsolutePath().normalize();
                Path base = baseDir().toAbsolutePath().normalize();
                Path game = Paths.get(gameDir).toAbsolutePath().normalize();
                // 缓存根与配置/游戏目录双向隔离，防清理误删
                if (p.getNameCount() > 1 && !p.startsWith(base) && !p.startsWith(game)
                        && !base.startsWith(p) && !game.startsWith(p)
                        && !p.equals(base) && !p.equals(game)) {
                    Files.createDirectories(p);
                    // 无标记的已有内容目录可能是用户的，不自动认领
                    ensureCacheMarker(p, false);
                    return p;
                }
            } catch (Exception ignored) {
            }
        }
        Path d = baseDir().resolve("cache");
        try {
            Files.createDirectories(d);
            ensureCacheMarker(d, true);
        } catch (Exception ignored) {
        }
        return d;
    }

    private static void ensureCacheMarker(Path d, boolean always) {
        try {
            Path marker = d.resolve(CACHE_MARKER);
            if (Files.isRegularFile(marker)) return;
            if (!always) {
                try (var s = Files.list(d)) {
                    if (s.findAny().isPresent()) return;
                }
            }
            Files.writeString(marker, "TCraft Launcher cache root", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    // 破坏性清理（prune / 清空缓存目录）前必须确认标记存在
    public static boolean hasCacheMarker(Path cacheRoot) {
        if (cacheRoot == null) return false;
        try {
            return Files.isRegularFile(cacheRoot.resolve(CACHE_MARKER));
        } catch (Exception e) {
            return false;
        }
    }

    // 用于旧配置迁移判断
    private static boolean isStockMinecraft(String path) {
        try {
            Path p = Paths.get(path).toAbsolutePath().normalize();
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            Path stock;
            if (os.contains("win") && System.getenv("APPDATA") != null) {
                stock = Paths.get(System.getenv("APPDATA"), ".minecraft");
            } else if (os.contains("mac")) {
                stock = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
            } else {
                stock = Paths.get(System.getProperty("user.home"), ".minecraft");
            }
            return p.equals(stock.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    private String detectGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        Path legacy;
        if (os.contains("mac")) {
            legacy = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        } else if (os.contains("win") && System.getenv("APPDATA") != null) {
            legacy = Paths.get(System.getenv("APPDATA"), ".minecraft");
        } else if (System.getenv("XDG_DATA_HOME") != null) {
            legacy = Paths.get(System.getenv("XDG_DATA_HOME"), "minecraft");
        } else {
            legacy = Paths.get(System.getProperty("user.home"), ".minecraft");
        }
        // 默认游戏目录=启动器所在文件夹（绿色版）
        try {
            Path app = Resources.appDir();
            if (app != null && Files.isDirectory(app)) return app.toString();
        } catch (Exception ignored) {
        }
        return legacy.toString();
    }

    public record JavaRuntime(String path, int major, String folder) {
    }

    private volatile List<JavaRuntime> javaScanCache;
    private final Object javaScanLock = new Object();
    private volatile long javaScanAt;

    public void invalidateJavaScan() {
        synchronized (javaScanLock) {
            javaScanCache = null;
            javaScanAt = 0;
        }
    }

    // 设置页免等扫描
    public boolean hasJavaScanCache() {
        return javaScanCache != null && System.currentTimeMillis() - javaScanAt < 300_000;
    }

    public String detectJava() {
        List<JavaRuntime> list = scanJavaRuntimes();
        for (JavaRuntime r : list) {
            if (r.major >= 17) return r.path;
        }
        return list.isEmpty() ? "" : list.get(0).path;
    }

    // 只读扫描缓存，绝不起进程
    private String cachedJavaPath() {
        List<JavaRuntime> cached = javaScanCache;
        if (cached == null || cached.isEmpty()) return "";
        for (JavaRuntime r : cached) {
            if (r.major >= 17) return r.path;
        }
        return cached.get(0).path;
    }

    private void startJavaScanFill() {
        Thread t = new Thread(() -> {
            try {
                List<JavaRuntime> list = scanJavaRuntimes();
                if ((javaPath == null || javaPath.isEmpty()) && !list.isEmpty()) {
                    for (JavaRuntime r : list) {
                        if (r.major >= 17) {
                            javaPath = r.path;
                            return;
                        }
                    }
                    javaPath = list.get(0).path;
                }
            } catch (Exception e) {
                log("后台扫描 Java 失败：" + Log.describe(e));
            }
        }, "TCL-Java-Fill");
        t.setDaemon(true);
        t.start();
    }

    // 会起 java -version 子进程（走 javaMajor），只能后台线程调
    public static boolean usableJava(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            return Files.isRegularFile(Paths.get(path)) && javaMajor(path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 不起进程的 usableJava，无 release 判 false
    public static boolean usableJavaFast(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            return Files.isRegularFile(Paths.get(path)) && javaMajorFast(path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void setJavaRuntime(String path, int major) {
        javaPath = path;
        set("java.path", path);
        save();
        if (major >= 17) writeLauncherCfg(path);
    }

    private void writeLauncherCfg(String path) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath.getParent().resolve("launcher.cfg"), path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log("写入 launcher.cfg 失败：" + Log.describe(e));
        }
    }

    public List<JavaRuntime> scanJavaRuntimes() {
        long now = System.currentTimeMillis();
        List<JavaRuntime> cached = javaScanCache;
        if (cached != null && now - javaScanAt < 300_000) return cached;
        synchronized (javaScanLock) {
            now = System.currentTimeMillis();
            cached = javaScanCache;
            if (cached != null && now - javaScanAt < 300_000) return cached;
            List<JavaRuntime> list = probeAll(javaCandidates());
            javaScanCache = list;
            javaScanAt = now;
            return list;
        }
    }

    // 设置页「搜索目录」用，找到的会并入扫描缓存
    public List<JavaRuntime> scanJavaIn(Path dir, int maxDepth) {
        List<String> candidates = new ArrayList<>();
        if (dir != null && Files.isDirectory(dir)) {
            try (var walk = Files.walk(dir, Math.max(1, maxDepth))) {
                walk.filter(p -> {
                    String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return n.equals("java.exe") || n.equals("java");
                }).filter(Files::isRegularFile).limit(200).forEach(p -> candidates.add(p.toString()));
            } catch (Exception e) {
                log("扫描 Java 目录失败：" + Log.describe(e));
            }
        }
        List<JavaRuntime> found = probeAll(candidates);
        if (!found.isEmpty()) {
            synchronized (javaScanLock) {
                java.util.LinkedHashMap<String, JavaRuntime> merged = new java.util.LinkedHashMap<>();
                if (javaScanCache != null) {
                    for (JavaRuntime rt : javaScanCache) merged.put(rt.path().toLowerCase(java.util.Locale.ROOT), rt);
                }
                for (JavaRuntime rt : found) merged.put(rt.path().toLowerCase(java.util.Locale.ROOT), rt);
                List<JavaRuntime> list = new ArrayList<>(merged.values());
                list.sort((a, b) -> Integer.compare(b.major(), a.major()));
                javaScanCache = list;
                javaScanAt = System.currentTimeMillis();
            }
        }
        return found;
    }

    // 起进程读版本，须并行
    private static List<JavaRuntime> probeAll(List<String> candidates) {
        java.util.LinkedHashMap<String, JavaRuntime> found = new java.util.LinkedHashMap<>();
        List<String> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String raw : candidates) {
            if (raw == null || raw.isBlank()) continue;
            if (!seen.add(raw.toLowerCase(java.util.Locale.ROOT))) continue;
            unique.add(raw);
        }
        if (unique.isEmpty()) return new ArrayList<>();

        int threads = Math.max(1, Math.min(6, unique.size()));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "TCL-JavaProbe");
            t.setDaemon(true);
            return t;
        });
        List<java.util.concurrent.Future<JavaRuntime>> futures = new ArrayList<>();
        for (String raw : unique) {
            futures.add(pool.submit(() -> {
                Path exe = Paths.get(raw).toAbsolutePath().normalize();
                if (!Files.isRegularFile(exe)) return null;
                int major = javaMajor(exe.toString());
                if (major <= 0) return null;
                Path home = exe.getParent() == null ? null : exe.getParent().getParent();
                String folder = home == null || home.getFileName() == null ? "" : home.getFileName().toString();
                return new JavaRuntime(exe.toString(), major, folder);
            }));
        }
        for (java.util.concurrent.Future<JavaRuntime> f : futures) {
            try {
                JavaRuntime rt = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (rt == null) continue;
                Path home = Paths.get(rt.path()).getParent();
                home = home == null ? null : home.getParent();
                String key = (home == null ? rt.path() : home.toString()).toLowerCase(java.util.Locale.ROOT);
                found.putIfAbsent(key, rt);
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        List<JavaRuntime> list = new ArrayList<>(found.values());
        list.sort((a, b) -> Integer.compare(b.major(), a.major()));
        return list;
    }

    private List<String> javaCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        boolean win = os.contains("win");
        boolean mac = os.contains("mac") || os.contains("darwin");
        List<String> out = new ArrayList<>();

        addJavaExes(out, System.getProperty("java.home"), win, mac);
        addJavaExes(out, System.getenv("JAVA_HOME"), win, mac);
        // 启动器下载的 Java（含 macOS 的 Contents/Home 布局）
        try {
            addJavaRoot(out, Resources.home().resolve("java").resolve(JavaInstaller.platformKey()), win, mac);
        } catch (Throwable ignored) {
        }

        if (win) {
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String base = System.getenv(env);
                if (base == null || base.isBlank()) continue;
                for (String vendor : new String[]{"Java", "Microsoft", "Eclipse Adoptium", "Eclipse Foundation",
                        "AdoptOpenJDK", "BellSoft", "Zulu", "Amazon Corretto", "Semeru", "GraalVM",
                        "JetBrains", "RedHat", "SapMachine", "ojdkbuild", "Dragonwell", "Kona"}) {
                    addJavaRoot(out, Paths.get(base, vendor), win, mac);
                }
            }
            String local = System.getenv("LOCALAPPDATA");
            String programData = System.getenv("ProgramData");
            String appData = System.getenv("APPDATA");
            if (local != null && !local.isBlank()) {
                addJavaRoot(out, Paths.get(local, "Programs", "Eclipse Adoptium"), win, mac);
                addJavaRoot(out, Paths.get(local, "Programs", "Microsoft"), win, mac);
                addJavaRoot(out, Paths.get(local, "Programs", "Common", "Oracle", "Java"), win, mac);
                addJavaRoot(out, Paths.get(local, "JetBrains", "Toolbox", "apps"), win, mac);
            }
            if (programData != null && !programData.isBlank()) {
                addJavaRoot(out, Paths.get(programData, "Oracle", "Java"), win, mac);
                addJavaRoot(out, Paths.get(programData, "chocolatey", "lib"), win, mac);
            }
            if (appData != null && !appData.isBlank()) {
                // 官方启动器下载的运行时
                addJavaRoot(out, Paths.get(appData, ".minecraft", "runtime"), win, mac);
            }
            String user = System.getProperty("user.home");
            if (user != null) {
                addJavaRoot(out, Paths.get(user, ".jdks"), win, mac);
                addJavaRoot(out, Paths.get(user, ".gradle", "jdks"), win, mac);
                addJavaRoot(out, Paths.get(user, "scoop", "apps"), win, mac);
            }
            // 其它盘根目录下的常见开发目录
            for (char drive = 'C'; drive <= 'Z'; drive++) {
                Path root = Paths.get(drive + ":\\");
                if (!Files.isDirectory(root)) continue;
                for (String name : new String[]{"Java", "jdk", "JDK", "dev", "tools"}) {
                    addJavaRoot(out, root.resolve(name), win, mac);
                }
            }
        } else if (mac) {
            for (String root : new String[]{"/Library/Java/JavaVirtualMachines", "/opt/homebrew/opt", "/usr/local/opt"}) {
                addJavaRoot(out, Paths.get(root), win, mac);
            }
        } else {
            for (String root : new String[]{"/usr/lib/jvm", "/usr/local/openjdk", "/usr/java"}) {
                addJavaRoot(out, Paths.get(root), win, mac);
            }
        }
        String userHome = System.getProperty("user.home");
        if (!win && userHome != null) {
            // JetBrains / SDKMAN 装在用户目录，常不在 PATH
            addJavaRoot(out, Paths.get(userHome, ".jdks"), win, mac);
            addJavaRoot(out, Paths.get(userHome, ".sdkman", "candidates", "java"), win, mac);
            if (mac) addJavaRoot(out, Paths.get(userHome, "Library", "Java", "JavaVirtualMachines"), win, mac);
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String part : pathEnv.split(java.io.File.pathSeparator)) {
                if (!part.isBlank()) addJavaBinDir(out, part, win);
            }
        }
        if (mac) {
            out.add("/usr/bin/java");
            out.add("/usr/local/bin/java");
        }
        return out;
    }

    private static void addJavaExes(List<String> out, String home, boolean win, boolean mac) {
        if (home == null || home.isBlank()) return;
        try {
            addJavaExes(out, Paths.get(home), win, mac);
        } catch (Exception ignored) {
        }
    }

    private static void addJavaBinDir(List<String> out, String dir, boolean win) {
        String lower = dir.toLowerCase(java.util.Locale.ROOT);
        if (win && (lower.contains("\\windowsapps") || lower.contains("/windowsapps"))) return;
        try {
            Path base = Paths.get(dir);
            for (String exe : win ? new String[]{"javaw.exe", "java.exe"} : new String[]{"java"}) {
                Path p = base.resolve(exe);
                if (Files.isRegularFile(p)) out.add(p.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private static void addJavaExes(List<String> out, Path home, boolean win, boolean mac) {
        if (home == null) return;
        // WindowsApps 是商店占位别名，运行会弹商店
        String lower = home.toString().toLowerCase(java.util.Locale.ROOT);
        if (win && (lower.contains("\\windowsapps") || lower.contains("/windowsapps"))) return;
        String exe = win ? "javaw.exe" : "java";
        Path bin = home.resolve("bin").resolve(exe);
        if (Files.isRegularFile(bin)) out.add(bin.toString());
        if (win) {
            Path console = home.resolve("bin").resolve("java.exe");
            if (Files.isRegularFile(console)) out.add(console.toString());
        }
        if (mac) {
            Path bundle = home.resolve("Contents/Home/bin/java");
            if (Files.isRegularFile(bundle)) out.add(bundle.toString());
        }
    }

    private static void addJavaRoot(List<String> out, Path root, boolean win, boolean mac) {
        if (root == null || !Files.isDirectory(root)) return;
        addJavaExes(out, root, win, mac);
        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                    .sorted(java.util.Comparator.comparing(Path::toString))
                    .forEach(dir -> {
                        addJavaExes(out, dir, win, mac);
                        try (var inner = Files.list(dir)) {
                            inner.filter(Files::isDirectory).forEach(sub -> addJavaExes(out, sub, win, mac));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    public static String offlineUuid(String name) {
        // 必须 UTF-8，与 HMCL、原版离线 UUID 一致
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static boolean javaIs64(String javaExe) {
        try {
            Path exe = Paths.get(javaExe);
            Path home = exe.getParent() == null ? null : exe.getParent().getParent();
            if (home != null) {
                Path release = home.resolve("release");
                if (Files.isRegularFile(release)) {
                    for (String line : Files.readAllLines(release)) {
                        if (line.startsWith("OS_ARCH=")) {
                            String arch = line.substring("OS_ARCH=".length()).replace("\"", "").trim().toLowerCase(java.util.Locale.ROOT);
                            if (arch.contains("64") || arch.contains("amd64") || arch.contains("aarch64")) return true;
                            if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) return false;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // release 缺失按 64 位；32 位启动时自会报错
        return true;
    }

    // 会起 java -version 子进程（最多等 5 秒），只能后台线程调
    private static final java.util.regex.Pattern JAVA_VERSION_P =
            java.util.regex.Pattern.compile("version \"([^\"]+)\"");

    public static int javaMajor(String javaExe) {
        int fast = javaMajorFast(javaExe);
        if (fast > 0) return fast;
        try {
            Path exe = Paths.get(javaExe);
            // javaw 无控制台，探测统一用同目录 java.exe
            String name = exe.getFileName() == null ? ""
                    : exe.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("javaw.exe") && exe.getParent() != null) {
                Path console = exe.getParent().resolve("java.exe");
                if (Files.isRegularFile(console)) exe = console;
            }
            Process p = new ProcessBuilder(exe.toString(), "-version").redirectErrorStream(true).start();
            try (java.io.InputStream in = p.getInputStream()) {
                // 卡死的 Java 最多等 5 秒
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return 0;
                }
                String text = new String(in.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = JAVA_VERSION_P.matcher(text);
                if (m.find()) return parseJavaMajor(m.group(1));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    // 只读 release 认版本，不起进程；无则 0
    public static int javaMajorFast(String javaExe) {
        try {
            Path exe = Paths.get(javaExe);
            Path home = exe.getParent() != null ? exe.getParent().getParent() : null;
            if (home != null) {
                Path release = home.resolve("release");
                if (Files.isRegularFile(release)) {
                    for (String line : Files.readAllLines(release)) {
                        if (line.startsWith("JAVA_VERSION=")) {
                            return parseJavaMajor(line.substring("JAVA_VERSION=".length())
                                    .replace("\"", "").trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int parseJavaMajor(String version) {
        try {
            if (version.startsWith("1.")) {
                String[] parts = version.split("\\.");
                return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            }
            String head = version.split("[.\\-+_]")[0];
            return Integer.parseInt(head);
        } catch (Exception e) {
            return 0;
        }
    }

    private volatile boolean fallbackVersions = false;

    public boolean isFallbackVersions() {
        return fallbackVersions;
    }

    private synchronized void seedBuiltinVersions() {
        versions.clear();
        String mark = "离线兜底数据 · 官方清单不可用";
        addVersion("1.21.4", "release", "2024/12/03 10:12", mark);
        addVersion("1.21.1", "release", "2024/08/08 14:32", mark);
        addVersion("1.20.6", "release", "2024/04/29 12:32", mark);
        addVersion("1.20.1", "release", "2023/06/12 20:10", mark);
        addVersion("1.19.2", "release", "2022/08/05 20:15", mark);
        addVersion("1.16.5", "release", "2021/01/15 10:00", mark);
        addVersion("1.12.2", "release", "2017/09/18 08:00", mark);
        addVersion("1.7.10", "release", "2014/05/14 08:00", mark);
        addVersion("1.21.4-pre1", "snapshot", "2024/11/28 18:40", mark);
        addVersion("1.6.4", "old", "2013/09/19 08:00", mark);
        addVersion("b1.7.3", "old", "2011/07/08 08:00", mark);
        addVersion("20w14∞", "special", "2020/04/01 20:00", Lang.t("愚人节版 · ") + Lang.t(mark));
        versions.sort((a, b) -> b.time.compareTo(a.time));
        fallbackVersions = true;
    }

    private void addVersion(String id, String type, String time, String lore) {
        McVersion v = new McVersion(id, type, time);
        v.lore = lore;
        versions.add(v);
    }

    private void seedInstances() {
        Path versionsDir = Paths.get(gameDir, "versions");
        if (!Files.isDirectory(versionsDir)) return;
        try (var stream = Files.list(versionsDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                if (!Files.isRegularFile(dir.resolve(name + ".json"))) return;
                boolean exists = false;
                for (Instance i : instances) {
                    // 已登记的版本目录不重复生成
                    if (i.folder != null && i.folder.equals(gameDir)
                            && (i.name.equals(name) || name.equals(i.version))) exists = true;
                }
                if (exists) return;
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                String loader = lower.contains("fabric") ? "Fabric" : lower.contains("neoforge") ? "NeoForge"
                        : lower.contains("forge") ? "Forge" : lower.contains("optifine") ? "OptiFine" : "原版";
                String icon = "Fabric".equals(loader) ? "Fabric.png" : "NeoForge".equals(loader) ? "NeoForge.png"
                        : "Forge".equals(loader) ? "CobbleStone.png" : "Grass.png";
                instances.add(new Instance(name, name, loader, "", "已安装版本", icon, System.currentTimeMillis(), false, gameDir));
            });
        } catch (Exception e) {
            log("读取已安装版本失败：" + Log.describe(e));
        }
    }

    public Account account() {
        if (accounts.isEmpty()) return null;
        if (selectedAccount < 0 || selectedAccount >= accounts.size()) selectedAccount = 0;
        return accounts.get(selectedAccount);
    }

    public Instance instance() {
        if (instances.isEmpty()) return null;
        if (selectedInstance < 0 || selectedInstance >= instances.size()) selectedInstance = 0;
        return instances.get(selectedInstance);
    }

    public McVersion firstOf(String type) {
        return versions.stream().filter(v -> type.equals(v.type)).findFirst().orElse(null);
    }

    public List<McVersion> versionsOf(String group) {
        List<McVersion> out = new ArrayList<>();
        for (McVersion v : versions) if (v.group().equals(group)) out.add(v);
        return out;
    }

    public void refreshVersions() {
        refreshVersions(false, null);
    }

    public void refreshVersions(boolean force, Runnable onDone) {
        refreshVersions(force, onDone, null);
    }

    public void refreshVersions(boolean force, Runnable onDone,
                                java.util.function.DoubleConsumer progress) {
        final long now = System.currentTimeMillis();
        synchronized (this) {
            if (!force && now - lastVersionRefresh < 5 * 60 * 1000L && !versions.isEmpty()) {
                if (onDone != null) runOnFx(onDone);
                return;
            }
            if (versionRefreshRunning) {
                if (onDone != null) versionWaiters.add(onDone);
                return;
            }
            versionRefreshRunning = true;
            if (onDone != null) versionWaiters.add(onDone);
        }
        Threads.start("TCL-Manifest", () -> {
            try {
                lastVersionRefresh = System.currentTimeMillis();
                // 下载版本策略：0 镜像优先 / 1 官方优先并回退 / 2 只用官方
                String official = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
                String mirror = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
                int src = getInt("SystemVersionSource", 1);
                String text = src == 0 ? Downloader.getText(progress, mirror, official)
                        : src == 2 ? Downloader.getText(progress, official)
                        : Downloader.getText(progress, official, mirror);
                java.util.Map<String, Object> m = Json.obj(Json.parse(text));
                List<Object> vs = Json.arr(m.get("versions"));
                List<McVersion> real = new ArrayList<>();
                for (Object o : vs) {
                    java.util.Map<String, Object> vm = Json.obj(o);
                    String id = Json.str(vm, "id", "");
                    if (id.isEmpty()) continue;
                    String type = Json.str(vm, "type", "release");
                    String iso = Json.str(vm, "releaseTime", "");
                    String group = classifyVersion(id, type, iso);
                    if ("special".equals(group) && id.equalsIgnoreCase("20w14infinite")) id = "20w14∞";
                    McVersion v = new McVersion(id, group, formatIso(iso));
                    if ("special".equals(group)) {
                        String lore = foolName(id);
                        v.lore = lore.isEmpty() ? null : lore;
                    }
                    real.add(v);
                }
                if (real.isEmpty()) return;
                synchronized (this) {
                    versions.clear();
                    versions.addAll(real);
                    versions.sort((a, b) -> b.time.compareTo(a.time));
                    fallbackVersions = false;
                }
                // 版本数量不写盘，仅兜底数据需留痕
            } catch (Exception e) {
                log("获取官方版本列表失败，改用标注过的离线兜底数据：" + Log.describe(e));
                seedBuiltinVersions();
            } finally {
                List<Runnable> waiters;
                synchronized (this) {
                    versionRefreshRunning = false;
                    waiters = new ArrayList<>(versionWaiters);
                    versionWaiters.clear();
                }
                for (Runnable r : waiters) runOnFx(r);
            }
        });
    }

    private static void runOnFx(Runnable r) {
        try {
            if (javafx.application.Platform.isFxApplicationThread()) r.run();
            else javafx.application.Platform.runLater(r);
        } catch (Throwable t) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String formatIso(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            String d = s.substring(0, Math.min(10, s.length())).replace('-', '/');
            int t = s.indexOf('T');
            if (t < 0) return d;
            String time = s.substring(t + 1, Math.min(t + 6, s.length()));
            return d + " " + time;
        } catch (Exception e) {
            return s;
        }
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)));
    }

    public boolean crashAnalysisEnabled() {
        if (props.getProperty("LaunchCrashAnalysis") != null) {
            return getBool("LaunchCrashAnalysis", true);
        }
        return !getBool("LaunchAdvanceDisableCrashAnalysis", false);
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    private long lastSaveTick;

    void setRuntime(String key, String value) {
        props.setProperty(key, value);
    }

    public void set(String key, Object value) {
        props.setProperty(key, String.valueOf(value));
        long now = System.currentTimeMillis();
        if (now - lastSaveTick > 600) {
            lastSaveTick = now;
            save();
        }
    }

    public String instanceKey(String key) {
        Instance ins = instance();
        if (ins == null) return key;
        String id = ins.folder == null || ins.folder.isEmpty() ? ins.name : ins.folder;
        return "Instance." + id + "." + key;
    }

    public int getInstanceInt(String key, int def) {
        String v = props.getProperty(instanceKey(key));
        if (v != null) {
            try {
                return Integer.parseInt(v);
            } catch (Exception ignored) {
            }
        }
        return getInt("Instance" + key, def);
    }

    public boolean getInstanceBool(String key, boolean def) {
        String v = props.getProperty(instanceKey(key));
        if (v != null) return Boolean.parseBoolean(v);
        return getBool("Instance" + key, def);
    }

    public String getInstance(String key, String def) {
        String v = props.getProperty(instanceKey(key));
        if (v != null) return v;
        String legacy = props.getProperty("Instance" + key);
        return legacy != null ? legacy : def;
    }

    public void setInstance(String key, Object value) {
        set(instanceKey(key), value);
    }

    public void load() {
        try {
            // 清旧键，防导入残留叠加
            props.clear();
            if (!Files.exists(configPath)) {
                props.setProperty("UiThemePreset", "-1");
                props.setProperty("UiAutoThemeFromBackground", "true");
                props.setProperty("UiBackgroundOpacity", "1000");
                props.setProperty("UiBackgroundBlur", "0");
                props.setProperty("UiBackgroundSuit", "0");
                props.setProperty("UiLauncherTransparent", "600");
                props.setProperty("UiWindowRadius", "6");
                props.setProperty("UiAnimSpeed", "100");
                props.setProperty("UiMenuAnimation", "true");
                props.setProperty("UiListButtonsAlwaysShow", "false");
                props.setProperty("UiTitleAccountBadge", "true");
                props.setProperty("UiRememberWindowPos", "false");
                props.setProperty("SystemLanguage", "0");
                props.setProperty("SystemMaxThreads", "64");
                props.setProperty("SystemDownloadSource", "0");
                return;
            }
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            }
            if (!getBool("LaunchVisibilityMigrated", false)) {
                int old = getInt("LaunchVisibility", -1);
                if (old >= 0 && old <= 2) {
                    props.setProperty("LaunchVisibility", String.valueOf(switch (old) {
                        case 0 -> 5;
                        case 1 -> 4;
                        default -> 3;
                    }));
                }
                props.setProperty("LaunchVisibilityMigrated", "true");
            }
            if (!getBool("LaunchPriorityMigrated", false)) {
                int old = getInt("LaunchPriority", -1);
                if (old == 0) props.setProperty("LaunchPriority", "1");
                else if (old == 1) props.setProperty("LaunchPriority", "0");
                props.setProperty("LaunchPriorityMigrated", "true");
            }
            if (!getBool("LaunchIsolationMigrated", false)) {
                int old = getInt("LaunchIsolation", -1);
                if (old == 0) props.setProperty("LaunchIsolation", "4");
                else if (old == 1) props.setProperty("LaunchIsolation", "0");
                else if (old == 2) props.setProperty("LaunchIsolation", "3");
                props.setProperty("LaunchIsolationMigrated", "true");
            }
            Lang.setMode(getInt("SystemLanguage", 0));
            int count = getInt("account.count", 0);
            accounts.clear();
            for (int i = 0; i < count; i++) {
                String raw = props.getProperty("account." + i);
                if (raw == null) continue;
                String[] p = raw.split("\\|", -1);
                if (p.length >= 4) {
                    Account a = new Account(p[0], p[1], p[2], Boolean.parseBoolean(p[3]));
                    if (p.length >= 6) {
                        a.accessToken = p[4];
                        a.userType = p[5];
                    }
                    if (p.length >= 7) a.clientToken = p[6];
                    if (p.length >= 8) a.server = p[7];
                    if (p.length >= 9) a.refreshToken = p[8];
                    if (p.length >= 10) {
                        try {
                            a.expiresAt = Long.parseLong(p[9]);
                        } catch (Exception ignored) {
                        }
                    }
                    accounts.add(a);
                }
            }
            selectedAccount = getInt("account.selected", 0);
            int icount = getInt("instance.count", 0);
            instances.clear();
            for (int i = 0; i < icount; i++) {
                String raw = props.getProperty("instance." + i);
                if (raw == null) continue;
                String[] p = raw.split("\\|", -1);
                if (p.length >= 9) {
                    Instance ins = new Instance(p[0], p[1], p[2], p[3], p[4], p[5],
                            parseLong(p[6]), Boolean.parseBoolean(p[7]), p[8]);
                    if (p.length >= 10) ins.isolation = (int) parseLong(p[9]);
                    // 旧格式无 addedAt，回退 lastPlayed
                    long added = p.length >= 11 ? parseLong(p[10]) : 0;
                    ins.addedAt = added > 0 ? added : ins.lastPlayed;
                    instances.add(ins);
                }
            }
            migrateLegacyIsolation();
            selectedInstance = getInt("instance.selected", 0);
            folders.clear();
            int foldCount = getInt("folder.count", 0);
            for (int i = 0; i < foldCount; i++) {
                String raw = props.getProperty("folder." + i);
                if (raw == null) continue;
                String[] p = raw.split("\\|", -1);
                if (p.length >= 2) folders.add(new FolderItem(p[0], p[1]));
            }
            String folderPath = get("folder.current", "");
            // 迁移来的空目录回落到启动器所在文件夹
            if (!folderPath.isEmpty() && isStockMinecraft(folderPath)
                    && !Files.isDirectory(Paths.get(folderPath, "versions"))) {
                try {
                    folderPath = Resources.appDir().toString();
                } catch (Exception ignored) {
                }
            }
            if (folders.isEmpty() && !folderPath.isEmpty()) folders.add(new FolderItem("当前文件夹", folderPath));
            if (!folderPath.isEmpty()) gameDir = folderPath;
            javaPath = get("java.path", javaPath);
            favorites.clear();
            int fc = getInt("favorite.count", 0);
            for (int i = 0; i < fc; i++) {
                String f = props.getProperty("favorite." + i);
                if (f != null && !f.isEmpty()) favorites.add(Favorite.decode(f));
            }
        } catch (Exception e) {
            Errors.error("配置加载异常，部分数据使用默认值", e, Errors.Behavior.NONE);
        }
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private void migrateLegacyIsolation() {
        for (Instance ins : instances) {
            if (ins.isolation != 0 || ins.folder == null || ins.folder.isEmpty()) continue;
            String path = ins.folder.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            if (path.contains("/tcl/instances/")) ins.isolation = 2;
        }
    }

    private final Object saveWriteLock = new Object();

    public synchronized void save() {
        byte[] payload;
        try {
            // 先取快照，FX 线程可能正在增删这些列表
            List<Account> accountSnap = new ArrayList<>(accounts);
            List<Instance> instanceSnap = new ArrayList<>(instances);
            List<Instance> orphanSnap = new ArrayList<>(orphanInstances);
            List<FolderItem> folderSnap = new ArrayList<>(folders);
            List<Favorite> favoriteSnap = new ArrayList<>(favorites);
            int selectedAccountSnap = selectedAccount;
            int selectedInstanceSnap = selectedInstance;
            String gameDirSnap = gameDir;
            String javaPathSnap = javaPath;

            props.setProperty("account.count", String.valueOf(accountSnap.size()));
            for (int i = 0; i < accountSnap.size(); i++) {
                Account a = accountSnap.get(i);
                props.setProperty("account." + i, Favorite.esc(a.name) + "|" + a.uuid + "|"
                        + Favorite.esc(a.skin) + "|" + a.online
                        + "|" + a.accessToken + "|" + Favorite.esc(a.userType) + "|" + a.clientToken
                        + "|" + a.server.replace("|", " ")
                        + "|" + (a.refreshToken == null ? "" : a.refreshToken.replace("|", " "))
                        + "|" + a.expiresAt);
            }
            props.setProperty("account.selected", String.valueOf(selectedAccountSnap));
            List<Instance> persisted = new ArrayList<>(instanceSnap);
            persisted.addAll(orphanSnap);
            props.setProperty("instance.count", String.valueOf(persisted.size()));
            for (int i = 0; i < persisted.size(); i++) {
                Instance ins = persisted.get(i);
                // 按 | 切分，文本字段须过 esc
                props.setProperty("instance." + i, Favorite.esc(ins.name) + "|" + Favorite.esc(ins.version) + "|"
                        + Favorite.esc(ins.loader) + "|" + Favorite.esc(ins.loaderVersion) + "|"
                        + Favorite.esc(ins.description) + "|" + Favorite.esc(ins.icon) + "|" + ins.lastPlayed + "|"
                        + ins.favorite + "|" + Favorite.esc(ins.folder) + "|" + ins.isolation + "|" + ins.addedAt);
            }
            props.setProperty("instance.selected", String.valueOf(selectedInstanceSnap));
            props.setProperty("folder.current", gameDirSnap == null ? "" : gameDirSnap);
            props.setProperty("folder.count", String.valueOf(folderSnap.size()));
            for (int i = 0; i < folderSnap.size(); i++) {
                props.setProperty("folder." + i, Favorite.esc(folderSnap.get(i).name)
                        + "|" + Favorite.esc(folderSnap.get(i).path));
            }
            props.setProperty("java.path", javaPathSnap == null ? "" : javaPathSnap);
            props.setProperty("favorite.count", String.valueOf(favoriteSnap.size()));
            for (int i = 0; i < favoriteSnap.size(); i++) {
                props.setProperty("favorite." + i, favoriteSnap.get(i).encode());
            }
            props.setProperty("SystemLanguage", String.valueOf(Lang.setting));
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            props.store(buf, "TCraft Launcher JavaFX");
            payload = buf.toByteArray();
        } catch (IOException e) {
            Errors.error("保存配置失败（账户、实例与设置可能不会被保留）", e);
            return;
        } catch (RuntimeException e) {
            // 并发改动等：记日志不外抛
            Errors.error("保存配置时发生异常（本次改动可能未写入）", e);
            return;
        }
        // saveWriteLock 只保证 .tmp 替换原子性
        try {
            synchronized (saveWriteLock) {
                Files.createDirectories(configPath.getParent());
                Path tmp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
                try (OutputStream out = Files.newOutputStream(tmp,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                        java.nio.file.StandardOpenOption.WRITE)) {
                    out.write(payload);
                }
                try {
                    Files.move(tmp, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            restrictPermissions(configPath);
        } catch (IOException e) {
            Errors.error("保存配置失败（账户、实例与设置可能不会被保留）", e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            return;
        } catch (Exception ignored) {
            // 没有 POSIX 权限概念的平台（Windows）走下面的 ACL
        }
        try {
            // 配置里有明文 token，Windows 上把 ACL 收紧到只剩文件所有者
            java.nio.file.attribute.AclFileAttributeView acl =
                    Files.getFileAttributeView(file, java.nio.file.attribute.AclFileAttributeView.class);
            if (acl == null) return;
            java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(
                            java.nio.file.attribute.AclEntryPermission.READ_DATA,
                            java.nio.file.attribute.AclEntryPermission.WRITE_DATA,
                            java.nio.file.attribute.AclEntryPermission.APPEND_DATA,
                            java.nio.file.attribute.AclEntryPermission.READ_NAMED_ATTRS,
                            java.nio.file.attribute.AclEntryPermission.WRITE_NAMED_ATTRS,
                            java.nio.file.attribute.AclEntryPermission.READ_ATTRIBUTES,
                            java.nio.file.attribute.AclEntryPermission.WRITE_ATTRIBUTES,
                            java.nio.file.attribute.AclEntryPermission.DELETE,
                            java.nio.file.attribute.AclEntryPermission.READ_ACL,
                            java.nio.file.attribute.AclEntryPermission.WRITE_ACL,
                            java.nio.file.attribute.AclEntryPermission.SYNCHRONIZE)
                    .build();
            acl.setAcl(java.util.List.of(entry));
        } catch (Exception ignored) {
        }
    }

    public void applyProxy() {
        try {
            applyProxyProps();
        } finally {
            // 老连接仍是旧代理配置
            if (INSTANCE != null) Http.rebuild();
        }
    }

    private void applyProxyProps() {
        // 0 不使用 / 1 跟随系统 / 2 HTTP / 3 SOCKS5
        int mode = Math.max(0, Math.min(3, getInt("SystemHttpProxyType", 1)));
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        java.net.Authenticator.setDefault(null);
        if (mode == 1) {
            System.setProperty("java.net.useSystemProxies", "true");
            return;
        }
        System.setProperty("java.net.useSystemProxies", "false");
        if (mode == 0) return;

        String host = get("SystemProxyHost", "");
        String port = get("SystemProxyPort", "7890");
        if (host.isEmpty()) {
            String address = get("SystemProxyAddress", "");
            if (address.contains(":")) {
                String[] parts = address.split(":", 2);
                host = parts[0];
                port = parts[1];
            } else if (!address.isEmpty()) {
                host = address;
            }
        }
        if (host.isEmpty()) return;
        if (mode == 3) {
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", port);
        } else {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", port);
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", port);
        }
        String user = get("SystemProxyUser", "");
        String pass = get("SystemProxyPass", "");
        if (!user.isEmpty() || !pass.isEmpty()) {
            java.net.Authenticator.setDefault(new java.net.Authenticator() {
                @Override
                protected java.net.PasswordAuthentication getPasswordAuthentication() {
                    return new java.net.PasswordAuthentication(user, pass.toCharArray());
                }
            });
        }
    }

    public String describeProxy() {
        int mode = Math.max(0, Math.min(3, getInt("SystemHttpProxyType", 1)));
        String prefix = Lang.t("当前：");
        String address = get("SystemProxyHost", "") + ":" + get("SystemProxyPort", "7890");
        return switch (mode) {
            case 0 -> prefix + Lang.t("不使用代理");
            case 1 -> prefix + Lang.t("跟随系统代理");
            case 2 -> prefix + Lang.t("HTTP 代理") + " " + address;
            default -> prefix + Lang.t("SOCKS5 代理") + " " + address;
        };
    }

    // 子进程 JVM 的代理 -D 参数；host 空或模式非 2/3 时返回空表
    public List<String> proxyJvmArgs() {
        List<String> args = new ArrayList<>();
        int mode = getInt("SystemHttpProxyType", 1);
        String host = get("SystemProxyHost", "");
        String port = get("SystemProxyPort", "7890");
        if (!host.isEmpty() && (mode == 2 || mode == 3)) {
            if (mode == 3) {
                args.add("-DsocksProxyHost=" + host);
                args.add("-DsocksProxyPort=" + port);
            } else {
                args.add("-Dhttp.proxyHost=" + host);
                args.add("-Dhttp.proxyPort=" + port);
                args.add("-Dhttps.proxyHost=" + host);
                args.add("-Dhttps.proxyPort=" + port);
            }
        }
        return args;
    }

    public boolean exportConfig(Path target) {
        try {
            save();
            Files.copy(configPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log("导出配置失败：" + Log.describe(e));
            return false;
        }
    }

    public boolean importConfig(Path source) {
        try {
            Properties incoming = new Properties();
            try (InputStream in = Files.newInputStream(source)) {
                incoming.load(in);
            }
            // 命令/可执行/代理类键拒收
            List<String> dropped = new ArrayList<>();
            for (String key : new ArrayList<>(incoming.stringPropertyNames())) {
                if (isImportBlockedKey(key)) {
                    incoming.remove(key);
                    dropped.add(key);
                }
            }
            // 缺特征键（account/instance 等）即非配置文件
            if (!incoming.containsKey("account.count") && !incoming.containsKey("instance.count")
                    && !incoming.containsKey("folder.count") && !incoming.containsKey("UiThemePreset")) {
                log("导入配置失败：文件不包含启动器配置项");
                return false;
            }
            Files.createDirectories(configPath.getParent());
            Path tmp = configPath.resolveSibling(configPath.getFileName() + ".import.tmp");
            try (OutputStream out = Files.newOutputStream(tmp,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE)) {
                incoming.store(out, "TCraft Launcher JavaFX");
            }
            try {
                Files.move(tmp, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(configPath);
            load();
            if (!dropped.isEmpty()) {
                log("导入配置时已忽略 " + dropped.size() + " 项敏感设置：" + String.join("、", dropped));
            }
            return true;
        } catch (Exception e) {
            log("导入配置失败：" + Log.describe(e));
            return false;
        }
    }

    // 导入黑名单：命令、可执行路径、下载源与代理不能由外部文件决定
    static boolean isImportBlockedKey(String key) {
        if (key == null || key.isEmpty()) return false;
        String k = key.toLowerCase(java.util.Locale.ROOT);
        // 启动前/后命令会直接交给 cmd.exe / sh -c 执行
        if (k.contains("command")) return true;
        // 指向任意可执行文件的路径
        if (k.equals("java.path")) return true;
        // legacy 键与实例变体都落到 java.executable
        if (k.equals("instancejava") || (k.startsWith("instance.") && k.endsWith(".java"))) return true;
        // classpath 头最先加载，等于任意代码执行
        if (k.endsWith("classpathhead")) return true;
        // 缓存根重定向配合清理功能可以删任意目录
        if (k.equals("systemsystemcache")) return true;
        // 下载源与代理可以把后续所有下载导向第三方
        if (k.equals("systembmclroot")) return true;
        if (k.startsWith("systemproxy")) return true;
        // -javaagent 等是任意代码执行入口
        return k.endsWith("jvmargs") || k.endsWith("gameargs");
    }

    // 运行日志：内存留一份供界面兜底，同时异步追加到 log/launcher.log
    public void log(String line) {
        String stamped = Redact.apply("[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line);
        log.add(stamped);
        Log.info(stamped);
        if (getBool("SystemDebugMode", false)) System.out.println("[TCL] " + stamped);
    }
}
