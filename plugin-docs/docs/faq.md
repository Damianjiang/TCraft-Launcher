# 常见问题

## 插件没有出现在「更多」里

1. 打开「更多 → 插件」，确认状态不是「已禁用」。
2. 确认插件目录是 `.tcl/plugins/<id>/`，且里面有 `plugin.json` 和 `main` 指向的 jar。
3. 看 `.tcl/log/launcher.log` 是否有 `[插件][<id>] 加载失败` 及异常堆栈。
4. 点「刷新」重新扫描。插件是在启动器显示后异步加载的，首次进入可能还没加载完。

## 导入 zip 失败

- zip 根目录必须直接包含 `plugin.json`，不能再套一层文件夹。
- 单个插件最多 4096 个文件、解压后不超过 512 MB。
- 路径中不能出现 `..`，否则会被拒绝。
- 清单里的 `id` 只能使用字母、数字、点、下划线和短横线。

## 提示「缺少 main 类」

`plugin.json` 的 `main` 是完整类名，不是文件名：

```json
{ "main": "com.example.MyPlugin" }
```

类必须 `implements TcraftPlugin`，并且是 `public`、有无参构造函数。

## 提示 Java 版本不兼容

插件 jar 必须用 Java 17 编译：

```bash
javac --release 17 -cp TCraftLauncher.jar -d out src/com/example/MyPlugin.java
```

用 JDK 21/25 编译但指定 `--release 17` 也可以；不要用 `--release 21`。

## 插件加载了但页面是空白

- builder 返回 `null` 是正常的，内容通过 `view.add(...)` 添加。
- 确认没有在 builder 里抛出异常；异常会显示在日志里。
- 不要复用已经属于其他父节点的 Node，JavaFX 会抛 `IllegalArgumentException`。
- 页面每次打开都会重建，状态请放进字段或 `Storage`。

## 修改代码后没有生效

「更多 → 插件」里点「重载」，或禁用再启用。重载会创建新的类加载器，
静态字段会被重置。

## 删除插件提示文件被占用

Windows 下 jar 被类加载器持有句柄时无法删除。先禁用插件，再删除；
启动器会把文件移入回收站，失败时会提示具体文件。

## 插件能访问哪些文件

插件运行在启动器进程内，拥有和启动器相同的文件权限，没有沙箱。
请只读写自己的数据目录和用户明确选择的文件，不要修改启动器自身文件。
安装第三方插件前请确认来源可信，插件页会显示安全提示。

## 插件可以依赖其他库吗

可以。把依赖一起打进插件 jar（fat jar），或作为独立 jar 放在插件目录根部 / `lib/`。
多个插件使用不同版本的同一库时，类加载器按插件隔离，互不影响。
JavaFX 本身由启动器提供，不要把 JavaFX 打进插件。

## 支持 FXML 和 WebView 吗

默认只加载 `javafx.controls`、`javafx.graphics`、`javafx.media`，不含 `javafx.fxml` 和 `javafx.web`。
需要这些模块请联系启动器作者，或在插件内用纯 Java 构建界面。

## 图标不显示

- 内置图标名必须是启动器 `res/icons/lucide` 中存在的名称，例如 `puzzle`、`star`、`package`。
- 目录插件可以用相对图片路径，例如 `"icon": "icon.png"`；jar 插件建议使用内置图标。
- 名称拼错时启动器会回退到默认图标，不会导致加载失败。

## 插件会影响启动器体积吗

不会。插件是外部文件，不参与启动器打包。启动器本身保持在约 1 MB，
插件按需安装、按需加载。

## 如何发布插件

目前通过 zip 分发：

1. 在仓库 Release 提供 `xxx-plugin.zip`。
2. 附上 `plugin.json`、版本号、兼容的启动器版本和截图。
3. 建议在仓库 README 标注 `tcraft-plugin` 主题，方便社区检索。

插件市场与在线索引还在规划中，完成后会在此文档更新。

## 文档里的示例能直接运行吗

可以。每个示例都基于 `tcraft.api` 的公开 API，按[快速开始](/guide)编译即可。
遇到文档与启动器不一致时，以 `TCraftLauncher.jar` 中的 `tcraft.api` 类为准。
