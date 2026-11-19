# Subfase 1D — verificación del Paso 2

Fecha: 16/11/2026, America/Lima. Paso 2 aprobado por el usuario. Se detiene antes
del informe final del Paso 3. No se instala MACE/PawnIO, no se cambia HVCI y no se
publica ningún artefacto. Firma y licencia siguen pendientes.

## Comparación con la línea base

| Batería | Paso 0 | Paso 2 | Resultado |
|---|---|---|---|
| Helper | 63 | 63 | Todas aprobadas; 205 artefactos fuente verificados |
| CTest | 36 | 40 | Todas aprobadas; cero omitidas/fallos |
| Java | 279 | 279 | 278 aprobadas, una omitida; cero fallos/errores |

Las cuatro pruebas CTest añadidas en Paso 1 son: helper predeterminado ausente,
ruta explícita ausente, ausencia con PCI duplicada y PawnIO Stopped. La omisión
Java es el caso opcional de DLL antigua, porque MACE_LEGACY_DLL no está configurada.
La batería Java se ejecutó desde package-windows.bat app-image --with-tests,
con DLL/hash/helper reales fijados, incluidos los controles JavaFX del HUD.

Se compiló desde un directorio nuevo y se generó un paquete nativo con metadatos
de commit 56132e26a91651c100b1e84a3df2244f0821484b. La DLL tiene SHA-256
4D5E10F1CEF44A26BE4F2DA32955489613266AB33759496EECAD07B5E839EED8.
Se comprobaron 24 exports, ABI 1.4 y FFM sobre la DLL distribuida.

## Integridad del contrato y proveedores conservados

CPU V2 56 bytes, GPU V2 104, inventario GPU 296 y TelemetryData 28, con offsets
congelados comprobados por CTest y Java. Los IDs públicos 0..8 no cambian; 2/3/6
se conservan reservados/obsoletos, sin despacho a implementación activa; 4 nunca
se implementó. NVML=5, IGCL=7, LHM=8.

check-protected-telemetry.ps1 comparó contra el respaldo 1b62000: NVML, IGCL,
inventario DXGI, layouts, ReadCpuUsagePercent/GetSystemTimes, conversión FILETIME,
capacidad Windows de vídeo y RAM están intactos. Compilan y sus pruebas pasan.
Las comprobaciones simuladas de primarias y respaldo no validan hardware adicional.

## Ausencias, ceros y estados

| Caso simulado aprobado | Resultado esperado y comprobado |
|---|---|
| Helper predeterminado o explícito ausente | CPU temperatura/potencia y sensores GPU NO_PROVIDER; no vuelve a proveedores retirados |
| Capacidad Windows sin helper | VRAM total válida solo con caché e identidad única; resto de sensores ausentes sigue no válido |
| Sin elevación, aunque lleguen números finitos | CPU temperatura/potencia NO_PROVIDER; no se promueven a VALID |
| PawnIO Missing o Stopped | CPU NO_PROVIDER; GPU por API de controlador puede seguir válida |
| Cero CPU sin elevación/driver | NO_PROVIDER, nunca cero VALID |
| Cero CPU con acceso declarado | READ_FAILED conservador; LHM1 no prueba éxito individual de lectura |
| GPU en cero, RPM cero, potencia GPU muy baja | VALID si cumplen contrato; no se prohíben ceros legítimos |
| Potencia UnknownPower | UNSUPPORTED, cero marcador, ámbito UNKNOWN y N/D en presentación |
| PCI ambigua | Métricas no válidas, sin rescatar capacidad por asociación incierta |
| Caducidad, desconexión, PID/mensaje inválido o replay | READ_FAILED, invalidación de caché y reconexión según protocolo |

Los casos simulados registraron Initialize en 0 ms y CPU+GPU Poll máximo 0 ms
con resolución de milisegundos; no significa latencia física nula. Los límites
de las aserciones son menos de 200 ms al iniciar y 100 ms al sondear. CP_Poll no
espera al helper. La caducidad sigue en 3000 ms y no cambió el protocolo.

## Imagen final

package-windows.bat app-image --with-tests terminó con salida 0. Ubicación:
packaging/dist/MACE/MACE.exe. La imagen contiene 369 archivos / 147719124 bytes;
el payload de app contiene 222 archivos declarados más los archivos de jpackage.
Son 11 JAR contando el runtime Java. Se incluye:

1. Aplicación y dependencias runtime, con JVM y launchers.
2. mace_native.dll verificada por hash y ABI.
3. Helper autocontenido en app/lhm, localizado por la DLL sin rutas de desarrollo.
4. app/prerequisites/PawnIO_setup.exe oficial 2.2.0 intacto: SHA-256
   1F519A22E47187F70A1379A48CA604981C4FCF694F4E65B734AAA74A9FBA3032,
   firma Authenticode Valid. No se ejecutó su instalador.
5. THIRD-PARTY-NOTICES.txt y textos de licencia bajo app/third-party.

Helper EXE: DEAA3EC9CBA4EEA670471382C45348A8044C4D01E5C52FC90AAF7A1449068C19.
El manifiesto UAC extraído de MACE.exe exige requireAdministrator; Preview usa
asInvoker. No se lanzó la UI para obtener estos datos.

No hay fuentes, históricos, pruebas, scripts, PDB, import libraries, SDK binarios
retirados o documentación interna. Las bibliotecas jawt.lib y jvm.lib que jlink
copiaba inicialmente se excluyeron en la generación. Los JAR no incluyen fuentes
Java ni pruebas/históricos. Los avisos legales del runtime se conservan.
El fichero MACE-SHA256SUMS.txt, fuera de la imagen, lista hashes de todos sus archivos.

## Excepción de compatibilidad aprobada

La búsqueda binaria detectó dos símbolos en RAMSPDToolkit-NDD 1.4.2, dependencia
fijada de LHM. El usuario aprobó expresamente conservarlos como compatibilidad
sin driver activo. No se modifica ni se reempaqueta esa DLL de terceros.

| Archivo dentro de imagen | Offset byte ASCII | Símbolo | Motivo |
|---|---|---|---|
| app/lhm/RAMSPDToolkit-NDD.dll | 105819 | DriverImplementation.WinRing0 | Campo de enum de compatibilidad; sin implementación de driver |
| app/lhm/RAMSPDToolkit-NDD.dll | 127562 | IWinRing0Driver | Interfaz vacía de compatibilidad; sin métodos/cuerpos |

SHA-256 de esa DLL: 42C1CCAF90592542EC64E801AE599ED803BB98F4039B2ABB7119CB8EA2087CBA.
Se inspeccionaron metadatos PE con System.Reflection.Metadata, sin ejecutar la DLL:
solo esos tipos de compatibilidad, sin métodos ni recursos embebidos del driver.
El README del paquete NDD declara ausencia de implementación/binarios de drivers
predeterminados. Los símbolos no son el proveedor MSR de MACE ni una DLL WinRing0.

compatibility-references.json limita la excepción a ruta/hash/offsets exactos.
verify-image.ps1 rechaza otros cargadores y archivos de desarrollo. Su prueba
negativa rechazó una .lib temporal y una copia idéntica de NDD fuera de la ruta
aprobada; retiró ambos archivos y volvió a aprobar la imagen. Cero referencias
binarias injustificadas; las dos coincidencias aprobadas no se presentan como cero.
La auditoría textual por archivo/línea/columna está en docs/REFERENCIAS-ACEPTADAS-1D.tsv.

## Prueba de la imagen con hardware local

Se utilizó el JVM de la imagen, su JAR y su DLL, con MACE_LHM_HELPER y su hash
de entorno eliminados únicamente en el proceso de prueba. El helper se localizó
en app/lhm. Se leyeron ocho snapshots, y el cierre eliminó el helper propio.
Ryzen 5 3600: uso CPU y RAM Windows válidos; temperatura/potencia NO_PROVIDER
en esta ejecución sin elevación. La confirmación elevada anterior del usuario
sigue documentada en 1C; no se atribuye aquí una nueva prueba elevada.
RX 5500 XT: LHM, temperatura, potencia GPU_CHIP, uso, RPM y VRAM válidos;
hubo uso GPU 0 % legítimo. Máximo snapshot Java completo observado: 18.508 ms.
No es una garantía de latencia universal ni una inspección visual de la UI.

## Correcciones necesarias durante verificación

| Archivo | Corrección |
|---|---|
| native/scripts/native-common.ps1 | Capturar salida completa de cmake --version antes de seleccionar primera línea; evita código -1 falso en PowerShell 5.1 |
| native/scripts/verify-native.ps1 | Misma corrección al consultar java --version |
| packaging/package-windows.bat | Excluir .lib con jlink e invocar verificación de imagen completa |
| packaging/verify-image.ps1 | Revisar nombres/binarios/JAR y excepción NDD fijada |
| packaging/compatibility-references.json | Registrar aprobación, versión, hash, símbolos y offsets |
| packaging/tests/test-image.ps1 | Probar rechazo de desarrollo y limitación de excepción |
| native/scripts/audit-retired-providers.ps1 | Incluir como aceptadas las declaraciones de la política aprobada |

Commits de las dos correcciones de detección: 7e6c532 y 56132e2. Las otras
correcciones y este registro se guardan juntos después de comprobar la imagen.

## Evidencia y reproducción

Evidencias generadas e ignoradas por Git bajo native/build/1d-paso2:
helper.log, native-package.log, ctest-final.log/xml, app-image.log,
image-verification.json, image-files.json, image-negative-tests.log e
image-telemetry.log. ctest-final.xml incluye hostname; los logs contienen rutas
locales y propiedades del equipo. No se anonimizaron ni se publicaron originales.

Desde la raíz del repositorio, con JDK 22+, Maven, .NET y Visual Studio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\package-native.ps1 -BuildDirectory .\native\build\1d-paso2\compiled
# Fijar MACE_NATIVE_LIB/MACE_NATIVE_SHA256 al paquete recién generado;
# MACE_LHM_HELPER/MACE_LHM_HELPER_SHA256 al helper publicado para los tests Java;
# MACE_PAWNIO_INSTALLER al instalador oficial intacto y JAVA_HOME al JDK.
.\packaging\package-windows.bat app-image --with-tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\verify-image.ps1 -ImageDirectory .\packaging\dist\MACE
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image.ps1 -ImageDirectory .\packaging\dist\MACE
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

## NO VERIFICADO y parada

CPU Intel; NVIDIA NVML y respaldo; Intel GPU/IGCL; PawnIO ausente en equipo limpio;
HVCI con el conjunto actual; otras Radeon, APU/multi-GPU; instalación MSI/EXE y
comportamiento 3010; inspección visual de esta app-image elevada; reversiones
aisladas de tramos. La retirada no demuestra equivalencia en plataformas sin
validación física. Firma, licencia MACE y revisión de redistribución siguen pendientes.

Paso 2 terminado. Se espera aprobación antes de comenzar el Paso 3.
