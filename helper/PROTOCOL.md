# Contrato privado LHM1, versión 1

Little endian, valores IEEE-754 binary64. Los offsets se leen explícitamente. Este contrato es independiente de los structs V2 y TelemetryData de la DLL.

Un mensaje es una cabecera de 48 bytes seguida de 0 a 17 dispositivos de 144 bytes. Máximo 2.496 bytes. El receptor lee primero 12 bytes y valida la longitud antes de reservar memoria. Debe tolerar lecturas parciales y detectar EOF/truncamiento. Debe imponer un plazo de lectura desde su hilo receptor.

| Offset | Tipo | Campo |
|---|---|---|
| 0 | u32 | Magic 0x314D484C, bytes LHM1 |
| 4 | u16 | Versión 1 |
| 6 | u16 | Cabecera 48 |
| 8 | u32 | Tamaño total exacto |
| 12 | u32 | Flags, bit 0 proceso elevado; demás bits cero |
| 16 | u64 | Secuencia creciente desde 1 en cada proceso nuevo |
| 24 | u64 | Uptime ms de Windows, compatible con GetTickCount64 |
| 32 | u32 | PID emisor, debe coincidir con el proceso autenticado por Windows |
| 36 | u32 | PawnIO, Missing 1 / Stopped 2 / Running 3 / Unknown 4 |
| 40 | u32 | Helper, WarmingUp 1 / Ready 2 / ReadFailed 3 |
| 44 | u32 | Número de dispositivos |

Un dispositivo comienza con esta cabecera de 32 bytes.

| Offset relativo | Tipo | Campo |
|---|---|---|
| 0 | u32 | Tipo, CPU 1 / NVIDIA 2 / AMD GPU 3 / Intel GPU 4 |
| 4 | u32 | Identidad, Unknown 0 / CPU 1 / UniquePCI 2 |
| 8 | u64 | Reservado, cero; sin LUID en la versión 1 |
| 16 | u32 | PCI vendor |
| 20 | u32 | PCI device |
| 24 | u32 | PCI subsystem |
| 28 | u32 | PCI revision |

CPU usa PCI cero. Una identidad GPU UniquePCI exige fabricante coherente, revisión de 8 bits, device de 16 bits y ausencia de tuplas duplicadas en el mensaje. Una GPU Unknown lleva métricas sin cifras utilizables. La DLL debe comprobar también la unicidad en su inventario DXGI antes de usar la tupla. El protocolo admite una CPU y hasta 16 GPU.

Después siguen siete métricas de 16 bytes, en orden fijo. Cada métrica contiene value f64, state u32 y meaning u32.

| Índice | Offset relativo | Métrica | Unidad | Rango de transporte |
|---|---|---|---|---|
| 0 | 32 | Temperatura | °C | -50 a 150 |
| 1 | 48 | Potencia | W | 0 a 2.000 |
| 2 | 64 | Uso GPU | % | 0 a 100 |
| 3 | 80 | Ventilador | RPM | entero, 0 a 100.000 |
| 4 | 96 | Ventilador/control | % | entero, 0 a 200 |
| 5 | 112 | VRAM usada | bytes | entero, 0 a 2^50 |
| 6 | 128 | VRAM total | bytes | entero, 0 a 2^50 |

Los rangos acotan el transporte y no certifican la exactitud del sensor. Se rechazan NaN, infinitos, enteros fraccionarios, uso de VRAM mayor al total y campos desconocidos.

State tiene los valores NumericReported 1, WarmingUp 2, NoProvider 3, Unsupported 4 y ReadFailed 5. Si una lectura no es NumericReported, value y meaning son cero. NumericReported conserva también ceros como cifras crudas. No equivale a MACE_METRIC_VALID.

Meaning tiene los valores None 0, CpuPackage 1, CpuControlTemperature 2, GpuCore 3, GpuBoard 4, GpuChip 5 y UnknownPower 6. Temperatura CPU acepta CpuPackage o CpuControlTemperature, GPU acepta GpuCore, potencia CPU exige CpuPackage y potencia GPU acepta GpuBoard, GpuChip o UnknownPower. Las demás métricas usan None.

El mapeo actual selecciona CPU Package o Core (Tctl/Tdie) para temperatura, CPU Package/Package para potencia y GPU Core para temperatura GPU. GPU Package/GPU Power se conserva con UnknownPower hasta validar su ámbito físico por proveedor/modelo. Hotspot no sustituye la temperatura core. Las RPM y el porcentaje se transmiten por separado. Sensores ambiguos o no seleccionados quedan UNSUPPORTED. Dos sensores con el mismo nombre y tipo no se agregan ni se eligen arbitrariamente.

Los datos SmallData de GPU Memory Used/Total se convierten de MiB a bytes. Las capacidades y las lecturas de GPU inactiva deben seguir siendo distinguibles al integrarlas. La identidad PCI requiere todos los campos y una sola instancia del dispositivo; falla de forma conservadora si el registro contiene duplicados.

Para 1B, el receptor debe validar el PID obtenido desde la tubería, todos los campos del contrato y una secuencia estrictamente creciente. El decodificador de referencia rechaza timestamps futuros o de más de 3.000 ms. El receptor debe repetir la comprobación de antigüedad al consultar la caché, aunque no lleguen más mensajes. Una conexión o proceso nuevos reinician el contexto de secuencia y deben invalidar la caché anterior.

La promoción de CPU a VALID requiere helper Ready, elevación, PawnIO Running, lectura NumericReported, identidad correcta y coherencia física/temporal. Los ceros observados sin acceso al driver no pueden promoverse por el mero estado del servicio. Para GPU, los sensores procedentes de API del controlador pueden funcionar aunque PawnIO falte; deben validarse por adaptador, fuente y estado sin heredar el requisito de driver de CPU de forma indiscriminada. El contrato 1 no ofrece una prueba individual de éxito de lectura MSR de LHM. Ante incertidumbre el receptor conserva N/D. La tabla de promoción implementada en 1B está en RESULTADOS-1B.md.

## Arranque privado implementado en 1B

La DLL genera 32 bytes con BCryptGenRandom(BCRYPT_USE_SYSTEM_PREFERRED_RNG), codificados como 64 hex minúsculos tras mace-lhm-. Crea el servidor y un pipe anónimo de bootstrap. CreateProcessW recibe únicamente el argumento --bootstrap-stdin y una lista explícita de handles heredables (lectura de stdin y salida NUL). El servidor y el escritor de bootstrap no se heredan. El nombre no aparece en la línea de comandos ni en el entorno.

El bloque de bootstrap mide 128 bytes, little endian. La DLL escribe el bloque una sola vez y cierra su extremo escritor. El helper consume exactamente 128 bytes y valida nombre y campos antes de conectar.

| Offset | Tamaño | Campo |
|---|---|---|
| 0 | 4 | Magic 0x3142434D, MCB1 |
| 4 | 4 | Versión 1 |
| 8 | 4 | PID del servidor, distinto de cero |
| 12 | 4 | Longitud del nombre, exactamente 73 |
| 16 | 73 | Nombre ASCII mace-lhm- y 64 hex minúsculos |
| 89 | 39 | Reservado, cero |

La comprobación del PID mediante Windows es independiente del PID declarado en la cabecera LHM1. Se comprueban ambos. Cada reconexión crea otro hijo y nombre, invalida la caché y reinicia la secuencia local a cero; no se reutiliza el canal anterior.

El plazo de conexión es 5.000 ms. Cada mensaje completo tiene un plazo de lectura de 3.000 ms desde que comienza la lectura del prefijo; el sondeo de caché verifica además la edad del timestamp original, como máximo 3.000 ms. Se toleran dos intervalos perdidos de 1 Hz; al agotarse el tercero no se conservan cifras válidas. EOF, PID incorrecto, mensaje inválido o replay invalidan inmediatamente al detectarse. El backoff es 1, 2, 4 y 8 segundos, con techo de 8; tres mensajes aceptados en un canal lo reinician a 1 segundo. La política de límite total de reinicios y su presentación en UI quedan para Fase 2.

El nombre aleatorio dificulta adivinar/precrear la tubería, pero no es una credencial frente al mismo usuario: el espacio de nombres puede enumerarse y un administrador o depurador puede leer memoria o duplicar handles. stdin evita su exposición rutinaria en argumentos. No protege frente a un equipo/usuario comprometido. La ruta absoluta del ejecutable puede configurarse mediante MACE_LHM_HELPER; MACE_LHM_HELPER_SHA256 permite fijar su hash. La distribución confiable de ese hash, la firma, la comprobación de todas las DLL y el endurecimiento frente a reemplazo entre verificación y arranque siguen pendientes.

El servidor necesita CreateNamedPipe con PIPE_ACCESS_INBOUND, FILE_FLAG_OVERLAPPED, FILE_FLAG_FIRST_PIPE_INSTANCE y PIPE_REJECT_REMOTE_CLIENTS, máximo una instancia, handle no heredable y DACL protegida para usuario de MACE y SYSTEM. El helper conecta a punto local, con TokenImpersonationLevel.Identification y verifica el PID del servidor. El nombre lleva 256 bits de aleatoriedad criptográfica. La ACL y la comprobación de PID reducen suplantación entre procesos, pero no aíslan de un adversario que ya controle el mismo usuario o tenga privilegios de depuración. La verificación de firma del helper sigue pendiente.
