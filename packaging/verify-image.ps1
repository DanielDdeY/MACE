param([Parameter(Mandatory=$true)][string]$ImageDirectory,[string]$ReportPath)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ImageDirectory).Path
& (Join-Path $PSScriptRoot 'verify-runtime.ps1') -RuntimeDirectory (Join-Path $root 'app') -AppImage
$policy=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'compatibility-references.json') -Raw | ConvertFrom-Json
if ($policy.schema -ne 1) { throw 'Politica de compatibilidad desconocida.' }
$needles=@(('Win'+'Ring0'),('Rd'+'msr'),('amdad'+'lx64.dll'),('RyzenMaster'+'MonitoringSDK'),
    ('Ad'+'lx_Init'),('Ad'+'lx_PollAdapter'),('Amd'+'RyzenProvider'))
$retiredFilePattern=@(('Win'+'Ring0'),('amdad'+'lx'),('Ryzen'+'Master')) -join '|'
$accepted=[Collections.Generic.List[object]]::new()
$files=@(Get-ChildItem -LiteralPath $root -Recurse -File)
foreach ($file in $files) {
    $relative=$file.FullName.Substring($root.Length+1).Replace('\','/')
    if ($relative -match '(^|/)(artifacts|historico|tests|sources)/|\.(ps1|bat|pdb|lib|obj|h|c|java)$' -or
        $file.Name -match 'test_mace|lhm_fake_helper|Mace\.Lhm\.Tests') { throw "Archivo de desarrollo en imagen: $relative" }
    if ($file.Name -match $retiredFilePattern -or $file.Name -in @('Platform.dll','Device.dll')) { throw "Binario SDK retirado en imagen: $relative" }
    if ($relative -eq $policy.file -and (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne $policy.sha256) {
        throw 'Cambio la dependencia de compatibilidad; se requiere nueva revision y aprobacion.'
    }
    if ($file.Extension -notin @('.dll','.exe')) { continue }
    $bytes=[IO.File]::ReadAllBytes($file.FullName)
    $text=[Text.Encoding]::ASCII.GetString($bytes)
    foreach ($needle in $needles) {
        foreach ($encoding in @('ASCII','UTF16LE')) {
            $query=$needle
            if ($encoding -eq 'UTF16LE') { $query=[Text.Encoding]::ASCII.GetString([Text.Encoding]::Unicode.GetBytes($needle)) }
            $offset=0
            while (($offset=$text.IndexOf($query,$offset,[StringComparison]::OrdinalIgnoreCase)) -ge 0) {
                if ($relative -ne $policy.file -or $needle -ne $policy.needle -or $encoding -ne 'ASCII') {
                    throw "Referencia binaria retirada no aceptada: $relative / $needle / offset $offset"
                }
                $symbol=@($policy.metadata | Where-Object { $_.offset -eq $offset })
                if ($symbol.Count -ne 1) { throw 'Referencia fuera de los offsets de metadatos aprobados.' }
                $accepted.Add([pscustomobject]@{file=$relative;offset=$offset;encoding=$encoding;token=$needle;symbol=$symbol[0].name;kind=$symbol[0].kind;reason=$policy.reason})
                $offset+=$query.Length
            }
        }
    }
}
if ($accepted.Count -ne $policy.expectedMatches) { throw 'La excepcion binaria no coincide con los metadatos revisados.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarCount=0
foreach ($jar in @($files | Where-Object { $_.Extension -eq '.jar' })) {
    $archive=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '(^|/)(src/test|historico|test-classes)/|pre1D-|\.java$') {
                throw "Contenido de desarrollo en JAR: $($jar.Name) / $($entry.FullName)"
            }
        }
    } finally { $archive.Dispose() }
    $jarCount++
}
$report=[ordered]@{schema=1;files=$files.Count;bytes=($files | Measure-Object Length -Sum).Sum;
    jars=$jarCount;acceptedBinaryReferences=@($accepted.ToArray());unexpectedReferences=0;forbiddenFiles=0}
if ($ReportPath) { $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $ReportPath -Encoding UTF8 }
$accepted | ForEach-Object { Write-Host ("ACEPTADA: {0} offset={1} {2}: {3}" -f $_.file,$_.offset,$_.token,$_.reason) }
Write-Host ("PASS: imagen {0} archivos, {1} JAR; sin archivos de desarrollo ni cargadores retirados; {2} referencias de compatibilidad aprobadas." -f $files.Count,$jarCount,$accepted.Count)
