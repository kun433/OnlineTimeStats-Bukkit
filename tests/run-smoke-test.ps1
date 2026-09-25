# 在真实的 Paper 1.12.2 测试服上跑一遍冒烟测试（需要 node 与 JRE 8）
# 用法： .\run-smoke-test.ps1
$tests = Split-Path -Parent $MyInvocation.MyCommand.Path
$base = Split-Path -Parent $tests
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) {
    Write-Host "找不到 node，无法运行冒烟测试" -ForegroundColor Red
    exit 1
}
& $node "$tests\bot\smoke-test.js" "$base\.testserver\1122" "C:\Program Files\Java\jre1.8.0_503\bin\java.exe" "$base\dist\OnlineTimeStats-1.0.0.jar" "$tests\out"
exit $LASTEXITCODE
