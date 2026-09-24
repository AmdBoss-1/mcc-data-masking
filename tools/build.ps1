# MCC脱敏工具 打包脚本：跑全测 → shade 单 jar → 复制到 dist/ 并打 sha256
# 用法：powershell -File tools\build.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Write-Output "==> mvn clean package（含 44 项测试）"
mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw "构建/测试失败" }
New-Item -ItemType Directory -Force -Path "$root\dist" | Out-Null
Copy-Item "$root\target\mask-tool-1.0.0.jar" "$root\dist\mcc-mask.jar" -Force
$h = (Get-FileHash "$root\dist\mcc-mask.jar" -Algorithm SHA256).Hash
Write-Output "==> 产物 dist\mcc-mask.jar  sha256=$h"
Write-Output ("{0}  dist/mcc-mask.jar" -f $h.ToLower()) | Set-Content "$root\dist\mcc-mask.jar.sha256" -Encoding ascii
