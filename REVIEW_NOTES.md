# Revisión técnica de MACE

Esta versión corrige los problemas estructurales detectados al comparar el repositorio con los documentos de arquitectura entregados.

## Correcciones aplicadas

- Unificación del namespace Java bajo `com.mace` en dominio, aplicación, infraestructura, presentación y pruebas.
- Eliminación de referencias heredadas a `CorePulse` / `corepulse_native` en Maven, empaquetado, README y localización de la DLL.
- Restauración de los puertos hexagonales faltantes:
  - `HardwareSensorsPort`
  - `ProcessLifecyclePort`
  - `TelemetryPublisherPort`
  - `BlackboxSinkPort`
- Integración real de la UI con los casos de uso y los adaptadores de infraestructura mediante un composition root en `MaceApplication`.
- Sustitución del polling puramente visual/mock de JavaFX por sondeo en segundo plano a 1 Hz y actualización de UI mediante `Platform.runLater()`.
- Conexión de la terminación manual de procesos con `TerminateProcessService`, manteniendo diálogo de confirmación y protección de PID críticos.
- Implementación de un publicador reactivo con `SubmissionPublisher` y un `NoOpBlackboxSink` para el hook futuro de caja negra.
- Añadido `ListProcessesService` para evitar que la vista consulte directamente la infraestructura.
- Corrección del workflow de GitHub Actions, que antes contenía solo un comentario y no ejecutaba ninguna compilación ni prueba.
- Corrección de manejo de `GetLastError()` en `process_controller.c`: ahora el código de error se captura antes de cerrar el handle.
- Ajuste de permisos usados al abrir procesos para obtener métricas de memoria.

## Validación realizada en este entorno

- Se compiló correctamente con `javac` la capa `domain` + `application` tras las correcciones de puertos y paquetes.
- No fue posible ejecutar `mvn test` aquí porque el entorno disponible no tiene Maven instalado y usa JDK 21, mientras MACE exige JDK 22+ por la FFM API final.
- La capa C no puede compilarse en este entorno Linux porque depende de Win32/MSVC.

## Puntos todavía incompletos o técnicamente problemáticos

1. `native/src/telemetry/cpu_sensors.c` expone el **uso de CPU real** de todo el sistema (campo `cpu_usage`, 0-100 %) calculado con `GetSystemTimes()`. La **temperatura** y el **consumo** se leen de registros MSR (DTS y RAPL) solo si `WinRing0x64.dll` está junto a la DLL, la app corre como administrador y la CPU es Intel; en otro caso el campo `is_cpu_sensor_available` vale 0 y la UI muestra "N/D" en vez de inventar valores. WinRing0 **no se distribuye** con MACE: su driver tiene una vulnerabilidad conocida (CVE-2020-14979) y Windows 11 lo bloquea con la integridad de memoria activa. Los MSR de AMD Zen (0xC0010299 / 0xC001029B) no están implementados.
2. `nvmlDeviceGetFanSpeed()` devuelve porcentaje de velocidad, no RPM. El campo actual del contrato se llama `gpu_fan_rpm`. Para obtener RPM reales debe migrarse al API `nvmlDeviceGetFanSpeedRPM` cuando la versión de NVML instalada lo soporte, o cambiar explícitamente el contrato a porcentaje.
3. El módulo de memoria compartida de caja negra sigue siendo un hook futuro. `SetupSharedTelemetryBuffer()` crea un mapping pero todavía no existe un API de liberación/unmap, por lo que no debe activarse en producción hasta completar su ciclo de vida.
4. Los cuatro módulos futuros indicados en la arquitectura (detector de fugas/cuelgues, panel móvil, caja negra completa y Micro-HUD) permanecen como puntos de extensión, tal como define el MVP.

## Recomendación para la verificación final en Windows

1. Instalar JDK 22+ y Maven 3.9+.
2. Ejecutar `mvn clean test` dentro de `app/`.
3. Ejecutar `cmake -S native -B native/build -A x64` y `cmake --build native/build --config Release`.
4. Ejecutar la aplicación primero con `-Dmace.infra.mode=mock` y luego con `-Dmace.infra.mode=native`.
5. Confirmar offsets FFM, enumeración de procesos, lectura NVML y terminación manual en un equipo de prueba Windows.
