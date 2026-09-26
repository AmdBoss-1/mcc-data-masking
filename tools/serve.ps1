# MCC 脱敏控制台 · 启动器
# 用法（在本机任意位置）：
#   powershell -File tools\serve.ps1                      # 默认 8098 端口，工作目录=当前目录
#   powershell -File tools\serve.ps1 -Port 8099 -Root "D:\台账"
# 说明：用 Start-Process 独立起进程，不受本终端关闭影响；日志落 logs\ 子目录。
param(
  [int]$Port = 8098,
  [string]$Root = (Get-Location).Path,
  [string]$Book = "",
  [string]$Rules = ""
)
$ErrorActionPreference = "Stop"
# 注意：PowerShell 变量不区分大小写，别再用 $root 当内部名（会把 -Root 参数覆盖掉）
$projDir = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projDir "dist\mcc-mask.jar"
if (-not (Test-Path $jar)) { throw "missing $jar , run tools\build.ps1 first" }
$logDir = Join-Path $projDir "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$out = Join-Path $logDir ("serve-{0}.out.log" -f $Port)
$err = Join-Path $logDir ("serve-{0}.err.log" -f $Port)
$a = @("-jar", $jar, "serve", "--root", $Root, "--port", $Port)
if ($Book)  { $a += @("--book", $Book) }
if ($Rules) { $a += @("--rules", $Rules) }
Start-Process -FilePath "java" -ArgumentList $a -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err
Start-Sleep -Seconds 2
$alive = (netstat -ano | Select-String (":{0} " -f $Port) | Select-String "LISTENING")
if ($alive) {
  Write-Output ("OK  http://127.0.0.1:{0}/   root={1}" -f $Port, $Root)
} else {
  Write-Output ("FAILED, check log: {0}" -f $err)
}
