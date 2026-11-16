# Subfase 1D — registro del Paso 1

Fecha: 16/11/2026, America/Lima. El usuario aprobó los cinco puntos del Paso 0,
con IDs retirados reservados/obsoletos, sin despacho a implementaciones activas,
y una lista exhaustiva de coincidencias aceptadas y motivos.

Respaldo previo: `1b62000eddd0819258ceb33bf64eed69c2f09d44`, rama `LHM_implementado`.
Incluye el cierre documental 1C. El ZIP de nativebridge ajeno a la implementación
no se añadió ni modificó. No se crearon etiquetas ni ramas de archivo.

## 1D-1

Se retira el lector Intel MSR, WinRing0 y Rdmsr del módulo CPU. Se conserva
literalmente el bloque de conversión FILETIME, estado diferencial y
ReadCpuUsagePercent/GetSystemTimes, con los mismos reinicios al cerrar.
CpuSensors_Poll conserva solo el uso Windows. Se adapta el fixture sin hardware.
El ID público 2 queda reservado; no puede seleccionarse como proveedor activo.

Sin helper predeterminado o explícito: NO_PROVIDER, ceros marcadores no válidos,
sin retorno a proveedores retirados. La capacidad total Windows se conserva
solo si su caché es válida y hay una coincidencia PCI única; provider WINDOWS.
No se restaura capacidad ante ambigüedad, caducidad, mensaje inválido o hash erróneo.
CPU uso, RAM, NVML e IGCL siguen independientes del helper.

Cuatro casos CTest adicionales: helper predeterminado ausente, ruta explícita
ausente, ausencia con PCI duplicada y PawnIO Stopped simulado. Los casos anteriores
siguen comprobando falta de elevación, PawnIO Missing, cero CPU inválido y ceros
GPU legítimos. No se cambian layouts, offsets ni códigos del contrato.

Pruebas del tramo: helper 63/63, CTest 40/40, Java 279 (278 aprobadas, una
omitida por falta de MACE_LEGACY_DLL), cero fallos/errores. Logs generados:
native/build/1d-1/{helper,native,java}.log. Son cuatro casos nativos adicionales
respecto de la línea base 36. Firma omitida con el aviso existente.

## 1D-2

Se retiran amd_ryzen_provider.cpp/h y su integración de compilación y del
trabajador. Se archiva check-ryzen-sdk-version.ps1 como texto fuera de los builds.
Se elimina LegacyTelemetry_Cpu y su caché; los sondeos CPU solo combinan uso
Windows y temperatura/potencia LHM. El ID 3 queda reservado sin implementación.
Los mocks CPU actuales y las expectativas Java pasan a LHM; no se suprimen tests.
Las opciones generales de los scripts se terminan de retirar en 1D-4.

Pruebas del tramo: helper 63/63, CTest 40/40, Java 279 (una omitida), sin fallos ni errores. Logs: native/build/1d-2/{helper,native,java}.log.

## Límites

NO VERIFICADO: CPU Intel, NVIDIA (NVML y respaldo), Intel GPU/IGCL, PawnIO ausente
en equipo limpio, HVCI con el conjunto actual, otras Radeon y multi-GPU.
La retirada no demuestra equivalencia en esas plataformas. La potencia AMD
permanece limitada a PCI 1002:7340 y las condiciones existentes de 1C.
Firma, licencia y redistribución siguen pendientes. No se instala/desinstala
nada ni se altera HVCI o PawnIO.

Al terminar el Paso 1 se requiere aprobación antes del Paso 2 y del informe final
del Paso 3. Las regresiones por tramo sí forman parte del Paso 1 autorizado.
