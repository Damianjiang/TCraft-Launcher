# 第三方组件声明（THIRD-PARTY NOTICES）

TCraft Launcher 本体代码以 MIT 协议开源。发行版中还打包了以下第三方组件，
它们各自遵循其原始许可证发布，版权归各自作者所有。若本清单有遗漏或错误，
请通过 Issue 告知，我们会立即更正。

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| [Newtonsoft.Json](https://github.com/JamesNK/Newtonsoft.Json) | JSON 解析 | MIT |
| [NAudio](https://github.com/naudio/NAudio) | 音乐播放 | MIT |
| [Ookii.Dialogs.Wpf](https://github.com/ookii-dialogs/wpf-dialogs) | Vista 风格对话框 | BSD |
| [CacheCow](https://github.com/aliostad/CacheCow) | HTTP 缓存 | MIT |
| [Imazen.WebP](https://github.com/imazen/Imazen.WebP) | WebP 图片解码 | Apache-2.0 / BSD |
| [libwebp](https://developers.google.com/speed/webp) | WebP 原生编码器 | BSD-3-Clause |
| [Microsoft.Win32.Registry](https://github.com/dotnet/corefx) | 注册表访问 | MIT |
| [Lucide Icons](https://github.com/lucide-icons/lucide) | 界面图标 | ISC |
| [EasyTier](https://github.com/EasyTier/EasyTier) | 联机虚拟局域网 | GPL-3.0（以独立进程调用，不修改其源码） |

说明：

1. 各组件随发行版分发时的具体版本以 Release 附件为准。
2. 游戏本体的下载、启动与皮肤等内容均来自 Mojang / Microsoft 官方渠道或社区公开服务，
   本启动器不包含、也不分发任何 Minecraft 游戏文件。
3. [EasyTier](https://github.com/EasyTier/EasyTier) 采用 GPL-3.0 协议。TCL 仅通过独立进程
   方式调用其官方发行版，未链接或修改其源代码，因此主程序不适用 GPL-3.0。
4. 微软正版登录（OAuth 设备码流程）需要你自己的 Azure 应用 ClientId：
   本项目不内置、不分发任何微软凭据，可通过环境变量 `TCL_MS_CLIENT_ID` 自行配置。
