# 进阶与参考资料

这一页集中放开发时最容易踩到的边界、性能建议和外部文档入口。第一次写插件只需要看
[快速开始](/guide)，遇到问题再回来查。

## 生命周期与状态

| 状态 | 何时出现 | 可以做什么 |
| --- | --- | --- |
| `disabled` | 插件被禁用，或清单校验通过但还没加载 | 在插件页重新启用；不能贡献页面与命令 |
| `loaded` | `onLoad` 完成；手动启用时还会执行 `onEnable` | 页面、按钮、命令、事件全部可用 |
| `failed` | 清单错误、入口类异常、API 版本过高、id 重复 | 看详情里的错误；修正后重载或重装 |
| `removed` | 删除流程进行中 | 等待文件移入回收站，数据目录仍保留 |

禁用会调用 `onDisable()` 并关闭该插件的类加载器；重新启用会新建类加载器并再次执行 `onLoad`。
所以**不要把必须长期保存的状态放在静态字段里**，请写进 `ctx.storage()`。

## 类加载与依赖

- 每个插件有独立 `URLClassLoader`，父加载器是启动器，`tcraft.api` 与 JavaFX 由启动器提供。
- 目录插件的根目录、`lib/*.jar` 都会进入同一个类加载器；单 jar 插件只加载自身。
- 不要把自己的 `tcraft.api`、JavaFX 打进插件，重复类可能造成 `LinkageError`。
- 第三方库建议放 `lib/`，不要和入口 jar 一起 shaded；升级库时只替换依赖文件即可。
- 插件的静态单例只在本次加载期间有效，重载后是新的类与新的实例。

## 线程模型

生命周期回调、页面构建、按钮与命令回调都在 **JavaFX Application Thread** 执行。

```java
// 错误 在 UI 线程里同步下载 界面会卡住
String json = Http.get(url);

// 正确 后台线程做 IO 回到 UI 线程更新控件
new Thread(() -> {
    String json = Http.get(url);
    Platform.runLater(() -> label.setText(json));
}, "my-plugin-io").start();
```

- 不要在 `onLoad` 里做耗时操作；启动器会等待它返回。
- 后台线程不要直接改 JavaFX 节点，统一用 `Platform.runLater`。
- 需要周期任务时使用单线程 `ScheduledExecutorService`，并在 `onDisable` 里关闭，避免重载后遗留线程。

## 存储与数据目录

`ctx.storage()` 对应 `.tcl/plugins/.data/<插件 id>/storage.properties`：

```java
ctx.storage().set("lastLaunch", System.currentTimeMillis());
ctx.storage().save();
String name = ctx.storage().get("playerName", "Steve");
```

- 存储按插件 id 隔离；覆盖安装会保留，删除插件也不会自动清空。
- 值按字符串保存，数字/布尔请自行转换。
- 不要把大文件塞进 properties；图片、缓存、数据库放插件自己的数据目录，并在清单或文档中说明。
- 写入后调用 `save()`；高频写入建议合并，避免每次点击都落盘。

## 界面约定

- 优先使用 `Widgets`（`Widgets.card`、`Widgets.label`、`Widgets.button`、`Widgets.row`）保持与启动器一致的圆角、间距和配色。
- 页面根节点会被放进滚动容器；不要给根节点设置固定超大高度，列表多时使用分页或懒加载。
- 主题色、字体、阴影会随浅色/深色模式变化；不要硬编码只适合一种背景的颜色。
- 需要自定义 JavaFX 控件时，可以直接使用 `ctx.stage()` / `ctx.scene()`，但要避免替换启动器核心节点。
- 插件页面按需构建：重页面在第一次打开时再创建内容，避免拖慢启动。

## 打包与缩略图

推荐目录结构：

```text
plugins/com.example.stats/
├── plugin.json
├── stats.jar
├── icon.png          # 列表小图标 建议 1:1 64×64 或 128×128
├── thumbnail.png     # 详情页大图 建议 16:9 或 1:1 不小于 512×512
└── lib/
    └── gson-2.10.1.jar
```

清单中可显式指定：

```json
{
  "icon": "icon.png",
  "thumbnail": "thumbnail.png"
}
```

也接受 `preview` 作为 `thumbnail` 的别名。没有声明时，启动器会依次寻找
`icon.png`、`icon.jpg`、`logo.png` 作为小图标，以及 `thumbnail.png`、`preview.png`、
`cover.png`、`icon.png`、`logo.png` 作为详情缩略图。支持 PNG / JPG / GIF / BMP / WebP；
WebP 解码库由启动器首次启动时自动补齐。

导入限制：

- 最多 4096 个文件，解压后不超过 512 MB。
- 禁止 `../` 路径穿越；清单或图片路径不允许跳出插件目录。
- 同一个 `id` 只允许一个插件；重复 id 会标记为加载失败。
- `.zip` / `.jar` 导入后会展开为 `plugins/<id>/`，之后按目录插件加载。

## 调试清单

| 现象 | 优先检查 |
| --- | --- |
| 插件页显示「加载失败」 | 详情里的错误、`plugin.json` 的 `main` 与 `api` |
| 日志没有「已加载」 | 入口类是否实现 `TcraftPlugin`、构造函数是否能访问 |
| 页面或按钮不出现 | 是否在 `onLoad` 中注册、插件是否处于启用状态 |
| 点刷新后列表异常 | 更新到当前客户端；旧版本存在重复追加问题 |
| 命令找不到 | 命令名是否重复、是否在插件加载完成前调用 |
| 图片不显示 | 路径是否相对插件根目录、格式是否受支持、文件是否超过 8 MB |

日志位置：`.tcl/log/launcher.log`。开发时可以保留一个测试插件目录，修改后点「刷新」或
在详情里「重新加载」，不必重启整个启动器。

## 性能建议

- 长列表每页 10–20 项，分页或懒加载；不要一次创建上千个 JavaFX 节点。
- 图片按显示尺寸解码并缓存，列表小图不要加载原图。
- 避免在 `onLoad`、页面构建、按钮回调里同步访问网络或磁盘。
- 事件监听在 `onDisable` 中取消；线程池要关闭，否则重载会累积。
- 大量文本使用 `Label.setWrapText(true)` 并限制最大宽度，避免反复触发布局。
- 需要动画时使用 `Timeline` / `Transition`，不要用忙循环。

## 兼容性基线

| 项目 | 版本 |
| --- | --- |
| Java | 17（`javac --release 17`） |
| JavaFX | 17.0.x |
| 插件 API | `PluginContext.API_VERSION = 1` |
| 客户端 | TCraft Launcher 1.3.x |

API 只做向后兼容的增量时不会提升版本号；删除或改变已有签名会提升 `api`，旧插件会被拒绝加载。

## 外部参考资料

- [JavaFX 17 API 文档](https://openjfx.io/javadoc/17/) —— 控件、布局、动画、属性和事件。
- [JavaFX CSS Reference](https://openjfx.io/javadoc/17/javafx.graphics/javafx/scene/doc-files/cssref.html) —— 选择器、颜色、背景与字体。
- [VitePress 文档](https://vitepress.dev/zh/) —— 本站使用的静态文档框架。
- [Minecraft Wiki：版本清单](https://minecraft.wiki/w/Version_manifest.json) —— 原版版本 JSON 与资源索引说明。
- [Fabric 文档](https://docs.fabricmc.net/) —— Fabric Loader / API 的官方开发文档。
- [NeoForge 文档](https://docs.neoforged.net/) —— NeoForge 模组开发与版本信息。
- [Lucide 图标](https://lucide.dev/icons/) —— 启动器内置图标名对应的图标集。
- [jwebp](https://github.com/Glavo/jwebp) —— JavaFX 的 WebP 解码支持。
- [EasyTier](https://github.com/EasyTier/EasyTier) —— 启动器联机功能使用的网络组件。

## 文档站维护

文档源码在仓库 `plugin-docs/`，使用 VitePress + Teek：

```bash
cd plugin-docs
npm install
npm run docs:dev
npm run docs:build
```

`docs:build` 输出到项目里的 `website/wiki/`。部署到 GitHub Pages 时设置 `BASE_PATH=/TCraft-Launcher/wiki/`；
如果独立部署到网站根路径，设置 `BASE_PATH=/wiki/`，然后正常构建即可。
