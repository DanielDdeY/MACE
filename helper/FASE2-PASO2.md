# Fase 2 — Paso 2: arranque seguro e inventario de persistencia

Fecha: **17/11/2026, America/Lima**. Rama **LHM_implementado**.
Punto de partida: **220697310435c0c07bf967ed126073dd496032e7**, Paso 1B aprobado.
El árbol estaba limpio antes de editar. Este informe pertenece al commit único
**feat: Fase 2 Paso 2 pin helper runtime trust and isolate launch**; su hash final
se entrega en el chat. No se reescribe historial ni se fusiona a principal.

## Resultado y perfiles

Producción tiene **MACE_LHM_DEVELOPMENT=OFF por defecto**. La ruta del helper
proviene exclusivamente de lhm/Mace.Lhm.Helper.exe junto a la DLL cargada;
MACE_LHM_HELPER y MACE_LHM_HELPER_SHA256 no se leen ni permanecen como literales
en la DLL de producción comprobada. El perfil Development debe activarse
expresamente. Sus anulaciones siguen funcionando, pero staging rechaza esa DLL.

Se auditaron las lecturas de entorno del arranque: esas dos son las anulaciones
del helper existentes. MACE_SIM_* pertenece a fixtures de pruebas; únicamente
MACE_SIM_SCENARIO puede pasar al hijo en Development. MACE_HELPER_PUBLISH,
MACE_PAWNIO_INSTALLER y MACE_SIGNING_SCRIPT son entradas de herramientas de
construcción, no selectores del helper en ejecución, y no se heredan al hijo.
Los selectores Java de la DLL anfitriona son otra capa preexistente: no se
modifican sus launchers ni el JVM. El hash configurado del paquete sigue
protegiendo esa carga; el propio programa/JVM forma parte de la raíz de confianza.

| Selección | Uso |
|---|---|
| build-native.ps1, sin -Development | Producción; exige publicación verificada y fija el manifiesto final |
| build-native.ps1 -Development | Pruebas/desarrollo; permite ruta/hash del EXE por entorno |
| test-lhm1b.ps1 | Selecciona Development explícitamente y prueba también fixtures con perfil Production |
| CMake directo | -DMACE_LHM_DEVELOPMENT=ON para desarrollo; OFF y -DMACE_LHM_RUNTIME_MANIFEST=<archivo> para producción |
| package-native.ps1 | Siempre producción; exige fuente native/ registrada y conserva revisión/hash en build-info |

No cambian ABI **1.4**, tamaños/offsets, IDs ni estados: CPU V2 56 B, GPU V2 104 B,
inventario 296 B y TelemetryData 28 B. Continúa el protocolo privado 2 de 1B.
La inicialización y los sondeos siguen sin esperar verificaciones ni al helper.

## Cadena de construcción y manifiestos

1. Build-Helper compila/publica el helper autocontenido en una carpeta nueva.
2. Aplica el gancho de firma a sus archivos propios; hoy se omite con aviso.
3. Genera artifact-manifest.json con **205 archivos** de la publicación y
   helper-runtime.manifest con los **202 archivos de ejecución**.
4. CMake calcula el SHA-256 de helper-runtime.manifest y lo compila en la DLL.
5. package-native firma la DLL mediante su gancho y después calcula su SHA-256.
6. Staging verifica el conjunto y copia solo ejecución, PawnIO intacto y avisos.
   **Ya no vuelve a firmar el helper después de fijar su manifiesto en la DLL.**

La publicación de 205 contiene dos PDB y createdump.exe. Se mantiene/verifica
ese inventario completo durante construcción, pero no se incorporan esos tres
archivos al producto. El manifiesto runtime enumera **202**, no se afirma que
la DLL compruebe 205 archivos presentes. Esta separación conserva la política
aprobada de excluir símbolos/herramientas. El runtime del helper tiene 203
archivos contando su manifiesto.

El manifiesto runtime es ASCII sin BOM, encabezado MACE-LHM-RUNTIME 2 y líneas
SHA-256 hexadecimal, dos espacios y nombre simple. No contiene rutas locales.
El parser nativo impone tamaños máximos, nombres planos ASCII, ausencia de
reservados DOS, duplicados, traversal, directorios y archivos adicionales.
No se añade un parser JSON ni otra dependencia al receptor.

Si cambia un archivo después de firmar/calcular el manifiesto, la verificación
de publicación o de lanzamiento lo rechaza. Si se regenera el manifiesto tras
refirmar y no se recompila la DLL, staging rechaza el vínculo compilado. Si se
refirma la DLL después de fijar su hash de distribución, la verificación de
DLL/carga Java detecta el hash distinto. Hay que repetir el orden completo.
MACE_LHM_REQUIRE_AUTHENTICODE permite habilitar WinVerifyTrust sin UI y con
recuperación de certificados solo de caché; actualmente está OFF y emite aviso.
No se presenta como probado con certificado válido ni como pin de identidad
de publicador, que aún no se ha definido.

## Verificación en cada lanzamiento y localización

Cada LhmTransport_Open adquiere una LhmTrustLease nueva: no reutiliza la
verificación anterior. Esto incluye todos los relanzamientos del bucle existente.
La DLL comprueba el hash del manifiesto contra el compilado, después cada uno
de sus archivos y que no existan entradas adicionales en lhm/.

Obtiene nombres finales por handle y compara la carpeta canónica con lhm junto
a la DLL real. Rechaza UNC, reparse points en el directorio/archivos abiertos y
archivos cuyo nombre final no corresponda al esperado. Mantiene abiertos la
carpeta, manifiesto y archivos con FILE_SHARE_READ, sin compartir escritura ni
borrado, desde la comprobación hasta finalizar la sesión.

Una política fallida impide crear el proceso; no activa proveedores retirados.
Se conservan los estados existentes:

| Condición | Resultado LHM |
|---|---|
| Helper predeterminado ausente | NO_PROVIDER, sin lecturas de CPU inventadas |
| Manifiesto/dependencia ausente o alterado, archivo extra o ruta rechazada | READ_FAILED, valores marcadores no válidos |
| Sin elevación con helper íntegro | Temperatura/potencia CPU NO_PROVIDER; uso Windows continúa |
| Política válida | Se permite arranque y se aplican las reglas de validez/frescura existentes |

La prueba de relanzamiento termina únicamente el helper poseído, espera a que
se liberen los archivos, altera una dependencia y demuestra que el segundo
intento no ejecuta el helper. No se adelanta el presupuesto/backoff del Paso 4.

## Entorno, directorio de trabajo y escrituras del helper

CreateProcessW recibe CREATE_UNICODE_ENVIRONMENT, un bloque explícito ordenado
y el runtime canónico como directorio de trabajo. Producción solo pasa PATH
(directorios Windows/System32 obtenidos por API), SystemRoot, WINDIR, TEMP y TMP.
TEMP/TMP apuntan a AppData/Local/Temp de la cuenta mediante API de carpeta conocida,
no se copian del entorno del padre. No se pasan perfiles/nombres de equipo,
anulaciones LHM, hooks/profilers/dumps .NET ni PATH heredado. Development solo
añade el selector del helper simulado para mantener las pruebas existentes.

El código propio del helper no escribe configuración, logs ni cachés en
Program Files: envía datos por pipe y errores de tipo por stderr, que la DLL
redirige a NUL. La prueba local mantuvo 203 archivos en su directorio runtime,
sin archivos nuevos. No equivale a una traza exhaustiva de escrituras indirectas
de runtime, controladores, WER o sistema operativo, ni a instalación física en
Program Files. Si se necesitan datos propios persistentes del helper, la ruta
propuesta es %LOCALAPPDATA%\MACE\helper, con diagnóstico optativo y sin perfiles,
hostname ni PID en los mensajes. No se añadió esa persistencia ahora.

Los nuevos diagnósticos nativos son marcadores como LHM_TRUST_REJECTED y
LHM_SIGNATURE_SKIPPED. Las herramientas de compilación/prueba pueden imprimir
rutas por sí mismas; sus logs/XML permanecen ignorados y no se distribuyen.

## Límites y requisitos para Fase 3

| Límite | Reducción implementada | Lo que sigue abierto / requisito |
|---|---|---|
| Autenticidad | Hash del manifiesto compilado y comprobación de 202 archivos | Un hash no autentica al publicador; firma/certificado/identidad pendientes |
| TOCTOU de archivos | Se hashéan los handles que permanecen abiertos sin compartir escritura/borrado; las pruebas rechazan escrituras y renombrados durante la sesión | CreateProcessW vuelve a resolver una ruta; no se declara resistencia total a un administrador, memoria comprometida, mapeos previos o todas las carreras de ancestros |
| Ruta canónica | Carpeta derivada de DLL real, nombre final por handle, rechazo de reparse/UNC | No se ha probado físicamente cada variante de junction/symlink/volumen; el instalador debe controlar toda la cadena de carpetas |
| Dependencias tardías | Archivos propios/BCL del manifiesto retenidos durante la sesión; directorio/entorno explícitos | DLL del sistema, APIs gráficas/controladores y resolución externa que sus bibliotecas realicen no se fijan por este manifiesto |
| Archivos adicionales | Se rechazan entradas no inventariadas al verificar | El inventario es una observación; una carpeta escribible por el atacante no se convierte en una frontera segura |
| App-image actual | Se rechaza DLL Development al empaquetar | En una carpeta de usuario se puede sustituir el programa/JVM/DLL antes del arranque; no es una instalación protegida |
| Permisos de instalación | Se documentan, no se cambian en este equipo | Fase 3: dueño Administradores/SYSTEM, escritura solo Administradores/SYSTEM, usuarios con lectura/ejecución; sin herencia que otorgue Modify/Write a usuarios en app/, lhm/, runtime/ y padres |
| Publicación/actualización | Manifiesto después de firma; DLL y helper ligados | Actualizar ambos con MACE cerrado; revisar ACL y rechazar destinos controlados mediante enlaces. No ejecutado aquí |
| Authenticode | Gancho compilable, sin UI ni red | Certificado y política de publicador pendientes; cadena válida/firma real NO VERIFICADAS |

Referencias primarias sobre
[compartición de archivos](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilea),
[WinVerifyTrust](https://learn.microsoft.com/en-us/windows/win32/api/wintrust/nf-wintrust-winverifytrust)
y [verificación sin consultas de red](https://learn.microsoft.com/en-us/windows/win32/api/wintrust/ns-wintrust-wintrust_data).
La semántica documentada y la inspección se distinguen de las pruebas ejecutadas.

## Auditoría de persistencia — inventario sin cambios de código

Se buscaron escrituras en app/src/main/java, native/src y el helper propio.
git diff frente a 2206973 confirma que **ningún archivo de Java, HUD o persistencia
se modificó en este paso**. La auditoría es de implementación, no una prueba
de fallo de disco, apagado eléctrico ni halt durante cada escritura.

| Dato / destino | Cuándo y evidencia | Atomicidad y riesgo ante halt |
|---|---|---|
| Preferencias Micro-HUD: enabled, métricas, opacidad, GPU, x/y | HudPane usa Preferences.userNodeForPackage y putBoolean/putInt/putDouble al cambiar controles o ocultarse. En Windows/JDK corresponden al almacén de preferencias de usuario, normalmente HKCU\Software\JavaSoft\Prefs\com\mace\presentation\view\hud | No hay transacción conjunta ni snapshot/renombrado propio; x e y se guardan por separado y no hay flush explícito de la aplicación. Halt puede dejar una combinación anterior/nueva o no guardar el último cambio; no se afirma corrupción de toda la rama del registro |
| Caja negra *.mbbx | BlackboxSettings.defaultDirectory(): -Dmace.blackbox.dir, si no %LOCALAPPDATA%\MACE\blackbox; fallback ~/.mace/blackbox, simulated separado. PersistentBlackboxSink.open/rotate crean partes nuevas con fecha UTC/PID/parte | CREATE_NEW evita sobrescribir archivos anteriores, pero no hace atómico el contenido nuevo. Un override puede apuntar a otra carpeta; no se cambió esta política |
| Cabecera inicial de caja negra | BlackboxFileWriter.create(): escribe cabecera y force(true) | No hay archivo temporal/rename; interrupción antes/durante confirmar puede dejar cabecera parcial/archivo corto |
| Registros de CPU/GPU/RAM/procesos/eventos | record() añade al anillo; flush()/recordEvent() drenan. append(): escribe registro posicional con CRC32, incrementa committed y reescribe cabecera | No es una transacción entre registro y cabecera. Halt durante writeFully o actualización puede dejar cola truncada, CRC inválido o contador/cabecera no coherentes. CRC detecta daño, no asegura durabilidad |
| Confirmación periódica | MonitoringService.publish(): blackbox.flush() cada ciclo (periodo predeterminado 1 s); también al cerrar. BlackboxFileWriter.force(): channel.force(false) | Reduce datos pendientes, pero un write/force en curso puede no completarse; no se garantiza conservar la última muestra ni tolerancia a fallo eléctrico |
| Cierre limpio | PersistentBlackboxSink.close(): drain, evento SESSION_END, writer.close; este reescribe cabecera cleanClose y force(true), después libera anillo | Halt puede impedir SESSION_END/cleanClose o interrumpir la cabecera/force. El lector no debe confundirlos con cierre confirmado |
| Rotación y retención | rotate(): cierra archivo anterior, applyRetention() elimina los más antiguos y crea la siguiente parte | No es una transacción de carpeta. Halt tras eliminar y antes de crear/confirmar puede reducir historial sin una parte nueva; no se implementó recuperación de retención |
| Recuperación del historial | BlackboxFileReader.read(): valida cabecera/CRC y recorre registros de tamaño fijo, secuencia y CRC; scanIncomplete conserva originales | Puede recuperar un prefijo válido incluso con cabecera inválida. Archivo menor que HEADER_SIZE queda sin registros; cola incompleta se marca dañada. No repara ni reescribe el original y no garantiza recuperar todo |
| Anillos nativos y compartidos | Blackbox_Create/Append y SharedBuffer usan memoria/file mappings con INVALID_HANDLE_VALUE; Java mantiene también ring y wallTimes | Son datos volátiles; no archivos de historial propios. Halt pierde lo no drenado. El pagefile del SO no convierte el anillo en persistencia de la aplicación |
| Gráficas e historial de la sesión UI | MetricHistory/GpuCardViewModel/MainDashboardViewModel conservan listas de puntos en RAM | Se pierden al salir; no se encontró otro archivo/base de datos de historial de esas gráficas |
| Diagnóstico | Java utiliza logging/console; helper stderr va a NUL. Herramientas escriben logs/XML en build/target/artifacts | Salida redirigida puede quedar cortada. Configuración externa de logging, informes fatales JVM y WER son otra capa y no se inventaría como un formato persistente propio de MACE |
| Configuración/JAR/manifiestos de producto | Se generan en compilación/empaquetado; en ejecución se leen | El helper no los reescribe. La construcción no es una actualización transaccional de una instalación; Fase 3 debe definirla |

La caja negra puede contener PID/identidades de proceso, horas, métricas y detalles
de eventos. No se deben publicar archivos de usuario ni logs brutos sin revisión.
No se leyeron ni modificaron los historiales/preferencias personales del equipo.
La ubicación de preferencias Windows se contrastó con WindowsPreferences.java
del src.zip del JDK 22 instalado, sin consultar los valores del registro.

### Propuestas pendientes de aprobación, no implementadas

1. Cabeceras redundantes con generación/CRC o journal de commit para evitar
   una única cabecera sobrescrita; definir orden registro -> confirmación y
   pruebas de interrupción en cada frontera. No prometer atomicidad por usar CRC.
2. Partes selladas mediante archivo temporal, force y renombrado atómico cuando
   el sistema de archivos lo admita, con fallback explícito y pruebas Windows.
   No basta renombrar para garantizar durabilidad del directorio/medio.
3. Retención posterior a confirmar la nueva parte, para no borrar antes de
   asegurar la continuidad; recuperación que preserve originales dañados.
4. Preferencias como snapshot versionado/coherente (especialmente x/y y selección
   de métricas), o una política explícita de tolerancia a cambios no guardados.
5. Añadir pruebas de halt/corte de escrituras para persistencia en una fase
   autorizada, con archivos sintéticos y sin tocar datos reales.

Estas propuestas requieren aprobación separada. El watchdog de 1B y la política
de persistencia permanecen exactamente como estaban.

## Resultados de pruebas

| Batería | Resultado |
|---|---|
| Helper | **86 aprobadas**, publicación nueva y sus 205 archivos verificados |
| CTest Development | **57/57**, las 49 existentes más ocho pruebas de seguridad |
| CTest Production | **58/58**, las anteriores y prueba de la DLL real que ignora entorno |
| Java/JavaFX | **286 aprobadas + 1 omitida**, cero fallos/errores; con perfil Development explícito |
| Payload | **8 aprobadas + rechazo adicional de DLL Development** |
| Vías protegidas / referencias | check-protected-telemetry y audit-retired-providers aprobados al cierre |

Nuevos casos: ruta/hash ignorados en producción, manifiesto/dependencia ausentes
o alterados, extras rechazados, escritura/renombrado bloqueados durante la sesión,
reverificación tras terminar el helper y entorno/directorio del hijo controlados.
Los fixtures usan proveedores/helpers simulados y políticas de producción reales;
no se presentan como validación física de sensores.

La preparación inicial de algunos fixtures encontró retención transitoria de
archivos entre procesos. Se acotó el reintento de copia a 2,5 s, manteniendo
intacta la política de rechazo; las baterías finales completas pasaron.

Prueba física local adicional **sin elevar**, con DLL Production y runtime
publicado en su carpeta esperada: inicialización 0,044 ms, primera disponibilidad
1844 ms y máximo sondeo individual 1,986 ms en ocho segundos. GPU local válida;
temperatura/potencia CPU NO_PROVIDER, uso CPU Windows válido. No se ejecutó
-RequireCpu ni se atribuye una nueva prueba elevada al usuario. Son medidas
locales, no garantías universales. El runtime mantuvo 203 archivos.
Tras salir, también se volvieron a comprobar los hashes de los 202 archivos y
coincidían con el manifiesto; no se modificaron durante esa ejecución local.

| Artefacto comprobado | SHA-256 |
|---|---|
| helper-runtime.manifest | EE3029241B41F66180826DDC9197271DD1078C692C4332FD7795DC2AFA9958FF |
| DLL Production | 3F34E24C728A40BF8DADCD77F0566B113F96DC52CC3ECAADEB52215AC143BED8 |
| DLL Development | 0DEFCB4B64B5B0EB5C1E596889EC4C84B6D8DA377E5C85E77C72435923156ED1 |

Se verificó staging (214 archivos, cinco componentes); no se regeneró
packaging/dist/MACE ni se ejecutó jpackage/instalador. La app-image sigue siendo
la de 1D hasta el Paso 5, según lo solicitado.

## Cambios por archivo

| Archivo | Cambio |
|---|---|
| helper/Write-RuntimeManifest.ps1 | Formato canónico, filtro de ejecución, validación/verificación exacta |
| helper/Build-Helper.ps1 | Generar manifiesto después de la firma y del inventario completo |
| helper/Verify-Artifacts.ps1 | Verificar 205 y manifiesto runtime adicional |
| native/cmake/lhm_security_config.h.in | Configuración/pin compilados, sin rutas del desarrollador |
| native/CMakeLists.txt | Production por defecto, opción Development, fuente trust y targets de seguridad |
| native/src/telemetry/lhm_trust.c y .h | Verificación, lease, ruta canónica, entorno mínimo y gancho Authenticode |
| native/src/telemetry/lhm_transport.c y .h | Adquirir/retener lease en cada Open, entorno/directorio explícitos; retirar verificación de EXE antigua duplicada |
| native/src/telemetry/lhm_receiver.c | Compilar anulaciones solo en Development; quitar hash una sola vez antes del bucle |
| native/scripts/native-common.ps1 | Resolver publicación/manifiesto verificados para producción |
| native/scripts/build-native.ps1 | Selección explícita de perfil y parámetro HelperDirectory |
| native/scripts/package-native.ps1 | Forzar producción y registrar protocolo/pin en metadatos |
| native/scripts/test-lhm1b.ps1 | Habilitar Development explícitamente, conservar pruebas existentes |
| native/scripts/make-lhm-security-fixture.ps1 | Generar fixture sintético y pin independiente para pruebas |
| native/tests/lhm_fake_helper.c | Canary de entorno/CWD para target de seguridad, nunca en producto |
| native/tests/test_lhm_security.c | Ocho escenarios de producción simulada y manipulación/relanzamiento |
| native/tests/test_lhm_production_profile.c | Comprobar DLL Production real con entorno adverso y helper predeterminado ausente |
| packaging/stage-runtime.ps1 | Rechazar Development/vínculo distinto, copiar manifiesto y no refirmar helper |
| packaging/verify-runtime.ps1 | Comprobar manifiesto ligado a DLL y dependencias también contra ese manifiesto |
| packaging/tests/test-runtime-payload.ps1 | Conservar ocho casos y permitir rechazo adicional de DLL Development |
| helper/PROTOCOL.md, helper/README.md, native/README.md, packaging/README.md | Documentación operativa y orden actualizado |
| helper/FASE2-PASO2.md | Este informe; inventario de persistencia sin cambios de implementación |

## Reproducción

Desde la raíz, con .NET fijado, MSVC/Windows SDK, JDK 22 y Maven:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\helper\Build-Helper.ps1
# Si hay certificado: pasar -SigningScript aqui, antes de compilar la DLL.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\build-native.ps1 -BuildDirectory .\native\build\fase2-paso2\prod
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso2\dev
$helperDirectory=(Get-Content .\helper\artifacts\latest.txt -Raw).Trim()
$env:JAVA_HOME='C:\Program Files\Java\jdk-22' # Ajustar a JDK instalado.
$env:MACE_NATIVE_LIB=(Resolve-Path .\native\build\fase2-paso2\dev\Release\mace_native.dll).Path
$env:MACE_NATIVE_SHA256=(Get-FileHash -LiteralPath $env:MACE_NATIVE_LIB -Algorithm SHA256).Hash
$env:MACE_LHM_HELPER=Join-Path $helperDirectory 'Mace.Lhm.Helper.exe'
$env:MACE_LHM_HELPER_SHA256=(Get-FileHash -LiteralPath $env:MACE_LHM_HELPER -Algorithm SHA256).Hash
$env:MACE_WATCHDOG_FAKE_HELPER=(Resolve-Path .\native\build\fase2-paso2\dev\Release\lhm_fake_helper.exe).Path
mvn.cmd -o -f .\app\pom.xml test
$prodDll=(Resolve-Path .\native\build\fase2-paso2\prod\Release\mace_native.dll).Path
$pawn=(Resolve-Path .\packaging\dist\MACE\app\prerequisites\PawnIO_setup.exe).Path
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-runtime-payload.ps1 -NativeDll $prodDll -DevelopmentDll $env:MACE_NATIVE_LIB -HelperDirectory $helperDirectory -PawnIoInstaller $pawn -JavaExe "$env:JAVA_HOME\bin\java.exe"
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\check-protected-telemetry.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\audit-retired-providers.ps1
```

Maven offline necesita dependencias ya restauradas. -RealHelper/-RequireCpu se
mantienen en test-lhm1b con Development; RequireCpu exige permisos/driver que
no se proporcionan ni instalan automáticamente. Tras registrar la fuente,
package-native.ps1 genera el paquete nativo de producción y puede firmar la DLL;
no se hizo un paquete nativo firmado en esta prueba sin certificado.

Evidencias ignoradas: native/build/fase2-paso2/{helper,dev,prod-tests,java,payload,
production-live}.log; XML de CTest en dev/ y prod/ y Java en target/surefire-reports.
Pueden contener rutas y datos locales de las herramientas. No se anonimizan
originales ni se suben logs/binarios/paquetes en este commit.

## NO VERIFICADO y parada

- Ejecución instalada en Program Files con ACL definitivas, GUI elevada completa,
  coexistencia con LHM del usuario, suspensión/reanudación y todas las carreras
  de junction/symlink/mapeos de escritura previos: NO VERIFICADO físicamente.
- CPU Intel, NVIDIA NVML/respaldo, Intel GPU/IGCL, PawnIO en equipo limpio,
  HVCI actual, MSI/EXE y 3010 real: NO VERIFICADO.
- Certificado válido, firma definitiva, identidad de publicador y políticas de
  revocación offline reales: NO VERIFICADO; ganchos conservados.
- Halt a mitad de cada escritura, fallo eléctrico/del medio, reparación de datos
  y atomicidad de preferencias: NO VERIFICADO; solo inventario/correcciones propuestas.
- Auditoría exhaustiva de escrituras de dependencias/OS y rutas de depuración
  embebidas en todos los binarios: NO VERIFICADO.

No se modificaron proveedores protegidos, ABI, estados, manifiesto UAC,
launchers, Java/persistencia ni watchdog. No se instaló/desinstaló PawnIO,
no se ejecutó su instalador y no se cambió HVCI. Firma, licencia y revisión de
redistribución continúan pendientes. No se requiere una decisión nueva para
este commit; las propuestas de persistencia esperan aprobación separada.

**Paso 2 terminado. Se detiene el trabajo hasta aprobación antes del Paso 3.**
