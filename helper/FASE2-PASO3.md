# Fase 2 — Paso 3: instancia única y procesos existentes

Fecha: 2026-11-17. Alcance: únicamente Paso 3 aprobado. Árbol limpio al
comenzar; rama `LHM_implementado`; base
`7510fa48d5dfc4b7867509907bf652359155cc9c`.
Un commit local para este paso, sin reescribir historial ni fusionar a principal.
El hash resultante se entrega en la respuesta de cierre (este documento forma
parte del propio commit).

## Decisión e implementación

La reserva vive en Java, en `WindowsSingleInstance`, mediante FFM de Java 22
hacia Win32, sin cargar `mace_native.dll`. `MaceApplication.init()` la adquiere
antes del Stage principal, adaptadores, DLL y helper. La creación del toolkit
interno de JavaFX no implica que se haya abierto el dashboard.

Nombre: `Local\MACE.Instance.v1.<sha256(SID:sessionId)>`. SID proviene del token
del proceso (TokenUser), no de variables de entorno/nombre de usuario;
ProcessIdToSessionId obtiene la sesión. `Local\` separa sesiones; el hash y
SID separan usuarios. El hash evita exponer literalmente el SID en el nombre;
no es un secreto ni autentica otros procesos. La identidad permanece fija
entre versiones para que una actualización también compita con la anterior
que implemente esta política. Versiones antiguas sin ella no quedan cubiertas.

Se usa CreateMutexExW, flags 0 y acceso `SYNCHRONIZE | READ_CONTROL`, sin adquirir
propiedad, sin WaitForSingleObject y sin ReleaseMutex. La reserva consiste en
la existencia del objeto, creada atómicamente por Windows. No depende de la
vida del hilo `init`, de mutex abandonado, de PID almacenado ni de un fichero.
GetLastError se captura en la misma llamada FFM; ERROR_ALREADY_EXISTS significa
BUSY. El perdedor cierra inmediatamente su referencia antes del aviso para no
prolongar la reserva del ganador. Los errores de API/ACL/tipo de objeto
conflictivo fallan cerrados: UNAVAILABLE; no arrancan sensores ni helper.

El handle ganador es no heredable y se retiene hasta la muerte del proceso.
No lo cierran `stop()`, la limpieza de recursos, un callback, un shutdown hook
ni el watchdog. Esto cubre la ventana oculta durante el cierre nativo de hasta
4 s y el plazo de último recurso de 10 s. Windows cierra los handles del
proceso al salir normalmente, ser terminado o ejecutar Runtime.halt(70).
No queda fichero huérfano que borrar. Un crash nativo no se provocó: su
liberación automática es una garantía del OS, NO VERIFICADO mediante crash real.

La segunda instancia muestra «MACE ya está abierto o se está cerrando», explica
una instancia por usuario/sesión y pide esperar y reintentar. Sale al aceptar
el aviso; mientras este está abierto no posee reserva ni helper. No hay IPC
para activar la ventana ajena ni espera para transferir la reserva. Un error
al reservar muestra otro aviso con código de error; -1 indica fallo de la
integración/consulta, sin revelar rutas o identidad local.

MACE y Preview comparten identidad: una Preview impide iniciar MACE y viceversa,
evitando dashboards/HUD duplicados y accesos simultáneos a preferencias. MACE.exe
conserva requireAdministrator y Preview asInvoker/datos simulados. No se cambian
launchers ni manifiestos. Abrir MACE.exe puede seguir solicitando UAC antes de
que Java detecte la otra instancia.

Alternativas descartadas: un guard en la DLL llegaría después de resolverla y
no cubriría Preview; un PID/fichero introduce rutas, limpieza y reutilización
de PID; un puerto/IPC para activar la ventana añade autenticación y cruces de
integridad innecesarios. No se añade un estado al contrato de telemetría.

## Permisos, elevación y límite de suplantación

DACL protegida: SYSTEM y el SID actual con lectura/sincronización
(`0x00120000`). SDDL:
`D:P(A;;0x00120000;;;SY)(A;;0x00120000;;;<SID>)S:(ML;;NW;;;ME)`.
La etiqueta explícita Medium evita que la primera instancia elevada cree por
defecto un objeto High que dificulte la participación de una Medium del mismo
SID. No se solicitan derechos de modificación/propiedad del mutex. Las
estructuras SECURITY_ATTRIBUTES son x64, como el producto; handle no heredable.
Las DLL Win32 usadas son KnownDLLs de Windows, independientes del payload LHM.

Comprobado: creación/competencia/cierre en procesos reales del mismo usuario y
sesión, sin elevación. Cruce High/Medium en ambos órdenes: **NO VERIFICADO**;
la ACL/etiqueta es el diseño, no una prueba física. Si la política local impide
abrir/crear el objeto, se rechaza el arranque sin helper. UAC con credenciales
de OTRO usuario cambia SID: se permiten instancias por usuario según el alcance.
Sesiones de escritorio diferentes: nombres separados por diseño; prueba real
multiusuario/multisesión: NO VERIFICADO.

Un proceso del mismo usuario puede anticipar el nombre y mantenerlo ocupado,
crear otro tipo de objeto con él o retener una referencia duplicada al mutex.
Es denegación de servicio local; no permite mandar órdenes ni obtener acceso
al helper. La ACL, SID, sesión y derechos mínimos limitan colisiones accidentales
y acceso entre usuarios; no convierten procesos del mismo usuario en una frontera
de seguridad. No se intenta eliminar objetos ajenos, cambiar sus ACL ni matar
procesos para recuperar la reserva. Si un tercero conserva una referencia,
puede prolongar el bloqueo incluso después de morir MACE: límite explícito a la
garantía de ausencia de bloqueos permanentes en uso normal.

Fuentes primarias consultadas:
[CreateMutexExW](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-createmutexexw)
(creación/existencia, colisión de tipo y Local), y
[Mandatory Integrity Control](https://learn.microsoft.com/en-us/windows/win32/secauthz/mandatory-integrity-control)
(DACL más etiqueta y niveles Medium/High). La retención hasta la muerte y la
política compartida con Preview son decisiones de MACE.

## Helper previo, homónimos y LHM de escritorio

No hay búsqueda/terminación por nombre, PID persistido ni identificación de
«huérfanos» por heurística. El helper actual nace dentro de un Job Object con
KILL_ON_JOB_CLOSE mediante JOB_LIST antes de ejecutar código. No hay breakaway;
el helper no hereda el handle del job. Al morir MACE se cierra la última
referencia de ese job y Windows termina sus miembros. La tubería es aleatoria
por ejecución y conserva ACL, primera instancia, rechazo remoto y verificación
de PID; un helper viejo no se reutiliza en otra sesión de transporte.

Evidencia: CTest `lhm_job_abrupt-parent`, `lhm_job_descendants`,
`lhm_job_restrictive-job`, cierre cooperativo/EOF/forzado y pruebas Java kill,
halt y watchdog. `lhm_job_foreign-homonym` inicia DOS procesos con el mismo
ejecutable simulado: uno ajeno al transporte en un job del arnés, otro propiedad
de MACE. Tras cerrar el transporte, el segundo murió y el primero siguió vivo.
Solo después el arnés cerró su propio job para limpiar el proceso ajeno usando
handles retenidos. No se tocó una aplicación ajena real ni un proceso del usuario.

Una versión vieja sin job podría dejar un helper vivo; la reserva actual no lo
detecta ni lo mata. El usuario tendría que revisar su procedencia con las
herramientas de Windows. Un PID/nombre coincidente nunca autoriza terminarlo.

LHM de escritorio del usuario no se usa como servidor ni dependencia y no se
cierra, reinicia o busca para terminarlo. El helper de MACE lleva su propia
copia publicada de LHM. Ambos podrían usar PawnIO y los mismos sensores/buses;
la exclusión de acceso a recursos de hardware corresponde a las bibliotecas y
driver, no al mutex de instancia de la UI. No se ha comprobado aquí la cobertura
ni compatibilidad de esa exclusión entre versiones, ni la contención/lecturas
simultáneas: **NO VERIFICADO**. No se promete que todos los sensores funcionen
simultáneamente ni se instala otro driver para comprobarlo.

## Pruebas automatizadas y resultados

Procesos Windows reales; sensores simulados, sin necesitar hardware. Las ocho
pruebas Java añadidas tienen el siguiente resultado esperado y observado:

| Prueba | Resultado esperado y observado |
|---|---|
| Nombre según identidad | SID/sesión distintos dan nombres distintos; no SID literal |
| Dos contendientes concurrentes | Exactamente ACQUIRED y BUSY; perdedor sale 0; siguiente arranque adquiere |
| Ganador nativo, otro nativo/Preview | BUSY antes de cargar una DLL deliberadamente inexistente; helper original vivo; CP_PollCpuTelemetryV2 devuelve MACE_OK |
| Ganador Preview, otro nativo | BUSY sin cargar DLL; Preview no inicia helper |
| Kill del dueño | Proceso/helper propio terminan; una nueva JVM adquiere |
| Runtime.halt(70) | Sale 70, helper termina por job; nueva JVM adquiere |
| Cierre lento (2,5 s) | Relanzamiento recibe BUSY durante limpieza; al salir queda disponible |
| Limpieza atascada con watchdog real | BUSY durante cierre; halt 70 tras unos 10 s; helper termina; nueva JVM adquiere |

Los test probes no se empaquetan. Solo ellos aceptan un namespace aleatorio
por test. El producto usa el identificador fijo, sin propiedad JVM, variable
de entorno ni argumento que desactive o cambie su reserva. No se necesitó bypass
de desarrollo: las JVM que prueban NativeFunctions directamente no abren la
aplicación, y las de instancia única usan objetos aislados del producto.
`conhost.exe` creado por Windows para java.exe se distingue del helper.

| Regresión | Paso 2 | Paso 3 |
|---|---:|---:|
| Helper | 86 | 86 PASS |
| CTest Development | 57 | 58 PASS (+ homónimo) |
| CTest Production | 58 | 59 PASS (+ homónimo) |
| Java | 286 PASS + 1 omitida | 294 PASS + 1 omitida (295 total) |
| Payload | 8 + rechazo Development | 8 PASS + rechazo Development PASS |
| check-protected-telemetry | PASS | PASS |
| audit-retired-providers | 181 aceptadas | 181 aceptadas; 0 inesperadas |

Se mantienen ABI 1.4 y tamaños CPU 56, GPU 104, inventario GPU 296 y legado
28 bytes, sin tocar offsets, estados o reserved. Sin cambios en NVML, IGCL,
GetSystemTimes/ReadCpuUsagePercent, DXGI ni RAM. CP_Poll* y CP_InitializeTelemetry
no han sido modificados ni esperan por el mutex/helper.

Comandos desde la raíz del repositorio (PowerShell; JDK 22 y dependencias Maven
ya disponibles). El arnés puede requerir ejecutarse fuera de un sandbox que
restrinja MSBuild/tuberías; eso no implica privilegios de administrador:

```powershell
New-Item -ItemType Directory -Force .\native\build\fase2-paso3 | Out-Null
& .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso3\dev
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\build-native.ps1 -BuildDirectory .\native\build\fase2-paso3\prod
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-paso3\dev\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$helperDirectory=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $helperDirectory 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
$env:MACE_WATCHDOG_FAKE_HELPER=(Resolve-Path .\native\build\fase2-paso3\dev\Release\lhm_fake_helper.exe).Path
mvn.cmd -o -f .\app\pom.xml test
$prod=(Resolve-Path .\native\build\fase2-paso3\prod\Release\mace_native.dll).Path
$pawn=(Resolve-Path .\packaging\dist\MACE\app\prerequisites\PawnIO_setup.exe).Path
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-runtime-payload.ps1 -NativeDll $prod -DevelopmentDll $env:MACE_NATIVE_LIB -HelperDirectory $helperDirectory -PawnIoInstaller $pawn -JavaExe "$env:JAVA_HOME\bin\java.exe"
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

Las primeras ejecuciones dentro del sandbox no pudieron compilar nativo o
abrir tuberías del helper; se repitieron fuera y pasaron. El test inicial de
Preview contó conhost como helper; se corrigió esa aserción y pasó. No se
ocultan estos intentos fallidos ni se cuentan como evidencia positiva.
El test omitido de Java es el de compatibilidad con una DLL histórica:
no se definió MACE_LEGACY_DLL. Las ocho pruebas nuevas se ejecutaron sin omisiones.

## Procedimiento manual: fuentes actuales

**NO VERIFICADO visualmente en este paso.** La imagen `packaging/dist/MACE`
todavía corresponde a la anterior: se regenerará al terminar Paso 5. Para
comprobar ahora la instancia única se pueden usar las clases recién compiladas.
Esto es una ejecución de desarrollo, no la validación del MACE.exe elevado
empaquetado. Tras ejecutar los comandos anteriores, usa esta preparación en una
PowerShell situada en la raíz; para High/Medium repítela en ambas consolas:

```powershell
$repo=(git rev-parse --show-toplevel).Trim()
$java='C:\Program Files\Java\jdk-22\bin\java.exe'
$report=[xml](Get-Content .\app\target\surefire-reports\TEST-com.mace.presentation.SingleInstanceProcessTest.xml -Raw)
$cp=($report.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value
$testClasses=(Resolve-Path .\app\target\test-classes).Path
$cp=($cp.Split(';') | Where-Object { $_ -ne $testClasses }) -join ';'
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-paso3\dev\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$pub=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $pub 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
$nativeArgs='--enable-native-access=ALL-UNNAMED -cp "'+$cp+'" com.mace.presentation.MaceLauncher'
$previewArgs='--enable-native-access=ALL-UNNAMED -Dmace.infra.mode=mock -cp "'+$cp+'" com.mace.presentation.MaceLauncher'
```

1. **Doble apertura.** Ejecuta:

   ```powershell
   $first=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   $second=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   ```

   Debe verse un dashboard y un aviso de instancia existente; acepta el aviso.
   La instancia perdedora termina sin helper; el dashboard ganador sigue leyendo.
   En Administrador de tareas, pestaña Detalles, revisa árbol/PID/ruta del helper
   del proceso ganador; no termines por nombre. Cierra el dashboard normalmente.

2. **Cerrar y relanzar rápido.** Abre `$first` como arriba. Justo después de
   cerrar su ventana ejecuta:

   ```powershell
   $second=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   ```

   Si la primera sigue limpiando, aparece el aviso y no un nuevo helper.
   Si ya terminó, el nuevo arranque es correcto. Tras aceptar el aviso, esperar
   a que termine el proceso anterior y repetir, debe abrir. Un fallo que bloquee
   limpieza debe terminar por el watchdog, aproximadamente 10 s después del cierre.

3. **Kill y relanzamiento.** Abre una sola con `$first` y verifica que es la
   que muestra el dashboard antes de ejecutar (solo PID capturado por ti):

   ```powershell
   taskkill.exe /PID $first.Id /F
   $first.WaitForExit()
   $next=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   ```

   El helper del proceso terminado debe desaparecer y `$next` debe abrir sin
   borrar archivos ni reiniciar Windows. Cierra `$next` normalmente.

4. **Misma sesión, elevación mixta.** Abre una PowerShell normal y otra «Ejecutar
   como administrador» del MISMO usuario, prepara ambas con el bloque anterior.
   En la elevada inicia `$nativeArgs`; en la normal inicia `$previewArgs`:

   ```powershell
   # Consola elevada:
   $first=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   # Consola normal (con sus variables preparadas):
   $second=Start-Process -FilePath $java -ArgumentList $previewArgs -WorkingDirectory $repo -PassThru
   ```

   Esperado: Preview recibe el aviso y no cambia la instancia elevada. Cierra
   ambas y repite en orden inverso (Preview normal primero, native elevada
   después). Debe rechazar la elevada antes de crear su helper. Registra avisos,
   tiempos, PID y nivel de integridad en cada orden. Probar con credenciales de
   otro usuario no comprueba este caso, porque cambia la identidad de la reserva.

5. **Coexistencia con LHM del usuario.** Usa tu copia de escritorio ya instalada;
   reemplaza solo la ruta del siguiente comando por la suya:

   ```powershell
   $lhmPath='C:\RUTA_DE_TU_COPIA\LibreHardwareMonitor.exe'
   $lhmUser=Start-Process -FilePath $lhmPath -Verb RunAs -PassThru
   $first=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
   ```

   No ejecutes PawnIO_setup ni cambies HVCI para esta prueba. Observa durante
   al menos un minuto temperaturas/potencia, lecturas que quedan N/D, errores
   y actualizaciones en ambos programas. Cierra MACE: el LHM del usuario debe
   seguir abierto y actualizando. Reabre MACE, cierra LHM desde su UI y comprueba
   que MACE sigue actualizando con su helper. Repite abriendo MACE antes de LHM.
   Registra versiones, privilegios, hardware, estado de PawnIO/HVCI y resultados;
   diferencias numéricas entre programas por sí solas no prueban un fallo.

Al disponer de la imagen del Paso 5, repetir los casos normales con
`Start-Process .\packaging\dist\MACE\MACE.exe -Verb RunAs -PassThru` y Preview
con `Start-Process .\packaging\dist\MACE\MACE-Preview.exe -PassThru`. El uso de
Java directo anterior no sustituye esa comprobación UAC/empaquetado.

## Instalación futura: solo contrato, sin implementación ahora

La actualización/desinstalación debe pedir cierre normal, esperar la muerte del
proceso y liberación de handles/leases del payload antes de sustituir archivos.
El mutex UI no es un bloqueo global del instalador: otros usuarios/sesiones y
versiones antiguas pueden estar ejecutándose. Considerar Restart Manager y los
archivos en uso de todas las sesiones en Fase 3; no inferir seguridad únicamente
de que el mutex local no exista. Si vence el plazo, diferir o pedir intervención
explícita; no matar helper/MACE por nombre ni por PID guardado. El propio
watchdog limita un cierre atascado de la versión actual, pero no autoriza al
instalador a borrar un payload con handles activos. Mantener PawnIO compartido
y sus avisos según el diseño de instalación, sin desinstalarlo por heurística.

## Cambios por archivo, privacidad y límites

| Archivo | Cambio |
|---|---|
| app/src/main/java/com/mace/presentation/WindowsSingleInstance.java | Reserva Win32, identidad/ACL/etiqueta, vida del proceso |
| app/src/main/java/com/mace/presentation/MaceApplication.java | Adquisición en init, aviso y salida previos a adaptadores |
| app/src/test/java/com/mace/presentation/SingleInstanceProbe.java | JVM desechable, modos Preview/native, kill/halt/cierre/watchdog |
| app/src/test/java/com/mace/presentation/SingleInstanceProcessTest.java | Ocho pruebas reales, namespaces exclusivos del arnés |
| native/tests/test_lhm_job.c | Homónimo ajeno conservado durante cierre del helper propio |
| native/CMakeLists.txt | Registro del escenario homónimo en ambos perfiles |
| helper/FASE2-PASO3.md | Este informe, decisiones y procedimientos |

Evidencias ignoradas en `native/build/fase2-paso3`: helper/dev/prod/java/payload/
protected/audit.log; XML CTest dentro de dev/prod, XML Java en
app/target/surefire-reports. Los logs/XML de herramientas contienen rutas de
perfil, classpath y datos del entorno: no se versionan ni se anonimizan los
originales. Este informe y el TSV de referencias usan rutas relativas, sin
copiar identidad de usuario/equipo o rutas personales. No se necesitó modificar
el TSV: las 181 referencias aceptadas y sus motivos siguen vigentes.

NO VERIFICADO: UI de esta versión en ejecución manual; ejecutable completo
elevado e imagen final actualizada; High/Medium y multisesión reales; crash
nativo deliberado; coexistencia física LHM/PawnIO/sensores entre versiones;
hardware Intel/NVIDIA, HVCI y equipos limpios; instalador/actualización/desinstalación
y 3010 reales; firma válida/políticas de certificados. Firma, licencia de MACE y
revisión legal de redistribución siguen pendientes con sus ganchos conservados.
La simulación no acredita lectura de sensores ni permisos de administrador.

No se instaló/desinstaló PawnIO, no se ejecutó su instalador, no se cambió HVCI,
no se regeneró la imagen final ni se implementaron estados/backoff del Paso 4.
El payload mantiene aplicación, DLL, helper publicado, PawnIO intacto y avisos
de terceros. No se necesita otra decisión técnica para cerrar este paso; las
decisiones de firma/licencia/redistribución siguen pendientes. La única
autorización siguiente será continuar al Paso 4.
**Paso 3 terminado; detenerse hasta aprobación para Paso 4.**
