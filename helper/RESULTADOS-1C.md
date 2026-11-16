# Entrega 1C — potencia AMD y puente Java

Se releyeron RESULTADOS-1B.md, PROTOCOL.md y prompt_mace_fase1.md. El usuario aprobó 1B y confirmó que ejecutó como administrador test-lhm1b.ps1 -RealHelper -RequireCpu con éxito. Esa prueba elevada queda CONFIRMADA POR EL USUARIO; no se inventa una medición elevada independiente del agente. El alcance termina en 1C.

No se eliminaron proveedores antiguos ni se tocaron empaquetado/instalador. GetSystemTimes, NVML principal e IGCL principal siguen como en 1B. Firma y licencia siguen pendientes; se conservan los ganchos de firma opcional y hash. RESULTADOS-1B.md ya tenía modificaciones del usuario al comenzar y se conservaron.

## Confirmación del usuario y cierre documental — 15 de noviembre de 2026

**CONFIRMADO POR EL USUARIO EN LA UI:** en la confirmación recibida el 15/11/2026 (America/Lima), el usuario indica que la CPU ya muestra **temperatura y potencia en la interfaz de MACE**, ejecutada desde una consola de administrador. Es una observación del usuario, no una inspección ni una medición propia del agente. No se aportaron cifras, captura, hora exacta de la prueba, hashes de esa ejecución; no se deducen de esta confirmación.

La ejecución elevada de test-lhm1b.ps1 -RealHelper -RequireCpu con éxito ya había sido confirmada por el usuario. Ambas confirmaciones satisfacen el caso local de CPU AMD elevada; no prueban CPU Intel, otras GPU, la Micro-HUD completa.

Este cierre solo corrige documentación. El alcance real de potencia AMD, las condiciones exactas de AmdPowerPolicy y la tabla de requisitos previos a 1D están en [COBERTURA-AMD-POTENCIA.md](COBERTURA-AMD-POTENCIA.md). No se amplió la política ni se eliminó ningún proveedor.

## 1C-0: evidencia de potencia AMD

El paquete NuGet local de LHM 0.9.6 identifica el commit 3d331e3370efb858411f19511373eff65a218701. En ese código, GPU Package es el sensor de potencia de índice 3 y puede recibir rutas distintas: no equivale universalmente a potencia de placa. La ruta PMLog distingue ASIC y board; el reporte público identifica su disponibilidad y sus valores. Véase [AmdGpu.cs fijado al commit del paquete](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3370efb858411f19511373eff65a218701/LibreHardwareMonitorLib/Hardware/Gpu/AmdGpu.cs#L432).

AMD define ASIC_POWER y BOARD_POWER como identificadores distintos, 23 y 73. Se mapea el valor explícito ASIC a GpuChip, no a GpuBoard. Fuente primaria: [definiciones oficiales de ADL](https://github.com/GPUOpen-LibrariesAndSDKs/display-library/blob/master/include/adl_defines.h). Esto identifica la fuente declarada por el controlador; no es una calibración eléctrica externa ni consumo de toda la tarjeta/equipo.

Política implementada en AmdPowerPolicy:

- Solo AMD PCI vendor 1002/device 7340, LHM 0.9.6.0 y un sensor Power llamado GPU Package, único e índice 3. Subsystem y revision no se restringen en AmdPowerPolicy; su identidad inequívoca se comprueba por separado.
- Cada sondeo exige en la sección textual Overdrive Caps las líneas Status: ADL_OK y Version: 8, y en el reporte ASIC_POWER soportado y BOARD_POWER no soportado. Version: 8 es una comprobación literal de capacidades, no una prueba del éxito de cada consulta individual de potencia.
- Se usa el número explícito y único Sensor[ADL_PMLOG_ASIC_POWER].Value del reporte público de LHM, finito y entre 0 y 2.000 W. No se reetiqueta el número ambiguo de GPU Package ni un fallback OD6.
- Reporte incompleto, duplicado, fuera de rango, otro modelo/versión/sensor o disponibilidad ambigua: la cifra original permanece UnknownPower y la DLL entrega UNSUPPORTED/0, scope UNKNOWN.

Esta elección evita reflexión, acceso a miembros privados de LHM y un proveedor ADL independiente. GetReport se ejecuta dentro del helper, nunca en CP_Poll ni en JavaFX. Su formato textual es una dependencia deliberada de la versión fijada; cambios inesperados dejan N/D.

El filtro de device ID es una limitación explícita de cobertura: una Radeon con otro ID no pasa a GpuChip aunque publique un reporte equivalente. La presencia de BOARD_POWER también se rechaza por una decisión conservadora; no se ha demostrado que impida interpretar un valor ASIC identificado por separado. La tabla completa, las omisiones del parser y la diferencia entre casos permitidos por código y hardware probado están en el informe de cobertura.

Se reutiliza Meaning.GpuChip=5 de LHM1 y MACE_POWER_SCOPE_GPU_CHIP=4 de V2. La DLL ya validaba y traducía ese significado; se añadió el test end-to-end amd-chip. No se agregó ningún campo ni valor nuevo al protocolo, ni cambió su versión.

Evidencia física local: AMD Radeon RX 5500 XT, PCI 1002:7340 / subsystem 38221462 / revision C5. El diagnóstico mostró GPU Package /gpu-amd/0/power/3, Overdrive 8, ASIC_POWER soportado y BOARD_POWER ausente. Con el helper definitivo y la DLL se observaron 5–6 W, state=0 (VALID), scope=4 (GPU_CHIP), provider=8. No se presenta como validación física de otros modelos.

Evidencias regenerables:

- helper/artifacts/evidence-1c/amd-power-report.txt: reporte de la GPU.
- helper/artifacts/evidence-1c/AmdGpu.cs: fuente oficial descargada para inspección, fuera de los archivos distribuidos.
- native/build/vs2026-lhm1b/lhm-live-20261114-154807.txt: ocho segundos con la DLL y el helper definitivo.
- app/target/fase1c-tests.txt: resultados Java y registros de integración FFM.

## 1C-1: puente Java y estados

SensorProvider.LHM se añade al final, ordinal/código 8. Los códigos 0..7 conservan sus valores. NativeCodes traduce ambos sentidos y mantiene NONE para proveedor desconocido. CPU/GPU V2 siguen midiendo 56/104 bytes; inventario 296 y TelemetryData 28. NativeLayouts, tipos C y offsets no se modificaron.

NativeFunctions solo conserva telemetryInitialized=true si CP_InitializeTelemetry devuelve OK. BUSY no adquiere propiedad del cierre ni se convierte en éxito en la siguiente llamada. tryLock evita poner la inicialización Java en cola detrás de un cierre nativo lento.

TelemetryInitialization implementa el reintento dirigido por el bucle de monitorización existente:

| Condición | Acción |
|---|---|
| Primer intento OK | Sondeos normales; no se vuelve a inicializar |
| BUSY | WARMING_UP, sin excepción de inicio ni fallback a datos simulados |
| Reintento | Un intento por llamada de sondeo, separación mínima de 250 ms, sin sleep |
| Presupuesto | Como máximo cinco intentos en una ventana de cinco segundos |
| Presupuesto agotado | NOT_INITIALIZED y cinco segundos de pausa sin llamar a Initialize |
| Termina la pausa | Nueva ráfaga acotada desde el sondeo de fondo; permite recuperarse sin reiniciar la app |
| Código distinto de OK/BUSY | Error explícito; no se oculta como BUSY |

A 1 Hz normalmente se intenta al arrancar y en los cuatro ciclos siguientes. Los reintentos nunca se ejecutan como espera activa, sleep ni tarea que bloquee JavaFX. No se crea un planificador adicional. Mientras falta confirmar la inicialización no se sondea CPU/GPU; sí se puede leer RAM Windows. La pausa evita reintentos ilimitados a alta frecuencia sin convertir una contención transitoria en fallo permanente.

## Tolerancia de presentación

Límite principal: **un solo sondeo WARMING_UP consecutivo por métrica**, y solo si el último VALID se recibió hace como máximo **1.500 ms** al evaluar ese sondeo. Un segundo WARMING_UP muestra N/D aunque llegue inmediatamente. A 1 Hz admite el siguiente ciclo normal y 500 ms de variación, pero no dos pérdidas. No se reinicia la edad al retener y no se recupera un dato invalidado por un fallo explícito.

La decisión se toma con System.nanoTime en cada nueva secuencia de hardware. Refrescos que solo actualizan procesos no consumen ni renuevan esa tolerancia. El límite por número de sondeos es independiente de su velocidad; los 1.500 ms son una condición de elegibilidad, no un temporizador autónomo si deja de llegar todo el flujo.

TelemetryPresentation vive exclusivamente en presentación. MainDashboardViewModel entrega la misma copia visual a dashboard y HudPane. Alertas, caja negra, registros V2 y resultados del puente FFM conservan el snapshot crudo y no se promueve allí WARMING_UP a VALID. La caché visual separa métricas, proveedor, identidad CPU/GPU y limpia adaptadores desaparecidos; conserva también el ámbito de una potencia retenida.

| Estado V2 | Dashboard | Micro-HUD |
|---|---|---|
| VALID | Cifra y unidad, incluidos ceros legítimos | Cifra y unidad |
| WARMING_UP tras VALID, primer caso elegible | Última cifra durante ese sondeo | La misma cifra |
| WARMING_UP inicial, repetido o con dato anterior demasiado antiguo | N/D: Calentando | N/D |
| READ_FAILED | N/D: Error de lectura, inmediato | N/D inmediato |
| NO_PROVIDER | N/D: Sin proveedor, inmediato | N/D inmediato |
| UNSUPPORTED | N/D: No soportado, inmediato | N/D inmediato |
| NO_DEVICE | N/D: Sin dispositivo; si deja de enumerarse se retira la GPU | N/D o GPU desconectada |
| ACCESS_DENIED | N/D: Acceso denegado | N/D |
| DEVICE_INACTIVE | N/D: Inactivo y estado de GPU inactiva | N/D |
| NOT_INITIALIZED | N/D: No inicializado | N/D |

El texto concreto del motivo usa MetricState.label(); N/D nunca se sustituye por el cero marcador de la DLL.

GPU ambigua: los modelos/tarjetas muestran N/D en temperatura, potencia, uso, RPM, porcentaje y ambas VRAM. HudPane muestra N/D en las cuatro métricas GPU que ofrece actualmente (temperatura, potencia, uso y VRAM usada); las otras tres se comprueban en el modelo de tarjeta GPU. UnknownPower deja N/D solo en potencia; el resto de métricas válidas siguen visibles. El test de controles JavaFX verifica ambos casos en el HUD.

## Cierre

El orden de MaceApplication.stop es vista/HUD, monitor, viewmodel, publisher y adaptadores nativos. Un error en un cierre no omite los demás: se acumula y se informa después. InfrastructureAdapters.close apaga NativeFunctions; esa instancia llama a CP_ShutdownTelemetry una sola vez tras una inicialización correcta y nunca después de haber recibido solo BUSY. La DLL se carga con Arena.global y permanece cargada hasta salir del proceso, de modo que el shutdown precede a su descarga.

No hay tarea de reintento independiente que pueda inicializar después del cierre. Las pruebas FFM con upcalls comprueban BUSY/OK, cierre único, ausencia de shutdown si no se adquirió el runtime y respuesta BUSY mientras un cierre mantiene el bloqueo.

## Cambios por archivo de la implementación 1C

Rutas relativas a mace-native-dev:

| Archivo | Cambio |
|---|---|
| helper/Mace.Lhm.Helper/AmdPowerPolicy.cs | Identificación conservadora y extracción del valor ASIC explícito |
| helper/Mace.Lhm.Helper/SensorSampler.cs | Aplica la política de potencia AMD |
| helper/Mace.Lhm.Tests/Program.cs | Trece comprobaciones nuevas y diagnóstico --amd-power-report |
| helper/PROTOCOL.md | Excepción AMD documentada, sin cambio de versión/layout |
| helper/README.md | Enlace a esta entrega |
| helper/RESULTADOS-1C.md | Evidencia, políticas, matriz, límites y reproducción |
| native/tests/lhm_fake_helper.c | Caso AMD con potencia de chip conocida |
| native/tests/test_lhm_receiver.c | Comprueba VALID y ámbito GPU_CHIP |
| native/CMakeLists.txt | Añade amd-chip a la regresión; conserva las 35 pruebas anteriores |
| app/src/main/java/com/mace/domain/model/SensorProvider.java | LHM=8 al final |
| app/src/main/java/com/mace/domain/model/CpuReading.java | Documentación CPU actualizada a LHM/PawnIO |
| app/src/main/java/com/mace/infrastructure/nativebridge/NativeCodes.java | Contrato de mapeo 0..8 |
| app/src/main/java/com/mace/infrastructure/nativebridge/NativeApi.java | Documenta BUSY transitorio |
| app/src/main/java/com/mace/infrastructure/nativebridge/NativeFunctions.java | Marca éxito solo con OK; inicialización sin espera por lock |
| app/src/main/java/com/mace/infrastructure/nativebridge/TelemetryInitialization.java | Reintentos y pausa acotados, dirigidos por sondeos |
| app/src/main/java/com/mace/infrastructure/nativebridge/FfmSensorsAdapter.java | Integra reintento y estados pendientes |
| app/src/main/java/com/mace/presentation/viewmodel/TelemetryPresentation.java | Tolerancia visual por métrica y fuente |
| app/src/main/java/com/mace/presentation/viewmodel/MainDashboardViewModel.java | Copia visual común para dashboard/HUD |
| app/src/main/java/com/mace/presentation/MaceApplication.java | Cierre completo aunque falle un recurso |
| app/src/test/java/com/mace/infrastructure/nativebridge/NativeCodesTest.java | Códigos 0..8 y desconocidos |
| app/src/test/java/com/mace/infrastructure/nativebridge/NativeLifecycleTest.java | FFM real hacia stubs: BUSY, cierre y concurrencia |
| app/src/test/java/com/mace/infrastructure/nativebridge/TelemetryInitializationTest.java | Ráfaga, ventana, pausa y recuperación con reloj simulado |
| app/src/test/java/com/mace/infrastructure/nativebridge/FfmSensorsAdapterTest.java | BUSY pendiente/recuperación y LHM V2 hasta formato visual |
| app/src/test/java/com/mace/infrastructure/nativebridge/NativeV2IntegrationTest.java | Registra también potencia GPU en la prueba física Java |
| app/src/test/java/com/mace/presentation/viewmodel/TelemetryPresentationTest.java | Límites de tolerancia, fallos inmediatos, GPU ambigua y UnknownPower |
| app/src/test/java/com/mace/presentation/viewmodel/MainDashboardPresentationTest.java | Dashboard y HUD reciben la misma copia tolerante |
| app/src/test/java/com/mace/presentation/view/hud/HudPaneTest.java | Casos de N/D en controles JavaFX; toolkit no termina entre tests |
| app/src/test/java/com/mace/presentation/MaceApplicationStopTest.java | Fallo de vista no impide apagar monitor/adaptadores |

El ZIP ajeno al cambio y las modificaciones previas de RESULTADOS-1B.md permanecen intactos. No se crea un commit automáticamente.

## Pruebas y NO VERIFICADO

- Helper: 63 comprobaciones aprobadas, compilación sin advertencias ni errores, 205 artefactos verificados. Firma omitida con aviso.
- CTest: 36/36 aprobadas, incluidas las 35 anteriores, ABI/offsets y el caso nuevo amd-chip. UnknownPower sigue UNSUPPORTED; no se cambia la semántica del receptor.
- Java: batería completa con la DLL real, JDK 22 y helper configurado. 279 pruebas, cero fallos/errores; una omitida porque falta MACE_LEGACY_DLL para el caso de DLL antigua.
- JavaFX: pruebas de controles del HUD y sus textos aprobadas. Hay infraestructura existente JUnit + Platform.startup; no se necesita añadir un framework UI.
- Prueba nativa física definitiva: 5–6 W GPU_CHIP VALID, inicio 0,049 ms, primera disponibilidad a 937 ms, sondeo máximo observado 0,596 ms. Mediciones locales, sin garantía universal.
- Se arrancó MACE nativo con las clases nuevas y la DLL exacta; el hilo JavaFX se observó en su bucle normal. La inspección visual propia del agente fue NO VERIFICADO: la herramienta de captura devolvió FrameArrived timed out y, al reintentar, window capture timed out. Esto no invalida la confirmación posterior del usuario de temperatura y potencia CPU en la UI elevada, recibida el 15/11/2026.
- CONFIRMADO POR EL USUARIO: temperatura y potencia CPU en la UI desde consola de administrador, además del éxito previamente comunicado de la prueba elevada de 1B.
- NO VERIFICADO: inspección visual independiente del agente, todas las métricas GPU/HUD en UI elevada, Intel/NVIDIA físicos, otros modelos AMD, suspensión/hotplug y calibración eléctrica externa.
- NO VERIFICADO: cierre normal de la ventana mediante UI automatizada, por el fallo de captura. El orden stop/shutdown sí está cubierto por pruebas automáticas.
- Dependencia deliberada del reporte textual de LHM 0.9.6: si cambia su formato o no permite identificar el valor ASIC, se conserva UnknownPower/N/D.
- Firma, licencia y distribución quedan pendientes. Se preservan los ganchos y no se modificó ningún instalador.

La primera batería Java dentro del aislamiento produjo READ_FAILED del helper; se repitió fuera y obtuvo lecturas reales. No se considera la ejecución aislada una validación de sensores.

## Reproducir las pruebas

PowerShell desde la raíz de mace-native-dev. Requiere JDK 22, Maven, SDK .NET fijado en helper/global.json y Visual Studio C++/Windows SDK:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-22'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\vs2026-lhm1b -RealHelper
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\vs2026-lhm1b\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$published=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $published 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
mvn -B -f .\app\pom.xml test
```

La prueba elevada de CPU se repite desde esa PowerShell como administrador:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\vs2026-lhm1b -RealHelper -RequireCpu
```

Para obtener el reporte de potencia:

```powershell
& .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe --amd-power-report
```

Los informes Java están en app/target/surefire-reports; la prueba física nativa crea lhm-live-*.txt. Código de salida esperado: 0.

## Comprobar la UI manualmente

1. Cierra las instancias anteriores de MACE para evitar confundir ventanas o versiones.
2. En PowerShell como administrador, ejecuta la configuración de JDK/rutas/hashes anterior. PawnIO debe estar Running;
3. Desde la raíz ejecuta:

```powershell
mvn -f .\app\pom.xml javafx:run
```

4. Confirma en consola Modo NATIVO y la ruta native/build/vs2026-lhm1b/Release/mace_native.dll. Espera unos segundos.
5. En CPU debe aparecer LibreHardwareMonitor y, con acceso correcto, temperatura y potencia reales; uso sigue siendo Windows. Sin elevar, N/D en esos dos sensores CPU es esperado.
6. En GPU selecciona Radeon RX 5500 XT: proveedor LibreHardwareMonitor, potencia con ámbito GPU chip y lectura en W. El número varía con la carga; no se exige repetir 5–6 W.
7. Abre Micro-HUD y activa GPU potencia. Debe coincidir con el dashboard, además de temperatura, uso y VRAM. El HUD actual ofrece esas cuatro métricas GPU; las siete se consultan en la sección GPU.
8. Los casos GPU ambigua y UnknownPower se verifican de forma determinista con los tests. No hace falta provocar fallos de controlador en hardware para probarlos.
9. Cierra la ventana principal: debe desaparecer el HUD. No debe quedar el helper perteneciente a esa instancia. No finalices LHM que hayas abierto tú.

El empaquetado instalado anterior no se actualizó; esta comprobación usa las clases del proyecto y rutas explícitas.

## Lista de correcciones documentales aplicadas el 15/11/2026

1. Registrada la confirmación del usuario de temperatura y potencia CPU en la interfaz elevada, con fecha de recepción y sin atribuirla al agente ni inventar cifras.
2. Sustituido el pendiente genérico de UI elevada por una separación entre CPU confirmada por el usuario y aspectos visuales que siguen NO VERIFICADO.
3. Precisados el filtro PCI, la comprobación literal de Overdrive Caps y el carácter conservador del rechazo de BOARD_POWER.
4. Separada la lista histórica de cambios de implementación de este cierre documental.
5. Añadido COBERTURA-AMD-POTENCIA.md con recorrido, condiciones, cobertura por grupos, opciones de ampliación, plan de pruebas, requisitos para 1D y lista de NO VERIFICADO.

No se ejecutaron nuevas pruebas ni se cambió código de producción en este cierre. Las cifras y resultados anteriores son evidencia histórica de 1C, no una nueva ejecución del 15/11/2026. Subfase 1C cerrada documentalmente; no se avanza a 1D y se detiene aquí hasta aprobación del usuario.
