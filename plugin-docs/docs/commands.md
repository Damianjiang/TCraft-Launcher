# 命令、弹窗与事件

命令接入搜索与快捷执行，弹窗用于反馈，事件跟随启动器状态。

## 命令

```java
ctx.commands().register("demo.say", "弹出一个问候窗口", c -> {
    String name = c.args.isBlank() ? "世界" : c.args;
    ctx.dialogs().message("来自插件", "你好，" + name + "！");
    c.reply("已问候 " + name);
});
```

执行命令有三种方式：

1. 全局搜索（`Ctrl+K`）里输入命令名。
2. 「更多 → 插件 → 详情」里的命令列表。
3. 插件自己的按钮：`ctx.commands().run("demo.say", "参数")`。

命令名全局唯一，建议使用 `<插件id>.<动作>`。重复注册会覆盖同名的旧命令。

### 参数

`c.args` 是命令名后的整串文本，`c.argv()` 是按下空格切分后的列表：

```java
ctx.commands().register("demo.sum", "求和", c -> {
    int total = 0;
    for (String s : c.argv()) {
        try { total += Integer.parseInt(s); } catch (NumberFormatException ignored) { }
    }
    c.reply("结果：" + total);
});
```

### 从清单触发

`plugin.json` 的 `onInstall` 可以列出首次导入后自动执行的命令：

```json
{
  "onInstall": ["demo.welcome"]
}
```

启动器会在插件加载成功后依次执行这些命令。

## 弹窗

```java
// 消息
ctx.dialogs().message("标题", "内容");
// 错误样式
ctx.dialogs().error("出错了", "文件不存在。");
// 确认
ctx.dialogs().confirm("继续", "确定要删除吗？", () -> {
    // 用户点确定后执行
});
// 危险确认 红色
ctx.dialogs().confirmDanger("删除实例", "此操作不可撤销。", this::deleteInstance);
// 输入
String name = ctx.dialogs().input("重命名", "新的名称", "默认值");
if (name != null) { /* 用户点了确定 */ }
```

### 自定义弹窗

`custom(Node)` 会把节点放进启动器的模态遮罩。节点需要自己设置背景、圆角和尺寸：

```java
VBox card = new VBox(12);
card.setPadding(new javafx.geometry.Insets(22));
card.setPrefWidth(420);
card.setBackground(new javafx.scene.layout.Background(
        new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.WHITE,
                new javafx.scene.layout.CornerRadii(10), javafx.geometry.Insets.EMPTY)));
card.setEffect(new javafx.scene.effect.DropShadow(18, 0, 4,
        new javafx.scene.paint.Color(0, 0, 0, 0.22)));

card.getChildren().addAll(
        Widgets.title("自定义窗口"),
        Widgets.text("这里可以放任意 JavaFX 内容。"),
        Widgets.primaryButton("关闭", ctx.dialogs()::close));

ctx.dialogs().custom(card);
```

::: tip
自定义弹窗不会自动关闭，用 `ctx.dialogs().close()` 关闭即可。
:::

## 存储

```java
int count = ctx.storage().getInt("count", 0);
ctx.storage().set("count", count + 1);
ctx.storage().save();
```

- 位置：`.tcl/plugins/.data/<id>/storage.properties`
- 类型：字符串键值；数字和布尔用 `getInt` / `getLong` / `getDouble` / `getBool` 转换
- 自动保存：禁用、重载、卸载时启动器会调用 `save()`
- 数据独立：删除插件不会删除数据目录，便于重新安装后恢复

## 事件

```java
ctx.events().on("game.launch", e -> {
    ctx.log("准备启动：" + e.string("instance") + " / " + e.string("version"));
});

ctx.events().on("page.open", e -> {
    ctx.log("当前页面：" + e.string("page"));
});

ctx.events().on("app.start", e -> {
    ctx.log("启动器版本：" + e.string("version"));
});

ctx.events().on("plugin.install", e -> {
    ctx.log("安装完成：" + e.string("id"));
});
```

| 事件 | data | 说明 |
| --- | --- | --- |
| `app.start` | `version` | 插件加载完成、主界面就绪 |
| `page.open` | `page` | 每次页面显示；插件页面为 `plugin:<id>:<page>` |
| `game.launch` | `instance`、`version` | 点击启动、真正开始流程前 |
| `plugin.install` | `id` | 插件导入并加载完成 |

所有监听在插件卸载时自动取消。

### 插件间广播

```java
ctx.events().emit("demo.status", Map.of("ready", true, "count", 3));
ctx.events().on("demo.status", e -> {
    ctx.log(e.string("ready") + " / " + e.integer("count", 0));
});
```

自定义事件会广播给所有启用插件，包括发送者自己。事件名建议加插件 id 前缀，避免冲突。

## 后台任务

生命周期与事件回调都在 JavaFX 线程。下载、解压、网络请求请用插件自己的调度器：

```java
ctx.scheduler().async(() -> {
    try {
        String result = doHeavyWork();
        ctx.scheduler().ui(() -> ctx.dialogs().message("完成", result));
    } catch (Exception e) {
        ctx.log("失败：" + e);
    }
});
```

周期任务：

```java
ctx.scheduler().repeat(java.time.Duration.ofSeconds(30), () -> {
    // 轮询接口、检查文件
});
```

插件禁用或重载时调度器自动关闭；插件自己创建的线程仍需在 `onDisable` 中停止。

::: warning
不要在 JavaFX 线程里 `Thread.sleep`、同步网络请求或解压大文件，否则界面会卡住。
:::

## 安装与卸载钩子

```java
@Override
public void onInstall(PluginContext ctx) {
    ctx.dialogs().message("欢迎", "感谢安装！在「更多」里可以找到本插件。");
}

@Override
public void onUninstall(PluginContext ctx) {
    // 清理插件在外部创建的文件 插件数据目录会保留
}
```

`onInstall` 只在首次导入后调用；覆盖安装不会重复触发。
`onUninstall` 在用户确认删除、文件被移入回收站前调用。
