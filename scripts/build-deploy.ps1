<#
.SYNOPSIS
    本地构建部署产物（jar + dist），准备上传到服务器。

.DESCRIPTION
    服务器只有 2G 内存，跑不了 mvn package（Maven + javac 的内存峰值会被 OOM killer
    杀掉，表现为编译到一半进程消失）。所以构建必须在本地做，服务器只负责运行。

    这个脚本做三件事：
      1. mvn package  → deploy/backend/app.jar
      2. npm run build → deploy/www/
      3. 打印产物大小，便于判断传输耗时

.PARAMETER SkipBackend
    只构建前端

.PARAMETER SkipFrontend
    只构建后端

.EXAMPLE
    pwsh scripts/build-deploy.ps1

.EXAMPLE
    # 只改了前端
    pwsh scripts/build-deploy.ps1 -SkipBackend
#>
[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $PSScriptRoot
$backend  = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$deploy   = Join-Path $root 'deploy'

# ---------------------------------------------------------------------------
# 检查 JDK：必须是 21，否则 Boot 4 编译不过
# ---------------------------------------------------------------------------
if (-not $SkipBackend) {
    $javaHome = $env:JAVA_HOME
    if (-not $javaHome -or -not (Test-Path $javaHome)) {
        throw '未设置 JAVA_HOME。本项目要求 JDK 21。'
    }
    $version = & (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1
    Write-Host "JDK: $version"
    if ($version -notmatch '"21') {
        Write-Warning "当前 JAVA_HOME 不是 JDK 21，Boot 4 可能编译失败：$javaHome"
    }

    Write-Host ''
    Write-Host '=== 构建后端 ===' -ForegroundColor Cyan
    Push-Location $backend
    try {
        # 跳过测试：这一步只关心能打出可运行的 jar
        mvn -B -DskipTests clean package
        if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }

        # 排除 Spring Boot 插件生成的 .original 备份文件
        $jar = Get-ChildItem 'target' -Filter '*.jar' |
            Where-Object { $_.Name -notmatch '\.original$' } |
            Select-Object -First 1
        if (-not $jar) { throw '没找到构建出的 jar' }

        Copy-Item $jar.FullName (Join-Path $deploy 'backend\app.jar') -Force
        Write-Host "[OK] app.jar  $([math]::Round($jar.Length / 1MB, 1)) MB" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------------------
# 前端
# ---------------------------------------------------------------------------
if (-not $SkipFrontend) {
    Write-Host ''
    Write-Host '=== 构建前端 ===' -ForegroundColor Cyan
    Push-Location $frontend
    try {
        # npm run build 里已经包含 vue-tsc --noEmit，类型错误会在这里暴露
        npm run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }

        $wwwDir = Join-Path $deploy 'www'
        if (Test-Path $wwwDir) { Get-ChildItem $wwwDir | Remove-Item -Recurse -Force }
        Copy-Item 'dist\*' $wwwDir -Recurse -Force

        $size = (Get-ChildItem $wwwDir -Recurse -File | Measure-Object Length -Sum).Sum
        Write-Host "[OK] dist  $([math]::Round($size / 1KB, 0)) KB" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host '=== 产物 ===' -ForegroundColor Cyan
Get-ChildItem $deploy -Recurse -File |
    Where-Object { $_.Extension -in '.jar', '.html', '.js', '.css' } |
    Measure-Object Length -Sum |
    ForEach-Object { Write-Host "  合计 $([math]::Round($_.Sum / 1MB, 1)) MB（含 jar 与前端产物）" }

Write-Host ''
Write-Host '下一步：把整个 deploy 目录传到服务器，命令见 docs/deploy.md 第四节' -ForegroundColor Yellow
