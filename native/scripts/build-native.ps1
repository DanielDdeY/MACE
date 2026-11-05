param(
    [ValidateSet('Release', 'Debug')][string]$Configuration = 'Release',
    [ValidateSet('Auto', 'VS2022', 'VS2026')][string]$VisualStudio = 'Auto',
    [string]$BuildDirectory,
    [string]$AdlxSdkDirectory = $env:MACE_ADLX_SDK_DIR,
    [string]$RyzenSdkDirectory = $env:MACE_RYZEN_SDK_DIR
)

. (Join-Path $PSScriptRoot 'native-common.ps1')
$toolchain = Get-MaceToolchain -VisualStudio $VisualStudio
$nativeRoot = Get-MaceNativeRoot
$build = Get-MaceBuildDirectory -BuildDirectory $BuildDirectory -Preset $toolchain.Preset

Write-Host "MACE native: $($toolchain.Generator), CMake $($toolchain.CMakeVersion), $Configuration"
$configureArgs = @('-S', $nativeRoot, '-B', $build, '-G', $toolchain.Generator,
    '-A', 'x64', "-DMACE_ADLX_SDK_DIR=$AdlxSdkDirectory",
    "-DMACE_RYZEN_SDK_DIR=$RyzenSdkDirectory")
& $toolchain.CMake @configureArgs
Assert-MaceCommandSucceeded 'Configuración CMake'
& $toolchain.CMake --build $build --config $Configuration
Assert-MaceCommandSucceeded 'Compilación nativa'
Write-Host "DLL compilada: $(Join-Path $build "$Configuration\mace_native.dll")"
Write-Host "Diagnóstico manual: $(Join-Path $build "$Configuration\test_mace.exe")"
