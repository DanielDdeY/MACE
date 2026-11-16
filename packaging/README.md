# Paquete de ejecución MACE

El repositorio conserva informes, scripts y pruebas. El producto solo recibe:

| Destino en app-image | Contenido |
|---|---|
| MACE.exe y MACE-Preview.exe | Launchers generados; el principal pide elevación |
| runtime/ | Java/JavaFX generados por jlink, incluidos sus avisos legales |
| app/*.jar | Aplicación actual y dependencias Maven de alcance runtime |
| app/mace_native.dll | DLL verificada por SHA-256 y ABI, de la revisión actual |
| app/lhm/ | Helper publicado autocontenido, assemblies, dependencias y configuración .NET |
| app/prerequisites/PawnIO_setup.exe | Instalador oficial 2.2.0 intacto, hash fijado y firma válida |
| app/THIRD-PARTY-NOTICES.txt y app/third-party/ | Inventario y textos de licencia |
| app/runtime-manifest.json | Hashes del payload; no es una firma de confianza |

No se copian carpetas de fuentes, SDK externos, cabeceras, diagnósticos de
desarrollo, pruebas, históricos, scripts, PDB, logs ni artifacts/. El helper se
obtiene de una publicación en artifacts, pero se copian solo sus archivos de
ejecución a lhm/. No se incluyen su ejecutable de pruebas ni createdump.exe.

## Preparación manual

1. Compilar/probar/publicar el helper con helper/Build-Helper.ps1.
2. Generar la DLL mediante native/scripts/package-native.ps1 desde una revisión
   registrada. Ese paquete de desarrollo no se copia entero al instalador.
3. Definir MACE_NATIVE_LIB/MACE_NATIVE_SHA256 con esa DLL y su hash. El script
   también puede elegir el paquete nativo más reciente, pero rechaza cargadores
   retirados; no reutilizar el paquete previo a esta limpieza.
4. Definir MACE_PAWNIO_INSTALLER con una copia oficial 2.2.0 descargada manualmente
   desde la URL registrada en pawnio-2.2.0.json. No se guarda el EXE en Git.
5. Opcional: MACE_HELPER_PUBLISH fija el directorio publicado; por defecto se lee
   helper/artifacts/latest.txt. JAVA_HOME debe apuntar al JDK completo 22+.
6. Ejecutar package-windows.bat app-image --with-tests; msi/exe requieren WiX.

stage-runtime.ps1 verifica el helper completo antes de seleccionar archivos,
comprueba hashes y firma de PawnIO, copia las cinco piezas y genera el manifiesto.
verify-runtime.ps1 rechaza archivos adicionales/faltantes o alterados; se invoca
también sobre app/ después de jpackage. Los únicos extras propios de jpackage
aceptados allí son sus dos archivos CFG y .jpackage.xml.

La copia de PawnIO no ejecuta su instalador. Instalación compartida, código 3010,
reinicio y conservación durante desinstalación requieren la fase posterior.
No se ha validado aquí una instalación limpia ni MSI/EXE.

## Firma y publicación

MACE_SIGNING_SCRIPT es un hook opcional que recibe -FilePath. La DLL se firma
en package-native.ps1 antes de fijar su hash. El helper propio se puede firmar
en Build-Helper.ps1 o durante staging. PawnIO y las bibliotecas de terceros
no se firman ni modifican. Sin hook se emite un aviso; la firma sigue pendiente.

THIRD-PARTY-NOTICES.txt registra versiones, metadatos y URL de código fuente.
Se conservan los textos incluidos en los paquetes restaurados. La licencia de
MACE, revisión de distribución de PawnIO/módulos y del runtime Java elegido,
y cumplimiento completo de avisos para una release pública siguen PENDIENTES.
No publicar binarios ni interpretar este inventario como autorización legal.

Los logs de compilación y Surefire contienen rutas/propiedades locales; el
diagnóstico añade PID, procesos y hardware. Permanecen fuera del paquete y Git.
Antes de compartir copias se propone sustituir rutas de usuario/equipo por
marcadores y retirar procesos/propiedades innecesarios. No se anonimizaron originales.

## Comprobación del payload sin instalar

packaging/tests/test-runtime-payload.ps1 recibe -NativeDll y -PawnIoInstaller;
opcionalmente -HelperDirectory y -JavaExe (JDK 22+, o JAVA_HOME/PATH). Comprueba
el verificador FFM/ABI real con el JAR de clases, copia completa/intacta y rechazos de
instalador incorrecto, DLL con cargador retirado, archivo interno añadido y helper
alterado. Usa carpetas únicas bajo native/build/1d-4 y nunca ejecuta PawnIO.
La imagen final mediante jpackage se verificó en el Paso 2; los resultados están
en ../helper/RESULTADOS-1D-PASO2.md. MSI/EXE e instalación limpia siguen pendientes.

verify-image.ps1 revisa además el runtime completo y el contenido de los JAR.
jlink excluye las bibliotecas .lib de desarrollo. La excepción de compatibilidad
aprobada para la dependencia NDD está fijada por ruta, SHA-256 y dos offsets en
compatibility-references.json; no autoriza otros archivos ni implementaciones.
packaging/tests/test-image.ps1 prueba el rechazo de una .lib añadida y de los
mismos bytes de la dependencia fuera de la ruta aprobada; elimina sus archivos
temporales y verifica otra vez la imagen final.
