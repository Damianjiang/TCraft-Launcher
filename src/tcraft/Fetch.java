package tcraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/** 下载引擎，带重试、断点续传和 SHA-1 校验。 */
public final class Fetch {

    private Fetch() {
    }

    /** 单源 5 次重试。 */
    private static final int DEFAULT_RETRY = 5;
    private static final long[] BACKOFF_MS = {200, 500, 1000, 2000, 3000};

    /** 下载到 target，带断点续传和校验。 */
    public static void file(Tasks.Task task, String url, Path target, String sha1,
                            double from, double to) throws IOException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.toAbsolutePath().getParent());

        final String expect = sha1 == null || sha1.isEmpty() ? null : sha1.toLowerCase();
        MessageDigest digest = expect == null ? null : sha1();

        long done = 0;
        if (Files.isRegularFile(part)) {
            done = Files.size(part);
            // 续传时先重放已有部分，继续算摘要。
            if (digest != null && done > 0) replay(part, digest);
        }

        IOException last = null;
        for (int attempt = 0; attempt <= DEFAULT_RETRY; attempt++) {
            if (attempt > 0) sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
            if (task != null && task.canceled) throw new IOException("已取消");
            try {
                transfer(task, url, part, done, digest, from, to);
                if (digest != null) {
                    String actual = HexFormat.of().formatHex(digest.digest());
                    if (!expect.equals(actual)) {
                        // 校验失败，丢掉 .part 重下。
                        Files.deleteIfExists(part);
                        digest = sha1();
                        done = 0;
                        last = new IOException("文件校验失败（SHA-1 不匹配）");
                        continue;
                    }
                }
                Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                // 取消直接退出，不走重试退避。
                if (task != null && task.canceled) throw e;
                last = e;
                // 416 表示 Range 越界，丢掉重下。
                if (e.getMessage() != null && e.getMessage().contains("416")) {
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
        throw last != null ? last : new IOException("下载失败：" + url);
    }

    /** 单次传输，从 .part 的 done 处继续。 */
    private static void transfer(Tasks.Task task, String url, Path part, long done,
                                 MessageDigest digest, double from, double to) throws IOException {
        Http.Body body = done > 0 ? Http.open(url, Duration.ofSeconds(60), done)
                : Http.open(url, Duration.ofSeconds(60));
        try (InputStream in = body.in) {
            boolean append = done > 0 && body.status == 206;
            if (done > 0 && !append) {
                // 服务器不支持 Range，只能从头写。
                done = 0;
                if (digest != null) digest.reset();
            }
            long total = body.length >= 0 ? done + body.length : -1;
            long written = 0;
            long started = System.currentTimeMillis();
            long lastTick = started;
            long lastBytes = 0;
            long limit = Downloader.speedLimitBytes();
            byte[] buffer = new byte[65536];
            OutputStream out = append
                    ? Files.newOutputStream(part, StandardOpenOption.APPEND, StandardOpenOption.WRITE)
                    : Files.newOutputStream(part, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (task != null && task.canceled) throw new IOException("已取消");
                    out.write(buffer, 0, n);
                    if (digest != null) digest.update(buffer, 0, n);
                    written += n;
                    if (limit > 0) Downloader.throttle(written, started, limit);
                    long now = System.currentTimeMillis();
                    if (task != null && now - lastTick > 400) {
                        task.speed = speed((written - lastBytes) * 1000 / Math.max(1, now - lastTick));
                        if (total > 0) {
                            task.progress = from + (to - from) * Math.min(1.0, (done + written) / (double) total);
                        }
                        lastTick = now;
                        lastBytes = written;
                    }
                }
            } finally {
                out.close();
            }
            // 长度对不上说明连接被掐断，交给外层重试
            if (body.length >= 0 && written != body.length) {
                throw new IOException("下载不完整：" + written + " / " + body.length);
            }
        }
    }

    private static void replay(Path part, MessageDigest digest) {
        try (InputStream in = Files.newInputStream(part)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
        } catch (Exception e) {
            // 重放失败就重置摘要，外层会从头下
            digest.reset();
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            return null;
        }
    }

    private static String speed(long bytesPerSec) {
        if (bytesPerSec > 1024 * 1024) return String.format("%.1f M/s", bytesPerSec / 1024.0 / 1024);
        if (bytesPerSec > 1024) return String.format("%.0f K/s", bytesPerSec / 1024.0);
        return bytesPerSec + " B/s";
    }

    private static void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
