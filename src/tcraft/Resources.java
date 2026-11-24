package tcraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Resources {

    private static final String MODULES = "javafx.controls,javafx.graphics,javafx.base,javafx.media";
    private static final String JFX_VERSION = "17.0.21";
    private static final String[] JFX_MODULES = {"base", "graphics", "controls", "media"};

    private Resources() {
    }

    private static Path homeDir;
    private static volatile boolean logsMigrated;

    public static synchronized Path home() {
        if (homeDir != null) return homeDir;
        Path dir = resolveHome();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        homeDir = dir;
        return dir;
    }

    public static Path logDir() {
        Path dir = home().resolve("log");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        if (!logsMigrated) migrateOldLogs(dir);
        return dir;
    }

    public static Path errorLogFile() {
        return logDir().resolve("errors.log");
    }

    // 只清旧临时输出 日志保留
    public static void cleanupNoiseLogs() {
        try {
            Path dir = logDir();
            for (String name : new String[]{"stub.log", "java-error.log", "java-version.txt"}) {
                Files.deleteIfExists(dir.resolve(name));
            }
        } catch (Exception ignored) {
        }
    }

    private static void migrateOldLogs(Path logDir) {
        logsMigrated = true;
        Path home = home();
        for (String name : new String[]{"launcher.log", "launcher.log.1", "launch.log",
                "errors.log", "crash.txt", "stub.log", "java-error.log", "java-version.txt"}) {
            Path src = home.resolve(name);
            Path dst = logDir.resolve(name);
            if (!Files.isRegularFile(src)) continue;
            try {
                // 新日志已开写时删旧
                if (Files.exists(dst)) {
                    Files.deleteIfExists(src);
                    continue;
                }
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // stub 占用时下次再搬
            }
        }
    }

    private static Path resolveHome() {
        Path legacy = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
        String explicit = System.getProperty("tcraft.dir");
        if (explicit == null || explicit.trim().isEmpty()) explicit = System.getenv("TCRAFT_HOME");
        if (explicit == null || explicit.trim().isEmpty()) return legacy;

        Path dir = Paths.get(explicit.trim()).toAbsolutePath().normalize();
        migrateLegacy(legacy, dir);
        return dir;
    }

    private static void migrateLegacy(Path legacy, Path target) {
        Path marker = target.resolve(".migrated");
        if (Files.exists(marker)) return;
        if (legacy.equals(target) || !Files.isDirectory(legacy)) return;
        try {
            Files.createDirectories(target);
            for (String name : new String[]{"config.properties", "i18n.txt"}) {
                Path src = legacy.resolve(name);
                Path dst = target.resolve(name);
                if (Files.isRegularFile(src) && !Files.exists(dst)) {
                    Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            copyDir(legacy.resolve("option-presets"), target.resolve("option-presets"));
            copyDir(legacy.resolve("skins"), target.resolve("skins"));
            copyDir(legacy.resolve("icons"), target.resolve("icons"));
            Files.writeString(marker, "migrated from " + legacy);
        } catch (Exception ignored) {
        }
    }

    private static void copyDir(Path src, Path dst) {
        if (!Files.isDirectory(src)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(src)) {
            walk.forEach(p -> {
                try {
                    Path out = dst.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(out);
                    } else if (!Files.exists(out)) {
                        Path parent = out.getParent();
                        if (parent != null) Files.createDirectories(parent);
                        Files.copy(p, out, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean arm32 = arch.contains("arm") && !arm64;
        boolean x86 = arch.equals("x86") || arch.equals("i386") || arch.equals("i686") || arch.equals("ia32");
        if (os.contains("win")) return arm64 ? "win-arm64" : arm32 ? "win-arm" : x86 ? "win-x86" : "win";
        if (os.contains("mac")) return arm64 ? "mac-aarch64" : "mac";
        if (arm32) return "linux-arm";
        if (arm64) return "linux-aarch64";
        if (x86) return "linux-x86";
        return "linux";
    }

    public static Path jar() {
        try {
            Path p = Paths.get(Resources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Path appDir() {
        Path jar = jar();
        if (jar != null) {
            Path parent = jar.getParent();
            if (parent == null) return Paths.get("").toAbsolutePath();
            Path up = parent.getParent();
            if (up != null && (Files.isRegularFile(up.resolve("TCraftLauncher.exe"))
                    || Files.isRegularFile(up.resolve("TCraftLauncher.sh"))
                    || Files.isRegularFile(up.resolve("TCraftLauncher.command"))
                    || Files.isRegularFile(up.resolve("TCraftLauncher.bat")))) {
                return up;
            }
            return parent;
        }
        try {
            Path p = Paths.get(Resources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path cur = p;
            for (int i = 0; i < 4 && cur != null; i++) {
                if (Files.isDirectory(cur.resolve("res"))) return cur;
                cur = cur.getParent();
            }
            Path parent = p.getParent();
            return parent == null ? Paths.get("").toAbsolutePath() : parent;
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }

    private static final String WEBP_JAR = "webp-0.2.0.jar";
    // 下载后先校验哈希 防镜像投毒
    private static final String WEBP_SHA256 = "3f644c53bec98c7129e3461b947ca918525ea888f151bd226785fb98db75c8d9";

    public static Path webpJar() {
        return home().resolve("dependencies").resolve("libs").resolve(WEBP_JAR);
    }

    // 首启补 WebP 库 失败不拦启动
    private static boolean ensureWebp(StartupWindow ui) {
        Path target = webpJar();
        Path tmp = target.resolveSibling(WEBP_JAR + ".part");
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 100_000
                    && WEBP_SHA256.equals(sha256(target))) return false;
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
            }
            Files.createDirectories(target.getParent());
            ui.setStatus(Lang.t("正在下载 WebP 解码库…"), 88);
            String[] urls = {
                    "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/org/glavo/webp/0.2.0/webp-0.2.0.jar",
                    "https://repo1.maven.org/maven2/org/glavo/webp/0.2.0/webp-0.2.0.jar"
            };
            Exception last = null;
            for (String url : urls) {
                try {
                    byte[] data = Http.getBytes(url);
                    if (data == null || data.length < 100_000 || data[0] != 'P' || data[1] != 'K') {
                        throw new IOException(Lang.t("下载内容不是 jar"));
                    }
                    if (!WEBP_SHA256.equals(sha256(data))) {
                        throw new IOException(Lang.t("WebP 解码库校验值不匹配"));
                    }
                    Files.write(tmp, data);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (Exception e) {
                    last = e;
                }
            }
            if (last != null) throw last;
            return false;
        } catch (Exception e) {
            Log.error("[" + java.time.LocalDateTime.now().toLocalTime().withNano(0) + "] WebP 解码库下载失败：" + e);
            return false;
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    public static void bootstrap(String[] args, StartupWindow ui) throws Exception {
        if (jar() == null) return; // 开发模式由 IDE 提供 JavaFX
        ui.setStatus(Lang.t("检查运行环境…"), 10);
        boolean fxReady = fxPresent();
        if (!fxReady) {
            // 只有内置 JavaFX 缺失时才需要下载运行库
            Path rt = ensureJavaFx(ui);
            // WebP 只是可选解码库 失败不能拦 JavaFX 重启
            ensureWebp(ui);
            ui.setStatus(Lang.t("正在启动主界面…"), 95);
            relaunch(jar(), rt, platform(), args);
        } else {
            // 已有 JavaFX 直接起主界面 只有刚补 WebP 才重拉一次
            boolean webpReady = ensureWebp(ui);
            if (webpReady) {
                ui.setStatus(Lang.t("正在启动主界面…"), 95);
                relaunchBuiltin(jar(), args);
            }
        }
        ui.setStatus(Lang.t("正在启动主界面…"), 90);
    }

    public static Path iconFile(String name) {
        Path dir = home().resolve("res").resolve("images");
        Path out = dir.resolve(name);
        if (Files.isRegularFile(out)) return out;
        try (InputStream in = Resources.class.getResourceAsStream("/res/images/" + name)) {
            if (in == null) return null;
            Files.createDirectories(dir);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (Exception e) {
            return Files.isRegularFile(out) ? out : null;
        }
    }

    public static Path launchTarget() {
        Path dir = appDir();
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] names = os.contains("win")
                ? new String[]{"TCraftLauncher.exe", "TCraftLauncher.bat"}
                : new String[]{"TCraftLauncher.sh", "TCraftLauncher.command"};
        for (String n : names) {
            Path p = dir.resolve(n);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static boolean fxPresent() {
        try {
            Class.forName("javafx.application.Application", false, Resources.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // 自更新后直接带运行库拉起
    static List<String> directLaunchCommand(Path app, String... appArgs) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String exe = os.contains("win") ? "javaw.exe" : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dtcraft.dir=" + home());
        String self = System.getProperty("tcraft.exe", "");
        if (!self.isEmpty()) cmd.add("-Dtcraft.exe=" + self);
        if (os.contains("mac")) cmd.add("-Xdock:name=TCraft Launcher");
        String plat = platform();
        Path deps = home().resolve("dependencies").resolve(plat).resolve("openjfx");
        boolean builtinFx = Files.isRegularFile(
                Paths.get(System.getProperty("java.home"), "lib", "javafx.properties"));
        // .ready 不可信 缺 jar 时走 bootstrap
        boolean depsReady = Files.isRegularFile(deps.resolve(".ready"));
        if (depsReady) {
            for (String module : JFX_MODULES) {
                if (!Files.isRegularFile(deps.resolve(jarName(module, plat, JFX_VERSION)))) {
                    depsReady = false;
                    break;
                }
            }
        }
        String cp = Files.isRegularFile(webpJar())
                ? app + File.pathSeparator + webpJar()
                : app.toString();
        if (builtinFx) {
            cmd.add("--add-modules=" + MODULES);
            cmd.add("--add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED");
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add("tcraft.Boot");
        } else if (depsReady) {
            cmd.add("-Djava.library.path=" + deps);
            cmd.add("--module-path=" + modulePath(deps, plat));
            cmd.add("--add-modules=" + MODULES);
            cmd.add("--add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED");
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add("tcraft.Boot");
        } else {
            // 运行库未就绪 交给 bootstrap 引导
            cmd.add("-jar");
            cmd.add(app.toAbsolutePath().toString());
        }
        for (String a : appArgs) cmd.add(a);
        return cmd;
    }

    private static Path ensureJavaFx(StartupWindow ui) throws Exception {
        ui.setStatus(Lang.t("检查 JavaFX 运行库…"), 15);
        String plat = platform();
        String classifier = switch (plat) {
            case "win" -> "win";
            case "mac" -> "mac";
            case "mac-aarch64" -> "mac-aarch64";
            case "linux" -> "linux";
            case "win-arm64" -> throw new IOException(
                    Lang.t("OpenJFX 17 没有 Windows ARM64 原生包。请使用 x64 版 Java 运行启动器（Windows 会自动模拟），")
                            + Lang.t("或把 JavaFX 17 放到 ") + home().resolve("dependencies").resolve("win-arm64").resolve("openjfx"));
            case "win-x86" -> throw new IOException(
                    Lang.t("OpenJFX 17 没有 32 位 Windows 原生包。请安装 64 位 Java 后重新启动启动器。"));
            case "linux-x86" -> throw new IOException(
                    Lang.t("OpenJFX 17 没有 32 位 Linux 原生包。请安装 64 位 Java 后重新启动启动器。"));
            case "linux-aarch64" -> "linux-aarch64";
            default -> throw new IOException(Lang.t("暂不支持当前系统：") + System.getProperty("os.name")
                    + " / " + System.getProperty("os.arch"));
        };
        String version = JFX_VERSION;
        Path dir = home().resolve("dependencies").resolve(plat).resolve("openjfx");
        Files.createDirectories(dir);
        String marker = version + "-" + classifier;
        Path ready = dir.resolve(".ready");
        // 同名模块多版本会让启动失败
        removeStaleJfxJars(dir, classifier, version);
        // 按 javafx.properties 校验
        boolean jarsPresent = true;
        for (String module : JFX_MODULES) {
            if (!Files.isRegularFile(dir.resolve(jarName(module, classifier, version)))) {
                jarsPresent = false;
                break;
            }
        }
        if (jarsPresent && jarVersionMatches(dir.resolve(jarName("base", classifier, version)), version)) {
            extractAllNatives(dir, classifier, version);
            String readyText = "";
            if (Files.isRegularFile(ready)) {
                if (Files.size(ready) <= 64L * 1024) readyText = Files.readString(ready).trim();
                else Files.deleteIfExists(ready);
            }
            if (!marker.equals(readyText)) {
                Files.writeString(ready, marker);
            }
            ui.setStatus(Lang.t("运行库检查完成"), 70);
            return dir;
        }
        // 17 和 21 混用起不来
        for (String module : JFX_MODULES) {
            Files.deleteIfExists(dir.resolve(jarName(module, classifier, version)));
        }

        for (int i = 0; i < JFX_MODULES.length; i++) {
            String module = JFX_MODULES[i];
            Path target = dir.resolve(jarName(module, classifier, version));
            if (Files.isRegularFile(target) && Files.size(target) > 0) continue;
            int base = 20 + i * 70 / JFX_MODULES.length;
            int span = 70 / JFX_MODULES.length;
            downloadArtifact(module, classifier, version, target, ui, base, span,
                    i + 1, JFX_MODULES.length);
        }
        ui.setStatus(Lang.t("正在解压运行库…"), 92);
        extractAllNatives(dir, classifier, version);
        Files.writeString(ready, marker);
        return dir;
    }

    private static String jarName(String module, String classifier, String version) {
        return "javafx-" + module + "-" + version + "-" + classifier + ".jar";
    }

    private static void removeStaleJfxJars(Path dir, String classifier, String version) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (String module : JFX_MODULES) keep.add(jarName(module, classifier, version));
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.endsWith(".part")) {
                    Files.deleteIfExists(p);
                } else if (name.startsWith("javafx-") && name.endsWith(".jar")
                        && !keep.contains(name)) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void extractAllNatives(Path dir, String classifier, String version) throws IOException {
        Path marker = dir.resolve(".natives");
        String expected = version + "-" + classifier;
        if (Files.isRegularFile(marker) && Files.size(marker) <= 64L * 1024
                && expected.equals(Files.readString(marker).trim())) {
            return;
        }
        for (String module : JFX_MODULES) {
            extractNatives(dir.resolve(jarName(module, classifier, version)), dir);
        }
        Files.writeString(marker, expected);
    }

    private static boolean jarVersionMatches(Path jar, String expected) {
        if (!Files.isRegularFile(jar)) return false;
        try (JarFile jf = new JarFile(jar.toFile())) {
            JarEntry en = jf.getJarEntry("javafx.properties");
            if (en == null) return false;
            try (InputStream in = jf.getInputStream(en)) {
                String text = new String(Io.read(in, 64L * 1024), java.nio.charset.StandardCharsets.UTF_8);
                for (String line : text.split("\\R")) {
                    if (!line.startsWith("javafx.version=")) continue;
                    return expected.equals(line.substring("javafx.version=".length()).trim());
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void downloadArtifact(String module, String classifier, String version, Path target,
                                         StartupWindow ui, int base, int span, int index, int total) throws Exception {
        List<String> repos = new ArrayList<>();
        repos.add("https://mirrors.cloud.tencent.com/nexus/repository/maven-public");
        repos.add("https://repo1.maven.org/maven2");

        Exception last = null;
        for (String repo : repos) {
            String url = repo + "/org/openjfx/javafx-" + module + "/" + version + "/"
                    + jarName(module, classifier, version);
            try {
                download(url, target, ui,
                        Lang.tf("正在下载 JavaFX %s（%d/%d）", module, index, total), base, span);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException(Lang.tf("%s 下载失败", "JavaFX " + module), last);
    }

    private static void download(String url, Path target, StartupWindow ui, String label,
                                 int base, int span) throws Exception {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        // 连接阶段无进度 先切不确定态
        ui.setIndeterminate(label + Lang.t(" · 连接中…"));
        HttpURLConnection c = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(45000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "TCraftLauncher");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long total = c.getContentLengthLong();
            long done = 0;
            long lastUi = 0;
            try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (done > 80L * 1024 * 1024) throw new IOException(Lang.t("文件过大"));
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 80) {
                        lastUi = now;
                        if (total > 0) {
                            int p = base + (int) Math.min(span, done * span / total);
                            ui.setDownload(label, done, total, p);
                        } else {
                            ui.setIndeterminate(label + Lang.t(" · 已下载 ")
                                    + String.format(java.util.Locale.ROOT, "%.1f MB", done / 1048576.0));
                        }
                    }
                }
            }
            // 无 .sha1 视为坏源换仓 无校验不落盘
            String expected = fetchText(url + ".sha1");
            if (expected == null) throw new IOException(Lang.t("拿不到 .sha1 校验值：") + url);
            String actual = sha1(tmp);
            if (!actual.equalsIgnoreCase(expected)) {
                Files.deleteIfExists(tmp);
                throw new IOException(Lang.t("SHA1 校验失败：") + url);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            c.disconnect();
            Files.deleteIfExists(tmp);
        }
    }

    private static String fetchText(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", "TCraftLauncher");
            try (InputStream in = c.getInputStream()) {
                String text = new String(Io.read(in, 64L * 1024), java.nio.charset.StandardCharsets.UTF_8).trim();
                int space = text.indexOf(' ');
                if (space > 0) text = text.substring(0, space);
                return text.length() >= 40 ? text.substring(0, 40) : null;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return java.util.HexFormat.of().formatHex(md.digest());
    }

    private static String sha256(byte[] data) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void extractNatives(Path jar, Path dir) throws IOException {
        if (!Files.isRegularFile(jar)) return;
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry en = entries.nextElement();
                String name = en.getName();
                if (en.isDirectory() || name.contains("/")) continue;
                String low = name.toLowerCase(java.util.Locale.ROOT);
                if (!low.endsWith(".dll") && !low.endsWith(".dylib") && !low.endsWith(".so")
                        && !low.endsWith(".jnilib")) continue;
                Path out = dir.resolve(name);
                // 同名 DLL 覆盖
                try (InputStream in = jf.getInputStream(en)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String modulePath(Path rt, String plat) throws IOException {
        String classifier = switch (plat) {
            case "win" -> "win";
            case "mac" -> "mac";
            case "mac-aarch64" -> "mac-aarch64";
            case "win-arm64" -> "win-arm64";
            case "linux" -> "linux";
            case "linux-aarch64" -> "linux-aarch64";
            default -> throw new IOException(Lang.t("没有 JavaFX 17 运行库：") + plat);
        };
        List<String> paths = new ArrayList<>();
        for (String module : JFX_MODULES) {
            paths.add(rt.resolve(jarName(module, classifier, JFX_VERSION)).toString());
        }
        return String.join(File.pathSeparator, paths);
    }

    private static void relaunch(Path jar, Path rt, String plat, String[] args)
            throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String exe = os.contains("win") ? "javaw.exe" : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        cmd.add("-Dtcl.runtime=" + plat);
        cmd.add("-Dtcl.quietStart=true");
        cmd.add("-Dtcraft.dir=" + home());
        String self = System.getProperty("tcraft.exe", "");
        if (!self.isEmpty()) cmd.add("-Dtcraft.exe=" + self);
        if (os.contains("mac")) cmd.add("-Xdock:name=TCraft Launcher");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + rt);
        cmd.add("--module-path=" + modulePath(rt, plat));
        cmd.add("--add-modules=" + MODULES);
        // 反射需开放 com.sun.prism
        cmd.add("--add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED");
        cmd.add("-cp");
        Path webp = webpJar();
        cmd.add(Files.isRegularFile(webp)
                ? jar + File.pathSeparator + webp
                : jar.toString());
        cmd.add("tcraft.Boot");
        if (args != null && args.length > 0) cmd.addAll(Arrays.asList(args));
        startBootProcess(cmd);
    }

    private static void relaunchBuiltin(Path jar, String[] args) throws IOException {
        startBootProcess(directLaunchCommand(jar, args));
    }

    private static void startBootProcess(List<String> cmd) throws IOException {
        Path log = logDir().resolve("launch.log");
        Path ready = logDir().resolve(".window-ready");
        Files.deleteIfExists(ready);
        // 新 JVM 取锁前须释放旧进程
        Boot.releaseInstanceLock();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(
                new File(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                        ? "NUL" : "/dev/null")));
        // 诊断输出追加
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        pb.start();
        // 启动窗留到主界面出现
        long until = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < until) {
            if (Files.isRegularFile(ready)) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.exit(0);
    }
}
