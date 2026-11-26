# Etapa 3 — Cierre estático de metadatos para revisión del lote binario

Fecha: **2026-11-25**, America/Lima. Partida **053277e (mono-in)** en
`LHM_implementado`, árbol seguido limpio; dos archivos locales preexistentes
sin seguimiento excluidos. Etapas 1 y 2 aceptadas y cuatro ensayos Mono 0
**VERIFICADOS POR EL USUARIO**, sin extrapolar hardware/token ni pruebas nuevas.
Decisión vigente: **EXCLUIR Mono**, CONSERVAR solo como alternativa ante fallo
Intel/NVIDIA. [Registro exacto](FASE3-DECISIONES-METADATOS.md).

## Resultado y entregables completos del análisis estático

Lectura terminada. Se entrega el inventario completo **del alcance estático
definido abajo**, no una salida de Maven ni garantía de cierre dinámico.
**No ejecutar las descargas binarias del índice sin aprobación.**

| Medida | Resultado |
|---|---:|
| POM leídos y con checksum coincidente | **331** |
| POM con SHA-512 publicado | **38** |
| POM solo con SHA-1 publicado, control débil | **293** |
| Archivos del índice, POM y sidecars | **738** |
| Bytes únicos de esos archivos | **4.137.405** |
| Aristas declaradas únicas, padres/BOM/dependencias | **802** |
| Perfiles inventariados | **693** |
| Perfiles con activación por archivo no evaluada | **80** |
| URLs binarias Maven propuestas, fijas y únicas | **192** |
| NuGet del índice previo de locks, propuestos | **28** |
| JAR / NuGet descargados en esta ampliación | **0 / 0** |
| Tests offline del lector | **20 aprobados, código 0** |

Índices públicos sin hashes completos ni datos locales:

- [FASE3-MAVEN-METADATOS.csv](FASE3-MAVEN-METADATOS.csv): **738 filas**, URL,
  tamaño, método y fuerza/limitación de cada POM o checksum guardado.
- [FASE3-MAVEN-GRAFO.csv](FASE3-MAVEN-GRAFO.csv): **802 aristas** con origen,
  destino, tipo y ámbito. Incluye dependencias declaradas que luego se median;
  no presentarlo como el árbol efectivo de clases de Maven.
- [FASE3-MAVEN-PERFILES.csv](FASE3-MAVEN-PERFILES.csv): **693 perfiles** y
  decisión del lector para el contexto Windows x64/JDK 25 definido.
- [FASE3-MAVEN-CONDICIONES.csv](FASE3-MAVEN-CONDICIONES.csv): las **80**
  activaciones por archivo que no se afirman verificadas con ModelBuilder.
- [FASE3-LOTE-BINARIO-PROPUESTO.json](FASE3-LOTE-BINARIO-PROPUESTO.json):
  **192 Maven + 28 NuGet = 220 propuestas** con versión y URL exactas,
  descargado=false y aprobación pendiente. No incluye los ZIP de herramientas
  ya comprobados en la entrega anterior como si fueran nuevas descargas.

Raíz de trabajo exclusiva e ignorada:
`native/build/fase3-etapa3-metadata-20261125-01/`. Los POM originales, respuestas,
índices con digests y salidas operativas quedan allí, **fuera de commits**.
No se usó la caché de usuario ni se reutilizaron sus settings/credenciales.
Las relecturas reutilizaron solo la caché de esta campaña, incluyendo respuestas
404 de sidecars de versiones fijas. No se modificaron JAVA_HOME/PATH.

## Alcance de resolución y versiones que se propone congelar

Aplicación/pruebas: ocho JAR JavaFX (base, graphics, controls y fxml, sin
classifier y win) **27.0.1.1**, AtlantaFX **2.0.1**, Jupiter **5.10.2**,
Mockito **5.20.0**, Byte Buddy/agente **1.17.7** y transitivas seleccionadas.
Herencia, importaciones BOM, propiedades, exclusiones, scopes y mediación
nearest-first se analizaron en Python con fixtures; no se ejecutó Java/Maven.
No quedaron versiones dinámicas/rangos sin resolver en los ámbitos seleccionados.

Plugins explícitos: compiler **3.13.0**, surefire **3.2.5**, dependency
**3.8.1** y javafx-maven-plugin **0.0.8**. Se incluyó el padre antes bloqueado
**maven-plugins:43**, maven-parent:43, apache:33 y sus importaciones.

Plugins implícitos de la receta test/package/jar: resources **3.4.0** y jar
**3.5.0**, leídos de `META-INF/plexus/default-bindings.xml` del maven-core
**3.9.16** ya descargado y verificado, sin ejecutarlo. Los plugins explícitos
del proyecto sustituyen los defaults de compiler/surefire, no se actualizaron.
Una futura receta debe invocar los goals con coordenadas/versiones completas
para evitar búsquedas abiertas de prefijo/versión. No se editó la receta ahora.

Se incluyen POM del proveedor JUnit Platform y booter de Surefire **3.2.5**,
y launcher **1.10.2** alineado con Jupiter 5.10.2 como propuesta condicional.
El grafo del proveedor también declara launcher 1.9.3: ambos están identificados,
no se confunden con una modificación de la versión JUnit de la aplicación.

El lote es **conservador** por ámbitos de plugin/pruebas: algunos JAR pueden
estar ya aportados por el Maven instalado o no cargarse en los goals elegidos.
No significa que los 192 deban viajar en el instalador. No incluye clean,
install, deploy, site, publicación, SDK JavaFX/JMOD ni plugins no usados por
la receta. Añadir goals o perfiles exige revisar el alcance y el índice.

Los **28 NuGet** se trasladan como propuesta del índice previo basado en locks,
sin requests a nuget.org ni restore. Excluir activos Mono del payload **no
elimina Mono 1.0.0 del grafo de restore**: sigue en esa propuesta. Comprobar
la ausencia de las tres DLL será una prueba de la futura candidata, no de
esta lectura. Cierre real de packs SDK/restore **NO VERIFICADO**.

## Perfiles, límites y NO VERIFICADO

El lector no es Maven ModelBuilder. De los 80 perfiles por archivo, **78** no
añaden dependencias directas ni dependencyManagement del proyecto: configuran
principalmente compilación/documentación de upstream; no se ejecutan sus plugins.
Los otros dos casos identificados son:

- `io.airlift:airbase:112`, build-airlift: depende del marcador `.build-airlift`
  y añade dos variantes tar.gz de launcher. No se descargaron ni se incluyen en
  el lote; el escenario previsto carece de ese marcador. No crear ese archivo
  en la candidata/caché sin revisar el grafo. La evaluación por Maven es NV.
- `com.google.guava:guava:25.1-android`, srczip: dependencia **system y optional**
  de src.zip del JDK, no transitiva en este análisis. No solicitud de ZIP.

No se evaluaron archivos de un checkout upstream ni se fingió que existían.
Los cambios del entorno, perfiles explícitos adicionales y decisiones dinámicas
de Surefire/plugins siguen **NO VERIFICADOS**. En una etapa autorizada habrá
que contrastar con Maven offline/dependency:tree y el contexto exacto; si pide
otro artefacto, detener y aprobarlo aparte, sin ampliar este lote implícitamente.
**No se afirma cierre dinámico exhaustivo ni build validada**.

## Integridad, segunda fuente y GnuPG

Cada POM se comparó con todos sus sidecars disponibles (SHA-512/SHA-256/SHA-1);
los 404 se registraron sin sustituirlos por firmas inventadas. Todos los
checksums disponibles coincidieron. Los checksums descargados no tienen una
verificación recursiva infinita: son metadatos HTTPS del mismo canal, con
digest local operativo. SHA-1 permanece marcado **WEAK_SHA1** en el índice.
No hay firma verificada ni autenticación independiente de esos 331 POM.

Contrastes locales posibles: versiones explícitas del proyecto/propuestas y
bindings del Maven verificado frente a los POM y raíces elegidas. Eso comprueba
coherencia de versiones; **no** es una segunda autenticación criptográfica del
editor. No se consultó otro repositorio remoto de POM/checksums como si fuera
independiente. Las fuentes SCM indicadas en los POM no se abrieron: el permiso
de metadatos exige Central. Ese cotejo independiente permanece NV donde no
hay evidencia local, no se presenta como cumplido mediante dos URLs del mismo canal.

Se encontró y ejecutó `usr/bin/gpg.exe` de Git for Windows: **GnuPG 2.4.8,
código 0**, con --no-options, homedir aislado y sin recuperación automática de
claves. No instalación, llavero de usuario ni red. Primer intento dentro del
aislamiento falló al inicializar DLL; fuera de ese aislamiento funcionó, sin
elevación UAC. Corrige la conclusión anterior limitada a buscar GPG en PATH.

**No se verificaron firmas de binarios**: no existen sus .asc/.sig locales ni
se autoriza solicitar esos tipos con la regla actual. Para el siguiente lote,
aprobar aparte firmas y fuentes de claves autenticadas si se quieren verificar
antes de usar JAR/NuGet/ZIP. No se importó clave ni se aceptó una firma sin
identidad acreditada. Tampoco se usa una firma de repositorio como permiso legal.

## Desviación de alcance registrada

Al preparar el lector, el agente abrió documentación técnica en
[dependencias Maven](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html),
[perfiles](https://maven.apache.org/guides/introduction/introduction-to-profiles.html)
y [Model Builder 3.9.16](https://maven.apache.org/ref/3.9.16/maven-model-builder/),
y posteriormente [bindings](https://maven.apache.org/ref/3.9.16/maven-core/default-bindings.html)
y [Surefire/JUnit](https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit-platform.html).
Consultas del **25/11/2026**. **Excedió el límite literal de dominio** y no se
respetó la parada inmediata en ese punto; fue un error del agente, no una
autorización del usuario. No se recibieron binarios en esas consultas.
Se deja constancia y no se realizan más solicitudes de red en esta entrega.
El registro del lector sí contiene exclusivamente `repo.maven.apache.org`
y solicitudes de POM/checksums, sin requests de JAR/NuGet u otros tipos.

## Reproducción local y parada

### Adenda 2026-11-26: dominios de las Etapas 2 y 3

El usuario acepta el cierre y reitera la regla **literal**, también para leer
documentación: antes de cualquier solicitud fuera de los dominios/tipos
autorizados, detenerse y preguntar. La aceptación del aviso no autoriza nuevas
consultas de documentación ni convierte la desviación anterior en cumplimiento.
Se confirma nuevamente su declaración: los cuatro ensayos Mono dieron 0 en
su equipo y la primera candidata **EXCLUYE Mono**; sin nuevos datos de hardware,
token individual o acciones autorizadas en las laptops.

La tabla reconstruye los destinos identificables en las llamadas de esta
conversación, incluidas consultas fallidas. No es una captura de tráfico DNS/TLS
ni permite acreditar todos los saltos internos de los proveedores de herramientas.

| Dominio solicitado | Etapa | Motivo / alcance observado |
|---|---|---|
| www.nuget.org | 2 | Ficha histórica Mono 1.0.0; lectura pública, sin nupkg nuevo. |
| github.com | 2 y 3 | Fuentes/tags/licencias de Mono, PawnIO, LHM, JavaFX; en 3, URL de descarga del ZIP Temurin y sidecar autorizados. |
| api.github.com | 2 | Árboles, tags y contenido de repositorios; metadatos del release Temurin fijado. |
| raw.githubusercontent.com | 2 | Textos/fuentes oficiales de repositorios; algunos paths devolvieron 404/no accesibles. |
| softwarefreedom.org | 2 | Servicios, portada pro bono y tentativa de página about. |
| fsfe.org | 2 | FAQ de orientación; tentativa adicional de página de preguntas. |
| sfconservancy.org | 2 | Servicios para miembros, admisión y costo publicado. |
| learn.microsoft.com | 2 | Condiciones de redistribución VC y despliegue UCRT. |
| visualstudio.microsoft.com | 2 | Directorio público de términos del runtime; no aceptación de EULA. |
| docs.nvidia.com | 2 | Referencia NVML y tentativa de notices. |
| www.nvidia.com | 2 | Acuerdo público del driver; no aceptación ni instalación. |
| git.musl-libc.org | 2 | Tentativa de leer COPYRIGHT; el acceso falló/no aportó evidencia utilizable. |
| maven.apache.org | 2 y 3 | En 2, página de versiones/descarga Maven. En 3, cinco guías técnicas: desviación de dominio ya registrada, sin parada inmediata. |
| dlcdn.apache.org | 3 | ZIP exacto Maven 3.9.16 aprobado. |
| downloads.apache.org | 3 | Sidecar SHA-512 del ZIP Maven aprobado. |
| repo.maven.apache.org | 2 y 3 | En 2, tentativa de consultar el POM JavaFX fijado; en 3, POM/checksums del plugin y el cierre de metadatos autorizado. |
| api.nuget.org | 3, ampliación del 26/11 | Lote cerrado de 28 nupkg autorizado posteriormente; no pertenecía a las consultas de metadatos del 25/11. |

Los dominios `go.microsoft.com` y `pawnio.eu` aparecen como referencias en
documentos leídos; no consta una solicitud nueva a ellos en estas dos etapas.
El descargador inicial permitió redirects de GitHub a
`release-assets.githubusercontent.com` u `objects.githubusercontent.com`, pero
no guardó el host final: **NO VERIFICADO cuál se usó**, no se inventa un acceso
confirmado a ambos. El enlace embebido de Office en la página de Visual Studio
no se abrió; no se afirma haber leído ese documento.
La lista no amplía el permiso actual. No se realizaron contactos ni envíos a
los autores/servicios; las consultas públicas son distintas de esos contactos.

Pruebas sin red ni Maven:

```powershell
python -B .\helper\auditoria-licencias\test-metadatos-maven.py
$LASTEXITCODE
python -B .\helper\auditoria-licencias\exportar-cierre-maven.py
$LASTEXITCODE
```

El segundo comando necesita los índices privados locales de esta campaña;
exporta solo los campos saneados. El lector de red es
`cerrar-metadatos-maven.py`; **no es necesario volver a ejecutarlo** para
revisar el lote, y no autoriza nuevos tipos, dominios o binarios.

Se actualizaron los dos borradores PawnIO/LHM, sin enviar. No se reauditaron
licencias históricas reemplazadas. La revisión se retoma con payload real y
foco en PawnIO, IGCL, CRT/VC/UCRT nuevos e Inno, sin dictamen legal ahora.

**DETENIDO:** el usuario revisa el lote de 220 propuestas y sus límites antes
de autorizar cualquier JAR/NuGet o Etapa 4. No aplicar todavía el parche Mono,
crear build candidata ni avanzar a 3.2/3.3. Ninguna acción en las laptops.
