# Build TCraft Launcher (Release) from source.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\build.ps1
# Output: TCl\bin\TCraft Launcher.exe
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

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

# Refresh the embedded gz snapshots from the freshly built dlls (optional but recommended;
# requires 7-Zip for best compression, falls back to .NET GZipStream automatically).
$regen = Join-Path $PSScriptRoot 'regen_gz.ps1'
if (Test-Path $regen) { & $regen }

# Main executable.
& $msb (Join-Path $root 'TCl\TCl.vbproj') '/t:Rebuild' /p:Configuration=Release /v:m /nologo
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$out = Join-Path $root 'TCl\bin\TCraft Launcher.exe'
Write-Output ''
Write-Output "BUILD OK: $out"
