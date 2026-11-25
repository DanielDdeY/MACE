# D — Descargas necesarias propuestas, ninguna autorizada/realizada ahora

Fecha: **2026-11-25**. Consulta de documentación/metadatos públicos solamente;
no se descargaron ZIP/JAR/NuGet/SDK, sidecars o claves ni se aceptó EULA.
Esta lista no es una orden de descarga: requiere aprobación expresa.
Rutas relativas al nuevo directorio R del plan, no al checkout de referencia.

## Herramientas y prerequisitos

| Insumo / versión exacta propuesta | URL oficial | Verificación futura | Ubicación y condición |
|---|---|---|---|
| Temurin **25.0.5.1+2**, Windows x64 HotSpot JDK ZIP | [ZIP versionado](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip) | SHA-256 contra .zip.sha256.txt oficial; GPG .zip.sig con clave autenticada si herramienta ya disponible. Leer release y java -version tras verificar | downloads/ y tools/temurin; única nueva línea JDK; **no MSI**, PATH/JAVA_HOME intactos |
| Sidecars de ese ZIP | [SHA-256](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip.sha256.txt), [firma](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip.sig) | No convertir checksum de mismo canal en garantía independiente de autenticidad; firma requiere clave confiable, no solo resultado “good” | downloads; no se ha leído su contenido ni verificado la firma |
| Apache Maven **3.9.16** ZIP, si no se reutiliza copia local verificada | [ZIP](https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip), [SHA-512](https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512), [ASC](https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.asc) | SHA-512 y, si disponible, firma con [KEYS oficial](https://downloads.apache.org/maven/KEYS) autenticada | tools/maven/apache-maven-3.9.16; no añadir al PATH. Local detectado por maven-core; integridad del bundle local **NV** |
| .NET SDK **10.0.401**, solo si falta exacto | [Página oficial .NET 10](https://dotnet.microsoft.com/en-us/download/dotnet/10.0), [metadata oficial de releases](https://builds.dotnet.microsoft.com/dotnet/release-metadata/10.0/releases.json) | Obtener de esa metadata enlace ZIP win-x64 y SHA-512 de 10.0.401 antes de aprobar descarga. Enlace/sidecar exacto todavía **NV** en esta entrega | tools/dotnet si se autorizara; host ya dispone del SDK fijado. No actualizarlo/instalarlo ahora |
| PawnIO setup **2.2.0 / FileVersion 2.2.0.0** | [Release oficial](https://github.com/namazso/PawnIO.Setup/releases/tag/2.2.0) | Pin en packaging/pawnio-2.2.0.json + Authenticode Valid del editor | Reutilizar archivo intacto de referencia como insumo solo lectura; descarga solo con aprobación si falta. **Nunca ejecutar/refirmar** |

Release Temurin identificada por GET de API el 25/11: tag jdk-25.0.5.1+2,
publicada 18/11/2026. Se fijan URLs versionadas, no usar latest al construir.
Fuentes: [Temurin ZIP/verificación](https://adoptium.net/installation/archives/),
[GPG](https://adoptium.net/installation/gpg-verification/),
[licencia Temurin](https://adoptium.net/docs/faq/),
[Apache Maven y mantenimiento 3.9.16](https://maven.apache.org/download.cgi).
Páginas abiertas el 25/11; ninguna descarga de artefactos para este plan.
No descargar Inno, NSIS, WiX, certificados o herramientas de firma. MSVC,
Windows SDK/mt, CMake y Python existentes se inventarían por lectura; si faltan,
el plan se detiene en vez de instalar herramientas sin permiso.

## Maven: coordenadas y URLs deterministas

Repositorio oficial: **https://repo.maven.apache.org/maven2/**.
Para grupo G, artefacto A, versión V y classifier C opcional:
`G/con/puntos/sustituidos/A/V/A-V[-C].jar`; POM `A-V.pom`.
No usar repositorios de terceros para suplir versiones no encontradas.

| Grupo | Artefactos / versiones exactas | Uso/ubicación |
|---|---|---|
| org.openjfx | javafx-base, javafx-graphics, javafx-controls, javafx-fxml **27.0.1.1**, JAR sin classifier y classifier **win** | Ocho JAR conocidos; POM de cada módulo + padre javafx 27.0.1.1. deps/m2 y staging runtime, **sin JMOD/SDK JavaFX** |
| org.mockito | mockito-core, mockito-junit-jupiter **5.20.0** | Tests; core también javaagent; nunca payload runtime |
| net.bytebuddy | byte-buddy, byte-buddy-agent **1.17.7** | Tests transitivos esperados; comprobar dependency:tree, no forzar una versión incompatible |
| org.objenesis | objenesis **3.3** | Dependencia declarada en catálogo Mockito v5.20.0; verificar árbol |
| io.github.mkpaz | atlantafx-base **2.0.1** | Existente, sin actualización; compatibilidad JavaFX 27 NV |
| org.junit / org.junit.jupiter | junit-bom **5.10.2**; junit-jupiter, api, engine, params **5.10.2** | Mantener línea existente; resolver únicamente cierre efectivo, sin añadir dependencias no usadas |
| org.apache.maven.plugins | compiler **3.13.0**, surefire **3.2.5**, dependency **3.8.1** | Plugins, no runtime. El nuevo goal properties evita ruta fija del agente |
| org.openjfx | javafx-maven-plugin **0.0.8** | Existente, no se usa para seleccionar Java en el plan; no actualizar |

Ejemplos exactos oficiales:

- [graphics win](https://repo.maven.apache.org/maven2/org/openjfx/javafx-graphics/27.0.1.1/javafx-graphics-27.0.1.1-win.jar)
- [FXML win](https://repo.maven.apache.org/maven2/org/openjfx/javafx-fxml/27.0.1.1/javafx-fxml-27.0.1.1-win.jar)
- [Mockito core](https://repo.maven.apache.org/maven2/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar)
- [Mockito JUnit](https://repo.maven.apache.org/maven2/org/mockito/mockito-junit-jupiter/5.20.0/mockito-junit-jupiter-5.20.0.jar)
- [Byte Buddy](https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.17.7/byte-buddy-1.17.7.jar)
- [Byte Buddy agent](https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy-agent/1.17.7/byte-buddy-agent-1.17.7.jar)
- [dependency plugin POM](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-dependency-plugin/3.8.1/maven-dependency-plugin-3.8.1.pom)

Maven debe usar checksumPolicy=fail; validar sidecars publicados, registrar
SHA-256/512 operativo por archivo y firmas .asc cuando existan con confianza
de clave acreditada. Nunca aceptar un .asc inexistente como firma validada.
Los checksums descargados del mismo canal no prueban por sí solos identidad
del editor. No imprimir hashes completos en informes; conservarlos en índice
operativo privado necesario para reproducibilidad y pins, no versionarlo aquí.

**Límite explícito:** no se ejecutó dependency:tree ni se resolvió el cierre
completo Maven/plugins. No se afirma que esta tabla sea toda la transferencia
de un caché frío; parents, BOM, transitivas y plugins predeterminados requieren
resolución. Sus versiones exactas restantes son **NO VERIFICADO**. La propuesta
de edición es completa, la evidencia de resolución todavía no lo es.
Antes de aprobar un lote binario cerrado: autorizar solo resolución de metadata
si se desea, congelar effective-pom/dependency:tree/índice completo, revisar
cualquier dependencia extra y aprobar ese índice. **No** interpretar este
plan como autorización amplia para descargar versiones desconocidas.
Reutilizar caché verificada evita transferencias, no elimina el inventario.

## NuGet: lista extraída de locks, ambas variantes

[CANDIDATA-NUGET-DESCARGAS.json](pendientes/CANDIDATA-NUGET-DESCARGAS.json)
enumera **27 pares paquete/versión** de locks actuales y el runtime pack
Microsoft.NETCore.App.Runtime.win-x64 **10.0.12** (28 entradas). Cada uno tiene
URL oficial flat-container, versión, condición y ubicación **deps/nuget**.
Incluye activos de otros RID del grafo de restore: no significa distribuirlos
en Windows. Mono 1.0.0 sigue en el grafo de restore aun con ExcludeAssets=all.

Verificación: `dotnet restore --locked-mode`, contentHash del lock, comprobación
de firma NuGet según política aplicable y después Verify-Artifacts/runtime.
La firma NuGet no acredita el derecho de redistribución. No cambiar hashes
de lock para aceptar bytes diferentes. Si SDK necesita otros targeting/runtime
packs fuera de esas entradas, detener y completar/aprobar índice antes de
descargarlos; el cierre de SDK no está acreditado como exhaustivo aquí.

## Decisiones antes de cualquier transferencia

Autorizar build Temurin exacta y fuentes; elegir reutilización o ZIP de Maven;
aprobar alcance de resolución y después índice cerrado de descargas/cachés.
No pedir datos personales ni aceptar contratos para esta preparación.
Integridad/firma efectiva de lo no descargado y legalidad del conjunto:
**NO VERIFICADO**. Toda publicación binaria sigue bloqueada por revisión legal.
