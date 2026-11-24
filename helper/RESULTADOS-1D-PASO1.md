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

## 1D-3

Se retira adlx_provider.c, su despacho y las opciones de compilación relacionadas
con esa fuente. El test de despacho conserva NVML/IGCL y exige ausencia del
proveedor AMD anterior; su original se archiva como texto. No se modifican las
implementaciones NVML, IGCL ni windows_memory_capacity/DXGI.
Mocks Radeon actuales pasan a LHM. El ID 6 queda reservado sin implementación.
No se cambia AmdPowerPolicy: potencia AMD fuera de PCI 1002:7340 queda N/D;
temperatura, uso, RPM y VRAM dependen de sus sensores LHM y de una identidad única.

Pruebas del tramo: helper 63/63, CTest 40/40, Java 279 (una omitida), sin fallos ni errores. Logs: native/build/1d-3/{helper,native,java}.log.

## 1D-4

Se retiran opciones SDK de build-native.ps1 y package-native.ps1 y sus tres campos
de build-info.json. Se conserva el manifiesto de compilación, hashes y ABI.
verify-native-dll.ps1 conserva SHA-256 y verificación FFM sin supuestos de SDK.
Se conservan completos documentos y mocks anteriores bajo docs/historico; las
rutas operativas se actualizan. No se anonimizaron ni borraron evidencias.
Los IDs 2/3/6 quedan reservados/obsoletos en C y Java; 4 sigue reservado, nunca
implementado. Los tests de códigos y serialización conservan compatibilidad con
esos números sin invocar implementaciones retiradas. Los mocks vigentes usan LHM.

packaging/stage-runtime.ps1 exige aplicación, DLL verificada, helper publicado,
PawnIO 2.2.0 intacto (SHA-256 fijado y firma válida) y avisos de terceros. Solo
copia archivos de ejecución y licencias; no ejecuta el instalador. Se excluyen
pruebas, fuentes, diagnósticos, scripts, PDB, documentación interna y artifacts.
verify-runtime.ps1 comprueba integridad y presencia de las cinco piezas y rechaza
extras; package-windows.bat lo invoca antes y después de jpackage. Se conservan
ganchos de firma, sin alterar PawnIO. La limpieza de carpetas generadas usa
PowerShell, rutas exactas permitidas y rechazo de enlaces/reparse points.

Regresión del tramo: helper 63/63, CTest 40/40, Java 279 (278 aprobadas y una
omitida por MACE_LEGACY_DLL), cero fallos/errores. Logs generados bajo
native/build/1d-4. Comprobación adicional del payload: seis casos aprobados
(copia de las cinco piezas y PawnIO intacto, verificador Java FFM/ABI real,
rechazo de instalador incorrecto,
cargador retirado, archivo interno extra y helper alterado). La prueba usa un
JAR de las clases compiladas, no afirma que haya ejecutado la app-image final.
El payload positivo contiene 213 archivos más su manifiesto; no se ejecutó PawnIO.

check-protected-telemetry.ps1 comprueba contra el respaldo: archivos NVML, IGCL,
inventario DXGI y layouts, y cuerpos de uso CPU, FILETIME, capacidad DXGI y RAM.
audit-retired-providers.ps1 enumera cada coincidencia con archivo, línea, columna
y motivo en docs/REFERENCIAS-ACEPTADAS-1D.tsv. Ese informe de salida no se analiza
a sí mismo. La consulta incluye nombres de cargadores, SDK, archivos e IDs;
las marcas CPU Ryzen o el acceso MSR interno de LHM no equivalen a proveedores
retirados. Los binarios/caches ignorados no son fuentes del repositorio público.

## Reproducción y recuperación

| Tramo | Commit | Helper | CTest | Java |
|---|---|---|---|---|
| 1D-1 | 44c6609 | 63/63 | 40/40 | 279, una omitida |
| 1D-2 | aad1fbd | 63/63 | 40/40 | 279, una omitida |
| 1D-3 | 2868b7f | 63/63 | 40/40 | 279, una omitida |
| 1D-4 | Commit que contiene este registro; consultar git log | 63/63 | 40/40 | 279, una omitida |

Desde la raíz: ejecutar helper/Build-Helper.ps1, native/scripts/test-lhm1b.ps1
con -BuildDirectory native/build/vs2026-lhm1b, y Maven test con DLL/hash/helper
configurados como en RESULTADOS-1C.md. Ejecutar también:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-runtime-payload.ps1 -NativeDll .\native\build\vs2026-lhm1b\Release\mace_native.dll -PawnIoInstaller 'RUTA_AL_INSTALADOR_OFICIAL' -JavaExe 'RUTA_JDK_22\bin\java.exe'
```

Cada tramo es un commit compilado/probado. Ver sus IDs con git log --oneline.
La reversión aislada de combinaciones de tramos que comparten archivos sigue
NO VERIFICADO; no se promete que git revert aislado esté libre de conflictos.
Para recuperar el respaldo sin borrar el historial posterior, desde un árbol
limpio: git switch -c recuperar-pre-1D 1b62000eddd0819258ceb33bf64eed69c2f09d44.
Para deshacer toda la retirada conservando historia se revisan/revierten los
cuatro commits en orden inverso. No se ejecutó ninguna reversión ni reset.

La imagen final mediante package-windows.bat y los instaladores MSI/EXE NO SE
EJECUTARON: pertenecen al Paso 2 y requieren la aprobación siguiente. Firma,
licencia MACE, revisión legal de PawnIO/módulos y runtime Java siguen pendientes;
el inventario de avisos no es autorización de redistribución. No se publica nada.

## Límites

NO VERIFICADO: CPU Intel, NVIDIA (NVML y respaldo), Intel GPU/IGCL, PawnIO ausente
en equipo limpio, HVCI con el conjunto actual, otras Radeon y multi-GPU.
La retirada no demuestra equivalencia en esas plataformas. La potencia AMD
permanece limitada a PCI 1002:7340 y las condiciones existentes de 1C.
Firma, licencia y redistribución siguen pendientes. No se instala/desinstala
nada ni se altera HVCI o PawnIO.

Al terminar el Paso 1 se requiere aprobación antes del Paso 2 y del informe final
del Paso 3. Las regresiones por tramo sí forman parte del Paso 1 autorizado.
