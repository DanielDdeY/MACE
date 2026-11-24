# Auditoría del inventario del Paso A

Fecha: **2026-11-23**, America/Lima. Partida: **d922d99**, rama
`LHM_implementado`; sin cambios en archivos seguidos al comenzar. Dos documentos
previos sin seguimiento se conservan fuera de los commits de esta auditoría.
Autor del informe y auditor: el mismo agente; **no es revisión externa ni
asesoría legal**. Los derechos de redistribución requieren revisión cualificada.

## Veredicto

**El informe original necesitaba corrección.** Se registran **9 hallazgos:
1 error de recuento, 1 afirmación desactualizada y 7 carencias de acreditación/presentación**. No se
afirma ausencia global de discrepancias. Las licencias que sí pudieron
contrastarse por muestreo no contradicen su fuente dentro del alcance
limitado indicado; no se da por acreditado el conjunto distribuido.

La cobertura física y los pins coinciden: 370 archivos de imagen asignados,
202 de ejecución del helper y 205 del publicado; cero archivos sin componente.
Hay **10 JAR, no 11**, y once módulos declarados por el runtime jlink.
No está listo el dossier de admisión a SignPath. B permanece detenido.

Se releyeron inventario y adenda. El usuario confirmó que
`prompt_mace_licencia_firma.md` es el mismo documento recibido como `3.1c.md`;
se releyó este último. También aclaró que **mantiene MIT confirmado**: el
literal g del checklist que decía C1 pendiente contradice esa confirmación
y queda sustituido por su aclaración, no por una decisión del auditor.

## 1. Método reproducible y límites

Scripts y datos de referencia en [auditoria-licencias](auditoria-licencias/README.md):

- `comprobar.ps1`: Windows PowerShell 5.1, sin administrador; invoca Python.
- `audit.py`: biblioteca estándar, lectura de archivos/ZIP/XML/PE como datos;
  primero captura fuentes locales, después lee y compara el informe.
- `fuentes-oficiales.json`: instantánea compacta de lecturas oficiales, con
  fecha, URL final, estado, IDs SPDX y listado OSI. No es una consulta nueva
  cuando se usa offline; `-ConsultarFuentes` vuelve a hacer GET públicos.

Únicas escrituras del arnés: informes CSV/JSON bajo una subcarpeta de
`native/build/`. No ejecuta MACE, helper, PawnIO, JVM del producto, instaladores
o pruebas de sensores; no instala, restaura, compila, eleva, toca servicios,
registro, imagen, manifiestos ni entorno de usuario/máquina. No envía archivos.

La separación captura/cotejo reduce la dependencia del texto previo, pero
el auditor humano ya lo conoce; **no equivale a una revisión humana ciega**.
Las comprobaciones de texto no pueden demostrar por sí solas cumplimiento
legal, inexistencia de contratos alternativos o ausencia universal de red.

## 2. Comprobaciones mecánicas

| Punto | Resultado | Cómo / evidencia reproducible |
|---|---|---|
| 1. Imagen | PASS: 370/370, 147.787.297 bytes, 0 sin componente, 0 extras | `cobertura-imagen.csv`; cada nombre a fila, con deps para helper y casos explícitos; comparación de todos los pins sin exportarlos |
| 1. Helper | PASS: 202/202 del manifiesto; 205/205 del publicado | CSV helper/publicado; el publicado seleccionado coincide además con los 202 pins de la imagen, no solo con nombres iguales |
| 1. Exclusiones | 3 identificadas | createdump.exe → runtime .NET; Mace.Lhm.Helper.pdb y Mace.Lhm.Protocol.pdb → proyectos propios. No viajan en imagen; artifact-manifest.json es metadato externo al conjunto 205 |
| 1. JAR / jlink | PASS: 10 JAR, 54 DLL internas, 11 módulos | ZIP enumerados en fuentes-locales.json; módulos desde runtime/release, con su directorio legal. No se ejecutó jimage ni se demostró la correspondencia de cada porción comprimida |
| 2. Filas | 103: 34 principales, 23 avisos Java, 46 avisos .NET | No son 103 paquetes independientes: existen filas agregadas y subcomponentes declarados |
| 2. ARCHIVO_LOCAL | 74 / 103 = 71,84 % | Texto real de licencia o aviso, archivo concreto en evidencia-por-fila.csv |
| 2. METADATO_PAQUETE | 8 / 103 = 7,77 % | Nuspec/POM reales; se identifica como declaración, no como licencia local incluida |
| 2. URL_OFICIAL | 5 / 103 = 4,85 % | Texto oficial accesible; driver/módulo actuales no acreditan correspondencia de los binarios históricos |
| 2. SIN_EVIDENCIA | 16 / 103 = 15,53 % | No hay licencia acreditada suficiente para esa fila/artefacto; no se marca verificada |
| 3. Versiones | 45 comparaciones PASS; 0 discrepancias; 58 filas NV | versiones.csv y versiones-no-acreditadas.csv; nuspec, VERSIONINFO PE dentro/fuera del ZIP, POM, pom.properties y títulos de avisos |
| 4. SPDX / OSI | 24 identificadores cotejados, incluidos 5 LicenseRef y una excepción | 743 licencias en lista SPDX consultada; 123 IDs no vacíos en tabla OSI; archivos spdx-osi.csv e identificador-ausente.csv |
| 5. Doble licencia comercial | 103 filas NO VERIFICADO | CSV individual; ausencia en texto no prueba inexistencia de contratos. Fuente del driver sí ofrece alternativas, sin probar modalidad comercial concreta |
| 6. Lenguaje dubitativo | 0 antes y después | Búsqueda de las seis expresiones pedidas, con variantes acentuadas y límites de palabra; lenguaje.csv |
| 7. URLs | 26 abiertas, 1 intento no utilizable | Registro abajo y urls.csv; la API OSI fallida no se usa para determinar estado OSI |
| 8. Datos locales | 0 en inventario/scripts/datos; revisión adicional de entregables | Regex de perfil/SID/hash completo/correo y búsqueda de identidad real del entorno en memoria, sin serializar sus valores |

Los porcentajes cuentan **fuente predominante por fila**, no archivos ni
licencias plenamente verificadas; redondean a dos decimales. Un aviso
agregado local no acredita versión/presencia de todos sus componentes.
Las cinco filas propias SIN_EVIDENCIA significan que MIT aún no está
materializada en LICENSE y que componentes del stub/CRT requieren su alcance,
**no** que la elección del usuario sea desconocida.

Las otras once filas SIN_EVIDENCIA suficiente son: ambas filas Mono,
nativos OpenJFX como conjunto, VC y UCRT internos de JavaFX, VC de runtime/bin,
PawnIO_setup.exe, runtime IGCL, NVML, APIs/DLL Windows y CRT estático.
No se ha inferido su licencia del firmante, de un nombre o de memoria.

### SPDX y OSI: resultados que no deben confundirse

- Unicode-3.0 está identificado y listado por ambas fuentes.
- FTL, IJG, BSD-4-Clause, CC0-1.0, InnoSetup y libpng-2.0: booleano SPDX
  falso y sin esos IDs exactos en el listado OSI consultado. Esto no equivale
  a «prohibido redistribuir» ni a una decisión de SignPath sobre MACE.
- W3C: SPDX marca aprobado, pero la tabla OSI actual no muestra ese ID
  exacto. No se declara revocación; correspondencia histórica NV.
- Los aliases GPL/LGPL usados por la tabla OSI quedan explícitos en el CSV.
  Se normaliza la referencia histórica GPL-3.0 a GPL-3.0-only; no se elige GPL.
- Classpath-exception-2.0 es una excepción registrada, no una licencia
  independiente aprobada por OSI. Los cinco LicenseRef son identificadores
  locales; NOASSERTION sigue sin acreditar una licencia estándar.

## 3. Muestreo independiente de fuentes: diez obligatorios y tres al azar

La selección adicional se fijó con semilla pública **20261123**, universo de
las otras 93 filas, ordenadas y sin ponderar por tamaño. Resultó c-libutl,
musl y System.Management. La captura primaria no toma licencias del informe.
Resultado: **9 cotejos sin contradicción en el alcance probado, 4 NV**.

| Componente | Derivación nueva de fuente primaria | Cotejo y límite |
|---|---|---|
| Inno Setup | license.txt oficial: InnoSetup; listas SPDX/OSI independientes | Coincide; no acredita admisión ni una build MACE aún inexistente |
| PawnIO_setup.exe | PE 2.2.0.0 y copia fijada; fuente actual del driver GPL con excepción no licencia todo el setup | NO VERIFICADO: falta licencia/fuente correspondiente del binario completo |
| Módulos PawnIO | Fuente actual IntelMSR.p declara LGPL-2.1-or-later | NO VERIFICADO para atribuirlo a los doce recursos exactos; no ampliar esa licencia por inferencia |
| Mono.Posix.NETStandard | Nuspec real 1.0.0 sin ID/tag; enlace de licencia accesible hacia main de Mono | NO VERIFICADO para correspondencia histórica y los tres binarios; corregida afirmación sobre acceso al enlace |
| JDK empaquetado | runtime/release 22.0.2 y LICENSE real Oracle NFTC | Coincide; proveedor futuro y derechos completos pendientes |
| JavaFX empaquetado | POM padre restaurado 22.0.2 declara GPLv2+CE; ZIP contiene binarios Microsoft separados | Coincide con separación de licencias; no prueba fuente/build de todo nativo ni elegibilidad global |
| LibreHardwareMonitorLib | Nuspec 0.9.6 MPL-2.0 y texto oficial v0.9.6 | Coincide para biblioteca; recursos no se relicencian automáticamente |
| RAMSPDToolkit-NDD | Nuspec 1.4.2 MPL-2.0 y LICENSE del commit corto declarado | Coincide; no es una recompilación reproducible ni revisión del driver |
| HidSharp | LICENSE.txt del paquete real: Apache License 2.0 | Coincide; no se atribuye MIT por autor/nombre |
| Cabecera IGCL | LICENSE versionado 10.07.21, restricción de plataforma y avisos | Coincide; versión/tag de cabecera y encaje en programa NV |
| c-libutl, azar | Texto local 20160225, dos condiciones BSD y exención | Coincide con BSD-2-Clause; presencia binaria individual NV |
| musl, azar | Aviso .NET indica MIT para conjunto y porciones con otros términos | NO VERIFICADO el conjunto completo; el informe ya lo limitaba |
| System.Management, azar | Nuspec 10.0.2, elemento license MIT | Coincide como declaración del paquete; avisos se conservan |

La ausencia de contradicción en esos nueve cotejos **no** es una afirmación
de que los trece componentes están aprobados, completamente licenciados o
verificados en ejecución Windows. Mono cargado/excluible sigue NV; no se
ejecutó ningún producto para intentar demostrarlo.

## 4. Registro de correcciones, sin reescribir historia

| ID | Qué decía o faltaba | Qué dice ahora / motivo |
|---|---|---|
| D01, error | 11 JAR en recuento/alcance | 10; enumeración ZIP y suma 1 propio + 1 AtlantaFX + 8 OpenJFX |
| D02, carencia | 8 columnas combinadas y anexos de 4/5, sin tabla literal de once | 103 filas con once columnas explícitas; se distingue compilador exacto NV del origen ajeno |
| D03, carencia | Agrupación por directorios como soporte de cobertura | Asignación archivo→fila, verificación 370/202/205, tres exclusiones y módulos/JAR enumerados |
| D04, carencia | E1–E9 demasiado generales; sin categoría y alcance individual | Cuatro categorías por fila; 16 sin evidencia de licencia suficiente explícitamente no acreditadas |
| D05, carencia | «No declarada» podía leerse como inexistencia de doble licencia | NO VERIFICADO por componente; positivo limitado al anuncio de alternativas del driver |
| D06, carencia | IDs/estados de varios avisos sin cotejo sistemático de ambas listas | Listas oficiales separadas, personalizados/excepción, Unicode acreditada y divergencia W3C delimitada |
| D07, afirmación desactualizada | No se pudo acreditar un destino útil del enlace Mono | Se abrió y redirige a main de Mono; no acredita automáticamente los tres binarios 1.0.0. No se afirma que la lectura previa hubiera tenido éxito |
| D08, carencia de normalización | Referencia histórica GPL-3.0 sin aclarar ID deprecado | GPL-3.0-only; comparación histórica, MIT confirmada intacta |
| D09, carencia de reproducibilidad | Revisión estática de privacidad descrita, sin búsqueda entregada | CSV archivo/línea/categoría, 0 APIs HTTP/socket del patrón, con límites explícitos sobre terceros/carga dinámica |

El inventario se corrige en un commit separado titulado
**`docs: corrección tras auditoría`**. Primer commit de arnés/datos:
**b71dc07**. No se hace amend, rebase ni push; el original d922d99 permanece.
No se añaden LICENSE, metadatos de compilación ni THIRD-PARTY-NOTICES.

## 5. Checklist solicitado

| Ítem | Estado | Evidencia / alcance exacto |
|---|---|---|
| a. Once columnas | FAIL original → PASS corregido | 103/103 filas con campos separados; NV se conserva donde no existe respuesta acreditada |
| b. C1–C9 completos | PASS | 9/9 contienen opciones, recomendación, riesgo y decisión; lectura humana y estructura-conflictos.csv |
| c. C2 Inno | PASS documental / admisión NV | Loader/motor/desinstalador, avisos y URLs preservados, relevancia de OSI y NSIS con compresión/plugins auditados; no herramienta cambiada |
| c. C3 PawnIO | NO VERIFICADO | Derechos/fuentes exactos del setup y módulos aún sin cerrar. Sí están descritos intacto/exclusión de firma propia, conservación de firma y obligaciones a acreditar |
| c. C4 Mono | NO VERIFICADO | Enlace actual accesible; ninguna prueba física de carga o exclusión segura. No se borra el payload |
| c. C5 JDK/JavaFX | PASS de identificación; conjunto NV | Oracle/NFTC 22.0.2 y POM OpenJFX 22.0.2 GPLv2+CE; librerías Microsoft separadas, no todos los nativos acreditados |
| c. C8 privacidad | PASS estático / ejecución NV | Búsqueda reproducible y revisión del inventario de escrituras; no captura de tráfico ni promesa absoluta |
| d. Preguntas | PASS | Once preguntas más tratamiento de licencias de runtimes no listadas OSI; no enviadas |
| e. Diff de alcance | PASS | Frente a d922d99 solo inventario, dos informes y cuatro archivos de arnés/datos; todos bajo helper; 370 pins intactos |
| f. No fingir admisión | PASS documental | Se distinguen candidato, tercero y bloqueo; no afirma SignPath aprobado ni compatibilidad universal |
| g. C1 pendiente literal | PASS tras aclaración del usuario | Mantener MIT confirmado; el literal inicial se descarta; no se reabre C1 ni se elige una alternativa por él |
| h. Diseño/adenda | PASS con precedencia explícita | Sección 7 de adenda actualiza diseño histórico: Inno, MIT, D4(a), JDK separado, SignPath pendiente; contratos congelados intactos |

No hay contradicción nueva con decisiones vigentes. La sección histórica de
la adenda aún habla de WiX/D1/D7 pendientes a fecha anterior; su nota de
precedencia/sección 7 impide leerlo como estado actual. No se modifica esa
historia. Se conserva el gate de diff aparte para JDK, consumidor 3010 y
cualquier cambio de metadatos/payload, con aprobación del usuario.

## 6. Fuentes públicas: propiedad, acceso y fecha

Todas las lecturas siguientes se hicieron el **2026-11-23, America/Lima**;
los instantes UTC concretos están en la instantánea. Solo GET sin cuentas,
cookies de usuario ni archivos del proyecto. Una autoridad de licencias no
es necesariamente el titular del componente. Acceso no prueba correspondencia
con la versión histórica ni que el texto cubra cada binario.

La tabla de URLs completa se conserva a continuación y en `urls.csv`.
La API OSI produjo HTTP 500/no utilizable en la lectura web inicial;
se utilizó la página oficial de licencias, no ese intento como evidencia.

| URL consultada | Titular/proyecto o autoridad | Acceso el 2026-11-23 |
|---|---|---|
| [fuente](https://adoptium.net/docs/faq) | Eclipse Adoptium; distribucion Temurin | Abierta |
| [fuente](https://github.com/Blacktempel/RAMSPDToolkit/blob/3b47b96/LICENSE) | Proyecto RAMSPDToolkit; repositorio declarado en nuspec | Abierta |
| [fuente](https://github.com/namazso/PawnIO.Setup/releases/tag/2.2.0) | Proyecto PawnIO/namazso | Abierta |
| [fuente](https://github.com/openjdk/jfx/) | Proyecto OpenJFX/OpenJDK | Abierta |
| [fuente](https://go.microsoft.com/fwlink/?linkid=869050) | Microsoft: redireccion al proyecto Mono, version historica NV | Abierta; redirige a licencia Mono main |
| [fuente](https://jrsoftware.org/inno-setup/documentation/topic_setup_usesetupldr.htm) | Autores/proyecto Inno Setup | Abierta |
| [fuente](https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files) | Microsoft; guia, no licencia individual de cada DLL | Abierta |
| [fuente](https://nsis.sourceforge.io/License) | Proyecto NSIS | Abierta |
| [fuente](https://opensource.org/license/MPL-2.0) | OSI, autoridad de aprobacion | Abierta |
| [fuente](https://opensource.org/license/mit) | OSI, autoridad de aprobacion | Abierta |
| [fuente](https://opensource.org/licenses) | OSI, autoridad de aprobacion | Abierta |
| [fuente](https://raw.githubusercontent.com/Blacktempel/RAMSPDToolkit/3b47b96/LICENSE) | Proyecto RAMSPDToolkit; repositorio declarado en nuspec | Abierta |
| [fuente](https://raw.githubusercontent.com/LibreHardwareMonitor/LibreHardwareMonitor/v0.9.6/LICENSE) | Proyecto LibreHardwareMonitor | Abierta |
| [fuente](https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt) | Autores/proyecto Inno Setup | Abierta |
| [fuente](https://raw.githubusercontent.com/mkpaz/atlantafx/v2.0.1/LICENSE) | Proyecto AtlantaFX | Abierta |
| [fuente](https://raw.githubusercontent.com/mono/mono/main/LICENSE) | Proyecto Mono; no acredita el paquete 1.0.0 | Abierta |
| [fuente](https://raw.githubusercontent.com/namazso/PawnIO.Modules/main/IntelMSR.p) | Proyecto PawnIO/namazso | Abierta |
| [fuente](https://raw.githubusercontent.com/namazso/PawnIO/master/README.md) | Proyecto PawnIO/namazso | Abierta |
| [fuente](https://raw.githubusercontent.com/spdx/license-list-data/main/json/exceptions.json) | SPDX, autoridad de catalogo; no titular del componente | Abierta |
| [fuente](https://raw.githubusercontent.com/spdx/license-list-data/main/json/licenses.json) | SPDX, autoridad de catalogo; no titular del componente | Abierta |
| [fuente](https://signpath.org/terms) | SignPath Foundation; condiciones del programa | Abierta |
| [fuente](https://spdx.org/licenses/CC0-1.0.json) | SPDX, autoridad de catalogo; no titular del componente | Abierta |
| [fuente](https://spdx.org/licenses/InnoSetup.json) | SPDX, autoridad de catalogo; no titular del componente | Abierta |
| [fuente](https://spdx.org/licenses/libpng-2.0.json) | SPDX, autoridad de catalogo; no titular del componente | Abierta |
| [fuente](https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0) | NuGet, registro oficial de paquete; no titular | Abierta |
| [fuente](https://www.oracle.com/downloads/licenses/no-fee-license.html) | Oracle, titular de NFTC | Abierta |
| [fuente](https://api.opensource.org/licenses/) | OSI, autoridad de aprobacion | NO VERIFICADO; intento no utilizable |

## 7. Alcance final, riesgos y parada

Verificaciones finales: Windows PowerShell 5.1, arnés con código 0; protección
PASS; auditoría de referencias **181 aceptadas y 0 inesperadas**; cero enlaces
locales rotos y cero datos locales en los siete archivos entregados y en los
20 informes locales examinados. Prueba negativa del arnés: salida dirigida
a la imagen rechazada con código 1 antes de escribir; después se verifican
de nuevo los 370 pins. No hubo ejecución del producto.

El diff contra d922d99 se guarda como `diff-stat.txt` junto a los resultados,
con solo nombres relativos y cifras; siete archivos bajo helper. No contiene
los dos documentos previos sin seguimiento ni los tres TXT excluidos.

Regresión de helper/CTest/Java/payload de producto **no repetida**: el usuario
autorizó solo documentación/comprobaciones estáticas. No se presenta el código
0 del arnés como prueba física o legal. Comprobaciones de protección/auditoría
del repositorio son las indicadas arriba; no se ejecuta producto.

Quedan NO VERIFICADOS los componentes/alcances identificados en las tablas,
la ausencia universal de licencias comerciales alternativas, las versiones
de 58 filas, carga/exclusión Mono, correspondencia de fuentes/bytes de
PawnIO y recursos, actividad dinámica de terceros, admisión y MFA/configuración
SignPath, versiones futuras del instalador/JDK y las pruebas físicas pendientes.
La titularidad MIT se registra por declaración, no dictamen jurídico.

Los informes largos quedan locales y saneados bajo native/build; no se
incluyen los tres TXT excluidos, perfiles, identidades del host o hashes
completos en commits. **DETENERSE. Sin avanzar a B ni 3.2.**
