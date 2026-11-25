package tcraft.api;

import javafx.scene.Node;

import java.util.function.Consumer;
import java.util.function.Function;

// 启动器桥接接口 插件不应直接调用
public interface PluginHost {

    PluginInfo info();

    void addPage(String id, String title, String icon, Function<PageView, Node> builder);

    void addToolButton(Node node);

    void addTitleButton(Node node);

    void addLaunchButton(Node node);

    void addFloatButton(Node node);

    void addCommand(String name, String description, Consumer<CommandContext> handler);

    void runCommand(String name, String args);

    void toast(String title, String message, int type);

    void message(String title, String text, boolean error);

    void confirm(String title, String text, Runnable onOk, boolean danger);

    void custom(Node node);

    void closeModal();

    String input(String title, String hint, String defaultValue);

    String storageGet(String key, String defaultValue);

    void storageSet(String key, String value);

    void storageSave();

    void listen(String event, Consumer<Event> handler);

    void log(String message);

    void launchGame();

    void refreshLauncher();

    javafx.stage.Stage stage();

    String launcherVersion();
}
