<div align="center">

<img src="docs/assets/logo.png" width="96" alt="TCraft Launcher">

# TCraft Launcher

**Lighter. Faster.**

A single-file Minecraft launcher: 3.5 MB, ~2 s cold start. Open source, free, no ads.

[![Release](https://img.shields.io/github/v/release/Damianjiang/TCraft-Launcher?style=flat-square&color=f2b632)](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Damianjiang/TCraft-Launcher/total?style=flat-square&color=5eead4)](https://github.com/Damianjiang/TCraft-Launcher/releases)
[![License](https://img.shields.io/github/license/Damianjiang/TCraft-Launcher?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%207%20SP1%2B-blue?style=flat-square)](https://github.com/Damianjiang/TCraft-Launcher/releases/latest)

[Download](https://github.com/Damianjiang/TCraft-Launcher/releases/latest) · [Website](https://damianjiang.github.io/TCraft-Launcher/en/) · [中文文档](README.md) · [Issues](https://github.com/Damianjiang/TCraft-Launcher/issues)

<img src="docs/assets/shot-home.png" width="820" alt="TCraft Launcher home page">

</div>

---

## What is this

TCraft Launcher (short **TCL**) is a Minecraft launcher written in VB.NET / WPF.
There is no installer, no bundle, and no telemetry: the whole program is one
3.5 MB exe. Copy it anywhere and run; delete it to uninstall.

- **Single file**: the launch core and utility libraries are embedded as compressed snapshots — no runtime dependencies.
- **Fast**: lazy initialization, assembly caching and on-demand UI resources give a ~2 s cold start.
- **Good looking**: Win11-style translucent UI with custom wallpaper, background video, music and theme colors — all optional.
- **Bilingual**: built-in Chinese & English, hot-reloadable language table.

## Features

| Module | What you get |
| --- | --- |
| Launch | Multi-version, JVM / game arguments, memory allocation, process priority, window mode, pre-launch commands |
| Accounts | Microsoft device-code login / offline / external Authlib servers, account cards with import & export |
| Instances | Fully isolated instances, per-version settings, mod / saves / datapacks / screenshots / servers management, modpack export |
| Modpacks | Parses CurseForge, Modrinth, MCBBS, MultiMC and HMCL formats; shows name, icon, game version and loader before installing |
| Downloads | Vanilla + Forge / NeoForge / Fabric / OptiFine / LiteLoader, CurseForge & Modrinth search, resumable downloads |
| Multiplayer | Virtual LAN sessions powered by EasyTier — play your single-player worlds with friends |
| Skins | 2D front view + real WPF 3D model preview (classic / slim auto-detected) |
| Extras | Chinese crash analysis, categorized cache cleaner, full logs |

## Download

Grab `TCraft-Launcher-v1.2.exe` from the [latest release](https://github.com/Damianjiang/TCraft-Launcher/releases/latest) and run it.

- **OS**: Windows 7 SP1+ (Windows 10 / 11 recommended), 64-bit
- **Size**: 3.48 MiB
- **SHA-256**: `3671fd86dfb0ee1bd6cfa712ee053328715dd5c595044de2eef43f9f1aded008`

> The project does not purchase a code-signing certificate, so SmartScreen may
> show an "unknown publisher" warning. Verify the hash above, or build from source.

> Microsoft (genuine) login ships **disabled** in the open-source build: no
> Microsoft credentials are embedded. To enable it, register your own Azure app
> and set the `TCL_MS_CLIENT_ID` environment variable — or join QQ group
> **1081955336** to get the full edition with built-in Microsoft login.

## Build from source

Requirements:

- Windows 10+
- [Visual Studio 2022 Build Tools](https://visualstudio.microsoft.com/downloads/) with the **.NET desktop development** workload (includes Visual Basic and the .NET Framework 4.8 targeting pack)
- Optional: [7-Zip](https://www.7-zip.org/) (smaller embedded snapshots)

```powershell
git clone https://github.com/Damianjiang/TCraft-Launcher.git
cd TCraft-Launcher
powershell -ExecutionPolicy Bypass -File scripts\build.ps1
```

Output: `TCl\bin\TCraft Launcher.exe`. You can also open `TCl.sln` in Visual Studio.

## Project structure

```
├── TCl/          Main app (VB.NET + WPF): UI, pages, business logic
│   ├── Pages/        Feature pages
│   ├── Controls/     In-house controls (list items, cards, message boxes, scrollbars…)
│   ├── Modules/      Launch, download, modpack, multiplayer, skin modules
│   └── Resources/    Embedded assets (compressed third-party DLLs, fonts, textures)
├── TClCS/        Launch core (C#): Java management, game launch, Minecraft data
├── TClKit/       Shared utilities (JSON, base types, WPF helpers)
├── docs/         Website (GitHub Pages)
└── scripts/      Build scripts
```

## Credits

TCL stands on the shoulders of prior work — every source is listed:

| Source | What was referenced | Current state |
| --- | --- | --- |
| [HMCL](https://github.com/HMCL-dev/HMCL) (GPL-3.0) | Feature planning & interaction design: account layout, version list / detail pages, skin editor UX, 3D preview idea | Interaction only — **no source code copied**; everything re-implemented in VB.NET / WPF |
| [PCL](https://github.com/Meloong-Git/PCL) (Plain Craft Launcher) | Early (pre-1.0) core flows — version install, resource download, launch-argument building — referenced PCL's public source and approach | Heavily refactored since: modules re-organized, algorithms and data structures rewritten, UI fully original. **Current code no longer maps to PCL source files**; TCL is an independently evolving project |
| [EasyTier](https://github.com/EasyTier/EasyTier) | Multiplayer feature calls its official release | Separate process; no linking or modification |
| [Lucide](https://lucide.dev/) | UI icons | Static assets, ISC |

Regarding PCL's [distribution license](https://github.com/Meloong-Git/PCL/blob/main/LICENCE):
TCL falls under the "light use" case — implementation approach studied, project
named independently, no confusion with PCL. Attribution is given above. If the
rights holder disagrees, please open an issue and we will cooperate immediately.

Thanks also to Newtonsoft.Json, NAudio, Ookii.Dialogs and other open-source
components — see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Community

- **Issues**: [open an issue](https://github.com/Damianjiang/TCraft-Launcher/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Damianjiang/TCraft-Launcher/discussions)
- **QQ group** (Chinese): 1081955336

## License

[MIT](LICENSE) © 2026 TCraft Team

## Disclaimer

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

This launcher does not include or distribute Minecraft itself; use it only if
you own a legitimate copy of Minecraft: Java Edition.
