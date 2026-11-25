package tcraft;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// 读取资源包/光影包的本地图标
final class PackIcons {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20000;
    private static final int MAX_CACHE = 512;

    // 已知图标位置 前面的优先
    private static final String[] NAMES = {
            "pack.png", "icon.png", "logo.png", "preview.png",
            "shaders/pack.png", "shaders/icon.png", "shaders/logo.png", "shaders/preview.png",
            "shaders/screenshot.png", "shaders/preview.jpg", "shaders/screenshot.jpg",
            "shaders/icon.jpg", "shaders/logo.jpg", "pack.jpg", "icon.jpg", "logo.jpg"
    };

    private static final class Cached {
        final Image image;
        final long time;

        Cached(Image image) {
            this.image = image;
            this.time = System.currentTimeMillis();
        }
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private PackIcons() {
    }

    static Image load(Path pack) {
        if (pack == null) return null;
        String key = key(pack);
        Cached cached = CACHE.get(key);
        // 失败缓存 60 秒 文件占用解除后会重试
        if (cached != null && (cached.image != null || System.currentTimeMillis() - cached.time < 60_000)) {
            return cached.image;
        }
        Image image = null;
        try {
            image = Files.isDirectory(pack) ? fromFolder(pack) : fromZip(pack);
        } catch (Exception ignored) {
        }
        if (CACHE.size() >= MAX_CACHE) CACHE.clear();
        CACHE.put(key, new Cached(image));
        return image;
    }

    private static String key(Path pack) {
        try {
            String path = pack.toAbsolutePath().normalize().toString();
            long modified = Files.getLastModifiedTime(pack).toMillis();
            long size = Files.isRegularFile(pack) ? Files.size(pack) : 0;
            return path + "|" + modified + "|" + size;
        } catch (Exception e) {
            return pack.toAbsolutePath().normalize().toString();
        }
    }

    private static Image fromFolder(Path dir) throws Exception {
        for (String name : NAMES) {
            Path file = child(dir, name);
            if (file != null) {
                Image image = decode(Files.readAllBytes(file));
                if (image != null) return image;
            }
        }
        Image image = firstInFolder(dir.resolve("shaders").resolve("screenshots"), false);
        if (image != null) return image;
        image = firstInFolder(dir.resolve("shaders"), true);
        if (image != null) return image;
        return firstInFolder(dir, true);
    }

    private static Path child(Path dir, String name) {
        try {
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) return null;
            long size = Files.size(file);
            return size > 0 && size <= MAX_BYTES ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Image firstInFolder(Path dir, boolean onlySuggested) throws Exception {
        if (!Files.isDirectory(dir)) return null;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!isImage(name)) continue;
                if (onlySuggested && !suggested(name)) continue;
                try {
                    long size = Files.size(file);
                    if (size > 0 && size <= MAX_BYTES) files.add(file);
                } catch (Exception ignored) {
                }
                if (files.size() >= 500) break;
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path file : files) {
            Image image = decode(Files.readAllBytes(file));
            if (image != null) return image;
        }
        return null;
    }

    private static Image fromZip(Path file) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            // 先按标准文件名直接查 大资源包不用全表扫描
            for (String name : NAMES) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) continue;
                Image image = decode(zip, entry);
                if (image != null) return image;
            }
            Map<String, ZipEntry> images = new HashMap<>();
            var entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements() && count++ < MAX_ENTRIES) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("./")) name = name.substring(2);
                String lower = name.toLowerCase(Locale.ROOT);
                if (!isImage(lower) || lower.contains("../")) continue;
                images.putIfAbsent(lower, entry);
            }
            for (String name : NAMES) {
                ZipEntry entry = images.get(name);
                if (entry == null) continue;
                Image image = decode(zip, entry);
                if (image != null) return image;
            }
            // 截图目录与 shaders 根的预览图
            TreeMap<String, ZipEntry> sorted = new TreeMap<>(images);
            // 单层目录包裹的 pack.png
            for (Map.Entry<String, ZipEntry> item : sorted.entrySet()) {
                String name = item.getKey();
                if (!name.endsWith("/pack.png")) continue;
                int first = name.indexOf('/');
                if (first != name.lastIndexOf('/')) continue;
                Image image = decode(zip, item.getValue());
                if (image != null) return image;
            }
            for (Map.Entry<String, ZipEntry> item : sorted.entrySet()) {
                String name = item.getKey();
                if (!name.startsWith("shaders/screenshots/")) continue;
                Image image = decode(zip, item.getValue());
                if (image != null) return image;
            }
            for (Map.Entry<String, ZipEntry> item : sorted.entrySet()) {
                String name = item.getKey();
                if (!name.startsWith("shaders/") || name.indexOf('/', 8) >= 0) continue;
                String base = name.substring(8);
                if (!suggested(base)) continue;
                Image image = decode(zip, item.getValue());
                if (image != null) return image;
            }
            for (Map.Entry<String, ZipEntry> item : sorted.entrySet()) {
                String name = item.getKey();
                if (!name.startsWith("shaders/") || name.indexOf('/', 8) >= 0) continue;
                Image image = decode(zip, item.getValue());
                if (image != null) return image;
            }
        }
        return null;
    }

    private static boolean isImage(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static boolean suggested(String name) {
        String base = name.toLowerCase(Locale.ROOT);
        return base.contains("preview") || base.contains("screenshot")
                || base.contains("logo") || base.contains("icon");
    }

    private static Image decode(ZipFile zip, ZipEntry entry) throws Exception {
        long size = entry.getSize();
        if (size > MAX_BYTES) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return decode(Io.read(in, MAX_BYTES));
        }
    }

    private static Image decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        if (ImageSafety.tooLarge(data)) return null;
        int width = 128;
        int height = 128;
        long[] size = ImageSafety.sizeOf(data);
        if (size != null && size[0] > 0 && size[1] > 0) {
            double scale = Math.min(1.0, 128.0 / Math.max(size[0], size[1]));
            width = Math.max(1, (int) Math.round(size[0] * scale));
            height = Math.max(1, (int) Math.round(size[1] * scale));
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            Image image = new Image(in, width, height, true, true);
            return image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0 ? null : image;
        } catch (Exception e) {
            return null;
        }
    }
}
