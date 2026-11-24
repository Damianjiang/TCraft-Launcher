package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SelfUpdate {

    // jar 内置外壳 按后缀取用
    private static final String WIN_STUB = "/res/launcher/win.exe";
    private static final String SH_HEADER = "/res/launcher/linux.sh";

    // 只还原文件名版本号
    private static final Pattern VERSIONED = Pattern.compile(
            "^(TCraftLauncher-)(\\d+(?:\\.\\d+)*)(-win\\.exe|\\.exe|\\.jar|\\.sh|-linux\\.sh|\\.command)$",
            Pattern.CASE_INSENSITIVE);

    private SelfUpdate() {
    }

    private static Path selfJar() throws IOException {
        try {
            return Paths.get(SelfUpdate.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IOException("定位自身 jar 失败", e);
        }
    }

    // 待替换本体 null 表示定位失败
    public static Path target() {
        String prop = System.getProperty("tcraft.exe", "").trim();
        if (!prop.isEmpty()) {
            Path p = Paths.get(prop);
            if (Files.isRegularFile(p)) return p;
        }
        Path jar;
        try {
            jar = selfJar();
        } catch (Exception e) {
            return null;
        }
        String dir = System.getProperty("tcraft.dir", "");
        if (dir.isEmpty() || !jar.toAbsolutePath().startsWith(Paths.get(dir).toAbsolutePath())) {
            return Files.isRegularFile(jar) ? jar : null;
        }
        // 本体是 .tcl 上级目录中的 exe
        Path parent = Paths.get(dir).toAbsolutePath().getParent();
        return parent == null ? null : scanLauncher(parent, jar);
    }

    // 旧外壳按长度与哈希定位本体
    static Path scanLauncher(Path dir, Path runningJar) {
        try {
            byte[] jarHash = fileHash(runningJar);
            long jarSize = Files.size(runningJar);
            Path first = null;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
                for (Path cand : entries) {
                    if (!Files.isRegularFile(cand)) continue;
                    String name = cand.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!name.endsWith(".exe")) continue;
                    try {
                        int header = headerLength(cand);
                        if (header <= 0) continue;
                        if (Files.size(cand) - header != jarSize) continue;
                        if (!Arrays.equals(bodyHash(cand, header), jarHash)) continue;
                        if (first == null) first = cand;
                        if (name.startsWith("tcraftlauncher")) return cand;
                    } catch (Exception ignored) {
                    }
                }
            }
            return first;
        } catch (Exception ignored) {
        }
        return null;
    }

    // Boot --apply-to 替换本体并拉起新版
    public static void applyUpdate(Path oldFile) throws IOException {
        Path self = selfJar();
        if (oldFile.toAbsolutePath().equals(self.toAbsolutePath())) return;
        validateLauncher(oldFile);
        replace(oldFile, self);
        Path next = tryRename(oldFile);
        try {
            startJava(next);
        } catch (IOException e) {
            // 拉起失败改回名字
            if (!next.equals(oldFile)) {
                try {
                    Files.move(next, oldFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
            throw e;
        }
    }

    // 只接受三种本体形态
    private static void validateLauncher(Path oldFile) throws IOException {
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(oldFile.toFile())) {
            var attrs = jf.getManifest() == null ? null : jf.getManifest().getMainAttributes();
            if (attrs == null || !"tcraft.Boot".equals(attrs.getValue("Main-Class"))) {
                throw new IOException("--apply-to 目标不是本启动器：" + oldFile);
            }
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("--apply-to 目标校验失败：" + oldFile, e);
        }
    }

    // 优先用新 jar 内置外壳
    // 临时文件校验后原子覆盖
    static void replace(Path oldFile, Path newJar) throws IOException {
        byte[] head = newHeaderFor(oldFile);
        Path tmp = oldFile.resolveSibling(oldFile.getFileName() + ".update-tmp");
        Files.deleteIfExists(tmp);
        boolean done = false;
        IOException last = null;
        try {
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                 InputStream in = Files.newInputStream(newJar)) {
                out.write(head);
                in.transferTo(out);
            }
            // 尺寸与 zip 结构对得上才允许覆盖本体
            if (Files.size(tmp) != (long) head.length + Files.size(newJar)
                    || headerLength(tmp) != head.length) {
                throw new IOException("更新包写入校验失败");
            }
            copyPermissions(oldFile, tmp);
            // 旧进程占用时是 FileSystemException
            for (int attempt = 0; attempt < 50; attempt++) {
                try {
                    Files.move(tmp, oldFile, StandardCopyOption.REPLACE_EXISTING);
                    done = true;
                    return;
                } catch (IOException e) {
                    last = e;
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("替换被中断", ie);
                    }
                }
            }
            throw last != null ? last : new IOException("替换失败");
        } finally {
            if (!done) Files.deleteIfExists(tmp);
        }
    }

    // 保留 POSIX 执行位
    private static void copyPermissions(Path from, Path to) {
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        } catch (Exception ignored) {
            // 仅当两端都支持 POSIX 视图时才生效
        }
    }

    private static byte[] newHeaderFor(Path oldFile) throws IOException {
        String name = oldFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        String res = name.endsWith(".exe") ? WIN_STUB
                : name.endsWith(".sh") || name.endsWith(".command") ? SH_HEADER : null;
        if (res == null) return new byte[0];
        try (InputStream in = SelfUpdate.class.getResourceAsStream(res)) {
            if (in != null) return Io.read(in, 1024L * 1024);
        }
        // 过渡构建没内置外壳 沿用旧文件的外壳
        int header = headerLength(oldFile);
        byte[] head = new byte[header];
        try (InputStream in = Files.newInputStream(oldFile)) {
            int got = 0;
            while (got < header) {
                int n = in.read(head, got, header - got);
                if (n < 0) throw new IOException("读取外壳中断");
                got += n;
            }
        }
        return head;
    }

    // InputStream.skip 不保证一次跳满 循环跳到目标位置
    private static void skipFully(InputStream in, long n) throws IOException {
        long skipped = 0;
        while (skipped < n) {
            long s = in.skip(n - skipped);
            if (s <= 0) {
                if (in.read() < 0) throw new IOException("定位失败");
                skipped += 1;
            } else {
                skipped += s;
            }
        }
    }

    // 与 launcher.c 同一 jar_offset 公式
    static int headerLength(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 100) throw new IOException("文件过小：" + file.getFileName());
        long tailLen = Math.min(size, 66000);
        long start = size - tailLen;
        byte[] tail;
        try (InputStream in = Files.newInputStream(file)) {
            skipFully(in, start);
            tail = in.readNBytes((int) tailLen);
        }
        if (tail.length != tailLen) throw new IOException("读取文件尾失败");
        for (int i = tail.length - 22; i >= 0; i--) {
            if (tail[i] != 0x50 || tail[i + 1] != 0x4b || tail[i + 2] != 0x05 || tail[i + 3] != 0x06) continue;
            long cdSize = (tail[i + 12] & 0xFFL) | (tail[i + 13] & 0xFFL) << 8
                    | (tail[i + 14] & 0xFFL) << 16 | (tail[i + 15] & 0xFFL) << 24;
            long cdOff = (tail[i + 16] & 0xFFL) | (tail[i + 17] & 0xFFL) << 8
                    | (tail[i + 18] & 0xFFL) << 16 | (tail[i + 19] & 0xFFL) << 24;
            long eocd = start + i;
            if (eocd < cdSize + cdOff) continue;
            long jarStart = eocd - cdSize - cdOff;
            if (jarStart < 0 || jarStart >= size || !isZipMagic(file, jarStart)) {
                continue;
            }
            return (int) jarStart;
        }
        throw new IOException("找不到 zip 目录（不是有效的启动器文件）");
    }

    private static boolean isZipMagic(Path file, long pos) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            skipFully(in, pos);
            byte[] magic = in.readNBytes(4);
            return magic.length == 4 && magic[0] == 0x50 && magic[1] == 0x4b
                    && magic[2] == 0x03 && magic[3] == 0x04;
        }
    }

    private static byte[] bodyHash(Path file, long offset) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                skipFully(in, offset);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) >= 0) digest.update(buf, 0, n);
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 不可用", e);
        }
    }

    private static byte[] fileHash(Path file) throws IOException {
        return bodyHash(file, 0);
    }

    private static Path tryRename(Path file) {
        Matcher m = VERSIONED.matcher(file.getFileName().toString());
        if (!m.find()) return file;
        String next = m.group(1) + Build.VERSION + m.group(3);
        Path renamed = file.resolveSibling(next);
        if (renamed.equals(file)) return file;
        try {
            Files.move(file, renamed, StandardCopyOption.REPLACE_EXISTING);
            return renamed;
        } catch (IOException e) {
            return file;
        }
    }

    static void startJava(Path file, String... appArgs) throws IOException {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".exe") || name.endsWith(".sh") || name.endsWith(".command")) {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(file.toAbsolutePath().toString());
            cmd.addAll(Arrays.asList(appArgs));
            spawn(cmd, file.toAbsolutePath().getParent());
            return;
        }
        spawn(buildCommand(file, appArgs), null);
    }

    // 先构建命令再 spawn 构建失败不释放锁
    static java.util.List<String> buildCommand(Path file, String... appArgs) throws IOException {
        java.util.List<String> cmd;
        try {
            // 运行库就绪时直连模块路径 否则走 bootstrap 引导
            cmd = Resources.directLaunchCommand(file.toAbsolutePath(), appArgs);
        } catch (Throwable ignored) {
            cmd = null;
        }
        if (cmd == null) {
            cmd = new java.util.ArrayList<>();
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            String exe = os.contains("win") ? "javaw.exe" : "java";
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", exe).toString());
            String dir = System.getProperty("tcraft.dir", "");
            if (!dir.isEmpty()) cmd.add("-Dtcraft.dir=" + dir);
            String self = System.getProperty("tcraft.exe", "");
            if (!self.isEmpty()) cmd.add("-Dtcraft.exe=" + self);
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-jar");
            cmd.add(file.toAbsolutePath().toString());
            for (String arg : appArgs) cmd.add(arg);
        }
        return cmd;
    }

    static Process spawn(java.util.List<String> cmd) throws IOException {
        return spawn(cmd, null);
    }

    static Process spawn(java.util.List<String> cmd, Path dir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        if (dir != null) pb.directory(dir.toFile());
        return pb.start();
    }
}
