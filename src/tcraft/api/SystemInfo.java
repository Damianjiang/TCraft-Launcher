package tcraft.api;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;

/// Host and launcher information plus small desktop helpers.
///
/// `notify` and clipboard methods may be called from a background thread; opening
/// files or URLs delegates to the operating system.
public final class SystemInfo {

    private final PluginHost host;

    SystemInfo(PluginHost host) {
        this.host = host;
    }

    /// Returns the operating-system name reported by Java.
    public String osName() {
        return System.getProperty("os.name", "");
    }

    /// Returns the operating-system version.
    public String osVersion() {
        return System.getProperty("os.version", "");
    }

    /// Returns the CPU architecture.
    public String arch() {
        return System.getProperty("os.arch", "");
    }

    /// Returns the current Java version.
    public String javaVersion() {
        return System.getProperty("java.version", "");
    }

    /// Returns the Java vendor.
    public String javaVendor() {
        return System.getProperty("java.vendor", "");
    }

    /// Returns the launcher version.
    public String launcherVersion() {
        return host.launcherVersion();
    }

    /// Returns the current user name.
    public String userName() {
        return System.getProperty("user.name", "");
    }

    /// Returns the machine host name, or an empty string when unavailable.
    public String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    /// Returns the user home directory.
    public Path home() {
        return Path.of(System.getProperty("user.home", "."));
    }

    /// Returns the process working directory.
    public Path workingDir() {
        return Path.of(System.getProperty("user.dir", "."));
    }

    /// Returns the system temporary directory.
    public Path tempDir() {
        return Path.of(System.getProperty("java.io.tmpdir", "."));
    }

    /// Returns the plugin data directory.
    public Path dataDir() {
        return host.info().dir;
    }

    /// Returns the number of processors visible to Java.
    public int cpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /// Returns the maximum heap size in bytes.
    public long maxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /// Returns the currently allocated heap size in bytes.
    public long totalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /// Returns the free heap size in bytes.
    public long freeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /// Reads one system environment variable.
    public String env(String name) {
        return System.getenv().getOrDefault(name, "");
    }

    /// Returns an immutable snapshot of the process environment.
    public Map<String, String> env() {
        return Map.copyOf(System.getenv());
    }

    /// Reads one JVM system property.
    public String property(String key) {
        return System.getProperty(key, "");
    }

    /// Tests whether the host is Windows.
    public boolean isWindows() {
        return normalizedOs().contains("windows");
    }

    /// Tests whether the host is macOS.
    public boolean isMac() {
        String os = normalizedOs();
        return os.contains("mac") || os.contains("darwin");
    }

    /// Tests whether the host is Linux.
    public boolean isLinux() {
        return normalizedOs().contains("linux");
    }

    private String normalizedOs() {
        return osName().toLowerCase(java.util.Locale.ROOT);
    }

    /// Opens an http(s) URL in the default browser.
    public boolean openUrl(String url) {
        return tcraft.Shell.openUrl(url);
    }

    /// Opens a file or folder with the operating system's default handler.
    public boolean open(Path path) {
        return path != null && tcraft.Shell.openFile(path.toString());
    }

    /// Opens a folder in the system file manager.
    public boolean openFolder(Path path) {
        return path != null && tcraft.Shell.openFolder(path.toString());
    }

    /// Reads text from the system clipboard.
    public String clipboard() {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /// Writes text to the system clipboard.
    public void clipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text == null ? "" : text), null);
        } catch (Exception e) {
            throw new IllegalStateException("cannot access clipboard", e);
        }
    }

    /// Shows a launcher toast; this method marshals itself onto the JavaFX thread.
    public void notify(String title, String message) {
        host.toast(title, message, 1);
    }

    /// Plays the platform beep when supported.
    public void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
        }
    }
}
