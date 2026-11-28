package tcraft;

import java.io.IOException;
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
        // 旧实例还在关窗口时先等它释放数据目录和锁 跨平台通用
        args = waitForPrevious(args);
        // 更新进程先替换文件 再拉起新版本
        if (args.length == 2 && args[0].equals("--apply-to")) {
            Path oldFile = Paths.get(args[1]);
            try {
                SelfUpdate.applyUpdate(oldFile);
            } catch (Throwable t) {
                // javaw 无 stderr 失败落盘并留标记
                System.err.println("[TCL] 应用更新失败：" + t);
                try {
                    Log.error("应用更新失败：" + t);
                    Log.error(Log.describe(t));
                    // Keep the failure report even when update/ is missing.
                    Path marker = Resources.home().resolve("update").resolve("last-apply-error.txt");
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, String.valueOf(t));
                } catch (Throwable ignored) {
                }
                try {
                    // 失败时保留本体并回滚旧版；改名成功后原路径已不存在 用实际落位文件
                    Path installed = SelfUpdate.installed;
                    SelfUpdate.startAny(installed != null ? installed : oldFile);
                } catch (Throwable ignored) {
                }
                System.exit(1);
            }
            return;
        }
        // 数据导入先落位 再读导入后的配置启动
        if (args.length == 2 && args[0].equals("--apply-data-import")) {
            if (!applyDataImport(Paths.get(args[1]))) {
                dataImportFail();
                return;
            }
            args = new String[0];
        }
        // 部分单文件外壳不转发命令行参数，导入重启用标记文件交接
        if (args.length == 0) {
            Path pending = Resources.home().toAbsolutePath().normalize()
                    .resolveSibling(Resources.home().getFileName() + ".import-pending");
            if (Files.isRegularFile(pending)) {
                boolean hasSource = false;
                boolean applied = false;
                try {
                    java.util.List<String> lines = Files.readAllLines(pending,
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (lines.size() >= 2 && !lines.get(1).isBlank()) {
                        hasSource = true;
                        waitForPid(parsePid(lines.get(0)), 20_000);
                        // Data must not be touched while the old process can still hold the lock.
                        if (!acquireInstanceLock()) {
                            System.err.println("[TCL] old instance still running, retry import later");
                            return;
                        }
                        Path source = Paths.get(lines.get(1).trim());
                        if (!Files.isDirectory(source)) {
                            // A previous import finished but the marker could not be removed.
                            hasSource = false;
                        } else {
                            applied = applyDataImport(source);
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[TCL] 读取导入标记失败：" + t);
                }
                try {
                    Files.deleteIfExists(pending);
                } catch (Throwable ignored) {
                    // 删不掉就留到下次启动重试
                }
                if (hasSource && !applied) {
                    dataImportFail();
                    return;
                }
            }
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
            Shell.notifyDesktop("TCraft Launcher", Lang.t("启动器已在运行，请切换到已打开的窗口。"));
            return;
        }
        cleanupLegacyCache();
        initDockIcon();
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

    // --wait-pid <pid>：等旧进程退出后再继续 避免抢锁 超时也照常启动
    private static String[] waitForPrevious(String[] args) {
        long pid = -1;
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--wait-pid") && i + 1 < args.length) {
                pid = parsePid(args[++i]);
            } else {
                rest.add(args[i]);
            }
        }
        waitForPid(pid, 20_000);
        return rest.toArray(new String[0]);
    }

    private static long parsePid(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // 旧进程可能还在关窗口，等它退出最多 timeout 毫秒
    private static void waitForPid(long pid, long timeoutMillis) {
        if (pid <= 0) return;
        try {
            java.util.Optional<ProcessHandle> old = ProcessHandle.of(pid);
            if (old.isPresent() && old.get().isAlive()) {
                old.get().onExit().get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (Throwable ignored) {
            // 超时或无权查询时继续启动
        }
    }

    private static boolean acquireInstanceLock() {
        if (instanceLock != null && instanceLock.isValid()) return true;
        // stub mutex is no longer reliable; the file lock keeps a single instance
        Path lockFile = Resources.home().resolve("launcher.lock");
        try {
            // 锁文件可能是符号链接 不能顺它锁外部文件
            if (Files.isSymbolicLink(lockFile)) Files.deleteIfExists(lockFile);
        } catch (Exception ignored) {
        }
        // 旧进程退出可能拖到 2 秒以上，导入重启时放宽到 10 秒
        for (int attempt = 0; attempt < 100; attempt++) {
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
            boolean lowMemory = lowMemoryMode(p.getProperty("SystemLowMemoryMode", "on"));
            System.setProperty("tcl.lowMemory", Boolean.toString(lowMemory));
            if (lowMemory) {
                // 低内存模式默认软件渲染，省掉 D3D 管线与显存映射
                System.setProperty("prism.order", "sw");
                System.setProperty("prism.forceGPU", "false");
                System.setProperty("prism.vsync", "false");
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

    private static boolean lowMemoryMode(String raw) {
        String mode = raw == null ? "auto" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (mode.equals("on") || mode.equals("true") || mode.equals("1")) return true;
        if (mode.equals("off") || mode.equals("false") || mode.equals("0")) return false;
        long total = totalMemory();
        // 自动模式只在小内存机器上关闭硬件加速，避免影响高配机器的动画
        return total > 0 && total <= 8L * 1024 * 1024 * 1024;
    }

    private static long totalMemory() {
        try {
            Object bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
                return os.getTotalMemorySize();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // Sets the macOS Dock icon before JavaFX starts; Stage icons only cover windows.
    private static void initDockIcon() {
        if (!Shell.mac()) return;
        try {
            System.setProperty("apple.awt.application.name", "TCraft Launcher");
            java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
            if (!taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) return;
            java.net.URL url = Theme.resUrl("images/icon-256.png");
            if (url == null) return;
            java.awt.Image icon = javax.imageio.ImageIO.read(url);
            if (icon != null) taskbar.setIconImage(icon);
        } catch (Throwable ignored) {
            // Non-mac or headless environments keep the platform default.
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

    private static boolean applyDataImport(Path staging) {
        Path stage = staging.toAbsolutePath().normalize();
        Path base = Resources.home().toAbsolutePath().normalize();
        Path payload = stage.resolve("data").normalize();
        try {
            // 只接受 DataBackup 生成的临时目录，防止任意目录被当成导入源
            Path parent = base.getParent();
            if (parent == null || stage.getParent() == null || !stage.getParent().equals(parent)) {
                throw new IOException("导入临时目录不在启动器数据目录旁");
            }
            String prefix = "." + base.getFileName() + "-import-";
            if (!stage.getFileName().toString().startsWith(prefix)) {
                throw new IOException("导入临时目录名称不正确");
            }
            if (Files.isSymbolicLink(stage) || Files.isSymbolicLink(base)
                    || Files.isSymbolicLink(payload)) {
                throw new IOException("数据导入路径不能是符号链接");
            }
            if (!Files.isDirectory(payload)) throw new IOException("导入临时目录不完整");
            String marker = Files.readString(stage.resolve("import.json"), java.nio.charset.StandardCharsets.UTF_8);
            java.util.Map<String, Object> root = Json.obj(Json.parse(marker));
            if (!"tcraft-backup-import".equals(Json.str(root, "type", ""))) {
                throw new IOException("导入标记类型不正确");
            }
            // 旧窗口退出和文件锁释放需要时间；单文件外壳会占住 .tcl 内的句柄，
            // 所以不整目录改名，先快照旧数据再按文件覆盖，Windows / macOS / Linux 同一套
            Thread.sleep(1200);
            Path backup = base.resolveSibling(base.getFileName() + ".pre-import-"
                    + System.currentTimeMillis());
            if (Files.exists(base)) {
                snapshotDir(base, backup);
                Path oldConfig = base.resolve("config.properties");
                if (Files.isRegularFile(oldConfig)
                        && !Files.isRegularFile(backup.resolve("config.properties"))) {
                    throw new IOException("旧配置备份失败，已中止导入以免覆盖原数据");
                }
            }
            int failed = copyOver(payload, base);
            Path config = base.resolve("config.properties");
            Path sourceConfig = payload.resolve("config.properties");
            if (!Files.isRegularFile(config) || Files.size(config) <= 0) {
                throw new IOException("导入后缺少 config.properties，"
                        + failed + " 个文件被占用或写入失败");
            }
            if (failed > 0 || Files.mismatch(sourceConfig, config) != -1) {
                throw new IOException("导入未完整写入，"
                        + failed + " 个文件被占用或写入失败，旧数据保留在 " + backup.getFileName());
            }
            deleteTree(stage);
            return true;
        } catch (Throwable t) {
            System.err.println("[TCL] 应用数据导入失败：" + t);
            try {
                Log.error("应用数据导入失败：" + Log.describe(t));
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    private static void dataImportFail() {
        String text = "启动器数据导入失败。\n\n"
                + "旧数据没有被删除，已尽量快照到 .tcl.pre-import-*。\n"
                + "请检查磁盘权限和剩余空间后重试。";
        System.err.println("[TCL] " + text);
        if (Shell.mac()) {
            try {
                Shell.macAlert("TCraft Launcher", text);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                javax.swing.JOptionPane.showMessageDialog(null, text, "TCraft Launcher",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
            } catch (Throwable ignored) {
            }
        }
        System.exit(1);
    }

    // 导入前把旧数据快照到 .tcl.pre-import-*；运行库和游戏目录不复制，避免几 GB 拷贝
    private static final java.util.Set<String> SNAPSHOT_SKIP = java.util.Set.of(
            "cache", "log", "logs", "update", "dependencies", "java", "EasyTier",
            "assets", "libraries", "versions", "mods", "saves", "resourcepacks",
            "shaderpacks", "crash-reports", "screenshots", "replay_recordings", "backups", "backup");

    private static void snapshotDir(Path base, Path backup) {
        try {
            Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                        java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (dir.equals(base)) return java.nio.file.FileVisitResult.CONTINUE;
                    String first = base.relativize(dir).getName(0).toString();
                    return SNAPSHOT_SKIP.contains(first)
                            ? java.nio.file.FileVisitResult.SKIP_SUBTREE
                            : java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file,
                        java.nio.file.attribute.BasicFileAttributes attrs) {
                    try {
                        Path out = backup.resolve(base.relativize(file));
                        Files.createDirectories(out.getParent());
                        Files.copy(file, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Throwable ignored) {
                        // 被占用的文件跳过，快照只是兜底
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException e) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable ignored) {
            // 快照失败不阻断导入
        }
    }

    // 把备份里的文件逐个覆盖回数据目录，返回失败数量
    private static int copyOver(Path payload, Path base) throws IOException {
        int[] failed = {0};
        Files.walkFileTree(payload, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                    java.nio.file.attribute.BasicFileAttributes attrs) {
                try {
                    Files.createDirectories(base.resolve(payload.relativize(dir)));
                } catch (Throwable ignored) {
                    failed[0]++;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) {
                try {
                    Path out = base.resolve(payload.relativize(file));
                    Files.createDirectories(out.getParent());
                    Files.copy(file, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable ignored) {
                    failed[0]++;
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return failed[0];
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
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
