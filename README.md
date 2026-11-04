<div align="center">

<img src="docs/assets/logo.png" width="96" alt="TCraft Launcher">

# TCraft Launcher

**轻一点，快一点。**

一个单文件的 Minecraft 启动器：3.5 MB，冷启动约 5 秒，开源、免费、无广告。

[![Release](https://img.shields.io/github/v/release/Damianjiang/TCraft-Launcher?style=flat-square&color=f2b632)](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Damianjiang/TCraft-Launcher/total?style=flat-square&color=5eead4)](https://github.com/Damianjiang/TCraft-Launcher/releases)
[![License](https://img.shields.io/github/license/Damianjiang/TCraft-Launcher?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%207%20SP1%2B-blue?style=flat-square)](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)

[下载最新版](https://github.com/Damianjiang/TCraft-Launcher/releases/latest) · [官网](https://damianjiang.github.io/TCraft-Launcher/) · [反馈问题](https://github.com/Damianjiang/TCraft-Launcher/issues)

<img src="docs/assets/shot-home.png" width="820" alt="TCraft Launcher 启动台界面">

</div>

---

## 这是什么

TCraft Launcher（简称 **TCL**）是一个用 VB.NET / WPF 写的 Minecraft 启动器。
它没有安装向导、没有捆绑、也没有任何统计上报：整个程序就是一个 3.5 MB 的 exe，
拷到哪儿都能跑，删掉就是卸载。

- **单文件发布**：启动核心与工具库以压缩快照内嵌进 exe，无需运行库依赖。
- **快**：延迟初始化 + 汇编缓存 + UI 资源按需加载，冷启动约 5 秒，空闲占用接近 0。
- **好看、可换**：Win11 风格半透明界面，支持自定义壁纸、背景视频、音乐与主题色。
- **多语言**：内置中英文，运行时可热更新语言表。

## 核心功能

| 模块 | 能力 |
| --- | --- |
| 启动 | 多版本选择、JVM / 游戏参数、内存分配、进程优先级、窗口模式、启动前命令 |
| 账户 | 微软设备码正版登录 / 离线登录 / 外置 Authlib 登录，账户卡片切换与导入导出 |
| 实例 | 多实例隔离、版本独立设置、Mod / 存档 / 数据包 / 截图 / 服务器管理、整合包导出 |
| 整合包 | CurseForge、Modrinth、MCBBS、MultiMC、HMCL 五种格式解析，安装前预览图标与版本 |
| 下载 | 原版与 Forge / NeoForge / Fabric / OptiFine / LiteLoader，CurseForge / Modrinth 资源搜索、断点续传 |
| 联机 | 基于 EasyTier 的虚拟局域网，创建 / 加入房间，直接联机单人存档 |
| 皮肤 | 2D 正视图 + WPF 3D 模型预览，自动识别经典 / 纤细模型 |
| 其他 | 崩溃分析（中文人话）、分类缓存清理、完整日志 |

## 界面

所有截图都来自真实运行，没有渲染图。

| 启动台 | 下载 |
| --- | --- |
| ![启动台](docs/assets/shot-home.png) | ![下载](docs/assets/shot-download.png) |
| ![实例](docs/assets/shot-instance.png) | ![设置](docs/assets/shot-setup.png) |

## 下载

前往 [Releases](https://github.com/Damianjiang/TCraft-Launcher/releases/latest) 下载
`TCraft-Launcher-v1.2.exe`，双击运行即可。

- **系统**：Windows 7 SP1 及以上（推荐 Windows 10 / 11），64 位
- **体积**：3.49 MiB
- **SHA-256**：`b9b043edc43d170e23fd20b41a7da6332380c22942a7f1de29bccdbe7636f6e6`

> 项目未购买代码签名证书，SmartScreen 可能提示「未知发布者」。
> 可核对上方哈希，或直接从源码构建。

> 微软正版登录在开源版中**默认未启用**：程序不内置任何微软凭据。
> 如需使用，注册一个 Azure 应用后设置环境变量 `TCL_MS_CLIENT_ID` 为你的 ClientId，重启启动器即可。

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

构建产物在 `TCl\bin\TCraft Launcher.exe`。也可以直接用 Visual Studio 打开 `TCl.sln` 编译。

## 项目结构

```
├── TCl/          主程序（VB.NET + WPF）：界面、页面、业务逻辑
│   ├── Pages/        各功能页面
│   ├── Controls/     自研控件（列表项、卡片、消息框、滚动条等）
│   ├── Modules/      启动、下载、整合包、联机、皮肤等业务模块
│   └── Resources/    内嵌资源（第三方 DLL 压缩快照、字体、贴图）
├── TClCS/        启动核心（C#）：Java 管理、游戏启动、Minecraft 数据
├── TClKit/       共享工具库（JSON 解析、基础类型、WPF 辅助）
├── docs/         官网（GitHub Pages）
└── scripts/      构建脚本
```

## 参考与致谢

TCL 的功能规划与交互设计参考了 [HMCL](https://github.com/HMCL-dev/HMCL) 的成熟方案；
版本安装、资源下载、启动参数构建等核心流程，参考了
[PCL（Plain Craft Launcher）](https://github.com/Meloong-Git/PCL)的主体代码。
在此向 HMCL 开发者与 PCL 作者龙腾猫跃致谢。

同时感谢 [Lucide](https://lucide.dev/)（ISC）等开源组件，
完整清单见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 许可证

[MIT](LICENSE) © 2026 TCraft Team

## 免责声明

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

非 Minecraft 官方产品。未经 Mojang 或 Microsoft 批准，也不与 Mojang 或 Microsoft 关联。
本启动器不包含、不分发 Minecraft 游戏本体；使用本软件即表示你已通过官方渠道购买了
Minecraft: Java Edition。侵权投诉与合规问题可通过 [Issue](https://github.com/Damianjiang/TCraft-Launcher/issues) 联系。

---

<div align="center">如果这个项目帮到了你，点一个 Star 就是最好的支持 ⭐</div>
