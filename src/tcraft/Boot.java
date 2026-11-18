package tcraft;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Boot {

    private static final String FX = "javafx.application.Application";
    static StartupWindow startupWindow;

    private Boot() {
    }

    public static void main(String[] args) {
        // 语言要先于一切 UI：读 config.properties 的 SystemLanguage，让启动壳/失败弹窗按设置显示
        applySystemProperties();
        if (Runtime.version().feature() < 17) {
            fail(Lang.t("TCraft Launcher 需要 Java 17 或更高版本。\n\n")
                    + Lang.t("当前 Java：") + System.getProperty("java.version") + "\n"
                    + Lang.t("请安装 64 位 Java 17 后重新运行。"), null);
            return;
        }
        StartupWindow ui = StartupWindow.open();
        startupWindow = ui;
        ui.setStatus(Lang.t("准备 TCL 启动器中…"), 5);
        try {
            // 单文件版首次运行按平台下载 JavaFX
            Resources.bootstrap(args, ui);
        } catch (Throwable t) {
            ui.close();
            fail(Lang.t("JavaFX 运行库准备失败：\n\n") + t.getMessage()
                    + Lang.t("\n\n请检查网络后重试，或删除 ")
                    + Resources.home().resolve("dependencies") + Lang.t(" 再运行。"), t);
        }
        if (!hasFx()) {
            ui.close();
            fail(Lang.t("找不到 JavaFX 运行时。\n\n")
                    + Lang.t("首次启动需要联网下载约 10MB 的 JavaFX 运行库。\n")
                    + Lang.t("请检查网络后重试，或删除 ")
                    + Resources.home().resolve("dependencies") + Lang.t(" 再运行。"), null);
        }
        ui.setStatus(Lang.t("正在启动 TCL…"), 90);
        try {
            javafx.application.Application.launch(App.class, args);
        } catch (Throwable t) {
            fail(Lang.t("TCraft Launcher 无法启动：\n\n") + t, t);
        }
    }

    private static void applySystemProperties() {
        try {
            java.util.Properties p = new java.util.Properties();
            Path cfg = Resources.home().resolve("config.properties");
            if (Files.isRegularFile(cfg)) {
                try (java.io.InputStream in = Files.newInputStream(cfg)) {
                    p.load(in);
                }
            }
            int fps = parseInt(p.getProperty("UiMaxFps", "0"));
            if (fps > 0) {
                System.setProperty("javafx.animation.pulse", String.valueOf(fps));
                System.setProperty("prism.targetvblank", String.valueOf(fps));
            }
            if ("true".equalsIgnoreCase(p.getProperty("SystemDisableHardwareAcceleration", "false"))) {
                // 关闭 GPU 管线，兼容老显卡与远程桌面
                System.setProperty("prism.order", "sw");
                System.setProperty("prism.forceGPU", "false");
                System.setProperty("prism.vsync", "false");
            }
            if ("true".equalsIgnoreCase(p.getProperty("SystemDebugMode", "false"))) {
                System.setProperty("prism.verbose", "true");
            }
            // 壳窗口在 Data.get() 之前就要用语言，这里是对应读取点（0 简体 / 1 英文 / 2 繁体 / 3 跟随系统）
            Lang.setMode(parseInt(p.getProperty("SystemLanguage", "0")));
            Lang.reload();
        } catch (Throwable ignored) {
            // 配置损坏时按默认启动，保证仍能打开
        }
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean hasFx() {
        try {
            Class.forName(FX, false, Boot.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void fail(String text, Throwable t) {
        if (startupWindow != null) startupWindow.close();
        Path crash = t == null ? null : writeCrash(t);
        String full = text + (crash == null ? "" : Lang.t("\n\n详细堆栈已写入：\n") + crash)
                + Lang.t("\n\n请不要发送截图，点「导出错误报告」保存日志，再把文件发到 TCL 交流群（1131373915）。");
        System.err.println(full);
        try {
            // “退出”表内是联机退出语境（Leave/離開），程序退出另用“退出程序”
            Object[] options = {Lang.t("导出错误报告"), Lang.t("退出程序")};
            int res = javax.swing.JOptionPane.showOptionDialog(null, full, Lang.t("TCraft Launcher 启动失败"),
                    javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE,
                    null, options, options[0]);
            if (res == 0) exportReport(full, t);
        } catch (Throwable ignored) {
        }
        System.exit(1);
    }

    private static void exportReport(String text, Throwable t) {
        try {
            Path dir = Resources.home().resolve("feedback");
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("TCraft-错误报告-" + stamp + ".log");
            StringBuilder sb = new StringBuilder();
            sb.append("TCraft Launcher ").append(Build.VERSION).append(" 错误报告\n");
            sb.append("时间：").append(java.time.LocalDateTime.now().withNano(0)).append('\n');
            sb.append("\n===== 报错 =====\n").append(text).append('\n');
            if (t != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                sb.append("\n===== 完整堆栈 =====\n").append(sw).append('\n');
            }
            sb.append("\n===== 诊断信息 =====\n")
                    .append("系统：").append(System.getProperty("os.name")).append(' ')
                    .append(System.getProperty("os.version")).append(' ')
                    .append(System.getProperty("os.arch")).append('\n')
                    .append("Java：").append(System.getProperty("java.version")).append(" (")
                    .append(System.getProperty("java.vendor")).append(")\n")
                    .append("工作目录：").append(System.getProperty("user.dir")).append('\n')
                    .append("配置目录：").append(Resources.home()).append('\n');
            sb.append("\n===== 最近日志 =====\n");
            Path log = Resources.home().resolve("launcher.log");
            if (Files.isRegularFile(log)) {
                for (String line : tailLines(log, 200)) sb.append(line).append('\n');
            } else {
                sb.append("（暂无日志）\n");
            }
            // 带 BOM，记事本/QQ 打开不乱码
            Files.writeString(file, "\uFEFF" + Redact.apply(sb.toString()), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    // 日志可能几十 MB，只从尾部读一段出来
    private static java.util.List<String> tailLines(Path file, int max) {
        try {
            long size = Files.size(file);
            long from = Math.max(0, size - 256 * 1024);
            String[] lines;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
                raf.seek(from);
                byte[] buf = new byte[(int) (size - from)];
                raf.readFully(buf);
                lines = new String(buf, java.nio.charset.StandardCharsets.UTF_8).split("\\R");
            }
            java.util.List<String> out = new java.util.ArrayList<>();
            // 起点落在行中间时第一段是半行，丢掉
            for (int i = from > 0 ? 1 : 0; i < lines.length; i++) out.add(lines[i]);
            int start = Math.max(0, out.size() - max);
            return out.subList(start, out.size());
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    static Path writeCrash(Throwable t) {
        try {
            Path dir = Resources.home();
            Files.createDirectories(dir);
            Path crash = dir.resolve("crash.txt");
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            Files.writeString(crash, Redact.apply(sw.toString()), java.nio.charset.StandardCharsets.UTF_8);
            return crash;
        } catch (Exception e) {
            return null;
        }
    }
}
