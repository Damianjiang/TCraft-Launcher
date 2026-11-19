package tcraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static boolean logsMigrated;

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

    // 日志统一放 .tcl/log，不再散落在 .tcl 根目录
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

    // 只清旧版残留的临时输出，launcher/errors 日志必须保留
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
                // 目标已存在即新日志已开写，删旧防两套
                if (Files.exists(dst)) {
                    Files.deleteIfExists(src);
                    continue;
                }
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // 文件正被 stub 占用时下次启动再搬
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
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.equals("x86") || arch.equals("i386") || arch.equals("ia32");
        if (os.contains("win")) return arm ? "win-arm64" : x86 ? "win-x86" : "win";
        if (os.contains("mac")) return arm ? "mac-aarch64" : "mac";
        if (arm) return "linux-aarch64";
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

    public static void bootstrap(String[] args, StartupWindow ui) throws Exception {
        if (jar() == null) return; // 开发模式由 IDE 提供 JavaFX
        ui.setStatus("检查运行环境…", 10);
        if (fxPresent()) {
            ui.setStatus("正在启动主界面…", 90);
            return;
        }
        Path rt = ensureJavaFx(ui);
        ui.setStatus("正在启动主界面…", 95);
        relaunch(jar(), rt, platform(), args, ui);
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
        String os = System.getProperty("os.name", "").toLowerCase();
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

    private static Path ensureJavaFx(StartupWindow ui) throws Exception {
        ui.setStatus("检查 JavaFX 运行库…", 15);
        String plat = platform();
        String classifier = switch (plat) {
            case "win" -> "win";
            case "mac" -> "mac";
            case "mac-aarch64" -> "mac-aarch64";
            case "linux" -> "linux";
            // 异常消息直接展示给用户，整串查表必失败，片段各自 Lang.t
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
        // 清掉非当前版本的 jar，同名模块多版本会让启动失败
        removeStaleJfxJars(dir, classifier, version);
        // .ready 不可信，按 javafx.properties 校验
        boolean jarsPresent = true;
        for (String module : JFX_MODULES) {
            if (!Files.isRegularFile(dir.resolve(jarName(module, classifier, version)))) {
                jarsPresent = false;
                break;
            }
        }
        if (jarsPresent && jarVersionMatches(dir.resolve(jarName("base", classifier, version)), version)) {
            extractAllNatives(dir, classifier, version);
            if (!marker.equals(Files.isRegularFile(ready) ? Files.readString(ready).trim() : "")) {
                Files.writeString(ready, marker);
            }
            ui.setStatus("运行库检查完成", 70);
            return dir;
        }
        // 版本不符或文件残缺时清掉旧 jar，17 和 21 混用起不来
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
        ui.setStatus("正在解压运行库…", 92);
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
        if (Files.isRegularFile(marker) && expected.equals(Files.readString(marker).trim())) {
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
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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
        if (System.getProperty("user.country", "").equalsIgnoreCase("CN")) {
            repos.add("https://mirrors.cloud.tencent.com/nexus/repository/maven-public");
        }
        repos.add("https://repo1.maven.org/maven2");
        repos.add("https://mirrors.cloud.tencent.com/nexus/repository/maven-public");

        Exception last = null;
        for (String repo : repos) {
            String url = repo + "/org/openjfx/javafx-" + module + "/" + version + "/"
                    + jarName(module, classifier, version);
            try {
                download(url, target, ui,
                        Lang.t("正在下载 JavaFX ") + module + "（" + index + "/" + total + "）", base, span);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("JavaFX " + module + Lang.t(" 下载失败"), last);
    }

    private static void download(String url, Path target, StartupWindow ui, String label,
                                 int base, int span) throws Exception {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
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
                    if (done > 80L * 1024 * 1024) throw new IOException("文件过大");
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 80) {
                        lastUi = now;
                        if (total > 0) {
                            int p = base + (int) Math.min(span, done * span / total);
                            ui.setDownload(label, done, total, p);
                        } else {
                            ui.setDownload(label, done, total, -1);
                        }
                    }
                }
            }
            // 无 .sha1 视为坏源换仓；无校验不落盘
            String expected = fetchText(url + ".sha1");
            if (expected == null) throw new IOException("拿不到 .sha1 校验值：" + url);
            String actual = sha1(tmp);
            if (!actual.equalsIgnoreCase(expected)) {
                Files.deleteIfExists(tmp);
                throw new IOException("SHA1 校验失败：" + url);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            c.disconnect();
            Files.deleteIfExists(tmp);
        }
    }

    private static String fetchText(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", "TCraftLauncher");
            try (InputStream in = c.getInputStream()) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
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

    private static void extractNatives(Path jar, Path dir) throws IOException {
        if (!Files.isRegularFile(jar)) return;
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry en = entries.nextElement();
                String name = en.getName();
                if (en.isDirectory() || name.contains("/")) continue;
                String low = name.toLowerCase(java.util.Locale.ROOT);
                if (!low.endsWith(".dll") && !low.endsWith(".dylib") && !low.endsWith(".so")) continue;
                Path out = dir.resolve(name);
                // 同名 DLL 一律覆盖，防 17/21 本地库混配
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

    private static void relaunch(Path jar, Path rt, String plat, String[] args, StartupWindow ui)
            throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? "javaw.exe" : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        cmd.add("-Dtcl.runtime=" + plat);
        cmd.add("-Dtcl.quietStart=true");
        cmd.add("-Dtcraft.dir=" + home());
        if (os.contains("mac")) cmd.add("-Dapple.application.name=TCraft Launcher");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + rt);
        cmd.add("--module-path=" + modulePath(rt, plat));
        cmd.add("--add-modules=" + MODULES);
        // MainWindow 反射查 GraphicsPipeline，需要开放 com.sun.prism
        cmd.add("--add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED");
        cmd.add("-cp");
        cmd.add(jar.toString());
        cmd.add("tcraft.Boot");
        if (args != null && args.length > 0) cmd.addAll(Arrays.asList(args));

        Path log = logDir().resolve("launch.log");
        Path ready = logDir().resolve(".window-ready");
        Files.deleteIfExists(ready);
        // 新 JVM 需要拿实例锁，旧进程先放手，否则它会自己退出
        Boot.releaseInstanceLock();
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(
                new File(os.contains("win") ? "NUL:" : "/dev/null")));
        // 早期诊断输出追加写，重启不覆盖
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        pb.start();
        // 旧进程启动窗留到主界面出现，防弹两次
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
