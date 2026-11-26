# 3.1d — Etapa 2: evidencia y ruta crítica de distribución

Fecha de consulta: **2026-11-25**, America/Lima. Partida **530f0ec**,
rama `LHM_implementado`, árbol seguido limpio. Solo lectura de fuentes
públicas y archivos existentes; ningún paquete descargado en esta etapa,
producto ejecutado, instalador invocado, servicio/registro tocado o contacto
externo enviado. Este análisis **no es asesoría legal**.

## Ensayos físicos recibidos

**VERIFICADO POR EL USUARIO:** comunica que ejecutó los cuatro ensayos de
Etapa 1 y todos devolvieron **0**: Read, Read RequireCpu, Reconnect RequireCpu
y CloseEarly. Se registra su comunicación, sin inventar logs, mediciones,
modelos, tokens individuales ni ejecución por el agente. La Etapa 1 fue
aceptada. Eso supera el gate técnico de esos ensayos del experimento sin Mono;
no convierte CloseEarly en CloseWarming ni acredita licencia o SignPath.
La primera candidata futura sigue siendo **CONSERVAR Mono**; no se integra
la exclusión por inferencia. No hay permiso para actuar sobre las dos laptops.

## B03 — Resultado por cada una de las 16 filas históricas

No se reescribe el inventario auditado ni se recalculan sus porcentajes
históricos como si se hubiese repetido la auditoría completa. Los IDs son
los de FASE3-LICENCIA-RESUMEN.md. «Cerrada con evidencia» aquí solo cierra
la falta de identificación de licencia propia; no la entrega de avisos en
una imagen ni la revisión legal del conjunto.

| ID / componente | Resultado actual | Evidencia y qué falta |
|---|---|---|
| R001 JAR MACE | **CERRADA CON EVIDENCIA**, licencia propia | LICENSE raíz MIT, incorporada exacta en 7be9ab0 y confirmación del titular. Metadatos/aviso en candidata aún no aplicados; no se relicencian dependencias. |
| R002 MACE.exe / Preview | **DEPENDE DE REVISIÓN CUALIFICADA** | MIT propia identificada; stub jpackage del JDK Oracle 22.0.2 y CRT son ajenos. Acreditar condiciones de la distribución/build exacta; no usar licencia de OpenJDK como sustituto de la de Oracle. |
| R003 mace_native.dll | **DEPENDE DE REVISIÓN CUALIFICADA** | MIT propia, licencia IGCL local 10.07.21 y CRT estático explícito en CMake; revisar alcance de términos Intel y licencia del toolchain. |
| R004 Helper exe/DLL | **DEPENDE DE REVISIÓN CUALIFICADA** | MIT propia identificada; apphost/runtime y avisos del publicado .NET se analizan por separado. No tratar todos los bytes del EXE generado como código propio. |
| R005 Protocol.dll | **CERRADA CON EVIDENCIA**, licencia propia | LICENSE raíz y fuentes propias; no incluye un apphost. Metadatos/aviso de candidata siguen pendientes, sin cambiar wire 2. |
| R011 Mono.Posix.NETStandard.dll | **AÚN SIN EVIDENCIA SUFICIENTE** del binario exacto | Nuevas fuentes históricas, proyecto y licencia encontrados; falta vínculo autoritativo paquete→revisión/build. Véase B04. |
| R012 dos DLL nativas Mono | **AÚN SIN EVIDENCIA SUFICIENTE** del binario exacto | Mismo nupkg 1.0.0; no etiqueta/build declarada. Las versiones nativas vacías y la firma no completan la procedencia. |
| R019 siete nativos OpenJFX 22.0.2 | **AÚN SIN EVIDENCIA SUFICIENTE** del conjunto exacto | Fuente histórica jfx22u/22.0.2-ga con GPLv2 y excepción por archivo; falta correspondencia de build, porciones y avisos de las siete DLL del JAR. No extender GPL a las DLL Microsoft vecinas. |
| R021 cinco VC dentro de JavaFX | **DEPENDE DE REVISIÓN CUALIFICADA** | Microsoft publica condiciones y lista REDIST; acreditar procedencia/derechos sobre versión 14.36.32532.0 y obligaciones del redistribuidor. |
| R022 42 UCRT/API sets JavaFX | **DEPENDE DE REVISIÓN CUALIFICADA** | Microsoft documenta UCRT como componente Windows y reglas de despliegue local; eso no acredita automáticamente la redistribución del conjunto 10.0.17763.132. |
| R023 tres VC del JDK actual | **DEPENDE DE REVISIÓN CUALIFICADA** | Mismo problema de términos/procedencia de VC; la migración futura obliga a inventariar los nuevos bytes. |
| R025 PawnIO_setup.exe 2.2.0.0 | **AÚN SIN EVIDENCIA SUFICIENTE** del EXE completo | Tag Setup 2.2.0 solo contiene README de repositorio de releases; licencia del driver no cubre por inferencia todo el contenedor. Borrador de consulta separado, no enviado. |
| R029 runtime IGCL externo | **DEPENDE DE REVISIÓN CUALIFICADA** | No se copia; licencia pública Intel de Control API identificada, pero versión/términos del driver local no acreditados. |
| R030 NVML externo | **DEPENDE DE REVISIÓN CUALIFICADA** | No se copia; documentación API y acuerdo público de driver NVIDIA consultados. Debe acreditarse qué contrato corresponde al driver concreto; no se afirma permiso de redistribuir NVML para Windows. |
| R031 APIs/DLL Windows externas | **DEPENDE DE REVISIÓN CUALIFICADA** | No se copian. Separar uso de API del permiso de copiar DLL y de la excepción de bibliotecas del sistema de SignPath; versiones/contrato concretos NV. |
| R032 CRT estático | **DEPENDE DE REVISIÓN CUALIFICADA** | CMake fija MultiThreaded en Release: el código CRT sí viaja enlazado. Verificar licencia del toolchain y lista/condiciones aplicables, no omitirlo por ausencia de una DLL separada. |

Balance **16 = 2 cierres documentales propios + 4 aún sin evidencia exacta +
10 dependientes de revisión cualificada**. No se declaran 14 componentes
ilegales ni 2 artefactos listos para publicar. Las demás 87 filas no se
re-auditaron y conservan los límites de la auditoría histórica.

### Los cuatro cotejos históricos del muestreo

| Cotejo | Evidencia nueva / estado |
|---|---|
| Setup PawnIO | Tag 2.2.0 leído: README, sin fuente/licencia integral del contenedor. **NO VERIFICADO** el alcance exacto. |
| Módulos PawnIO de LHM | El README junto a los recursos, fijado a 3d331e3, declara procedencia 0.1.6 y contiene COPYING LGPL 2.1. El árbol oficial 0.1.6 **no contiene IsaBridgeEC.p**, aunque LHM incluye IsaBridgeEC.bin entre sus 12 recursos. Por ello no puede atribuirse ese tag a los doce sin más evidencia. **NO VERIFICADO** el conjunto exacto. |
| Mono 1.0.0 | Fuentes históricas y proyecto compatibles con el nombre/assembly version; todavía sin prueba que vincule esos bytes al tag. **NO VERIFICADO**; los ensayos físicos 0 no resuelven ese vacío. |
| musl, aviso .NET | Aviso local declara MIT para musl y porciones adicionales; acredita el texto agregado, **NO VERIFICADO** qué porción/versión binaria está presente en win-x64. No afirmar que todo el runtime Windows contiene musl ni que su ausencia se demostró. |

Se mantienen **4 cotejos NO VERIFICADOS**, con evidencia parcial mejorada y
una discrepancia de procedencia concreta de módulos; no se fabrica una reducción.

## B04 — Mono histórico: qué se pudo acreditar

Se releyó el **nupkg 1.0.0 ya restaurado**, sin descargar otro: nuspec con
licenseUrl `https://go.microsoft.com/fwlink/?linkid=869050` y projectUrl con
linkid=869051; sin elemento repository ni texto LICENSE/NOTICE/COPYING.
La DLL win-x64 declara 1.0.0/1.0.0.0; las dos nativas no declaran FileVersion.
No se obtiene una revisión del repositorio de esos campos.

Fuentes primarias históricas leídas:

- [Ficha NuGet 1.0.0](https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0):
  fecha de publicación 5 de marzo de 2018. La ficha no proporciona el puente
  completo hacia un commit de cada DLL.
- [LICENSE del tag mono-5.10.0.160](https://github.com/mono/mono/blob/mono-5.10.0.160/LICENSE):
  establece MIT general y porciones con otros términos; no se tomó main como
  prueba histórica ni se supone que todo el árbol sea MIT.
- [Proyecto NETStandard del mismo tag](https://github.com/mono/mono/blob/mono-5.10.0.160/mcs/class/Mono.Posix/Mono.Posix.NETStandard-netstandard_2_0.csproj)
  y [AssemblyInfo](https://github.com/mono/mono/blob/mono-5.10.0.160/mcs/class/Mono.Posix/Assembly/AssemblyInfo.cs):
  nombre y assembly version compatibles, configuración y texto MIT en ese archivo.
- [support/stdlib.c](https://github.com/mono/mono/blob/mono-5.10.0.160/support/stdlib.c):
  fuente nativa histórica consultada, sin prueba de equivalencia de compilación.

Ese tag se investigó como **candidato histórico**, no como fuente declarada
del nupkg. No basta la coincidencia de nombre/versión/época. Falta declaración
del editor o evidencia de build/publicación que vincule paquete, commit,
fuentes nativas y licencias correspondientes. R011/R012 siguen abiertas;
no se les asigna MIT definitivo ni se impone otra licencia al usuario.

## B15 — Matriz de términos públicos y obligaciones por revisar

En esta tabla «acción» es preparación de cumplimiento, **no dictamen de
permiso ni aceptación contractual por el agente**. No se aceptó una EULA.

| Componente / modalidad | Fuente pública leída | Término o permiso declarado | Acción verificable y pregunta pendiente |
|---|---|---|---|
| Setup PawnIO intacto | [Setup tag 2.2.0](https://github.com/namazso/PawnIO.Setup/tree/2.2.0) | Repositorio de releases; no licencia integral acreditada | Mantener intacto y fuera de refirma; obtener alcance/fuentes/obligaciones del EXE exacto antes de publicar. |
| Driver PawnIO, dentro del setup | [README oficial](https://github.com/namazso/PawnIO/blob/master/README.md) | GPL-2.0-or-later y excepción específica para LGPL/IOCTL; distingue interfaz Pawn; anuncia licencias alternativas | Preservar términos y acreditar fuente del driver empaquetado; revisor debe determinar entrega/oferta de fuentes y aplicación de la excepción. No se deduce un contrato comercial. |
| Módulos PawnIO de LHM | [README fijado](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Resources/PawnIo/README), [COPYING](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Resources/PawnIo/COPYING), [IntelMSR 0.1.6](https://github.com/namazso/PawnIO.Modules/blob/0.1.6/IntelMSR.p) | LGPL 2.1 en aviso; IntelMSR histórico permite versiones posteriores | Obtener correspondencia de los doce recursos, especialmente IsaBridgeEC, fuente y scripts; evaluar obligaciones LGPL y sustitución/relink según modalidad concreta. |
| VC dinámico de Java/JavaFX y CRT estático propio | [Redistribución Microsoft](https://learn.microsoft.com/en-us/cpp/windows/redistributing-visual-cpp-files?view=msvc-170), [directorio de términos](https://visualstudio.microsoft.com/license-terms/vs2022-cruntime/) | Microsoft condiciona redistribución a licencia de Visual Studio y términos aplicables; remite a REDIST | Identificar toolchain/licencia de quien construye y binarios autorizados; conservar avisos. La página de términos enlaza documento embebido: no se declara leído el contrato completo ni aceptado. |
| UCRT / API sets | [Despliegue UCRT](https://learn.microsoft.com/en-us/cpp/windows/universal-crt-deployment?view=msvc-170) | Componente Windows 10+; despliegue local tiene restricciones y usa el UCRT del sistema en Windows 10/11 | Inventariar las copias que viajan aunque no se carguen; revisar derechos/condiciones del SDK. No borrar copias ni instalar redistribuibles en esta tarea. |
| Cabecera IGCL / uso de Control API | [Licencia Intel](https://github.com/intel/drivers.gpu.control-library/blob/master/LICENSE), copia local 10.07.21 | Uso/redistribución solo en plataformas Intel; conservar términos/avisos; otras restricciones | Revisor delimita alcance para MACE multiplataforma de hardware. Dos laptops Intel no acreditan permiso universal. Preservar aviso existente. |
| Runtime IGCL del driver | Misma licencia pública Intel, contrato del driver concreto NV | No se copia en la imagen | Declarar dependencia externa y términos de uso del driver; decisión SignPath separada. No inspección de laptops ni cambio de proveedor. |
| NVML del driver NVIDIA | [API NVML](https://docs.nvidia.com/deploy/nvml-api/latest/nvml-api-reference.html), [acuerdo público de driver](https://www.nvidia.com/en-us/drivers/geforce-license/) | API y acuerdo de uso con condiciones/restricciones; no prueba de contrato histórico de cada driver | No redistribuir DLL NVML; identificar acuerdo aplicable del equipo por el usuario cuando corresponda. La excepción pública de redistribución para determinados kernels no se extiende por inferencia a Windows. |
| DLL/API del SO | Documentación Microsoft de uso de API/UCRT; licencia Windows concreta NV | Uso de API no equivale a licencia de copia del SO | Mantener dependencia externa, sin empaquetar DLL del SO; consultar encaje de bibliotecas de sistema con SignPath. |

Fuentes adicionales históricas de JavaFX: [LICENSE 22.0.2-ga](https://github.com/openjdk/jfx22u/blob/22.0.2-ga/LICENSE),
[ADDITIONAL_LICENSE_INFO](https://github.com/openjdk/jfx22u/blob/22.0.2-ga/ADDITIONAL_LICENSE_INFO)
y [cabecera GlassApplication.cpp](https://github.com/openjdk/jfx22u/blob/22.0.2-ga/modules/javafx.graphics/src/main/native-glass/win/GlassApplication.cpp).
Acreditan términos de fuente y separación de programas; no todos los bytes del JAR.
Consultas previas a refs `openjdk/jfx/22.0.2` y `jfx22.0.2` fallaron; no se
utilizan como evidencia. La fuente válida consultada es **jfx22u/22.0.2-ga**.

## B17 — Orientación y límites

La guía FASE3-GUIA-REVISION-SIN-REVISOR.md se actualiza con la reconsulta de
SFLC, FSFE y Conservancy, condiciones y costo publicado de esta última.
**No hay revisor designado, dictamen, tarifa comercial baja comprobada ni
promesa de admisión gratuita**. El borrador para el autor está en
FASE3-CONSULTA-PAWNIO-BORRADOR.md, sin enviar ni abrir incidencia.

## Ruta crítica mínima antes de una primera release binaria

1. Fijar bytes reales de la candidata autorizada; inventario por componente,
   fuentes/versiones, modalidad de copia y dependencia externa. La referencia
   actual no acredita el nuevo JDK/JavaFX ni sus avisos.
2. Acreditar licencia/fuentes del setup/driver PawnIO y de los doce módulos;
   resolver el desacuerdo de IsaBridgeEC y el vínculo histórico de Mono si
   se conserva. Ensayos 0 y firmas upstream no reemplazan esa evidencia.
3. Identificar términos de toolchain/VC/UCRT, cabecera y drivers; revisión
   cualificada de derechos, obligaciones GPL/LGPL/MPL y límites de las excepciones.
4. Materializar textos, atribuciones y fuentes/oferta válida que determine el
   revisor. Avisos finales solo tras contraste con el payload; siguen borrador.
5. Revisor y usuario cierran expresamente la revisión legal. Después, resolver
   admisión/configuración de SignPath si se pretende esa firma; Inno, motor,
   desinstalador y terceros intactos siguen sujetos a su decisión.
6. Completar validación técnica/cadena de integridad y pruebas de instalación
   futuras, sin confundir su aprobación con permiso de redistribución.

No se publica una nueva release binaria hasta ese cierre. La publicación del
código fuente MIT sigue la decisión del usuario. No se solicita nueva elección
de MIT, DCO ni instalador. B15/B17/B16/B19 no quedan cerrados por este informe.
