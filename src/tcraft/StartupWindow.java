package tcraft;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

// 仅首次运行/修复 JavaFX 运行库时显示
public final class StartupWindow {

    private static final int W = 460;
    private static final int H = 170;
    private static final Color ACCENT = new Color(0x13, 0x70, 0xf3);
    private static final Color TEXT = new Color(0x34, 0x3d, 0x4a);
    private static final Color GRAY = new Color(0x73, 0x73, 0x73);
    private static final Color TRACK = new Color(0xe6, 0xe8, 0xeb);
    private static final Font FONT_LOGO = new Font("Microsoft YaHei UI", Font.BOLD, 20);
    private static final Font FONT_TITLE = new Font("Microsoft YaHei UI", Font.BOLD, 17);
    private static final Font FONT_STATUS = new Font("Microsoft YaHei UI", Font.PLAIN, 12);
    private static final Font FONT_PCT = new Font("Microsoft YaHei UI", Font.PLAIN, 11);

    private final Window window;
    private final Panel panel;
    private final long openedAt = System.currentTimeMillis();
    // AWT 文案不走 localize，创建时取当前语言
    private volatile String status = Lang.t("准备 TCL 启动器中…");
    private volatile String detail = "";
    private volatile int percent = -1;
    private volatile boolean closed;
    private Thread animator;

    private long downloadStartMs;
    private long downloadLastMs;
    private volatile double speedMb;

    private StartupWindow() {
        panel = new Panel();
        window = new Window((java.awt.Frame) null);
        window.setBackground(new Color(0, 0, 0, 0));
        window.add(panel);
        window.setSize(W, H);
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        try {
            window.setShape(new RoundRectangle2D.Float(0, 0, W, H, 26, 26));
        } catch (Exception ignored) {
        }
        window.setVisible(true);
        animator = new Thread(this::animate, "TCL-Startup");
        animator.setDaemon(true);
        animator.start();
    }

    public static StartupWindow open() {
        try {
            if (Shell.mac()) return new StartupWindow(true);   // macOS 不起 AWT 窗，防双 toolkit 抢 NSApplication
            if (java.awt.GraphicsEnvironment.isHeadless()) return new StartupWindow(true);
            StartupWindow[] box = new StartupWindow[1];
            javax.swing.SwingUtilities.invokeAndWait(() -> box[0] = new StartupWindow());
            return box[0] != null ? box[0] : new StartupWindow(true);
        } catch (Throwable t) {
            Log.error("[" + java.time.LocalDateTime.now().toLocalTime().withNano(0) + "] 加载窗不可用：" + t);
            return new StartupWindow(true);
        }
    }

    private StartupWindow(boolean headless) {
        window = null;
        panel = null;
    }

    public static StartupWindow none() {
        return new StartupWindow(true);
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    public void setIndeterminate(String text) {
        status = text == null ? "" : text;
        detail = "";
        percent = -1;
        repaint();
    }

    public void setStatus(String text, int value) {
        status = text == null ? "" : text;
        detail = "";
        percent = value;
        repaint();
    }

    public void setDownload(long done, long total, String text) {
        applyDownload(text, done, total, total > 0 ? (int) Math.min(99, done * 100 / total) : -1);
    }

    // percent 为整体进度，当前文件进度另显在详情行
    public void setDownload(String text, long done, long total, int overallPercent) {
        applyDownload(text, done, total, overallPercent);
    }

    private void applyDownload(String text, long done, long total, int overallPercent) {
        long now = System.currentTimeMillis();
        if (downloadStartMs == 0) downloadStartMs = now;
        if (now - downloadLastMs >= 200) {
            double secs = Math.max(0.001, (now - downloadStartMs) / 1000.0);
            double avg = done / secs / 1048576.0;
            speedMb = speedMb <= 0 ? avg : speedMb * 0.7 + avg * 0.3;
            downloadLastMs = now;
        }
        StringBuilder d = new StringBuilder();
        if (total > 0) {
            d.append(String.format(java.util.Locale.ROOT, "%.1f / %.1f MB", done / 1048576.0, total / 1048576.0));
            d.append("  ").append(done * 100 / Math.max(1, total)).append('%');
        } else {
            d.append(String.format(java.util.Locale.ROOT, Lang.t("已下载 %.1f MB"), done / 1048576.0));
        }
        if (speedMb > 0) d.append(String.format(java.util.Locale.ROOT, "  %.1f MB/s", speedMb));
        detail = d.toString();
        status = text == null ? "" : text;
        percent = overallPercent;
        repaint();
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (window == null) return;
        try {
            window.setAlwaysOnTop(false);
        } catch (Exception ignored) {
        }
        long wait = 300 - (System.currentTimeMillis() - openedAt);
        Runnable dispose = () -> java.awt.EventQueue.invokeLater(() -> {
            // AWT 组件的显隐与销毁按规范只能在 EDT 做
            try {
                window.setVisible(false);
                window.dispose();
            } catch (Exception ignored) {
            }
        });
        if (wait <= 0) {
            dispose.run();
        } else {
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ignored) {
                }
                dispose.run();
            }, "TCL-StartupClose");
            t.setDaemon(true);
            t.start();
        }
    }

    private void repaint() {
        if (panel != null && !closed) panel.repaint();
    }

    private void animate() {
        while (!closed) {
            if (percent < 0 && panel != null) panel.repaint();
            try {
                Thread.sleep(33);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    static void paintCard(Graphics2D g2, String text, String detail, int value, Image logo) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(0xfb, 0xfb, 0xfb));
        g2.fillRoundRect(0, 0, W, H, 26, 26);
        g2.setColor(new Color(0xe3, 0xe6, 0xea));
        g2.drawRoundRect(0, 0, W - 1, H - 1, 26, 26);

        if (logo != null) {
            g2.drawImage(logo, 26, 49, 72, 72, null);
        } else {
            g2.setColor(ACCENT);
            g2.fillRoundRect(26, 49, 72, 72, 16, 16);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_LOGO);
            g2.drawString("TCL", 42, 92);
        }

        g2.setColor(TEXT);
        g2.setFont(FONT_TITLE);
        g2.drawString("TCraft Launcher", 116, 46);

        g2.setColor(GRAY);
        g2.setFont(FONT_STATUS);
        g2.drawString(clip(text, 250, g2), 116, 75);
        if (value >= 0) {
            g2.setFont(FONT_PCT);
            String p = value + "%";
            g2.drawString(p, 116 + 318 - g2.getFontMetrics().stringWidth(p), 75);
        }
        if (detail != null && !detail.isEmpty()) {
            g2.setColor(new Color(0x8c, 0x8c, 0x8c));
            g2.setFont(FONT_PCT);
            g2.drawString(clip(detail, 330, g2), 116, 91);
        }

        int x = 116, y = 101, w = 318, h = 8;
        g2.setColor(TRACK);
        g2.fillRoundRect(x, y, w, h, h, h);
        if (value >= 0) {
            int fill = Math.max(0, Math.min(w, w * value / 100));
            g2.setColor(ACCENT);
            g2.fillRoundRect(x, y, fill, h, h, h);
        } else {
            int span = 96;
            int travel = w + span;
            double phase = (System.currentTimeMillis() % 1400) / 1400.0;
            int bx = x - span + (int) (travel * phase);
            g2.setColor(new Color(0x6a, 0xa6, 0xf8));
            g2.fillRoundRect(Math.max(x, bx), y, Math.min(span, x + w - Math.max(x, bx)), h, h, h);
        }
    }

    private static String clip(String text, int width, Graphics2D g2) {
        if (text == null) return "";
        if (g2.getFontMetrics().stringWidth(text) <= width) return text;
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int avg = Math.max(1, fm.stringWidth(text) / text.length());
        int keep = Math.max(1, (width - fm.stringWidth("…")) / avg);
        if (keep >= text.length()) keep = text.length() - 1;
        return text.substring(0, keep) + "…";
    }

    private final class Panel extends java.awt.Panel {
        private static final long serialVersionUID = 1L;
        private final transient Image logo = loadLogo();

        Panel() {
            setLayout(null);
        }

        @Override
        public void paint(Graphics g) {
            paintCard((Graphics2D) g, status, detail, percent, logo);
        }

        private Image loadLogo() {
            try (InputStream in = StartupWindow.class.getResourceAsStream("/res/images/Splash.png")) {
                if (in == null) return null;
                BufferedImage img = ImageIO.read(in);
                return img;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
