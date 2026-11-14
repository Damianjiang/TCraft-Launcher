package tcraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** 单文件版首次运行把 res 和 runtime 解压到用户目录。 */
public final class Resources {

    private static final String STAMP = ".build";
    private static final String MODULES = "javafx.controls,javafx.graphics,javafx.base,javafx.media";

    private Resources() {
    }

    /** 配置、资源、运行库都在这。 */
    public static Path home() {
        return Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
    }

    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("win")) return "win";
        if (os.contains("mac")) return arm ? "mac-aarch64" : "mac";
        String distro = System.getProperty("os.name", "");
        return arm && !distro.toLowerCase().contains("win") ? "linux-aarch64" : "linux";
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

    /** 解包资源，必要时带 module-path 重启。 */
    public static void bootstrap(String[] args) {
        if (jar() == null) return;                                 // 开发模式不用解包
        if (Files.isDirectory(appDir().resolve("res"))) return;    // 目录版，资源就在旁边
        Path jar = jar();
        String plat = platform();
        String stamp = stampOf(jar);
        try {
            Path res = home().resolve("res");
            if (!stamp.equals(read(res.resolve(STAMP)))) {
                extract(jar, "res/", res);
                write(res.resolve(STAMP), stamp);
            }
            Path rt = home().resolve("runtime").resolve(plat);
            if (!Files.isDirectory(rt) || !stamp.equals(read(rt.resolve(STAMP)))) {
                extract(jar, "runtime/" + plat + "/", rt);
                if (!Files.isDirectory(rt) && plat.endsWith("-aarch64")) {
                    // ARM 机器上拿了 Intel 包，退回基础包
                    String base = plat.substring(0, plat.length() - "-aarch64".length());
                    rt = home().resolve("runtime").resolve(base);
                    extract(jar, "runtime/" + base + "/", rt);
                }
                if (!Files.isDirectory(rt)) return;                // 没带运行库就用系统的 JavaFX
                write(rt.resolve(STAMP), stamp);
            }
            // 标记实际解出的目录名
            String used = rt.getFileName().toString();
            if (!used.equals(System.getProperty("tcl.runtime", ""))) {
                relaunch(jar, rt, used, args);
            }
        } catch (Exception e) {
            // 解包失败不拦，交给启动流程报错；先落个痕，不然只能瞎猜
            try {
                Files.createDirectories(home());
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(
                        home().resolve("launch.log"), java.nio.charset.StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND))) {
                    e.printStackTrace(pw);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** 换一个带着 JavaFX 的 JVM 跑，自己退出。 */
    private static void relaunch(Path jar, Path rt, String plat, String[] args) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase();
        // javaw 才不会在 GUI 后面挂个黑控制台
        String exe = os.contains("win") ? "javaw.exe" : "java";
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        cmd.add("-Dtcl.runtime=" + plat);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + rt);
        cmd.add("--module-path=" + rt);
        cmd.add("--add-modules=" + MODULES);
        cmd.add("-cp");
        cmd.add(jar.toString());
        cmd.add("tcraft.Boot");
        if (args != null && args.length > 0) cmd.addAll(Arrays.asList(args));
        // javaw 没控制台，输出写进 launch.log
        Path log = home().resolve("launch.log");
        Files.createDirectories(home());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        // DISCARD 是 WRITE 型重定向，redirectInput 不收，stdin 得自己指到空设备
        pb.redirectInput(ProcessBuilder.Redirect.from(
                new File(os.contains("win") ? "NUL:" : "/dev/null")));
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        pb.start();
        System.exit(0);
    }

    /** 把 jar 中指定前缀的条目解压到 dest。 */
    private static void extract(Path jar, String prefix, Path dest) throws IOException {
        try (JarFile f = new JarFile(jar.toFile())) {
            var entries = f.entries();
            while (entries.hasMoreElements()) {
                JarEntry en = entries.nextElement();
                String name = en.getName();
                if (en.isDirectory() || !name.startsWith(prefix)) continue;
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) continue;
                Path out = dest.resolve(rel);
                if (Files.isRegularFile(out) && en.getSize() > 0 && Files.size(out) == en.getSize()) continue;
                Path parent = out.getParent();
                if (parent != null) Files.createDirectories(parent);
                try (InputStream in = f.getInputStream(en)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** jar 的构建标记，没有就用文件名加大小兜底。 */
    private static String stampOf(Path jar) {
        try (JarFile f = new JarFile(jar.toFile())) {
            JarEntry en = f.getJarEntry("res/" + STAMP);
            if (en != null) {
                try (InputStream in = f.getInputStream(en)) {
                    return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return jar.getFileName() + "-" + Files.size(jar);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String read(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void write(Path p, String text) {
        try {
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(p, text);
        } catch (Exception ignored) {
        }
    }
}
