#!/usr/bin/env bash
# ============================================================
#  鲸鱼娘·汐汐 / 鲨鱼娘·澜澜 —— Linux 服务端一键安装脚本
#  用途：部署 DeepSeek 同步后端（server.js 零依赖，仅需 Node.js）
#
#  一键安装（交互式，之后手动填 config.json）：
#    curl -fsSL https://raw.githubusercontent.com/lango578/whale-maid/main/server/install.sh | bash
#
#  带配置一键安装（推荐，一步到位）：
#    curl -fsSL https://raw.githubusercontent.com/lango578/whale-maid/main/server/install.sh \
#      | bash -s -- --api-key=sk-你的DeepSeekKey --sync-key=你的同步口令 --port=8787
#
#  可选参数：--api-key=xxx  --sync-key=xxx  --port=8787  --dir=/opt/whale-maid-server
# ============================================================
set -e

API_KEY=""
SYNC_KEY=""
PORT="8787"
INSTALL_DIR="/opt/whale-maid-server"
GH_BASE="https://raw.githubusercontent.com/lango578/whale-maid/main/server"

for arg in "$@"; do
  case "$arg" in
    --api-key=*) API_KEY="${arg#*=}" ;;
    --sync-key=*) SYNC_KEY="${arg#*=}" ;;
    --port=*) PORT="${arg#*=}" ;;
    --dir=*) INSTALL_DIR="${arg#*=}" ;;
    *) ;;
  esac
done

echo "============================================================"
echo " 🐳 鲸鱼娘·汐汐 / 🦈 鲨鱼娘·澜澜  服务端一键安装"
echo "============================================================"

# ---------- 1) 检测 / 安装 Node.js ----------
if ! command -v node >/dev/null 2>&1; then
  echo "▶ 未检测到 Node.js，正在自动安装…"
  if [ -x /usr/bin/apt-get ]; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y >/dev/null 2>&1 || true
    apt-get install -y nodejs npm
  elif [ -x /usr/bin/dnf ]; then
    dnf install -y nodejs npm
  elif [ -x /usr/bin/yum ]; then
    yum install -y nodejs npm
  elif [ -x /usr/bin/zypper ]; then
    zypper --non-interactive install nodejs npm
  else
    echo "✗ 未找到可用的包管理器，请手动安装 Node.js（≥18）：https://nodejs.org" >&2
    exit 1
  fi
fi
NODE_VER=$(node -v 2>/dev/null | sed 's/v//' | cut -d. -f1)
if [ -z "$NODE_VER" ] || [ "$NODE_VER" -lt 18 ]; then
  echo "⚠ 当前 Node.js 版本 $(node -v) 偏低（建议 ≥18），可继续，如异常请升级。"
fi
echo "✓ Node.js: $(node -v)"

# ---------- 2) 创建安装目录 ----------
sudo_mkdir() {
  if [ "$(id -u)" -eq 0 ]; then mkdir -p "$1"; else sudo mkdir -p "$1"; fi
}
sudo_mkdir "$INSTALL_DIR"
echo "▶ 安装目录：$INSTALL_DIR"

# ---------- 3) 下载服务端文件 ----------
echo "▶ 下载 server.js / config.example.json …"
DL() { # $1=文件名
  if [ "$(id -u)" -eq 0 ]; then
    curl -fsSL "$GH_BASE/$1" -o "$INSTALL_DIR/$1"
  else
    curl -fsSL "$GH_BASE/$1" | sudo tee "$INSTALL_DIR/$1" >/dev/null
  fi
}
DL "server.js"
DL "config.example.json"

# ---------- 4) 生成 config.json ----------
if [ -n "$API_KEY" ] && [ -n "$SYNC_KEY" ]; then
  echo "▶ 生成 config.json（apiKey / syncKey / port=$PORT）…"
  TMP_CFG=$(mktemp)
  cat > "$TMP_CFG" <<EOF
{
  "port": $PORT,
  "apiKey": "$API_KEY",
  "syncKey": "$SYNC_KEY"
}
EOF
  if [ "$(id -u)" -eq 0 ]; then cp "$TMP_CFG" "$INSTALL_DIR/config.json"; else sudo cp "$TMP_CFG" "$INSTALL_DIR/config.json"; fi
  rm -f "$TMP_CFG"
  echo "✓ config.json 已生成"
else
  if [ ! -f "$INSTALL_DIR/config.json" ]; then
    if [ "$(id -u)" -eq 0 ]; then cp "$INSTALL_DIR/config.example.json" "$INSTALL_DIR/config.json"; else sudo cp "$INSTALL_DIR/config.example.json" "$INSTALL_DIR/config.json"; fi
  fi
  echo "◈ 已生成 $INSTALL_DIR/config.json（模板）"
  echo "  请编辑它：填入 DeepSeek apiKey，并设置 syncKey 同步口令；"
  echo "  或重新运行本脚本并加上 --api-key=... --sync-key=... 参数一步完成。"
fi

# ---------- 5) 安装为 systemd 服务 ----------
if [ "$(id -u)" -eq 0 ] && [ -x /bin/systemctl ]; then
  NODE_BIN=$(command -v node)
  cat > /etc/systemd/system/whale-maid-server.service <<EOF
[Unit]
Description=Whale Maid Sync Server (DeepSeek proxy + cross-device sync)
After=network.target

[Service]
WorkingDirectory=$INSTALL_DIR
ExecStart=$NODE_BIN $INSTALL_DIR/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable whale-maid-server >/dev/null 2>&1 || true
  systemctl restart whale-maid-server
  echo "✓ 已注册为 systemd 服务：whale-maid-server（开机自启、崩溃自动重启）"
  echo "  常用命令：systemctl status/restart/stop whale-maid-server"
else
  echo "◈ 当前非 root 或系统无 systemctl，未注册系统服务。"
  echo "  手动启动： cd $INSTALL_DIR && node server.js"
  echo "  （想用 systemd： sudo bash -c 'cd / && curl -fsSL $GH_BASE/install.sh | bash' 以 root 运行本脚本）"
fi

# ---------- 6) 健康检查 ----------
sleep 2
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
if curl -fsSL "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  echo "============================================================"
  echo " ✅ 安装完成，服务已启动！"
  echo "    本机访问：  http://127.0.0.1:$PORT"
  echo "    局域网访问：http://$IP:$PORT    （App 后台模式填这个地址 + syncKey）"
  echo "============================================================"
else
  echo "⚠ 健康检查未通过。请查看日志：journalctl -u whale-maid-server -n 50"
  echo "  或手动运行： cd $INSTALL_DIR && node server.js"
fi
