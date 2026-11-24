# Fase 3 — 3.1c, Paso A: inventario de licencias y conflictos

Fecha: **2026-11-23**, America/Lima. Partida: **bd08310**, rama
`LHM_implementado`. Al empezar solo había dos documentos previos sin seguimiento:
`helper/GUIA-RELEVO-AGENTE.md` y
`helper/INFORME-LHM-PAWNIO-DOTNET-FIRMAS-LICENCIAS.md`. No se incluyen en este commit.

**Entrega documental para revisión, no autorización de distribución ni admisión
en SignPath.** MIT está elegida y confirmada para el código propio, con titular
declarado **DanielDdeY**. No se crea LICENSE, política de firma, workflow ni
instalador. Los pasos B–E y 3.2–3.9 siguen pendientes. Este análisis técnico
no es asesoría legal; los bloqueos de derechos requieren revisión cualificada.

**Corrección tras auditoría, 2026-11-23:** se conserva la historia en Git
(`d922d99`). Se corrigen el recuento de JAR (10, no 11), el estado del enlace
de Mono y siete carencias de presentación/acreditación. Registro antes/después
en [auditoría](FASE3-LICENCIA-AUDITORIA.md); lectura breve en
[resumen](FASE3-LICENCIA-RESUMEN.md). No se cambian producto ni licencias elegidas.
Las tablas ahora separan las once columnas y las 103 filas (34 principales,
23 avisos Java y 46 avisos .NET): **no son 103 dependencias independientes**.
La fuente documentada no acredita por sí sola todos los bytes ni su elegibilidad.

## 1. Alcance, método y conclusión

Se releen `3.1c.md`, `prompt_mace_fase3.md`, [diseño](FASE3-DISENO.md),
[adenda](FASE3-DISENO-ADENDA.md), [cierre de Fase 2](FASE2-CIERRE.md) y
[decisiones vigentes](FASE3-3.1C-DECISIONES-Y-CONTINUIDAD.md).
Los tres borradores LICENSE, CODE_SIGNING_POLICY.borrador.md y
README.borrador.md no están disponibles con esos nombres en las ubicaciones
revisadas. No se atribuye contenido a esos borradores.

Se inspeccionan archivos de la imagen congelada, ZIP/JAR/NuGet como datos,
POM, nuspec, lock, deps, metadatos PE/CLI, recursos y firmas Authenticode.
No se ejecuta MACE/helper ni se carga su código para inspeccionarlo. PEReader
lee tablas de metadatos, no ejecuta ensamblados. Solo se extraen copias de DLL
del JAR JavaFX a un directorio de análisis ignorado bajo `native/build/`;
**no se extrae, ejecuta, modifica ni refirma PawnIO_setup.exe**.

La imagen tiene **370 archivos, 147.787.297 bytes**:

| Grupo disjunto | Archivos | Bytes | Cobertura del inventario |
|---|---:|---:|---|
| `app/lhm/` | 203 | 85.548.222 | 202 archivos de ejecución y helper-runtime.manifest; dependencias y runtime .NET abajo |
| `runtime/` | 141 | 46.737.166 | Runtime Java, legales de sus 11 módulos y CRT de Microsoft |
| `app/third-party/` | 7 | 328.790 | Textos de licencia/avisos identificados abajo |
| Resto | 19 | 15.173.119 | Dos launchers, 10 JAR, DLL propia, dos cfg, XML de jpackage, manifiesto global, avisos y setup PawnIO |
| **Total** | **370** | **147.787.297** | El contenido nativo dentro de JAR y DLL requiere además las subtablas |

No existe aún un instalador definitivo MACE. «Viaja» significa presente en la
imagen base prevista como payload; no acredita un EXE Inno construido. Se
separan dependencias copiadas, recursos embebidos, código enlazado,
dependencias del sistema y paquetes presentes solo en el grafo de restauración.
No se confunde una lista de avisos multiplataforma con prueba de ejecución
de cada componente en Windows.

**Resultado:** el inventario técnico identifica las familias del payload y
sus componentes embebidos conocidos. La acreditación legal completa **NO está
cerrada**: Mono, el setup/driver/módulos exactos de PawnIO, licencias de algunos
subcomponentes y su correspondencia con fuentes siguen NO VERIFICADOS. No
sería correcto declarar que todas las filas son OSI o elegibles. Inno, Oracle
NFTC, la cabecera IGCL y los redistribuibles Microsoft exigen resolver su encaje
con el programa, además de tener derechos de redistribución.

## 2. Cómo leer las tablas y las evidencias

- `NOASSERTION`: no se ha acreditado una identificación SPDX suficiente.
  `LicenseRef-*` es un identificador local descriptivo, **no** una licencia
  aprobada por SPDX/OSI. No se inventan identificadores de excepciones.
- «OSI sí» califica la licencia identificada, no el paquete completo ni su
  admisión por SignPath. «NV» significa **NO VERIFICADO**, nunca «aprobado».
- Doble licencia: la comprobación individual no permite acreditar ausencia
  de otros contratos comerciales; queda **NO VERIFICADO** por fila. El driver
  anuncia alternativas, sin acreditar aquí un contrato comercial concreto.
  Dos licencias libres alternativas no equivalen a doble licencia comercial.
- Firmas: `NS` = Authenticode NotSigned; `V(editor)` = Valid en esta inspección;
  `J0` = sin entradas de firma JAR detectadas. Una firma no concede licencias.
- Elegibilidad: `P` = candidato propio, condicionado a formalizar MIT y al
  conjunto; `T` = tercero, conservar bytes/firma y **no firmarlo como propio**;
  `B` = bloqueo o consulta pendiente; `F` = futuro/no empaquetado. Ninguna letra
  significa admisión de MACE.

Evidencias locales, relativas al checkout o a cachés simbólicas sin datos locales:

| ID | Evidencia |
|---|---|
| E1 | `packaging/dist/MACE/`, `packaging/dist/MACE-SHA256SUMS.txt`; metadatos de archivos y firmas leídos, sin ejecutar |
| E2 | `app/pom.xml`; POM restaurados de OpenJFX 22.0.2 y AtlantaFX 2.0.1 en `<cacheMaven>`; entradas ZIP de los 10 JAR reales |
| E3 | `helper/Mace.Lhm.Helper/packages.lock.json`, `obj/project.assets.json`; `app/lhm/Mace.Lhm.Helper.deps.json` en la imagen |
| E4 | nuspec y archivos de licencia/avisos de cada versión exacta en `<cacheNuGet>`; sus revisiones declaradas, cuando existen |
| E5 | `packaging/THIRD-PARTY-NOTICES.txt` y los siete archivos `packaging/third-party/`; no se reescriben todavía |
| E6 | `runtime/release`, `runtime/legal/*/LICENSE` y los 23 avisos `.md` distintos del runtime empaquetado |
| E7 | `native/vendor_headers/intel_igcl/LICENSE`, `igcl_api.h`, `native/CMakeLists.txt`, proveedores NVML/IGCL propios |
| E8 | Tablas AssemblyReference/ManifestResource de las DLL reales: 183 ensamblados administrados; recursos PawnIO de LHM |
| E9 | `packaging/pawnio-2.2.0.json`, `packaging/compatibility-references.json`; pins existentes usados solo como comparación |

Las revisiones declaradas se localizan en E4/E5: LHM `3d331e3`, BlackSharp
`c70b735`, DiskInfoToolkit `25319ea`, NDD `3b47b96`, .NET Runtime `95017c7`,
CodeDom/Management `4452502`, Ports `c2435c3`. No son hashes completos de
binarios ni prueba de recompilación reproducible. La ausencia de repositorio
en un nuspec no se rellena atribuyéndole el repositorio de otra versión.

### 2.1 Artefactos propios y componentes seleccionados por el helper

| Componente | Version exacta | Licencia SPDX | Aprobada OSI | Doble licencia comercial | Fuente de evidencia | Viaja en instalador | Quien lo compila | Quien lo firma hoy | Elegibilidad SignPath | Accion necesaria |
|---|---|---|---|---|---|---|---|---|---|---|
| Aplicación Java MACE | JAR 1.0.0-SNAPSHOT | MIT decidida; formalizacion pendiente B. MIT elegida, aún sin LICENSE | sí para MIT; formalización de MACE pendiente; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | MACE/Maven; Java | Propio, J0 | P | B formaliza licencia; no asumir que firmar PE firma el JAR |
| MACE.exe y MACE-Preview.exe | Producto 1.0.0 | MIT decidida; formalizacion pendiente B. Código MACE MIT pendiente; stub jpackage y CRT con términos propios | NO VERIFICADO; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | jpackage; stub ajeno | Generados por jpackage, NS ambos | P/B | revisar stub/runtime y C10, no tratar todo byte generado como autoría propia |
| mace_native.dll | Archivo 1.5.0.0, ABI 1.5 | MIT decidida; formalizacion pendiente B. MIT elegida + cabecera IGCL + CRT estático | conjunto NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | MACE/MSVC; CRT ajeno | C propio con componentes indicados, NS | P/B | C6/C10, mantener NVML/IGCL y 4A |
| Mace.Lhm.Helper.exe y DLL propia | 1.1.1; archivo 1.1.1.0 | MIT decidida; formalizacion pendiente B. MIT elegida; apphost .NET MIT y avisos | sí para esas licencias; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | .NET publish; apphost ajeno | Propio generado con .NET, PE propios NS | P | resolver conjunto antes de firma, no firmar dependencias como propias |
| Mace.Lhm.Protocol.dll | 1.0.0.0; wire privado 2 | MIT decidida; formalizacion pendiente B. MIT elegida | sí para MIT; formalización de MACE pendiente; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | MACE/.NET SDK | Propio, NS | P | no confundir versión de ensamblado con wire/helper; C10 |
| LibreHardwareMonitorLib | 0.9.6 | MPL-2.0; recursos con términos separados | sí para MPL | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/librehardwaremonitorlib/0.9.6/librehardwaremonitorlib.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, NS | T/B | fuentes/avisos MPL y módulos PawnIO abajo |
| BlackSharp.Core | 1.0.7 | MPL-2.0 | sí | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/blacksharp.core/1.0.7/blacksharp.core.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, NS | T | conservar avisos y fuentes correspondientes |
| DiskInfoToolkit | 1.1.2 | MPL-2.0 | sí | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/diskinfotoolkit/1.1.2/diskinfotoolkit.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, NS | T | mismas obligaciones MPL |
| RAMSPDToolkit-NDD | 1.4.2 | MPL-2.0 | sí | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/ramspdtoolkit-ndd/1.4.2/ramspdtoolkit-ndd.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, NS | T | excepción de compatibilidad limitada al archivo/pin y offsets 105819/127562, sin driver activo |
| HidSharp | 2.6.4 | Apache-2.0 | sí | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `<cacheNuGet>/hidsharp/2.6.4/LICENSE.txt` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, NS | T | corregir posteriormente inventario que solo dice LICENSE.txt, conservar texto/avisos aplicables |
| Mono.Posix.NETStandard.dll | Paquete 1.0.0, assembly 1.0.0.0 | NO VERIFICADO para el artefacto; atribucion previa: NOASSERTION | NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, V(Xamarin Inc.) | B | firma y nombre no acreditan licencia exacta |
| MonoPosixHelper.dll y libMonoPosixHelper.dll | Paquete 1.0.0; FileVersion no declarada | NO VERIFICADO para el artefacto; atribucion previa: NOASSERTION | NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Terceros, V(Xamarin Inc.) ambos | B | C4 incluye los tres, no solo DLL administrada |
| Microsoft.NETCore.App.Runtime.win-x64 | 10.0.12 | MIT + avisos de componentes | sí MIT, resto por aviso | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `<cacheNuGet>/microsoft.netcore.app.runtime.win-x64/10.0.12/LICENSE.TXT` | Sí, autocontenido | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero; coreclr V(.NET); no afirmar firma de cada archivo por ese ejemplo | T/B | conservar LICENSE/THIRD-PARTY-NOTICES, acreditar subcomponentes y correspondencia |
| System.CodeDom | 10.0.2 | MIT + avisos | sí MIT | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/system.codedom/10.0.2/system.codedom.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, V(.NET) | T | conservar avisos |
| System.IO.Ports | 10.0.3 | MIT + avisos | sí MIT | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/system.io.ports/10.0.3/system.io.ports.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, V(.NET) | T | distinguir grafo de otros RID, que no se copia |
| System.Management | 10.0.2 | MIT + avisos | sí MIT | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheNuGet>/system.management/10.0.2/system.management.nuspec` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, V(.NET) | T | conservar avisos |

El runtime .NET aporta el resto de DLL de System/Microsoft y sus motores
nativos (hostfxr, hostpolicy, coreclr, JIT, etc.) como **una distribución
10.0.12**, no 183 dependencias NuGet independientes. Los archivos de control
deps/runtimeconfig/manifiestos describen el producto y no son librerías con
licencia propia adicional. Las firmas de cada DLL no inspeccionada siguen NV.
`createdump.exe` y PDB del publicado completo no están en esta imagen.

**Advertencia de versión:** `runtime-manifest.json` usa `helperVersion=0.9.6`
para la biblioteca LHM. No es la versión del ejecutable helper; esta es
**1.1.1**, comprobada en deps/PE/proyecto. No se modifica el formato ni el pin.

### 2.2 Java, JavaFX y bibliotecas nativas contenidas

| Componente | Version exacta | Licencia SPDX | Aprobada OSI | Doble licencia comercial | Fuente de evidencia | Viaja en instalador | Quien lo compila | Quien lo firma hoy | Elegibilidad SignPath | Accion necesaria |
|---|---|---|---|---|---|---|---|---|---|---|
| Runtime jlink Oracle Java | 22.0.2; build detallada no presente en release reducido | LicenseRef-Oracle-NFTC + términos separados | no para NFTC | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/LICENSE` | Sí, 11 módulos | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero; firma de todos sus PE NV | B | migración separada aprobada, proveedor/build por concretar |
| OpenJFX base, controls, fxml, graphics | 22.0.2; ocho JAR, normal y win por módulo | GPL-2.0-only WITH Classpath-exception-2.0 para código OpenJFX; nativos ajenos aparte | sí GPL base | NO VERIFICADO; no se acredita ausencia de acuerdos | METADATO_PAQUETE: `<cacheMaven>/org/openjfx/javafx/22.0.2/javafx-22.0.2.pom` | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, J0 en los ocho JAR | T/B | añadir textos/avisos exactos y acreditar fuente/build correspondiente |
| Nativos OpenJFX dentro de graphics-win | 22.0.2; siete DLL | NO VERIFICADO para el artefacto; atribucion previa: Licencia OpenJFX más subcomponentes nativos: identificación completa NV | NO VERIFICADO; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí, dentro del JAR | Distribucion ajena; compilador exacto NO VERIFICADO | Terceros, NS los siete | T/B | decora_sse, glass, javafx_font, javafx_iio, prism_common, prism_d3d, prism_sw; no refirmar |
| AtlantaFX base | 2.0.1 | MIT | sí | NO VERIFICADO; no se acredita ausencia de acuerdos | URL_OFICIAL: [fuente oficial](https://raw.githubusercontent.com/mkpaz/atlantafx/v2.0.1/LICENSE) | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, J0 | T | incluir licencia/atribución en B/3.7 |
| Microsoft VC runtime dentro de JavaFX | 14.36.32532.0, cinco DLL | NO VERIFICADO para el artefacto; atribucion previa: LicenseRef-MSVC-Redistributable | no acreditada como OSI; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí, dentro del JAR | Distribucion ajena; compilador exacto NO VERIFICADO | V(Microsoft Windows Software Compatibility Publisher) | B | consultar excepción de bibliotecas de sistema y derechos aplicables; no refirmar |
| Microsoft UCRT/API sets dentro de JavaFX | 10.0.17763.132; 42 DLL | NO VERIFICADO para el artefacto; atribucion previa: LicenseRef-Microsoft-UCRT | no acreditada como OSI; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí, dentro del JAR | Distribucion ajena; compilador exacto NO VERIFICADO | V(Microsoft Corporation) | B | no atribuir licencia GPL a estos binarios por estar en el mismo JAR |
| Microsoft VC runtime en runtime/bin | 14.36.32532.0; tres DLL | NO VERIFICADO para el artefacto; atribucion previa: LicenseRef-MSVC-Redistributable | no acreditada como OSI; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí | Distribucion ajena; compilador exacto NO VERIFICADO | Tres V; atribución Microsoft, no firma propia | B | msvcp140, vcruntime140, vcruntime140_1; revisar también al migrar JDK |
| Subcomponentes declarados por Java | Véase §2.5, 23 avisos distintos | Licencias por aviso; no todo es NFTC ni GPL | NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/` | Avisos sí; correspondencia binaria individual NV | Distribucion ajena; compilador exacto NO VERIFICADO | Terceros embebidos, sin PE separable en muchos casos | T/B | conservar legales y revisar los no acreditados |

Las **54 DLL** de graphics-win son siete OpenJFX, cinco VC (msvcp140,
msvcp140_1, msvcp140_2, vcruntime140, vcruntime140_1) y 42 UCRT/API sets
(26 api-ms-win-core, 15 api-ms-win-crt y ucrtbase). Se leyeron versiones y
firmas de las 54 copias. No se ha probado qué subconjunto extrae/carga JavaFX
durante una sesión concreta; todos viajan y deben inventariarse.

Los 10 JAR inspeccionados no contienen entradas estándar LICENSE/NOTICE/COPYING/legal
ni firmas META-INF SF/RSA/DSA. Esa búsqueda de nombres no demuestra ausencia de
cualquier mención de copyright. El payload actual tampoco incluye textos
separados de AtlantaFX/OpenJFX entre sus siete avisos. Es un pendiente de B/3.7,
no permiso para rellenarlos con una licencia de versión distinta.

Migrar Oracle a OpenJDK **no elimina por sí solo** los redistribuibles Microsoft
incluidos por JavaFX ni resuelve Mono/IGCL/PawnIO. Temurin es una opción libre
bajo GPL con excepción Classpath según su [FAQ oficial](https://adoptium.net/docs/faq);
no se ha elegido ni instalado una build. Rehacer jlink/jpackage, compatibilidad
JavaFX/FFM, regresión, manifiestos y pins pertenece al cambio separado.

### 2.3 PawnIO, interfaces y componentes de construcción

| Componente | Version exacta | Licencia SPDX | Aprobada OSI | Doble licencia comercial | Fuente de evidencia | Viaja en instalador | Quien lo compila | Quien lo firma hoy | Elegibilidad SignPath | Accion necesaria |
|---|---|---|---|---|---|---|---|---|---|---|
| PawnIO_setup.exe | 2.2.0.0 | NO VERIFICADO para el artefacto; atribucion previa: NOASSERTION para el instalador completo | NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí, intacto | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, V(namazso.eu), pin coincidente | B | permiso de copia no acredita licencia OSI de cada parte; excluir de toda firma propia |
| Driver PawnIO incluido por setup | La correspondencia interna exacta con fuentes de 2.2.0 NV | GPL-2.0-or-later en fuente actual + excepción propia IOCTL/LGPL; expresión completa NOASSERTION | GPL sí, excepción/conjunto NV | Licencias alternativas anunciadas; modalidad comercial NO VERIFICADO | URL_OFICIAL: [fuente oficial](https://raw.githubusercontent.com/namazso/PawnIO/master/README.md) | Sí, dentro del setup; no extraído | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero; firma interna no inspeccionada, no inferirla de firma del setup | B | acreditar fuente/versión/excepción/oferta y consultar doble licencia |
| Módulos PawnIO embebidos en LHM | 12 recursos binarios, incluidos en LHM 0.9.6; commits de compilación NV | LGPL-2.1-or-later en fuente IntelMSR actual; NOASSERTION para atribución exacta del conjunto | NV conjunto | NO VERIFICADO; no se acredita ausencia de acuerdos | URL_OFICIAL: [fuente oficial](https://raw.githubusercontent.com/namazso/PawnIO.Modules/main/IntelMSR.p) | Sí, recursos de DLL LHM | Distribucion ajena; compilador exacto NO VERIFICADO | Terceros, no PE Authenticode individuales | B | obtener fuentes y licencias correspondientes a los doce binarios |
| Cabecera Intel IGCL | Texto de licencia 10.07.21; versión/tag de cabecera no acreditados | LicenseRef-Intel-IGCL | no: restricción de plataforma | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `native/vendor_headers/intel_igcl/LICENSE` | Cabecera no como archivo; se usa al compilar y su aviso sí viaja | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero versionado, texto sin firma | B | consultar alcance del programa, preservar aviso y proveedor; no borrarla/reimplementarla aquí |
| Runtime IGCL del controlador Intel | Versión depende del equipo, NV | NO VERIFICADO para el artefacto; atribucion previa: NOASSERTION del binario concreto | NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | No se copia | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero del sistema, firma NV | B/F | consultar si califica como biblioteca de sistema, no darlo por aceptado |
| NVML del controlador NVIDIA | Versión depende del equipo, NV | NO VERIFICADO para el artefacto; atribucion previa: NOASSERTION del binario concreto | NV; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | No se copia; API declarada en código C propio | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero del sistema, firma NV | B/F | mantener proveedor primario; declarar dependencia externa a SignPath |
| APIs y DLL de Windows | Versión depende de Windows, NV | NO VERIFICADO para el artefacto; atribucion previa: LicenseRef-Microsoft-Windows | no es licencia OSI; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | No se copian las DLL del sistema | Distribucion ajena; compilador exacto NO VERIFICADO | Microsoft, firma local de cada una NV | F | posible excepción de bibliotecas de sistema, no equivale a permiso para copiar cualquier DLL |
| CRT MSVC enlazado estáticamente | Build exacta de bibliotecas usadas en imagen NV | NO VERIFICADO para el artefacto; atribucion previa: LicenseRef-MSVC-Redistributable | no acreditada OSI; aplicacion al artefacto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | SIN_EVIDENCIA de licencia acreditada; metadatos tecnicos no bastan | Sí, código enlazado en DLL propia | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero dentro del artefacto propio NS | B | no omitirlo porque no existe msvcrt separado; revisar redistribución y excepción |
| Inno Setup, motor, loader y desinstalador futuros | Ninguna versión instalada/seleccionada para MACE acreditada | InnoSetup | **no** según SPDX | NO VERIFICADO; no se acredita ausencia de acuerdos | URL_OFICIAL: [fuente oficial](https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt) | Futuros componentes del instalador, aún inexistente | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero; firma de futuro resultado NV | B/F | C2 antes de publicar/solicitar; no se instala |
| NSIS como alternativa, no elegida | Versión/build concreta NV | Zlib en motor/compresión zlib; otros módulos tienen licencias propias | Zlib sí | NO VERIFICADO; no se acredita ausencia de acuerdos | URL_OFICIAL: [fuente oficial](https://nsis.sourceforge.io/License) | No | Distribucion ajena; compilador exacto NO VERIFICADO | Tercero, no instalado, firma NV | F | solo opción con decisión del usuario y revisión de plugins |

Los doce recursos comprobados en E8 son: `AMDFamily0F.bin`, `AMDFamily10.bin`,
`AMDFamily17.bin`, `IntelMSR.bin`, `IsaBridgeEC.bin`, `LpcACPIEC.bin`,
`LpcCrOSEC.bin`, `LpcIO.bin`, `RyzenSMU.bin`, `SmbusI801.bin`,
`SmbusNCT6793.bin`, `SmbusPIIX4.bin`. Declarar LGPL para una fuente actual no
prueba la correspondencia de cada recurso histórico. El hardware Intel
declarado por el usuario no autoriza retirar módulos de otros fabricantes.

Solo en grafo, **no como paquetes copiados**: System.IO.FileSystem.AccessControl
5.0.0 y paquetes runtime.native.System.IO.Ports 10.0.3 para otros RID. La DLL
FileSystem.AccessControl efectiva proviene del runtime .NET 10.0.12, no debe
etiquetarse como binario 5.0.0. Ports enumera Android arm/arm64/x64/x86,
Linux arm/arm64/x64, bionic arm64/x64, musl arm/arm64/x64, Mac Catalyst arm64/x64
y macOS arm64/x64, además del metapaquete runtime.native. Sus licencias
individuales no se acreditan aquí: **NO VERIFICADO**, no «redistribuidos».

MSVC/Windows SDK, CMake, Maven, .NET SDK, JDK completo, herramientas y dependencias
JUnit/Mockito de pruebas son insumos de construcción, no paquetes del payload.
No se realiza aquí una auditoría legal exhaustiva del toolchain/CI; su selección
y condiciones se revisan al fijar el entorno reproducible. Los textos de
`app/third-party/` son HidSharp, Intel IGCL, LICENSE y THIRD-PARTY-NOTICES de
Microsoft.NETCore.App.Runtime.win-x64 y avisos de CodeDom, Ports y Management.

### 2.4 Avisos internos de .NET: límite de la etiqueta MIT

E5 conserva el aviso agregado de .NET 10.0.12. Incluye nombres multiplataforma
y no ofrece versiones exactas de todos ni un mapa de inclusión por binario.
Cada elemento es **tercero declarado por el runtime**, viaja al menos su aviso;
presencia/versión/firma individual y doble licencia comercial no documentadas
quedan **NO VERIFICADAS**. No se refirma ninguno; conservar el aviso íntegro y
acreditar el subconjunto efectivo antes de declarar elegibilidad global.
La tabla final de este documento transcribe todos sus encabezados con la
identificación que permite el texto, sin atribuir a Mono.Posix el MIT del
componente llamado «Mono» dentro de .NET.

### 2.5 Legales internos de Java

Los 23 avisos `.md` distintos presentes en E6 se catalogan al final. Heredan:
terceros; viajan sus avisos en runtime/legal; no se firman como propios;
versión cuando el título la declara, sin inferir un tag a partir del nombre.
Las licencias no cotejadas suficientemente se dejan NOASSERTION aunque el
texto esté disponible. Completar esa clasificación es deuda legal explícita,
no una afirmación de incompatibilidad ni elegibilidad ya confirmada.

## 3. Conflictos C1–C10: opciones y decisiones

### C1 — MIT y derivados cerrados: RESUELTO POR EL USUARIO

**Evidencia:** confirmación expresa; DanielDdeY como titular y declaración de
acuerdo del equipo. MIT permite usos/redistribución con sus condiciones;
[texto oficial OSI](https://opensource.org/license/mit).
**Opciones consideradas:** MIT conserva la elección; MPL-2.0 obligaría a
mantener abiertos archivos cubiertos y GPL-3.0-only impondría copyleft más amplio.
Se describen como comparación histórica, no una nueva elección solicitada.
**Recomendación:** formalizar MIT solo en B para código propio, conservando
atribuciones ajenas. **Riesgo residual:** no hay auditoría independiente de
titularidad ni la elección relicencia terceros. **Decisión necesaria:** ninguna
sobre MIT/derivados cerrados/alias; B sigue sujeto a aprobación de A.

### C2 — Inno frente al programa de firma: ABIERTO

SPDX marca InnoSetup como no aprobada por OSI. El EXE generado incorpora un
loader y motor Inno; el desinstalador también es código redistribuido, no
solo una salida de datos. Deben conservarse los avisos y direcciones web
existentes en los binarios y marcar modificaciones. No se retiran textos
para hacer parecer el motor propio. [Licencia](https://spdx.org/licenses/InnoSetup.json),
[documentación del loader](https://jrsoftware.org/inno-setup/documentation/topic_setup_usesetupldr.htm).

**Opciones:** mantener Inno y consultar su admisión; proponer NSIS con núcleo
y compresión Zlib y plugins auditados; distribuir solo ZIP sería cambiar el
alcance aprobado del instalador y no resuelve sus requisitos. NSIS también
ofrece bzip2 y LZMA con licencias diferentes: no declarar «todo NSIS Zlib».
[Licencias NSIS](https://nsis.sourceforge.io/License).
**Recomendación:** no sustituir Inno sin decisión; preparar la consulta antes
de invertir en su implementación. Si no se admite, evaluar NSIS. El cambio
cuesta reescribir detección/consentimiento/timeout/actualización/reparación,
desinstalación, hashes y pruebas de VM; no es una conversión automática.
**Riesgo residual:** aceptar redistribución no demuestra elegibilidad de firma.
**Decisión necesaria:** mantener Inno sujeto a respuesta externa o autorizar
el estudio separado de la alternativa; ninguna compra/instalación ahora.

### C3 — Setup, driver y módulos PawnIO: ABIERTO

La firma externa de setup 2.2.0.0 es válida y su pin coincide. La fuente actual
del driver declara GPL-2.0-or-later, una excepción para ciertos enlaces y
comunicación IOCTL, y opciones alternativas de licencia. La interfaz Pawn
y los módulos cargados no quedan cubiertos de la misma manera por esa
excepción. La fuente de IntelMSR consultada declara LGPL-2.1-or-later.
[Driver y excepción](https://raw.githubusercontent.com/namazso/PawnIO/master/README.md),
[módulo](https://raw.githubusercontent.com/namazso/PawnIO.Modules/main/IntelMSR.p).

**No acreditado:** licencia de todo el setup exacto, correspondencia de su
driver con fuente/tag, contenido/licencias internas no inspeccionados, fuente
exacta de los doce módulos de LHM y cumplimiento de distribución de fuentes.
La release del setup no acredita por sí sola las fuentes completas de cada
contenido. No se atribuye automáticamente al setup la GPL del driver.

**Opciones:** completar dossier de redistribución del conjunto intacto;
pedir al titular referencias exactas si faltan (por el usuario); retirar el
setup y descargarlo aparte cambiaría la regla de incluirlo y requiere decisión.
**Recomendación:** mantenerlo intacto en el inventario y tratar estas carencias
como bloqueo de distribución. Documentar licencia, atribución y mecanismo
conforme a la modalidad GPL elegida para suministrar fuente correspondiente,
incluidos scripts relevantes; un enlace mutable genérico no basta para dar
por cumplida toda obligación. Revisión cualificada antes de publicar.
**Riesgo residual:** «licencia alternativa» no prueba contrato comercial
concreto, pero obliga a preguntar por el criterio de doble licencia.
**Decisión necesaria:** completar fuentes/derechos y consulta a SignPath;
no ejecutar ni refirmar el setup. Firmar el contenedor no firma ni legaliza
sus binarios internos; la configuración debe excluirlos recursivamente.

### C4 — Mono.Posix.NETStandard: BLOQUEO LEGAL EXPLÍCITO

El nupkg real 1.0.0 tiene **26 entradas**, ninguna LICENSE/NOTICE/COPYING.
El nuspec exige aceptación de licencia y remite a los fwlink 869050/869051;
no declara SPDX, repositorio, commit ni tag. **Corrección:** el enlace 869050
se pudo abrir durante esta auditoría y redirige a la licencia del repositorio
Mono en `main`. Ya no se afirma que su destino no sea accesible. Esa licencia
actual no acredita aquí la correspondencia histórica de los tres binarios
1.0.0 con fuentes/tag y términos exactos. El bloqueo queda delimitado a esa
correspondencia, no a la inexistencia de un enlace de licencia.
Los tres binarios empaquetados
coinciden byte a byte con los activos win-x64 restaurados, conservando su
firma Xamarin; eso acredita procedencia de caché, no permiso de distribución.

**Carga:** en 183 ensamblados administrados del payload no se encontró ninguna
AssemblyReference a Mono.Posix.NETStandard. La biblioteca LHM net10 seleccionada
tampoco tiene esa referencia ni TypeReference Mono. **Es evidencia estática**:
deps sí enumera el paquete y sus DLL están copiadas. Carga por reflexión,
resolución dinámica y ejecución Windows real: **NO VERIFICADO**. No se afirma
que jamás se cargue ni que borrarla sea inocuo.

**Opciones:** acreditar licencia y fuente exactas; o proponer aparte excluir
sus tres activos para win-x64 si publicación y ejecución lo permiten.
**Recomendación:** intentar primero acreditar derechos; como alternativa
técnica, un diff separado sobre selección/publicación/deps y pruebas reales.
No borrar simplemente DLL: el host puede validar dependencias. Cambiar payload
exige manifiesto, hashes, pins, regeneración y regresión, incluida ejecución
normal/admin y sensores. **Riesgo residual:** exclusión no probada y licencia
no acreditada. **Decisión necesaria:** elegir investigación de licencia o
autorizar una propuesta técnica aparte; nada se retira en A.

### C5 — Oracle, OpenJFX y JDK futuro: ABIERTO

El runtime real es Oracle 22.0.2 bajo NFTC, con tecnología separadamente
licenciada; no es correcto llamarlo automáticamente GPL por usar jlink.
NFTC permite ciertos usos/redistribución bajo condiciones: **no se afirma
que toda redistribución esté prohibida**. Tampoco es licencia OSI por ser
sin coste. [Términos Oracle](https://www.oracle.com/downloads/licenses/no-fee-license.html).

**Opciones:** migrar a una distribución OpenJDK libre mantenida (ya aprobado
como trabajo separado); mantener Oracle sujeto a derechos y aceptación del
programa; exigir JDK del usuario cambiaría el alcance autocontenido.
**Recomendación:** concretar distribución/build mantenida con FFM final y
pareja JavaFX antes de construir la release definitiva. Temurin es candidato,
no elección realizada. JavaFX necesita además revisar los 47 binarios Microsoft
internos y sus subcomponentes. **Riesgo residual:** cambiar JDK no los elimina;
compatibilidad y seguridad de la nueva combinación NO VERIFICADAS.
**Decisión necesaria:** proveedor/build/JavaFX y diff separado; no cambiar
JDK del host ni imagen congelada en A.

### C6 — Dependencias adicionales e interfaces: ABIERTO

Los nuspec exactos acreditan MPL en LHM/BlackSharp/DiskInfo/NDD y el archivo
real de HidSharp acredita Apache-2.0. No se inventa una licencia a partir
del autor. La cabecera IGCL impone uso en plataformas Intel, conservar avisos
y otras condiciones; no es una licencia libre sin restricciones de campo.
NVML/IGCL se cargan desde el controlador del sistema y no se copian, pero
eso no demuestra automáticamente una excepción del programa. E7 y §2.3.

**Opciones:** documentar límites y consultar bibliotecas de sistema; revisar
interfaces/cabeceras en un cambio aparte si la respuesta lo exige. El CRT
estático y las DLL Microsoft redistribuidas también cuentan, aunque no estén
en la lista NuGet. Microsoft condiciona redistribución a sus términos:
[documentación oficial](https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files).
**Recomendación:** conservar proveedores y sus avisos; no borrar ni reemplazar
IGCL/NVML para eludir la revisión. **Riesgo residual:** fuentes/licencias de
subcomponentes y excepción aceptada NO VERIFICADAS. **Decisión necesaria:**
resolución externa/legal; cualquier cambio funcional requiere nueva aprobación.
La excepción NDD sigue estrictamente limitada a ruta/pin/offsets existentes.

### C7 — MIT y licencias del conjunto: CIERRE PARCIAL

MIT se aplica al código propio; no convierte en MIT bibliotecas MPL, módulos
LGPL, runtime GPL con excepción ni binarios propietarios. MPL permite un
conjunto con archivos bajo otros términos, preservando obligaciones de sus
archivos cubiertos y acceso a fuente: [MPL oficial](https://opensource.org/license/MPL-2.0).
La excepción Classpath permite ciertos enlaces independientes con otras
licencias, pero no elimina avisos/fuentes del runtime cubierto ni cubre
cualquier biblioteca ajena incluida junto a él.

**Opciones:** distribución agregada con cumplimiento individual y aislamiento
actual; cambiar arquitecturas/licencias requeriría revisión aparte.
**Recomendación:** mantener MIT, completar licencias/copyrights/textos, fuente
correspondiente y posibilidades de sustitución/relink cuando LGPL lo requiera,
analizando los módulos reales. No afirmar que GPL del driver obliga sin más
a relicenciar todo MACE, ni que la excepción resuelve todo vínculo posible.
**Riesgo residual:** correspondencia de fuentes y excepción PawnIO exacta
pendientes, derechos Oracle/Intel/Microsoft y alcance de módulos pendientes.
**Decisión necesaria:** revisión cualificada del conjunto antes de distribuir;
no se solicita volver a elegir MIT. Cumplimiento legal y admisión de un
programa de firma son evaluaciones distintas.

### C8 — Privacidad: TEXTO CONSERVADOR PROPUESTO

Auditoría estática del código propio Java/C/helper: búsqueda de APIs de red,
clientes HTTP, sockets y URLs; revisión de los usos de pipe, ficheros, registro
y Preferences. Las llamadas `Send` de Program.cs envían muestras por named
pipe local, no prueban envío a Internet. SensorSampler habilita CPU/GPU;
PciAssociation lee Enum/PCI. No se encontró un cliente de telemetría de red
en esas fuentes. No es una captura de tráfico ni auditoría de todo tercero.

La búsqueda es reproducible con `auditoria-licencias/comprobar.ps1`:
`privacidad-estatica.csv` registra archivo/línea/categoría sin copiar valores
locales. Dio cero referencias a las APIs HTTP/socket del patrón utilizado.
Esto **no** equivale a una prueba exhaustiva de ausencia de red: APIs cargadas
dinámicamente, dependencias y comportamientos del sistema siguen NV.

Escrituras propias y riesgos ya detallados en
[inventario de escrituras](FASE2-INVENTARIO-ESCRITURAS.md): caja negra local
de métricas/procesos/eventos, rotación y retención; preferencias del HUD;
salidas de diagnóstico. Las rutas de procesos y otros datos que maneja la
aplicación pueden ser sensibles; los logs compartidos deben sanearse.
En Windows Preferences utiliza su backend de usuario; no se promete que
todo dato de configuración sea un archivo propio. La señal 3010 aún no existe.
No se consultan claves del host para este análisis.

**Texto propuesto para revisar en C, no política ya publicada:**

> MACE consulta información de hardware y procesos para mostrar métricas y
> conserva localmente la caja negra y preferencias de la interfaz. La revisión
> del código propio no ha identificado envío automático de esas métricas a un
> servidor. Los informes que decidas compartir por GitHub Issues pueden contener
> datos del equipo: revísalos antes de publicarlos. El comportamiento de red o
> diagnóstico del sistema operativo, runtimes y otros componentes no se ha
> verificado mediante una captura completa de ejecución.

**Opciones:** ese texto limitado ahora o una afirmación más fuerte después
de traza dinámica/terceros. **Recomendación:** primera opción, explicar
destinos/retención/configuración cuando C redacte la política; diferenciar
descargas de compilación y futuro servicio de firma de uso del producto.
**Riesgo residual:** WER, validación de certificados, controladores y runtimes
pueden tener actividad indirecta no cubierta. **Decisión necesaria:** revisar
texto en C y alcance de una captura futura; no copiar una promesa absoluta
de «ninguna información a ninguna red» sin evidencia suficiente.

### C9 — Release previa en la forma firmable: PENDIENTE FUTURO

**Opciones:** una primera release de prueba sin firma propia del instalador
definitivo, revisada y publicada por el usuario; consultar qué evidencia
previa admite el programa. Tener código público no demuestra esa release.
**Recomendación:** resolver bloqueos legales y runtime, implementar/validar
instalador y avisos en VM, fijar una release/tag y publicar el EXE definitivo
sin firma con advertencia clara, sin eludir SmartScreen/Defender. Después
el usuario presenta dossier. No publicar hoy el prototipo 5.1 como definitivo.

La build firmada será una release posterior: helper firmado → manifiesto
→ DLL con pins → paquete/launchers → instalador, según diseño 3.6 y futuras
aprobaciones. Sus hashes cambian y necesitan verificaciones nuevas.
**Riesgo residual:** reputación/admisión siguen siendo decisión externa;
no se promete aceptación por una sola publicación. **Decisión necesaria:**
autorización de release por el usuario cuando exista un instalador validado;
ahora solo se registra el plan, sin push, publicación ni envío.

### C10 — Metadatos de firma frente a versiones congeladas: ABIERTO

Los PE actuales declaran MACE 1.0.0 en launchers, MACE 1.5.0.0 en DLL,
Mace.Lhm.Helper 1.1.1 y Mace.Lhm.Protocol 1.0.0.0 en sus componentes. Esos
ProductName/ProductVersion distintos deben reconciliarse con la política
del programa; **no se cambian** como «simple documentación». FileVersion,
AssemblyVersion, ABI y versión del wire no son el mismo campo.

**Opciones:** consultar si se permiten versiones por componente; si exigen
un ProductName MACE y ProductVersion común, proponer un diff de metadatos
de release separado, manteniendo ABI/wire/política y sin reescribir imagen
congelada. **Recomendación:** consulta primero, diff después solo si autorizado.
**Riesgo residual:** nuevos hashes/manifiestos/pins y regresión aun sin cambiar
una función. **Decisión necesaria:** interpretación externa y aprobación
expresa de cualquier cambio de producto. No encubrirlo como actualización
del pom/csproj en B sin su revisión.

## 4. Preguntas para el dossier del usuario a SignPath

**Solo borrador de preguntas; no enviadas.** Se adjuntaría un inventario
saneado, sin datos locales ni el correo alternativo en documentos públicos.
Las condiciones oficiales distinguen licencia, terceros, bibliotecas de
sistema, metadatos y procedencia verificable; la admisión no se presume.
[Condiciones oficiales consultadas](https://signpath.org/terms).

1. ¿El motor/loader/desinstalador InnoSetup, con SPDX no OSI, impide la admisión
   del contenedor MACE? ¿Cómo debe tratarse ese código generado/redistribuido?
2. ¿Aceptan un setup PawnIO intacto y firmado por su editor dentro del paquete,
   sin firmarlo con la suscripción MACE? ¿Qué documentación exacta de licencia,
   fuentes y excepción requieren para driver y módulos?
3. La fuente del driver ofrece licencias alternativas: ¿entra en la prohibición
   de doble licencia comercial aunque MACE solo utilice la opción GPL/excepción?
   No se presupone que exista un contrato comercial con MACE.
4. ¿Califican como bibliotecas de sistema los VC/UCRT redistribuidos con
   JavaFX/JDK y el CRT enlazado estáticamente por MSVC? ¿Qué evidencia requieren?
5. ¿Cómo evalúan IGCL: cabecera Intel con restricción de plataforma incluida
   en el repositorio y uso al compilar, frente al runtime del controlador no
   copiado? ¿Y la carga dinámica de NVML proporcionada por el sistema?
6. Tras sustituir Oracle por OpenJDK, ¿qué revisan de sus excepciones Classpath,
   subcomponentes y redistribuibles? No se solicita exención genérica para NFTC.
7. ¿Admiten que DanielDdeY asuma autoría, revisión y aprobación manual, con 2FA,
   o exigen separación de personas/permisos? La aceptación no está verificada.
8. ¿Qué evidencia de release previa/reputación requieren del primer instalador
   sin firma, y qué diferencia aceptan entre esa release y la futura firmada?
9. ¿Los ProductVersion deben coincidir también entre helper, DLL y launchers?
   ¿Puede conservarse FileVersion/AssemblyVersion de cada componente y ABI/wire?
10. ¿Cómo configurar inclusiones/exclusiones para firmar solo artefactos propios,
    respetar upstream, y enlazar aprobaciones manuales por etapas cuando cada
    firma modifica hashes consumidos en la etapa siguiente? ¿Incluye el programa
    los PE propios generados mediante apphost/jpackage y el desinstalador?
11. ¿Qué configuración de procedencia y MFA requiere la cuenta del proyecto y
    qué datos de contacto pide el formulario? GitHub Issues es el contacto público
    elegido; el usuario aportará correo al formulario si lo exige.

No se usa este listado para eludir condiciones: una respuesta desfavorable
obliga a decidir alcance/herramienta con el usuario. La falta de licencia
acreditada de Mono debe resolverse antes de declarar que el paquete cumple.

## 5. Decisiones necesarias y NO VERIFICADO

**Ya resuelto:** MIT/derivados cerrados, alias DanielDdeY, roles, contacto por
GitHub Issues y 2FA GitHub/Gmail declarada por el usuario. No falta solicitar
nombre legal para redactar MIT. MFA SignPath y aceptación de roles siguen NV.

**Para continuar:** revisar este Paso A; decidir el tratamiento de C2 sin
cambiar Inno automáticamente; priorizar acreditación de licencia Mono o una
propuesta separada de exclusión; completar fuentes/derechos PawnIO y módulos;
concretar JDK/JavaFX para el cambio separado y revisión legal del conjunto.
Responder a SignPath corresponde al usuario cuando prepare la solicitud.
Los borradores ausentes se aportarán o se autorizará redactarlos desde cero
al llegar a sus pasos, no se inventan ahora.

**NO VERIFICADO:** licencias y versiones internas que las tablas dejan sin
acreditar; ejecución/carga real Mono y seguridad de excluirla; correspondencia
fuente/binario de setup/driver/módulos y subcomponentes de runtimes; derechos
exactos de toolchain/CRT; aplicación de excepciones del programa, aceptación,
firma/certificado/MFA/procedencia multietapa; instalador construido, release
previa definitiva, clon limpio, JDK nuevo, capturas dinámicas de red/disco,
VM/instantánea/restauración, HVCI y hardware Intel/NVIDIA del conjunto actual.
No se ejecuta instalación, registro 3010, servicios ni pruebas físicas.

## 6. Verificación de esta entrega y punto de parada

**Alcance temporal:** la tabla siguiente conserva comprobaciones del Paso A
original. La auditoría posterior está separada en FASE3-LICENCIA-AUDITORIA.md:
45 comparaciones de versión sin discrepancia, 58 filas con versión sin
acreditar, 370/370 archivos intactos y asignados, helper 202/202 y publicado
205/205. Son lecturas, no pruebas de ejecución, carga de sensores o nueva
validación criptográfica de todos los componentes.

La asignación individual y las 103 filas se reproducen con
`helper/auditoria-licencias/comprobar.ps1`; sus CSV quedan bajo
`native/build/auditoria-licencias/`, ignorados. Los tres excluidos del
publicado son createdump.exe y los PDB propios de helper/protocolo; sus
componentes están identificados aunque no viajen en la imagen.

Solo documentación: inventario nuevo y notas de estado/enlace en los tres
documentos de diseño/continuidad. Producto, imagen, fuentes, lock, pom/csproj,
avisos de terceros, pins, binarios y contratos congelados permanecen intactos.
No se repiten helper/CTest/Java ni pruebas con procesos por un cambio documental;
sus cifras históricas no se presentan como ejecución nueva de A.

| Comprobación nueva de A | Resultado y alcance |
|---|---|
| `git diff --check` y comprobación documental del archivo nuevo | PASS, sin errores de espacios |
| Enlaces locales de los cuatro documentos | 0 rotos |
| Búsqueda de rutas de perfil, SID, correo y hashes completos en los documentos cambiados | 0 hallazgos; alias público y atribuciones de terceros se conservan |
| `native/scripts/check-protected-telemetry.ps1`, Windows PowerShell 5.1 | PASS; proveedores, contratos y bloques protegidos intactos |
| `native/scripts/audit-retired-providers.ps1`, Windows PowerShell 5.1 | 181 aceptadas, 0 inesperadas; TSV de ejecución local ignorado |
| Comparación de imagen con MACE-SHA256SUMS.txt, Windows PowerShell 5.1.19041.6456 | **370/370 coinciden, 0 faltantes/diferentes, 0 extras**; solo lectura, no regeneración |
| Lectura de metadatos CLI, PowerShell 7 | 183 ensamblados, 0 referencias estáticas a Mono.Posix.NETStandard, 12 recursos PawnIO en LHM; no ejecución física |
| Comparación Mono con paquete restaurado | Los tres binarios coinciden; no demuestra licencia/carga |
| Inspección ZIP/PE de JavaFX | 54 DLL catalogadas; 47 con firma Microsoft válida y 7 OpenJFX NotSigned; no se ejecutaron |

Comandos de las dos comprobaciones versionadas, desde la raíz del checkout
en Windows PowerShell 5.1, sin elevar:

```powershell
.\native\scripts\check-protected-telemetry.ps1
.\native\scripts\audit-retired-providers.ps1 -ReportPath .\native\build\fase3-3.1c-a\referencias-auditadas.tsv
```

No se versionan copias de análisis, TSV de ejecución, hashes completos ni
logs locales. La comparación de imagen se hizo con un lector temporal
ignorado, sin invocar scripts de empaquetado ni modificar manifiestos.

**DETENERSE al terminar A. Esperar aprobación antes de B o 3.2.** El presente
inventario no convierte los bloqueos legales ni las comprobaciones NV en
deudas aceptadas para distribución.

## Anexo I — componentes declarados por los avisos Java empaquetados

Identificación limitada al texto real; «NV» requiere cotejo antes de afirmar
elegibilidad. Para todos: tercero, sin nueva firma propia, doble licencia
comercial **NO VERIFICADA**, inclusión binaria exacta NV; aviso sí empaquetado.

| Componente | Version exacta | Licencia SPDX | Aprobada OSI | Doble licencia comercial | Fuente de evidencia | Viaja en instalador | Quien lo compila | Quien lo firma hoy | Elegibilidad SignPath | Accion necesaria |
|---|---|---|---|---|---|---|---|---|---|---|
| Cryptix AES | 3.2.0 | BSD-2-Clause | BSD-2-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/aes.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar atribución Cryptix |
| ASM | 9.3 | BSD-3-Clause | BSD-3-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/asm.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |
| BCEL | 6.7.0 | Apache-2.0 | Apache-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.xml/bcel.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar licencia y NOTICE |
| c-libutl | 20160225 | BSD-2-Clause | BSD-2-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/c-libutl.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |
| CLDR | 44 | Unicode-3.0 | Unicode-3.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/cldr.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Revisar encaje del componente y preservar texto |
| Eastman Kodak, color/imaging | No declarada | NOASSERTION; solo aviso de copyright | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/colorimaging.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Acreditar términos que cubren esas porciones |
| W3C DOM Level 3 Core | 1.0 | W3C (texto de 2002) | W3C: SPDX True, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.xml/dom.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso y reglas de modificaciones |
| FreeType | 2.13.2 | FTL en texto empaquetado | FTL: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/freetype.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Revisar alternativa/partes aplicables, conservar texto |
| GIFLIB | 5.2.1 | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/giflib.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |
| HarfBuzz | 8.2.2 | NOASSERTION para conjunto; declara Old MIT y otras partes | MIT: SPDX True, OSI LISTADO; conjunto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/harfbuzz.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Cotejar variantes y COPYING por subdirectorio |
| ICU4J | 74.1 | Unicode-3.0 | Unicode-3.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/icu.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar texto y revisar encaje |
| Java CUP | 0.11b | NOASSERTION; texto permisivo tipo SML/NJ | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.xml/jcup.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Cotejar identificador exacto, no adivinarlo |
| IJG JPEG | 6b | IJG | IJG: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/jpeg.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar texto y revisar encaje |
| LCMS | 2.16 | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/lcms.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |
| libpng | 1.6.40 | libpng-2.0 | libpng-2.0: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/libpng.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Consultar tratamiento del subcomponente |
| Mesa 3D | 21.0.3 | MIT en aviso | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.desktop/mesa3d.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso, cotejar partes |
| Mozilla Public Suffix List | No declarada | MPL-2.0 | MPL-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/public_suffix.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Fuente/fecha exactas NV; preservar aviso |
| SipHash | 1.0-68c8a7c | CC0-1.0 OR MIT; CC0 no OSI, MIT sí | CC0-1.0: SPDX False, OSI NO_LISTADO; MIT: SPDX True, OSI LISTADO; conjunto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/siphash.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Identificar opción ejercida; no es doble licencia comercial |
| Unicode Character Database | 15.1.0 | Unicode-3.0 | Unicode-3.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/unicode.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar texto y revisar encaje |
| wepoll | 1.5.8 | BSD-2-Clause | BSD-2-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/wepoll.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |
| Xalan | 2.7.3 | Apache-2.0 | Apache-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.xml/xalan.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar licencia y NOTICE |
| Xerces | 2.12.2 | Apache-2.0 | Apache-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.xml/xerces.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar licencia y NOTICE |
| zlib | 1.3.1 | Zlib | Zlib: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/dist/MACE/runtime/legal/java.base/zlib.md` | Aviso si; correspondencia binaria NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | No PE individual; no firma propia | T/B, no admitido | Conservar aviso |

SPDX distingue **tener identificador** de estar aprobada por OSI:
[libpng-2.0](https://spdx.org/licenses/libpng-2.0.json) y
[CC0-1.0](https://spdx.org/licenses/CC0-1.0.json) muestran `isOsiApproved=false`.
Son datos adicionales para C5/C6 y el dossier, no una conclusión de que el
runtime completo deba rechazarse sin evaluar las reglas y excepciones del
programa. La auditoría coteja también los identificadores menos comunes: Unicode-3.0
consta en ambas listas; FTL, IJG, BSD-4-Clause, CC0 e InnoSetup no aparecen
con esos IDs en la lista OSI consultada y SPDX marca falso. Para W3C, SPDX
marca verdadero pero la lista OSI actual no contiene ese ID exacto: su
correspondencia histórica se deja **NO VERIFICADA**, sin afirmar revocación.
Los LicenseRef son referencias propias, no entradas oficiales ni pruebas
de incompatibilidad. Datos/fechas en `auditoria-licencias/fuentes-oficiales.json`.

## Anexo II — componentes declarados por los avisos .NET empaquetados

Los cuatro THIRD-PARTY-NOTICES de .NET/CodeDom/Ports/Management tienen los
mismos **46 encabezados de componentes**. Los textos se conservan íntegros;
versiones y subapartados deben acreditarse contra la distribución concreta.
«No declarada» no se sustituye por la versión 10.0.12 del runtime.

Para todas las filas: terceros, aviso sí en imagen, carga/binario individual
NV, firma individual no aplicable o NV, doble licencia comercial **NO VERIFICADA**,
**sin firma propia**; acción común conservar texto y cotejar
fuente/alcance antes de una declaración global de elegibilidad. Las expresiones
simples califican solo el texto identificado, no todas las porciones posibles.

| Componente | Version exacta | Licencia SPDX | Aprobada OSI | Doble licencia comercial | Fuente de evidencia | Viaja en instalador | Quien lo compila | Quien lo firma hoy | Elegibilidad SignPath | Accion necesaria |
|---|---|---|---|---|---|---|---|---|---|---|
| ASP.NET | No declarada | Apache-2.0 | Apache-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Correspondencia Windows |
| Slicing-by-8 | No declarada | NOASSERTION, remisión BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar texto/revisión |
| Unicode data | No declarada | NOASSERTION, texto Unicode | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar revisión de licencia |
| zlib-ng | Revisión enlazada en aviso; versión semántica no declarada | Zlib | Zlib: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fijar fuente correspondiente |
| opentelemetry-dotnet | Revisión enlazada; versión semántica no declarada | Apache-2.0 | Apache-2.0: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No prueba que MACE exporte telemetría de red |
| LinuxTracepoints | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Inclusión Windows NV |
| Mono | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No atribuir esta licencia a Mono.Posix 1.0.0 |
| International Organization for Standardization | No declarada | NOASSERTION, permiso SGML específico | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Acreditar identificación/encaje |
| Intel | No declarada | BSD-3-Clause en texto | BSD-3-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No confundir con licencia IGCL |
| Xamarin and Novell | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No acredita otro paquete Xamarin por nombre |
| Bit Twiddling Hacks | No declarada | NOASSERTION, dedicación de fragmentos a dominio público | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No asignar CC0 sin texto que lo sustente |
| Brotli | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar correspondencia |
| Json.NET | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No afirmar que Newtonsoft.Json.dll viaje por ese aviso |
| vectorized base64 encoding / decoding | No declarada | NOASSERTION, texto BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variantes/partes |
| vectorized hex parsing | No declarada | NOASSERTION, texto BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variante |
| RFC 3492 | RFC 3492 | NOASSERTION, licencia Internet Society | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Identificar permiso y código derivado |
| Algorithm from RFC 4122 | RFC 4122 | NOASSERTION, permiso específico | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Identificar variante |
| The LLVM Compiler Infrastructure | No declarada | NOASSERTION para conjunto | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar licencia LLVM y posibles excepciones |
| Bob Jenkins | No declarada | NOASSERTION, permiso propio | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar identificador |
| Greg Parker | No declarada | NOASSERTION, dominio público | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar términos |
| libunwind based code | No declarada | MIT en texto | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fuente/partes |
| Printing Floating-Point Numbers (Dragon4) | No declarada | NOASSERTION, texto tipo Zlib | Zlib: SPDX True, OSI LISTADO; conjunto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variante |
| Printing Floating-point Numbers (Grisu3) | No declarada | BSD-3-Clause | BSD-3-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fuente/partes |
| xxHash | No declarada | BSD-2-Clause | BSD-2-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No inferir versión del año de copyright |
| Berkeley SoftFloat | Release 3e | BSD-3-Clause | BSD-3-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fuente/partes |
| xoshiro RNGs | No declarada | CC0-1.0 | CC0-1.0: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Consulta de tratamiento del programa |
| The C++ REST SDK | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Inclusión efectiva |
| MessagePack-CSharp | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Inclusión efectiva |
| lz4net | No declarada | NOASSERTION, texto BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variante |
| Nerdbank.Streams | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Inclusión efectiva |
| RapidJSON | No declarada | MIT con posibles porciones separadas | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Revisar aviso completo |
| DirectX Math Library | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No es licencia de todo DirectX |
| ldap4net | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Inclusión efectiva |
| vectorized sorting code | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fuente/partes |
| musl | No declarada | MIT para conjunto con porciones separadas | MIT: SPDX True, OSI LISTADO; conjunto NO VERIFICADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Revisar excepciones del aviso |
| Faster Unsigned Division by Constants | No declarada | NOASSERTION, dedicación a dominio público | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar expresión exacta |
| mimalloc | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Fuente/partes |
| Apple header files | No declarada | BSD-4-Clause en texto | BSD-4-Clause: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Revisar alcance y cláusula publicitaria |
| JavaScript queues | No declarada | CC0-1.0 | CC0-1.0: SPDX False, OSI NO_LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Consulta de tratamiento del programa |
| FastFloat algorithm | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No inferir versión |
| MsQuic | No declarada | MIT | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | No prueba que MACE establezca conexiones QUIC |
| m-ou-se/floatconv | No declarada | NOASSERTION, texto BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variante |
| code from The Practice of Programming | No declarada | NOASSERTION, permiso Lucent | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Conservar cita exigida, cotejar identificador |
| amd/aocl-libm-ose | No declarada | BSD-3-Clause en texto | BSD-3-Clause: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar porciones/revisión |
| fmtlib/fmt | No declarada | MIT con nota de excepciones | MIT: SPDX True, OSI LISTADO | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Revisar excepción y código efectivo |
| Avx512Vbmi base64 encoding / decoding | No declarada | NOASSERTION, texto BSD | NV | NO VERIFICADO; no se acredita ausencia de acuerdos | ARCHIVO_LOCAL: `packaging/third-party/Microsoft.NETCore.App.Runtime.win-x64-THIRD-PARTY-NOTICES.TXT` | Aviso si; presencia binaria individual NO VERIFICADO | Tercero; compilador exacto NO VERIFICADO | NO VERIFICADO; no firma propia | T/B, no admitido | Cotejar variante |

**Consulta adicional al programa:** cómo evalúa subcomponentes de runtimes
con licencias permisivas/dedicaciones no marcadas OSI por SPDX, como
libpng-2.0 y CC0. No eliminar avisos ni componentes para ocultarlos. Esta
pregunta complementa las once de §4; no se ha enviado.
