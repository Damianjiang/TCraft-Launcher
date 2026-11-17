package tcraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** 真正入口：JavaFX 起不来时落盘并弹 AWT 提示。 */
public final class Boot {

    private static final String FX = "javafx.application.Application";
    /** 启动加载窗，App 接着用同一个。 */
    static StartupWindow startupWindow;

    private Boot() {
    }

    public static void main(String[] args) {
        StartupWindow ui = StartupWindow.open();
        startupWindow = ui;
        ui.setStatus("准备 TCL 启动器中…", 5);
        try {
            // 单文件版首次运行按平台下载 JavaFX
            Resources.bootstrap(args, ui);
        } catch (Throwable t) {
            ui.close();
            fail("JavaFX 运行库准备失败：\n\n" + t.getMessage()
                    + "\n\n请检查网络后重试，或删除 "
                    + Resources.home().resolve("dependencies") + " 再运行。", t);
        }
        if (!hasFx()) {
            ui.close();
            fail("找不到 JavaFX 运行时。\n\n"
                    + "首次启动需要联网下载约 10MB 的 JavaFX 运行库。\n"
                    + "请检查网络后重试，或删除 "
                    + Resources.home().resolve("dependencies") + " 再运行。", null);
        }
        ui.setStatus("正在启动 TCL…", 90);
        try {
            javafx.application.Application.launch(App.class, args);
        } catch (Throwable t) {
            fail("TCraft Launcher 无法启动：\n\n" + t, t);
        }
    }

    /** 只查类在不在，不触发初始化。 */
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
        String full = text + (crash == null ? "" : "\n\n详细堆栈已写入：\n" + crash)
                + "\n\n请不要发送截图，点「导出错误报告」保存日志，再把文件发到 TCL 交流群（1131373915）。";
        System.err.println(full);
        try {
            Object[] options = {"导出错误报告", "退出"};
            int res = javax.swing.JOptionPane.showOptionDialog(null, full, "TCraft Launcher 启动失败",
                    javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE,
                    null, options, options[0]);
            if (res == 0) exportReport(full, t);
        } catch (Throwable ignored) {
            // 无图形环境时只能靠日志
        }
        System.exit(1);
    }

    /** 只用 JDK 写错误报告，JavaFX 起不来时也能导出。 */
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
                String[] lines = new String(Files.readAllBytes(log),
                        java.nio.charset.StandardCharsets.UTF_8).split("\\R");
                int from = Math.max(0, lines.length - 200);
                for (int i = from; i < lines.length; i++) sb.append(lines[i]).append('\n');
            } else {
                sb.append("（暂无日志）\n");
            }
            // 带 BOM，记事本和 QQ 里直接打开不乱码
            Files.writeString(file, "\uFEFF" + Redact.apply(sb.toString()), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把堆栈写进 crash.txt，返回路径；失败返回 null。 */
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
