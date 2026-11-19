param([Parameter(Mandatory=$true)][string]$RuntimeDirectory,[switch]$AppImage)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $RuntimeDirectory).Path
$manifestPath=Join-Path $root 'runtime-manifest.json'
$manifest=Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.schema -ne 1 -or $manifest.helperProtocol -ne 2 -or $manifest.helperVersion -ne '0.9.6' -or $manifest.pawnIoVersion -ne '2.2.0') {
    throw 'Contrato del paquete inesperado.'
}
$names=@($manifest.files | ForEach-Object { $_.file })
if (@($names | Select-Object -Unique).Count -ne $names.Count) { throw 'Rutas duplicadas.' }
foreach ($required in @('mace_native.dll','lhm/helper-runtime.manifest','lhm/Mace.Lhm.Helper.exe','lhm/Mace.Lhm.Helper.dll',
    'lhm/Mace.Lhm.Protocol.dll','lhm/LibreHardwareMonitorLib.dll','lhm/coreclr.dll',
    'lhm/Mace.Lhm.Helper.deps.json','lhm/Mace.Lhm.Helper.runtimeconfig.json',
    'prerequisites/PawnIO_setup.exe','THIRD-PARTY-NOTICES.txt')) {
    if ($names -notcontains $required) { throw "Falta componente obligatorio: $required" }
}
if ([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes((Join-Path $root 'mace_native.dll'))).IndexOf('MACE_LHM_PROTOCOL=2',[StringComparison]::Ordinal) -lt 0) {
    throw 'DLL incompatible con helperProtocol 2.'
}
$dllText=[Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes((Join-Path $root 'mace_native.dll')))
$helperManifestSha=(Get-FileHash -LiteralPath (Join-Path $root 'lhm\helper-runtime.manifest') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($dllText.Contains('MACE_LHM_PROFILE=DEVELOPMENT') -or $manifest.helperManifestSha256 -ne $helperManifestSha -or
    -not $dllText.Contains('MACE_LHM_PROFILE=PRODUCTION;MACE_LHM_MANIFEST_SHA256='+$helperManifestSha)) { throw 'DLL/manifiesto runtime sin vinculo de produccion.' }
$trustedLines=@(Get-Content -LiteralPath (Join-Path $root 'lhm\helper-runtime.manifest'))
if ($trustedLines.Count -lt 2 -or $trustedLines[0] -cne 'MACE-LHM-RUNTIME 2') { throw 'Manifiesto helper no canonico.' }
foreach ($line in $trustedLines | Select-Object -Skip 1) {
    if ($line -cnotmatch '^([0-9a-f]{64})  ([A-Za-z0-9_.-]+)$' -or $Matches[2].Contains('..')) { throw 'Entrada helper no valida.' }
    $trustedHash=$Matches[1]; $trustedFile=$Matches[2]
    if ((Get-FileHash -LiteralPath (Join-Path $root ('lhm\'+$trustedFile)) -Algorithm SHA256).Hash -ne $trustedHash) { throw 'Hash incorrecto: dependencia del helper.' }
}
if (@($names | Where-Object { $_ -match '^mace-app-[^/]+\.jar$' }).Count -ne 1) { throw 'Falta el JAR de la aplicación.' }
foreach ($entry in $manifest.files) {
    $name=[string]$entry.file
    $allowed=$name -eq 'mace_native.dll' -or $name -eq 'THIRD-PARTY-NOTICES.txt' -or
        $name -match '^[^/]+\.jar$' -or $name -match '^lhm/[^/]+\.(dll|json)$' -or
        $name -eq 'lhm/Mace.Lhm.Helper.exe' -or $name -eq 'lhm/helper-runtime.manifest' -or $name -eq 'prerequisites/PawnIO_setup.exe' -or
        $name -match '^third-party/[^/]+\.(txt|md)$'
    if (-not $allowed -or $name.Contains('..') -or $name.Contains('\') -or $name.Contains(':')) { throw "Archivo no permitido en runtime: $name" }
    $path=[IO.Path]::GetFullPath((Join-Path $root $name))
    if (-not $path.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Ruta fuera del paquete.' }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Hash incorrecto: $name" }
}
$actual=@(Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object { $_.FullName -ne $manifestPath } |
    ForEach-Object { $_.FullName.Substring($root.Length+1).Replace('\','/') } |
    Where-Object { -not ($AppImage -and $_ -in @('MACE.cfg','MACE-Preview.cfg','.jpackage.xml')) })
if (@(Compare-Object ($names | Sort-Object) ($actual | Sort-Object)).Count -ne 0) { throw 'Archivos no declarados o ausentes.' }
$policy=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pawnio-2.2.0.json') -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath (Join-Path $root 'prerequisites\PawnIO_setup.exe') -Algorithm SHA256).Hash -ne $policy.sha256) { throw 'PawnIO alterado.' }
$forbiddenJars=@('junit','mockito','byte-buddy','opentest','apiguardian','junit-platform')
foreach ($jar in @($names | Where-Object { $_ -match '\.jar$' })) {
    foreach ($term in $forbiddenJars) { if ($jar -like "$term*") { throw "Dependencia de pruebas: $jar" } }
}
Write-Host ("Runtime verificado: {0} archivos y los cinco componentes obligatorios." -f $names.Count)
