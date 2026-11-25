# 网络、系统与桌面能力

这一页是“我想让插件做什么，应该调用哪个 API”的速查表。所有方法签名以 [API 参考](/api) 为准。

::: danger 权限边界
插件运行在启动器进程内，拥有和启动器相同的文件、网络、子进程和桌面权限，没有沙箱。
只安装可信来源的插件；插件在执行删除、命令、键鼠操作或上传数据前必须自行确认。
:::

## 先记住三条规则

1. **UI 线程只做 UI。** `onLoad`、页面 builder、按钮和命令回调都在 JavaFX 线程；网络、文件、进程、截图放 `ctx.scheduler().async(...)`。
2. **UI 更新回主线程。** 后台线程不能直接改控件，统一用 `ctx.scheduler().ui(...)`。
3. **插件资源自动回收。** 调度器、WebSocket、TCP、UDP 和子进程会在禁用/重载时关闭；插件自己创建的线程仍要在 `onDisable` 里停止。

## 需求到 API 的映射

| 想做的事 | 调用的 API |
| --- | --- |
| 调 OpenAI / 自建大模型接口 | `ctx.network().post(url, json, headers)` |
| 下载文件、显示进度 | `ctx.network().download(url, path, (read, total) -> ...)` |
| 连接实时服务 | `ctx.network().websocket(...)` / `ctx.network().tcp(...)` |
| 读写配置、日志、截图 | `ctx.files()`、`ctx.dataDir()` |
| 执行 `ipconfig`（Windows）或 `uname`（macOS/Linux）、`git`、脚本 | `ctx.processes().run(command, Duration.ofSeconds(30))` |
| 启动外部程序 | `ctx.processes().start(command)` |
| 读取系统、Java、内存信息 | `ctx.system()` |
| 复制/粘贴文本 | `ctx.system().clipboard()` / `ctx.system().clipboard(text)` |
| 打开网页/文件夹/文件 | `ctx.system().openUrl(url)` / `ctx.system().open(path)` |
| 截图 | `ctx.desktop().screenshot(path)` |
| 移动鼠标、点击、输入 | `ctx.desktop().mouseMove/click/typeText/combo` |
| 定时/轮询/后台任务 | `ctx.scheduler().delay/repeat/async` |
| 解析模型返回的 JSON | `ctx.json().parse(text)` |
| 选择并启动 Minecraft 实例 | `ctx.launcher().select(...)` / `ctx.launcher().launch()` |
| 在插件之间广播状态 | `ctx.events().emit(name, map)` |

## 网络请求

```java
ctx.scheduler().async(() -> {
    try {
        String body = ctx.json().stringify(Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", "你好"))));
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + key,
                "Content-Type", "application/json");
        String response = ctx.network().post(endpoint, body, headers);
        Object json = ctx.json().parse(response);
        ctx.scheduler().ui(() -> output.setText(response));
    } catch (Exception e) {
        ctx.scheduler().ui(() -> ctx.dialogs().error("请求失败", e.getMessage()));
    }
});
```

`post` 默认 30 秒超时；下载默认 5 分钟。`request` 支持任意 HTTP 方法，但 URL 只允许 `http/https`。
WebSocket 只允许 `ws/wss`。

## 执行命令并拿到输出

```java
ctx.scheduler().async(() -> {
    String command = ctx.system().isWindows() ? "ipconfig" : "uname";
    var result = ctx.processes().run(command, Duration.ofSeconds(20));
    String text = result.stdout + result.stderr;
    ctx.scheduler().ui(() -> output.setText(text));
});
```

`run(String)` 在 Windows 走 `cmd.exe /c`，macOS/Linux 走 `/bin/sh -c`；`run(List<String>)` 不经过 shell。
`Result` 有 `exitCode`、`stdout`、`stderr`、`timedOut`、`durationMs` 和 `success()`。
输出在 macOS/Linux 按 UTF-8 解码，Windows 按 `native.encoding` 解码。

::: warning
不要把用户输入直接拼进 shell 命令。需要参数化时用 `start(List.of("git", "status"))` 这类命令列表，
或先做白名单校验。插件拥有完整进程权限，命令注入会影响整台电脑。
:::

## 文件与数据目录

```java
Path data = ctx.dataDir();                  // .tcl/plugins/.data/<id>
ctx.files().createDirectories(data);
ctx.files().writeText(data.resolve("config.json"), json);
String saved = ctx.files().readText(data.resolve("config.json"));
```

`ctx.files().deleteRecursive(path)` 会删除整棵树，不跟随符号链接；执行前必须确认路径在
`ctx.dataDir()` 或用户明确选择的位置内。

`ctx.dialogs().chooseFile(...)`、`chooseFiles(...)`、`saveFile(...)`、`chooseFolder(...)`
会打开系统原生选择器，取消返回 `null` 或空列表。

## 系统信息与剪贴板

```java
String info = ctx.system().osName() + " " + ctx.system().arch()
        + " / Java " + ctx.system().javaVersion()
        + " / " + ctx.system().cpuCores() + " cores";
ctx.system().clipboard(info);
ctx.system().notify("系统信息", "已复制到剪贴板");
```

`SystemInfo` 还提供 `env()`、`property()`、`home()`、`workingDir()`、`tempDir()`、`maxMemory()`、
`freeMemory()`、`openUrl()`、`open()`、`openFolder()`、`beep()`。

## 截图、鼠标和键盘

桌面控制依赖图形会话；无显示器、部分 Linux X11（缺 XTEST）和受限 Wayland 会话可能直接失败。
插件应在 `try/catch` 中执行，并在失败时提示用户换到有桌面的环境。

```java
ctx.dialogs().confirmDanger("桌面操作", "将移动鼠标并输入文字，是否继续？", () -> {
    ctx.scheduler().async(() -> {
        ctx.desktop().screenshot(ctx.dataDir().resolve("screen.png"));
        ctx.desktop().click(400, 300);
        ctx.desktop().typeText("hello");
        ctx.desktop().combo(ctx.system().isMac() ? "META" : "CTRL", "S");
        ctx.scheduler().ui(() -> ctx.toast("完成", "桌面操作已执行"));
    });
});
```

按键名支持 `ENTER`、`TAB`、`ESC`、`SPACE`、`BACKSPACE`、`DELETE`、方向键、`HOME`、`END`、
`PAGEUP`、`PAGEDOWN`、`CTRL`、`SHIFT`、`ALT`、`META`、`F1`–`F12`，以及单个字符。
macOS 的 Command 键用 `META`，复制/粘贴等组合键不要固定写 `CTRL`。
`typeText` 对小写字母、数字、空格、Tab 和换行直接按键；大写、标点、中文等走剪贴板粘贴，
并在完成后恢复原剪贴板文本。

## 后台任务与轮询

```java
ctx.scheduler().delay(Duration.ofSeconds(2), () -> ctx.log("两秒后执行"));
ctx.scheduler().repeat(Duration.ofSeconds(30), () -> {
    // 轮询接口或检查文件
});
ctx.scheduler().ui(() -> label.setText("回到 UI 线程"));
```

插件禁用/重载时调度器自动 `shutdownNow`，重复任务不需要手动取消。

## 启动器实例与启动

```java
for (var instance : ctx.launcher().instances()) {
    ctx.log(instance.index() + " " + instance.name() + " / " + instance.version());
}
ctx.launcher().select("1.20.1");
ctx.scheduler().ui(() -> ctx.launcher().launch());
```

`select` 会保存选择并刷新界面；`launch` 会走正常登录、内存检查和启动进度流程。

## 插件间事件

```java
ctx.events().emit("aidesk.status", Map.of("state", "ready", "count", 3));
ctx.events().on("aidesk.status", event -> {
    ctx.log(event.string("state") + " " + event.integer("count", 0));
});
```

自定义事件会广播给所有已启用插件，包括发送者自己。事件名建议加插件 id 前缀。

## 一个最小“AI 动作执行器”

模型返回 JSON，插件先确认再执行：

```json
{
  "reply": "已准备检查 Java 版本",
  "actions": [
    { "type": "run", "command": "java -version" },
    { "type": "type", "text": "今天的待办" },
    { "type": "screenshot" }
  ]
}
```

```java
void execute(String reply) {
    var root = ctx.json().object(ctx.json().parse(reply));
    var actions = ctx.json().array(root, "actions");
    if (actions.isEmpty()) return;

    String summary = actions.stream()
            .map(a -> ctx.json().string(ctx.json().object(a), "type", "?"))
            .toList().toString();
    ctx.dialogs().confirmDanger("执行桌面动作", summary, () -> {
        ctx.scheduler().async(() -> {
            for (Object raw : actions) {
                var action = ctx.json().object(raw);
                switch (ctx.json().string(action, "type", "")) {
                    case "run" -> ctx.processes().run(ctx.json().string(action, "command", ""));
                    case "type" -> ctx.desktop().typeText(ctx.json().string(action, "text", ""));
                    case "key" -> ctx.desktop().tapKey(ctx.json().string(action, "key", ""));
                    // click / screenshot / open / clipboard 同理
                }
            }
        });
    });
}
```

完整可运行版本见
[AiDesktopPlugin.java](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/AiDesktopPlugin.java)。

## 当前没有开放的能力

- 没有安全沙箱、权限弹窗或网络域名白名单；插件和启动器同权限。
- 没有 FXML / WebView 模块，默认只加载 `javafx.controls`、`javafx.graphics`、`javafx.media`。
- 没有提权 API；需要管理员权限的操作由插件启动的系统进程自行处理。
- 没有插件市场在线安装协议；当前通过 zip 导入或目录复制。
