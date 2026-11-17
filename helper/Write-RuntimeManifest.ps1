param([Parameter(Mandatory=$true)][string]$Directory,[switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $Directory).Path
$source=Get-Content -LiteralPath (Join-Path $root 'artifact-manifest.json') -Raw | ConvertFrom-Json
if ($source.protocol -ne 2) { throw 'Protocolo del helper inesperado.' }
$entries=@($source.files | Where-Object { $_.file -match '\.(dll|json)$' -or $_.file -eq 'Mace.Lhm.Helper.exe' } | Sort-Object file)
if (-not $entries.Count -or $entries.Count -gt 512) { throw 'Cantidad de dependencias inesperada.' }
$lines=@('MACE-LHM-RUNTIME 2')
$seen=@{}
foreach ($entry in $entries) {
    $name=[string]$entry.file
    if ($name -notmatch '^[A-Za-z0-9_-][A-Za-z0-9._-]{0,125}[A-Za-z0-9_-]$' -or $name.Contains('..') -or $seen.ContainsKey($name) -or
        ($name -split '\.')[0] -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$') { throw 'Nombre de dependencia no permitido.' }
    $seen[$name]=$true
    $hash=(Get-FileHash -LiteralPath (Join-Path $root $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $entry.sha256) { throw 'Dependencia modificada tras publicar/firmar.' }
    $lines+=($hash+'  '+$name)
}
$bytes=[Text.Encoding]::ASCII.GetBytes(($lines -join "`n")+"`n")
$path=Join-Path $root 'helper-runtime.manifest'
if ($VerifyOnly) {
    if (-not (Test-Path -LiteralPath $path) -or [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) -cne [Convert]::ToBase64String($bytes)) { throw 'Manifiesto runtime ausente o distinto de la publicacion firmada.' }
} else { [IO.File]::WriteAllBytes($path,$bytes) }
