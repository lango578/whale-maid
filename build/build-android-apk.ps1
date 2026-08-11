#Requires -Version 5.1
<#
  🐳 鲸鱼娘·汐汐 / 🦈 鲨鱼娘·澜澜 — 安卓端打包 apk 一键脚本
  1) 自动检测 JDK 17，缺失时用 winget 安装 Temurin
  2) 自动下载 Android SDK commandline-tools 并安装所需组件（android-36 / build-tools 35）
  3) 自动下载 Gradle 8.13
  4) 构建 debug APK
  产物位置：android\app\build\outputs\apk\debug\
  提示：新手也可以直接用 Android Studio 打开 android\ 目录构建（方式见 build\README.md）
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
function Test-Cmd([string]$c){ return [bool](Get-Command $c -ErrorAction SilentlyContinue) }

Write-Host '=== 鲸鱼娘·汐汐 / 鲨鱼娘·澜澜 — 安卓端 apk 打包 ===' -ForegroundColor Cyan

# 1. JDK 17
if (-not (Test-Cmd java)) {
  Write-Host '未检测到 Java，正在通过 winget 安装 Temurin JDK 17 …'
  winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-package-agreements --accept-source-agreements
  $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
  if (-not (Test-Cmd java)) { Write-Error 'JDK 安装失败。请安装 Temurin JDK 17 后重试。' }
}
Write-Host ("Java: " + ((java -version 2>&1) | Select-Object -First 1))

# 2. Android SDK
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$sdkmanager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkmanager)) {
  Write-Host '未检测到 Android SDK，正在下载 commandline-tools（约 150MB）…'
  $zip = Join-Path $env:TEMP 'cmdline-tools.zip'
  Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $zip
  New-Item -ItemType Directory -Force (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
  Expand-Archive $zip -DestinationPath (Join-Path $sdkRoot 'cmdline-tools\tmp') -Force
  Move-Item (Join-Path $sdkRoot 'cmdline-tools\tmp\cmdline-tools') (Join-Path $sdkRoot 'cmdline-tools\latest') -Force
  Remove-Item (Join-Path $sdkRoot 'cmdline-tools\tmp') -Recurse -Force -ErrorAction SilentlyContinue
  [Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkRoot, 'User')
  $env:ANDROID_HOME = $sdkRoot
}
Write-Host "Android SDK: $sdkRoot"

# 接受许可 + 安装组件
$yes = 'y' * 200
Write-Host '安装 Android SDK 组件（platform-tools / android-36 / build-tools）…'
$yes | & $sdkmanager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { Write-Error 'Android SDK 组件安装失败，请检查网络后重试。' }

# 3. 写 local.properties（告诉 Gradle SDK 在哪）
$escapedSdk = ($sdkRoot -replace '\\','\\' -replace ':','\:')
Set-Content -Path (Join-Path $Root 'android\local.properties') -Value ("sdk.dir=$escapedSdk") -Encoding ASCII

# 4. Gradle 8.13
$gradleBin = Join-Path $sdkRoot 'gradle-8.13\bin\gradle.bat'
if (-not (Test-Path $gradleBin)) {
  Write-Host '正在下载 Gradle 8.13（约 130MB）…'
  $gz = Join-Path $env:TEMP 'gradle.zip'
  Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile $gz
  Expand-Archive $gz -DestinationPath $sdkRoot -Force
}

# 5. 构建
Write-Host '开始构建 APK（首次需下载依赖，可能 10~30 分钟）…'
Set-Location (Join-Path $Root 'android')
& $gradleBin :app:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Error 'APK 构建失败，请查看上方日志。' }

Write-Host '' -ForegroundColor Cyan
Write-Host '=== 打包完成 ✅ ===' -ForegroundColor Green
$apk = Get-ChildItem (Join-Path $Root 'android\app\build\outputs\apk\debug') -Filter '*.apk' | Select-Object -First 1
if ($apk) { Write-Host ("APK: " + $apk.FullName + "  (" + [math]::Round($apk.Length/1MB,1) + " MB)") -ForegroundColor Yellow }
Write-Host '把 APK 发到手机安装即可（手机需允许「安装未知来源应用」）。'
