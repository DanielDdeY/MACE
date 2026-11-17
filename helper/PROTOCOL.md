# Contrato privado LHM1/MCB1/MCC1, versión 2

Vigente desde **Fase 2, Paso 1B**. El helper de aplicación 1.1.0 y la DLL
compilada con MACE_LHM_PROTOCOL=2 se distribuyen juntos. No hay negociación ni
downgrade: mezclar versiones 1 y 2 falla de forma conservadora. La versión del
protocolo privado es independiente del ABI público de MACE, que continúa en 1.4.

## Compatibilidad de distribución

| DLL | Helper | Resultado |
|---|---|---|
| Privado 2 | Privado 2, aplicación 1.1.0 | Dúplex, muestras LHM1 versión 2, bootstrap MCB1 versión 2 y control MCC1 versión 2 |
| Privado 2 | Privado 1 | El helper antiguo rechaza el bootstrap; no se aceptan muestras v1 ni se hace downgrade |
| Privado 1 | Privado 2 | El helper nuevo rechaza el bootstrap antiguo; no funciona como pareja compatible |
| Privado 1 | Privado 1 | Pareja histórica; no incorpora el cierre de Fase 2 Paso 1B |

Los manifiestos de publicación y runtime declaran protocolo 2. El staging exige
esa versión del helper y la marca MACE_LHM_PROTOCOL=2 dentro de la DLL; rechaza
componentes declarados de versión 1. La marca/manifiesto no son autenticación del
publicador. Firma y confianza fijada de dependencias siguen pendientes del Paso 2.
La imagen histórica de 1D permanece sin regenerar: reconstruir y distribuir
ambos componentes juntos, nunca reemplazar uno solo dentro de esa imagen.

## Muestras helper → DLL (LHM1)

Little endian, valores IEEE-754 binary64. Los offsets se leen explícitamente. Este contrato es independiente de los structs V2 y TelemetryData de la DLL.

Un mensaje es una cabecera de 48 bytes seguida de 0 a 17 dispositivos de 144 bytes. Máximo 2.496 bytes. El receptor lee primero 12 bytes y valida la longitud antes de reservar memoria. Debe tolerar lecturas parciales y detectar EOF/truncamiento. Debe imponer un plazo de lectura desde su hilo receptor.

| Offset | Tipo | Campo |
|---|---|---|
| 0 | u32 | Magic 0x314D484C, bytes LHM1 |
| 4 | u16 | Versión 2 |
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
| 8 | u64 | Reservado, cero; sin LUID en la versión 2 |
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

Actualización histórica 1C-0: no cambió la entonces versión 1 ni ningún campo/tamaño; estas reglas se conservan en versión 2. Se utiliza el Meaning GpuChip=5 ya existente para la potencia ASIC identificada en AMD PCI 1002:7340 con LHM 0.9.6.0, sensor GPU Package índice 3, backend Overdrive 8 y reporte con ASIC_POWER soportado y BOARD_POWER no soportado. Se toma el valor explícito y único ASIC_POWER del reporte público de LHM; no se reetiqueta el posible fallback de GPU Package. Se exigen cifras finitas entre 0 y 2.000 W. Otros dispositivos, versiones, informes ambiguos/incompletos y sensores sin identificar conservan UnknownPower=6, que la DLL devuelve UNSUPPORTED/0 con scope UNKNOWN. Evidencia y límites en RESULTADOS-1C.md.

State tiene los valores NumericReported 1, WarmingUp 2, NoProvider 3, Unsupported 4 y ReadFailed 5. Si una lectura no es NumericReported, value y meaning son cero. NumericReported conserva también ceros como cifras crudas. No equivale a MACE_METRIC_VALID.

Meaning tiene los valores None 0, CpuPackage 1, CpuControlTemperature 2, GpuCore 3, GpuBoard 4, GpuChip 5 y UnknownPower 6. Temperatura CPU acepta CpuPackage o CpuControlTemperature, GPU acepta GpuCore, potencia CPU exige CpuPackage y potencia GPU acepta GpuBoard, GpuChip o UnknownPower. Las demás métricas usan None.

El mapeo actual selecciona CPU Package o Core (Tctl/Tdie) para temperatura, CPU Package/Package para potencia y GPU Core para temperatura GPU. GPU Package/GPU Power se conserva con UnknownPower hasta validar su ámbito físico por proveedor/modelo. Hotspot no sustituye la temperatura core. Las RPM y el porcentaje se transmiten por separado. Sensores ambiguos o no seleccionados quedan UNSUPPORTED. Dos sensores con el mismo nombre y tipo no se agregan ni se eligen arbitrariamente.

Los datos SmallData de GPU Memory Used/Total se convierten de MiB a bytes. Las capacidades y las lecturas de GPU inactiva deben seguir siendo distinguibles al integrarlas. La identidad PCI requiere todos los campos y una sola instancia del dispositivo; falla de forma conservadora si el registro contiene duplicados.

Para 1B, el receptor debe validar el PID obtenido desde la tubería, todos los campos del contrato y una secuencia estrictamente creciente. El decodificador de referencia rechaza timestamps futuros o de más de 3.000 ms. El receptor debe repetir la comprobación de antigüedad al consultar la caché, aunque no lleguen más mensajes. Una conexión o proceso nuevos reinician el contexto de secuencia y deben invalidar la caché anterior.

La promoción de CPU a VALID requiere helper Ready, elevación, PawnIO Running, lectura NumericReported, identidad correcta y coherencia física/temporal. Los ceros observados sin acceso al driver no pueden promoverse por el mero estado del servicio. Para GPU, los sensores procedentes de API del controlador pueden funcionar aunque PawnIO falte; deben validarse por adaptador, fuente y estado sin heredar el requisito de driver de CPU de forma indiscriminada. El contrato 2 tampoco ofrece una prueba individual de éxito de lectura MSR de LHM. Ante incertidumbre el receptor conserva N/D. La tabla de promoción implementada en 1B está en RESULTADOS-1B.md.

## Control DLL → helper (MCC1)

La tubería es ahora dúplex, con un solo lector por dirección. La DLL sigue
creando el servidor privado y el helper verifica su PID antes de iniciar el
lector de control. No se añade otra tubería pública, argumento con nombre,
servicio, WMI ni COM. ACL, primera instancia y rechazo remoto se mantienen.

Un control mide exactamente 32 bytes, little endian. **Solo existe Shutdown**;
no hay ACK, ping ni órdenes para ejecutar acciones arbitrarias.

| Offset | Tipo | Campo |
|---|---|---|
| 0 | u32 | Magic 0x3143434D, bytes MCC1 |
| 4 | u16 | Versión 2 |
| 6 | u16 | Cabecera 32 |
| 8 | u32 | Tamaño total exacto 32 |
| 12 | u32 | Tipo 1, Shutdown; cualquier otro se rechaza |
| 16 | u64 | Secuencia 1; solo un control aceptado por conexión |
| 24 | u32 | PID de MACE, debe coincidir con el bootstrap autenticado por el PID real del servidor |
| 28 | u32 | Reservado, cero |

La DLL emite como máximo un cierre por sesión. El helper rechaza tamaño,
cabecera, versión, tipo, PID, secuencia o reservado incorrectos; también un
segundo control aunque sea idéntico al primero. Un control parcial tiene un
plazo de 500 ms desde su primer byte. No se reserva memoria según su tamaño
declarado: se usa un buffer fijo de 32 bytes.

El primer cierre válido solicita cancelación del muestreo. El lector conserva
una ventana de drenaje acotada de 100 ms para rechazar duplicados ya encolados;
no espera otro mensaje ni EOF para completar el cierre. Tras salir del proceso
ya no hay una conexión sobre la que aceptar controles tardíos. Un control
inválido/duplicado cierra la sesión con error (salida del helper 1), sin seguir
muestreando indefinidamente. La aplicación utiliza sus estados no válidos y
reconexión existentes; los nuevos motivos UI quedan para el Paso 4.

**EOF sigue siendo una solicitud de cierre independiente**, aun si nunca llegó
un control. La DLL mantiene la tubería abierta durante la salida cooperativa
si pudo emitir el mensaje; si no pudo, cierra la tubería para usar EOF. Si vence
el plazo o el muestreo/dispose está bloqueado, el job limita la vida del helper.
Enviar el control comparte el presupuesto cooperativo de 2000 ms; se esperan
hasta 1000 ms adicionales tras la terminación forzada, como en 1A. No se usa
FlushFileBuffers bloqueante. Los límites de cancelación/kernel documentados
en FASE2-PASO1A.md siguen aplicando.

## Arranque privado (Subfase 1B, actualizado en Fase 2 Paso 1B)

La DLL genera 32 bytes con BCryptGenRandom(BCRYPT_USE_SYSTEM_PREFERRED_RNG), codificados como 64 hex minúsculos tras mace-lhm-. Crea el servidor y un pipe anónimo de bootstrap. CreateProcessW recibe únicamente el argumento --bootstrap-stdin y una lista explícita de handles heredables (lectura de stdin y salida NUL). El servidor y el escritor de bootstrap no se heredan. El nombre no aparece en la línea de comandos ni en el entorno.

El bloque de bootstrap mide 128 bytes, little endian. La DLL escribe el bloque una sola vez y cierra su extremo escritor. El helper consume exactamente 128 bytes y valida nombre y campos antes de conectar.

| Offset | Tamaño | Campo |
|---|---|---|
| 0 | 4 | Magic 0x3142434D, MCB1 |
| 4 | 4 | Versión 2 |
| 8 | 4 | PID del servidor, distinto de cero |
| 12 | 4 | Longitud del nombre, exactamente 73 |
| 16 | 73 | Nombre ASCII mace-lhm- y 64 hex minúsculos |
| 89 | 39 | Reservado, cero |

La comprobación del PID mediante Windows es independiente del PID declarado en la cabecera LHM1. Se comprueban ambos. Cada reconexión crea otro hijo y nombre, invalida la caché y reinicia la secuencia local a cero; no se reutiliza el canal anterior.

El plazo de conexión es 5.000 ms. Cada mensaje completo tiene un plazo de lectura de 3.000 ms desde que comienza la lectura del prefijo; el sondeo de caché verifica además la edad del timestamp original, como máximo 3.000 ms. Se toleran dos intervalos perdidos de 1 Hz; al agotarse el tercero no se conservan cifras válidas. EOF, PID incorrecto, mensaje inválido o replay invalidan inmediatamente al detectarse. El backoff es 1, 2, 4 y 8 segundos, con techo de 8; la implementación actual lo reinicia al recibir sequence >= 3, lo que no demuestra tres muestras válidas. La corrección por muestras válidas, el límite de reinicios y su recuperación/presentación quedan para el Paso 4 de Fase 2; no se adelantan en 1B.

El nombre aleatorio dificulta adivinar/precrear la tubería, pero no es una credencial frente al mismo usuario: el espacio de nombres puede enumerarse y un administrador o depurador puede leer memoria o duplicar handles. stdin evita su exposición rutinaria en argumentos. No protege frente a un equipo/usuario comprometido. La ruta absoluta del ejecutable puede configurarse mediante MACE_LHM_HELPER; MACE_LHM_HELPER_SHA256 permite fijar su hash. La distribución confiable de ese hash, la firma, la comprobación de todas las DLL y el endurecimiento frente a reemplazo entre verificación y arranque siguen pendientes.

El servidor necesita CreateNamedPipe con PIPE_ACCESS_DUPLEX, FILE_FLAG_OVERLAPPED, FILE_FLAG_FIRST_PIPE_INSTANCE y PIPE_REJECT_REMOTE_CLIENTS, máximo una instancia, handle no heredable y DACL protegida para usuario de MACE y SYSTEM. El helper conecta a punto local, con TokenImpersonationLevel.Identification y verifica el PID del servidor. El nombre lleva 256 bits de aleatoriedad criptográfica. La ACL y la comprobación de PID reducen suplantación entre procesos, pero no aíslan de un adversario que ya controle el mismo usuario o tenga privilegios de depuración. La verificación de firma del helper sigue pendiente.
