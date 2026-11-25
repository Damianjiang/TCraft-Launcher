package tcraft.api;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/// Screen capture, mouse and keyboard control through `java.awt.Robot`.
///
/// The robot needs a graphical desktop. On a headless server, or on a Linux
/// X server without the XTEST extension, the first call throws
/// `IllegalStateException`; callers must treat desktop control as optional.
/// These methods affect the real desktop. Ask the user for confirmation before
/// moving the pointer, typing text, or sending shortcuts.
public final class Desktop {

    private Robot robot;
    private int autoDelayMs = 30;

    Desktop() {
    }

    /// Returns the primary screen size.
    public Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    /// Captures the primary screen and writes a PNG image.
    public Path screenshot(Path target) throws IOException {
        BufferedImage image = robot().createScreenCapture(new Rectangle(screenSize()));
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        ImageIO.write(image, "png", target.toFile());
        return target;
    }

    /// Returns the color of one screen pixel.
    public Color pixelColor(int x, int y) {
        return robot().getPixelColor(x, y);
    }

    /// Moves the pointer to absolute screen coordinates.
    public void mouseMove(int x, int y) {
        robot().mouseMove(x, y);
    }

    /// Clicks the left button at the current pointer position.
    public void click() {
        click(InputEvent.BUTTON1_DOWN_MASK);
    }

    /// Clicks the specified mouse button at the current pointer position.
    public void click(int buttonMask) {
        Robot r = robot();
        r.mousePress(buttonMask);
        r.mouseRelease(buttonMask);
    }

    /// Moves the pointer and clicks the left button.
    public void click(int x, int y) {
        mouseMove(x, y);
        click();
    }

    /// Double-clicks the left button at the current pointer position.
    public void doubleClick() {
        click();
        click();
    }

    /// Presses a mouse button without releasing it.
    public void mousePress(int buttonMask) {
        robot().mousePress(buttonMask);
    }

    /// Releases a mouse button.
    public void mouseRelease(int buttonMask) {
        robot().mouseRelease(buttonMask);
    }

    /// Scrolls the wheel by the given number of notches; negative scrolls up.
    public void mouseWheel(int notches) {
        robot().mouseWheel(notches);
    }

    /// Sets the delay inserted after every robot event.
    public void autoDelay(Duration delay) {
        autoDelayMs = (int) Math.max(0, Math.min(1000, delay == null ? 30 : delay.toMillis()));
        if (robot != null) robot.setAutoDelay(autoDelayMs);
    }

    /// Types text into the focused window. Text beyond lowercase letters, digits, spaces,
    /// tabs and newlines uses clipboard paste so uppercase, punctuation and CJK work on
    /// Windows, macOS and Linux. macOS uses Command+V instead of Ctrl+V.
    public void typeText(String text) {
        if (text == null || text.isEmpty()) return;
        boolean simple = text.chars().allMatch(c ->
                c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                        || c == ' ' || c == '\n' || c == '\r' || c == '\t');
        if (simple) {
            for (char c : text.toCharArray()) {
                if (c == '\n' || c == '\r') tapKey("ENTER");
                else if (c == '\t') tapKey("TAB");
                else tapKey(String.valueOf(c));
            }
            return;
        }
        String previous = null;
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            previous = value == null ? null : value.toString();
        } catch (Exception ignored) {
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        combo(isMac() ? "META" : "CTRL", "V");
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (previous != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(previous), null);
        }
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    /// Taps one key by name: `ENTER`, `TAB`, `ESC`, `CTRL`, `F1`, or a single character.
    public void tapKey(String key) {
        int code = keyCode(key);
        Robot r = robot();
        r.keyPress(code);
        r.keyRelease(code);
    }

    /// Presses a key without releasing it.
    public void keyPress(String key) {
        robot().keyPress(keyCode(key));
    }

    /// Releases a key.
    public void keyRelease(String key) {
        robot().keyRelease(keyCode(key));
    }

    /// Presses modifiers, taps the last key, and releases the modifiers.
    public void combo(String... keys) {
        if (keys == null || keys.length == 0) return;
        Robot r = robot();
        int[] pressed = new int[keys.length - 1];
        try {
            for (int i = 0; i < keys.length - 1; i++) {
                pressed[i] = keyCode(keys[i]);
                r.keyPress(pressed[i]);
            }
            int last = keyCode(keys[keys.length - 1]);
            r.keyPress(last);
            r.keyRelease(last);
        } finally {
            for (int i = pressed.length - 1; i >= 0; i--) {
                if (pressed[i] != 0) r.keyRelease(pressed[i]);
            }
        }
    }

    private Robot robot() {
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(autoDelayMs);
            } catch (AWTException e) {
                throw new IllegalStateException("desktop robot is unavailable", e);
            }
        }
        return robot;
    }

    private static int keyCode(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("key is empty");
        String name = key.toUpperCase(java.util.Locale.ROOT);
        return switch (name) {
            case "ENTER", "RETURN" -> KeyEvent.VK_ENTER;
            case "TAB" -> KeyEvent.VK_TAB;
            case "ESC", "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "SPACE" -> KeyEvent.VK_SPACE;
            case "BACKSPACE" -> KeyEvent.VK_BACK_SPACE;
            case "DELETE", "DEL" -> KeyEvent.VK_DELETE;
            case "UP" -> KeyEvent.VK_UP;
            case "DOWN" -> KeyEvent.VK_DOWN;
            case "LEFT" -> KeyEvent.VK_LEFT;
            case "RIGHT" -> KeyEvent.VK_RIGHT;
            case "HOME" -> KeyEvent.VK_HOME;
            case "END" -> KeyEvent.VK_END;
            case "PAGEUP", "PAGE_UP" -> KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN", "PAGE_DOWN" -> KeyEvent.VK_PAGE_DOWN;
            case "CTRL", "CONTROL" -> KeyEvent.VK_CONTROL;
            case "SHIFT" -> KeyEvent.VK_SHIFT;
            case "ALT" -> KeyEvent.VK_ALT;
            case "META", "WIN", "WINDOWS" -> KeyEvent.VK_META;
            case "F1" -> KeyEvent.VK_F1;
            case "F2" -> KeyEvent.VK_F2;
            case "F3" -> KeyEvent.VK_F3;
            case "F4" -> KeyEvent.VK_F4;
            case "F5" -> KeyEvent.VK_F5;
            case "F6" -> KeyEvent.VK_F6;
            case "F7" -> KeyEvent.VK_F7;
            case "F8" -> KeyEvent.VK_F8;
            case "F9" -> KeyEvent.VK_F9;
            case "F10" -> KeyEvent.VK_F10;
            case "F11" -> KeyEvent.VK_F11;
            case "F12" -> KeyEvent.VK_F12;
            default -> {
                int code = key.length() == 1 ? KeyEvent.getExtendedKeyCodeForChar(key.charAt(0)) : 0;
                if (code == 0) throw new IllegalArgumentException("unknown key: " + key);
                yield code;
            }
        };
    }
}
