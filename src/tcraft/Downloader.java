package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Downloader {

    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String BMCL_DEFAULT = "https://bmclapi2.bangbang93.com";

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

    public static void startInstall(MainWindow win, Data.McVersion version, String loader, String loaderVersion) {
        startInstall(win, version, loader, (java.nio.file.Path) null, loaderVersion, null, null);
    }

    public static void startInstall(MainWindow win, Data.McVersion version, String loader,
                                    java.nio.file.Path gameDirOverride) {
        startInstall(win, version, loader, gameDirOverride, (String) null, (Inst) null, null);
    }

    public static void startInstall(MainWindow win, Data.McVersion version, String loader,
                                    java.nio.file.Path gameDirOverride, String loaderVersion, Inst inst) {
        startInstall(win, version, loader, gameDirOverride, loaderVersion, inst, null);
    }

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
                    data.log("安装失败：" + Log.describe(e));
                    Errors.error(Lang.tf("安装 %s 失败", version.id), e);
                }
            } finally {
                Tasks.INSTANCE.changed();
            }
        }, "TCL-Download-" + version.id);
        thread.setDaemon(true);
        thread.start();
    }

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
        // 装当前游戏目录，隔离在启动时决定
        startInstall(win, version, loader, (java.nio.file.Path) null, loaderVersion, inst);
    }

    public static String install(Tasks.Task task, Data.McVersion version, String loader, Data data, MainWindow win,
                                java.nio.file.Path gameDirOverride) throws Exception {
        return install(task, version, loader, data, win, gameDirOverride, null, null);
    }

    public static String install(Tasks.Task task, Data.McVersion version, String loader, Data data, MainWindow win,
                                java.nio.file.Path gameDirOverride, String loaderVersion) throws Exception {
        return install(task, version, loader, data, win, gameDirOverride, loaderVersion, null);
    }

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
        if (entry == null) throw new IOException(Lang.t("版本列表中找不到 ") + version.id);
        sub(task, 0, Tasks.State.RUNNING);
        task.stage = "获取版本信息";
        task.progress = 0.03;
        Tasks.INSTANCE.changed();

        Path versionDir = game.resolve("versions").resolve(version.id);
        Files.createDirectories(versionDir);
        Path versionJson = versionDir.resolve(version.id + ".json");
        String versionUrl = Json.str(entry, "url", "");
        // 带 sha1：版本 JSON 被换=整条下载链被接管
        String versionSha1 = Json.str(entry, "sha1", "");
        if (source == 1) downloadTo(task, versionUrl, "", versionJson, 0.03, 0.08, versionSha1);
        else if (source == 2) downloadTo(task, bmcl() + "/version/" + version.id + "/json", versionUrl, versionJson, 0.03, 0.08, versionSha1);
        else downloadTo(task, versionUrl, bmcl() + "/version/" + version.id + "/json", versionJson, 0.03, 0.08, versionSha1);
        sub(task, 0, Tasks.State.DONE);
        task.progress = 0.08;

        Map<String, Object> versionData = Json.obj(Json.parse(Files.readString(versionJson, StandardCharsets.UTF_8)));
        Map<String, Object> downloads = Json.obj(versionData.get("downloads"));
        Map<String, Object> client = Json.obj(downloads.get("client"));
        if (!client.isEmpty()) {
            String clientUrl = Json.str(client, "url", "");
            String clientSha1 = Json.str(client, "sha1", "");
            Path jar = versionDir.resolve(version.id + ".jar");
            sub(task, 1, Tasks.State.RUNNING);
            task.stage = "下载客户端";
            downloadFile(task, clientUrl, jar, 0.08, 0.28, "客户端 " + version.id + ".jar", clientSha1);
            sub(task, 1, Tasks.State.DONE);
        } else {
            sub(task, 1, Tasks.State.DONE);
        }

        // 按平台规则过滤依赖库
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
            String sha1 = Json.str(art, "sha1", "");
            if (!rel.isEmpty() && !url.isEmpty()) {
                libFiles.add(new Object[]{url, game.resolve("libraries").resolve(rel), sha1});
            }
            // 旧版 natives 格式，只下当前平台的
            Map<String, Object> nativesMap = Json.obj(lib.get("natives"));
            if (!nativesMap.isEmpty()) {
                String classifier = Json.str(nativesMap, nativeOsName(), "")
                        .replace("${arch}", is64Bit() ? "64" : "32");
                Map<String, Object> nat = Json.obj(Json.obj(downloadsLib.get("classifiers")).get(classifier));
                String nrel = Json.str(nat, "path", "");
                String nurl = Json.str(nat, "url", "");
                String nsha1 = Json.str(nat, "sha1", "");
                if (!nrel.isEmpty() && !nurl.isEmpty()) {
                    libFiles.add(new Object[]{nurl, game.resolve("libraries").resolve(nrel), nsha1});
                }
            }
        }
        int total = Math.max(1, libFiles.size());
        if (!libFiles.isEmpty()) {
            // 依赖库数量多且文件偏大，串行下太慢，走和资源阶段一样的并发池
            int threads = Math.max(1, Math.min(32, Data.get().getInt("SystemMaxThreads", 64)));
            task.threads = threads;
            Tasks.INSTANCE.changed();
            ExecutorService libPool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "TCL-Lib");
                t.setDaemon(true);
                return t;
            });
            AtomicInteger libDone = new AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<Exception> libError =
                    new java.util.concurrent.atomic.AtomicReference<>();
            List<Future<?>> libFutures = new ArrayList<>();
            try {
                for (Object[] f : libFiles) {
                    libFutures.add(libPool.submit(() -> {
                        if (task.canceled) return;
                        try {
                            downloadFile(null, (String) f[0], (Path) f[1], 0, 0,
                                    ((Path) f[1]).getFileName().toString(), (String) f[2]);
                        } catch (Exception e) {
                            libError.compareAndSet(null, e);
                        }
                        int n = libDone.incrementAndGet();
                        task.progress = 0.28 + 0.22 * n / (double) total;
                        task.remainingFiles = Math.max(0, total - n);
                        task.stage = "下载依赖库 " + n + " / " + total;
                        if (n % 3 == 0) Tasks.INSTANCE.changed();
                    }));
                }
                for (Future<?> fu : libFutures) {
                    try {
                        fu.get();
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                libPool.shutdownNow();
            }
            check(task);
            if (libError.get() != null) throw libError.get();
        }
        sub(task, 2, Tasks.State.DONE);

        sub(task, 3, Tasks.State.RUNNING);
        task.stage = "下载资源索引";
        Map<String, Object> assetIndex = Json.obj(versionData.get("assetIndex"));
        String assetId = Json.str(assetIndex, "id", "legacy");
        String indexUrl = Json.str(assetIndex, "url", "");
        String indexSha1 = Json.str(assetIndex, "sha1", "");
        Path indexPath = game.resolve("assets").resolve("indexes").resolve(assetId + ".json");
        downloadTo(task, indexUrl, bmcl() + "/indexes/" + assetId + ".json", indexPath, 0.50, 0.55, indexSha1);
        sub(task, 3, Tasks.State.DONE);

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
                // 路径与地址都由 hash 拼，非 40 位十六进制跳过
                if (!HASH_40.matcher(hash).matches()) continue;
                long expectedSize = (long) parseNum(Json.str(obj, "size", "0"), 0);
                Path target = game.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                futures.add(pool.submit(() -> {
                    if (task.canceled) return;
                    try {
                        if (!assetReady(target, expectedSize, hash)) {
                            Files.deleteIfExists(target);
                            String assetUrl = Data.get().getInt("SystemDownloadSource", 0) == 1
                                    ? "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash
                                    : bmcl() + "/assets/" + hash.substring(0, 2) + "/" + hash;
                            try {
                                downloadFile(null, assetUrl, target, 0, 0, hash, hash);
                            } catch (Exception first) {
                                // 并发大时 CDN 偶尔拒连，退避后重试一次
                                Thread.sleep(600);
                                Files.deleteIfExists(target);
                                downloadFile(null, assetUrl, target, 0, 0, hash, hash);
                            }
                            if (!assetReady(target, expectedSize, hash)) throw new IOException("文件不完整");
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
        // 失败文件补抓一轮，不让最后几个卡住整个安装
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
                        downloadFile(null, url, target, 0, 0, h[0], h[0]);
                        if (assetReady(target, expectedSize, h[0])) {
                            ok++;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                task.stage = "补抓失败文件 " + retried + " / " + retryQueue.size();
            }
            if (ok < retryQueue.size()) {
                throw new IOException(Lang.t("有 ") + (retryQueue.size() - ok)
                        + Lang.t(" 个资源文件下载失败，请检查网络后重试"));
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

        // 附加载器下到 mods 或缓存目录
        if (inst != null) {
            Data.Instance probe = new Data.Instance("", actualVersion, loader, "", "", "", 0, false,
                    game.toString(), Isolation.FOLLOW);
            Path contentDir = Isolation.on(probe) ? game.resolve("versions").resolve(actualVersion) : game;
            try {
                Files.createDirectories(contentDir.resolve("mods"));
            } catch (Exception e) {
                Data.get().log("创建 mods 目录失败：" + Log.describe(e));
            }
            installExtraComponents(task, version.id, contentDir, inst, "OptiFine".equals(loader), win);
        }

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

    private static void installExtraComponents(Tasks.Task task, String mc, Path game, Inst inst,
                                                boolean optiFineIsPrimary, MainWindow win) {
        if (!inst.fabricApi.isEmpty() || !inst.optiFabric.isEmpty()) {
            try {
                Files.createDirectories(game.resolve("mods"));
            } catch (Exception e) {
                Data.get().log("无法创建 mods 文件夹：" + Log.describe(e));
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
        if (!optiFineIsPrimary && !inst.optiFine.isEmpty()) {
            task.stage = "安装 OptiFine";
            installOptiFineExtra(task, mc, game, inst.optiFine, win);
        }
    }

    private static void installMod(Tasks.Task task, String loader, String mc, String version, Path mods, MainWindow win) {
        try {
            String[] f = Loaders.modFile(loader, mc, version);
            if (f == null) throw new IOException("找不到 " + loader + " " + version + " 的下载地址");
            downloadFile(task, f[0], mods.resolve(f[1]), 0.95, 0.99, loader + " " + version);
            Data.get().log(loader + " " + version + " 已安装到 mods/" + f[1]);
        } catch (Exception e) {
            String msg = loader + " " + version + Lang.t(" 未安装：")
                    + (e.getMessage() == null ? Lang.t("网络错误") : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
    }

    private static void installOptiFineExtra(Tasks.Task task, String mc, Path game, String chosen, MainWindow win) {
        try {
            downloadOptiFineJar(task, mc, game, chosen);
        } catch (Exception e) {
            String msg = "OptiFine " + chosen + Lang.t(" 未安装：")
                    + (e.getMessage() == null ? Lang.t("网络错误") : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
    }

    public static void downloadFileTo(java.util.function.DoubleConsumer progress, String url, Path target,
                                      String label) throws IOException {
        downloadFileTo(progress, url, target, label, null);
    }

    public static void downloadFileTo(java.util.function.DoubleConsumer progress, String url, Path target,
                                      String label, String expectedSha1) throws IOException {
        String sha1 = normalizeSha1(expectedSha1);
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.toAbsolutePath().getParent());
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            if (sha1 != null) {
                boolean ok;
                try {
                    ok = GameLauncher.sha1Of(target).equalsIgnoreCase(sha1);
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) return;
                Files.deleteIfExists(target);
            } else if (target.getFileName().toString().endsWith(".json")) {
                // JSON 能解析才算完整，否则重下
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
        IOException last = null;
        for (String candidate : Sources.rank(mirrors(url))) {
            try {
                transferWithProgress(progress, candidate, part, target, sha1);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException(Lang.t("下载失败：") + (sha1 != null ? label + "（SHA-1 " + sha1 + "）" : label)
                + (last == null ? "" : "（" + last.getMessage() + "）"));
    }

    private static final long[] DIRECT_BACKOFF_MS = {200, 500, 1000, 2000, 3000};

    // 断点续传 + 校验 + 退避重试，进度直接透传给调用方
    private static void transferWithProgress(java.util.function.DoubleConsumer progress, String url,
                                             Path part, Path target, String sha1) throws IOException {
        java.security.MessageDigest digest = null;
        if (sha1 != null) {
            try {
                digest = java.security.MessageDigest.getInstance("SHA-1");
            } catch (Exception e) {
                throw new IOException(Lang.t("SHA-1 不可用"), e);
            }
        }
        // 与 Fetch.PART_LOCKS 同锁，防 .part 双写
        String lockKey = part.toAbsolutePath().toString();
        Object lock = new Object();
        if (Fetch.PART_LOCKS.putIfAbsent(lockKey, lock) != null) {
            throw new IOException(Lang.t("该文件已在下载中：") + target.getFileName());
        }
        // 无哈希无法校验旧 .part，取锁后删除重下
        if (sha1 == null) Files.deleteIfExists(part);
        IOException last = null;
        try {
            long done = 0;
            if (Files.isRegularFile(part)) {
                done = Files.size(part);
                // 续传时先重放已有部分，继续算摘要
                if (digest != null && done > 0) {
                    try (InputStream rin = Files.newInputStream(part)) {
                        byte[] rbuf = new byte[65536];
                        int rn;
                        while ((rn = rin.read(rbuf)) > 0) digest.update(rbuf, 0, rn);
                    } catch (Exception e) {
                        digest.reset();
                    }
                }
            }
            for (int attempt = 0; attempt <= 5; attempt++) {
                if (attempt > 0) {
                    try {
                        Thread.sleep(DIRECT_BACKOFF_MS[Math.min(attempt - 1, DIRECT_BACKOFF_MS.length - 1)]);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(Lang.t("下载被中断"), e);
                    }
                }
                try {
                    transferOnce(progress, url, part, done, digest);
                    if (digest != null) {
                        String actual = java.util.HexFormat.of().formatHex(digest.digest());
                        if (!sha1.equalsIgnoreCase(actual)) {
                            // 校验失败，丢掉 .part 重下
                            Files.deleteIfExists(part);
                            digest.reset();
                            done = 0;
                            last = new IOException("文件校验失败（SHA-1 不匹配）");
                            continue;
                        }
                    }
                    Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (IOException e) {
                    last = e;
                    // 按类型识别 416，message 匹配会误伤
                    if (e instanceof Http.StatusException se && se.code == 416) {
                        Files.deleteIfExists(part);
                        if (digest != null) digest.reset();
                        done = 0;
                        continue;
                    }
                    try {
                        done = Files.isRegularFile(part) ? Files.size(part) : 0;
                    } catch (Exception ignored) {
                        done = 0;
                    }
                }
            }
        } finally {
            Fetch.PART_LOCKS.remove(lockKey, lock);
        }
        throw last != null ? last : new IOException(Lang.t("下载失败：") + url);
    }

    private static void transferOnce(java.util.function.DoubleConsumer progress, String url, Path part,
                                     long done, java.security.MessageDigest digest) throws IOException {
        Http.Body body = done > 0 ? Http.open(url, java.time.Duration.ofSeconds(60), done)
                : Http.open(url, java.time.Duration.ofSeconds(60));
        IdleCloser guard = new IdleCloser(body.in, 20_000);
        guard.start();
        try (InputStream in = body.in) {
            boolean append = done > 0 && body.status == 206;
            if (done > 0 && !append) {
                done = 0;
                if (digest != null) digest.reset();
            }
            long total = body.length >= 0 ? done + body.length : -1;
            long written = 0;
            long limit = speedLimitBytes();
            byte[] buffer = new byte[65536];
            OutputStream out = append
                    ? Files.newOutputStream(part, StandardOpenOption.APPEND, StandardOpenOption.WRITE)
                    : Files.newOutputStream(part, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    guard.touch();
                    out.write(buffer, 0, n);
                    if (digest != null) digest.update(buffer, 0, n);
                    written += n;
                    if (limit > 0) throttle(n, limit);
                    if (progress != null && total > 0) {
                        progress.accept(Math.min(1.0, (done + written) / (double) total));
                    }
                }
            } finally {
                out.close();
            }
            // 长度对不上说明连接被掐断，交给外层重试
            if (body.length >= 0 && written != body.length) {
                throw new IOException(Lang.t("下载不完整：") + written + " / " + body.length);
            }
        } finally {
            guard.close();
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService DIRECT_WATCHDOG =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TCL-Direct-Idle");
                t.setDaemon(true);
                return t;
            });

    // 长时间无新数据就把流掐了，让外层重试换源
    private static final class IdleCloser {
        private final InputStream in;
        private final long timeoutMs;
        private volatile long last = System.currentTimeMillis();
        private volatile boolean stopped;
        private java.util.concurrent.ScheduledFuture<?> job;

        IdleCloser(InputStream in, long timeoutMs) {
            this.in = in;
            this.timeoutMs = timeoutMs;
        }

        void start() {
            job = DIRECT_WATCHDOG.scheduleWithFixedDelay(this::check, 500, 500,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        void touch() {
            last = System.currentTimeMillis();
        }

        void close() {
            stopped = true;
            if (job != null) job.cancel(false);
        }

        private void check() {
            if (stopped || System.currentTimeMillis() - last <= timeoutMs) return;
            stopped = true;
            if (job != null) job.cancel(false);
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

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
            if (fv.isEmpty()) throw new IOException(Lang.t("没有适用于 Minecraft ") + mc + Lang.t(" 的 Forge 版本"));
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
            if (best.isEmpty()) throw new IOException(Lang.t("没有适用于 Minecraft ") + mc + Lang.t(" 的 NeoForge 版本"));
            installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + best
                    + "/neoforge-" + best + "-installer.jar";
            prefix = "neoforge-" + best;
        }

        Path installer = game.resolve("TCL").resolve("installers")
                .resolve(loader.toLowerCase() + "-" + mc + "-installer.jar");
        task.stage = "下载 " + loader + " 安装器";
        // 安装器会被执行，无 .sha1 即中止
        String installerSha1 = fetchRemoteSha1(installerUrl);
        if (installerSha1 == null) {
            // 官方 maven 连不上时镜像侧再取 .sha1
            String mirrorSha1 = mirrorSha1Url(installerUrl);
            if (mirrorSha1 != null) installerSha1 = fetchRemoteSha1(mirrorSha1);
        }
        if (installerSha1 == null) {
            throw new IOException(loader + Lang.t(" 安装器下载源没有提供 .sha1 校验值，出于安全考虑已中止安装：")
                    + installerUrl);
        }
        downloadFile(task, installerUrl, installer, 0.95, 0.97, loader + " 安装器", installerSha1);
        String javaExe = data.javaPath;
        if (javaExe == null || javaExe.isEmpty() || !Files.isRegularFile(Paths.get(javaExe))) {
            throw new IOException(Lang.t("未找到可用的 Java 运行时，无法运行 ") + loader + Lang.t(" 安装器"));
        }
        task.stage = "正在运行 " + loader + " 安装器";
        // 安装器靠 launcher_profiles.json 定位游戏目录
        Path profiles = game.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profiles)) {
            Files.writeString(profiles,
                    "{\"profiles\":{},\"settings\":{\"enableSnapshots\":false,\"enableAdvanced\":false},"
                            + "\"version\":3}", StandardCharsets.UTF_8);
        }
        // 安装器日志量大，输出落文件，不然管道塞满两边卡死
        Path logFile = installer.getParent().resolve(loader.toLowerCase() + "-" + mc + ".log");
        Files.createDirectories(logFile.getParent());
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Dfile.encoding=UTF-8");
        // 安装器需联网下载依赖，代理一并传入
        cmd.addAll(data.proxyJvmArgs());
        cmd.add("-jar");
        cmd.add(installer.toString());
        cmd.add("--installClient");
        cmd.add(game.toString());
        // 安装器从官方 maven 拉依赖很慢，先用镜像预下
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
            throw new IOException(loader + Lang.t(" 安装器退出码 ") + code + Lang.t("：") + tail.trim());
        }
        return findInstalledVersion(game, prefix, mc);
    }

    // forge/neoforge 的 maven 路径在 bmcl 镜像上是同一相对路径
    private static String mirrorSha1Url(String installerUrl) {
        String root = bmcl();
        if (installerUrl.contains("maven.minecraftforge.net")) {
            return installerUrl.replace("https://maven.minecraftforge.net", root + "/maven")
                    .replace("http://maven.minecraftforge.net", root + "/maven") + ".sha1";
        }
        if (installerUrl.contains("maven.neoforged.net/releases")) {
            return installerUrl.replace("https://maven.neoforged.net/releases", root + "/maven") + ".sha1";
        }
        return null;
    }

    // 取 maven 旁边的 .sha1 文件；拿不到或格式不对时返回 null
    private static String fetchRemoteSha1(String url) {
        try {
            String text = getText(url + ".sha1");
            Matcher m = Pattern.compile("[0-9a-fA-F]{40}").matcher(text);
            return m.find() ? m.group().toLowerCase(java.util.Locale.ROOT) : null;
        } catch (Exception e) {
            return null;
        }
    }

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
            // 先把地址解析出来，下载走并发池
            List<Object[]> prefetch = new ArrayList<>();
            for (Map<String, Object> lib : libs) {
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
                prefetch.add(new Object[]{url, path, Json.str(artifact, "sha1", "")});
            }
            if (prefetch.isEmpty()) return;
            task.stage = "预下载 " + loader + " 依赖";
            ExecutorService pool = Executors.newFixedThreadPool(
                    Math.max(1, Math.min(8, prefetch.size())), r -> {
                        Thread t = new Thread(r, "TCL-Prefetch");
                        t.setDaemon(true);
                        return t;
                    });
            AtomicInteger done = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (Object[] p : prefetch) {
                    futures.add(pool.submit(() -> {
                        if (task.canceled) return;
                        String path = (String) p[1];
                        Path target = game.resolve("libraries").resolve(path);
                        try {
                            if (!Files.isRegularFile(target) || Files.size(target) <= 0) {
                                // 有 sha1 就让下载器校验，坏文件不会留给安装器
                                String sha1 = (String) p[2];
                                String label = normalizeSha1(sha1) != null ? sha1
                                        : loader + Lang.t(" 依赖 ") + target.getFileName();
                                downloadFile(task, (String) p[0], target, 0.97, 0.99, label, sha1);
                            }
                        } catch (Exception e) {
                            // 预下载失败不影响安装器，安装器会重试
                            Data.get().log("预下载失败：" + path + " " + Log.describe(e));
                        }
                        int n = done.incrementAndGet();
                        task.stage = "预下载 " + loader + " 依赖 " + n + " / " + prefetch.size();
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
        } catch (Exception e) {
            Data.get().log("读取 " + loader + " 安装器清单失败：" + Log.describe(e));
        }
    }

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
                task.stage = "运行 " + loader + " 安装器（" + ((now - start) / 1000) + " 秒，下载依赖较慢请稍候）";
                lastChange = now;
            }
            if (now - start > 20 * 60 * 1000L) {
                killTree(process);
                throw new IOException(loader + Lang.t(" 安装器超过 20 分钟没有结束，请检查网络或改用镜像。日志：") + logFile);
            }
        }
    }

    private static void killTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
        }
        process.destroyForcibly();
    }

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
        String mirror = legacy ? base : bmcl() + "/fabric-meta/v2/versions/loader/" + gameVersion;
        Object list = Json.parse(getText(mirror, base));
        List<Object> loaders = Json.arr(list);
        if (loaders.isEmpty()) throw new IOException("没有可用的 Fabric 加载器");
        String loaderVersion = null;
        if (wantedVersion != null && !wantedVersion.isBlank()) {
            // 选中的版本不在列表里就回退稳定版
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
        if (loaderVersion == null || loaderVersion.isEmpty()) throw new IOException(Lang.t("没有可用的 ") + (legacy ? "Legacy" : "") + Lang.t("Fabric 加载器版本"));
        String profileUrl = base + "/" + loaderVersion + "/profile/json";
        String profileText = getText(mirror + "/" + loaderVersion + "/profile/json", profileUrl);
        Map<String, Object> profile = Json.obj(Json.parse(profileText));
        String id = legacy ? "legacy-fabric-loader-" + loaderVersion + "-" + gameVersion
                : "fabric-loader-" + loaderVersion + "-" + gameVersion;
        profile.put("id", id);
        Path dir = game.resolve("versions").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), toJson(profile), StandardCharsets.UTF_8);
        for (Object libObj : Json.arr(profile.get("libraries"))) {
            Map<String, Object> lib = Json.obj(libObj);
            String name = Json.str(lib, "name", "");
            Map<String, Object> art = Json.obj(Json.obj(lib.get("downloads")).get("artifact"));
            String artifactUrl = Json.str(art, "url", "");
            String rel = Json.str(art, "path", "");
            String libSha1 = Json.str(art, "sha1", "");
            if (rel.isEmpty()) rel = nameToPath(name);
            String url = artifactUrl;
            if (url.isEmpty()) {
                String mavenBase = Json.str(lib, "url", legacy ? "https://maven.legacyfabric.net/" : "https://maven.fabricmc.net/");
                if (!mavenBase.endsWith("/")) mavenBase += "/";
                url = mavenBase + rel;
            }
            downloadFile(task, url, game.resolve("libraries").resolve(rel), 0.95, 0.99, name, libSha1);
        }
        return id;
    }

    private static String installLiteLoader(Tasks.Task task, String gameVersion, Path game, Data data) throws Exception {
        String versionName = gameVersion + "-LiteLoader";
        Path versionDir = game.resolve("versions").resolve(versionName);
        Files.createDirectories(versionDir);
        // 运行库在 dl.liteloader.com 的 maven；1.7.10 仅正式版
        String liteVersion = "1.7.10".equals(gameVersion) ? gameVersion : gameVersion + "-SNAPSHOT";
        String libName = "com.mumfrey:liteloader:" + liteVersion;
        String rel = nameToPath(libName);
        String liteUrl = "https://dl.liteloader.com/versions/" + rel;
        Path liteJar = game.resolve("libraries").resolve(rel);
        Files.createDirectories(liteJar.getParent());
        // jar 落点须匹配坐标路径，否则启动必崩
        downloadFile(task, liteUrl, liteJar, 0.95, 0.99, "LiteLoader " + gameVersion,
                fetchRemoteSha1(liteUrl));
        String json = "{"
                + "\"id\":\"" + versionName + "\","
                + "\"mainClass\":\"net.minecraft.launchwrapper.Launch\","
                + "\"minimumLauncherVersion\":18,"
                + "\"inheritsFrom\":\"" + gameVersion + "\","
                + "\"jar\":\"" + gameVersion + "\","
                + "\"arguments\":{\"game\":[\"--tweakClass\",\"com.mumfrey.liteloader.launch.LiteLoaderTweaker\"]},"
                + "\"libraries\":[{\"name\":\"" + libName + "\","
                + "\"url\":\"https://dl.liteloader.com/versions/\"}]}";
        Files.writeString(versionDir.resolve(versionName + ".json"), json, StandardCharsets.UTF_8);
        return versionName;
    }

    private static String installOptiFine(Tasks.Task task, String gameVersion, Path game, Inst inst, MainWindow win) {
        String chosen = inst == null ? "" : inst.optiFine;
        if (chosen.isEmpty()) {
            Data.get().log("OptiFine 需从官网手动下载安装器；原版文件已就绪。");
            return gameVersion;
        }
        try {
            downloadOptiFineJar(task, gameVersion, game, chosen);
        } catch (Exception e) {
            String msg = "OptiFine " + chosen + Lang.t(" 未安装：")
                    + (e.getMessage() == null ? Lang.t("网络错误") : e.getMessage());
            Data.get().log(msg);
            javafx.application.Platform.runLater(() -> win.toast("附加载器未安装", msg, 3));
        }
        return gameVersion;
    }

    private static String[] optifineUrls(String mc, String chosen) {
        String inherit = mc.replace(".", "");
        String tail = chosen.trim().replace(" ", "/");
        return new String[]{
                bmcl() + "/optifine/" + inherit + "/HD_U_" + tail,
                bmcl() + "/optifine/" + inherit + "/HD_U/" + tail
        };
    }

    private static String downloadOptiFineJar(Tasks.Task task, String mc, Path game, String chosen) throws Exception {
        Map<String, String> files = Loaders.optifineFiles(mc);
        String file = files.getOrDefault(chosen, "");
        if (file.isEmpty()) throw new IOException(Lang.t("找不到 OptiFine ") + chosen + Lang.t(" 的文件名"));
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
        throw new IOException(Lang.t("下载失败：") + (last == null ? Lang.t("网络错误") : last.getMessage()));
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

    // 会话内缓存已校验文件，同一次安装不重复哈希 assets
    private static final java.util.Map<String, Boolean> VERIFIED = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean assetReady(Path file, long expectedSize, String sha1) {
        try {
            if (!Files.isRegularFile(file)) return false;
            long size = Files.size(file);
            if (size <= 0 || (expectedSize > 0 && size != expectedSize)) return false;
            String expect = normalizeSha1(sha1);
            if (expect == null) return true;
            String key = file.toAbsolutePath() + "|" + size + "|" + Files.getLastModifiedTime(file).toMillis();
            if (VERIFIED.containsKey(key)) return true;
            String actual = GameLauncher.sha1Of(file);
            if (!expect.equalsIgnoreCase(actual)) {
                VERIFIED.remove(key);
                return false;
            }
            if (VERIFIED.size() > 20000) VERIFIED.clear();
            VERIFIED.put(key, Boolean.TRUE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final java.util.regex.Pattern HASH_40 =
            java.util.regex.Pattern.compile("[0-9a-f]{40}");
    private static final java.util.regex.Pattern SHA1_40 =
            java.util.regex.Pattern.compile("[0-9a-fA-F]{40}");

    // 只接受 40 位十六进制，其余（label、空串）视为没有哈希可校验
    private static String normalizeSha1(String value) {
        if (value == null) return null;
        String v = value.trim();
        return SHA1_40.matcher(v).matches() ? v.toLowerCase(java.util.Locale.ROOT) : null;
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
        downloadFile(task, url, target, progressFrom, progressTo, label, null);
    }

    // expectedSha1 非空时，已存在的文件也要过一遍校验
    private static void downloadFile(Tasks.Task task, String url, Path target,
                                     double progressFrom, double progressTo, String label,
                                     String expectedSha1) throws IOException {
        String sha1 = normalizeSha1(expectedSha1);
        if (sha1 == null) sha1 = normalizeSha1(label);
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            if (sha1 != null) {
                // 有哈希就一定要对得上，对不上说明文件被换过或是半截文件
                boolean ok;
                try {
                    ok = GameLauncher.sha1Of(target).equalsIgnoreCase(sha1);
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) return;
                Files.deleteIfExists(target);
            } else if (target.getFileName().toString().endsWith(".json")) {
                // JSON 能解析才算完整，否则重下
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
        Exception last = null;
        // 候选源按测速结果排序，别一直命中慢源
        for (String candidate : Sources.rank(mirrors(url))) {
            try {
                Fetch.file(task, candidate, target, sha1, progressFrom, progressTo);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException(Lang.t("下载失败：") + (sha1 != null ? label + "（SHA-1 " + sha1 + "）" : label)
                + (last == null ? "" : "（" + last.getMessage() + "）"));
    }

    private static void downloadTo(Tasks.Task task, String primary, String fallback, Path target,
                                   double progressFrom, double progressTo) throws IOException {
        downloadTo(task, primary, fallback, target, progressFrom, progressTo, null);
    }

    private static void downloadTo(Tasks.Task task, String primary, String fallback, Path target,
                                   double progressFrom, double progressTo, String expectedSha1) throws IOException {
        try {
            downloadFile(task, primary, target, progressFrom, progressTo, target.getFileName().toString(), expectedSha1);
        } catch (IOException e) {
            if (fallback != null && !fallback.isEmpty() && !fallback.equals(primary)) {
                downloadFile(task, fallback, target, progressFrom, progressTo, target.getFileName().toString(), expectedSha1);
            } else {
                throw e;
            }
        }
    }

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

    private static String speed(long bytesPerSec) {
        if (bytesPerSec > 1024 * 1024) return String.format("%.1f M/s", bytesPerSec / 1024.0 / 1024);
        if (bytesPerSec > 1024) return String.format("%.0f K/s", bytesPerSec / 1024.0);
        return bytesPerSec + " B/s";
    }

    public static long speedLimitBytes() {
        String v = Data.get().get("SystemSpeedLimit", "").trim();
        if (v.isEmpty() || !v.matches(".*[0-9.].*")) return 0;
        try {
            double n = Double.parseDouble(v.replaceAll("[^0-9.]", ""));
            if (v.toUpperCase().contains("M")) return (long) (n * 1024 * 1024);
            if (v.toUpperCase().contains("K")) return (long) (n * 1024);
            return (long) (n * 1024);
        } catch (Exception e) {
            return 0;
        }
    }

    // 所有流共用秒级配额，防单流限速被并发放大
    private static final Object LIMIT_LOCK = new Object();
    private static long limitWindowStart;
    private static long limitWindowBytes;

    // bytes 是本次新写入的字节数；limit<=0 不限速，无锁直通
    public static void throttle(int bytes, long limit) {
        if (limit <= 0 || bytes <= 0) return;
        long waitMs = 0;
        synchronized (LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            if (limitWindowStart == 0 || now - limitWindowStart >= 1000) {
                limitWindowStart = now;
                limitWindowBytes = 0;
            }
            limitWindowBytes += bytes;
            if (limitWindowBytes > limit) {
                // 本秒配额用完就睡到下一个窗口，聚合速率≈设置值
                waitMs = Math.max(1, 1000 - (now - limitWindowStart));
            }
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String getText(String... urls) throws IOException {
        Exception last = null;
        for (String url : Sources.rank(java.util.Arrays.asList(urls))) {
            try {
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
}
