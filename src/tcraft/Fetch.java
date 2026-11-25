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

public final class Fetch {

    private Fetch() {
    }

    private static final int DEFAULT_RETRY = 5;
    private static final long[] BACKOFF_MS = {200, 500, 1000, 2000, 3000};
    private static final long IDLE_TIMEOUT_MS = 20_000;

    // 与 Downloader 共写 .part 锁须共用
    static final java.util.concurrent.ConcurrentHashMap<String, Object> PART_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void file(Tasks.Task task, String url, Path target, String sha1,
                            double from, double to) throws IOException {
        file(task, url, target, sha1, from, to, true);
    }

    // 并发下载时进度由调用方汇总 单文件不再抢写
    static void file(Tasks.Task task, String url, Path target, String sha1,
                     double from, double to, boolean report) throws IOException {
        fileCandidates(task, java.util.List.of(url), target, sha1, from, to, report);
    }

    // 候选源逐个试 单源少量重试 失败立刻换下一个
    static void fileCandidates(Tasks.Task task, java.util.List<String> urls, Path target, String sha1,
                               double from, double to, boolean report) throws IOException {
        java.util.List<String> candidates = urls == null ? java.util.List.of()
                : urls.stream().filter(u -> u != null && !u.isEmpty()).distinct().toList();
        if (candidates.isEmpty()) throw new IOException(Lang.t("没有可用的下载地址：") + target.getFileName());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.createDirectories(target.toAbsolutePath().getParent());

        final String expect = sha1 == null || sha1.isEmpty() ? null : sha1.toLowerCase();
        MessageDigest digest = expect == null ? null : sha1();

        String lockKey = part.toAbsolutePath().toString();
        Object lock = new Object();
        if (PART_LOCKS.putIfAbsent(lockKey, lock) != null) {
            throw new IOException(Lang.t("该文件已在下载中：") + target.getFileName());
        }
        // 无哈希不能续传 删除重下
        if (expect == null) Files.deleteIfExists(part);
        IOException last = null;
        try {
            long done = 0;
            if (Files.isRegularFile(part)) {
                done = Files.size(part);
                // 续算摘要
                if (digest != null && done > 0) replay(part, digest);
            }

            candidateLoop:
            for (String url : candidates) {
                // 多源时每个源快速失败 单源保留原有耐心
                int retries = candidates.size() > 1 ? 2 : DEFAULT_RETRY;
                for (int attempt = 0; attempt <= retries; attempt++) {
                    if (attempt > 0) {
                        // 分段退避 可取消
                        long left = BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)];
                        while (left > 0) {
                            if (task != null && task.canceled) throw new IOException(Lang.t("已取消"));
                            sleep(Math.min(200, left));
                            left -= 200;
                        }
                    }
                    if (task != null && task.canceled) throw new IOException(Lang.t("已取消"));
                    try {
                        transfer(task, url, part, done, digest, from, to, report);
                        if (digest != null) {
                            String actual = HexFormat.of().formatHex(digest.digest());
                            if (!expect.equals(actual)) {
                                Files.deleteIfExists(part);
                                digest = sha1();
                                done = 0;
                                last = new IOException(Lang.t("文件校验失败（SHA-1 不匹配）"));
                                // 内容不对 换源比原地重试有效
                                continue candidateLoop;
                            }
                        }
                        // 无哈希只认 zip 魔数
                        if (expect == null && !Downloader.jarLooksValid(part, target)) {
                            Files.deleteIfExists(part);
                            done = 0;
                            last = new IOException(Lang.t("下载内容不是有效的 jar：") + target.getFileName());
                            continue candidateLoop;
                        }
                    } catch (IOException e) {
                        if (task != null && task.canceled) throw e;
                        last = e;
                        if (e instanceof Http.StatusException se && se.code == 416) {
                            Files.deleteIfExists(part);
                            if (digest != null) digest.reset();
                            done = 0;
                        } else {
                            try {
                                done = Files.isRegularFile(part) ? Files.size(part) : 0;
                            } catch (Exception ignored) {
                                done = 0;
                            }
                        }
                        continue;
                    }
                    // 占用类错误不重试
                    try {
                        Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.FileSystemException e) {
                        throw new IOException(Lang.t("文件被占用，请关闭正在运行的游戏后重试：") + target.getFileName(), e);
                    }
                    return;
                }
            }
        } finally {
            PART_LOCKS.remove(lockKey, lock);
        }
        throw last != null ? last : new IOException(Lang.t("下载失败：") + target.getFileName());
    }

    private static void transfer(Tasks.Task task, String url, Path part, long done,
                                 MessageDigest digest, double from, double to,
                                 boolean report) throws IOException {
        Http.Body body = done > 0 ? Http.openOnce(url, Duration.ofSeconds(60), done)
                : Http.openOnce(url, Duration.ofSeconds(60));
        IdleGuard guard = new IdleGuard(body.in, IDLE_TIMEOUT_MS);
        guard.start();
        try (InputStream in = body.in) {
            boolean append = done > 0 && body.status == 206;
            if (done > 0 && !append) {
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
                    guard.touch();
                    if (task != null && task.canceled) throw new IOException(Lang.t("已取消"));
                    out.write(buffer, 0, n);
                    if (digest != null) digest.update(buffer, 0, n);
                    written += n;
                    NetSpeed.add(n);
                    if (done + written > 4L * 1024 * 1024 * 1024) {
                        throw new IOException(Lang.t("下载文件超过大小上限"));
                    }
                    if (limit > 0) Downloader.throttle(n, limit);
                    long now = System.currentTimeMillis();
                    if (report && task != null && now - lastTick > 400) {
                        task.speed = speed((written - lastBytes) * 1000 / Math.max(1, now - lastTick));
                        // from==to 时调用方自管进度
                        if (total > 0 && to > from) {
                            task.progress = from + (to - from) * Math.min(1.0, (done + written) / (double) total);
                        }
                        lastTick = now;
                        lastBytes = written;
                    }
                }
            } finally {
                out.close();
            }
            // 长度不符交给外层重试
            if (body.length >= 0 && written != body.length) {
                throw new IOException(Lang.t("下载不完整：") + written + " / " + body.length);
            }
        } finally {
            guard.close();
        }
    }

    public static byte[] readAll(InputStream in, long idleMs) throws IOException {
        return readAll(in, idleMs, Long.MAX_VALUE);
    }

    public static byte[] readAll(InputStream in, long idleMs, long maxBytes) throws IOException {
        IdleGuard guard = new IdleGuard(in, idleMs);
        guard.start();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                guard.touch();
                int take = (int) Math.min(n, maxBytes - out.size());
                if (take > 0) out.write(buffer, 0, take);
                if (out.size() >= maxBytes) {
                    // 超限报错 不给半截
                    if (in.read() != -1) throw new java.io.IOException(Lang.t("响应超过大小上限"));
                    break;
                }
            }
            return out.toByteArray();
        } finally {
            guard.close();
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService WATCHDOG =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TCL-Fetch-Idle");
                t.setDaemon(true);
                return t;
            });

    private static final class IdleGuard {
        private final InputStream in;
        private final long timeoutMs;
        private volatile long last = System.currentTimeMillis();
        private volatile boolean stopped;
        private java.util.concurrent.ScheduledFuture<?> task;

        IdleGuard(InputStream in, long timeoutMs) {
            this.in = in;
            this.timeoutMs = timeoutMs;
        }

        void start() {
            task = WATCHDOG.scheduleAtFixedRate(this::check, 500, 500, TimeUnit.MILLISECONDS);
        }

        void touch() {
            last = System.currentTimeMillis();
        }

        void close() {
            stopped = true;
            if (task != null) task.cancel(false);
        }

        private void check() {
            if (stopped || System.currentTimeMillis() - last <= timeoutMs) return;
            stopped = true;
            if (task != null) task.cancel(false);
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void replay(Path part, MessageDigest digest) {
        try (InputStream in = Files.newInputStream(part)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
        } catch (Exception e) {
            // 重放失败重置摘要
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
