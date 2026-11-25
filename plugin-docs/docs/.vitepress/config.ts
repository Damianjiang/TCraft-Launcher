import { defineConfig } from 'vitepress'
import { defineTeekConfig } from 'vitepress-theme-teek/config'

const teekConfig = defineTeekConfig({
  teekTheme: true,
  teekHome: false,
  vpHome: true,

  viewTransition: {
    enabled: true,
    mode: 'out-in',
    duration: 300,
    easing: 'ease-in'
  },

  codeBlock: {
    enabled: true,
    collapseHeight: 700
  },

  backTop: {
    enabled: true,
    content: 'progress'
  },

  themeEnhance: {
    enabled: true,
    position: 'top'
  },

  toComment: { enabled: false },
  notice: { enabled: false },
  articleBanner: { enabled: false },
  articleUpdate: { enabled: false },
  articleAnalyze: { enabled: false },
  breadcrumb: { enabled: false },

  loading: false,

  vitePlugins: {
    sidebar: false,
    permalink: false,
    mdH1: false,
    docAnalysis: false
  }
})

const base = process.env.BASE_PATH || '/TCraft-Launcher/wiki/'

export default defineConfig({
  extends: teekConfig,
  lang: 'zh-CN',
  title: 'TCraft Launcher 插件开发文档站',
  description: 'TCraft Launcher 插件开发文档站：清单、完整 API、网络/系统/桌面能力、AI 开发手册与可运行示例。',
  base,
  // 文档成品直接放进官网目录 部署后从 /wiki/ 访问
  outDir: '../../website/wiki',

  head: [
    ['link', { rel: 'icon', href: base + 'logo.png' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:title', content: 'TCraft Launcher 插件开发文档站' }],
    ['meta', { property: 'og:description', content: '用 Java + JavaFX 扩展 TCraft Launcher：页面、命令、网络、文件、进程、桌面控制与 AI 动作。' }],
    ['link', { rel: 'preconnect', href: 'https://tcraft.pages.dev' }],
    ['link', { rel: 'dns-prefetch', href: 'https://tcraft.pages.dev' }]
  ],

  lastUpdated: true,
  cleanUrls: true,
  appearance: 'dark',
  ignoreDeadLinks: true,

  vite: {
    build: {
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('vitepress-theme-teek')) return 'teek'
            if (id.includes('node_modules/vue') || id.includes('node_modules/@vue')) return 'vue-vendor'
            if (id.includes('node_modules/medium-zoom') || id.includes('node_modules/nprogress')) return 'utils'
          }
        }
      }
    }
  },

  themeConfig: {
    siteTitle: 'TCL 插件开发文档站',
    logo: '/logo.png',

    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide' },
      { text: 'API', link: '/api' },
      { text: '能力手册', link: '/capabilities' },
      { text: 'AI 开发', link: '/ai' },
      { text: '示例', link: '/examples' },
      { text: '进阶参考', link: '/references' },
      { text: '官方网站', link: 'https://tcraft.pages.dev' }
    ],

    sidebar: [
      {
        text: '开始',
        items: [
          { text: '插件系统概览', link: '/overview' },
          { text: '五分钟写出第一个插件', link: '/guide' }
        ]
      },
      {
        text: '参考',
        items: [
          { text: 'plugin.json 清单', link: '/manifest' },
          { text: 'API 参考', link: '/api' },
          { text: '网络、系统与桌面能力', link: '/capabilities' },
          { text: '界面与布局扩展', link: '/ui' },
          { text: '命令、弹窗与事件', link: '/commands' }
        ]
      },
      {
        text: 'AI 开发',
        items: [
          { text: '让 AI 写插件', link: '/ai' },
          { text: 'llms.txt 全量接口', link: 'https://damianjiang.github.io/TCraft-Launcher/wiki/llms.txt', target: '_blank' }
        ]
      },
      {
        text: '实践',
        items: [
          { text: '完整示例与模板', link: '/examples' },
          { text: '进阶与参考资料', link: '/references' },
          { text: '常见问题', link: '/faq' }
        ]
      },
      {
        text: '链接',
        items: [
          { text: 'TCraft 官方网站', link: 'https://tcraft.pages.dev' },
          { text: 'TCWiki 服务器文档', link: 'https://tcwiki.pages.dev' },
          { text: '下载启动器', link: 'https://github.com/Damianjiang/TCraft-Launcher/releases/latest' },
          { text: '源码仓库', link: 'https://github.com/Damianjiang/TCraft-Launcher' }
        ]
      }
    ],

    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            displayDetails: '显示详情',
            resetButtonTitle: '清除查询条件',
            backButtonTitle: '返回',
            noResultsText: '未找到相关内容',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭'
            }
          }
        }
      }
    },

    outline: {
      level: [2, 3],
      label: '本页目录'
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇'
    },

    footer: {
      message: 'TCraft Launcher 插件开发文档站 · API v1 · 网络 / 系统 / 桌面 / AI',
      copyright: '© 2026 TCraft Launcher'
    },

    returnToTopLabel: '返回顶部',
    darkModeSwitchLabel: '主题',
    sidebarMenuLabel: '菜单',
    darkModeSwitchTitle: '切换深色模式',
    lightModeSwitchTitle: '切换浅色模式'
  }
})
