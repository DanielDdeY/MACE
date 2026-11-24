# Fase 2 — 4B: estados aditivos y mensajes

Fecha: 2026-11-17. Base confirmada:
`4ffd231a3c454904997d1c228fefaa3d34e6200d`, rama `LHM_implementado`.
El árbol tenía únicamente `prueba-cpu.txt` sin seguimiento; se dejó intacto
y fuera de los commits. No se reescribió historial ni se fusionó a principal.

## Evidencia aportada por el usuario

- El usuario informó éxito de `test-lhm1b.ps1 -RealHelper -RequireCpu`
  elevado, mostrando temperatura de CPU. Se registra como prueba del usuario
  sobre 4A, sin atribuirle otros resultados ni repetirla aquí como propia.
- Parada/arranque de PawnIO: **NO VERIFICADO**. Según el usuario,
  `ControlService ERROR 1052`, el servicio siguió RUNNING con el helper activo,
  CPU/GPU sin cambios; no se forzó. No se interpreta como transición observada,
  ni como reinicio pendiente, ni como prueba de recuperación física.

## Causa → código → mensaje

ABI 1.5, DLL 1.5.0. Valores existentes 0..8 intactos. Solo VALID=0 permite usar
una cifra. Los ocho códigos nuevos van al final; valor de transporte siempre
0 para F32/U32/U64 y reserved sin reutilizar. Java representa ausencia con
NaN/Optional vacío, sin convertirla en cero físico. SensorProvider conserva
sus IDs: estas causas son MetricState, no proveedores adicionales.

| Causa | Código público | Mensaje completo de la UI |
|---|---|---|
| Proceso del helper sin elevación | NOT_ELEVATED = 9 | Ejecuta MACE como administrador para ver la temperatura y la potencia de la CPU. |
| PawnIO Missing | PAWNIO_MISSING = 10 | PawnIO no está instalado. Los sensores de CPU no están disponibles. |
| PawnIO Stopped | PAWNIO_STOPPED = 11 | PawnIO está detenido. Los sensores de CPU no están disponibles. |
| Reinicio pendiente por resultado 3010 | REBOOT_REQUIRED = 12 | Para habilitar los sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando |
| Helper no disponible | HELPER_UNAVAILABLE = 13 | El componente de sensores no está disponible. Revisa o repara la instalación de MACE. |
| Presupuesto agotado/modo lento | HELPER_RETRYING = 14 | El componente de sensores no responde. MACE seguirá intentándolo. |
| Runtime del helper no verificable/inaccesible | HELPER_UNTRUSTED = 15 | El componente de sensores no pudo verificarse. Repara la instalación de MACE. |
| Fallo conocido de contención | HELPER_CONTAINMENT_FAILED = 16 | Windows no pudo iniciar el componente de sensores de forma segura. Revisa las restricciones de ejecución o repara MACE. |
| Calentando | WARMING_UP = 5, existente | Preparando las lecturas de los sensores. |
| Código desconocido, lectura fallida u otra ausencia genérica | Desconocido → READ_FAILED = 6; los demás conservan código | La lectura no está disponible. |

9/10/11 y el futuro 12 afectan a temperatura/potencia CPU. Uso de CPU/RAM de
Windows y GPU se evalúan independientemente. 13..16 afectan a métricas que
dependen del helper; no invalidan las primarias NVML/IGCL. Una capacidad VRAM
conocida por Windows conserva el fallback anterior únicamente cuando falta el
helper y la asociación es única; no se amplió a integridad/mensaje inválido/caducidad.

Prioridades implementadas: un fallo de transporte/confianza/contención conserva
su motivo; una muestra fresca puede indicar falta de elevación incluso durante
WarmingUp. Con muestra Ready, falta de elevación precede a Missing/Stopped de
PawnIO; después se aplican identidad, lectura y coherencia. PawnIO Unknown sigue
NO_PROVIDER=2, sin inventar una causa concreta. Cero CPU sospechoso sigue
READ_FAILED=6; GPU ambigua y UnknownPower siguen UNSUPPORTED=3. Ventilador a
0 RPM y potencia GPU cero siguen siendo válidos si superan la validación.

Agrupaciones deliberadas:

- Ausencia y arranque bloqueado comparten 13: ambos necesitan revisar la
  distribución o restricciones. Los cortes transitorios de tubería/mensajes
  conservan READ_FAILED=6 mientras 4A reintenta, y pasan a 14 en modo lento.
  Comparten 6 con fallos transitorios de lectura porque no requieren una acción
  inmediata distinta. Un corte breve no muestra instrucciones de reparar.
- Todos los rechazos de confianza comparten 15: nunca se revela qué chequeo
  de ruta, acceso, hash, manifiesto o firma falló. La UI no concatena excepciones,
  rutas, hashes, PID ni identidad local en estas explicaciones.
- CreateProcess fallido usa 13: por sí solo no prueba que falló el job. Solo
  un fallo conocido de configuración/verificación de contención usa 16.
- Al entrar en modo lento, 14 sustituye al motivo transitorio; no sustituye
  fallos permanentes. Su mensaje no promete un plazo de recuperación.

## Compatibilidad y política intacta

El incremento es menor, 1.4 → 1.5, por extensión aditiva de estados uint32_t.
CPU 56, GPU 104, inventario 296 y legado 28 bytes, offsets, campos reserved,
versión de estructura 2 y **24 exports** se conservan. Comparación dumpbin de
producción contra 4A: mismos 24 nombres, sin hooks de pruebas.

Java nuevo conserva mínimo ABI 1.4 y acepta DLL 1.4.2; se verificó con la DLL
real de 4A: ABI=1.4, BUILD=1.4.2, CP_PollSystemMemoryV2=MACE_OK. Esa DLL no
produce los motivos nuevos y la UI conserva sus mensajes genéricos. El Java
anterior tenía fallback de códigos desconocidos a READ_FAILED/N/D y acepta
minor >=4. Consumidores externos que exijan minor exactamente 4 deberán
actualizar su comprobación; los que usen un enum cerrado sin caso desconocido
deben corregirlo. Los estados nuevos no pueden interpretarse como VALID.

El protocolo privado continúa en **2**, helper publicado **1.1.1** sin cambios;
no se añadieron bytes, controles ni campos al wire. Los motivos privados C se
traducen después de recibir/validar muestras o de fallar una apertura. El 3010
solo tiene código/encoder/decodificación y simulación de mensaje: **no existe
un origen real de 3010 conectado en esta fase**.

Se compararon contra 4A `lhm_retry.c/h`, RecoverableSampler, Program y
SensorSampler del helper: sin cambios. Presupuesto, reloj activo, backoff,
cooldown 30 s y recuperación en caliente intactos. Los cambios del receptor
publican motivos; no añaden reintentos ni modifican sus fechas. CP_Poll* y
CP_InitializeTelemetry siguen sin esperar al helper. NVML, IGCL,
GetSystemTimes/ReadCpuUsagePercent, DXGI y RAM protegidos pasan su auditoría.

## UI, Radeon y estabilidad

MetricDisplay conserva el estado/ausencia; MetricMessages contiene texto fijo
para el usuario. MetricCard muestra número o N/D y la explicación completa,
con tooltip. No crea/elimina nodos al cambiar estado y reserva altura para el
mensaje, evitando que las tarjetas salten. El valor N/D se mantiene corto para
que las etiquetas largas no alteren la altura de la zona numérica. No hay I/O
ni consultas de sensores en estas vinculaciones JavaFX.

La tolerancia existente de TelemetryPresentation se mantiene sin cambios: un
WARMING_UP aislado a 1 Hz puede retener la última lectura presentada durante
como máximo 1.500 ms, con mismo dispositivo/proveedor; el segundo consecutivo
ya muestra N/D. No modifica snapshots crudos, alertas o persistencia. No retiene
valores tras códigos nuevos de fallo explícito.

La simulación Radeon/LHM demostró: válido → un sondeo WarmingUp → válido no
introduce N/D en la copia presentada; tres sondeos WarmingUp sí producen N/D
desde el segundo y recuperan después. Una reapertura real que dure más de esa
tolerancia puede mostrar un N/D breve: mantener cifras antiguas más tiempo
ocultaría la interrupción. Se conserva esta política; no se alargó el grace.
Como la parada real del driver no se consiguió, la duración real de esa
interrupción Radeon y su aspecto en pantalla siguen **NO VERIFICADO**.

Prueba JavaFX con el tema real: cada estado mantiene la altura preferida de
la tarjeta y el texto completo cabe en el área reservada. Se intentó inspección
de la aplicación con Computer Use: la ventana/árbol accesible se localizaron,
pero la captura devolvió `FrameArrived timed out`, y tras recuperar el objetivo
`no screenshot targets found`; la navegación informó `coordinate input geometry
is unavailable`. No se declara revisión visual aprobada. Las JVM de prueba
propias se cerraron; la primera apertura usó la carpeta habitual de caja negra.
El script entregado ya aísla sus datos en native/build/fase2-paso4b/ui-data.

## Decisiones propuestas, NO implementadas

### Reintentar ahora

Recomiendo considerar **«Solicitar reintento»**, no prometer inmediatez si la
ventana de tres/60 s todavía impide lanzar. Ventaja: tras una reparación, evita
esperar hasta el siguiente sondeo lento. Coste propuesto: nueva función ABI
aditiva que señale al trabajador de la DLL; no requiere un comando del wire
porque la DLL ya gestiona la vida del helper. Exigiría otra versión menor,
pruebas de concurrencia y manejo UI del resultado aceptado/ocupado.

La propuesta debe conservar el historial y presupuesto de 4A, agrupar pulsaciones,
tener un mínimo de 30 s activos entre solicitudes y no reiniciar un helper sano.
No debe eludir integridad, contención, cierre o estado no inicializado. No debe
aceptar ruta/PID/órdenes arbitrarias ni resetear el presupuesto con Shutdown/Init.
Una solicitud aceptada solo significa petición recibida, no sensores válidos.

Alternativas: conservar los reintentos automáticos actuales (sin coste ABI),
o cerrar/reabrir manualmente MACE (pierde continuidad y puede pedir UAC).
Un archivo/IPC adicional para evitar una función ABI añade superficie y validación
innecesarias. No se implementó botón, función, señal ni cambio de deadline.
**Decisión del usuario pendiente**, separada de aprobar el Paso 5.

### Señal del instalador para 3010

Propuesta para Fase 3, **sin leer/escribir ninguna clave ahora**:

- Tras verificar y ejecutar el instalador autorizado de PawnIO, solo un resultado
  3010 escribe DWORD `PawnIoInstallResult=3010` en una subclave **volátil**, vista
  HKLM de 64 bits, por ejemplo SOFTWARE\MACE\VolatileBootState.
- Clave con escritura únicamente Administradores/SYSTEM y lectura para usuarios;
  creación explícita REG_OPTION_VOLATILE. No usar un archivo de usuario editable
  ni inferir 3010 de servicio detenido o de un reinicio pendiente ajeno a PawnIO.
- La clave volátil desaparece en un reinicio real; suspensión/hibernación no
  cuentan como reiniciar. No reutilizar una subclave persistente preexistente
  como si fuese volátil: instalador y consumidor deben validar ese contrato.
- El resultado 0 de otra ejecución en el mismo arranque no debe borrar una señal
  3010 pendiente anterior. Sin clave, sin DWORD válido o sin 3010 no se inventa
  reinicio pendiente; fallos de lectura requieren diagnóstico genérico.
- La DLL consumiría la señal desde un trabajador al inicializar, la cachearía y
  mapearía solo temperatura/potencia CPU a 12, con cifra 0. No añadir lectura de
  registro bloqueante a CP_Poll, ni información al wire. Propuesta de prioridad:
  fallo estructural/confianza, falta de elevación, reinicio pendiente, estado de
  PawnIO, lectura. GPU/uso CPU/RAM mantienen sus validaciones independientes.

Esto necesita aprobación antes de implementarse en Fase 3, incluidos esquema,
ACL, consumo y manejo de reinicio/inicio rápido. Fase 2 solo demuestra el código
12 y su mensaje exacto mediante Preview y registros V2 de prueba. No se ejecutó
PawnIO_setup ni se modificó registro, servicio o HVCI para generar un 3010.

## Pruebas y cifras

| Regresión | 4A | 4B |
|---|---:|---:|
| Helper | 105 | 105 PASS, sin cambios de código |
| CTest Development | 74 | 76 PASS |
| CTest Production | 75 | 77 PASS |
| Java | 294 PASS + 1 omitida | 334 PASS + 1 omitida (335 total) |
| Payload | 8 + rechazo Development | 8 PASS + rechazo Development PASS |
| check-protected-telemetry | PASS | PASS |
| audit-retired-providers | 181 aceptadas / 0 inesperadas | 181 aceptadas / 0 inesperadas |

Delta CTest +2: tabla/promoción de estados y fallo conocido de contención simulado
en la DLL desechable del arnés, no en producto. Las pruebas existentes se adaptaron
a los motivos nuevos sin reducir los controles de validez, tiempo o presupuesto.
Delta Java +40: 11 escenarios nuevos de MockScenario, 16 casos añadidos por dos
pruebas existentes parametrizadas sobre MetricState, 12 casos de SensorReasonTest
y una prueba JavaFX de layout. La omisión sigue siendo una DLL histórica sin
CP_GetAbiInfo no aportada (MACE_LEGACY_DLL); no es la DLL compatible 1.4.2.

También se ejecutaron refuerzos dirigidos de las 13 pruebas de mensajes/layout
tras añadir aserciones exactas de códigos antiguos/nuevos y cero F32/U32/U64.
La primera pasada nativa detectó una aserción del recurso Windows que todavía
esperaba 1.4.2; se actualizó a 1.5.0 y pasó la regresión. No se cambió la política
de 4A para hacer pasar pruebas.

**Prueba real sin elevación propia:** el script rechazó ejecutarse si el token
fuera administrador; utilizó DLL 1.5.0 de producción, runtime completo publicado
y verificado de 203 archivos, sin sensores simulados. Java obtuvo state=9,
CPU Windows y RAM VALID y el mensaje de administrador. El script también pasó
su modalidad ConsoleProbe, que devuelve código 0 solo si comprueba ese resultado.
Esto acredita integración real y el texto obtenido; no revisión visual del
dashboard, ni lectura física de temperatura CPU sin permisos.

## Reproducir y comprobar mensajes

Desde la raíz del repositorio, con JDK 22, MSVC y dependencias disponibles:

```powershell
New-Item -ItemType Directory -Force .\native\build\fase2-paso4b | Out-Null
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4b\dev
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\build-native.ps1 -BuildDirectory .\native\build\fase2-paso4b\prod
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-paso4b\dev\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$pub=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $pub 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
$env:MACE_WATCHDOG_FAKE_HELPER=(Resolve-Path .\native\build\fase2-paso4b\dev\Release\lhm_fake_helper.exe).Path
mvn.cmd -o -f .\app\pom.xml test
$prod=(Resolve-Path .\native\build\fase2-paso4b\prod\Release\mace_native.dll).Path
$pawn=(Resolve-Path .\packaging\dist\MACE\app\prerequisites\PawnIO_setup.exe).Path
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-runtime-payload.ps1 -NativeDll $prod -DevelopmentDll $env:MACE_NATIVE_LIB -HelperDirectory $pub -PawnIoInstaller $pawn -JavaExe "$env:JAVA_HOME\bin\java.exe"
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

Build-Helper ejecuta los 105 controles y publica antes de compilar producción,
que fija ese manifiesto. No mezclar esa DLL con un runtime distinto. La imagen
final sigue pendiente de regenerar al terminar Paso 5.

**Cierra cualquier MACE/Preview antes de cada prueba UI**: comparten instancia
única. No terminar procesos ajenos ni desactivar la reserva. Para usar otro JDK
22 instalado, añadir `-JavaExe 'RUTA\bin\java.exe'` al script.

Prueba REAL sin elevar, desde una PowerShell **normal**, sin cambiar launchers:

```powershell
# Consola: verifica estado 9 y texto, termina automáticamente.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-sensor-ui.ps1 -ConsoleProbe
# Dashboard real: abre Java/FFM reales; entrar en CPU para ver el mensaje.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-sensor-ui.ps1 -Native
```

El script copia DLL de producción/runtime verificado a una carpeta nueva e
ignorada, conserva su hash y prepara datos UI aislados. No usa Preview al elegir
Native, no cambia manifiestos ni eleva; rechaza consola administradora.

SIMULACIÓN, reutilizando la infraestructura de Preview, sin DLL ni PawnIO:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-sensor-ui.ps1 -Scenario CPU_REBOOT_REQUIRED
```

En CPU, leer el texto completo debajo de N/D y comprobar que GPU/uso CPU/RAM
siguen disponibles. Cerrar la ventana antes de sustituir el nombre del escenario
en el mismo comando para revisar cada mensaje:

| Escenario | Qué comprobar |
|---|---|
| CPU_NOT_ELEVATED | Mensaje de administrador, GPU independiente |
| PAWNIO_MISSING | Ausente, sin mensaje de reinicio |
| PAWNIO_STOPPED | Detenido, sin mensaje de reinicio |
| CPU_REBOOT_REQUIRED | Texto 3010 exacto; es simulación, no se instaló nada |
| HELPER_UNAVAILABLE | Componente no disponible en CPU y Radeon/LHM |
| HELPER_RETRYING | No responde, seguirá intentando, sin plazo prometido |
| HELPER_UNTRUSTED | Mensaje genérico de verificación, sin detalles locales |
| HELPER_CONTAINMENT_FAILED | No pudo iniciar de forma segura |
| SENSORS_WARMING | Calentando continuo para inspeccionar texto |
| LHM_RECOVERY_SHORT | En GPU/Radeon: un calentamiento aislado se cubre con tolerancia |
| LHM_RECOVERY_LONG | Desde el segundo calentamiento muestra N/D; luego recupera |

Para los dos últimos, abrir GPU inmediatamente: comienzan con dos muestras
válidas, luego una o tres calentando, y después vuelven a válido. Si se llega
tarde, cerrar/repetir. Todas esas cifras son simuladas y el badge lo indica.
El script no prueba UAC del MACE.exe final ni una transición física de PawnIO.

## Commits, archivos y límites

- `63cedd5`: ABI/documentación y adaptación de pruebas nativas a motivos nuevos.
- `0adca05`: dominio/códigos Java, mensajes, tarjetas y escenarios Preview.
- `1cc99f6`: estabilidad de la zona numérica de las tarjetas con etiquetas largas.
- `8ee6ef5`: distingue cortes temporales (6) del componente ausente/bloqueado
  (13), evitando sugerir reparación durante una reconexión breve.
- Commit final de pruebas/procedimiento/informe: hash entregado en la respuesta
  final; no se inserta un hash autorreferente en este mismo archivo.

| Archivos | Cambio |
|---|---|
| native/include/mace_status.h; native/cmake/mace_version.h.in; native/CMakeLists.txt; native/scripts/package-native.ps1 | Estados 9..16, ABI/build 1.5/1.5.0 y registro de pruebas |
| native/src/telemetry/lhm_wire.c; lhm_receiver.c; lhm_transport.h | Traducción de causas y estado lento; fallo de confianza/contención y fallback Windows conservados |
| native/src/telemetry/lhm_transport.c | Inyección de fallo de job solo en fixture de pruebas, excluida de producto |
| native/docs/ABI.md; helper/PROTOCOL.md | Contrato/compatibilidad; wire privado intacto |
| native/tests/test_abi_exports.c; test_lhm_wire.c; test_lhm_receiver.c; test_lhm_job.c; test_lhm_security.c; test_lhm_production_profile.c; test_lhm_live.c; test_lhm_retry_receiver.c | Expectativas aditivas, recurso Windows y escenario de contención |
| native/tests/test_metric_reasons.c | Códigos/validez y traducción de fallos privados |
| app/src/main/java/com/mace/domain/model/MetricState.java; SensorProvider.java | Estados al final; IDs de proveedor intactos/documentados |
| app/src/main/java/com/mace/infrastructure/nativebridge/NativeCodes.java | Decodificación compatible, desconocido no válido |
| app/src/main/java/com/mace/infrastructure/mock/MockScenario.java; MockSensorsAdapter.java | Once escenarios adicionales |
| app/src/main/java/com/mace/presentation/viewmodel/MetricMessages.java; MetricDisplay.java | Mensajes y explicación por estado |
| app/src/main/java/com/mace/presentation/view/component/MetricCard.java | Mensaje completo, tooltip y espacio estable |
| app/src/test/java/com/mace/presentation/viewmodel/SensorReasonTest.java; SensorCardLayoutTest.java; NativeSensorReasonProbe.java | Mapeo, desconocido/cero, compatibilidad/layout y probe real |
| native/scripts/test-sensor-ui.ps1 | Prueba real sin elevar y Preview manual con datos aislados |
| docs/REFERENCIAS-ACEPTADAS-1D.tsv | Ajuste de ubicaciones tras documentación; mismas 181 aceptadas |
| helper/FASE2-PASO4B.md | Este informe, propuestas y procedimiento |

Evidencias ignoradas en native/build/fase2-paso4b: helper/dev/prod/java/payload/
protected/audit/exports/no-elevation/no-elevation-production/abi14-compatibility/
layout/reasons-final/ui-preview.log y XML de herramientas. Pueden contener rutas
de perfil/classpath y datos locales; no se suben ni se anonimizan los originales.
El informe y TSV no copian identidad/rutas personales. prueba-cpu.txt se conserva
fuera de los commits; no se inspeccionó ni modificó su contenido.

NO VERIFICADO: visual completo del dashboard y comparación física del parpadeo;
parada/arranque real de PawnIO/recuperación Radeon; suspensión real; hardware
Intel/NVIDIA y equipos limpios/HVCI no comprobados aquí; instalador/3010 real,
contrato propuesto de registro aún no implementado; MACE.exe elevado completo
con imagen final actualizada; firma/certificado, licencia y revisión legal de
redistribución pendientes. Los ganchos se conservan. El staging mantiene los
cinco componentes y 214 archivos, PawnIO intacto, sin ejecutar su instalador.

No se instaló/desinstaló PawnIO, no se forzó su servicio, no se cambió HVCI,
UAC o launchers, ni se implementó reintento manual/origen 3010. No se regeneró
packaging/dist/MACE. **4B terminado: detenerse hasta aprobación para Paso 5.**
