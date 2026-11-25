package tcraft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Shell {

    private Shell() {
    }

    // 按空白切分 保留引号内空格
    public static List<String> splitArgs(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"' && i + 1 < text.length()
                        && (text.charAt(i + 1) == '"' || text.charAt(i + 1) == '\\')) {
                    cur.append(text.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                } else {
                    cur.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                quoted = true;
            } else if (Character.isWhitespace(c)) {
                if (quoted || cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    quoted = false;
                }
            } else {
                cur.append(c);
            }
        }
        if (quoted || cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
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
                String cfgHome = System.getenv("XDG_CONFIG_HOME");
                java.nio.file.Path cfg = cfgHome != null && !cfgHome.isBlank()
                        ? java.nio.file.Paths.get(cfgHome, "user-dirs.dirs")
                        : java.nio.file.Paths.get(home, ".config", "user-dirs.dirs");
                if (java.nio.file.Files.isRegularFile(cfg)
                        && java.nio.file.Files.size(cfg) <= 1024L * 1024) {
                    for (String line : java.nio.file.Files.readAllLines(cfg)) {
                        if (!line.startsWith("XDG_DESKTOP_DIR=")) continue;
                        String value = line.substring("XDG_DESKTOP_DIR=".length()).replace("\"", "").trim();
                        value = value.replace("$HOME", home);
                        if (!value.isEmpty()) {
                            java.nio.file.Path p = java.nio.file.Paths.get(value);
                            return p.isAbsolute() ? p : java.nio.file.Paths.get(home, value);
                        }
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
                // wmic 已移除 改用 PS
                runHidden(30, "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "(Get-Process -Id " + pid + " -ErrorAction Stop).PriorityClass='" + cls + "'");
                return;
            }
            // renice 数值越小优先级越高
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

    private static final java.util.Set<String> GPU_APPLIED = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    // 让指定 java 优先使用独显 仅 Windows 每个路径写一次
    public static void setHighPerformanceGpu(String javaPath) {
        if (!windows() || javaPath == null || javaPath.isBlank()) return;
        String path;
        try {
            path = new File(javaPath).getAbsolutePath();
        } catch (Exception e) {
            return;
        }
        String key = path.toLowerCase(java.util.Locale.ROOT);
        if (!GPU_APPLIED.add(key)) return;
        try {
            int code = runHidden(3, "reg", "add", "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
                    "/v", path, "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f");
            if (code != 0) throw new java.io.IOException("reg 退出状态 " + code);
        } catch (Exception e) {
            GPU_APPLIED.remove(key);
        }
    }

    private static int runHidden(long timeoutSeconds, String... cmd) throws java.io.IOException {
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (java.io.IOException e) {
            Data.get().log("命令启动失败：" + cmd[0] + "（" + Log.describe(e) + "）");
            throw e;
        }
        try {
            if (!p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                Data.get().log("命令超时已强制结束：" + cmd[0] + "（" + timeoutSeconds + " 秒）");
                return -1;
            }
            int code = p.exitValue();
            if (code != 0) Data.get().log("命令退出状态 " + code + "：" + cmd[0]);
            return code;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            return -1;
        }
    }

    private static void runHidden(String... cmd) throws java.io.IOException {
        runHidden(10, cmd);
    }

    // macOS 无 GUI 时用系统提示
    public static void macAlert(String title, String message) {
        if (!isMac()) return;
        try {
            String text = message == null ? "" : message;
            if (text.length() > 1800) text = text.substring(0, 1800) + "…";
            String esc = text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\r", "").replace("\n", "\\n");
            String head = (title == null ? "TCraft Launcher" : title)
                    .replace("\\", "\\\\").replace("\"", "\\\"");
            Process p = new ProcessBuilder("osascript", "-e", "display dialog \"" + esc
                    + "\" with title \"" + head + "\" buttons {\"好\"} default button 1 with icon stop")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (Throwable ignored) {
        }
    }

    // Linux 通知 缺 notify-send 跳过
    public static void linuxNotify(String title, String message) {
        if (!isLinux()) return;
        try {
            Process p = new ProcessBuilder("notify-send",
                    title == null ? "TCraft Launcher" : title,
                    message == null ? "" : message)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (Throwable ignored) {
        }
    }

    // Linux/macOS 的 open 不能只看进程起来了 等退出码才算成功
    private static boolean openExternally(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (fxThread()) {
                // 浏览器/文件管理器可能一直挂着 FX 线程不能等
                if (p.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    return p.exitValue() == 0;
                }
                Threads.start("TCL-Open-Check", () -> {
                    try {
                        p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return true;
            }
            if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                // 有的 open 会挂到浏览器退出 拉起就算成功 不卡调用线程
                return true;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean fxThread() {
        try {
            return javafx.application.Platform.isFxApplicationThread();
        } catch (Throwable t) {
            return false;
        }
    }

    // 只放行网页协议 防 file:/javascript:/自定义 handler 进系统打开
    private static boolean webUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase(java.util.Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openUrl(String url) {
        if (url == null || url.isBlank() || !webUrl(url)) return false;
        if (isMac()) return openExternally("open", url);
        if (isLinux()) {
            return openExternally("xdg-open", url) || openExternally("gio", "open", url);
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    try {
                        d.browse(java.net.URI.create(url));
                        return true;
                    } catch (Exception ignored) {
                        // URI 未转义时走 rundll32
                    }
                }
            }
            return openExternally("rundll32", "url.dll,FileProtocolHandler", url);
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
        if (isMac()) return openExternally("open", f.getAbsolutePath());
        if (isLinux()) {
            return openExternally("xdg-open", f.getAbsolutePath())
                    || openExternally("gio", "open", f.getAbsolutePath())
                    || desktopOpen(f);
        }
        try {
            if (desktopOpen(f)) return true;
            // 不走 cmd.exe &|^ 会被当命令分隔符
            if (f.isDirectory()) {
                new ProcessBuilder("explorer.exe", f.getAbsolutePath()).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } else {
                return openExternally("rundll32.exe", "url.dll,FileProtocolHandler", f.getAbsolutePath());
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
            // 无图形环境时 Desktop 会抛错
            return false;
        }
    }

    public static boolean deleteToRecycleBin(String path) {
        if (path == null || path.isBlank()) return false;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
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
        if (!os.contains("windows")) {
            // gio 失败即失败 不可回退成永久删除
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
            return false;
        }
    }
}
