# 插件系统概览

插件是带 `plugin.json` 的 Java 目录或 jar。可添加页面、按钮、命令、弹窗、事件，也可直接操作 JavaFX 场景树。

## 能力

| 能力 | 入口 | 位置 / 说明 |
| --- | --- | --- |
| 完整页面 | `ctx.ui().page(...)` | 「更多」左栏 |
| 标题栏按钮 | `ctx.ui().titleButton(...)` | 窗口右上角 |
| 首页按钮 | `ctx.ui().launchButton(...)` | 首页「插件扩展」 |
| 工具按钮 | `ctx.ui().toolButton(...)` | 「更多 → 实用工具」 |
| 悬浮按钮 | `ctx.ui().floatButton(...)` | 右下角 |
| 命令 | `ctx.commands().register(...)` | 搜索、详情、插件按钮 |
| 弹窗与文件选择 | `ctx.dialogs()` | 消息、确认、输入、原生选择器 |
| 网络 | `ctx.network()` | HTTP、下载、WebSocket、TCP、UDP |
| 文件 | `ctx.files()` | 文本/字节读写、遍历、复制、删除、zip |
| 系统 | `ctx.system()` | 环境变量、剪贴板、打开文件、通知 |
| 进程 | `ctx.processes()` | 执行命令、超时、stdout/stderr |
| 桌面 | `ctx.desktop()` | 截图、鼠标、键盘、组合键 |
| 调度 | `ctx.scheduler()` | 后台任务、延时、轮询、UI 回调 |
| JSON | `ctx.json()` | 解析、序列化、安全取值 |
| 启动器 | `ctx.launcher()` | 实例列表、选择、启动 |
| 事件 | `ctx.events()` | 生命周期事件、插件间广播 |
| 任意布局 | `ctx.stage()` | 直接改 `Stage` / `Scene` |

::: warning 安全边界
插件拥有完整权限；启动器只隔离类加载器，不限制行为。只安装可信插件。
:::

## 插件目录

插件位于 `.tcl/plugins/`，支持两种形态。

### 目录插件

```text
.tcl/plugins/demo.hello/
├── plugin.json          # 必需
├── hello.jar            # 至少一个 jar
├── lib/                 # 可选依赖
│   └── some-lib.jar
├── icon.png             # 可选列表图标
└── thumbnail.png        # 可选详情封面
```

### 单 jar 插件

清单放在 jar 根目录，导入时展开成目录：

```text
demo-hello.jar
├── plugin.json
└── demo/HelloPlugin.class
```

入口在「更多 → 插件」：刷新、导入、启停、详情、命令、打开目录、删除。

## 加载流程

1. 启动约 1.2 秒后扫描 `.tcl/plugins/`，按目录名排序。
2. 读取 `plugin.json`，校验 `id`、`main`、API 版本。
3. 创建独立 `URLClassLoader`，父加载器为启动器。
4. 实例化 `main` 类并调用 `onLoad(ctx)`。
5. 手动启用时再调用 `onEnable()`。
6. 禁用或卸载时调用 `onDisable()`，关闭页面、命令、事件、调度器、网络连接、子进程和类加载器。

::: tip 线程模型
回调和事件都在 JavaFX 线程执行。耗时任务放 `ctx.scheduler().async(...)`，更新 UI 用
`ctx.scheduler().ui(...)`。
:::

## 确认加载

日志 `.tcl/log/launcher.log` 出现下面一行，即加载成功：

```text
[09:19:51] [插件][demo.hello] 已加载 Hello 插件 1.0.0
```

「更多」左栏出现插件页面，「更多 → 插件」显示「已启用」。失败会显示异常。

## 下一步

- [第一个插件](/guide)
- [plugin.json](/manifest)
- [进阶参考](/references)
