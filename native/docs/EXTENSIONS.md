# Integración de las tres extensiones previstas

El panel móvil queda fuera del alcance acordado. La capa nativa ofrece datos y buffers para detector de fugas/cuelgues, caja negra y Micro-HUD; no crea pantallas ni decide alertas.

## Detector de fugas y cuelgues

Dev 2 conserva las últimas 30 `MaceProcessSampleV2` por identidad completa (`pid` y `creation_time_100ns_utc`), descarta muestras con estado distinto de `MACE_METRIC_VALID` y calcula la tendencia de Working Set y Private Bytes por separado. La identidad evita unir la serie de un proceso finalizado con otro que reutilice el PID. Dev 3 enlaza las funciones FFM de consulta y Dev 4 presenta la alerta. Para cuelgues, Dev 2 usa `CP_CheckProcessResponsivenessV2` y diferencia `NO_WINDOW`, `PROCESS_EXITED` y `ERROR` de `RESPONDING`.

## Caja negra

Dev 3 crea un único buffer con `CP_BlackboxCreateV2(NULL, capacidad, ...)`, registra muestras CPU/GPU/proceso/memoria mediante `CP_BlackboxAppendV2`, lee por secuencia con `CP_BlackboxReadV2` y copia los datos al sink persistente Java. Debe cerrar el token con `CP_BlackboxCloseV2`, o dejar que el apagado global limpie como recuperación. El archivo histórico tiene un solo propietario: el sink Java. Si la lectura observa una brecha de secuencias, debe marcar registros perdidos; la memoria circular por sí sola no sobrevive al cierre del último handle. Un nombre de mapping se reserva para un requisito explícito de acceso entre procesos: los nombres existentes se rechazan y la lectura concurrente solo se ofrece a través de la API de la DLL. Dev 3 no usa `CP_InitSharedTelemetryBuffer`, cuyo puntero heredado exige detener y esperar a todos sus lectores antes de cerrar o apagar.

## Micro-HUD

Dev 4 consume los snapshots ya sondeados por la aplicación y los estados de disponibilidad por métrica de las funciones de telemetría V2. El uso de GPU por adaptador y la memoria global están disponibles cuando su proveedor/Windows los expone. La DLL no crea overlay, ventana ni otro ciclo de sondeo; el HUD JavaFX reutiliza el flujo existente. La interfaz debe mostrar «No disponible» si una métrica no tiene `state == MACE_METRIC_VALID`.

Para todos los módulos, Dev 3 comprueba primero `CP_GetAbiInfo` (`abi_major=1`, `abi_minor>=4` y el bit de capacidad correspondiente). Los registros de salida usan `struct_size`, `version=2` y capacidades explícitas. No debe suponer que una capacidad compilada garantiza que un sensor o una ventana estén disponibles en cada equipo.
