package tcraft;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Reads mod icons from Fabric, Forge, NeoForge, Quilt, mcmod.info, and LiteLoader metadata.
final class ModIcons {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final long MAX_META = 2L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20000;
    private static final int MAX_CACHE = 1024;

    private static final String[] ROOT_NAMES = {
            "logo.png", "icon.png", "logo.jpg", "icon.jpg", "logo.jpeg", "icon.jpeg"
    };

    private static final Pattern FABRIC_ICON =
            Pattern.compile("\"icon\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FABRIC_ICON_OBJECT =
            Pattern.compile("\"icon\"\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern FABRIC_ICON_VALUE =
            Pattern.compile("\"[^\"]+\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOML_LOGO =
            Pattern.compile("(?m)^\\s*logoFile\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern JSON_LOGO =
            Pattern.compile("\"logoFile\"\\s*:\\s*\"([^\"]+)\"");

    private static final class Cached {
        final Image image;
        final long time;

        Cached(Image image) {
            this.image = image;
            this.time = System.currentTimeMillis();
        }
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private ModIcons() {
    }

    static Image load(Path mod) {
        if (mod == null || !Files.isRegularFile(mod)) return null;
        String key = key(mod);
        Cached cached = CACHE.get(key);
        // Cache failures briefly so a replaced jar is picked up without retrying on every paint.
        if (cached != null && (cached.image != null || System.currentTimeMillis() - cached.time < 30_000)) {
            return cached.image;
        }
        Image image = null;
        try (ZipFile zip = new ZipFile(mod.toFile())) {
            image = read(zip);
        } catch (Exception ignored) {
        }
        if (CACHE.size() >= MAX_CACHE) CACHE.clear();
        CACHE.put(key, new Cached(image));
        return image;
    }

    private static String key(Path mod) {
        try {
            String path = mod.toAbsolutePath().normalize().toString();
            return path + "|" + Files.getLastModifiedTime(mod).toMillis() + "|" + Files.size(mod);
        } catch (Exception e) {
            return mod.toAbsolutePath().normalize().toString();
        }
    }

    private static Image read(ZipFile zip) {
        String path = metadataPath(zip);
        if (!path.isEmpty()) {
            Image image = readEntry(zip, path);
            if (image != null) return image;
        }
        for (String name : ROOT_NAMES) {
            Image image = readEntry(zip, name);
            if (image != null) return image;
        }
        return shallowImage(zip);
    }

    private static String metadataPath(ZipFile zip) {
        String text = text(zip, "fabric.mod.json");
        if (!text.isEmpty()) {
            String path = jsonIcon(text);
            if (!path.isEmpty()) return path;
        }
        text = text(zip, "quilt.mod.json");
        if (!text.isEmpty()) {
            String path = jsonIcon(text);
            if (!path.isEmpty()) return path;
        }
        text = text(zip, "META-INF/neoforge.mods.toml");
        if (!text.isEmpty()) {
            String path = tomlLogo(text);
            if (!path.isEmpty()) return path;
        }
        text = text(zip, "META-INF/mods.toml");
        if (!text.isEmpty()) {
            String path = tomlLogo(text);
            if (!path.isEmpty()) return path;
        }
        text = text(zip, "mcmod.info");
        if (!text.isEmpty()) {
            String path = jsonLogo(text);
            if (!path.isEmpty()) return path;
        }
        text = text(zip, "litemod.json");
        if (!text.isEmpty()) {
            String path = jsonIcon(text);
            if (!path.isEmpty()) return path;
        }
        return "";
    }

    private static String jsonIcon(String text) {
        Matcher direct = FABRIC_ICON.matcher(text);
        if (direct.find()) return direct.group(1);
        Matcher object = FABRIC_ICON_OBJECT.matcher(text);
        if (!object.find()) return "";
        Matcher value = FABRIC_ICON_VALUE.matcher(object.group(1));
        return value.find() ? value.group(1) : "";
    }

    private static String tomlLogo(String text) {
        Matcher matcher = TOML_LOGO.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String jsonLogo(String text) {
        Matcher matcher = JSON_LOGO.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String text(ZipFile zip, String name) {
        try {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.isDirectory()) return "";
            long size = entry.getSize();
            if (size > MAX_META) return "";
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(Io.read(in, MAX_META), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Image readEntry(ZipFile zip, String raw) {
        String path = cleanPath(raw);
        if (path.isEmpty()) return null;
        try {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null && path.startsWith("./")) entry = zip.getEntry(path.substring(2));
            if (entry == null) entry = findEntry(zip, path);
            if (entry == null || entry.isDirectory()) return null;
            long size = entry.getSize();
            if (size > MAX_BYTES) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return decode(Io.read(in, MAX_BYTES));
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ZipEntry findEntry(ZipFile zip, String path) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        int count = 0;
        while (entries.hasMoreElements() && count++ < MAX_ENTRIES) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().replace('\\', '/');
            while (name.startsWith("./")) name = name.substring(2);
            if (name.equalsIgnoreCase(path)) return entry;
        }
        return null;
    }

    private static String cleanPath(String raw) {
        if (raw == null) return "";
        String path = raw.trim().replace('\\', '/');
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("data:")) return "";
        while (path.startsWith("./")) path = path.substring(2);
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty() || path.contains("..")) return "";
        return path;
    }

    private static Image shallowImage(ZipFile zip) {
        ZipEntry best = null;
        int bestDepth = Integer.MAX_VALUE;
        int count = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements() && count++ < MAX_ENTRIES) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            while (name.startsWith("./")) name = name.substring(2);
            if (!name.endsWith("/logo.png") && !name.endsWith("/icon.png")
                    && !name.endsWith("/logo.jpg") && !name.endsWith("/icon.jpg")) continue;
            int depth = 0;
            for (int i = 0; i < name.length(); i++) if (name.charAt(i) == '/') depth++;
            if (depth > 2 || depth >= bestDepth) continue;
            bestDepth = depth;
            best = entry;
        }
        if (best == null) return null;
        try {
            long size = best.getSize();
            if (size > MAX_BYTES) return null;
            try (InputStream in = zip.getInputStream(best)) {
                return decode(Io.read(in, MAX_BYTES));
            }
        } catch (Exception ignored) {
            return null;
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
