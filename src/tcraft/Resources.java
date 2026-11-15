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

/** 配置目录与 JavaFX 运行库准备。 */
public final class Resources {

    private static final String MODULES = "javafx.controls,javafx.graphics,javafx.base,javafx.media";
    private static final String JFX_VERSION = "21.0.4";
    private static final String[] JFX_MODULES = {"base", "graphics", "controls", "media"};

    private Resources() {
    }

    private static Path homeDir;

    /** 配置、日志、皮肤和下载依赖的根目录。 */
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

    private static Path resolveHome() {
        Path legacy = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
        String explicit = System.getProperty("tcraft.dir");
        if (explicit == null || explicit.trim().isEmpty()) explicit = System.getenv("TCRAFT_HOME");
        if (explicit == null || explicit.trim().isEmpty()) return legacy;

        Path dir = Paths.get(explicit.trim()).toAbsolutePath().normalize();
        migrateLegacy(legacy, dir);
        return dir;
    }

    /** 把旧目录的配置搬到 .tcl，只搬一次。 */
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
        if (os.contains("win")) return "win";
        if (os.contains("mac")) return arm ? "mac-aarch64" : "mac";
        return arm ? "linux-aarch64" : "linux";
    }

    /** 自己所在的 jar，开发模式返回 null。 */
    public static Path jar() {
        try {
            Path p = Paths.get(Resources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 程序目录，jar 的上级。 */
    public static Path appDir() {
        Path jar = jar();
        if (jar != null) {
            Path parent = jar.getParent();
            return parent == null ? Paths.get("").toAbsolutePath() : parent;
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

    /** 没有 JavaFX 时按平台下载依赖，然后带 module-path 重启。 */
    public static void bootstrap(String[] args, StartupWindow ui) throws Exception {
        if (jar() == null) return; // 开发模式由 IDE 提供 JavaFX
        ui.setStatus("正在检查运行环境…", 10);
        if (fxPresent()) {
            ui.setStatus("正在启动 TCL…", 90);
            return;
        }
        Path rt = ensureJavaFx(ui);
        ui.setStatus("正在启动 TCL…", 95);
        relaunch(jar(), rt, platform(), args);
    }

    private static boolean fxPresent() {
        try {
            Class.forName("javafx.application.Application", false, Resources.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 首次运行下载当前平台的 JavaFX，已有缓存直接复用。 */
    private static Path ensureJavaFx(StartupWindow ui) throws Exception {
        ui.setStatus("正在检查资源是否齐全…", 15);
        String plat = platform();
        String classifier = switch (plat) {
            case "win" -> "win";
            case "mac" -> "mac";
            case "mac-aarch64" -> "mac-aarch64";
            case "linux" -> "linux";
            case "linux-aarch64" -> "linux-aarch64";
            default -> throw new IOException("暂不支持当前系统：" + System.getProperty("os.name")
                    + " / " + System.getProperty("os.arch"));
        };
        Path dir = home().resolve("dependencies").resolve(plat).resolve("openjfx");
        Files.createDirectories(dir);
        String marker = JFX_VERSION + "-" + classifier;
        Path ready = dir.resolve(".ready");
        boolean complete = Files.isRegularFile(ready)
                && marker.equals(Files.readString(ready).trim());
        if (complete) {
            for (String module : JFX_MODULES) {
                if (!Files.isRegularFile(dir.resolve(jarName(module, classifier)))) {
                    complete = false;
                    break;
                }
            }
        }
        if (complete) {
            ui.setStatus("资源检查完成", 70);
            return dir;
        }

        for (int i = 0; i < JFX_MODULES.length; i++) {
            String module = JFX_MODULES[i];
            Path target = dir.resolve(jarName(module, classifier));
            if (Files.isRegularFile(target) && Files.size(target) > 0) continue;
            int base = 20 + i * 70 / JFX_MODULES.length;
            int span = 70 / JFX_MODULES.length;
            downloadArtifact(module, classifier, target, ui, base, span);
        }
        ui.setStatus("正在准备运行库…", 92);
        for (String module : JFX_MODULES) {
            extractNatives(dir.resolve(jarName(module, classifier)), dir);
        }
        Files.writeString(ready, marker);
        return dir;
    }

    private static String jarName(String module, String classifier) {
        return "javafx-" + module + "-" + JFX_VERSION + "-" + classifier + ".jar";
    }

    /** 先试国内镜像，再试 Maven Central。 */
    private static void downloadArtifact(String module, String classifier, Path target,
                                         StartupWindow ui, int base, int span) throws Exception {
        List<String> repos = new ArrayList<>();
        if (System.getProperty("user.country", "").equalsIgnoreCase("CN")) {
            repos.add("https://mirrors.cloud.tencent.com/nexus/repository/maven-public");
        }
        repos.add("https://repo1.maven.org/maven2");
        repos.add("https://mirrors.cloud.tencent.com/nexus/repository/maven-public");

        Exception last = null;
        for (String repo : repos) {
            String url = repo + "/org/openjfx/javafx-" + module + "/" + JFX_VERSION + "/"
                    + jarName(module, classifier);
            try {
                download(url, target, ui, "正在下载 JavaFX " + module + "…", base, span);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("JavaFX " + module + " 下载失败", last);
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
                            ui.setStatus(label + "  " + done * 100 / total + "%", p);
                        } else {
                            ui.setDownload(done, total, label);
                        }
                    }
                }
            }
            String expected = fetchText(url + ".sha1");
            if (expected != null) {
                String actual = sha1(tmp);
                if (!actual.equalsIgnoreCase(expected)) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("SHA1 校验失败：" + url);
                }
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
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** JavaFX 平台 jar 里的 DLL/dylib/so 放到同目录供 java.library.path 使用。 */
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
                if (Files.isRegularFile(out) && en.getSize() > 0 && Files.size(out) == en.getSize()) continue;
                try (InputStream in = jf.getInputStream(en)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** 换一个带着 JavaFX 的 JVM 跑，自己退出。 */
    private static void relaunch(Path jar, Path rt, String plat, String[] args) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        String exe = os.contains("win") ? "javaw.exe" : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        cmd.add("-Dtcl.runtime=" + plat);
        cmd.add("-Dtcraft.dir=" + home());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + rt);
        cmd.add("--module-path=" + rt);
        cmd.add("--add-modules=" + MODULES);
        cmd.add("-cp");
        cmd.add(jar.toString());
        cmd.add("tcraft.Boot");
        if (args != null && args.length > 0) cmd.addAll(Arrays.asList(args));

        Path log = home().resolve("launch.log");
        Files.createDirectories(home());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(
                new File(os.contains("win") ? "NUL:" : "/dev/null")));
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        pb.start();
        System.exit(0);
    }

}
