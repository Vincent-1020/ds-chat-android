# 构建说明

## 环境

| 依赖 | 版本 | 检查命令 |
| --- | --- | --- |
| Node.js | 20+ | `node -v` |
| JDK | 17 | `java -version` |
| Android SDK | platform 34 + build-tools 34.0.0 | 见下 |

Android SDK 只需要三样东西，用 [命令行工具](https://developer.android.com/studio#command-tools) 装即可，不必装完整的 Android Studio：

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

构建脚本会用 `ANDROID_SDK_ROOT` 找 SDK，用 `JAVA_HOME` 找 JDK。

### 本机已有的工具链

如果这台机器上已经放好了一套工具链（本项目放在**仓库外**的 `..\build-toolchain\`）：

```powershell
$env:JAVA_HOME        = "D:\build-toolchain\jdk\zulu17.70.113-ca-jdk17.0.21.0.3-win_x64"
$env:ANDROID_SDK_ROOT = "D:\build-toolchain\sdk"
```

JDK 约 300 MB、SDK 约 400 MB，只是本机构建用，放在仓库外是为了杜绝误提交。

## 一键构建

```powershell
$env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\Users\me\AppData\Local\Android\Sdk'

powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
```

产物：`dist\DSChat.apk`

## 分步构建

### 1. 前端

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-web.ps1
```

做三件事：`npm install`（如果没有 `node_modules`）→ `npm run build` → 把 `web/dist` 复制到 `android/assets/web`。

只调 UI 时不用打包 APK，直接跑开发服务器：

```powershell
cd web
npm run dev
```

浏览器打开 http://localhost:8090 ，在设置里填 API Key。

### 2. APK

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

可以选择性传参：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 `
    -JavaHome 'C:\Program Files\Java\jdk-17' `
    -SdkRoot  'C:\Users\me\AppData\Local\Android\Sdk' `
    -OutFile  'D:\out\DSChat.apk'
```

脚本步骤：

1. `aapt2 compile` 编译 `android/res`
2. `aapt2 link` 合并资源、清单与 `android/assets`
3. `javac` 编译 Java 源码
4. `d8` 生成 `classes.dex`
5. 把 dex 塞进 APK，并**把 assets 路径里的反斜杠改成斜杠**（见下）
6. `zipalign` 对齐、`apksigner` 签名（首次会自动生成 `<repo>\..\ds-chat-keys\debug.keystore`）

签名用的是 debug key，只能用来自行侧载，不能上架。需要正式签名时，把第 6 步换成你自己的 keystore：

```powershell
apksigner sign --ks my-release.jks --out DSChat-release.apk aligned.apk
```

### 关于 keystore

| 问题 | 答案 |
| --- | --- |
| APK 里会带私钥吗？ | **不会。** APK 内只有公钥证书（`META-INF/*.RSA`），私钥只在你电脑上的 `.keystore` 文件里 |
| 为什么要 gitignore？ | 私钥泄露后，别人能用同一签名做出"看起来是官方更新"的恶意包，覆盖安装 |
| 丢了会怎样？ | 新签名与旧包不一致，已安装的用户必须先卸载才能装新版，对话记录也会一起消失 |
| 能放仓库外吗？ | 可以。默认读 `..\ds-chat-keys\debug.keystore`（**仓库外**），也可以用参数或环境变量指定 |

把密钥放到仓库外（推荐给"整包上传/网盘同步"的场景）：

```powershell
$env:DSCHAT_KEYSTORE = 'D:\keys\ds-chat\debug.keystore'
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

首次使用新路径时，如果文件不存在，脚本会自动生成一个 debug keystore。

## 排错

### 白屏 / 空白页

先看屏幕底部有没有黑色诊断面板（`web/src/index.html` 里注入的脚本会显示报错和 UA）。常见原因：

- **assets 路径是反斜杠**：Windows 版 aapt2 的已知问题。脚本已自动修复并在打包后断言；
  如果你改了脚本，务必保留这一步（`normalised asset paths`）。
- **WebView 太旧**：前端已按 Chrome 61 编译并加了 polyfill，但仍建议在应用市场更新
  「Android System WebView」。
- **资源 404**：检查 `android/assets/web/index.html` 是否存在，且 `dist` 是否已同步。

### `JAVA_HOME is set to an invalid directory`

系统里可能残留了别的 JDK 路径。脚本会显式设置 `JAVA_HOME`，用 `-JavaHome` 传参最稳。

### PowerShell 报「禁止运行脚本」

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
```

### 脚本里的中文变成乱码 / 解析报错

`.ps1` 必须保存为 **UTF-8 with BOM**，否则 Windows PowerShell 5.1 会按 GBK 读取导致语法错误。
仓库里的脚本已经带 BOM，自己编辑后请保持。

### 编译很慢或卡在下载

gradle 依赖被绕开了，正常构建不需要联网（除 `npm install`）。如果 `npm install` 慢，可以换镜像：

```powershell
npm config set registry https://registry.npmmirror.com
```

## 发布新版本

1. 改 `android/AndroidManifest.xml` 里的 `versionCode`（+1）和 `versionName`
2. 更新 `docs/CHANGELOG.md`
3. `scripts\build-all.ps1`
4. 在 GitHub 建一个 Release，把 `dist\DSChat.apk` 上传为附件
