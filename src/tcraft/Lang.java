package tcraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 多语言：简体 / English / 繁體，由 LangTables.txt 驱动。 */
public final class Lang {

    public static int mode = 0; // 0 = 简中, 1 = English, 2 = 繁體
    private static final Map<String, String[]> TABLE = new HashMap<>();

    static {
        reload();
    }

    private Lang() {
    }

    public static void reload() {
        TABLE.clear();
        Path p = Theme.res("LangTables.txt");
        try {
            if (!Files.exists(p)) return;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.isEmpty()) continue;
                String[] cols = line.split("\t", -1);
                if (cols.length < 2) continue;
                // 表里换行写成 \n，读回来还原
                String zh = unescape(cols[0]);
                if (zh.isEmpty() || TABLE.containsKey(zh)) continue;
                String en = unescape(cols[1]);
                String tw = cols.length > 2 && !cols[2].isEmpty() ? unescape(cols[2]) : zh;
                TABLE.put(zh, new String[]{en, tw});
            }
        } catch (Exception ignored) {
        }
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

    /** 把界面文字换成当前语言，查不到原样保留。 */
    public static void localize(javafx.scene.Node node) {
        if (node == null || mode == 0) return;
        if (node instanceof javafx.scene.control.Labeled l) {
            String s = l.getText();
            if (s != null && !s.isEmpty()) l.setText(t(s));
        } else if (node instanceof javafx.scene.text.Text tx) {
            String s = tx.getText();
            if (s != null && !s.isEmpty()) tx.setText(t(s));
        }
        if (node instanceof javafx.scene.control.TextInputControl c) {
            String hint = c.getPromptText();
            if (hint != null && !hint.isEmpty()) c.setPromptText(t(hint));
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

    public static void setMode(int m) {
        mode = Math.max(0, Math.min(2, m));
    }
}
