# Cierre de Subfase 1D — Paso 3

Fecha: **16/11/2026, America/Lima**. El usuario aprobó el Paso 3 y pidió revisar
el TSV y los informes por rutas/datos personales. Revisión de código y evidencia:
**4f283d9ce59ca84e18e45a74c2549c301b867e12**, rama `LHM_implementado`.
Respaldo anterior a la retirada: **1b62000eddd0819258ceb33bf64eed69c2f09d44**.

**Resultado:** se retiraron los tres proveedores heredados de MACE, se conservaron
las vías protegidas y se verificó la app-image local. **La retirada no demuestra
equivalencia en plataformas sin validar físicamente.** Potencia AMD solo para PCI
1002:7340 bajo las guardias de 1C; las demás Radeon mantienen potencia N/D.

Este cierre es documental y de auditoría. No reinstala/desinstala componentes,
no cambia HVCI, no inicia fases posteriores y no publica nada. No se anonimizaron
los documentos ni logs originales. Se conserva la distinción entre mediciones
propias, pruebas simuladas y confirmaciones previas del usuario.

## 1. Commits y cambios por archivo/tramo

El commit de respaldo incluye el cierre documental 1C. Los siguientes commits
son los tramos y correcciones realmente ejecutados; el commit de este informe
es solo el cierre documental, posterior a ellos.

| Tramo | Commit completo |
|---|---|
| 1D-1 | `44c6609ad1af20a2bec5fe019003c2ff8aa28f1a` |
| 1D-2 | `aad1fbd46677f45a86e8344c943519d5c5ed8951` |
| 1D-3 | `2868b7f2495e251a06ccb5a70912ee71e4b616a2` |
| 1D-4 | `9911cf52db2cc82d2dcd339a438108f099048791` |
| Paso 2: CMake | `7e6c5327551d42a60f86cc78618c1d25bb3e9c95` |
| Paso 2: Java | `56132e26a91651c100b1e84a3df2244f0821484b` |
| Paso 2: imagen | `4f283d9ce59ca84e18e45a74c2549c301b867e12` |

### 1D-1 — `44c6609`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `app/src/main/java/com/mace/domain/model/SensorProvider.java` | Conservar orden/IDs; comentarios y etiquetas de reserva, sin implementación activa. |
| A | `docs/historico/pruebas-retiradas/1D-1-lhm_fixture_cpu.c.txt` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/pruebas-retiradas/README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `helper/RESULTADOS-1D-PASO1.md` | Registrar cambios, evidencia, aprobación y límites del paso/tramo correspondiente. |
| M | `native/CMakeLists.txt` | Añadir cuatro pruebas de ausencia/driver; layouts y compilación conservados. |
| M | `native/include/mace_status.h` | IDs 2/3/6 reservados/obsoletos; 4 reservado nunca implementado; valores numéricos intactos. |
| M | `native/src/mace_api.c` | Conservar uso Windows; simplificar llamada interna y quitar caché/include CPU retirados. |
| M | `native/src/telemetry/cpu_sensors.c` | Conservar literalmente GetSystemTimes/ReadCpuUsagePercent y sus referencias; retirar lectura hardware y simplificar sondeo/cierre. |
| M | `native/src/telemetry/cpu_sensors.h` | Interfaz interna de uso CPU Windows, sin declaraciones hardware retiradas. |
| M | `native/src/telemetry/legacy_telemetry.c` | 1D-1: quitar lectura Intel; 1D-2: retirar inicialización/sondeo Ryzen y caché CPU; conservar trabajador GPU/inventario. |
| M | `native/src/telemetry/lhm_receiver.c` | Ausencia del helper siempre NO_PROVIDER; conservar capacidad Windows solo con caché válida y PCI única. |
| M | `native/tests/lhm_fake_helper.c` | Añadir estado PawnIO Stopped a simulación existente. |
| M | `native/tests/lhm_fixture_cpu.c` | Usar implementación Windows real sin inicialización hardware; original archivado. |
| M | `native/tests/lhm_fixture_providers.c` | Simular capacidad Windows independiente para los casos sin helper. |
| M | `native/tests/test_lhm_receiver.c` | Comprobar ausencias, no elevación, driver, ceros legítimos/no válidos y capacidad independiente. |

### 1D-2 — `aad1fbd`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `app/src/main/java/com/mace/domain/model/SensorProvider.java` | Conservar orden/IDs; comentarios y etiquetas de reserva, sin implementación activa. |
| M | `app/src/main/java/com/mace/infrastructure/mock/MockScenario.java` | Actualizar descripción de escenario a proveedores vigentes. |
| M | `app/src/main/java/com/mace/infrastructure/mock/MockSensorsAdapter.java` | Escenarios vigentes producen LHM; conservar estados y métricas simuladas. |
| M | `app/src/test/java/com/mace/infrastructure/mock/MockSensorsAdapterTest.java` | Actualizar nombre/expectativas de proveedores sin eliminar pruebas de estados. |
| M | `app/src/test/java/com/mace/infrastructure/nativebridge/FfmSensorsAdapterTest.java` | Actualizar fixture/expectativas CPU a LHM. |
| A | `docs/historico/scripts/README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| R100 | `native/scripts/check-ryzen-sdk-version.ps1` → `docs/historico/scripts/check-ryzen-sdk-version.ps1.txt` | Archivar comprobador como texto; retirarlo de CMake y rutas ejecutables. |
| M | `helper/RESULTADOS-1D-PASO1.md` | Registrar cambios, evidencia, aprobación y límites del paso/tramo correspondiente. |
| M | `native/CMakeLists.txt` | Retirar fuente/configuración C++ y validación del SDK CPU. |
| M | `native/include/mace_status.h` | IDs 2/3/6 reservados/obsoletos; 4 reservado nunca implementado; valores numéricos intactos. |
| M | `native/scripts/test-lhm1b.ps1` | Retirar argumentos CMake de SDK; conservar regresión y prueba física opcional. |
| M | `native/src/internal/mace_runtime.c` | Retirar include Ryzen sobrante; conservar sincronización y ciclo de vida. |
| M | `native/src/mace_api.c` | Conservar uso Windows; simplificar llamada interna y quitar caché/include CPU retirados. |
| D | `native/src/telemetry/amd_ryzen_provider.cpp` | Retirar implementación/declaraciones; recuperable en respaldo Git, sin modificar SDK instalados fuera del repo. |
| D | `native/src/telemetry/amd_ryzen_provider.h` | Retirar implementación/declaraciones; recuperable en respaldo Git, sin modificar SDK instalados fuera del repo. |
| M | `native/src/telemetry/legacy_telemetry.c` | 1D-1: quitar lectura Intel; 1D-2: retirar inicialización/sondeo Ryzen y caché CPU; conservar trabajador GPU/inventario. |
| M | `native/src/telemetry/legacy_telemetry.h` | Retirar interfaz de caché CPU; conservar GPU, inventario y estados. |

### 1D-3 — `2868b7f`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `app/src/main/java/com/mace/domain/model/GpuReading.java` | Actualizar documentación de proveedores vigentes. |
| M | `app/src/main/java/com/mace/domain/model/SensorProvider.java` | Conservar orden/IDs; comentarios y etiquetas de reserva, sin implementación activa. |
| M | `app/src/main/java/com/mace/infrastructure/mock/MockScenario.java` | Actualizar descripción de escenario a proveedores vigentes. |
| M | `app/src/main/java/com/mace/infrastructure/mock/MockSensorsAdapter.java` | Escenarios vigentes producen LHM; conservar estados y métricas simuladas. |
| M | `app/src/main/java/com/mace/infrastructure/nativebridge/NativeFunctions.java` | Actualizar comentario de ciclo de vida; mantener carga FFM/Arena global. |
| M | `app/src/test/java/com/mace/infrastructure/mock/MockSensorsAdapterTest.java` | Actualizar nombre/expectativas de proveedores sin eliminar pruebas de estados. |
| A | `docs/historico/pruebas-retiradas/1D-3-test_gpu_provider_dispatch.c.txt` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| M | `docs/historico/pruebas-retiradas/README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| M | `helper/RESULTADOS-1D-PASO1.md` | Registrar cambios, evidencia, aprobación y límites del paso/tramo correspondiente. |
| M | `native/CMakeLists.txt` | Retirar fuente/configuración ADLX y define del mock. |
| M | `native/include/mace_status.h` | IDs 2/3/6 reservados/obsoletos; 4 reservado nunca implementado; valores numéricos intactos. |
| M | `native/scripts/test-lhm1b.ps1` | Retirar argumentos CMake de SDK; conservar regresión y prueba física opcional. |
| D | `native/src/telemetry/adlx_provider.c` | Retirar implementación/declaraciones; recuperable en respaldo Git, sin modificar SDK instalados fuera del repo. |
| M | `native/src/telemetry/gpu_providers.c` | Retirar únicamente integración ADLX; NVML, IGCL y capacidad DXGI intactos. |
| M | `native/tests/test_gpu_provider_dispatch.c` | Conservar pruebas NVML/IGCL y verificar ausencia del proveedor AMD anterior; original archivado. |

### 1D-4 — `9911cf5`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `.gitignore` | Ignorar binarios, NuGet, artefactos y ZIP local de respaldo. |
| M | `REVIEW_NOTES.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `app/lib/README.txt` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `app/src/main/java/com/mace/domain/model/SensorProvider.java` | Conservar orden/IDs; comentarios y etiquetas de reserva, sin implementación activa. |
| M | `app/src/main/java/com/mace/infrastructure/mock/MockSensorsAdapter.java` | Escenarios vigentes producen LHM; conservar estados y métricas simuladas. |
| M | `app/src/test/java/com/mace/domain/DomainFixtures.java` | Actualizar proveedor de fixtures generales a LHM. |
| M | `app/src/test/java/com/mace/domain/model/TelemetrySnapshotCompatibilityTest.java` | Mantener prueba con fixture de proveedor vigente. |
| M | `app/src/test/java/com/mace/infrastructure/nativebridge/NativeCodesTest.java` | Conservar y comentar compatibilidad numérica de IDs reservados. |
| M | `app/src/test/java/com/mace/infrastructure/nativebridge/V2RecordsTest.java` | Conservar serialización de IDs históricos, explícitamente sin proveedor hardware. |
| A | `docs/REFERENCIAS-ACEPTADAS-1D.tsv` | Listado exhaustivo por archivo/línea/columna y motivo; se actualiza al cerrar documentación. |
| A | `docs/historico/README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-REVIEW_NOTES.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-app__lib__README.txt` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__ABI.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__AMD_CPU_TEMPERATURE.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__HANDOFF.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__HARDWARE_COMPATIBILITY.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__PHASE2.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__PHASE3.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__PHASE4.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__PHASE5.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__TEAM_DIAGNOSTIC_GUIDE.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/native/pre1D-native__docs__TESTING.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| M | `docs/historico/pruebas-retiradas/README.md` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/pruebas-retiradas/pre1D-MockSensorsAdapter.java.txt` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| A | `docs/historico/pruebas-retiradas/pre1D-MockSensorsAdapterTest.java.txt` | Conservar original histórico como documentación/texto, fuera de compilación y producto; sin anonimizar. |
| M | `helper/COBERTURA-AMD-POTENCIA.md` | Conservar evidencia histórica y aclarar estado de la migración. |
| M | `helper/RESULTADOS-1B.md` | Conservar evidencia histórica y aclarar estado de la migración. |
| M | `helper/RESULTADOS-1D-PASO1.md` | Registrar cambios, evidencia, aprobación y límites del paso/tramo correspondiente. |
| M | `native/README.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/ABI.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/AMD_CPU_TEMPERATURE.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/HANDOFF.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/HARDWARE_COMPATIBILITY.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/PHASE2.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/PHASE3.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/PHASE4.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/PHASE5.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/TEAM_DIAGNOSTIC_GUIDE.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/docs/TESTING.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| M | `native/include/mace_status.h` | IDs 2/3/6 reservados/obsoletos; 4 reservado nunca implementado; valores numéricos intactos. |
| A | `native/scripts/audit-retired-providers.ps1` | Enumerar cada referencia y motivo, rechazar referencias no justificadas; excepción NDD aprobada. |
| M | `native/scripts/build-native.ps1` | Eliminar parámetros y argumentos de SDK retirados. |
| A | `native/scripts/check-protected-telemetry.ps1` | Comprobar archivos, funciones e IDs protegidos contra respaldo. |
| M | `native/scripts/package-native.ps1` | Eliminar opciones/campos SDK; conservar commit/hash/ABI, inventariar CTest y firmar DLL antes de fijar su hash. |
| M | `native/test_main.c` | Etiquetar IDs históricos como reservados, sin invocar proveedores retirados. |
| A | `packaging/README.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| A | `packaging/THIRD-PARTY-NOTICES.txt` | Inventariar versiones, autores, licencias y fuentes; pendientes de redistribución explícitos. |
| M | `packaging/package-windows.bat` | Copiar cinco componentes, limpiar rutas generadas con control; Paso 2: excluir .lib e inspeccionar imagen completa. |
| A | `packaging/pawnio-2.2.0.json` | Fijar versión/hash/URL del instalador intacto; revisión legal pendiente. |
| A | `packaging/prepare-directory.ps1` | Limpiar solo tres destinos generados permitidos; rechazar reparse points. |
| A | `packaging/stage-runtime.ps1` | Verificar/copiar runtime, helper y PawnIO intacto; avisos y manifiesto; preservar firma opcional. |
| A | `packaging/tests/test-runtime-payload.ps1` | Seis comprobaciones de copia, FFM y rechazo de alteraciones. |
| A | `packaging/third-party/HidSharp-LICENSE.txt` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/Intel-IGCL-LICENSE.txt` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-LICENSE.TXT` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/System.CodeDom-THIRD-PARTY-NOTICES.TXT` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/System.IO.Ports-THIRD-PARTY-NOTICES.TXT` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| A | `packaging/third-party/System.Management-THIRD-PARTY-NOTICES.TXT` | Conservar texto de licencia/avisos del paquete; atribuciones públicas sin anonimizar. |
| M | `packaging/verify-native-dll.ps1` | Conservar SHA-256/ABI; retirar lectura/advertencias de campos SDK. |
| A | `packaging/verify-runtime.ps1` | Exigir cinco componentes y hashes; rechazar archivos ajenos o de pruebas. |

### Paso 2: CMake — `7e6c532`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `native/scripts/native-common.ps1` | Leer toda la salida de cmake --version; evitar falso -1 en PowerShell 5.1. |

### Paso 2: Java — `56132e2`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `native/scripts/verify-native.ps1` | Leer toda la salida de java --version; conservar comprobación C/FFM. |

### Paso 2: imagen — `4f283d9`

| Estado | Archivo | Cambio |
|---|---|---|
| M | `docs/REFERENCIAS-ACEPTADAS-1D.tsv` | Listado exhaustivo por archivo/línea/columna y motivo; se actualiza al cerrar documentación. |
| A | `helper/RESULTADOS-1D-PASO2.md` | Registrar cambios, evidencia, aprobación y límites del paso/tramo correspondiente. |
| M | `native/scripts/audit-retired-providers.ps1` | Enumerar cada referencia y motivo, rechazar referencias no justificadas; excepción NDD aprobada. |
| M | `packaging/README.md` | Actualizar documentación operativa; original íntegro conservado bajo docs/historico. |
| A | `packaging/compatibility-references.json` | Registrar dos nombres NDD de compatibilidad aprobados por usuario; sin driver. |
| M | `packaging/package-windows.bat` | Copiar cinco componentes, limpiar rutas generadas con control; Paso 2: excluir .lib e inspeccionar imagen completa. |
| A | `packaging/tests/test-image.ps1` | Rechazar .lib añadida y copia NDD fuera de ruta autorizada; retirar archivos temporales. |
| A | `packaging/verify-image.ps1` | Revisar runtime/JAR/binarios; limitar excepción a ruta/hash/offsets exactos. |

Las pruebas y mocks anteriores se conservaron como texto excluido de CMake/CTest/Maven.
Los informes originales se archivaron íntegros antes de actualizar las rutas operativas.
NVML, IGCL, inventario DXGI, RAM y el cálculo GetSystemTimes/ReadCpuUsagePercent
siguen intactos contra el respaldo. Los IDs públicos 0..8 y los layouts no cambian:
CPU V2 56 B, GPU V2 104 B, inventario 296 B, TelemetryData 28 B. Los IDs retirados
2/3/6 están comentados como reservados/obsoletos; 4 nunca se implementó.

## 2. Regresión y artefacto comprobado

| Batería | Línea base Paso 0 | Resultado final Paso 2 |
|---|---|---|
| Helper | 63 | 63 aprobadas; 205 artefactos fuente verificados |
| CTest | 36 | 40 aprobadas; cuatro casos nuevos, ninguna omisión/fallo |
| Java | 279 | 278 aprobadas y una omitida, cero fallos/errores |

La omisión Java requiere una DLL antigua externa mediante MACE_LEGACY_DLL.
Se probaron controles JavaFX del HUD, estados, ABI/offsets, driver ausente/detenido,
no elevación, caducidad, PID/replay y reconexión. Los ceros marcadores no válidos
se muestran N/D; ceros GPU legítimos (uso, RPM, potencia) pueden seguir VALID.

Sin helper no vuelve ningún proveedor retirado: CPU temperatura/potencia y sensores
GPU quedan NO_PROVIDER. La capacidad total Windows se conserva solo con identidad
PCI única y caché válida. Una GPU ambigua no recibe cifras por posición. UnknownPower
produce potencia UNSUPPORTED/0, scope UNKNOWN y N/D, sin invalidar otras métricas válidas.

La app-image generada con package-windows.bat app-image --with-tests está en
`packaging/dist/MACE/`: **369 archivos, 147719124 bytes**. Incluye aplicación/JVM,
mace_native.dll, helper autocontenido, instalador PawnIO 2.2.0 intacto y avisos de
terceros. No contiene código fuente, scripts, pruebas, históricos, PDB ni .lib de
desarrollo. Se verificaron 24 exports, FFM y manifiestos UAC (MACE elevado; Preview
asInvoker). Se generó app-image; no un MSI/EXE instalador ni una instalación limpia.

| Artefacto | SHA-256 |
|---|---|
| mace_native.dll | 4D5E10F1CEF44A26BE4F2DA32955489613266AB33759496EECAD07B5E839EED8 |
| Mace.Lhm.Helper.exe | DEAA3EC9CBA4EEA670471382C45348A8044C4D01E5C52FC90AAF7A1449068C19 |
| PawnIO_setup.exe | 1F519A22E47187F70A1379A48CA604981C4FCF694F4E65B734AAA74A9FBA3032 |

La DLL procede del paquete nativo cuyo sourceCommit es 56132e2; las correcciones
posteriores verifican/empaquetan y documentan, sin alterar sus fuentes de sensores.
PawnIO conserva firma Authenticode válida y no se ejecutó su instalador.
La imagen usa el helper por su ruta predeterminada; ocho snapshots con su JVM/JAR/DLL
confirmaron GPU AMD y uso CPU/RAM. Máximo snapshot Java observado: 18.508 ms, una
medición local, sin garantía universal. La UI de esta imagen no se inspeccionó visualmente.

La evidencia y comandos completos permanecen en [Paso 2](RESULTADOS-1D-PASO2.md).
No se repitieron suites funcionales en este cierre: no hay cambios de runtime.

Comprobaciones de cierre ejecutadas: auditoría de referencias aprobada (181
coincidencias justificadas, ninguna inesperada), comprobador de vías protegidas
aprobado, revisión de privacidad de 60 documentos y 124 archivos generados, y
git diff --check sin errores. Las 60 entradas incluyen este informe.

## 3. Pérdida final de cobertura

“Por código” y “simulado” no equivalen a prueba física. La ruta heredada no era
fallback automático mientras LHM estaba activo; tras retirar esa ruta ya no puede
recuperarse al faltar el helper. Los registros de estados sí se comprobaron en tests.

| Plataforma / métrica | Alternativa retirada o conservada | Resultado tras 1D y evidencia |
|---|---|---|
| CPU Intel — temperatura | Retirada DTS/MSR opcional | LHM/PawnIO si cumple contrato; N/D sin acceso. **NO VERIFICADO en hardware Intel** |
| CPU Intel — potencia | Retirada RAPL alternativa | LHM paquete si válido; N/D sin acceso. **NO VERIFICADO en hardware Intel** |
| CPU Intel — uso | Windows conservado | Sin pérdida prevista; algoritmo intacto y probado. Hardware Intel **NO VERIFICADO** |
| CPU Intel — RPM | No había campo CPU en V2 | No aplica; no se incorpora ventilador de placa como ventilador CPU |
| CPU Intel — VRAM | No corresponde a CPU | No aplica; RAM Windows continúa por API independiente |
| CPU AMD — temperatura | Retirada Ryzen SDK | LHM; UI elevada y test -RequireCpu **confirmados por el usuario** antes de 1D. Imagen actual sin elevar: NO_PROVIDER correcto |
| CPU AMD — potencia | El proveedor retirado no la entregaba | LHM; confirmación elevada previa del usuario. No se perdió una potencia heredada inexistente; imagen sin elevar N/D |
| CPU AMD — uso | Windows conservado | Válido en la imagen local; no depende de PawnIO/helper |
| CPU AMD — RPM | No había campo CPU | No aplica |
| CPU AMD — VRAM | No corresponde a CPU | No aplica |
| GPU AMD local — temperatura | Retirada ADLX | LHM válido en RX 5500 XT de prueba; sin helper/sensor válido N/D |
| GPU AMD local — potencia | Retirada ADLX; no se afirma equivalencia eléctrica | LHM GPU_CHIP válido en PCI 1002:7340; únicamente con guardias de 1C |
| GPU AMD local — uso | Retirada ADLX | LHM válido en imagen; se observó 0 % legítimo. Sin helper N/D |
| GPU AMD local — RPM | Retirada ADLX | LHM válido en imagen; cero fan-stop cubierto por simulación. Sin helper N/D |
| GPU AMD local — VRAM | Retirada usada ADLX; DXGI conservado | LHM usada/total válidas localmente. Sin helper usada N/D; capacidad Windows solo si independiente y única |
| Otras Radeon — temperatura/uso/RPM/VRAM | Retirada ADLX | Dependen de sensores e identidad LHM. **NO VERIFICADO** por modelo; fallos quedan N/D |
| Otras Radeon — potencia | Restricción C aceptada | **N/D fuera de PCI 1002:7340**. Otra placa con ese ID también requiere guardias y validación; no se certifica toda Radeon |
| NVIDIA — temperatura | NVML conservado | Primaria intacta; respaldo LHM condicionado a sensores. Ambos **NO VERIFICADO físicamente** |
| NVIDIA — potencia | NVML conservado | Primaria intacta; respaldo LHM UnknownPower queda N/D. Ambos caminos físicos **NO VERIFICADO** |
| NVIDIA — uso | NVML conservado | Primaria intacta; respaldo condicionado. **NO VERIFICADO físicamente** |
| NVIDIA — RPM | NVML conservado, RPM separadas de % | N/D cuando no soportadas; no se convierte porcentaje a RPM. **NO VERIFICADO físicamente** |
| NVIDIA — VRAM | NVML y capacidad Windows conservados | Primaria intacta; respaldo físico **NO VERIFICADO** |
| GPU Intel — temperatura | IGCL conservado | Primaria intacta; respaldo/equivalencia LHM **NO VERIFICADO físicamente** |
| GPU Intel — potencia | IGCL diferencial conservado | Primaria intacta; respaldo UnknownPower N/D. **NO VERIFICADO físicamente** |
| GPU Intel — uso | IGCL diferencial conservado | Primaria intacta; respaldo físico **NO VERIFICADO** |
| GPU Intel — RPM | IGCL conservado cuando existe sensor | iGPU sin ventilador puede quedar N/D. **NO VERIFICADO físicamente** |
| GPU Intel — VRAM | IGCL/Windows conservados | No se presupone VRAM dedicada en iGPU. **NO VERIFICADO físicamente** |
| Otras GPU — temperatura/potencia/uso/RPM | Sin proveedor dedicado implementado | No se añade soporte: N/D si no existe proveedor |
| Otras GPU — VRAM | DXGI capacidad conservado | Usada N/D sin proveedor; capacidad condicionada al estado/identidad |

NVML/IGCL se seleccionan por conjunto del adaptador, no se completan individualmente
sus métricas ausentes con LHM mientras el primario aporte sensores válidos.
**No hay demostración de equivalencia general tras eliminar las alternativas.**

## 4. Coincidencias aceptadas

El [TSV](../docs/REFERENCIAS-ACEPTADAS-1D.tsv) enumera cada coincidencia textual
con archivo, línea, columna y motivo. Incluye IDs reservados, pruebas ABI, históricos,
informes y la política aprobada. El propio TSV es salida y no se analiza a sí mismo.
Al comenzar este cierre tenía 166 filas de coincidencias; después de incorporar
este informe contiene 181. Se regeneró y se volvió a revisar por privacidad.

Excepción binaria **aprobada expresamente por el usuario**: únicamente
`app/lhm/RAMSPDToolkit-NDD.dll` 1.4.2, SHA-256
42C1CCAF90592542EC64E801AE599ED803BB98F4039B2ABB7119CB8EA2087CBA.

| Offset ASCII | Símbolo | Motivo |
|---|---|---|
| 105819 | DriverImplementation.WinRing0 | Campo de enum de compatibilidad; sin implementación de driver |
| 127562 | IWinRing0Driver | Interfaz vacía de compatibilidad; sin métodos ni recursos del driver |

La excepción está fijada por ruta/hash/offsets, no permite otros archivos ni una
implementación activa. No se modificó la DLL de terceros. El verificador rechazó
los mismos bytes copiados a otra ruta y una .lib temporal; la imagen final volvió
a aprobar tras retirar esos archivos de prueba. Las dos referencias no se ocultan
ni se presentan como cero coincidencias literales.

## 5. Revisión de rutas y datos personales

### Alcance y método

Se revisaron el TSV completo y los 59 documentos Markdown/texto/TSV versionados
existentes al iniciar este cierre, incluidos informes helper, documentación operativa,
históricos y avisos. Se revisaron además 124 informes/archivos generados seleccionados
de Paso 0, tramos 1D, Paso 2, Surefire y evidencia física del helper. También se
revisa este informe antes de entregarlo. No es una auditoría de todo el disco, del
historial Git completo ni de datos fuera de MACE. Una búsqueda por patrones requiere
interpretar contexto; no garantiza ausencia absoluta de información sensible.

Patrones: perfiles Windows/Unix, hostname, propiedades user.name/user.home, email,
posibles IP/MAC, LUID medido, instancia PCI, PID/procesos y descripción del hardware.
Se descartaron como falsos positivos win-x64, versiones como 0.9.6.0 y datos sintéticos.
No se copiaron nombres reales de usuario/equipo ni rutas de perfil reales a este informe.

**TSV:** sus columnas son rutas relativas al repositorio, línea/columna, nombre de
proveedor y motivo. No se encontraron rutas absolutas de perfil, nombres de equipo,
correos personales ni identificadores físicos en sus campos. Citar una fila de un
informe no copia el contenido sensible de ese informe al TSV.

**Informes versionados:** no se hallaron nombres de usuario/equipo ni perfiles reales
en lo revisado. Sí hay configuración técnica local (CPU/GPU, PCI, versiones, mediciones
y fechas). No son una identidad personal por sí solos, pero pueden correlacionarse con
el equipo. La tabla siguiente identifica sus ubicaciones sin volver a publicar datos
locales adicionales.

| Documento / líneas revisadas | Dato o clasificación | Tratamiento propuesto |
|---|---|---|
| DEV4_HANDOFF.md:27 | Modelo GPU de la ejecución local | En copia pública, valorar etiqueta de equipo de prueba; mantener evidencia técnica necesaria |
| helper/RESULTADOS-1A.md:23 | CPU/GPU, cuatro campos PCI, lecturas crudas y permisos | Mantener fuente original; revisar una copia antes de publicación |
| helper/RESULTADOS-1B.md:149 | Modelos y mediciones locales sin elevar | Identificar como equipo de prueba, no equivalencia general |
| helper/RESULTADOS-1C.md:34,190 | PCI completo/local y modelo GPU | Mantener vendor/device para allowlist; revisar subsystem/revision si se desea minimizar huella |
| helper/COBERTURA-AMD-POTENCIA.md:7,64,90,125,153,177,211,250,257 | Hardware local y clasificación de evidencia | Conservar datos necesarios para cobertura; no añadir identidad del propietario |
| helper/PROTOCOL.md:49 | Identidad técnica soportada, no serial único | Conservar como requisito del protocolo/política |
| helper/RESULTADOS-1D-PASO1.md:53,132 | PCI permitido y modelos como límite de cobertura | Conservar contexto técnico, sin rutas personales |
| helper/RESULTADOS-1D-PASO2.md:112,115 | Modelo CPU/GPU de la prueba local | Conservar separación de medición propia/confirmación del usuario |
| native/docs/HARDWARE_COMPATIBILITY.md:8; PHASE0.md:19; PHASE2.md:16 | Modelos/PCI y observaciones locales | Revisar copia pública conservando alcance real |
| docs/historico/native/pre1D-native__docs__AMD_CPU_TEMPERATURE.md:17 | Modelo CPU y temperaturas históricas | Preservar original; si se publica, separar identidad del equipo y evidencia |
| docs/historico/native/pre1D-native__docs__HARDWARE_COMPATIBILITY.md:9 | Modelos y PCI históricos | Misma regla; no es prueba actual |
| docs/historico/native/pre1D-native__docs__PHASE2.md:5,19,21,23 | Configuración y mediciones históricas | Misma regla |
| docs/historico/native/pre1D-native__docs__PHASE5.md:15,21 | Configuración/mediciones históricas | Misma regla |
| docs/historico/native/pre1D-native__docs__TEAM_DIAGNOSTIC_GUIDE.md:28 | Ruta con TU_USUARIO, plantilla | No contiene el nombre real; puede mantenerse como ejemplo |
| docs/historico/pruebas-retiradas/pre1D-MockSensorsAdapter.java.txt:212 | Modelo en fixture sintético | No tratarlo como identificación física del usuario |
| packaging/third-party/*THIRD-PARTY-NOTICES.TXT | 28 ocurrencias de correos de autores/atribuciones públicas en cuatro textos | Preservar avisos/licencias; no son cuentas locales del equipo |

Las líneas corresponden a los informes anteriores, que no se modificaron para anonimizar.
Este informe también conserva modelos/PCI necesarios en la tabla de cobertura y
comparte esa clasificación de datos técnicos locales.

### Informes generados con datos locales (no versionados)

| Archivo o grupo revisado | Hallazgo | Propuesta antes de compartir |
|---|---|---|
| native/build/1d-paso2/ctest-final.xml y copia bajo compiled/native/build/1d-paso2/ | Hostname real; rutas de ejecución | Sustituir hostname por <EQUIPO>; usar rutas <REPO>; conservar resultados |
| app/target/surefire-reports/TEST-*.xml (37 archivos) | user.name, user.home, rutas, classpath y otras propiedades; integración incluye datos locales | Eliminar propiedades ambientales no necesarias o producir copia saneada; mantener estados/resultados |
| native/build/1d-paso0/{helper-tests,native-tests,java-tests}.log | Rutas de usuario/workspace y herramienta; Java registra hardware/LUID | Sustituir raíces por <REPO>/<JDK>/<TOOLCHAIN>; revisar líneas de hardware |
| native/build/1d-{1,2,3,4}/{helper,native,java}.log | Rutas de usuario y artefactos; pruebas físicas Java con datos del dispositivo | Misma regla; no compartir carpetas completas |
| native/build/1d-paso2/{helper,native-package,ctest-final,app-image}.log | Rutas locales, herramientas y diagnóstico de integración | Misma regla; preservar hashes/versiones y resultado |
| native/build/1d-paso0/hardware-diagnostic.log | PID, procesos, rutas instaladas, tiempos, memoria, hardware y LUID | Retirar procesos/PID y rutas ajenas al ensayo; conservar solo métricas necesarias |
| native/build/1d-paso2/image-telemetry.log | Ruta de DLL y lecturas/modelo local | Sustituir ruta por <IMAGEN>; publicar solo datos necesarios |
| helper/artifacts/latest.txt | Ruta absoluta del helper publicado | No distribuir; sustituir en una copia por ruta relativa o marcador |
| helper/artifacts/evidence-1c/amd-power-report.txt | Instancia PnP/PCI del equipo y reporte del controlador | Conservar vendor/device requeridos; sustituir sufijo de instancia por <INSTANCIA_PCI> |
| native/build/vs2026-lhm1b/lhm-live-*.txt | Mediciones locales y estado de sensores | Mantener identificador anónimo de equipo; revisar contexto antes de publicar |
| packaging/dist/MACE-native-report.txt | Ruta absoluta de la DLL/hash de referencia | No incluir en producto; usar <PAQUETE> en copia compartida |
| native/build/1d-paso2/compiled/CMakeCache.txt, CMakeFiles/ y Testing/ | Rutas del compilador/workspace y metadatos de máquina | Caches de desarrollo, no subir ni distribuir |
| native/build/1d-paso2/image-files.json y image-verification.json | Rutas relativas/hash/offsets, sin perfil local | Pueden conservarse para integridad; no prueban ausencia de datos embebidos en todos los binarios |

Los archivos generados permanecen ignorados por Git y fuera del producto; que estén
ignorados no significa que se hayan eliminado o anonimizado. La imagen no contiene
estos logs/informes internos. No se certifica aquí ausencia de rutas embebidas en
información de depuración PE u otros metadatos de todos los binarios de terceros.

**Plan propuesto, no ejecutado:** conservar originales privados y generar copias
para publicar; reemplazar perfiles por <USUARIO>/<REPO>, hostname por <EQUIPO>,
instancia PCI/LUID/PID por marcadores cuando no sean necesarios y retirar listas de
procesos/propiedades ambientales. Mantener relaciones entre muestras con identificadores
anónimos, versiones, hashes, estados y PCI mínimo para reproducibilidad. No reemplazar
indiscriminadamente cifras: se perderían pruebas de cobertura/validez. Revisar esas
copias manualmente y mantener intactas atribuciones de terceros. Anonimizar originales
o reescribir historial requiere aprobación separada; no se hizo.

La evidencia local de la revisión es native/build/1d-paso3/privacy-audit.json:
archivo/línea/columna/categoría, sin copiar valores personales. Se vuelve a comprobar
el TSV generado al cerrar el informe. No se encontraron IP/MAC personales entre las
candidatas revisadas; los cuatro grupos numéricos detectados correspondían a versiones.

## 6. Recuperación y reversión por tramo

No se aplicó ninguna reversión ni se cambió de rama. Se comprobaron solo parches
inversos con git apply --reverse --check contra 4f283d9. Los cuatro tramos devuelven
no aplicable sin ajustes: comparten archivos y documentación modificados después.
Eso no ejecuta git revert ni demuestra si su merge de tres vías resolvería todo.
Una reversión aislada compilada y probada sigue **NO VERIFICADO**. No se promete
restaurar un proveedor con un único comando sin resolver dependencias de integración.

### Recuperar el estado anterior completo sin borrar historial

Desde la raíz, con git status --short vacío, elegir un nombre de rama libre:

```powershell
git switch -c recuperar-pre-1D 1b62000eddd0819258ceb33bf64eed69c2f09d44
```

Solo restaura archivos versionados. Las salidas ignoradas build/dist y la aplicación
instalada no retroceden con Git: hay que reconstruir y fijar los hashes de esa revisión.
No mezclar la DLL/helper/imagen actuales con fuentes recuperadas ni desinstalar PawnIO
para hacer una reversión de código. El ZIP ajeno a la implementación no está en el commit.

### Revertir un tramo conservando historia

Crear primero una rama de revisión desde un árbol limpio:

```powershell
git switch -c revision-reversion-1D
```

Ejecutar **solo el comando del tramo deseado**, no toda la tabla automáticamente:

| Tramo | Comando | Dependencias y revisión necesaria |
|---|---|---|
| 1D-1 | git revert 44c6609 | API interna CPU, trabajador y estados del receptor fueron cambiados después; revisar contexto y NO_PROVIDER |
| 1D-2 | git revert aad1fbd | Restaurar coherentemente fuente/cabecera/CMake/comprobador y opciones eliminadas en 1D-4; SDK externo requerido si se habilita |
| 1D-3 | git revert 2868b7f | Revisar despacho, CMake/mocks y opciones de 1D-4; SDK externo requerido si se habilita |
| 1D-4 | git revert 9911cf5 | Paso 2 cambió empaquetado/auditoría; no restaurar parámetros que apuntan a fuentes ausentes sin revisar tramos previos |

Si hay conflictos: inspeccionar git status, resolver por archivo, git add de archivos
resueltos y git revert --continue. Para abandonar la operación: git revert --abort.
Si la reversión se completó pero falla la regresión, revertir el commit de reversión
en esa rama o descartar esa rama tras volver a la principal; no usar reset --hard.
Después de cada reversión, reconstruir y ejecutar helper/CTest/Java y verificación
de paquete aplicables a esa versión. Los verificadores actuales rechazan cargadores
retirados: no basta restaurar la fuente y declarar válida la imagen sin revisión.

### Deshacer toda la retirada con commits inversos

Ruta recomendada cuando se quiere preservar la historia de la rama: revertir primero
correcciones posteriores y luego tramos en orden inverso, revisando cada resultado:

```powershell
git revert 4f283d9
git revert 56132e2
git revert 7e6c532
git revert 9911cf5
git revert 2868b7f
git revert aad1fbd
git revert 44c6609
```

Son instrucciones propuestas, **no ejecutadas**. El cierre documental/TSV posterior
puede requerir resolver conflictos aunque no cambie el runtime. Para una recuperación
exacta de fuentes se prefiere la rama del respaldo. No se ejecutaron etiquetas ni
ramas de archivo; los comandos opcionales siguen documentados en docs/historico/README.md.

## 7. NO VERIFICADO y cierre

- **CPU Intel**: temperatura/potencia físicas y equipo real, aunque el uso Windows y ABI se conservan.
- **NVIDIA, NVML principal y respaldo LHM**, incluidas métricas/unidades/actividad en hardware real.
- **Intel GPU/IGCL** y respaldo/equivalencia física LHM.
- **PawnIO ausente en equipo limpio**: estados simulados comprobados, no instalación limpia física.
- HVCI con el conjunto actual, instalación silenciosa limpia, reinicio/código 3010 y desinstalación compartida.
- Otras Radeon/variantes 7340, APU, híbridos/multi-GPU, suspensión, hotplug y cambios de controlador.
- Calibración eléctrica externa y éxito/frescura individual de cada lectura interna no certificable por LHM1.
- UI elevada de esta app-image inspeccionada por el agente; se conserva la confirmación previa del usuario de CPU elevada.
- Reversiones aisladas realmente aplicadas, compiladas y probadas; solo se revisó aplicabilidad de parches.
- Auditoría de todo el historial Git y ausencia absoluta de información sensible en binarios/metadatos no cubiertos.

Firma MACE, licencia y revisión legal de redistribución de PawnIO/módulos/runtime
siguen pendientes. El inventario de avisos no constituye autorización legal.

**Subfase 1D cerrada en el alcance aprobado y con esos límites. La retirada no
demuestra equivalencia en plataformas sin validar.** No se inicia otra fase sin
instrucción del usuario. Los originales con datos locales permanecen sin anonimizar.
