$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'La prueba fisica de CPU requiere PowerShell como administrador.'
}
$published = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'artifacts\latest.txt') -Raw).Trim()
& (Join-Path $PSScriptRoot 'Verify-Artifacts.ps1') -Directory $published
$tests = Join-Path $PSScriptRoot 'Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe'
$executable = Join-Path $published 'Mace.Lhm.Helper.exe'
$log = Join-Path $PSScriptRoot ('artifacts\smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
& $tests $executable 2>&1 | Tee-Object -FilePath $log
if ($LASTEXITCODE -ne 0) { throw ('Fallo la prueba real. Consulta ' + $log) }
Write-Host ('Evidencia guardada en ' + $log)
