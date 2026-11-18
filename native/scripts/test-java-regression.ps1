param(
    [string]$JavaExe='C:\Program Files\Java\jdk-22\bin\java.exe',
    [switch]$Payload,
    [string]$PawnIoInstaller
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dev=Join-Path $repo 'native\build\fase2-paso4b\dev\Release'
$prod=Join-Path $repo 'native\build\fase2-paso4b\prod\Release\mace_native.dll'
$saved=@{}
Get-ChildItem Env:MACE_* | ForEach-Object { $saved[$_.Name]=$_.Value }
$oldJavaHome=$env:JAVA_HOME
try {
    Get-ChildItem Env:MACE_* | Remove-Item
    $env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent (Resolve-Path -LiteralPath $JavaExe).Path)
    $env:MACE_NATIVE_LIB=(Resolve-Path -LiteralPath (Join-Path $dev 'mace_native.dll')).Path
    $env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
    $env:MACE_WATCHDOG_FAKE_HELPER=(Resolve-Path -LiteralPath (Join-Path $dev 'lhm_fake_helper.exe')).Path
    & mvn -f (Join-Path $repo 'app\pom.xml') '-Dmace.build.nativeProfile=production' test
    if ($LASTEXITCODE) { throw 'Fallo la regresion Java.' }
    if ($Payload) {
        if (-not $PawnIoInstaller) { $PawnIoInstaller=Join-Path $repo 'packaging\dist\MACE\app\prerequisites\PawnIO_setup.exe' }
        & (Join-Path $repo 'packaging\tests\test-runtime-payload.ps1') -NativeDll $prod -PawnIoInstaller $PawnIoInstaller -JavaExe $JavaExe -DevelopmentDll $env:MACE_NATIVE_LIB
        if (-not $?) { throw 'Fallo la regresion de payload.' }
    }
} finally {
    Get-ChildItem Env:MACE_* | Remove-Item
    foreach ($key in $saved.Keys) { Set-Item -LiteralPath ('Env:'+$key) -Value $saved[$key] }
    $env:JAVA_HOME=$oldJavaHome
}
Write-Host 'PASS: regresion Java; variables de proceso restauradas. No se elevo ni ejecuto el instalador.'
