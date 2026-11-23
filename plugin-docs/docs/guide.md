# 五分钟写出第一个插件

示例添加一个页面、标题栏按钮和命令。

## 准备

- JDK 17 或更高版本（`javac -version` 可查）。
- JavaFX 17 SDK，编译时需要读取 JavaFX 类型：[openjfx.io](https://openjfx.io/)。
  也可以直接用启动器首次运行后下载到 `.tcl/dependencies/` 的 JavaFX jar。
- 一份 `TCraftLauncher.jar`（Release 里的 jar，或本地构建产物 `dist/TCraftLauncher.jar`）。

## 1. 写 plugin.json

```json
{
  "id": "demo.hello",
  "name": "Hello 插件",
  "version": "1.0.0",
  "author": "你的名字",
  "description": "我的第一个 TCraft Launcher 插件",
  "icon": "puzzle",
  "main": "HelloPlugin",
  "api": 1
}
```

`main` 是入口类的完整类名。没有 package 就写 `HelloPlugin`。

## 2. 写入口类

```java
import javafx.scene.control.Label;
import tcraft.api.CommandContext;
import tcraft.api.PluginContext;
import tcraft.api.TcraftPlugin;
import tcraft.api.Widgets;

public class HelloPlugin implements TcraftPlugin {

    private PluginContext ctx;

    @Override
    public void onLoad(PluginContext ctx) throws Exception {
        this.ctx = ctx;

        ctx.ui().page("main", "Hello 页面", "puzzle", view -> {
            view.header("Hello 插件", "这个页面完全由插件绘制。");
            Label text = Widgets.text("这里放任意 JavaFX 控件，也可以用 Widgets 保持启动器风格。");
            text.setWrapText(true);
            view.add(text);

            var card = Widgets.card("试试看");
            card.add(Widgets.primaryButton("弹一个窗口", () -> ctx.commands().run("hello.say", "页面按钮")));
            view.add(card);
            return null;
        });

        ctx.ui().titleButton("star", "Hello 插件", () -> ctx.commands().run("hello.say", "标题栏"));

        ctx.commands().register("hello.say", "弹出一个问候窗口", this::say);
    }

    private void say(CommandContext c) {
        String name = c.args.isBlank() ? "世界" : c.args;
        ctx.dialogs().message("来自插件", "你好，" + name + "！");
        c.reply("已问候 " + name);
    }
}
```

::: tip 页面返回值
builder 可返回节点，也可返回 `null` 后自行 `view.add(...)`。
:::

## 3. 编译

把 `TCraftLauncher.jar` 和 JavaFX SDK 的 `lib` 目录放进 classpath。
**Windows 用分号，macOS / Linux 用冒号。**

### Windows PowerShell

```powershell
$JFX = "C:\javafx-sdk-17\lib"
$LAUNCHER = "C:\path\to\TCraftLauncher.jar"
New-Item -ItemType Directory -Force out | Out-Null

javac --release 17 -encoding UTF-8 `
  -cp "$LAUNCHER;$JFX\*" `
  -d out src\HelloPlugin.java

jar --create --file hello.jar -C out .
```

### macOS / Linux

```bash
JFX=/path/to/javafx-sdk-17/lib
LAUNCHER=/path/to/TCraftLauncher.jar
mkdir -p out
javac --release 17 -encoding UTF-8 \
  -cp "$LAUNCHER:$JFX/*" \
  -d out src/HelloPlugin.java
jar --create --file hello.jar -C out .
```

::: tip 不想下载 JavaFX SDK？
启动器首次运行后，JavaFX 会下载到 `.tcl/dependencies/win/openjfx/`（Linux 为 `linux/openjfx/`）。
把该目录下的 `javafx-*.jar` 全部加进 classpath，效果相同。
:::

## 4. 打包成可导入的插件

新建一个目录，放入清单和 jar：

```text
demo-hello/
├── plugin.json
└── hello.jar
```

压缩成 `demo-hello.zip`，或直接把整个目录复制到 `.tcl/plugins/demo.hello/`。

## 5. 导入并验证

1. 打开「更多 → 插件」。手动复制进去的插件点「刷新」即可看到。
2. 点「导入插件」，选择 `demo-hello.zip`，确认安全提示。成功后提示「插件已导入」。
3. 「更多」左栏出现「Hello 页面」，点进去是插件绘制的界面。
4. 右上角多了一个星形按钮，点击弹出问候窗口。

日志 `.tcl/log/launcher.log` 应出现：

```text
[插件][demo.hello] 已加载 Hello 插件 1.0.0
```

## 三种打包形态

目录插件、单 jar、zip 导入包的差异见 [plugin.json 清单](/manifest#目录插件与单-jar)。

## 下一步

- [plugin.json 清单](/manifest)
- [界面与布局](/ui)
- [命令与事件](/commands)
