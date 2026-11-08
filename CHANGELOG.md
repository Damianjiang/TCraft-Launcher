# 更新日志 / Changelog

[English](#english) · **中文**

每个版本的完整发布说明（下载、校验、合规说明）见
[最新 Release](https://github.com/Damianjiang/TCraft-Launcher/releases)。
官网与下载链接始终指向最新构建，因此页面上不写死版本号。

Full release notes for every build (download, checksum, compliance) are on the
[latest release](https://github.com/Damianjiang/TCraft-Launcher/releases).
The website and download link always resolve to the newest build, so no version number is hardcoded there.

---

## v1.3.7 (1.3.7.0) — 2026-11-08 / 中文

（正式版更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- 资源包、光影包管理页面完全对齐 Mod 管理页面布局与交互，支持滑动多选、底部批量操作面板、带图标的全部/已启用/已禁用筛选。
- 资源包、光影包启用/禁用统一通过修改文件后缀实现，操作后自动同步 options.txt，不影响游戏正常识别。
- 支持读取资源包/光影包内置 pack.png 图标，兼容多路径查找，自动缓存图标。
- 修复滑动多选时空引用崩溃、布局重叠、控件无响应、筛选按钮位置错误等问题。
- 列表刷新加载动画统一为镐子旋转动画，与下载页面完全一致。

## v1.3.7 (1.3.7.0) — 2026-11-08 / English

(Stable release; asset `TCraft-Launcher.exe`, checksum on the Release page)

- Resource pack and shader pack management pages now fully match the Mod management page layout and interaction, supporting swipe multi-select, bottom batch action panel, and icon-enabled All/Enabled/Disabled filters.
- Enabling/disabling resource packs and shader packs now works uniformly by modifying file extensions, automatically syncing options.txt after operations without affecting game recognition.
- Added support for reading built-in pack.png icons from resource/shader packs, with multi-path compatibility and automatic icon caching.
- Fixed null reference crashes on swipe multi-select, layout overlap, unresponsive controls, and incorrect filter button position issues.
- List refresh loading animation is now unified to the pickaxe rotation animation, fully consistent with the download page.

## v1.3.6 (1.3.6.0) — 2026-11-08 / 中文

（正式版更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- 修复账户管理页面点击刷新、复制、笑脸、删除按钮时，误弹出"已切换启动账户"提示的问题。
- 修复实例诊断页面日志按钮换行时上下粘连、被压缩导致文字显示不全的问题，改为固定两行三列布局。
- 修复自研 TLS 客户端在网络超时后，后台异步连接仍在运行导致的未观察 NullReferenceException 崩溃。
- 修复从历史快照页跳转到诊断页时，侧边栏选中状态与实际页面不匹配的问题。
- 修复 Mod 卡片描述文字颜色太浅、在浅色主题下看不清的问题。
- 修复下拉框弹出列表宽度被强制等于下拉框本身宽度，导致长选项文字被截断的问题。
- 资源下载页分类、来源、加载器下拉框改变时，现在会自动触发搜索，无需手动点击搜索按钮。
- 优化侧边栏切换动画，去掉回弹甩尾效果，减少控件较多时的卡顿。
- 优化后台资源占用，降低空闲时的 CPU 和内存消耗。
- 移除文言文语言选项，仅保留简体中文、English、繁體中文三种语言。
- 更新 QQ 交流群链接，同步更新 README 与页脚说明。

## v1.3.6 (1.3.6.0) — 2026-11-08 / English

(Stable release; asset `TCraft-Launcher.exe`, checksum on the Release page)

- Fixed a bug where clicking refresh, copy, emoji, or delete buttons on the account management page would incorrectly show a "Switched launch account" toast.
- Fixed the log buttons on the instance diagnostics page sticking together and being compressed when wrapping, now using a fixed two-row three-column layout.
- Fixed an unobserved NullReferenceException crash in the self-developed TLS client caused by background async connections still running after a network timeout.
- Fixed the sidebar selection state not matching the actual page when navigating from the history snapshot page to the diagnostics page.
- Fixed Mod card description text being too light and hard to read in light themes.
- Fixed the dropdown popup width being forced to match the combobox width, causing long option text to be truncated.
- Resource download page now automatically triggers search when category, source, or loader dropdowns change, no need to manually click the search button.
- Optimized sidebar transition animations, removed the bounce effect to reduce lag when there are many controls.
- Optimized background resource usage, reducing idle CPU and memory consumption.
- Removed the Classical Chinese language option, now only Simplified Chinese, English, and Traditional Chinese are available.
- Updated the QQ group link, synchronized updates to README and footer text.

## v1.3.4 (1.3.4.0) — 2026-11-08 / 中文

（灰度预览更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- Mod 启用 / 禁用（单个与批量）操作完成后弹出提示：单个显示文件名，批量显示数量，与安装 / 更新 / 删除的提示风格一致。
- 存档数据包的启用 / 禁用同样补上提示。
- 同页安装 / 更新 / 删除提示的计数文案补齐英文翻译（此前这些拼接文案在非简中语言下显示简中）。
- 资源包 / 光影页工具栏压缩为单行（搜索框自动适配宽度，按钮不再换行堆叠）；存档页取消独立的「快速操作」卡片，按钮并入存档列表，整体高度明显降低。
- 根除弱网下偶发的「后台任务存在未观察的异常（12002）」日志：这是 .NET HttpClient 取消路径丢弃内层任务与原生超时同刻撞车所致——原生超时改为远大于应用侧超时的最后防线（120s），HttpClient 隐式超时禁用、超时统一由请求侧管理，竞态不再可能发生。

## v1.3.4 (1.3.4.0) — 2026-11-08 / English

(Gray preview update; asset `TCraft-Launcher.exe`, checksum on the Release page)

- Enabling / disabling mods (single or bulk) now shows a toast when done: file name for a single mod, count for bulk, matching the style of the install / update / delete toasts.
- The same feedback was added to enabling / disabling save datapacks.
- Count-related toast fragments on the same page (install / update / delete) are now translated for non-Chinese languages instead of falling back to Simplified Chinese.
- The resource pack and shader toolbars now fit on a single row (the search box adapts its width; buttons no longer wrap); the saves page drops the separate quick-actions card, with the buttons merged into the saves list card.
- Root-caused the occasional "unobserved task exception (12002)" log on slow networks: .NET's HttpClient cancellation path drops the inner handler task, and when the native timeout coincided with the app-side timeout the dropped task carried a fault. Native timeouts are now a 120s last resort far above every app-side timeout, and HttpClient's implicit timeout is disabled in favor of per-request cancellation, so the race can no longer occur.

---

## v1.3.3 (1.3.3.0) — 2026-11-07 / 中文

（灰度预览更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- **Windows 7 下载彻底修复（自研 TLS 1.2 客户端）**：v1.3.2 的证书兜底在 Win7 上仍不够——Win7 系统加密栈（SChannel）缺少现代网站必需的密码套件，与 Cloudflare 系 CDN（Modrinth / ForgeCDN / fabricmc / neoforged / BMCLAPI 等）在密码协商阶段就直接失败，任何证书方案都无效。现内置一个只覆盖启动器所需的轻量 TLS 1.2 客户端（ECDHE secp256r1 + AES-128-GCM，加密原语全部来自系统 CNG，无第三方依赖），Win7 上自动切换、彻底绕开系统加密栈；Windows 10/11 不走此路径。HMCL 在 Win7 正常的原理相同（Java 托管 TLS）。
- 下载主机实测 11/11 通过（Modrinth API/CDN、CurseForge API/CDN、MCIM、Mojang、BMCLAPI、GitHub）。
- 下载失败自动重试时弹出提示（卡在哪个环节 + 第几轮重试，同文本只强调不刷屏）。
- 体积 3.73 MiB（< 4 MB ✅）。

## v1.3.3 (1.3.3.0) — 2026-11-07 / English

(Gray preview update; asset `TCraft-Launcher.exe`, checksum on the Release page)

- **Windows 7 downloads fixed for real (built-in lightweight TLS 1.2 client)**: the v1.3.2 certificate fallback was still not enough — Win7's crypto stack (SChannel) lacks the cipher suites modern sites require, so the handshake with Cloudflare-fronted CDNs (Modrinth / ForgeCDN / fabricmc / neoforged / BMCLAPI) dies at cipher negotiation, where no certificate fix can help. The launcher now embeds a minimal TLS 1.2 client covering exactly what it needs (ECDHE secp256r1 + AES-128-GCM, crypto primitives from the OS CNG, no third-party dependencies), automatically used on Win7 to bypass SChannel entirely; Windows 10/11 are unaffected. Same reason HMCL works on Win7 (Java's managed TLS).
- Verified against 11 download hosts (Modrinth API/CDN, CurseForge API/CDN, MCIM, Mojang, BMCLAPI, GitHub).
- Download auto-retries now announce themselves via toast (which stage + which round, deduplicated).
- Size 3.73 MiB (< 4 MB).

---

## v1.3.2 (1.3.2.0) — 2026-11-07 / 中文

（灰度预览更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- **修复 Windows 7 下载全挂**：Win7 已停止支持，系统根证书库冻结，现代 CDN 签发的证书链验证失败，所有 HTTPS 请求报「未能为 SSL/TLS 安全通道建立信任关系」（Mod 列表图标全部加载失败即此）。现内置最新 CA 根证书库（118 个根，随版本更新）作为兜底信任锚：系统验证失败时改用内置库重新验证，要求链锚定到内置根且域名匹配，安全语义不变。Windows 10/11 行为零变化。
- 下载主机实测：Modrinth API/CDN、CurseForge API/CDN、MCIM 镜像、Mojang、BMCLAPI、GitHub 共 11 个主机全部通过。

## v1.3.2 (1.3.2.0) — 2026-11-07 / English

(Gray preview update; asset `TCraft-Launcher.exe`, checksum on the Release page)

- **Fixed Windows 7 downloads entirely broken**: Win7 is EOL and its root certificate store is frozen, so certificate chains issued by modern CDNs fail validation and every HTTPS request dies with "could not create SSL/TLS secure channel" (the reason all mod-list icons failed to load). The launcher now embeds an up-to-date CA root store (118 roots, refreshed per release) as a fallback trust anchor: when OS validation fails, the chain is re-validated against the bundled roots (must anchor to a bundled self-signed root and match the hostname). No behavior change on Windows 10/11.
- Verified against 11 download hosts: Modrinth API/CDN, CurseForge API/CDN, MCIM mirror, Mojang, BMCLAPI, GitHub.

---

## v1.3.1 (1.3.1.0) — 2026-11-07 / 中文

（灰度预览更新；资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

- 修复网络任务超时后异常未被观察导致的「后台任务存在未观察的异常」日志刷屏（根源修复，覆盖全部网络调用）。
- Windows 7 兼容：TLS 1.2 等协议设置移至启动同步段（消除与首发网络请求的竞态）；HTTP/2 仅在 Windows 10 1607+ 启用，请求版本按系统能力自动选择。
- 下载引擎新增第二轮延迟自动重试：全部下载源失败后延迟 1.5 秒再整体重试一次，弱网下更可靠。
- 实例列表分页：每页 50 个实例，多页时显示翻页条，大量实例不再卡顿；搜索 / 排序 / 视图 / 合集切换自动回到第一页。

## v1.3.1 (1.3.1.0) — 2026-11-07 / English

(Gray preview update; asset `TCraft-Launcher.exe`, checksum on the Release page)

- Fixed the root cause of "unobserved task exception" log spam: abandoned network tasks are now observed on timeout (applies to every network call).
- Windows 7 compatibility: TLS 1.2 protocol setup moved to the synchronous startup phase (removes the race with the first network request); HTTP/2 is only enabled on Windows 10 1607+, request version follows OS capability.
- Download engine gains a second delayed automatic retry (1.5 s after all sources fail) for flaky networks.
- Instance list pagination: 50 instances per page with a page bar, so large instance collections no longer stall the UI; search / sort / view / collection changes return to page 1.

---

## v1.3 (1.3.0.0) — 2026-11-07 / 中文

（资产 `TCraft-Launcher.exe`，校验值见 Release 页面）

### 新功能
- **通知条全面改版**：新样式通知条（图标 + 粗体文本 + 关闭按钮 + 倒计时进度条，支持拖拽关闭）。
- **主题色扩充至 21 种**：新增青柠绿、薄荷绿、靛夜蓝、葡萄紫、雾霭蓝、玫瑰金；控件底色跟随全局半透明设置。
- **社区资源双源竞速**：官方 API 与镜像源几乎同时请求、取最快响应——下载页列表与图标加载数倍提速（实测最坏 27s → 约 5s）。
- 下载 Mod 自动补全前置依赖（递归解析 + 已装检查）；光影 / 资源包列表即预览（自动提取 pack.png）。

### 修复
- **语言切换根治**：切换即时生效、永不重启、往返不回退——原「重启应用语言」机制的全部竞态（等锁超时、异常退出误报、设置文件写回竞态）已随重构移除。
- 单文件部署下社区资源报「System.Buffers 找不到」：已补齐 WinHttpHandler 的依赖闭包（4 个程序集入嵌）。
- 取消启动不再闪退：后台线程异常接入全局兜底记录，可在日志定位。
- 实例页工具栏按钮全部可用（视图切换 / 刷新 / 下载新版本 / 导入整合包 / 打开目录）。
- 补齐缺失的加载动画（光影 / 材质包 / 存档 / Jar-in-Jar 刷新）；按钮样式统一。

### 性能
- **动画引擎重写**：帧循环迁移至 WPF 渲染管线（`CompositionTarget.Rendering`），与合成同相位——切页、侧边栏、加载动画全面跟手；空闲 2 秒自动停用渲染订阅，后台占用归零。
- 下载层启用 HTTP/2 多路复用（Win10 1607+，Win7 自动回退）；启动优化（多核 JIT 预编译、热点消除）；主时钟迁移 DispatcherTimer。
- 单文件体积保持在 3.6 MiB 左右（< 4 MB）。

## v1.3 (1.3.0.0) — 2026-11-07 / English

(Asset `TCraft-Launcher.exe`, checksum on the Release page)

### New
- **Redesigned toast notifications**: icon + bold text + close button + countdown progress bar; drag to dismiss.
- **21 theme colors** (6 new: lime, mint, indigo night, grape, mist blue, rose gold); control backgrounds now follow the global translucency setting.
- **Dual-source racing for community resources**: official API and mirror are queried near-simultaneously, fastest response wins — download-page lists and icons load several times faster (worst case 27s → ~5s measured).
- Missing dependency auto-completion when downloading mods (recursive resolution + installed check); shader / resource-pack lists preview icons via pack.png.

### Fixed
- **Language switching rebuilt**: applies instantly, never restarts, never reverts — all races of the old restart-based mechanism (mutex wait timeout, false "abnormal exit" report, settings-file write-back race) are gone.
- Fixed "System.Buffers not found" in single-file deployments by embedding the full WinHttpHandler dependency closure (4 assemblies).
- Cancelling a launch no longer crashes: background-thread exceptions now hit a global safety-net logger.
- All instance-page toolbar buttons work (view mode / refresh / download / import / open folder).
- Restored missing loading animations (shader / resource-pack / saves / Jar-in-Jar refresh); unified button styles.

### Performance
- **Animation engine rewrite**: the frame loop now runs on the WPF render pipeline (`CompositionTarget.Rendering`), phase-locked to composition — page switches, sidebar and loaders feel snappy; the render subscription self-detaches after 2s idle, zero idle overhead.
- HTTP/2 multiplexing for downloads (Win10 1607+, automatic fallback on Win7); startup optimizations (multi-core JIT profiling, hot-path cleanup); main clock moved to DispatcherTimer.
- Single-file size stays around 3.6 MiB (< 4 MB).

---

## v1.2 补丁更新 — 2026-11-06 / 中文

（程序集 / 文件版本保持 1.2.0.0；资产 `TCraft-Launcher.exe`，校验值见最新 Release 页面）

- 社区群迁移：对外统一群更换为 **TCraft Launcher 启动器交流群（QQ 群号 1131373915）**，
  加入链接 https://qm.qq.com/q/WaG64YE9uo ；官网弹窗、FAQ、页脚、README、更新日志与程序内
  文案同步更新，旧群号不再出现。
- 移除仓库中的 JavaFX 跨平台目录（`tcljavafx/`，源码、构建脚本与产物）；官网 Mac / Linux
  条目改为「从源码自行构建」。
- 重新生成 `LangTables.txt.gz` 及全部内嵌 gz 快照（与当前 dll 字节一致）。

## Patch update — 2026-11-06 / English

(Assembly / file version stays 1.2.0.0; asset `TCraft-Launcher.exe`, checksum on the latest release page)

- Community group moved: the project's external channel is now the **TCraft Launcher group
  (QQ 1131373915)** — join link https://qm.qq.com/q/WaG64YE9uo ; the website modal, FAQ,
  footer, READMEs, changelog and in-app copy were updated accordingly; the old group number
  no longer appears anywhere.
- Removed the JavaFX cross-platform tree (`tcljavafx/`: source, build scripts and artifacts)
  from the repository; the website's Mac / Linux entry now points to building from source.
- Regenerated `LangTables.txt.gz` and all embedded gz snapshots so they match the current dlls.

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
- 「获取完整版」入口弹窗引导加入 <a href="https://qm.qq.com/q/WaG64YE9uo">TCraft Launcher 启动器交流群</a>（群号 **1131373915**），支持复制群号、Esc / 遮罩 / × 关闭。
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
- "Get the full edition" modal pointing to the <a href="https://qm.qq.com/q/WaG64YE9uo">TCraft Launcher group</a> (QQ **1131373915**), with copy-to-clipboard and
  Esc / backdrop / × close.
- Cold-start figure corrected to 2 seconds; no version number is shown on the site.
- Download points at the always-latest release; size, date and SHA-256 are read from the GitHub API
  with a timeout and a working fallback.

---

## 1.0 / 1.1

早期内部迭代版本，未开源发布，也没有公开的二进制资产。功能与界面在 1.2 中已大幅重写。
Early internal iterations; never open-sourced and no public binaries. Most of their functionality
and UI was rewritten for 1.2.
