package tcraft;

import java.io.File;

public final class Shell {

    private Shell() {
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static boolean mac() {
        return isMac();
    }

    public static boolean linux() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix")
                || os.contains("freebsd") || os.contains("openbsd") || os.contains("netbsd");
    }

    private static boolean isLinux() {
        return linux();
    }

    public static java.nio.file.Path desktopDir() {
        String home = System.getProperty("user.home", "");
        if (isLinux()) {
            try {
                java.nio.file.Path cfg = java.nio.file.Paths.get(home, ".config", "user-dirs.dirs");
                if (java.nio.file.Files.isRegularFile(cfg)) {
                    for (String line : java.nio.file.Files.readAllLines(cfg)) {
                        if (!line.startsWith("XDG_DESKTOP_DIR=")) continue;
                        String value = line.substring("XDG_DESKTOP_DIR=".length()).replace("\"", "").trim();
                        value = value.replace("$HOME", home);
                        if (!value.isEmpty()) return java.nio.file.Paths.get(value);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return java.nio.file.Paths.get(home, "Desktop");
    }

    public static void setProcessPriority(long pid, int level) {
        if (pid <= 0 || level == 1) return;
        try {
            if (windows()) {
                String cls = switch (level) {
                    case 0 -> "AboveNormal";
                    case 2 -> "BelowNormal";
                    case 3 -> "High";
                    case 4 -> "RealTime";
                    default -> null;
                };
                if (cls == null) return;
                // wmic 在 Windows 11 24H2 已移除，改用 PowerShell；PS 冷启动可能超过 10s，放宽到 30s
                runHidden(30, "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "(Get-Process -Id " + pid + " -ErrorAction Stop).PriorityClass='" + cls + "'");
                return;
            }
            // macOS / Linux：renice，数值越小优先级越高
            int nice = switch (level) {
                case 0 -> -5;
                case 2 -> 10;
                case 3 -> -10;
                case 4 -> -15;
                default -> 0;
            };
            if (nice == 0) return;
            runHidden("renice", "-n", String.valueOf(nice), "-p", String.valueOf(pid));
        } catch (Exception ignored) {
        }
    }

    // 子进程要有人收尾：输出丢掉，超时强杀，不留堵着管道的僵尸；失败落一条日志方便排查
    private static void runHidden(long timeoutSeconds, String... cmd) throws java.io.IOException {
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (java.io.IOException e) {
            Data.get().log("命令启动失败：" + cmd[0] + "（" + e.getMessage() + "）");
            throw e;
        }
        try {
            if (!p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                Data.get().log("命令超时已强制结束：" + cmd[0] + "（" + timeoutSeconds + " 秒）");
            } else if (p.exitValue() != 0) {
                Data.get().log("命令退出状态 " + p.exitValue() + "：" + cmd[0]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    // 默认 10s：PowerShell 冷启动不够用，调用方用重载自己放宽
    private static void runHidden(String... cmd) throws java.io.IOException {
        runHidden(10, cmd);
    }

    public static boolean preferHighPerformanceGpu(String exe) {
        if (!windows() || exe == null || exe.isBlank()) return false;
        try {
            regGpu(exe);
            // WPF 版会同时把启动器自身设为高性能，避免启动器界面走核显
            String self = ProcessHandle.current().info().command().orElse("");
            if (!self.isBlank() && !self.equalsIgnoreCase(exe)) regGpu(self);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void regGpu(String exe) throws java.io.IOException {
        runHidden(30, "reg", "add",
                "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                "/v", exe, "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f");
    }

    public static boolean openUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            if (isMac()) {
                new ProcessBuilder("open", url).start();
                return true;
            }
            if (isLinux()) {
                new ProcessBuilder("xdg-open", url).start();
                return true;
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    d.browse(java.net.URI.create(url));
                    return true;
                }
            }
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openFolder(String path) {
        return openFile(path);
    }

    public static boolean openFile(String path) {
        if (path == null || path.isBlank()) return false;
        File f = new File(path);
        try {
            if (isMac()) {
                new ProcessBuilder("open", f.getAbsolutePath()).start();
                return true;
            }
            if (isLinux()) {
                new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();
                return true;
            }
            if (desktopOpen(f)) return true;
            // 不走 cmd.exe：路径里的 & | ^ 会被当命令分隔符；explorer/rundll32 都把路径当普通参数
            if (f.isDirectory()) {
                new ProcessBuilder("explorer.exe", f.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", f.getAbsolutePath()).start();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean desktopOpen(File f) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return false;
            java.awt.Desktop d = java.awt.Desktop.getDesktop();
            if (!d.isSupported(java.awt.Desktop.Action.OPEN)) return false;
            d.open(f);
            return true;
        } catch (Throwable e) {
            // Desktop 在没有图形环境（或 AWT 初始化失败）时会直接抛错，交给调用方回退
            return false;
        }
    }

    public static boolean deleteToRecycleBin(String path) {
        if (path == null || path.isBlank()) return false;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            try {
                String script = "tell application \"Finder\" to delete POSIX file \""
                        + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                Process p = new ProcessBuilder("osascript", "-e", script)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return false;
                }
                return p.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        if (!os.contains("win")) {
            // gio 失败就当失败，宁可删不掉也不能悄悄改成永久删除
            try {
                Process p = new ProcessBuilder("gio", "trash", path)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    return false;
                }
                return p.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        try {
            String target = path.replace("'", "''");
            boolean dir = new File(path).isDirectory();
            String api = dir
                    ? "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory('" + target + "','OnlyErrorDialogs','SendToRecycleBin')"
                    : "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('" + target + "','OnlyErrorDialogs','SendToRecycleBin')";
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Add-Type -AssemblyName Microsoft.VisualBasic; " + api)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            // PowerShell 起不来同样返回失败，让调用方把真实原因报出来
            return false;
        }
    }
}
