# DS Chat · Android

**中文** | [English](README.en.md)

把 DeepSeek 官方开源的网页版 [`chat-demo`](https://github.com/deepseek-ai/chat-demo) 打包成安卓 App 的小工程。

一个 WebView 外壳 + 内置的官方前端，直接连 `https://api.deepseek.com`，用你自己的 API Key 聊天。
没有后端、没有账号体系、不收集任何数据。

> **个人项目，非官方。** 与深度求索（DeepSeek）无关。
> 应用图标与首屏标识是自制的 "DS" 字母标，不含任何第三方商标素材；
> 对话能力来自官方开源的 [chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT）。
>
> 开发与打包在 [DeepSeek Harness](https://deepseek-harness.github.io/deepseek-harness/en/guide/quickstart) 中完成。

## 功能

- 完整对话能力：流式输出、深度思考、联网搜索、链接读取、来源引用
- 文件与图片上传、语音输入、回复朗读
- 多会话管理：自动标题、重命名、删除、消息分支切换
- Markdown / 代码高亮 / KaTeX 公式 / Mermaid 图表
- 中英双语、深浅色主题
- 对话历史导入导出（ZIP），可从官方 App 迁移
- 适配移动端：顶部状态栏跟随页面配色、侧边栏跟手拖拽抽屉

## 安装

1. 到 [Releases](../../releases) 下载最新 `DSChat.apk`
2. 手机上允许「安装未知来源应用」后安装
3. 打开 App → 设置 → 填入 DeepSeek API Key（在 [platform.deepseek.com](https://platform.deepseek.com) 申请）

> 微信/QQ 传输 APK 会把它改名成 `xxx.apk.1`，手动去掉末尾的 `.1` 再安装即可。安装包是 debug 签名，供自己侧载使用，不能上架应用商店。

## 从源码构建

需要：

| 依赖 | 版本 |
| --- | --- |
| Node.js | 20+ |
| JDK | 17 |
| Android SDK | platform `android-34`、build-tools `34.0.0` |

```powershell
$env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\Users\me\AppData\Local\Android\Sdk'

powershell -ExecutionPolicy Bypass -File scripts\build-web.ps1   # 前端 -> android/assets/web
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1   # -> dist\DSChat.apk
```

只想改前端看效果：

```powershell
cd web
npm install
npm run dev        # http://localhost:8090 ，浏览器里填 API Key 就能用
```

详细步骤和排错见 [docs/BUILD.md](docs/BUILD.md)，上传到 GitHub 见 [docs/PUBLISH.md](docs/PUBLISH.md)。

## 目录结构

```
.
├── android/                 安卓工程（无 Gradle，纯 aapt2 + javac + d8）
│   ├── AndroidManifest.xml
│   ├── java/com/example/dschat/MainActivity.java   WebView 外壳
│   ├── res/                 图标、名称、主题
│   └── assets/web/          前端产物（构建生成，未入库）
├── web/                     官方 chat-demo 源码 + 本项目改动
│   └── src/
├── scripts/
│   ├── build-web.ps1        构建前端并同步到 android/assets/web
│   ├── build-apk.ps1        打包签名 APK
│   ├── build-all.ps1        一键构建
│   └── make-icons.py        生成 DS 字母标图标（应用图标 + 首屏标识）
├── docs/
│   ├── BUILD.md             构建与排错
│   ├── PUBLISH.md           上传 GitHub 与发版
│   └── CHANGELOG.md
└── .github/workflows/       前端构建 CI
```

## 实现要点

**1. 用虚拟 https 源加载前端**

WebView 加载的是 `https://appassets.androidplatform.net/`，由 `shouldInterceptRequest`
从 APK 的 assets 里读文件返回。这样页面处于安全上下文，`localStorage`（API Key）
和 `IndexedDB`（对话记录）才能持久化——直接用 `file://` 拿不到 IndexedDB。

**2. CORS**

DeepSeek API 对任意 `Origin` 都回 `Access-Control-Allow-Origin: <你的 Origin>`，
所以虚拟源可以直连 API，不需要自建代理。

**3. 不用 Gradle**

构建环境可能连不上 `maven.google.com` / `services.gradle.org`，因此没有用 AGP：
`aapt2` 编译资源 → `aapt2 link` 合并 assets → `javac` → `d8` → 加 `classes.dex`
→ `zipalign` → `apksigner`，全部只用 JDK 和 Android SDK 自带工具。

**4. Windows 上的 aapt2 坑**

Windows 版 aapt2 会把 assets 条目写成反斜杠（`assets/web\index.html`），而
AssetManager 只认 `/`，结果是运行时全部 404、白屏。`build-apk.ps1` 打包后会把
zip 里的路径统一改成斜杠，并断言不再有反斜杠。

**5. 兼容旧 WebView**

上游默认编译目标是 Chrome 105+，而不少华为/荣耀设备仍停留在 Chromium 6x。
`web/rspack.config.ts` 把目标降到 `chrome >= 61`，`web/src/polyfills.ts` 补了
`crypto.randomUUID`、`Object.hasOwn`、`Array.prototype.at/findLast` 等运行时 API。


## 许可

- 本项目新增的代码（安卓外壳、构建脚本、对前端的改动）：[MIT](LICENSE)
- `web/` 目录来自 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)，MIT 许可
- 第三方归属与商标说明见 [NOTICE.md](NOTICE.md)
