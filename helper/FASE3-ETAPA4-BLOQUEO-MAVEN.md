# Etapa 4 — bloqueo de resolución offline, sin descargas nuevas

Fecha: **2026-11-26**. Estado: **DETENIDA**, pendiente de decisión del usuario.
Ninguna URL de este documento se ha consultado en esta entrega.

## Error acreditado

La aplicación parcheada pasó dependency:tree y compilación Java development.
Al invocar `initialize` y `maven-surefire-plugin:3.2.5:test`, Maven offline falla
antes de iniciar JUnit:

> commons-io:commons-io:jar:2.15.1 no está disponible en el repositorio aislado.

La fuente local primaria es
`deps/m2/org/apache/maven/surefire/surefire/3.2.5/surefire-3.2.5.pom`,
línea 94, propiedad `commonsIoVersion=2.15.1`; líneas 124–125 la gestionan como
dependencia commons-io. La caché candidata contiene otras cuatro versiones,
pero no 2.15.1. No se la sustituye ni se usa una copia del caché personal.

La matriz estática del lote se aceptó como propuesta, pero **no fue suficiente
para el realm efectivo de Surefire**. El tree de dependencias de aplicación no
ejecuta ni cierra automáticamente todos los plugins/providers de prueba. Es un
defecto de completitud de la receta de resolución entregada, no evidencia de
un fallo funcional del código Java de MACE. Tampoco prueba que este sea el
único faltante: el primer error impide conocer todavía los siguientes.

Evidencia privada, no versionada:
`native/build/candidata-20261126-02/evidence/java-development-private.log`.
Salida **1**, **0 pruebas Java ejecutadas**, **0 supervivientes** registrados.

## Diecisiete advertencias de POM ausente

Son coordenadas observadas, no diecisiete descargas aprobadas ni una afirmación
de que todas sean imprescindibles para el resultado. No se cambian versiones
por las advertencias. En particular JavaFX 20 es metadata transitiva de AtlantaFX;
el árbol efectivo de la aplicación sigue seleccionando 27.0.1.1.

| groupId:artifactId | Versión del POM ausente |
|---|---|
| commons-beanutils:commons-beanutils | 1.8.3 |
| commons-io:commons-io | **2.15.1**; además falta su JAR, error fatal |
| commons-io:commons-io | 2.17.0 |
| commons-logging:commons-logging | 1.1.1 |
| org.apache.commons:commons-lang3 | 3.10 |
| org.apache.commons:commons-lang3 | 3.14.0 |
| org.apache.maven.shared:maven-common-artifact-filters | 3.1.0 |
| org.apache.maven:maven-artifact | 3.2.5 |
| org.apache.maven:maven-core | 3.2.5 |
| org.apache.maven:maven-plugin-api | 3.2.5 |
| org.apache.velocity:velocity-engine-core | 2.3 |
| org.codehaus.plexus:plexus-archiver | 4.9.2 |
| org.codehaus.plexus:plexus-component-annotations | 2.1.0 |
| org.codehaus.plexus:plexus-io | 3.5.0 |
| org.openjfx:javafx-controls | 20 |
| org.slf4j:slf4j-api | 1.7.30 |
| org.slf4j:slf4j-api | 1.7.5 |

## Opciones y recomendación, sin ejecutar

1. **Recomendada:** autorizar primero un suplemento cerrado de metadatos para
   las 17 coordenadas enumeradas, con URL/tamaño/checksums;
   entregar después el lote binario adicional cerrado para aprobación. Como
   mínimo el fatal tendría estas rutas propuestas en el mismo dominio oficial:
   - [commons-io 2.15.1 POM](https://repo.maven.apache.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.pom).
   - [commons-io 2.15.1 JAR](https://repo.maven.apache.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar).
   - Sus sidecars publicados, solo tras concretar y autorizar la lista. No se
     afirma ahora qué algoritmos ofrece esta versión ni su tamaño.
   Nada de Maven online, imports de claves o ampliación automática del lote.
   Los padres/imports nuevos, si aparecen, se listarían para otra autorización;
   no quedan autorizados implícitamente por aprobar solo esas 17 coordenadas.
   Mantener los 192 originales; el suplemento de plugins/pruebas no viaja al
   producto. Si la expansión revela otra coordenada, detener y presentar lista.
2. Entregar al usuario el inventario del faltante para que consiga/verifique
   los insumos y autorice poblar la caché aislada, sin usar su `.m2` general.
3. Detener y archivar la candidata parcial. No la sustituye la referencia.

No se recomienda cambiar Surefire, forzar una commons-io distinta, omitir Java,
usar cachés personales o llamar «regresión aprobada» a la compilación.

**Decisión necesaria:** autorización expresa del suplemento de resolución antes
de solicitar cualquiera de esos archivos. El permiso previo era exactamente
el lote cerrado y la Etapa 4 prohíbe descargas nuevas; no se reutiliza como
permiso para ampliar la lista. No se ha ejecutado nada posterior al bloqueo
salvo lectura y consolidación de evidencia/documentación.
