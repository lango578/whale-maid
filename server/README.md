# 同步后端（轻量，零依赖 Node.js）

为「鲸鱼娘·汐汐 / 鲨鱼娘·澜澜」提供两个能力：
1. **集中管理 DeepSeek API Key**：前端不再填 Key，所有聊天请求走后端转发，Key 只保存在 `config.json` 里。
2. **手机 / 电脑数据互通**：聊天记录、设置、当前人格一键同步，任何一端保存，另一端拉取即可。

## 快速开始

1. 安装 Node.js（LTS 即可）：https://nodejs.org
2. 配置：
   ```powershell
   cd d:\Projects\whale-maid\server
   Copy-Item config.example.json config.json
   # 编辑 config.json，填入：
   #   apiKey   = 你的 DeepSeek API Key（sk-...）
   #   syncKey  = 自定义同步口令（手机/电脑访问后端时要用，建议复杂一点）
   #   port     = 服务端口（默认 8787）
   ```
3. 启动：
   ```powershell
   node server.js
   ```
   看到「同步后端已启动」即成功。

## 手机/电脑怎么连它

- 让手机和电脑在**同一个 Wi-Fi/局域网**下；
- 手机和电脑的 App 设置里，把「连接方式」切到 **后台模式**，填：
  - 后端地址：`http://<电脑的局域网IP>:8787`（启动时控制台会提示 IP；也可以用 `ipconfig` 查）
  - 同步口令：`config.json` 里设置的 `syncKey`
- 之后电脑上聊的、设置的、选的人格，手机上「从云端拉取」即可同步，反之亦然。

> 想让手机在**外网**也能连：需要公网 IP / 端口映射 / frp 内网穿透，或用云服务器部署 `server.js`（改 config.json 即可）。注意 `apiKey` 会存在于服务器上，请自己保管好服务器。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/api/chat` | 代理 DeepSeek（SSE 流式）。Body 与原 DeepSeek Chat Completions 一致（model/messages/thinking/reasoning_effort/max_tokens/temperature） |
| GET | `/api/state` | 读取同步数据（需 `X-Sync-Key` 头） |
| POST | `/api/state` | 写入同步数据（需 `X-Sync-Key` 头） |

鉴权：请求头加 `X-Sync-Key: <你的syncKey>`。若 `config.json` 里 `syncKey` 留空则不做校验（不推荐）。
