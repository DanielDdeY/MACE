param(
    [switch]$Native,
    [switch]$ConsoleProbe,
    [ValidateSet('CPU_NOT_ELEVATED','PAWNIO_MISSING','PAWNIO_STOPPED','CPU_REBOOT_REQUIRED',
        'HELPER_UNAVAILABLE','HELPER_RETRYING','HELPER_UNTRUSTED','HELPER_CONTAINMENT_FAILED',
        'SENSORS_WARMING','LHM_RECOVERY_SHORT','LHM_RECOVERY_LONG')]
    [string]$Scenario='CPU_NOT_ELEVATED',
    [string]$BuildDirectory='',
    [string]$JavaExe='C:\Program Files\Java\jdk-22\bin\java.exe'
)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $BuildDirectory) { $BuildDirectory=Join-Path $repo 'native\build\fase2-paso4b\prod' }
$report=Join-Path $repo 'app\target\surefire-reports\TEST-com.mace.presentation.viewmodel.SensorReasonTest.xml'
if (-not (Test-Path -LiteralPath $report)) { throw 'Ejecuta primero mvn -f app/pom.xml test con JDK 22.' }
$xml=[xml](Get-Content -LiteralPath $report -Raw)
$classpath=($xml.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value
if (-not $classpath) { throw 'Falta el classpath de las pruebas.' }
$options=@('--enable-native-access=ALL-UNNAMED')
$data=Join-Path $repo 'native\build\fase2-paso4b\ui-data'
$options+="-Dmace.blackbox.dir=$data"
if ($Native -or $ConsoleProbe) {
    $admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($admin) { throw 'Esta comprobacion requiere una consola NORMAL, sin elevar.' }
    $source=Join-Path $BuildDirectory 'Release\mace_native.dll'
    if (-not ([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($source))).Contains('MACE_LHM_PROFILE=PRODUCTION')) {
        throw 'Se requiere la DLL de produccion con manifiesto del helper fijado.'
    }
    $pub=(Get-Content -LiteralPath (Join-Path $repo 'helper\artifacts\latest.txt') -Raw).Trim()
    & (Join-Path $repo 'helper\Verify-Artifacts.ps1') -Directory $pub
    if (-not $?) { throw 'El helper publicado no supera la verificacion.' }
    # Fresh, ignored test directory: never overwrite an installed/active runtime.
    $proof=Join-Path $repo ('native\build\fase2-paso4b\real-'+[Guid]::NewGuid().ToString('N'))
    $runtime=Join-Path $proof 'lhm'
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination (Join-Path $proof 'mace_native.dll')
    $manifest=Join-Path $pub 'helper-runtime.manifest'
    $files=@('helper-runtime.manifest')+@(Get-Content -LiteralPath $manifest | Select-Object -Skip 1 | ForEach-Object { $_.Substring(66) })
    foreach ($name in $files) { Copy-Item -LiteralPath (Join-Path $pub $name) -Destination (Join-Path $runtime $name) }
    $env:MACE_NATIVE_LIB=Join-Path $proof 'mace_native.dll'
    $env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
    $options+='-Dmace.infra.mode=native'
    Write-Host 'Datos nativos reales, sin elevar. CPU requiere administrador; CPU Windows/RAM independientes.'
} else {
    $options+=@('-Dmace.infra.mode=mock',"-Dmace.mock.scenario=$Scenario")
    Write-Host "SIMULACION: $Scenario. No se carga la DLL ni se toca PawnIO."
}
if ($ConsoleProbe) {
    $main='com.mace.presentation.viewmodel.NativeSensorReasonProbe'
} else {
    $tests=(Resolve-Path -LiteralPath (Join-Path $repo 'app\target\test-classes')).Path
    $classpath=($classpath.Split(';') | Where-Object { $_ -ne $tests }) -join ';'
    $main='com.mace.presentation.MaceLauncher'
}
$options+=@('-cp',$classpath,$main)
& $JavaExe @options
if ($LASTEXITCODE) { throw "La comprobacion Java termino con codigo $LASTEXITCODE." }
