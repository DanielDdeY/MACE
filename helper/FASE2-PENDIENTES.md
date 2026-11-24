# Fase 2 — verificaciones diferidas y puertas de avance

Fecha: 2026-11-18. Complemento de [FASE2-CIERRE.md](FASE2-CIERRE.md). Todos los
casos incompletos conservan **NO VERIFICADO** hasta evidencia con su alcance.
Estos comandos son entregables para el usuario; no se ejecutan como pruebas
manuales por el agente en el 5.3. No autorizan instalación ni Fase 3.

Ejecutar desde la raíz del repositorio, Windows PowerShell **5.1 x64**:
`powershell.exe`, no sustituir por pwsh (PS7 difiere al ordenar el manifiesto).
Abrir manualmente administrador solo donde se indica. Cerrar aplicaciones
propias manualmente entre escenarios; no taskkill /IM ni selección global de
JVM. Los scripts de 5.2 descubren L/V/H por árbol/identidad y piden confirmación
antes de terminar el objetivo. No reutilizar PIDs de ejecuciones anteriores.

Los resultados saneados de 5.2 van a native/build/fase2-paso5.2. Código 0 de
captura no equivale a prueba aprobada. Anotar ficha del plan, tokens de consola,
caso, L/V/H antes/después, estados recientes, mensaje visual y resultado real;
sin perfil, usuario, hostname, SID ni hashes completos. No compartir logs crudos
de compilación/Surefire. Los tres TXT excluidos localmente siguen fuera de Git.

Leyenda de bloqueo propuesto, pendiente de aprobación del usuario:
**F3**: empezar o ejecutar una parte de Fase 3; **M**: fusionar; **D**: distribuir.
Una deuda aceptada no es una prueba aprobada. La tabla completa de puertas está
en el cierre; aquí se concreta cómo saldar cada deuda.

## P01. CloseWarming: precondición no reproducible de forma garantizada

Estado: **NO VERIFICADO**, el usuario no pudo observar la precondición. Requiere
imagen elevada, arranque nuevo y lograr observar estado 5 **antes de cerrar**.
No existe en el producto congelado un selector soportado que garantice una
ventana WARMING_UP larga. El script no ralentiza ni suspende el helper; no se
propone alterar binarios, trust, drivers, prioridades o políticas para lograrla.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseWarming -ObserveSeconds 30
```

Esperado solo si se logra: X en preparación, cierre acotado y ausencia posterior
de V/H. Anotar instante/estado visual 5, registro reciente cercano y L/V/H
capturados. Si ya estaba listo o no se capturó identidad, mantener NO VERIFICADO.
Los fixtures automatizados cubren el orden/cierre simulado, **no esta precondición
física**. Propuesta: **M/D requieren aceptación explícita de esta limitación o
prueba lograda**; no bloquea diseñar F3. No repetir indefinidamente para fingir cobertura.

## P02. KillHelper: PID nuevo, recuperación y mensaje

Ya hay evidencia: UI N/D → temperatura declarada por usuario y dos logs con H
15740 → 19172 / 5144 → 15768, V estable y recuperación a estados 0. **No está
pendiente demostrar que esos logs contienen PID distinto**; queda texto exacto
visual y, si se desea, correlación en una nueva ficha completa del usuario.

Requiere consola administradora, imagen nueva sin otras MACE y baseline útil.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillHelper -ObserveSeconds 360
```

Solo se termina el H atribuido tras CONFIRMO; observar V vivo, N/D CPU, nuevo H
bajo el mismo V y recuperación de temperatura/estados recientes. Anotar PIDs
antiguo/nuevo, secuencia temporal y mensaje sin deducirlo del código; posible
«Preparando las lecturas de los sensores» o indisponibilidad transitoria.
Si entra en modo lento: «El componente de sensores no responde. MACE seguirá
intentándolo», sin plazo prometido. La GPU primaria independiente puede seguir
válida. No matar H otra vez para prolongar la captura. **No bloquea F3/M por el
cambio de PID ya respaldado; texto visual pendiente de D si se promete ese flujo.**

## P03. Suspensión: observación inmediata de UI

Requiere baseline reciente y capacidad de suspender/reanudar, sin cambiar HVCI.
El usuario ya ejecutó suspensión y vio datos después; el log muestra antigüedad,
invalidación y recuperación. Eso no acredita la pantalla inmediata.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case SuspendResume -ObserveSeconds 180
```

Suspender manualmente desde Windows al menos 15 s, reanudar y observar enseguida.
Esperado: ninguna cifra anterior presentada como válida mientras falte una
lectura nueva válida, UI responde y recupera. Anotar duración aproximada, primer
estado visible, si hubo N/D/preparación, lectura posterior y log correspondiente.
Una grabación local enfocada solo en tarjetas, si el usuario la desea, permite
revisar el primer fotograma sin exponer escritorio/identidad; no es obligatoria.
Si ya recuperó antes de mirar, intervalo inmediato NO VERIFICADO. **M/D: comprobar
o aceptar expresamente la deuda de frescura visual; no bloquea diseño F3.**

## P04. Coexistencia con LibreHardwareMonitor de escritorio

Estado: **NO VERIFICADO**; el usuario no lo tiene instalado. Requiere obtener
la aplicación de escritorio oficial, runtime requerido disponible y PawnIO ya
presente, sin ejecutar su instalador para esta prueba. No descargar ni ejecutar
nada automáticamente como parte de este entregable.

Fuente comprobada el 2026-11-18: [release oficial v0.9.6](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/releases/tag/v0.9.6),
[assets y digest publicados](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/releases/expanded_assets/v0.9.6).
Elegir **LibreHardwareMonitor.zip** (variante .NET Framework; no source code ni
nightly), guardar manualmente como native/build/lhm-desktop-0.9.6.zip.
El README oficial indica Framework 4.7.2 / alternativa .NET 10; si falta el
runtime, detener y decidir su preparación aparte, no instalar a ciegas.

```powershell
# Solo despues de descargar manualmente ese asset oficial exacto:
$archive='.\native\build\lhm-desktop-0.9.6.zip'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne '086D9F1B5A99E643EDC2CFAAAC16051685B551E4C5AC0B32A57C58C0E529C001') { throw 'LHM_ARCHIVE_HASH_MISMATCH' }
Expand-Archive -LiteralPath $archive -DestinationPath .\native\build\lhm-desktop-0.9.6
& .\native\build\lhm-desktop-0.9.6\LibreHardwareMonitor.exe
```

Comparar además con el digest de la misma página oficial, no con un hash de
un mirror. El digest prueba coincidencia con esa publicación, no es firma
Authenticode. No sustituir el ZIP por otro con un nombre parecido ni bajar
versiones antiguas para sortear un aviso del antivirus.

El [código oficial de arranque v0.9.6](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/v0.9.6/LibreHardwareMonitor/UI/MainForm.cs)
muestra una oferta de instalar/actualizar PawnIO si falta/es antiguo. **Cancelar
esa oferta y abortar el caso**, no aceptar ni ejecutar PawnIO_setup. No activar
autoinicio, logging, servidor web, controles de ventilador ni cambios de hardware.
Abrir/cerrar desde su UI por el usuario, no mediante scripts que busquen nombres.
Usar **File → Exit** o **Exit** del icono de bandeja; X puede minimizar según su
configuración. Confirmar desaparición de su UI/bandeja sin matar por nombre.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CoexistLhm -ObserveSeconds 180
```

9A: abrir LHM → ejecutar el comando → comparar → X de MACE → verificar LHM
continúa leyendo → cerrar LHM desde Exit. 9B, nueva ejecución: MACE primero →
abrir LHM → Exit de LHM → comprobar MACE → X de MACE. Anotar versiones, orden,
errores/lecturas ausentes, respuesta/cierre y que el otro sigue funcionando;
no exigir cifras idénticas por sondeos distintos. **D bloqueado si se anuncia
coexistencia; M requiere resultado o exclusión/deuda explícita. No bloquea F3.**

## P05. Instancia única: 8A incompleto en logs y variantes restantes

Usuario confirma **8A elevado primero**, con aviso correcto. No hay log
SecondNormal; no ampliar esa observación a demostrar ausencia de DLL/helper del
perdedor. **8B no se declara aprobada** por los archivos compatibles con ella.
Dos elevados y variantes Preview fueron expresamente no ejecutadas.

Requiere misma cuenta/sesión, dos consolas abiertas manualmente (ADMIN/NORMAL
según cada línea), ninguna otra instancia y esperar OWNER_READY antes del segundo.
Cada rol genera su propio log. No se baja/eleva token automáticamente.

```powershell
# 8A: ADMIN propietario; NORMAL segundo (dos consolas en paralelo).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role ElevatedOwner -ObserveSeconds 180
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondNormal -ObserveSeconds 30
# Cerrar propietario manualmente antes del siguiente par.
# 8B: NORMAL propietario; ADMIN segundo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role NormalOwner -ObserveSeconds 180
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondElevated -ObserveSeconds 30
# Dos elevados: ADMIN propietario / otra ADMIN segundo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role ElevatedOwner -ObserveSeconds 180
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondElevated -ObserveSeconds 30
# Preview perdedor: ADMIN propietario / otra ADMIN segundo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role ElevatedOwner -ObserveSeconds 180
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondPreview -ObserveSeconds 30
# Preview propietario: NORMAL primero / ADMIN segundo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role PreviewOwner -ObserveSeconds 180
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-instance.ps1 -Role SecondElevated -ObserveSeconds 30
```

Cada par es una prueba separada, no ejecutar todo el bloque secuencialmente en
una sola consola. Esperado: propietario conserva su reserva, perdedor muestra
«MACE ya está abierto o se está cerrando», sin DLL/helper propios. Dejar aviso
visible al menos 5 s para inspección; aceptar solo el aviso del perdedor. Normal
real usa Java de la imagen con pins; Preview es simulación sin DLL. Registrar
qué ventana era propietaria, tokens, PIDs y native_module_loaded del perdedor.
Si se perdió su inspección, ese requisito NO VERIFICADO. **M/D: completar los
permisos/roles de un proceso elevado; no bloquea diseño F3.**

## P06. PawnIO STOP real y restauración

11A ya **rechazó STOP con 1052**; RUNNING final no prueba una restauración desde
detenido. No repetir en este equipo para forzar aceptación. Requiere otro
entorno donde la **parada normal** sea admitida, administrador, MACE/LHM previos
cerrados y aprobación manual ACEPTO_PAWNIO del script.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-pawnio.ps1 -Scenario RunningToStoppedToRunning
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-pawnio.ps1 -Scenario StoppedBeforeMace
```

Casos separados. Si 1052 u otro rechazo: abortar, NO VERIFICADO, finally confirma
RUNNING, no forzar ni repetir STOP. Si acepta: observar STOPPED, CPU no válida
con estado 11 (no 3010), START y recuperación real. Anotar estados por segundo,
H, CPU/GPU, mensaje, cambio de estado y RESTORE_END running=1. En inverso, MACE
o LHM pueden arrancarlo antes del script; registrar eso y no atribuirlo al START.
11C: solo donde STOP se acepta, nueva ejecución del primer comando y Ctrl+C
después de STOPPED_OBSERVED; verificar finalmente RUNNING en archivo. No matar
PowerShell/cerrar su consola a la fuerza. Si no se confirma, detener pruebas y
consultar `sc.exe query PawnIO`; `sc.exe start PawnIO` es solicitud normal manual,
no mecanismo para ocultar fallo. El controlador debe aceptar el arranque.
**No bloquea F3 por el 1052; M/D: aceptar limitación de STOP o verificarlo donde
se soporte. Nunca se exige forzar el servicio.**

## P07. VM limpia: ausente → instalado y 3010 real, exclusivamente Fase 3

Requisito previo: aprobación de Fase 3, snapshot Windows limpio preparado por
el usuario, tecnología del instalador elegida, revisión legal, hash/firma/origen
oficial de PawnIO y argumentos silenciosos confirmados, esquema/ACL/consumo de
señal volátil revisados. **No existe aún un comando de instalador MACE validado
para estos casos**. No inventar switches ni ejecutar directamente PawnIO ahora.

Comandos disponibles **solo después de aprobar/preparar ese entorno** para
observar el baseline y la app-image, sin instalar:

```powershell
sc.exe query PawnIO
# Desde la raiz de una copia verificada del proyecto con la misma imagen:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseX
```

Antes, SCM 1060 debe corresponder a ausencia; CPU t/p no válidas con motivo 10,
no confundir con detenido 11. La prueba de transición importante consiste en
ejecutar el **futuro comando aprobado del instalador**, conservar código real
(0/3010/error), abrir/observar MACE y comprobar lectura tras instalación y/o
reinicio cuando proceda. Comando definitivo y script de señal quedan pendientes
de diseño; se entregarán antes de ejecutarlo, no se improvisan aquí.
Para 3010: reproducir resultado real del instalador, mensaje 12 exacto, otras
métricas disponibles, caducidad después de reinicio real y rechazo de señal
inválida/no confiable. No simular un DWORD para llamarlo prueba real; Preview
ya cubre el mensaje. Anotar snapshot inicial/final por alias sin identidad,
versiones, códigos y lecturas, sin claves/rutas personales en logs compartidos.
**F3: bloquea las pruebas de instalación hasta esos prerrequisitos; D: bloquea
release. No exige que Fase 2 haya instalado nada.**

## P08. Intel CPU/GPU, NVIDIA y HVCI con el conjunto actual

Todo **NO VERIFICADO** con la imagen congelada. Requiere equipos del usuario,
drivers oficiales ya operativos, PawnIO admitido y token adecuado; no activar
HVCI ni instalar drivers para esta entrega. Para HVCI, usar equipo donde el
usuario confirme Integridad de memoria ya activa y reiniciada; no inferirla de
un test de sensores. No hay Test-WithHvci.ps1 vigente en este checkout: no dar
una ruta inexistente ni reutilizar un resultado de componentes anteriores.

```powershell
# ADMIN: imagen actual, confirmar seleccion real de componentes.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image-environment.ps1
# Cerrar MACE manualmente; luego baseline/observacion y cierre de imagen.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseX
# Opcional si hay build dev/publicacion verificada: CPU fisica, no imagen Production.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4b\dev -RealHelper -RequireCpu'
```

Esperado: CPU con métricas disponibles válidas donde exista soporte; Intel GPU
con IGCL primaria cuando corresponda, NVIDIA con NVML primaria, sin exigir
sensores inexistentes. El script de entorno solo demuestra selección de archivos.
Revisar proveedor/estados de cada GPU en log y tarjetas; no deducir cobertura
NVIDIA por mera presencia de una tarjeta ni presencia de helper. Anotar modelo
general/versión driver/Windows, versiones MACE, HVCI declarado/observado por el
usuario, token, proveedor, estados, valores visibles y errores, sin hostname/SID.
Para HVCI repetir pruebas bajo esa configuración y separar lo real de Preview.
**D bloquea compatibilidad anunciada Intel/NVIDIA/HVCI; M puede aceptar matriz
limitada explícita. No bloquea diseño F3.**

## P09. Arranque posterior a cierres forzados y archivos persistidos

Usuario ya confirmó arranque normal tras los kills; no se repite para negar
esa evidencia. Falta ficha individual y datos de persistencia por cada tipo,
además de crash/halt real del producto (no autorizado ni reproducible por
comando soportado). Requiere respaldo local que el usuario decida de sus datos
y cerrar instancias previas; nunca borrar historial para convertirlo en PASS.

```powershell
# Elegir un caso; antes de otro, cerrar manualmente la instancia nueva.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case KillJvm
# Abrir despues desde otra captura y observar dashboard/historial/prefs; cerrar con X.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case CloseX
# En ejecuciones separadas repetir el par anterior con -Case KillLauncher y KillTree.
```

Esperado: nuevo dashboard abre y métricas disponibles recuperan; historial puede
mostrar sesión incompleta y solo prefijo recuperable, no recuperación total.
Anotar caso anterior, nuevo arranque, UI, preferencias/posición HUD e historial
incompleto, y excepciones. No exigir conservación de la última cola ni cleanClose
después de /F. No provocar halt/disco lleno/energía por cuenta del agente.
**M: aceptar riesgo de escrituras o aprobar corrección separada; D: documentar
la pérdida posible. Arranque normal general ya verificado no queda pendiente.**

## P10. Regresión completa en administrador

NO VERIFICADO salvo Development 76/76 + CPU real y los 13 Java aislados ya
declarados previamente por el usuario. Requiere herramientas/builds existentes,
consola administradora abierta por el usuario, sin otras ejecuciones y solo
cierres propios de los fixtures previamente autorizados. No aporta pruebas
físicas de otros equipos. **M/D: completar matriz; no bloquea diseño F3.**

Desde la raíz, un comando por vez; registrar `$LASTEXITCODE` tras cada uno.
Las PowerShell hijas limpian MACE_* solo en su entorno; no cambian User/Machine.
No se necesita republicar helper ni reconstruir imagen para repetir CTest.

```powershell
# 105 checks, sin argumento live.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe; exit $LASTEXITCODE'
# 76 y 77: usar el CTest de la toolchain MSVC, builds preparados del 4B.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; . .\native\scripts\native-common.ps1; $tc=Get-MaceToolchain; & $tc.CTest --test-dir .\native\build\fase2-paso4b\dev -C Release --output-on-failure -j 4; exit $LASTEXITCODE'
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; . .\native\scripts\native-common.ps1; $tc=Get-MaceToolchain; & $tc.CTest --test-dir .\native\build\fase2-paso4b\prod -C Release --output-on-failure -j 4; exit $LASTEXITCODE'
# Java 347 + 1 omitida; payload 8 + ambos rechazos Development. Exige PS5.1.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-java-regression.ps1 -Payload
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
# Reporte ignorado: no reescribir el TSV versionado.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command 'New-Item -ItemType Directory -Force .\native\build\fase2-paso5.3-admin | Out-Null; & .\native\scripts\audit-retired-providers.ps1 -ReportPath .\native\build\fase2-paso5.3-admin\referencias.tsv'
```

Esperado: código 0 de cada comando, cifras anteriores, protegidos PASS y auditoría
181 aceptadas/0 inesperadas. Si falla, guardar el fallo; no borrar pins confiables,
cambiar política ni ampliar excepciones para aprobar. No confundir resultado
negativo deliberado de un fixture con batería fallida. Lo omitido de Java es
solo integración opcional de DLL antigua. No ejecutar test-image.ps1 sobre la
imagen congelada: crea archivos temporales; usar una copia de QA si se aprueba
repetir sus negativos. La integridad de la imagen se comprueba por lectura.

## P11. EndTaskWindow: captura del cierre y objetivo

El usuario informó sin anomalías y PID 7264; log de esa captura acaba con L/V/H
vivos. No se invalida su observación ni se afirma fallo del producto: falta el
tramo que demuestre desaparición. Requiere administrador, administrador de
tareas abierto por el usuario y escoger la ventana/grupo visible, anotando qué fila.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-phase2-manual.ps1 -Case EndTaskWindow -ObserveSeconds 120
```

Al aviso, pulsar Finalizar tarea en Procesos sobre MACE y dejar continuar captura.
Anotar PID/fila/grupo y si corresponde a L o V/window_owner; no sustituir por
Detalles o /F sin anotarlo. Esperado: UI desaparece; registrar qué muere y cuándo,
si hay JVM oculta o helper restante. Si acción queda fuera de captura, NO
VERIFICADO. **M/D: resolver garantía de cierre por este gesto o limitarla
expresamente; no bloquea diseñar F3.**

## P12. Equipo limpio, instalación/actualización/desinstalación

Requiere VM/snapshot y aprobación de Fase 3 como P07. No existe MSI/EXE de
instalación MACE validado ni switches definitivos; **comando pendiente del diseño
aprobado**, no implementado ahora. App-image no sustituye el instalador.
Con el futuro paquete verificar UI/silencioso, Windows soportado, ausencia de
SDKs locales, permisos/destinos de datos, arranque y actualización/reparación;
desinstalar MACE conservando PawnIO y su uso por terceros según política aprobada.
Anotar códigos, reinicios requeridos y estado de archivos/servicio, sin datos
locales compartidos. **F3 bloquea ejecutar instalador hasta preparar VM/comando;
D bloqueado hasta validación. No se exige hacerlo en el host actual.**

## P13. Firma, licencia, persistencia y matices operativos

Pendientes de decisión, no pruebas ya aprobadas. Requiere elegir firma/custodia,
licencia MACE, revisión legal de terceros y aceptar o corregir en trabajo
separado los riesgos del inventario de escrituras. El cierre lista todos los
documentos a matizar; **no se editan sin aprobación**. No hay comando que elija
una licencia, demuestre legalidad o convierta hashes en firma de publicador.

Cuando exista una release firmada aprobada, inspección de solo lectura:

```powershell
Get-AuthenticodeSignature -LiteralPath .\packaging\dist\MACE\MACE.exe | Select-Object Status
Get-AuthenticodeSignature -LiteralPath .\packaging\dist\MACE\app\mace_native.dll | Select-Object Status
Get-AuthenticodeSignature -LiteralPath .\packaging\dist\MACE\app\lhm\Mace.Lhm.Helper.exe | Select-Object Status
```

La imagen actual no firmada no puede cumplir Valid para archivos propios;
no ejecutar un hook para cambiarla. Firmar cambia bytes/pins y exige una
construcción futura coherente. Anotar estrategia, cadena/timestamp y revisión
de avisos/fuentes, no claves privadas ni identidad local en logs públicos.
**M: decidir riesgos documentales/persistencia; D: licencia/legal y firma o
excepción expresa resueltas. F3 puede diseñarse, pero decisiones afectan su paquete.**

**Parada:** este registro no autoriza Fase 3 ni instala nada. Esperar aprobación
del 5.3 y decisiones/prerrequisitos antes de ejecutar las pruebas reservadas.
