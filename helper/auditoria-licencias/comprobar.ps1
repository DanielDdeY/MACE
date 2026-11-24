param([switch]$ConsultarFuentes, [string]$Salida='native/build/auditoria-licencias')
$ErrorActionPreference='Stop'
if ($PSVersionTable.PSVersion.Major -ne 5) {
    throw 'Ejecutar con Windows PowerShell 5.1. No se necesita administrador.'
}
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$python=Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Falta Python 3.10 o posterior. No se instala automaticamente.' }
$arguments=@('-B',(Join-Path $PSScriptRoot 'audit.py'),'--out',$Salida)
if ($ConsultarFuentes) {$arguments+='--refresh-official'}
Push-Location -LiteralPath $repo
try {
    & $python.Source @arguments
    $result=$LASTEXITCODE
} finally { Pop-Location }
exit $result
