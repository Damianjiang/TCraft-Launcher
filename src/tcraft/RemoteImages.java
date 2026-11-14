package tcraft;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** 远程图标加载：内存缓存、磁盘缓存、失败分级。 */
public final class RemoteImages {

    private RemoteImages() {
    }

    /** 内存缓存，key 是 url 加解码尺寸。 */
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    /** 失败记录，永久失败填 Long.MAX_VALUE。 */
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
    /** 正在加载的 key 对应的等待回调。 */
    private static final Map<String, List<Consumer<Image>>> WAITERS = new ConcurrentHashMap<>();

    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong DISK_HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();

    private static volatile Path diskDir;
    private static volatile boolean diskChecked;

    /** 临时失败的重试间隔，毫秒。 */
    private static final long RETRY_AFTER_MS = 30_000;
    private static final int MEMORY_LIMIT = 1200;

    /** 已缓存的图，没有就 null。 */
    public static Image cached(String url, double size) {
        return url == null ? null : CACHE.get(key(url, size));
    }

    /** URL 是否在失败冷却期。 */
    public static boolean failed(String url) {
        if (url == null) return false;
        Long until = FAILED.get(url);
        if (until == null) return false;
        if (until == Long.MAX_VALUE) return true;
        if (System.currentTimeMillis() >= until) {
            FAILED.remove(url);
            return false;
        }
        return true;
    }

    /** 缓存命中统计。 */
    public static String stats() {
        return "图标缓存：内存命中 " + HITS.get() + " · 磁盘命中 " + DISK_HITS.get()
                + " · 网络 " + MISSES.get() + " · 内存条目 " + CACHE.size();
    }

    /** 异步取图，同一 URL 只加载一次，回调在 FX 线程。 */
    public static void load(String url, double decodeSize, Consumer<Image> callback) {
        if (url == null || url.isEmpty()) {
            fire(callback, null);
            return;
        }
        String k = key(url, decodeSize);
        Image cached = CACHE.get(k);
        if (cached != null) {
            HITS.incrementAndGet();
            fire(callback, cached);
            return;
        }
        if (failed(url)) {
            fire(callback, null);
            return;
        }
        List<Consumer<Image>> waiters = WAITERS.computeIfAbsent(k, x -> new CopyOnWriteArrayList<>());
        waiters.add(callback);
        if (waiters.size() > 1) return; // 已有加载在跑

        final int px = decodePx(decodeSize);
        CompletableFuture
                .supplyAsync(() -> readDisk(url), Http.pool())
                .thenCompose(bytes -> {
                    if (bytes != null && bytes.length > 0) {
                        DISK_HITS.incrementAndGet();
                        return CompletableFuture.completedFuture(bytes);
                    }
                    MISSES.incrementAndGet();
                    return Http.getBytesAsync(url).thenApply(downloaded -> {
                        writeDisk(url, downloaded);
                        return downloaded;
                    });
                })
                .whenComplete((bytes, err) -> {
                    Image img = null;
                    if (err == null && bytes != null && bytes.length > 0) {
                        img = decode(bytes, px);
                    }
                    if (img == null) {
                        // 4xx 永久失败，其余冷却 30 秒
                        long until = Http.isPermanentFailure(err) || bytes == null && err == null
                                ? Long.MAX_VALUE : System.currentTimeMillis() + RETRY_AFTER_MS;
                        FAILED.put(url, until);
                    } else {
                        if (CACHE.size() > MEMORY_LIMIT) CACHE.clear();
                        CACHE.put(k, img);
                    }
                    final Image result = img;
                    List<Consumer<Image>> pending = WAITERS.remove(k);
                    if (pending == null) return;
                    Platform.runLater(() -> {
                        for (Consumer<Image> cb : pending) cb.accept(result);
                    });
                });
    }

    /** 预取，让图早点到。 */
    public static void prefetch(List<String> urls, double size) {
        if (urls == null) return;
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            if (cached(url, size) != null || failed(url)) continue;
            load(url, size, img -> {
                // 只为落缓存
            });
        }
    }

    private static Path dir() {
        Path d = diskDir;
        if (d != null) return d;
        try {
            d = Data.get().baseDir().resolve("cache").resolve("images");
            Files.createDirectories(d);
        } catch (Exception e) {
            d = null;
        }
        diskDir = d;
        if (!diskChecked) {
            diskChecked = true;
            prune(d);
        }
        return d;
    }

    private static Path fileOf(String url) {
        Path d = dir();
        if (d == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return d.resolve(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readDisk(String url) {
        Path f = fileOf(url);
        if (f == null) return null;
        try {
            if (!Files.isRegularFile(f) || Files.size(f) == 0) return null;
            return Files.readAllBytes(f);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeDisk(String url, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 2 * 1024 * 1024) return;
        Path f = fileOf(url);
        if (f == null) return;
        try {
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // 缓存写失败不影响显示
        }
    }

    /** 超过 3000 张就删最旧的，后台跑。 */
    private static void prune(Path d) {
        if (d == null) return;
        try {
            Http.pool().execute(() -> {
                try (Stream<Path> s = Files.list(d)) {
                    List<Path> files = s.filter(Files::isRegularFile)
                            .sorted((a, b) -> {
                                try {
                                    return Long.compare(Files.getLastModifiedTime(a).toMillis(),
                                            Files.getLastModifiedTime(b).toMillis());
                                } catch (Exception e) {
                                    return 0;
                                }
                            })
                            .toList();
                    int over = files.size() - 3000;
                    for (int i = 0; i < over; i++) {
                        try {
                            Files.deleteIfExists(files.get(i));
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static String key(String url, double size) {
        return url + "#" + decodePx(size);
    }

    private static int decodePx(double size) {
        return (int) Math.max(8, Math.round(size * 2));
    }

    private static Image decode(byte[] bytes, int px) {
        try {
            Image img = new Image(new ByteArrayInputStream(bytes), px, px, true, true);
            return img.isError() ? null : img;
        } catch (Exception e) {
            return null;
        }
    }

    private static void fire(Consumer<Image> cb, Image img) {
        if (Platform.isFxApplicationThread()) cb.accept(img);
        else Platform.runLater(() -> cb.accept(img));
    }
}
