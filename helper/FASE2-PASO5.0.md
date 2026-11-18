# Fase 2 — Subpaso 5.0: consolidación sin código

Fecha: 2026-11-17. Rama `LHM_implementado`.
Commit de partida: `5f818cb7c0e6cceafab46300d8d350209088d208`.
Antes de empezar, el árbol solo contenía tres archivos sin seguimiento:
`diagnostico-admin.txt`, `diagnostico-normal.txt` y `prueba-cpu.txt`.
No se modifican ni se incluyen en el commit. No se reescribe historial.

Se consolida evidencia previa y la aprobación del usuario de 4B. No se repiten
pruebas funcionales, no se regenera la imagen ni se prepara todavía el 5.2.
Solo se revisa documentación y se comprueba estáticamente el cambio documental.
ABI 1.5, DLL 1.5.0, protocolo privado 2 y helper 1.1.1 quedan intactos.

## Criterio de estados

- **AUTOMATIZADO:** existe una prueba y un resultado registrado; no significa
  verificación física del escenario equivalente con el producto elevado.
- **VERIFICADO POR EL USUARIO:** resultado comunicado por el usuario, con el
  alcance indicado; no se extrapola a otra imagen ni a una batería completa.
- **NO VERIFICADO:** no hay comprobación suficiente; incluye intentos que no
  lograron provocar la condición, como la parada de PawnIO rechazada.
- **PENDIENTE DE MANUAL:** comprobación concreta que queda para el usuario.

Las rutas de evidencia se expresan respecto a la raíz del repositorio.
Los informes históricos describen la versión de su paso: la política definitiva
es la de 4A y los códigos definitivos son los de 4B, no las propuestas iniciales.

## Tabla de consolidación

| Requisito | Estado | Evidencia y alcance / qué falta |
|---|---|---|
| Responsabilidad única de la DLL sobre el helper; sondeos e inicialización no esperan al helper | AUTOMATIZADO | `lhm_slow`, `lhm_init-race`, `lhm_shutdown_budget`, `concurrency`; informes 1A/4B. Java no lanza un segundo helper |
| Job con KILL_ON_JOB_CLOSE y asignación durante creación mediante JOB_LIST | AUTOMATIZADO | `lhm_job_contained`, `descendants`, `abrupt-parent`, `restrictive-job`; falla de forma cerrada, sin ejecutar helper sin contención; informe 1A |
| Pruebas job con tokens normal y administrador | VERIFICADO POR EL USUARIO | Usuario confirma que las pruebas que fallaban pasan en ambos. Los controles con pin correcto pasan; no equivale a toda la regresión elevada |
| Cierre nativo acotado y limpieza Java fuera de JavaFX | AUTOMATIZADO | `lhm_shutdown_budget`, `lhm_job_forced-close`; `ShutdownCoordinatorTest`, `MaceApplicationStopTest`. Presupuesto público 4 s; cooperación hasta 2 s y confirmación forzada hasta 1 s, según 1A |
| Watchdog Java de último recurso de 10 s | AUTOMATIZADO | `ShutdownWatchdogProcessTest`, tres procesos de prueba; salida por halt si la limpieza no termina. Pérdida de datos ante halt sigue siendo un riesgo |
| Cierre normal real del dashboard con CPU/GPU y helper activo | VERIFICADO POR EL USUARIO | Dashboard elevado con DLL 1.5.0 de desarrollo: CPU/GPU con valores y helper desaparece al cerrar. No es prueba de la nueva app-image |
| Cierre X y taskkill del MACE.exe elevado completo de la imagen nueva | PENDIENTE DE MANUAL | 5.1 debe generar la imagen; 5.2 probará ambos cierres y ausencia posterior de helper. No se ejecutan aquí |
| Control privado de cierre, invalidación/duplicado y EOF independiente | AUTOMATIZADO | `lhm_job_control-close`, `lhm_job_eof-close`; pruebas de control inválido/duplicado del helper documentadas en 1B. EOF sigue suficiente para salir |
| Compatibilidad de componentes y wire | AUTOMATIZADO | `lhm_wire`, `abi_layouts`, `abi_exports`, caso `lhm_old-version`. Protocolo 2, helper 1.1.1; CPU 56, GPU 104, inventario 296, legado 28 bytes; 24 exports |
| Pipe privado: aleatoriedad, bootstrap sin nombre en comando, ACL, primera instancia, rechazo remoto y PID bilateral | AUTOMATIZADO | `lhm_bad-pid`, `wrong-client`, `bad-server-pid`, `replay`, `fragmented`, `malformed`; `PROTOCOL.md` e informes 1A/1B. No se amplió el wire |
| Anulaciones del helper solo en Development; hash compilado en producción | AUTOMATIZADO | `lhm_actual_production_ignores_environment`, `lhm_security_ignore-env`; `helper_path()` y `LhmTrust_Acquire()` tienen guard de compilación. Ver auditoría adjunta |
| Anulaciones Java de ruta/hash de la DLL | NO VERIFICADO | Como prohibición universal en producción, no se cumple sin pins JVM: Java directo conserva el fallback de entorno. Hallazgo H-5.0-JAVA y decisión de alcance pendientes; con launcher íntegro prevalecen propiedades. No se cambió código |
| Prueba integral de la imagen con las cuatro variables ajenas | PENDIENTE DE MANUAL | 5.1: proceso controlado, verificar pins y componente efectivamente usado; si exige administrador, ejecuta el usuario |
| Manifiesto runtime y confianza antes de cada relanzamiento | AUTOMATIZADO | 202 archivos runtime fijados; `lhm_security_valid`, `missing`, `manifest`, `tampered`, `extra`, `locks`, `relaunch`; informe 2. Orden firma → manifiesto → DLL conservado |
| Entorno/directorio del helper controlados y archivos retenidos | AUTOMATIZADO | Fixture de seguridad valida entorno, bloqueo de escritura/borrado y relanzamiento. Canonicalización en `lhm_trust.c`; no demuestra resistencia a un administrador que sustituya la instalación completa |
| Firma y autenticidad del publicador | NO VERIFICADO | Firma/certificado pendientes, ganchos existentes. Un hash no autentica al publicador; no se simula una firma válida |
| Escrituras propias del helper y separación de datos | AUTOMATIZADO | Inventario de runtime sin archivos nuevos en Paso 2; helper propio sin persistencia de configuración/logs. Scripts y evidencias permanecen fuera del producto |
| Escrituras indirectas, instalación en Program Files y consistencia de persistencia tras halt | NO VERIFICADO | Falta observación exhaustiva de runtime/dependencias/sistema y prueba de datos tras terminación de último recurso. No se afirma atomicidad ni ausencia total de escrituras |
| Instancia única por usuario/sesión y liberación al morir | AUTOMATIZADO | Ocho casos de `SingleInstanceProcessTest`; mutex Local con identidad por usuario, antes de telemetría. Informe 3 |
| Instancia elevada frente a normal, misma sesión | PENDIENTE DE MANUAL | Prueba manual de Paso 3 pendiente con la imagen; namespaces aislados del arnés no acreditan todos los permisos de integridad mixta |
| Helper huérfano y no terminar procesos ajenos por nombre | AUTOMATIZADO | `lhm_job_abrupt-parent`, `descendants`, `foreign-homonym`; job poseído, sin heurística de procesos huérfanos. Falta kill del producto elevado completo |
| Coexistencia con LHM de escritorio del usuario | PENDIENTE DE MANUAL | `foreign-homonym` solo prueba no tocar un homónimo; no demuestra coexistencia de sensores ni controlador compartido. Usuario abrirá/cerrará LHM en 5.2 |
| Presupuesto, backoff y modo lento | AUTOMATIZADO | `lhm_retry_policy_budget`, `streak`, `permanent`, `recovery`, `slow-cap`; casos `lhm_retry_*`. Inicial + tres relanzamientos por ventana móvil de 60 s activos; backoff rápido 1/2/4/8 s y lento 30/60/120/240/300 s, según 4A |
| Historial de relanzamientos, tres muestras útiles y reloj activo | AUTOMATIZADO | `lhm_retry_policy_streak`, `suspend`, `lhm_retry_retry-streak`. Tres muestras útiles restablecen backoff, sin borrar historial de ventana; reloj excluye suspensión |
| Matar helper con UI abierta y recuperación real con nuevo PID | PENDIENTE DE MANUAL | Hay simulación de desconexión, bloqueo, mensajes inválidos y recuperación; falta hacerlo sobre la app-image elevada y observar UI/PID. Este agente no termina procesos |
| PawnIO detenido → RUNNING y reapertura LHM con cooldown de 30 s | AUTOMATIZADO | `helper/Mace.Lhm.Tests/RecoveryTests.cs`, `lhm_retry_retry-pawn`; estados simulados, sin cambiar política 4A |
| Recuperación física de PawnIO por parada real | NO VERIFICADO | Usuario: sc stop devolvió ControlService ERROR 1052; servicio siguió RUNNING y helper activo. No ocurrió la transición; CPU/GPU sin cambios no prueban recuperación. No se fuerza |
| PawnIO ausente que pasa a instalado y origen real de 3010 | NO VERIFICADO | Fase 3 en VM limpia; no se ejecuta instalador. Señal volátil aprobada solo en principio, todavía sin esquema/ACL/consumo aprobados ni implementados |
| Estados públicos 9–16, cero no válido y código desconocido | AUTOMATIZADO | `metric_reasons`, `SensorReasonTest`, `V2RecordsTest`; desconocido → N/D, solo VALID permite valor. CPU sin elevación/PawnIO y GPU ambigua/UnknownPower conservan reglas; 0 RPM legítimo no se invalida por ser cero |
| Mensajes y once escenarios Preview | VERIFICADO POR EL USUARIO | Usuario informa resultados esperados de UI y once escenarios simulados; incluye mensaje 3010 exacto. Simulación de 3010 no demuestra señal del instalador |
| Java + DLL real sin elevar | VERIFICADO POR EL USUARIO | Usuario confirma prueba correcta. Evidencia previa 4B: DLL de producción ABI 1.5 muestra estado 9; uso CPU y RAM independientes continúan. No se modifican manifiestos/launchers |
| Tolerancia visual WARMING_UP y estabilidad del layout | AUTOMATIZADO | `TelemetryPresentationTest`, `SensorCardLayoutTest`: un sondeo hasta 1.500 ms, misma fuente; segundo consecutivo muestra N/D. No altera datos crudos/alertas/persistencia ni oculta fallos explícitos |
| Interrupción Radeon durante reapertura física de LHM, parpadeo y cierre en calentamiento | PENDIENTE DE MANUAL | Simulación breve/larga aprobada; falta recuperación física y observación de UI de la imagen. No extender la gracia para ocultar cifras antiguas |
| Suspensión/reanudación sin aceptar muestras antiguas | PENDIENTE DE MANUAL | Pruebas de reloj activo/frescura automatizadas; suspensión física y recuperación UI con la imagen siguen sin ejecutar |
| Rutas protegidas de telemetría, ABI y proveedores primarios | AUTOMATIZADO | `check-protected-telemetry` PASS; ABI/exports y pruebas de despacho. Sin cambios a NVML, IGCL, GetSystemTimes/ReadCpuUsagePercent, DXGI ni RAM Windows |
| Imagen sin hooks, docs, tests, scripts, artifacts o logs; UAC y PawnIO intacto | PENDIENTE DE MANUAL | Verificadores existentes y payload previos aprobados; falta regeneración y cotejo de nueva imagen en 5.1 con versiones/tamaños/hashes. No se ejecuta PawnIO |
| Dos símbolos NDD de compatibilidad aceptados | AUTOMATIZADO | `packaging/compatibility-references.json` fija ruta, hash y offsets 105819/127562; `verify-image.ps1` y `packaging/tests/test-image.ps1` limitan la excepción. Revalidar esos mismos límites en 5.1 |
| Auditoría de referencias retiradas y privacidad del material versionado | AUTOMATIZADO | Evidencia anterior: 181 aceptadas, 0 inesperadas; tabla TSV sin datos personales según cierre 1D. Nuevos documentos sin rutas de perfil, SID, hostname o hashes personales; no se publican logs originales |
| Hardware adicional, HVCI y equipos limpios | NO VERIFICADO | No se extrapolan simulaciones ni una CPU/GPU local a Intel/NVIDIA, HVCI o instalación limpia. Sin cambiar HVCI ni instalar drivers |
| Licencia de MACE y revisión legal de redistribución | NO VERIFICADO | Pendientes, con ganchos y avisos de terceros conservados. No se presentan como decisiones ya cerradas |

## Resultado del diagnóstico de entorno y README del arnés

El [README del arnés](../native/tests/README.md) documenta la precondición de
entorno y el comando de proceso hijo que elimina solo sus variables `MACE_*`.
`test-lhm1b.ps1` no limpia automáticamente el entorno antes de CTest.
`diagnose-lhm-job.ps1` contrasta heredado/pin correcto/pin incorrecto y restaura
el valor de proceso en finally; no cambia variables persistentes.

La evidencia local aportada por el usuario muestra:

| Consola | Heredado | Control con pin del fixture | Negativo deliberado |
|---|---|---|---|
| Normal | CTest 0; no había pin heredado | CTest 0 | CTest 8 esperado |
| Administrador | CTest 8; pin heredado no corresponde al fixture | CTest 0 | CTest 8 esperado |

Esto confirma la dependencia de un pin ajeno en el caso diagnosticado; no un
requisito de token no elevado ni una corrección automática del aislamiento.
Se registra también la declaración actual del usuario de éxito en ambas
consolas. No se convierte esa declaración de las pruebas antes fallidas en
evidencia de toda la matriz de regresión.

Se propone ignorar únicamente `/diagnostico-admin.txt`,
`/diagnostico-normal.txt` y `/prueba-cpu.txt` en `.gitignore`, o localmente en
`.git/info/exclude`. No se cambia la política de ignorados en esta entrega.

## Auditoría de anulaciones

Véase [el informe de auditoría](FASE2-PASO5.0-AUDITORIA.md), con archivos,
funciones, pruebas y límites. Resultado: helper cerrado en producción;
Java protegido por pins del launcher, con fallback todavía activo cuando
faltan. H-5.0-JAVA requiere decisión de alcance antes de continuar con la
imagen; no se implementa endurecimiento ni se cambia el launcher aquí.

## Regresión consolidada, sin repetir ejecuciones

Evidencias existentes en `native/build/fase2-paso4b/` (no se versionan):

| Batería | Último resultado normal registrado | Administrador, matriz completa actual |
|---|---|---|
| Helper | 105 PASS (`helper.log`) | NO VERIFICADO |
| CTest Development | 76/76 (`dev.log`) | VERIFICADO POR EL USUARIO después de aprobar 5.0: consola limpia, sin MACE_*, test-lhm1b -RealHelper -RequireCpu; código 0, CPU real con DLL 1.5.0 de desarrollo |
| CTest Production | 77/77 (`prod.log`) | NO VERIFICADO; no atribuir el éxito job a los 77 casos |
| Java | 334 PASS + 1 omitida, 335 total; sin fallos/errores (`java.log`) | NO VERIFICADO |
| Payload | 8 PASS + rechazo Development PASS (`payload.log`) | NO VERIFICADO |
| check-protected-telemetry | PASS (`protected.log`) | NO VERIFICADO |
| audit-retired-providers | 181 aceptadas, 0 inesperadas (`audit.log`) | NO VERIFICADO |

La omitida es la integración opcional con DLL antigua externa. No hay cambios
de código ni nuevos casos en 5.0: las cifras esperadas no cambian. El diagnóstico
añadió instrumentación, no tests: mantiene 76/77. Los resultados previos de 4A
(74 casos) y su CPU física elevada no se sustituyen por resultados inventados
de la matriz 4B. La regresión completa exigida en ambos tipos de consola sigue
pendiente de consolidar sobre los artefactos finales; la ejecutará el usuario
cuando implique administración o terminación de procesos.

Evidencia adicional posterior a 5.0: el usuario confirmó la fila Development
elevada y CPU real indicada arriba. No cubre CTest Production, Java, helper,
payload ni la nueva imagen. La decisión H-5.0-JAVA fue endurecer antes de 5.1;
el cambio y la nueva regresión se documentan en [su informe](FASE2-H-5.0-JAVA.md).

## Historial de cambios de la fase

| Paso | Commit(s) |
|---|---|
| 0, inventario | `bcc7f1c9615084a4ff2d29a8e13874f6165f3b64` |
| 1A, contención/cierre | `fc615ed507e487922ee2b8dffd40e0996e7648ce` |
| 1B, control/EOF/watchdog | `220697310435c0c07bf967ed126073dd496032e7` |
| 2, confianza/runtime | `7510fa48d5dfc4b7867509907bf652359155cc9c` |
| 3, instancia única | `83348cf0aa9158dfb46b00aa51f0549f706819c2` |
| 4A, recuperación/reintentos | `4ffd231a3c454904997d1c228fefaa3d34e6200d` |
| 4B, ABI/documentación | `63cedd5` |
| 4B, Java/UI y ajustes | `0adca05`, `1cc99f6`, `8ee6ef5` |
| 4B, pruebas/informe | `e47cb54` |
| Diagnóstico sin cambio funcional | `5f818cb7c0e6cceafab46300d8d350209088d208` |

Los detalles y límites por paso permanecen en `FASE2-PASO0.md`,
`FASE2-PASO1A.md`, `FASE2-PASO1B.md`, `FASE2-PASO2.md`, `FASE2-PASO3.md`,
`FASE2-PASO4A.md`, `FASE2-PASO4B.md` y `DIAGNOSTICO-4B-ELEVACION.md`.
Este documento actualiza su estado con la evidencia posterior del usuario.

## Decisiones registradas y condiciones para cerrar

- Acción manual de reintento: diferida a futuras versiones; no se implementa
  ni se vuelve a proponer en esta fase.
- Señal volátil de 3010: aprobada en principio para Fase 3, sujeta a revisión
  de esquema, ACL y consumo. En Fase 2 solo mapeo y simulación; no se leyeron
  ni escribieron claves de registro.
- PawnIO no aceptó la parada: recuperación física NO VERIFICADA. En 5.2 se
  preparará un intento normal con confirmación, aborto sin forzar y restauración.
  La transición ausente → instalado corresponde a VM limpia en Fase 3.
- Firma, licencia y revisión legal continúan pendientes; no se bloquean los
  ganchos ni se afirman resueltas para distribución pública.

Antes de cerrar Fase 2 faltan: resolver el alcance H-5.0-JAVA; aprobar el avance
desde este subpaso; regenerar/verificar la imagen en 5.1 y obtener confirmación
del usuario; entregar y ejecutar el plan manual 5.2 con resultados saneados;
consolidar la regresión completa normal/administrador; y redactar el informe
5.3 con riesgos y prerrequisitos de Fase 3. Los puntos no provocables físicamente
permanecerán NO VERIFICADO, sin fingir cobertura por simulación.

## Cambios de este subpaso

| Archivo | Cambio |
|---|---|
| `native/tests/README.md` | Contrato de entorno, comportamiento real del arnés, invocación aislada y propuesta de ignorar los tres TXT |
| `native/README.md` | ABI vigente 1.5 y enlace al README del arnés |
| `helper/README.md` | Helper vigente 1.1.1, estados CPU y precisión sobre qué anulaciones ignora la DLL |
| `helper/FASE2-PASO5.0-AUDITORIA.md` | Auditoría sin código, evidencia y hallazgo condicionado al arranque Java sin pins |
| `helper/FASE2-PASO5.0.md` | Consolidación, historial, resultados y pendientes |

**Fin de 5.0: detenerse antes de 5.1.** No se genera todavía la imagen ni el
documento/scripts de 5.2. Tras 5.1 habrá otra parada para que el usuario confirme
la imagen. No se instala/desinstala ni ejecuta el instalador PawnIO; no se fuerza
servicio, eleva, termina proceso, cambia HVCI, manifiesto, launcher o rama principal.
