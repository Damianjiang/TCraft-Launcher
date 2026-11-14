package tcraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 真正入口：JavaFX 起不来时落盘并弹 AWT 提示。 */
public final class Boot {

    private static final String FX = "javafx.application.Application";

    private Boot() {
    }

    public static void main(String[] args) {
        try {
            // 单文件版首次运行解压 res 和 JavaFX
            Resources.bootstrap(args);
        } catch (Throwable ignored) {
            // 解压失败不拦，下面给更具体的提示
        }
        if (!hasFx()) {
            fail("找不到 JavaFX 运行时。\n\n"
                    + "单文件版首次启动会把它解压到用户目录，解压失败或文件缺失时会这样。\n"
                    + "删除 " + Resources.home() + " 后重新运行；仍失败说明程序包不完整。", null);
        }
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
        Path crash = t == null ? null : writeCrash(t);
        String full = text + (crash == null ? "" : "\n\n详细堆栈已写入：\n" + crash);
        System.err.println(full);
        try {
            javax.swing.JOptionPane.showMessageDialog(null, full, "TCraft Launcher 启动失败",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
            // 无图形环境时只能靠日志
        }
        System.exit(1);
    }

    /** 把堆栈写进 crash.txt，返回路径；失败返回 null。 */
    static Path writeCrash(Throwable t) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".tcraft-launcher-javafx");
            Files.createDirectories(dir);
            Path crash = dir.resolve("crash.txt");
            java.io.PrintWriter pw = new java.io.PrintWriter(Files.newBufferedWriter(
                    crash, java.nio.charset.StandardCharsets.UTF_8));
            t.printStackTrace(pw);
            pw.close();
            return crash;
        } catch (Exception e) {
            return null;
        }
    }
}
