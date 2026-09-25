# 编译 + 打包 OnlineTimeStats（Paper/Spigot 1.8 ~ 1.12.2，Java 8 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
# 依赖：lib/paper-api-1.12.2.jar（仅用于编译，不会打进 jar）
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = "C:\Program Files\Java\jdk-11"
$javac = "$jdk\bin\javac.exe"
$jar = "$jdk\bin\jar.exe"
if (-not (Test-Path $javac)) {
    # 退回到 PATH 里的 javac（需要支持 --release 8）
    $javac = "javac"
    $jar = "jar"
}

$api = "$base\lib\paper-api-1.12.2.jar"
$chat = "$base\lib\bungeecord-chat.jar"
$classes = "$base\build\classes"
$stage = "$base\build\stage"
$version = "1.0.0"

if (-not (Test-Path $api)) {
    Write-Host "缺少依赖: $api" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $chat)) {
    Write-Host "缺少依赖: $chat" -ForegroundColor Red
    exit 1
}

Write-Host "== 清理 =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host "== 编译 =="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 8 -encoding UTF-8 -Xlint:-options -classpath "$api;$chat" -d $classes $src 2>&1 |
    Out-File -Encoding UTF8 "$base\build\javac.log"
if ($LASTEXITCODE -ne 0) {
    Get-Content "$base\build\javac.log" -Encoding UTF8 | Select-Object -Last 40
    Write-Host "编译失败（exit=$LASTEXITCODE，完整日志见 build\javac.log）" -ForegroundColor Red
    exit 1
}
Write-Host "== 打包 =="
Copy-Item -Recurse "$classes\*" $stage -Force
Copy-Item "$base\resources\plugin.yml" $stage -Force
Copy-Item "$base\resources\config.yml" $stage -Force
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
$outJar = "$base\dist\OnlineTimeStats-$version.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .
if ($LASTEXITCODE -ne 0) {
    Write-Host "打包失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
