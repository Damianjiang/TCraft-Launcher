# API 参考

插件 API 位于 `tcraft.api` 包，API 版本 `1`。插件编译时只需要 `TCraftLauncher.jar` 和 JavaFX 17。

```java
import tcraft.api.*;
```

::: tip 稳定性
`tcraft.api` 下的类是稳定接口。`tcraft` 包下的其他类属于启动器内部实现，插件可以读取但不保证长期不变；
需要长期兼容的能力请优先使用 `ctx` 暴露的方法。
:::

## 生命周期

入口类实现 `tcraft.api.TcraftPlugin`，所有回调都在 JavaFX 线程执行。

| 方法 | 调用时机 | 说明 |
| --- | --- | --- |
| `onLoad(PluginContext ctx)` | 插件加载时 | 注册页面、按钮、命令、事件；抛出异常会导致插件加载失败 |
| `onEnable()` | 用户从「已禁用」切换到启用 | 启动器启动时不会调用，只有手动启用才会 |
| `onDisable()` | 禁用、重载、删除、退出前 | 释放资源；之后类加载器关闭，所有贡献被移除 |
| `onInstall(PluginContext ctx)` | 首次导入后 | 弹欢迎窗口、写默认配置 |
| `onUninstall(PluginContext ctx)` | 删除插件前 | 清理外部数据；插件自己的 `.data/<id>` 不会被删除 |

```java
public class MyPlugin implements TcraftPlugin {
    @Override
    public void onLoad(PluginContext ctx) throws Exception {
        ctx.log("loaded");
    }

    @Override
    public void onDisable() {
        // 关闭线程 保存状态
    }
}
```

## PluginContext

| 成员 | 返回 | 说明 |
| --- | --- | --- |
| `info()` | `PluginInfo` | 清单信息快照 |
| `id()` / `name()` / `version()` | `String` | 清单字段快捷方式 |
| `launcherVersion()` | `String` | 当前启动器版本 |
| `dataDir()` | `Path` | 插件数据目录 `.tcl/plugins/.data/<id>/` |
| `ui()` | `Ui` | 界面扩展 |
| `commands()` | `Commands` | 命令注册与执行 |
| `dialogs()` | `Dialogs` | 弹窗与输入 |
| `storage()` | `Storage` | 键值存储 |
| `events()` | `Events` | 事件订阅 |
| `stage()` | `Stage` | 主窗口 Stage，可拿到 Scene |
| `log(String)` | `void` | 写入 `.tcl/log/launcher.log` |
| `run(String command, String args)` | `void` | 以当前插件身份执行命令 |

## PluginInfo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` / `name` / `version` / `author` / `description` / `icon` / `main` / `website` | `String` | 清单字段 |
| `api` | `int` | 目标 API 版本 |
| `dir` | `Path` | 插件数据目录 |

## Ui

| 方法 | 说明 |
| --- | --- |
| `page(id, title, icon, builder)` | 在「更多」添加完整页面，builder 返回 `Node` 或 `null` |
| `page(id, title, builder)` | 同上，图标默认 `puzzle` |
| `titleButton(icon, tooltip, action)` | 标题栏右侧图标按钮 |
| `toolButton(text, action)` / `toolButton(Node)` | 「更多 → 实用工具」按钮 |
| `launchButton(text, action)` / `launchButton(Node)` | 首页「插件扩展」卡片按钮 |
| `floatButton(icon, tooltip, action)` | 右下角悬浮圆形按钮 |
| `refresh()` | 请求启动器重建插件相关界面 |
| `stage()` / `scene()` | 主窗口 Stage / Scene |
| `openPage(key)` | 打开内置页面：`launch`、`instance`、`download`、`link`、`other`、`setup` 等 |

页面 `id` 只允许字母、数字、`.`、`_`、`-`，同一个插件内不能重复。

## PageView

`page(...)` 的 builder 参数。`root()` 是页面容器（已带启动器内边距与滚动）。

| 方法 | 说明 |
| --- | --- |
| `root()` | 页面根 `VBox`，可以直接 `getChildren().add(...)` |
| `header(title, subtitle)` | 添加统一风格的标题与副标题 |
| `add(Node...)` | 向页面追加节点 |
| `text(String)` | 追加一段正文 |
| `space(double)` | 追加垂直空白 |
| `context()` | 当前 `PluginContext` |

```java
ctx.ui().page("main", "标题", "puzzle", view -> {
    view.header("我的页面", "副标题");
    view.add(new Label("任意 JavaFX 控件"));
    return null;
});
```

## Widgets

启动器风格基础组件。返回类型是 JavaFX 节点或轻量包装，可直接放进页面。

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `title(String)` | `Label` | 26px 标题 |
| `text(String)` | `Label` | 13px 正文 |
| `label(String, double)` | `Label` | 指定字号正文 |
| `button(String, Runnable)` | `StackPane` | 普通按钮 |
| `primaryButton(String, Runnable)` | `StackPane` | 主题色按钮 |
| `dangerButton(String, Runnable)` | `StackPane` | 红色按钮 |
| `card(String title)` | `Card` | 卡片容器，`.add(Node...)` 添加内容 |
| `row(double spacing, Node...)` | `HBox` | 水平布局 |
| `column(double spacing, Node...)` | `VBox` | 垂直布局 |
| `header(String, String)` | `VBox` | 标题组合 |
| `spacer()` | `Region` | 弹性空白 |
| `field(String hint)` | `TextField` | 输入框 |
| `check(String text, boolean checked)` | `Check` | 复选框，`isSelected()` 取值 |
| `combo(String[] items, int selected)` | `ComboBox<String>` | 下拉框 |
| `image(String path, double w, double h)` | `ImageView` | 按路径加载图片 |
| `grow(Node)` | `void` | 在 HBox 中设置水平拉伸 |

## Commands

```java
ctx.commands().register("demo.hello", "说明", commandContext -> { });
ctx.commands().run("demo.hello", "参数");
ctx.commands().list(); // 当前插件已注册的命令名
```

命令名全局唯一。建议用 `<插件id>.<动作>` 命名，避免和内置或其他插件冲突。

### CommandContext

| 成员 | 说明 |
| --- | --- |
| `name` | 命令名 |
| `args` | 命令名后的整串参数，可能为空 |
| `argv()` | 按空白切分的参数列表 |
| `pluginId` | 注册该命令的插件 id |
| `reply(String)` | 弹出一条启动器通知 |
| `run(String, String)` | 执行另一个命令 |
| `log(String)` | 写日志 |

## Dialogs

| 方法 | 说明 |
| --- | --- |
| `message(title, text)` | 消息框 |
| `error(title, text)` | 错误样式消息框 |
| `confirm(title, text, onOk)` | 确认框，确定后执行 `onOk` |
| `confirmDanger(title, text, onOk)` | 红色危险确认框 |
| `custom(Node)` | 用启动器模态层展示任意节点，节点需要自己设置尺寸和背景 |
| `close()` | 关闭当前由插件打开的模态窗口 |
| `input(title, hint, defaultValue)` | 单行输入，返回字符串；取消返回 `null` |

::: warning
`input` 会阻塞当前 JavaFX 线程直到用户确认。不要在动画或高频回调里使用。
:::

## Storage

插件私有键值存储，落盘到 `.tcl/plugins/.data/<id>/storage.properties`，禁用/卸载时自动保存。

```java
ctx.storage().set("count", 3);
ctx.storage().save();
int count = ctx.storage().getInt("count", 0);
boolean dark = ctx.storage().getBool("dark", false);
String name = ctx.storage().get("name", "");
```

| 方法 | 说明 |
| --- | --- |
| `get(key)` / `get(key, def)` | 读取字符串 |
| `getInt(key, def)` / `getBool(key, def)` | 读取数字 / 布尔 |
| `set(key, value)` | 写入内存，调用 `save()` 落盘 |
| `save()` | 立即写盘 |

## Events

```java
ctx.events().on("game.launch", event -> {
    ctx.log("启动实例：" + event.string("instance"));
});
```

| 事件 | data 键 | 触发时机 |
| --- | --- | --- |
| `app.start` | `version` | 插件加载完成、启动器进入主界面后 |
| `page.open` | `page` | 每次页面提交显示（launch / instance / other / `plugin:<id>:<page>` 等） |
| `game.launch` | `instance`、`version` | 用户点击启动、真正开始启动流程前 |
| `plugin.install` | `id` | 插件导入并加载完成后 |

### Event

| 成员 | 说明 |
| --- | --- |
| `name` | 事件名 |
| `data()` | 只读 `Map<String,Object>` |
| `string(key)` | 读取字符串，缺失返回空串 |
| `bool(key)` | 读取布尔 |
| `integer(key, def)` | 读取整数 |
| `list(key)` | 读取列表 |

插件卸载时，启动器会自动取消该插件的全部事件监听，不需要手动反注册。

## 直接操作 JavaFX

`ctx.stage()` 返回主窗口，可以拿到 `Scene` 和根节点：

```java
StackPane root = (StackPane) ctx.stage().getScene().getRoot();
Label badge = new Label("插件运行中");
StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
root.getChildren().add(badge);
```

这种方式可以改任何布局、样式和交互，但也可能影响启动器内置界面。
请给节点设置唯一的 style class 或 id，方便卸载时移除，并在 `onDisable()` 中清理自己添加的节点。
