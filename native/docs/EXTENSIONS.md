# Estado de las extensiones

El panel móvil quedó fuera del alcance. Las otras tres extensiones usan el contrato nativo V2; la presentación y las reglas de negocio residen en Java.

## Fugas de memoria y cuelgues

La DLL entrega muestras de memoria por identidad completa del proceso (PID y hora de creación) y un resultado estructurado sobre la respuesta de su ventana. Java conserva las series, calcula tendencias y muestra avisos. Una lectura ausente o un proceso finalizado no se trata como cero ni como prueba de cuelgue. MACE no finaliza procesos por una alerta; el usuario debe iniciar y confirmar esa acción.

## Caja Negra

La DLL ofrece un anillo V2 con token de control para registrar y leer eventos por secuencia. Java es el propietario del archivo persistente y registra lecturas, avisos y acciones. La vista «Caja Negra» permite consultar sesiones anteriores y detectar huecos o pérdidas. El puntero heredado del buffer compartido no se usa como fuente de persistencia.

## Micro-HUD

La navegación ya reserva una pestaña vacía. El HUD visual todavía no está implementado. Cuando se agregue, debe reutilizar los snapshots existentes del monitor Java, respetar el estado de disponibilidad de cada métrica y evitar un segundo ciclo de sondeo o un proveedor de sensores independiente.

La app comprueba ABI y capacidades de la DLL antes de usar V2. Una capacidad compilada no garantiza que el sensor físico exista o que el proveedor pueda leerlo en cada equipo.
