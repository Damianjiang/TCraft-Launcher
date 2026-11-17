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

/** 整合包安装：Modrinth、CurseForge、MCBBS。 */
public final class Modpacks {

    private Modpacks() {
    }

    /** 预读信息。 */
    public static class PackInfo {
        public boolean valid;
        /** CurseForge / Modrinth / MCBBS。 */
        public String typeName = "";
        public String name = "";
        public String mcVersion = "";
        /** 加载器名，跟 Data.Instance.loader 一致。 */
        public String loader = "";
        public String loaderVersion = "";
        /** 包里声明了但没法支持的加载器。 */
        public List<String> unsupportedLoaders = new ArrayList<>();
        public long sizeBytes;
    }

    /** 只读预读整合包信息，失败返回 valid=false。 */
    public static PackInfo readInfo(Path file) {
        PackInfo info = new PackInfo();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            info.sizeBytes = Files.size(file);
            String base = baseFolder(zip);
            if (base == null) return info;
            // 按清单文件判断类型
            ZipEntry mcbbs = entry(zip, base + "mcbbs.packmeta");
            ZipEntry mmc = entry(zip, base + "mmc-pack.json");
            ZipEntry index = entry(zip, base + "modrinth.index.json");
            ZipEntry manifest = entry(zip, base + "manifest.json");
            ZipEntry hmcl = entry(zip, base + "modpack.json");
            if (mcbbs != null) {
                info.typeName = "MCBBS";
                parseMcbbs(readJson(zip, mcbbs), info);
            } else if (mmc != null) {
                // MultiMC 的 components 加 instance.cfg 里的名字
                info.typeName = "MultiMC";
                parseMmc(readJson(zip, mmc), info);
                ZipEntry cfg = entry(zip, base + "instance.cfg");
                if (cfg != null) {
                    try (InputStream in = zip.getInputStream(cfg)) {
                        for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                            if (line.startsWith("name=")) {
                                info.name = line.substring(5).trim();
                                break;
                            }
                        }
                    }
                }
            } else if (hmcl != null) {
                info.typeName = "HMCL";
                parseHmcl(readJson(zip, hmcl), info);
                parseHmclPack(zip, base, info);
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
                return info; // 没有可识别的清单
            }
            if (info.name.isEmpty()) {
                info.name = file.getFileName().toString().replaceAll("\\.(zip|mrpack|rar)$", "");
            }
            normalizeLoaderVersion(info);
            info.valid = !info.mcVersion.isEmpty();
        } catch (Exception e) {
            Data.get().log("预读整合包失败：" + e.getMessage());
        }
        return info;
    }

    private static void parseCurseForge(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        java.util.Map<String, Object> minecraft = Json.obj(json.get("minecraft"));
        info.mcVersion = Json.str(minecraft, "version", info.mcVersion).trim();
        List<Object> loaders = Json.arr(minecraft.get("modLoaders"));
        // 只认 primary 的加载器
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
            switch (e.getKey().toLowerCase()) {
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

    /** 去掉加载器版本里可能带的 MC 前缀，如 1.20.1-47.2.0 → 47.2.0。 */
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

    /** 解析 CurseForge 风格的加载器 id。 */
    private static void parseLoaderId(String id, PackInfo info) {
        if (id == null || id.isBlank()) return;
        String text = id.trim();
        String lower = text.toLowerCase();
        if (lower.startsWith("forge-")) {
            // 老格式 forge-1.12.2-recommended 定不出具体版本
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

    /** 解析 MultiMC mmc-pack.json 里的 components。 */
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

    /** 解析 modpack.json，只有 gameVersion，没有加载器。 */
    private static void parseHmcl(java.util.Map<String, Object> json, PackInfo info) {
        if (json == null) return;
        info.name = Json.str(json, "name", info.name);
        info.mcVersion = Json.str(json, "gameVersion", "").trim();
    }

    /** HMCL 包没写版本/加载器时从 minecraft/pack.json 补。 */
    private static void parseHmclPack(ZipFile zip, String base, PackInfo info) {
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
            // 先看 libraries，HMCL 导出包会把加载器写进依赖
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
            // 再退回 inheritsFrom，例如 1.20.1-forge-47.2.0
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
            Data.get().log("读取 HMCL pack.json 失败：" + e.getMessage());
        }
    }

    /** 网络安装：先下整合包文件，再走安装流程。 */
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
                Tasks.INSTANCE.changed();
            }
        }, "TCL-Modpack-Network-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    /** 本地文件安装。 */
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
                Tasks.INSTANCE.changed();
            }
        }, "TCL-Modpack-Local-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    /** 版本名只允许单层目录名，且不能和已有实例重名。 */
    static String instanceNameError(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "名称不能为空。";
        if (value.length() > 64) return "名称不能超过 64 个字符。";
        if (value.equals(".") || value.equals("..")) return "名称不合法。";
        for (char c : "\\/:*?\"<>|".toCharArray()) {
            if (value.indexOf(c) >= 0) return "名称不能包含字符：" + c;
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
        // 清理半成品实例
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
        } catch (Exception ignored) {
        }
    }

    /** 从压缩包安装内容，之后触发 MC 和加载器安装。 */
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
            ZipEntry hmcl = entry(zip, base + "modpack.json");
            PackInfo info = new PackInfo();
            List<String> contentDirs = new ArrayList<>();
            boolean isMcbbs = false;
            java.util.Map<String, Object> mcbbsJson = null;
            if (mcbbs != null) {
                isMcbbs = true;
                mcbbsJson = readJson(zip, mcbbs);
                parseMcbbs(mcbbsJson, info);
                contentDirs.add(base + "overrides");
            } else if (hmcl != null) {
                info.typeName = "HMCL";
                parseHmcl(readJson(zip, hmcl), info);
                parseHmclPack(zip, base, info);
                contentDirs.add(base + "minecraft");
            } else if (mmc != null) {
                info.typeName = "MultiMC";
                parseMmc(readJson(zip, mmc), info);
                contentDirs.add(base + ".minecraft");
            } else if (index != null) {
                parseModrinth(readJson(zip, index), info);
                // Modrinth 客户端覆盖优先，和 HMCL 一样先 client-overrides 再 overrides
                contentDirs.add(base + "client-overrides");
                contentDirs.add(base + "overrides");
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
                throw new Exception("无法识别整合包类型（缺少 modrinth.index.json / manifest.json / mcbbs.packmeta）");
            }
            normalizeLoaderVersion(info);
            if (info.mcVersion.isEmpty()) {
                throw new Exception(info.typeName + " 整合包未提供 Minecraft 版本信息");
            }
            if (!info.unsupportedLoaders.isEmpty()) {
                throw new Exception("该整合包使用了 TCL 不支持的加载器：" + String.join("、", info.unsupportedLoaders));
            }

            // 解压并覆盖文件
            task.stage = "解压整合包文件";
            task.progress = 0.05;
            Tasks.INSTANCE.changed();
            extractContent(zip, tempDir, versionFolder, base, info, contentDirs);

            // 下载整合包内容
            if (index != null) {
                downloadModrinthContent(win, zip, index, versionFolder, task);
            } else if (info.typeName.equals("HMCL") || info.typeName.equals("MultiMC")) {
                // 这类包的内容全在覆盖目录里。
                task.progress = 0.6;
            } else if (isMcbbs) {
                downloadMcbbsContent(win, mcbbsJson, versionFolder, task);
            } else if (manifest != null) {
                downloadCurseForgeContent(win, readJson(zip, manifest), versionFolder, task);
            }

            // 解压 saves 里的世界 zip。
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

            // 设置图标
            String iconPath = "";
            java.util.List<String> iconCandidates = new ArrayList<>(java.util.List.of("icon.png", "pack.png", "logo.png", "icon.jpg", "logo.jpg"));
            // instance.cfg 的 iconKey 指向包里的图标
            if (info.typeName.equals("MultiMC") && tempDir.resolve(base + "instance.cfg").toFile().exists()) {
                for (String line : Files.readString(tempDir.resolve(base + "instance.cfg"), StandardCharsets.UTF_8).split("\n")) {
                    if (line.startsWith("iconKey=") && !line.substring(8).isBlank()) {
                        iconCandidates.add(0, line.substring(8).trim() + ".png");
                        break;
                    }
                }
            }
            for (String candidate : iconCandidates) {
                Path p = versionFolder.resolve(candidate);
                if (Files.isRegularFile(p)) {
                    Path logoDir = versionFolder.resolve("TCL");
                    Files.createDirectories(logoDir);
                    Path logo = logoDir.resolve("Logo.png");
                    Files.copy(p, logo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    iconPath = logo.toAbsolutePath().toString();
                    break;
                }
                Path fromTemp = tempDir.resolve(candidate);
                if (Files.isRegularFile(fromTemp)) {
                    Path logoDir = versionFolder.resolve("TCL");
                    Files.createDirectories(logoDir);
                    Path logo = logoDir.resolve("Logo.png");
                    Files.copy(fromTemp, logo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    iconPath = logo.toAbsolutePath().toString();
                    break;
                }
            }

            // 登记实例：内容在 versions/<实例名>，用自己的版本 json 继承加载器版本
            String desc = info.typeName + " 整合包 · MC " + info.mcVersion
                    + (info.loader.isEmpty() ? "" : "，" + info.loader + " " + info.loaderVersion);
            Data.Instance instance = new Data.Instance(instanceName, instanceName,
                    info.loader.isEmpty() ? "原版" : info.loader, info.loaderVersion, desc,
                    iconPath, System.currentTimeMillis(), false, data.gameDir, Isolation.ON);
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

            // 把 MC 和加载器装进游戏目录，装完写一份继承加载器版本的实例 json
            String loader = info.loader.isEmpty() ? "原版" : info.loader;
            Downloader.startInstall(win, new Data.McVersion(info.mcVersion, "release", ""), loader,
                    Paths.get(data.gameDir), info.loaderVersion, null, actual -> {
                        if (actual == null || actual.isEmpty()) return;
                        try {
                            Path dir = Paths.get(data.gameDir, "versions", instanceName);
                            Files.createDirectories(dir);
                            String json = "{\"id\":" + Json.quote(instanceName)
                                    + ",\"inheritsFrom\":" + Json.quote(actual)
                                    + ",\"type\":\"release\"}";
                            Files.writeString(dir.resolve(instanceName + ".json"), json, StandardCharsets.UTF_8);
                            data.save();
                            win.refreshRight();
                            data.log("整合包版本已就绪：" + instanceName + " → " + actual);
                        } catch (Exception e) {
                            data.log("写整合包版本 json 失败：" + e.getMessage());
                        }
                    });
            task.progress = 1;
            task.state = Tasks.State.DONE;
            task.stage = "整合包内容就绪";
            data.log("整合包内容安装完成：" + instanceName + "，已开始安装游戏版本 " + info.mcVersion);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private static void downloadModrinthContent(MainWindow win, ZipFile zip, ZipEntry indexEntry,
                                                Path versionFolder, Tasks.Task task) throws Exception {
        java.util.Map<String, Object> json = readJson(zip, indexEntry);
        List<Object> files = Json.arr(json.get("files"));
        int total = Math.max(1, files.size());
        int done = 0;
        for (Object o : files) {
            if (task.canceled) throw new java.io.IOException("已取消");
            java.util.Map<String, Object> f = Json.obj(o);
            // 跳过服务端专用文件
            java.util.Map<String, Object> env = Json.obj(f.get("env"));
            if (!env.isEmpty() && "unsupported".equalsIgnoreCase(Json.str(env, "client", ""))) continue;
            String relPath = Json.str(f, "path", "");
            if (relPath.isEmpty()) continue;
            java.util.Map<String, Object> hashes = Json.obj(f.get("hashes"));
            String sha1 = Json.str(hashes, "sha1", "");
            Path target = versionFolder.resolve(relPath).normalize();
            if (!target.startsWith(versionFolder)) continue; // 防路径穿越
            List<String> urls = new ArrayList<>();
            for (Object u : Json.arr(f.get("downloads"))) urls.add(String.valueOf(u));
            if (!urls.isEmpty() && !Files.isRegularFile(target)) {
                Files.createDirectories(target.getParent());
                tryDownload(task, urls, target, sha1);
            }
            done++;
            task.progress = 0.15 + 0.6 * done / (double) total;
            task.stage = "下载整合包内容 " + done + " / " + total;
            task.remainingFiles = total - done;
            Tasks.INSTANCE.changed();
        }
    }

    private static void downloadCurseForgeContent(MainWindow win, java.util.Map<String, Object> manifest, Path versionFolder,
                                                  Tasks.Task task) throws Exception {
        List<Long> required = new ArrayList<>();
        List<Long> optional = new ArrayList<>();
        for (Object o : Json.arr(manifest.get("files"))) {
            java.util.Map<String, Object> f = Json.obj(o);
            long projectId = (long) Json.num(f, "projectID", 0);
            long fileId = (long) Json.num(f, "fileID", 0);
            if (fileId <= 0) continue;
            boolean req = !"false".equalsIgnoreCase(Json.str(f, "required", "true"));
            (req ? required : optional).add(fileId);
        }
        List<Long> all = new ArrayList<>(required);
        all.addAll(optional);
        if (all.isEmpty()) return;
        List<Object> fileInfos = CurseForge.filesByIds(all);
        // 文件缺失就提示
        if (fileInfos.size() < all.size()) {
            java.util.Set<String> found = new java.util.HashSet<>();
            for (Object o : fileInfos) {
                found.add(String.valueOf((long) Json.num(Json.obj(o), "id", -1)));
            }
            List<Long> missing = new ArrayList<>();
            for (Long id : all) {
                if (!found.contains(String.valueOf(id))) missing.add(id);
            }
            if (!missing.isEmpty()) {
                data_log("整合包缺失以下文件（可能已被原作者删除）：" + missing);
                boolean go = askInBackground(win, "整合包内容缺失",
                        Lang.tf("未找到该整合包所需的 %d 个内容，它们可能已被原作者删除。\n"
                                + "缺失这些内容可能导致整合包出现异常！是否继续安装？", missing.size()));
                if (!go) throw new java.io.IOException("已取消");
            }
        }
        // 可选文件合并成一次询问
        java.util.Set<Long> skipOptional = new java.util.HashSet<>();
        if (!optional.isEmpty()) {
            boolean dl = askInBackground(win, "下载可选文件",
                    Lang.tf("该整合包包含 %d 个标记为可选的文件，是否一并下载？", optional.size()));
            if (!dl) skipOptional.addAll(optional);
        }
        List<Object> installFiles = new ArrayList<>();
        for (Object o : fileInfos) {
            long fileId = (long) Json.num(Json.obj(o), "id", 0);
            if (!skipOptional.contains(fileId)) installFiles.add(o);
        }
        int total = Math.max(1, installFiles.size());
        int done = 0;
        java.util.Map<Long, Integer> classCache = new java.util.HashMap<>();
        for (Object o : installFiles) {
            if (task.canceled) throw new java.io.IOException("已取消");
            java.util.Map<String, Object> f = Json.obj(o);
            ResourceVersion rv = ResourceVersion.fromCurseForgeJson(f, "mod");
            if (rv.downloadUrls.isEmpty() || rv.fileName.isEmpty()) continue;
            // 按模块推断保存目录
            List<String> modules = new ArrayList<>();
            for (Object m : Json.arr(f.get("modules"))) {
                modules.add(Json.str(Json.obj(m), "name", ""));
            }
            String sub = inferModuleTarget(modules, rv.fileName);
            // jar 默认就是 mod；资源包/光影/世界再查项目分类，避免放进 mods
            String lower = rv.fileName.toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".litemod")) {
                long modId = (long) Json.num(f, "modId", 0);
                if (modId > 0) {
                    int classId = classCache.computeIfAbsent(modId, id -> {
                        try {
                            return CurseForge.projectInfo(id).classId;
                        } catch (Exception e) {
                            return 0;
                        }
                    });
                    sub = targetForClass(classId, sub);
                }
            }
            Path target = versionFolder.resolve(sub).resolve(rv.fileName).normalize();
            if (!target.startsWith(versionFolder)) continue; // 防止文件名带 ../ 写穿目录
            if (!Files.isRegularFile(target)) {
                Files.createDirectories(target.getParent());
                tryDownload(task, rv.downloadUrls, target, rv.hash);
            }
            done++;
            task.progress = 0.15 + 0.6 * done / (double) total;
            task.stage = "下载整合包内容 " + done + " / " + total;
            task.remainingFiles = total - done;
            Tasks.INSTANCE.changed();
        }
    }

    private static void downloadMcbbsContent(MainWindow win, java.util.Map<String, Object> json, Path versionFolder, Tasks.Task task) throws Exception {
        List<Object> files = Json.arr(json.get("files"));
        int total = Math.max(1, files.size());
        int done = 0;
        for (Object o : files) {
            if (task.canceled) throw new java.io.IOException("已取消");
            java.util.Map<String, Object> f = Json.obj(o);
            java.util.Map<String, Object> source = Json.obj(f.get("source"));
            String url = Json.str(source, "url", "");
            String relPath = Json.str(f, "target", "");
            if (url.isEmpty() || relPath.isEmpty()) continue;
            var hashes = Json.obj(source.get("hashes"));
            Path target = versionFolder.resolve(relPath).normalize();
            if (!target.startsWith(versionFolder)) continue;
            if (!Files.isRegularFile(target)) {
                Files.createDirectories(target.getParent());
                tryDownload(task, List.of(url), target, Json.str(hashes, "sha1", ""));
            }
            done++;
            task.progress = 0.15 + 0.6 * done / (double) total;
            task.stage = "下载整合包内容 " + done + " / " + total;
            Tasks.INSTANCE.changed();
        }
    }

    /** 按 CurseForge 文件的 modules 推断保存目录。 */
    static String inferModuleTarget(List<String> modules, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        // jar 先按 mod 处理，避免 jar 里的 assets/data 目录被误判成资源包/数据包
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

    /** CurseForge 项目分类对应的保存目录，未知分类沿用文件名推断。 */
    private static String targetForClass(int classId, String fallback) {
        return switch (classId) {
            case CurseForge.CLASS_RESOURCEPACK -> "resourcepacks";
            case CurseForge.CLASS_SHADER -> "shaderpacks";
            case CurseForge.CLASS_DATAPACK -> "datapacks";
            case CurseForge.CLASS_WORLD -> "saves";
            default -> fallback;
        };
    }

    /** 后台线程等用户点「继续 / 取消」。 */
    private static boolean askInBackground(MainWindow win, String title, String message) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        Platform.runLater(() -> {
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
            // 关窗时放行后台线程
            win.setModalCloseHook(latch::countDown);
            win.showModal(card, false);
        });
        try {
            // 十分钟超时按取消处理
            if (!latch.await(10, java.util.concurrent.TimeUnit.MINUTES)) {
                Platform.runLater(() -> win.setModalCloseHook(null));
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return ok.get();
    }

    /** 判断包内根目录。 */
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
                for (String marker : markers) {
                    if (parts[1].equals(marker)) return parts[0] + "/";
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
        try (InputStream in = zip.getInputStream(entry)) {
            return Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /** 解压包，并把各格式的覆盖目录复制到实例目录。 */
    static void extractContent(ZipFile zip, Path tempDir, Path versionFolder, String base,
                               PackInfo info, List<String> contentDirs) throws Exception {
        extractZip(zip, tempDir);
        for (String dir : contentDirs) {
            Path overrideHome = tempDir.resolve(dir).normalize();
            if (!overrideHome.startsWith(tempDir)) {
                throw new Exception("整合包的覆盖目录超出了解压目录，请向整合包作者反馈此问题！");
            }
            if (!Files.isDirectory(overrideHome)) continue;
            if ("HMCL".equals(info.typeName)) {
                // pack.json 是版本清单，不是实例内容
                copyDirectory(overrideHome, versionFolder, rel -> rel.equalsIgnoreCase("pack.json"));
            } else {
                copyDirectory(overrideHome, versionFolder);
            }
        }
    }

    private static void extractZip(ZipFile zip, Path targetDir) throws java.io.IOException {
        for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry e = entries.nextElement();
            Path out = targetDir.resolve(e.getName().replace('\\', '/')).normalize();
            if (!out.startsWith(targetDir)) continue;
            if (e.isDirectory()) {
                Files.createDirectories(out);
            } else {
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(e); OutputStream outStream = Files.newOutputStream(out)) {
                    in.transferTo(outStream);
                }
            }
        }
    }

    private static void copyDirectory(Path from, Path to) throws java.io.IOException {
        copyDirectory(from, to, rel -> false);
    }

    private static void copyDirectory(Path from, Path to, java.util.function.Predicate<String> skip)
            throws java.io.IOException {
        if (!Files.isDirectory(from)) return;
        try (var stream = Files.walk(from)) {
            for (Path src : stream.toList()) {
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
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void tryDownload(Tasks.Task task, List<String> urls, Path target, String sha1) throws Exception {
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
        throw new java.io.IOException("没有可用的下载地址：" + target.getFileName());
    }

    private static void data_log(Object msg) {
        Data.get().log(String.valueOf(msg));
    }

}
