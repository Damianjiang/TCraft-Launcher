package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// 自更新链识别外壳头再拉起新本体 更新包是跨平台 jar 按后缀注入头
public final class SelfUpdate {

    private static final String WIN_STUB = "res/launcher/win.exe";
    private static final String SH_HEADER = "res/launcher/linux.sh";

    // 只还原文件名版本号
    private static final Pattern VERSIONED = Pattern.compile(
            "^(TCraftLauncher-)(\\d+(?:\\.\\d+)*)(-win\\.exe|\\.exe|\\.jar|\\.sh|-linux\\.sh|\\.command)$",
            Pattern.CASE_INSENSITIVE);
    private SelfUpdate() {
    }

    // 更新进程自身位置
    private static Path currentLocation() throws IOException {
        try {
            return Paths.get(SelfUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath();
        } catch (Exception e) {
            throw new IOException("定位自身 jar 失败", e);
        }
    }

    // 待替换本体 null 表示定位失败
    public static Path target() {
        try {
            Path self = currentLocation();
            return Files.isRegularFile(self) ? self : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Boot --apply-to 复制带目标后缀外壳的新 jar 改名后用 java -jar 拉起
    public static void applyUpdate(Path oldFile) throws IOException {
        Path self = currentLocation();
        if (!Files.isRegularFile(self)) throw new IOException("定位自身 jar 失败");
        copyWithHeader(self, oldFile);

        Path target = oldFile;
        Optional<Path> renamed = tryRename(oldFile);
        if (renamed.isPresent()) {
            try {
                // 单文件版旧进程退出稍慢 等文件句柄释放再改名
                moveWhenFree(oldFile, renamed.get());
                target = renamed.get();
            } catch (IOException e) {
                // 目标名已存在时保留原名
                Log.info("更新后改名失败 " + Log.describe(e));
            }
        }
        // 复制和改名都完成 才允许新本体展示升级完成
        UpgradeState.applied();
        startJavaAfterUpdate(target);
    }

    // 更新后本体路径已变 不能把旧 -Dtcraft.exe 传给新进程
    private static void startJavaAfterUpdate(Path file) throws IOException {
        java.util.List<String> cmd = buildCommand(file);
        cmd.removeIf(arg -> arg.startsWith("-Dtcraft.exe="));
        cmd.removeIf(arg -> arg.startsWith("-Dtcl.webpRetry="));
        if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
            int jar = cmd.indexOf("-jar");
            if (jar >= 0) cmd.add(jar, "-Dtcraft.exe=" + file.toAbsolutePath());
        }
        spawn(cmd);
    }

    private static Optional<String> suffix(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        return Optional.of(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
    }

    private static Optional<String> headerLocation(String suffix) {
        return switch (suffix) {
            case "exe" -> Optional.of(WIN_STUB);
            case "sh", "command" -> Optional.of(SH_HEADER);
            default -> Optional.empty();
        };
    }

    private static Optional<byte[]> readHeader(ZipFile zip, String suffix) throws IOException {
        Optional<String> location = headerLocation(suffix);
        if (location.isEmpty()) return Optional.empty();
        ZipEntry entry = zip.getEntry(location.get());
        if (entry == null || entry.isDirectory()) return Optional.empty();
        try (InputStream in = zip.getInputStream(entry)) {
            return Optional.of(Io.read(in, 8L * 1024 * 1024));
        }
    }

    // 只按已知外壳字节判断源文件头长度
    private static int detectHeaderLength(ZipFile zip, FileChannel channel) throws IOException {
        ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        for (String suffix : new String[]{"exe", "sh", "command"}) {
            Optional<byte[]> header = readHeader(zip, suffix);
            if (header.isEmpty()) continue;
            ((Buffer) buf).rewind();
            boolean match = true;
            for (byte b : header.get()) {
                if (!buf.hasRemaining() || b != buf.get()) {
                    match = false;
                    break;
                }
            }
            if (match) return header.get().length;
        }
        return 0;
    }

    // 复制更新 jar 并按目标文件名后缀追加内置外壳
    static void copyWithHeader(Path from, Path to) throws IOException {
        try (FileChannel in = FileChannel.open(from, StandardOpenOption.READ);
             ZipFile zip = new ZipFile(from.toFile())) {
            Optional<String> suffix = suffix(to);
            byte[] header = new byte[0];
            if (suffix.isPresent() && headerLocation(suffix.get()).isPresent()) {
                Optional<byte[]> found = readHeader(zip, suffix.get());
                // 缺头时不能截断本体 直接报错
                if (found.isEmpty()) {
                    throw new IOException("更新包缺少 " + headerLocation(suffix.get()).orElse(""));
                }
                header = found.get();
            }
            try (FileChannel out = FileChannel.open(to, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (header.length > 0) out.write(ByteBuffer.wrap(header));
                in.transferTo(detectHeaderLength(zip, in), Long.MAX_VALUE, out);
            }
            // Linux/macOS 更新后保持可执行 否则下次双击打不开
            if (("sh".equals(suffix.orElse("")) || "command".equals(suffix.orElse("")))
                    && !System.getProperty("os.name", "")
                    .toLowerCase(java.util.Locale.ROOT).contains("windows")) {
                to.toFile().setExecutable(true);
            }
        }
    }

    private static Optional<Path> tryRename(Path file) {
        Matcher m = VERSIONED.matcher(file.getFileName().toString());
        if (!m.find()) return Optional.empty();
        String next = m.group(1) + Build.VERSION + m.group(3);
        if (next.equals(file.getFileName().toString())) return Optional.empty();
        return Optional.of(file.resolveSibling(next));
    }

    private static void moveWhenFree(Path from, Path to) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Files.move(from, to);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
        }
        throw last;
    }

    // 复制 -D/-X 后 java -jar 目标文件
    static void startJava(Path file, String... appArgs) throws IOException {
        spawn(buildCommand(file, appArgs));
    }

    static java.util.List<String> buildCommand(Path file, String... appArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String exe = os.contains("windows") ? "javaw.exe" : "java";
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
        try {
            for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if ((arg.startsWith("-D") || arg.startsWith("-X"))
                        && !arg.startsWith("-Dtcl.webpRetry=")) cmd.add(arg);
            }
        } catch (Throwable ignored) {
            // ManagementFactory 不可用时退回自身属性
            for (java.util.Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
                if (entry.getKey() instanceof String key && key.startsWith("tcraft.")) {
                    cmd.add("-D" + key + "=" + entry.getValue());
                }
            }
        }
        cmd.add("-jar");
        cmd.add(file.toAbsolutePath().toString());
        for (String arg : appArgs) cmd.add(arg);
        return cmd;
    }

    static Process spawn(java.util.List<String> cmd) throws IOException {
        // 用当前工作目录 不切到目标文件父目录
        return new ProcessBuilder(cmd)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
    }
}
