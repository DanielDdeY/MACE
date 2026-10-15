# Contrato de integración native/FFM

Estado: fase 1. La DLL exporta los siete símbolos heredados y `CP_GetAbiInfo`; este último devuelve ABI **1.1** en un registro de estructura versión **2**. Los demás símbolos V2 para telemetría, procesos y extensiones siguen reservados para fases posteriores. El Java actual solo consume la interfaz heredada.

## Plataforma y tipos

- Objetivo: Windows 10/11 x64, MSVC, C17; proceso y DLL de 64 bits. Los GUID antiguos en `packaging/app.manifest` no acreditan soporte en Windows 7/8/8.1.
- `int`/`uint32_t`/`float`: 4 bytes; `uint64_t`/puntero: 8 bytes; `wchar_t`: 2 bytes UTF-16LE. Enteros y floats siguen el orden nativo little-endian de Windows x64.
- Llamadas C exportadas con `__declspec(dllexport)` y nombre sin decoración x64. Las cabeceras usan `extern "C"` al incluirse desde C++.
- `#pragma pack(push, 8)` está en ambas generaciones de registros. La prueba `abi_layouts` falla al cambiar tamaño, alineación u offsets.
- Todo buffer pasado a la DLL pertenece al llamador hasta que la función retorna. Las funciones V2 propuestas recibirán capacidad explícita, nunca guardarán ese puntero y devolverán tamaño/cantidad requerida si es insuficiente.

## Interfaz heredada que debe conservarse

La nueva consulta de identificación es `MaceStatus CP_GetAbiInfo(MaceAbiInfoV2* out_info, uint32_t out_capacity, uint32_t* out_required_size)`. Se puede llamar antes de `CP_InitializeTelemetry` y no carga sensores. Requiere `out_required_size`; siempre escribe allí `64` cuando el puntero es válido. Con `out_info=NULL, out_capacity=0` devuelve `MACE_E_BUFFER_TOO_SMALL (-2)` para consultar el tamaño. Si la capacidad es inferior a 64, no escribe ningún byte de `out_info`; si el puntero es nulo pese a capacidad suficiente devuelve `MACE_E_INVALID_ARGUMENT (-1)`. En éxito escribe solo los 64 bytes definidos, inicializa los reservados a cero y devuelve `MACE_OK (0)`. Se permiten buffers mayores; sus bytes adicionales permanecen intactos.

`abi_major=1, abi_minor=1` significa que la interfaz heredada está intacta y se añadió esta consulta; **no** significa que ya se implementaron todas las funciones descritas en los tipos V2. `build_major/minor/patch=1.1.0` identifica la versión de esta DLL. El campo `implemented_capabilities` anuncia solo `MACE_CAP_CPU_USAGE` en esta fase. Un bit de capacidad indica implementación, no disponibilidad física del sensor en un equipo concreto. Los siete símbolos originales se comprueban al cargar exactamente la DLL distribuida.

| Símbolo | Firma C | Resultado actual |
|---|---|
| `CP_InitializeTelemetry` | `int (void)` | Devuelve `0` aunque NVML o sensores CPU no estén disponibles. Carga proveedores opcionales. |
| `CP_ShutdownTelemetry` | `void (void)` | Descarga NVML/CPU. |
| `CP_PollTelemetry` | `int (TelemetryData*)` | `-1` si el puntero es nulo; `0` en otro caso, aun si faltan sensores. |
| `CP_EnumerateUserlandProcesses` | `int (ProcessData*, uint32_t max_count, uint32_t* out_count)` | `-1` si los argumentos son inválidos; `0` si la enumeración termina; código Win32 positivo si falla el snapshot. Puede truncar al llegar a `max_count` sin indicarlo. |
| `CP_TerminateProcess` | `int (uint32_t pid)` | `-1` para PID 0/4; `0` si termina; código Win32 positivo si falla. Solo se invoca por petición manual de la aplicación. |
| `CP_CheckProcessResponsiveness` | `int (uint32_t pid)` | `1` para la primera ventana visible colgada; `0` para ventana que responde **o sin ventana**. |
| `CP_InitSharedTelemetryBuffer` | `void* (const wchar_t* mapping_name, uint32_t buffer_size)` | Vista compartida o `NULL`. No hay hoy API de cierre: defecto pendiente de fase 4. |

Los códigos `-1` y Win32 positivos pertenecen a la API heredada. **No** se reinterpretan como los estados MACE negativos de V2. Las funciones de procesos no requieren `CP_InitializeTelemetry`; la aplicación debe coordinar su propio ciclo de vida y evitar consultas concurrentes con `CP_ShutdownTelemetry` hasta que la fase 5 añada sincronización nativa.

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
| `gpu_fan_rpm` | 16 | **Nombre defectuoso:** el código actual escribe porcentaje NVML, no RPM |
| `is_gpu_available` | 20 | El código actual señala inicialización NVML, no validez de cada lectura |
| `is_cpu_sensor_available` | 21 | El código actual señala proveedor MSR listo, no validez de temperatura y potencia por separado |
| `_padding[2]` | 22 | Reservado; el código actual no lo inicializa siempre |
| `cpu_usage` | 24 | Porcentaje global [0,100]; primera muestra de calentamiento = 0 |

`BlackboxSharedHeader` mide 16 B, alineación 4: `magic_header` +0 (`0x4D414345`), `ring_buffer_capacity` +4 (bytes), `write_cursor` +8 y `record_count` +12. El nombre y tamaño de estos registros **no cambiarán**. `NativeLayouts.java` usa exactamente los mismos offsets.

La futura corrección de la DLL deberá escribir los 28 bytes de `TelemetryData` en cada consulta, incluidos los dos reservados, sin alterar el layout. Para mantener compatibilidad, un dato sin sensor se codifica como cero más el indicador heredado; Java V2 podrá distinguir estados por métrica. El campo `gpu_fan_rpm` solo recibirá RPM reales o cero si la consulta de RPM no existe.

## Registros V2 definidos en fase 0

Todos tienen `struct_size` y `version=2` cuando son registros de salida; la DLL solo escribirá un registro si el llamador ofrece la capacidad requerida. El retorno `MACE_E_BUFFER_TOO_SMALL` comunicará `required_size` y no escribirá parcialmente. Una versión desconocida se rechazará con `MACE_E_UNSUPPORTED_VERSION`. Los campos `reserved` deben ser cero. Una métrica se usa **solo** cuando `state == MACE_METRIC_VALID`; `0` con estado distinto de válido no significa lectura física de cero.

- `MaceAbiInfoV2` (64 B): versiones ABI/compilación, tamaños heredados, tamaño de puntero y `wchar_t`, capacidades **implementadas**. La presencia del bit no afirma que el sensor del equipo actual esté disponible.
- `MaceGpuAdapterInfoV2` (296 B): LUID, IDs PCI/DXGI, flags e índice de enumeración y descripción UTF-16. El LUID e índice se consultan de nuevo al iniciar; no son claves persistentes entre reinicios. Los adaptadores de software se marcan mediante flags DXGI.
- `MaceCpuTelemetryV2` (56 B): fabricante detectado, proveedor, tiempo monotónico, uso/temperatura/potencia con estados separados y ámbito de potencia.
- `MaceGpuTelemetryV2` (104 B): LUID del adaptador consultado, proveedor y estados independientes de temperatura, potencia, uso, RPM, porcentaje de ventilador y memoria de vídeo. Jamás se mezclan valores de varias GPU.
- `MaceSystemMemoryV2` (48 B): memoria total y disponible del sistema con estados separados.
- `MaceProcessIdentityV2` (16 B): PID más fecha de creación Windows FILETIME en intervalos de 100 ns UTC. Deben compararse ambos para rechazar reutilización del PID.
- `MaceProcessSampleV2` (64 B): identidad, marca monotónica, Working Set y Private Bytes con estados separados.
- `MaceResponsivenessV2` (40 B): identidad, número de ventanas examinadas/colgadas y resultado explícito; no confunde ausencia de ventanas con capacidad de respuesta.
- `MaceBlackboxHeaderV2` (48 B) y `MaceBlackboxRecordV2` (160 B): capacidad en registros, secuencia, sobrescrituras y carga acotada de 128 B. Las estructuras CPU/GPU/proceso/sistema definidas aquí caben en esa carga. La caja negra en RAM no equivale al archivo persistente que corresponde al sink Java.

`timestamp_monotonic_ns` se deriva de `QueryPerformanceCounter` y sirve para intervalos, no para fecha de calendario. El tiempo de creación de proceso procede de `GetProcessTimes` y sigue el formato FILETIME UTC. Los valores de potencia declaran ámbito (`CPU_PACKAGE`, `CPU_RAIL`, `APU_SOC`, `GPU_BOARD` o `GPU_CHIP`) para evitar sumar dos lecturas que se solapan.

### Errores, disponibilidad y funciones previstas

`include/mace_status.h` fija retornos `MACE_OK=0`, errores negativos y estados de métrica. Los errores de permiso, proceso finalizado, PID reutilizado, buffer insuficiente y sensor ausente son distintos. Una función V2 que falle por Win32 devolverá `MACE_E_WINDOWS_ERROR` y, cuando corresponda, un parámetro de salida `uint32_t win32_error` capturado **antes** de cerrar el handle. Nunca se sobrecargará un float con un código de error.

`CP_GetAbiInfo` es la primera exportación ampliada y ya está disponible. Más tarde se implementarán enumeración de GPU y consulta por LUID, sondeos CPU/GPU/memoria, muestras de proceso y terminación por identidad, respuesta detallada de ventanas y API de caja negra. Estos nombres describen el contrato de integración; **no deben invocarse todavía**. Los módulos Java comprobarán ABI/capacidades antes de usar esas futuras funciones, manteniendo los siete símbolos heredados.

El buffer de procesos y las estructuras de salida son propiedad del llamador. El mapeo V2 será propiedad de la DLL y expondrá un token opaco, escritura y lectura por copia, estadísticas y cierre; Java no liberará ni retendrá punteros internos. Habrá un escritor por buffer y las lecturas usarán el protocolo nativo de sincronización. El símbolo heredado que devuelve una vista requiere una función de cierre específica en la fase 4; hasta entonces no debe usarse en producción.

## Permisos y límites

El manifiesto `packaging/app.manifest` pertenece al launcher `MACE.exe`; la DLL hereda el token del anfitrión. La elevación permite pedir derechos adicionales, pero no da acceso automático a MSR ni a procesos protegidos. Los tests del módulo corren sin elevarse; la prueba de terminación deberá crear y cerrar solo procesos auxiliares controlados.
