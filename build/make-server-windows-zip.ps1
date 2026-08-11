# 生成 Windows 服务端整合包 zip（无需 Node，纯 PowerShell）
# 产物： build\output\whale-maid-server-windows.zip
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $env:TEMP 'whale-maid-server-stage'
$outDir = Join-Path $Root 'build\output'

New-Item -ItemType Directory -Force $stage | Out-Null
New-Item -ItemType Directory -Force $outDir | Out-Null

# 1) 复制服务端核心文件
Copy-Item (Join-Path $Root 'server\server.js') $stage -Force
Copy-Item (Join-Path $Root 'server\config.example.json') $stage -Force
Copy-Item (Join-Path $Root 'server\README.md') $stage -Force

# 2) 生成 start-server.bat（UTF-8 无 BOM，内部 chcp 65001 显示中文）
$bat = @"
@echo off
chcp 65001 >nul
title Whale Maid Sync Server - 鲸鱼娘汐汐/鲨鱼娘澜澜 同步服务端
echo ============================================================
echo   鲸鱼娘·汐汐 / 鲨鱼娘·澜澜  同步服务端（DeepSeek API 代理）
echo ============================================================
where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 Node.js，请先安装：https://nodejs.org  （LTS 版）
    pause
    exit /b 1
)
if not exist config.json (
    copy config.example.json config.json >nul
    echo [首次运行] 已自动生成 config.json。
    echo 请用记事本打开 config.json，把 apiKey 换成你的 DeepSeek API Key，
    echo 把 syncKey 设成你自己的同步口令，保存后重新双击本文件启动。
    pause
    exit /b 1
)
echo 正在启动服务端，本机地址: http://127.0.0.1:8787
echo 局域网地址: http://%COMPUTERNAME%:8787  （或用 ipconfig 查看本机IP）
echo 按 Ctrl+C 停止服务。
echo.
node server.js
pause
"@
[IO.File]::WriteAllText((Join-Path $stage 'start-server.bat'), $bat, [Text.UTF8Encoding]::new($false))

# 3) 生成 使用说明.txt
$readmeTxt = @"
鲸鱼娘·汐汐 / 鲨鱼娘·澜澜 同步服务端 —— Windows 整合包
=====================================================

【这是什么】
一个零依赖的 Node.js 小服务，做两件事：
  1) 代理 DeepSeek API：电脑/手机 App 填这个后端地址后，API Key 只存在这里，不再填到前端；
  2) 数据互通：手机和电脑的聊天记录、设置、当前人格通过它互相同步。

【三步上手】
  1. 安装 Node.js（LTS 版）：https://nodejs.org
  2. 双击 start-server.bat —— 首次运行会自动生成 config.json，
     用记事本打开，把 apiKey 换成你的 DeepSeek API Key（platform.deepseek.com 获取），
     把 syncKey 设成一个自己的口令（手机/电脑访问时要用）。
  3. 再双击 start-server.bat 启动。看到「同步后端已启动」即成功。

【怎么让手机/电脑连上】
  - 手机和电脑要在同一个 Wi-Fi/局域网；
  - 打开鲸鱼娘 App 的「设置」→ 连接方式选「后台模式」→ 填：
        后端地址：http://<这台电脑的局域网IP>:8787   （cmd 里 ipconfig 可查）
        同步口令：你在 config.json 里设置的 syncKey
  - 然后「⬆️ 同步到云端」/「⬇️ 从云端拉取」即可互通。

【开机自启（可选）】
  Win+R 输入 shell:startup 回车，把 start-server.bat 的快捷方式放进去即可。

【文件说明】
  server.js           服务端程序（不要动）
  config.example.json 配置模板
  config.json         你的实际配置（apiKey / syncKey / port）—— 别外传！
  README.md           详细说明
"@
[IO.File]::WriteAllText((Join-Path $stage '使用说明.txt'), $readmeTxt, [Text.UTF8Encoding]::new($true))

# 4) 打包 zip
$zip = Join-Path $outDir 'whale-maid-server-windows.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip
Remove-Item $stage -Recurse -Force

Write-Host '✅ 整合包已生成:' -ForegroundColor Green
Write-Host "   $zip  ($([math]::Round((Get-Item $zip).Length/1KB,1)) KB)"
