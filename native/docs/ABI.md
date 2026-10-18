# Contrato de integración native/FFM

Estado: fase 5. La DLL exporta los siete símbolos heredados, `CP_GetAbiInfo`, cuatro funciones V2 de telemetría, seis de procesos, cinco del anillo y el cierre del mapeo heredado: **24 símbolos**. `CP_GetAbiInfo` devuelve ABI **1.4** en un registro de estructura versión **2**. El Java actual solo consume la interfaz heredada.

## Plataforma y tipos

- Objetivo: Windows 10/11 x64, MSVC, C17; proceso y DLL de 64 bits. Los GUID antiguos en `packaging/app.manifest` no acreditan soporte en Windows 7/8/8.1.
- `int`/`uint32_t`/`float`: 4 bytes; `uint64_t`/puntero: 8 bytes; `wchar_t`: 2 bytes UTF-16LE. Enteros y floats siguen el orden nativo little-endian de Windows x64.
- Llamadas C exportadas con `__declspec(dllexport)` y nombre sin decoración x64. Las cabeceras usan `extern "C"` al incluirse desde C++.
- `#pragma pack(push, 8)` está en ambas generaciones de registros. La prueba `abi_layouts` falla al cambiar tamaño, alineación u offsets.
- Todo buffer pasado a la DLL pertenece al llamador hasta que la función retorna. Las funciones V2 de telemetría reciben capacidad explícita en bytes para registros individuales y en cantidad de elementos para el inventario. No guardan esos punteros y no escriben parcialmente cuando la capacidad es insuficiente.

## Interfaz heredada que debe conservarse

La nueva consulta de identificación es `MaceStatus CP_GetAbiInfo(MaceAbiInfoV2* out_info, uint32_t out_capacity, uint32_t* out_required_size)`. Se puede llamar antes de `CP_InitializeTelemetry` y no carga sensores. Requiere `out_required_size`; siempre escribe allí `64` cuando el puntero es válido. Con `out_info=NULL, out_capacity=0` devuelve `MACE_E_BUFFER_TOO_SMALL (-2)` para consultar el tamaño. Si la capacidad es inferior a 64, no escribe ningún byte de `out_info`; si el puntero es nulo pese a capacidad suficiente devuelve `MACE_E_INVALID_ARGUMENT (-1)`. En éxito escribe solo los 64 bytes definidos, inicializa los reservados a cero y devuelve `MACE_OK (0)`. Se permiten buffers mayores; sus bytes adicionales permanecen intactos.

`abi_major=1, abi_minor=4` significa que la interfaz heredada está intacta y se añadieron telemetría, procesos y extensiones V2. `build_major/minor/patch=1.4.2` identifica esta DLL. `implemented_capabilities` anuncia además `MACE_CAP_PROCESS_SAMPLES`, `MACE_CAP_WINDOW_RESPONSIVENESS` y `MACE_CAP_BLACKBOX_BUFFER`; un bit indica código disponible, no acceso a todos los procesos ni sensores del equipo. Los 24 símbolos exportados se comprueban al cargar exactamente la DLL distribuida.

| Símbolo | Firma C | Resultado actual |
|---|---|
| `CP_InitializeTelemetry` | `int (void)` | Devuelve `0` aunque falten proveedores opcionales. Carga CPU, NVML, IGCL, ADLX y Ryzen Master Monitoring SDK si se compilaron los adaptadores respectivos. |
| `CP_ShutdownTelemetry` | `void (void)` | Espera las lecturas activas, reinicia las referencias de muestreo y cierra todos los mapeos de ambas generaciones. Descarga proveedores salvo la sesión ADLX, que permanece hasta el fin del proceso por una limitación comprobada del controlador AMD. |
| `CP_PollTelemetry` | `int (TelemetryData*)` | `-1` si el puntero es nulo; `0` en otro caso, aun si faltan sensores. |
| `CP_EnumerateUserlandProcesses` | `int (ProcessData*, uint32_t max_count, uint32_t* out_count)` | `-1` si los argumentos son inválidos; `0` si la enumeración termina; código Win32 positivo si falla el snapshot. Puede truncar al llegar a `max_count` sin indicarlo; comparte el filtro de seguridad con V2. |
| `CP_TerminateProcess` | `int (uint32_t pid)` | `-1` para PID 0/4; `0` si termina; `ERROR_ACCESS_DENIED` para objetivo protegido/propio y código Win32 positivo en otros errores. Revalida el objetivo en el mismo handle, pero no puede detectar que un PID cambió desde una enumeración previa. Solo se invoca por petición manual. |
| `CP_CheckProcessResponsiveness` | `int (uint32_t pid)` | `1` si alguna ventana superior visible aparece colgada; `0` para otros casos. No expone distinciones de V2. |
| `CP_InitSharedTelemetryBuffer` | `void* (const wchar_t* mapping_name, uint32_t buffer_size)` | Vista heredada o `NULL`. Rechaza nombres ya existentes y tamaños mayores de 16 MiB. |
| `CP_CloseSharedTelemetryBuffer` | `MaceStatus (void* view)` | Libera exactamente una vista heredada registrada. Puntero ajeno o doble cierre: `MACE_E_INVALID_ARGUMENT`. |
| `CP_EnumerateGpuAdapters` | `MaceStatus (MaceGpuAdapterInfoV2*, uint32_t capacity, uint32_t* count)` | Enumera GPU físicas DXGI. Un buffer nulo y capacidad 0 consulta el número requerido. |
| `CP_PollCpuTelemetryV2` | `MaceStatus (MaceCpuTelemetryV2*, uint32_t capacity)` | CPU detectada por CPUID, uso Windows y sensores Intel opcionales. |
| `CP_PollGpuTelemetryV2` | `MaceStatus (const MaceAdapterLuidV2*, MaceGpuTelemetryV2*, uint32_t capacity)` | Consulta un adaptador específico por LUID y devuelve estados por métrica. |
| `CP_PollSystemMemoryV2` | `MaceStatus (MaceSystemMemoryV2*, uint32_t capacity)` | Memoria física total y disponible de Windows. No requiere inicializar telemetría. |
| `CP_EnumerateUserlandProcessesV2` | `MaceStatus (MaceProcessInfoV2*, uint32_t capacity, uint32_t* required, uint32_t* skipped, uint32_t* win32_error)` | Devuelve identidades y estados de memoria. La capacidad es cantidad de registros; con buffer corto informa la cantidad necesaria y no escribe entradas. `skipped` cuenta fallos de acceso/consulta, no exclusiones deliberadas por política. |
| `CP_GetProcessIdentityV2` | `MaceStatus (uint32_t pid, MaceProcessIdentityV2*, uint32_t capacity_bytes, uint32_t* win32_error)` | Obtiene PID + FILETIME de creación. Puede consultarse incluso para el proceso propio; no concede permiso para terminarlo. |
| `CP_GetProcessImagePathV2` | `MaceStatus (const MaceProcessIdentityV2*, wchar_t*, uint32_t capacity_chars, uint32_t* required_chars, uint32_t* win32_error)` | Ruta UTF-16 completa; `required_chars` incluye NUL. Verifica la identidad y la política antes de copiar. Un buffer corto no se modifica. |
| `CP_TerminateProcessV2` | `MaceStatus (const MaceProcessIdentityV2*, uint32_t* win32_error)` | Rechaza PID reutilizado, proceso propio, crítico, nombre/ruta protegidos o imposibilidad de verificarlos. La comprobación y `TerminateProcess` usan el mismo handle. |
| `CP_SampleProcessV2` | `MaceStatus (const MaceProcessIdentityV2*, MaceProcessSampleV2*, uint32_t capacity_bytes, uint32_t* win32_error)` | Muestra puntual de Working Set y Private Bytes con identidad y validez. Rechaza identidad reutilizada. |
| `CP_CheckProcessResponsivenessV2` | `MaceStatus (const MaceProcessIdentityV2*, MaceResponsivenessV2*, uint32_t capacity_bytes)` | Examina ventanas superiores visibles del escritorio accesible y distingue sin ventana, respuesta, cuelgue, proceso finalizado y error. |
| `CP_BlackboxCreateV2` | `MaceStatus (const wchar_t* name, uint32_t capacity_records, uint64_t* handle, uint32_t* win32_error)` | Crea un anillo de 1–65 536 registros; `name=NULL` crea uno privado. Un nombre existente devuelve `MACE_E_BUSY` sin modificarlo. |
| `CP_BlackboxGetStatsV2` | `MaceStatus (uint64_t handle, MaceBlackboxHeaderV2*, uint32_t capacity_bytes)` | Devuelve encabezado, secuencias y estadísticas calculados desde el estado privado de la DLL. |
| `CP_BlackboxAppendV2` | `MaceStatus (uint64_t handle, uint32_t type, const void* payload, uint32_t payload_size, uint64_t* sequence)` | Escribe un registro completo de tipo CPU, GPU, proceso o memoria del sistema. Requiere versión/tamaño V2 exactos. |
| `CP_BlackboxReadV2` | `MaceStatus (uint64_t handle, uint64_t from_sequence, MaceBlackboxRecordV2*, uint32_t capacity_records, uint32_t* required)` | Copia los registros conservados desde una secuencia; cero significa desde el más antiguo. Buffer corto: informa todos los necesarios y no escribe registros parciales. |
| `CP_BlackboxCloseV2` | `MaceStatus (uint64_t handle)` | Cierra el token y libera mapeo/vista. Token inválido o doble cierre: `MACE_E_INVALID_ARGUMENT`. |

Los códigos `-1` y Win32 positivos pertenecen a la API heredada. **No** se reinterpretan como los estados MACE negativos de V2. Las funciones de procesos no requieren `CP_InitializeTelemetry`. Las lecturas CPU/GPU, la inicialización, el apagado y la creación de mapeos se ordenan con un bloqueo de ciclo de vida; las operaciones del anillo y las vistas heredadas tienen bloqueos propios. Véase [PHASE5.md](PHASE5.md) para el alcance exacto.

| `ProcessData` (1584 B, alineación 8) | Offset | Unidad/representación |
|---|---:|---|
| `pid` | 0 | ID de proceso, `uint32_t` |
| `name[260]` | 4 | UTF-16, terminación NUL si cabe |
| `path[520]` | 524 | UTF-16, máximo heredado; `longPathAware` no lo amplía |
| relleno implícito | 1564 | 4 bytes |
| `working_set_bytes` | 1568 | bytes residentes |
| `private_bytes` | 1576 | bytes privados comprometidos; no se presupone `private <= working_set` |

| `TelemetryData` (28 B, alineación 4) | Offset | Unidad/representación |
|---|---:|---|
| `cpu_temp` | 0 | °C, si está disponible |
| `cpu_watts` | 4 | W, si está disponible |
| `gpu_temp` | 8 | °C, GPU seleccionada por la DLL heredada |
| `gpu_watts` | 12 | W |
| `gpu_fan_rpm` | 16 | RPM reales del proveedor seleccionado o cero; nunca porcentaje |
| `is_gpu_available` | 20 | Vale 1 si la GPU heredada aporta temperatura válida |
| `is_cpu_sensor_available` | 21 | Vale 1 si temperatura o potencia CPU son válidas |
| `_padding[2]` | 22 | Reservado, siempre cero |
| `cpu_usage` | 24 | Porcentaje global [0,100]; primera muestra de calentamiento = 0 |

`BlackboxSharedHeader` mide 16 B, alineación 4: `magic_header` +0 (`0x4D414345`), `ring_buffer_capacity` +4 (bytes), `write_cursor` +8 y `record_count` +12. El nombre y tamaño de estos registros **no cambiarán**. `NativeLayouts.java` usa exactamente los mismos offsets.

La DLL escribe los 28 bytes de `TelemetryData` en cada consulta, incluidos los reservados, sin alterar el layout. Un dato sin sensor se codifica como cero más el indicador heredado. V2 distingue los estados por métrica. La API heredada selecciona la primera GPU DXGI con temperatura válida; la selección explícita por LUID corresponde a V2.

## Registros V2 definidos en fase 0

Todos tienen `struct_size` y `version=2` cuando son registros de salida; la DLL solo escribirá un registro si el llamador ofrece la capacidad requerida. `CP_GetAbiInfo` comunica el tamaño requerido en `out_required_size`; el inventario GPU comunica la cantidad en `out_count`. Los sondeos de un solo registro usan el tamaño publicado de la estructura. Un buffer corto no se escribe parcialmente. Los campos `reserved` son cero. Una métrica se usa **solo** cuando `state == MACE_METRIC_VALID`; `0` con otro estado no significa lectura física de cero.

- `MaceAbiInfoV2` (64 B): versiones ABI/compilación, tamaños heredados, tamaño de puntero y `wchar_t`, capacidades **implementadas**. La presencia del bit no afirma que el sensor del equipo actual esté disponible.
- `MaceGpuAdapterInfoV2` (296 B): LUID, IDs PCI/DXGI, flags e índice de enumeración y descripción UTF-16. El LUID e índice se consultan de nuevo al iniciar; no son claves persistentes entre reinicios. Los adaptadores de software se excluyen del inventario.
- `MaceCpuTelemetryV2` (56 B): fabricante detectado, proveedor, tiempo monotónico, uso/temperatura/potencia con estados separados y ámbito de potencia.
- `MaceGpuTelemetryV2` (104 B): LUID del adaptador consultado, proveedor y estados independientes de temperatura, potencia, uso, RPM, porcentaje de ventilador y memoria de vídeo. Jamás se mezclan valores de varias GPU.
- `MaceSystemMemoryV2` (48 B): memoria total y disponible del sistema con estados separados.
- `MaceProcessIdentityV2` (16 B): PID más fecha de creación Windows FILETIME en intervalos de 100 ns UTC. Deben compararse ambos para rechazar reutilización del PID.
- `MaceProcessInfoV2` (576 B): identidad, nombre UTF-16, Working Set y Private Bytes con estados independientes. Su ruta completa se solicita aparte y no se trunca en este contrato.
- `MaceProcessSampleV2` (64 B): identidad, marca monotónica, Working Set y Private Bytes con estados separados.
- `MaceResponsivenessV2` (40 B): identidad, número de ventanas examinadas/colgadas y resultado explícito; no confunde ausencia de ventanas con capacidad de respuesta.
- `MaceBlackboxHeaderV2` (48 B) y `MaceBlackboxRecordV2` (160 B): capacidad en registros, secuencia, sobrescrituras y carga acotada de 128 B. Las estructuras CPU/GPU/proceso/sistema definidas aquí caben en esa carga. La caja negra en RAM no equivale al archivo persistente que corresponde al sink Java.

`timestamp_monotonic_ns` se deriva de `GetTickCount64` (resolución de milisegundos) y sirve para intervalos, no para fecha de calendario. El tiempo de creación de proceso procede de `GetProcessTimes` y sigue el formato FILETIME UTC. Los valores de potencia declaran ámbito (`CPU_PACKAGE`, `CPU_RAIL`, `APU_SOC`, `GPU_BOARD` o `GPU_CHIP`) para evitar sumar dos lecturas que se solapan.

### Errores, disponibilidad y funciones previstas

`include/mace_status.h` fija retornos `MACE_OK=0`, errores negativos y estados de métrica. Los errores de permiso, proceso finalizado, PID reutilizado, buffer insuficiente y sensor ausente son distintos. Una función V2 que falle por Win32 devolverá `MACE_E_WINDOWS_ERROR` y, cuando corresponda, un parámetro de salida `uint32_t win32_error` capturado **antes** de cerrar el handle. Nunca se sobrecargará un float con un código de error.

`CP_GetAbiInfo`, telemetría V2, inventario, procesos V2 y anillo temporal ya están disponibles. Java debe comprobar ABI/capacidades antes de adoptar las funciones nuevas, manteniendo los siete símbolos heredados.

El buffer de procesos y las estructuras de salida son propiedad del llamador. El mapeo V2 pertenece a la DLL y expone un token opaco, escritura y lectura por copia, estadísticas y cierre; Java no libera ni retiene punteros internos. La DLL serializa operaciones por anillo dentro del proceso. La capacidad, los índices y los contadores del anillo son privados a la DLL; el encabezado del mapeo es una copia informativa y nunca controla los accesos. Un lector detecta pérdidas comparando `sequence` y `overwritten_records`; si pide una secuencia ya sobrescrita, la lectura comienza en la más antigua conservada. El anillo no es un archivo histórico persistente. El símbolo heredado devuelve una vista que solo puede cerrarse mediante `CP_CloseSharedTelemetryBuffer`; todo uso del puntero debe terminar antes de ese cierre o de `CP_ShutdownTelemetry`.

## Permisos y límites

El manifiesto `packaging/app.manifest` pertenece al launcher `MACE.exe`; la DLL hereda el token del anfitrión. La elevación permite pedir derechos adicionales, pero no da acceso automático a MSR ni a procesos protegidos. Los tests del módulo corren sin elevarse; la prueba de terminación deberá crear y cerrar solo procesos auxiliares controlados.
