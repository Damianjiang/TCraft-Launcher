---
layout: home

hero:
  name: TCraft Launcher
  text: 插件开发文档站
  tagline: Java + JavaFX 扩展页面、命令、网络、文件、进程与桌面控制。
  image:
    src: /logo.png
    alt: TCraft Launcher
  actions:
    - theme: brand
      text: 五分钟上手
      link: /guide
    - theme: alt
      text: API 参考
      link: /api
    - theme: alt
      text: 让 AI 写插件
      link: /ai

features:
  - icon: ▤
    title: 页面与按钮
    details: 在「更多」添加 JavaFX 页面，使用四个按钮位。
  - icon: ⇄
    title: 网络
    details: HTTP、下载、WebSocket、TCP、UDP，连接随插件关闭。
  - icon: ⚙
    title: 系统与进程
    details: 文件、环境变量、剪贴板、子进程、超时与输出捕获。
  - icon: ⌨
    title: 桌面控制
    details: 截图、鼠标、键盘、组合键，适合做 AI 动作执行器。
  - icon: ⏱
    title: 后台任务
    details: 每插件独立调度器，async、delay、repeat、UI 回调。
  - icon: ⌘
    title: 命令与事件
    details: 命令、弹窗、自定义事件、插件间广播与生命周期清理。
---

## 最短路径

1. 写 `plugin.json`，声明 `id`、`name`、`version`、`main`、`api`。
2. 实现 `TcraftPlugin.onLoad(PluginContext ctx)`，注册页面、按钮或命令。
3. 编译成 jar，和清单一起打成 zip，在「更多 → 插件」导入。
4. 日志出现 `[插件][id] 已加载` 即完成。

## 能力速查

| 能力 | API | 位置 |
| --- | --- | --- |
| 页面 | `ctx.ui().page(...)` | 「更多」左栏 |
| 标题栏按钮 | `ctx.ui().titleButton(...)` | 窗口右上角 |
| 首页按钮 | `ctx.ui().launchButton(...)` | 首页「插件扩展」 |
| 工具按钮 | `ctx.ui().toolButton(...)` | 「更多 → 实用工具」 |
| 悬浮按钮 | `ctx.ui().floatButton(...)` | 窗口右下角 |
| 命令 | `ctx.commands().register(...)` | 全局搜索、插件详情、按钮 |
| 弹窗 | `ctx.dialogs()` | 消息、确认、输入、原生文件选择 |
| 网络 | `ctx.network()` | HTTP、下载、WebSocket、TCP、UDP |
| 文件 | `ctx.files()` | 读写、遍历、复制、删除、zip |
| 系统 | `ctx.system()` | 环境变量、剪贴板、打开文件、通知 |
| 进程 | `ctx.processes()` | 执行命令、超时、stdout/stderr |
| 桌面 | `ctx.desktop()` | 截图、鼠标、键盘、组合键 |
| 调度 | `ctx.scheduler()` | 后台任务、延时、轮询、UI 回调 |
| 启动器 | `ctx.launcher()` | 实例列表、选择、启动 |
| 事件 | `ctx.events()` | 生命周期事件、插件间广播 |

## 官方入口

- [TCraft 官网](https://tcraft.pages.dev)
- [TCWiki](https://tcwiki.pages.dev)
- [启动器 Releases](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)
- [进阶参考](/references)

::: warning 安全边界
插件与启动器同进程，权限不受限。只安装可信来源的插件。
:::
