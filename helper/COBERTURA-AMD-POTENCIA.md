> Documento histórico conservado. La opción C y la retirada de 1D fueron aprobadas después de esta evidencia; el estado vigente está en [RESULTADOS-1D-PASO1.md](RESULTADOS-1D-PASO1.md).

# Cobertura de potencia GPU AMD y requisitos previos a 1D

Fecha del informe: **15/11/2026, America/Lima**. Revisión de implementación: commit 79c713c, sin cambios de producción en este cierre. Se revisaron RESULTADOS-1C.md, PROTOCOL.md, AmdPowerPolicy.cs y sus consumidores. Este documento propone pruebas y alternativas; no las implementa ni autoriza retirar proveedores.

**Conclusión:** la potencia AMD validada físicamente corresponde a la **Radeon RX 5500 XT del usuario**. El código admite la identidad vendor/device **1002:7340**, con restricciones adicionales; no admite todas las Radeon. Solo eliminar ese filtro ampliaría las cifras aceptables por código, pero no establecería su exactitud ni equivalencia entre familias.

Convenciones:

- **PROBADO EN HARDWARE:** hay evidencia física local de 1C, identificada abajo.
- **PREVISTO POR CÓDIGO:** consecuencia de las condiciones actuales o del cambio hipotético expresamente descrito; no equivale a prueba física.
- **NO VERIFICADO:** falta evidencia del equipo, controlador, fuente o comportamiento correspondiente.
- **CONFIRMADO POR EL USUARIO:** observación comunicada por el usuario; se conserva separada de las mediciones del agente.

## 2.1. Cómo funciona hoy, de extremo a extremo

### Recorrido de una cifra

| Paso | Comportamiento comprobado en el código del proyecto |
|---|---|
| LHM y dispositivo | SensorSampler actualiza cada IHardware admitido. Para GPU exige que GenericGpu.DeviceId pueda traducirse a vendor/device/subsystem/revision PCI y que la tupla sea única en el registro |
| Lectura inicial | Selecciona Power/GPU Package; si no existe, intenta GPU Power. Duplicados no se eligen arbitrariamente; null produce WARMING_UP y un número no finito, READ_FAILED. La potencia comienza con significado UnknownPower |
| Reporte y política | AmdPowerPolicy.Apply solo considera la AMD permitida y un único Power/GPU Package, índice 3, con lectura inicial NUMERIC_REPORTED. Obtiene GetReport() del **mismo objeto hardware** |
| Valor elegido | Classify comprueba versión, capacidades y formato; Resolve extrae el **valor ASIC explícito** del reporte. Esa cifra sustituye al número inicial: no se convierte GPU Package a chip por su nombre |
| Protocolo | El helper envía la métrica de potencia en índice 1: value f64, state NUMERIC_REPORTED=1 y meaning GpuChip=5. LHM1 conserva cabecera de 48 bytes y dispositivo de 144; no agrega campos |
| DLL | El hilo receptor autentica PID, valida frame, secuencia, finitud/rangos y edad máxima de 3.000 ms. La asociación vuelve a exigir una tupla única en su inventario DXGI. Ready y una muestra coherente son necesarios para VALID |
| V2 | La potencia identificada pasa a power_w.value y state VALID=0; power_scope GPU_CHIP=4 y provider_id LHM=8. Los números 5 del significado privado y 4 del scope V2 pertenecen a enums diferentes |
| Java | V2Records decodifica los registros y NativeCodes mapea provider 8 a SensorProvider.LHM. La presentación solo tolera un WARMING_UP aislado según la política de 1C; no oculta READ_FAILED, NO_PROVIDER ni UNSUPPORTED |
| UI | Tarjeta GPU: W y ámbito GPU chip. HudPane: cifra en W si isValid(), N/D en otro caso. Un sensor no seleccionado en la configuración del HUD no se muestra |

Fuentes locales: [SensorSampler](Mace.Lhm.Helper/SensorSampler.cs), [AmdPowerPolicy](Mace.Lhm.Helper/AmdPowerPolicy.cs), [protocolo](PROTOCOL.md), [receptor C](../native/src/telemetry/lhm_receiver.c), [validación C](../native/src/telemetry/lhm_wire.c) y [V2Records](../app/src/main/java/com/mace/infrastructure/nativebridge/V2Records.java).

En GPU, PawnIO Running y elevación **no son condiciones universales**: se permiten sensores de API del controlador sin ellos. En CPU sí se exigen ambos para promover temperatura/potencia; no se trasladan esas reglas indiscriminadamente a GPU.

### Todas las condiciones de AmdPowerPolicy

**T**: requisito técnico de identificación, interpretación o ausencia de ambigüedad. **C**: restricción conservadora del mecanismo actual; podría cambiar tras evidencia y revisión. Una condición mixta separa qué debe conservarse de qué podría revisarse.

| Condición efectiva | Lugar | Tipo | Motivo y posibilidad de relajación |
|---|---|---|---|
| HardwareType.GpuAmd | Apply | T | Evita aplicar semántica AMD a otro proveedor que use etiquetas similares |
| PCI vendor == 0x1002 | Apply y Classify | T | Identifica el proveedor para esta interpretación |
| PCI device == 0x7340 | Apply y Classify | **C** | Principal allowlist de cobertura; no es un requisito físico de ASIC_POWER |
| Lectura original NUMERIC_REPORTED | Apply y Resolve | C | Evita promover una ausencia inicial. Un rediseño que lea directamente una fuente identificada podría revisarlo; hoy se conserva |
| SensorType.Power y nombre exacto GPU Package | Apply y Classify | Mixta | T: distinguir potencia de otro tipo de dato. C: etiqueta literal como huella de esta versión |
| Exactamente un candidato Power/GPU Package | Apply | T | No elegir entre sensores duplicados sin identificarlos |
| Índice de sensor == 3 | Apply y Classify | C | Huella de LHM 0.9.6, no propiedad física de una Radeon |
| Assembly version exactamente 0.9.6.0 | Classify | C | Fija el contrato de nombres/formato inspeccionado; no permite asumir compatibilidad con otra versión |
| Existe Overdrive Caps y después Overdrive5 Parameters | Classify | Mixta | T: delimitar la sección correcta. C: delimitadores textuales concretos |
| Status: ADL_OK dentro de esa sección | Classify | T | Exige una respuesta satisfactoria de capacidades; **no certifica el éxito de cada lectura ASIC** |
| Version: 8 dentro de esa sección | Classify | C | Comprobación literal de capacidades. No consulta un objeto tipado que pruebe cuál fue la ruta efectiva de adquisición |
| Sensor[ADL_PMLOG_ASIC_POWER].Supported: True | Classify | T | Exige que el reporte declare disponible la fuente elegida |
| Sensor[ADL_PMLOG_BOARD_POWER].Supported: False | Classify | **C** | Guardia de la validación inicial. Que exista potencia de placa no vuelve por sí solo ambiguo otro valor ASIC identificado |
| Una sola línea que comience por Sensor[ADL_PMLOG_ASIC_POWER].Value: | Resolve | T | Se rechazan tanto ausencia como duplicidad; no se elige un número arbitrario |
| La cifra se puede analizar con NumberStyles.Float e InvariantCulture | Resolve | Mixta | T: interpretar el número correctamente. C: aceptar exclusivamente ese formato |
| Cifra finita | Resolve | T | NaN e infinito no son lecturas físicas utilizables |
| Valor >= 0 | Resolve | T | No se acepta potencia negativa para este sensor de consumo |
| Valor <= 2.000 W | Resolve | C | Cota de seguridad compartida con LHM1, no una especificación nominal de la tarjeta |
| GetReport no lanza una excepción recuperable | Apply | T | Si falla no hay prueba utilizable del origen. OutOfMemoryException no se trata como un simple sensor ausente |

No hay filtro de **subsystem, revision, nombre comercial, fabricante de la placa ni número de serie** dentro de AmdPowerPolicy. Por ello, otras tarjetas con device 7340 pueden pasar la política si cumplen las demás condiciones; eso es PREVISTO POR CÓDIGO, no validación física de esas tarjetas.

El vendor 1002 distingue AMD. El device 7340 limita la identidad admitida. Versión, sensor, reporte, capacidades y valor son condiciones independientes del modelo comercial. La identidad completa PCI se usa además para asociación inequívoca en el helper y la DLL: no basta con coincidir en 1002:7340.

### Qué no garantiza esa política

- El ADL_OK comprobado es el de **Overdrive Caps**. No se comprueban por separado los campos Supported: 1 o Enabled: 1 de esa sección, ni un código de éxito individual de cada lectura en Performance Metrics.
- Los flags de disponibilidad se buscan mediante Contains; no se exige que cada flag aparezca una sola vez ni se detectan explícitamente flags contradictorios. La unicidad implementada corresponde a la **línea del valor ASIC**.
- El reporte no aporta al protocolo una fecha individual del sensor. La edad de 3.000 ms es la del **mensaje del helper**, no una certificación independiente de frescura eléctrica del ASIC.
- Si la política no identifica la cifra, devuelve la lectura original. Si esa lectura era numérica, normalmente sigue como UnknownPower y V2 entrega UNSUPPORTED/0. Si ya era WARMING_UP, UNSUPPORTED o READ_FAILED, conserva ese estado; una falla de transporte puede invalidar todo el frame.
- Un valor ASIC de 0 W puede ser VALID; no se invalida por ser cero ni se confunde con el marcador cero de una métrica no válida.

Son límites del código existente, no cambios implementados en este cierre.

## 2.2. Qué tarjetas podrían leerse en teoría

### Base documental y significado de «en teoría»

LHM 0.9.6, fijado al commit **3d331e3370efb858411f19511373eff65a218701**, contempla distintas rutas y prefiere BOARD_POWER cuando el identificador de familia alcanza FAMILY_NV3 o cuando la consulta ASIC no está disponible. Esto impide deducir una política universal a partir del rótulo GPU Package. [Fuente LHM fijada](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3370efb858411f19511373eff65a218701/LibreHardwareMonitorLib/Hardware/Gpu/AmdGpu.cs#L432).

Su enumeración identifica Vega, Navi/Navi2x, Van Gogh y varias APU posteriores; habilita el intento PMLog a partir de FAMILY_AI. **Intentar PMLog no garantiza Overdrive 8, ASIC disponible ni ausencia de BOARD_POWER.** [Familias e interop de LHM](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3370efb858411f19511373eff65a218701/LibreHardwareMonitorLib/Interop/AtiAdlxx.cs).

AMD separa los identificadores ASIC_POWER=23 y BOARD_POWER=73. Su API consulta datos por adaptador y capacidades del controlador; la documentación consultada no certifica para cada SKU la combinación exacta requerida por MACE. [Identificadores ADL](https://github.com/GPUOpen-LibrariesAndSDKs/display-library/blob/master/include/adl_defines.h), [API Overdrive 8](https://gpuopen-librariesandsdks.github.io/adl/group__OVERDRIVE8API.html).

Por tanto, «si se quitara solo el filtro de device» significa: **una GPU que además satisfaga todas las demás condiciones de la tabla sería aceptable para Resolve**. No significa que una generación completa publique esos datos ni que ese cambio sea seguro sin ensayarlo.

| Grupo | Hoy, con el código actual | Si se relajara únicamente el filtro de device | Confianza y motivo |
|---|---|---|---|
| RX 5500 XT del usuario, 1002:7340 / 38221462 / C5 | Potencia ASIC → GPU_CHIP cuando cumple las guardias | No necesita relajación | **PROBADO EN HARDWARE**: reporte local y 5–6 W VALID en DLL; Java registró 5 W VALID |
| Otra tarjeta/placa/revisión con device 7340 | Puede pasar las mismas guardias; no hay filtro de subsystem/revision en la política | Igual que hoy | **PREVISTO POR CÓDIGO**; hardware adicional **NO VERIFICADO** |
| Otra Radeon RDNA 1 / Navi, fuera de 7340 | Potencia N/D por el filtro de device | Candidata si el reporte satisface todas las condiciones | **NO VERIFICADO** por modelo/controlador; aceptación condicional **PREVISTA POR CÓDIGO** |
| Radeon dedicada RDNA 2 / Navi2x | N/D si su device no es 7340 | Candidata con OD8 + ASIC True + BOARD False y resto de condiciones | **NO VERIFICADO** físicamente; no se afirma cobertura de toda RX 6000 |
| Vega 10/20 | N/D fuera de 7340 | Solo candidata si además cumple el reporte literal OD8; PMLog por sí solo no basta | **NO VERIFICADO**; no se presupone OD8 por generación |
| Radeon cuyo reporte indique Overdrive 5/6/7 u otra versión | N/D en potencia identificada por esta política | Seguiría N/D por Version: 8 | **PREVISTO POR CÓDIGO**; asignar cada SKU a un backend concreto es **NO VERIFICADO** |
| RDNA 3 / Navi3x y familias para las que LHM prefiere placa | N/D fuera de 7340; BOARD True también la excluye | Si BOARD True, seguiría N/D. Una combinación ASIC True/BOARD False no debe aceptarse solo por generación o disponibilidad | **PREVISTO POR CÓDIGO** en rechazo; exactitud y capacidades por equipo **NO VERIFICADO** |
| Familias nuevas/no ensayadas | N/D fuera de 7340 | No existe validación para aprobarlas por inferencia | **NO VERIFICADO** |
| APU Ryzen con iGPU AMD | Una iGPU con device distinto de 7340 no obtiene potencia conocida por esta política | Solo sería candidata si LHM la expone como GPU, hay identidad PCI inequívoca y todas las demás pruebas pasan | **NO VERIFICADO**; potencia CPU/SoC no prueba potencia de su iGPU |

La comparación de familias del código es numérica: el umbral FAMILY_NV3 incluye también identificadores posteriores de algunas APU RDNA 2. No debe interpretarse como una frontera comercial exacta «todas las RDNA 3 en adelante». Fuente: enumeración y condición enlazadas arriba.

Para seleccionar equipos de prueba, AMD identifica la **RX 6600 como RDNA 2** y la **RX 7600 como RDNA 3**. Son ejemplos de grupos a investigar, no tarjetas declaradas compatibles por MACE ni recomendaciones de compra. [RX 6600, AMD](https://www.amd.com/en/newsroom/press-releases/2021-10-13-amd-radeon-rx-6600-graphics-card-delivers-incredib.html), [RX 7600, AMD](https://www.amd.com/en/newsroom/press-releases/2023-5-24-amd-introduces-amd-radeon-rx-7600-graphics-card-f.html).

### Casos que quedan como N/D

| Caso | Motivo | Alcance de N/D |
|---|---|---|
| device distinto de 7340 | La allowlist rechaza antes de consultar el reporte desde Apply | Potencia; otros sensores pueden seguir disponibles |
| BOARD_POWER soportado, aunque ASIC también lo esté | Falta la condición literal BOARD False | Potencia |
| Solo hay valor de placa conocido en LHM | No se implementó aquí un mapeo de ese sensor a GpuBoard | Potencia; no se reetiqueta como chip |
| Reporte ausente, formato distinto, nombre/índice diferente o versión LHM distinta | No se puede satisfacer el contrato textual fijado | Potencia o estado de ausencia original |
| GPU Package inexistente/null o duplicado | No se obtiene la lectura original exigida o un candidato único | Potencia; WARMING_UP/UNSUPPORTED según el caso |
| ASIC no soportado, valor ausente/duplicado/no interpretable/no finito/fuera de rango | Falta una fuente admisible | Potencia; no se usa otro número como sustituto |
| PCI incompleta o tupla duplicada | El adaptador no puede asociarse con certeza | Las siete métricas GPU del camino LHM |
| Helper desconectado, mensaje inválido, PID incorrecto o muestra caducada | Cache/frame inválido | Métricas del camino LHM, normalmente READ_FAILED |
| dGPU de portátil apagada/no enumerada | Puede no haber dispositivo o sensor accesible | **NO VERIFICADO** físicamente; no se infiere actividad por un cero |
| APU con solo un sensor de potencia CPU/SoC | Ese número no identifica por separado la iGPU | Potencia iGPU |

**Ver números en LHM o en --amd-power-report no significa que MACE los promueva a VALID.** Este diagnóstico imprime las GPU AMD y sus reportes sin aplicar la allowlist de producción.

### iGPU AMD y dedicada simultáneas; varias AMD

El helper trata cada IHardware por separado y obtiene su propio reporte. No usa el primer número de potencia global ni copia la cifra de una GPU a otra. La DLL conserva el LUID solicitado y busca correspondencia de la tupla PCI completa. **PREVISTO POR CÓDIGO**, no prueba física multi-GPU.

- iGPU AMD + RX 5500 XT: si ambas se enumeran e identifican inequívocamente, la dedicada puede pasar su política mientras la iGPU con otro device mantiene potencia N/D. La presencia de la iGPU no invalida por sí sola a la dedicada.
- Dos AMD con tuplas completas distintas: se evalúan independientemente; solo pasan las que satisfacen la allowlist y el reporte.
- Dos GPU con igual vendor/device/subsystem/revision: la política de asociación falla de forma conservadora, aunque Windows tenga LUID diferentes. LHM1 no transporta LUID ni bus/device/function para resolver ese empate; no se permite resolverlo por posición/nombre.
- La enumeración de LHM y las entradas fantasma del registro pueden limitar la identidad. El comportamiento físico con APU, híbridos, dispositivos inactivos o dos AMD sigue **NO VERIFICADO**.
- El protocolo admite una CPU y hasta 16 GPU; exceder los límites no habilita truncamiento arbitrario ni asociación por índice.

## 2.3. Opciones para ampliar la cobertura con seguridad

No se implementa ninguna opción en este cierre.

| Opción | Riesgo de falso VALID | Esfuerzo | Mantenimiento al cambiar LHM | Pruebas necesarias |
|---|---|---|---|---|
| **A. Allowlist con evidencia por entrada** | Bajo relativo si cada entrada fija fuente, ámbito y condiciones; no nulo ante cambios de controlador | Moderado e incremental | Revisar versión, formato y fuentes por entrada; no heredar validación automáticamente | Reportes positivos/negativos del modelo, cargas, comparación del mismo ámbito, regresión de identidad y parser |
| **B. Capacidades en lugar de device ID** | Mayor si solo se comprueban flags y un número; el soporte anunciado no certifica semántica, frescura ni familias con otra ruta preferida | Medio/alto para endurecer el contrato y demostrarlo | Alto al cambiar los reportes; convendría una fuente estructurada y versionada | Matriz amplia entre ASIC/Board/ambas/ninguna, generaciones, APU, controladores y fallos/contradicciones |
| **C. Mantener la restricción actual** | No agrega riesgo de expansión; conserva los límites actuales del parser y fuente | Bajo | Mantener pin de versión y regresiones; una actualización puede dejar N/D | Regresión local y pruebas de fallo; no acredita más tarjetas |

**Recomendación: A para ampliar de forma incremental**, manteniendo ahora C hasta reunir evidencia. No recomiendo reemplazar hoy el filtro PCI por una comprobación genérica de capacidades. La relación entre modelos, rutas del controlador y ámbito no está validada más allá del caso local.

Una entrada debería registrar PCI y variantes ensayadas, versión/commit LHM, controlador, fuente exacta, unidad, ámbito, reporte y resultados de carga/fallos. Añadir un ID **sin cambiar lo demás** no resuelve tarjetas con BOARD True ni backends distintos de 8; eso requiere otra regla de fuente y su validación.

Si más adelante se elige B, sus salvaguardas mínimas serían: asociación por adaptador inequívoca; fuente ASIC o Board explícita; semántica de familia revisada; éxito de adquisición distinguido de éxito de capacidades; ausencia de flags contradictorios y valor único; control de frescura; versión/formato fijados; rangos, NaN/Inf, ceros legítimos y fallo cerrado. Si se admite placa, debe salir GpuBoard, no GpuChip. **Son requisitos propuestos, no prestaciones actuales.**

Retirar proveedores con la opción C sería una decisión de aceptar cobertura limitada, no una demostración de equivalencia. Requiere aprobación expresa del alcance y resolver las demás filas de la sección 3.

## 2.4. Qué se necesita para validarla

### Qué puede hacer el usuario con su equipo actual

La CPU AMD en UI elevada y el éxito del test elevado están **CONFIRMADOS POR EL USUARIO**. Se puede completar la evidencia local de RX 5500 XT sin ampliar código: guardar reportes en reposo, carga estable y regreso a reposo, capturas del ámbito GPU chip y logs V2/Java. Eso no verifica otro modelo.

Desde la raíz de mace-native-dev, con los binarios de pruebas de 1C disponibles:

```powershell
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
& .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe --amd-power-report |
    Tee-Object -FilePath ".\helper\artifacts\amd-power-$stamp.txt"
```

El diagnóstico actual espera tres actualizaciones por GPU y toma un reporte, no registra por sí solo una serie continua de un minuto. Repetirlo en cada estado de carga. Si no existe el ejecutable, los prerrequisitos y Build-Helper.ps1 están en RESULTADOS-1C.md; el EXE no debe distribuirse solo sin sus dependencias.

Para comprobar el camino completo, desde PowerShell como administrador y con PawnIO ya instalado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\vs2026-lhm1b -RealHelper -RequireCpu
```

El script ejecuta la regresión existente y una observación física de ocho segundos. **No activa HVCI ni certifica cobertura de otros modelos.** En una AMD fuera de la allowlist puede pasar la prueba global aunque la potencia siga UNSUPPORTED: hay que inspeccionar power state/scope por separado.

### Equipos y evidencias por obtener

| Equipo/grupo | Objetivo | Qué requiere |
|---|---|---|
| RX 5500 XT actual | Completar carga, versión de controlador, comparación y UI | Equipo disponible; no ampliar cobertura |
| Otra placa con el mismo device y/o otra RDNA 1 | Distinguir variante permitida por código de modelo nuevo | Reportes de equipos adicionales; no presumir igualdad de firmware/controlador |
| Radeon dedicada RDNA 2, por ejemplo RX 6600 | Saber si su driver cumple ASIC True/Board False/OD8 | Equipo adicional o colaborador; hoy su potencia no se habilita por solo aportar un reporte |
| Radeon RDNA 3, por ejemplo RX 7600 | Cubrir una ruta de placa y el rechazo esperado de la regla actual | Equipo adicional; demostrar fuente/ámbito antes de proponer otra regla |
| Vega y una Radeon con backend anterior/distinto de 8 | Probar candidatos y rechazos del límite OD8 | Reportes reales; no asignar backend solo por nombre comercial |
| APU Ryzen sola y APU + dedicada AMD | Identidad, fuente de potencia iGPU y aislamiento entre dispositivos | Equipo adicional con ambas GPU visibles |
| Dos AMD; incluir dos de tupla idéntica si se pretende soportarlas | Verificar independencia y rechazo conservador de ambigüedad | Equipo multi-GPU; resolverlo exigiría una evolución de identificación posterior |
| Intel + NVIDIA + iGPU Intel visible, preferiblemente Windows 11 | CPU Intel, NVML, IGCL y escenarios híbridos | La laptop Intel/NVIDIA mencionada por el usuario podría cubrir varias filas; configuración exacta aún NO VERIFICADO |

Pedir a cada persona que pruebe:

1. Fecha, modelo CPU/GPU, sobremesa/portátil, iGPU+dGPU presentes, Windows/compilación y versión del controlador.
2. Los cuatro campos PCI, DeviceId, AdapterIndex del reporte, identificador/nombre/tipo/índice del sensor y LUID V2. No asociar solo por orden de listado.
3. Versión de assembly y paquete LHM, commit/versión MACE y hashes de DLL/helper usados.
4. Reporte completo: sección Overdrive Caps, ASIC_POWER, BOARD_POWER y sus valores/flags, con errores incluidos.
5. Elevación, estado PawnIO y evidencia separada de HVCI cuando forme parte del ensayo. El hecho de ver temperatura no acredita HVCI activo.
6. Logs de prueba, código de salida, capturas de CPU/GPU/HUD y estado/valor/scope/provider V2. Conservar los N/D y sus motivos.
7. Mediciones comparables en reposo y carga estable, por ejemplo ventanas de 60 segundos a 1 Hz con número de muestras y sus marcas de tiempo. Estas ventanas son un plan propuesto; no son mediciones realizadas en este cierre.

### Comparar con otra fuente de potencia

La referencia debe identificar **ASIC/chip** si se compara con GPU_CHIP. Dos programas que reutilizan LHM no son dos métodos independientes. GPU Package del diagnóstico y el valor ASIC de su reporte pueden corresponder a instantes distintos; no se exige igualdad entre esos dos números aislados.

Una herramienta de monitorización o API alternativa solo sirve para la comparación cuantitativa tras confirmar su ámbito y unidad. El nombre GPUPower de ADLX, por sí solo, no demuestra ASIC: la página consultada lo describe genéricamente como potencia GPU en W. En cambio, GPUTotalBoardPower incluye componentes de placa y VRAM; **no debe igualarse a ASIC**. [GPUPower, AMD](https://gpuopen.com/manuals/adlx/adlx-sdk-references/adlx-interfaces/performance-monitoring/iadlxgpumetrics/gpupower/), [GPUTotalBoardPower, AMD](https://gpuopen.com/manuals/adlx/adlx-sdk-references/adlx-interfaces/performance-monitoring/iadlxgpumetrics/gputotalboardpower/).

Comparar la misma GPU, intervalos alineados y carga estable; guardar mediana, mínimo/máximo, resolución e intervalo de actualización de ambas fuentes. Investigar diferencias persistentes o cifras que no respondan a la carga antes de admitir una entrada. No hay un umbral universal de precisión aprobado por este proyecto. Un medidor en el enchufe mide el equipo; incluso una medida física completa de placa incluye dominios fuera del ASIC y no valida por igualdad esa métrica.

## 3. Requisitos previos para poder aprobar 1D

Estados de esta tabla: **CUMPLIDO** en el alcance indicado; **PENDIENTE** requiere decisión/trabajo; **NO VERIFICADO** necesita evidencia. Las pruebas simuladas no convierten una fila física en cumplida.

| Requisito | Estado actual | Evidencia y cómo cerrarlo | Equipo necesario |
|---|---|---|---|
| 1. CPU AMD elevada: temperatura y potencia en UI y test -RequireCpu | **CUMPLIDO para el equipo local, por confirmación del usuario** | UI confirmada el 15/11/2026 y test elevado confirmado previamente. Archivar log/captura/hash mejoraría la trazabilidad; no inventar valores ni HVCI | AMD actual, consola administrador, PawnIO Running |
| 2. GPU AMD RX 5500 XT | **CUMPLIDO para el caso físico de 1C** | Reporte y 5–6 W VALID/GPU_CHIP en DLL, 5 W VALID en Java. No equivale a toda Radeon ni a medición eléctrica externa | AMD actual |
| 2b. Alcance AMD después de eliminar ADLX | **PENDIENTE** | Aprobar una lista de equipos soportados o aceptar explícitamente la restricción actual; otras familias, APU y multi-GPU siguen NO VERIFICADO | AMD adicionales según 2.4 |
| 3. CPU Intel: temperatura y potencia RAPL mediante LHM/PawnIO | **NO VERIFICADO en hardware** | Ejecutar -RealHelper -RequireCpu elevado, comprobar V2 y UI tras calentamiento, carga/retorno y falta de acceso. GetSystemTimes debe seguir válido sin sensores de CPU | Intel real; idealmente la laptop Intel/NVIDIA |
| 4. NVIDIA: NVML principal | **NO VERIFICADO en hardware** | Ver provider 5; comprobar temperatura, uso, potencia y VRAM. fan_percent y fan_rpm se leen por funciones separadas; % no se transforma en RPM. Si RPM no está soportado, N/D es correcto | NVIDIA real con controlador |
| 4b. NVIDIA: respaldo LHM | **NO VERIFICADO en hardware** | Hay prueba simulada de dispatch. Obtener snapshots crudos del helper real y después verificar el respaldo V2 con un medio de prueba controlado. El ejecutable normal no ofrece un interruptor público para forzar NVML inactivo | NVIDIA; ensayo de respaldo pendiente de preparación/autorización posterior |
| 5. Intel GPU: IGCL y comparación con LHM | **NO VERIFICADO en hardware; mantener IGCL es requisito** | Ver provider 7 y métricas de IGCL; comparar identidad, disponibilidad, unidades y estados contra LHM. No declarar equivalencia por un test simulado | iGPU Intel visible y/o Intel discreta si forma parte del alcance |
| 6. CPU no elevada | **CUMPLIDO en código, simulación y prueba física local de 1C** | Temperatura/potencia NO_PROVIDER, nunca cero VALID; uso CPU/RAM y métricas GPU disponibles continúan. Usuario puede repetir sin administrador | AMD actual; repetir en Intel |
| 6b. PawnIO ausente/no cargado en un equipo limpio | **NO VERIFICADO físicamente** | Casos simulados Missing/Stopped cubiertos. Obtener evidencia real en equipo de pruebas/instantánea limpia; no detener drivers del equipo de trabajo para improvisar la prueba | Máquina de pruebas o VM para estados/instalación; hardware físico para sensores |
| Windows 11 con HVCI activo | **NO VERIFICADO** | Documentar Integridad de memoria activa, reinicio aplicado, PawnIO Running y prueba elevada satisfactoria. Un éxito sin registrar HVCI no cierra esta fila | Windows 11 físico, preferiblemente Intel/NVIDIA y también AMD para matriz completa |
| 7. Inventario de retirada y pérdida de cobertura aceptada | **PENDIENTE de aprobación de alcance** | Ver tabla siguiente; preservar GetSystemTimes, NVML, IGCL y contrato. No renumerar IDs públicos por retirar implementaciones | Revisión de código y regresiones antes/después, en los equipos definidos |

**IGCL y NVML no forman parte de los proveedores que se deben retirar en 1D.** Conservar IGCL no depende de que se demuestre antes una equivalencia que todavía no existe. El respaldo LHM no sustituye individualmente una métrica ausente mientras el primario aporte sensores válidos: la selección actual es por conjunto del adaptador.

Para inspeccionar LHM físico sin alterar NVML/IGCL puede usarse el receptor de referencia existente de pruebas:

```powershell
$published=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
& .\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe (Join-Path $published 'Mace.Lhm.Helper.exe')
```

Este comando muestra snapshots crudos del helper y comprueba el protocolo; **no fuerza el cambio de proveedor de la DLL** ni convierte NUMERIC_REPORTED a VALID por sí mismo. No se ejecutó en este cierre. El respaldo físico completo requiere completar esa segunda parte en una prueba posterior; no se propone renombrar DLL del controlador ni desactivar el driver del usuario.

Además, el código NVIDIA de LHM 0.9.6 usa NVML para GPU Package. Por ello el respaldo no es necesariamente independiente de NVML en potencia; el helper MACE mantiene UnknownPower para NVIDIA actualmente. [Fuente NVIDIA de LHM fijada al mismo commit](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3370efb858411f19511373eff65a218701/LibreHardwareMonitorLib/Hardware/Gpu/NvidiaGpu.cs). No se promete recuperar todas las métricas ante una falla del primario.

### Proveedores que desaparecerían y pérdida posible

| Retirada prevista | Función actual/anterior | Qué quedaría si LHM no entrega el dato |
|---|---|---|
| WinRing0 / Rdmsr / proveedor Intel MSR | Acceso alternativo a temperatura Intel y energía RAPL | Temperatura/potencia Intel N/D; GetSystemTimes debe conservarse, aunque comparta hoy archivo con código MSR |
| Ryzen Master Monitoring SDK / AmdRyzenProvider | Vía alternativa de temperatura AMD | Temperatura AMD N/D. No se inventa una vía heredada de potencia AMD que ese proveedor no ofrecía |
| ADLX / AdlxProvider | Vía alternativa AMD GPU de temperatura, potencia, uso, RPM y VRAM usada cuando soportadas | Potencia fuera de la política LHM permanece N/D; si falla toda la vía LHM pueden perderse también los demás sensores AMD |
| NVML | **Se conserva** | NVIDIA mantiene la primaria; si falla, solo hay cobertura de respaldo que realmente se demuestre |
| IGCL | **Se conserva** | Intel mantiene su primaria; LHM no se considera equivalente sin evidencia |
| GetSystemTimes y RAM Windows | **Se conservan** | Uso CPU y RAM continúan aunque falten acceso al driver o sensores |

La «red de seguridad» heredada no es un fallback universal activo: hoy, cuando la vía LHM está habilitada, sus resultados sustituyen las métricas CPU/AMD aunque sean no válidos. La ausencia del helper en la ruta predeterminada puede deshabilitar esa vía y permitir la heredada; una ruta explícita inválida no tiene ese efecto. Mantener los proveedores permite una alternativa de despliegue/diagnóstico, pero no implica que ADLX o Ryzen se activen automáticamente cada vez que cae el helper.

### Orden mínimo de pruebas propuesto

1. **Equipo AMD actual:** conservar las confirmaciones CPU y la evidencia de RX 5500 XT; completar logs/identidad/estado de Windows y una sesión de carga de potencia ASIC.
2. **Laptop Intel + NVIDIA + iGPU Intel visible:** comprobar primero CPU elevada, luego ausencia de elevación, NVML principal y sus unidades, e IGCL. Esto cierra varias filas con un solo equipo si la configuración realmente expone ambos adaptadores.
3. **Windows 11/HVCI en hardware:** repetir allí con evidencia explícita del estado y reinicio. Si la laptop anterior reúne esas condiciones, aprovecharla; para cobertura AMD/HVCI hace falta también un AMD adecuado.
4. **AMD adicional:** obtener al menos un reporte RDNA 2 y uno RDNA 3/BOARD, más APU/híbrido si se ofrece ese soporte. Elegir A/B/C a partir de los resultados; no habilitar por un nombre de familia.
5. **Fallos y respaldos:** validar helper caído/caducidad/reconexión, falta real de PawnIO en entorno de prueba y respaldo físico NVIDIA/Intel. No equiparar la VM sin GPU asignada con una prueba de sensores físicos.
6. Solo después, decidir el alcance aprobado de **1D en un cambio separado**, ejecutar regresiones de ABI/estados/cierre antes y después y comprobar que no sobrevivan dependencias de los proveedores retirados. En este paso no se hace esa limpieza.

**Recomendación de cierre:** todavía no hay evidencia para declarar equivalencia general antes de retirar las alternativas. CPU AMD local y RX 5500 XT están cubiertas en el alcance descrito; faltan CPU Intel, primarias y respaldos físicos de NVIDIA/Intel, HVCI y una decisión explícita sobre el alcance AMD.

## 4. Lista consolidada de NO VERIFICADO

- Compatibilidad física de otras placas 7340, otras RDNA/Vega, APU y multi-GPU; backend y combinación de capacidades por SKU/controlador.
- Exactitud eléctrica externa, calibración y umbral universal de error del ASIC.
- Éxito/frescura individual de cada lectura ASIC que no pueda deducirse del mensaje/reporte actual; robustez ante flags contradictorios.
- CPU Intel con temperatura y RAPL reales; NVIDIA NVML y respaldo físico; Intel IGCL y respaldo/equivalencia LHM.
- PawnIO ausente/no cargado probado físicamente y Windows 11 con HVCI activo documentado.
- Inspección visual independiente del agente de la UI, totalidad GPU/HUD elevada y cierre por ventana. **La temperatura y potencia CPU en UI elevada sí están confirmadas por el usuario el 15/11/2026.**
- Suspensión, reanudación, hotplug, dGPU inactiva y cambios de controlador en los equipos adicionales.

Las opciones de ampliación, criterios y ensayos aquí propuestos están **PENDIENTES**, no implementados. Los 63 checks del helper, 36 tests nativos y 279 tests Java pertenecen a la evidencia histórica de 1C; no se reejecutaron hoy. Este cierre no modifica código, no instala nada, no cambia HVCI y no avanza a 1D.
