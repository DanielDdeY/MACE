# D — Comandos futuros de candidata, NO EJECUTAR sin aprobación

Fecha: **2026-11-25**. Receta propuesta, **NO VERIFICADA por ejecución**.
No constituye autorización de descargas, aplicación de parches o builds.
La variante EXCLUIR sigue bloqueada por los cuatro códigos 2 actuales.
Solo el usuario ejecutará pruebas que eleven, terminen procesos o suspendan.

## 0. Gates y herramientas

Antes de usar esta receta: elegir una variante, aprobar diff completo y build
Temurin concreta, disponer de índice de descargas/cachés aprobado, verificar
integridad de herramientas e insumos y, para EXCLUIR, resultados físicos 0.
No usar una ejecución de dependency:tree con permiso de red como autorización
implícita para descargar artefactos desconocidos. Los comandos Maven siguientes
son **offline**: faltantes detienen, se identifican y se aprueban aparte.

Windows PowerShell **5.1 x64 nuevo**. No usar RunAs desde scripts. No asignar
JAVA_HOME/PATH ni copiar settings/credenciales del usuario. Antes de pruebas,
cerrar manualmente MACE/Preview previos; no ejecutar en paralelo.

## 1. Raíz nueva y un parche (futuro, consola normal)

```powershell
$ErrorActionPreference = 'Stop'
$Original = (Get-Location).Path
$R = Join-Path $Original 'native/build/candidata-20261125-01'
if (Test-Path -LiteralPath $R) { throw 'RAIZ_YA_EXISTE' }
$Variant = Read-Host 'Escribe CONSERVAR o EXCLUIR, solo tras aprobacion'
if ($Variant -cnotin @('CONSERVAR','EXCLUIR')) { throw 'VARIANTE_NO_ELEGIDA' }
$BeforeJava = $env:JAVA_HOME
$BeforePath = $env:PATH
$S = Join-Path $R 'source'
New-Item -ItemType Directory -Path $R | Out-Null
git -c core.autocrlf=false clone --no-hardlinks --no-local $Original $S
if ($LASTEXITCODE) { throw 'CLONE_FAILED' }
$Patch = Join-Path $Original ('helper/pendientes/CANDIDATA-'+$Variant+'-MONO.patch')
git -C $S apply --check --unidiff-zero $Patch
if ($LASTEXITCODE) { throw 'PATCH_CHECK_FAILED' }
git -C $S apply --unidiff-zero $Patch
if ($LASTEXITCODE) { throw 'PATCH_FAILED' }
```

Estos dos últimos apply solo sobre **S nuevo** y tras aprobación; en la entrega
actual se usa únicamente --check sobre el checkout, nunca apply efectivo.
La elegibilidad de EXCLUIR no se obtiene escribiendo su nombre en Read-Host:
la aprobación y evidencia son gates de revisión, no un control simulado en UI.
Confirmar igualdad LICENSE con el adjunto/main y revisión de origen. No se
importan artefactos de releases viejos ni outputs del checkout original.

Antes de compilar, registrar la edición aprobada en **S**, nunca en main ni
mediante git add general. Comandos futuros, después de revisar ese diff:

```powershell
$CandidateFiles = @('app/pom.xml','helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj','helper/Mace.Lhm.Protocol/Mace.Lhm.Protocol.csproj','helper/Mace.Lhm.Tests/Mace.Lhm.Tests.csproj','packaging/stage-runtime.ps1')
if ($Variant -ceq 'EXCLUIR') { $CandidateFiles += @('helper/Mace.Lhm.Helper/packages.lock.json','helper/Mace.Lhm.Tests/packages.lock.json') }
git -C $S add -- $CandidateFiles
if ($LASTEXITCODE) { throw 'SOURCE_STAGE_FAILED' }
git -C $S diff --cached --check
if ($LASTEXITCODE) { throw 'SOURCE_DIFF_FAILED' }
git -C $S commit --quiet -m ('candidate: propuesta combinada '+$Variant)
if ($LASTEXITCODE) { throw 'SOURCE_COMMIT_FAILED' }
```

El commit de fuente es local/aislado; no reescribe historial ni publica nada.
Registrar revisión y diff en evidencia saneada, sin identidades/correos del log.

Tras descargas aprobadas/verificadas, extraer ZIP Temurin dentro de
R/tools/temurin y Maven 3.9.16 en R/tools/maven/apache-maven-3.9.16.
No se dan órdenes de descarga automática. Crear deps/m2/deps/nuget a partir
de los artefactos/cachés aprobados. No copiar .m2/settings.xml ni secretos.

## 2. Seleccionar Java explícito y preparar resolución offline

```powershell
$Jdks = @(Get-ChildItem -LiteralPath (Join-Path $R 'tools/temurin') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') })
if ($Jdks.Count -ne 1) { throw 'JDK_NOT_UNIQUE' }
$Jdk = $Jdks[0].FullName
$Java = Join-Path $Jdk 'bin/java.exe'
$Maven = Join-Path $R 'tools/maven/apache-maven-3.9.16'
$Boot = @(Get-ChildItem -LiteralPath (Join-Path $Maven 'boot') -Filter 'plexus-classworlds-*.jar')
if ($Boot.Count -ne 1) { throw 'MAVEN_BOOT_NOT_UNIQUE' }
if ($env:JAVA_TOOL_OPTIONS -or $env:JDK_JAVA_OPTIONS) { throw 'JAVA_OPTIONS_INHERITED' }
& $Java --version
if ($LASTEXITCODE) { throw 'JDK_FAILED' }
New-Item -ItemType Directory -Path (Join-Path $R 'evidence'),(Join-Path $R 'deps/m2') -Force | Out-Null
$Settings = Join-Path $R 'deps/maven-settings.xml'
@'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors><mirror><id>central-only</id><mirrorOf>*</mirrorOf>
    <url>https://repo.maven.apache.org/maven2</url>
  </mirror></mirrors>
  <profiles><profile><id>integrity</id>
    <repositories><repository><id>central</id><url>https://repo.maven.apache.org/maven2</url>
      <releases><checksumPolicy>fail</checksumPolicy></releases>
      <snapshots><enabled>false</enabled></snapshots>
    </repository></repositories>
    <pluginRepositories><pluginRepository><id>central</id><url>https://repo.maven.apache.org/maven2</url>
      <releases><checksumPolicy>fail</checksumPolicy></releases>
      <snapshots><enabled>false</enabled></snapshots>
    </pluginRepository></pluginRepositories>
  </profile></profiles><activeProfiles><activeProfile>integrity</activeProfile></activeProfiles>
</settings>
'@ | Set-Content -LiteralPath $Settings -Encoding UTF8
$Bootstrap = @('--enable-native-access=ALL-UNNAMED',('-Dmaven.home='+$Maven),
    ('-Dclassworlds.conf='+(Join-Path $Maven 'bin/m2.conf')),
    ('-Dmaven.multiModuleProjectDirectory='+(Join-Path $S 'app')),
    '-cp',$Boot[0].FullName,'org.codehaus.plexus.classworlds.launcher.Launcher')
function Mvn {
    param([string[]]$Goals)
    & $Java @Bootstrap -o -B -s $Settings -gs $Settings -f (Join-Path $S 'app/pom.xml') "-Dmaven.repo.local=$R/deps/m2" @Goals
    if ($LASTEXITCODE) { throw 'MAVEN_FAILED' }
}
Mvn -Goals @('org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree',"-DoutputFile=$R/evidence/dependency-tree.txt")
```

Verificar release file y --version **25.0.5.1+2**; árbol JavaFX cuatro módulos
27.0.1.1/classifier win, AtlantaFX 2.0.1, Mockito 5.20.0 y Byte Buddy/agent 1.17.7.
Parar ante conflicto, snapshot, versión inesperada o faltante offline. No
compilar antes de revisar ese árbol. Maven usa ese Java por ruta absoluta;
Surefire hereda java.home de esa JVM. No se usa mvn.cmd, setx o cambios de PATH.
La invocación Classworlds y el conjunto de plugins permanecen NV hasta ensayo.

## 3. Helper final una vez, DLL dev y prod

Preparar **R/NuGet.Config** con clear + feed local aprobado solamente y
sin servidores de red. Puede apuntar a un almacén local de nupkg verificados;
no basta copiar DLL sueltas. El archivo contiene rutas privadas operativas:
no se versiona ni comparte. Si falta un paquete: detener, no quitar locked-mode.
NUGET_PACKAGES y DOTNET_CLI_HOME siguientes solo en este proceso nuevo;
ninguna variable JAVA_HOME/PATH cambia.

```powershell
$env:NUGET_PACKAGES = Join-Path $R 'deps/nuget'
$env:DOTNET_CLI_HOME = Join-Path $R 'tools/dotnet-cli-home'
Get-ChildItem Env:MACE_* | Remove-Item
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'helper/Build-Helper.ps1')
if ($LASTEXITCODE) { throw 'HELPER_FAILED' }
$H = (Get-Content -LiteralPath (Join-Path $S 'helper/artifacts/latest.txt') -Raw).Trim()
$Manifest = Join-Path $H 'helper-runtime.manifest'
$Dev = Join-Path $S 'native/build/candidate/dev'
$Prod = Join-Path $S 'native/build/candidate/prod'
$Cmake = (Get-Command cmake.exe -ErrorAction Stop).Source
$CTest = Join-Path (Split-Path -Parent $Cmake) 'ctest.exe'
$Generator = Read-Host 'Generador instalado: Visual Studio 17 2022 o Visual Studio 18 2026'
if ($Generator -cnotin @('Visual Studio 17 2022','Visual Studio 18 2026')) { throw 'GENERATOR_NOT_APPROVED' }
& $Cmake -S (Join-Path $S 'native') -B $Dev -G $Generator -A x64 -DBUILD_TESTING=ON -DMACE_LHM_DEVELOPMENT=ON
if ($LASTEXITCODE) { throw 'CMAKE_DEV_FAILED' }
& $Cmake --build $Dev --config Release --parallel 4
if ($LASTEXITCODE) { throw 'BUILD_DEV_FAILED' }
& $Cmake -S (Join-Path $S 'native') -B $Prod -G $Generator -A x64 -DBUILD_TESTING=ON -DMACE_LHM_DEVELOPMENT=OFF "-DMACE_LHM_RUNTIME_MANIFEST=$Manifest"
if ($LASTEXITCODE) { throw 'CMAKE_PROD_FAILED' }
& $Cmake --build $Prod --config Release --parallel 4
if ($LASTEXITCODE) { throw 'BUILD_PROD_FAILED' }
$DevDll = Join-Path $Dev 'Release/mace_native.dll'
$ProdDll = Join-Path $Prod 'Release/mace_native.dll'
```

Build-Helper exige global.json 10.0.401 y runtime 10.0.12, ejecuta 105 checks
sin argumentos y escribe latest **solo en S**, no referencia original.
Conservar reportes saneados. El agente no ejecutará arneses con terminaciones.
Para integración con la DLL prod, copiar junto a ella en **Prod/Release/lhm**
la selección del manifiesto helper usada por stage-runtime (DLL/JSON/EXE helper
y helper-runtime.manifest), omitiendo PDB y artifact-manifest. De otro modo
prod ignora MACE_LHM_HELPER y buscaría un payload inexistente al lado de la DLL.
No hacer esa copia sobre binarios de la referencia.

Comandos de esa copia exclusivamente para la integración de la DLL prod:

```powershell
$Sidecar = Join-Path $Prod 'Release/lhm'
if (Test-Path -LiteralPath $Sidecar) { throw 'SIDECAR_NOT_FRESH' }
New-Item -ItemType Directory -Path $Sidecar | Out-Null
$HelperEntries = (Get-Content -LiteralPath (Join-Path $H 'artifact-manifest.json') -Raw | ConvertFrom-Json).files
foreach ($Entry in $HelperEntries) {
    if ($Entry.file -match '\.(dll|json)$' -or $Entry.file -eq 'Mace.Lhm.Helper.exe') {
        Copy-Item -LiteralPath (Join-Path $H $Entry.file) -Destination (Join-Path $Sidecar $Entry.file)
    }
}
Copy-Item -LiteralPath $Manifest -Destination (Join-Path $Sidecar 'helper-runtime.manifest')
```

## 4. Regresión del usuario, normal y después administrador

Ejecutar **desde S**, con variables R/S/Java/H/Dev/Prod/CTest/Bootstrap
redefinidas en cada consola; ninguna transición automática de privilegios.
Repetir configuración de Mvn del paso 2, no rebuild de helper/DLL/imagen.
Registrar token y $LASTEXITCODE tras **cada** comando. CTest exige consola
limpia de MACE_*; Java necesita sus variables de integración en ese proceso.

```powershell
Set-Location -LiteralPath $S
Get-ChildItem Env:MACE_* | Remove-Item
& $CTest --test-dir $Dev -C Release --output-on-failure
& $CTest --test-dir $Prod -C Release --output-on-failure
& (Join-Path $S 'helper/Mace.Lhm.Tests/bin/Release/net10.0-windows/win-x64/Mace.Lhm.Tests.exe')
$env:MACE_NATIVE_LIB = $DevDll
$env:MACE_NATIVE_SHA256 = (Get-FileHash -LiteralPath $DevDll -Algorithm SHA256).Hash
$env:MACE_LHM_HELPER = Join-Path $H 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256 = (Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
Mvn -Goals @('-Pdevelopment','-Dmace.build.directory=target/candidate-development','test')
$env:MACE_NATIVE_LIB = $ProdDll
$env:MACE_NATIVE_SHA256 = (Get-FileHash -LiteralPath $ProdDll -Algorithm SHA256).Hash
Mvn -Goals @('test')
& (Join-Path $Prod 'Release/test_abi_exports.exe') $ProdDll
& $Java --enable-native-access=ALL-UNNAMED (Join-Path $S 'native/examples/ffm/NativeContractProbe.java') $ProdDll
```

Esperados por cada contexto cubierto: CTest 76/77; helper 105; Java 347 + 1
omisión histórica, sin nuevas omisiones. No presentar un CTest parcial o JUnit
sin sus precondiciones como regresión completa. CTest/Java incluyen procesos
simulados y terminaciones: **usuario exclusivamente** en esta campaña.
No usar test-lhm1b para esta campaña: recompila y toma latest por defecto;
los comandos directos usan exactamente los outputs finalizados seleccionados.

## 5. JAR production y una app-image, tras superar regresión previa

Conservar las clases production ya probadas: goal jar:jar no recompila ni
vuelve a ejecutar toda la suite. Si una herramienta cambia/recompila esas
clases, revalidar la correspondencia; no atribuir prueba a bytes diferentes.

```powershell
$Stage = Join-Path $S 'packaging/output/input'
$Runtime = Join-Path $S 'packaging/runtime'
$Dist = Join-Path $S 'packaging/dist'
if ((Test-Path $Stage) -or (Test-Path $Runtime) -or (Test-Path $Dist)) { throw 'OUTPUT_NOT_FRESH' }
New-Item -ItemType Directory -Path $Stage,$Dist | Out-Null
Mvn -Goals @('jar:jar','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies','-DincludeScope=runtime',"-DoutputDirectory=$Stage")
$Jar = Join-Path $S 'app/target/mace-app-1.0.0-SNAPSHOT.jar'
$DllSha = (Get-FileHash -LiteralPath $ProdDll -Algorithm SHA256).Hash
$Pawn = Join-Path $Original 'packaging/dist/MACE/app/prerequisites/PawnIO_setup.exe'
& (Join-Path $S 'packaging/stage-runtime.ps1') -StageDirectory $Stage -AppJar $Jar -NativeDll $ProdDll -NativeSha256 $DllSha -HelperDirectory $H -PawnIoInstaller $Pawn -SigningScript ''
if (-not $?) { throw 'STAGE_FAILED' }
if ((Get-FileHash -LiteralPath (Join-Path $S 'LICENSE') -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath (Join-Path $Stage 'third-party/MACE-LICENSE.txt') -Algorithm SHA256).Hash) { throw 'OWN_LICENSE_MISMATCH' }
& (Join-Path $Jdk 'bin/jlink.exe') --module-path (Join-Path $Jdk 'jmods') --add-modules java.base,java.desktop,java.logging,java.xml,java.naming,java.scripting,java.net.http,jdk.unsupported --strip-debug --no-header-files --no-man-pages --exclude-files '**.lib' --compress zip-6 --output $Runtime
if ($LASTEXITCODE) { throw 'JLINK_FAILED' }
$IconOptions = @()
$IconPath = Join-Path $S 'packaging/icon.ico'
if (Test-Path -LiteralPath $IconPath) { $IconOptions = @('--icon',$IconPath) }
& (Join-Path $Jdk 'bin/jpackage.exe') @IconOptions --type app-image --name MACE --app-version 1.0.0 --vendor 'MACE Team' --description 'MACE - Telemetria de hardware y gestor de procesos' --input $Stage --main-jar 'mace-app-1.0.0-SNAPSHOT.jar' --main-class com.mace.presentation.MaceLauncher --dest $Dist --runtime-image $Runtime --java-options '--enable-native-access=ALL-UNNAMED' --java-options '-Dmace.infra.mode=native' --java-options '-Dmace.native.lib=$APPDIR\mace_native.dll' --java-options "-Dmace.native.sha256=$DllSha" --java-options '-Djava.library.path=$APPDIR' --add-launcher ("MACE-Preview="+(Join-Path $S 'packaging/preview-launcher.properties'))
if ($LASTEXITCODE) { throw 'JPACKAGE_FAILED' }
$Image = Join-Path $Dist 'MACE'
```

Vendor/version y plantilla Preview se conservan: no decidir metadatos de firma
ni nuevas versiones aquí. Usar icono original con --icon si existe, antes de
la única invocación jpackage. No usar JAVAFX_JMODS, jpackage MSI/EXE, Inno o WiX.
La candidata es interna/sin firma de MACE, con avisos todavía provisionales.

Resolver **Mt** a mt.exe x64 existente del Windows SDK, verificarlo y aportar
ruta absoluta sin modificar PATH; si no disponible, detener, no instalar.

```powershell
$Mt = Read-Host 'Ruta absoluta al mt.exe x64 verificado del Windows SDK'
if (-not (Test-Path -LiteralPath $Mt -PathType Leaf)) { throw 'MT_REQUIRED' }
$Launcher = Join-Path $Image 'MACE.exe'
$Info = Get-Item -LiteralPath $Launcher
$Info.IsReadOnly = $false
& $Mt -nologo -manifest (Join-Path $S 'packaging/app.manifest') ("-outputresource:"+$Launcher+';#1')
if ($LASTEXITCODE) { throw 'MANIFEST_FAILED' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'packaging/verify-image.ps1') -ImageDirectory $Image
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'packaging/tests/test-image.ps1') -ImageDirectory $Image
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'packaging/tests/test-runtime-payload.ps1') -NativeDll $ProdDll -HelperDirectory $H -PawnIoInstaller $Pawn -JavaExe $Java -DevelopmentDll $DevDll
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'native/scripts/check-protected-telemetry.ps1')
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $S 'native/scripts/audit-retired-providers.ps1') -ReportPath (Join-Path $R 'evidence/referencias.tsv')
if ($env:JAVA_HOME -cne $BeforeJava -or $env:PATH -cne $BeforePath) { throw 'HOST_ENV_CHANGED' }
```

Capturar cada código antes del siguiente comando. test-image crea y retira
solo sus dos scratch dentro de **la imagen candidata**, nunca referencia.
Medir número/tamaño de archivos y producir sumas operativas completas bajo
R/evidence, con informe compartible de conteos/igualdad, sin hashes completos.
Los logs de compilador pueden incluir rutas: no guardarlos en informes ni
commits; sanear antes de cualquier conservación/entrega. Validar que no hay
datos de perfil/correos/SID/máquina, docs internas, scripts, tests o logs en imagen.

## 6. Ensayos físicos/UI del usuario, una campaña sobre esa misma candidata

Con el diagnóstico C aprobado previamente, repetir contra H y **DevDll nuevos**:

```powershell
$T = Join-Path $S 'helper/auditoria-licencias/probar-mono-manual.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $T -HelperDirectory $H -NativeDll $DevDll -Scenario Read
```

Ese comando en normal. En administrador, mismos prefijos y parámetros:
Read -RequireCpu, Reconnect -RequireCpu, CloseEarly. Se detallan todos los
criterios en FASE3-DIAGNOSTICO-MONO-MANUAL.md. No aceptar 2 como aprobado.
Después probar **Image/MACE.exe real** y **MACE-Preview.exe**: CPU/GPU de cada
equipo, dashboard/HUD, CSS, FXML, input, escalas, ventanas y mensajes. Preview
no prueba hardware. Cerrar con X manualmente; sin autoelevación del agente.

Desde S y consola administrador del usuario, sobre Image dentro de S:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image-environment.ps1 -ImageDirectory $Image
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseX
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillHelper
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillJvm
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillLauncher
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillTree
```

Estos arneses atan Image por ubicación relativa a **S**; verificar el mensaje
de preflight, no ejecutarlos desde el checkout original. Casos EndTaskWindow,
CloseWarming, SuspendResume, coexistencia e instancia única: orden y observación
en helper/FASE2-PASO5-PRUEBAS.md de S. No usar su ruta Java normal predeterminada
para acreditar Temurin 25: esa variante usa herramientas/salidas antiguas.
Para UI normal de fuentes, invocar **Java** explícito con classpath de
target/candidate-development/classes + JAR runtime de Stage y pines DevDll;
no run-source.ps1. Preparar ese classpath sin repetir build.

Comando explícito para ese caso, **consola normal**, variables de la candidata:

```powershell
$Classes = Join-Path $S 'app/target/candidate-development/classes'
$DependencyJars = @(Get-ChildItem -LiteralPath $Stage -Filter '*.jar' | Where-Object { $_.Name -ne 'mace-app-1.0.0-SNAPSHOT.jar' })
$Classpath = $Classes+';'+(($DependencyJars | ForEach-Object { $_.FullName }) -join ';')
$DevSha = (Get-FileHash -LiteralPath $DevDll -Algorithm SHA256).Hash
$env:MACE_LHM_HELPER = Join-Path $H 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256 = (Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
& $Java --enable-native-access=ALL-UNNAMED '-Dmace.infra.mode=native' "-Dmace.native.lib=$DevDll" "-Dmace.native.sha256=$DevSha" "-Dmace.blackbox.dir=$R/evidence/blackbox-development" -cp $Classpath com.mace.presentation.MaceLauncher
```

UI real sin elevar: CPU t/p N/D y mensaje de falta de elevación, GPU según
proveedor/disponibilidad; no forzar lectura válida. Cerrar con X. Para inspección
simulada, mismo classpath/Java con infra.mode=mock y mock.scenario=NORMAL,
sin opciones de DLL. Los demás escenarios aprobados y sus textos están en
FASE2-PASO4B.md; simulación no reemplaza física. Para instancia mixta: dejar
la instancia normal abierta, y abrir Image/MACE.exe manualmente en consola
administrador de la misma cuenta/sesión; luego repetir orden inverso. Esperar
el mensaje de instancia ya abierta/cerrando, observar qué ventana conserva
propiedad y cerrar manualmente. No invocar automáticamente otro token.

Si el arnés histórico depende de Java/clases antiguas o falta una precondición,
dejar el caso NV y proponer adaptación aparte; no tocar launchers/criterios para
hacerlo pasar. Una precondición CloseWarming no vista sigue NV.
PawnIO/3010/HVCI no se provocan aquí. Intel/NVIDIA y suspensión/kills solo por
usuario; anotar PID launcher/JVM/helper y estado fresco tras reanudación, no
suponer que jpackage 25 reproduce el árbol de dos procesos antiguo.

**Parada:** conservar candidata/índice y resultados para revisión. Nada se
copia a imagen congelada, se firma, instala, publica o sube a GitHub.

Si hay error de longitud de ruta/archivo, no modificar registro ni mover el
checkout existente: detener y proponer otra raíz nueva más corta autorizada.
Si mt, CMake/MSVC, SDK o paquete faltan, detener; no instalar herramientas ni
quitar verificaciones para continuar. Integridad fallida no se “corrige”
cambiando el hash esperado. Esos fallos y compatibilidad efectiva siguen NV.
