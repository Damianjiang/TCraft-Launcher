<div align="center">

<img src="docs/assets/logo.png" width="96" alt="TCraft Launcher">

# TCraft Launcher

**轻一点，快一点。**

一个单文件的 Minecraft 启动器：约 3.6 MB，冷启动约 2 秒，开源、免费、无广告。

[![Release](https://img.shields.io/github/v/release/Damianjiang/TCraft-Launcher?style=flat-square&color=f2b632)](https://github.com/Damianjiang/TCraft-Launcher/releases)
[![Downloads](https://img.shields.io/github/downloads/Damianjiang/TCraft-Launcher/total?style=flat-square&color=5eead4)](https://github.com/Damianjiang/TCraft-Launcher/releases)
[![License](https://img.shields.io/github/license/Damianjiang/TCraft-Launcher?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%207%20SP1%2B-blue?style=flat-square)](https://github.com/Damianjiang/TCraft-Launcher/releases)

[下载最新版](https://github.com/Damianjiang/TCraft-Launcher/releases) · [官网](https://damianjiang.github.io/TCraft-Launcher/) · [更新日志](CHANGELOG.md) · [English](README.en.md) · [反馈问题](https://github.com/Damianjiang/TCraft-Launcher/issues) · [TCraft Launcher 启动器交流群](https://qm.qq.com/q/WaG64YE9uo)（群号 1131373915）

<img src="docs/assets/shot-home.png" width="820" alt="TCraft Launcher 启动台界面">

</div>

---

## 这是什么

TCraft Launcher（简称 **TCL**）是一个用 VB.NET / WPF 写的 Minecraft 启动器。
它没有安装向导、没有捆绑、也没有任何统计上报：整个程序就是一个 3.6 MB 的 exe，
拷到哪儿都能跑，删掉就是卸载。

- **单文件发布**：启动核心与第三方库以压缩快照内嵌进 exe，无需运行库依赖。
- **快**：延迟初始化 + 多核 JIT 预编译 + UI 资源按需加载，冷启动约 2 秒，空闲占用接近 0。
- **快的不只是启动**：下载走 HTTP/2 多路复用（Win10 1607+），最高 64 线程并发 + 可调限速 + 断点续传。
- **好看、可换**：Win11 风格半透明玻璃界面，支持壁纸自动取色、背景视频、背景音乐、21 种主题色。
- **多语言**：简体中文 / English / 繁體中文 / 文言文 / 自动识别，全部文案可通过 `TCL\i18n.json` 自行修改。
- **支持 Windows 7**：SP1 及以上均可运行（老系统自动回退兼容的网络栈）。

## 功能总览

| 模块 | 能力 |
| --- | --- |
| 启动 | 多文件夹多实例、版本列表 / 收藏夹 / 颜色标签 / 合集筛选、列表与网格双视图 |
| 账户 | 微软设备码正版登录 / 离线登录 / 外置 Authlib 登录（LittleSkin 等），账户卡片切换与导入导出 |
| 实例 | 版本独立设置、Mod / 存档 / 数据包 / 截图 / 服务器管理、整合包导出、Jar-in-Jar 内嵌依赖查看与导出 |
| 整合包 | CurseForge、Modrinth、MCBBS、MultiMC、HMCL 五种格式解析，安装前预览图标与版本 |
| 下载 | 原版与 Forge / NeoForge / Fabric / OptiFine / LiteLoader 一键安装；CurseForge / Modrinth 资源搜索；前置 Mod 自动补全 |
| 联机 | 基于 EasyTier 的虚拟局域网：创建 / 加入房间、自定义中转节点、启动后自动进服 |
| 皮肤 | 2D 正视图 + WPF 3D 模型预览，自动识别经典 / 纤细模型，离线皮肤上传 |
| 其他 | 崩溃分析（中文人话）、缓存清理、设置导入导出、全局搜索、完整日志 |

## 各页面详细功能

### 启动

- **版本管理**：多文件夹扫描，收藏夹置顶，颜色标签与合集筛选；F11 查看隐藏版本。
- **内存分配**：自动 / 手动 / 按版本独立；可选「锁定初始内存」（-Xms = -Xmx，降低延迟抖动，利于 ZGC）。
- **Java 管理**：按版本范围自动选择（如 `[21.0, 25)`），列表可排序，支持手动指定路径、版本内嵌 Java 自动识别、没有合适 Java 时自动下载。
- **参数与命令**：JVM 参数 / 游戏参数 / 启动前命令均支持 Minecraft 版本 JSON 字符串标记与 TCL 替换标记（`{verpath}`、`{java}` 等）。
- **高级修复**：mesa-loader 自定义渲染器注入、LWJGL Unsafe Agent、Java Launch Wrapper（中文路径修复）、高性能显卡自动设置。
- **其他**：进程优先级、窗口标题与大小自定义、启动脚本导出、愚人节彩蛋、游戏启动后自动隐藏 / 关闭启动器。

### 下载

- **版本安装**：原版、Forge、NeoForge、Fabric、OptiFine、LiteLoader 一键下载并自动组装。
- **资源搜索**：Mod / 资源包 / 光影 / 整合包 / 世界，来自 CurseForge 与 Modrinth，支持 API Key 配置。
- **前置补全**：下载 Mod 时自动解析并补齐缺失的前置依赖（递归解析、已装检查、无法解析时明确提示）。
- **下载引擎**：HTTP/2 多路复用（自动回退）、最高 64 线程、全局限速、断点续传、失败自动换源重试。

### 实例管理

- **Mod 管理**：启用 / 禁用 / 收藏 / 搜索 / 全选，拖入 zip 安装，Jar-in-Jar 内嵌依赖一览并可单个或全部导出。
- **资源包 / 光影包**：列表即预览（自动提取 pack.png 图标），单击切换当前启用项，直接打开文件夹。
- **存档管理**：备份 / 重命名 / 复制 / 导出 / 删除，自动识别世界图标与游戏规则。
- **服务器**：局域网与联机房间状态查询（MOTD / 延迟 / 人数），一键带当前实例进入服务器。
- **整合包导出**：CurseForge / Modrinth / MCBBS / MultiMC / HMCL 格式，可选打包资源文件（供弱网环境）。
- **版本独立设置**：每个实例可独立设置内存、Java、隔离模式、窗口标题等，支持保存为预设并套用到其他实例。

### 设置

- **个性化**：21 种主题色（含随机与自定义 HSL）、壁纸自动取色、背景模糊 / 视频 / 音乐、组件与弹出菜单双半透明滑杆。
- **系统**：界面语言切换（含文言文风格）、下载线程与限速、缓存位置、软件渲染开关、设置导入导出（.tcl）。
- **启动**：全局默认内存、窗口模式、默认隔离策略、Java 选择规则、正版支持提示开关。

## 使用技巧

- **全局搜索**：`Ctrl+K` 或标题栏放大镜，搜索所有设置项，点击直达。
- **替换标记**：在窗口标题、启动命令等文本里写 `{verpath}`、`{java}` 等，运行时自动填入真实值——详见「更多 → 帮助」。
- **隐藏版本**：在版本设置中隐藏后，版本列表按 `F11` 可查看全部隐藏版本。
- **改文案**：所有界面文案在 `TCL\i18n.json`，文本编辑器改完点「重载」即时生效，无需重启。
- **静默内存优化**：使用 `--memory` 参数启动 TCL，可直接执行一次内存优化。
- **导入整合包 / 存档 / Mod**：直接把 zip / jar 拖进对应页面即可。

## 界面

所有截图都来自真实运行，没有渲染图。

| 启动台 | 下载 |
| --- | --- |
| ![启动台](docs/assets/shot-home.png) | ![下载](docs/assets/shot-download.png) |
| ![实例](docs/assets/shot-instance.png) | ![设置](docs/assets/shot-setup.png) |

## 下载

前往 [最新 Release](https://github.com/Damianjiang/TCraft-Launcher/releases) 下载 `TCraft-Launcher.exe`，双击运行即可。
该链接始终指向最新版本，页面无需随版本更新。

- **系统**：Windows 7 SP1 及以上（推荐 Windows 10 / 11），64 位
- **体积**：约 3.6 MiB（单文件，< 4 MB）
- **校验值**：见最新 Release 页面（随发布同步更新）
- **更新日志**：[CHANGELOG.md](CHANGELOG.md)，中英双语，按版本记录每次改动

> 项目未购买代码签名证书，SmartScreen 可能提示「未知发布者」。
> 可在 Release 页面核对校验值，或直接从源码构建。

## 从源码构建

环境要求：

- Windows 10 及以上
- [Visual Studio 2022 Build Tools](https://visualstudio.microsoft.com/zh-hans/downloads/)，勾选 **.NET 桌面开发**（需要包含 Visual Basic 与 .NET Framework 4.8 目标包）
- 可选：[7-Zip](https://www.7-zip.org/)（用于压缩内嵌快照，体积更小）

```powershell
git clone https://github.com/Damianjiang/TCraft-Launcher.git
cd TCraft-Launcher
powershell -ExecutionPolicy Bypass -File scripts\build.ps1
```

`build.ps1` 会依次编译 TClKit / TClCS、重打内嵌 gz 快照，产出单文件 `TCl\bin\TCraft Launcher.exe`。
也可以直接用 Visual Studio 打开 `TCl.sln` 编译。

## 项目结构

```
├── TCl/          主程序（VB.NET + WPF）：界面、页面、业务逻辑
│   ├── Pages/        各功能页面
│   ├── Controls/     自研控件（列表项、卡片、消息框、滚动条等）
│   ├── Modules/      启动、下载、整合包、联机、皮肤等业务模块
│   └── Resources/    内嵌资源（第三方 DLL 压缩快照、译表、贴图）
├── TClCS/        启动核心（C#）：Java 管理、游戏启动、Minecraft 数据
├── TClKit/       共享工具库（JSON 解析、基础类型、WPF 辅助）
├── docs/         官网（GitHub Pages）
└── scripts/      构建脚本（build.ps1 / regen_gz.ps1 / gen_langtables.py）
```

## 性能与工程说明

- **冷启动**：SplashScreen 前置 + 后台预热池（语言词典 / 网络栈 / 注册表预热与首帧并行）+ 多核 JIT 配置档案，目标 2 秒内完成首帧。
- **下载引擎**：Win10 1607+ 使用 WinHTTP 的 HTTP/2 多路复用（几十个并发共用少量连接），Win7 自动回退兼容栈；64 线程令牌桶限速；失败自动换源。
- **UI**：全部控件自绘，无第三方 UI 框架；页面切换、按钮、列表均为增量动画，无整页重绘。
- **健壮性**：后台线程异常全局兜底记录（崩溃可从日志定位）、取消启动全程可中断、加载器状态机防重入。

## 参考与致谢

TCL 站在前人的肩膀上，这里把每一个来源都写清楚：

| 来源 | 参考内容 | 当前状态 |
| --- | --- | --- |
| [HMCL](https://github.com/HMCL-dev/HMCL)（GPL-3.0） | 功能规划与交互设计：账户管理布局、版本列表与详情页结构、皮肤编辑器的交互方式、3D 预览的思路 | 仅参考交互与功能形态，**未复制任何源代码**，全部用 VB.NET / WPF 重新实现 |
| [PCL](https://github.com/Meloong-Git/PCL)（Plain Craft Launcher） | 项目早期（1.0 之前）的版本安装、资源下载、启动参数构建等核心流程，参考了它的公开源码与实现思路 | 此后经过长期重构：模块重新划分、算法与数据结构重写、界面完全自绘，**当前代码与 PCL 的源文件已无直接对应关系**，TCL 是一个独立演化的项目 |
| [EasyTier](https://github.com/EasyTier/EasyTier) | 联机功能直接调用其官方发行版 | 以独立进程方式使用，未链接、未修改其源码 |
| [Lucide](https://lucide.dev/) | 界面图标 | 静态资源，遵循 ISC |

关于 PCL 的[分发许可](https://github.com/Meloong-Git/PCL/blob/main/LICENCE)：TCL 属于「参考实现思路后自行开发、且未与 PCL 混淆」的情形，因此按其中的「轻度使用」条款处理——给出署名、不暗示与 PCL 有关，也不使用 PCL 相关名称。如果权利人认为需要调整，请提 Issue，我们会第一时间配合。

同时感谢 Newtonsoft.Json、libwebp 等开源组件，
完整清单见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 社区

- **TCraft Launcher 启动器交流群**：[点击加入](https://qm.qq.com/q/WaG64YE9uo)（群号 1131373915，更新通知、问题反馈、交流，本项目唯一即时通讯渠道）
- **Issue**：[提交问题反馈](https://github.com/Damianjiang/TCraft-Launcher/issues)
- **讨论区**：[GitHub Discussions](https://github.com/Damianjiang/TCraft-Launcher/discussions)

## 许可证

[MIT](LICENSE) © 2026 TCraft Team

## 免责声明

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

非 Minecraft 官方产品。未经 Mojang 或 Microsoft 批准，也不与 Mojang 或 Microsoft 关联。
本启动器不包含、不分发 Minecraft 游戏本体；使用本软件即表示你已通过官方渠道购买了
Minecraft: Java Edition。侵权投诉与合规问题可通过 [Issue](https://github.com/Damianjiang/TCraft-Launcher/issues) 联系。

---

<div align="center">如果这个项目帮到了你，点一个 Star 就是最好的支持 ⭐</div>
