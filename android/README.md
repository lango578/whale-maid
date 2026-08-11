# 鲸鱼娘·汐汐 —— 安卓端（Android 16）

DeepSeek 女仆鲸鱼娘的手机伴侣。两种安装方式，任选其一：

## 方式 A：网页版（PWA，最快上手，0 安装）

1. 把 `pwa/` 文件夹里的 4 个文件（`index.html`、`manifest.webmanifest`、`sw.js`、`icon.svg`）放到手机可访问的地方：
   - 简单粗暴：用数据线拷到手机，用 Chrome 打开 `index.html`。
   - 推荐：上传到任意静态托管（如 GitHub Pages / Netlify / 家里的电脑用 `python -m http.server` 局域网访问），这样「添加到主屏幕」后就是全屏 App，还能离线打开。
2. 打开后：点右上角 ⚙️ → 填 DeepSeek API Key → 保存。
3. 浏览器菜单 →「添加到主屏幕」→ 以后从桌面图标直接启动，全屏无地址栏。

功能：聊天（流式）、🐳⇄🦈 双人格一键切换（汐汐/澜澜，形象同步换装）、虚拟形象（可点击互动）、语音输入/播报、陪玩模式、陪看模式、本地记忆历史。
后台模式：设置里选「后台模式」填后端地址+同步口令后，可把聊天记录与人格同步到电脑（见 `..\server\README.md`）。

> 注意：在 `file://` 下打开时「添加到主屏幕」和离线功能受限，建议托管后使用；也可以直接用 Chrome 打开，功能完全一样。

## 方式 B：原生 App（Kotlin + Jetpack Compose，真正的 APK）

### 构建步骤（在 Windows 11 电脑上）

1. 安装 **Android Studio**（下载地址 https://developer.android.com/studio ，选最新稳定版即可，自带 Android 16 SDK）。
2. 打开 Android Studio → `Open` → 选择 `whale-maid\android` 文件夹。
3. 首次打开会提示下载 Gradle、依赖、Android SDK（点允许，等待完成）。
   - 若提示 Gradle 版本问题，点击提示里的「升级 / Use Gradle wrapper」让 Studio 自动修复。
4. 顶部工具栏 `Build` → `Build App Bundle(s)/APK(s)` → `Build APK(s)`。
5. 生成的 APK 在 `android\app\build\outputs\apk\debug\` 下（如 `app-debug.apk`）。
6. 手机开启「开发者选项 → USB 调试」，用数据线连电脑，直接点 ▶ 运行；或者把 APK 发到手机（微信/网盘）安装。

### 原生 App 功能
- 💬 DeepSeek 流式聊天（思考内容会显示为「💭 内心小剧场」）
- 🐳⇄🦈 双人格切换（顶栏一键换装；提示词/形象/台词全换）
- 🖥️ 后台模式：Key 存后端，聊天记录与人格可云端互相同步
- 🐳 原生 Canvas 手绘虚拟形象：摸头/戳脸/挠尾巴有表情反应
- 🎮 陪玩模式：输入游戏名开局鼓励、赢了/输了即时互动
- 🎬 陪看模式：切换后场景提示注入人格，聊剧情更投入
- 🎤 系统语音识别输入（点击输入框旁的 🎤）
- 🔊 系统 TTS 语音播报回复（设置里可关）
- 本地记忆历史 / 设置自动保存

### 工程结构

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties   # Gradle 配置
├── app/
│   ├── build.gradle.kts                                          # 应用构建配置（compileSdk 36）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/whalemaid/app/
│       │   ├── MainActivity.kt          # Compose 界面 + 原生 Canvas 鲸鱼娘形象
│       │   ├── data/ChatViewModel.kt    # 状态管理 + 人格提示词 + 历史持久化
│       │   ├── data/ChatMessage.kt      # 消息模型
│       │   └── net/DeepSeekApi.kt       # OkHttp SSE 流式调用 DeepSeek API
│       └── res/                         # 主题、图标、资源
└── pwa/                                 # 方式 A 的网页版
```

## 常见问题

- **编译报错版本问题**：Android Studio 会提示升级 AGP/Gradle，点「Upgrade」即可；若仍失败，把 `app/build.gradle.kts` 里的 `compileSdk/targetSdk = 36` 改成 `35` 试试（Android 16 手机同样能跑）。
- **语音没反应**：确保系统有中文 TTS（设置→系统→无障碍→文字转语音），或安装「讯飞/Google 中文语音」。
- **API Key**：只存在本机 SharedPreferences，不会外传。
