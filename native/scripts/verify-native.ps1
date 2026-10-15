param(
    [Parameter(Mandatory = $true)][string]$DllPath,
    [Parameter(Mandatory = $true)][string]$BuildDirectory,
    [ValidateSet('Release', 'Debug')][string]$Configuration = 'Release',
    [string]$FfmSourcePath,
    [switch]$SkipCTest
)

. (Join-Path $PSScriptRoot 'native-common.ps1')
$toolchain = Get-MaceToolchain
$dll = [IO.Path]::GetFullPath($DllPath)
$build = [IO.Path]::GetFullPath($BuildDirectory)
if (-not (Test-Path -LiteralPath $dll -PathType Leaf)) { throw "No existe la DLL: $dll" }
$exportsTest = Join-Path $build "$Configuration\test_abi_exports.exe"
if (-not (Test-Path -LiteralPath $exportsTest -PathType Leaf)) {
    throw "No existe el verificador C para $Configuration en $build"
}

if (-not $SkipCTest) {
    & $toolchain.CTest --test-dir $build -C $Configuration --output-on-failure
    Assert-MaceCommandSucceeded 'Pruebas CTest'
}
& $exportsTest $dll
Assert-MaceCommandSucceeded 'Verificación de exportaciones de la DLL seleccionada'

$java = Get-Command java.exe -ErrorAction SilentlyContinue
if ($null -eq $java) { throw 'La prueba FFM requiere JDK 22 o superior.' }
$javaVersionLine = (& $java.Source --version | Select-Object -First 1)
if ($LASTEXITCODE -ne 0 -or $javaVersionLine -notmatch '(\d+)(?:\.\d+)?') {
    throw 'No se pudo identificar la versión de Java.'
}
if ([int]$Matches[1] -lt 22) { throw 'La prueba FFM requiere JDK 22 o superior.' }
$ffmSource = if ([string]::IsNullOrWhiteSpace($FfmSourcePath)) {
    Join-Path (Get-MaceNativeRoot) 'examples\ffm\NativeContractProbe.java'
} else {
    [IO.Path]::GetFullPath($FfmSourcePath)
}
if (-not (Test-Path -LiteralPath $ffmSource -PathType Leaf)) {
    throw "No existe el ejemplo FFM: $ffmSource"
}
& $java.Source --enable-native-access=ALL-UNNAMED $ffmSource $dll
Assert-MaceCommandSucceeded 'Prueba FFM contra la DLL seleccionada'
Write-Host "DLL verificada: $dll"
