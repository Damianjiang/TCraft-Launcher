# plugin.json 清单

`plugin.json` 是插件的唯一必需入口文件，UTF-8 编码，放在插件目录根部，或放在单 jar / zip 的根目录（也可以在一层子目录内）。

## 字段

| 字段 | 类型 | 必需 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 插件唯一标识，2–64 位，只允许字母、数字、`.`、`_`、`-`，必须以字母或数字开头 |
| `name` | string | 是 | 显示名称，会出现在插件列表和「更多」左栏 |
| `version` | string | 是 | 版本号，建议语义化版本，如 `1.0.0` |
| `main` | string | 是 | 入口类完整类名，必须实现 `tcraft.api.TcraftPlugin` |
| `api` | number | 否 | 目标 API 版本，默认 `1`；大于客户端支持的版本会被拒绝加载 |
| `author` | string | 否 | 作者名，显示在插件列表与详情 |
| `description` | string | 否 | 一句话描述，显示在插件详情 |
| `icon` | string | 否 | 内置图标名（如 `puzzle`、`star`、`package`），或插件根目录内的相对图片路径 |
| `thumbnail` | string | 否 | 插件详情页缩略图，插件根目录内的相对图片路径；也可写 `preview` |
| `website` | string | 否 | 项目主页，保留字段，显示在详情里 |
| `onInstall` | string[] | 否 | 首次导入后自动执行的命令名列表，命令必须已在 `onLoad` 中注册 |

未知字段会被忽略，方便以后扩展。

## 最小清单

```json
{
  "id": "demo.hello",
  "name": "Hello 插件",
  "version": "1.0.0",
  "main": "HelloPlugin",
  "api": 1
}
```

## 完整清单

```json
{
  "id": "com.example.stats",
  "name": "游戏时长统计",
  "version": "2.1.0",
  "author": "Example",
  "description": "记录每个实例的启动时长，并在插件页面展示排行榜。",
  "icon": "assets/icon.png",
  "thumbnail": "assets/cover.png",
  "main": "com.example.stats.StatsPlugin",
  "website": "https://example.com/tcl-stats",
  "api": 1,
  "onInstall": ["stats.welcome"]
}
```

## 校验规则

启动器会在加载前检查：

- `id` 非空且符合命名规则；同一个 `id` 只允许一个插件，重复时后发现的会标记为「加载失败」。
- `name`、`version`、`main` 不能为空。
- `api` 不得超过客户端支持版本（当前为 `1`）。
- `main` 类必须能被实例化，并且实现 `TcraftPlugin`。

任何一项失败，插件会出现在「更多 → 插件」里并标记为「加载失败」，详情中给出具体原因。

## 图标与缩略图

`icon` 支持两种写法：

1. **内置图标名**：`puzzle`、`star`、`package`、`folder-open`、`power`、`info`、`refresh-cw`、`trash-2`、`triangle-alert` 等。
2. **相对图片路径**：相对于插件根目录，例如 `icon.png`、`assets/logo.jpg`；目录插件和单 jar 插件都支持。
   支持 `.png`、`.jpg`、`.jpeg`、`.gif`、`.bmp`、`.webp`，单张图片不超过 8 MB。

```json
{
  "icon": "assets/icon.png",
  "thumbnail": "assets/cover.png"
}
```

没有显式声明时，启动器会自动寻找：

| 用途 | 自动尝试的文件名 |
| --- | --- |
| 列表小图标 | `icon.png`、`icon.jpg`、`icon.jpeg`、`icon.gif`、`icon.webp`、`logo.png` |
| 详情缩略图 | `thumbnail.png`、`preview.png`、`cover.png`、`icon.png`、`logo.png` |

`thumbnail` 也接受别名 `preview`。图片路径不能以 `/` 开头，也不能包含 `..`；
缩略图只在插件详情里展示，不占用列表行的高度。

## 目录插件与单 jar

| 形态 | 加载方式 | 说明 |
| --- | --- | --- |
| `plugins/<id>/plugin.json` | 读取目录清单，classpath 包含根目录 `*.jar` 与 `lib/*.jar` | 推荐，方便热重载与资源文件 |
| `plugins/<name>.jar` | 从 jar 内读取 `plugin.json`，classpath 只有这个 jar | 适合纯代码插件 |
| `.zip` / `.jar` 导入 | 启动器展开到 `plugins/<id>/`，写入 `plugin.json` | 用户一键安装 |

导入时的限制：

- 最多 4096 个文件，解压后不超过 512 MB。
- 禁止 `../` 路径穿越。
- 同 `id` 已存在时会提示覆盖；覆盖安装会先卸载旧插件再删除旧目录。

## 依赖 jar

目录插件可以把依赖放在插件根目录或 `lib/`，启动器会把它们全部加入同一个类加载器：

```text
plugins/com.example.stats/
├── plugin.json
├── stats.jar
└── lib/
    ├── gson-2.10.1.jar
    └── commons-lang3.jar
```

::: warning
不要把启动器自带的 `tcraft.api` 或 JavaFX 打包进插件，类加载器会优先使用启动器提供的版本。
如果插件自带同名类，可能产生冲突。依赖第三方库时建议放到 `lib/`，不要 shaded 进入口 jar。
:::

## 清单版本与更新

插件更新时递增 `version`；启动器按 `id` 判断是否为同一个插件。覆盖安装后，`.tcl/plugins/.data/<id>/`
中的配置与存储会保留，卸载插件时数据目录也不会自动删除。
