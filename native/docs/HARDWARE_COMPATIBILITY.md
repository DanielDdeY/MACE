# Compatibilidad de hardware y límites de lectura

MACE identifica automáticamente la CPU mediante CPUID y las GPU físicas mediante DXGI; no solicita al usuario marcas ni modelos. La detección de un dispositivo y la disponibilidad de sus sensores son cosas distintas. Cada lectura informa su propio estado y la interfaz muestra «No disponible» cuando corresponde.

| Componente | Identificación | Lecturas y proveedor | Validación física |
|---|---|---|---|
| CPU AMD Ryzen 5 3600 | Confirmada | Uso global de Windows. Temperatura mediante Ryzen Master Monitoring SDK 3.1.1.5478 con controlador y elevación. Potencia AMD aún sin proveedor. | Confirmada temperatura con elevación en el equipo AMD de desarrollo. Sin elevación se obtiene `ACCESS_DENIED`. |
| Radeon RX 5500 XT | Confirmada | ADLX: temperatura, uso, potencia del chip, RPM y VRAM según disponibilidad del controlador. | Confirmada en el equipo AMD de desarrollo. |
| CPU Intel | Identificación por CPUID | Uso global de Windows. Temperatura/potencia dependen del acceso opcional a MSR; WinRing0 no se distribuye. | Pendiente de prueba física con los equipos de los compañeros. |
| GPU Intel integrada | Enumeración DXGI | IGCL puede aportar sensores según modelo, controlador y permiso. Una iGPU puede carecer de ventilador propio. | Pendiente de prueba física. |
| GPU NVIDIA | Enumeración DXGI | NVML puede aportar sensores según modelo y controlador. | Pendiente de prueba física. |
| APU AMD o portátil con iGPU y dGPU | Cada adaptador físico se enumera por separado si Windows lo expone | ADLX/Ryzen SDK pueden tener cobertura distinta entre modelos; una GPU apagada por ahorro energético puede no ofrecer lecturas. | Pendiente de prueba física en portátil. |

Las cifras de potencia CPU/GPU no equivalen al consumo eléctrico total del equipo. MACE no muestra energía total ni suma sensores de ámbitos distintos. En la interfaz visual se oculta la sección de energía hasta definir proveedores y unidades.

Para validar otro equipo, ejecute `test_mace.exe` de la misma compilación que la DLL, anote el modelo/driver y los estados de cada métrica, y consulte la [guía de diagnóstico](TEAM_DIAGNOSTIC_GUIDE.md). Los SDK oficiales AMD son dependencias externas: sus encabezados no se incluyen en el repositorio ni en el paquete MACE. La DLL compilada con ADLX y Ryzen SDK puede seguir necesitando el controlador o la instalación oficial en el equipo final para ciertas lecturas.
