package tcraft;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
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
    // 显式给 alpha，8 位 hex 会被当 RRGGBBAA 解析
    public static Color halfWhite = Color.color(1, 1, 1, 0x55 / 255.0);
    public static Color white = Color.WHITE;
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
    public static Color gray7 = Color.web("#f0f0f0");

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

    // 每 99 次启动按 HIDDEN_THEMES 顺序解锁，UiThemeUnlocked 记录已解锁 id
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

    // 壁纸就绪（已赋值、可渲染）后在 FX 线程回调，MainWindow 用来刷背景与主题色
    public static volatile Runnable onWallpaperReady;

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

    public static Path skinFile(String fileName) {
        return skinDir().resolve(fileName);
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
                Data.get().log("资源索引构建失败（jar）：" + e.getMessage());
            }
        } else {
            Path dir = Resources.appDir().resolve("res");
            try (var walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String rel = "res/" + dir.relativize(p).toString().replace('\\', '/');
                    map.put(rel.toLowerCase(java.util.Locale.ROOT), rel);
                });
            } catch (Exception e) {
                Data.get().log("资源索引构建失败（res 目录）：" + e.getMessage());
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
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Path userImage(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.startsWith("Skins/")) {
            Path p = skinDir().resolve(name.substring(6));
            return Files.isRegularFile(p) ? p : null;
        }
        if (name.startsWith("Icons/")) {
            Path p = iconDir().resolve(name.substring(6));
            return Files.isRegularFile(p) ? p : null;
        }
        Path skin = skinDir().resolve(name);
        return Files.isRegularFile(skin) ? skin : null;
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

    public static synchronized void loadUserFonts() {
        USER_FONTS.clear();
        try {
            Path dir = fontDir();
            if (!Files.isDirectory(dir)) return;
            try (var stream = Files.list(dir)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!name.endsWith(".ttf") && !name.endsWith(".otf") && !name.endsWith(".ttc")) continue;
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
        if (os.contains("mac")) {
            candidates = new String[]{"PingFang SC", "Hiragino Sans GB", "Heiti SC", "Helvetica Neue", "Arial"};
        } else if (os.contains("win")) {
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
        // 整屏壁纸同步解码会把 FX 线程卡住，挪后台，完成后回 FX 赋值
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

    public static boolean loadCustomWallpaper(Path dir) {
        try {
            if (dir == null || !Files.isDirectory(dir)) return false;
            List<Path> images = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).filter(Theme::isWallpaperImage).forEach(images::add);
            }
            if (images.isEmpty()) return false;
            Path pick = images.get((int) (Math.random() * images.size()));
            // 壁纸是整屏大图，同步解码会把 FX 线程卡住，交给后台解码
            Image img = new Image(pick.toUri().toString(), true);
            if (img.isError()) return false;
            final int gen = wallpaperGen.incrementAndGet();
            final Image loaded = img;
            img.errorProperty().addListener((o, was, now) -> {
                if (Boolean.TRUE.equals(now) && wallpaper == loaded) {
                    javafx.application.Platform.runLater(Theme::resetWallpaper);
                }
            });
            javafx.application.Platform.runLater(() -> {
                if (wallpaperGen.get() != gen) return;
                wallpaper = loaded;
                if (onWallpaperReady != null) onWallpaperReady.run();
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 按字节预算的 LRU，整屏壁纸一张就几十 MB，按条数清表太粗暴
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
            // 字节预算为主；解码失败的图宽高是 0 计不了字节，留个条数兜底
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
            // 绝对路径的用户文件
            if (name.contains("/") || name.contains("\\")) {
                Path abs = Paths.get(name);
                if (Files.isRegularFile(abs)) return cachedImage(abs);
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
            String key = p.toAbsolutePath() + "|" + Files.getLastModifiedTime(p).toMillis() + "|" + Files.size(p);
            Image cached = IMAGE_CACHE.get(key);
            if (cached != null) return cached;
            Image img = new Image(p.toUri().toString(), false);
            cacheImage(key, img);
            return img;
        } catch (Exception e) {
            return new Image(p.toUri().toString(), false);
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

    // 把当前 accent 固化为自定义主题参数，供关掉"跟随壁纸"时保留取到的颜色
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
            // 文件名带代次：StyleManager 按 URL 缓存样式表，同路径换内容不会重解析
            Path out = Paths.get(System.getProperty("java.io.tmpdir"),
                    "tcraft-theme-" + Long.toHexString(System.currentTimeMillis()) + "-" + cssGen++ + ".css");
            Files.writeString(out, css, StandardCharsets.UTF_8);
            cssUrl = out.toUri().toURL();
            sweepThemeCss(out);
        } catch (Exception e) {
            cssUrl = resUrl("style.css");
        }
        return cssUrl;
    }

    // 临时目录只留当前代，旧文件在下次生成时删掉（可能还被旧场景引用，不能提前删）
    private static void sweepThemeCss(Path keep) {
        try (var stream = Files.list(Paths.get(System.getProperty("java.io.tmpdir")))) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return n.startsWith("tcraft-theme-") && n.endsWith(".css");
            }).forEach(p -> {
                if (p.equals(keep)) return;
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

    // 启动早期同步取壁纸主色，避免主窗口首帧用默认蓝再跳成目标色
    public static void applyWallpaperThemeSync() {
        Image img = loadImage("wallpaper.jpg");
        if (img == null) img = loadImage("TCraft.webp");
        if (img == null) return;
        setAccent(extractAccent(img), true);
    }

    public static void applyWallpaperTheme() {
        Image img = wallpaper;
        if (img == null) return;
        // 取色是上万次像素扫描，放后台；壁纸还在异步解码时 pixelReader 拿不到，等加载完再算
        if (img.isBackgroundLoading() && img.getProgress() < 1) {
            img.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> o, Number a, Number b) {
                    img.widthProperty().removeListener(this);
                    if (b.doubleValue() > 0) extractAccentInBackground(img);
                }
            });
            return;
        }
        extractAccentInBackground(img);
    }

    private static void extractAccentInBackground(Image img) {
        Threads.start("TCL-Accent", () -> {
            Color c = extractAccent(img);
            javafx.application.Platform.runLater(() -> setAccent(c, true));
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
        // viewBox 支持 24 以外的底（TCl 参考图标是 1024 底的填充路径）；svg 为 null 时按默认 24 缩放
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

    public static ImageView headImage(Image skin, double size) {
        ImageView iv = new ImageView(skin);
        iv.setViewport(new javafx.geometry.Rectangle2D(8, 8, 8, 8));
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(false);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(3);
        clip.setArcHeight(3);
        iv.setClip(clip);
        return iv;
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
