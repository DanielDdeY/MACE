param(
    [Parameter(Mandatory=$true)][string]$NativeDll,
    [Parameter(Mandatory=$true)][string]$PawnIoInstaller,
    [string]$HelperDirectory,
    [string]$JavaExe
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$packaging=Join-Path $repoRoot 'packaging'
$testRoot=Join-Path $repoRoot ('native\build\1d-4\payload-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$jar=Join-Path $testRoot 'mace-app-payload-test.jar'
$classes=Join-Path $repoRoot 'app\target\classes'
if (-not (Test-Path -LiteralPath $classes)) { throw 'Compila primero las clases Java.' }
if (-not $JavaExe) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $JavaExe=Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $javaCommand=Get-Command java.exe -ErrorAction Stop
        $JavaExe=$javaCommand.Source
    }
}
# Windows PowerShell/.NET Framework can otherwise write backslashes in ZIP paths,
# which the Java class loader does not accept as package separators.
$classes=(Resolve-Path -LiteralPath $classes).Path
$jarStream=[IO.File]::Create($jar)
$archive=[IO.Compression.ZipArchive]::new($jarStream,[IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -LiteralPath $classes -Recurse -File | ForEach-Object {
        $entryName=$_.FullName.Substring($classes.Length+1).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$_.FullName,$entryName,[IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $archive.Dispose(); $jarStream.Dispose() }
$sha=(Get-FileHash -LiteralPath $NativeDll -Algorithm SHA256).Hash
function Expect-Rejection([scriptblock]$Action,[string]$Reason) {
    $rejected=$false
    try { & $Action } catch {
        if ($_.Exception.Message -notmatch $Reason) { throw }
        $rejected=$true
    }
    if (-not $rejected) { throw 'El caso negativo fue aceptado.' }
}
$good=Join-Path $testRoot 'good'
New-Item -ItemType Directory -Path $good | Out-Null
& (Join-Path $packaging 'stage-runtime.ps1') -StageDirectory $good -AppJar $jar -NativeDll $NativeDll -NativeSha256 $sha -HelperDirectory $HelperDirectory -PawnIoInstaller $PawnIoInstaller
& (Join-Path $packaging 'verify-native-dll.ps1') -DllPath $NativeDll -ExpectedSha256 $sha -AppJar $jar -JavaExe $JavaExe -ReportPath (Join-Path $testRoot 'dll-verification.txt') | Out-Host
$copiedPawn=Join-Path $good 'prerequisites\PawnIO_setup.exe'
if ((Get-FileHash -LiteralPath $copiedPawn -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $PawnIoInstaller -Algorithm SHA256).Hash) { throw 'Se alteró el instalador.' }
$bad=Join-Path $testRoot 'bad'
New-Item -ItemType Directory -Path $bad | Out-Null
Expect-Rejection { & (Join-Path $packaging 'stage-runtime.ps1') -StageDirectory $bad -AppJar $jar -NativeDll $NativeDll -NativeSha256 $sha -HelperDirectory $HelperDirectory -PawnIoInstaller $jar } 'Hash de PawnIO'
if (Test-Path -LiteralPath (Join-Path $bad 'lhm')) { throw 'Se escribió payload antes de rechazar PawnIO.' }
$fake=Join-Path $testRoot 'obsolete.dll'
[IO.File]::WriteAllBytes($fake,[Text.Encoding]::ASCII.GetBytes(('Win'+'Ring0x64.dll')))
$fakeSha=(Get-FileHash -LiteralPath $fake -Algorithm SHA256).Hash
Expect-Rejection { & (Join-Path $packaging 'stage-runtime.ps1') -StageDirectory $bad -AppJar $jar -NativeDll $fake -NativeSha256 $fakeSha -HelperDirectory $HelperDirectory -PawnIoInstaller $PawnIoInstaller } 'proveedor retirado'
$extra=Join-Path $good 'internal.log'
[IO.File]::WriteAllText($extra,'development-only')
Expect-Rejection { & (Join-Path $packaging 'verify-runtime.ps1') -RuntimeDirectory $good } 'Archivos no declarados'
Remove-Item -LiteralPath $extra # A single file created by this test; never recursive.
$module=Join-Path $good 'lhm\Mace.Lhm.Helper.dll'
[IO.File]::AppendAllText($module,'tampered')
Expect-Rejection { & (Join-Path $packaging 'verify-runtime.ps1') -RuntimeDirectory $good } 'Hash incorrecto'
Write-Host 'PASS: 6 payload checks (complete runtime with intact installer, real Java FFM/ABI verifier, wrong installer, obsolete loader, extra file, tampered helper).'
Write-Host 'No se ejecutó PawnIO ni jpackage. La imagen final y el instalador MSI/EXE siguen NO VERIFICADO hasta Paso 2.'
