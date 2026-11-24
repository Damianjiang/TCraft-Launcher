package tcraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// 自更新成功标记：更新进程写 applied，新本体首次启动读取并清理
public final class UpgradeState {

    public static final class Notice {
        public final String from;
        public final String to;
        public final int versions;
        public final String changelog;

        Notice(String from, String to, int versions, String changelog) {
            this.from = from;
            this.to = to;
            this.versions = versions;
            this.changelog = changelog;
        }
    }

    private UpgradeState() {
    }

    private static Path file() {
        return Resources.home().resolve("update").resolve("last-upgrade.properties");
    }

    public static void pending(String from, String to, int versions, String changelog) {
        write(from, to, versions, changelog, "true");
    }

    // 更新进程在复制、改名完成后调用
    public static void applied() {
        Properties p = read();
        if (p == null) return;
        p.setProperty("pending", "false");
        write(p);
    }

    // 读取后立即删除 失败安装留下的 pending 标记直接丢弃
    public static Notice readAndClear() {
        Properties p = read();
        try {
            Files.deleteIfExists(file());
        } catch (IOException ignored) {
        }
        if (p == null) return null;
        if ("true".equalsIgnoreCase(p.getProperty("pending", "false"))) return null;
        String from = p.getProperty("from", "").trim();
        String to = p.getProperty("to", "").trim();
        if (from.isEmpty() || to.isEmpty()) return null;
        int versions;
        try {
            versions = Math.max(1, Integer.parseInt(p.getProperty("versions", "1").trim()));
        } catch (Exception e) {
            versions = 1;
        }
        return new Notice(from, to, versions, p.getProperty("changelog", ""));
    }

    // 旧版本没有写升级标记时 从上次启动日志还原 from/to 至少保证弹窗出现
    public static Notice detectFromLog() {
        try {
            Path log = Resources.logDir().resolve("launcher.log");
            if (!Files.isRegularFile(log)) return null;
            String text = Files.readString(log, StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("软件：TCraft Launcher ([0-9]+(?:\\.[0-9]+)*)")
                    .matcher(text);
            String previous = null;
            String last = null;
            while (m.find()) {
                previous = last;
                last = m.group(1);
            }
            if (last == null) return null;
            String from = Build.VERSION.equals(last) ? previous : last;
            if (from == null || from.equals(Build.VERSION)) return null;
            int versions = 1;
            try {
                String[] a = from.split("\\.");
                String[] b = Build.VERSION.split("\\.");
                int oldTail = Integer.parseInt(a[a.length - 1]);
                int newTail = Integer.parseInt(b[b.length - 1]);
                versions = Math.max(1, newTail - oldTail + 1);
            } catch (Exception ignored) {
            }
            return new Notice(from, Build.VERSION, versions, "");
        } catch (Exception e) {
            return null;
        }
    }

    private static Properties read() {
        Path f = file();
        if (!Files.isRegularFile(f)) return null;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            p.load(r);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(String from, String to, int versions, String changelog, String pending) {
        Properties p = new Properties();
        p.setProperty("pending", pending);
        p.setProperty("from", from == null ? "" : from);
        p.setProperty("to", to == null ? "" : to);
        p.setProperty("versions", String.valueOf(Math.max(1, versions)));
        p.setProperty("changelog", changelog == null ? "" : changelog);
        write(p);
    }

    private static void write(Properties p) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                p.store(w, "TCraft Launcher self-update");
            }
        } catch (IOException ignored) {
        }
    }
}
