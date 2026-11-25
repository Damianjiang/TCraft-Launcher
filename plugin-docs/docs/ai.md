# 让 AI 写插件

这一页是给“把文档丢给 AI，让 AI 直接写一个能操控电脑的插件”准备的。目标不是让 AI 猜 API，
而是让它在明确的接口、线程规则、动作协议和打包结构下生成可编译代码。

## 推荐投喂方式

按顺序把这四份材料给 AI：

1. [llms.txt](https://damianjiang.github.io/TCraft-Launcher/wiki/llms.txt)：单文件 API 清单，包含全部公开方法签名、清单格式、线程规则和打包命令。
2. [AiDesktopPlugin.java](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/AiDesktopPlugin.java)：完整可编译的“AI 桌面助手”源码。
3. [ai-desktop-plugin.json](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/ai-desktop-plugin.json)：对应的 `plugin.json`。
4. 本页的“动作协议”和“验收清单”。

比直接丢 HTML 文档更有效，因为 `llms.txt` 没有导航、样式和重复页面，token 密度更高。

## 可直接复制的提示词

```text
你是 TCraft Launcher 插件开发者。只使用 `tcraft.api` 公开 API，写 Java 17 插件。

硬约束：
1. 入口类实现 `TcraftPlugin`，在 `onLoad(PluginContext ctx)` 注册页面/命令/事件。
2. 所有回调默认在 JavaFX 线程；网络、文件、进程、截图必须放进 `ctx.scheduler().async(...)`。
3. 后台线程更新 UI 必须用 `ctx.scheduler().ui(...)`，不要直接改控件。
4. 只用 `ctx.network()`、`ctx.files()`、`ctx.system()`、`ctx.processes()`、`ctx.desktop()`、
   `ctx.scheduler()`、`ctx.json()`、`ctx.launcher()`、`ctx.events()`、`ctx.dialogs()`。
5. 不引用 `tcraft` 包内部类；不把 JavaFX 或 tcraft.api 打进插件 jar。
6. 执行命令、删除文件、移动鼠标、输入文字、上传数据前必须让用户确认。
7. 输出必须包含：plugin.json、入口类完整源码、编译命令、zip 目录结构。

任务：
写一个“AI 桌面助手”插件。页面包含接口地址、模型、API Key、需求输入框和回复框。
点击发送后请求 OpenAI 兼容 `/chat/completions` 接口，要求模型返回：
{"reply":"...","actions":[{"type":"run|type|key|click|screenshot|open|clipboard", ...}]}
解析 actions 后先显示动作摘要，用户确认后再执行。最多 10 个动作。
```

## 插件最小契约

`plugin.json`：

```json
{
  "id": "demo.aidesktop",
  "name": "AI 桌面助手",
  "version": "1.0.0",
  "author": "TCraft",
  "description": "根据模型返回的动作操作桌面。",
  "icon": "bot",
  "main": "demo.aidesktop.AiDesktopPlugin",
  "api": 1
}
```

入口：

```java
package demo.aidesktop;

import tcraft.api.PluginContext;
import tcraft.api.TcraftPlugin;

public class AiDesktopPlugin implements TcraftPlugin {
    private PluginContext ctx;

    @Override
    public void onLoad(PluginContext ctx) {
        this.ctx = ctx;
        ctx.commands().register("aidesk.open", "打开助手",
                c -> ctx.ui().openPage("plugin:" + ctx.id() + ":main"));
    }
}
```

目录：

```text
ai-desktop-plugin/
├── plugin.json
├── ai-desktop-plugin.jar
├── icon.png
└── lib/                 # 可选依赖
```

编译与打包：

Windows PowerShell：

```powershell
javac --release 17 -encoding UTF-8 `
  --module-path "C:\javafx-sdk-17\lib" `
  --add-modules javafx.controls,javafx.media `
  -cp "C:\path\to\TCraftLauncher.jar" `
  -d out src\demo\aidesktop\AiDesktopPlugin.java

jar --create --file ai-desktop-plugin.jar -C out .
Compress-Archive -Path plugin.json, ai-desktop-plugin.jar -DestinationPath ai-desktop-plugin.zip
```

macOS / Linux：

```bash
JFX=/path/to/javafx-sdk-17/lib
LAUNCHER=/path/to/TCraftLauncher.jar
mkdir -p out
javac --release 17 -encoding UTF-8 \
  --module-path "$JFX" --add-modules javafx.controls,javafx.media \
  -cp "$LAUNCHER" -d out src/demo/aidesktop/AiDesktopPlugin.java
jar --create --file ai-desktop-plugin.jar -C out .
zip -qr ai-desktop-plugin.zip plugin.json ai-desktop-plugin.jar
```

## 动作协议

让模型只输出 JSON，不要 Markdown。推荐 schema：

```json
{
  "reply": "给用户看的一句话",
  "actions": [
    { "type": "run", "command": "java -version" },
    { "type": "type", "text": "今天的待办\n1. 写插件文档" },
    { "type": "key", "key": "ENTER" },
    { "type": "click", "x": 400, "y": 300 },
    { "type": "screenshot" },
    { "type": "open", "url": "https://example.com" },
    { "type": "clipboard", "text": "复制这段" }
  ]
}
```

| type | 字段 | 执行 API |
| --- | --- | --- |
| `run` | `command` | `ctx.processes().run(command, Duration.ofSeconds(30))` |
| `type` | `text` | `ctx.desktop().typeText(text)` |
| `key` | `key` | `ctx.desktop().tapKey(key)` |
| `click` | `x`、`y` | `ctx.desktop().click(x, y)` |
| `screenshot` | 无 | `ctx.desktop().screenshot(ctx.dataDir().resolve("ai-screen.png"))` |
| `open` | `url` | `ctx.system().openUrl(url)` |
| `clipboard` | `text` | `ctx.system().clipboard(text)` |

### 执行器骨架

```java
void execute(String reply) {
    var root = ctx.json().object(ctx.json().parse(reply));
    var actions = ctx.json().array(root, "actions");
    if (actions.isEmpty()) return;

    StringBuilder summary = new StringBuilder();
    for (Object raw : actions) {
        var action = ctx.json().object(raw);
        summary.append(ctx.json().string(action, "type", "?")).append('\n');
    }
    ctx.dialogs().confirmDanger("执行桌面动作", summary.toString(), () -> {
        ctx.scheduler().async(() -> {
            for (Object raw : actions) {
                var action = ctx.json().object(raw);
                try {
                    switch (ctx.json().string(action, "type", "")) {
                        case "run" -> {
                            String command = ctx.json().string(action, "command", "");
                            var result = ctx.processes().run(command, Duration.ofSeconds(30));
                            append(result.stdout + result.stderr);
                        }
                        case "type" -> ctx.desktop().typeText(ctx.json().string(action, "text", ""));
                        case "key" -> ctx.desktop().tapKey(ctx.json().string(action, "key", ""));
                        case "click" -> ctx.desktop().click(
                                ctx.json().integer(action, "x", 0),
                                ctx.json().integer(action, "y", 0));
                        case "screenshot" -> ctx.desktop().screenshot(
                                ctx.dataDir().resolve("ai-screen.png"));
                        case "open" -> ctx.system().openUrl(ctx.json().string(action, "url", ""));
                        case "clipboard" -> ctx.system().clipboard(ctx.json().string(action, "text", ""));
                        default -> append("skip: " + action);
                    }
                } catch (Exception e) {
                    append("failed: " + e.getMessage());
                }
            }
        });
    });
}
```

完整版本（带页面、接口请求、OpenAI 响应解析、动作摘要和回复框）：
[AiDesktopPlugin.java](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/AiDesktopPlugin.java)。

## 调用 OpenAI 兼容接口

```java
List<Map<String, String>> messages = List.of(
        Map.of("role", "system", "content", systemPrompt),
        Map.of("role", "user", "content", userText));
String body = ctx.json().stringify(Map.of(
        "model", model,
        "temperature", 0.2,
        "messages", messages));
Map<String, String> headers = Map.of(
        "Authorization", "Bearer " + apiKey,
        "Content-Type", "application/json");

ctx.scheduler().async(() -> {
    try {
        String response = ctx.network().post(endpoint, body, headers);
        Object json = ctx.json().parse(response);
        var choices = ctx.json().array(json, "choices");
        var message = ctx.json().object(choices.get(0), "message");
        String content = ctx.json().string(message, "content", "");
        ctx.scheduler().ui(() -> output.setText(content));
    } catch (Exception e) {
        ctx.scheduler().ui(() -> output.setText("失败：" + e.getMessage()));
    }
});
```

接口地址不要写死在某台机器；放进 `ctx.storage()` 或页面输入框。API Key 不建议写入
`storage.properties`，除非用户明确知道它是明文。

## AI 最容易写错的地方

| 错误 | 正确做法 |
| --- | --- |
| 在按钮回调里直接 `ctx.network().get(...)` | `ctx.scheduler().async(() -> ...)` |
| 后台线程直接 `label.setText(...)` | `ctx.scheduler().ui(() -> label.setText(...))` |
| 引用 `tcraft.Plugins`、`tcraft.Data` | 只用 `ctx.launcher()` / `ctx.network()` 等公开 API |
| 把 JavaFX 或 `tcraft.api` 打进插件 jar | 只编译自己的类，运行时由启动器提供 |
| 用 Java 21 编译 | 必须 `javac --release 17` |
| `plugin.json` 的 `main` 写文件名 | 写完整类名，如 `demo.aidesktop.AiDesktopPlugin` |
| 把未编译源码目录直接丢进插件目录 | 先编译出 jar，再和 `plugin.json` 打包 |
| 直接执行模型给的任意 shell | 先确认，再做命令白名单或风险提示 |

## 验收清单

- [ ] `plugin.json` 的 `id`、`name`、`version`、`main`、`api` 完整。
- [ ] `javac --release 17` 无错误。
- [ ] jar 内包含入口类，`plugin.json` 在插件根目录。
- [ ] 导入后「更多 → 插件」显示已启用，日志出现 `[插件][id] 已加载`。
- [ ] 页面能打开，按钮不卡界面。
- [ ] 网络失败、JSON 解析失败、命令超时都有错误提示。
- [ ] 执行命令/键鼠/删除前有确认。
- [ ] 禁用插件后没有残留线程、连接或子进程。

## 相关页面

- [API 参考](/api)
- [网络、系统与桌面能力](/capabilities)
- [完整示例与模板](/examples)
- [进阶与参考资料](/references)
