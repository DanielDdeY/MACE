> Documento histórico conservado. La opción C y la retirada de 1D fueron aprobadas después de esta evidencia; el estado vigente está en [RESULTADOS-1D-PASO1.md](RESULTADOS-1D-PASO1.md).

# Entrega Subfase 1B — receptor nativo C

Se releyeron PROTOCOL.md y RESULTADOS-1A.md antes de implementar. El alcance termina en 1B. No se implementan el puente Java de 1C, la limpieza de 1D ni el instalador. Los cambios previos de Java, HUD, CSS y el ZIP del proyecto se conservaron.

## Comportamiento y fuentes

CP_InitializeTelemetry crea los hilos y retorna sin esperar conexión, Open() de LHM ni inicialización de SDK. El receptor es el único que conecta/lee el pipe. CP_PollCpuTelemetryV2, CP_PollGpuTelemetryV2 y CP_PollTelemetry consultan cachés; no abren, leen ni esperan al helper. Las lecturas de proveedores antiguos también pasan a un hilo con caché, conservando su código. GetSystemTimes continúa calculando el uso de CPU en CP_Poll, con su referencia diferencial independiente.

CPU usa LHM cuando la ruta está disponible/configurada. AMD GPU usa LHM. NVML e IGCL conservan prioridad cuando aportan alguna métrica de sensor válida, excluyendo la mera capacidad DXGI. El respaldo LHM sustituye el conjunto del adaptador cuando el primario no aporta sensores válidos; no se mezclan proveedores dentro de ese conjunto. Si el helper no existe en la ruta predeterminada, se permite la vía heredada. Una ruta explícita inexistente entrega NO_PROVIDER en la vía LHM.

Ruta predeterminada: subcarpeta lhm junto a la DLL, archivo Mace.Lhm.Helper.exe. Para pruebas se admite MACE_LHM_HELPER con ruta absoluta local. La aplicación y el hijo heredan el mismo token; esta subfase no solicita elevación automática.

El bloqueo de sondeo solo serializa llamadas locales y el estado diferencial de GetSystemTimes. Los lectores de caché usan TryAcquireSRWLockShared; si hay contención devuelven WARMING_UP con cero, sin esperar al escritor. El cierre marca primero NOT_INITIALIZED y después espera a los hilos fuera del bloqueo de sondeo. Una inicialización que coincide con otro init/shutdown devuelve MACE_E_BUSY inmediatamente; no se pone en cola detrás del cierre del helper/SDK. No hay garantía de tiempo real sobre el planificador de Windows, DXGI ni llamadas de procesos. La garantía implementada es que los sondeos y el arranque no esperan al helper ni a los SDK.

## Tabla de validez por métrica

Condiciones comunes de la vía LHM, abreviadas C: canal local autenticado por PID de Windows y PID de cabecera; longitud, versión, enums, reservados y rangos válidos; secuencia creciente; timestamp no futuro y edad como máximo 3.000 ms; helper Ready; identidad de dispositivo coherente; estado crudo NUMERIC_REPORTED. Se valida el mensaje entero antes de publicar. Un mensaje incoherente invalida la caché; no conserva la última cifra como VALID.

| Métrica | Condiciones para VALID además de C | Estado si no se cumplen |
|---|---|---|
| Temperatura CPU | Identidad CPU; PawnIO Running; Elevated=1; significado CpuPackage o CpuControlTemperature; -50..150 °C y distinto de cero | NO_PROVIDER si falta elevación o driver Running; READ_FAILED para cero aun elevado, por no existir prueba individual de lectura MSR en LHM1 |
| Potencia CPU | Identidad CPU; PawnIO Running; Elevated=1; CpuPackage; 0 < W <= 2.000 | NO_PROVIDER sin elevación/driver; READ_FAILED para cero con privilegios |
| Uso CPU | GetSystemTimes correcto, referencia diferencial previa y delta positivo; 0..100 % | WARMING_UP sin referencia/delta; independiente del helper, PawnIO y elevación |
| Temperatura GPU | Tipo NVIDIA/AMD/Intel admitido; tupla PCI única tanto en helper como en inventario DXGI de caché reciente; GpuCore; -50..150 °C | UNSUPPORTED si asociación ambigua; NO_PROVIDER si no hay dispositivo coincidente; se conserva el estado crudo de ausencia |
| Potencia GPU | Misma identidad única; 0..2.000 W; ámbito explícito GpuBoard o GpuChip | UNSUPPORTED con UnknownPower y power_scope UNKNOWN; los W muy bajos y 0 W pueden ser VALID con ámbito conocido |
| Uso GPU | Identidad única; 0..100 % | Estado crudo de ausencia; 0 % es válido |
| Ventilador GPU RPM | Identidad única; entero 0..100.000 | Estado crudo de ausencia; 0 RPM es válido, no se infiere a partir del porcentaje |
| Ventilador GPU % | Identidad única; entero 0..200, rango de transporte del contrato | Estado crudo de ausencia; 0 % es válido, no se convierte a RPM |
| VRAM usada | Identidad única; bytes enteros 0..2^50; si ambas cifras están presentes, usada <= total | READ_FAILED si mensaje incoherente; estado crudo si no hay lectura; cero no se rechaza automáticamente |
| VRAM total | Identidad única; bytes enteros 0..2^50; coherencia con usada cuando está presente | Igual que VRAM usada; no se inventa capacidad para una GPU ambigua |
| RAM sistema | GlobalMemoryStatusEx correcto; fuente Windows conservada | Retorno MACE_E_WINDOWS_ERROR si falla; independiente de LHM |

Para GPU, PawnIO y Elevated son contexto validado del mensaje y no requisitos de acceso a las API del controlador empleadas por los tipos GPU seleccionados en 1A. Missing/Stopped/Unknown de PawnIO y Elevated=0 no convierten por sí solos esos sensores en ausentes. Se exige igualmente Ready, identidad y coherencia; se prueba expresamente driver ausente y sin elevación. Esto evita aplicar a GPU la dependencia MSR de CPU. No se admiten otros tipos de dispositivo/proveedor en LHM1.

Orden de estados: fallo de transporte/protocolo/caducidad => READ_FAILED; helper WarmingUp => WARMING_UP; helper ReadFailed => READ_FAILED. En Ready se exige acceso CPU; después se traducen estados crudos WarmingUp/NoProvider/Unsupported/ReadFailed a los estados V2 homónimos. Solo NUMERIC_REPORTED que cumple la fila correspondiente pasa a VALID. Todo campo no VALID producido por la vía LHM lleva valor numérico cero, que es un marcador y no una lectura.

## GPU ambigua, UnknownPower y UI

Esta tabla describe las salidas del respaldo LHM; una asociación LHM ambigua no invalida lecturas independientes de NVML/IGCL que ya sean válidas.

| Salida de MaceGpuTelemetryV2 | GPU ambigua | Potencia UnknownPower, GPU asociada y resto válido |
|---|---|---|
| temperature_c | UNSUPPORTED, 0 | VALID, temperatura core |
| power_w | UNSUPPORTED, 0 | UNSUPPORTED, 0 |
| usage_percent | UNSUPPORTED, 0 | VALID, porcentaje informado |
| fan_rpm | UNSUPPORTED, 0 | VALID, RPM informadas, incluso 0 |
| fan_percent | UNSUPPORTED, 0 | VALID, porcentaje informado |
| video_memory_used_bytes | UNSUPPORTED, 0 | VALID, bytes informados |
| video_memory_total_bytes | UNSUPPORTED, 0 | VALID, bytes informados |
| power_scope | UNKNOWN=0 | UNKNOWN=0 |
| provider_id | LHM=8 | LHM=8 |
| luid, struct_size, version | Identidad solicitada por API y cabecera V2 | Identidad solicitada por API y cabecera V2 |

La UI existente comprueba isValid() y presenta N/D para estas métricas ausentes: una GPU ambigua muestra N/D en las siete métricas; UnknownPower muestra N/D solo en potencia. El diagnóstico C usa el texto equivalente “No disponible (no soportada)”. Se comprobó esta regla en el código de HudPane y los modelos Java; la visualización integrada con esta DLL es NO VERIFICADO, pendiente de 1C y empaquetado. El provider_id 8 todavía se interpreta como NONE por el Java actual, hasta el trabajo autorizado para 1C.

No se tocaron mace_types_v2.h ni mace_types.h. ABI comprobada: CPU V2 56 bytes, GPU V2 104, inventario GPU 296 y TelemetryData 28. Pasaron todos los offsets de abi_layouts y las exportaciones de abi_exports. Única ampliación pública: MACE_PROVIDER_LHM=8 al final del enum de 32 bits, sin cambiar valores anteriores.

## Seguridad de transporte y caducidad

El formato MCB1 y el razonamiento del arranque privado están en PROTOCOL.md. La DLL crea el servidor con FILE_FLAG_FIRST_PIPE_INSTANCE, FILE_FLAG_OVERLAPPED y PIPE_REJECT_REMOTE_CLIENTS, máximo una instancia, DACL protegida con solo SID de usuario y SYSTEM y handle no heredable. El helper verifica PID del servidor; la DLL verifica PID del cliente mediante Windows y en cada cabecera.

El nombre contiene 256 bits de BCryptGenRandom. Viaja por stdin anónimo heredado y lista explícita de handles; el argumento visible es únicamente --bootstrap-stdin. El helper simulado comprueba que su línea de comandos no contiene el nombre, la exclusividad de primera instancia y la ACL real del pipe (dos ACE permitidas, usuario/SYSTEM, DACL protegida). Otra prueba conecta desde un descendiente distinto del hijo esperado y se rechaza por PID del sistema operativo.

Edad máxima: 3.000 ms desde el timestamp de adquisición. A 1 Hz permite perder dos muestras previstas, dejando un intervalo adicional antes de invalidar. La tercera ausencia lleva a READ_FAILED a más tardar al plazo de lectura de 3.000 ms; no se renueva la edad al consultar caché. El decodificador prueba los límites 3.000/3.001 ms y se prueba expiración mientras el cliente mantiene abierto el pipe. EOF, replay, PID falso o frame inválido invalidan inmediatamente al detectarse.

Cada fallo limpia la caché antes de cerrar/esperar al hijo. La reconexión espera 1, 2, 4 u 8 s; techo 8 s, con espera cancelable. Se reinicia a 1 s tras al menos tres mensajes aceptados del canal. Cada intento crea un hijo y nombre nuevos y secuencia local desde cero, por lo que el primer número del nuevo proceso no reutiliza la muestra anterior. La prueba disconnect demuestra VALID -> READ_FAILED -> VALID con secuencia reiniciada; replay se rechaza dentro del mismo canal. El límite total de reinicios y su diagnóstico en UI quedan pendientes de Fase 2.

El transporte mínimo usa Job Object KILL_ON_JOB_CLOSE, asignación al hijo suspendido antes de reanudarlo y cierre del pipe para terminar ordenadamente. Espera 250 ms y, si es necesario, termina solo el hijo del que posee handle; espera hasta 1 s adicional. No descubre ni finaliza LHM abierto por el usuario. Esto hace concretas y repetibles las pruebas de reconexión de 1B; no completa la Fase 2.

## Cambios por archivo

Las rutas de esta tabla son relativas a mace-native-dev.

| Archivo | Cambio |
|---|---|
| native/src/telemetry/lhm_wire.h | Estructuras privadas y límites LHM1, fuera de ABI pública |
| native/src/telemetry/lhm_wire.c | Decodificación estricta, identidad, secuencia, rangos y promoción por métrica |
| native/src/telemetry/lhm_transport.h | Sesión privada y operaciones de transporte |
| native/src/telemetry/lhm_transport.c | Pipe Win32 protegido, RNG, bootstrap, PID, lectura solapada cancelable y gancho SHA-256 |
| native/src/telemetry/lhm_receiver.h | Interfaz interna de inicio/cierre y consulta de caché |
| native/src/telemetry/lhm_receiver.c | Hilo receptor, caducidad, invalidación, reconexión y salidas V2 |
| native/src/telemetry/legacy_telemetry.h | Interfaz interna de caché de proveedores conservados |
| native/src/telemetry/legacy_telemetry.c | Hilo de CPU/SDK y NVML/IGCL/ADLX; inventario único para asociación PCI |
| native/src/internal/mace_runtime.c | Arranque asíncrono, cierre separado del bloqueo de sondeo |
| native/src/internal/mace_runtime.h | Contrato interno de sincronización actualizado |
| native/src/mace_api.c | Cachés en sondeos, GetSystemTimes conservado, NVML/IGCL principales |
| native/include/mace_status.h | Provider LHM=8, sin nuevos estados ni cambios de layout |
| native/include/mace_api.h | Documenta arranque asíncrono, retorno BUSY concurrente y cierre obligatorio |
| native/CMakeLists.txt | Fuentes/librerías Win32 y targets diagnósticos/pruebas; fixtures no instalados |
| native/test_main.c | Nombre del proveedor LHM en diagnóstico; compartido por test_mace y Test_MACE_new |
| native/tests/lhm_fake_helper.c | Cliente simulado, bootstrap, comprobaciones de seguridad y escenarios |
| native/tests/lhm_fixture_providers.c | Inventario/SDK deterministas solo para DLL de prueba |
| native/tests/lhm_fixture_cpu.c | GetSystemTimes real, desactiva inicialización de hardware en fixture |
| native/tests/test_lhm_receiver.c | Pruebas end-to-end contra API real de DLL con proveedores simulados |
| native/tests/test_lhm_wire.c | 34 comprobaciones de parser y promoción, incluidos límites temporales |
| native/tests/test_lhm_live.c | Medición física opcional de ocho segundos contra DLL de producción |
| native/scripts/test-lhm1b.ps1 | Configura/compila, ejecuta CTest y opcionalmente helper real con hash |
| native/README.md | Entrada a documentación de 1B |
| helper/Mace.Lhm.Protocol/Bootstrap.cs | Valida bloque privado MCB1 |
| helper/Mace.Lhm.Helper/Program.cs | --bootstrap-stdin en producción; conserva argumentos de harness 1A |
| helper/Mace.Lhm.Tests/Program.cs | Tres comprobaciones adicionales de bootstrap |
| helper/PROTOCOL.md | Bootstrap, límites temporales y seguridad documentados |
| helper/README.md | Flujo actualizado para receptor C |
| helper/RESULTADOS-1B.md | Esta entrega, política, matriz, riesgos y comandos |

RESULTADOS-1A.md se conserva. Los proveedores antiguos, sus opciones de CMake y scripts permanecen; eliminarlos corresponde a 1D.

## Matriz de pruebas con helper simulado

Todos los escenarios pasan por CP_InitializeTelemetry y CP_Poll* de la DLL de prueba, compilada con el mismo receptor/transporte/runtime/API C de producción. Solo se sustituyen inventario y proveedores de hardware; no se exige disponer de Intel, AMD ni NVIDIA. En todos se comprueba uso CPU GetSystemTimes, cierre y límites de latencia de init (<200 ms) y CPU+GPU poll (<100 ms).

| Test CTest | Estado V2 esperado/comprobado |
|---|---|
| lhm_slow | WARMING_UP antes de conectar y antes de Ready; después CPU/GPU VALID |
| lhm_valid | CPU/GPU VALID; ventilador 0 RPM y 0 % VALID; GPU 0,05 W VALID con ámbito Board |
| lhm_stale | VALID durante dos intervalos perdidos; después READ_FAILED y cero sin conservar cifras |
| lhm_stale-arrival | Muestra con edad 3.001 ms rechazada: READ_FAILED |
| lhm_bad-pid | PID de cabecera distinto: READ_FAILED |
| lhm_wrong-client | Cliente con PID real distinto del hijo esperado: READ_FAILED |
| lhm_bad-server-pid | Cliente rechaza servidor por PID: READ_FAILED |
| lhm_malformed | Longitud excesiva en prefijo: READ_FAILED |
| lhm_nan | Número NaN: READ_FAILED |
| lhm_replay | Primera muestra VALID; secuencia repetida => READ_FAILED |
| lhm_disconnect | VALID -> READ_FAILED inmediato al EOF -> VALID en hijo/canal nuevos |
| lhm_no-elevation | Cifras CPU finitas 55 °C/15 W con Elevated=0 => NO_PROVIDER, valores V2 cero; GPU válida |
| lhm_driver-missing | Cifras CPU finitas sin PawnIO => NO_PROVIDER; GPU de API válida |
| lhm_zero-unprivileged | Ceros CPU, sin elevación/PawnIO => NO_PROVIDER, nunca VALID |
| lhm_zero-cpu | Ceros CPU con Elevated y PawnIO Running => READ_FAILED por incertidumbre MSR |
| lhm_gpu-zero | Potencia GPU 0 W con Board y ventilador 0 RPM => VALID |
| lhm_unknown-power | Solo power_w UNSUPPORTED/0, scope UNKNOWN; resto GPU VALID |
| lhm_unknown-gpu | Siete métricas GPU UNSUPPORTED/0, scope UNKNOWN |
| lhm_duplicate-pci | Tupla duplicada en inventario nativo => siete métricas UNSUPPORTED/0 |
| lhm_fragmented | Mensaje entregado byte a byte => VALID |
| lhm_fallback | NVML/IGCL simulados sin sensores => LHM VALID para NVIDIA/Intel |
| lhm_hash-mismatch | Hash fijado incorrecto impide arranque => READ_FAILED |
| lhm_init-race | Durante cierre lento simulado, Poll devuelve NOT_INITIALIZED e Initialize devuelve MACE_E_BUSY sin esperar; reinicialización posterior correcta |
| lhm_wire | Rechazo de tamaños, versión, flags, enums, campos, NaN/Inf, replay, futuro y caducidad; promociones esperadas |

También se ejecutan las once pruebas anteriores: abi_layouts, abi_exports, telemetry_v2, gpu_provider_dispatch, process_security, process_api, process_extensions, blackbox_buffer, lifecycle, concurrency y buffer_concurrency. Las primarias NVML/IGCL simuladas se comprueban en cada escenario aplicable.

## Resultados observados

MSVC 19.51 x64, C17, /W4 /WX: compilación correcta. CTest: 35/35 pruebas aprobadas. Parser C: 34 comprobaciones. Helper .NET: 50 comprobaciones aprobadas, cero advertencias/errores; 205 archivos publicados verificados por SHA-256. Firma omitida con aviso.

La última ejecución del script completo (antes de cambios documentales) duró 6,78 s para CTest. lifecycle registró 169 -> 169 handles tras diez ciclos; concurrency completó 100 ciclos de sondeo concurrentes a través de doce init/shutdown. Los escenarios simulados registraron 0 ms en init y CPU+GPU poll con GetTickCount64: significa menos que su resolución, no latencia nula. init-race comprobó que la inicialización no espera un cierre lento.

Prueba física local sin elevar, con hash del EXE fijado: inicialización 0,039 ms, primera disponibilidad observable a los 687 ms y máximo sondeo individual CPU/GPU 0,377 ms en ocho segundos. Ryzen 5 3600 y Radeon RX 5500 XT. PawnIO estaba Running, pero el proceso no elevado mantuvo CPU temperatura/potencia en NO_PROVIDER/0; GPU 33 °C VALID, ventilador aproximadamente 1.285..1.295 RPM VALID y potencia UNSUPPORTED/0 con scope UNKNOWN. Test_MACE_new.exe mostró además RAM y procesos y terminó con éxito fuera del aislamiento.

La primera ejecución del diagnóstico dentro del aislamiento dio READ_FAILED del helper. Se repitió fuera del aislamiento y funcionó; no se presenta ese fallo de ejecución aislada como un fallo de sensor ni como éxito de hardware.

Evidencias regenerables, ignoradas por Git:

- native/build/vs2026-lhm1b/Testing/Temporary/LastTest.log
- native/build/vs2026-lhm1b/lhm1b-tests.xml
- native/build/vs2026-lhm1b/lhm-live-20261114-130901.txt
- helper/artifacts/latest.txt y manifiesto de la publicación indicada

## Riesgos y NO VERIFICADO

| Elemento | Estado y límite |
|---|---|
| CPU elevada a través de esta DLL C en hardware real | NO VERIFICADO en 1B; CPU elevada real sí constaba en 1A a través del receptor .NET. 1B verifica la promoción con simulación y la negativa real sin elevar |
| Intel CPU, NVIDIA e Intel GPU físicos | NO VERIFICADO; preservar y probar dispatch simulado no prueba hardware |
| HVCI activo y VM/instalación limpia | NO VERIFICADO; no se cambia Integridad de memoria ni se instala PawnIO en esta entrega |
| Conexión desde otro usuario/SMB remoto | NO VERIFICADO físicamente; ACL nativa real inspeccionada y PIPE_REJECT_REMOTE_CLIENTS establecido |
| UI Java con DLL nueva | NO VERIFICADO visualmente; mapeo provider 8 pendiente de 1C; paquete de aplicación no actualizado |
| Suspensión/reanudación, hotplug, iGPU/dGPU inactivas, registro con dispositivos fantasma | NO VERIFICADO; se conserva N/D si no se puede probar identidad única reciente |
| Cuatro y ocho segundos de backoff bajo fallos sostenidos | Inspeccionado en código; NO VERIFICADO con cronometraje prolongado; primera reconexión y reinicio de secuencia sí probados |
| Autenticidad de helper y dependencias | Firma pendiente. Gancho hash del EXE probado; manifiesto sin firma, entorno y hash administrados por el mismo usuario no autentican publicador. No se elimina TOCTOU ni se fijan todas las DLL desde C |
| LHM1 y CPU cero | No existe evidencia individual de éxito MSR; se rechaza conservadoramente incluso cero elevado. No se puede distinguir una lectura física cero de una falla interna en ese contrato |
| Proveedores antiguos | Permanecen hasta 1D y pueden inicializar sus SDK/WinRing0 opcionales. Sus llamadas lentas o bloqueadas pueden demorar shutdown; los sondeos usan caché y nunca esperan esos hilos |
| Ciclo de vida completo | Reinicios totales limitados, reporte específico a UI, crash/kill del padre y entornos con Job Object restrictivo: NO VERIFICADO o pendiente de Fase 2. Se debe llamar CP_ShutdownTelemetry antes de descargar la DLL |
| Distribución y licencia | Pendientes Fase 3: helper junto a DLL, firma definitiva, licencia MACE, avisos de terceros, instalador/3010 y despliegue sin .NET preinstalado |

## Comandos de reproducción

PowerShell desde la raíz de mace-native-dev, con Visual Studio 2022/2026 C++ y Windows SDK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1
```

Genera native/build/lhm1b/Release, ejecuta las 35 pruebas y escribe lhm1b-tests.xml. No requiere hardware compatible, PawnIO ni administrador.

Para reconstruir el helper autocontenido se exige el SDK fijado por helper/global.json:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -RealHelper
```

Desde PowerShell como administrador, para exigir también temperatura y potencia CPU válidas (necesita PawnIO ya instalado/en ejecución):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -RealHelper -RequireCpu
```

El script no instala PawnIO ni modifica HVCI. Un fallo de -RequireCpu conserva el log para diagnóstico.

Para repetir únicamente CTest en la carpeta verificada aquí:

```powershell
. .\native\scripts\native-common.ps1
$toolchain=Get-MaceToolchain
& $toolchain.CTest --test-dir .\native\build\vs2026-lhm1b -C Release --output-on-failure
```

Diagnóstico C de CPU/GPU/RAM/procesos, usando la última publicación:

```powershell
$published=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:MACE_LHM_HELPER=Join-Path $published 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
& .\native\build\vs2026-lhm1b\Release\Test_MACE_new.exe
```

Este diagnóstico realiza un muestreo de un segundo; un arranque frío lento puede seguir en WARMING_UP. test-lhm1b.ps1 -RealHelper observa ocho segundos.

La Subfase 1B se detiene en esta entrega hasta aprobación del usuario.
