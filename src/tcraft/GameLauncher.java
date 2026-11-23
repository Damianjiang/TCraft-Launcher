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

public final class GameLauncher {

    private GameLauncher() {
    }

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

    public static class MissingFile {
        public final String url;
        public final Path target;
        public final long size;
        public final String desc;
        public final String sha1;

        MissingFile(String url, Path target, long size, String desc) {
            this(url, target, size, desc, "");
        }

        MissingFile(String url, Path target, long size, String desc, String sha1) {
            this.url = url;
            this.target = target;
            this.size = size;
            this.desc = desc;
            this.sha1 = sha1 == null ? "" : sha1;
        }
    }

    private static List<Map<String, Object>> readChain(Path game, String version) throws Exception {
        List<Map<String, Object>> chain = new ArrayList<>();
        String current = version;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (current != null && !current.isEmpty()) {
            // 版本 ID 直接拿来拼路径 带分隔符或 .. 的都当伪造版本处理
            if (current.contains("\\") || current.contains("/") || current.contains("..")) {
                throw new Exception(Lang.t("版本 ID 含非法字符：") + current);
            }
            if (!visited.add(current) || visited.size() > 16) {
                throw new Exception(Lang.t("版本继承链存在循环或过深：") + current);
            }
            Path p = game.resolve("versions").resolve(current).resolve(current + ".json");
            if (!Files.isRegularFile(p)) break;
            Map<String, Object> json = Json.obj(Json.parse(Files.readString(p, StandardCharsets.UTF_8)));
            chain.add(0, json);
            current = Json.str(json, "inheritsFrom", "");
        }
        return chain;
    }

    // 无 javaVersion 时按版本猜 Java
    private static int guessJava(String version) {
        String v = version == null ? "" : version.toLowerCase(java.util.Locale.ROOT).trim();
        try {
            if (v.startsWith("1.")) {
                String[] p = v.split("[^0-9]+");
                int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                if (minor <= 16) return 8;
                if (minor == 20) {
                    int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
                    return patch >= 5 ? 21 : 17;
                }
                return minor >= 21 ? 21 : 17;
            }
            String[] p = v.split("[^0-9]+");
            int major = p.length > 0 && !p[0].isEmpty() ? Integer.parseInt(p[0]) : 0;
            if (major >= 24) return 25;
        } catch (Exception ignored) {
        }
        return 17;
    }

    // 读 JSON 的 Java 主版本 无则 0
    public static int requiredJava(Data.Instance instance) throws Exception {
        List<Map<String, Object>> chain = readChain(gameDirOf(instance), instance.version);
        int required = 0;
        for (Map<String, Object> j : chain) {
            required = Math.max(required, (int) parseNum(
                    Json.str(Json.obj(j.get("javaVersion")), "majorVersion", "0"), 0));
        }
        return required;
    }

    // 组件代号 8=legacy 17=gamma 21=delta 25=epsilon
    private static String componentFor(int required) {
        if (required <= 8) return "jre-legacy";
        if (required <= 17) return "java-runtime-gamma";
        if (required <= 21) return "java-runtime-delta";
        return "java-runtime-epsilon";
    }

    private static String findJava(int required, boolean exactOnly) {
        List<Data.JavaRuntime> list = Data.get().scanJavaRuntimes();
        String exact = "";
        String nearest = "";
        int nearestMajor = Integer.MAX_VALUE;
        for (Data.JavaRuntime rt : list) {
            if (!Data.javaIs64(rt.path())) continue;
            if (rt.major() == required && exact.isEmpty()) exact = rt.path();
            if (rt.major() >= required && rt.major() < nearestMajor) {
                nearest = rt.path();
                nearestMajor = rt.major();
            }
        }
        if (!exact.isEmpty()) return exact;
        if (exactOnly || required <= 8) return "";
        return nearest;
    }

    // 实例指定 > 同版 > 更高版 > 下载
    public static String resolveJava(Data.Instance instance, java.util.function.Consumer<String> status,
                                     java.util.function.BooleanSupplier cancelled,
                                     java.util.function.DoubleConsumer progress) throws Exception {
        Data data = Data.get();
        int required = requiredJava(instance);
        if (required <= 0) required = guessJava(instance.version);

        String explicit = data.getInstance("Java", "");
        if (explicit != null && !explicit.isBlank() && !"@auto".equals(explicit)) {
            try {
                int m = Files.isRegularFile(Paths.get(explicit)) ? Data.javaMajor(explicit) : 0;
                if (m >= required && Data.javaIs64(explicit)) return explicit;
                if (m > 0 && status != null) {
                    status.accept(Lang.tf("实例指定的 Java %d 低于版本要求 %d，自动更换", m, required));
                }
            } catch (Exception e) {
                data.log("实例指定的 Java 探测失败，改为自动选择：" + Log.describe(e));
            }
        }

        String found = findJava(required, true);
        if (found.isEmpty()) found = findJava(required, false);
        if (!found.isEmpty()) return found;

        String component = componentFor(required);
        if (status != null) status.accept(Lang.tf("未找到 Java %d，正在自动下载", required));
        Path exe = JavaInstaller.install(component, status, cancelled, progress);
        int major = Data.javaMajor(exe.toString());
        if (major > 0 && major < required) {
            throw new Exception(Lang.tf("下载的 Java %d 仍低于版本要求的 %d", major, required));
        }
        data.invalidateJavaScan();
        return exe.toString();
    }

    private static Path gameDirOf(Data.Instance instance) {
        return Isolation.base(instance);
    }

    public static Path runDirOf(Data.Instance instance) {
        return Isolation.run(instance);
    }

    public static List<MissingFile> scanMissing(Data.Instance instance) throws Exception {
        Path game = gameDirOf(instance);
        // 按 target 去重
        Map<Path, MissingFile> out = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> chain = readChain(game, instance.version);
        if (chain.isEmpty()) throw new Exception("版本 JSON 无效或不存在：" + instance.version);
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
                String url = Json.str(artifact, "url", "");
                if (path.isEmpty()) {
                    // 旧式库按 maven 拼路径
                    String base = Json.str(lib, "url", "");
                    if (!base.isEmpty() && !libName.isEmpty()) {
                        if (!base.endsWith("/")) base += "/";
                        path = nameToPath(libName);
                        url = base + path;
                    }
                }
                Path libFile = Downloader.libraryPath(game, path);
                if (libFile != null && !url.isEmpty() && !Files.isRegularFile(libFile)) {
                    out.putIfAbsent(libFile.toAbsolutePath().normalize(),
                            new MissingFile(url, libFile,
                                    (long) parseNum(Json.str(artifact, "size", "0"), 0), "依赖库 " + path,
                                    Json.str(artifact, "sha1", "")));
                }
                Map<String, Object> nativesMap = Json.obj(lib.get("natives"));
                if (!nativesMap.isEmpty()) {
                    String classifier = Json.str(nativesMap, osName(), "")
                            .replace("${arch}", is64Bit() ? "64" : "32");
                    if (!classifier.isEmpty()) {
                        Map<String, Object> nat = Json.obj(
                                Json.obj(downloads.get("classifiers")).get(classifier));
                        String rel = Json.str(nat, "path", "");
                        Path nativeFile = Downloader.libraryPath(game, rel);
                        if (nativeFile != null && !Files.isRegularFile(nativeFile)) {
                            String nativeUrl = Json.str(nat, "url", "");
                            out.putIfAbsent(nativeFile.toAbsolutePath().normalize(),
                                    new MissingFile(nativeUrl, nativeFile,
                                            (long) parseNum(Json.str(nat, "size", "0"), 0), "依赖库 " + rel,
                                            Json.str(nat, "sha1", "")));
                        }
                    }
                }
            }
        }
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
                    out.putIfAbsent(game.resolve("versions").resolve(id).resolve(id + ".jar")
                                    .toAbsolutePath().normalize(),
                            new MissingFile(url, game.resolve("versions").resolve(id).resolve(id + ".jar"),
                                    (long) parseNum(Json.str(client, "size", "0"), 0), "客户端 " + id + ".jar",
                                    Json.str(client, "sha1", "")));
                    break;
                }
            }
        }
        for (Map<String, Object> j : chain) {
            Map<String, Object> assetIndex = Json.obj(j.get("assetIndex"));
            String id = Json.str(assetIndex, "id", "");
            String url = Json.str(assetIndex, "url", "");
            if (!id.isEmpty() && !url.isEmpty()
                    && !Files.isRegularFile(game.resolve("assets").resolve("indexes").resolve(id + ".json"))) {
                out.putIfAbsent(game.resolve("assets").resolve("indexes").resolve(id + ".json")
                                .toAbsolutePath().normalize(),
                        new MissingFile(url, game.resolve("assets").resolve("indexes").resolve(id + ".json"),
                                (long) parseNum(Json.str(assetIndex, "size", "0"), 0), "资源索引 " + id + ".json",
                                Json.str(assetIndex, "sha1", "")));
                break;
            }
        }
        return new ArrayList<>(out.values());
    }

    public static void downloadMissing(List<MissingFile> files, java.util.function.DoubleConsumer progress)
            throws Exception {
        downloadMissing(files, progress, () -> false);
    }

    public static void downloadMissing(List<MissingFile> files, java.util.function.DoubleConsumer progress,
                                       java.util.function.BooleanSupplier cancelled) throws Exception {
        if (files.isEmpty()) return;
        int threads = Math.max(1, Math.min(8, files.size()));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "TCL-MissingFile");
            t.setDaemon(true);
            return t;
        });
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        Exception first = null;
        try {
            for (MissingFile f : files) {
                futures.add(pool.submit(() -> {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("已取消");
                    Downloader.downloadFileTo(null, f.url, f.target, f.desc, f.sha1);
                    int n = done.incrementAndGet();
                    progress.accept(n / (double) files.size());
                    return null;
                }));
            }
            boolean canceled = false;
            for (java.util.concurrent.Future<?> future : futures) {
                if (!canceled && cancelled.getAsBoolean()) {
                    canceled = true;
                    pool.shutdownNow();
                }
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    if (!canceled && cancelled.getAsBoolean()) {
                        canceled = true;
                        pool.shutdownNow();
                    }
                    if (first == null) {
                        // 异常类型区分取消
                        first = canceled
                                ? new java.util.concurrent.CancellationException("已取消")
                                : e.getCause() instanceof Exception ex ? ex : new Exception(e.getCause());
                    }
                } catch (java.util.concurrent.CancellationException e) {
                    if (first == null) first = e;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        if (first != null) throw first;
    }

    public static Process launch(Data.Instance instance) throws Exception {
        return launch(instance, null);
    }

    public static Process launch(Data.Instance instance, String serverIp) throws Exception {
        return launch(instance, serverIp, resolveJava(instance, null, null, null));
    }

    // ≤1.12 options.txt 用 ANSI
    static java.nio.charset.Charset optionsCharset(Path options) {
        try {
            byte[] raw = Files.readAllBytes(options);
            try {
                java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(raw));
                return java.nio.charset.StandardCharsets.UTF_8;
            } catch (java.nio.charset.CharacterCodingException e) {
                // native.encoding 对齐旧版 JVM
                try {
                    return java.nio.charset.Charset.forName(System.getProperty("native.encoding",
                            System.getProperty("sun.jnu.encoding", "UTF-8")));
                } catch (Exception e2) {
                    return java.nio.charset.Charset.defaultCharset();
                }
            }
        } catch (Exception e) {
            return java.nio.charset.StandardCharsets.UTF_8;
        }
    }

    private static void applyGameLanguage(Path run) {
        if (!Data.get().getBool("ToolHelpChinese", true)) return;
        Path options = run.resolve("options.txt");
        if (!Files.isRegularFile(options)) return;
        try {
            java.nio.charset.Charset cs = optionsCharset(options);
            List<String> lines = Files.readAllLines(options, cs);
            boolean changed = false;
            boolean hasLang = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("lang:")) {
                    hasLang = true;
                    String value = line.substring(5).trim();
                    if (value.isEmpty() || "en_us".equals(value) || "en_US".equals(value)) {
                        lines.set(i, "lang:zh_cn");
                        changed = true;
                    }
                }
            }
            if (!hasLang) {
                lines.add("lang:zh_cn");
                changed = true;
            }
            if (changed) Files.write(options, lines, cs);
        } catch (Exception ignored) {
        }
    }

    public static Process launch(Data.Instance instance, String serverIp, String resolvedJava) throws Exception {
        Data data = Data.get();
        String javaExe = resolvedJava;
        if (javaExe == null || javaExe.isEmpty() || !Files.isRegularFile(Paths.get(javaExe))) {
            throw new Exception("未找到可用的 Java，自动下载 Java 也没有成功");
        }
        boolean showConsole = data.getInstanceBool("Console", false)
                || data.getBool("LaunchAdvanceNoJavaw", false);
        if (showConsole && javaExe.toLowerCase().endsWith("javaw.exe")) {
            Path console = Paths.get(javaExe.substring(0, javaExe.length() - 9) + "java.exe");
            if (Files.isRegularFile(console)) javaExe = console.toString();
        } else if (!showConsole && javaExe.toLowerCase().endsWith("java.exe")) {
            Path w = Paths.get(javaExe.substring(0, javaExe.length() - 8) + "javaw.exe");
            if (Files.isRegularFile(w)) javaExe = w.toString();
        }
        Path game = gameDirOf(instance);
        Path run = runDirOf(instance);
        try {
            Files.createDirectories(run);
        } catch (Exception e) {
            throw new Exception(Lang.t("无法创建游戏目录：") + run, e);
        }
        Path versionDir = game.resolve("versions").resolve(instance.version);
        applyGameLanguage(run);
        Path jsonPath = versionDir.resolve(instance.version + ".json");
        if (!Files.isRegularFile(jsonPath)) {
            throw new Exception("未找到版本文件：" + jsonPath);
        }
        List<Map<String, Object>> chain = readChain(game, instance.version);
        if (chain.isEmpty()) throw new Exception("版本 JSON 无效");

        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map<String, Object> j : chain) merged.putAll(j);

        int requiredJava = 0;
        for (Map<String, Object> j : chain) {
            requiredJava = Math.max(requiredJava,
                    (int) parseNum(Json.str(Json.obj(j.get("javaVersion")), "majorVersion", "0"), 0));
        }
        if (requiredJava > 0) {
            int currentJava = Data.javaMajor(javaExe);
            if (currentJava > 0 && currentJava < requiredJava) {
                throw new Exception("Minecraft " + instance.version + " 需要 Java " + requiredJava
                        + "，自动选择的 Java 为 " + currentJava);
            }
        }

        Map<String, Map<String, Object>> libs = new LinkedHashMap<>();
        for (Map<String, Object> j : chain) {
            for (Object o : Json.arr(j.get("libraries"))) {
                Map<String, Object> lib = Json.obj(o);
                if (!rulesAllow(lib)) continue;
                libs.put(Json.str(lib, "name", String.valueOf(libs.size())), lib);
            }
        }
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
            if (path.isEmpty() && nativesMap.isEmpty()) path = nameToPath(libName);
            if (!path.isEmpty()) {
                Path jar = Downloader.libraryPath(game, path);
                if (jar != null && Files.isRegularFile(jar)) {
                    classpath.add(jar);
                } else if (!artifactUrl.isEmpty() || !Json.str(lib, "url", "").isEmpty()) {
                    missingLibs.add(path);
                }
            }
            // 旧版 natives 解压到 java.library.path
            if (!nativesMap.isEmpty()) {
                String classifier = Json.str(nativesMap, osName(), "")
                        .replace("${arch}", is64Bit() ? "64" : "32");
                if (!classifier.isEmpty()) {
                    Map<String, Object> classifiers = Json.obj(downloads.get("classifiers"));
                    Map<String, Object> nat = Json.obj(classifiers.get(classifier));
                    String rel = Json.str(nat, "path", "");
                    Path jar = Downloader.libraryPath(game, rel);
                    if (jar != null && Files.isRegularFile(jar)) legacyNatives.add(jar);
                    else if (jar != null && !Json.str(nat, "url", "").isEmpty()) missingLibs.add(rel);
                }
            }
        }
        if (!missingLibs.isEmpty()) {
            throw new Exception(Lang.t("缺少 ") + missingLibs.size()
                    + Lang.t(" 个依赖库（重新启动一次将自动补全）：")
                    + String.join("、", missingLibs.subList(0, Math.min(3, missingLibs.size()))));
        }

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
        // 用户 jar/目录插 classpath 首部
        String classpathHead = data.getInstance("ClasspathHead",
                data.get("LaunchAdvanceClasspathHead", ""));
        if (classpathHead != null && !classpathHead.isBlank()) {
            classpathStr = classpathHead.trim() + File.pathSeparator + classpathStr;
        }

        Data.Account account = data.account();
        if (account != null && "msa".equals(account.userType)) {
            boolean overdue = account.expiresAt == 0 || System.currentTimeMillis() > account.expiresAt;
            boolean valid = true;
            if (overdue) {
                try {
                    valid = MsAuth.validate(account.accessToken);
                } catch (Exception e) {
                    // 校验失败不算有效
                    valid = false;
                }
            }
            if (!valid) {
                try {
                    MsAuth.Result r = MsAuth.refresh(account.refreshToken);
                    account.accessToken = r.accessToken;
                    if (!r.refreshToken.isEmpty()) account.refreshToken = r.refreshToken;
                    account.expiresAt = r.expiresAt;
                    data.save();
                } catch (Exception e) {
                    throw new Exception(Lang.t("微软登录已失效：") + e.getMessage(), e);
                }
            }
        }
        if (account != null && "legacy".equals(account.userType)
                && account.server != null && !account.server.isEmpty()) {
            try {
                Yggdrasil.ensureFresh(account);
            } catch (Yggdrasil.AuthException e) {
                throw new Exception(Lang.t("外置登录已失效：") + e.getMessage() + Lang.t("。请在「账户」里重新登录。"), e);
            } catch (java.io.IOException e) {
                throw new Exception(Lang.t("连接认证服务器失败：") + e.getMessage() + Lang.t("，请检查网络或代理设置。"), e);
            }
        }
        String username = account == null ? "Player" : account.name;
        String uuid = account == null ? Data.offlineUuid(username) : account.uuid;
        uuid = uuid.replace("-", "");
        String accessToken = account != null && account.accessToken != null
                && !account.accessToken.isEmpty() ? account.accessToken : "0";
        String userType = account != null && account.userType != null && !account.userType.isEmpty()
                ? account.userType : (account != null && account.online ? "msa" : "legacy");
        boolean externalAuth = account != null && "legacy".equals(userType)
                && account.server != null && !account.server.isEmpty();
        String gameUserType = externalAuth ? "msa" : userType;
        if (account != null && "nide".equals(userType)
                && account.server != null && !account.server.isEmpty()) {
            gameUserType = "mojang";
        }
        int insRam = data.getInstanceInt("Ram", 0);
        int ramType = data.getInstanceInt("RamType", data.getInt("LaunchRamType", 0));
        int ram = Math.max(512, insRam > 0 ? insRam : data.getInt("LaunchRam", 4096));
        if (ramType == 0) {
            long totalMb = physicalMemoryMb();
            ram = (int) Math.max(2048, Math.min(8192, totalMb / 4));
            if (instance.loader != null && !instance.loader.isEmpty() && !"原版".equals(instance.loader)) {
                ram = Math.min(12288, ram + 512);
            }
            ram = (int) (Math.round(ram / 512.0) * 512);
        }
        int initialRam = data.getInstanceInt("RamInitial", data.getInt("LaunchRamInitial", 0));
        if (initialRam > ram) initialRam = 0;

        int winType = data.getInstanceInt("WindowType", -1);
        if (winType < 0) winType = data.getInt("LaunchWindowType", 1);
        int winW = (int) parseNum(data.getInstance("WindowWidth",
                data.get("LaunchWindowWidth", "854")), 854);
        int winH = (int) parseNum(data.getInstance("WindowHeight",
                data.get("LaunchWindowHeight", "480")), 480);
        boolean fullscreen = winType == 0;
        if (winType == 2) {
            winW = (int) parseNum(data.get("Window.Width", "854"), 854);
            winH = (int) parseNum(data.get("Window.Height", "600"), 600);
        } else if (winType == 4) {
            try {
                javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                winW = (int) bounds.getWidth();
                winH = (int) bounds.getHeight();
            } catch (Throwable ignored) {
                winW = 1920;
                winH = 1080;
            }
        }
        boolean customResolution = (winType == 2 || winType == 3 || winType == 4)
                && winW > 0 && winH > 0;

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("auth_player_name", username);
        vars.put("version_name", instance.version);
        vars.put("game_directory", run.toAbsolutePath().toString());
        vars.put("assets_root", assetsDir);
        vars.put("game_assets", assetsDir);
        vars.put("assets_index_name", assetIndex);
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", accessToken);
        vars.put("auth_session", "token:" + accessToken + ":" + uuid);
        vars.put("user_type", gameUserType);
        vars.put("version_type", "release");
        vars.put("user_properties", "{}");
        vars.put("clientid", "");
        vars.put("auth_xuid", "");
        vars.put("natives_directory", nativesDir.toAbsolutePath().toString());
        vars.put("launcher_name", "TCraftLauncher");
        vars.put("launcher_version", Build.VERSION);
        vars.put("classpath", classpathStr);
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", game.resolve("libraries").toAbsolutePath().toString());
        vars.put("resolution_width", String.valueOf(Math.max(1, winW)));
        vars.put("resolution_height", String.valueOf(Math.max(1, winH)));
        vars.put("quickPlayPath", run.resolve("TCL").resolve("quickplay.log").toAbsolutePath().toString());
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");

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
        // macOS 上 LWJGL3 必须运行在进程主线程 LWJGL2 的 AWT 路径反而冲突
        if ("osx".equals(osName()) && needsFirstThread(libs)) cmd.add("-XstartOnFirstThread");
        int renderer = data.getInstanceInt("Renderer", -1);
        if (renderer < 0) renderer = data.getInt("LaunchAdvanceRenderer", 0);
        if (Shell.windows() && renderer > 0) {
            Path agent = Mesa.ensure();
            cmd.add("-javaagent:" + agent.toAbsolutePath() + "=" + Mesa.driverName(renderer));
        }
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-Xms" + xms + "M");
        // AlwaysPreTouch 预触堆
        if (lockMemory) cmd.add("-XX:+AlwaysPreTouch");
        cmd.add("-Dfile.encoding=UTF-8");
        int javaMajor = Data.javaMajor(javaExe);
        int gcMode = data.getInstanceInt("GC", -1);
        if (gcMode < 0) gcMode = data.getInt("LaunchAdvanceGC", 4);
        switch (gcMode) {
            case 0 -> {
                if (javaMajor >= 15) cmd.add("-XX:+UseZGC");
                else cmd.add("-XX:+UseG1GC");
            }
            case 1 -> {
                // ZGenerational 24+ 已废弃
                if (javaMajor >= 23) {
                    cmd.add("-XX:+UseZGC");
                } else if (javaMajor >= 21) {
                    cmd.add("-XX:+UseZGC");
                    cmd.add("-XX:+ZGenerational");
                } else {
                    cmd.add("-XX:+UseG1GC");
                }
            }
            case 2 -> cmd.add("-XX:+UseG1GC");
            case 3 -> {   }
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
        if (data.getBool("SystemHttpProxyForGame", false)) {
            cmd.addAll(data.proxyJvmArgs());
        }
        String brand = data.getInstance("Info", data.get("LaunchCustomInfo", "TCL"));
        if (brand == null || brand.isBlank()) brand = "TCraftLauncher";
        cmd.add("-Dminecraft.launcher.brand=" + subst(brand, vars));
        cmd.add("-Dminecraft.launcher.version=" + Build.VERSION);

        if (account != null && "nide".equals(account.userType) && account.server != null && !account.server.isEmpty()) {
            Path nideJar = game.resolve("TCL").resolve("nide8auth.jar");
            ensureNideAgentJar(nideJar, account.server);
            cmd.add("-javaagent:" + nideJar.toAbsolutePath() + "=" + account.server);
        } else if (account != null && "legacy".equals(account.userType)
                && account.server != null && !account.server.isEmpty()) {
            Path authJar = game.resolve("TCL").resolve("authlib-injector.jar");
            ensureAuthlibInjectorJar(authJar);
            cmd.add("-Dauthlibinjector.side=client"); // 参数顺序跟 nide8auth 一致
            cmd.add("-javaagent:" + authJar.toAbsolutePath() + "=" + account.server);
        }
        String customTitle = data.getInstance("WindowTitle",
                data.get("InstanceWindowTitle", data.get("LaunchWindowTitle", "")));
        if (!customTitle.isEmpty()) cmd.add("-Dminecraft.window.title=" + subst(customTitle, vars));

        List<Object> jvmArgs = new ArrayList<>();
        for (Map<String, Object> j : chain) {
            Object arguments = j.get("arguments");
            if (arguments instanceof Map<?, ?> argsMap) {
                jvmArgs.addAll(Json.arr(Json.obj(argsMap).get("jvm")));
            }
        }
        // java.library.path 指向 natives
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
        String extraJvm = data.getInstance("JvmArgs", data.get("InstanceJvmArgs", data.get("LaunchJvmArgs", "")));
        if (!extraJvm.isEmpty()) {
            for (String a : Shell.splitArgs(extraJvm)) cmd.add(subst(a, vars));
        }
        cmd.add(mainClass);

        String legacy = Json.str(merged, "minecraftArguments", "");
        List<Object> gameArgs = new ArrayList<>();
        for (Map<String, Object> j : chain) {
            List<Object> ga = Json.arr(Json.obj(j.get("arguments")).get("game"));
            gameArgs.addAll(ga);
        }
        if (!legacy.isEmpty()) {
            if (customResolution) {
                cmd.add("--width");
                cmd.add(String.valueOf(winW));
                cmd.add("--height");
                cmd.add(String.valueOf(winH));
            }
            for (String a : Shell.splitArgs(legacy)) cmd.add(subst(a, vars));
        } else {
            cmd.addAll(resolveArgs(gameArgs, vars, features));
        }
        if (fullscreen) cmd.add("--fullscreen");
        String extraGame = data.getInstance("GameArgs", data.get("LaunchGameArgs", ""));
        if (!extraGame.isEmpty()) {
            for (String a : Shell.splitArgs(extraGame)) cmd.add(subst(a, vars));
        }
        if (serverIp != null && !serverIp.trim().isEmpty()) {
            // IPv6 拆解见 Servers.splitAddress
            String[] hp = Servers.splitAddress(serverIp);
            String host = hp[0];
            String port = hp[1].matches("\\d+") ? hp[1] : "";
            if (!port.isEmpty()) {
                try {
                    if (Long.parseLong(port) > 65535) port = "";
                } catch (NumberFormatException ignored) {
                    port = "";
                }
            }
            cmd.add("--server");
            cmd.add(host);
            if (!port.isEmpty()) {
                cmd.add("--port");
                cmd.add(port);
            }
            data.launchLog("直连服务器：" + serverIp);
        }

        String pre = data.getInstance("PreCommand", data.get("LaunchPreCommand", ""));
        if (!pre.isEmpty()) {
            try {
                boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
                String shell = win ? "cmd.exe" : "/bin/sh";
                String arg = win ? "/c" : "-c";
                Process preProcess = new ProcessBuilder(shell, arg, pre).directory(run.toFile())
                        .redirectErrorStream(true).start();
                // 持续排水防管道阻塞
                Thread consumer = new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(preProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        while (br.readLine() != null) {
                        }
                    } catch (Exception ignored) {
                    }
                }, "TCL-PreCommand");
                consumer.setDaemon(true);
                consumer.start();
                if (data.getInstanceBool("PreCommandWait", data.getBool("LaunchPreCommandWait", true))) {
                    if (!preProcess.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
                        preProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                        preProcess.destroyForcibly();
                        data.launchLog("启动前命令超过 5 分钟未结束，已强制结束：" + pre);
                    }
                }
                // 不等待模式不杀常驻进程
                data.launchLog("已执行启动前命令：" + pre);
            } catch (Exception e) {
                data.launchLog("启动前命令执行失败：" + e.getMessage());
            }
        }

        boolean optimizeRam = data.getInstanceBool("OptimizeRam", data.getBool("LaunchOptimizeRam", true));
        if (optimizeRam) {
            System.gc();
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(run.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        data.launchLog("游戏目录：" + run);
        // 诊断看游戏 latest.log
        data.launchLog("已启动游戏进程，PID " + process.pid());
        data.log("启动游戏：" + instance.version + "（PID " + process.pid() + "）");
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // 写入环形缓冲
                    data.launchLog(line);
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

    private static boolean needsFirstThread(Map<String, Map<String, Object>> libs) {
        boolean lwjgl2 = false;
        boolean lwjgl3 = false;
        for (String name : libs.keySet()) {
            if (name.startsWith("org.lwjgl.lwjgl:")) lwjgl2 = true;
            else if (name.startsWith("org.lwjgl:")) lwjgl3 = true;
        }
        return !lwjgl2 || lwjgl3;
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
            // 有 arm64 变体时排除其他
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

    private static final java.util.regex.Pattern ASSET_HASH_40 =
            java.util.regex.Pattern.compile("[0-9a-f]{40}");

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
            Path targetNorm = target.normalize();
            Map<String, Object> objects = Json.obj(index, "objects");
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                Map<String, Object> obj = Json.obj(e.getValue());
                String hash = Json.str(obj, "hash", "");
                if (!ASSET_HASH_40.matcher(hash).matches()) {
                    Data.get().log("资源索引条目哈希不合法，已跳过：" + e.getKey());
                    continue;
                }
                Path source = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
                Path dest = targetNorm.resolve(e.getKey()).normalize();
                if (!dest.startsWith(targetNorm)) {
                    Data.get().log("资源索引条目路径越界，已跳过：" + e.getKey());
                    continue;
                }
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

    private static void extractNatives(Path jar, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\")) continue;
                if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib")
                        && !name.endsWith(".jnilib")) continue;
                Path out = dir.resolve(name).normalize();
                if (!out.startsWith(dir)) throw new Exception("natives 压缩包含非法路径：" + name);
                Files.copy(zip, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void ensureNideAgentJar(Path jar, String serverCode) throws Exception {
        Tasks.Task probe = new Tasks.Task("t");
        String jarHash = "";
        try {
            String text = Downloader.getText("https://auth.mc-user.com:233/" + serverCode);
            jarHash = Json.str(Json.obj(Json.parse(text)), "jarHash", "").toLowerCase();
        } catch (Exception e) {
            if (Files.isRegularFile(jar)) {
                Data.get().log("获取统一通行证下载信息失败，使用本地 jar：" + Log.describe(e));
                return;
            }
            throw new Exception("获取统一通行证下载信息失败：" + e.getMessage());
        }
        // 无 hash 用本地缓存
        if (Files.isRegularFile(jar) && jarHash.isEmpty()) return;
        if (Files.isRegularFile(jar) && sha1Of(jar).equalsIgnoreCase(jarHash)) return;
        try {
            Modrinth.downloadUrl(probe, "https://login.mc-user.com:233/index/jar", jar, jarHash);
        } catch (Exception e) {
            if (Files.isRegularFile(jar)) {
                Data.get().log("统一通行证 jar 下载失败，使用本地旧版本：" + Log.describe(e));
                return;
            }
            throw new Exception("下载统一通行证运行时失败：" + e.getMessage());
        }
    }

    private static void ensureAuthlibInjectorJar(Path jar) throws Exception {
        Tasks.Task probe = new Tasks.Task("t");
        String text;
        try {
            text = Downloader.getText(
                    "https://authlib-injector.yushi.moe/artifact/latest.json",
                    "https://bmclapi2.bangbang93.com/mirrors/authlib-injector/artifact/latest.json");
        } catch (Exception e) {
            if (Files.isRegularFile(jar)) return;
            throw e;
        }
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
