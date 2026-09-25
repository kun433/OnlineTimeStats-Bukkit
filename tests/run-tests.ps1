# 运行不依赖服务器的核心逻辑测试
# 用法： .\run-tests.ps1
$ErrorActionPreference = "Stop"

$tests = Split-Path -Parent $MyInvocation.MyCommand.Path
$base = Split-Path -Parent $tests
$jdk = "C:\Program Files\Java\jdk-11"
$javac = "$jdk\bin\javac.exe"
$java = "$jdk\bin\java.exe"
if (-not (Test-Path $javac)) {
    $javac = "javac"
    $java = "java"
}

$out = "$tests\out\core"
Remove-Item -Recurse -Force "$tests\out\coreclasses" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$tests\out\coreclasses" | Out-Null

$sources = @()
$sources += Get-ChildItem -Recurse "$base\src\com\onlinetimestats\core" -Filter *.java | ForEach-Object { $_.FullName }
$sources += "$tests\CoreTest.java"

& $javac --release 8 -encoding UTF-8 -Xlint:-options -d "$tests\out\coreclasses" $sources
if ($LASTEXITCODE -ne 0) {
    Write-Host "测试编译失败" -ForegroundColor Red
    exit 1
}

& $java "-Dfile.encoding=UTF-8" -classpath "$tests\out\coreclasses" CoreTest
exit $LASTEXITCODE
