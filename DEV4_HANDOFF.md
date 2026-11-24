# MACE — traspaso de la integración Dev 3 a Dev 4

Esta copia aislada contiene el `app/` entregado por Dev 3, el empaquetado de Windows y el paquete nativo oficial `mace-native.dll` 1.4.2 (ABI 1.4). La carpeta original del equipo no se modificó. El SHA-256 de la DLL verificada es `01F0A52EF711D6FB17483F86468CE1C992FC4BB45EF9FBC09EE918D6A121A152`.

## Estado de la integración

- `MaceApplication` usa `InfrastructureAdapters` y abre la caja negra persistente antes de crear `MonitoringService`. Ese servicio es el **único** sondeo periódico. `MainDashboardViewModel` se suscribe a `MonitoringSnapshot`; no tiene un planificador propio.
- La tabla convierte cada `ProcessEntry` en `ProcessRow` conservando `ProcessIdentity` (PID y hora de creación), ruta y estados de memoria. El botón existente pide confirmación y llama a `TerminateProcessService.terminate(row.identity())`. Muestra resultados específicos (`ACCESS_DENIED`, `PID_REUSED`, `PROCESS_EXITED`, etc.) y solicita refresco cuando corresponde. Nunca reintenta por PID solo.
- Los mismos inventarios de procesos alimentan el detector de fugas y las muestras persistentes de la caja negra. `MonitoringService` no realiza una segunda enumeración para grabarlas. La sección «Historial» muestra si la caja negra está activa, registros escritos/perdidos, huecos y ruta del archivo actual.
- Las lecturas inválidas se muestran como `N/D` en vez de cero. El dashboard actual todavía proyecta una sola GPU: elige la primera con temperatura válida. **Dev 4 debe presentar todas las GPU del `HardwareSnapshot.gpus()` por identidad LUID**, sin sumar sensores ni iniciar otro sondeo.
- `MaceLauncher` es la entrada para los paquetes de JavaFX en classpath. `package-windows.bat` comprueba SHA-256/ABI de la DLL, quita el atributo de solo lectura al launcher antes de `mt.exe` y falla si no puede incrustar el manifiesto. `MACE.exe` requiere administrador; `MACE-Preview.exe` usa datos simulados y `asInvoker`.

## Contratos para la interfaz

Consultar `MonitoringSnapshot` para hardware, inventario, alertas, respuesta de ventanas y `BlackboxStats`. `HardwareSnapshot.cpu()` y cada elemento de `gpus()` tienen lecturas con `MetricState`; mostrar el valor solo cuando `isValid()` es verdadero y conservar el motivo de `N/D` cuando no lo es. `ProcessRow.identity()` es el token que debe llegar a `TerminateProcessService.terminate(...)` tras la confirmación.

Para el Micro-HUD, suscribirse al **mismo** `MonitoringPublisherPort` ya usado por el dashboard. El Stage, su cierre y la presentación de varias GPU pertenecen a Dev 4. Las alertas térmicas, de fuga y de cuelgue son informativas; no cierran procesos automáticamente. El panel móvil sigue fuera del alcance.

El panel provisional de caja negra muestra salud y ubicación del archivo, pero aún no tiene un explorador de registros ni exportación. Dev 4 puede añadir esa vista usando el lector de archivos de `infrastructure/telemetry`, sin leer directamente el anillo de la DLL desde JavaFX. La política actual rota archivos a 8 192 registros, conserva como máximo 20 y limita la carpeta a 64 MiB; la grabación de cada proceso a 1 Hz puede hacer que se alcance antes el límite de retención.

## Ejecución y comprobaciones

1. Instalar JDK 22+ y Maven. Para probar la interfaz sin DLL ni UAC: desde `app/`, ejecutar `mvn javafx:run -Ppreview`. También se puede abrir `packaging/dist/MACE/MACE-Preview.exe` del app-image ya generado.
2. Para pruebas con la DLL real, definir `MACE_NATIVE_LIB` con la ruta de `native/dist/mace-native-1.4.2-win-x64-20261018T230709722Z-01f0a52ef711/bin/mace_native.dll` y ejecutar `mvn test` desde `app/`. Las pruebas unitarias no necesitan la DLL; las de integración se habilitan al definir esa variable.
3. Para regenerar el app-image, ejecutar `packaging/package-windows.bat app-image` desde Windows con JDK, Maven y `mt.exe` del Windows SDK. `JAVA_HOME` debe apuntar al JDK 22+. La salida es `packaging/dist/MACE/`. En esta copia se verificaron ambos manifiestos y el SHA-256 de la DLL dentro de la imagen.

Resultado local: 246 pruebas Java, 0 fallos, 1 omitida (requiere una DLL antigua adicional para comprobar el mensaje de rechazo). La integración con la DLL real enumeró procesos y detectó la Radeon RX 5500 XT. La temperatura AMD devolvió `ACCESS_DENIED` al probar sin elevación; el launcher real incorpora el manifiesto para pedirla. La vista previa empaquetada superó una prueba de arranque de cinco segundos. El cierre manual real debe comprobarse con un proceso auxiliar seguro desde una sesión elevada; no se debe probar finalizando procesos del sistema.
