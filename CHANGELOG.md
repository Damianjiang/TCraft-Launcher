# 更新日志 / Changelog

[English](#english) · **中文**

每个版本的完整发布说明（下载、校验、合规说明）见
[最新 Release](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)。
官网与下载链接始终指向最新构建，因此页面上不写死版本号。

Full release notes for every build (download, checksum, compliance) are on the
[latest release](https://github.com/Damianjiang/TCraft-Launcher/releases/latest).
The website and download link always resolve to the newest build, so no version number is hardcoded there.

---

## v1.2 (1.2.0.0) — 2026-11-04 / 中文

首个开源版本，也是自 1.0 / 1.1 以来改动最大的一次更新。

| 项目 | 内容 |
| --- | --- |
| 版本号 | v1.2（程序集 / 文件版本 1.2.0.0） |
| 发布日期 | 2026-11-04 |
| 资产 | `TCraft-Launcher.exe`，3,652,608 字节（3.48 MiB，< 4 MB） |
| 运行平台 | Windows 7 SP1 / 10 / 11，64 位，.NET Framework 4.8 |
| 许可证 | MIT |
| 校验值 | 见最新 Release 页面（随每次发布同步更新） |

### 开源与合规

- 源码以 MIT 公开，补齐中英双语 README、参考与致谢、`THIRD-PARTY-NOTICES.md`。
- 源码与二进制不再内置任何微软凭据，开源版微软正版登录默认未启用；CurseForge Key 永不内置。
- 新增双版本构建：完整版用 `InternalBuild=true` 临时编入 `InternalSecrets.vb`，编译后即删除；
  `ModSecret.vb` 反射读取，环境变量 `TCL_MS_CLIENT_ID` / `TCL_CURSEFORGE_API_KEY` 优先级最高。
- 修复完整版「有 GUID 却未启用微软登录」：`DefineConstants` 中的逗号被 MSBuild 当作属性分隔符。
- 版本元数据三处同步（显示名 / `AssemblyInfo.vb` / 项目文件），含 exe 文件属性里的文件版本。
- 发布通道改为固定 `latest` Release + 固定资产名 `TCraft-Launcher.exe`；`v1.2` 仅作历史归档。

### 界面与控件

- 左侧栏全局统一可滚动基类，超长列表可滚到底。
- 下拉框统一改为圆角半透明卡片 + 全局弹出动画（回弹缩放、上浮），选项行加悬停块与选中指示条。
- 修复点击下拉框无反应 / 选项不显示：模板中被冻结的变换对象让面板停留在全透明状态；
  现在变换由运行时创建，面板始终不透明，并有兜底归位。
- 移除实例「世界地图」页面、内嵌颜色表、种子地图功能与「导入其他启动器实例」入口。
- 日志框跟随全局半透明、文字换行、禁用横向滚动、使用自研细滚动条。
- 修复资源包 / 光影页按钮换行贴死、导出说明文字对比度低、菜单弹出偏移、标题栏头像花屏、
  异常退出提示误弹；「备份与恢复」弹窗增加「取消」。

### 整合包

- 安装前新增确认弹窗（名称、图标、格式、游戏版本、体积）。
- 支持 CurseForge / Modrinth / MCBBS / MultiMC / HMCL 清单解析。
- CurseForge 清单只读取 `primary` 加载器；Quilt 整合包明确提示暂不支持。
- 修复「选择现有文件夹」；拖入文件夹或整合包可直接导入并跳转。

### 多语言

- 语言表四列：简体 / English / 繁體 / 文言，Tab 分隔，多行 `\n` 转义。
- 补齐 609 条未翻译文案，英文逐条翻译、繁体 OpenCC 转换、文言繁体兜底，重生成
  `LangTables.txt.gz`；切换语言不再漏出简体原文。

### 稳定性与性能

- 修复存档子页面 `NullReferenceException`（ID `InstanceSaves`）：XAML 初始化期间排序 ComboBox
  提前触发 `SelectionChanged`，目标面板尚未赋值。
- 多处缓存加上限与写入锁；常规清理与性能优化。

### 官网

- 中英切换（桌面 / 移动端）、SEO、社交预览卡片、sitemap。
- 「获取完整版」入口弹窗引导加入 QQ 群 **1081955336**，支持复制群号、Esc / 遮罩 / × 关闭。
- 冷启动数据修正为 2 秒，官网不显示版本号。
- 下载指向永远最新的 Release，体积 / 时间 / SHA-256 由 GitHub 接口动态读取，含超时与失败兜底。

---

<a id="english"></a>

## v1.2 (1.2.0.0) — 2026-11-04 / English

The first open-source release, and the largest update since 1.0 / 1.1.

| Item | Value |
| --- | --- |
| Version | v1.2 (assembly & file version 1.2.0.0) |
| Released | 2026-11-04 |
| Asset | `TCraft-Launcher.exe`, 3,652,608 bytes (3.48 MiB, under 4 MB) |
| Platform | Windows 7 SP1 / 10 / 11, 64-bit, .NET Framework 4.8 |
| License | MIT |
| Checksum | Listed on the latest release page (refreshed with every build) |

### Open source & compliance

- Full source published under MIT; bilingual READMEs, references & credits, `THIRD-PARTY-NOTICES.md`.
- No Microsoft credentials in source or binary: genuine login is disabled by default in the open-source
  build; no CurseForge key is ever embedded.
- Two-edition build: the full edition compiles a temporary `InternalSecrets.vb` with
  `InternalBuild=true` and deletes it right after; `ModSecret.vb` reads it via reflection. Environment
  variables `TCL_MS_CLIENT_ID` / `TCL_CURSEFORGE_API_KEY` take priority.
- Fixed the full edition having a GUID but no Microsoft login: MSBuild treated the comma in
  `DefineConstants` as a property separator.
- Version metadata synced in three places, including the exe's File version property.
- Distribution moved to a fixed `latest` release with a fixed asset name; `v1.2` is a frozen archive.

### Interface & controls

- All left sidebars share one scrollable base control.
- Drop-downs redesigned as rounded translucent cards with the shared pop-up animation, hover blocks
  and a selected marker.
- Fixed drop-downs opening empty: a frozen template transform left the panel fully transparent;
  transforms are now created at runtime and the panel stays opaque, with a fallback reset.
- Removed the instance "World map" page, its colour table, the seed-map feature and the
  "Import instances from other launchers" entry.
- Log panels follow global translucency, wrap text and use the launcher's own thin scrollbar.
- Fixed overlapping action buttons, low-contrast export descriptions, menu positioning, the title-bar
  avatar artefact and a phantom crash prompt; added a Cancel button to "Backup & restore".

### Modpacks

- New confirmation dialog before installation (name, icon, format, game version, size).
- CurseForge / Modrinth / MCBBS / MultiMC / HMCL manifest parsing.
- CurseForge manifests honour `primary` loaders only; Quilt packs are reported as unsupported.
- "Select an existing folder" fixed; drag-and-drop imports folders and modpacks directly.

### Languages

- Four-column language table: Simplified Chinese / English / Traditional Chinese / Literary Chinese.
- 609 previously untranslated strings added, with English translations, OpenCC conversion and a
  literary-Chinese fallback; `LangTables.txt.gz` regenerated.

### Stability & performance

- Fixed a `NullReferenceException` on the Saves sub-page (`InstanceSaves`) caused by `SelectionChanged`
  firing during XAML initialisation.
- Cache limits and write locks added; general cleanup and optimisation.

### Website

- Chinese / English switch on desktop and mobile, SEO, Open Graph cards, sitemap.
- "Get the full edition" modal pointing to QQ group **1081955336**, with copy-to-clipboard and
  Esc / backdrop / × close.
- Cold-start figure corrected to 2 seconds; no version number is shown on the site.
- Download points at the always-latest release; size, date and SHA-256 are read from the GitHub API
  with a timeout and a working fallback.

---

## 1.0 / 1.1

早期内部迭代版本，未开源发布，也没有公开的二进制资产。功能与界面在 1.2 中已大幅重写。
Early internal iterations; never open-sourced and no public binaries. Most of their functionality
and UI was rewritten for 1.2.
