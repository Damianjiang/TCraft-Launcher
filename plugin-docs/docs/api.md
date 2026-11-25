# API 参考

插件 API 位于 `tcraft.api` 包，API 版本 `1`。插件编译时只需要 `TCraftLauncher.jar` 和 JavaFX 17。

```java
import tcraft.api.*;
```

::: tip 稳定性
`tcraft.api` 下的类是稳定接口。`tcraft` 包下的其他类属于启动器内部实现，插件可以读取但不保证长期不变；
长期功能请优先使用 `ctx` 暴露的方法。
:::

::: warning 线程模型
生命周期、页面、按钮、命令、弹窗和事件回调都在 **JavaFX Application Thread** 执行。
`network()`、`files()`、`processes()`、`desktop()` 中的阻塞方法不要直接放在这些回调里；
用 `ctx.scheduler().async(...)` 转到后台，再用 `ctx.scheduler().ui(...)` 回到界面线程。
:::

## 能力总表

| 入口 | 返回类型 | 用途 |
| --- | --- | --- |
| `ctx.ui()` | `Ui` | 页面、标题栏/首页/工具/悬浮按钮、打开内置页 |
| `ctx.commands()` | `Commands` | 注册命令、执行命令、列出命令 |
| `ctx.dialogs()` | `Dialogs` | 消息、确认、输入、文件/目录选择 |
| `ctx.storage()` | `Storage` | 插件私有键值存储 |
| `ctx.events()` | `Events` | 启动器事件、插件间事件广播 |
| `ctx.network()` | `Network` | HTTP、WebSocket、TCP、UDP |
| `ctx.files()` | `Files` | 文本/字节读写、遍历、复制、移动、删除、zip |
| `ctx.system()` | `SystemInfo` | 系统信息、环境变量、剪贴板、打开文件/网址 |
| `ctx.processes()` | `Processes` | 执行命令、捕获 stdout/stderr、超时 |
| `ctx.desktop()` | `Desktop` | 截图、鼠标、键盘、组合键 |
| `ctx.scheduler()` | `Scheduler` | 后台任务、延时、周期任务、UI 线程调度 |
| `ctx.json()` | `Json` | JSON 解析、序列化、取值 |
| `ctx.launcher()` | `Launcher` | 实例列表、选择实例、启动游戏 |
| `ctx.stage()` | `Stage` | 主窗口 Stage |
| `ctx.log()` / `ctx.toast()` | `void` | 写日志 / 弹启动器通知 |

## 生命周期

入口类实现 `tcraft.api.TcraftPlugin`。

| 方法 | 调用时机 | 说明 |
| --- | --- | --- |
| `onLoad(PluginContext ctx)` | 插件加载时 | 注册页面、按钮、命令、事件；异常会导致加载失败 |
| `onEnable()` | 手动从禁用切到启用 | 启动器启动时不会调用 |
| `onDisable()` | 禁用、重载、删除、退出前 | 释放资源；之后类加载器关闭 |
| `onInstall(PluginContext ctx)` | 首次导入后 | 写默认配置、弹欢迎页 |
| `onUninstall(PluginContext ctx)` | 删除插件前 | 清理外部数据；`.data/<id>` 保留 |

```java
public class MyPlugin implements TcraftPlugin {
    private PluginContext ctx;

    @Override
    public void onLoad(PluginContext ctx) throws Exception {
        this.ctx = ctx;
        ctx.log("loaded");
    }

    @Override
    public void onDisable() {
        // 只需要处理插件自己的外部资源；调度器、网络连接和子进程会由启动器关闭。
    }
}
```

## PluginContext

| 成员 | 返回 | 说明 |
| --- | --- | --- |
| `info()` | `PluginInfo` | 清单信息快照 |
| `id()` / `name()` / `version()` | `String` | 清单字段 |
| `launcherVersion()` | `String` | 当前启动器版本 |
| `dataDir()` | `Path` | `.tcl/plugins/.data/<id>/` |
| `ui()` | `Ui` | 界面扩展 |
| `commands()` | `Commands` | 命令注册与执行 |
| `dialogs()` | `Dialogs` | 弹窗、输入、文件选择 |
| `storage()` | `Storage` | 键值存储 |
| `events()` | `Events` | 事件订阅与广播 |
| `network()` | `Network` | 网络能力 |
| `files()` | `Files` | 文件能力 |
| `system()` | `SystemInfo` | 系统能力 |
| `processes()` | `Processes` | 进程能力 |
| `desktop()` | `Desktop` | 桌面控制 |
| `scheduler()` | `Scheduler` | 后台调度 |
| `json()` | `Json` | JSON 工具 |
| `launcher()` | `Launcher` | 实例与启动 |
| `stage()` | `Stage` | 主窗口 |
| `log(String)` | `void` | 写 `.tcl/log/launcher.log` |
| `toast(title, message)` | `void` | 启动器通知 |
| `run(command, args)` | `void` | 以当前插件身份执行命令 |
| `close()` | `void` | 由启动器调用；插件通常不需要手动调用 |

## Network

`ctx.network()` 提供 HTTP、WebSocket、TCP 和 UDP。所有方法都是阻塞的，除非明确标注。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `get(url)` | `String` | GET，UTF-8 文本，30 秒超时 |
| `get(url, headers)` | `String` | GET，带请求头 |
| `post(url, body)` | `String` | POST，默认 `application/json` |
| `post(url, body, headers)` | `String` | POST，带请求头 |
| `postForm(url, fields)` | `String` | 表单 POST，自动 URL 编码 |
| `request(method, url, headers, body)` | `String` | 任意方法，默认 JSON Content-Type |
| `request(method, url, headers, body, contentType)` | `String` | 任意方法，自定义 Content-Type |
| `download(url, target)` | `Path` | 下载到文件，自动建父目录 |
| `download(url, target, progress)` | `Path` | 进度回调 `(read, total)` |
| `bytes(url)` | `byte[]` | 读取二进制，最大 64 MiB |
| `websocket(url, onText, onError)` | `WebSocketSession` | 建立 WebSocket |
| `websocket(url, onText, onError, onClose)` | `WebSocketSession` | 额外接收关闭原因 |
| `tcp(host, port)` / `tcp(host, port, timeoutMs)` | `TcpConnection` | TCP 客户端 |
| `udp()` / `udp(localPort)` | `UdpSocket` | UDP 套接字 |
| `close()` | `void` | 关闭该插件打开的全部连接 |

```java
String json = ctx.network().get("https://example.com/api");
String reply = ctx.network().post(
        "https://api.example.com/chat",
        "{\"message\":\"你好\"}",
        java.util.Map.of("Authorization", "Bearer " + token));
ctx.network().download(
        "https://example.com/file.zip",
        ctx.dataDir().resolve("file.zip"),
        (read, total) -> ctx.log("下载 " + read + "/" + total));
```

### WebSocketSession / TcpConnection / UdpSocket

`WebSocketSession`：`send(String)`、`isOpen()`、`close()`。

`TcpConnection`：`readLine()`、`readAll(maxBytes)`、`write(String)`、`writeLine(String)`、
`setReadTimeout(ms)`、`isOpen()`、`close()`。

`UdpSocket`：`send(host, port, text)`、`receive(timeoutMs)`、`localPort()`、`close()`；
`receive` 返回 `Network.Packet`，字段为 `host`、`port`、`text`。

```java
var ws = ctx.network().websocket("wss://example.com/ws",
        text -> ctx.log("收到 " + text),
        error -> ctx.log("WebSocket 错误 " + error));
ws.send("hello");
```

## Files

`ctx.files()` 封装 `java.nio.file`，路径使用 `Path`。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `readText(path)` / `readText(path, charset)` | `String` | 读取文本 |
| `readLines(path)` | `List<String>` | 读取所有行 |
| `writeText(path, text)` | `void` | 写入 UTF-8，自动建父目录 |
| `writeLines(path, lines)` | `void` | 写入多行 |
| `appendText(path, text)` | `void` | 追加文本 |
| `readBytes(path)` / `writeBytes(path, data)` | `byte[]` / `void` | 二进制读写 |
| `exists(path)` / `isDirectory(path)` / `isRegularFile(path)` | `boolean` | 类型判断 |
| `list(directory)` | `List<Path>` | 列一层，按文件名排序 |
| `walk(directory, maxDepth)` | `List<Path>` | 递归遍历 |
| `createDirectories(path)` / `createFile(path)` | `Path` | 创建目录/文件 |
| `size(path)` | `long` | 文件大小 |
| `lastModified(path)` / `setLastModified(path, instant)` | `Instant` / `void` | 修改时间 |
| `copy(source, target, replace)` | `void` | 文件或目录复制 |
| `move(source, target, replace)` | `void` | 移动 |
| `delete(path)` | `boolean` | 删除单文件/空目录 |
| `deleteRecursive(path)` | `void` | 删除整棵树，不跟随符号链接 |
| `tempFile(prefix, suffix)` / `tempDirectory(prefix)` | `Path` | 系统临时目录 |
| `zip(source, zipFile)` / `unzip(zipFile, target)` | `void` | 压缩/解压；解压有路径穿越和 2 GiB 上限检查 |

## SystemInfo

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `osName()` / `osVersion()` / `arch()` | `String` | 操作系统信息 |
| `javaVersion()` / `javaVendor()` | `String` | Java 信息 |
| `launcherVersion()` | `String` | 启动器版本 |
| `userName()` / `hostName()` | `String` | 当前用户/主机名 |
| `home()` / `workingDir()` / `tempDir()` / `dataDir()` | `Path` | 常用目录 |
| `cpuCores()` | `int` | CPU 核心数 |
| `maxMemory()` / `totalMemory()` / `freeMemory()` | `long` | 堆内存 |
| `env(name)` / `env()` | `String` / `Map` | 环境变量 |
| `property(key)` | `String` | JVM 属性 |
| `isWindows()` / `isMac()` / `isLinux()` | `boolean` | 平台判断 |
| `openUrl(url)` | `boolean` | 浏览器打开 http(s) |
| `open(path)` / `openFolder(path)` | `boolean` | 系统默认程序打开 |
| `clipboard()` / `clipboard(text)` | `String` / `void` | 读写系统剪贴板 |
| `notify(title, message)` / `beep()` | `void` | 通知/提示音 |

## Processes

`ctx.processes()` 用于执行外部程序。`run` 会阻塞，放在 `ctx.scheduler().async(...)` 里。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `start(commandLine)` | `Process` | 通过 `cmd.exe /c` 或 `/bin/sh -c` 启动 |
| `start(command)` | `Process` | 直接启动命令列表，不经过 shell |
| `start(command, workDir, env)` | `Process` | 指定工作目录和环境变量 |
| `run(commandLine)` | `Result` | 执行并等待最多 60 秒 |
| `run(commandLine, timeout)` | `Result` | 自定义超时 |
| `run(command, timeout)` | `Result` | 命令列表版本 |
| `close()` | `void` | 终止该插件启动的全部进程 |

`Processes.Result` 字段：`exitCode`、`stdout`、`stderr`、`timedOut`、`durationMs`；
`success()` 表示未超时且退出码为 0。`stdout`/`stderr` 在 macOS/Linux 按 UTF-8 解码，
Windows 按 `native.encoding` 解码，避免 `cmd.exe` 的中文输出乱码。

```java
ctx.scheduler().async(() -> {
    String command = ctx.system().isWindows() ? "ipconfig" : "uname";
    var result = ctx.processes().run(command, java.time.Duration.ofSeconds(20));
    ctx.scheduler().ui(() -> {
        if (result.success()) ctx.dialogs().message("输出", result.stdout);
        else ctx.dialogs().error("失败", result.stderr);
    });
});
```

## Desktop

`ctx.desktop()` 通过 `java.awt.Robot` 操作真实桌面。所有鼠标/键盘方法都可能影响用户当前操作，
执行前应先 `ctx.dialogs().confirmDanger(...)`。桌面能力不是所有机器都可用：无图形环境的
服务器、部分未启用 XTEST 的 Linux X11、Wayland 受限会话都可能让第一次调用抛出异常，
插件必须允许失败并提示用户。

| 方法 | 说明 |
| --- | --- |
| `screenSize()` | 主屏尺寸 |
| `screenshot(path)` | 截取主屏并保存 PNG |
| `pixelColor(x, y)` | 读取屏幕像素颜色 |
| `mouseMove(x, y)` | 绝对坐标移动鼠标 |
| `click()` / `click(buttonMask)` / `click(x, y)` | 单击 |
| `doubleClick()` | 双击 |
| `mousePress(mask)` / `mouseRelease(mask)` | 按下/释放鼠标键 |
| `mouseWheel(notches)` | 滚轮 |
| `typeText(text)` | 向当前焦点窗口输入文字；大写、标点、中文等走剪贴板粘贴 |
| `tapKey(key)` / `keyPress(key)` / `keyRelease(key)` | 单键 |
| `combo(keys...)` | 组合键；Windows/Linux 用 `CTRL`，macOS 用 `META`（Command） |
| `autoDelay(duration)` | 设置每个事件后的延迟 |

按键名支持：`ENTER`、`TAB`、`ESC`、`SPACE`、`BACKSPACE`、`DELETE`、`UP`、`DOWN`、
`LEFT`、`RIGHT`、`HOME`、`END`、`PAGEUP`、`PAGEDOWN`、`CTRL`、`SHIFT`、`ALT`、
`META`、`F1`–`F12`，以及单个字符。

```java
ctx.desktop().screenshot(ctx.dataDir().resolve("screen.png"));
ctx.desktop().click(400, 300);
ctx.desktop().combo(ctx.system().isMac() ? "META" : "CTRL", "S");
```

## Scheduler

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `async(task)` | `Task` | 后台执行一次 |
| `delay(duration, task)` | `Task` | 延迟执行 |
| `repeat(duration, task)` | `Task` | 固定间隔重复 |
| `ui(task)` | `void` | 回到 JavaFX 线程 |
| `close()` | `void` | 停止该插件全部任务 |
| `Task.cancel()` / `Task.cancel(interrupt)` | `void` | 取消任务 |

`Task.isCancelled()` 可查询状态。插件禁用、重载或卸载时调度器会自动关闭。

## Json

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `parse(text)` | `Object` | 解析为 `Map` / `List` / 字符串 / 数字 / 布尔 / `null` |
| `stringify(value)` | `String` | 序列化 Map、List、数组、字符串、数字、布尔 |
| `object(value)` / `object(value, key)` | `Map` | 取对象 |
| `array(value)` / `array(value, key)` | `List` | 取数组 |
| `string(value, key, def)` / `integer(...)` / `number(...)` / `bool(...)` | 对应类型 | 安全取值 |

## Launcher

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `instances()` | `List<Launcher.Instance>` | 全部实例快照 |
| `selected()` | `Launcher.Instance` | 当前实例，没有则为 `null` |
| `select(index)` / `select(nameOrVersion)` | `Launcher.Instance` | 选择并保存、刷新界面 |
| `launch()` | `void` | 通过正常登录/进度流程启动当前实例 |
| `launch(nameOrVersion)` | `void` | 选择后启动 |
| `gameDir()` / `baseDir()` / `pluginsDir()` | `Path` | 游戏/配置/插件目录 |
| `openPage(key)` | `void` | 打开内置页 |
| `toast(title, message)` | `void` | 启动器通知 |

`Launcher.Instance` 是 record：`index`、`name`、`version`、`loader`、`loaderVersion`、
`folder`、`favorite`。

::: warning
`launch()` 必须在 JavaFX 线程调用，因为它会使用正常启动流程和登录校验。
从后台线程调用时用 `ctx.scheduler().ui(() -> ctx.launcher().launch())`。
:::

## Ui / PageView / Widgets

### Ui

| 方法 | 说明 |
| --- | --- |
| `page(id, title, icon, builder)` | 在「更多」添加完整页面 |
| `page(id, title, builder)` | 同上，图标默认 `puzzle` |
| `titleButton(icon, tooltip, action)` | 标题栏右侧图标按钮 |
| `toolButton(text, action)` / `toolButton(Node)` | 「更多 → 实用工具」按钮 |
| `launchButton(text, action)` / `launchButton(Node)` | 首页「插件扩展」卡片按钮 |
| `floatButton(icon, tooltip, action)` | 右下角悬浮按钮 |
| `refresh()` | 重建插件相关界面 |
| `stage()` / `scene()` | 主窗口 Stage / Scene |
| `openPage(key)` | 打开内置页：`launch`、`instance`、`download`、`link`、`other` 等 |

### PageView

`page(...)` 的 builder 参数。`root()` 已带内边距和滚动容器。

| 方法 | 说明 |
| --- | --- |
| `root()` | 页面根 `VBox` |
| `header(title, subtitle)` | 标题与副标题 |
| `add(Node...)` | 追加节点 |
| `text(String)` | 追加正文 |
| `space(double)` | 追加垂直空白 |
| `context()` | 当前 `PluginContext` |

### Widgets

`title`、`text`、`label`、`button`、`primaryButton`、`dangerButton`、`card`、`row`、
`column`、`header`、`spacer`、`field`、`check`、`combo`、`image`、`grow`。
可以直接把任意 JavaFX 控件放进页面。

## Commands / CommandContext

```java
ctx.commands().register("demo.hello", "说明", c -> {
    String name = c.args.isBlank() ? "世界" : c.args;
    c.reply("你好 " + name);
});
ctx.commands().run("demo.hello", "参数");
```

`CommandContext` 字段：`name`、`args`、`pluginId`；方法：`argv()`、`reply(String)`、
`run(command, args)`、`log(String)`。命令名全局唯一，建议 `<插件id>.<动作>`。

## Events

```java
ctx.events().on("game.launch", e -> ctx.log("启动 " + e.string("instance")));
ctx.events().emit("myplugin.refresh", java.util.Map.of("reason", "manual"));
```

| 事件 | data 键 | 触发时机 |
| --- | --- | --- |
| `app.start` | `version` | 插件加载完成、主界面就绪 |
| `page.open` | `page` | 每次页面显示 |
| `game.launch` | `instance`、`version` | 点击启动、真正开始前 |
| `plugin.install` | `id` | 插件导入并加载完成 |

`Event` 提供 `name`、`data()`、`string(key)`、`bool(key)`、`integer(key, def)`、`list(key)`。
插件卸载时监听会自动取消。

## Dialogs

`message`、`error`、`confirm`、`confirmDanger`、`custom(Node)`、`close()`、`input(...)`。

新增原生文件选择：

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `chooseFile(title, extensions...)` | `Path` | 打开单个文件 |
| `chooseFiles(title, extensions...)` | `List<Path>` | 多选 |
| `saveFile(title, defaultName, extensions...)` | `Path` | 保存对话框 |
| `chooseFolder(title)` | `Path` | 选择目录 |

扩展名可以写 `txt` 或 `*.txt`。取消时返回 `null` 或空列表。

## Storage

`get`、`getInt`、`getLong`、`getDouble`、`getBool`、`set`、`save`。
落盘位置 `.tcl/plugins/.data/<id>/storage.properties`，删除插件不会清空。

## 直接操作 JavaFX

`ctx.stage()` 返回主窗口，可以拿到 `Scene` 和根节点：

```java
StackPane root = (StackPane) ctx.stage().getScene().getRoot();
Label badge = new Label("插件运行中");
StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
root.getChildren().add(badge);
```

这种方式可以改任何布局、样式和交互，但也可能影响启动器内置界面。请给节点设置唯一的
style class 或 id，并在 `onDisable()` 中清理自己添加的节点。

## 安全边界

插件与启动器同进程，拥有和启动器相同的文件、网络、进程和桌面权限；启动器只隔离类加载器，
不提供安全沙箱。安装前确认来源可信。涉及删除文件、执行命令、控制鼠标键盘、上传数据时，
插件必须自己弹出确认，并把危险操作限制在用户明确选择的范围内。
