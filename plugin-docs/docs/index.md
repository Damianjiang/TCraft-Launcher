---
layout: home

hero:
  name: TCraft Launcher
  text: 插件开发文档站
  tagline: Java + JavaFX 扩展页面、按钮和命令。
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
      text: 下载示例
      link: /examples

features:
  - icon: ▤
    title: 任意页面
    details: 在「更多」添加 JavaFX 页面。
  - icon: ▦
    title: 四个按钮位
    details: 标题栏、首页、工具、悬浮四个入口。
  - icon: ⌘
    title: 命令与弹窗
    details: 命令，消息、确认、输入、自定义弹窗。
  - icon: ⚙
    title: 生命周期
    details: 启停、重载、存储、事件、类加载器。
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
| 弹窗 | `ctx.dialogs()` | 消息、确认、输入、自定义节点 |
| 事件 | `ctx.events().on(...)` | 启动、切页、开游戏、安装插件 |

## 官方入口

- [TCraft 官网](https://tcraft.pages.dev)
- [TCWiki](https://tcwiki.pages.dev)
- [启动器 Releases](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)
- [进阶参考](/references)

::: warning 安全边界
插件与启动器同进程，权限不受限。只安装可信来源的插件。
:::
