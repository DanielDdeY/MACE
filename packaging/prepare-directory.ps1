param([Parameter(Mandatory=$true)][string]$Directory,[switch]$Create)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($PSScriptRoot)
$target=[IO.Path]::GetFullPath($Directory)
$allowed=@('output\input','runtime','dist\MACE') | ForEach-Object { [IO.Path]::GetFullPath((Join-Path $root $_)) }
if ($allowed -notcontains $target) { throw 'Solo se permite preparar las tres carpetas generadas de packaging.' }
$cursor=$target
while ($cursor.Length -ge $root.Length) {
    if (Test-Path -LiteralPath $cursor) {
        if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'No se permiten enlaces en la ruta de limpieza.' }
    }
    if ($cursor -eq $root) { break }
    $cursor=Split-Path -Parent $cursor
}
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
if ($Create) { New-Item -ItemType Directory -Path $target -Force | Out-Null }
