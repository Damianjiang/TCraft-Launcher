package tcraft;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Modpacks {

    private Modpacks() {
    }

    public static class PackInfo {
        public boolean valid;
        public String typeName = "";
        public String name = "";
        public String mcVersion = "";
        public String loader = "";
        public String loaderVersion = "";
        public boolean genericLayout;
        public boolean mmcLayout;
        public List<String> unsupportedLoaders = new ArrayList<>();
        public long sizeBytes;
        public String readError = "";
    }

    // readError 用于区分文案 须恒等比较
    static String unrecognizedError() {
        return Lang.t("无法识别整合包格式（压缩包里没有清单文件）");
    }

    public static PackInfo readInfo(Path file) {
        PackInfo info = new PackInfo();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            info.sizeBytes = Files.size(file);
            String base = baseFolder(zip);
            if (base == null) {
                info.readError = unrecognizedError();
                return info;
            }
            ZipEntry mcbbs = entry(zip, base + "mcbbs.packmeta");
            ZipEntry mmc = entry(zip, base + "mmc-pack.json");
            ZipEntry index = entry(zip, base + "modrinth.index.json");
            ZipEntry manifest = entry(zip, base + "manifest.json");
            ZipEntry genericPack = entry(zip, base + "modpack.json");
            if (mcbbs != null) {
                info.typeName = "MCBBS";
                parseMcbbs(readJson(zip, mcbbs), info);
            } else if (mmc != null) {
                info.typeName = "通用整合包";
                info.mmcLayout = true;
                parseMmc(readJson(zip, mmc), info);
                ZipEntry cfg = entry(zip, base + "instance.cfg");
                if (cfg != null) {
                    try (InputStream in = zip.getInputStream(cfg)) {
                        for (String line : new String(Io.read(in, 1024L * 1024), StandardCharsets.UTF_8).split("\n")) {
                            if (line.startsWith("name=")) {
                                info.name = line.substring(5).trim();
                                break;
                            }
                        }
                    }
                }
            } else if (genericPack != null) {
                info.typeName = "通用整合包";
                info.genericLayout = true;
                parseGenericPack(readJson(zip, genericPack), info);
                parseGenericManifest(zip, base, info);
            } else if (index != null) {
                info.typeName = "Modrinth";
                parseModrinth(readJson(zip, index), info);
            } else if (manifest != null) {
                java.util.Map<String, Object> json = readJson(zip, manifest);
                if (Json.obj(json.get("addons")).isEmpty()) {
                    info.typeName = "CurseForge";
                    parseCurseForge(json, info);
                } else {
                    info.typeName = "MCBBS";
                    parseMcbbs(json, info);
                }
            } else {
                info.readError = unrecognizedError();
                return info;
            }
            if (info.name.isEmpty()) {
                info.name = file.getFileName().toString().replaceAll("\\.(zip|mrpack|rar)$", "");
            }
            normalizeLoaderVersion(info);
            if (!safePackVersion(info.mcVersion) || !safePackVersion(info.loaderVersion)) {
                info.valid = false;
                info.readError = Lang.t("整合包版本号包含非法字符");
                return info;
            }
            info.valid = !info.mcVersion.isEmpty();
        } catch (Exception e) {
            info.readError = Lang.tf("压缩包损坏或读取失败：%s", e.getMessage() == null ? e.toString() : e.getMessage());
            Data.get().log("预读整合包失败：" + Log.describe(e));
        }
        return info;
    }

    private static void parseCurseForge(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        java.util.Map<String, Object> minecraft = Json.obj(json.get("minecraft"));
        info.mcVersion = Json.str(minecraft, "version", info.mcVersion).trim();
        List<Object> loaders = Json.arr(minecraft.get("modLoaders"));
        String primaryId = "";
        for (Object o : loaders) {
            java.util.Map<String, Object> l = Json.obj(o);
            if (Json.str(l, "primary", "").equalsIgnoreCase("true")) {
                primaryId = Json.str(l, "id", "");
                break;
            }
        }
        if (primaryId.isEmpty() && !loaders.isEmpty()) {
            primaryId = Json.str(Json.obj(loaders.get(0)), "id", "");
        }
        parseLoaderId(primaryId, info);
    }

    private static void parseModrinth(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        java.util.Map<String, Object> deps = Json.obj(json.get("dependencies"));
        for (java.util.Map.Entry<String, Object> e : deps.entrySet()) {
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
            switch (e.getKey().toLowerCase(java.util.Locale.ROOT)) {
                case "minecraft" -> info.mcVersion = value;
                case "forge" -> {
                    info.loader = "Forge";
                    info.loaderVersion = value;
                }
                case "neoforge", "neo-forge" -> {
                    info.loader = "NeoForge";
                    info.loaderVersion = value;
                }
                case "fabric-loader" -> {
                    info.loader = "Fabric";
                    info.loaderVersion = value;
                }
                case "quilt-loader" -> info.unsupportedLoaders.add("quilt-" + value);
            }
        }
    }

    private static void parseMcbbs(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        for (Object o : Json.arr(json.get("addons"))) {
            java.util.Map<String, Object> addon = Json.obj(o);
            String id = Json.str(addon, "id", "").toLowerCase();
            String version = Json.str(addon, "version", "").trim();
            switch (id) {
                case "game" -> info.mcVersion = version;
                case "forge" -> {
                    info.loader = "Forge";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "neoforge" -> {
                    info.loader = "NeoForge";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "fabric" -> {
                    info.loader = "Fabric";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "quilt" -> info.unsupportedLoaders.add("quilt-" + version);
            }
        }
    }

    private static String loaderVersion(String version, PackInfo info) {
        String v = version == null ? "" : version.trim();
        if (!info.mcVersion.isEmpty() && v.startsWith(info.mcVersion + "-")) {
            return v.substring(info.mcVersion.length() + 1);
        }
        return v;
    }

    private static void normalizeLoaderVersion(PackInfo info) {
        info.loaderVersion = loaderVersion(info.loaderVersion, info);
    }

    private static void parseLoaderId(String id, PackInfo info) {
        if (id == null || id.isBlank()) return;
        String text = id.trim();
        String lower = text.toLowerCase();
        if (lower.startsWith("forge-")) {
            if (lower.endsWith("-recommended") || lower.endsWith("-latest")) {
                info.unsupportedLoaders.add(text);
                return;
            }
            info.loader = "Forge";
            info.loaderVersion = loaderVersion(text.substring("forge-".length()), info);
        } else if (lower.startsWith("neoforge-")) {
            info.loader = "NeoForge";
            info.loaderVersion = loaderVersion(text.substring("neoforge-".length()), info);
        } else if (lower.startsWith("fabric-")) {
            info.loader = "Fabric";
            info.loaderVersion = loaderVersion(text.substring("fabric-".length()), info);
        } else {
            info.unsupportedLoaders.add(text);
        }
    }

    private static void parseMmc(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        for (Object o : Json.arr(json.get("components"))) {
            java.util.Map<String, Object> c = Json.obj(o);
            String uid = Json.str(c, "uid", "");
            String version = Json.str(c, "version", "").trim();
            switch (uid) {
                case "net.minecraft" -> info.mcVersion = version;
                case "net.minecraftforge" -> {
                    info.loader = "Forge";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "net.neoforged" -> {
                    info.loader = "NeoForge";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "net.fabricmc.fabric-loader" -> {
                    info.loader = "Fabric";
                    info.loaderVersion = loaderVersion(version, info);
                }
                case "org.quiltmc.quilt-loader" -> info.unsupportedLoaders.add("quilt-" + version);
            }
        }
    }

    private static void parseGenericPack(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        info.mcVersion = Json.str(json, "gameVersion", "").trim();
    }

    private static void parseGenericManifest(ZipFile zip, String base, PackInfo info) {
        try {
            ZipEntry pack = entry(zip, base + "minecraft/pack.json");
            if (pack == null) return;
            java.util.Map<String, Object> json = readJson(zip, pack);
            if (info.mcVersion.isEmpty()) {
                Object jarValue = json.get("jar");
                String jar = jarValue instanceof String s ? s.trim() : "";
                info.mcVersion = jar.isEmpty() ? Json.str(json, "id", "").trim() : jar;
            }
            if (!info.loader.isEmpty()) return;
            // 先查 libraries
            for (Object o : Json.arr(json.get("libraries"))) {
                String name = Json.str(Json.obj(o), "name", "");
                int at = name.indexOf('@');
                if (at > 0) name = name.substring(0, at);
                String[] parts = name.split(":");
                if (parts.length < 3) continue;
                String group = parts[0].toLowerCase(java.util.Locale.ROOT);
                String artifact = parts[1].toLowerCase(java.util.Locale.ROOT);
                String version = parts[2];
                if (group.equals("net.fabricmc") && artifact.equals("fabric-loader")) {
                    info.loader = "Fabric";
                    info.loaderVersion = version;
                    return;
                }
                if (group.equals("net.minecraftforge") && artifact.equals("forge")) {
                    info.loader = "Forge";
                    int dash = version.indexOf('-');
                    info.loaderVersion = dash >= 0 ? version.substring(dash + 1) : version;
                    return;
                }
                if (group.equals("net.neoforged") && (artifact.equals("neoforge") || artifact.equals("forge"))) {
                    info.loader = "NeoForge";
                    info.loaderVersion = version;
                    return;
                }
                if (group.equals("optifine") && artifact.equals("optifine")) {
                    info.loader = "OptiFine";
                    int cut = version.lastIndexOf('_');
                    info.loaderVersion = cut >= 0 ? version.substring(cut + 1) : version;
                    return;
                }
                if (group.equals("org.quiltmc") && artifact.equals("quilt-loader")) {
                    info.unsupportedLoaders.add("quilt-" + version);
                    return;
                }
            }
            String inherits = Json.str(json, "inheritsFrom", "").trim();
            String lower = inherits.toLowerCase(java.util.Locale.ROOT);
            int neo = lower.indexOf("-neoforge-");
            if (neo >= 0) {
                info.loader = "NeoForge";
                info.loaderVersion = inherits.substring(neo + 10);
                return;
            }
            int forge = lower.indexOf("-forge-");
            if (forge >= 0) {
                info.loader = "Forge";
                info.loaderVersion = inherits.substring(forge + 7);
                return;
            }
            int fabric = lower.indexOf("fabric-loader-");
            if (fabric >= 0) {
                String rest = inherits.substring(fabric + 14);
                int dash = rest.indexOf('-');
                info.loader = "Fabric";
                info.loaderVersion = dash > 0 ? rest.substring(0, dash) : rest;
            }
        } catch (Exception e) {
            Data.get().log("读取整合包 pack.json 失败：" + Log.describe(e));
        }
    }

    public static void installFromNetwork(MainWindow win, ResourceVersion packFile, ResourceInfo info, String instanceName) {
        String name = instanceName == null ? "" : instanceName.trim();
        String error = instanceNameError(name);
        if (!error.isEmpty()) {
            win.toast("实例名称不可用", error, 2);
            return;
        }
        Tasks.Task task = new Tasks.Task("整合包安装：" + name);
        task.subs.add(new Tasks.SubTask("下载整合包文件"));
        task.subs.add(new Tasks.SubTask("安装整合包内容"));
        Tasks.INSTANCE.add(task);
        win.openPage("tasks", true);
        Data data = Data.get();
        Thread thread = new Thread(() -> {
            try {
                Path versionsDir = Paths.get(data.gameDir, "versions", name);
                Files.createDirectories(versionsDir);
                String ext = packFile.resourceType.equals("modpack") && packFile.fileName.toLowerCase().endsWith(".mrpack")
                        ? "mrpack" : "zip";
                Path target = versionsDir.resolve("原始整合包." + ext);
                task.subs.get(0).state = Tasks.State.RUNNING;
                task.stage = "下载整合包文件";
                tryDownload(task, packFile.downloadUrls, target, packFile.hash);
                task.subs.get(0).state = Tasks.State.DONE;
                task.subs.get(1).state = Tasks.State.RUNNING;
                installFromZip(win, target, name, task, 1);
            } catch (Exception e) {
                failTask(win, task, data, name, e);
            } finally {
            }
        }, "TCL-Modpack-Network-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    public static void installLocal(MainWindow win, Path packPath, PackInfo info, String instanceName) {
        String name = instanceName == null ? "" : instanceName.trim();
        String error = instanceNameError(name);
        if (!error.isEmpty()) {
            win.toast("实例名称不可用", error, 2);
            return;
        }
        Tasks.Task task = new Tasks.Task("整合包安装：" + name);
        task.subs.add(new Tasks.SubTask("安装整合包内容"));
        Tasks.INSTANCE.add(task);
        win.openPage("tasks", true);
        Data data = Data.get();
        data.log("开始安装整合包：" + packPath + "（" + info.typeName + "，MC " + info.mcVersion
                + (info.loader.isEmpty() ? "" : "，" + info.loader + " " + info.loaderVersion) + "）");
        Thread thread = new Thread(() -> {
            try {
                installFromZip(win, packPath, name, task, 0);
            } catch (Exception e) {
                failTask(win, task, data, name, e);
            } finally {
            }
        }, "TCL-Modpack-Local-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    // 版本号会拼进目录与下载地址 拒路径分隔与上跳
    static boolean safePackVersion(String value) {
        if (value == null || value.isEmpty()) return true;
        if (value.length() > 64 || value.contains("..") || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0) return false;
        return value.matches("[A-Za-z0-9._+~-]+");
    }

    static String instanceNameError(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "名称不能为空。";
        if (value.length() > 64) return "名称不能超过 64 个字符。";
        if (value.equals(".") || value.equals("..")) return "名称不合法。";
        for (char c : "\\/:*?\"<>|".toCharArray()) {
            if (value.indexOf(c) >= 0) return Lang.tf("名称不能包含字符：%s", c);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127) return Lang.t("名称不能包含控制字符。");
        }
        if (value.endsWith(".") || value.endsWith(" ")) return "名称不能以点或空格结尾。";
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        if (upper.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return "这是系统保留名称。";
        Data data = Data.get();
        if (Files.exists(Paths.get(data.gameDir, "versions", value))) return "已存在同名实例。";
        String current = Paths.get(data.gameDir).toAbsolutePath().normalize().toString();
        for (Data.Instance ins : data.instances) {
            if (!value.equalsIgnoreCase(ins.name)) continue;
            String folder = ins.folder == null || ins.folder.isEmpty() ? ""
                    : Paths.get(ins.folder).toAbsolutePath().normalize().toString();
            if (folder.equalsIgnoreCase(current)) return "已存在同名实例。";
        }
        return "";
    }

    private static void failTask(MainWindow win, Tasks.Task task, Data data, String instanceName, Exception e) {
        if (task.canceled) {
            task.state = Tasks.State.CANCELED;
            task.stage = "已取消";
        } else {
            task.state = Tasks.State.FAILED;
            task.error = e.getMessage() == null ? "安装失败" : e.getMessage();
            task.stage = "整合包安装失败";
            data.log("整合包安装失败：" + task.error);
            Platform.runLater(() -> win.toast("整合包安装失败", task.error, 3));
        }
        cleanupInstance(data, instanceName);
    }

    private static void cleanupInstance(Data data, String instanceName) {
        try {
            Path dir = Paths.get(data.gameDir, "versions", instanceName);
            Data.Instance created = null;
            for (Data.Instance ins : data.instances) {
                if (instanceName.equals(ins.name) && ins.folder != null
                        && Paths.get(ins.folder).toAbsolutePath().normalize()
                        .equals(Paths.get(data.gameDir).toAbsolutePath().normalize())) {
                    created = ins;
                    break;
                }
            }
            boolean selected = created != null && data.instances.indexOf(created) == data.selectedInstance;
            if (created != null) data.instances.remove(created);
            if (selected) data.selectedInstance = data.instances.isEmpty() ? 0
                    : Math.max(0, data.selectedInstance - 1);
            data.save();
            deleteRecursive(dir);
        } catch (Exception e) {
            data.log("清理整合包实例失败：" + Log.describe(e));
        }
    }

    private static void installFromZip(MainWindow win, Path zipPath, String instanceName, Tasks.Task task,
                                       int fromSub) throws Exception {
        Data data = Data.get();
        int idx = fromSub;
        Path versionFolder = Paths.get(data.gameDir, "versions", instanceName);
        Files.createDirectories(versionFolder);
        Path tempDir = Files.createTempDirectory("tcl-modpack-");
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            String base = baseFolder(zip);
            ZipEntry mcbbs = entry(zip, base + "mcbbs.packmeta");
            ZipEntry index = entry(zip, base + "modrinth.index.json");
            ZipEntry manifest = entry(zip, base + "manifest.json");
            ZipEntry mmc = entry(zip, base + "mmc-pack.json");
            ZipEntry genericManifest = entry(zip, base + "modpack.json");
            PackInfo info = new PackInfo();
            List<String> contentDirs = new ArrayList<>();
            boolean isMcbbs = false;
            java.util.Map<String, Object> mcbbsJson = null;
            if (mcbbs != null) {
                isMcbbs = true;
                mcbbsJson = readJson(zip, mcbbs);
                parseMcbbs(mcbbsJson, info);
                contentDirs.add(base + "overrides");
            } else if (mmc != null) {
                info.typeName = "通用整合包";
                info.mmcLayout = true;
                parseMmc(readJson(zip, mmc), info);
                contentDirs.add(base + ".minecraft");
            } else if (genericManifest != null) {
                info.typeName = "通用整合包";
                info.genericLayout = true;
                parseGenericPack(readJson(zip, genericManifest), info);
                parseGenericManifest(zip, base, info);
                contentDirs.add(base.endsWith("minecraft/") ? base : base + "minecraft");
            } else if (index != null) {
                parseModrinth(readJson(zip, index), info);
                // client-overrides 优先于 overrides
                contentDirs.add(base + "overrides");
                contentDirs.add(base + "client-overrides");
            } else if (manifest != null) {
                java.util.Map<String, Object> json = readJson(zip, manifest);
                if (Json.obj(json.get("addons")).isEmpty()) {
                    parseCurseForge(json, info);
                    String ov = Json.str(json, "overrides", "overrides").trim();
                    if (ov.isEmpty()) ov = "overrides";
                    contentDirs.add(base + ov);
                } else {
                    isMcbbs = true;
                    mcbbsJson = json;
                    parseMcbbs(json, info);
                    contentDirs.add(base + "overrides");
                }
            } else {
                throw new Exception(Lang.t("无法识别整合包类型（缺少 modrinth.index.json / manifest.json / mcbbs.packmeta）"));
            }
            normalizeLoaderVersion(info);
            if (!safePackVersion(info.mcVersion) || !safePackVersion(info.loaderVersion)) {
                throw new Exception(Lang.t("整合包版本号包含非法字符"));
            }
            if (info.mcVersion.isEmpty()) {
                throw new Exception(Lang.t("整合包未提供 Minecraft 版本信息"));
            }
            if (!info.unsupportedLoaders.isEmpty()) {
                throw new Exception(Lang.t("该整合包使用了 TCL 不支持的加载器：") + String.join(Lang.t("、"), info.unsupportedLoaders));
            }

            task.stage = "解压整合包文件";
            task.progress = 0.05;
            extractContent(zip, tempDir, versionFolder, base, info, contentDirs);
            // 清包内危险设置
            sanitizeImportedInstanceSettings(versionFolder);

            if (index != null) {
                downloadModrinthContent(win, zip, index, versionFolder, task);
            } else if (info.genericLayout || info.mmcLayout) {
                // 这类包的内容全在覆盖目录里
                task.progress = 0.6;
            } else if (isMcbbs) {
                downloadMcbbsContent(win, mcbbsJson, versionFolder, task);
            } else if (manifest != null) {
                downloadCurseForgeContent(win, readJson(zip, manifest), versionFolder, task);
            }
            // 装完再清 TCL/
            sanitizeImportedInstanceSettings(versionFolder);

            task.stage = "整理文件";
            Path saves = versionFolder.resolve("saves");
            if (Files.isDirectory(saves)) {
                try (var stream = Files.list(saves)) {
                    for (Path p : stream.toList()) {
                        if (p.toString().toLowerCase().endsWith(".zip")) {
                            unzipTo(p, saves);
                            Files.deleteIfExists(p);
                        }
                    }
                }
            }
            if (zipPath.getFileName() != null && zipPath.getFileName().toString().startsWith("原始整合包.")) {
                Files.deleteIfExists(zipPath);
            }
            task.progress = 0.85;

            String iconPath = "";
            java.util.List<String> iconCandidates = new ArrayList<>(java.util.List.of("icon.png", "pack.png", "logo.png", "icon.jpg", "logo.jpg"));
            if (info.mmcLayout) {
                Path tempBase = tempDir.toAbsolutePath().normalize();
                Path cfg = tempBase.resolve(base + "instance.cfg").normalize();
                if (cfg.startsWith(tempBase) && Files.isRegularFile(cfg)) {
                    try (InputStream cin = Files.newInputStream(cfg)) {
                        for (String line : new String(Io.read(cin, 1024L * 1024), StandardCharsets.UTF_8).split("\n")) {
                            if (line.startsWith("iconKey=") && !line.substring(8).isBlank()) {
                                String key = safeIconName(line.substring(8));
                                if (!key.isEmpty()) iconCandidates.add(0, key + ".png");
                                break;
                            }
                        }
                    }
                }
            }
            for (String candidate : iconCandidates) {
                Path p = versionFolder.resolve(candidate);
                if (Files.isRegularFile(p) && Files.size(p) <= 8L * 1024 * 1024) {
                    Path logoDir = versionFolder.resolve("TCL");
                    Files.createDirectories(logoDir);
                    Path logo = logoDir.resolve("Logo.png");
                    Files.copy(p, logo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    iconPath = logo.toAbsolutePath().toString();
                    break;
                }
                Path fromTemp = tempDir.resolve(candidate);
                if (Files.isRegularFile(fromTemp) && Files.size(fromTemp) <= 8L * 1024 * 1024) {
                    Path logoDir = versionFolder.resolve("TCL");
                    Files.createDirectories(logoDir);
                    Path logo = logoDir.resolve("Logo.png");
                    Files.copy(fromTemp, logo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    iconPath = logo.toAbsolutePath().toString();
                    break;
                }
            }

            String desc = info.typeName + Lang.t(" · MC ") + info.mcVersion
                    + (info.loader.isEmpty() ? "" : Lang.t("，") + info.loader + " " + info.loaderVersion);
            Data.Instance instance = new Data.Instance(instanceName, instanceName,
                    info.loader.isEmpty() ? "原版" : info.loader, info.loaderVersion, desc,
                    iconPath, System.currentTimeMillis(), false, data.gameDir, Isolation.ON);
            instance.addedAt = System.currentTimeMillis();
            for (Data.Instance ins : data.instances) {
                if (instanceName.equals(ins.name)) {
                    data.instances.remove(ins);
                    break;
                }
            }
            data.instances.add(instance);
            data.selectedInstance = data.instances.size() - 1;
            data.save();
            task.progress = 0.9;
            if (idx < task.subs.size()) task.subs.get(idx).state = Tasks.State.DONE;
            task.stage = "安装游戏版本 " + info.mcVersion
                    + (info.loader.isEmpty() ? "" : " · " + info.loader);
            task.progress = 0.92;

            // 确保版本 json 存在
            String loader = info.loader.isEmpty() ? "原版" : info.loader;
            String actual;
            try {
                actual = Downloader.install(task, new Data.McVersion(info.mcVersion, "release", ""), loader,
                        data, win, Paths.get(data.gameDir), info.loaderVersion, null);
            } catch (Exception e) {
                if (task.canceled) {
                    task.state = Tasks.State.CANCELED;
                    task.stage = "已取消";
                    return;
                }
                // 内容已解压 不删实例目录
                task.state = Tasks.State.FAILED;
                task.error = Lang.tf("整合包内容已解压，但游戏版本安装失败：%s",
                        e.getMessage() == null ? Lang.t("网络错误") : e.getMessage());
                task.stage = "游戏版本安装失败";
                data.log(task.error);
                String msg = task.error;
                Platform.runLater(() -> win.toast("整合包内容已就绪，游戏版本未装完", msg, 3));
                return;
            }
            if (actual != null && !actual.isEmpty()) {
                try {
                    Path dir = Paths.get(data.gameDir, "versions", instanceName);
                    Files.createDirectories(dir);
                    String json = "{\"id\":" + Json.quote(instanceName)
                            + ",\"inheritsFrom\":" + Json.quote(actual)
                            + ",\"type\":\"release\"}";
                    Files.writeString(dir.resolve(instanceName + ".json"), json, StandardCharsets.UTF_8);
                    data.save();
                    Platform.runLater(win::refreshRight);
                    data.log("整合包版本已就绪：" + instanceName + " → " + actual);
                } catch (Exception e) {
                    data.log("写整合包版本 json 失败：" + Log.describe(e));
                }
            }
            task.progress = 1;
            task.state = Tasks.State.DONE;
            task.stage = "整合包安装完成";
            data.log("整合包安装完成：" + instanceName + " → " + actual);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    // 清除包内命令与附加参数
    private static void sanitizeImportedInstanceSettings(Path versionFolder) {
        try {
            Path ini = versionFolder.resolve("TCL").resolve("Setup.ini");
            if (!Files.isRegularFile(ini)) return;
            if (Files.size(ini) > 1024L * 1024) {
                Files.deleteIfExists(ini);
                Data.get().log("整合包自带实例设置文件过大，已删除");
                return;
            }
            List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
            List<String> kept = new ArrayList<>();
            List<String> dropped = new ArrayList<>();
            for (String line : lines) {
                String t = line.trim();
                int eq = t.indexOf('=');
                String key = eq > 0 ? t.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT) : "";
                if (eq > 0 && (key.equals("precommand") || key.equals("precommandwait")
                        || key.equals("jvmargs") || key.equals("gameargs")
                        || key.equals("java") || key.equals("classpathhead")
                        || key.equals("serverenter"))) {
                    dropped.add(key);
                    continue;
                }
                kept.add(line);
            }
            if (!dropped.isEmpty()) {
                Files.write(ini, kept, StandardCharsets.UTF_8);
                Data.get().log("整合包自带实例设置中的 " + String.join("、", dropped)
                        + " 已被忽略（只允许本机手动设置）");
            }
        } catch (Exception e) {
            Data.get().log("检查整合包实例设置失败：" + Log.describe(e));
        }
    }

    // 仅接受纯文件名
    private static String safeIconName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) return "";
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) return "";
        if (name.equals(".") || name.equals("..")) return "";
        return name;
    }

    private static void downloadModrinthContent(MainWindow win, ZipFile zip, ZipEntry indexEntry,
                                                Path versionFolder, Tasks.Task task) throws Exception {
        java.util.Map<String, Object> json = readJson(zip, indexEntry);
        List<Object> files = Json.arr(json.get("files"));
        List<DownloadJob> jobs = new ArrayList<>();
        for (Object o : files) {
            if (task.canceled) throw new java.io.IOException(Lang.t("已取消"));
            java.util.Map<String, Object> f = Json.obj(o);
            java.util.Map<String, Object> env = Json.obj(f.get("env"));
            if (!env.isEmpty() && "unsupported".equalsIgnoreCase(Json.str(env, "client", ""))) continue;
            String relPath = Json.str(f, "path", "");
            if (relPath.isEmpty()) continue;
            java.util.Map<String, Object> hashes = Json.obj(f.get("hashes"));
            String sha1 = Json.str(hashes, "sha1", "");
            Path target = versionFolder.resolve(relPath).normalize();
            if (!target.startsWith(versionFolder)) continue; // 别写穿
            // TCL/ 是配置目录 禁止 javaagent
            if (target.startsWith(versionFolder.resolve("TCL"))) {
                logSkipped("跳过写向 TCL 目录的整合包内容：" + relPath);
                continue;
            }
            List<String> urls = new ArrayList<>();
            for (Object u : Json.arr(f.get("downloads"))) urls.add(String.valueOf(u));
            if (Downloader.existingDownloadValid(target, sha1)) continue;
            if (urls.isEmpty()) {
                throw new java.io.IOException(Lang.tf("整合包缺少文件下载地址：%s", relPath));
            }
            Files.createDirectories(target.getParent());
            jobs.add(new DownloadJob(urls, target, sha1));
        }
        downloadAll(task, jobs, 0.15, 0.75);
    }

    private static void downloadCurseForgeContent(MainWindow win, java.util.Map<String, Object> manifest, Path versionFolder,
                                                  Tasks.Task task) throws Exception {
        List<Long> required = new ArrayList<>();
        List<Long> optional = new ArrayList<>();
        for (Object o : Json.arr(manifest.get("files"))) {
            java.util.Map<String, Object> f = Json.obj(o);
            long fileId = (long) Json.num(f, "fileID", 0);
            if (fileId <= 0) continue;
            boolean req = !"false".equalsIgnoreCase(Json.str(f, "required", "true"));
            (req ? required : optional).add(fileId);
        }
        List<Long> all = new ArrayList<>(required);
        all.addAll(optional);
        if (all.isEmpty()) return;
        List<Object> fileInfos = CurseForge.filesByIds(all);
        if (!required.isEmpty()) {
            java.util.Set<String> foundRequired = new java.util.HashSet<>();
            for (Object o : fileInfos) {
                foundRequired.add(String.valueOf((long) Json.num(Json.obj(o), "id", -1)));
            }
            List<Long> missingRequired = new ArrayList<>();
            for (Long id : required) {
                if (!foundRequired.contains(String.valueOf(id))) missingRequired.add(id);
            }
            if (!missingRequired.isEmpty()) {
                logSkipped("整合包缺失以下必需文件（可能已被原作者删除）：" + missingRequired);
                boolean go = askInBackground(win, "整合包内容缺失",
                        Lang.tf("未找到该整合包所需的 %d 个内容，它们可能已被原作者删除。\n"
                                + "缺失这些内容可能导致整合包出现异常！是否继续安装？", missingRequired.size()));
                if (!go) throw new java.io.IOException(Lang.t("已取消"));
            }
        }
        java.util.Set<Long> skipOptional = new java.util.HashSet<>();
        if (!optional.isEmpty()) {
            skipOptional = askOptionalInBackground(win, fileInfos, optional);
            if (skipOptional == null) throw new java.io.IOException(Lang.t("已取消"));
        }
        List<Object> installFiles = new ArrayList<>();
        for (Object o : fileInfos) {
            long fileId = (long) Json.num(Json.obj(o), "id", 0);
            if (!skipOptional.contains(fileId)) installFiles.add(o);
        }
        int noUrl = 0;
        List<DownloadJob> jobs = new ArrayList<>();
        java.util.Map<Long, Integer> classCache = new java.util.HashMap<>();
        for (Object o : installFiles) {
            if (task.canceled) throw new java.io.IOException(Lang.t("已取消"));
            java.util.Map<String, Object> f = Json.obj(o);
            ResourceVersion rv = ResourceVersion.fromCurseForgeJson(f, "mod");
            if (rv.fileName.isEmpty()) continue;
            if (rv.downloadUrls.isEmpty()) {
                // 作者禁止分发时不返回下载地址
                noUrl++;
                logSkipped("整合包内容没有下载地址：" + rv.fileName);
                continue;
            }
            List<String> modules = new ArrayList<>();
            for (Object m : Json.arr(f.get("modules"))) {
                modules.add(Json.str(Json.obj(m), "name", ""));
            }
            String sub = inferModuleTarget(modules, rv.fileName);
            String lower = rv.fileName.toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".litemod")) {
                long modId = (long) Json.num(f, "modId", 0);
                if (modId > 0) {
                    // 分类未知则中止
                    int classId;
                    try {
                        classId = classCache.computeIfAbsent(modId, id -> {
                            try {
                                return CurseForge.projectInfo(id).classId;
                            } catch (Exception e) {
                                throw new RuntimeException(e.getMessage() == null ? Lang.t("网络错误") : e.getMessage(), e);
                            }
                        });
                    } catch (RuntimeException re) {
                        Throwable cause = re.getCause() != null ? re.getCause() : re;
                        throw new java.io.IOException(Lang.tf("项目分类获取失败（id=%s）：%s", modId,
                                cause.getMessage() == null ? Lang.t("网络错误") : cause.getMessage()), cause);
                    }
                    sub = targetForClass(classId, sub);
                }
            }
            Path target = versionFolder.resolve(sub).resolve(rv.fileName).normalize();
            if (!target.startsWith(versionFolder)) continue; // 别写穿目录
            // TCL/ 不可写穿
            if (target.startsWith(versionFolder.resolve("TCL"))) {
                logSkipped("跳过写向 TCL 目录的整合包内容：" + target.getFileName());
                continue;
            }
            if (Downloader.existingDownloadValid(target, rv.hash)) continue;
            Files.createDirectories(target.getParent());
            jobs.add(new DownloadJob(rv.downloadUrls, target, rv.hash));
        }
        downloadAll(task, jobs, 0.15, 0.75);
        if (noUrl > 0 && win != null) {
            final int n = noUrl;
            javafx.application.Platform.runLater(() -> win.toast("整合包内容缺失",
                    Lang.tf("有 %d 个文件没有下载地址，已跳过，请在安装后手动补齐", n), 4));
        }
    }

    private static void downloadMcbbsContent(MainWindow win, java.util.Map<String, Object> json, Path versionFolder, Tasks.Task task) throws Exception {
        List<Object> files = Json.arr(json.get("files"));
        List<DownloadJob> jobs = new ArrayList<>();
        for (Object o : files) {
            if (task.canceled) throw new java.io.IOException(Lang.t("已取消"));
            java.util.Map<String, Object> f = Json.obj(o);
            java.util.Map<String, Object> source = Json.obj(f.get("source"));
            String url = Json.str(source, "url", "");
            String relPath = Json.str(f, "target", "");
            if (relPath.isEmpty()) continue;
            var hashes = Json.obj(source.get("hashes"));
            Path target = versionFolder.resolve(relPath).normalize();
            if (!target.startsWith(versionFolder)) continue;
            // TCL/ 不可写穿
            if (target.startsWith(versionFolder.resolve("TCL"))) {
                logSkipped("跳过写向 TCL 目录的整合包内容：" + relPath);
                continue;
            }
            String sha1 = Json.str(hashes, "sha1", "");
            if (Downloader.existingDownloadValid(target, sha1)) continue;
            if (url.isEmpty()) {
                throw new java.io.IOException(Lang.tf("整合包缺少文件下载地址：%s", relPath));
            }
            Files.createDirectories(target.getParent());
            jobs.add(new DownloadJob(List.of(url), target, sha1));
        }
        downloadAll(task, jobs, 0.15, 0.75);
    }

    static String inferModuleTarget(List<String> modules, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        // jar 优先归 mod
        if (lower.endsWith(".jar") || lower.endsWith(".litemod")) return "mods";
        if (lower.endsWith(".mrpack")) return "modpacks";
        for (String m : modules) {
            String n = m == null ? "" : m.toLowerCase();
            if (n.contains("shaders")) return "shaderpacks";
            if (n.equals("data")) return "datapacks";
            if (n.equals("assets")) return "resourcepacks";
        }
        if (lower.endsWith(".zip")) return "resourcepacks";
        return "mods";
    }

    private static String targetForClass(int classId, String fallback) {
        return switch (classId) {
            case CurseForge.CLASS_RESOURCEPACK -> "resourcepacks";
            case CurseForge.CLASS_SHADER -> "shaderpacks";
            case CurseForge.CLASS_DATAPACK -> "datapacks";
            case CurseForge.CLASS_WORLD -> "saves";
            default -> fallback;
        };
    }

    private static boolean askInBackground(MainWindow win, String title, String message) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        AtomicBoolean alive = new AtomicBoolean(true);
        // 模态占用时 3s 后重试 仍占用按取消
        showAsk(win, latch, ok, alive, title, message, 1);
        try {
            // 10 分钟超时按取消
            if (!latch.await(10, java.util.concurrent.TimeUnit.MINUTES)) {
                alive.set(false);
                Platform.runLater(() -> {
                    win.setModalCloseHook(null);
                    if (win.hasOpenModal()) win.closeModal();
                });
                return false;
            }
        } catch (InterruptedException e) {
            alive.set(false);
            Platform.runLater(() -> {
                win.setModalCloseHook(null);
                if (win.hasOpenModal()) win.closeModal();
            });
            Thread.currentThread().interrupt();
            return false;
        }
        return ok.get();
    }

    private static void showAsk(MainWindow win, CountDownLatch latch, AtomicBoolean ok,
                                AtomicBoolean alive, String title, String message, int attempt) {
        Platform.runLater(() -> {
            if (!alive.get()) {
                latch.countDown();
                return;
            }
            if (win.hasOpenModal()) {
                if (attempt <= 0) {
                    latch.countDown();
                    return;
                }
                javafx.animation.PauseTransition retry =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
                retry.setOnFinished(e -> showAsk(win, latch, ok, alive, title, message, attempt - 1));
                retry.play();
                return;
            }
            VBox card = new VBox(12);
            card.setPadding(new Insets(20, 22, 16, 22));
            card.setPrefWidth(430);
            card.setBackground(Ui.fill(Theme.dialogBg, 12));
            card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
            javafx.scene.control.Label body = Ui.label(message, 12.5, Theme.gray1);
            body.setWrapText(true);
            body.setMaxWidth(380);
            HBox actions = new HBox(10);
            actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            actions.getChildren().addAll(
                    Ui.button("取消", Ui.BTN_NORMAL, () -> {
                        win.closeModal();
                        latch.countDown();
                    }),
                    Ui.button("继续", Ui.BTN_THEME, () -> {
                        ok.set(true);
                        win.closeModal();
                        latch.countDown();
                    }));
            card.getChildren().addAll(Ui.bold(title, 17, Theme.text), body, actions);
            win.showModal(card, false);
            // hook 在 showModal 后设
            win.setModalCloseHook(latch::countDown);
        });
    }

    // 返回未勾选的可选文件 id
    private static java.util.Set<Long> askOptionalInBackground(MainWindow win, List<Object> fileInfos,
                                                               List<Long> optional) {
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (Object o : fileInfos) {
            java.util.Map<String, Object> f = Json.obj(o);
            long id = (long) Json.num(f, "id", 0);
            if (id <= 0) continue;
            String name = Json.str(f, "displayName", "");
            if (name.isEmpty()) name = Json.str(f, "fileName", "");
            if (name.isEmpty()) name = String.valueOf(id);
            names.put(id, name);
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : optional) if (names.containsKey(id)) ids.add(id);
        java.util.Set<Long> skipped = new java.util.HashSet<>(ids);
        if (ids.isEmpty()) return skipped;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean alive = new AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<java.util.Set<Long>> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        showOptional(win, latch, result, alive, ids, names, 1);
        try {
            if (!latch.await(10, java.util.concurrent.TimeUnit.MINUTES)) {
                alive.set(false);
                Platform.runLater(() -> {
                    win.setModalCloseHook(null);
                    if (win.hasOpenModal()) win.closeModal();
                });
            }
        } catch (InterruptedException e) {
            alive.set(false);
            Platform.runLater(() -> {
                win.setModalCloseHook(null);
                if (win.hasOpenModal()) win.closeModal();
            });
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    private static void showOptional(MainWindow win, CountDownLatch latch,
                                     java.util.concurrent.atomic.AtomicReference<java.util.Set<Long>> result,
                                     AtomicBoolean alive, List<Long> ids,
                                     java.util.Map<Long, String> names, int attempt) {
        Platform.runLater(() -> {
            if (!alive.get()) {
                latch.countDown();
                return;
            }
            if (win.hasOpenModal()) {
                if (attempt <= 0) {
                    latch.countDown();
                    return;
                }
                javafx.animation.PauseTransition retry =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
                retry.setOnFinished(e -> showOptional(win, latch, result, alive, ids, names, attempt - 1));
                retry.play();
                return;
            }
            VBox card = new VBox(12);
            card.setPadding(new Insets(20, 22, 16, 22));
            card.setPrefWidth(480);
            card.setMaxWidth(480);
            card.setBackground(Ui.fill(Theme.dialogBg, 12));
            card.setEffect(Theme.shadow(22, 4, 0.32, Theme.text));
            javafx.scene.control.Label title = Ui.bold("选择可选文件", 17, Theme.text);
            javafx.scene.control.Label body = Ui.label(
                    Lang.tf("共 %d 个可选文件，取消勾选的不安装。", ids.size()), 12.5, Theme.gray1);
            body.setWrapText(true);
            VBox rows = new VBox(7);
            java.util.Map<Long, javafx.scene.control.CheckBox> boxes = new java.util.LinkedHashMap<>();
            for (Long id : ids) {
                javafx.scene.control.CheckBox box = new javafx.scene.control.CheckBox(names.get(id));
                box.setSelected(true);
                box.setWrapText(true);
                boxes.put(id, box);
                rows.getChildren().add(box);
            }
            javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(rows);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(Math.min(260, 34 * ids.size() + 12));
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            HBox tools = new HBox(8);
            tools.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            tools.getChildren().addAll(
                    Ui.button("全选", Ui.BTN_NORMAL, () -> boxes.values().forEach(b -> b.setSelected(true))),
                    Ui.button("全不选", Ui.BTN_NORMAL, () -> boxes.values().forEach(b -> b.setSelected(false))));
            HBox actions = new HBox(10);
            actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            actions.getChildren().addAll(
                    Ui.button("取消", Ui.BTN_NORMAL, () -> {
                        win.setModalCloseHook(null);
                        win.closeModal();
                        latch.countDown();
                    }),
                    Ui.button("安装", Ui.BTN_THEME, () -> {
                        java.util.Set<Long> skip = new java.util.HashSet<>();
                        for (java.util.Map.Entry<Long, javafx.scene.control.CheckBox> e : boxes.entrySet()) {
                            if (!e.getValue().isSelected()) skip.add(e.getKey());
                        }
                        result.set(skip);
                        win.setModalCloseHook(null);
                        win.closeModal();
                        latch.countDown();
                    }));
            card.getChildren().addAll(title, body, scroll, tools, actions);
            win.showModal(card, false);
            win.setModalCloseHook(latch::countDown);
        });
    }

    private static String baseFolder(ZipFile zip) {
        String[] markers = {"modrinth.index.json", "manifest.json", "mcbbs.packmeta",
                "mmc-pack.json", "modpack.json", "instance.cfg"};
        for (String marker : markers) {
            if (entry(zip, marker) != null) return "";
        }
        for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            String name = entries.nextElement().getName().replace('\\', '/');
            String[] parts = name.split("/");
            if (parts.length == 2) {
                String folder = parts[0];
                if (folder.isEmpty() || folder.contains("..") || folder.contains(":")) continue;
                for (String marker : markers) {
                    if (parts[1].equals(marker)) return folder + "/";
                }
            }
        }
        return "";
    }

    private static ZipEntry entry(ZipFile zip, String name) {
        ZipEntry e = zip.getEntry(name);
        if (e == null) {
            for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry cand = entries.nextElement();
                if (cand.getName().replace('\\', '/').equalsIgnoreCase(name)) return cand;
            }
            return null;
        }
        return e;
    }

    private static java.util.Map<String, Object> readJson(ZipFile zip, ZipEntry entry) throws Exception {
        if (entry.getSize() > 10L * 1024 * 1024) {
            throw new java.io.IOException(Lang.tf("整合包清单文件过大：%s（%d 字节）",
                    entry.getName(), entry.getSize()));
        }
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length > 10 * 1024 * 1024) {
                // getSize 可能 -1 按实读长度
                throw new java.io.IOException(Lang.tf("整合包清单文件过大：%s（%d 字节）",
                        entry.getName(), bytes.length));
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            try {
                return Json.obj(Json.parse(text));
            } catch (Json.MalformedException e) {
                throw new java.io.IOException(Lang.tf("整合包清单 JSON 损坏：%s，%s", entry.getName(),
                        e.getMessage() == null ? Lang.t("格式不合法") : e.getMessage()), e);
            } catch (RuntimeException e) {
                throw new java.io.IOException(Lang.tf("整合包清单 JSON 无法解析：%s，%s", entry.getName(),
                        e.getMessage() == null ? Lang.t("格式不合法") : e.getMessage()), e);
            }
        }
    }

    static void extractContent(ZipFile zip, Path tempDir, Path versionFolder, String base,
                               PackInfo info, List<String> contentDirs) throws Exception {
        extractZip(zip, tempDir);
        Path tempBase = tempDir.toAbsolutePath().normalize();
        for (String dir : contentDirs) {
            Path overrideHome = tempBase.resolve(dir).normalize();
            if (!overrideHome.startsWith(tempBase)) {
                throw new Exception(Lang.t("整合包的覆盖目录超出了解压目录，请向整合包作者反馈此问题！"));
            }
            if (!Files.isDirectory(overrideHome)) continue;
            if (info.genericLayout) {
                // pack.json 是清单 不是实例内容
                copyDirectory(overrideHome, versionFolder, rel -> rel.equalsIgnoreCase("pack.json"));
            } else {
                copyDirectory(overrideHome, versionFolder);
            }
        }
    }

    private static void extractZip(ZipFile zip, Path targetDir) throws java.io.IOException {
        Path base = targetDir.toAbsolutePath().normalize();
        // 限制条目数与解压总量
        long totalBytes = 0;
        long written = 0;
        int entries = 0;
        for (java.util.Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements(); ) {
            ZipEntry e = it.nextElement();
            if (++entries > MAX_ZIP_ENTRIES) {
                throw new java.io.IOException(Lang.t("整合包条目过多，已中止解压"));
            }
            long size = e.getSize();
            if (size > 0) {
                totalBytes += size;
                if (totalBytes > MAX_ZIP_BYTES) {
                    throw new java.io.IOException(Lang.t("整合包解压后体积过大，已中止解压"));
                }
            }
            Path out = base.resolve(e.getName().replace('\\', '/')).normalize();
            if (!out.startsWith(base)) continue;
            if (e.isDirectory()) {
                Files.createDirectories(out);
            } else {
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(e); OutputStream outStream = Files.newOutputStream(out)) {
                    written += Io.copy(in, outStream, MAX_ZIP_BYTES - written);
                }
            }
        }
    }

    private static final int MAX_ZIP_ENTRIES = 200_000;
    private static final long MAX_ZIP_BYTES = 16L * 1024 * 1024 * 1024;

    private static void copyDirectory(Path from, Path to) throws java.io.IOException {
        copyDirectory(from, to, rel -> false);
    }

    private static void copyDirectory(Path from, Path to, java.util.function.Predicate<String> skip)
            throws java.io.IOException {
        if (!Files.isDirectory(from)) return;
        try (var stream = Files.walk(from)) {
            var it = stream.iterator();
            while (it.hasNext()) {
                Path src = it.next();
                if (Files.isSymbolicLink(src)) continue;
                String rel = from.relativize(src).toString().replace('\\', '/');
                if (!rel.isEmpty() && skip.test(rel)) continue;
                Path dest = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void unzipTo(Path zip, Path targetDir) throws java.io.IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            extractZip(zf, targetDir);
        }
    }

    private static void deleteRecursive(Path dir) {
        if (dir == null || !Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(dir)) {
            try {
                Files.deleteIfExists(dir);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    Files.deleteIfExists(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path folder, java.io.IOException exc)
                        throws java.io.IOException {
                    if (exc != null) throw exc;
                    Files.deleteIfExists(folder);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static final class DownloadJob {
        final List<String> urls;
        final Path target;
        final String sha1;

        DownloadJob(List<String> urls, Path target, String sha1) {
            this.urls = List.copyOf(urls);
            this.target = target;
            this.sha1 = sha1;
        }
    }

    private static void downloadAll(Tasks.Task task, List<DownloadJob> jobs,
                                    double from, double to) throws Exception {
        if (task.canceled) throw new java.io.IOException(Lang.t("已取消"));
        if (jobs.isEmpty()) {
            task.progress = to;
            task.remainingFiles = 0;
            return;
        }
        if (jobs.size() > 50_000) throw new java.io.IOException(Lang.t("整合包文件数量过多"));
        int threads = Math.max(1, Math.min(32, Math.min(jobs.size(),
                Data.get().getInt("SystemMaxThreads", 64))));
        task.threads = threads;
        task.remainingFiles = jobs.size();
        task.progress = from;
        task.stage = "下载整合包内容 0 / " + jobs.size();
        task.speed = "";
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                threads, r -> {
                    Thread t = new Thread(r, "TCL-Modpack");
                    t.setDaemon(true);
                    return t;
                });
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicLong bytesDone = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicReference<Exception> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        long started = System.nanoTime();
        try {
            for (DownloadJob job : jobs) {
                futures.add(pool.submit(() -> {
                    if (task.canceled || stopping.get()) return;
                    boolean ok = false;
                    try {
                        tryDownload(task, job.urls, job.target, job.sha1, false);
                        ok = true;
                    } catch (Exception e) {
                        if (!task.canceled) {
                            stopping.set(true);
                            failure.compareAndSet(null, e);
                        }
                    } finally {
                        if (ok) {
                            try {
                                bytesDone.addAndGet(Files.size(job.target));
                            } catch (Exception ignored) {
                            }
                        }
                        int n = done.incrementAndGet();
                        task.remainingFiles = Math.max(0, jobs.size() - n);
                        task.progress = from + (to - from) * n / jobs.size();
                        task.stage = "下载整合包内容 " + n + " / " + jobs.size();
                        long elapsed = Math.max(1, (System.nanoTime() - started) / 1_000_000);
                        task.speed = Downloader.speed(bytesDone.get() * 1000 / elapsed);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        if (task.canceled) throw new java.io.IOException(Lang.t("已取消"));
        Exception err = failure.get();
        if (err != null) throw err;
    }

    private static void tryDownload(Tasks.Task task, List<String> urls, Path target, String sha1) throws Exception {
        tryDownload(task, urls, target, sha1, true);
    }

    private static void tryDownload(Tasks.Task task, List<String> urls, Path target, String sha1,
                                    boolean report) throws Exception {
        Exception last = null;
        for (String url : Sources.rank(urls)) {
            try {
                Modrinth.downloadUrl(task, url, target, sha1, report);
                return;
            } catch (Exception e) {
                if (task.canceled) throw e;
                last = e;
            }
        }
        if (last != null) throw last;
        throw new java.io.IOException(Lang.t("没有可用的下载地址：") + target.getFileName());
    }

    private static void logSkipped(Object msg) {
        Data.get().log(String.valueOf(msg));
    }
}
