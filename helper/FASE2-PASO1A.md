# Fase 2 — Paso 1A: Job Object y cierre acotado

Fecha: **16/11/2026, America/Lima**. Rama: **LHM_implementado**.

## Alcance y commits

El usuario aprobó el inventario y exigió separar 1A, 1B y los cambios de estados.
Primero se guardó el informe aprobado en el commit
**bcc7f1c9615084a4ff2d29a8e13874f6165f3b64 — docs: Fase 2 Paso 0**.

Este informe forma parte del commit independiente de implementación del Paso 1A
(mensaje: **feat: Fase 2 Paso 1A bounded helper lifecycle**). Su identificador
queda disponible en git log y se entrega en el chat tras crear el commit.
No se reescribe historial ni se fusiona a principal.

**Resultado de 1A:** creación del helper dentro del job mediante JOB_LIST;
comprobación de pertenencia antes de reanudar; cierre público con presupuesto
de espera; conservación segura de trabajadores tardíos y apagado Java fuera
del hilo JavaFX. No se cambia ABI, protocolo, estados ni manifiesto UAC.

## Creación dentro del job

LhmTransport_Open() crea un job anónimo no heredable con
JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE y sin banderas de breakaway. Mantiene
CREATE_SUSPENDED, CREATE_NO_WINDOW y EXTENDED_STARTUPINFO_PRESENT.

La lista de atributos tiene dos entradas: HANDLE_LIST para stdin/NUL y JOB_LIST
para asociar el job durante CreateProcessW. Ya no existe el intervalo separado
CreateProcessW -> AssignProcessToJobObject. Antes de ResumeThread se confirma
IsProcessInJob contra el job exacto de la sesión y se consulta la señal de parada.

Si la plataforma, los atributos o un job envolvente impiden esa contención,
no se reanuda un proceso sin job ni se intenta escapar del job existente.
La vía LHM queda degradada con el READ_FAILED ya existente; Windows y las
primarias independientes continúan. La explicación específica hacia la UI
se implementará en el Paso 4, sin inventar un estado nuevo en 1A.

El job no figura entre los handles heredados. La sesión conserva su propiedad;
cerrar el último handle al morir el padre aplica la política de terminación.
Los descendientes normales heredan la contención. La prueba nueva verifica
también un descendiente deliberado del helper simulado y procesos conhost
que Windows puede incluir; no asume una cantidad fija de procesos auxiliares.

Se conservan nombre aleatorio, bootstrap por stdin, ACL, primera instancia,
rechazo de remotos y autenticación por PID. No se transmite información nueva
por el protocolo. No se descubre ni termina LHM del usuario por nombre o PID.

Referencias contrastadas:
[UpdateProcThreadAttribute](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute),
[Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)
y [AssignProcessToJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-assignprocesstojobobject).
La semántica documentada de Windows se distingue de las pruebas ejecutadas abajo.

## Plazos y propiedad durante el cierre

| Operación | Política implementada en 1A |
|---|---|
| Aviso al receptor y trabajador GPU | Se señalan sus eventos al solicitar el cierre; no se espera para solicitar la parada |
| Cierre de sesión del helper | EOF al cerrar la tubería; hasta 2000 ms de salida cooperativa y hasta 1000 ms para confirmar terminación forzada |
| Terminación forzada | Solo el job de esa sesión; el fallback de limpieza conserva únicamente el handle de proceso poseído, nunca búsquedas por nombre/PID |
| Espera inicial de los trabajadores | Ambos reciben parada antes de esperar; comparten un deadline de 3000 ms |
| CP_ShutdownTelemetry() | Presupuesto total de espera de 4000 ms por llamada, incluidas llamadas concurrentes; no espera indefinidamente al coordinador |
| Trabajador que excede el plazo | Se retienen sus handles, eventos y estado; el coordinador comprueba su finalización en intervalos de hasta 100 ms |
| Inicialización durante cierre pendiente | MACE_E_BUSY inmediatamente; no empieza otra generación sobre recursos en uso |
| Sondeos tras pedir el cierre | MACE_E_NOT_INITIALIZED; no se esperan el helper ni el coordinador |

**El plazo de 4000 ms es un presupuesto de espera en código de usuario, no una
garantía de tiempo real del planificador ni de todas las llamadas del kernel.**
Una ejecución anterior observó 4015 ms por granularidad/planificación; la prueba
acepta menos de 4700 ms y comprueba además la recuperación segura. No se afirma
latencia física nula ni un máximo universal exacto de 4,000 segundos.

El cierre cooperativo de 1A sigue usando EOF. No se presenta como la orden
explícita solicitada para 1B: el canal aún es unidireccional y LHM1/MCB1 no cambian.

El coordinador nativo es único mientras sigue activo. Los llamadores concurrentes
esperan mediante handles duplicados al mismo trabajador. El cierre desactiva
sondeos antes de esperar a los hilos, sin mantener el bloqueo de sondeo durante
las llamadas al helper o al proveedor. El coordinador cierra caja negra y
buffers después de que los trabajadores dejen de utilizarlos.

No se usa TerminateThread. Si una llamada del proveedor no retorna, la limpieza
puede seguir pendiente en segundo plano; no se cierran sus eventos ni se
reutilizan sus cachés. El proceso puede salir y Windows termina sus hilos.
La DLL queda fijada hasta salir del proceso mediante GetModuleHandleExW/PIN,
también si se solicita cierre antes de inicializar. Así un consumidor C que
llame FreeLibrary no descarga código todavía usado por un trabajador pendiente.
Esta política de vida útil es deliberada; no modifica la firma de ningún export.

La cancelación del I/O solapado conserva GetOverlappedResult con espera de
finalización para no liberar OVERLAPPED/buffers aún usados por Windows. Esa
espera de kernel queda en el receptor retenido, fuera del plazo público; no se
declara que cualquier I/O del kernel o SDK pueda interrumpirse en un tiempo fijo.
Si el receptor no termina dentro del deadline, se solicita terminación de su
job activo. Los joins explícitos con INFINITE de los trabajadores se retiraron.

Si falla crear el coordinador por falta de recursos, ya se han señalado las
paradas y queda inhabilitada la inicialización; se emite solo un código textual
genérico de depuración. Una llamada posterior de cierre puede intentar crear
el coordinador. Esta condición extrema no se presenta como probada físicamente.

## JavaFX

MaceApplication consume el evento de cierre y evita la salida implícita al
ocultar la última ventana. En JavaFX solo se desconectan vistas, HUD y
suscripciones. MonitoringService, publicador y adaptadores se cierran en el
trabajador dedicado mace-shutdown, conservando ese orden.

ShutdownCoordinator usa un único propietario; solicitudes repetidas de cierre
o stop() no vuelven a cerrar la telemetría. Una excepción de un recurso no impide
cerrar los siguientes. El trabajador no es daemon para permitir la limpieza
nativa antes de salir normalmente. Al completar un cierre iniciado desde la
ventana, solicita Platform.exit() por runLater. stop() nunca hace join ni vuelve
a programar otra salida si el toolkit ya está apagándose.

Las llamadas nativas y las esperas de monitoreo/persistencia no se ejecutan en
el hilo de JavaFX. No se promete un límite universal del proceso completo frente
a I/O de disco o llamadas ajenas al helper que no retornen; el plazo público
nativo sí se comprueba con un proveedor simulado lento.

## Cambios por archivo

Rutas relativas a la raíz del repositorio.

| Archivo | Cambio |
|---|---|
| native/src/telemetry/lhm_transport.c | JOB_LIST, confirmación de pertenencia, contención del job activo y cierre 2000 + 1000 ms |
| native/src/telemetry/lhm_transport.h | Operación interna para abortar únicamente el job activo poseído |
| native/src/telemetry/lhm_receiver.c | Separar solicitud de parada y join con deadline; conservar recursos si no termina |
| native/src/telemetry/lhm_receiver.h | Interfaces internas RequestStop/StopUntil |
| native/src/telemetry/legacy_telemetry.c | Mismo cierre acotado del trabajador GPU, sin tocar NVML/IGCL ni su sondeo |
| native/src/telemetry/legacy_telemetry.h | Interfaces internas RequestStop/StopUntil |
| native/src/internal/mace_runtime.c | Coordinador, presupuesto público, estado de cierre, cuarentena y pin de DLL |
| native/src/internal/mace_runtime.h | Documentar propiedad, plazo y vida útil de trabajadores |
| native/include/mace_api.h | Solo comentarios del cierre y pin; firmas públicas intactas |
| native/CMakeLists.txt | Añadir targets de prueba y seis casos CTest; no se instalan |
| native/tests/lhm_fake_helper.c | Escenarios de cierre colgado y descendiente dentro del job, sin cambiar el wire format |
| native/tests/lhm_fixture_providers.c | Retraso de cierre configurable únicamente en la DLL de pruebas |
| native/tests/test_lhm_job.c | Pruebas de job real, descendientes, cierre forzado, muerte de padre y restricción de procesos |
| native/tests/test_lhm_shutdown.c | Tres cierres concurrentes, plazo, NOT_INITIALIZED, BUSY y recuperación tras trabajador tardío |
| app/src/main/java/com/mace/presentation/MaceApplication.java | Separar desconexión de UI y cierre de recursos fuera de JavaFX; stop idempotente |
| app/src/main/java/com/mace/presentation/ShutdownCoordinator.java | Único propietario de limpieza en segundo plano y continuación ante errores |
| app/src/test/java/com/mace/presentation/MaceApplicationStopTest.java | Conservar la prueba de error de vista; adaptar expectativas al cierre asíncrono y comprobar una sola llamada |
| app/src/test/java/com/mace/presentation/ShutdownCoordinatorTest.java | Tres pruebas de respuesta JavaFX, orden ante errores y solicitudes concurrentes |
| helper/FASE2-PASO1A.md | Este informe y límites |

## Estados observables sin ampliación del contrato

| Situación | Resultado existente, comprobado o conservado |
|---|---|
| Arranque pendiente | WARMING_UP según caché; no se promueve una lectura todavía no válida |
| Job restrictivo impide crear helper | CPU temperatura READ_FAILED con valor marcador 0; uso Windows VALID; la app sigue operativa |
| Cierre solicitado | CP_PollCpuTelemetryV2 devuelve MACE_E_NOT_INITIALIZED; no hay nuevo snapshot válido |
| Limpieza nativa todavía pendiente | CP_InitializeTelemetry devuelve MACE_E_BUSY; no se reemplaza un trabajador en uso |
| Limpieza termina | Se permite una nueva inicialización segura |
| Motivo específico del job/cierre hacia la UI | Pendiente del Paso 4; en 1A no se añaden estados ni se reutilizan reserved |

No cambian valores públicos, offsets, tamaños ni versión ABI: ABI 1.4,
CPU V2 56 B, GPU V2 104 B, inventario 296 B y TelemetryData 28 B.
No se modifica PROTOCOL.md, WireProtocol.cs ni Program.cs del helper.
Las reglas de validez de CPU/GPU, incluidos los ceros legítimos, se conservan.

## Resultados de pruebas

| Batería | Base aprobada | Resultado de 1A |
|---|---|---|
| Helper | 63 aprobadas | 63 aprobadas; ejecutable de pruebas existente, fuentes del helper sin cambios |
| CTest | 40 aprobadas | 46 aprobadas; se conservan las 40 y se añaden seis |
| Java | 278 aprobadas + una omitida | 281 aprobadas + una omitida; se conservan los casos existentes y se añaden tres |
| Proveedores y layouts protegidos | Intactos en 1D | check-protected-telemetry.ps1 aprobado |
| Auditoría de referencias retiradas | 181 justificadas | 181 justificadas, ninguna inesperada |

Los resultados finales se comprueban sobre la DLL compilada antes de calcular
su hash y ejecutar Java. No se usa una compilación simultánea al consumo del DLL.

| Caso nuevo | Ejecución y criterio |
|---|---|
| lhm_job_contained | APIs reales de Windows con helper simulado; pertenencia al job, flags y propiedad de handles |
| lhm_job_forced-close | Helper simulado mantiene el proceso vivo sin atender EOF; termina tras aproximadamente 2 s, antes del límite de prueba de 3,5 s |
| lhm_job_descendants | Dos ejecutables simulados más auxiliares de consola dentro del job; todos los handles observados quedan señalados tras cerrar |
| lhm_job_abrupt-parent | Se termina el padre de prueba exacto mediante su handle; Windows termina el helper propio al cerrar el último handle del job |
| lhm_job_restrictive-job | Job real con ActiveProcessLimit=1 impide crear helper; CPU READ_FAILED/0 y uso Windows VALID; sin fallback sin job |
| lhm_shutdown_budget | Tres cierres concurrentes frente a proveedor simulado con retraso de 6500 ms; plazo público menor de 4700 ms, sondeo NOT_INITIALIZED, init BUSY y recuperación posterior |
| JavaFX y cierre | Un cierre bloqueado mediante latch no impide procesar eventos JavaFX; sin cierre nativo duplicado; orden y continuación tras error comprobados |

Estas son pruebas del ciclo de vida y de las APIs reales de Windows con sensores
simulados; no constituyen validación física adicional de hardware.
Las pruebas de integración Java usan la DLL nueva y el helper real configurado
en el equipo local; no sustituyen la matriz pendiente de plataformas.

Incidencias resueltas durante la verificación: el sandbox negó acceso al pipe
privado y la compilación no se completó en aislamiento; se repitieron fuera de
él. La prueba de descendientes se corrigió para admitir conhost y normalizar
separadores de ruta. La prueba existente de stop se adaptó al contrato asíncrono.
Una ejecución Java rechazó un hash obtenido antes de una recompilación simultánea;
se corrigió la secuencia de ejecución y se repitió con el binario estable.

## Reproducción

Desde la raíz de mace-native-dev, con MSVC/Windows SDK, JDK 22 y Maven disponibles:

```powershell
# Reconstrucción y regresión nativa, incluidas las seis pruebas nuevas.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-1a\compiled
```

Helper, sin cambios en 1A, si ya existe el ejecutable compilado:

```powershell
& .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe
```

Si no existe, helper/Build-Helper.ps1 reconstruye, prueba y publica el helper
con el SDK fijado. No instala PawnIO. Para Java, terminar primero la compilación
nativa; no recompilar la DLL mientras se consumen sus bytes:

```powershell
# Ajustar JAVA_HOME a un JDK 22 instalado en el equipo de prueba.
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-1a\compiled\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$helperDirectory=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $helperDirectory 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
mvn.cmd -o -f .\app\pom.xml test
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

El modo Maven -o usa las dependencias ya disponibles. En un equipo sin esas
dependencias debe restaurarse previamente con acceso de red. Estas anulaciones
siguen siendo las existentes; su separación desarrollo/producción corresponde
al Paso 2, no se presenta como implementada en 1A.

Evidencia generada e ignorada por Git: native/build/fase2-1a/helper.log,
native.log, java.log, compiled/lhm1b-tests.xml y
compiled/Testing/Temporary/LastTest.log. Los XML Java quedan en
app/target/surefire-reports/. Pueden contener rutas/propiedades del equipo por
el framework; no se incluyen en producto ni se publican. Los diagnósticos nuevos
de ejecución no escriben perfiles, nombres de usuario, nombres de equipo ni PID.

## Riesgos, pendientes y NO VERIFICADO

- Inspección visual y cierre/kill manual de la ventana principal completa de
  MACE con esta compilación: NO VERIFICADO. Sí se ejecutaron el padre de prueba
  real, el job restrictivo real y la prueba de respuesta JavaFX.
- Crash/kill en todos los instantes de la llamada del kernel, fallo de asignación
  de memoria/handles y averías de kernel/controlador: NO VERIFICADO físicamente.
- Un proveedor que no retorne nunca puede dejar un coordinador retenido hasta
  salir del proceso. No se afirma que sea seguro matar el hilo o descargar su DLL.
- La prueba tarda 6500 ms de forma deliberada y comprueba la recuperación; no
  equivale a demostrar que todo SDK/controlador físico bloquee y se recupere igual.
- Coexistencia con LHM del usuario, suspensión/reanudación, CPU Intel, NVIDIA
  NVML y respaldo, Intel GPU/IGCL, PawnIO en equipo limpio, HVCI actual e instalación
  MSI/EXE: NO VERIFICADO, sin cambios respecto al alcance aceptado.
- No se regeneró packaging/dist/MACE; conserva la imagen de 1D. Las pruebas
  nuevas usan native/build/fase2-1a/compiled y app/target, no ese producto antiguo.
- Canal dúplex y mensaje versionado: Paso 1B. Firma y manifiesto de confianza:
  Paso 2. Instancia única: Paso 3. Estados aditivos, backoff por muestras válidas,
  recuperación del límite de reinicios, PawnIO en caliente y mapeo 3010: pasos
  posteriores aprobados, sin adelantarlos aquí.
- No se requiere una decisión nueva para aplicar este commit. La política de
  conservación segura de trabajadores se hace explícita como límite operativo.

Firma de MACE, licencia y revisión de redistribución siguen pendientes, con
sus ganchos intactos. No se instaló ni desinstaló PawnIO, no se ejecutó su
instalador y no se cambió HVCI. No se incluyen pruebas/documentación en producto.

**Paso 1A terminado. Se detiene el trabajo antes de 1B hasta aprobación del usuario.**
