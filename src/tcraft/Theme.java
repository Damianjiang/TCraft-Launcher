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

/** 全局主题、图标、字体和动画工具。 */
public final class Theme {

    // 色阶由 setAccent 按 HSL 派生，下面只是云溪蓝初始值
    public static Color accent = Color.web("#1370f3");
    public static Color accentDark = Color.web("#0b5bcb");
    public static Color accentLight = Color.web("#4890f5");
    public static Color accentPale = Color.web("#96c0f9");
    public static Color accentPale2 = Color.web("#d5e6fd");
    public static Color accentPale3 = Color.web("#eaf2fe");
    public static Color titleBar = Color.web("#4890f5");

    public static Color text = Color.web("#343d4a");
    // 0 全透明到 100 全不透明，只缩放卡片、列表、输入框的 alpha
    public static double componentOpacity = 0.5;
    // 卡片底色 alpha = 245 * factor
    public static Color cardBg = Color.web("#ffffff", 245 * 0.5 / 255);
    public static Color sidebarBg = Color.web("#ffffff", 0.945);
    /** 弹窗底色，始终不透明。 */
    public static final Color dialogBg = Color.web("#fbfbfb");
    // 弹窗遮罩：普通纯黑约 35%，警告暗红约 55%
    public static final Color modalMask = Color.web("#000000", 90 / 255.0);
    public static final Color warnMask = Color.color(80 / 255.0, 0, 0, 140 / 255.0);
    // 输入框底色 alpha = 245 * factor + 25
    public static Color fieldBg = Color.web("#ffffff", Math.min(1, (245 * 0.5 + 25) / 255));
    // 列表悬停底色，alpha 随组件透明度缩放
    public static Color listHoverBg = Color.web("#e0eafd", 190 * 0.5 / 255);
    // alpha 仅 1/255，药丸控件的常态底
    public static Color semiTransparent = Color.web("#eaf2fe", 1 / 255.0);
    // 显式给 alpha，避免 8 位 hex 按 RRGGBBAA 解析
    public static Color halfWhite = Color.color(1, 1, 1, 0x55 / 255.0);
    public static Color white = Color.WHITE;
    // 按钮和输入框的悬停底色
    public static Color brush7 = Color.web("#e0eafd");
    public static Color redBack = Color.color(251 / 255.0, 221 / 255.0, 221 / 255.0, 0x80 / 255.0);
    // 深色半透明填充，悬停略亮
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
            // 只能末尾追加，UiThemePreset 存的是下标
            "深海青", "苔藓绿", "橄榄金", "落霞橙", "豆沙红", "藕荷紫", "墨绿", "石板灰", "柠檬黄", "月白蓝"
    };
    public static final String[] THEME_COLORS = {
            "#1370f3", "#3b8f7a", "#2f7a43", "#d6a01e", "#87592e", "#30343e", "#e2749a", "#7357c8",
            "#c98a2b", "#e8621d", "#d23329", "#3f9cc7", "#e05a8a", "#7a54d6", "#1370f3", "#86bf2a",
            "#2fbf92", "#24408f", "#8b3fc0", "#5b7a94", "#d99a8a",
            // 下标 21~30 用低饱和灰调，太艳会糊在白色界面上
            "#0e6b7a", "#5d7a3a", "#8a7b2f", "#d97a45", "#a85757", "#a273b8", "#2b5f52", "#55606e", "#c9b52a", "#6f8fb8"
    };

    // 每累计启动 99 次按 HIDDEN_THEMES 顺序解锁一个，UiThemeUnlocked 存已解锁 id
    public static final int[] HIDDEN_THEMES = {
            6,
            7,
            8,
            20,
            26
    };

    /** 解锁一个隐藏主题需要的累计启动次数。 */
    public static final int UNLOCK_EVERY_LAUNCHES = 99;

    public static Image wallpaper;
    private static final Path ROOT;
    public static Font baseFont;

    static {
        // 目录版用程序旁的 res，单文件版用用户目录解压的那份
        Path dir = Resources.appDir();
        ROOT = Files.isDirectory(dir.resolve("res")) ? dir : Resources.home();
    }

    private Theme() {
    }

    public static Path root() {
        return ROOT;
    }

    public static Path res(String relative) {
        return ROOT.resolve("res").resolve(relative);
    }

    public static void init() {
        try {
            Font f = Font.loadFont(res("Font.ttf").toUri().toString(), 13);
            if (f != null) baseFont = f;
        } catch (Exception ignored) {
        }
        resetWallpaper();
    }

    public static void resetWallpaper() {
        wallpaper = loadImage("wallpaper.jpg");
        if (wallpaper == null) wallpaper = loadImage("TCraft.webp");
    }

    /** 从 TCL/Pictures 随机挑一张背景图。 */
    public static boolean loadCustomWallpaper(Path dir) {
        try {
            if (dir == null || !Files.isDirectory(dir)) return false;
            List<Path> images = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
                }).forEach(images::add);
            }
            if (images.isEmpty()) return false;
            Path pick = images.get((int) (Math.random() * images.size()));
            Image img = new Image(pick.toUri().toString(), false);
            if (img.isError()) return false;
            wallpaper = img;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 图片缓存以路径、修改时间和大小做键。 */
    private static final java.util.Map<String, Image> IMAGE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Image loadImage(String name) {
        try {
            Path p = res("images/" + name);
            if (!Files.exists(p)) {
                // 文件名与代码大小写不一致时兜底找一次（磁盘和 jar 内区分大小写）。
                Path alt = findIgnoreCase(res("images"), name);
                if (alt != null) p = alt;
            }
            if (!Files.exists(p)) {
                // 也支持绝对路径的用户文件
                if (name != null && (name.contains("/") || name.contains("\\"))) {
                    Path abs = Paths.get(name);
                    if (Files.isRegularFile(abs)) return cachedImage(abs);
                }
                return null;
            }
            return cachedImage(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path findIgnoreCase(Path dir, String name) {
        if (dir == null || name == null) return null;
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            return s.filter(x -> x.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Image cachedImage(Path p) {
        try {
            String key = p.toAbsolutePath() + "|" + Files.getLastModifiedTime(p).toMillis() + "|" + Files.size(p);
            Image cached = IMAGE_CACHE.get(key);
            if (cached != null) return cached;
            if (IMAGE_CACHE.size() > 512) IMAGE_CACHE.clear();
            Image img = new Image(p.toUri().toString(), false);
            IMAGE_CACHE.put(key, img);
            return img;
        } catch (Exception e) {
            return new Image(p.toUri().toString(), false);
        }
    }

        public static Font font(double size) {
        return Font.font("Microsoft YaHei UI", size);
    }

    public static Font font(double size, boolean bold) {
        return Font.font("Microsoft YaHei UI", bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
    }

    public static Font font(double size, FontWeight weight) {
        return Font.font("Microsoft YaHei UI", weight, size);
    }

    /** 按组件透明度重算半透明表面色。 */
    public static void applyComponentOpacity() {
        int v = Data.get().getInt("UiComponentOpacity", 50);
        v = Math.max(0, Math.min(100, v));
        componentOpacity = v / 100.0;
        cardBg = Color.web("#ffffff", Math.min(1, 245 * componentOpacity / 255));
        fieldBg = Color.web("#ffffff", Math.min(1, (245 * componentOpacity + 25) / 255));
        // 列表悬停 alpha = 190 * factor，色相跟主题走
        listHoverBg = Color.color(brush7.getRed(), brush7.getGreen(), brush7.getBlue(),
                Math.min(1, 190 * componentOpacity / 255));
    }

    public static void applyPreset(int index) {
        if (index == 14) return; // 自定义不覆盖
        if (index >= 0 && index < THEME_COLORS.length) {
            Color c = Color.web(THEME_COLORS[index]);
            if (index == 12) {
                // 七曜彩每次色相加 7，饱和 85
                double hue = (accent.getHue() + 7) % 360;
                c = Color.hsb(hue, 0.85, c.getBrightness());
            } else if (index == 13) {
                // 曜光彩随机色相，饱和 55 到 100
                java.util.Random r = new java.util.Random();
                c = Color.hsb(r.nextInt(360), 0.55 + r.nextDouble() * 0.45, c.getBrightness());
            }
            setAccent(c, false);
        }
    }

    /** 不在 HIDDEN_THEMES 里的都是公开主题。 */
    public static boolean isHiddenTheme(int id) {
        for (int h : HIDDEN_THEMES) {
            if (h == id) return true;
        }
        return false;
    }

    /** 已解锁的隐藏主题 id，按 HIDDEN_THEMES 顺序返回。 */
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

    /** 公开主题恒可选，隐藏主题看解锁记录。 */
    public static boolean isThemeUnlocked(int id) {
        if (!isHiddenTheme(id)) return true;
        return unlockedHiddenIds().contains(id);
    }

    /** 按累计启动次数算出应解锁的隐藏主题数量。 */
    public static int earnedUnlockCount(int launches) {
        if (launches < 0) return 0;
        return Math.min(HIDDEN_THEMES.length, launches / UNLOCK_EVERY_LAUNCHES);
    }

    /** 补齐应解锁但未写入配置的隐藏主题，返回新解锁的名字。 */
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
        d.save(); // 立即落盘
        return newly;
    }

    /** 距离下一个隐藏主题还差几次启动，全解锁返回 0。 */
    public static int launchesToNextUnlock(int launches) {
        java.util.List<Integer> have = unlockedHiddenIds();
        if (have.size() >= HIDDEN_THEMES.length) return 0;
        int next = (have.size() + 1) * UNLOCK_EVERY_LAUNCHES;
        return Math.max(0, next - Math.max(0, launches));
    }

    /** 下一个待解锁的隐藏主题名，全解锁返回空串。 */
    public static String nextHiddenThemeName() {
        int n = unlockedHiddenIds().size();
        return n >= HIDDEN_THEMES.length ? "" : THEME_NAMES[HIDDEN_THEMES[n]];
    }

    /** HSL 转 RGB，S/L 用 0~100 百分比。 */
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

    /** 按主题色重算整套色阶，悬停和浅色表面也要跟着变。 */
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
        brush7 = hsl(hue, satP, baseP + 40);  // 按钮和输入框悬停底
        accentPale3 = hsl(hue, satP, baseP + 42);
        semiTransparent = Color.color(accentPale3.getRed(), accentPale3.getGreen(), accentPale3.getBlue(), 1 / 255.0);
        blueHintBg = brush7;
        titleBar = deriveTitle
                ? Color.hsb(hue, Math.min(0.85, base.getSaturation() * 0.82), Math.max(0.22, base.getBrightness() * 0.66))
                : accentLight;
        applyComponentOpacity(); // 表面色跟着新色相重算
        refreshCss();
    }

    private static URL cssUrl;
    private static Runnable cssReload;

    private static String hex(Color c) {
        return c.toString().replace("0x", "#");
    }

    /** 主题色变化后重生成样式表；场景挂上后每次换色都会同步。 */
    private static void refreshCss() {
        if (cssUrl == null && cssReload == null) return; // 场景还没建，建场景时按当前色生成
        regenerateCss();
        if (cssReload != null) cssReload.run();
    }

    private static synchronized URL regenerateCss() {
        try {
            // style.css 里这几个蓝色标记换成当前色板，走 CSS 的控件（弹层、菜单、勾选框等）就跟主题走
            String css = Files.readString(res("style.css"), StandardCharsets.UTF_8);
            css = css.replace("#4890f5", hex(accentLight))
                    .replace("#1370f3", hex(accent))
                    .replace("#eaf2fe", hex(accentPale3));
            Path out = Paths.get(System.getProperty("java.io.tmpdir"), "tcraft-theme.css");
            Files.writeString(out, css, StandardCharsets.UTF_8);
            cssUrl = out.toUri().toURL();
        } catch (Exception e) {
            try {
                cssUrl = res("style.css").toUri().toURL();
            } catch (Exception ignored) {
            }
        }
        return cssUrl;
    }

    /** 场景注册回调：主题色变化时把新样式表挂回去。 */
    public static void onCssReload(Runnable r) {
        cssReload = r;
    }

    /** 从背景图提取主色调。 */
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
        // 保持色相，收窄亮度和饱和度
        return Color.hsb(avg.getHue(), Math.max(0.42, Math.min(0.78, avg.getSaturation() * 0.9)), 0.62);
    }

    public static void applyWallpaperTheme() {
        setAccent(extractAccent(wallpaper), true);
    }

    private static final java.util.Map<String, String> SVG_CACHE = new java.util.HashMap<>();

    private static String svgText(String name) {
        if (SVG_CACHE.containsKey(name)) {
            String cached = SVG_CACHE.get(name);
            return cached.isEmpty() ? null : cached;
        }
        try {
            Path p = res("icons/lucide/" + name + ".svg");
            if (Files.exists(p)) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                SVG_CACHE.put(name, text);
                return text;
            }
        } catch (IOException ignored) {
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
                // 带 fill 属性的按实心渲染（TCl 参考图标），lucide 全是描边
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
            // 缺图标时画圆点占位
            Circle c = new Circle(12, 12, 8);
            styleShape(c, color);
            group.getChildren().add(c);
        }
        // viewBox 支持 24 以外的底（TCl 参考图标是 1024 底的填充路径）
        // 图标缺失时 svg 为 null，跳过解析，否则 UI 线程 NPE
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

    /** 实心图标路径；WPF 参考实现默认 EvenOdd 挖空，JavaFX 默认 NonZero 出不来镂空。 */
    private static SVGPath fillPath(String d, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        p.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        p.setFill(color);
        return p;
    }

    private static List<String> attrs(String svg, String tag, String attr) {
        List<String> out = new ArrayList<>();
        int idx = 0;
        String open = "<" + tag;
        while ((idx = svg.indexOf(open, idx)) >= 0) {
            int end = svg.indexOf(">", idx);
            if (end < 0) break;
            String seg = svg.substring(idx, end);
            String val = attr(seg, attr);
            if (val != null) out.add(val);
            idx = end;
        }
        return out;
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

    /** 几何居中助手。 */
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
