# Fase 4 — bases nativas de las extensiones

La DLL 1.4.0 (ABI 1.4) añade muestras de memoria por proceso, diagnóstico detallado de ventanas y un buffer circular temporal. La aplicación Java todavía debe consumir estas interfaces. Los siete símbolos usados por la aplicación existente y sus layouts no cambian.

## Muestras y ventanas

`CP_SampleProcessV2` recibe la identidad `PID + tiempo de creación` obtenida de `CP_EnumerateUserlandProcessesV2` o `CP_GetProcessIdentityV2`. Abre el proceso, comprueba que sigue vivo y que la identidad coincide, y devuelve `MaceProcessSampleV2` (64 bytes). La muestra incluye tiempo monotónico, Working Set y Private Bytes con estados separados. Si Windows deniega la consulta de memoria pero permite identificar el proceso, la muestra se entrega con estados `ACCESS_DENIED` o `READ_FAILED`; `out_win32_error` conserva el error. No interpreta una muestra como fuga. Dev 2 puede mantener 30 muestras y decidir la tendencia en Java.

`CP_CheckProcessResponsivenessV2` recibe la misma identidad y devuelve `MaceResponsivenessV2` (40 bytes). Examina ventanas superiores visibles en el escritorio accesible al proceso actual mediante `EnumWindows`/`IsHungAppWindow`. Reporta cuántas ventanas examinó y cuántas aparecen colgadas; el resultado distingue `RESPONDING`, `HUNG`, `NO_WINDOW`, `PROCESS_EXITED` y `ERROR`. Una sola ventana colgada no implica necesariamente que todo el proceso esté bloqueado. Una identidad reutilizada devuelve `MACE_E_PID_REUSED` sin escribir el registro de salida. En sesiones sin escritorio interactivo, la consulta puede devolver `ERROR` en el registro. El símbolo antiguo continúa devolviendo solo 0/1.

Las dos funciones reciben capacidad en **bytes**. Un buffer corto devuelve `MACE_E_BUFFER_TOO_SMALL` sin tocar la salida. Ninguna función termina procesos ni requiere iniciar los sensores.

## Caja negra temporal

`CP_BlackboxCreateV2` acepta nombre opcional y capacidad entre 1 y 65 536 registros. Devuelve un token `uint64_t` no nulo. Un nombre ya usado devuelve `MACE_E_BUSY` y `ERROR_ALREADY_EXISTS`, sin reinicializar memoria ajena. Si se pasa `NULL`, Windows crea un mapeo privado sin nombre. La DLL posee el handle, la vista y el encabezado; nunca devuelve un puntero a la vista V2.

Cada registro mide 160 bytes: tipo, tamaño de payload, secuencia monotónica dentro del anillo, tiempo monotónico y hasta 128 bytes de carga. `CP_BlackboxAppendV2` acepta exactamente un registro V2 de CPU (56 B), GPU (104 B), muestra de proceso (64 B) o memoria del sistema (48 B). Comprueba `struct_size` y `version=2`. Los tipos y tamaños están en `mace_extensions.h`. La capacidad se cuenta en **registros**, no bytes. Al llenarse, sobrescribe el más antiguo y aumenta `overwritten_records`.

`CP_BlackboxGetStatsV2` construye el encabezado de 48 bytes desde el estado privado de la DLL. `CP_BlackboxReadV2` copia todos los registros conservados desde `from_sequence`; cero solicita desde el más antiguo. `out_required_count` siempre indica cuántos se necesitan para la lectura actual. Si la capacidad es insuficiente, no escribe ningún registro. Si la secuencia pedida fue sobrescrita, entrega desde la primera disponible; el consumidor debe detectar la brecha comparando las secuencias o el contador de sobrescrituras. La DLL sincroniza creación, lectura, escritura y cierre mediante un bloqueo interno. No está admitida la lectura directa concurrente de la vista compartida desde Java u otro proceso.

`CP_BlackboxCloseV2` libera el token, vista y handle. Un token inválido o doble cierre devuelve `MACE_E_INVALID_ARGUMENT`. `CP_ShutdownTelemetry` cierra los buffers que sigan abiertos, incluso si la telemetría no estaba inicializada. El mapeo vive solo mientras exista un handle de Windows; **no sustituye** el archivo persistente de `MemoryMappedBlackboxSink` que corresponde a Dev 3.

El hook heredado `CP_InitSharedTelemetryBuffer` conserva el encabezado de 16 bytes y el retorno por puntero para compatibilidad. Ahora rechaza nombres ya existentes, registra los recursos y ofrece `CP_CloseSharedTelemetryBuffer(view)`. La aplicación debe detener y esperar a todos los lectores del puntero antes de cerrarlo o llamar a `CP_ShutdownTelemetry`; la DLL no puede sincronizar usos externos directos de esa dirección. El apagado limpia las vistas pendientes. No se debe liberar el puntero con `free`, `UnmapViewOfFile` ni FFM. Las nuevas integraciones usan el token V2.

## Comprobación

`process_extensions` prueba tamaños, identidad alterada, estados de memoria y respuesta estructurada. `blackbox_buffer` prueba anillo lleno, orden de secuencias, lectura por copia, buffer corto intacto, nombre duplicado, doble cierre y limpieza en shutdown. `abi_exports` comprueba los 24 símbolos y la versión 1.4. Compilación Release con ADLX y Ryzen Master Monitoring SDK externos: 8/8 pruebas CTest aprobadas en el equipo AMD de desarrollo. La disponibilidad de ventanas interactivas, proveedores Intel/NVIDIA y comportamiento de procesos de otros usuarios debe comprobarse en los equipos de los compañeros.
