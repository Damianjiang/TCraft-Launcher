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

        public Instance(String name, String version, String loader, String loaderVersion,
                        String description, String icon, long lastPlayed, boolean favorite, String folder) {
            this.name = name;
            this.version = version;
            this.loader = loader;
            this.loaderVersion = loaderVersion;
            this.description = description;
            this.icon = icon;
            this.lastPlayed = lastPlayed;
            this.favorite = favorite;
            this.folder = folder;
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
        Path home = Paths.get(System.getProperty("user.home"));
        configPath = home.resolve(".tcraft-launcher-javafx").resolve("config.properties");
        gameDir = detectGameDir();
        // 启动不灌内置版本，等官方清单。
        load();
        pruneInstances();
        if (instances.isEmpty()) seedInstances();
        if (folders.isEmpty()) folders.add(new FolderItem("当前文件夹", gameDir));
        if (javaPath == null || javaPath.isEmpty()) javaPath = detectJava();
        log.add("[TCL] TCraft Launcher 1.3.7 (JavaFX Edition)");
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

    public String detectJava() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean win = os.contains("win");
        String exe = win ? "javaw.exe" : "java";
        // 当前运行环境自带的 Java，最稳
        String runtime = System.getProperty("java.home");
        if (runtime != null) {
            Path p = Paths.get(runtime, "bin", exe);
            if (Files.isRegularFile(p)) return p.toString();
        }
        // JAVA_HOME
        String jhome = System.getenv("JAVA_HOME");
        if (jhome != null && !jhome.isEmpty()) {
            if (Files.isRegularFile(Paths.get(jhome, "bin", exe))) return Paths.get(jhome, "bin", exe).toString();
            if (Files.isRegularFile(Paths.get(jhome, exe))) return Paths.get(jhome, exe).toString();
        }
        // 各平台固定路径
        if (win) {
            for (String c : new String[]{
                    "C:\\Program Files\\Microsoft\\jdk-25.0.4.7-hotspot\\bin\\javaw.exe",
                    "C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe"}) {
                if (Files.isRegularFile(Paths.get(c))) return c;
            }
        } else if (os.contains("mac")) {
            // 真实 JDK 优先，/usr/bin/java 在 Apple Silicon 只是 stub
            List<Path> candidates = new ArrayList<>();
            String[] roots = {"/Library/Java/JavaVirtualMachines", "/opt/homebrew/opt", "/usr/local/opt"};
            for (String root : roots) {
                java.io.File dir = new java.io.File(root);
                java.io.File[] list = dir.listFiles();
                if (list == null) continue;
                for (java.io.File f : list) {
                    Path candidate = root.startsWith("/Library")
                            ? f.toPath().resolve("Contents/Home/bin/java")
                            : f.toPath().resolve("bin/java");
                    if (Files.isRegularFile(candidate)) candidates.add(candidate);
                }
            }
            Path best = null;
            int bestMajor = -1;
            for (Path c : candidates) {
                int major = javaMajor(c.toString());
                if (major > bestMajor) {
                    bestMajor = major;
                    best = c;
                }
            }
            if (best != null) return best.toString();
            if (new java.io.File("/usr/bin/java").canExecute()) return "/usr/bin/java";
        }
        // PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String sep = win ? ";" : java.io.File.pathSeparator;
            for (String part : pathEnv.split(sep)) {
                Path p = Paths.get(part, exe);
                if (Files.isRegularFile(p)) return p.toString();
            }
        }
        return "";
    }

    public static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).toString();
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
            String text = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor();
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
                for (Instance i : instances) if (i.name.equals(name) && i.folder.equals(gameDir)) exists = true;
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
                    instances.add(ins);
                }
            }
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

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            props.setProperty("account.count", String.valueOf(accounts.size()));
            for (int i = 0; i < accounts.size(); i++) {
                Account a = accounts.get(i);
                props.setProperty("account." + i, a.name + "|" + a.uuid + "|" + a.skin + "|" + a.online
                        + "|" + a.accessToken + "|" + a.userType + "|" + a.clientToken
                        + "|" + a.server.replace("|", " "));
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
                        + ins.favorite + "|" + ins.folder);
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

    public void log(String line) {
        String stamped = "[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line;
        log.add(stamped);
        if (log.size() > 500) log.remove(0);
        // 调试模式同时输出到控制台
        if (getBool("SystemDebugMode", false)) System.out.println("[TCL] " + stamped);
        // 同时写 launcher.log
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath.getParent().resolve("launcher.log"),
                    stamped + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    }
