package tcraft.api;

import javafx.scene.Node;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Modal dialogs, text input, and native file choosers. All methods run on the
/// JavaFX application thread; `input` blocks until the user closes the dialog.
public final class Dialogs {

    private final PluginHost host;

    Dialogs(PluginHost host) {
        this.host = host;
    }

    /// Shows an information message.
    public void message(String title, String text) {
        host.message(title, text, false);
    }

    /// Shows an error-styled message.
    public void error(String title, String text) {
        host.message(title, text, true);
    }

    /// Shows a confirmation dialog and runs `onOk` when accepted.
    public void confirm(String title, String text, Runnable onOk) {
        host.confirm(title, text, onOk, false);
    }

    /// Shows a red danger confirmation dialog.
    public void confirmDanger(String title, String text, Runnable onOk) {
        host.confirm(title, text, onOk, true);
    }

    /// Shows arbitrary JavaFX content in the launcher modal layer.
    public void custom(Node content) {
        host.custom(content);
    }

    /// Closes the current plugin modal.
    public void close() {
        host.closeModal();
    }

    /// Shows a single-line input; returns `null` when cancelled.
    public String input(String title, String hint, String defaultValue) {
        return host.input(title, hint, defaultValue);
    }

    /// Opens a native file chooser and returns the selected file, or `null`.
    public Path chooseFile(String title, String... extensions) {
        FileChooser chooser = chooser(title, extensions);
        File file = chooser.showOpenDialog(host.stage());
        return file == null ? null : file.toPath();
    }

    /// Opens a native multi-file chooser and returns the selected files.
    public List<Path> chooseFiles(String title, String... extensions) {
        FileChooser chooser = chooser(title, extensions);
        List<File> files = chooser.showOpenMultipleDialog(host.stage());
        if (files == null) return List.of();
        List<Path> out = new ArrayList<>();
        for (File file : files) out.add(file.toPath());
        return List.copyOf(out);
    }

    /// Opens a native save dialog and returns the target path, or `null`.
    public Path saveFile(String title, String defaultName, String... extensions) {
        FileChooser chooser = chooser(title, extensions);
        if (defaultName != null && !defaultName.isBlank()) chooser.setInitialFileName(defaultName);
        File file = chooser.showSaveDialog(host.stage());
        return file == null ? null : file.toPath();
    }

    /// Opens a native directory chooser and returns the selected directory, or `null`.
    public Path chooseFolder(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title == null ? "选择文件夹" : title);
        File file = chooser.showDialog(host.stage());
        return file == null ? null : file.toPath();
    }

    private static FileChooser chooser(String title, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title == null ? "选择文件" : title);
        if (extensions != null && extensions.length > 0) {
            List<String> patterns = new ArrayList<>();
            for (String extension : extensions) {
                if (extension == null || extension.isBlank()) continue;
                patterns.add(extension.startsWith("*.") ? extension : "*." + extension.replaceFirst("^\\.", ""));
            }
            if (!patterns.isEmpty()) {
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文件", patterns));
            }
        }
        return chooser;
    }
}
