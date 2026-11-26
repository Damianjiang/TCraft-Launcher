package tcraft;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class Theme {

    public static Color accent = Color.web("#1370f3");
    public static Color accentDark = Color.web("#0b5bcb");
    public static Color accentLight = Color.web("#4890f5");
    public static Color accentPale = Color.web("#96c0f9");
    public static Color accentPale2 = Color.web("#d5e6fd");
    public static Color accentPale3 = Color.web("#eaf2fe");
    public static Color titleBar = Color.web("#4890f5");

    public static Color text = Color.web("#343d4a");
    public static double componentOpacity = 0.5;
    public static Color cardBg = Color.web("#ffffff", 245 * 0.5 / 255);
    public static Color sidebarBg = Color.web("#ffffff", 0.945);
    public static final Color dialogBg = Color.web("#fbfbfb");
    public static final Color modalMask = Color.web("#000000", 90 / 255.0);
    public static final Color warnMask = Color.color(80 / 255.0, 0, 0, 140 / 255.0);
    public static Color fieldBg = Color.web("#ffffff", Math.min(1, (245 * 0.5 + 25) / 255));
    public static Color listHoverBg = Color.web("#e0eafd", 190 * 0.5 / 255);
    public static Color semiTransparent = Color.web("#eaf2fe", 1 / 255.0);
    // 显式 alpha 避免 RRGGBBAA
    public static Color halfWhite = Color.color(1, 1, 1, 0x55 / 255.0);
    public static Color brush7 = Color.web("#e0eafd");
    public static Color redBack = Color.color(251 / 255.0, 221 / 255.0, 221 / 255.0, 0x80 / 255.0);
    public static Color darkBtnBg = Color.color(25 / 255.0, 25 / 255.0, 30 / 255.0, 214 / 255.0);
    public static Color darkBtnHover = Color.color(52 / 255.0, 52 / 255.0, 58 / 255.0, 226 / 255.0);

    public static Color gray1 = Color.web("#404040");
    public static Color gray2 = Color.web("#737373");
    public static Color gray3 = Color.web("#8c8c8c");
    public static Color gray4 = Color.web("#a6a6a6");
    public static Color gray5 = Color.web("#cccccc");
    public static Color gray6 = Color.web("#ebebeb");

    public static Color green = Color.web("#2ba55c");
    public static Color red = Color.web("#ff4c4c");
    public static Color redDark = Color.web("#ce2111");
    public static Color orange = Color.web("#e8871e");
    public static Color blueHintBg = Color.web("#dcEafd");

    public static final String[] THEME_NAMES = {
            "云溪蓝", "竹露青", "松涧绿", "杏阳黄", "檀木棕", "墨夜黑", "桃霞粉", "星雾紫",
            "暮霞金", "熔岩橙", "赤焰红", "海盐蓝", "七曜彩", "曜光彩", "自定义", "青柠绿",
            "薄荷绿", "靛夜蓝", "葡萄紫", "雾霭蓝", "玫瑰金",
            "深海青", "苔藓绿", "橄榄金", "落霞橙", "豆沙红", "藕荷紫", "墨松绿", "石板灰", "柠檬黄", "月白蓝"
    };
    public static final String[] THEME_COLORS = {
            "#1370f3", "#3b8f7a", "#2f7a43", "#d6a01e", "#87592e", "#30343e", "#e2749a", "#7357c8",
            "#c98a2b", "#e8621d", "#d23329", "#3f9cc7", "#e05a8a", "#7a54d6", "#1370f3", "#86bf2a",
            "#2fbf92", "#24408f", "#8b3fc0", "#5b7a94", "#d99a8a",
            "#0e6b7a", "#5d7a3a", "#8a7b2f", "#d97a45", "#a85757", "#a273b8", "#2b5f52", "#55606e", "#c9b52a", "#6f8fb8"
    };

    // 每 99 次启动解锁主题
    public static final int[] HIDDEN_THEMES = {
            6,
            7,
            8,
            20,
            26
    };

    public static final int UNLOCK_EVERY_LAUNCHES = 99;

    public static volatile Image wallpaper;
    public static Font baseFont;

    // 壁纸就绪后回调刷色
    public static volatile Runnable onWallpaperReady;
    // 取色变化时回调
    public static volatile Runnable onAccentApplied;

    private Theme() {
    }

    public static Path root() {
        return Resources.home();
    }

    public static Path skinDir() {
        return Resources.home().resolve("skins");
    }

    public static Path iconDir() {
        return Resources.home().resolve("icons");
    }

    private static java.util.Map<String, String> resourceIndex;

    private static synchronized java.util.Map<String, String> resourceIndex() {
        if (resourceIndex != null) return resourceIndex;
        java.util.Map<String, String> map = new java.util.HashMap<>();
        Path jar = Resources.jar();
        if (jar != null) {
            try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
                var entries = jf.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith("res/") && !name.endsWith("/")) {
                        map.put(name.toLowerCase(java.util.Locale.ROOT), name);
                    }
                }
            } catch (Exception e) {
                Data.get().log("资源索引构建失败（jar）：" + Log.describe(e));
            }
        } else {
            Path dir = Resources.appDir().resolve("res");
            try (var walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String rel = "res/" + dir.relativize(p).toString().replace('\\', '/');
                    map.put(rel.toLowerCase(java.util.Locale.ROOT), rel);
                });
            } catch (Exception e) {
                Data.get().log("资源索引构建失败（res 目录）：" + Log.describe(e));
            }
        }
        resourceIndex = map;
        return map;
    }

    private static String resourcePath(String relative) {
        if (relative == null) return null;
        return resourceIndex().get(("res/" + relative).toLowerCase(java.util.Locale.ROOT));
    }

    public static URL resUrl(String relative) {
        String path = resourcePath(relative);
        return path == null ? null : Theme.class.getResource("/" + path);
    }

    public static java.io.InputStream resStream(String relative) {
        URL url = resUrl(relative);
        try {
            return url == null ? null : url.openStream();
        } catch (IOException e) {
            return null;
        }
    }

    public static String resText(String relative) {
        try (java.io.InputStream in = resStream(relative)) {
            if (in == null) return "";
            return new String(Io.read(in, 2L * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Path userImage(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.startsWith("Skins/")) {
            Path base = skinDir().toAbsolutePath().normalize();
            Path p = base.resolve(name.substring(6)).normalize();
            return p.startsWith(base) && Files.isRegularFile(p) ? p : null;
        }
        if (name.startsWith("Icons/")) {
            Path base = iconDir().toAbsolutePath().normalize();
            Path p = base.resolve(name.substring(6)).normalize();
            return p.startsWith(base) && Files.isRegularFile(p) ? p : null;
        }
        Path base = skinDir().toAbsolutePath().normalize();
        Path skin = base.resolve(name).normalize();
        return skin.startsWith(base) && Files.isRegularFile(skin) ? skin : null;
    }

    public static java.io.InputStream imageStream(String name) throws IOException {
        Path user = userImage(name);
        if (user != null) return Files.newInputStream(user);
        return resStream("images/" + name);
    }

    public static void init() {
        try (java.io.InputStream in = resStream("Font.ttf")) {
            if (in != null) {
                Font f = Font.loadFont(in, 13);
                if (f != null) baseFont = f;
            }
        } catch (Exception ignored) {
        }
        loadUserFonts();
        resetWallpaper();
    }

    public static Path fontDir() {
        return root().resolve("Fonts");
    }

    private static final java.util.List<String> USER_FONTS = new java.util.ArrayList<>();

    public static java.util.List<String> userFonts() {
        return java.util.Collections.unmodifiableList(USER_FONTS);
    }

    private static boolean fontsLoaded;

    public static synchronized void ensureUserFonts() {
        if (!fontsLoaded) loadUserFonts();
    }

    public static synchronized void loadUserFonts() {
        fontsLoaded = true;
        USER_FONTS.clear();
        try {
            Path dir = fontDir();
            if (!Files.isDirectory(dir)) return;
            try (var stream = Files.list(dir)) {
                for (Path p : stream.filter(Files::isRegularFile).limit(100).toList()) {
                    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!name.endsWith(".ttf") && !name.endsWith(".otf") && !name.endsWith(".ttc")) continue;
                    try {
                        if (Files.size(p) > 32L * 1024 * 1024) continue;
                    } catch (Exception ignored) {
                        continue;
                    }
                    try (java.io.InputStream in = Files.newInputStream(p)) {
                        Font f = Font.loadFont(in, 13);
                        if (f != null && !USER_FONTS.contains(f.getFamily())) USER_FONTS.add(f.getFamily());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        fontFamily = null;
        FONT_CACHE.clear();
    }

    private static String fontFamily;
    private static final java.util.Map<String, Font> FONT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static String fontFamily() {
        if (fontFamily != null) return fontFamily;
        String custom = "";
        try {
            custom = Data.get().get("UiCustomFont", "");
        } catch (Throwable ignored) {
        }
        java.util.List<String> families;
        try {
            families = Font.getFamilies();
        } catch (Throwable t) {
            families = java.util.List.of();
        }
        if (!custom.isBlank() && (USER_FONTS.contains(custom) || families.contains(custom))) {
            fontFamily = custom;
            return fontFamily;
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] candidates;
        if (os.contains("mac") || os.contains("darwin")) {
            candidates = new String[]{"PingFang SC", "Hiragino Sans GB", "Heiti SC", "Helvetica Neue", "Arial"};
        } else if (os.contains("windows")) {
            candidates = new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Arial"};
        } else {
            candidates = new String[]{"Noto Sans CJK SC", "Noto Sans CJK", "Source Han Sans CN",
                    "Source Han Sans SC", "WenQuanYi Micro Hei", "DejaVu Sans", "SansSerif"};
        }
        for (String c : candidates) {
            if (families.contains(c) || USER_FONTS.contains(c)) {
                fontFamily = c;
                return fontFamily;
            }
        }
        fontFamily = baseFont != null ? baseFont.getFamily()
                : Font.getDefault() != null ? Font.getDefault().getFamily() : "SansSerif";
        return fontFamily;
    }

    private static final java.util.concurrent.atomic.AtomicInteger wallpaperGen =
            new java.util.concurrent.atomic.AtomicInteger();

    public static void resetWallpaper() {
        final int gen = wallpaperGen.incrementAndGet();
        Threads.start("TCL-Wallpaper", () -> {
            Image img = loadImage("wallpaper.jpg");
            if (img == null) img = loadImage("TCraft.webp");
            final Image loaded = img;
            javafx.application.Platform.runLater(() -> {
                if (wallpaperGen.get() != gen) return;
                wallpaper = loaded;
                if (onWallpaperReady != null) onWallpaperReady.run();
            });
        });
    }

    public static boolean isWallpaperImage(Path file) {
        if (file == null) return false;
        String n = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
    }

    private static boolean usableWallpaper(Path file) {
        try {
            return Files.isRegularFile(file) && isWallpaperImage(file)
                    && Files.size(file) <= 32L * 1024 * 1024;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean loadCustomWallpaper(Path dir) {
        try {
            if (dir == null || !Files.isDirectory(dir)) return false;
            List<Path> images = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(Theme::usableWallpaper).limit(100).forEach(images::add);
            }
            if (images.isEmpty()) return false;
            // 与启动取色同一张图
            Path pick = pendingWallpaper != null && usableWallpaper(pendingWallpaper)
                    ? pendingWallpaper
                    : images.get((int) (Math.random() * images.size()));
            pendingWallpaper = null;
            currentWallpaperFile = pick;
            Image img = new Image(pick.toUri().toString(), 4096, 4096, true, true, true);
            if (img.isError()) return false;
            final int gen = wallpaperGen.incrementAndGet();
            final Image loaded = img;
            // 监听列表仅 FX 可改
            javafx.application.Platform.runLater(() -> img.errorProperty().addListener((o, was, now) -> {
                if (Boolean.TRUE.equals(now) && wallpaper == loaded) {
                    javafx.application.Platform.runLater(Theme::resetWallpaper);
                }
            }));
            javafx.application.Platform.runLater(() -> {
                if (wallpaperGen.get() != gen) return;
                // 解码失败可能早于监听
                if (loaded.isError()) {
                    resetWallpaper();
                    return;
                }
                wallpaper = loaded;
                if (onWallpaperReady != null) onWallpaperReady.run();
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 按字节预算的 LRU
    private static final java.util.Map<String, Image> IMAGE_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true));
    private static final long IMAGE_CACHE_BUDGET = 256L * 1024 * 1024;
    private static long imageCacheBytes;

    private static long imageBytes(Image img) {
        double w = img.getWidth(), h = img.getHeight();
        return w > 0 && h > 0 ? (long) (w * h * 4) : 0;
    }

    private static void cacheImage(String key, Image img) {
        synchronized (IMAGE_CACHE) {
            Image old = IMAGE_CACHE.put(key, img);
            if (old != null) imageCacheBytes -= imageBytes(old);
            imageCacheBytes += imageBytes(img);
            var it = IMAGE_CACHE.entrySet().iterator();
            // 失败图按条数兜底
            while ((imageCacheBytes > IMAGE_CACHE_BUDGET || IMAGE_CACHE.size() > 512)
                    && IMAGE_CACHE.size() > 1 && it.hasNext()) {
                var e = it.next();
                imageCacheBytes -= imageBytes(e.getValue());
                it.remove();
            }
        }
    }

    public static Image loadImage(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            if (name.contains("/") || name.contains("\\")) {
                Path abs = Paths.get(name);
                String raw = abs.toString();
                // 拒绝 UNC 网络路径 防止加载图片时泄露系统凭据
                boolean unc = raw.startsWith("\\\\") || raw.startsWith("//");
                if (abs.isAbsolute() && !unc && Files.isRegularFile(abs)) return cachedImage(abs);
            }
            Path user = userImage(name);
            if (user != null) return cachedImage(user);
            String key = "res:" + name.toLowerCase(java.util.Locale.ROOT);
            Image cached = IMAGE_CACHE.get(key);
            if (cached != null) return cached;
            try (java.io.InputStream in = resStream("images/" + name)) {
                if (in == null) return null;
                Image img = new Image(in, 0, 0, true, false);
                if (img.isError()) return null;
                cacheImage(key, img);
                return img;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Image cachedImage(Path p) {
        try {
            if (Files.size(p) > 64L * 1024 * 1024) return null;
            String key = p.toAbsolutePath() + "|" + Files.getLastModifiedTime(p).toMillis() + "|" + Files.size(p);
            Image cached = IMAGE_CACHE.get(key);
            if (cached != null) return cached;
            Image img = new Image(p.toUri().toString(), 4096, 4096, true, false);
            cacheImage(key, img);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    public static Font font(double size) {
        return cachedFont(FontWeight.NORMAL, size);
    }

    public static Font font(double size, boolean bold) {
        return cachedFont(bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
    }

    public static Font font(double size, FontWeight weight) {
        return cachedFont(weight, size);
    }

    private static Font cachedFont(FontWeight weight, double size) {
        String key = fontFamily() + '|' + weight.getWeight() + '|' + size;
        Font f = FONT_CACHE.get(key);
        if (f != null) return f;
        f = Font.font(fontFamily(), weight, size);
        if (FONT_CACHE.size() > 256) FONT_CACHE.clear();
        FONT_CACHE.put(key, f);
        return f;
    }

    public static void applyComponentOpacity() {
        int v = Data.get().getInt("UiComponentOpacity", 50);
        v = Math.max(0, Math.min(100, v));
        componentOpacity = v / 100.0;
        cardBg = Color.web("#ffffff", Math.min(1, 245 * componentOpacity / 255));
        fieldBg = Color.web("#ffffff", Math.min(1, (245 * componentOpacity + 25) / 255));
        listHoverBg = Color.color(brush7.getRed(), brush7.getGreen(), brush7.getBlue(),
                Math.min(1, 190 * componentOpacity / 255));
    }

    public static void applyPreset(int index) {
        if (index == 14) return; // 自定义不覆盖
        if (index >= 0 && index < THEME_COLORS.length) {
            Color c = Color.web(THEME_COLORS[index]);
            if (index == 12) {
                double hue = (accent.getHue() + 7) % 360;
                c = Color.hsb(hue, 0.85, c.getBrightness());
            } else if (index == 13) {
                java.util.Random r = new java.util.Random();
                c = Color.hsb(r.nextInt(360), 0.55 + r.nextDouble() * 0.45, c.getBrightness());
            }
            setAccent(c, false);
        }
    }

    // 关闭跟随时固化 accent
    public static void saveCurrentAsCustom(Data d) {
        Color c = accent;
        double hue = c.getHue();
        double max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
        double min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
        double light = (max + min) / 2;
        double sat = max <= min ? 0 : (max - min) / (1 - Math.abs(2 * light - 1));
        d.set("UiLauncherHue", (int) Math.round(hue));
        d.set("UiLauncherSat", (int) Math.round(clamp01(sat) * 100));
        d.set("UiLauncherLight", (int) Math.round(light * 100));
        d.set("UiLauncherDelta", 20);
        d.save();
    }

    public static void applyCustomTheme() {
        Data d = Data.get();
        double hue = d.getInt("UiLauncherHue", 210);
        double sat = d.getInt("UiLauncherSat", 72);
        double light = d.getInt("UiLauncherLight", 55);
        double delta = d.getInt("UiLauncherDelta", 20);
        setAccent(hsl(hue, sat, light), false);
        titleBar = hsl(hue + delta, sat, Math.max(14, light - 22));
    }

    // 启动用上次取色
    public static boolean applyCachedAccent() {
        try {
            Data d = Data.get();
            int hue = d.getInt("UiAccentCacheHue", -1);
            if (hue < 0) return false;
            int sat = Math.max(0, Math.min(100, d.getInt("UiAccentCacheSat", 60)));
            int light = Math.max(0, Math.min(100, d.getInt("UiAccentCacheLight", 50)));
            setAccent(hsl(hue, sat, light), true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void cacheAccent(Color c) {
        try {
            double max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue()));
            double min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
            double light = (max + min) / 2;
            double sat = max <= min ? 0 : (max - min) / (1 - Math.abs(2 * light - 1));
            Data d = Data.get();
            d.set("UiAccentCacheHue", (int) Math.round(c.getHue()));
            d.set("UiAccentCacheSat", (int) Math.round(clamp01(sat) * 100));
            d.set("UiAccentCacheLight", (int) Math.round(light * 100));
            d.set("UiAccentCacheWallpaper",
                    currentWallpaperFile == null ? "" : currentWallpaperFile.getFileName().toString());
            d.save();
        } catch (Throwable ignored) {
        }
    }

    private static volatile Path currentWallpaperFile;

    private static boolean accentChanged(Color a, Color b) {
        if (a == null || b == null) return true;
        double diff = Math.abs(a.getRed() - b.getRed())
                + Math.abs(a.getGreen() - b.getGreen())
                + Math.abs(a.getBlue() - b.getBlue());
        return diff > 0.06;
    }

    // 启动前先定主题色
    private static volatile Path pendingWallpaper;
    private static boolean skipInitialAccentExtract;
    private static boolean startupPrepared;

    public static void prepareStartupAccent(Path pictures) {
        if (startupPrepared) return;
        startupPrepared = true;
        try {
            Data d = Data.get();
            int preset = d.getInt("UiThemePreset", -1);
            if (preset == 14) {
                applyCustomTheme();
                return;
            }
            if (preset >= 0) {
                applyPreset(preset);
                return;
            }
            if (applyCachedAccent()) {
                // 沿用上次取色壁纸
                try {
                    String name = d.get("UiAccentCacheWallpaper", "");
                    if (!name.isBlank() && pictures != null) {
                        Path p = pictures.resolve(name);
                        if (Files.isRegularFile(p)) pendingWallpaper = p;
                    }
                } catch (Throwable ignored) {
                }
                skipInitialAccentExtract = true;
                return;
            }
            // 无缓存用缩图取色
            Image small = loadAccentPreview(pictures);
            if (small != null) {
                Color c = extractAccent(small);
                setAccent(c, true);
                cacheAccent(c);
                skipInitialAccentExtract = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private static Image loadAccentPreview(Path pictures) {
        try {
            String uri = null;
            if (pictures != null && Files.isDirectory(pictures)) {
                List<Path> images = new ArrayList<>();
                try (var stream = Files.list(pictures)) {
                    stream.filter(Theme::usableWallpaper).limit(100).forEach(images::add);
                }
                if (!images.isEmpty()) {
                    Path pick = images.get((int) (Math.random() * images.size()));
                    pendingWallpaper = pick;
                    currentWallpaperFile = pick;
                    uri = pick.toUri().toString();
                }
            }
            if (uri == null) {
                URL url = resUrl("images/wallpaper.jpg");
                if (url == null) url = resUrl("images/TCraft.webp");
                if (url == null) return null;
                uri = url.toExternalForm();
            }
            Image img = new Image(uri, 400, 400, true, false);
            return img.isError() ? null : img;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isHiddenTheme(int id) {
        for (int h : HIDDEN_THEMES) {
            if (h == id) return true;
        }
        return false;
    }

    public static java.util.List<Integer> unlockedHiddenIds() {
        String raw = Data.get().get("UiThemeUnlocked", "");
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (raw.isEmpty()) return out;
        java.util.Set<String> have = new java.util.HashSet<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) have.add(t);
        }
        for (int id : HIDDEN_THEMES) {
            if (have.contains(String.valueOf(id))) out.add(id);
        }
        return out;
    }

    public static boolean isThemeUnlocked(int id) {
        if (!isHiddenTheme(id)) return true;
        return unlockedHiddenIds().contains(id);
    }

    public static int earnedUnlockCount(int launches) {
        if (launches < 0) return 0;
        return Math.min(HIDDEN_THEMES.length, launches / UNLOCK_EVERY_LAUNCHES);
    }

    public static java.util.List<String> applyLaunchUnlocks(int launches) {
        java.util.List<Integer> have = unlockedHiddenIds();
        int target = earnedUnlockCount(launches);
        java.util.List<String> newly = new java.util.ArrayList<>();
        if (target <= have.size()) return newly;
        StringBuilder sb = new StringBuilder();
        for (Integer id : have) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        for (int i = have.size(); i < target; i++) {
            int id = HIDDEN_THEMES[i];
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
            newly.add(THEME_NAMES[id]);
        }
        Data d = Data.get();
        d.set("UiThemeUnlocked", sb.toString());
        d.save();
        return newly;
    }

    public static int launchesToNextUnlock(int launches) {
        java.util.List<Integer> have = unlockedHiddenIds();
        if (have.size() >= HIDDEN_THEMES.length) return 0;
        int next = (have.size() + 1) * UNLOCK_EVERY_LAUNCHES;
        return Math.max(0, next - Math.max(0, launches));
    }

    public static String nextHiddenThemeName() {
        int n = unlockedHiddenIds().size();
        return n >= HIDDEN_THEMES.length ? "" : THEME_NAMES[HIDDEN_THEMES[n]];
    }

    public static Color hsl(double h, double sPercent, double lPercent) {
        double s = Math.max(0, Math.min(100, sPercent)) / 100;
        double l = Math.max(0, Math.min(100, lPercent)) / 100;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = (((h % 360) + 360) % 360) / 60;
        double x = c * (1 - Math.abs(hp % 2 - 1));
        double r1, g1, b1;
        if (hp < 1) {
            r1 = c; g1 = x; b1 = 0;
        } else if (hp < 2) {
            r1 = x; g1 = c; b1 = 0;
        } else if (hp < 3) {
            r1 = 0; g1 = c; b1 = x;
        } else if (hp < 4) {
            r1 = 0; g1 = x; b1 = c;
        } else if (hp < 5) {
            r1 = x; g1 = 0; b1 = c;
        } else {
            r1 = c; g1 = 0; b1 = x;
        }
        double m = l - c / 2;
        return Color.color(clamp01(r1 + m), clamp01(g1 + m), clamp01(b1 + m));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    public static void setAccent(Color base, boolean deriveTitle) {
        double hue = base.getHue();
        double max = Math.max(base.getRed(), Math.max(base.getGreen(), base.getBlue()));
        double min = Math.min(base.getRed(), Math.min(base.getGreen(), base.getBlue()));
        double light = (max + min) / 2;
        double sat = max <= min ? 0 : (max - min) / (1 - Math.abs(2 * light - 1));
        double satP = clamp01(sat) * 100;
        double baseP = light * 100;
        accent = base;
        accentDark = hsl(hue, satP, baseP - 10);
        accentLight = hsl(hue, satP, baseP + 10);
        accentPale = hsl(hue, satP, baseP + 25);
        accentPale2 = hsl(hue, satP, baseP + 36);
        brush7 = hsl(hue, satP, baseP + 40);
        accentPale3 = hsl(hue, satP, baseP + 42);
        semiTransparent = Color.color(accentPale3.getRed(), accentPale3.getGreen(), accentPale3.getBlue(), 1 / 255.0);
        blueHintBg = brush7;
        titleBar = deriveTitle
                ? Color.hsb(hue, Math.min(0.85, base.getSaturation() * 0.82), Math.max(0.22, base.getBrightness() * 0.66))
                : accentLight;
        applyComponentOpacity();
        refreshCss();
    }

    private static URL cssUrl;
    private static Runnable cssReload;
    private static long cssGen;

    private static String hex(Color c) {
        return c.toString().replace("0x", "#");
    }

    private static void refreshCss() {
        if (cssUrl == null && cssReload == null) return;
        regenerateCss();
        if (cssReload != null) cssReload.run();
    }

    private static synchronized URL regenerateCss() {
        try {
            String css = resText("style.css");
            if (css.isEmpty()) return cssUrl;
            css = css.replace("#4890f5", hex(accentLight))
                    .replace("#1370f3", hex(accent))
                    .replace("#eaf2fe", hex(accentPale3));
            // CSS 文件名带代次与进程号
            Path out = Paths.get(System.getProperty("java.io.tmpdir"),
                    cssOwnPrefix() + Long.toHexString(System.currentTimeMillis()) + "-" + cssGen++ + ".css");
            Files.writeString(out, css, StandardCharsets.UTF_8);
            cssUrl = out.toUri().toURL();
            sweepThemeCss(out);
        } catch (Exception e) {
            cssUrl = resUrl("style.css");
        }
        return cssUrl;
    }

    private static String cssOwnPrefix() {
        return "tcraft-theme-" + ProcessHandle.current().pid() + "-";
    }

    // 仅留当前代 旧文件延迟清理
    private static void sweepThemeCss(Path keep) {
        String own = cssOwnPrefix();
        try (var stream = Files.list(Paths.get(System.getProperty("java.io.tmpdir")))) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!n.startsWith("tcraft-theme-") || !n.endsWith(".css")) return false;
                if (n.startsWith(own)) return !p.equals(keep);
                try {
                    return System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() > 86400_000L;
                } catch (Exception e) {
                    return false;
                }
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    public static void onCssReload(Runnable r) {
        cssReload = r;
    }

    public static Color extractAccent(Image image) {
        if (image == null) return Color.web("#1370f3");
        PixelReader pr = image.getPixelReader();
        if (pr == null) return Color.web("#1370f3");
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        double[] weights = new double[36];
        double[] rs = new double[36], gs = new double[36], bs = new double[36];
        for (int y = 0; y < h; y += Math.max(1, h / 120)) {
            for (int x = 0; x < w; x += Math.max(1, w / 160)) {
                Color c = pr.getColor(x, y);
                double sat = c.getSaturation(), bri = c.getBrightness();
                if (sat < 0.22 || bri < 0.18 || bri > 0.97) continue;
                int idx = (int) (c.getHue() / 10) % 36;
                double weight = sat * (1 - Math.abs(bri - 0.6));
                weights[idx] += weight;
                rs[idx] += c.getRed() * weight;
                gs[idx] += c.getGreen() * weight;
                bs[idx] += c.getBlue() * weight;
            }
        }
        int best = -1;
        for (int i = 0; i < weights.length; i++) if (best < 0 || weights[i] > weights[best]) best = i;
        if (best < 0 || weights[best] <= 0) return Color.web("#1370f3");
        Color avg = Color.color(rs[best] / weights[best], gs[best] / weights[best], bs[best] / weights[best]);
        return Color.hsb(avg.getHue(), Math.max(0.42, Math.min(0.78, avg.getSaturation() * 0.9)), 0.62);
    }

    public static void applyWallpaperTheme() {
        // 首挂壁纸不重复取色
        if (skipInitialAccentExtract) {
            skipInitialAccentExtract = false;
            return;
        }
        Image img = wallpaper;
        if (img == null) return;
        // 取色放后台
        if (img.getProgress() < 1) {
            javafx.beans.value.ChangeListener<Boolean> onError =
                    new javafx.beans.value.ChangeListener<>() {
                        @Override
                        public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o,
                                            Boolean a, Boolean b) {
                            if (!Boolean.TRUE.equals(b)) return;
                            img.errorProperty().removeListener(this);
                        }
                    };
            javafx.beans.value.ChangeListener<Number> onWidth =
                    new javafx.beans.value.ChangeListener<>() {
                        @Override
                        public void changed(javafx.beans.value.ObservableValue<? extends Number> o,
                                            Number a, Number b) {
                            img.widthProperty().removeListener(this);
                            img.errorProperty().removeListener(onError);
                            if (b.doubleValue() > 0) extractAccentInBackground(img);
                        }
                    };
            img.widthProperty().addListener(onWidth);
            // 加载失败移除监听
            img.errorProperty().addListener(onError);
            return;
        }
        extractAccentInBackground(img);
    }

    private static void extractAccentInBackground(Image img) {
        Threads.start("TCL-Accent", () -> {
            // PixelReader 未就绪则跳过
            if (img.getProgress() < 1) return;
            Color c = extractAccent(img);
            cacheAccent(c);
            javafx.application.Platform.runLater(() -> {
                Color before = accent;
                setAccent(c, true);
                Runnable notify = onAccentApplied;
                if (notify != null && accentChanged(before, accent)) notify.run();
            });
        });
    }

    private static final java.util.Map<String, String> SVG_CACHE = new java.util.HashMap<>();

    private static String svgText(String name) {
        if (SVG_CACHE.containsKey(name)) {
            String cached = SVG_CACHE.get(name);
            return cached.isEmpty() ? null : cached;
        }
        String text = resText("icons/lucide/" + name + ".svg");
        if (!text.isEmpty()) {
            SVG_CACHE.put(name, text);
            return text;
        }
        SVG_CACHE.put(name, "");
        return null;
    }

    public static Node icon(String name, double size, Color color) {
        Pane pane = new Pane();
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.setMouseTransparent(true);
        String svg = svgText(name);
        Group group = new Group();
        if (svg != null) {
            for (String[] seg : elems(svg, "path")) {
                String d = attr(seg[0], "d");
                if (d == null) continue;
                String fill = attr(seg[0], "fill");
                if (fill != null && !fill.equals("none")) {
                    group.getChildren().add(fillPath(d, color));
                } else {
                    group.getChildren().add(strokePath(d, color));
                }
            }
            for (String[] c : elems(svg, "circle")) {
                Circle circle = new Circle(num(c, "cx", 12), num(c, "cy", 12), num(c, "r", 0));
                styleShape(circle, color);
                group.getChildren().add(circle);
            }
            for (String[] r : elems(svg, "rect")) {
                Rectangle rect = new Rectangle(num(r, "x", 0), num(r, "y", 0), num(r, "width", 0), num(r, "height", 0));
                double rx = num(r, "rx", 0);
                rect.setArcWidth(rx * 2);
                rect.setArcHeight(rx * 2);
                styleShape(rect, color);
                group.getChildren().add(rect);
            }
            for (String[] l : elems(svg, "line")) {
                Line line = new Line(num(l, "x1", 0), num(l, "y1", 0), num(l, "x2", 0), num(l, "y2", 0));
                styleShape(line, color);
                group.getChildren().add(line);
            }
            for (String[] pl : elems(svg, "polyline")) {
                Polyline line = new Polyline(points(pl));
                line.setFill(Color.TRANSPARENT);
                styleShape(line, color);
                group.getChildren().add(line);
            }
            for (String[] pg : elems(svg, "polygon")) {
                Polygon poly = new Polygon(points(pg));
                styleShape(poly, color);
                group.getChildren().add(poly);
            }
        } else {
            Circle c = new Circle(12, 12, 8);
            styleShape(c, color);
            group.getChildren().add(c);
        }
        // viewBox 支持任意底 null 按默认 24
        double vbx = 24;
        if (svg != null) {
            int rootStart = svg.indexOf("<svg");
            if (rootStart >= 0) {
                int rootEnd = svg.indexOf(">", rootStart);
                if (rootEnd > rootStart) {
                    String vb = attr(svg.substring(rootStart, rootEnd), "viewBox");
                    if (vb != null) {
                        String[] parts = vb.trim().split("[\\s,]+");
                        if (parts.length >= 4) {
                            try {
                                double w = Double.parseDouble(parts[2]);
                                if (w > 0) vbx = w;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        }
        double scale = size / vbx;
        group.getTransforms().add(new Scale(scale, scale));
        pane.getChildren().add(group);
        StackPaneAlign.center(pane);
        return pane;
    }

    public static Node icon(String name, double size) {
        return icon(name, size, text);
    }

    private static void styleShape(Shape s, Color color) {
        s.setFill(Color.TRANSPARENT);
        s.setStroke(color);
        s.setStrokeWidth(2);
        s.setStrokeLineCap(StrokeLineCap.ROUND);
        s.setStrokeLineJoin(StrokeLineJoin.ROUND);
    }

    private static SVGPath strokePath(String d, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        styleShape(p, color);
        return p;
    }

    private static SVGPath fillPath(String d, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        p.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        p.setFill(color);
        return p;
    }

    private static List<String[]> elems(String svg, String tag) {
        List<String[]> out = new ArrayList<>();
        int idx = 0;
        String open = "<" + tag;
        while ((idx = svg.indexOf(open, idx)) >= 0) {
            int end = svg.indexOf(">", idx);
            if (end < 0) break;
            String seg = svg.substring(idx, end);
            out.add(new String[]{seg});
            idx = end;
        }
        return out;
    }

    private static String attr(String seg, String name) {
        String key = name + "=\"";
        int i = seg.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = seg.indexOf('"', start);
        return end < 0 ? null : seg.substring(start, end);
    }

    private static double num(String[] holder, String name, double def) {
        String s = attr(holder[0], name);
        if (s == null) return def;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static double[] points(String[] holder) {
        String s = attr(holder[0], "points");
        if (s == null) return new double[0];
        String[] parts = s.trim().split("[\\s,]+");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i]);
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static DropShadow shadow(double radius, double offsetY, double opacity, Color color) {
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
        DropShadow ds = new DropShadow(radius, 0, offsetY, c);
        return ds;
    }

    public static Node headImage(Image skin, double size) {
        int scale = Math.max(1, (int) Math.floor(skin.getWidth() / 64.0));
        int tex = 8 * scale;
        int s = (int) Math.round(size);
        PixelReader pr = skin.getPixelReader();
        // 64x32 is the smallest valid skin layout; smaller or unreadable images cannot be cropped.
        if (pr == null || skin.getWidth() < 64 || skin.getHeight() < 32) {
            javafx.scene.layout.StackPane fallback = new javafx.scene.layout.StackPane(new ImageView(skin));
            fallback.setMinSize(size, size);
            fallback.setPrefSize(size, size);
            fallback.setMaxSize(size, size);
            Rectangle fallbackClip = new Rectangle(size, size);
            fallbackClip.setArcWidth(3);
            fallbackClip.setArcHeight(3);
            fallback.setClip(fallbackClip);
            return fallback;
        }
        // 预渲染 1:1 位图
        WritableImage out = new WritableImage(s, s);
        javafx.scene.image.PixelWriter pw = out.getPixelWriter();
        for (int py = 0; py < s; py++) {
            int ty = texelAt(py, s, tex);
            for (int px = 0; px < s; px++) {
                int tx = texelAt(px, s, tex);
                int face = pr.getArgb(8 * scale + tx, 8 * scale + ty);
                int hat = pr.getArgb(40 * scale + tx, 8 * scale + ty);
                pw.setArgb(px, py, hatOver(hat, face));
            }
        }
        javafx.scene.layout.StackPane head = new javafx.scene.layout.StackPane();
        head.setMinSize(size, size);
        head.setPrefSize(size, size);
        head.setMaxSize(size, size);
        head.getChildren().add(new ImageView(out));
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(3);
        clip.setArcHeight(3);
        head.setClip(clip);
        return head;
    }

    // texel 中心对齐的最近邻采样
    private static int texelAt(int px, int out, int tex) {
        return Math.min(tex - 1, (int) ((px + 0.5) * tex / out));
    }

    // 帽子层压在面部层上
    private static int hatOver(int hat, int face) {
        int ha = (hat >>> 24) & 0xFF;
        if (ha == 0) return face;
        if (ha == 0xFF) return hat;
        int fa = (face >>> 24) & 0xFF;
        int a = ha + fa * (0xFF - ha) / 0xFF;
        if (a == 0) return 0;
        int r = ((hat >> 16 & 0xFF) * ha + (face >> 16 & 0xFF) * fa * (0xFF - ha) / 0xFF) / a;
        int g = ((hat >> 8 & 0xFF) * ha + (face >> 8 & 0xFF) * fa * (0xFF - ha) / 0xFF) / a;
        int b = ((hat & 0xFF) * ha + (face & 0xFF) * fa * (0xFF - ha) / 0xFF) / a;
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static Image snapshot(Node node) {
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        return node.snapshot(p, null);
    }

    public static URL css() {
        if (cssUrl != null) return cssUrl;
        return regenerateCss();
    }

    private static final class StackPaneAlign {
        static void center(Node node) {
            if (node instanceof Region) {
                ((Region) node).setLayoutX(0);
                ((Region) node).setLayoutY(0);
            }
            node.setStyle("-fx-alignment: center;");
        }
    }
    public static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
