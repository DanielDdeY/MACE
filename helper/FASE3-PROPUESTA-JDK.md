# Propuesta: runtime libre antes del instalador 3.3

Fecha: **2026-11-25**, America/Lima. **Sin migración aplicada**. No se instala
JDK/JavaFX, cambia JAVA_HOME del equipo, compila ni regenera imagen.
La aprobación recibida autoriza presentar esta propuesta, no aplicarla.

## Selección propuesta y grado de comprobación

| Elemento | Propuesta / evidencia |
|---|---|
| Distribución | Eclipse Temurin, OpenJDK, HotSpot Windows x64, JDK completo para construir y runtime jlink para distribuir |
| Línea | **Java 25 LTS**, la LTS más reciente de la tabla oficial consultada; Java 27 aparece como feature no LTS |
| Mantenimiento | Adoptium lista 25 con disponibilidad al menos hasta septiembre 2031; es soporte comunitario, no SLA contratado |
| Build | La tabla lista jdk-25.0.5+8 como última y 25.0.5.1 como siguiente en noviembre; disponibilidad Windows exacta de esta última NO VERIFICADA. Elegir/revalidar patch de seguridad antes de aplicar, no usar un latest mutable como pin |
| Licencia | Temurin declara GPL v2 con excepción Classpath y textos de terceros. No se convierte en MIT por distribuirlo con MACE |
| JavaFX | Candidata **27.0.1.1**, tabla/descargas oficiales Windows x64, mínimo JDK 25. Sus JAR/JMOD y nativos deben corresponder a la misma versión y arquitectura |
| FFM | API java.lang.foreign final desde 22; 25 la incluye. No se propone JDK 21 ni --enable-preview |

Fuentes leídas el 25/11:
[soporte/LTS y calendario](https://adoptium.net/support/),
[licencia Temurin](https://adoptium.net/docs/faq/),
[JavaFX, mínimo JDK y descargas](https://gluonhq.com/products/javafx/),
[licencia OpenJFX](https://openjfx.io/),
[API FFM 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/package-summary.html).

**Límites de las fuentes:** la página JavaFX tiene texto introductorio que aún
llama 26 a la última versión, mientras su tabla y enlaces muestran 27.0.1.1.
Se usa la tabla y se registra esa inconsistencia; no se oculta. Las consultas
a API Adoptium y POM Maven de 27.0.1.1 no se pudieron abrir con la herramienta;
coordenadas Maven y build exacta descargable quedan **NO VERIFICADAS** hasta
consultarlas antes de aprobar el cambio completo. No se descargaron binarios.

Alternativas: JavaFX 25 figura como LTS, pero no se presupone que todos sus
backports sean públicos/gratuitos; la página distingue LTS contractual y
builds públicas. Se prefiere evaluar la línea GA pública compatible con JDK 25.
JavaFX 28 EA no se propone para release. No se contrata soporte ni suscripción.

## Impacto real en el repositorio

| Archivo/capa | Cambio a revisar, no aplicado |
|---|---|
| app/pom.xml | Release 25 tanto en propiedad como en configuración explícita; JavaFX 27.0.1.1; revisar las dependencias de test |
| .github/workflows/build-and-test.yml | Java 22 fijado: deberá alinearse en la migración aprobada; no se diseña ni crea aquí el CI de firma del Paso D |
| packaging/package-windows.bat | Revalidar detección JDK, jlink/jpackage 25 y documentación de JavaFX; no asumir que acepta una combinación por pasar el mínimo 22 |
| native/scripts/test-java-*.ps1 | Aceptan -JavaExe, pero tienen default JDK 22; utilizar explícitamente el JDK aprobado o proponer luego cambio de default |
| AtlantaFX 2.0.1 | Compatibilidad visual/binaria con JavaFX 27 NO VERIFICADA; no actualizarla por inferencia |
| Imagen | Nuevo runtime Java y librerías, nuevos launchers/metadatos; helper y DLL permanecen con contratos congelados |

**Riesgo de pruebas identificado:** el POM restaurado de Mockito 5.12.0 usa
Byte Buddy 1.14.15. La [tabla del proyecto Byte Buddy](https://github.com/raphw/byte-buddy#java-version-compatibility)
sitúa soporte Java 25 sin modo experimental en versiones posteriores (1.17+).
No se promete que la suite actual pase sin cambios. Antes de cerrar el diff
de migración hay que elegir una combinación Mockito/Byte Buddy compatible,
con versiones concretas y tests; no ocultarlo con flags experimentales o tests omitidos.

## jlink, jpackage e instalador

Propuesta inicial conservadora: conservar JavaFX en JAR como la imagen actual
y usar jlink para los módulos JDK. No combinar además una migración a JMOD
sin aprobación. Si después se eligen JMOD, deben ser de la misma JavaFX y no
quedar copias conflictivas en app y runtime; hoy el script elimina JAR al usar
JAVAFX_JMODS, comportamiento que requiere verificación con nueva imagen.

jlink 25 debe recalcular el cierre real de módulos, preservar legales y
revalidar compresión/opciones. No se fija que sigan siendo once módulos.
[Documentación jlink](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html).
El runtime distribuido necesita reconstrucción para recibir parches; instalar
otro JDK en el equipo no actualiza automáticamente ese runtime privado.

jpackage se usa para **app-image** y dos launchers; el EXE Inno seguirá siendo
el instalador externo elegido. No se añade MSI/WiX como requisito por cambiar
JDK. Revalidar manifiesto requireAdministrator solo de MACE, Preview normal,
FFM/native access, doble proceso launcher/JVM y política de pins.
[jpackage 25](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html).
No se cambia UAC ni se diseña una nueva política de lanzamiento.

## Diff y hashes

[JDK-JAVAFX.patch](pendientes/JDK-JAVAFX.patch) muestra el mínimo del POM,
**sin aplicar**. No es un diff completo listo para release: faltan versiones
de test verificadas, build/proveedor fijados y ajustes de scripts necesarios
según regresión. El detalle incompleto se declara antes de solicitar aprobación.

Se reconstruyen runtime, JAR, dependencias y app-image en salida nueva. Cambian
hashes de esos artefactos, manifiesto global/sumas y futuro instalador. El pin
del helper y la DLL no cambian solo por seleccionar otro JDK; sí cambian si se
combina con Mono/metadatos/firma. Siempre comparar todos los bytes y recalcular
pins del launcher sobre la DLL final. No reutilizar la aprobación de 370 archivos
para los nuevos bytes ni presumir que el conteo se conserva.

## Orden y regresión propuestos

1. Resolver build descargable, JavaFX/Maven y dependencias de test; presentar
   diff completo. MIT de componentes propios se revisa por separado.
2. Con aprobación, construir en ubicación aislada sin sustituir host/imagen
   actual; probar primero unidad/FFM y JavaFX, después integrar Mono si se aprobó.
3. Verificar helper 105, CTest 76/77, Java 347 + 1 omitida, payload 8 más rechazos,
   protección y auditoría 181/0. Documentar cifras nuevas y sus razones; no
   conservar recuentos ficticios tras actualizar herramientas de test.
4. UI real y Preview: dashboard, HUD, mensajes 9–16, calentamiento, frescura,
   cierre/instancia única/watchdog y contención sin regresión. Intel integrada
   y NVIDIA, HVCI, suspensión y escenarios con kill: usuario/entorno autorizado.
5. Imagen nueva: producción sin hooks, anulaciones ignoradas, cinco componentes,
   dependencias/manifiesto/pins, PawnIO intacto y legales. Nada de docs/tests/logs.
6. Aprobar la base resultante **antes de 3.3**, sin adelantar C/D/E ni firma real.

Todo lo anterior es plan, **NO VERIFICADO en la combinación propuesta**.

## Decisiones necesarias

1. Aceptar o ajustar Temurin 25 LTS como distribución/línea; autorizar después
   build concreta y migración mediante un diff completo separado.
2. Aceptar evaluar JavaFX 27.0.1.1 pública con JAR, sujeto a comprobar Maven,
   AtlantaFX y pruebas; alternativamente pedir estudiar otra línea mantenida.
3. Autorizar incluir la actualización mínima de dependencias de test necesaria
   en la propuesta completa, sin modos experimentales ni cambios silenciosos.
