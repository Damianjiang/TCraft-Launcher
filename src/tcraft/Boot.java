package tcraft;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class Boot {

    private static final String FX = "javafx.application.Application";
    static final long STARTED_AT = System.nanoTime();
    static StartupWindow startupWindow;
    private static FileChannel instanceLockChannel;
    private static FileLock instanceLock;

    private Boot() {
    }

    public static void main(String[] args) {
        // 更新进程先替换文件 再拉起新版本
        if (args.length == 2 && args[0].equals("--apply-to")) {
            Path oldFile = Paths.get(args[1]);
            try {
                SelfUpdate.applyUpdate(oldFile);
            } catch (Throwable t) {
                // javaw 无 stderr 失败落盘并留标记
                System.err.println("[TCL] 应用更新失败：" + t);
                t.printStackTrace();
                try {
                    Log.error("应用更新失败：" + t);
                    Log.error(Log.describe(t));
                    Files.writeString(Resources.home().resolve("update").resolve("last-apply-error.txt"),
                            String.valueOf(t));
                } catch (Throwable ignored) {
                }
                try {
                    // 失败时保留本体并回滚旧版
                    SelfUpdate.startJava(oldFile);
                } catch (Throwable ignored) {
                }
                System.exit(1);
            }
            return;
        }
        // 语言先于一切 UI
        applySystemProperties();
        if (Runtime.version().feature() < 17) {
            fail(Lang.t("TCraft Launcher 需要 Java 17 或更高版本。\n\n")
                    + Lang.t("当前 Java：") + System.getProperty("java.version") + "\n"
                    + Lang.t("请安装 64 位 Java 17 后重新运行。"), null);
            return;
        }
        if (!acquireInstanceLock()) {
            System.err.println("[TCL] 已有实例在运行，本次启动退出");
            Shell.macAlert("TCraft Launcher", Lang.t("启动器已在运行，请切换到已打开的窗口。"));
            Shell.linuxNotify("TCraft Launcher", Lang.t("启动器已在运行，请切换到已打开的窗口。"));
            return;
        }
        cleanupLegacyCache();
        // 仅缺 JavaFX/需下载时显示进度窗
        StartupWindow ui = hasFx() ? StartupWindow.none() : StartupWindow.open();
        startupWindow = ui;
        ui.setStatus(Lang.t("正在准备运行环境…"), 5);
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
        ui.setStatus(Lang.t("正在启动主界面…"), 90);
        try {
            javafx.application.Application.launch(App.class, args);
        } catch (Throwable t) {
            fail(Lang.t("TCraft Launcher 无法启动：\n\n") + t, t);
        }
    }

    private static boolean acquireInstanceLock() {
        // stub 互斥量已失效 长期单实例靠此文件锁
        Path lockFile = Resources.home().resolve("launcher.lock");
        try {
            // 锁文件若被替换成符号链接，不能顺着它锁外部文件。
            if (Files.isSymbolicLink(lockFile)) Files.deleteIfExists(lockFile);
        } catch (Exception ignored) {
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            FileChannel ch;
            try {
                ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
            } catch (Exception e) {
                // 目录不可写不拦启动
                return true;
            }
            try {
                FileLock lock = ch.tryLock();
                if (lock != null) {
                    instanceLockChannel = ch;
                    instanceLock = lock;
                    return true;
                }
                ch.close();
            } catch (OverlappingFileLockException e) {
                closeQuietly(ch);
                return false;
            } catch (Exception e) {
                // 文件已持锁 按已运行处理
                closeQuietly(ch);
                return false;
            }
            try {
                // 旧进程退出需要一点时间
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static void closeQuietly(FileChannel ch) {
        try {
            ch.close();
        } catch (Exception ignored) {
        }
    }

    // 旧外壳在 .tcl 落过本体缓存 新版不再使用
    private static void cleanupLegacyCache() {
        Path cache = Resources.home().resolve("TCraftLauncher.jar");
        if (!Files.exists(cache)) return;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                Files.deleteIfExists(cache);
                return;
            } catch (Throwable ignored) {
                // 旧进程可能还压着 jar 稍后再删
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static void releaseInstanceLock() {
        try {
            if (instanceLock != null) instanceLock.release();
        } catch (Exception ignored) {
        }
        try {
            if (instanceLockChannel != null) instanceLockChannel.close();
        } catch (Exception ignored) {
        }
        instanceLock = null;
        instanceLockChannel = null;
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
                // 关闭 GPU 管线 兼容老显卡与远程桌面
                System.setProperty("prism.order", "sw");
                System.setProperty("prism.forceGPU", "false");
                System.setProperty("prism.vsync", "false");
            }
            if ("true".equalsIgnoreCase(p.getProperty("SystemDebugMode", "false"))) {
                System.setProperty("prism.verbose", "true");
            }
            // 读语言要先于 Data.get 0简/1英/2繁/3系统
            Lang.setMode(parseInt(p.getProperty("SystemLanguage", "0")));
        } catch (Throwable ignored) {
            // 配置损坏按默认启动
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
        if (Shell.mac()) {
            // 已带 JavaFX 时用系统对话框 不再起 Swing
            System.err.println(full);
            Shell.macAlert(Lang.t("TCraft Launcher 启动失败"), full);
            try {
                Shell.openFolder(Resources.logDir().toString());
            } catch (Throwable ignored) {
            }
            System.exit(1);
        }
        System.err.println(full);
        try {
            // 退出是联机语境 程序退出用退出程序
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
            sb.append("TCraft Launcher ").append(Build.VERSION)
                    .append("（").append(Build.EDITION).append("）错误报告\n");
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
            sb.append("\n===== 完整日志 =====\n");
            String dump = Log.exportAll();
            sb.append(dump.isEmpty() ? "（暂无日志）\n" : dump);
            // UTF-8 BOM 记事本/QQ 不乱码
            Files.writeString(file, "\uFEFF" + Redact.apply(sb.toString()), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Shell.openFolder(dir.toString());
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    // 日志可能几十 MB 只留尾部
    static Path writeCrash(Throwable t) {
        return Log.crash(t);
    }
}
