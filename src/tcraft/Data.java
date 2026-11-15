package tcraft;

import java.net.URI;
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

/** 账户、实例、文件夹、版本表和设置的持久化。 */
public final class Data {

    public static class Account {
        public String name;
        public String uuid;
        public String skin; // 头像用的皮肤图文件名
        public boolean online;
        public String accessToken = "";
        public String clientToken = "";
        public String userType = ""; // msa / legacy / nide / ""
        /** 第三方登录源：nide 是服务器码，legacy 是地址。 */
        public String server = "";
        /** 微软长期令牌，用来在 Minecraft 令牌过期后自动续期。 */
        public String refreshToken = "";
        /** Minecraft 令牌过期时间（毫秒），0 表示还不知道。 */
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
        public String loader;  // 原版 / Forge / Fabric / NeoForge / OptiFine
        public String loaderVersion;
        public String description;
        public String icon;  // Images 下的文件名
        public long lastPlayed;
        public boolean favorite;
        public String folder;
        /** 版本隔离：0 跟随全局设置，1 强制隔离，2 不隔离。 */
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
            this.favorite = favorite;
            this.folder = folder;
            this.isolation = isolation;
        }

        public String subtitle() {
            if (description != null && !description.isEmpty()) return description;
            StringBuilder sb = new StringBuilder();
            sb.append("正式版 ").append(version);
            if (loader != null && !loader.isEmpty() && !"原版".equals(loader)) {
                sb.append("，").append(loader);
                if (loaderVersion != null) sb.append(" ").append(loaderVersion);
            }
            return sb.toString();
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

    /** 收藏的社区资源。 */
    public static class Favorite {
        public String type = "mod";
        public String slug = "";
        public String name = "";
        public String desc = "";
        public String icon = "";
        public String website = "";
        /** 来源平台：modrinth / curseforge。 */
        public String platform = "";
        /** 平台项目 ID，CurseForge 要数字。 */
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

        /** Mojang 清单里标成 release 的愚人节版本。 */
    private static final java.util.Set<String> SPECIAL_IDS = java.util.Set.of(
            "20w14infinite", "20w14∞", "2.0", "15w14a", "23w13a_or_b", "22w13oneblockatatime",
            "24w14potato", "25w14craftmine", "1.rv-pre1", "3d shareware v1.34", "26w14a");

    /** 愚人节版本的宣传语。 */
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

    /** 版本归类：release / snapshot / old / special。 */
    private static String classifyVersion(String id, String type, String releaseTimeIso) {
        String lower = id.toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "release":
                return "release";
            case "snapshot":
                // Mojang 标错：1.x 旧快照算正式版
                if (lower.startsWith("1.") && !lower.contains("combat") && !lower.contains("rc")
                        && !lower.contains("experimental") && !lower.contains("pre")) {
                    return "release";
                }
                if (SPECIAL_IDS.contains(lower)) return "special";
                // 4 月 1 日发的快照算愚人节版
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

    public final List<Account> accounts = new ArrayList<>();
    public final List<Instance> instances = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<FolderItem> folders = new ArrayList<>();
    public final List<McVersion> versions = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<Favorite> favorites = new ArrayList<>();
    public int selectedAccount = 0;
    public int selectedInstance = 0;
    public String selectedTab = "launch";
    public boolean rememberLastPage = true;
    public String gameDir;
    public String javaPath = "";
    public String downloadCategory = "原版游戏";
    public String otherSection = "about";
    public String setupSection = "launch";
    public String instanceSection = "overview";
    public final List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 启动过程日志，调试模式下显示。 */
    public final List<String> launchLog = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 上次拉版本清单的时间。 */
    private volatile long lastVersionRefresh;

    /** 记一条带时间戳的启动日志。 */
    public void launchLog(String line) {
        String stamped = "[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line;
        launchLog.add(stamped);
        if (launchLog.size() > 500) launchLog.remove(0);
        log(line);
    }

    private final Properties props = new Properties();
    private final Path configPath;

    private Data() {
        Path home = Resources.home();
        configPath = home.resolve("config.properties");
        gameDir = detectGameDir();
        // 启动不灌内置版本，等官方清单。
        load();
        pruneInstances();
        if (instances.isEmpty()) seedInstances();
        if (folders.isEmpty()) folders.add(new FolderItem("当前文件夹", gameDir));
        if (javaPath == null || javaPath.isEmpty()) javaPath = detectJava();
        // exe 外壳选定的 Java 优先级最高
        String forcedJava = System.getProperty("tcraft.java", "");
        if (!forcedJava.isEmpty() && usableJava(forcedJava)) {
            javaPath = forcedJava;
        } else {
            // Windows 外壳把选择结果写在 launcher.cfg
            try {
                Path cfg = configPath.getParent().resolve("launcher.cfg");
                if (Files.isRegularFile(cfg)) {
                    String picked = Files.readString(cfg, StandardCharsets.UTF_8).trim();
                    if (usableJava(picked)) javaPath = picked;
                }
            } catch (Exception ignored) {
            }
        }
        log.add("[TCL] TCraft Launcher " + Build.VERSION + " (JavaFX Edition)");
        log.add("[TCL] 当前游戏目录：" + gameDir);
        log.add("[TCL] Java：" + (javaPath.isEmpty() ? "未找到" : javaPath));
        applyProxy();
    }

    /** 暂时找不到版本文件的实例，只在本次会话隐藏。 */
    private final java.util.List<Instance> orphanInstances = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 实例跟着磁盘状态走，读不到时不清元数据。 */
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

    /** 数据目录，config.properties 在这儿。 */
    public Path baseDir() {
        return configPath.getParent();
    }

    private String detectGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft").toString();
        }
        String appdata = System.getenv("APPDATA");
        if (appdata != null && os.contains("win")) {
            Path p = Paths.get(appdata, ".minecraft");
            if (Files.exists(p)) return p.toString();
            return p.toString();
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null) {
            Path p = Paths.get(xdg, "minecraft");
            if (Files.exists(p)) return p.toString();
            return p.toString();
        }
        return Paths.get(System.getProperty("user.home"), ".minecraft").toString();
    }

    /** 一个可用的 Java 运行时。 */
    public record JavaRuntime(String path, int major, String folder) {
    }

    private volatile List<JavaRuntime> javaScanCache;
    private volatile long javaScanAt;

    /** 清掉扫描缓存，下次重新读磁盘。 */
    public void invalidateJavaScan() {
        javaScanCache = null;
        javaScanAt = 0;
    }

    /** 选中版本最高的 17+ Java，没有 17+ 就用最高版本。 */
    public String detectJava() {
        List<JavaRuntime> list = scanJavaRuntimes();
        for (JavaRuntime r : list) {
            if (r.major >= 17) return r.path;
        }
        return list.isEmpty() ? "" : list.get(0).path;
    }

    /** 路径存在且能读到版本号才算可用。 */
    public static boolean usableJava(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            return Files.isRegularFile(Paths.get(path)) && javaMajor(path) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 设置游戏用 Java，并同步给 exe 外壳。 */
    public void setJavaRuntime(String path, int major) {
        javaPath = path;
        set("java.path", path);
        save();
        if (major >= 17) writeLauncherCfg(path);
    }

    /** 写 .tcl\launcher.cfg，下次由外壳直接启动。 */
    private void writeLauncherCfg(String path) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath.getParent().resolve("launcher.cfg"), path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /** 扫描本机 Java，按版本降序；60 秒内复用结果。 */
    public List<JavaRuntime> scanJavaRuntimes() {
        long now = System.currentTimeMillis();
        List<JavaRuntime> cached = javaScanCache;
        if (cached != null && now - javaScanAt < 60_000) return cached;
        java.util.LinkedHashMap<String, JavaRuntime> found = new java.util.LinkedHashMap<>();
        for (String raw : javaCandidates()) {
            if (raw == null || raw.isBlank()) continue;
            try {
                Path exe = Paths.get(raw).toAbsolutePath().normalize();
                if (!Files.isRegularFile(exe)) continue;
                Path home = exe.getParent() == null ? null : exe.getParent().getParent();
                // 同一个 Java 的 javaw / java 只保留一个
                String key = (home == null ? exe.toString() : home.toString()).toLowerCase(java.util.Locale.ROOT);
                if (found.containsKey(key)) continue;
                int major = javaMajor(exe.toString());
                if (major <= 0) continue;
                String folder = home == null || home.getFileName() == null ? "" : home.getFileName().toString();
                found.put(key, new JavaRuntime(exe.toString(), major, folder));
            } catch (Exception ignored) {
            }
        }
        List<JavaRuntime> list = new ArrayList<>(found.values());
        list.sort((a, b) -> Integer.compare(b.major, a.major));
        javaScanCache = list;
        javaScanAt = now;
        return list;
    }

    /** 收集可能的 java 可执行文件，Windows 额外查注册表。 */
    private List<String> javaCandidates() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        boolean win = os.contains("win");
        boolean mac = os.contains("mac") || os.contains("darwin");
        List<String> out = new ArrayList<>();

        addJavaExes(out, System.getProperty("java.home"), win, mac);
        addJavaExes(out, System.getenv("JAVA_HOME"), win, mac);

        if (win) {
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String base = System.getenv(env);
                if (base == null || base.isBlank()) continue;
                for (String vendor : new String[]{"Java", "Microsoft", "Eclipse Adoptium", "Eclipse Foundation",
                        "BellSoft", "Zulu", "Amazon Corretto", "Semeru", "GraalVM", "JetBrains"}) {
                    addJavaRoot(out, Paths.get(base, vendor), win, mac);
                }
            }
            String local = System.getenv("LOCALAPPDATA");
            String programData = System.getenv("ProgramData");
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
            String user = System.getProperty("user.home");
            if (user != null) {
                addJavaRoot(out, Paths.get(user, ".jdks"), win, mac);
                addJavaRoot(out, Paths.get(user, "scoop", "apps"), win, mac);
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

    /** PATH 里的项本身就是 bin 目录。 */
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
        // WindowsApps 里是应用商店的占位别名，运行会弹商店
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

    /** 安装根目录最多向下找两层，覆盖 jdk-17 与 scoop 这类布局。 */
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
        // 必须用 UTF-8，和 HMCL、原版离线模式算出来的一致；用平台默认编码中文名会对不上
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 读 Java 主版本号，先看 release 文件。 */
    public static int javaMajor(String javaExe) {
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
            Process p = new ProcessBuilder(javaExe, "-version").redirectErrorStream(true).start();
            // 卡死的 Java 不能拖住启动，最多等 5 秒
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return 0;
            }
            String text = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("version \"([^\"]+)\"").matcher(text);
            if (m.find()) return parseJavaMajor(m.group(1));
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

    /** 版本清单是否来自离线兜底。 */
    private volatile boolean fallbackVersions = false;

    public boolean isFallbackVersions() {
        return fallbackVersions;
    }

    /** 离线兜底版本表，官方清单失败时才用。 */
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
        addVersion("20w14∞", "special", "2020/04/01 20:00", "愚人节版 · " + mark);
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
                    // 已登记实例用的就是这个版本目录，不再重复生成「已导入的版本」
                    if (i.folder != null && i.folder.equals(gameDir)
                            && (i.name.equals(name) || name.equals(i.version))) exists = true;
                }
                if (exists) return;
                String lower = name.toLowerCase();
                String loader = lower.contains("fabric") ? "Fabric" : lower.contains("neoforge") ? "NeoForge"
                        : lower.contains("forge") ? "Forge" : lower.contains("optifine") ? "OptiFine" : "原版";
                String icon = "Fabric".equals(loader) ? "Fabric.png" : "NeoForge".equals(loader) ? "NeoForge.png"
                        : "Forge".equals(loader) ? "CobbleStone.png" : "Grass.png";
                instances.add(new Instance(name, name, loader, "", "已安装版本", icon, System.currentTimeMillis(), false, gameDir));
            });
        } catch (Exception ignored) {
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

    /** 拉 Mojang 官方版本列表，失败保留兜底。 */
    public void refreshVersions() {
        refreshVersions(false, null);
    }

    /** 刷新版本清单，完成后在 FX 线程回调。 */
    public void refreshVersions(boolean force, Runnable onDone) {
        // 5 分钟内不重复拉
        if (!force && System.currentTimeMillis() - lastVersionRefresh < 5 * 60 * 1000L && !versions.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                lastVersionRefresh = System.currentTimeMillis();
                // 下载版本策略：0 镜像优先 / 1 官方优先并回退 / 2 只用官方
                String official = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
                String mirror = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
                int src = getInt("SystemVersionSource", 1);
                String text = src == 0 ? Downloader.getText(mirror, official)
                        : src == 2 ? Downloader.getText(official)
                        : Downloader.getText(official, mirror);
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
                log("已从官方获取 " + real.size() + " 个版本");
            } catch (Exception e) {
                log("获取官方版本列表失败，改用标注过的离线兜底数据：" + e.getMessage());
                seedBuiltinVersions();
            } finally {
                if (onDone != null) runOnFx(onDone);
            }
        }, "TCL-Manifest").start();
    }

    /** 在 FX 线程执行回调，没有图形环境就直接跑。 */
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

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    private long lastSaveTick;

    /** 只改内存不落盘，截图自检用。 */
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

    public Properties props() {
        return props;
    }

    public void load() {
        try {
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
            // 旧可见性 0 保持 1 最小化 2 隐藏，新语义 5 保持 4 最小化 3 隐藏并恢复
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
            // 旧进程优先级 0 中 1 高 2 低，新语义 0 高 1 中 2 低
            if (!getBool("LaunchPriorityMigrated", false)) {
                int old = getInt("LaunchPriority", -1);
                if (old == 0) props.setProperty("LaunchPriority", "1");
                else if (old == 1) props.setProperty("LaunchPriority", "0");
                props.setProperty("LaunchPriorityMigrated", "true");
            }
            // 旧版本隔离 0 全隔离 1 不隔离 2 仅 Mod，新语义 4 0 3
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
            // 配置坏了就取默认值继续，只写日志不弹窗。
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

    /** 1.3.14 及更早的「隔离」是整棵目录装进 TCL/instances，本身已经独立，不再套一层。 */
    private void migrateLegacyIsolation() {
        for (Instance ins : instances) {
            if (ins.isolation != 0 || ins.folder == null || ins.folder.isEmpty()) continue;
            String path = ins.folder.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            if (path.contains("/tcl/instances/")) ins.isolation = 2;
        }
    }

    /** 下载线程和界面线程都会调，串行化避免写坏配置文件。 */
    public synchronized void save() {
        try {
            Files.createDirectories(configPath.getParent());
            props.setProperty("account.count", String.valueOf(accounts.size()));
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                props.setProperty("account." + i, a.name + "|" + a.uuid + "|" + a.skin + "|" + a.online
                        + "|" + a.accessToken + "|" + a.userType + "|" + a.clientToken
                        + "|" + a.server.replace("|", " ")
                        + "|" + (a.refreshToken == null ? "" : a.refreshToken.replace("|", " "))
                        + "|" + a.expiresAt);
            }
            props.setProperty("account.selected", String.valueOf(selectedAccount));
            // 隐藏实例追加到末尾写回，索引不变
            List<Instance> persisted = new ArrayList<>(instances);
            persisted.addAll(orphanInstances);
            props.setProperty("instance.count", String.valueOf(persisted.size()));
            for (int i = 0; i < persisted.size(); i++) {
                Instance ins = persisted.get(i);
                props.setProperty("instance." + i, ins.name + "|" + ins.version + "|" + ins.loader + "|"
                        + ins.loaderVersion + "|" + ins.description + "|" + ins.icon + "|" + ins.lastPlayed + "|"
                        + ins.favorite + "|" + ins.folder + "|" + ins.isolation);
            }
            props.setProperty("instance.selected", String.valueOf(selectedInstance));
            props.setProperty("folder.current", gameDir);
            props.setProperty("folder.count", String.valueOf(folders.size()));
            for (int i = 0; i < folders.size(); i++) {
                props.setProperty("folder." + i, folders.get(i).name + "|" + folders.get(i).path);
            }
            props.setProperty("java.path", javaPath);
            props.setProperty("favorite.count", String.valueOf(favorites.size()));
            for (int i = 0; i < favorites.size(); i++) props.setProperty("favorite." + i, favorites.get(i).encode());
            props.setProperty("SystemLanguage", String.valueOf(Lang.mode));
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "TCraft Launcher JavaFX");
            }
        } catch (IOException e) {
            // 落盘失败要报出来
            Errors.error("保存配置失败（账户、实例与设置可能不会被保留）", e);
        }
    }

    /** 把设置里的代理应用到 JVM 网络栈。 */
    public void applyProxy() {
        try {
            applyProxyProps();
        } finally {
            // 改代理后重建 HttpClient，老连接还是旧配置。
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

    /** 网络页显示的代理状态。 */
    public String describeProxy() {
        int mode = Math.max(0, Math.min(3, getInt("SystemHttpProxyType", 1)));
        return switch (mode) {
            case 0 -> "当前：不使用代理";
            case 1 -> "当前：跟随系统代理";
            case 2 -> "当前：HTTP 代理 " + get("SystemProxyHost", "") + ":" + get("SystemProxyPort", "7890");
            default -> "当前：SOCKS5 代理 " + get("SystemProxyHost", "") + ":" + get("SystemProxyPort", "7890");
        };
    }

    public boolean exportConfig(Path target) {
        try {
            save();
            Files.copy(configPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean importConfig(Path source) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.copy(source, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            load();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 日志先入队，由后台线程批量落盘，游戏输出多时不会卡 UI。 */
    private final java.util.concurrent.BlockingQueue<String> logQueue =
            new java.util.concurrent.LinkedBlockingQueue<>(5000);
    private volatile Thread logWriter;

    public void log(String line) {
        String stamped = "[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line;
        log.add(stamped);
        if (log.size() > 500) log.remove(0);
        if (getBool("SystemDebugMode", false)) System.out.println("[TCL] " + stamped);
        startLogWriter();
        if (!logQueue.offer(stamped)) {
            logQueue.poll();
            logQueue.offer(stamped);
        }
    }

    private synchronized void startLogWriter() {
        if (logWriter != null) return;
        logWriter = new Thread(this::drainLogQueue, "TCL-LogWriter");
        logWriter.setDaemon(true);
        logWriter.start();
    }

    private void drainLogQueue() {
        Path file = configPath.getParent().resolve("launcher.log");
        while (true) {
            try {
                String first = logQueue.take();
                StringBuilder batch = new StringBuilder(first);
                long until = System.currentTimeMillis() + 400;
                while (true) {
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) break;
                    String next = logQueue.poll(Math.min(left, 100),
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (next == null) break;
                    batch.append(System.lineSeparator()).append(next);
                }
                Files.createDirectories(file.getParent());
                Files.writeString(file, batch + System.lineSeparator(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (InterruptedException e) {
                return;
            } catch (Exception ignored) {
            }
        }
    }
}
