# TCraft Launcher 插件开发者文档

与 [TCWiki](https://github.com/Damianjiang/TCWiki) 同架构：VitePress 1.6.4 + `vitepress-theme-teek`。
构建输出到 `website/wiki/`，该目录是构建产物、已加入 `.gitignore`；GitHub Actions 部署时重新构建，不提交到仓库。

## 本地开发

```bash
npm install
npm run docs:dev
```

默认访问 `http://localhost:5173/TCraft-Launcher/wiki/`。

## 构建

```bash
npm run docs:build
```

默认产物在 `../website/wiki/`。GitHub Pages 构建时会覆盖 `BASE_PATH`：

```bash
BASE_PATH=/TCraft-Launcher/wiki/ npm run docs:build
```

产物仍在 `../website/wiki/`，随 `website/` 一起发布到站点根目录的 `/wiki/`。
如果仓库改名或部署到别的路径，改 `BASE_PATH` 即可。

## 目录

| 文件 | 内容 |
| --- | --- |
| `docs/index.md` | 首页 |
| `docs/overview.md` | 插件系统概览与加载流程 |
| `docs/guide.md` | 从零写第一个插件 |
| `docs/manifest.md` | `plugin.json` 字段与校验规则 |
| `docs/api.md` | 生命周期与完整 API |
| `docs/capabilities.md` | 网络、文件、系统、进程、桌面、调度、启动器能力与代码配方 |
| `docs/ai.md` | 给 AI 的提示词、动作协议、验收清单与常见错误 |
| `docs/ui.md` | 页面、按钮位置与任意 JavaFX 布局 |
| `docs/commands.md` | 命令、弹窗、存储、事件 |
| `docs/examples.md` | 可直接导入的示例与模板 |
| `docs/faq.md` | 常见问题 |

`docs/public/llms.txt` 是给 AI 的单文件全量接口清单；`docs/public/examples/` 放可下载源码与 zip。

## 新增页面

1. 在 `docs/` 新建 Markdown。
2. 在 `docs/.vitepress/config.ts` 的 `themeConfig.sidebar` 增加条目。
3. 内部链接使用 `/page`，会按 `base` 自动改写。
4. 构建前确认没有未转义的 `<tag>` 文本，否则 Vue 编译器会报 `Element is missing end tag`。

## 约定

- 面向开发者，命令与代码必须可直接运行。
- API 变更时同步更新 `api.md`、`examples.md` 和 `faq.md`。
- 文档不承诺未实现的字段；新增能力先改代码再改文档。
