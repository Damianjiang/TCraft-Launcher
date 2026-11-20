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
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class RemoteImages {

    private RemoteImages() {
    }

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> ACCESSED = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
    private static final Map<String, List<Consumer<Image>>> WAITERS = new ConcurrentHashMap<>();

    private static volatile Path diskDir;
    private static volatile boolean diskChecked;

    public static void resetCacheDir() {
        diskDir = null;
        diskChecked = false;
    }

    private static final long RETRY_AFTER_MS = 8_000;
    private static final int MEMORY_LIMIT = 1200;

    public static Image cached(String url, double size) {
        if (url == null) return null;
        String k = key(url, size);
        Image img = CACHE.get(k);
        if (img != null) ACCESSED.put(k, System.currentTimeMillis());
        return img;
    }

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

    public static void load(String url, double decodeSize, Consumer<Image> callback) {
        if (url == null || url.isEmpty()) {
            fire(callback, null);
            return;
        }
        String k = key(url, decodeSize);
        Image cached = CACHE.get(k);
        if (cached != null) {
            ACCESSED.put(k, System.currentTimeMillis());
            fire(callback, cached);
            return;
        }
        if (failed(url)) {
            fire(callback, null);
            return;
        }
        // compute 保证登记与首例判定原子，防回调孤儿
        List<Consumer<Image>> waiters = WAITERS.compute(k, (kk, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(callback);
            return list;
        });
        if (waiters.size() > 1) return;

        final int px = decodePx(decodeSize);
        attemptLoad(url, k, px, 0);
    }

    private static final int MAX_RETRY = 3;

    private static void attemptLoad(String url, String key, int px, int attempt) {
        CompletableFuture
                .supplyAsync(() -> readDisk(url), Http.pool())
                .thenCompose(disk -> {
                    if (disk != null && disk.length > 0) {
                        return CompletableFuture.completedFuture(Map.entry(disk, Boolean.TRUE));
                    }
                    return Http.getBytesAsync(url).thenApply(downloaded -> {
                        writeDisk(url, downloaded);
                        return Map.entry(downloaded, Boolean.FALSE);
                    });
                })
                .whenComplete((entry, err) -> {
                    byte[] bytes = entry == null ? null : entry.getKey();
                    boolean fromDisk = entry != null && entry.getValue();
                    Image img = null;
                    if (err == null && bytes != null && bytes.length > 0) {
                        img = decode(bytes, px);
                    }
                    if (img == null) {
                        if (fromDisk) {
                            // 坏图留在缓存不会恢复，删除后下轮重新下载
                            try {
                                Files.deleteIfExists(fileOf(url));
                            } catch (Exception ignored) {
                            }
                        }
                        boolean permanent = Http.isPermanentFailure(err)
                                || (bytes == null && err == null);
                        if (!permanent && attempt < MAX_RETRY) {
                            // 网络抖动先自动重试，别让图标一直空着
                            long delay = 1200L * (attempt + 1);
                            CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    .execute(() -> attemptLoad(url, key, px, attempt + 1));
                            return;
                        }
                        // 4xx 永久失败，其余冷却 30 秒
                        FAILED.put(url, permanent ? Long.MAX_VALUE
                                : System.currentTimeMillis() + RETRY_AFTER_MS);
                    } else {
                        FAILED.remove(url);
                        if (CACHE.size() > MEMORY_LIMIT) trimCache();
                        CACHE.put(key, img);
                        ACCESSED.put(key, System.currentTimeMillis());
                    }
                    if (FAILED.size() > 2000) trimFailed();
                    final Image result = img;
                    List<Consumer<Image>> pending = WAITERS.remove(key);
                    if (pending == null) return;
                    Platform.runLater(() -> {
                        for (Consumer<Image> cb : pending) cb.accept(result);
                    });
                });
    }

    private static Path dir() {
        Path d = diskDir;
        if (d != null) return d;
        try {
            d = Data.get().cacheDir().resolve("images");
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
            return d.resolve(java.util.HexFormat.of().formatHex(hash));
        } catch (Exception e) {
            return null;
        }
    }

    private static void trimCache() {
        int target = MEMORY_LIMIT * 3 / 4;
        if (CACHE.size() <= target) return;
        // 并发 map 的迭代序与访问序无关，按最近访问时间从旧到新淘汰
        List<String> keys = new java.util.ArrayList<>(CACHE.keySet());
        keys.sort(java.util.Comparator.comparingLong(k -> ACCESSED.getOrDefault(k, 0L)));
        for (String k : keys) {
            if (CACHE.size() <= target) break;
            CACHE.remove(k);
            ACCESSED.remove(k);
        }
    }

    private static void trimFailed() {
        long now = System.currentTimeMillis();
        FAILED.entrySet().removeIf(e -> e.getValue() != Long.MAX_VALUE && e.getValue() <= now);
        if (FAILED.size() > 4000) FAILED.clear();
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

    // 缓存按内容 SHA-1 命名，只删 40 位十六进制
    private static final java.util.regex.Pattern SHA1_NAME = java.util.regex.Pattern.compile("[0-9a-f]{40}");

    private static void prune(Path d) {
        if (d == null) return;
        try {
            Http.pool().execute(() -> {
                try {
                    // 缓存根无标记文件即非本启动器目录，整目录拒绝删除
                    if (!Data.hasCacheMarker(d.getParent())) return;
                    try (Stream<Path> s = Files.list(d)) {
                        List<Path> files = s.filter(Files::isRegularFile)
                                .filter(p -> p.getFileName() != null
                                        && SHA1_NAME.matcher(p.getFileName().toString()).matches())
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
