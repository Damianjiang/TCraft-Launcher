# 完整示例与模板

## 直接导入的示例插件

[下载 hello-plugin.zip](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/hello-plugin.zip){target="_blank"}

这个包包含 `plugin.json` 和一个 `hello.jar`，导入后可以立即看到：

- 「更多」里新增「Hello 页面」
- 标题栏多一个星形按钮
- 「更多 → 实用工具」多一个「Hello 按钮」
- 首页出现「插件扩展」卡片
- 右下角出现悬浮按钮
- 命令 `hello.say` 出现在全局搜索和插件详情中

源码：[HelloPlugin.java](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/HelloPlugin.java){target="_blank"} ·
清单：[plugin.json](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/plugin.json){target="_blank"}

## AI 桌面助手（网络 + 桌面动作）

[下载 ai-desktop-plugin.zip](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/ai-desktop-plugin.zip){target="_blank"}

这是一个完整的 OpenAI 兼容客户端加桌面动作执行器：

- 页面填写接口地址、模型、API Key 和需求
- 后台请求 `/chat/completions`，解析 `choices[0].message.content`
- 模型返回 `{"reply":"...","actions":[...]}`
- 显示动作摘要，确认后执行 `run`、`type`、`key`、`click`、`screenshot`、`open`、`clipboard`
- 最多执行 10 个动作，失败会写入回复框

源码：[AiDesktopPlugin.java](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/AiDesktopPlugin.java){target="_blank"} ·
清单：[ai-desktop-plugin.json](https://damianjiang.github.io/TCraft-Launcher/wiki/examples/ai-desktop-plugin.json){target="_blank"}

## 模板一：Hello 插件（最小可运行）

`plugin.json`

```json
{
  "id": "demo.hello",
  "name": "Hello 插件",
  "version": "1.0.0",
  "author": "TCraft",
  "description": "插件系统联调示例：页面、按钮、命令、弹窗、存储与事件。",
  "icon": "puzzle",
  "main": "HelloPlugin",
  "api": 1
}
```

`HelloPlugin.java`

```java
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tcraft.api.CommandContext;
import tcraft.api.PluginContext;
import tcraft.api.TcraftPlugin;
import tcraft.api.Widgets;

public class HelloPlugin implements TcraftPlugin {

    private PluginContext ctx;

    @Override
    public void onLoad(PluginContext ctx) {
        this.ctx = ctx;

        ctx.ui().titleButton("puzzle", "Hello 插件", () -> ctx.commands().run("hello.say", "标题栏"));
        ctx.ui().toolButton("Hello 按钮", () -> ctx.commands().run("hello.say", "实用工具"));
        ctx.ui().launchButton("Hello 首页按钮", () -> ctx.commands().run("hello.say", "首页"));
        ctx.ui().floatButton("star", "Hello 悬浮按钮", () -> ctx.commands().run("hello.say", "悬浮按钮"));

        ctx.ui().page("main", "Hello 页面", "puzzle", view -> {
            view.header("Hello 插件", "这个页面完全由插件绘制。");

            Label intro = Widgets.text("插件可以使用 JavaFX 任意控件，也可以用 Widgets 保持启动器风格。");
            intro.setWrapText(true);
            view.add(intro);

            var card = Widgets.card("交互测试");
            Label status = Widgets.label("还没点过按钮。", 12.5);

            HBox row = Widgets.row(10,
                    Widgets.primaryButton("弹出问候", () -> {
                        ctx.commands().run("hello.say", "页面按钮");
                        status.setText("刚才弹出了一个问候窗口。");
                    }),
                    Widgets.button("写配置", () -> {
                        ctx.storage().set("lastClick", System.currentTimeMillis());
                        ctx.storage().save();
                        status.setText("已写入 storage：" + ctx.storage().get("lastClick"));
                    }));

            card.add(row);
            card.add(status);
            VBox.setMargin(card, new Insets(14, 0, 0, 0));
            view.add(card);
            return null;
        });

        ctx.commands().register("hello.say", "弹出一个问候窗口，参数为名字", this::say);
        ctx.events().on("page.open", e -> ctx.log("页面打开：" + e.string("page")));
    }

    private void say(CommandContext c) {
        String name = c.args.isBlank() ? "世界" : c.args;
        ctx.dialogs().message("来自插件", "你好，" + name + "！");
        c.reply("已问候 " + name);
    }
}
```

## 模板二：四个位置各放一个按钮

```java
@Override
public void onLoad(PluginContext ctx) {
    Runnable action = () -> ctx.dialogs().message("快捷入口", "这个按钮来自插件。");

    ctx.ui().titleButton("star", "快捷入口", action);
    ctx.ui().toolButton("快捷入口", action);
    ctx.ui().launchButton("快捷入口", action);
    ctx.ui().floatButton("rocket", "快捷入口", action);
}
```

四个按钮都调用同一个动作，可以按需替换成打开页面、执行命令或启动实例。

## 模板三：带实时数据的页面

```java
import javafx.animation.*;
import javafx.scene.control.Label;
import javafx.util.Duration;

@Override
public void onLoad(PluginContext ctx) {
    ctx.ui().page("clock", "运行时长", "clock", view -> {
        view.header("运行时长", "演示 JavaFX 动画与页面生命周期。");

        Label time = Widgets.title("0 秒");
        var card = Widgets.card("本次启动");
        card.add(time);
        view.add(card);

        long start = System.currentTimeMillis();
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long s = (System.currentTimeMillis() - start) / 1000;
            time.setText(s + " 秒");
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();

        // 页面关闭时停止动画 避免后台一直跑
        view.root().sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) timer.stop();
        });
        return null;
    });
}
```

## 模板四：命令 + 存储 + 事件

```java
@Override
public void onLoad(PluginContext ctx) {
    ctx.commands().register("stats.launch", "记录一次启动", c -> {
        int n = ctx.storage().getInt("launches", 0) + 1;
        ctx.storage().set("launches", n);
        ctx.storage().save();
        c.reply("累计启动 " + n + " 次");
    });

    ctx.events().on("game.launch", e -> {
        int n = ctx.storage().getInt("launches", 0) + 1;
        ctx.storage().set("launches", n);
        ctx.storage().save();
        ctx.log("记录启动：" + e.string("instance"));
    });

    ctx.events().on("app.start", e -> {
        ctx.log("启动器版本 " + e.string("version")
                + "，历史启动 " + ctx.storage().getInt("launches", 0) + " 次");
    });
}
```

配合清单里的自动命令：

```json
{
  "id": "demo.stats",
  "name": "启动统计",
  "version": "1.0.0",
  "main": "StatsPlugin",
  "api": 1,
  "onInstall": ["stats.launch"]
}
```

## 模板五：自定义弹窗

```java
@Override
public void onLoad(PluginContext ctx) {
    ctx.ui().toolButton("打开自定义窗口", () -> {
        var card = Widgets.card("自定义窗口");
        card.setPrefWidth(420);
        card.add(Widgets.text("这里可以放任意 JavaFX 内容。"));
        card.add(Widgets.row(10,
                Widgets.primaryButton("关闭", ctx.dialogs()::close)));
        ctx.dialogs().custom(card);
    });
}
```

## 打包与安装

```text
my-plugin/
├── plugin.json
└── my-plugin.jar
```

压缩成 zip 后，在「更多 → 插件 → 导入插件」选择它。
也可以把整个目录复制到 `.tcl/plugins/<id>/`，然后在插件页点「刷新」。

编译命令见[五分钟写出第一个插件](/guide#_3-编译)。

## 调试清单

- [ ] `.tcl/log/launcher.log` 有 `[插件][<id>] 已加载 ...`
- [ ] 「更多」左栏出现插件页面，点开不报错
- [ ] 「更多 → 插件」状态为「已启用」
- [ ] 标题栏 / 工具 / 首页 / 悬浮按钮出现在预期位置
- [ ] 全局搜索里能搜到命令，执行有反馈
- [ ] 禁用再启用后，页面和按钮正常重建
- [ ] 删除后文件进入回收站，无残留文件锁
