package tcraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                + "\n\n请不要发送截图，请复制完整报错，粘贴到交流群或 GitHub Issues 提交。";
        System.err.println(full);
        try {
            Object[] options = {"复制并退出", "退出"};
            int res = javax.swing.JOptionPane.showOptionDialog(null, full, "TCraft Launcher 启动失败",
                    javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE,
                    null, options, options[0]);
            if (res == 0) {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection(full);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            }
        } catch (Throwable ignored) {
            // 无图形环境时只能靠日志
        }
        System.exit(1);
    }

    /** 把堆栈写进 crash.txt，返回路径；失败返回 null。 */
    static Path writeCrash(Throwable t) {
        try {
            Path dir = Resources.home();
            Files.createDirectories(dir);
            Path crash = dir.resolve("crash.txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(Files.newBufferedWriter(
                    crash, java.nio.charset.StandardCharsets.UTF_8))) {
                t.printStackTrace(pw);
            }
            return crash;
        } catch (Exception e) {
            return null;
        }
    }
}
