# Regenerate TCl\Resources\gz\*.gz from the CURRENT Release dlls.
# Called by build_check.ps1 / build_package.bat AFTER dependency projects are built, BEFORE TCl.vbproj compiles.
# Keeping this in the build pipeline is mandatory: otherwise stale gz blobs (old TClCS etc.)
# silently fatten the single-file exe (r66 found a 462 KB stale TClCS.gz).
# compression switched from .NET GZipStream (zlib level ~6) to 7-Zip max deflate
# (-mx9 -mfb=258 -mpass=15). Output is still a standard gzip stream (deflate), so the
# runtime GZipStream decompresses it unchanged; measured saving ~50 KB across all blobs.
$root = Split-Path $PSScriptRoot -Parent  # parent of scripts/ = project root
$gzDir = Join-Path $root 'TCl\Resources\gz'
New-Item -ItemType Directory -Force -Path $gzDir | Out-Null

$sevenZip = 'C:\Program Files\7-Zip\7z.exe'
$use7z = Test-Path $sevenZip
if (-not $use7z) { Write-Output '7z.exe not found, falling back to GZipStream' }

$bin = Join-Path $root 'TClCS\bin'
$kit = Join-Path $root 'TClKit\Shared\bin'
$kitw = Join-Path $root 'TClKit\Wpf\bin'
$res = Join-Path $root 'TCl\Resources'
$map = @(
    @('TClCS',                 (Join-Path $bin 'TClCS.dll')),
    @('TClKit',                (Join-Path $kit 'TClKit.dll')),
    @('TClKit.Wpf',            (Join-Path $kitw 'TClKit.Wpf.dll')),
    @('Microsoft.Win32.Registry', (Join-Path $bin 'Microsoft.Win32.Registry.dll')),
    @('System.Net.Http.WinHttpHandler', (Join-Path $bin 'System.Net.Http.WinHttpHandler.dll')),
    # WinHttpHandler 7.0（net462 目标）的依赖闭包：单文件部署时旁边没有零散 dll 兜底，
    # 缺任何一个都会在首次发请求时报「未能加载文件或程序集 System.Buffers ... 系统找不到指定的文件」
    @('System.Buffers',                         (Join-Path $bin 'System.Buffers.dll')),
    @('System.Memory',                          (Join-Path $bin 'System.Memory.dll')),
    @('System.Numerics.Vectors',                (Join-Path $bin 'System.Numerics.Vectors.dll')),
    @('System.Runtime.CompilerServices.Unsafe', (Join-Path $bin 'System.Runtime.CompilerServices.Unsafe.dll')),
    @('Newtonsoft.Json',       (Join-Path $res 'Newtonsoft.Json.dll')),
    @('libwebp64',             (Join-Path $res 'libwebp64.dll')),
    @('Imazen.WebP',           (Join-Path $res 'Imazen.WebP.dll')),
    @('ForgeInstaller',        (Join-Path $res 'forge-installer.jar')),
    @('Transformer',           (Join-Path $res 'transformer.jar'))
)
$totR = 0; $totG = 0
# host safety hooks block Remove-Item (fail-closed trash). Archive stale blobs instead of deleting.
$oldDir = Join-Path $gzDir '.old'
Add-Type -Language CSharp -TypeDefinition @'
public class TClGzipGen {
  public static void GzipFile(string src, string dst) {
    using (var inFs = System.IO.File.OpenRead(src))
    using (var outMs = new System.IO.FileStream(dst, System.IO.FileMode.Create))
    using (var gz = new System.IO.Compression.GZipStream(outMs, System.IO.Compression.CompressionMode.Compress, true)) {
      inFs.CopyTo(gz);
    }
  }
}
'@
foreach ($m in $map) {
    $n = $m[0]; $src = $m[1]
    if (-not (Test-Path $src)) { Write-Output ("MISSING " + $n + " : " + $src); continue }
    $dst = Join-Path $gzDir ($n + '.gz')
    if ($use7z) {
        # move stale blob away first: 7z 'a' would otherwise try to UPDATE the existing gzip archive and fail
        if (Test-Path $dst) {
            New-Item -ItemType Directory -Force -Path $oldDir | Out-Null
            Move-Item -LiteralPath $dst -Destination (Join-Path $oldDir ($n + '.gz')) -Force
        }
        # writes a standard gzip stream; inner entry name is irrelevant (we read the whole blob)
        & $sevenZip a -tgzip -mx9 -mfb=258 -mpass=15 $dst $src 2>$null | Out-Null
        if (-not (Test-Path $dst) -or (Get-Item $dst).Length -eq 0) { Write-Output ("7Z FAILED for " + $n) }
    } else {
        [TClGzipGen]::GzipFile($src, $dst)
    }
    $r = (Get-Item $src).Length
    $g = (Get-Item $dst).Length
    $totR += $r; $totG += $g
    Write-Output ('{0,-24} {1,8} -> {2,8} bytes  (save {3})' -f $n, $r, $g, ($r-$g))
}
# LangTables.txt.gz: the embedded translation table. LangTables.txt is the editable source;
# without this step any txt edit silently ships stale translations in the built exe.
$lt = Join-Path $res 'LangTables.txt'
$ltg = Join-Path $res 'LangTables.txt.gz'
if (Test-Path $lt) {
    if ($use7z) {
        if (Test-Path $ltg) {
            New-Item -ItemType Directory -Force -Path $oldDir | Out-Null
            Move-Item -LiteralPath $ltg -Destination (Join-Path $oldDir 'LangTables.txt.gz') -Force
        }
        & $sevenZip a -tgzip -mx9 -mfb=258 -mpass=15 $ltg $lt 2>$null | Out-Null
        if (-not (Test-Path $ltg) -or (Get-Item $ltg).Length -eq 0) { Write-Output '7Z FAILED for LangTables.txt' }
    } else {
        [TClGzipGen]::GzipFile($lt, $ltg)
    }
    Write-Output ('{0,-24} {1,8} -> {2,8} bytes' -f 'LangTables', (Get-Item $lt).Length, (Get-Item $ltg).Length)
}
Write-Output '-----'
Write-Output ('raw: {0}   gz: {1}   save: {2}   (7z={3})' -f $totR, $totG, ($totR-$totG), $use7z)
# delete orphan LegacyFix.gz (now downloaded on demand; not referenced by resx)
$lf = Join-Path $gzDir 'LegacyFix.gz'
$refd = Select-String -Path (Join-Path $root 'TCl\My Project\Resources.resx') -Pattern 'LegacyFix' -Quiet
if ((Test-Path $lf) -and -not $refd) {
    New-Item -ItemType Directory -Force -Path $oldDir | Out-Null
    Move-Item -LiteralPath $lf -Destination (Join-Path $oldDir 'LegacyFix.gz') -Force
    Write-Output 'archived orphan LegacyFix.gz (not referenced by resx)'
} else {
    Write-Output ('LegacyFix.gz kept (referenced=' + $refd + ')')
}
