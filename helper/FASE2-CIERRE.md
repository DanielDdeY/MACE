# Fase 2 — cierre con deuda de verificación

Fecha: 2026-11-18, America/Lima. Rama `LHM_implementado`. Partida confirmada:
`8ed34d14fb029c06ef5cb4ca262eae7329a54a70`, árbol limpio. Este informe registra
el cierre solicitado, sujeto a aprobación del 5.3; **no aprueba verificaciones
pendientes, fusión ni distribución**. No implementa Fase 3.

Se leyeron el plan 5.2, el inventario de escrituras, los dos informes de 5.1,
los informes previos y los **18 result.txt** presentes en
`native/build/fase2-paso5.2/`. Los originales siguen ignorados y no se versionan.
Las observaciones visuales del usuario prevalecen. Los detalles que se extraen
de logs se identifican como tales, sin atribuirlos a una observación visual.
La aclaración posterior del usuario fue **8A: elevado primero**.

## Contratos y artefacto de referencia

- DLL **1.5.0**, ABI **1.5**; protocolo privado **2**, helper **1.1.1**.
  CPU 56, GPU 104, inventario 296 y legado 28 bytes; sin cambios de offsets.
- Imagen de 5.1: `packaging/dist/MACE`, Java Production del commit `2ef7dbf`;
  370 archivos, 147.787.297 bytes, 140,94 MiB, 11 JAR. Payload real: 223 archivos.
  Versiones, hashes y verificaciones detallados en [5.1](FASE2-PASO5.1.md).
- Launcher **L**, JVM hija **V** y helper **H** son actores diferentes. En esta
  imagen L y V se llaman MACE.exe; H es hijo de V. El observador usa árbol,
  ruta/creación y handles, nunca una JVM global por nombre.
- La regresión no reconstruye la imagen. La DLL Production probada conserva
  el hash de la DLL incluida. Los 370 hashes se cotejan al finalizar este informe.

## Pasos 0 a 5 y decisiones centrales

| Paso | Commits | Decisión / resultado de implementación |
|---|---|---|
| 0 | `bcc7f1c` | Inventario previo; separar garantías documentadas, fixtures y verificación física |
| 1A | `fc615ed` | Crear el helper contenido mediante JOB_LIST antes de reanudarlo; cierre nativo con presupuesto total de 4 s y recursos tardíos retenidos |
| 1B | `2206973` | Control mínimo Shutdown de protocolo 2, EOF independiente y watchdog Java de 10 s con halt(70) |
| 2 | `7510fa4` | Helper Production sin anulaciones de entorno; manifiesto final de 202 archivos fijado en la DLL, trust antes de cada lanzamiento y entorno del hijo acotado |
| 3 | `83348cf` | Reserva por usuario/sesión antes de sensores; MACE y Preview comparten identidad y el perdedor no abre telemetría |
| 4A | `4ffd231` | Ventana móvil de tres relanzamientos/60 s activos, backoff acotado/modo lento y reapertura de LHM con cooldown de 30 s; sin borrar historial tras muestras útiles |
| 4B ABI | `63cedd5` | ABI 1.5 aditivo: motivos 9–16, siempre no válidos y valor 0; tamaños/wire intactos |
| 4B Java/UI | `0adca05`, `1cc99f6`, `8ee6ef5` | Mensajes por causa, desconocido → N/D y tolerancia visual WARMING_UP sin cambiar datos crudos |
| 4B pruebas/diagnóstico | `e47cb54`, `5f818cb` | Pruebas/informe; diagnóstico de pin heredado ajeno al fixture, no requisito de token normal; no se debilita contención |
| 5.0 | `b305d3f` | Consolidación honesta y auditoría que expone H-5.0-JAVA; exclusión local de tres TXT |
| H-5.0-JAVA | `2ef7dbf` | Production Java exige pins del launcher y falla cerrado; Development solo por metadato de compilación, nunca habilitable por entorno |
| 5.1 | `2e8f557` | Regeneración y verificación de la imagen; arnés de cuatro anulaciones ajenas |
| Diagnóstico 5.1 | `9586105` | Corregir observador para wrapper + JVM hija, sin cambiar producto ni imagen |
| Evidencia 5.1 | `6b6e1e2`, `9895c9b` | Registrar PASS elevado aportado por el usuario, limitado a selección de componentes |
| 5.2 | `8ed34d1` | Plan y scripts manuales, inicialmente sin ejecución; separar cinco cierres y restauración de servicio en finally |
| 5.3 | Este commit documental, identificado en la entrega | Consolidar ejecución parcial, nueva regresión normal, riesgos y deuda; detenerse antes de Fase 3 |

Los documentos de cada paso conservan la fecha/alcance original. El resultado
actual se rige por esta consolidación, no por cifras históricas de una entrega anterior.

## Resultados del 5.2: observación del usuario y complemento del log

Todos estos casos usan la **imagen Production** y consola administradora,
salvo el actor normal de instancia mixta. Una captura saneada de procesos/V2
no acredita por sí sola lo que apareció en pantalla ni exactitud física.

| Caso | Observación explícita del usuario | Complemento del archivo; límite |
|---|---|---|
| CloseX | Ejecutado, sin anomalías | `CloseX-f423bd0ff980440998e771ae59a3b0fa/result.txt`: L=12224, V=5088, H=1672 observados antes; línea 17 ya muestra L/V/H muertos y recuento de helper de imagen 0, salidas L/V=0. No prueba activación real del watchdog |
| EndTaskWindow | Ejecutado, sin anomalías; PID MACE.exe observado **7264** | `EndTaskWindow-2c4f75036547417397d4911cea618ba6/result.txt`: 7264 es L; V=18084 y window_owner=18084, H=17228. La última muestra (línea 48) aún tiene L/V/H vivos. No se deduce qué terminó el botón ni que quedaron muertos después. Desaparición posterior y objetivo exacto: **NO VERIFICADO** |
| KillHelper | CPU pasó a N/D, texto no leído, no observó PID nuevo; después recuperó la temperatura | Dos capturas completas muestran relanzamiento: `KillHelper-413eac90f9c14b078c5dde53d7de8092/result.txt`, líneas 8–13, H 15740 → 19172 bajo V=19236, estados CPU 6 → 0; `KillHelper-27a842e622f9459bbed1bca88ff91b6f/result.txt`, líneas 8–13, H 5144 → 15768 bajo V=4560, 5 → 0. Acción y retorno taskkill=0 registrados. El cambio de PID está respaldado por logs, **no observado visualmente por el usuario**. Mensaje exacto: **NO VERIFICADO** |
| CloseWarming | **NO VERIFICADO**, no pudo observar la precondición | Una captura solo tiene BEGIN/error; otra arranca y termina sin demostrar cierre mientras estado 5. Un cierre normal posterior no acredita este escenario |
| KillJvm | La ventana se cerró; no comprobó muerte del helper | `KillJvm-5651892068994a7799844738c373545c/result.txt`: objetivo V=13040, H=9168 observado antes; línea 11 y siguientes muestran L/V muertos, H=0 y recuento de imagen=0 tras la acción. Complemento de logs para ese helper, no comprobación visual del usuario |
| KillLauncher | JVM y helper murieron | `KillLauncher-8766d93fa73b4682a9a838e5f46b676d/result.txt`: /F sobre L=14448, tree=0; V=108 ya muerto en línea 11, H=8916 aún vivo en esa muestra y muerto en línea 12 (~1 s después). No exige muerte simultánea |
| KillTree | No quedó ningún proceso | `KillTree-02c2f6b64eab4652a04b577801df9c87/result.txt`: /F /T sobre L=2964; V=4404 y H=1852 previos; tras la acción L/V/H muertos y recuento de imagen=0. Alcance: árbol observado de esta ejecución |
| Arranque posterior a kills | MACE arrancó con normalidad después de los kills | Confirmación general del usuario; no detalló una matriz de arranque/datos por cada kill. No prueba integridad exhaustiva de preferencias/historial ni un crash nativo o halt real |
| Instancia única | Apareció «MACE ya está abierto o se está cerrando». Aclaración: **8A, elevado primero** | Hay ElevatedOwner, NormalOwner y SecondElevated; **no hay SecondNormal**. Los dos últimos son compatibles con una ejecución 8B, pero no sustituyen la declaración del usuario ni aprueban 8B. En SecondElevated se observa módulo nativo=0 y ningún helper propio, sin atribuir esa evidencia a 8A. Asociación exacta de cada captura a la variante: **NO VERIFICADO** |
| Dos elevados y Preview | No ejecutados | **NO VERIFICADO**, aunque haya fixtures automatizados |
| CoexistLhm | **NO VERIFICADO**: no tiene LHM de escritorio instalado | Existe captura con ese nombre, pero no acredita coexistencia. No se presenta como prueba ni como instalación de LHM |
| SuspendResume | Ejecutado; después de reanudar siguen los datos. No distinguió valores antiguos de recuperación rápida | `SuspendResume-bbd63ef4adb8430bbd785218802834a5/result.txt`, líneas 24–29: registro histórico con edad 21.708 ms y recent=0; luego hueco de monitor 22.906 ms, CPU t/p=6, GPU=6, H 14572 → 20300, estado 5 y luego 0 recientes. Demuestra que el registro no se considera reciente y que hay estados no válidos/recuperación en la captura. **NO VERIFICADO** qué mostró la UI en el intervalo inmediato |
| PawnIO 11A | STOP_RETURN win32=1052, sin forzar, RESTORE_END running=1 | Coincide con `RunningToStoppedToRunning-e813b7dcc96d49c39283d36cf12e2730/result.txt`, líneas 11–16. **FALLO OBSERVADO: rechazo de STOP**, no fallo demostrado del mecanismo de recuperación. Nunca se observó STOPPED; finally confirmó RUNNING, no una transición STOPPED → RUNNING |
| PawnIO 11B/11C | No ejecutados porque rechaza la parada | Recuperación física inversa y restauración tras Ctrl+C con servicio realmente detenido: **NO VERIFICADO** |

Las rutas de esta tabla son relativas a `native/build/fase2-paso5.2/`. También
hay dos intentos KillHelper abortados y un CloseWarming abortado con
CASE_NOT_COMPLETED/hresult=-2146233087: no se conoce su causa concreta y no son
un código de salida de MACE ni prueba de una regresión del producto.

**Contraste de la línea base de PawnIO:** la lectura del usuario es correcta
para la parte estabilizada: líneas 7–10 muestran cpu_t/p/u=0, provider=8 (LHM),
recent=1, H=6320 estable. Matiz: la primera muestra de PAWNIO_BASELINE, línea 6,
es 5/5/5 (WARMING_UP); antes hay datos ausentes. La línea 13 posterior al rechazo
de STOP vuelve a mostrar estados 0 y el mismo H. **0 es el código VALID, no una
temperatura/potencia de cero**. El log no registra los valores físicos. No hubo
START solicitado ni prueba de reinstalación/3010. No se amplía esta evidencia.

## Tabla de verificación por requisito

AUTOMATIZADO significa prueba programada con el alcance indicado; no equivale
a verificación física en administrador. VERIFICADO POR EL USUARIO conserva
exactamente su declaración y, cuando se indica, complemento de sus logs.

| Requisito | Estado | Evidencia y alcance; límite pendiente |
|---|---|---|
| Job Object/JOB_LIST antes de reanudar, descendientes y job restrictivo | AUTOMATIZADO | CTest normal 76/77; lhm_job_contained/descendants/restrictive-job. Fixtures, no hardware |
| Helper huérfano, EOF y proceso homónimo ajeno | AUTOMATIZADO | lhm_job_abrupt-parent/eof-close/foreign-homonym; no búsqueda global para terminar procesos |
| Cierre público acotado, cierre concurrente, no bloqueo de Poll/Initialize | AUTOMATIZADO | lhm_shutdown_budget, lifecycle/concurrency, pruebas ABI/Java; no garantía temporal de disco/kernel bloqueados |
| Shutdown control, inválido/duplicado y EOF independiente | AUTOMATIZADO | Casos helper/CTest conservados; no bytes nuevos en este subpaso |
| Watchdog 10 s/halt(70) | AUTOMATIZADO | ShutdownWatchdogProcessTest, 3 casos normal, fixtures propios; activación en MACE elevado completo: NO VERIFICADO |
| X de imagen y salida de helper | VERIFICADO POR EL USUARIO | Sin anomalías + captura L/V/H muertos; no implica watchdog disparado |
| End task de ventana | VERIFICADO POR EL USUARIO | Solo ejecución sin anomalías/PID 7264; muerte del árbol después: NO VERIFICADO, captura termina con árbol vivo |
| /F JVM hija, /F launcher solo, /F /T | VERIFICADO POR EL USUARIO | Alcance de la tabla anterior; muerte H en KillJvm apoyada solo por log; muestra intermedia H vivo en KillLauncher |
| Reabrir después de cierres forzados | VERIFICADO POR EL USUARIO | Arranque normal general declarado; integridad de cada archivo y caso: NO VERIFICADO |
| Pipe: bootstrap privado, ACL, primera instancia, rechazo remoto y PID bilateral | AUTOMATIZADO | Helper/CTest, bad-pid/wrong-client/bad-server-pid/replay/malformed/fragmented; fixtures normal |
| Hash compilado y anulaciones helper solo Development | AUTOMATIZADO | lhm_security_ignore-env y lhm_actual_production_ignores_environment; DLL Production normal |
| Java Production falla cerrado sin pins e ignora MACE_* | AUTOMATIZADO | NativeProductionPolicyTest 13/13 en JVM aisladas; entorno no activa Development |
| Trece casos Java elevados | VERIFICADO POR EL USUARIO | 13 casos/código 0, solo administrador; no los 348 Java completos |
| Imagen elevada con cuatro variables ajenas | VERIFICADO POR EL USUARIO | 5.1 PASS/código 0, L/V separados, DLL/helper empaquetados; solo selección de componentes, no validez CPU |
| Manifiesto dependencias y trust por relanzamiento | AUTOMATIZADO | 202 runtime, fixtures valid/missing/tampered/extra/locks/relaunch, Production ligado al manifiesto; no autentica un publicador sin firma |
| Instancia única y liberación al morir | AUTOMATIZADO | SingleInstanceProcessTest 8 casos normal, namespaces de prueba |
| Mixta 8A: elevado primero | VERIFICADO POR EL USUARIO | Aviso explícito y variante confirmada; ausencia de DLL/helper del perdedor normal no correlacionada en logs |
| Mixta 8B, dos elevados y Preview | NO VERIFICADO | Logs compatibles con parte de 8B no bastan para aprobar toda la variante; dos elevados/Preview no ejecutados |
| Presupuesto/backoff/modo lento/racha/reloj activo | AUTOMATIZADO | lhm_retry_policy_* y lhm_retry_* normal; política 4A intacta |
| KillHelper y recuperación con nuevo PID | VERIFICADO POR EL USUARIO | UI N/D → temperatura; dos logs acreditan PID distinto y estados 0. Texto de transición exacto NO VERIFICADO |
| PawnIO caliente, cooldown y reapertura | AUTOMATIZADO | RecoveryTests y lhm_retry_retry-pawn, estados/tiempo simulados; hardware NO VERIFICADO |
| Parada normal PawnIO en 11A | FALLO OBSERVADO | SCM 1052; aborto sin forzar y RUNNING confirmado en finally. No es recuperación física lograda |
| PawnIO STOPPED → RUNNING real, 11B/11C | NO VERIFICADO | Sin parada aceptada ni Ctrl+C con servicio detenido |
| PawnIO ausente → instalado / origen real de 3010 | NO VERIFICADO | Reservado a Fase 3 y VM limpia con aprobación |
| Estados 9–16, cero no válido y código desconocido | AUTOMATIZADO | metric_reasons/V2RecordsTest/SensorReasonTest: estados nuevos no válidos/0, desconocido N/D; cero físico legítimo no se invalida indiscriminadamente |
| Mensajes y once escenarios Preview | VERIFICADO POR EL USUARIO | Declaración previa de resultados esperados de 4B, simulación explícita, incluido texto 3010; no instalador real |
| Java + DLL real sin elevar | VERIFICADO POR EL USUARIO | Prueba previa 4B correcta; no se extrapola a imagen completa sin UAC |
| WARMING_UP, estabilidad/layout y no ocultar fallos explícitos | AUTOMATIZADO | TelemetryPresentationTest/SensorCardLayoutTest: un sondeo hasta 1.500 ms, segundo consecutivo N/D, misma fuente |
| Cierre durante WARMING_UP y gracia en reapertura Radeon física | NO VERIFICADO | Precondición no observada; parada real no obtenida |
| Suspensión/reanudación, UI inmediata sin datos antiguos | NO VERIFICADO | Usuario ve datos después; log distingue antigüedad/6/5/0, pero no la pantalla inmediata |
| Coexistencia con LHM de escritorio | NO VERIFICADO | No instalado por el usuario; un homónimo de fixture no sustituye sensores concurrentes |
| NVML/IGCL/GetSystemTimes/DXGI/RAM y layouts protegidos | AUTOMATIZADO | check-protected-telemetry normal PASS; no acredita hardware NVIDIA/Intel |
| Imagen sin hooks/tests/docs/logs, UAC y PawnIO intacto | AUTOMATIZADO | Verificaciones 5.1; 370 hashes revalidados por lectura en 5.3. No se reejecuta test-image sobre producto porque añade archivos temporales |
| Dos metadatos NDD limitados a ruta/hash/offsets | AUTOMATIZADO | Política compatibility-references.json y negativos de 5.1; no es permiso para ampliar excepción |
| Firma de publicación y autenticidad del editor | NO VERIFICADO | Archivos propios sin firma de publicación; hashes/manifiestos no la sustituyen |
| Atomicidad/durabilidad ante halt, fallo de disco o energía | NO VERIFICADO | Inventario estático + PersistentBlackboxSinkTest con archivos preparados; no fallo físico en cada escritura |
| Intel CPU/GPU, NVIDIA, HVCI actual y máquina limpia | NO VERIFICADO | Sin evidencia explícita con el conjunto final |
| Licencia de MACE/revisión legal de redistribución | NO VERIFICADO | Pendientes; avisos de terceros no constituyen autorización legal completa |

## Regresión del cierre: consola normal y límites elevados

Ejecutada con **Administrator=False** sobre los artefactos existentes y fuentes
Java actuales. No se recompiló/republicó el helper ni la DLL y no se regeneró la
imagen. Los fixtures propios conservan la autorización previa para su cierre;
no se termina ningún proceso de usuario ni se solicita elevación Windows.
Logs de herramientas, que pueden incluir rutas locales, quedan solo en
`native/build/fase2-paso5.3/` y `app/target/`, fuera de Git y del producto.

| Batería | Resultado nuevo normal 5.3 | Administrador |
|---|---|---|
| Helper | **105 PASS**, helper-normal.log, código 0 | NO VERIFICADO batería completa |
| CTest Development | **76/76**, dev-normal.log, código 0 | VERIFICADO POR EL USUARIO previamente: 76/76 + CPU real, DLL Development 1.5.0, consola limpia; no repetido en 5.3 |
| CTest Production | **77/77**, prod-normal.log, código 0 | NO VERIFICADO |
| Java | **347 PASS + 1 omitida**, 348 total, 0 fallos/errores, BUILD SUCCESS, java-payload-normal.log | Solo los 13 aislados previos; resto NO VERIFICADO |
| Payload | **8 PASS + rechazo DLL Development + rechazo Java Development**, payload-ps51-normal.log, código 0 | NO VERIFICADO |
| check-protected-telemetry | **PASS**, protected-normal.log | NO VERIFICADO |
| audit-retired-providers | **181 aceptadas, 0 inesperadas**, audit-normal.log; TSV generado coincide con versionado | NO VERIFICADO |

Sin cambio de cifras frente a 5.1. La omitida corresponde a integración opcional
con DLL antigua externa, no a ocho fixtures faltantes. Se ejecutaron los 3 casos
de watchdog y 8 de instancia única. El payload de pruebas tiene 214 archivos,
frente a 223 reales por los nueve JAR runtime adicionales de la imagen.

Incidencias de la ejecución, sin ocultarlas ni cambiar el producto:

1. Primer intento dentro del sandbox: helper falla por UnauthorizedAccessException
   en pipe; CTest Development 39/76 y Production 39/77 fallan. Al repetir fuera
   del sandbox **con token normal comprobado**, helper y ambos CTest pasan.
   Es evidencia de dependencia del entorno de ejecución, no una prueba elevada.
2. Java completo pasa; el payload invocado directamente bajo PowerShell **7.6.9**
   rechaza el manifiesto sin alterar sus bytes. Comparación de solo lectura:
   primera diferencia de orden en línea 27, mscordaccore.dll frente a su variante
   con sufijo. El mismo Verify-Artifacts y payload pasan con Windows PowerShell
   **5.1.19041.6456**, el host de los comandos documentados `powershell.exe`.
   **FALLO OBSERVADO de compatibilidad del tooling con PS7**, sin corregirlo ni
   regenerar manifiestos. No confundirlo con corrupción de la imagen.
3. Un intento envolvente de QA tuvo expansión incorrecta de una variable y no
   se usa como prueba limpia. La confirmación válida usa un archivo local de QA,
   PS5.1 y token normal explícitos, con payload completo y código 0.

Los comandos reproducibles y la matriz elevada pendiente están en
[FASE2-PENDIENTES.md](FASE2-PENDIENTES.md#p10-regresión-completa-en-administrador).
No se atribuye al usuario ejecución elevada de helper, Production, Java completo,
payload o auditorías que no haya confirmado.

## Riesgos residuales y escrituras ante halt/cierres forzados

El [inventario de escrituras](FASE2-INVENTARIO-ESCRITURAS.md) se incorpora por
referencia íntegra; es inspección estática del código propio, no traza exhaustiva
de dependencias, controladores, Preferences, WER o sistema operativo.

| Escritura / momento | Riesgo residual y siguiente arranque |
|---|---|
| Crear carpeta y cabecera .mbbx, CREATE_NEW + force | No hay temporal/rename transaccional; puede quedar archivo vacío/parcial. Se marca incompleto y se abre otra sesión, sin reparar el original |
| Append de registro 192 B y reescritura de cabecera 64 B | Son escrituras separadas, no atómicas; halt puede truncar registro/cabecera o perder cola RAM. CRC detecta daño y lector recupera solo prefijo verificable |
| Flush periódico/force | Reduce pérdida; no garantiza durabilidad física ni transacción frente a disco/energía |
| SESSION_END/cleanClose al cerrar | halt(70), /F y crash evitan el cierre normal; puede faltar evento o cabecera coherente. Se clasifica sesión incompleta, no se inventa cierre limpio |
| Rotación/retención antes de crear reemplazo | Borrados ya completados no se revierten; puede perderse historial retenido antes de que exista el nuevo archivo |
| Preferencias Micro-HUD | Varias claves sin transacción conjunta ni flush explícito; posible pérdida/mezcla de valores. Lectura por clave con valores por defecto; no recuperación específica demostrada para todo fallo del backend |

Abrir después de kills, como confirmó el usuario, **no demuestra que cada
historial o preferencia quedó íntegro**. Daños físicos por halt en cada punto,
disco lleno, permisos y corte de energía siguen NO VERIFICADOS. No se corrigen
estas escrituras sin aprobación. La contención del helper limita su vida; no
hace atómicas las escrituras de MACE.

Otros riesgos que permanecen:

- Dos MACE.exe obligan a atribuir el objetivo correctamente. El PID observado
  7264 era L, no el dueño de ventana V. El observador fue corregido; End task
  todavía necesita una captura que incluya el cierre, no una inferencia por nombre.
- Muestra persistida V2 no es consulta remota de caché ni de UI. Hay sondeo/IO a
  ~1 Hz, rotación y huecos; recent=0 y estado histórico 0 nunca demuestran
  lectura actual válida. No se certifica precisión/calibración de sensores.
- Firma pendiente: MACE/Preview/DLL/helper propios no tienen firma de publicación.
  Hashes fijados protegen bytes respecto de una raíz confiable; no autentican
  una instalación completa sustituida por un administrador.
- Excepción NDD 1.4.2: solo `app/lhm/RAMSPDToolkit-NDD.dll`, hash exacto de
  [la política](../packaging/compatibility-references.json), offsets **105819**
  y **127562**, enum reservado e interfaz vacía, sin implementación/driver activo
  aceptado. Un cambio de ruta/hash/offset requiere nueva revisión; no se amplía.
- PawnIO compartido rechaza STOP en este equipo; no se fuerza para obtener PASS.
  La recuperación física, Intel/NVIDIA/HVCI y equipo limpio siguen deuda.
- PS7 no es host validado para construir/verificar ese manifiesto; mantener
  Windows PowerShell 5.1 hasta decidir una corrección específica del tooling.
- Firma, licencia propia y revisión legal de redistribución siguen pendientes;
  no se presenta la app-image como instalador ni como release distribuible.

## Coherencia del repositorio: propuestas, sin modificar los originales

Estas son afirmaciones a matizar, **no cambios aplicados**. Referencias por
archivo y sección/búsqueda para que no dependan de números de línea futuros.

| Documento / afirmación | Matiz o actualización propuesta para aprobación |
|---|---|
| packaging/README.md, «Comprobación del payload»: «el ABI público sigue en 1.4» | Dato vigente incorrecto: ABI 1.5/DLL 1.5.0. También cita imagen de 1D; enlazar 5.1 como referencia actual |
| helper/PROTOCOL.md, párrafo «Desde Fase 2 Paso 4A»: «no se añadieron estados al ABI 1.4», NO_PROVIDER/READ_FAILED | Delimitar como historia del 4A; contrato actual agrega 9–16 en 4B. El wire sigue 2 y no necesita cambio |
| helper/README.md: «El empaquetado final y los avisos completos ... Fase 3» | App-image y avisos ya verificados en 5.1; instalador y revisión legal completa permanecen pendientes |
| helper/README.md y packaging/README.md: «después de firmar», «compila y firma» | Es el orden del pipeline con hook, no firma realizada: sin hook la construcción actual está sin firma propia |
| helper/FASE2-PASO5-PRUEBAS.md: «todos los escenarios nuevos NO VERIFICADO», «finally restaura RUNNING» | Primera frase describe entrega 5.2; ahora ejecución parcial según este cierre. finally es intento/verificación: 1052 confirma RUNNING sin transición; restauración real con STOPPED/Ctrl+C no probada |
| helper/FASE2-PASO5.1.md, apartados finales de «pendientes» y «no se entregan todavía» | Son estado fechado de 5.1, no situación actual; enlazar cierre parcial 5.2/5.3 sin reescribir evidencia histórica |
| helper/FASE2-DIAGNOSTICO-5.1.md, tabla final «resultado pendiente» | La tabla describe el cambio inicial; el propio documento ya registra PASS posterior. Evitar leer esa fila como resultado vigente |
| helper/FASE2-PASO5.0-AUDITORIA.md y 5.0, fallback Java sin pins | Hallazgo histórico resuelto en 2ef7dbf; no presentarlo como anulación activa de Production actual |
| native/README.md: enlace docs/TESTING.md y «el segundo comando genera un paquete» | El archivo no existe y el segundo comando mostrado solo build/test; package-native es el tercero. Corregir enlace y ordinal tras aprobación |
| README.md raíz: «alta precisión», «sin finalización ... automática» | Precisión física no certificada. Aclarar que la política de procesos ajenos es manual, mientras helper propio tiene contención/watchdog; no una prohibición literal de cerrar todo proceso interno |
| packaging/tests/test-runtime-payload.ps1, mensaje final «imagen ... NO VERIFICADO hasta Paso 2» | Salida histórica del arnés; imagen 5.1 ya verificada, instalador sí pendiente. No se cambia script en este subpaso |
| Scripts de manifiesto, sin distinción PS5/PS7 | Registrar requisito operativo PS5.1; PS7 ordena distinto y falla comparación. Este informe limita el host; una corrección sería cambio separado aprobado |

Los informes de 1A/1B/2 que citan ABI 1.4 o helper 1.1.0 son históricos fechados,
no deben reemplazarse indiscriminadamente por cifras nuevas. No se encontró
base para declarar verificados equipo limpio, HVCI, recuperación PawnIO real,
coexistencia o 3010 real. La lista de matices no autoriza editar esos archivos.

## Decisiones registradas y propuesta de bloqueos

Decisiones existentes: reintento manual **diferido a futuras versiones**, sin
implementarlo ni volver a proponerlo aquí; señal 3010 mediante clave volátil
**aprobada en principio**, pendiente de esquema/ACL/consumo. No se lee/escribe
esa señal. La deuda física no se convierte en PASS por cerrar la fase.

Propuesta para decisión del usuario, detallada por pendiente en el documento
compañero:

| Hito | Qué propongo exigir antes | Qué puede mantenerse como deuda explícita |
|---|---|---|
| Empezar Fase 3 | Aprobar este 5.3, alcance/tecnología del instalador, política PawnIO compartido/hash/silencioso, contrato 3010 antes de codificarlo y disponer de VM limpia antes de instalar | 1052/STOP real, LHM coexistente y hardware adicional no impiden diseñar instalador; tampoco autorizan una prueba de instalación sin VM/aprobación |
| Fusionar a principal | Regresión completa en administrador; resolver captura EndTask; completar instancia mixta/restantes y frescura inmediata; aceptar expresamente limitación CloseWarming si sigue no reproducible; revisar/reconocer persistencia y matices documentales | 3010/ausente→instalado hasta Fase 3, firma hasta release; STOP real puede quedar deuda aceptada cuando el driver no lo admite. No es requisito forzar STOP |
| Distribuir públicamente | Instalación/actualización/desinstalación limpia y silenciosa; hash/firma/origen de PawnIO; 3010 real/consumo/ACL; pruebas HVCI y hardware anunciado; estrategia de firma aplicada o excepción de distribución sin firma expresamente aprobada; licencia y revisión legal; deudas críticas de fusión resueltas | Solo limitaciones de plataforma/comportamiento expresamente documentadas y aceptadas; no prometer hardware/HVCI/coexistencia no comprobados |

La firma no se declara una exigencia técnica universal de Windows para la app;
se propone como puerta de publicación y confianza del proyecto. La decisión
final y cualquier excepción pertenecen al usuario. Los pendientes no bloquean
todos lo mismo: ver IDs P01–P13 en [FASE2-PENDIENTES.md](FASE2-PENDIENTES.md).

Antes de implementar Fase 3 se necesitan estas decisiones:

1. **Tecnología:** propuesta MSI con WiX para instalación por máquina y ciclo de
   reparación/actualización; comparar con EXE/bootstrapper para coordinar PawnIO.
   Elegir tecnología, alcance por máquina/usuario y versiones soportadas; no se
   afirma que un MSI simple resuelva por sí solo el instalador EXE de PawnIO.
2. **PawnIO silencioso:** aprobar detección/versiones admitidas, consentimiento,
   origen oficial y hash fijado, firma original, argumentos reales documentados,
   códigos de salida, timeout/log saneado y política de fallo/rollback. No cambiar
   ni volver a firmar su instalador. Verificarlo de nuevo inmediatamente antes
   de ejecutarlo; nada se instala ahora.
3. **3010:** revisar esquema/versionado y vista de la clave volátil, propietario,
   ACL de escritura/lectura, ámbito compartido, quién escribe/consume y caduca,
   reinicio real frente a suspensión, casos 0/3010/error, señal ausente o inválida.
   No usar servicio detenido como 3010; mensaje exacto: «Para habilitar los
   sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando».
4. **Desinstalador:** aprobar conservación de PawnIO compartido y política de
   datos de usuario, actualizaciones/reparación y MACE abierto. No eliminar un
   driver usado por otra aplicación. No se implementa lógica de cierre ahora.
5. **Firma:** elegir certificado/custodia y cadena de timestamp/verificación,
   sin planes mensuales según preferencia registrada, antes de adquirir o firmar.
   El orden helper → manifiesto → DLL → pins → paquete obliga a regenerar una
   release firmada completa; no refirmar archivos de esta imagen congelada.
6. **Licencia de MACE y terceros:** decidir licencia propia tras revisar todas
   las dependencias y obligaciones de redistribución/fuentes/avisos. No se
   interpreta el README ni este informe como concesión de licencia resuelta.

## Alcance del commit y parada

Solo se añaden `helper/FASE2-CIERRE.md` y `helper/FASE2-PENDIENTES.md`. No se
incluyen logs, los tres TXT locales, scripts temporales ni cambios a los informes
operativos propuestos. No cambia producto/imagen, ABI/protocolo/helper, política
4A, manifiestos, launchers o proveedores protegidos. No se instala/desinstala
ni ejecuta PawnIO, cambia HVCI, fuerza servicio o fusiona a principal.

**Fin del 5.3. Esperar aprobación del usuario; no avanzar a Fase 3.**
