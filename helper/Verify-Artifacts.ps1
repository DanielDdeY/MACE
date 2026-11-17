param([Parameter(Mandatory=$true)][string]$Directory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $Directory).Path
$manifest = Get-Content -LiteralPath (Join-Path $root 'artifact-manifest.json') -Raw | ConvertFrom-Json
if ($manifest.protocol -ne 2 -or $manifest.sdk -ne '10.0.401' -or $manifest.runtime -ne '10.0.12' -or $manifest.lhm -ne '0.9.6') {
    throw 'Versiones del artefacto inesperadas.'
}
foreach ($required in @('Mace.Lhm.Helper.exe','Mace.Lhm.Helper.dll','Mace.Lhm.Protocol.dll','coreclr.dll','LibreHardwareMonitorLib.dll')) {
    if (@($manifest.files | Where-Object {$_.file -eq $required}).Count -ne 1) { throw ('Falta el artefacto requerido ' + $required) }
}
foreach ($entry in $manifest.files) {
    if ([IO.Path]::GetFileName($entry.file) -ne $entry.file) { throw 'Ruta inesperada en el manifiesto.' }
    $path = Join-Path $root $entry.file
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) { throw ('Hash incorrecto: ' + $entry.file) }
}
if (@(Get-ChildItem -LiteralPath $root -File | Where-Object {$_.Name -ne 'artifact-manifest.json'}).Count -ne $manifest.files.Count) {
    throw 'Hay archivos adicionales o faltantes en el paquete.'
}
$runtime = Get-Content -LiteralPath (Join-Path $root 'Mace.Lhm.Helper.runtimeconfig.json') -Raw | ConvertFrom-Json
if (@($runtime.runtimeOptions.includedFrameworks | Where-Object {$_.name -eq 'Microsoft.NETCore.App' -and $_.version -eq '10.0.12'}).Count -ne 1) {
    throw 'El runtime autocontenido esperado no esta presente.'
}
Write-Host ('Artefactos verificados: ' + $manifest.files.Count)
