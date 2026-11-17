param(
    [Parameter(Mandatory=$true)][string]$StageDirectory,
    [Parameter(Mandatory=$true)][string]$AppJar,
    [Parameter(Mandatory=$true)][string]$NativeDll,
    [Parameter(Mandatory=$true)][string]$NativeSha256,
    [string]$HelperDirectory=$env:MACE_HELPER_PUBLISH,
    [string]$PawnIoInstaller=$env:MACE_PAWNIO_INSTALLER,
    [string]$SigningScript=$env:MACE_SIGNING_SCRIPT
)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent $PSScriptRoot
$stage=[IO.Path]::GetFullPath($StageDirectory)
# This script never deletes an existing directory. jpackage prepares the JAR stage.
if (-not (Test-Path -LiteralPath $stage -PathType Container)) { throw 'La carpeta de staging debe existir.' }
$jar=(Resolve-Path -LiteralPath $AppJar).Path
$dll=(Resolve-Path -LiteralPath $NativeDll).Path
if ((Get-FileHash -LiteralPath $dll -Algorithm SHA256).Hash -ne $NativeSha256) { throw 'Hash de DLL nativa incorrecto.' }
if ($SigningScript -and (Get-AuthenticodeSignature -LiteralPath $dll).Status -ne 'Valid') {
    throw 'Firma la DLL con package-native.ps1 antes de verificar y fijar su hash.'
}
$binary=[IO.File]::ReadAllBytes($dll)
$binaryText=[Text.Encoding]::ASCII.GetString($binary)+[Text.Encoding]::Unicode.GetString($binary)
# Exact obsolete loader names, not reserved ABI identifiers.
foreach ($needle in @(('Win'+'Ring0x64.dll'),('amdad'+'lx64.dll'),('RyzenMaster'+'MonitoringSDK'))) {
    if ($binaryText.IndexOf($needle,[StringComparison]::OrdinalIgnoreCase) -ge 0) { throw 'La DLL contiene un cargador de proveedor retirado.' }
}
if ([Text.Encoding]::ASCII.GetString($binary).IndexOf('MACE_LHM_PROTOCOL=2',[StringComparison]::Ordinal) -lt 0) {
    throw 'La DLL no declara el protocolo privado 2 requerido por el helper; recompila ambos componentes.'
}
if (-not $HelperDirectory) {
    $latest=Join-Path $repoRoot 'helper\artifacts\latest.txt'
    if (-not (Test-Path -LiteralPath $latest)) { throw 'Publica el helper con helper\Build-Helper.ps1 o define MACE_HELPER_PUBLISH.' }
    $HelperDirectory=(Get-Content -LiteralPath $latest -Raw).Trim()
}
$helper=(Resolve-Path -LiteralPath $HelperDirectory).Path
& (Join-Path $repoRoot 'helper\Verify-Artifacts.ps1') -Directory $helper | Out-Host
$sourceManifest=Get-Content -LiteralPath (Join-Path $helper 'artifact-manifest.json') -Raw | ConvertFrom-Json
if (-not $PawnIoInstaller) { throw 'Define MACE_PAWNIO_INSTALLER con el instalador oficial intacto; este script no lo descarga ni ejecuta.' }
$pawn=(Resolve-Path -LiteralPath $PawnIoInstaller).Path
$pawnPolicy=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pawnio-2.2.0.json') -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $pawn -Algorithm SHA256).Hash -ne $pawnPolicy.sha256) { throw 'Hash de PawnIO incorrecto; se exige el instalador oficial intacto 2.2.0.' }
if ((Get-AuthenticodeSignature -LiteralPath $pawn).Status -ne 'Valid') { throw 'Firma Authenticode de PawnIO no válida.' }
$notices=Join-Path $PSScriptRoot 'THIRD-PARTY-NOTICES.txt'
if (-not (Test-Path -LiteralPath $notices)) { throw 'Faltan los avisos de terceros.' }
$entries=@($sourceManifest.files | Where-Object {
    $_.file -match '\.(dll|json)$' -or $_.file -eq 'Mace.Lhm.Helper.exe'
})
# Only the self-contained helper's runtime files are selected. No symbols, tests or dump utility.
$helperStage=Join-Path $stage 'lhm'
$prerequisites=Join-Path $stage 'prerequisites'
$legalStage=Join-Path $stage 'third-party'
foreach ($directory in @($helperStage,$prerequisites,$legalStage)) {
    if (Test-Path -LiteralPath $directory) { throw "Ya existe $directory; vuelve a preparar el staging limpio." }
}
$legalSources=Join-Path $PSScriptRoot 'third-party'
if (-not (Test-Path -LiteralPath $legalSources -PathType Container)) { throw 'Faltan los textos de licencia de terceros.' }
New-Item -ItemType Directory -Path $helperStage,$prerequisites,$legalStage | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage (Split-Path -Leaf $jar)) -Force
Copy-Item -LiteralPath $dll -Destination (Join-Path $stage 'mace_native.dll') -Force
foreach ($entry in $entries) {
    Copy-Item -LiteralPath (Join-Path $helper $entry.file) -Destination (Join-Path $helperStage $entry.file)
}
Copy-Item -LiteralPath $pawn -Destination (Join-Path $prerequisites 'PawnIO_setup.exe')
Copy-Item -LiteralPath $notices -Destination (Join-Path $stage 'THIRD-PARTY-NOTICES.txt')
Get-ChildItem -LiteralPath $legalSources -File | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $legalStage }
if ($SigningScript) {
    # Sign only MACE-owned files. NEVER alter the signed PawnIO installer or third-party assemblies.
    foreach ($relative in @('lhm\Mace.Lhm.Helper.exe','lhm\Mace.Lhm.Helper.dll','lhm\Mace.Lhm.Protocol.dll')) {
        $owned=Join-Path $stage $relative
        & $SigningScript -FilePath $owned
        if (-not $?) { throw "Falló la firma: $relative" }
        if ((Get-AuthenticodeSignature -LiteralPath $owned).Status -ne 'Valid') { throw "Firma no válida: $relative" }
    }
} else { Write-Warning 'Firma MACE omitida: certificado pendiente. PawnIO conserva su firma original.' }
$files=@(Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName | ForEach-Object {
    [ordered]@{file=$_.FullName.Substring($stage.Length+1).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
[ordered]@{schema=1;helperProtocol=2;helperVersion=$sourceManifest.lhm;pawnIoVersion=$pawnPolicy.version;files=$files} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $stage 'runtime-manifest.json') -Encoding UTF8
& (Join-Path $PSScriptRoot 'verify-runtime.ps1') -RuntimeDirectory $stage
Write-Host 'Staging completo: aplicación, DLL, helper, PawnIO intacto y avisos. No se ejecutó el instalador.'
