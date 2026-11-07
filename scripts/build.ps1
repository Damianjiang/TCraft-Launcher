# Build TCraft Launcher (Release) — 源码构建脚本。
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\build.ps1
# 产出单文件 TCl\bin\TCraft Launcher.exe（依赖 DLL 以压缩快照内嵌）。
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# 构建期生成的模块若残留在源码树中（本地构建配置产物），先移除以保证源码构建纯净
$generated = Join-Path $root 'TCl\Modules\InternalSecrets.vb'
if (Test-Path $generated) { Remove-Item $generated -Force }
$internalProps = '/p:InternalBuild=false'

# Locate MSBuild (VS 2022 / Build Tools)
$msb = $null
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (Test-Path $vswhere) {
    $msb = & $vswhere -latest -requires Microsoft.Component.MSBuild -find 'MSBuild\**\Bin\MSBuild.exe' | Select-Object -First 1
}
if (-not $msb) {
    foreach ($p in @(
        "$env:ProgramFiles\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe",
        "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe",
        "$env:ProgramFiles\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe",
        "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe"
    )) {
        if (Test-Path $p) { $msb = $p; break }
    }
}
if (-not $msb) {
    Write-Error 'MSBuild not found. Install Visual Studio 2022 Build Tools with the ".NET desktop development" workload.'
    exit 1
}
Write-Output "MSBuild: $msb"

function Invoke-Build([string]$project) {
    & $msb $project '/t:Restore;Rebuild' /p:Configuration=Release /v:q /nologo
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Dependency projects first, then the main WPF project.
Invoke-Build (Join-Path $root 'TClKit\Shared\TClKit.csproj')
Invoke-Build (Join-Path $root 'TClKit\Wpf\TClKit.Wpf.csproj')
Invoke-Build (Join-Path $root 'TClCS\TClCS.csproj')

# Refresh the embedded gz snapshots from the freshly built dlls (requires 7-Zip for best
# compression, falls back to .NET GZipStream automatically).
$regen = Join-Path $PSScriptRoot 'regen_gz.ps1'
if (Test-Path $regen) { & $regen }

# Main executable.
& $msb (Join-Path $root 'TCl\TCl.vbproj') '/t:Rebuild' /p:Configuration=Release /v:m /nologo $internalProps
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$out = Join-Path $root 'TCl\bin\TCraft Launcher.exe'
Write-Output ''
Write-Output "BUILD OK: $out"
