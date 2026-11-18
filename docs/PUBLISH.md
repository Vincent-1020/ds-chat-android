# 上传到 GitHub

仓库已经整理成可以直接提交的状态：源码、构建脚本、文档、`.gitignore`、CI 都在，
`node_modules`、`dist`、`android/assets/web`、签名文件都不会被提交。

> 本机当前**没有安装 Git**（`git --version` 找不到命令），下面三种方式选一个即可。

## 方案 A：GitHub Desktop（推荐，不用命令行）

1. 下载安装 [GitHub Desktop](https://desktop.github.com/)
2. 登录你的 GitHub 账号
3. `File` → `Add local repository…` → 选择仓库目录（例如 `D:\DS-chat`）
4. 它会提示"不是 Git 仓库"，点 **create a repository** → 直接 `Create repository`
5. 左下角填写提交信息 → `Commit to main`
6. 右上角 `Publish repository`
   - **保持勾选** `Keep this code private`（本项目为个人自用，不公开）
   - 仓库名建议：`ds-chat-android`

## 方案 B：命令行

先装 [Git for Windows](https://git-scm.com/download/win)，然后在仓库目录执行：

```powershell
cd D:\DS-chat

git init -b main
git add .
git commit -m "feat: DS Chat Android client"
git remote add origin https://github.com/<你的用户名>/ds-chat-android.git
git push -u origin main
```

首次 push 会弹出浏览器让你登录授权。

## 方案 C：网页上传（不推荐）

GitHub 网页版一次最多 100 个文件，这个仓库有 200+ 个文件，需要分批拖拽，容易漏文件。
真要走这条路，至少要分 3 批，并且要手动排除 `node_modules`、`.keystore`。

## 发布 APK

代码推上去之后，把安装包作为 **Release 附件**发布，而不是提交进仓库：

1. 构建：`powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1`
2. 打开仓库 → `Releases` → `Draft a new release`
3. Tag 填 `v0.1.3`（对应 `docs/CHANGELOG.md` 的版本）
4. 把 `dist\DSChat.apk` 拖进附件区
5. 发布

README 里的下载链接指向 `../../releases`，发布后就能直接点开。

## 提交前的检查清单

- [ ] `git status` 里没有 `node_modules/`、`dist/`、`android/assets/web/`（`.toolchain/`、`.keystore/` 已移到仓库外）
- [ ] 没有任何真实的 API Key（本项目不硬编码 key，只存在手机本地）
- [ ] 确认仓库可见性（图标已换成自制素材，私有/公开均可，见 [NOTICE.md](../NOTICE.md)）

## 关于署名与商标

本项目按"个人自用、仓库私有"定位：README 顶部有非官方声明，NOTICE 说明了素材归属。

**如果哪天要公开**，务必先做两件事，否则会有被投诉下架的风险：

1. 换掉鲸鱼图标（自行绘制或中性设计）
2. 仓库描述里写明"非官方，个人项目"
