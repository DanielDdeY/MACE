# Fase 2 — Paso 0: inventario del ciclo de vida del helper LHM

Fecha: **16/11/2026, America/Lima**.

Revisión realizada sobre **b825b79**, rama **LHM_implementado**, con árbol de
trabajo limpio al iniciar el inventario. El usuario solicitó después guardar
el informe en este documento. No se cambia código ni se avanza al Paso 1.

## Comprensión del alcance

La Fase 2 endurece el ciclo de vida del helper bajo responsabilidad exclusiva de
la DLL. Java consume estados y coordina el cierre de la aplicación; no duplica
la propiedad ni el lanzamiento del helper.

Se revisaron prompt_mace_fase2.md, RESULTADOS-1D-FINAL.md,
RESULTADOS-1D-PASO2.md, RESULTADOS-1B.md, PROTOCOL.md y prompt_mace_fase1.md.
No se utiliza la arquitectura descartada de la Fase 2 de prompt_mace_lhm.md.
Se conserva la arquitectura helper .NET 10 autocontenido, tubería privada y
caché nativa, sin introducir WMI ni COM.

Las rutas de evidencia siguientes son relativas a la raíz del repositorio.
Se distingue inspección de código, evidencia previa y pruebas nuevas. Este
inventario no incluye nuevas ejecuciones funcionales ni pruebas físicas.

## Inventario con evidencia

| Punto | Estado | Evidencia actual | Riesgo o carencia |
|---|---|---|---|
| 1. Lanzamiento | Parcial | native/src/telemetry/lhm_transport.c:65, LhmTransport_Open(): usa CreateProcessW, ejecutable explícito y argumento --bootstrap-stdin. Aplica CREATE_NO_WINDOW; solo hereda stdin y salida/error hacia NUL. El nombre aleatorio viaja en un bloque privado de 128 bytes por stdin. | El directorio de trabajo y el entorno se heredan de MACE; todavía no están controlados explícitamente. |
| 2. Job Object | Implementado; comprobación parcial | LhmTransport_Open() crea el job con JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, crea el hijo suspendido, lo asigna y después ejecuta ResumeThread. Si falla la asignación, no lo reanuda y entra en limpieza. | El error no tiene un diagnóstico específico hacia la UI. Falta probar jobs restrictivos. Existe una ventana entre creación y asignación en la que una muerte del padre podría dejar un hijo suspendido sin asignar. |
| 3. Cierre nativo | Parcial | native/src/telemetry/lhm_transport.c:54, LhmTransport_Close(): cierra la tubería, espera 250 ms, solicita terminación forzada si sigue vivo y espera hasta 1 s adicional. Finalmente cierra el job. | No existe un mensaje explícito de cierre. El helper detecta la tubería cerrada al intentar escribir; podría estar ocupado leyendo hardware. Esos plazos tampoco acotan todo CP_ShutdownTelemetry(). |
| 3. Cierre Java y orden | Parcial | app/src/main/java/com/mace/presentation/MaceApplication.java:115, stop(): cierra controlador/HUD, monitoreo, ViewModel, publicador y adaptadores. Los adaptadores cierran la caja negra y después NativeFunctions, cuyo cierre es idempotente. native/src/internal/mace_runtime.c:28, MaceRuntime_Shutdown(): marca primero la telemetría como no inicializada y después apaga los trabajadores. | El cierre es síncrono sobre JavaFX. Monitoreo puede esperar hasta 5 s y los trabajadores nativos se esperan con INFINITE. Actualmente no puede garantizarse que la UI no se bloquee durante el apagado. |
| 4. Reinicios | Parcial | native/src/telemetry/lhm_receiver.c:32, receive_worker(): invalida caché y reconecta con esperas cancelables de 1, 2, 4 y 8 s. Cada intento crea proceso, tubería y contexto de secuencia nuevos. | No hay límite de reinicios. El backoff se reinicia al recibir sequence >= 3; eso no equivale a contar tres muestras válidas ni tres mensajes Ready. Helper ausente o hash incorrecto al arrancar finalizan el trabajador sin reintentos. |
| 5. Localización e integridad | Parcial | native/src/telemetry/lhm_receiver.c:21, helper_path(): utiliza MACE_LHM_HELPER o lhm/Mace.Lhm.Helper.exe junto a la DLL. receive_worker() lee también MACE_LHM_HELPER_SHA256. Ambas anulaciones siguen activas en producto. | El hash es opcional y se verifica una vez antes del bucle, no antes de cada relanzamiento. Sin hash, solo emite un aviso de depuración. No hay verificación Authenticode del helper durante el arranque. |
| 6. Escrituras en disco | Parcial | En helper/Mace.Lhm.Helper/Program.cs y SensorSampler.cs no se encontró escritura propia de configuración, logs ni cachés. Los errores van a stderr, redirigido a NUL. AmdPowerPolicy.Apply() procesa el informe AMD en memoria. | Las escrituras indirectas de dependencias/runtime quedan NO VERIFICADAS mediante observación de ejecución. Los scripts de compilación y pruebas sí generan archivos, pero son herramientas de desarrollo. |
| 7. PawnIO y elevación | Parcial | helper/Mace.Lhm.Helper/PawnIoStatus.cs:9, PawnIoStatus.Read(): consulta SCM y devuelve ausente, detenido, ejecutándose o desconocido. Program.cs transmite ese resultado y su elevación. native/src/telemetry/lhm_wire.c:62, LhmWire_State(): exige ambos para CPU. | Esas causas terminan agrupadas en NO_PROVIDER. Las tarjetas muestran el estado general y el HUD muestra N/D; no llega una explicación específica. SCM Running tampoco demuestra por sí solo una lectura física correcta. |

Que CreateProcessW reciba NULL para entorno y directorio significa que hereda
ambos del proceso llamador. Se contrastó con la
[documentación de CreateProcessW](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw).

### Detalles de arranque y aislamiento existentes

- La DLL crea el servidor de la tubería con nombre aleatorio criptográfico.
- El nombre no se incluye en la línea de comandos ni en el entorno: se entrega
  mediante el bootstrap por stdin.
- Se usa una lista explícita de handles heredables; el job y el servidor de la
  tubería no se entregan al helper.
- Se mantienen ACL restringida, primera instancia exclusiva, rechazo de clientes
  remotos y comprobación del PID en ambos extremos.
- El helper conserva el token de MACE; este arranque no solicita otra elevación.
- La salida y los errores van a NUL y el proceso se crea sin ventana de consola.
- No existe lógica propia de producción para descubrir o cerrar LHM del usuario.
  El cierre actúa sobre el proceso y job que posee la sesión nativa.
- No se encontraron llamadas propias a Process.Start/CreateProcess en el helper
  de producción. La ausencia de descendientes indirectos de sus dependencias
  queda NO VERIFICADA en ejecución. El helper simulado sí crea un descendiente
  en la prueba wrong-client; eso es código de prueba, no el helper de producto.

Tras una asignación exitosa, el cierre del último handle del job aplica
KILL_ON_JOB_CLOSE a sus procesos. La pertenencia de descendientes y los límites
en jobs anidados dependen de las reglas de Windows; no se presenta esa garantía
documentada como una nueva prueba física de kill/crash de MACE.
Véanse [Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)
y [AssignProcessToJobObject](https://learn.microsoft.com/en-us/windows/win32/api/jobapi2/nf-jobapi2-assignprocesstojobobject).

### Cómo se refleja hoy en la UI

El helper consulta PawnIO durante el muestreo y transmite el estado de SCM y el
bit Elevated. LhmWire_State() los combina con estado del helper, antigüedad,
coherencia y reglas por métrica; no promueve automáticamente un número finito.
Para temperatura y potencia CPU, falta de elevación o PawnIO no Running producen
NO_PROVIDER. Los sensores GPU por API del controlador no heredan ese requisito
de CPU indiscriminadamente.

El puente Java conserva el estado de cada métrica. MetricDisplay.unavailable()
produce un texto general como «N/D: Sin proveedor». HudPane muestra «N/D» para
lecturas no válidas. No se transporta un motivo separado que distinga ausencia
de PawnIO, servicio detenido o falta de elevación.

Los ceros no válidos son marcadores nativos; el modelo Java no los muestra como
lecturas válidas. Siguen siendo posibles ceros GPU legítimos si cumplen las
reglas de validez. No se propone cambiar estas reglas.

## Artefactos y regresión previa

La app-image conserva los hashes registrados en el cierre de 1D. Se volvieron
a calcular los hashes de los dos archivos siguientes, sin ejecutar el producto:

| Archivo dentro de packaging/dist/MACE | SHA-256 |
|---|---|
| app/mace_native.dll | 4D5E10F1CEF44A26BE4F2DA32955489613266AB33759496EECAD07B5E839EED8 |
| app/lhm/Mace.Lhm.Helper.exe | DEAA3EC9CBA4EEA670471382C45348A8044C4D01E5C52FC90AAF7A1449068C19 |

No se ejecutaron nuevamente las suites en este inventario. La última regresión
documentada en RESULTADOS-1D-PASO2.md sigue siendo:

| Batería | Resultado previo, no repetido en este paso |
|---|---|
| Helper | 63 aprobadas |
| CTest | 40 aprobadas |
| Java | 278 aprobadas y una opcional omitida; cero fallos/errores |

La prueba Java omitida requiere una DLL antigua externa mediante MACE_LEGACY_DLL.
Las pruebas anteriores simuladas no demuestran soporte físico adicional.

## Conflictos, decisiones y sugerencias

Las siguientes son propuestas para aprobación. Ninguna está implementada por
este inventario.

### 1. Estados detallados frente al contrato actual

V2 no distingue PawnIO ausente, detenido, helper ausente y reintentos agotados.
native/include/mace_types_v2.h exige que sus campos reserved permanezcan en cero.

Se recomienda acordar códigos adicionales dentro del campo state, conservando
los códigos existentes y todos los tamaños/offsets. Eso requiere revisar
expresamente la compatibilidad semántica C/Java; no se reutilizarían campos
reservados por iniciativa propia. Mantener el tamaño de un campo no elimina
la necesidad de acordar su significado público.

**Decisión solicitada:** aprobar el enfoque de ampliar los estados del campo
existente, con tabla de códigos y compatibilidad revisada antes de implementarlo,
o elegir otra vía diagnóstica que preserve el contrato.

### 2. Señal de cierre frente a tubería unidireccional

Actualmente la DLL solo recibe y el helper solo escribe. Para enviar una orden
explícita se propone convertir el canal privado en dúplex y documentar el mensaje
de control, conservando las protecciones y el formato de las muestras.

Se recomiendan **2 s de cierre cooperativo y hasta 1 s adicional para confirmar
terminación forzada**, ejecutados fuera del hilo JavaFX. Una espera infinita de
los trabajadores sigue necesitando tratamiento separado. Estos plazos propuestos
no son una garantía ya implementada para todo el apagado.

**Decisión solicitada:** aprobar canal privado dúplex y esos plazos como objetivo
de cierre del helper. No supone cambiar los structs públicos V2.

### 3. Job Object y muerte durante el arranque

Se recomienda evaluar PROC_THREAD_ATTRIBUTE_JOB_LIST para asignar el job durante
la creación y reducir la ventana entre CreateProcessW y AssignProcessToJobObject.
Microsoft documenta este atributo para Windows 10 y posteriores:
[UpdateProcThreadAttribute](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute).

Si falla la asociación, la propuesta es **abortar únicamente el helper**, mantener
las métricas independientes y mostrar el motivo. No permitir que el helper se
ejecute sin contención. La compatibilidad con jobs restrictivos debe probarse;
no basta inspeccionar las banderas actuales.

**Decisión solicitada:** aprobar la degradación sin helper cuando no pueda
garantizarse su pertenencia al job, manteniendo MACE operativo.

### 4. Seguridad de producción y orden de compilación/firma

Se recomienda una opción explícita de desarrollo, desactivada en producción,
y hashes esperados incorporados a la DLL para el helper y sus dependencias de
ejecución. Deben fijarse **después de firmar el helper**: hoy staging puede
firmarlo posteriormente y cambiar sus bytes.

Debe comprobarse la integridad antes de cada lanzamiento. El manifiesto actual
sin firma, por sí solo, no constituye una raíz de confianza. El hash no autentica
al publicador ni protege una instalación completa que un atacante pueda
reemplazar. Quedan por tratar la ruta canónica, sustituciones entre verificación
y arranque, dependencias cargadas y permisos de la carpeta de producto.

Otro proceso no cambia automáticamente el entorno de un MACE ya iniciado.
El riesgo incluye controlar su lanzamiento, el entorno heredado o los archivos
que utiliza. Esta distinción evita atribuir a las variables una capacidad que
no tienen por sí solas. Véase
[Changing Environment Variables](https://learn.microsoft.com/en-us/windows/win32/procthread/changing-environment-variables).

También se recomienda un entorno explícito para el helper y directorio de
trabajo controlado. Si en el futuro necesita escribir, se deberá definir una
ruta apropiada por usuario; no Program Files. Los diagnósticos nuevos deben
usar códigos y rutas relativas/marcadores, sin perfiles ni nombres de equipo.

**Decisión solicitada:** aprobar anulaciones solo en desarrollo y confianza
fijada durante compilación/empaquetado de producción, conservando los ganchos
de firma pendientes.

### 5. Prueba sin elevación frente al launcher actual

packaging/app.manifest exige administrador para MACE.exe y
packaging/preview-launcher.properties utiliza datos simulados para
MACE-Preview.exe. Preview no demostraría el requisito de telemetría real sin
elevación.

Se recomienda conservar ambos comportamientos y preparar una ejecución de
prueba con **Java y telemetría nativa real sin elevar**.

**Decisión solicitada:** conservar los launchers actuales y usar esa vía de
prueba específica, sin cambiar silenciosamente el manifiesto del producto.

### 6. Mensaje 3010 sin instalar PawnIO en esta fase

Actualmente no existe una fuente fiable de ese resultado. En Fase 2 se pueden
implementar y simular el mensaje y su mapeo. En Fase 3 deberán conectarse con
el resultado real del instalador.

**PawnIO detenido no debe interpretarse como reinicio pendiente.**

El mensaje solicitado se conserva como requisito:

> Para habilitar los sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando.

**Decisión solicitada:** aceptar la separación entre presentación/simulación
en Fase 2 y origen real del estado 3010 en Fase 3. No se ejecutará el instalador
en este equipo.

### 7. Reintentos, instancias y recuperación

Se propone un arranque inicial y un máximo de **tres relanzamientos por 60 s**,
con backoff y agotamiento persistente hasta reiniciar MACE. Es una política
propuesta: el cómputo exacto de ventana, reinicio del contador y estados se
documentará antes de implementar el Paso 4.

Se recomienda una instancia por usuario/sesión para evitar doble sondeo, y no
terminar supuestos huérfanos por nombre o PID almacenado. No se debe tocar un
LHM abierto por el usuario ni usar terminación global por nombre de ejecutable.

Además, habrá que comprobar la recuperación al iniciar PawnIO en caliente:
hoy se consulta su estado periódicamente, pero Computer.Open() se ejecuta
una sola vez. Detectar Running no demuestra que el muestreador se haya
reinicializado correctamente tras haber arrancado sin el driver.

**Decisiones solicitadas:** aprobar la política de reintentos propuesta y elegir
una instancia MACE por usuario/sesión frente a helpers independientes.

### 8. «Sin modificar nada» frente a «un commit por paso»

El inventario original respetó la instrucción específica de solo lectura y se
entregó en el chat sin crear archivos ni commits. Se propuso incorporarlo al
commit del Paso 1 cuando se aprobara avanzar.

El usuario ha autorizado ahora guardar todo el informe en este Markdown.
Esa autorización documental no implica aprobación del Paso 1. La regla de un
commit por paso, sin reescribir historial, sigue siendo un requisito para la
ejecución de la fase; no se ha iniciado implementación mediante este documento.

## NO VERIFICADO

- Coexistencia con LHM abierto por el usuario y acceso compartido a sensores/PawnIO.
- Suspensión y reanudación.
- CPU Intel en hardware real.
- NVIDIA: NVML principal y respaldo LHM en hardware real.
- Intel GPU/IGCL y respaldo LHM en hardware real.
- PawnIO ausente en equipo limpio y su instalación limpia.
- HVCI con el conjunto actual.
- Instalación MSI/EXE y resultado real 3010.
- Jobs restrictivos, muerte del padre durante el arranque y nuevas garantías de
  cierre/fallos todavía no implementadas ni probadas en esta fase.
- Ausencia de procesos hijos indirectos y escrituras indirectas de dependencias/runtime.
- Recuperación física al cambiar el estado de PawnIO en caliente.

No se presenta una simulación ni una inspección de código como prueba física.
No se demuestra equivalencia en plataformas sin validar. Firma, licencia de
MACE y revisión legal de redistribución permanecen pendientes, con sus ganchos.

## Parada

### Aprobación posterior del usuario

El usuario aprobó el Paso 0 y las decisiones 1 a 8, con estos ajustes vinculantes:

- El agotamiento de reinicios deberá permitir recuperación posterior mediante
  reintentos lentos o acción manual; no será permanente.
- PawnIO que pasa a Running deberá provocar reinicialización del muestreador o
  relanzamiento del helper, sin consumir el presupuesto de reintentos.
- El reinicio del backoff dependerá de muestras válidas recibidas, no de sequence.
- Este informe se guarda primero en un commit propio: docs: Fase 2 Paso 0.
- Paso 1A: JOB_LIST, cierre acotado fuera de JavaFX y esperas nativas acotadas,
  sin cambios de ABI ni protocolo. Detenerse al terminar hasta nueva aprobación.
- Paso 1B: canal dúplex y cierre cooperativo con protocolo versionado, por separado.
- Estados aditivos y versión ABI documentada: Paso 4, con compatibilidad ante
  códigos desconocidos y sin usar campos reserved.
- Seguridad/orden de firma y manifiesto de dependencias: Paso 2. Instancia única:
  Paso 3. Reintentos, recuperación y decisiones 5/6: Pasos 4 y 5 según corresponda.

Esta aprobación sustituye las propuestas originales que difieran de esos puntos.
No autoriza instalar/desinstalar PawnIO, cambiar HVCI ni fusionar a principal.

**Paso 0 terminado. Se espera aprobación del usuario antes de comenzar el Paso 1.**
No se cambió HVCI, no se instaló ni desinstaló PawnIO, no se ejecutó su instalador,
no se fusionó a la rama principal y no se inició otra fase/paso.
