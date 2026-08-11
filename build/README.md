# 📦 打包指南（exe 安装包 / apk 安装包）

两个一键脚本：**双击即可**，会自动检测并安装缺失工具（Node.js / JDK / Android SDK / Gradle）。

| 你要的 | 脚本 | 产物位置 |
|---|---|---|
| 电脑端 exe 安装程序 | `build-pc-exe.ps1` | `pc\electron\dist\`（Setup 安装版 + 便携版） |
| 手机 apk 安装包 | `build-android-apk.ps1` | `android\app\build\outputs\apk\debug\` |

> 运行方式：在 `build` 文件夹里，右键脚本 →「使用 PowerShell 运行」。若系统提示「禁止运行脚本」，先执行一次：
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

---

## 方式一：一键脚本（推荐，自动装工具）

### 1️⃣ 电脑端 exe
```powershell
cd d:\Projects\whale-maid\build
.\build-pc-exe.ps1
```
脚本会自动：
- 用 **winget** 安装 Node.js LTS（若没有）
- `npm install` 安装 Electron 依赖
- 打包出 `鲸鱼娘汐汐 Setup x.x.x.exe`（安装版）和便携版

### 2️⃣ 手机端 apk
```powershell
cd d:\Projects\whale-maid\build
.\build-android-apk.ps1
```
脚本会自动：
- 用 **winget** 安装 Temurin JDK 17（若没有）
- 下载 Android SDK commandline-tools 到 `%LOCALAPPDATA%\Android\Sdk`
- 安装 `android-36` 平台、`build-tools;35.0.0`、`platform-tools`
- 下载 Gradle 8.13
- 构建出 `app-debug.apk`

> ⚠️ 首次运行要下载大量依赖（Node ~100MB，SDK ~300MB，Gradle ~130MB，Maven 依赖若干），**请保持网络畅通，耐心等待 10~30 分钟**。

---

## 方式二：手动安装（适合想要可控过程的人）

### 电脑端
1. 安装 Node.js LTS：https://nodejs.org
2. 打开 PowerShell：
   ```powershell
   cd d:\Projects\whale-maid\pc\electron
   npm install
   npm run dist        # 产物在 dist\ 目录
   ```
   - 想只跑不打包：`npm start`

### 手机端（推荐 Android Studio，最稳）
1. 安装 **Android Studio**：https://developer.android.com/studio
   - 安装向导里勾选 Android SDK、Build Tools（或首次打开时让它自动补装）
2. `File → Open` → 选择 `d:\Projects\whale-maid\android`
3. 首次打开会提示下载 Gradle 与依赖，点允许等待完成（右下角进度条走完）
4. `Build → Build App Bundle(s)/APK(s) → Build APK(s)`
5. APK 在 `android\app\build\outputs\apk\debug\app-debug.apk`

---

## 常见问题

- **下载很慢/失败**：国内网络建议：① 开代理；② Gradle 慢可把 `android\gradle\wrapper\gradle-wrapper.properties` 的 `distributionUrl` 换成腾讯/阿里镜像；③ 重跑一次脚本一般能续上。
- **杀毒软件误报**：electron-builder 打包出的 exe 偶有误报，加白名单即可（代码都在仓库里，可自行核对）。
- **APK 装不上**：手机设置 → 安全 → 允许「安装未知来源应用」。
- **Android 构建报 SDK 版本错**：脚本已固定 `compileSdk 36` + `build-tools;35.0.0`；若你手动用 Android Studio，提示升级 AGP 时点「Upgrade」即可。
- **构建成功后 exe 双击闪退**：多半是 API Key 未设置，先运行网页版 `pc\whale-maid-pc.html` 在设置里填 Key；exe 版设置会同步保存在本机。
