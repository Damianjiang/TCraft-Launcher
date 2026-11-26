package tcraft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Plugin project templates and capability entry points shown on the plugins page.
final class PluginDev {

    private static final String DOCS = PluginsPage.DOCS;

    private PluginDev() {
    }

    static void buildCard(MainWindow win, VBox page) {
        Ui.Card card = Ui.card("插件开发");
        card.setSpacing(8);
        VBox.setMargin(card, new Insets(0, 0, 16, 0));
        Label hint = Ui.label("生成可编译的插件项目，查看客户端已开放的网络、系统、进程、桌面和启动器能力。", 12.5, Theme.gray2);
        hint.setWrapText(true);
        card.add(hint);
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(
                Ui.button("新建插件项目", Ui.BTN_THEME, () -> newProject(win)),
                Ui.button("能力清单", Ui.BTN_NORMAL, () -> showCapabilities(win)),
                Ui.button("打开项目目录", Ui.BTN_NORMAL, () -> openProjects(win)));
        card.add(actions);
        page.getChildren().add(card);
    }

    private static void newProject(MainWindow win) {
        VBox card = PluginsPage.dialogCard("新建插件项目", 620);
        Label hint = Ui.label("项目会生成在 .tcl/plugin-projects 下，不会直接装进插件列表；编译后把 dist 里的 zip 导入即可。", 12, Theme.gray2);
        hint.setWrapText(true);
        card.getChildren().add(hint);

        TextField id = Ui.field("com.example.myplugin");
        TextField name = Ui.field("我的插件");
        TextField author = Ui.field("你的名字");
        TextField main = Ui.field("com.example.MyPlugin");
        ComboBox<String> template = Ui.combo(new String[]{"空白插件", "系统助手", "AI 助手"}, 0);
        template.setPrefWidth(180);

        card.getChildren().addAll(
                formRow("插件 id", id),
                formRow("显示名称", name),
                formRow("作者", author),
                formRow("入口类", main),
                formRow("模板", template));

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(
                Ui.button("取消", Ui.BTN_NORMAL, win::closeModal),
                Ui.button("创建项目", Ui.BTN_THEME, () -> {
                    try {
                        Path dir = createProject(id.getText(), name.getText(), author.getText(),
                                main.getText(), template.getSelectionModel().getSelectedIndex());
                        win.closeModal();
                        Shell.openFolder(dir.toString());
                        win.toast("项目已创建", dir.toString(), 1);
                    } catch (Exception e) {
                        win.toast("创建失败", Log.describe(e), 3);
                    }
                }));
        VBox.setMargin(actions, new Insets(14, 0, 0, 0));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private static HBox formRow(String label, javafx.scene.Node field) {
        Label key = Ui.label(label, 12, Theme.gray2);
        key.setMinWidth(72);
        if (field instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(region, Priority.ALWAYS);
        }
        HBox row = new HBox(10, key, field);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(row, new Insets(8, 0, 0, 0));
        return row;
    }

    static Path createProject(String idInput, String nameInput, String authorInput,
                              String mainInput, int templateIndex) throws IOException {
        String id = idInput == null ? "" : idInput.trim();
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,63}")) {
            throw new IllegalArgumentException("插件 id 只能使用字母、数字、点、下划线、短横线，长度 2–64");
        }
        String name = blank(nameInput) ? id : nameInput.trim();
        String author = blank(authorInput) ? "TCraft" : authorInput.trim();
        String main = blank(mainInput) ? id.replaceAll("[^A-Za-z0-9]", "") + "Plugin" : mainInput.trim();
        String className = main.contains(".") ? main.substring(main.lastIndexOf('.') + 1) : main;
        String packageName = main.contains(".") ? main.substring(0, main.lastIndexOf('.')) : "";
        if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("入口类名不合法：" + className);
        }
        if (!packageName.isEmpty()) {
            for (String part : packageName.split("\\.")) {
                if (!part.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                    throw new IllegalArgumentException("包名不合法：" + packageName);
                }
            }
        }
        Path root = Data.get().baseDir().resolve("plugin-projects").resolve(id);
        if (Files.exists(root)) throw new IOException("项目目录已存在：" + root);
        Files.createDirectories(root.resolve("src").resolve(packageName.replace('.', '/')));

        String display = escapeJson(name);
        String desc = switch (templateIndex) {
            case 1 -> "系统助手插件模板：系统信息、剪贴板、文件、进程和截图。";
            case 2 -> "AI 助手插件模板：HTTP、JSON、异步调度与输入框。";
            default -> "TCraft Launcher 插件模板。";
        };
        String manifest = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "author": "%s",
                  "description": "%s",
                  "icon": "puzzle",
                  "main": "%s",
                  "api": %d
                }
                """.formatted(id, display, escapeJson(author), escapeJson(desc), main, Plugins.API_VERSION);
        Files.writeString(root.resolve("plugin.json"), manifest, StandardCharsets.UTF_8);

        String packageLine = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
        String source = switch (templateIndex) {
            case 1 -> systemTemplate(packageLine, className, name, id);
            case 2 -> aiTemplate(packageLine, className, name, id);
            default -> blankTemplate(packageLine, className, name, id);
        };
        Files.writeString(root.resolve("src").resolve(packageName.replace('.', '/')).resolve(className + ".java"),
                source, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("build.ps1"), buildPs1(id), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("build.sh"), buildSh(id), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("README.md"), readme(name, id, main), StandardCharsets.UTF_8);
        return root;
    }

    private static String blankTemplate(String packageLine, String className, String name, String id) {
        return """
                %simport javafx.scene.control.Label;
                import tcraft.api.PluginContext;
                import tcraft.api.TcraftPlugin;
                import tcraft.api.Widgets;

                public class %s implements TcraftPlugin {

                    private PluginContext ctx;

                    @Override
                    public void onLoad(PluginContext ctx) {
                        this.ctx = ctx;
                        ctx.ui().page("main", "%s", "puzzle", view -> {
                            view.header("%s", "由 TCraft Launcher 插件模板生成。");
                            Label text = Widgets.text("在 src 中继续实现页面、命令和系统能力。");
                            text.setWrapText(true);
                            view.add(text);
                            var card = Widgets.card("开始");
                            card.add(Widgets.primaryButton("运行示例命令",
                                    () -> ctx.commands().run("%s.hello", "页面")));
                            view.add(card);
                            return null;
                        });
                        ctx.commands().register("%s.hello", "示例命令", c -> c.reply("插件运行正常"));
                    }
                }
                """.formatted(packageLine, className, escapeJava(name), escapeJava(name), id, id);
    }

    private static String systemTemplate(String packageLine, String className, String name, String id) {
        return """
                %simport javafx.scene.control.Label;
                import javafx.scene.control.TextArea;
                import tcraft.api.PluginContext;
                import tcraft.api.TcraftPlugin;
                import tcraft.api.Widgets;

                import java.time.Duration;

                public class %s implements TcraftPlugin {

                    private PluginContext ctx;

                    @Override
                    public void onLoad(PluginContext ctx) {
                        this.ctx = ctx;
                        ctx.ui().page("main", "%s", "monitor", view -> {
                            view.header("%s", "系统助手模板：可直接替换成你的自动化流程。");
                            String info = "系统：" + ctx.system().osName() + " " + ctx.system().osVersion()
                                    + "\\n架构：" + ctx.system().arch()
                                    + "\\nJava：" + ctx.system().javaVersion()
                                    + "\\n启动器：" + ctx.launcherVersion();
                            Label label = Widgets.text(info);
                            label.setWrapText(true);
                            view.add(label);

                            TextArea output = new TextArea();
                            output.setEditable(false);
                            output.setPrefRowCount(6);

                            var card = Widgets.card("系统操作");
                            card.add(Widgets.row(8,
                                    Widgets.primaryButton("复制系统信息", () -> {
                                        ctx.system().clipboard(info);
                                        ctx.toast("已复制", "系统信息已写入剪贴板");
                                    }),
                                    Widgets.button("执行系统命令", () -> runCommand(ctx,
                                            ctx.system().isWindows() ? "ipconfig" : "uname", output)),
                                    Widgets.button("选择文件", () -> {
                                        var file = ctx.dialogs().chooseFile("选择文本文件", "txt", "log");
                                        if (file != null) {
                                            ctx.scheduler().async(() -> {
                                                try {
                                                    String text = ctx.files().readText(file);
                                                    ctx.scheduler().ui(() -> output.setText(text));
                                                } catch (Exception e) {
                                                    ctx.scheduler().ui(() -> ctx.dialogs().error("读取失败", e.getMessage()));
                                                }
                                            });
                                        }
                                    }),
                                    Widgets.button("截图", () -> ctx.dialogs().confirmDanger(
                                            "屏幕截图", "将截取当前主屏幕并保存到插件数据目录。",
                                            () -> ctx.scheduler().async(() -> {
                                                try {
                                                    var shot = ctx.desktop().screenshot(ctx.dataDir().resolve("screen.png"));
                                                    ctx.scheduler().ui(() -> ctx.toast("截图完成", shot.toString()));
                                                } catch (Exception e) {
                                                    ctx.scheduler().ui(() -> ctx.dialogs().error("截图失败", e.getMessage()));
                                                }
                                            })))));
                            card.add(output);
                            view.add(card);
                            return null;
                        });
                        ctx.commands().register("%s.info", "输出系统信息", c -> c.reply(
                                ctx.system().osName() + " / " + ctx.system().arch() + " / Java " + ctx.system().javaVersion()));
                    }

                    private void runCommand(PluginContext ctx, String command, TextArea output) {
                        ctx.scheduler().async(() -> {
                            try {
                                var result = ctx.processes().run(command, Duration.ofSeconds(30));
                                ctx.scheduler().ui(() -> output.setText(result.stdout + result.stderr));
                            } catch (Exception e) {
                                ctx.scheduler().ui(() -> ctx.dialogs().error("命令失败", e.getMessage()));
                            }
                        });
                    }
                }
                """.formatted(packageLine, className, escapeJava(name), escapeJava(name), id);
    }

    private static String aiTemplate(String packageLine, String className, String name, String id) {
        return """
                %simport javafx.scene.control.PasswordField;
                import javafx.scene.control.TextArea;
                import javafx.scene.control.TextField;
                import tcraft.api.PluginContext;
                import tcraft.api.TcraftPlugin;
                import tcraft.api.Widgets;

                import java.util.List;
                import java.util.Map;

                public class %s implements TcraftPlugin {

                    private PluginContext ctx;

                    @Override
                    public void onLoad(PluginContext ctx) {
                        this.ctx = ctx;
                        ctx.ui().page("main", "%s", "bot", view -> {
                            view.header("%s", "把提示词发给 OpenAI 兼容接口，回复显示在下方。");
                            TextField endpoint = Widgets.field("接口地址，例如 https://api.openai.com/v1/chat/completions");
                            endpoint.setText(ctx.storage().get("endpoint", "https://api.openai.com/v1/chat/completions"));
                            TextField model = Widgets.field("模型，例如 gpt-4o-mini");
                            model.setText(ctx.storage().get("model", "gpt-4o-mini"));
                            PasswordField apiKey = new PasswordField();
                            apiKey.setPromptText("API Key，仅保存在当前页面");
                            TextArea prompt = new TextArea();
                            prompt.setPromptText("输入你的需求");
                            prompt.setPrefRowCount(4);
                            TextArea output = new TextArea();
                            output.setEditable(false);
                            output.setPrefRowCount(10);

                            var card = Widgets.card("发送请求");
                            card.add(endpoint, model, apiKey, prompt, Widgets.row(8,
                                    Widgets.primaryButton("发送", () -> send(endpoint, model, apiKey, prompt, output)),
                                    Widgets.button("清空", () -> output.clear())));
                            card.add(output);
                            view.add(card);
                            return null;
                        });
                        ctx.commands().register("%s.ask", "向接口发送一条消息", c -> {
                            ctx.log("AI 命令：" + c.args);
                            ctx.toast("AI", "请在插件页面配置接口和 Key");
                        });
                    }

                    private void send(TextField endpoint, TextField model, PasswordField apiKey,
                                      TextArea prompt, TextArea output) {
                        String url = endpoint.getText();
                        String text = prompt.getText();
                        if (url == null || url.isBlank() || text == null || text.isBlank()) {
                            ctx.dialogs().error("参数不足", "接口地址和提示词不能为空");
                            return;
                        }
                        ctx.storage().set("endpoint", url);
                        ctx.storage().set("model", model.getText());
                        ctx.storage().save();
                        String body = ctx.json().stringify(Map.of(
                                "model", model.getText(),
                                "messages", List.of(Map.of("role", "user", "content", text))));
                        Map<String, String> headers = Map.of(
                                "Authorization", "Bearer " + apiKey.getText(),
                                "Content-Type", "application/json");
                        output.setText("请求中…");
                        ctx.scheduler().async(() -> {
                            try {
                                String response = ctx.network().post(url, body, headers);
                                Object json = ctx.json().parse(response);
                                var choices = ctx.json().array(json, "choices");
                                String content = "";
                                if (!choices.isEmpty()) {
                                    var message = ctx.json().object(ctx.json().object(choices.get(0)), "message");
                                    content = ctx.json().string(message, "content", response);
                                }
                                String finalContent = content;
                                ctx.scheduler().ui(() -> output.setText(finalContent));
                            } catch (Exception e) {
                                ctx.scheduler().ui(() -> {
                                    output.setText("请求失败：" + e.getMessage());
                                    ctx.log("AI 请求失败：" + e);
                                });
                            }
                        });
                    }
                }
                """.formatted(packageLine, className, escapeJava(name), escapeJava(name), id);
    }

    private static String buildPs1(String id) {
        return """
                param(
                    [Parameter(Mandatory = $true)][string]$LauncherJar,
                    [string]$JavaFxLib = ""
                )
                $ErrorActionPreference = "Stop"
                $root = Split-Path -Parent $MyInvocation.MyCommand.Path
                $out = Join-Path $root "out"
                $dist = Join-Path $root "dist"
                if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }
                if (Test-Path $dist) { Remove-Item -LiteralPath $dist -Recurse -Force }
                New-Item -ItemType Directory -Path $out, $dist | Out-Null
                $sources = Get-ChildItem -Path (Join-Path $root "src") -Recurse -File -Filter *.java |
                    ForEach-Object { $_.FullName }
                $args = @("--release", "17", "-encoding", "UTF-8", "-d", $out)
                if ($JavaFxLib) { $args += @("--module-path", $JavaFxLib, "--add-modules", "javafx.controls,javafx.media") }
                $args += @("-cp", $LauncherJar)
                & javac @args $sources
                if ($LASTEXITCODE -ne 0) { throw "javac failed" }
                & jar --create --file (Join-Path $dist "%s.jar") -C $out .
                Copy-Item (Join-Path $root "plugin.json") (Join-Path $dist "plugin.json")
                Compress-Archive -Path (Join-Path $dist "*") -DestinationPath (Join-Path $root "%s.zip") -Force
                Write-Output "Built: $(Join-Path $root '%s.zip')"
                """.formatted(id, id, id);
    }

    private static String buildSh(String id) {
        return """
                #!/usr/bin/env sh
                set -eu
                ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
                LAUNCHER_JAR="${1:-}"
                JFX_LIB="${2:-}"
                if [ -z "$LAUNCHER_JAR" ]; then
                  echo "usage: sh build.sh <TCraftLauncher.jar> [javafx-lib-dir]" >&2
                  exit 1
                fi
                rm -rf "$ROOT/out" "$ROOT/dist" "$ROOT/%s.zip"
                mkdir -p "$ROOT/out" "$ROOT/dist"
                if [ -n "$JFX_LIB" ]; then
                  javac --release 17 -encoding UTF-8 --module-path "$JFX_LIB" \
                    --add-modules javafx.controls,javafx.media -cp "$LAUNCHER_JAR" \
                    -d "$ROOT/out" $(find "$ROOT/src" -name '*.java' -type f)
                else
                  javac --release 17 -encoding UTF-8 -cp "$LAUNCHER_JAR" \
                    -d "$ROOT/out" $(find "$ROOT/src" -name '*.java' -type f)
                fi
                jar --create --file "$ROOT/dist/%s.jar" -C "$ROOT/out" .
                cp "$ROOT/plugin.json" "$ROOT/dist/plugin.json"
                (cd "$ROOT/dist" && zip -qr "$ROOT/%s.zip" .)
                echo "Built: $ROOT/%s.zip"
                """.formatted(id, id, id, id);
    }

    private static String readme(String name, String id, String main) {
        return """
                # %s

                TCraft Launcher 插件项目。入口类：`%s`。

                ## 构建

                需要 JDK 17+、`TCraftLauncher.jar` 和 JavaFX 17 SDK。

                PowerShell：

                ```powershell
                .\\build.ps1 -LauncherJar "C:\\path\\to\\TCraftLauncher.jar" -JavaFxLib "C:\\path\\to\\javafx-sdk-17\\lib"
                ```

                macOS / Linux：

                ```bash
                sh ./build.sh /path/to/TCraftLauncher.jar /path/to/javafx-sdk-17/lib
                ```

                产物是项目根目录的 `%s.zip`。在「更多 → 插件 → 导入插件」里选择它。

                ## 文档

                %s
                """.formatted(name, main, id, DOCS);
    }

    private static void showCapabilities(MainWindow win) {
        VBox card = PluginsPage.dialogCard("插件能力清单", 640);
        Label intro = Ui.label("插件与启动器同进程，拥有完整文件与网络权限；下面是可以直接调用的稳定 API。", 12, Theme.gray2);
        intro.setWrapText(true);
        card.getChildren().add(intro);
        String[][] rows = {
                {"网络", "ctx.network()", "HTTP、WebSocket、TCP、UDP；支持请求头、下载进度"},
                {"文件", "ctx.files()", "读写文本/字节、遍历、复制、移动、删除、zip/unzip"},
                {"系统", "ctx.system()", "系统信息、环境变量、剪贴板、打开文件/网址、系统通知"},
                {"进程", "ctx.processes()", "执行 shell 命令、超时、stdout/stderr、子进程跟踪"},
                {"桌面", "ctx.desktop()", "截图、像素颜色、鼠标移动/点击/滚轮、按键/组合键、输入文字"},
                {"调度", "ctx.scheduler()", "后台任务、延时、周期任务、回到 JavaFX 线程"},
                {"JSON", "ctx.json()", "解析、序列化、对象/数组取值"},
                {"启动器", "ctx.launcher()", "实例列表、选择实例、启动游戏、打开页面、toast"},
                {"事件", "ctx.events()", "启动器事件、自定义事件广播、插件间通信"},
                {"弹窗", "ctx.dialogs()", "消息、确认、输入、文件选择、保存、目录选择"}
        };
        for (String[] row : rows) {
            HBox line = new HBox(10);
            line.setAlignment(Pos.CENTER_LEFT);
            Label key = Ui.semi(row[0], 12.5, Theme.text);
            key.setMinWidth(44);
            Label api = Ui.label(row[1], 12, Theme.accentDark);
            api.setMinWidth(120);
            Label desc = Ui.label(row[2], 11.5, Theme.gray2);
            desc.setWrapText(true);
            HBox.setHgrow(desc, Priority.ALWAYS);
            line.getChildren().addAll(key, api, desc);
            VBox.setMargin(line, new Insets(5, 0, 5, 0));
            card.getChildren().add(line);
        }
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(actions, new Insets(14, 0, 0, 0));
        actions.getChildren().addAll(
                Ui.button("打开文档", Ui.BTN_THEME, () -> Shell.openUrl(DOCS)),
                Ui.button("关闭", Ui.BTN_NORMAL, win::closeModal));
        card.getChildren().add(actions);
        win.showModal(card);
    }

    private static void openProjects(MainWindow win) {
        Path dir = Data.get().baseDir().resolve("plugin-projects");
        try {
            Files.createDirectories(dir);
            Shell.openFolder(dir.toString());
        } catch (Exception e) {
            win.toast("无法打开", e.getMessage(), 3);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
