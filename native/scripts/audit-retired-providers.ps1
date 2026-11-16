param([string]$ReportPath)
$ErrorActionPreference='Stop'
$repoRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $ReportPath) { $ReportPath=Join-Path $repoRoot 'docs\REFERENCIAS-ACEPTADAS-1D.tsv' }
$reportRelative='docs/REFERENCIAS-ACEPTADAS-1D.tsv'
# Construct the query so the auditor does not list its own query as a provider.
$pattern=@(('Win'+'Ring0'),('Rd'+'msr'),('INTEL_'+'MSR'),('Amd'+'RyzenProvider'),('amd_'+'ryzen_provider'),
    ('AMD_'+'RYZEN_SDK'),('Ryzen'+' Master'),('Ryzen'+'Sdk'),('MACE_'+'RYZEN_SDK_DIR'),
    ('ryzen'+'-sdk'),('AD'+'LX')) -join '|'
$regex=[regex]::new($pattern,[Text.RegularExpressions.RegexOptions]::IgnoreCase)
$files=@(& git -C $repoRoot ls-files --cached --others --exclude-standard | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0) { throw 'No se pudo enumerar el repositorio.' }
$rows=[Collections.Generic.List[string]]::new()
$rows.Add("archivo`tlinea`tcolumna`tcoincidencia`tmotivo")
$unexpected=[Collections.Generic.List[string]]::new()
foreach ($relative in $files) {
    if ($relative -eq $reportRelative) { continue } # Output report, never its own input.
    $path=Join-Path $repoRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    $bytes=[IO.File]::ReadAllBytes($path)
    $unicodeBom=$bytes.Length -ge 2 -and (($bytes[0] -eq 255 -and $bytes[1] -eq 254) -or ($bytes[0] -eq 254 -and $bytes[1] -eq 255))
    if (-not $unicodeBom -and $bytes -contains 0) { continue } # Binary assets; all textual extensions are audited.
    $reason=$null
    if ($relative.StartsWith('docs/historico/')) { $reason='Archivo historico conservado, excluido de compilacion y producto' }
    elseif ($relative -match '^helper/(RESULTADOS-[^/]+|COBERTURA-AMD-POTENCIA)\.md$') { $reason='Informe fechado y conservado de migracion/evidencia; no selecciona proveedores' }
    elseif ($relative -eq 'native/include/mace_status.h' -or $relative -eq 'app/src/main/java/com/mace/domain/model/SensorProvider.java') { $reason='ID ABI reservado/obsoleto; sin implementacion activa ni despacho' }
    elseif ($relative -eq 'native/test_main.c') { $reason='Etiqueta diagnostica de ID ABI reservado; no invoca proveedores retirados' }
    elseif ($relative -match '^app/src/test/java/com/mace/infrastructure/nativebridge/(NativeCodesTest|V2RecordsTest)\.java$') { $reason='Compatibilidad numerica/decodificacion ABI historica, sin acceso hardware' }
    elseif ($relative.StartsWith('packaging/third-party/')) { $reason='Texto de licencia de terceros conservado literalmente; no es codigo activo' }
    $lines=[IO.File]::ReadAllLines($path)
    for ($i=0;$i -lt $lines.Length;$i++) {
        foreach ($match in $regex.Matches($lines[$i])) {
            $accepted=$reason
            if ($relative -eq 'native/include/mace_status.h' -or $relative -eq 'native/test_main.c') {
                if ($lines[$i] -notmatch 'Reserved|Obsolete') { $accepted=$null }
            }
            if ($relative -eq 'app/src/main/java/com/mace/domain/model/SensorProvider.java' -and $lines[$i] -notmatch 'Reservado ABI') { $accepted=$null }
            $location="${relative}:$($i+1):$($match.Index+1)"
            if (-not $accepted) { $unexpected.Add($location); $accepted='NO ACEPTADA' }
            $rows.Add("$relative`t$($i+1)`t$($match.Index+1)`t$($match.Value)`t$accepted")
        }
    }
}
[IO.File]::WriteAllLines([IO.Path]::GetFullPath($ReportPath),$rows,[Text.UTF8Encoding]::new($false))
if ($unexpected.Count) { $unexpected | ForEach-Object { Write-Host $_ }; throw "$($unexpected.Count) referencias activas/no justificadas." }
Write-Host ("PASS: {0} coincidencias, cada una con archivo/linea/columna y motivo; cero referencias no justificadas." -f ($rows.Count-1))
Write-Host "Informe: $ReportPath"
