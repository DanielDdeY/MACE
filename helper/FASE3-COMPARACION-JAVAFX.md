# A — JavaFX 27.0.1.1 frente a la línea 25 LTS

Fecha: **2026-11-25**, America/Lima. Temurin **25 LTS aprobado como línea**;
**JavaFX no seleccionado**. Solo documentación, GET de XML y HEAD HTTP:
no se descargaron cuerpos JAR/JMOD/SDK, instalaron herramientas o ejecutaron UI.
Esta tarea no autoriza aplicar el antiguo JDK-JAVAFX.patch.

## Resultado de disponibilidad y mantenimiento

| Criterio | JavaFX 27.0.1.1 | Línea JavaFX 25 LTS |
|---|---|---|
| Release contrastada | 27.0.1.1, 18/11/2026 según notas de Gluon | 25.0.5.1, indicada por tabla Gluon y publicada en Maven Central |
| Mínimo JDK oficial | 25 | 23; por mínimo también admite Temurin 25 |
| Distribución pública comprobada | POM y cabeceras de cuatro JAR win sin autenticación | **También** POM/cabeceras de cuatro JAR win 25.0.5.1 sin autenticación |
| Actualizaciones | Línea principal pública; seguir parches y la siguiente línea cuando cese mantenimiento | Gluon ofrece backports y garantías LTS por contrato; el acceso público observado hoy no acredita una garantía gratuita futura |
| Licencia base | OpenJFX GPL con excepción Classpath; nativos ajenos requieren sus propios términos | Misma distinción: etiqueta LTS/servicio no sustituye licencias del contenido |
| AtlantaFX 2.0.1 | Compatibilidad NO VERIFICADA | Compatibilidad NO VERIFICADA |
| Costo/garantías | Disponibilidad pública no equivale a SLA ni a revisión de redistribución | No se contrata LTS. La página de soporte separa comunidad/mainline de builds certificados y garantías comerciales |

**Hallazgo que actualiza la propuesta anterior:** no sería correcto afirmar
que 25.0.5.1 no está en Maven público o que todo parche 25 exige pago. Se
abrieron sus XML y se recibieron HEAD 200 de los JAR win. Tampoco se deduce
de eso que todos los backports futuros de 25 se ofrecerán gratuitamente.

Fuentes oficiales leídas el 25/11:
[tabla/descargas Gluon](https://gluonhq.com/products/javafx/),
[política de soporte](https://gluonhq.com/services/javafx-support/),
[notas 27](https://gluonhq.com/products/javafx/openjfx-27-release-notes/),
[mínimos y cambios 25](https://openjfx.io/highlights/25/),
[mínimos y cambios 27](https://openjfx.io/highlights/27/),
[licencia base](https://openjfx.io/).
La introducción de Gluon aún llama 26 a la última versión, aunque tabla y
notas muestran 27: se usa evidencia específica, no ese texto desactualizado.
Las notas detalladas 25 no pudieron abrirse con la herramienta web: **NV**
su lista completa, sin extrapolarle todas las correcciones de 27.

## Coordenadas exactas, publicadas y consultables

Grupo **org.openjfx**, versiones consultadas **27.0.1.1** y **25.0.5.1**.
Para **cada versión**, se verificó el POM de los siguientes artifactId y
se hizo HEAD al mismo artefacto con classifier **win**, empaquetado **jar**:

| artifactId | POM 27 / HEAD win | POM 25 / HEAD win |
|---|---|---|
| javafx-base | 200 / 200 | 200 / 200 |
| javafx-graphics | 200 / 200 | 200 / 200 |
| javafx-controls | 200 / 200 | 200 / 200 |
| javafx-fxml | 200 / 200 | 200 / 200 |

Ejemplos exactos de dependencias Maven: `org.openjfx:javafx-controls:27.0.1.1`
y `org.openjfx:javafx-controls:25.0.5.1`; sustituir artifactId por cada fila
para el resto. Las coordenadas con classifier se expresan en XML con esos
groupId/artifactId/version más `<classifier>win</classifier>` y tipo jar.
No se mezclan versiones entre módulos ni classifier de otro sistema.

Consulta directa del repositorio público:
[metadata de controls](https://repo.maven.apache.org/maven2/org/openjfx/javafx-controls/maven-metadata.xml),
[POM 27](https://repo.maven.apache.org/maven2/org/openjfx/javafx-controls/27.0.1.1/javafx-controls-27.0.1.1.pom),
[POM 25](https://repo.maven.apache.org/maven2/org/openjfx/javafx-controls/25.0.5.1/javafx-controls-25.0.5.1.pom).
Se abrieron también ambos POM padre org.openjfx:javafx y los otros seis POM:
**10 GET POM correctos, 8 HEAD win correctos, 0 errores**. Salida saneada local
en `native/build/fase3-tareas-abcd/javafx-maven-verificado.json`.
La lista contiene 25, 25.0.1–25.0.5, 25.0.5.1 y 27/27.0.1/27.0.1.1; no se
elige la última entrada textual como la más nueva, porque no están en ese orden.
HEAD acredita acceso/metadatos, **no integridad, firma o ejecución del binario**.

## Compatibilidad, empaque y regresiones

Temurin 25 cumple los mínimos de ambas líneas y tiene FFM final. Eso no
acredita que MACE/AtlantaFX funcionen. El [POM AtlantaFX v2.0.1](https://github.com/mkpaz/atlantafx/blob/v2.0.1/pom.xml)
declara Java 17 y OpenJFX 20: no prueba una matriz con 25 o 27. Se requiere
regresión visual y de comportamiento, incluyendo CSS, controles, DPI,
ventanas/HUD, FXML, selección/ordenación y cierre del toolkit.

Cambios documentados relevantes: 25 elimina un constructor obsoleto de
TransitionEvent y deja de necesitar el antiguo permiso Unsafe mencionado en
sus notas; 27 cambia comportamientos de animación según accesibilidad y
contiene correcciones de listas/controles y ventanas. Las notas de 27.0.1
incluyen correcciones de CSS, FXML, portapapeles y apagado del toolkit; no se
presupone que todo fallo listado afecte a MACE ni que 25 carezca de backport.
WebKit/media no se añaden al payload por existir en las notas de release.

Se propone primero conservar **JAR** como ahora. jlink se genera con Temurin
25 para módulos JDK; jpackage crea app-image y dos launchers, sin instalar
JavaFX en el equipo. Usar JMOD exigiría mismo número de versión/arquitectura,
revisión de native-access y evitar duplicados app/runtime; es otra decisión.
Ambas opciones cambian bytes/legales, manifiesto global y sumas de una imagen
nueva. No cambian ABI 1.5 ni wire 2; no se aplica nada a la imagen actual.

## Cadencia y recomendación, sin fijar versión

Recomendación provisional: **preferir evaluar 27.0.1.1** si MACE puede seguir
la línea pública y revisar su sucesora aproximadamente cada semestre.
Revisar parches al publicarse y avisos de seguridad sin esperar a ese semestre;
no se promete un plazo de corrección del proveedor. No hay prueba de que
27 sea intrínsecamente más seguro que el parche 25 observado.

25.0.5.1 es una alternativa pública real, con menor salto funcional desde 22,
si se prioriza estabilidad. Exige vigilar disponibilidad pública de cada
parche LTS y decidir migración si deja de estar accesible; no descansar en un
SLA no contratado. Una futura JavaFX puede elevar su mínimo de JDK y exigir
revisar incluso la base Temurin; no se garantiza 25 para siempre.

**NO VERIFICADO:** ejecución de ambas combinaciones, compatibilidad AtlantaFX,
integridad/firma/contenido de los JAR/JMOD, notas completas 25, garantías
gratuitas futuras y regresión del producto. No se cambia el JDK ni se ocultan
tests. La combinación Mockito/Byte Buddy propuesta abajo no se ha probado
en un JDK 25 instalado.

## Dependencias de tests: propuesta concreta, no aplicada

Proponer un único cambio de propiedad: Mockito **5.12.0 → 5.20.0**, manteniendo
mockito-core y mockito-junit-jupiter alineados. El [catálogo v5.20.0](https://github.com/mockito/mockito/blob/v5.20.0/gradle/libs.versions.toml)
fija **Byte Buddy y byte-buddy-agent 1.17.7**. La [tabla de esa versión](https://github.com/raphw/byte-buddy/blob/byte-buddy-1.17.7/README.md)
indica Java 25+ desde 1.17.0, sin modo experimental. Las [notas Mockito](https://github.com/mockito/mockito/releases/tag/v5.20.0)
confirman 1.17.7. Fuentes leídas el 25/11; no se descargaron JAR.

“Cambio mínimo” significa una propiedad de tests sin forzar transitivas por
separado; no se acredita que sea la primera release histórica compatible.
POM publicado completo y árbol efectivo con JDK 25: **NO VERIFICADO** aquí.
Verificar dependency:tree antes de aplicar la combinación. No usar
net.bytebuddy.experimental, permisos experimentales o exclusiones de tests.
Si hace falta configurar agente de tests, presentar diff aparte con ruta
resuelta por Maven y argLine preservado; no ocultarlo omitiendo pruebas.
Las dependencias test no deben viajar en runtime final.

Tras aprobación del diff completo: árbol resuelve Mockito 5.20.0 y ambos
Byte Buddy 1.17.7; clean test en development y production. Comparar **347
aprobadas + 1 omitida histórica**, sin añadir omisiones ni usar la existente
para ocultar incompatibilidad. Probar mocks finales/estáticos usados, FFM
real, UI/Preview con JavaFX elegida, jlink/jpackage e imagen nueva.
Todo esto es plan **NO VERIFICADO**, sin modificar POM ni cambiar JDK del host.

## Decisión que queda al usuario

Elegir línea JavaFX después de revisar esta comparación y autorizar la
validación de una combinación completa. **No queda fijada ninguna JavaFX.**
