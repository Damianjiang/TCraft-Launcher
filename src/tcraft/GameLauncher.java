package tcraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 解析版本 JSON 并拉起 Minecraft。 */
public final class GameLauncher {

    private GameLauncher() {
    }

    /** 系统物理内存 MB，取不到用 8192。 */
    private static long physicalMemoryMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalMemorySize();
            if (total > 0) return Math.max(2048, Math.min(131072, total / 1024 / 1024));
        } catch (Throwable ignored) {
        }
        return 8192;
    }

    /** 启动前校验缺失文件。 */
    public static class MissingFile {
        public final String url;
        public final Path target;
        public final long size;
        public final String desc;

        MissingFile(String url, Path target, long size, String desc) {
            this.url = url;
            this.target = target;
            this.size = size;
            this.desc = desc;
        }
    }

    /** 读版本继承链，最底层版本在前。 */
    private static List<Map<String, Object>> readChain(Path game, String version) throws Exception {
        List<Map<String, Object>> chain = new ArrayList<>();
        String current = version;
        while (current != null && !current.isEmpty()) {
            Path p = game.resolve("versions").resolve(current).resolve(current + ".json");
            if (!Files.isRegularFile(p)) break;
            Map<String, Object> json = Json.obj(Json.parse(Files.readString(p, StandardCharsets.UTF_8)));
            chain.add(0, json);
            current = Json.str(json, "inheritsFrom", "");
        }
        return chain;
    }

    private static Path gameDirOf(Data.Instance instance) {
        Data data = Data.get();
        return Paths.get(instance.folder == null || instance.folder.isEmpty()
                ? data.gameDir : instance.folder);
    }

    /** 启动前校验依赖库、natives、客户端 jar 和资源索引。 */
    public static List<MissingFile> scanMissing(Data.Instance instance) throws Exception {
        Path game = gameDirOf(instance);
        List<MissingFile> out = new ArrayList<>();
        List<Map<String, Object>> chain = readChain(game, instance.version);
        if (chain.isEmpty()) throw new Exception("版本 JSON 无效或不存在：" + instance.version);
        // 依赖库，含旧版 natives。
        List<String> allLibNames = new ArrayList<>();
        for (Map<String, Object> j : chain) {
            for (Object o : Json.arr(j.get("libraries"))) {
                allLibNames.add(Json.str(Json.obj(o), "name", ""));
            }
        }
        for (Map<String, Object> j : chain) {
            for (Object o : Json.arr(j.get("libraries"))) {
                Map<String, Object> lib = Json.obj(o);
                if (!rulesAllow(lib)) continue;
                String libName = Json.str(lib, "name", "");
                if (libName.contains(":natives-") && !nativeArtifactMatches(libName, allLibNames)) continue;
                Map<String, Object> downloads = Json.obj(lib.get("downloads"));
                Map<String, Object> artifact = Json.obj(downloads.get("artifact"));
                String path = Json.str(artifact, "path", "");
                // 只有 classifiers 没有 artifact 的库不处理。
                String url = Json.str(artifact, "url", "");
                if (!path.isEmpty() && !url.isEmpty()
                        && !Files.isRegularFile(game.resolve("libraries").resolve(path))) {
                    out.add(new MissingFile(url, game.resolve("libraries").resolve(path),
                            (long) parseNum(Json.str(artifact, "size", "0"), 0), "依赖库 " + path));
                }
                Map<String, Object> nativesMap = Json.obj(lib.get("natives"));
                if (!nativesMap.isEmpty()) {
                    String classifier = Json.str(nativesMap, osName(), "")
                            .replace("${arch}", is64Bit() ? "64" : "32");
                    if (!classifier.isEmpty()) {
                        Map<String, Object> nat = Json.obj(
                                Json.obj(downloads.get("classifiers")).get(classifier));
                        String rel = Json.str(nat, "path", "");
                        if (!rel.isEmpty() && !Files.isRegularFile(game.resolve("libraries").resolve(rel))) {
                            String nativeUrl = Json.str(nat, "url", "");
                            out.add(new MissingFile(nativeUrl, game.resolve("libraries").resolve(rel),
                                    (long) parseNum(Json.str(nat, "size", "0"), 0), "依赖库 " + rel));
                        }
                    }
                }
            }
        }
        // 客户端 jar 沿继承链找。
        boolean clientOk = false;
        for (Map<String, Object> j : chain) {
            String id = Json.str(j, "id", "");
            if (!id.isEmpty() && Files.isRegularFile(game.resolve("versions").resolve(id).resolve(id + ".jar"))) {
                clientOk = true;
                break;
            }
        }
        if (!clientOk) {
            for (Map<String, Object> j : chain) {
                String id = Json.str(j, "id", instance.version);
                Map<String, Object> client = Json.obj(Json.obj(j.get("downloads")).get("client"));
                String url = Json.str(client, "url", "");
                if (!url.isEmpty()) {
                    out.add(new MissingFile(url, game.resolve("versions").resolve(id).resolve(id + ".jar"),
                            (long) parseNum(Json.str(client, "size", "0"), 0), "客户端 " + id + ".jar"));
                    break;
                }
            }
        }
        // 资源索引
        for (Map<String, Object> j : chain) {
            Map<String, Object> assetIndex = Json.obj(j.get("assetIndex"));
            String id = Json.str(assetIndex, "id", "");
            String url = Json.str(assetIndex, "url", "");
            if (!id.isEmpty() && !url.isEmpty()
                    && !Files.isRegularFile(game.resolve("assets").resolve("indexes").resolve(id + ".json"))) {
                out.add(new MissingFile(url, game.resolve("assets").resolve("indexes").resolve(id + ".json"),
                        (long) parseNum(Json.str(assetIndex, "size", "0"), 0), "资源索引 " + id + ".json"));
                break;
            }
        }
        return out;
    }

    /** 下载缺失文件，进度按文件均分。 */
    public static void downloadMissing(List<MissingFile> files, java.util.function.DoubleConsumer progress)
            throws Exception {
        if (files.isEmpty()) return;
        // 并发补全，串行太慢。
        int threads = Math.max(1, Math.min(8, files.size()));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (MissingFile f : files) {
            futures.add(pool.submit(() -> {
                Downloader.downloadFileTo(null, f.url, f.target, f.desc);
                int n = done.incrementAndGet();
                progress.accept(n / (double) files.size());
                return null;
            }));
        }
        Exception first = null;
        for (java.util.concurrent.Future<?> future : futures) {
            try {
                future.get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (first == null) {
                    first = e.getCause() instanceof Exception ex ? ex : new Exception(e.getCause());
                }
            }
        }
        pool.shutdown();
        if (first != null) throw first;
    }

    public static Process launch(Data.Instance instance) throws Exception {
        return launch(instance, null);
    }

    /** serverIp 不为空就直连该服务器。 */
    public static Process launch(Data.Instance instance, String serverIp) throws Exception {
        Data data = Data.get();
        if (data.javaPath == null || data.javaPath.isEmpty() || !Files.isRegularFile(Paths.get(data.javaPath))) {
            throw new Exception("未找到可用的 Java，请先在设置中选择 Java 路径");
        }
        String javaExe = data.javaPath;
        if (data.getBool("LaunchAdvanceNoJavaw", false) && javaExe.toLowerCase().endsWith("javaw.exe")) {
            Path console = Paths.get(javaExe.substring(0, javaExe.length() - 9) + "java.exe");
            if (Files.isRegularFile(console)) javaExe = console.toString();
        }
        Path game = Paths.get(instance.folder == null || instance.folder.isEmpty()
                ? data.gameDir : instance.folder);
        Path versionDir = game.resolve("versions").resolve(instance.version);
        Path jsonPath = versionDir.resolve(instance.version + ".json");
        if (!Files.isRegularFile(jsonPath)) {
            throw new Exception("未找到版本文件：" + jsonPath);
        }
        List<Map<String, Object>> chain = readChain(game, instance.version);
        if (chain.isEmpty()) throw new Exception("版本 JSON 无效");

        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map<String, Object> j : chain) merged.putAll(j);

        // Java 版本不够时直接报错。
        int requiredJava = 0;
        for (Map<String, Object> j : chain) {
            requiredJava = Math.max(requiredJava,
                    (int) parseNum(Json.str(Json.obj(j.get("javaVersion")), "majorVersion", "0"), 0));
        }
        if (requiredJava > 0) {
            int currentJava = Data.javaMajor(javaExe);
            if (currentJava > 0 && currentJava < requiredJava) {
                throw new Exception("Minecraft " + instance.version + " 需要 Java " + requiredJava
                        + "，当前 Java 为 " + currentJava + "，请在「设置 → 启动设置」中切换 Java");
            }
        }

        // 现代版本的 natives 是普通 jar，直接进 classpath。
        Map<String, Map<String, Object>> libs = new LinkedHashMap<>();
        for (Map<String, Object> j : chain) {
            for (Object o : Json.arr(j.get("libraries"))) {
                Map<String, Object> lib = Json.obj(o);
                if (!rulesAllow(lib)) continue;
                libs.put(Json.str(lib, "name", String.valueOf(libs.size())), lib);
            }
        }
        // 同一个 artifact 有 arm64 natives 时排除非 arm64 的。
        List<String> libNames = new ArrayList<>(libs.keySet());
        List<Path> classpath = new ArrayList<>();
        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(nativesDir);
        List<String> missingLibs = new ArrayList<>();
        List<Path> legacyNatives = new ArrayList<>();
        for (Map<String, Object> lib : libs.values()) {
            String libName = Json.str(lib, "name", "");
            Map<String, Object> downloads = Json.obj(lib.get("downloads"));
            Map<String, Object> artifact = Json.obj(downloads.get("artifact"));
            String path = Json.str(artifact, "path", "");
            String artifactUrl = Json.str(artifact, "url", "");
            boolean nativeArtifact = libName.contains(":natives-");
            if (nativeArtifact && !nativeArtifactMatches(libName, libNames)) continue;
            Map<String, Object> nativesMap = Json.obj(lib.get("natives"));
            // 老版本只有 classifier，Forge 库没有下载元数据。
            if (path.isEmpty() && nativesMap.isEmpty()) path = nameToPath(libName);
            if (!path.isEmpty()) {
                Path jar = game.resolve("libraries").resolve(path);
                if (Files.isRegularFile(jar)) {
                    classpath.add(jar);
                } else if (!artifactUrl.isEmpty()) {
                    missingLibs.add(path);
                }
            }
            // 旧版 natives 解压到 java.library.path。
            if (!nativesMap.isEmpty()) {
                String classifier = Json.str(nativesMap, osName(), "")
                        .replace("${arch}", is64Bit() ? "64" : "32");
                if (!classifier.isEmpty()) {
                    Map<String, Object> classifiers = Json.obj(downloads.get("classifiers"));
                    Map<String, Object> nat = Json.obj(classifiers.get(classifier));
                    String rel = Json.str(nat, "path", "");
                    if (!rel.isEmpty()) {
                        Path jar = game.resolve("libraries").resolve(rel);
                        if (Files.isRegularFile(jar)) legacyNatives.add(jar);
                        else if (!Json.str(nat, "url", "").isEmpty()) missingLibs.add(rel);
                    }
                }
            }
        }
        if (!missingLibs.isEmpty()) {
            throw new Exception("缺少 " + missingLibs.size() + " 个依赖库（重新启动一次将自动补全）："
                    + String.join("、", missingLibs.subList(0, Math.min(3, missingLibs.size()))));
        }

        // 客户端 jar 沿继承链找。
        Path clientJar = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            String id = Json.str(chain.get(i), "id", "");
            Path jar = game.resolve("versions").resolve(id).resolve(id + ".jar");
            if (Files.isRegularFile(jar)) {
                clientJar = jar;
                break;
            }
        }
        if (clientJar == null) throw new Exception("未找到客户端 jar，请先下载该版本");
        classpath.add(clientJar);

        String mainClass = Json.str(merged, "mainClass", "");
        if (mainClass.isEmpty()) throw new Exception("版本 JSON 缺少主类");
        String assetIndex = Json.str(Json.obj(merged.get("assetIndex")), "id", "legacy");
        String assetsDir = prepareAssetsRoot(game, assetIndex).toAbsolutePath().toString();
        String classpathStr = String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList());
        // 把用户指定的 jar 或目录插到 classpath 最前面
        String classpathHead = data.get("LaunchAdvanceClasspathHead", "");
        if (classpathHead != null && !classpathHead.isBlank()) {
            classpathStr = classpathHead.trim() + File.pathSeparator + classpathStr;
        }

        Data.Account account = data.account();
        String username = account == null ? "Player" : account.name;
        String uuid = account == null ? Data.offlineUuid(username) : account.uuid;
        uuid = uuid.replace("-", "");
        String accessToken = account != null && account.accessToken != null
                && !account.accessToken.isEmpty() ? account.accessToken : "0";
        String userType = account != null && account.userType != null && !account.userType.isEmpty()
                ? account.userType : (account != null && account.online ? "msa" : "legacy");
        int ram = Math.max(512, data.getInt("InstanceRam", 0) > 0
                ? data.getInt("InstanceRam", 4096) : data.getInt("LaunchRam", 4096));
        // 自动按物理内存的 1/4 分配。
        if (data.getInt("LaunchRamType", 0) == 0 && data.getInt("InstanceRam", 0) <= 0) {
            long totalMb = physicalMemoryMb();
            ram = (int) Math.max(2048, Math.min(8192, totalMb / 4));
            if (instance.loader != null && !instance.loader.isEmpty() && !"原版".equals(instance.loader)) {
                ram = Math.min(12288, ram + 512);
            }
            ram = (int) (Math.round(ram / 512.0) * 512);
        }
        int initialRam = data.getInt("LaunchRamInitial", 0);

        // 0 全屏 / 1 默认 / 2 同启动器 / 3 自定义 / 4 最大化
        int windowType = data.getInt("LaunchWindowType", 1);
        int winW = (int) parseNum(data.get("LaunchWindowWidth", "854"), 854);
        int winH = (int) parseNum(data.get("LaunchWindowHeight", "480"), 480);
        boolean fullscreen = windowType == 0;
        if (windowType == 2) {
            winW = (int) parseNum(data.get("Window.Width", "854"), 854);
            winH = (int) parseNum(data.get("Window.Height", "600"), 600);
        } else if (windowType == 4) {
            try {
                javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                winW = (int) bounds.getWidth();
                winH = (int) bounds.getHeight();
            } catch (Throwable ignored) {
                winW = 1920;
                winH = 1080;
            }
        }
        boolean customResolution = (windowType == 2 || windowType == 3 || windowType == 4)
                && winW > 0 && winH > 0;

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("auth_player_name", username);
        vars.put("version_name", instance.version);
        vars.put("game_directory", game.toAbsolutePath().toString());
        vars.put("assets_root", assetsDir);
        vars.put("game_assets", assetsDir);
        vars.put("assets_index_name", assetIndex);
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", accessToken);
        vars.put("auth_session", "token:" + accessToken + ":" + uuid);
        vars.put("user_type", userType);
        vars.put("version_type", "release");
        vars.put("user_properties", "{}");
        // 这些变量不补会以字面量进游戏参数。
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("natives_directory", nativesDir.toAbsolutePath().toString());
        vars.put("launcher_name", "TCraftLauncher");
        vars.put("launcher_version", "1.3.7");
        vars.put("classpath", classpathStr);
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", game.resolve("libraries").toAbsolutePath().toString());
        vars.put("resolution_width", String.valueOf(Math.max(1, winW)));
        vars.put("resolution_height", String.valueOf(Math.max(1, winH)));
        vars.put("quickPlayPath", game.resolve("TCL").resolve("quickplay.log").toAbsolutePath().toString());
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");

        // 现代版本用 features 控制可选参数。
        Map<String, Boolean> features = new java.util.HashMap<>();
        features.put("is_demo_user", false);
        features.put("has_custom_resolution", customResolution);
        features.put("has_quick_plays_support", false);
        features.put("is_quick_play_singleplayer", false);
        features.put("is_quick_play_multiplayer", false);
        features.put("is_quick_play_realms", false);

        boolean lockMemory = data.getBool("LaunchAdvanceLockMemory", false);
        int xms = lockMemory ? ram : (initialRam > 0 ? Math.min(initialRam, ram) : Math.max(256, ram / 4));
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-Xms" + xms + "M");
        cmd.add("-Dfile.encoding=UTF-8");
        // 0 ZGC / 1 分代 ZGC / 2 G1 / 3 不指定 / 4 调优 G1
        int javaMajor = Data.javaMajor(javaExe);
        switch (data.getInt("LaunchAdvanceGC", 4)) {
            case 0 -> {
                if (javaMajor >= 15) cmd.add("-XX:+UseZGC");
                else cmd.add("-XX:+UseG1GC");
            }
            case 1 -> {
                if (javaMajor >= 21) {
                    cmd.add("-XX:+UseZGC");
                    cmd.add("-XX:+ZGenerational");
                } else {
                    cmd.add("-XX:+UseG1GC");
                }
            }
            case 2 -> cmd.add("-XX:+UseG1GC");
            case 3 -> { /* 交给用户自己写 */ }
            default -> {
                cmd.add("-XX:+UseG1GC");
                cmd.add("-XX:+UnlockExperimentalVMOptions");
                cmd.add("-XX:G1NewSizePercent=20");
                cmd.add("-XX:G1ReservePercent=20");
                cmd.add("-XX:MaxGCPauseMillis=50");
                cmd.add("-XX:G1HeapRegionSize=32M");
            }
        }
        // 0 IPv4 优先 / 1 Java 默认 / 2 IPv6 优先
        switch (data.getInt("LaunchIpStack", 1)) {
            case 0 -> cmd.add("-Djava.net.preferIPv4Stack=true");
            case 2 -> {
                cmd.add("-Djava.net.preferIPv6Addresses=true");
                cmd.add("-Djava.net.preferIPv6Stack=true");
            }
            default -> { }
        }
        // 把当前代理同步给游戏 JVM。
        if (data.getBool("SystemHttpProxyForGame", false)) {
            int proxyMode = data.getInt("SystemHttpProxyType", 1);
            String host = data.get("SystemProxyHost", "");
            String port = data.get("SystemProxyPort", "7890");
            if (!host.isEmpty() && (proxyMode == 2 || proxyMode == 3)) {
                if (proxyMode == 3) {
                    cmd.add("-DsocksProxyHost=" + host);
                    cmd.add("-DsocksProxyPort=" + port);
                } else {
                    cmd.add("-Dhttp.proxyHost=" + host);
                    cmd.add("-Dhttp.proxyPort=" + port);
                    cmd.add("-Dhttps.proxyHost=" + host);
                    cmd.add("-Dhttps.proxyPort=" + port);
                }
            }
        }
        String brand = data.get("LaunchCustomInfo", "TCL");
        if (brand == null || brand.isBlank()) brand = "TCraftLauncher";
        cmd.add("-Dminecraft.launcher.brand=" + subst(brand, vars));
        cmd.add("-Dminecraft.launcher.version=1.3.7");

        // 第三方登录注入。
        if (account != null && "nide".equals(account.userType) && account.server != null && !account.server.isEmpty()) {
            Path nideJar = Paths.get(data.gameDir, "TCL", "nide8auth.jar");
            ensureNideAgentJar(nideJar, account.server);
            cmd.add("-javaagent:" + nideJar.toAbsolutePath() + "=" + account.server);
        } else if (account != null && "legacy".equals(account.userType)
                && account.server != null && !account.server.isEmpty()) {
            Path authJar = Paths.get(data.gameDir, "TCL", "authlib-injector.jar");
            ensureAuthlibInjectorJar(authJar);
            cmd.add("-Dauthlibinjector.side=client"); // 参数顺序跟 nide8auth 一致
            cmd.add("-javaagent:" + authJar.toAbsolutePath() + "=" + account.server);
        }
        String customTitle = data.get("InstanceWindowTitle", data.get("LaunchWindowTitle", ""));
        if (!customTitle.isEmpty()) cmd.add("-Dminecraft.window.title=" + subst(customTitle, vars));

        List<Object> jvmArgs = new ArrayList<>();
        for (Map<String, Object> j : chain) {
            // arguments 是对象，不是数组。
            Object arguments = j.get("arguments");
            if (arguments instanceof Map<?, ?> argsMap) {
                jvmArgs.addAll(Json.arr(Json.obj(argsMap).get("jvm")));
            }
        }
        // 新版 java.library.path 指向 natives 子目录。
        Path javaNativeFolder = nativesDir;
        for (Object o : jvmArgs) {
            String value = o instanceof String s ? s : null;
            if (value == null && o instanceof Map<?, ?> m && Json.obj(m).get("value") instanceof String s2) value = s2;
            if (value == null || !value.startsWith("-Djava.library.path=")) continue;
            String p = value.substring("-Djava.library.path=".length()).replace("${natives_directory}", "");
            if (p.startsWith("/") || p.startsWith("\\")) {
                Path candidate = nativesDir.resolve(p.substring(1)).normalize();
                if (candidate.startsWith(nativesDir)) javaNativeFolder = candidate;
            }
            break;
        }
        if (!legacyNatives.isEmpty()) {
            cleanDir(javaNativeFolder);
            for (Path jar : legacyNatives) extractNatives(jar, javaNativeFolder);
        } else {
            Files.createDirectories(javaNativeFolder);
        }
        if (jvmArgs.isEmpty()) {
            cmd.add("-Djava.library.path=" + javaNativeFolder.toAbsolutePath());
            cmd.add("-cp");
            cmd.add(classpathStr);
        } else {
            for (String arg : resolveArgs(jvmArgs, vars, features)) cmd.add(arg);
        }
        String extraJvm = data.get("InstanceJvmArgs", data.get("LaunchJvmArgs", ""));
        if (!extraJvm.isEmpty()) {
            for (String a : extraJvm.trim().split("\\s+")) if (!a.isEmpty()) cmd.add(subst(a, vars));
        }
        cmd.add(mainClass);

        String legacy = Json.str(merged, "minecraftArguments", "");
        List<Object> gameArgs = new ArrayList<>();
        for (Map<String, Object> j : chain) {
            List<Object> ga = Json.arr(Json.obj(j.get("arguments")).get("game"));
            gameArgs.addAll(ga);
        }
        if (!legacy.isEmpty()) {
            // 旧版本没有 feature 参数，尺寸自己补。
            if (customResolution) {
                cmd.add("--width");
                cmd.add(String.valueOf(winW));
                cmd.add("--height");
                cmd.add(String.valueOf(winH));
            }
            for (String a : legacy.trim().split("\\s+")) cmd.add(subst(a, vars));
        } else {
            cmd.addAll(resolveArgs(gameArgs, vars, features));
        }
        if (fullscreen) cmd.add("--fullscreen"); // 全屏
        String extraGame = data.get("LaunchGameArgs", "");
        if (!extraGame.isEmpty()) {
            for (String a : extraGame.trim().split("\\s+")) if (!a.isEmpty()) cmd.add(subst(a, vars));
        }
        if (serverIp != null && !serverIp.trim().isEmpty()) {
            // 直连服务器。
            String[] parts = serverIp.trim().split(":");
            cmd.add("--server");
            cmd.add(parts[0]);
            if (parts.length == 2) {
                cmd.add("--port");
                cmd.add(parts[1]);
            }
            data.launchLog("直连服务器：" + serverIp);
        }

        String pre = data.get("LaunchPreCommand", "");
        if (!pre.isEmpty()) {
            try {
                boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
                String shell = win ? "cmd.exe" : "/bin/sh";
                String arg = win ? "/c" : "-c";
                Process preProcess = new ProcessBuilder(shell, arg, pre).directory(game.toFile())
                        .redirectErrorStream(true).start();
                if (data.getBool("LaunchPreCommandWait", true)) {
                    preProcess.waitFor();
                }
                data.launchLog("已执行启动前命令：" + pre);
            } catch (Exception e) {
                data.launchLog("启动前命令执行失败：" + e.getMessage());
            }
        }

        // 启动前先回收启动器内存。
        if (data.getBool("LaunchOptimizeRam", true)) {
            System.gc();
            try {
                Thread.sleep(80);
            } catch (InterruptedException ignored) {
            }
        }
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(game.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        data.launchLog("已启动游戏进程：" + String.join(" ", cmd));
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    data.launchLog("[MC] " + line);
                }
            } catch (Exception ignored) {
            }
        }, "TCL-GameLog");
        reader.setDaemon(true);
        reader.start();
        return process;
    }

    static boolean rulesAllow(Map<String, Object> item) {
        return rulesAllow(item, java.util.Collections.emptyMap());
    }

    /** 规则判定：os、arch、features 都要匹配。 */
    private static boolean rulesAllow(Map<String, Object> item, Map<String, Boolean> features) {
        List<Object> rules = Json.arr(item.get("rules"));
        if (rules.isEmpty()) return true;
        boolean allow = false;
        for (Object o : rules) {
            Map<String, Object> rule = Json.obj(o);
            Map<String, Object> os = Json.obj(rule.get("os"));
            String name = Json.str(os, "name", "");
            String arch = Json.str(os, "arch", "");
            boolean match = (name.isEmpty() || name.equals(osName()))
                    && (arch.isEmpty() || arch.equals(osArch()));
            Map<String, Object> ruleFeatures = Json.obj(rule.get("features"));
            if (match && !ruleFeatures.isEmpty()) {
                for (Map.Entry<String, Object> fe : ruleFeatures.entrySet()) {
                    boolean want = Boolean.parseBoolean(String.valueOf(fe.getValue()));
                    boolean actual = Boolean.TRUE.equals(features.get(fe.getKey()));
                    if (want != actual) {
                        match = false;
                        break;
                    }
                }
            }
            if (match) allow = "allow".equals(Json.str(rule, "action", "allow"));
        }
        return allow;
    }

    /** Mojang 版本清单里的系统名：windows / osx / linux。 */
    private static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static String osArch() {
        String a = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) return "arm64";
        if (a.contains("64")) return "x86_64";
        return "x86";
    }

    private static boolean is64Bit() {
        return !"x86".equals(osArch());
    }

    private static List<String> resolveArgs(List<Object> list, Map<String, String> vars) {
        return resolveArgs(list, vars, java.util.Collections.emptyMap());
    }

    private static List<String> resolveArgs(List<Object> list, Map<String, String> vars,
                                            Map<String, Boolean> features) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String s) {
                out.add(subst(s, vars));
            } else if (o instanceof Map<?, ?> map) {
                Map<String, Object> arg = Json.obj(map);
                if (!rulesAllow(arg, features)) continue;
                Object value = arg.get("value");
                if (value instanceof String s) out.add(subst(s, vars));
                else if (value instanceof List<?> values) {
                    for (Object v : values) if (v != null) out.add(subst(String.valueOf(v), vars));
                }
            }
        }
        return out;
    }

    /** 判断 natives 是否匹配当前平台和架构。 */
    static boolean nativeArtifactMatches(String libName, List<String> allNames) {
        int i = libName.indexOf(":natives-");
        if (i < 0) return true;
        String tail = libName.substring(i + ":natives-".length()).toLowerCase(java.util.Locale.ROOT);
        boolean arm = osArch().equals("arm64");
        boolean variantArm = tail.endsWith("-arm64") || tail.endsWith("arm64");
        boolean winOs = osName().equals("windows");
        boolean macOs = osName().equals("osx");
        boolean linOs = osName().equals("linux");
        boolean winVariant = tail.startsWith("windows");
        boolean macVariant = tail.startsWith("macos") || tail.startsWith("osx");
        boolean linVariant = tail.startsWith("linux");
        if (!((winOs && winVariant) || (macOs && macVariant) || (linOs && linVariant))) return false;
        if (arm) {
            // 同一个 artifact 有 arm64 变体就排除其他变体
            String key = libName.substring(0, i);
            if (!variantArm) {
                for (String other : allNames) {
                    if (!other.startsWith(key + ":")) continue;
                    String ot = other.substring(other.indexOf(":natives-") + ":natives-".length())
                            .toLowerCase(java.util.Locale.ROOT);
                    if (ot.endsWith("-arm64") || ot.endsWith("arm64")) return false;
                }
            }
        } else if (variantArm) {
            return false;
        }
        return true;
    }

    private static void cleanDir(Path dir) {
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 旧版资源索引要复制成目录结构。 */
    private static Path prepareAssetsRoot(Path game, String assetId) {
        Path assets = game.resolve("assets");
        Path indexFile = assets.resolve("indexes").resolve(assetId + ".json");
        if (!Files.isRegularFile(indexFile)) return assets;
        try {
            Map<String, Object> index = Json.obj(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
            boolean virtual = Json.bool(index, "virtual", false);
            boolean mapToResources = Json.bool(index, "map_to_resources", false);
            if (!virtual && !mapToResources) return assets;
            Path target = mapToResources ? game.resolve("resources") : assets.resolve("virtual").resolve(assetId);
            Map<String, Object> objects = Json.obj(index, "objects");
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                Map<String, Object> obj = Json.obj(e.getValue());
                String hash = Json.str(obj, "hash", "");
                if (hash.length() < 2) continue;
                Path source = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                Path dest = target.resolve(e.getKey());
                if (!Files.isRegularFile(source) || Files.isRegularFile(dest)) continue;
                Files.createDirectories(dest.getParent());
                Files.copy(source, dest);
            }
            return target;
        } catch (Exception e) {
            return assets;
        }
    }

    private static String subst(String s, Map<String, String> vars) {
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static String nameToPath(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace(':', '/');
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static double parseNum(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** natives 解压失败要往上抛。 */
    private static void extractNatives(Path jar, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\")) continue;
                if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib")) continue;
                Path out = dir.resolve(name).normalize();
                if (!out.startsWith(dir)) throw new Exception("natives 压缩包含非法路径：" + name);
                Files.copy(zip, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** 准备 nide8auth.jar，按 jarHash 校验。 */
    private static void ensureNideAgentJar(Path jar, String serverCode) throws Exception {
        Tasks.Task probe = new Tasks.Task("t");
        String jarHash = "";
        try {
            String text = Downloader.getText("https://auth.mc-user.com:233/" + serverCode);
            jarHash = Json.str(Json.obj(Json.parse(text)), "jarHash", "").toLowerCase();
        } catch (Exception e) {
            if (Files.isRegularFile(jar)) {
                Data.get().log("获取统一通行证下载信息失败，使用本地 jar：" + e.getMessage());
                return;
            }
            throw new Exception("获取统一通行证下载信息失败：" + e.getMessage());
        }
        if (Files.isRegularFile(jar) && sha1Of(jar).equalsIgnoreCase(jarHash)) return;
        try {
            Modrinth.downloadUrl(probe, "https://login.mc-user.com:233/index/jar", jar, jarHash);
        } catch (Exception e) {
            if (Files.isRegularFile(jar)) {
                Data.get().log("统一通行证 jar 下载失败，使用本地旧版本：" + e.getMessage());
                return;
            }
            throw new Exception("下载统一通行证运行时失败：" + e.getMessage());
        }
    }

    /** 准备 authlib-injector.jar，失败回落 BMCLAPI。 */
    private static void ensureAuthlibInjectorJar(Path jar) throws Exception {
        Tasks.Task probe = new Tasks.Task("t");
        String text = Downloader.getText(
                "https://authlib-injector.yushi.moe/artifact/latest.json",
                "https://bmclapi2.bangbang93.com/mirrors/authlib-injector/artifact/latest.json");
        java.util.Map<String, Object> artifact = Json.obj(Json.obj(Json.parse(text)).get("artifact"));
        String sha1 = Json.str(Json.obj(artifact.get("checksums")), "sha1", "").toLowerCase();
        String url = Json.str(artifact, "downloadUrl", "");
        if (Files.isRegularFile(jar) && sha1.isEmpty()) return;
        if (Files.isRegularFile(jar) && sha1Of(jar).equalsIgnoreCase(sha1)) return;
        if (url.isEmpty()) {
            if (Files.isRegularFile(jar)) return;
            throw new Exception("获取 authlib-injector 下载地址失败");
        }
        Modrinth.downloadUrl(probe, url, jar, sha1);
    }

    static String sha1Of(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        try (java.io.InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
