package tcraft;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Lang {

    public static volatile int mode = 0;
    public static volatile int setting = 0;
    // 整体换表 读不到半张
    private static volatile Map<String, String[]> TABLE = Map.of();

    static {
        reload();
    }

    private Lang() {
    }

    public static void reload() {
        Map<String, String[]> fresh = new HashMap<>();
        try {
            String text = loadTableText();
            if (!text.isEmpty()) {
                // 记事本 UTF-8 可能带 BOM
                if (text.charAt(0) == '\uFEFF') text = text.substring(1);
                for (String line : text.split("\\R")) {
                    if (line.isEmpty()) continue;
                    String[] cols = line.split("\t", -1);
                    if (cols.length < 2) continue;
                    String zh = unescape(cols[0]);
                    if (zh.isEmpty() || fresh.containsKey(zh)) continue;
                    String en = unescape(cols[1]);
                    String tw = cols.length > 2 && !cols[2].isEmpty() ? unescape(cols[2]) : zh;
                    fresh.put(zh, new String[]{en, tw});
                }
            }
        } catch (Exception ignored) {
        }
        TABLE = fresh;
    }

    // JavaFX 前读表 不能经 Theme 会 NoClassDefFoundError
    private static String loadTableText() {
        try {
            Path code = java.nio.file.Paths.get(
                    Lang.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (java.nio.file.Files.isRegularFile(code)) {
                // 单文件版从 jar 读 res
                try (java.util.jar.JarFile jf = new java.util.jar.JarFile(code.toFile())) {
                    var entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry en = entries.nextElement();
                        if ("res/langtables.txt".equalsIgnoreCase(en.getName()) && !en.isDirectory()) {
                            return new String(jf.getInputStream(en).readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
            } else {
                // 开发态从 classes 向上找 res/
                Path cur = code.getParent();
                for (int i = 0; i < 5 && cur != null; i++) {
                    Path f = cur.resolve("res").resolve("LangTables.txt");
                    if (java.nio.file.Files.isRegularFile(f)) {
                        return java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    cur = cur.getParent();
                }
            }
        } catch (Throwable ignored) {
        }
        // 兜底走 classpath
        try (var in = Lang.class.getResourceAsStream("/res/LangTables.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }

    public static String t(String zh) {
        if (mode == 0 || zh == null) return zh;
        String[] v = TABLE.get(zh);
        if (v == null) return zh;
        return mode == 1 ? v[0] : v[1];
    }

    public static String tf(String zh, Object... args) {
        String pattern = t(zh);
        if (args == null || args.length == 0) return pattern;
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            try {
                return String.format(zh, args);
            } catch (Exception ignored) {
                return zh;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void localize(javafx.scene.Node node) {
        if (node == null || mode == 0) return;
        if (node instanceof javafx.scene.control.Labeled l) {
            String s = l.getText();
            if (s != null && !s.isEmpty()) l.setText(t(s));
        } else if (node instanceof javafx.scene.text.Text tx) {
            String s = tx.getText();
            if (s != null && !s.isEmpty()) tx.setText(t(s));
        }
        // ComboBox 选项非 Labeled 单独翻译
        if (node instanceof javafx.scene.control.ComboBox<?> combo) {
            javafx.collections.ObservableList items = combo.getItems();
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (item instanceof String s) {
                    String translated = t(s);
                    if (!translated.equals(s)) items.set(i, translated);
                }
            }
        }
        if (node instanceof javafx.scene.control.TextInputControl c) {
            String hint = c.getPromptText();
            if (hint != null && !hint.isEmpty()) c.setPromptText(t(hint));
        }
        Object tip = node.getProperties().get("tclTooltip");
        if (tip instanceof javafx.scene.control.Tooltip tt) {
            tt.setText(t(tt.getText()));
        }
        if (node instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) localize(child);
        }
    }

    public static String langName() {
        return switch (mode) {
            case 1 -> "English";
            case 2 -> "繁體中文";
            default -> "简体中文";
        };
    }

    private static int detect() {
        java.util.Locale locale = java.util.Locale.getDefault();
        String lang = locale == null ? "" : locale.getLanguage().toLowerCase(java.util.Locale.ROOT);
        if (!"zh".equals(lang)) return 1;
        String country = locale.getCountry().toUpperCase(java.util.Locale.ROOT);
        return "TW".equals(country) || "HK".equals(country) || "MO".equals(country) ? 2 : 0;
    }

    public static void setMode(int m) {
        setting = Math.max(0, Math.min(3, m));
        mode = setting == 3 ? detect() : setting;
    }
}
