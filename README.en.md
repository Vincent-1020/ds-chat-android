# DS Chat · Android

[中文](README.md) | **English**

A small project that wraps DeepSeek's open-source web client, [`chat-demo`](https://github.com/deepseek-ai/chat-demo),
into an Android app.

A WebView shell plus the bundled official front-end, talking to `https://api.deepseek.com`
directly with your own API key. No backend, no account system, no data collection.

> **Personal project, unofficial.** Not affiliated with DeepSeek.
> The app icon and welcome mark are a self-made "DS" letter mark and contain no third-party trademarks.
> Chat capabilities come from the official open-source [chat-demo](https://github.com/deepseek-ai/chat-demo) (MIT).
>
> Developed and packaged with [DeepSeek Harness](https://deepseek-harness.github.io/deepseek-harness/en/guide/quickstart).

## Features

- Full chat: streaming, deep thinking, web search, link reading, source citations
- File and image upload, voice input, read-aloud
- Multiple sessions: automatic titles, rename, delete, message branches
- Markdown / syntax highlighting / KaTeX math / Mermaid diagrams
- Chinese and English UI, light and dark themes
- Chat history import/export (ZIP), can migrate from the official app
- Mobile refinements: the status bar follows the page colour, swipe-to-open drawer

## Install

1. Download the latest `DSChat.apk` from [Releases](../../releases)
2. Allow "install unknown apps" on the phone
3. Open the app → Settings → paste your DeepSeek API key (create one at
   [platform.deepseek.com](https://platform.deepseek.com))

> WeChat/QQ rename APK files to `xxx.apk.1` when transferring. Remove the trailing `.1` before installing. The APK is debug-signed for personal sideloading, not for app stores.

## Build from source

Requirements:

| Dependency | Version |
| --- | --- |
| Node.js | 20+ |
| JDK | 17 |
| Android SDK | platform `android-34`, build-tools `34.0.0` |

```powershell
$env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\Users\me\AppData\Local\Android\Sdk'

powershell -ExecutionPolicy Bypass -File scripts\build-web.ps1   # front-end -> android/assets/web
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1   # -> dist\DSChat.apk
```

Front-end only, for UI work:

```powershell
cd web
npm install
npm run dev        # http://localhost:8090, enter your API key in the browser
```

Detailed steps and troubleshooting: [docs/BUILD.md](docs/BUILD.md) (Chinese).

## Layout

```
.
├── android/                 Android project (no Gradle; plain aapt2 + javac + d8)
│   ├── AndroidManifest.xml
│   ├── java/com/example/dschat/MainActivity.java   WebView shell
│   ├── res/                 icon, label, theme
│   └── assets/web/          front-end build output (generated, not committed)
├── web/                     upstream chat-demo source + this project's changes
│   └── src/
├── scripts/
│   ├── build-web.ps1        build the front-end into android/assets/web
│   ├── build-apk.ps1        assemble and sign the APK
│   ├── build-all.ps1        build everything
│   └── make-icons.py        generate the "DS" letter-mark icons
├── docs/
│   ├── BUILD.md             build guide and troubleshooting
│   ├── PUBLISH.md           publishing and releases
│   └── CHANGELOG.md
└── .github/workflows/       front-end build CI
```

## Implementation notes

**1. Loading the front-end from a virtual https origin**

The WebView loads `https://appassets.androidplatform.net/` and `shouldInterceptRequest`
serves the files from the APK's assets. That keeps the page in a secure context, which
`localStorage` (API key) and `IndexedDB` (chat history) need — a `file://` page has no
IndexedDB.

**2. CORS**

The DeepSeek API replies `Access-Control-Allow-Origin: <your origin>` to any origin, so
the virtual origin can call it directly; no proxy is needed.

**3. No Gradle**

The build environment may not reach `maven.google.com` or `services.gradle.org`, so AGP
is not used. The pipeline is `aapt2` compile → `aapt2 link` (resources + assets) →
`javac` → `d8` → add `classes.dex` → `zipalign` → `apksigner`, using only the JDK and
the Android SDK's own tools.

**4. The aapt2 backslash trap on Windows**

Windows aapt2 writes asset entries with backslashes (`assets/web\index.html`), while
AssetManager only understands `/`. The result is a 404 for every file and a blank page.
`build-apk.ps1` rewrites those entry names after packaging and asserts that no backslash
remains.

**5. Compatibility with older WebViews**

Upstream targets Chrome 105+; many Huawei/Honor devices still ship Chromium 6x.
`web/rspack.config.ts` lowers the target to `chrome >= 61`, and `web/src/polyfills.ts`
fills in runtime APIs such as `crypto.randomUUID`, `Object.hasOwn` and
`Array.prototype.at/findLast`.


## License

- Code added by this project (Android shell, build scripts, front-end changes): [MIT](LICENSE)
- `web/` comes from [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo), MIT
- Third-party attribution and trademark notes: [NOTICE.md](NOTICE.md)
