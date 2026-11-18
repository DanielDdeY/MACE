param([string]$JavaExe='C:\Program Files\Java\jdk-22\bin\java.exe',[switch]$ShowFailure)
$ErrorActionPreference='Stop'
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host "TEST_CONTEXT Administrator=$admin"
$repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dll=Join-Path $repo 'native\build\fase2-paso4b\prod\Release\mace_native.dll'
if (-not (Test-Path -LiteralPath $dll)) { throw 'Compila antes la DLL de produccion en native/build/fase2-paso4b/prod.' }
$oldJavaHome=$env:JAVA_HOME
try {
    $env:JAVA_HOME=Split-Path -Parent (Split-Path -Parent (Resolve-Path -LiteralPath $JavaExe).Path)
    & mvn -f (Join-Path $repo 'app\pom.xml') '-Dmace.build.directory=target/production-validation' '-Dmace.build.nativeProfile=production' -Dtest=NativeProductionPolicyTest test
    if ($LASTEXITCODE) { throw 'Fallo la comprobacion Java de produccion.' }
} finally { $env:JAVA_HOME=$oldJavaHome }
$output=Join-Path $repo 'app\target\production-validation'
$metadata=Join-Path $output 'classes\com\mace\infrastructure\nativebridge\native-profile.properties'
if ((Get-Content -LiteralPath $metadata -Raw).Trim() -cne 'profile=production') { throw 'Las clases principales no tienen perfil de produccion.' }
Write-Host 'PASS: politica de produccion; cuatro variables ajenas; rechazo cerrado sin pins; sin UI ni helper real.'
if ($ShowFailure) {
    $report=Join-Path $output 'surefire-reports\TEST-com.mace.infrastructure.nativebridge.NativeProductionPolicyTest.xml'
    $xml=[xml](Get-Content -LiteralPath $report -Raw)
    $classpath=($xml.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value
    $tests=Join-Path $output 'test-classes'
    $classpath=($classpath.Split(';') | Where-Object { [IO.Path]::GetFullPath($_).TrimEnd('\') -ne $tests.TrimEnd('\') }) -join ';'
    Write-Host 'PRUEBA VISUAL: sin pins, debe mostrar aviso generico y cerrar al aceptarlo. Cierra otras instancias de MACE antes.'
    & $JavaExe --enable-native-access=ALL-UNNAMED -Dmace.infra.mode=native -cp $classpath com.mace.presentation.MaceLauncher
    if ($LASTEXITCODE) { throw "La prueba visual termino con codigo $LASTEXITCODE." }
}
