<#
.SYNOPSIS
    把模板里的包名、项目名替换成你自己的。

.DESCRIPTION
    模板默认使用 com.touki / service-template 这套坐标。
    直接在上面写业务代码，将来想改就得满仓库找引用，很容易漏。
    这个脚本一次性完成：移动源码目录、替换 package/import、更新 pom 与前端包名。

    执行前建议先 git commit，跑完用 git diff 确认改动范围符合预期。

.PARAMETER Package
    新的 Java 包名，至少两段，例如 com.yourname.tools。

.PARAMETER ProjectName
    新的项目名（artifactId），不填则取包名最后一段。
    只允许小写字母、数字和连字符。

.EXAMPLE
    pwsh scripts/rename-project.ps1 -Package com.yourname.tools

.EXAMPLE
    # 先看看会改哪些文件，不实际动手
    pwsh scripts/rename-project.ps1 -Package com.yourname.tools -WhatIf
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true, HelpMessage = '新的 Java 包名，例如 com.yourname.tools')]
    [ValidatePattern('^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')]
    [string]$Package,

    [Parameter(HelpMessage = '新的项目名（artifactId），不填则取包名最后一段')]
    [ValidatePattern('^[a-z][a-z0-9-]*$')]
    [string]$ProjectName
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 模板中写死的旧坐标。它们与当前仓库内容一一对应，不要改这里。
# ---------------------------------------------------------------------------
$OldPackage    = 'com.touki'
$OldGroupId    = 'com.touki'
$OldArtifactId = 'service-template'

# 脚本位于 scripts/ 下，仓库根目录就是它的上一级
$RepoRoot = Split-Path -Parent $PSScriptRoot

# 项目名默认取包名的最后一段：com.yourname.tools -> tools
if (-not $ProjectName) {
    $ProjectName = ($Package -split '\.')[-1]
}

# groupId 是包名去掉最后一段：com.yourname.tools -> com.yourname
$segments   = $Package -split '\.'
$NewGroupId = ($segments[0..($segments.Length - 2)]) -join '.'

Write-Host ''
Write-Host '即将执行的替换：' -ForegroundColor Cyan
Write-Host ("  Java 包名 : {0}  ->  {1}" -f $OldPackage, $Package)
Write-Host ("  groupId   : {0}  ->  {1}" -f $OldGroupId, $NewGroupId)
Write-Host ("  artifactId: {0}  ->  {1}" -f $OldArtifactId, $ProjectName)
Write-Host ''

# ---------------------------------------------------------------------------
# 1. 移动源码目录
#
# Java 的包名必须和目录结构一致，所以改包名等于改目录。
# 只移动叶子目录（com/example/template），父目录 com/example 留给下面的替换步骤判断，
# 因为新包名的父目录可能完全不同（比如 com/yourname）。
# ---------------------------------------------------------------------------
$SrcJavaRoot = Join-Path $RepoRoot 'backend/src/main/java'
$OldDir      = Join-Path $SrcJavaRoot ($OldPackage -replace '\.', [IO.Path]::DirectorySeparatorChar)
$NewDir      = Join-Path $SrcJavaRoot ($Package -replace '\.', [IO.Path]::DirectorySeparatorChar)

if (-not (Test-Path $OldDir)) {
    Write-Warning "找不到源码目录：$OldDir"
    Write-Warning '如果已经改过包名，这个脚本不需要再执行。'
    return
}

if ($OldDir -ne $NewDir) {
    if ($PSCmdlet.ShouldProcess($OldDir, "移动到 $NewDir")) {
        $NewParent = Split-Path -Parent $NewDir
        if (-not (Test-Path $NewParent)) {
            New-Item -ItemType Directory -Force -Path $NewParent | Out-Null
        }
        Move-Item -Path $OldDir -Destination $NewDir
        Write-Host "[OK] 目录已移动" -ForegroundColor Green
    }
}

# ---------------------------------------------------------------------------
# 2. 替换源码里的 package / import
#
# 用纯字符串替换而不是正则：包名里的点号在正则里是通配符，
# 稍不注意就会把 comXexample 这种也匹配上。
# 文件统一用 UTF-8 无 BOM 写回，避免 Windows 下出现中文注释乱码。
# ---------------------------------------------------------------------------
$SourceFiles = Get-ChildItem -Path (Join-Path $RepoRoot 'backend/src') -Recurse -File |
    Where-Object { $_.Extension -in '.java', '.xml', '.yml', '.yaml' }

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$changed   = 0

foreach ($file in $SourceFiles) {
    $text = [System.IO.File]::ReadAllText($file.FullName)
    if (-not $text.Contains($OldPackage)) {
        continue
    }

    if ($PSCmdlet.ShouldProcess($file.FullName, "替换 $OldPackage -> $Package")) {
        $newText = $text.Replace($OldPackage, $Package)
        [System.IO.File]::WriteAllText($file.FullName, $newText, $Utf8NoBom)
    }
    $changed++
}

Write-Host ("[OK] 已处理 {0} 个文件" -f $changed) -ForegroundColor Green

# ---------------------------------------------------------------------------
# 3. 更新 pom.xml 坐标
#
# 只替换项目自己的 groupId（com.touki），不会碰到 parent 的 org.springframework.boot。
# ---------------------------------------------------------------------------
$PomPath = Join-Path $RepoRoot 'backend/pom.xml'
if (Test-Path $PomPath) {
    $pom = [System.IO.File]::ReadAllText($PomPath)
    $pom = $pom.Replace("<groupId>$OldGroupId</groupId>", "<groupId>$NewGroupId</groupId>")
    $pom = $pom.Replace("<artifactId>$OldArtifactId</artifactId>", "<artifactId>$ProjectName</artifactId>")
    $pom = $pom.Replace("<name>$OldArtifactId</name>", "<name>$ProjectName</name>")

    if ($PSCmdlet.ShouldProcess($PomPath, '更新项目坐标')) {
        [System.IO.File]::WriteAllText($PomPath, $pom, $Utf8NoBom)
        Write-Host '[OK] pom.xml 已更新' -ForegroundColor Green
    }
}

# ---------------------------------------------------------------------------
# 4. 更新前端包名
#
# 前端的 name 只影响 npm 包管理，不改也能跑，改了更整洁。
# ---------------------------------------------------------------------------
$PkgJsonPath = Join-Path $RepoRoot 'frontend/package.json'
if (Test-Path $PkgJsonPath) {
    $json = [System.IO.File]::ReadAllText($PkgJsonPath)
    $json = $json.Replace('"name": "service-template-frontend"', "`"name`": `"$ProjectName-frontend`"")

    if ($PSCmdlet.ShouldProcess($PkgJsonPath, '更新前端包名')) {
        [System.IO.File]::WriteAllText($PkgJsonPath, $json, $Utf8NoBom)
        Write-Host '[OK] package.json 已更新' -ForegroundColor Green
    }
}

Write-Host ''
Write-Host '完成。接下来还需要手动处理两件事：' -ForegroundColor Yellow
Write-Host '  1. mvn clean —— target 里还留着旧包名的 class，不清会报找不到类'
Write-Host '  2. 检查 backend/src/main/resources/application.yml 里 app.jwt.secret，换成你自己的随机串'
