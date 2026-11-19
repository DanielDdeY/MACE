# Plan de implementación de Dev 4 en `mace-native-dev`

## Punto de partida y límites

La integración de Dev 3 ya está en esta carpeta. La DLL 1.4.2/ABI 1.4 fue verificada con SHA-256 `01F0A52EF711D6FB17483F86468CE1C992FC4BB45EF9FBC09EE918D6A121A152`; `mvn clean test` ejecutó 246 pruebas sin fallos (una omitida por requerir una DLL antigua). El usuario comprobó en la aplicación real las lecturas y el botón de finalizar procesos. Estas dos funciones son regresiones que no se deben romper.

La guía del equipo asigna a Dev 4 `app/src/main/java/com/mace/presentation/**`, `app/src/main/resources/**` y las pruebas de presentación. El trabajo se hará allí; `native/`, el ABI, dominio, casos de uso, FFM y empaquetado solo se tocarían ante un bloqueo concreto demostrado por una prueba. No se implementará panel móvil. MACE seguirá con un solo `MonitoringService` a 1 Hz, sin sumar las potencias de CPU y GPU como consumo total de la máquina.

## 0. Congelar la base de pruebas

1. Registrar en un checkpoint local el estado integrado de Dev 3 antes de editar la interfaz (sin llevarlo a `main`). Conservar el hash de DLL, resultado de Maven y la imagen de Windows actual.
2. Abrir `MACE.exe` y `MACE-Preview.exe` como referencia visual. Guardar una lista corta de los datos que muestra cada pantalla y confirmar la prueba manual ya realizada: cierre con confirmación, cancelación sin cierre y refresco de la tabla.
3. Mantener la prueba de integración V2 y las pruebas del botón como control de regresión en cada etapa. No copiar archivos de la entrega ZIP sobre el árbol de trabajo una segunda vez.

**Salida:** base recuperable y criterios de comparación para la nueva UI.

## 1. Convertir el snapshot V2 en estado de presentación completo

**Modificar:** `presentation/viewmodel/MainDashboardViewModel.java`, `presentation/viewmodel/ProcessRow.java`.

**Agregar:** `presentation/viewmodel/MetricDisplay.java`, `GpuCardViewModel.java`, `AlertItemViewModel.java` y, si simplifica el código, `MetricHistory.java`; sus pruebas en `app/src/test/java/com/mace/presentation/viewmodel/`.

1. Mantener la única suscripción al `MonitoringPublisherPort`. Guardar la secuencia recibida para descartar actualizaciones atrasadas. Aplicar cambios a propiedades JavaFX mediante `Platform.runLater`; al cerrar, cancelar la suscripción y no aceptar callbacks tardíos.
2. Exponer CPU, memoria global, alertas, inventario y **lista observable de todas las GPU**. Mantener cada GPU por `GpuAdapterId` (LUID de la sesión), sin mezclar lecturas ni usar el índice como identidad permanente. Si una GPU desaparece, retirar su tarjeta y su historial; si reaparece, crear uno nuevo.
3. Formatear cada `MetricReading`/`LongMetricReading` con valor, unidad y `MetricState`. Solo `VALID` produce número; para los demás estados mostrar `N/D` y el motivo (`ACCESS_DENIED`, `NO_PROVIDER`, `WARMING_UP`, `DEVICE_INACTIVE`, etc.). Etiquetar la potencia con `PowerScope` (`CPU package`, `GPU board/chip`, `APU SoC`).
4. Actualizar las gráficas solo cuando cambie `hardwareSequence`, nunca por un refresco exclusivo de procesos. Limitar el historial (por ejemplo, 30–60 muestras) y representar lecturas inválidas como huecos, sin insertar ceros ni unir visualmente tramos separados por datos ausentes.

**Salida:** pruebas puras del mapeo para cero, una y dos GPU; cambios de orden/LUID; estado inválido; y refresco de procesos sin nuevo punto de hardware.

## 2. Reconstruir CPU/GPU y gráficos para varias GPU

**Modificar:** `presentation/view/MainDashboardController.java`, `presentation/view/component/GaugeDial.java` si necesita estados, `app/src/main/resources/fxml/MainDashboard.fxml`, `app/src/main/resources/styles/dark-theme.css`.

**Agregar:** `presentation/view/component/GpuCard.java` o `presentation/view/GpuCardController.java` con `app/src/main/resources/fxml/GpuCard.fxml`; elegir una sola variante al implementar.

1. Sustituir la sección GPU fija por tarjetas dinámicas: nombre y fabricante, proveedor, temperatura, uso, potencia y su ámbito, ventilador (RPM/% cuando exista) y VRAM. Mostrar por separado «sin GPU», «GPU detectada sin sensor» e «inactiva». Una iGPU sin ventilador no debe parecer una lectura de 0 RPM.
2. Presentar CPU con fabricante/proveedor y estados independientes de temperatura, potencia y uso. La falta de potencia AMD no debe ocultar temperatura o uso válidos.
3. Ofrecer selector o leyenda de GPU en «Historial»; las series se asociarán al LUID. Ajustar el layout para 1–3 GPU y resoluciones pequeñas, sin crear una gráfica pesada por cada proceso.
4. Eliminar al terminar esta etapa la proyección provisional de «primera GPU con temperatura válida» (`gpuTemp`, `gpuWatts`, `gpuTempHistory`, `gpuGaugesBox` y sus controles fijos). Conservar `GaugeDial` si sirve al diseño nuevo; eliminar `MetricCard.java` solo si continúa sin usos.

**Salida:** revisión visual en `NORMAL`, `DUAL_GPU`, `IGPU_NO_FAN`, `GPU_INACTIVE`, `GPU_NO_SENSOR` y `NO_GPU` del modo mock.

## 3. Mostrar alertas y pulir procesos sin alterar el cierre seguro

**Modificar:** `MainDashboardViewModel.java`, `MainDashboardController.java`, `ProcessTableViewModel.java`, `MainDashboard.fxml` y CSS.

1. Añadir un panel de alertas activas a partir de `MonitoringSnapshot.thermalAlerts()`, `leakAlerts()` y `hangAlerts()`, con origen, gravedad y tiempo. Usar también `responsiveness` para distinguir `HUNG`, `NO_WINDOW`, terminado, error y acceso denegado. Evitar diálogos repetidos cada segundo.
2. Quitar los umbrales `75/85` duplicados del controlador. El estado térmico visible vendrá de `ThermalAlert`/`AlertLevel`; una lectura ausente no será «Normal». Estas alertas serán informativas: nunca finalizan procesos.
3. Preservar búsqueda, orden y selección por `ProcessIdentity` al renovar el inventario. Mantener la confirmación modal y pasar **la identidad capturada de la fila** al caso de uso, incluso si la tabla se refresca mientras el diálogo está abierto. Mostrar estados de fallo específicos y no reintentar por PID.
4. Limitar el gráfico circular a los procesos con mayor memoria y agrupar el resto como «Otros», para que cientos de filas no saturen JavaFX. Indicar que el total corresponde a los procesos listados, no a toda la RAM física.

**Salida:** pruebas de cancelación, cierre correcto, PID reutilizado, selección tras ordenar/filtrar, memoria `N/D` y alertas que no disparan cierres.

## 4. Hacer útil la vista de caja negra

**Agregar:** `presentation/viewmodel/BlackboxHistoryViewModel.java`, un panel/controlador de historial en `presentation/view/` y sus pruebas. **Modificar:** `MainDashboard.fxml`, `MainDashboardController.java` y CSS.

1. Conservar la tarjeta ya funcional de estado, conteos y ruta. Añadir lista de archivos de sesión, marca de datos simulados/reales, estado de cierre y tabla acotada de registros recientes. Los huecos (`GAP`) y archivos incompletos deben ser explícitos.
2. Usar `BlackboxFileReader` en un ejecutor de lectura, bajo demanda o con actualización limitada; nunca leer todos los archivos en cada tick de JavaFX. La carpeta tiene rotación y límite: la vista debe tolerar que un archivo antiguo desaparezca entre listado y apertura.
3. Mostrar el texto UTF-8 solo para tipos de evento de aplicación (`BlackboxEventType`). Los tipos 1–4 contienen payload binario V2: identificarlos como muestra CPU/GPU/memoria/proceso y no interpretarlos como texto. La pantalla puede mostrar secuencia, hora, tipo y pérdidas sin añadir decodificación binaria nueva.
4. En `BLACKBOX_GAP`, comprobar visualmente que se vean los registros perdidos. Un archivo abierto durante la escritura puede aparecer incompleto; distinguir «sesión en curso» de corrupción comprobada.

**Salida:** vista navegable de eventos y huecos, sin bloquear el dashboard ni modificar el archivo histórico.

## 5. Añadir el Micro-HUD opcional

**Agregar:** `presentation/view/hud/HudController.java`, `presentation/viewmodel/HudViewModel.java` (adaptador ligero del estado compartido), `app/src/main/resources/fxml/Hud.fxml` y CSS propio o clases del tema existente. **Modificar:** `MaceApplication.java`, `MainDashboardController.java` y `MainDashboard.fxml` para abrir/cerrar.

1. Abrir un solo `Stage` opcional y mostrar CPU, GPU seleccionada, estados `N/D` y alerta más alta. El HUD usa el **mismo ViewModel/snapshot** que el dashboard; abrirlo no crea otro `MonitoringService`, adaptador nativo ni planificador.
2. Al cerrar el HUD, soltar bindings/listeners y permitir abrirlo otra vez. Al cerrar MACE, cerrar HUD y cancelar suscriptores antes de cerrar publisher y adaptadores.
3. Dar una selección estable de GPU por LUID mientras dure la sesión; si se desconecta o pasa a inactiva, mostrar el estado, no cambiar silenciosamente a otra GPU.

**Salida:** prueba de ciclos abrir/cerrar y comparación de `hardwareSequence`/número de sondeos antes y después: la tasa de lectura debe permanecer en 1 Hz.

## 6. Validar escenarios simulados y dispositivos reales

1. Usar `mvn javafx:run -Ppreview -Dmace.mock.scenario=<NOMBRE>` para revisar `NORMAL`, `CPU_NO_TEMPERATURE`, `AMD_NO_POWER`, `ACCESS_DENIED`, `IGPU_NO_FAN`, `DUAL_GPU`, `GPU_INACTIVE`, `GPU_NO_SENSOR`, `WARMING_UP`, `THERMAL_WARNING`, `THERMAL_CRITICAL`, `NO_GPU` y `BLACKBOX_GAP`. Los procesos mock ya incluyen colgado, sin ventana, corta vida, protegido, acceso denegado y PID reutilizado. No hay necesidad inicial de nuevos mocks.
2. Ejecutar `mvn clean test` sin DLL y con `MACE_NATIVE_LIB` apuntando al paquete verificado. Hacer una prueba de arranque de `MACE-Preview.exe`, luego de `MACE.exe` elevado. En el equipo actual comparar CPU/Radeon con `test_mace.exe`; solicitar pruebas físicas Intel/NVIDIA e iGPU de portátil cuando haya equipos disponibles.
3. Regenerar `packaging/dist/MACE/` y comprobar SHA-256, manifiestos (`requireAdministrator`/`asInvoker`), cierre del HUD, persistencia de caja negra y botón de procesos. Registrar las limitaciones reales de cada proveedor en la entrega; no presentar `NO_PROVIDER` como un cero ni prometer consumo eléctrico total.

**Criterio de finalización:** dashboard y HUD muestran el mismo ciclo de datos; todas las GPU detectadas tienen tarjeta propia; los motivos de N/D y las alertas son correctos; historial y huecos son visibles; búsqueda y finalización manual siguen funcionando; las pruebas pasan y el paquete de Windows arranca en modo real y simulado.
