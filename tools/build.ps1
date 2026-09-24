# MCC脱敏工具 打包脚本：跑全测 → shade 单 jar → 复制到 dist/ 并打 sha256
# 用法：powershell -File tools\build.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
Write-Output "==> mvn clean package（含全部测试）"
mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw "构建/测试失败" }
New-Item -ItemType Directory -Force -Path "$root\dist" | Out-Null
# 不写死版本号：取 target 下唯一的 shaded jar（shade 会把原始 jar 挪走，只剩 mask-tool-<版本>.jar）
$built = Get-ChildItem "$root\target\mask-tool-*.jar" -File | Select-Object -First 1
if (-not $built) { throw "target 下找不到 mask-tool-*.jar" }
Copy-Item $built.FullName "$root\dist\mcc-mask.jar" -Force
Write-Output "==> 打包源: $($built.Name)"
$h = (Get-FileHash "$root\dist\mcc-mask.jar" -Algorithm SHA256).Hash
Write-Output "==> 产物 dist\mcc-mask.jar  sha256=$h"
Write-Output ("{0}  dist/mcc-mask.jar" -f $h.ToLower()) | Set-Content "$root\dist\mcc-mask.jar.sha256" -Encoding ascii
