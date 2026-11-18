# NOTICE

## 上游项目

`web/` 目录基于 **deepseek-ai/chat-demo**（MIT License）：

- 仓库：https://github.com/deepseek-ai/chat-demo
- 许可证：MIT，原文见 [`web/LICENSE`](web/LICENSE)
- 本项目对其做了如下修改：
  - `rspack.config.ts`：编译目标从 `chrome >= 105` 降到 `chrome >= 61`；新增图片资源规则
  - `src/polyfills.ts`：新增，为旧版 WebView 补充运行时 API
  - `src/main.tsx`：引入 polyfills
  - `src/index.html`：注入一段诊断脚本（报错/白屏时在屏幕上显示原因）
  - `src/components/session/Welcome.tsx`、`Welcome.module.css`：首屏加鲸鱼标识，问候语文案调整
  - `src/components/chatInput/ChatInput.tsx`：开关按钮始终显示文字标签
  - `src/components/sider/Sider.tsx`、`mobileSiderState.ts`：移动端侧边栏改为跟手拖拽抽屉
  - `src/app/App.tsx`：抽屉展开时内容面板右移
  - `src/services/settings.ts`：默认主题改为浅色
  - `src/assets/logo.png`：新增 "DS" 字母标（首屏标识）
  - `package.json`：`build` 脚本改为跨平台写法

## 商标与素材

- 「DeepSeek」名称与鲸鱼图形是杭州深度求索人工智能基础技术研究有限公司的商标/著作权素材。
- 本项目**不使用任何 DeepSeek 图形素材**：应用图标与首屏标识都是用
  `scripts/make-icons.py` 生成的 **"DS" 字母标**（白色底 + 品牌蓝字样）。
- 项目中出现的 "DeepSeek" 字样，仅用于说明所对接的 API 服务与上游开源项目，不构成关联暗示。

## 使用范围

本项目为个人自用，仓库保持私有。图标与名称均不含第三方商标，
即使将来公开，也只需在仓库描述中注明"非官方、个人项目"。
## 第三方依赖

- React 18、zustand、rspack、KaTeX、Mermaid、PrismJS 等，各自遵循其原始许可证（见 `web/package.json`）。
- Android 构建仅使用 JDK 与 Android SDK 自带工具，未引入 AndroidX 或 Gradle 插件。

## 关于命名

本项目已从 `DeepSeekChat` 改名为 **DS Chat**，Android 包名也从 `com.deepseek.chatdemo`
改为中性域名 `com.example.dschat`，避免在分发时造成"官方出品"的误解。

名称与包名已不含 DeepSeek 商标；但**应用图标与首屏仍是 DeepSeek 的鲸鱼图形**。
若要公开分发，建议一并替换成自己的图形。