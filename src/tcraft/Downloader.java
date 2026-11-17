package tcraft;

import java.net.URI;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft 原版 / Fabric 下载器，带任务进度。 */
public final class Downloader {

    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    /** 默认 BMCLAPI 镜像根。 */
    private static final String BMCL_DEFAULT = "https://bmclapi2.bangbang93.com";

    /** 当前镜像根，留空用内置 BMCLAPI。 */
    static String bmcl() {
        String custom = Data.get().get("SystemBmclRoot", "").trim();
        if (custom.isEmpty()) return BMCL_DEFAULT;
        if (!custom.startsWith("http://") && !custom.startsWith("https://")) custom = "https://" + custom;
        while (custom.endsWith("/")) custom = custom.substring(0, custom.length() - 1);
        return custom;
    }

    private Downloader() {
    }

    public static void startInstall(MainWindow win, Data.McVersion version, String loader) {
        startInstall(win, version, loader, (java.nio.file.Path) null, null, null, null);
    }

    /** loaderVersion 为 null 时用官方推荐版本。 */
    public static void startInstall(MainWindow win, Data.McVersion version, String loader, String loaderVersion) {
        startInstall(win, version, loader, (java.nio.file.Path) null, loaderVersion, null, null);
    }

    /** 安装到指定目录，整合包导入用。 */
    public static void startInstall(MainWindow win, Data.McVersion version, String loader,
                                    java.nio.file.Path gameDirOverride) {
        startInstall(win, version, loader, gameDirOverride, (String) null, (Inst) null, null);
    }

    /** 完整安装入口，inst 为 null 表示不用组件选择。 */
    public static void startInstall(MainWindow win, Data.McVersion version, String loader,
                                    java.nio.file.Path gameDirOverride, String loaderVersion, Inst inst) {
        startInstall(win, version, loader, gameDirOverride, loaderVersion, inst, null);
    }

    /** onInstalled 在装完后回调真实版本号，整合包导入靠它登记实例。 */
    public static void startInstall(MainWindow win, Data.McVersion version, String loader,
                                    java.nio.file.Path gameDirOverride, String loaderVersion, Inst inst,
                                    java.util.function.Consumer<String> onInstalled) {
        Tasks.Task task = new Tasks.Task("安装 " + version.id + ("原版".equals(loader) ? "" : " · " + loader)
                + (inst != null && !inst.name.isEmpty() && !inst.name.equals(version.id) ? "（" + inst.name + "）" : ""));
        task.versionId = version.id;
        task.stage = "准备下载";
        task.subs.add(new Tasks.SubTask("获取版本信息"));
        task.subs.add(new Tasks.SubTask("下载客户端"));
        task.subs.add(new Tasks.SubTask("下载依赖库"));
        task.subs.add(new Tasks.SubTask("下载资源索引"));
        task.subs.add(new Tasks.SubTask("下载资源文件"));
        if (!"原版".equals(loader)) task.subs.add(new Tasks.SubTask("安装 " + loader));
        Tasks.INSTANCE.add(task);
        win.toast("开始下载", version.id + " · " + loader, 0);
        Data data = Data.get();
        data.log("开始安装 " + version.id + "（" + loader + "）");

        Thread thread = new Thread(() -> {
            try {
                String actual = install(task, version, loader, data, win, gameDirOverride, loaderVersion, inst);
                task.progress = 1;
                task.state = Tasks.State.DONE;
                task.stage = "安装完成";
                task.speed = "";
                javafx.application.Platform.runLater(() ->
                        win.toast("安装完成", version.id + ("原版".equals(loader) ? "" : " · " + loader), 1));
                data.log("安装完成：" + version.id + "（" + loader + "）");
                if (onInstalled != null) {
                    final String realVersion = actual;
                    javafx.application.Platform.runLater(() -> onInstalled.accept(realVersion));
                }
                // 回 FX 线程刷新当前页。
                javafx.application.Platform.runLater(() -> {
                    String key = win.currentKey;
                    if ("download".equals(key) || "instance".equals(key)) win.openPage(key, false);
                });
            } catch (Exception e) {
                if (task.canceled) {
                    task.state = Tasks.State.CANCELED;
                    task.stage = "已取消";
                } else {
                    task.state = Tasks.State.FAILED;
                    task.error = e.getMessage();
                    task.stage = "安装失败";
                    data.log("安装失败：" + e.getMessage());
                    Errors.error("安装 " + version.id + " 失败", e);
                }
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-Download-" + version.id);
        thread.setDaemon(true);
        thread.start();
    }

    /** 按「开始下载」选好的组件启动安装。 */
    public static void startInstall(MainWindow win, Inst inst) {
        Data data = Data.get();
        Data.McVersion version = null;
        for (Data.McVersion v : data.versions) {
            if (inst.vanillaId.equals(v.id)) { version = v; break; }
        }
        if (version == null) {
            version = new Data.McVersion(inst.vanillaId, "release", "");
        }
        String loader = inst.primaryLoader();
        String loaderVersion = !inst.fabric.isEmpty() ? inst.fabric
                : !inst.forge.isEmpty() ? inst.forge
                : !inst.neoforge.isEmpty() ? inst.neoforge
                : !inst.liteLoader.isEmpty() ? inst.liteLoader
                : !inst.legacyFabric.isEmpty() ? inst.legacyFabric : "";
        // 装进当前游戏目录，隔离在启动时按设置决定（共用 libraries / assets）。
        startInstall(win, version, loader, (java.nio.file.Path) null, loaderVersion, inst);
    }

    /** 整合包导入用的简化入口。 */
    public static String install(Tasks.Task task, Data.McVersion version, String loader, Data data, MainWindow win,
                                java.nio.file.Path gameDirOverride) throws Exception {
        return install(task, version, loader, data, win, gameDirOverride, null, null);
    }

    public static String install(Tasks.Task task, Data.McVersion version, String loader, Data data, MainWindow win,
                                java.nio.file.Path gameDirOverride, String loaderVersion) throws Exception {
        return install(task, version, loader, data, win, gameDirOverride, loaderVersion, null);
    }

    /** 完整安装，含附加载器、OptiFine、LiteLoader 这些组件；返回实际版本号。 */
    public static String install(Tasks.Task task, Data.McVersion version, String loader, Data data, MainWindow win,
                                java.nio.file.Path gameDirOverride, String loaderVersion, Inst inst) throws Exception {
        Path game = gameDirOverride != null ? gameDirOverride : Paths.get(data.gameDir);
        Files.createDirectories(game.resolve("versions"));
        Files.createDirectories(game.resolve("libraries"));
        Files.createDirectories(game.resolve("assets").resolve("indexes"));
        Files.createDirectories(game.resolve("assets").resolve("objects"));

        int source = data.getInt("SystemDownloadSource", 0);
        String manifestBmcl = bmcl() + "/mc/game/version_manifest_v2.json";
        String manifestText = source == 1 ? getText(MANIFEST)
                : source == 2 ? getText(manifestBmcl, MANIFEST)
                : getText(MANIFEST, manifestBmcl);
        Map<String, Object> manifest = Json.obj(Json.parse(manifestText));
        Map<String, Object> entry = null;
        for (Object o : Json.arr(manifest.get("versions"))) {
            if (version.id.equals(Json.str(o, "id", ""))) {
                entry = Json.obj(o);
                break;
            }
        }
        if (entry == null) throw new IOException("版本列表中找不到 " + version.id);
        sub(task, 0, Tasks.State.RUNNING);
        task.stage = "获取版本信息";
        task.progress = 0.03;
        Tasks.INSTANCE.changed();

        Path versionDir = game.resolve("versions").resolve(version.id);
        Files.createDirectories(versionDir);
        Path versionJson = versionDir.resolve(version.id + ".json");
        String versionUrl = Json.str(entry, "url", "");
        if (source == 1) downloadTo(task, versionUrl, "", versionJson, 0.03, 0.08);
        else if (source == 2) downloadTo(task, bmcl() + "/version/" + version.id + "/json", versionUrl, versionJson, 0.03, 0.08);
        else downloadTo(task, versionUrl, bmcl() + "/version/" + version.id + "/json", versionJson, 0.03, 0.08);
        sub(task, 0, Tasks.State.DONE);
        task.progress = 0.08;

        Map<String, Object> versionData = Json.obj(Json.parse(Files.readString(versionJson, StandardCharsets.UTF_8)));
        Map<String, Object> downloads = Json.obj(versionData.get("downloads"));
        Map<String, Object> client = Json.obj(downloads.get("client"));
        if (!client.isEmpty()) {
            String clientUrl = Json.str(client, "url", "");
            Path jar = versionDir.resolve(version.id + ".jar");
            sub(task, 1, Tasks.State.RUNNING);
            task.stage = "下载客户端";
            downloadFile(task, clientUrl, jar, 0.08, 0.28, "客户端 " + version.id + ".jar");
            sub(task, 1, Tasks.State.DONE);
        } else {
            sub(task, 1, Tasks.State.DONE);
        }

        // 按平台规则过滤依赖库。
        sub(task, 2, Tasks.State.RUNNING);
        task.stage = "下载依赖库";
        List<Object> libraries = Json.arr(versionData.get("libraries"));
        List<String> allLibNames = new ArrayList<>();
        for (Object libObj : libraries) allLibNames.add(Json.str(Json.obj(libObj), "name", ""));
        List<Object[]> libFiles = new ArrayList<>();
        for (Object libObj : libraries) {
            Map<String, Object> lib = Json.obj(libObj);
            if (!GameLauncher.rulesAllow(lib)) continue;
            String libName = Json.str(lib, "name", "");
            if (libName.contains(":natives-") && !GameLauncher.nativeArtifactMatches(libName, allLibNames)) continue;
            Map<String, Object> downloadsLib = Json.obj(lib.get("downloads"));
            Map<String, Object> art = Json.obj(downloadsLib.get("artifact"));
            String rel = Json.str(art, "path", "");
            String url = Json.str(art, "url", "");
            if (!rel.isEmpty() && !url.isEmpty()) {
                libFiles.add(new Object[]{url, game.resolve("libraries").resolve(rel)});
            }
            // 旧版 natives 格式，只下当前平台的。
            Map<String, Object> nativesMap = Json.obj(lib.get("natives"));
            if (!nativesMap.isEmpty()) {
                String classifier = Json.str(nativesMap, nativeOsName(), "")
                        .replace("${arch}", is64Bit() ? "64" : "32");
                Map<String, Object> nat = Json.obj(Json.obj(downloadsLib.get("classifiers")).get(classifier));
                String nrel = Json.str(nat, "path", "");
                String nurl = Json.str(nat, "url", "");
                if (!nrel.isEmpty() && !nurl.isEmpty()) {
                    libFiles.add(new Object[]{nurl, game.resolve("libraries").resolve(nrel)});
                }
            }
        }
        int total = Math.max(1, libFiles.size());
        int done = 0;
        for (Object[] f : libFiles) {
            check(task);
            downloadTo(task, (String) f[0], mirrorLib((String) f[0]), (Path) f[1], 0.28, 0.50);
            done++;
            task.progress = 0.28 + 0.22 * done / total;
            task.remainingFiles = total - done;
            task.stage = "下载依赖库 " + done + " / " + total;
            if (done % 3 == 0) Tasks.INSTANCE.changed();
        }
        sub(task, 2, Tasks.State.DONE);

        // 资源索引
        sub(task, 3, Tasks.State.RUNNING);
        task.stage = "下载资源索引";
        Map<String, Object> assetIndex = Json.obj(versionData.get("assetIndex"));
        String assetId = Json.str(assetIndex, "id", "legacy");
        String indexUrl = Json.str(assetIndex, "url", "");
        Path indexPath = game.resolve("assets").resolve("indexes").resolve(assetId + ".json");
        downloadTo(task, indexUrl, bmcl() + "/indexes/" + assetId + ".json", indexPath, 0.50, 0.55);
        sub(task, 3, Tasks.State.DONE);

        // 资源对象
        sub(task, 4, Tasks.State.RUNNING);
        task.stage = "读取资源索引";
        Object index = Json.parse(Files.readString(indexPath, StandardCharsets.UTF_8));
        Map<String, Object> objects = Json.obj(index, "objects");
        final int totalAssets = Math.max(1, objects.size());
        final AtomicInteger assetDone = new AtomicInteger();
        final java.util.concurrent.ConcurrentLinkedQueue<String[]> retryQueue =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        int threads = Math.max(1, Math.min(32, Data.get().getInt("SystemMaxThreads", 64)));
        task.threads = threads;
        task.remainingFiles = totalAssets;
        Tasks.INSTANCE.changed();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "TCL-Asset");
            t.setDaemon(true);
            return t;
        });
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Object value : objects.values()) {
                Map<String, Object> obj = Json.obj(value);
                String hash = Json.str(obj, "hash", "");
                if (hash.length() < 2) continue;
                long expectedSize = (long) parseNum(Json.str(obj, "size", "0"), 0);
                Path target = game.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                futures.add(pool.submit(() -> {
                    if (task.canceled) return;
                    try {
                        if (!assetReady(target, expectedSize)) {
                            Files.deleteIfExists(target);
                            String assetUrl = Data.get().getInt("SystemDownloadSource", 0) == 1
                                    ? "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash
                                    : bmcl() + "/assets/" + hash.substring(0, 2) + "/" + hash;
                            try {
                                downloadFile(null, assetUrl, target, 0, 0, hash);
                            } catch (Exception first) {
                                // 并发大时 CDN 偶尔拒连，退避后重试一次。
                                Thread.sleep(600);
                                Files.deleteIfExists(target);
                                downloadFile(null, assetUrl, target, 0, 0, hash);
                            }
                            if (!assetReady(target, expectedSize)) throw new IOException("文件不完整");
                        }
                    } catch (Exception e) {
                        retryQueue.add(new String[]{hash, String.valueOf(expectedSize)});
                    }
                    int n = assetDone.incrementAndGet();
                    task.remainingFiles = Math.max(0, totalAssets - n);
                    task.progress = 0.55 + 0.40 * n / (double) totalAssets;
                    task.stage = "下载资源文件 " + n + " / " + totalAssets;
                    if (n % 40 == 0) {
                        Tasks.INSTANCE.changed();
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        } finally {
            pool.shutdownNow();
        }
        check(task);
        // 失败文件补抓一轮，避免卡在最后几个文件。
        if (!retryQueue.isEmpty()) {
            task.stage = "补抓失败文件 0 / " + retryQueue.size();
            int retried = 0;
            int ok = 0;
            for (String[] h : retryQueue) {
                check(task);
                retried++;
                Path target = game.resolve("assets").resolve("objects")
                        .resolve(h[0].substring(0, 2)).resolve(h[0]);
                long expectedSize = (long) parseNum(h[1], 0);
                for (String url : new String[]{
                        bmcl() + "/assets/" + h[0].substring(0, 2) + "/" + h[0],
                        "https://resources.download.minecraft.net/" + h[0].substring(0, 2) + "/" + h[0]}) {
                    try {
                        Files.deleteIfExists(target);
                        downloadFile(null, url, target, 0, 0, h[0]);
                        if (assetReady(target, expectedSize)) {
                            ok++;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                task.stage = "补抓失败文件 " + retried + " / " + retryQueue.size();
            }
            if (ok < retryQueue.size()) {
                throw new IOException("有 " + (retryQueue.size() - ok) + " 个资源文件下载失败，请检查网络后重试");
            }
        }
        sub(task, 4, Tasks.State.DONE);
        task.progress = 0.95;

        String actualVersion = version.id;
        if ("Fabric".equals(loader)) {
            task.stage = "安装 Fabric";
            actualVersion = installFabric(task, version.id, game, loaderVersion, false);
            sub(task, 5, Tasks.State.DONE);
        } else if ("LegacyFabric".equals(loader)) {
            task.stage = "安装 LegacyFabric";
            actualVersion = installFabric(task, version.id, game, loaderVersion, true);
            sub(task, 5, Tasks.State.DONE);
        } else if ("LiteLoader".equals(loader)) {
            task.stage = "安装 LiteLoader";
            actualVersion = installLiteLoader(task, version.id, game, data);
            sub(task, 5, Tasks.State.DONE);
        } else if ("Forge".equals(loader) || "NeoForge".equals(loader)) {
            task.stage = "安装 " + loader;
            actualVersion = installForgeLike(task, version.id, loader, game, data, loaderVersion);
            sub(task, 5, Tasks.State.DONE);
        } else if ("OptiFine".equals(loader)) {
            task.stage = "安装 OptiFine";
            actualVersion = installOptiFine(task, version.id, game, inst, win);
            sub(task, 5, Tasks.State.DONE);
        } else if (!"原版".equals(loader)) {
            sub(task, 5, Tasks.State.DONE);
        }

        // 附加载器下到 mods 或缓存目录；隔离实例放进 versions/<版本>，跟游戏实际目录一致
        if (inst != null) {
            Data.Instance probe = new Data.Instance("", actualVersion, loader, "", "", "", 0, false,
                    game.toString(), Isolation.FOLLOW);
            Path contentDir = Isolation.on(probe) ? game.resolve("versions").resolve(actualVersion) : game;
            try {
                Files.createDirectories(contentDir.resolve("mods"));
            } catch (Exception ignored) {
            }
            installExtraComponents(task, version.id, contentDir, inst, "OptiFine".equals(loader), win);
        }

        // 整合包导入交给调用方登记实例。
        if (gameDirOverride != null && inst == null) return actualVersion;
        String instanceName = (inst != null && inst.name != null && !inst.name.isBlank())
                ? inst.name.trim() : actualVersion;
        Data.Instance instance = new Data.Instance(
                instanceName, actualVersion, loader, loaderVersion == null ? "" : loaderVersion,
                version.group() + " " + version.id + ("原版".equals(loader) ? "" : "，已安装 " + loader),
                instanceIcon(loader, version),
                System.currentTimeMillis(), false, game.toString(), Isolation.FOLLOW);
        int selected = -1;
        for (int i = 0; i < data.instances.size(); i++) {
            if (data.instances.get(i).name.equals(instance.name)) { selected = i; break; }
        }
        if (selected < 0) {
            data.instances.add(instance);
            selected = data.instances.size() - 1;
        }
        data.selectedInstance = selected;
        data.save();
        return actualVersion;
    }

    /** 实例图标跟着主加载器。 */
    private static String instanceIcon(String loader, Data.McVersion version) {
        return switch (loader) {
            case "Fabric", "LegacyFabric" -> "Fabric.png";
            case "Forge" -> "Anvil.png";
            case "NeoForge" -> "NeoForge.png";
            case "OptiFine" -> "GrassPath.png";
            case "LiteLoader" -> "Egg.png";
            default -> version.icon();
        };
    }

    /** 安装选中的附加载器组件，失败不阻断主安装。 */
    private static void installExtraComponents(Tasks.Task task, String mc, Path game, Inst inst,
                                                boolean optiFineIsPrimary, MainWindow win) {
        if (!inst.fabricApi.isEmpty() || !inst.optiFabric.isEmpty()) {
            try {
                Files.createDirectories(game.resolve("mods"));
            } catch (Exception e) {
                Data.get().log("无法创建 mods 文件夹：" + e.getMessage());
            }
        }
        if (!inst.fabricApi.isEmpty()) {
            task.stage = "安装 Fabric API";
            installMod(task, Loaders.FABRICAPI, mc, inst.fabricApi, game.resolve("mods"), win);
        }
        if (!inst.optiFabric.isEmpty()) {
            task.stage = "安装 OptiFabric";
            installMod(task, Loaders.OPTIFABRIC, mc, inst.optiFabric, game.resolve("mods"), win);
        }
        // OptiFine 并存时也要把 jar 取到本地。
        if (!optiFineIsPrimary && !inst.optiFine.isEmpty()) {
            task.stage = "安装 OptiFine";
            installOptiFineExtra(task, mc, game, inst.optiFine, win);
        }
    }

    /** 把附加 Mod 下到 mods，失败只提示。 */
    private static void installMod(Tasks.Task task, String loader, String mc, String version, Path mods, MainWindow win) {
        try {
            String[] f = Loaders.modFile(loader, mc, version);
            if (f == null) throw new IOException("找不到 " + loader + " " + version + " 的下载地址");
            downloadFile(task, f[0], mods.resolve(f[1]), 0.95, 0.99, loader + " " + version);
            Data.get().log(loader + " " + version + " 已安装到 mods/" + f[1]);
        } catch (Exception e) {
            String msg = loader + " " + version + " 未安装：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
    }

    /** OptiFine 并存时把 jar 下到 TCL/cache/optifine。 */
    private static void installOptiFineExtra(Tasks.Task task, String mc, Path game, String chosen, MainWindow win) {
        try {
            downloadOptiFineJar(task, mc, game, chosen);
        } catch (Exception e) {
            String msg = "OptiFine " + chosen + " 未安装：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
    }

    /** 单文件下载，progress 为 0~1，null 表示不关心。 */
    public static void downloadFileTo(java.util.function.DoubleConsumer progress, String url, Path target,
                                      String label) throws IOException {
        Tasks.Task task = progress == null ? null : new Tasks.Task("t");
        if (task != null) {
            Thread poll = new Thread(() -> {
                while (task.state == Tasks.State.RUNNING) {
                    progress.accept(Math.min(1, Math.max(0, task.progress)));
                    try {
                        Thread.sleep(150);
                    } catch (Exception ignored) {
                    }
                }
            }, "TCL-ProgressPoll");
            poll.setDaemon(true);
            poll.start();
        }
        try {
            downloadFile(task, url, target, 0, 1, label);
        } finally {
            if (task != null) task.state = Tasks.State.DONE;
        }
    }

    /** Mojang 旧版 natives 的映射键：windows / osx / linux。 */
    private static String nativeOsName() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static boolean is64Bit() {
        String a = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return a.contains("64");
    }

    /** 运行 Forge / NeoForge 官方安装器。 */
    private static String installForgeLike(Tasks.Task task, String mc, String loader, Path game, Data data,
                                           String wantedVersion) throws Exception {
        String installerUrl;
        String prefix;
        if ("Forge".equals(loader)) {
            String fv = wantedVersion == null ? "" : wantedVersion.trim();
            if (fv.isEmpty()) {
                String promos = getText(bmcl() + "/maven/net/minecraftforge/forge/promotions_slim.json",
                        "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
                Map<String, Object> p = Json.obj(Json.obj(Json.parse(promos)), "promos");
                fv = Json.str(p, mc + "-recommended", Json.str(p, mc + "-latest", ""));
            }
            if (fv.isEmpty()) throw new IOException("没有适用于 Minecraft " + mc + " 的 Forge 版本");
            installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mc + "-" + fv
                    + "/forge-" + mc + "-" + fv + "-installer.jar";
            prefix = mc + "-forge";
        } else {
            String best = wantedVersion == null ? "" : wantedVersion.trim();
            if (best.isEmpty()) {
                String listText = getText(bmcl() + "/maven/net/neoforged/neoforge/maven-metadata.xml",
                        "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
                String want = neoforgePrefix(mc);
                List<String> all = new ArrayList<>();
                Matcher xml = Pattern.compile("<version>([^<]+)</version>").matcher(listText);
                while (xml.find()) all.add(xml.group(1));
                if (all.isEmpty()) {
                    for (Object o : Json.arr(Json.obj(Json.parse(listText)), "versions")) {
                        all.add(String.valueOf(o));
                    }
                }
                for (String v : all) {
                    if (want == null || !v.startsWith(want)) continue;
                    if (best.isEmpty() || compareVersion(v, best) > 0) best = v;
                }
            }
            if (best.isEmpty()) throw new IOException("没有适用于 Minecraft " + mc + " 的 NeoForge 版本");
            installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + best
                    + "/neoforge-" + best + "-installer.jar";
            prefix = "neoforge-" + best;
        }

        Path installer = game.resolve("TCL").resolve("installers")
                .resolve(loader.toLowerCase() + "-" + mc + "-installer.jar");
        task.stage = "下载 " + loader + " 安装器";
        downloadFile(task, installerUrl, installer, 0.95, 0.97, loader + " 安装器");
        String javaExe = data.javaPath;
        if (javaExe == null || javaExe.isEmpty() || !Files.isRegularFile(Paths.get(javaExe))) {
            throw new IOException("未找到可用的 Java 运行时，无法运行 " + loader + " 安装器");
        }
        task.stage = "正在运行 " + loader + " 安装器";
        // 安装器靠 launcher_profiles.json 定位游戏目录。
        Path profiles = game.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profiles)) {
            Files.writeString(profiles,
                    "{\"profiles\":{},\"settings\":{\"enableSnapshots\":false,\"enableAdvanced\":false},"
                            + "\"version\":3}", StandardCharsets.UTF_8);
        }
        // 输出写文件，避免安装器大量日志把管道塞满导致卡死
        Path logFile = installer.getParent().resolve(loader.toLowerCase() + "-" + mc + ".log");
        Files.createDirectories(logFile.getParent());
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Dfile.encoding=UTF-8");
        // 安装器自己也要联网下依赖，代理一起带过去
        String proxyHost = data.get("SystemProxyHost", "");
        String proxyPort = data.get("SystemProxyPort", "7890");
        int proxyMode = data.getInt("SystemHttpProxyType", 1);
        if (!proxyHost.isEmpty() && (proxyMode == 2 || proxyMode == 3)) {
            if (proxyMode == 3) {
                cmd.add("-DsocksProxyHost=" + proxyHost);
                cmd.add("-DsocksProxyPort=" + proxyPort);
            } else {
                cmd.add("-Dhttp.proxyHost=" + proxyHost);
                cmd.add("-Dhttp.proxyPort=" + proxyPort);
                cmd.add("-Dhttps.proxyHost=" + proxyHost);
                cmd.add("-Dhttps.proxyPort=" + proxyPort);
            }
        }
        cmd.add("-jar");
        cmd.add(installer.toString());
        cmd.add("--installClient");
        cmd.add(game.toString());
        // 安装器自己从官方 maven 拉依赖很慢，先用镜像把能预下的都放好
        prefetchInstallerLibraries(task, installer, game, loader);
        if (task.canceled) throw new IOException("已取消");
        task.stage = "正在运行 " + loader + " 安装器";
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(game.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        Process process = pb.start();
        runInstaller(task, process, logFile, loader);
        int code = process.exitValue();
        if (code != 0) {
            String output = Files.isRegularFile(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            String tail = output.length() > 400 ? output.substring(output.length() - 400) : output;
            throw new IOException(loader + " 安装器退出码 " + code + "：" + tail.trim());
        }
        return findInstalledVersion(game, prefix, mc);
    }

    /** 从安装器 jar 的 install_profile.json 里读出依赖，先用镜像下好，安装器校验通过就直接跳过。 */
    private static void prefetchInstallerLibraries(Tasks.Task task, Path installer, Path game, String loader) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(installer.toFile())) {
            java.util.zip.ZipEntry entry = zip.getEntry("install_profile.json");
            if (entry == null) return;
            String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> profile = Json.obj(Json.parse(text));
            List<Map<String, Object>> libs = new ArrayList<>();
            for (Object o : Json.arr(profile.get("libraries"))) libs.add(Json.obj(o));
            for (Object o : Json.arr(Json.obj(profile.get("versionInfo")).get("libraries"))) libs.add(Json.obj(o));
            // spec 1 的客户端库在 jar 内的 version.json 里
            String jsonPath = Json.str(profile, "json", "");
            if (!jsonPath.isEmpty()) {
                java.util.zip.ZipEntry versionEntry = zip.getEntry(jsonPath.replaceFirst("^/", ""));
                if (versionEntry != null) {
                    String versionText = new String(zip.getInputStream(versionEntry).readAllBytes(), StandardCharsets.UTF_8);
                    for (Object o : Json.arr(Json.obj(Json.parse(versionText)).get("libraries"))) {
                        libs.add(Json.obj(o));
                    }
                }
            }
            if (libs.isEmpty()) return;
            task.stage = "预下载 " + loader + " 依赖";
            int done = 0;
            for (Map<String, Object> lib : libs) {
                if (task.canceled) return;
                Map<String, Object> artifact = Json.obj(Json.obj(lib.get("downloads")).get("artifact"));
                String path = Json.str(artifact, "path", "");
                String url = Json.str(artifact, "url", "");
                if (path.isEmpty()) {
                    // 老版本 Forge 只有 name + url
                    String name = Json.str(lib, "name", "");
                    String base = Json.str(lib, "url", "");
                    if (name.isEmpty() || base.isEmpty()) continue;
                    path = nameToPath(name);
                    if (!base.endsWith("/")) base += "/";
                    url = base + path;
                }
                if (url.isEmpty()) continue;
                Path target = game.resolve("libraries").resolve(path);
                if (!Files.isRegularFile(target) || Files.size(target) <= 0) {
                    // 有 sha1 就让下载器校验，坏文件不会留给安装器
                    String sha1 = Json.str(artifact, "sha1", "");
                    String label = sha1.matches("[0-9a-fA-F]{40}") ? sha1 : loader + " 依赖 " + target.getFileName();
                    try {
                        downloadFile(task, url, target, 0.97, 0.99, label);
                    } catch (Exception e) {
                        // 预下载失败不影响安装器，它自己会再试
                        Data.get().log("预下载失败：" + path + " " + e.getMessage());
                    }
                }
                done++;
                task.stage = "预下载 " + loader + " 依赖 " + done + " / " + libs.size();
            }
        } catch (Exception e) {
            Data.get().log("读取 " + loader + " 安装器清单失败：" + e.getMessage());
        }
    }

    /** 跟踪安装器进程：日志最后一行写进任务，取消时杀掉进程。 */
    private static void runInstaller(Tasks.Task task, Process process, Path logFile, String loader) throws Exception {
        long start = System.currentTimeMillis();
        long lastChange = start;
        String lastLine = "";
        while (true) {
            if (task.canceled) {
                killTree(process);
                throw new IOException("已取消");
            }
            if (process.waitFor(700, java.util.concurrent.TimeUnit.MILLISECONDS)) return;
            String line = lastLogLine(logFile);
            long now = System.currentTimeMillis();
            if (!line.isEmpty() && !line.equals(lastLine)) {
                lastLine = line;
                lastChange = now;
                task.stage = "运行 " + loader + " 安装器：" + (line.length() > 70 ? line.substring(0, 70) + "…" : line);
            } else if (now - lastChange > 3000) {
                // 日志没动静也报个时长，让人知道没死
                task.stage = "运行 " + loader + " 安装器（" + ((now - start) / 1000) + " 秒，下载依赖较慢请稍候）";
                lastChange = now;
            }
            if (now - start > 20 * 60 * 1000L) {
                killTree(process);
                throw new IOException(loader + " 安装器超过 20 分钟没有结束，请检查网络或改用镜像。日志：" + logFile);
            }
        }
    }

    /** 先杀子进程再杀自己，安装器会 fork 出子 JVM。 */
    private static void killTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
        }
        process.destroyForcibly();
    }

    /** 日志最后一行，读文件尾巴避免整份加载。 */
    private static String lastLogLine(Path logFile) {
        try {
            if (!Files.isRegularFile(logFile)) return "";
            long size = Files.size(logFile);
            long from = Math.max(0, size - 4096);
            String text;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(from);
                byte[] buf = new byte[(int) (size - from)];
                raf.readFully(buf);
                text = new String(buf, StandardCharsets.UTF_8);
            }
            String[] lines = text.split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                String s = lines[i].trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String findInstalledVersion(Path game, String prefix, String mc) {
        Path versions = game.resolve("versions");
        String best = "";
        if (Files.isDirectory(versions)) {
            try (var stream = Files.list(versions)) {
                for (Path dir : (Iterable<Path>) stream::iterator) {
                    String name = dir.getFileName().toString();
                    if (!name.startsWith(prefix)) continue;
                    if (!Files.isRegularFile(dir.resolve(name + ".json"))) continue;
                    if (best.isEmpty() || compareVersion(name, best) > 0) best = name;
                }
            } catch (Exception ignored) {
            }
        }
        return best.isEmpty() ? mc : best;
    }

    /** MC 版本转 NeoForge 前缀，如 1.20.2 对应 20.2。 */
    private static String neoforgePrefix(String mc) {
        String v = mc.startsWith("1.") ? mc.substring(2) : mc;
        if (!v.contains(".")) v = v + ".0";
        return v;
    }

    private static int compareVersion(String a, String b) {
        String[] x = a.split("[^0-9]+");
        String[] y = b.split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int av = i < x.length && !x[i].isEmpty() ? parseIntSafe(x[i]) : 0;
            int bv = i < y.length && !y[i].isEmpty() ? parseIntSafe(y[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String installFabric(Tasks.Task task, String gameVersion, Path game, String wantedVersion, boolean legacy) throws Exception {
        String base = (legacy ? "https://meta.legacyfabric.net/v2/versions/loader/"
                : "https://meta.fabricmc.net/v2/versions/loader/") + gameVersion;
        // 官方 meta 在国内很慢，镜像优先
        String mirror = legacy ? base : bmcl() + "/fabric-meta/v2/versions/loader/" + gameVersion;
        Object list = Json.parse(getText(mirror, base));
        List<Object> loaders = Json.arr(list);
        if (loaders.isEmpty()) throw new IOException("没有可用的 Fabric 加载器");
        String loaderVersion = null;
        if (wantedVersion != null && !wantedVersion.isBlank()) {
            // 选中的版本不在列表里就回退稳定版。
            for (Object o : loaders) {
                if (wantedVersion.trim().equals(Json.str(Json.obj(o, "loader"), "version", ""))) {
                    loaderVersion = wantedVersion.trim();
                    break;
                }
            }
        }
        if (loaderVersion == null) {
            for (Object o : loaders) {
                Map<String, Object> loaderObj = Json.obj(o, "loader");
                if (Json.bool(loaderObj, "stable", false)) {
                    loaderVersion = Json.str(loaderObj, "version", "0.16.9");
                    break;
                }
            }
        }
        if (loaderVersion == null || loaderVersion.isEmpty()) throw new IOException("没有可用的 " + (legacy ? "Legacy" : "") + "Fabric 加载器版本");
        String profileUrl = base + "/" + loaderVersion + "/profile/json";
        String profileText = getText(mirror + "/" + loaderVersion + "/profile/json", profileUrl);
        Map<String, Object> profile = Json.obj(Json.parse(profileText));
        String id = legacy ? "legacy-fabric-loader-" + loaderVersion + "-" + gameVersion
                : "fabric-loader-" + loaderVersion + "-" + gameVersion;
        profile.put("id", id);
        Path dir = game.resolve("versions").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), toJson(profile), StandardCharsets.UTF_8);
        // 下载加载器依赖。
        for (Object libObj : Json.arr(profile.get("libraries"))) {
            Map<String, Object> lib = Json.obj(libObj);
            String name = Json.str(lib, "name", "");
            String artifactUrl = Json.str(Json.obj(lib.get("downloads")).get("artifact"), "url", "");
            String rel = Json.str(Json.obj(lib.get("downloads")).get("artifact"), "path", "");
            if (rel.isEmpty()) rel = nameToPath(name);
            String url = artifactUrl;
            if (url.isEmpty()) {
                String mavenBase = Json.str(lib, "url", legacy ? "https://maven.legacyfabric.net/" : "https://maven.fabricmc.net/");
                if (!mavenBase.endsWith("/")) mavenBase += "/";
                url = mavenBase + rel;
            }
            downloadFile(task, url, game.resolve("libraries").resolve(rel), 0.95, 0.99, name);
        }
        return id;
    }

    /** LiteLoader 安装：写 inheritsFrom 版本 JSON 并下 jar。 */
    private static String installLiteLoader(Tasks.Task task, String gameVersion, Path game, Data data) throws Exception {
        String versionName = gameVersion + "-LiteLoader";
        Path versionDir = game.resolve("versions").resolve(versionName);
        Files.createDirectories(versionDir);
        // liteloader 官方源
        String mc = gameVersion;
        String fileName = "liteloader-installer-" + mc + ((mc.equals("1.8") || mc.equals("1.9")) ? ".0" : "") + "-00-SNAPSHOT.jar";
        String liteUrl = "http://jenkins.liteloader.com/job/LiteLoaderInstaller%20" + mc
                + "/lastSuccessfulBuild/artifact/" + (mc.equals("1.8") ? "ant/dist/" : "build/libs/") + fileName;
        Path liteJar = game.resolve("libraries").resolve("com").resolve("mumfrey").resolve("liteloader")
                .resolve(gameVersion + "-SNAPSHOT").resolve(fileName);
        Files.createDirectories(liteJar.getParent());
        downloadFile(task, liteUrl, liteJar, 0.95, 0.99, "LiteLoader " + gameVersion);
        String json = "{"
                + "\"id\":\"" + versionName + "\","
                + "\"mainClass\":\"net.minecraft.launchwrapper.Launch\","
                + "\"minimumLauncherVersion\":18,"
                + "\"inheritsFrom\":\"" + gameVersion + "\","
                + "\"jar\":\"" + gameVersion + "\","
                + "\"arguments\":{\"game\":[\"--tweakClass\",\"com.mumfrey.liteloader.launch.LiteLoaderTweaker\"]},"
                + "\"libraries\":[{\"name\":\"com.mumfrey:liteloader:" + gameVersion + "-SNAPSHOT\","
                + "\"url\":\"https://dl.liteloader.com/versions/\"}]}";
        Files.writeString(versionDir.resolve(versionName + ".json"), json, StandardCharsets.UTF_8);
        return versionName;
    }

    /** OptiFine 只下 jar 并登记，没指定版本就提示手动安装。 */
    private static String installOptiFine(Tasks.Task task, String gameVersion, Path game, Inst inst, MainWindow win) {
        String chosen = inst == null ? "" : inst.optiFine;
        if (chosen.isEmpty()) {
            // 没选版本，提示手动安装。
            Data.get().log("OptiFine 需从官网手动下载安装器；原版文件已就绪。");
            return gameVersion;
        }
        try {
            downloadOptiFineJar(task, gameVersion, game, chosen);
        } catch (Exception e) {
            String msg = "OptiFine " + chosen + " 未安装：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
        return gameVersion;
    }

    /** OptiFine 的两个 BMCLAPI 源。 */
    private static String[] optifineUrls(String mc, String chosen) {
        String inherit = mc.replace(".", "");
        String tail = chosen.trim().replace(" ", "/");
        return new String[]{
                bmcl() + "/optifine/" + inherit + "/HD_U_" + tail,
                bmcl() + "/optifine/" + inherit + "/HD_U/" + tail
        };
    }

    /** 用 versionList 里的真实文件名落盘。 */
    private static String downloadOptiFineJar(Tasks.Task task, String mc, Path game, String chosen) throws Exception {
        Map<String, String> files = Loaders.optifineFiles(mc);
        String file = files.getOrDefault(chosen, "");
        if (file.isEmpty()) throw new IOException("找不到 OptiFine " + chosen + " 的文件名");
        Path target = game.resolve("TCL").resolve("cache").resolve("optifine").resolve(file);
        Exception last = null;
        for (String url : optifineUrls(mc, chosen)) {
            try {
                downloadFile(task, url, target, 0.95, 0.99, "OptiFine " + chosen);
                Data.get().log("OptiFine " + chosen + " 已下载到 " + target.getFileName() + "（需手动运行安装器注入）");
                return file;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("下载失败：" + (last == null ? "网络错误" : last.getMessage()));
    }

    private static String nameToPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace(':', '/');
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    private static void sub(Tasks.Task task, int index, Tasks.State state) {
        if (index >= 0 && index < task.subs.size()) task.subs.get(index).state = state;
        Tasks.INSTANCE.changed();
    }

    private static void check(Tasks.Task task) throws IOException {
        if (task.canceled) throw new IOException("已取消");
    }

    /** 资源文件是否完整落盘：有大小就精确比对。 */
    private static boolean assetReady(Path file, long expectedSize) {
        try {
            if (!Files.isRegularFile(file)) return false;
            long size = Files.size(file);
            return size > 0 && (expectedSize <= 0 || size == expectedSize);
        } catch (Exception e) {
            return false;
        }
    }

    private static double parseNum(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void downloadFile(Tasks.Task task, String url, Path target,
                                     double progressFrom, double progressTo, String label) throws IOException {
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            // 资源文件按 SHA-1 校验，半截文件重下。
            if (label != null && label.matches("[0-9a-fA-F]{40}")) {
                try {
                    if (GameLauncher.sha1Of(target).equalsIgnoreCase(label)) return;
                } catch (Exception ignored) {
                }
                Files.deleteIfExists(target);
            } else if (target.getFileName().toString().endsWith(".json")) {
                // JSON 能解析才算完整，否则重下。
                try {
                    Json.parse(Files.readString(target));
                    return;
                } catch (Exception ignored) {
                    Files.deleteIfExists(target);
                }
            } else {
                return;
            }
        }
        Files.createDirectories(target.getParent());
        // 40 位十六进制视为 SHA-1。
        String sha1 = label != null && label.matches("[0-9a-fA-F]{40}") ? label : null;
        Exception last = null;
        // 候选源按速度排序，避免一直走慢源。
        for (String candidate : Sources.rank(mirrors(url))) {
            try {
                Fetch.file(task, candidate, target, sha1, progressFrom, progressTo);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("下载失败：" + label + (last == null ? "" : "（" + last.getMessage() + "）"));
    }

    private static void downloadTo(Tasks.Task task, String primary, String fallback, Path target,
                                   double progressFrom, double progressTo) throws IOException {
        try {
            downloadFile(task, primary, target, progressFrom, progressTo, target.getFileName().toString());
        } catch (IOException e) {
            if (fallback != null && !fallback.isEmpty() && !fallback.equals(primary)) {
                downloadFile(task, fallback, target, progressFrom, progressTo, target.getFileName().toString());
            } else {
                throw e;
            }
        }
    }

    /** 官方 URL 转候选源列表，镜像根跟随设置。 */
    private static List<String> mirrors(String url) {
        List<String> out = new ArrayList<>();
        int source = Data.get().getInt("SystemDownloadSource", 0);
        if (source == 1) {
            out.add(url);
            return out;
        }
        String root = bmcl();
        String host;
        try { host = URI.create(root).getHost(); } catch (Exception e) { host = ""; }

        // 本身就是镜像地址时补上官方地址回落。
        String official = officialOf(url, root);
        if (official != null) {
            out.add(url);
            out.add(official);
            return out;
        }

        List<String> mirrorUrls = new ArrayList<>();
        if (url.contains("libraries.minecraft.net")) {
            mirrorUrls.add(url.replace("https://libraries.minecraft.net", root + "/maven"));
            mirrorUrls.add(url.replace("http://libraries.minecraft.net", root + "/maven"));
        }
        if ((url.contains("piston-meta.mojang.com") || url.contains("launchermeta.mojang.com"))
                && host != null && !host.isEmpty()) {
            mirrorUrls.add(url.replace("piston-meta.mojang.com", host)
                    .replace("launchermeta.mojang.com", host));
        }
        if (url.contains("resources.download.minecraft.net")) {
            mirrorUrls.add(url.replace("https://resources.download.minecraft.net", root + "/assets"));
        }
        // 加载器 maven 也走镜像：官方源在国内经常只有几十 KB/s，安装器能拖十几分钟
        if (url.contains("maven.minecraftforge.net")) {
            mirrorUrls.add(url.replace("https://maven.minecraftforge.net", root + "/maven")
                    .replace("http://maven.minecraftforge.net", root + "/maven"));
        }
        if (url.contains("files.minecraftforge.net/maven")) {
            mirrorUrls.add(url.replace("https://files.minecraftforge.net/maven", root + "/maven")
                    .replace("http://files.minecraftforge.net/maven", root + "/maven"));
        }
        if (url.contains("maven.neoforged.net/releases")) {
            mirrorUrls.add(url.replace("https://maven.neoforged.net/releases", root + "/maven"));
        }
        if (url.contains("maven.fabricmc.net")) {
            mirrorUrls.add(url.replace("https://maven.fabricmc.net", root + "/maven"));
        }
        if (url.contains("meta.fabricmc.net")) {
            mirrorUrls.add(url.replace("https://meta.fabricmc.net", root + "/fabric-meta"));
        }
        if (source == 2 && !mirrorUrls.isEmpty()) {
            out.addAll(mirrorUrls);
            out.add(url);
        } else {
            out.add(url);
            out.addAll(mirrorUrls);
        }
        out.removeIf(u -> u == null || u.isEmpty());
        return out.stream().distinct().toList();
    }

    /** 镜像地址转回官方地址，不认识返回 null。 */
    private static String officialOf(String url, String root) {
        if (root == null || root.isEmpty() || !url.startsWith(root)) return null;
        String tail = url.substring(root.length());
        if (tail.startsWith("/assets/")) {
            return "https://resources.download.minecraft.net" + tail.substring("/assets".length());
        }
        if (tail.startsWith("/maven/")) {
            return "https://libraries.minecraft.net" + tail.substring("/maven".length());
        }
        return null;
    }

    private static String mirrorLib(String url) {
        return url;
    }

    private static String speed(long bytesPerSec) {
        if (bytesPerSec > 1024 * 1024) return String.format("%.1f M/s", bytesPerSec / 1024.0 / 1024);
        if (bytesPerSec > 1024) return String.format("%.0f K/s", bytesPerSec / 1024.0);
        return bytesPerSec + " B/s";
    }

    /** 解析设置里的限速，空或「不限」返回 0。 */
    public static long speedLimitBytes() {
        String v = Data.get().get("SystemSpeedLimit", "").trim();
        if (v.isEmpty() || v.contains("不限")) return 0;
        try {
            double n = Double.parseDouble(v.replaceAll("[^0-9.]", ""));
            if (v.toUpperCase().contains("M")) return (long) (n * 1024 * 1024);
            if (v.toUpperCase().contains("K")) return (long) (n * 1024);
            return (long) (n * 1024);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 按限速休眠，started 是开始时间毫秒。 */
    public static void throttle(long done, long started, long limit) {
        long expected = done * 1000 / limit;
        long elapsed = System.currentTimeMillis() - started;
        if (expected > elapsed) {
            try {
                Thread.sleep(expected - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String getText(String... urls) throws IOException {
        Exception last = null;
        // 清单也按速度择优，慢镜像直接跳过。
        for (String url : Sources.rank(java.util.Arrays.asList(urls))) {
            try {
                // 版本清单几 MB，gzip 后小很多。
                return Http.get(url);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException(last == null ? "网络请求失败" : last.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return "null";
    }

    private static void unused() {
        // 保留 Map 引用，避免 IDE 警告。
        Map<String, String> m = new LinkedHashMap<>();
        m.clear();
    }
}
