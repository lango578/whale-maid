# 🐳 鲸鱼娘·汐汐 —— DeepSeek 女仆鲸鱼娘

让「汐汐」——一位来自深海的鲸族女仆少女（本体：DeepSeek 大模型）——成为你电脑和手机上的 AI 伴侣。
陪你聊天、陪你打游戏、陪你看视频，还能被摸摸头害羞地甩尾巴～♡

## 项目结构

```
whale-maid/
├── persona/
│   ├── whale_maid_persona.md   # 鲸鱼娘「汐汐」完整人设（系统提示词母本）
│   └── shark_maid_persona.md   # 鲨鱼娘「澜澜」完整人设（第二人格）
├── server/                      # 🖥️ 轻量同步后端（零依赖 Node.js）
│   ├── server.js               #   代理 DeepSeek（Key 集中管理）+ 手机/电脑数据同步
│   └── config.example.json     #   配置模板（apiKey / syncKey / port）
├── pc/                          # 💻 Windows 11 电脑端
│   ├── whale-maid-pc.html      #   单文件网页版（双击即用，Edge/Chrome）
│   ├── electron/               #   Electron 桌面版（可打包 exe + 桌面透明悬浮窗）
│   └── README.md
└── android/                     # 📱 安卓端
    ├── pwa/                    #   网页版 PWA（手机浏览器打开，可添加到主屏幕）
    ├── app/                    #   原生 App 工程（Kotlin + Jetpack Compose，可打包 APK）
    └── README.md
```

## 快速开始（3 步）

1. **拿 API Key**：注册 https://platform.deepseek.com → 左侧「API Keys」→ 创建（充值几块钱够玩很久）。
2. **电脑端**：双击 `pc\whale-maid-pc.html` 用 Edge/Chrome 打开 → ⚙️ 填 Key → 开聊。
   - 想要真·桌面 App + 游戏悬浮窗：装 Node.js 后 `cd pc\electron && npm install && npm start`。
3. **手机端**：把 `android\pwa\` 拷到手机用 Chrome 打开 → ⚙️ 填 Key → 添加到主屏幕。
   - 想要原生 APK：用 Android Studio 打开 `android\` 构建（详见 `android\README.md`）。
4. **（可选）后台模式 + 双端互通**：装 Node.js → `cd server` → 复制 `config.example.json` 为 `config.json` 并填 `apiKey`/`syncKey` → `node server.js`；之后在电脑/手机 App 设置里切到「后台模式」，填后端地址和同步口令，即可互相同步聊天记录、设置与当前人格（详见 `server\README.md`）。

## 双人格说明

- 🐳 **鲸鱼娘·汐汐**：温柔治愈系女仆，泡茶暖床陪你熬夜。
- 🦈 **鲨鱼娘·澜澜**：元气傲娇系女仆，深海掠食者反差萌，专属啦啦队+游戏军师。
- 顶栏「🐳 汐汐 / 🦈 澜澜」一键切换：系统提示词、手绘形象（发色/尾巴/虎牙/女仆装）、互动台词全部同步换装，切换后她会用自己的人设跟你打招呼。
- 人格选择会被记住（本地），后台模式下还会随云端同步到另一台设备。

## 📦 打包安装包（exe / apk）

一键脚本放在 `build\` 文件夹，自动检测并安装缺失工具（Node.js / JDK / Android SDK / Gradle）：

```powershell
# 电脑端 exe 安装程序
cd d:\Projects\whale-maid\build
.\build-pc-exe.ps1          # 产物：pc\electron\dist\（Setup 安装版 + 便携版）

# 手机端 apk 安装包
.\build-android-apk.ps1     # 产物：android\app\build\outputs\apk\debug\app-debug.apk
```

也可以用 Android Studio 打开 `android\` 手动构建。详细步骤与常见问题见 **`build\README.md`**。

## 功能一览

| 功能 | 电脑端 | 手机端 |
|---|---|---|
| 🐳⇄🦈 双人格一键切换（汐汐/澜澜，含形象换装） | ✅ | ✅ |
| DeepSeek 流式聊天（思考内容→「内心小剧场」） | ✅ | ✅ |
| 后台模式（API Key 存后端，手机/电脑数据互通） | ✅ | ✅（PWA+原生） |
| 手绘虚拟形象 + 点击互动（摸头/戳脸/挠尾巴） | ✅ 大号形象 | ✅ |
| 语音输入 / 语音播报 | ✅ | ✅ |
| 🎮 陪玩游戏（开局鼓励/赢了欢呼/输了安慰/复盘） | ✅ | ✅ |
| 🎬 陪看视频（本地文件 / B站 / YouTube 嵌入） | ✅（本地播放器） | 场景模式 |
| 桌面透明悬浮窗（全屏游戏伴在身边，可穿透） | ✅ Electron 版 | — |
| 本地记忆历史 | ✅ | ✅ |

## 玩法小贴士

- 点虚拟形象的**头**：她害羞地说「别揉头啦」；戳**脸颊**会脸红；挠**尾巴**会变成螺旋桨逃跑。
- 玩游戏时开「悬浮窗」，汐汐会飘在游戏画面旁当啦啦队，`Ctrl+Shift+O` 让鼠标穿透。
- 看视频时跟她说剧情，她会超投入地陪你吐槽、一起哭一起笑。
- 深夜聊天时她会「监督」你别熬夜——用尾巴威胁的那种。

## 说明与安全

- **隐私**：API Key 仅保存在你本机（localStorage / SharedPreferences），对话只发给 DeepSeek 官方接口。
- **费用**：按 DeepSeek 官方 token 计费（`deepseek-v4-flash` 很便宜，日常闲聊首选）。
- 代码可自由修改：换人设、换模型、加功能都在 `persona` 和对应端的源码里。
