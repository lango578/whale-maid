#Requires -Version 5.1
<#
  🐳 鲸鱼娘·汐汐 / 🦈 鲨鱼娘·澜澜 — 电脑端打包 exe 一键脚本
  1) 自动检测 Node.js，缺失时用 winget 安装
  2) 安装 Electron 依赖
  3) 用 electron-builder 打包（NSIS 安装包 + 免安装便携版）
  产物位置：pc\electron\dist\
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Test-Cmd([string]$c){ return [bool](Get-Command $c -ErrorAction SilentlyContinue) }

Write-Host '=== 鲸鱼娘·汐汐 / 鲨鱼娘·澜澜 — 电脑端 exe 打包 ===' -ForegroundColor Cyan

# 1. 检测 / 安装 Node.js
if (-not (Test-Cmd node)) {
  Write-Host '未检测到 Node.js，正在通过 winget 安装 Node.js LTS（可能需要几分钟）…'
  winget install -e --id OpenJS.NodeJS.LTS --accept-package-agreements --accept-source-agreements
  $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
  if (-not (Test-Cmd node)) {
    Write-Error 'Node.js 安装失败。请到 https://nodejs.org 下载 LTS 版安装后重新运行本脚本。'
  }
}
Write-Host ("Node.js 版本: " + (node --version))

# 2. 安装依赖
Set-Location (Join-Path $Root 'pc\electron')
if (-not (Test-Path 'node_modules')) {
  Write-Host '首次运行，安装 Electron 依赖（下载较大，请耐心等待）…'
  npm install --no-fund --no-audit
} else {
  Write-Host '依赖已存在，跳过 npm install'
}

# 3. 打包
Write-Host '开始打包 exe …'
npm run dist

# 4. 输出结果
Write-Host '' -ForegroundColor Cyan
Write-Host '=== 打包完成 ✅ ===' -ForegroundColor Green
Get-ChildItem (Join-Path $Root 'pc\electron\dist') -Filter '*.exe' | ForEach-Object {
  Write-Host ("安装包: " + $_.FullName + "  (" + [math]::Round($_.Length/1MB,1) + " MB)") -ForegroundColor Yellow
}
Write-Host '双击「鲸鱼娘汐汐 Setup x.x.x.exe」即可安装；另一个为免安装便携版。'
