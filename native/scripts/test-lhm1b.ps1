param(
    [string]$BuildDirectory='',
    [ValidateSet('Auto','VS2022','VS2026')][string]$VisualStudio='Auto',
    [switch]$RealHelper,
    [switch]$RequireCpu
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'native-common.ps1')
$nativeRoot=Get-MaceNativeRoot
$repoRoot=Split-Path -Parent $nativeRoot
$toolchain=Get-MaceToolchain -VisualStudio $VisualStudio
if (-not $BuildDirectory) { $BuildDirectory=Join-Path $nativeRoot 'build\lhm1b' }
$BuildDirectory=[IO.Path]::GetFullPath($BuildDirectory)
& $toolchain.CMake -S $nativeRoot -B $BuildDirectory -G $toolchain.Generator -A x64 -DBUILD_TESTING=ON '-DMACE_ADLX_SDK_DIR=' '-DMACE_RYZEN_SDK_DIR='
Assert-MaceCommandSucceeded 'Configuracion 1B'
& $toolchain.CMake --build $BuildDirectory --config Release --parallel 4
Assert-MaceCommandSucceeded 'Compilacion 1B'
& $toolchain.CTest --test-dir $BuildDirectory -C Release --output-on-failure -j 4 --output-junit (Join-Path $BuildDirectory 'lhm1b-tests.xml')
Assert-MaceCommandSucceeded 'Pruebas 1B'
if ($RealHelper -or $RequireCpu) {
    $helperRoot=Join-Path $repoRoot 'helper'
    $latest=Join-Path $helperRoot 'artifacts\latest.txt'
    if (-not (Test-Path -LiteralPath $latest)) { throw 'Ejecuta primero helper\Build-Helper.ps1.' }
    $published=Get-Content -LiteralPath $latest -Raw
    $published=$published.Trim()
    & (Join-Path $helperRoot 'Verify-Artifacts.ps1') -Directory $published
    $oldPath=[Environment]::GetEnvironmentVariable('MACE_LHM_HELPER','Process')
    $oldHash=[Environment]::GetEnvironmentVariable('MACE_LHM_HELPER_SHA256','Process')
    try {
        $env:MACE_LHM_HELPER=Join-Path $published 'Mace.Lhm.Helper.exe'
        $env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
        $arguments=@($env:MACE_LHM_HELPER)
        if ($RequireCpu) { $arguments+='--require-cpu' }
        $log=Join-Path $BuildDirectory ('lhm-live-'+[DateTime]::Now.ToString('yyyyMMdd-HHmmss')+'.txt')
        & (Join-Path $BuildDirectory 'Release\test_lhm_live.exe') @arguments | Tee-Object -FilePath $log
        Assert-MaceCommandSucceeded 'Integracion fisica DLL/helper'
        Write-Host "Evidencia: $log"
    } finally {
        [Environment]::SetEnvironmentVariable('MACE_LHM_HELPER',$oldPath,'Process')
        [Environment]::SetEnvironmentVariable('MACE_LHM_HELPER_SHA256',$oldHash,'Process')
    }
}
