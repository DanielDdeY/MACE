param([Parameter(Mandatory=$true)][string]$ImageDirectory)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ImageDirectory).Path
$packaging=Split-Path -Parent $PSScriptRoot
$verifier=Join-Path $packaging 'verify-image.ps1'
& $verifier -ImageDirectory $root
$scratch=Join-Path $root ('runtime\lib\mace-qa-'+[Guid]::NewGuid().ToString('N')+'.lib')
$copy=Join-Path $root ('runtime\bin\mace-qa-'+[Guid]::NewGuid().ToString('N')+'.dll')
if (Test-Path -LiteralPath $scratch) { throw 'El archivo temporal ya existe.' }
if (Test-Path -LiteralPath $copy) { throw 'El archivo temporal ya existe.' }
try {
    [IO.File]::WriteAllText($scratch,'development-only test file')
    $rejected=$false
    try { & $verifier -ImageDirectory $root } catch {
        if ($_.Exception.Message -notmatch 'Archivo de desarrollo') { throw }
        $rejected=$true
    }
    if (-not $rejected) { throw 'Se acepto una biblioteca de desarrollo.' }
    Remove-Item -LiteralPath $scratch
    # Identical approved bytes in another path must NOT inherit the exception.
    Copy-Item -LiteralPath (Join-Path $root 'app\lhm\RAMSPDToolkit-NDD.dll') -Destination $copy
    $rejected=$false
    try { & $verifier -ImageDirectory $root } catch {
        if ($_.Exception.Message -notmatch 'Referencia binaria retirada no aceptada') { throw }
        $rejected=$true
    }
    if (-not $rejected) { throw 'La excepcion se aplico a un archivo no aprobado.' }
} finally {
    # Remove only the two uniquely named files this test created, never recurse.
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch }
    if (Test-Path -LiteralPath $copy) { Remove-Item -LiteralPath $copy }
}
& $verifier -ImageDirectory $root
Write-Host 'PASS: imagen valida, .lib rechazada y excepcion limitada a ruta/hash/offsets aprobados; archivos de prueba retirados.'
