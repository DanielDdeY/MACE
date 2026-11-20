# Fase 2 — Subpaso 5.2: plan manual con la imagen elevada

Fecha de entrega: 2026-11-18. Partida confirmada: `9895c9b4b4f3524828d358390700bc67e7cf376a`,
rama LHM_implementado, árbol limpio antes de preparar esta entrega.

**ENTREGABLE SIN EJECUCIÓN. Todos los escenarios nuevos: NO VERIFICADO.**
El agente no ejecutó scripts, compiló el soporte C#, abrió MACE, mató procesos,
controló servicios, suspendió el equipo ni solicitó elevación para este subpaso.
Solo se preparan archivos y revisión estática. Tampoco se repiten las pruebas
ya aportadas: 5.1 con cuatro variables ajenas y selección de componentes está
VERIFICADO POR EL USUARIO, no demuestra los cierres o sensores de este plan.

## Preparación y reglas de ejecución

1. Guarda tu trabajo. Usa PowerShell **de 64 bits**, consola de administrador
   abierta por ti, misma cuenta y sesión que las consolas normales cuando se
   indique. Los scripts no elevan ni reducen tokens automáticamente.
2. Desde la raíz del repositorio usa la imagen `packaging/dist/MACE` del 5.1.
   Cierra tú MACE/Preview anteriores, incluidas ejecuciones desde fuentes.
   No inicies pruebas nuevas si quedan procesos de una anterior sin aclarar.
3. Cada comando abre una instancia nueva y registra primero su propio árbol.
   No elijas una JVM por nombre global. No uses taskkill /IM ni cierres todos
   los procesos Java. El arnés comprueba el inventario de la imagen antes de
   iniciar; no reconstruye ni modifica sus archivos.
4. Deja el dashboard abierto durante las líneas de base. Cuando llegue la
   instrucción de acción, realiza solo la acción de ese caso. Los casos que
   incluyen taskkill vuelven a comprobar identidad y piden **CONFIRMO**.
5. Conserva `result.txt`, anota el resultado visual en la ficha del final y
   cierra tú las ventanas que queden abiertas. No hay limpieza de procesos
   automática, ni siquiera tras un fallo o Ctrl+C.

Los scripts son archivos de repositorio en packaging/tests, fuera del producto.
Al ejecutarlos tú, Add-Type compilará el soporte de QA en el proceso de PowerShell.
La compilación y los escenarios completos están **NO VERIFICADO** en esta entrega.
Si hay error de compilación/sintaxis, detén ese caso y comunícalo; no lo cuentes
como fallo del producto ni cambies políticas para obtener PASS.

Revisión de entrega: los cuatro archivos PowerShell pasan el análisis estático
de sintaxis (Parser.ParseFile, sin ejecutarlos ni dot-sourcearlos). Esto no
valida la compilación C#, permisos, servicios, UI o comportamiento en ejecución.
El inventario SHA256 de la imagen conserva su hash del 5.1; no se regenera.

## Identificación y evidencia

Se reutiliza **Find-ImageJvmProcess** de `image-process-observer.ps1`, ya usado
en 5.1. Para la imagen habitual:

- **L**: launcher inicial MACE.exe, el PID que inicia el script.
- **V**: MACE.exe que carga jvm.dll; puede ser hijo de L. Se contrasta árbol,
  ruta y creación. El test 5.1 del usuario observó dos MACE.exe y una JVM.
- **H**: Mace.Lhm.Helper.exe de la imagen, hijo de V. Se verifica ruta y edad.

El script imprime L/V y, antes de taskkill, el PID exacto del objetivo.
`window_owner` identifica el dueño de la ventana de V; no se registra su título.
Tras morir L se conservan los handles de V/H ya identificados y se siguen
observando. No se interpreta la muerte de L como prueba suficiente de muerte
de V/H. Un PID puede reutilizarse: el arnés revalida ruta/creación justo antes
de taskkill; /PID sigue teniendo una ventana de carrera, por lo que no debes
copiar y reutilizar un PID viejo posteriormente.

Si L=V, **KillLauncher se rechaza**: no existe launcher separado que matar.
No se sustituye ese caso por matar la JVM. Si una identidad no pudo observarse,
el resultado es NO VERIFICADO, no "ningún proceso restante".

### Registros por segundo y estados V2

Los logs están en `native/build/fase2-paso5.2/<caso>-<id>/result.txt`, ignorados
por Git. El script imprime únicamente esa ruta relativa. Se guardan campos
fijos, números, PIDs, códigos y relaciones; sin perfiles, usuario, equipo, SID
ni hashes completos. No pegues datos privados en la ficha. La salida cruda de
MACE se consume mediante el capturador saneado del arnés, no se copia al log.

Campos principales:

| Campo | Interpretación |
|---|---|
| root_pid/root_alive/root_exit | L y su estado/salida, si se conoce |
| jvm_pid/jvm_observed/jvm_alive/jvm_exit | V observado y su vida/salida; 0/observed=0 no demuestra ausencia |
| helper_observed/helper_alive/helper_pids | Helpers atribuidos a V y handles conservados, incluidos los anteriores al relanzamiento |
| helper_query_ok | Enumeración actual de hijos atribuibles; 0 significa cobertura incompleta o V ya terminado |
| image_helper_count | Recuento adicional por ruta exacta del helper de la imagen, solo diagnóstico; -1 indica inspección incompleta. Nunca elige objetivos de kill |
| pawnio/pawnio_error | SCM: RUNNING, STOPPED, pendientes o UNKNOWN y código Win32 |
| bb_source=JVM_PERSISTED_V2 | Copia pasiva de los V2 que escribió la propia JVM en su caja negra; no una consulta remota a la caché |
| cpu_t/cpu_p/cpu_u | Estados V2 de temperatura, potencia y uso CPU |
| g1_t/g1_p/g1_u/g1_fan/g1_mem | Estados V2 de cada GPU, etiquetas locales g1, g2…; sin nombres/identidades de hardware |
| cpu_provider/g1_provider | IDs de proveedor del contrato, sin reinterpretar la prioridad NVML/IGCL/LHM |
| cpu_recent/g1_recent | 1 solo para registro reciente, con JVM viva; 0 impide tratar el estado histórico como actual |
| cpu_record_age_ms/cpu_stamp_ns | Edad del registro y timestamp de su payload; no prueba aislada de frescura de cada sensor |
| bb=MISSING/UNSTABLE/IO_OR_PARTIAL/PARTIAL_TAIL | Falta de datos o lectura concurrente/incompleta; no inventa estado VALID |
| monitor_gap_ms | Hueco entre observaciones, útil tras suspensión; no distingue por sí solo suspensión de un bloqueo del monitor |

El lector abre **solo READ**, comparte acceso con el escritor, verifica CRC y
secuencia y contrasta PID/creación de sesión. No repara, escribe ni borra la
caja negra. No carga la DLL ni abre un segundo helper para medir, evitando
alterar el caso de PawnIO. Los datos proceden del formato de `BlackboxFileReader`,
`BlackboxFileFormat` y `V2Records.encodeCpu/encodeGpu` ya existentes.
Son los snapshots que Java volvió a codificar para persistir, antes de la
tolerancia visual; no una lectura remota de los bytes de la caché nativa.

Limitaciones: puede perder una transición menor de un sondeo a 1 Hz; hay
latencia de persistencia y rotación. Si caja negra está deshabilitada, no es
legible, no contiene CPU/GPU o el registro no es reciente, la parte V2 queda
**NO VERIFICADA**. Un 0 histórico con recent=0 no es una lectura actual válida.
El estado 0 significa VALID, no valor numérico cero; aquí se registran estados,
no temperaturas, watts ni RPM. Solo los estados recientes y la observación UI
apoyan las conclusiones; no se deducen valores físicos de PIDs o de la existencia
del helper. Datos simulados de Preview se identifican como tales.

## Matriz de cierres: expectativas de diseño, no resultados

**Cada fila está NO VERIFICADO físicamente.** "Esperado" es criterio de la prueba,
no una afirmación sobre lo ocurrido en este equipo.

| Caso / objetivo | Hipótesis que se debe observar | JVM/helper y UI a registrar |
|---|---|---|
| X de ventana principal de V | JavaFX oculta la ventana y limpia en segundo plano. Watchdog existente de 10 s si no termina | Al final V/H deben desaparecer y L debería salir. Anotar duración, si queda ventana/proceso oculto, códigos (70 implica el watchdog), y ambos recuentos de helper |
| Administrador de tareas: Finalizar tarea sobre ventana MACE | **No se presupone** si Windows manda cierre, termina V, L o el grupo; depende de selección/versión | Anotar pestaña/fila/grupo, PID de Detalles si lo pudiste identificar y si coincide con window_owner. Registrar L/V/H, salida y desaparición de UI; si solo se identifica grupo, objetivo exacto NO VERIFICADO |
| taskkill /F /PID V | Se solicita terminar V únicamente. Su muerte debería cerrar el job de MACE y eliminar H; L debería terminar al finalizar la JVM | V original debe morir; observar si aparece otra JVM, si L permanece, helper residual y UI desaparecida. No inferir H muerto solo porque taskkill devolvió 0 |
| taskkill /F /PID L, **sin /T** | Código de jpackage sugiere cierre de su job y muerte de V; se debe comprobar | Confirmar L muerto y observar si V/H realmente mueren o sobreviven. Si queda V viva, anotar si sigue UI/telemetría o queda oculta. No se mata V para esconder el resultado |
| taskkill /F /T /PID L | Windows solicita terminar L y sus descendientes | Observar L/V/H; se espera ninguno restante y ventana desaparecida. Registrar comando/código y cualquier superviviente, sin cleanup adicional automático |

Base documental: [taskkill](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/taskkill),
[Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects) y
[launcher de OpenJDK 22](https://raw.githubusercontent.com/openjdk/jdk/jdk-22%2B36/src/jdk.jpackage/windows/native/applauncher/WinLauncher.cpp).
Estas fuentes orientan expectativas, no sustituyen la ejecución física del producto
elevado ni determinan lo que hizo el botón concreto del Administrador de tareas.

## Orden y comandos: cierres

Desde la raíz, consola administradora. Ejecuta un comando por vez y completa
la ficha antes del siguiente. Cada caso tiene instancia propia. Un código 0
del script significa **captura completada**, no aprobación automática del caso.

```powershell
# 1. Cierre cooperativo: pulsa X cuando el script lo indique.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseX
# 2. Finalizar tarea en la fila de la ventana visible, accion manual.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case EndTaskWindow
# 3. /F sobre V; requiere CONFIRMO para el PID mostrado.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillJvm
# 4. /F sobre L separado; nunca /T en este caso.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillLauncher
# 5. /F /T sobre L; afecta al arbol de esta instancia.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillTree
```

Los casos 3–5 emiten y ejecutan exclusivamente `taskkill /PID <verificado> /F`
y añaden `/T` solo para KillTree. No ejecutan comandos por nombre. Espera la
captura de 45 s: compara el estado a los pocos segundos y después de 10–15 s.
Si faltó descubrir V/H, no cuentes "alive=0" como cierre demostrado. Si queda
un proceso oculto o helper residual, conserva el resultado y detén la secuencia;
no uses un kill global ni reinicies para convertirlo en éxito. Cierra con la
X si aún es posible y comunica los PIDs/resultados saneados antes de continuar.

## Helper muerto y cierre en calentamiento

```powershell
# 6. Mata SOLO H de la JVM capturada, mediante /PID /F, despues de confirmar.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillHelper -ObserveSeconds 90
# 7. Intenta X mientras la UI muestra Preparando lecturas.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseWarming -ObserveSeconds 30
```

Caso 6: L/V deben seguir vivos. H inicial termina y se espera otro H bajo el
mismo V; compara PIDs. CPU dependiente del helper debería pasar a no válido;
READ_FAILED=6 es un fallo transitorio, WARMING_UP=5 preparación. Si llega al
modo lento, HELPER_RETRYING=14 explica que el componente no responde y MACE
seguirá intentándolo, sin prometer un plazo. La recuperación puede tardar más
que la captura; si ves 14, puedes repetir la observación en una ejecución
planificada de hasta 360 s sin volver a matar el helper. El script actual no
mata dos veces en una misma ejecución. No confundir repetir el comando completo
con extender una captura: repetir el comando abre otro MACE y hace otra acción.
Si se necesita más observación, decide -ObserveSeconds 360 **antes** de iniciarlo.

Comprueba que UI responde y recupera el baseline disponible. NVML en NVIDIA e
IGCL en Intel son primarias independientes: no exijas que toda GPU pase a N/D.
Una Radeon dependiente de LHM sí puede perder lecturas. Un WARMING_UP aislado
puede quedar cubierto por la tolerancia visual de 1.500 ms; dos consecutivos
deben mostrar N/D. No fuerces estados para probarlo. No declares VALIDs físicos
solo por un nuevo PID. Si la transición no quedó muestreada, ese punto NO VERIFICADO.

Caso 7: el script no retrasa ni suspende el helper ni añade modo warming.
Anota si viste WARMING_UP antes de X y si quedó evidencia reciente de estado 5.
Si ya estaba Ready o no se capturó V/H, marca ese escenario NO VERIFICADO;
un cierre Ready no prueba cierre durante calentamiento. Observar terminación
y procesos restantes es independiente de haber logrado la precondición.

## Instancia única y elevación mixta

MACE.exe exige administrador. Una instancia realmente normal **no se crea
aceptando UAC del EXE**: se usa Java/JAR/DLL de la misma imagen con los pins de
su CFG mediante el script, sin editar manifiesto o launcher. El script normal
rechaza una consola administradora. Preview es simulación explícita sin DLL.

Mantén ambas consolas en la **misma cuenta/sesión** y confirma MISMA_CUENTA.
El script no registra SID ni nombres y no convierte una declaración de cuenta
en prueba de acceso entre usuarios. Espera OWNER_READY antes de iniciar la segunda.

```powershell
# 8A. Consola ADMIN, primer propietario elevado (observacion 180 s).
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role ElevatedOwner -ObserveSeconds 180
# Consola NORMAL, mientras sigue el anterior: perdedor nativo sin elevar.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondNormal -ObserveSeconds 30
```

Esperado: normal recibe «MACE ya está abierto o se está cerrando», sin DLL
nativa/helper propios; acepta solo su aviso. Elevado conserva su V/H y lectura.
No afirmar "sin DLL" si el perdedor murió antes de que se pudiera inspeccionar.
Al terminar ambas capturas, cierra manualmente al propietario y espera su salida.

```powershell
# 8B. Consola NORMAL, propietario real no elevado.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role NormalOwner -ObserveSeconds 180
# Consola ADMIN, mientras sigue el anterior: segundo EXE elevado.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondElevated -ObserveSeconds 30
```

Esperado: normal conserva propiedad; CPU temperatura/potencia no válidas con
NOT_ELEVATED=9 («Ejecuta MACE como administrador…»), uso CPU/RAM independientes.
Elevado debe recibir aviso y no crear su helper. No exijas temperatura válida
por el mero hecho de arrancar el segundo elevado. Cierra ambos manualmente al finalizar.

Para instancia única con dos elevados, repite 8A sustituyendo SecondNormal por
**SecondElevated en otra consola administradora**. Para Preview perdedor usa
SecondPreview en esa segunda consola. Para Preview propietario usa, desde
NORMAL, `-Role PreviewOwner -ObserveSeconds 180` y después SecondElevated desde
ADMIN. Preview no carga sensores; el elevado debe recibir aviso. Estas variantes
siguen NO VERIFICADO hasta sus resultados, no se deducen unas de otras.

## Coexistencia con LHM y suspensión

```powershell
# 9. Abre LHM de escritorio tu mismo; el arnes no lo identifica ni termina.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CoexistLhm -ObserveSeconds 180
# 10. Suspende y reanuda manualmente desde Windows; no hay comando de suspension.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case SuspendResume -ObserveSeconds 180
```

9A: LHM abierto → abrir MACE → comparar lecturas/errores → X de MACE → comprobar
que LHM continúa vivo/leyendo → cerrar LHM tú. 9B, ejecución nueva: MACE leyendo
→ abrir LHM → cerrar LHM tú → comprobar que MACE continúa → X de MACE. Anota
valores ausentes o errores, respuesta de cada UI y estado del servicio. No
esperes cifras idénticas por diferencias de sondeo. No se fuerza PawnIO.

10: con línea base reciente, suspende al menos 15 s y reanuda. Anota duración
aproximada y lo que ves inmediatamente, no solo cuando ya recuperó. No deben
presentarse cifras antiguas como válidas mientras no haya lectura nueva válida.
El log puede marcar un hueco; recent=0 no se toma como estado actual. Si la UI
ya recuperó antes de observarla, el intervalo inmediato queda NO VERIFICADO.
No se infiere ausencia de datos obsoletos solo porque el siguiente registro sea
válido. WARMING_UP visual tiene su gracia existente; el arnés no la amplía.

## Recuperación de PawnIO: parada normal y restauración

Antes cierra LHM y otras ejecuciones MACE manualmente. No hagas estos casos si
el servicio está ausente: eso corresponde a máquina virtual limpia/Fase 3.

```powershell
# 11A. MACE leyendo; parada normal y arranque.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-pawnio.ps1 -Scenario RunningToStoppedToRunning
# 11B. Otra ejecucion: parar con MACE cerrado, abrir MACE y arrancar PawnIO.
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-pawnio.ps1 -Scenario StoppedBeforeMace
```

Exige administrador y **ACEPTO_PAWNIO**. Comprueba baseline RUNNING y usa
ControlService(STOP) normal una sola vez. Si devuelve **1052 o cualquier error**,
aborta, registra STOP_REJECTED/NO_VERIFICADO y pasa a finally: no hay segundo
intento forzado, cambio de configuración, desinstalación ni reinicio del equipo.
Un STOP_RETURN state=0 con error significa estado de respuesta desconocido,
no STOPPED; el muestreador consulta el estado real aparte.

Si acepta, exige observar STOPPED. Si no se observa en 15 s, NO VERIFICADO y
finally. Al estar detenido, CPU debería mostrar PAWNIO_STOPPED=11 («PawnIO está
detenido…»), **no** reinicio pendiente/3010. Luego solicita START y observa al
menos 75 s por defecto; cubre el cooldown existente de 30 s activos sin alterarlo.
H debería conservar PID en una recuperación sana dentro de LHM; una caída/bloqueo
puede provocar relanzamiento por 4A y debe anotarse, no ocultarse.

En 11B, MACE o LHM pueden arrancar PawnIO por su cuenta. Si ya está RUNNING antes
del START solicitado, registra AUTO_RUNNING_BEFORE_REQUESTED_START y deja ese
escenario controlado NO VERIFICADO. La transición realmente observada puede
anotarse aparte, sin atribuirla al START del script. Radeon puede mostrar N/D
breve durante reapertura; registra duración y si la gracia de WARMING_UP cubre
solo un sondeo. NVML/IGCL siguen su evaluación independiente.

Un muestreador C# separado dentro de PowerShell registra aproximadamente a 1 Hz
servicio, PIDs y estados V2 mientras la llamada SCM síncrona espera. No es un
segundo helper ni una segunda instancia de telemetría. Los huecos o falta de
caja negra quedan registrados/NO VERIFICADO; no se inventan muestras.

**finally restaura RUNNING:** usa APIs SCM/.NET y logging directo a archivo,
sin depender de la salida de un pipeline cancelado. Si está STOPPED solicita
START; espera estados pendientes, verifica RUNNING y escribe
`RESTORE_END running=1`. Nunca instala servicio ausente ni fuerza parada.
Ante Ctrl+C, el finally debe ejecutarse; compruébalo en el archivo, porque la
consola puede dejar de mostrar salida. Fuente: [PowerShell finally y Ctrl+C](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_try_catch_finally?view=powershell-5.1).

11C, prueba de cancelación **solo si la parada normal se acepta**: ejecución
nueva de 11A, pulsa Ctrl+C tras STOPPED_OBSERVED durante la observación, y revisa
RESTORE_END running=1. No cierres la consola a la fuerza, no mates PowerShell y
no apagues el equipo: una terminación forzada o fallo de SO impide garantizar
finally. Si START se rechaza o no se confirma RUNNING en 30 s, se registra
RESTORE_RUNNING_NOT_CONFIRMED/NO_VERIFICADO; no se declara restaurado. En esa
situación detén los demás casos y consulta manualmente `sc.exe query PawnIO`;
puedes solicitar `sc.exe start PawnIO` sin forzar, conservando el resultado.

La restauración y Ctrl+C están **NO VERIFICADO físicamente**, al igual que los
scripts. La documentación de finally no prueba que el driver acepte START ni
que un disco/SCM bloqueado responda. No se modifican estos componentes para
simular éxito. El ERROR 1052 ya informado permanece como parada no lograda.
Si nunca acepta STOP, la prueba importante «PawnIO ausente → instalado» se
realizará en una **VM limpia en Fase 3**, con aprobación de instalación. Nada de
esa fase se ejecuta aquí.

## Ficha de resultados: una por caso

Completa sin rutas de perfil, usuarios, hostname, SID o hashes completos:

```text
Caso / variante:
Fecha/hora aproximada:
Consola: administrador / normal (solo donde se indica)
Precondicion lograda: SI / NO / NO VERIFICADO
L / V / H iniciales (PIDs):
Objetivo real y accion; Task Manager: pestaña/fila/grupo/PID si identificado:
Codigo del comando / root_exit / jvm_exit:
A los 3 s, 10–15 s y final: L vivo / V viva / H vivos / image_helper_count:
Ventana visible, oculta o cerrada; respuesta de la UI:
Transicion V2 reciente CPU/GPU y mensajes observados; si falto una muestra:
Caso helper: PID antiguo/nuevo y recuperacion observada:
Caso PawnIO: STOP_RETURN, STOPPED_OBSERVED, START_RETURN, RESTORE_END:
Caso suspension/LHM: orden y observacion visual inmediata:
Resultado: VERIFICADO POR EL USUARIO / FALLO OBSERVADO / NO VERIFICADO
Archivo relativo result.txt:
```

0 del script = captura acabada, no equivale a PASS global. 2 = precondición,
cancelación o escenario/restauración no completado; Ctrl+C puede dar un código
del host distinto, revisa finally en el archivo. Si el registro falta por un
error de E/S, no fabriques un resultado: NO VERIFICADO. No repitas escenarios
destructivos para completar un log sin aclarar los supervivientes.

## Archivos, integridad y parada

| Archivo | Función |
|---|---|
| packaging/tests/test-phase2-manual.ps1 | Cierres, kills por PID, helper, calentamiento, coexistencia y suspensión manual |
| packaging/tests/test-phase2-instance.ps1 | Propietarios/perdedores elevados, Java normal y Preview; dos consolas del usuario |
| packaging/tests/test-phase2-pawnio.ps1 | Confirmación, STOP normal, aborto y finally de restauración |
| packaging/tests/phase2-manual-common.ps1 | Arranque controlado, reuse del observador, captura y comprobación previa de identidad |
| packaging/tests/phase2-manual-support.cs | Soporte QA de servicio, handles, logging saneado y lectura pasiva de V2 de la caja negra |
| helper/FASE2-PASO5-PRUEBAS.md | Este plan; todos los casos nuevos NO VERIFICADO |

No se cambia producto o imagen, ABI 1.5, protocolo 2, helper 1.1.1, política 4A,
manifiestos/launchers, NVML/IGCL/GetSystemTimes/DXGI/RAM, firma/licencia ni señal
3010. No se ejecuta instalador o instalación/desinstalación PawnIO, HVCI, ni se
fusiona a principal. Los scripts de QA están fuera de la imagen; los datos de
caja negra/prefs que genere el propio producto al ejecutarlo siguen su política
existente, incluido el riesgo de cierre forzado descrito en
[el inventario de escrituras](FASE2-INVENTARIO-ESCRITURAS.md).

**Fin del 5.2: entregar y detenerse. Esperar resultados del usuario antes de 5.3.**
No se genera FASE2-CIERRE.md ni se implementa Fase 3 en esta entrega.
