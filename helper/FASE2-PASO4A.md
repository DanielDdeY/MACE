# Fase 2 — Subpaso 4A: recuperación sin cambiar el ABI

Fecha: 2026-11-17. Base: `83348cf0aa9158dfb46b00aa51f0549f706819c2`,
rama `LHM_implementado`, árbol limpio confirmado antes de empezar.
Se implementa solo 4A. Un commit local del subpaso, sin reescribir historial
ni fusionar a principal; el hash del commit se entrega en la respuesta final.

## Diseño y decisiones

### Presupuesto y backoff de la DLL

`lhm_retry.c/h` contiene una política privada, usada únicamente por el hilo
receptor. Hay un arranque inicial y hasta **tres relanzamientos en cualquier
ventana móvil de 60.000 ms activos**. Se registran los instantes de lanzamiento,
también si el intento falla antes de conectar. El arranque inicial no consume
un relanzamiento. No es una ventana fija que permita dos ráfagas en sus bordes.

El backoff rápido parte de 1 s, crece a 2/4/8 s y tiene techo de 8 s. Tras usar
los tres relanzamientos se entra en modo lento: 30/60/120/240/300 s, techo de
300 s, sin límite de agotamiento definitivo. Cada fecha es además como mínimo
el instante en que la ventana móvil permita otro lanzamiento. Los retrasos se
miden DESPUÉS de terminar la limpieza de la sesión anterior, sin solapar helpers.

Ejemplo idealizado de salidas instantáneas y limpieza instantánea: inicial t=0,
relanzamientos t=1, 3, 7 s. El siguiente no sería t=37: se difiere hasta t=61,
cuando el primer relanzamiento sale de la ventana de 60 s. Si vuelve a fallar,
el siguiente sondeo lento es al menos 60 s después. El tiempo real incluye
verificación de integridad, creación, conexión y cierre acotado. No se promete
que el sistema operativo complete esas operaciones en el instante ideal.

**Tres muestras consecutivas utilizables** restauran el backoff a 1 s y salen
del modo lento. Nunca borran los timestamps de relanzamientos: ni un helper que
funcione brevemente puede eludir tres relanzamientos por minuto activo.
Una muestra utilizable exige:

- Mensaje completo decodificado con PID, versión, tamaño, rangos, coherencia,
  identidad, secuencia creciente y antigüedad de como máximo 3.000 ms válidos.
- Helper Ready y al menos una métrica promovible a VALID por las reglas actuales.
  CPU exige elevación/PawnIO Running y no acepta su cero sospechoso; GPU exige
  identidad UniquePCI y coincidencia única en el inventario local. UnknownPower
  no cuenta como potencia válida; una GPU puede tener otras métricas válidas.
- Una racha se rompe con WarmingUp/ReadFailed, sin métricas utilizables o un hueco
  entre recepciones mayor de 3.000 ms. Cada conexión nueva empieza una racha nueva.

Una GPU utilizable puede acreditar salud del transporte aunque CPU no esté
disponible. Esto no promueve CPU a válida ni convierte un estado no válido en un
valor. Se eligen tres muestras para acreditar continuidad durante unos dos
intervalos a 1 Hz, sin esperar innecesariamente a todos los sensores. Un número
de secuencia alto ya no tiene efecto sobre el backoff. Un helper que sigue
enviando mensajes bien formados pero sin sensores válidos no se reinicia solo
por ello: puede tratarse de sensores incompatibles; simplemente no acredita
recuperación de la racha.

### Relojes y suspensión

La política usa QueryUnbiasedInterruptTime / 10.000, monótono y sin tiempo de
suspensión/hibernación. No usa fecha civil ni GetTickCount64 para consumir o
renovar presupuesto. Si esa consulta falla, se degrada a READ_FAILED y se
detiene el receptor, sin cambiar silenciosamente a otro reloj incompatible.
El cooldown de PawnIO utiliza el mismo tipo de reloj en el helper.

La edad de la lectura conserva el reloj del protocolo: GetTickCount64 / .NET
10 Environment.TickCount64, que incluye suspensión. Una caché anterior a una
suspensión de más de 3 s se vuelve READ_FAILED al consultarla; no se mantiene
VALID mientras se espera una lectura nueva. La racha también se rompe tras
ese hueco. Una lectura pendiente puede fallar al reanudar y causar un reintento
contabilizado, pero dormir no vacía ni restaura artificialmente el presupuesto.
Los plazos pendientes de reintento/cooldown continúan con su tiempo activo
restante; no se disparan ráfagas por el tiempo pasado dormido.

Las simulaciones adelantan tiempo lógico o mantienen congelado el reloj activo.
Suspensión/reanudación físicas: **NO VERIFICADO**. Fuente primaria:
[QueryUnbiasedInterruptTime](https://learn.microsoft.com/en-us/windows/win32/api/realtimeapiset/nf-realtimeapiset-queryunbiasedinterrupttime).

### PawnIO en caliente dentro del helper

Se elige **recrear el muestreador en el mismo proceso**, en vez de relanzar el
helper. `RecoverableSampler` observa Missing/Stopped → Running a 1 Hz. Cierra el
Computer anterior y crea/abre uno nuevo, nunca dos simultáneos. El primer paso
a Running se atiende inmediatamente; nuevas transiciones se agrupan con un
enfriamiento mínimo de **30 s activos** entre recuperaciones. Una transición
pendiente se atiende al vencer el cooldown si PawnIO está Running.

Durante una recuperación diferida la CPU queda WARMING_UP, sin cifras del
muestreador viejo; las GPU siguen su evaluación independiente. Justo antes de
reabrir se envía el WarmingUp ya existente; luego vuelven las lecturas normales.
Al observar PawnIO detenido/ausente, CPU queda NO_PROVIDER/0. Al volver Running
se recrean los objetos y solo se promueven lecturas que superen la política de
validez. Sin elevación CPU sigue NO_PROVIDER: arrancar el driver no sustituye
los permisos del proceso. Unknown no inventa una transición desde detenido,
pero tampoco borra una ausencia/parada ya observada.

No hay relanzamiento de proceso por esta transición: consume **cero** del
presupuesto de la decisión 7. Si Dispose/Open se cuelga, el receptor deja de
recibir muestras, vence su plazo de 3 s y contiene al helper por su job. Si
Open lanza una excepción, el helper sale; esa salida anómala sí se trata como
un fallo ordinario sujeto al presupuesto. El antibucle no oculta esos fallos.

La selección se contrastó con LHM **v0.9.6**: Computer.Close retira/cierra grupos,
un nuevo Open crea nuevos grupos, y los objetos CPU abren sus módulos PawnIO
por instancia. Esto respalda recrear el contexto sin reiniciar el proceso; no
es evidencia física de recuperación de todos los sensores/modelos. Fuentes:
[Computer.cs](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LibreHardwareMonitorLib/Hardware/Computer.cs),
[IntelCpu.cs](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LibreHardwareMonitorLib/Hardware/Cpu/IntelCpu.cs),
[IntelMsr.cs](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LibreHardwareMonitorLib/PawnIo/IntelMsr.cs),
[Amd17Cpu.cs](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LibreHardwareMonitorLib/Hardware/Cpu/Amd17Cpu.cs).

Límite: un ciclo de parada/arranque completo entre dos sondeos podría no
observarse. La prueba manual debe dejar cada estado durante varios sondeos.
La reapertura afecta también al contexto GPU de LHM durante su duración; las
primarias NVML/IGCL siguen independientes y no se han tocado.

Alternativas descartadas: usar sequence>=3 no demuestra salud; resetear el
presupuesto con muestras buenas permite ráfagas; un contador agotado para
siempre requiere reiniciar ante un fallo transitorio; usar tiempo civil o
uptime para el presupuesto lo altera con cambios de hora/suspensión. Reiniciar
todo el helper por PawnIO añade proceso/tubería/verificación/estado de transición
entre procesos, innecesarios para la recreación elegida. No se añade un comando
de control ni campos al protocolo para reinicializar sensores.

## Tabla de estados y fallos: exclusivamente códigos existentes

| Causa/caso | Política de recuperación | Qué reciben las métricas afectadas en V2 / UI actual |
|---|---|---|
| Arranque o reapertura | Esperar muestras frescas | WARMING_UP (5), valor 0, presentación de calentando existente |
| Helper ausente/ruta no válida | Sin bucle; reparar y cerrar/reabrir MACE | NO_PROVIDER (2), valor 0, N/D |
| Runtime inaccesible o confianza rechazada, hash/firma/manifiesto | No reintentar automáticamente; reparar distribución/permisos y reiniciar MACE | READ_FAILED (6), 0, N/D genérico; no se afirma qué verificación concreta falló |
| Job/JOB_LIST/contención fallida | Fallo cerrado sin bucle; resolver restricción y reiniciar | READ_FAILED (6), 0, N/D |
| Creación contenida bloqueada | Se exige intervención; incluye fallos de CreateProcess que no permiten atribuir con certeza la causa al job | READ_FAILED (6), 0; no se etiqueta falsamente todo CreateProcess fallido como fallo de job |
| EOF, tubería rota, PID/versión/mensaje/replay inválido | Invalidar al detectar; cerrar exclusivamente sesión propia y contar siguiente relanzamiento | READ_FAILED (6), 0, N/D hasta datos nuevos |
| Sale inmediatamente en bucle | Inicial + 3/60 s activos; después sondeos lentos hasta techo 300 s | READ_FAILED (6), 0 durante espera; agotamiento no permanente |
| Se cuelga sin muestras | 3.000 ms por mensaje, invalidación por edad; cierre cooperativo 2 s y confirmación forzada hasta 1 s, como antes | READ_FAILED (6), 0; termina por job/handle propio, nunca nombre/PID almacenado |
| PawnIO Missing o Stopped | No reinicia el proceso; observar transición futura | Temperatura/potencia CPU NO_PROVIDER (2), 0; GPU independiente |
| PawnIO vuelve Running durante cooldown | Transición pendiente agrupada, sin cifras CPU viejas | CPU WARMING_UP (5), 0; después validación normal |
| Sin elevación | Sin elevar automáticamente ni relanzar por ello | CPU NO_PROVIDER (2), 0 en 4A; motivo específico queda para 4B |
| Tres muestras utilizables seguidas | Salir del modo lento, backoff 1 s; conservar historial de relanzamientos | Cada métrica conserva su propio estado; no se fuerza VALID global |
| Suspensión larga | Caché caduca; presupuesto/cooldown no avanzan dormido | READ_FAILED (6) hasta muestra nueva, luego evaluación normal |

No hay aviso nuevo de reintentos agotados ni mapeo 3010 en 4A. Los motivos
privados de apertura son datos internos C, no estados añadidos a V2. No se
infiere reinicio pendiente de PawnIO detenido. Los mensajes específicos,
estados aditivos, versión de ABI y contrato del 3010 quedan para **4B**.

## Pruebas y regresión

| Prueba añadida | Resultado esperado y comprobado sin hardware |
|---|---|
| 6 casos de política C | Presupuesto móvil; racha de 3; fallo permanente; suspensión simulada; recuperación; techo lento 300 s |
| retry-loop | Cuatro intentos iniciales totales; READ_FAILED/0; modo lento y quinto intento posterior, sin bloqueo permanente |
| retry-recover | Igual agotamiento; quinto helper válido; tres muestras restauran modo rápido; VALID |
| retry-streak | Primera salida consume reintento; secuencias >1000 con métricas Unsupported no reinician backoff; dos buenas tampoco, tercera sí |
| retry-hung | WarmingUp, luego READ_FAILED/0; proceso propio colgado termina por job y un segundo helper recupera VALID |
| retry-broken | VALID → READ_FAILED → VALID con un relanzamiento contabilizado |
| retry-invalid | Mensaje inválido → READ_FAILED/0 → nuevo helper VALID; un relanzamiento contabilizado |
| retry-pawn | Missing → Running → Stopped → Running: CPU NO_PROVIDER → VALID → NO_PROVIDER → VALID; un único proceso, cero relanzamientos |
| permanent-missing/trust/job | NO_PROVIDER o READ_FAILED según tabla, intentos 0/1/1, política bloqueada y receptor termina sin bucle; ningún helper ejecutado |
| 19 controles C# nuevos | Factory falsa prueba cierre/apertura secuenciales, recuperación, coalescencia, cooldown activo, CPU calentando pendiente y ausencia de bucles |

Los seis casos C puros usan reloj inyectado. Los diez de receptor usan procesos,
tuberías y jobs Windows reales con un helper C simulado; una DLL de arnés
separada compila la escala temporal **10** solo para el reloj de reintentos
(60 s lógicos = 6 s activos reales). La caducidad del wire sigue en 3.000 ms
reales. Los hooks de evidencia y esa escala no se activan en mace_native.dll;
no hay variable de entorno ni opción runtime para modificar el presupuesto
del producto. Las pruebas C# ejecutan el recuperador real con sensores falsos;
no equivalen a una transición real de PawnIO ni a lecturas CPU físicas.

| Regresión | Paso 3 | 4A |
|---|---:|---:|
| Helper | 86 | 105 PASS (+19) |
| CTest Development | 58 | 74 PASS (+16) |
| CTest Production | 59 | 75 PASS (+16) |
| Java | 294 PASS + 1 omitida | 294 PASS + 1 omitida (295 total) |
| Payload | 8 + rechazo Development | 8 PASS + rechazo Development PASS |
| check-protected-telemetry | PASS | PASS |
| audit-retired-providers | 181 aceptadas, 0 inesperadas | 181 aceptadas, 0 inesperadas |

La omisión Java sigue siendo compatibilidad con una DLL histórica no aportada
(MACE_LEGACY_DLL). Las pruebas de instancia única, job, EOF/control, cierre
acotado y watchdog siguen pasando. La primera pasada de producción encontró
dos fallos del arnés: esperaban 200 ms tras iniciar el cuarto intento, antes
de que acabara su limpieza. Se cambió a esperar la transición observada a modo
lento y se repitieron ambas configuraciones. Un primer arranque del comando de
publicación no pudo redirigir el log porque su carpeta aún se estaba creando;
se corrigió el orden y se publicó con éxito. No se cuentan esos intentos como PASS.

Comandos desde la raíz, con toolchain/JDK 22/dependencias disponibles:

```powershell
New-Item -ItemType Directory -Force .\native\build\fase2-paso4a | Out-Null
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4a\dev
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\build-native.ps1 -BuildDirectory .\native\build\fase2-paso4a\prod
# Solo los 16 casos nuevos, ejecutados directamente (desde esta misma raíz):
foreach ($case in 'budget','streak','permanent','suspend','recovery','slow-cap') {
    & .\native\build\fase2-paso4a\dev\Release\test_lhm_retry_policy.exe $case
    if ($LASTEXITCODE) { throw "Fallo $case" }
}
$fake=(Resolve-Path .\native\build\fase2-paso4a\dev\Release\lhm_fake_helper.exe).Path
foreach ($case in 'retry-loop','retry-recover','retry-streak','retry-hung','retry-broken','retry-invalid','retry-pawn','permanent-missing','permanent-trust','permanent-job') {
    & .\native\build\fase2-paso4a\dev\Release\test_lhm_retry_receiver.exe $fake $case
    if ($LASTEXITCODE) { throw "Fallo $case" }
}
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-paso4a\dev\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$pub=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $pub 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
$env:MACE_WATCHDOG_FAKE_HELPER=$fake
mvn.cmd -o -f .\app\pom.xml test
$prod=(Resolve-Path .\native\build\fase2-paso4a\prod\Release\mace_native.dll).Path
$pawn=(Resolve-Path .\packaging\dist\MACE\app\prerequisites\PawnIO_setup.exe).Path
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-runtime-payload.ps1 -NativeDll $prod -DevelopmentDll $env:MACE_NATIVE_LIB -HelperDirectory $pub -PawnIoInstaller $pawn -JavaExe "$env:JAVA_HOME\bin\java.exe"
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

El helper se debe publicar **antes** de compilar producción: su nuevo manifiesto
se fija en la DLL. No mezclar DLL antigua con helper nuevo. Maven offline necesita
las dependencias ya restauradas. Si se quiere repetir el smoke real de CPU,
en una PowerShell elevada usar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4a\dev -RealHelper -RequireCpu
```

Ese smoke real no se ejecutó con RequireCpu en este paso ni instala drivers.

## Procedimiento manual y NO VERIFICADO

La imagen final sigue sin regenerar hasta terminar Paso 5. Para UI actual desde
fuentes, tras la batería anterior preparar PowerShell en la raíz:

```powershell
$java='C:\Program Files\Java\jdk-22\bin\java.exe'
$report=[xml](Get-Content .\app\target\surefire-reports\TEST-com.mace.presentation.SingleInstanceProcessTest.xml -Raw)
$cp=($report.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value
$testClasses=(Resolve-Path .\app\target\test-classes).Path
$cp=($cp.Split(';') | Where-Object { $_ -ne $testClasses }) -join ';'
$repo=(git rev-parse --show-toplevel).Trim()
$nativeArgs='--enable-native-access=ALL-UNNAMED -cp "'+$cp+'" com.mace.presentation.MaceLauncher'
$mace=Start-Process -FilePath $java -ArgumentList $nativeArgs -WorkingDirectory $repo -PassThru
sc.exe query PawnIO
```

El bloque reutiliza MACE_NATIVE_LIB/SHA256/MACE_LHM_HELPER/SHA256 preparados
arriba, apuntando al helper publicado real, NO al ejecutable simulado. Es una
ejecución de desarrollo; no sustituye validar el MACE.exe empaquetado elevado.

- En un equipo/VM de pruebas con PawnIO ya instalado y la consola elevada,
  se puede observar la parada/arranque sin instalar ni desinstalar nada:

  ```powershell
  sc.exe stop PawnIO
  Start-Sleep -Seconds 3
  sc.exe query PawnIO
  sc.exe start PawnIO
  sc.exe query PawnIO
  ```

  Hacerlo solo como prueba manual elegida por el usuario, fuera de sesiones de
  otras herramientas de sensores. Si el driver rechaza detenerse o sigue en
  estado pendiente, registrar NO VERIFICADO; no forzar, reinstalar ni cambiar
  HVCI. Esperado: CPU N/D al observar detenido; luego calentando y lectura válida
  si permisos/hardware lo permiten, mismo PID del helper. Repetir tras al menos
  30 s; en ciclos más rápidos CPU puede esperar calentando hasta el cooldown.
  Registrar PID/tiempos/estados antes y después. La transición física Missing →
  Running no se debe fabricar instalando PawnIO en este paso: está simulada.
- Para suspensión: con MACE leyendo, suspender desde el menú de Windows durante
  más de 3 s y reanudar. Esperado: no conservar cifras válidas antiguas; recupera
  lecturas frescas o queda N/D mientras se respeta el presupuesto. Si ya estaba
  en modo lento, dormir no debe agotar ni renovar su tiempo activo de espera.
- Para salidas en bucle/recuperación/PawnIO sin tocar el equipo, ejecutar los
  diez casos simulados anteriores; cada uno imprime estados/presupuesto
  comprobados y debe salir con código 0. Para comportamiento de 60 s/300 s
  reales, la DLL del producto usa escala 1; el arnés acelerado no demuestra
  duración física de esos plazos en una sesión real prolongada.

NO VERIFICADO físicamente: transiciones PawnIO reales y recuperación de CPU;
suspensión/hibernación reales; sensores Intel/NVIDIA y otros modelos; convivencia
con LHM de escritorio; ejecución elevada completa con imagen final actualizada;
cruce de integridades/múltiples sesiones; instalador/origen 3010 y equipos
limpios/HVCI; certificado/firma de publicador y revisión legal de redistribución.
Las pruebas Java con DLL y helper publicados son reales en cuanto a integración,
pero no demuestran esos cambios del driver ni disponibilidad de todas las métricas.

## Cambios por archivo y compatibilidad

| Archivo | Cambio |
|---|---|
| native/src/telemetry/lhm_retry.c y lhm_retry.h | Política privada, ventana móvil, backoff y recuperación lenta |
| native/src/telemetry/lhm_receiver.c | Reloj activo, muestras utilizables, clasificación de fallos; hooks solo en arnés |
| native/src/telemetry/lhm_transport.c y lhm_transport.h | Causas privadas de apertura; espera sensible a muerte del hijo; misma contención/cierre |
| helper/Mace.Lhm.Helper/RecoverableSampler.cs | Recreación serializada, transición/cooldown y CPU calentando pendiente |
| helper/Mace.Lhm.Helper/SensorSampler.cs | Implementa interfaz del muestreador, selección de sensores intacta |
| helper/Mace.Lhm.Helper/Program.cs | Observación de transición y reapertura con WarmingUp existente |
| helper/Mace.Lhm.Helper/Mace.Lhm.Helper.csproj | Versión de aplicación helper 1.1.1; mismo protocolo 2 |
| helper/Mace.Lhm.Tests/RecoveryTests.cs y Program.cs | 19 controles nuevos sin hardware |
| native/tests/test_lhm_retry_policy.c | Seis casos deterministas de política |
| native/tests/test_lhm_retry_receiver.c | Diez casos de procesos/estados/presupuesto |
| native/tests/lhm_retry_evidence.h y lhm_retry_fixture.h | Evidencia privada del arnés, no exportada por DLL de producto |
| native/tests/lhm_fake_helper.c | Escenarios de salida, colgado, invalidación y estados de PawnIO simulados |
| native/CMakeLists.txt | Fuente de política, DLL separada y registro de pruebas |
| helper/PROTOCOL.md | Documenta nueva política sin cambiar bytes ni versión del protocolo |
| helper/FASE2-PASO4A.md | Este informe y procedimiento |

ABI público **1.4 intacto**, DLL 1.4.2, tamaños CPU 56/GPU 104/inventario 296/
legado 28, offsets y reserved sin cambios. Ningún estado nuevo; wire privado 2
conserva exactamente campos/tamaños/control/EOF. Se publica helper 1.1.1 y se
fija su manifiesto en producción; 1.1.0 conserva compatibilidad de wire, pero
carece de recuperación en caliente. Distribuir siempre DLL y runtime fijado
juntos, no sustituir componentes individualmente.
Se compararon los exports de producción con la DLL del Paso 3 mediante
dumpbin: **24 nombres idénticos**, sin LhmReceiver_TestEvidence; evidencia en
`native/build/fase2-paso4a/exports.log`.

No se tocaron NVML, IGCL, GetSystemTimes/ReadCpuUsagePercent, DXGI ni RAM;
CP_Poll* y CP_InitializeTelemetry no esperan por helper/reintentos. Instancia
única, leases, ACL/PID, job, cierre acotado y watchdog siguen pasando regresión.
El staging conserva aplicación, DLL, helper publicado, PawnIO intacto y avisos
de terceros (214 archivos), sin ejecutar su instalador.

Evidencias locales ignoradas: `native/build/fase2-paso4a/{helper,dev,prod,java,
payload,protected,audit}.log`, XML de CTest y `app/target/surefire-reports`.
Pueden contener rutas de perfil/classpath/identidad del entorno: no se suben
ni se anonimizan los originales. Informe y TSV solo incluyen rutas relativas,
rutas genéricas de herramientas o placeholders, sin datos personales nuevos.
El TSV conserva sus 181 coincidencias aceptadas con motivo; no requiere cambios.

No se instaló/desinstaló PawnIO, no se ejecutó su instalador, no se cambió HVCI,
manifiesto UAC o launchers, ni se regeneró packaging/dist/MACE. Firma/licencia/
revisión legal siguen pendientes con sus ganchos. No se necesita cambio de ABI
o protocolo ni otra decisión para cerrar 4A.
**DETENERSE aquí: 4B requiere aprobación del usuario.**
