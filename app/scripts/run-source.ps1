param(
    [string]$NativeDll,
    [string]$HelperDirectory,
    [switch]$Preview,
    [string]$Scenario='NORMAL',
    [switch]$PrepareOnly,
    [string]$JavaExe='C:\Program Files\Java\jdk-22\bin\java.exe'
)
$ErrorActionPreference='Stop'
$app=Split-Path -Parent $PSScriptRoot
$repo=Split-Path -Parent $app
$output=Join-Path $app 'target\source-development'
$classpathFile=Join-Path $output 'runtime-classpath.txt'
$oldJavaHome=$env:JAVA_HOME
try {
    $env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent (Resolve-Path -LiteralPath $JavaExe).Path)
    & mvn -f (Join-Path $app 'pom.xml') -Pdevelopment '-Dmace.build.directory=target/source-development' -DskipTests package dependency:build-classpath "-Dmdep.outputFile=$classpathFile" -DincludeScope=runtime
    if ($LASTEXITCODE) { throw 'No se pudo compilar el perfil Java de desarrollo.' }
} finally { $env:JAVA_HOME=$oldJavaHome }
$classes=Join-Path $output 'classes'
$metadata=Join-Path $classes 'com\mace\infrastructure\nativebridge\native-profile.properties'
if ((Get-Content -LiteralPath $metadata -Raw) -notmatch '(?m)^profile=development\s*$') { throw 'Perfil Java de desarrollo ausente.' }
if ($PrepareOnly) { Write-Host 'PASS: clases Java de desarrollo preparadas en app/target/source-development.'; return }
$options=@('--enable-native-access=ALL-UNNAMED',"-Dmace.blackbox.dir=$(Join-Path $output 'blackbox')")
$saved=@{}
Get-ChildItem Env:MACE_* | ForEach-Object { $saved[$_.Name]=$_.Value }
try {
    Get-ChildItem Env:MACE_* | Remove-Item
    if ($Preview) {
        $options+=@('-Dmace.infra.mode=mock',"-Dmace.mock.scenario=$Scenario")
        Write-Host 'SIMULACION: sin DLL ni helper.'
    } else {
        if (-not $NativeDll) { $NativeDll=Join-Path $repo 'native\build\fase2-paso4b\dev\Release\mace_native.dll' }
        $dll=(Resolve-Path -LiteralPath $NativeDll).Path
        if (-not $HelperDirectory) { $HelperDirectory=(Get-Content -LiteralPath (Join-Path $repo 'helper\artifacts\latest.txt') -Raw).Trim() }
        & (Join-Path $repo 'helper\Verify-Artifacts.ps1') -Directory $HelperDirectory
        if (-not $?) { throw 'Publicacion del helper no verificada.' }
        $helper=(Resolve-Path -LiteralPath (Join-Path $HelperDirectory 'Mace.Lhm.Helper.exe')).Path
        $env:MACE_LHM_HELPER=$helper
        $env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash
        $options+=@('-Dmace.infra.mode=native',"-Dmace.native.lib=$dll", "-Dmace.native.sha256=$((Get-FileHash -LiteralPath $dll -Algorithm SHA256).Hash)")
        Write-Host 'DESARROLLO: datos nativos reales; no solicita elevacion ni modifica servicios.'
    }
    $classpath=$classes+';'+(Get-Content -LiteralPath $classpathFile -Raw).Trim()
    & $JavaExe @options -cp $classpath com.mace.presentation.MaceLauncher
    if ($LASTEXITCODE) { throw "MACE termino con codigo $LASTEXITCODE." }
} finally {
    Get-ChildItem Env:MACE_* | Remove-Item
    foreach ($key in $saved.Keys) { Set-Item -LiteralPath ('Env:'+$key) -Value $saved[$key] }
}
