package tcraft;

import java.io.File;

/** 跨平台打开 URL、文件、文件夹。类名叫 Shell 是为了避开 Platform。 */
public final class Shell {

    private Shell() {
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix");
    }

    /** 用系统默认浏览器打开 URL。 */
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

    /** 用系统文件管理器打开文件夹。 */
    public static boolean openFolder(String path) {
        return openFile(path);
    }

    /** 用系统默认程序打开文件。 */
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
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.OPEN)) {
                    d.open(f);
                    return true;
                }
            }
            new ProcessBuilder("cmd.exe", "/c", "start", "", f.getAbsolutePath()).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 打开文件夹并选中文件。 */
    public static boolean revealFile(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            if (isMac()) {
                new ProcessBuilder("open", "-R", path).start();
                return true;
            }
            if (isLinux()) {
                // xdg-open 只能打开目录本身
                File f = new File(path);
                File dir = f.isDirectory() ? f : f.getParentFile();
                if (dir != null) {
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                    return true;
                }
                return false;
            }
            new ProcessBuilder("explorer.exe", "/select,", path).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 删除到回收站。 */
    public static boolean deleteToRecycleBin(String path) {
        if (path == null || path.isBlank()) return false;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            try {
                String script = "tell application \"Finder\" to delete POSIX file \""
                        + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                Process p = new ProcessBuilder("osascript", "-e", script).start();
                return p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        if (!os.contains("win")) {
            try {
                Process p = new ProcessBuilder("gio", "trash", path).start();
                if (p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) return true;
            } catch (Exception ignored) {
            }
            try {
                return java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
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
                    "Add-Type -AssemblyName Microsoft.VisualBasic; " + api).start();
            return p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                    && p.exitValue() == 0;
        } catch (Exception e) {
            // PowerShell 失败就永久删除
            try {
                return java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path));
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
