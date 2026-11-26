# 3.1d — Etapa 3 PARCIAL: herramientas verificadas y parada de resolución

Fecha: **2026-11-25**, America/Lima. Partida **9ec2067** en
`LHM_implementado`. Autorizadas las descargas de la lista existente bajo
sus condiciones; **no** una resolución de dependencias con red abierta.
No se ejecutó MACE, helper ni PawnIO; no build, instalación, elevación UAC,
servicios, registro, acción en laptops o cambios de JAVA_HOME/PATH.
No se avanzó a Etapa 4, ni a 3.2/3.3.

## Resultado y punto exacto de parada

**Etapa 3 INCOMPLETA, detenida por la regla del usuario:**
«Si aparecen descargas no previstas en la lista aprobada, DETENTE y avísame».

El POM aprobado de `org.apache.maven.plugins:maven-dependency-plugin:3.8.1`
declara como padre **org.apache.maven.plugins:maven-plugins:43**, que no
figura en FASE3-CANDIDATA-DESCARGAS.md. Se detectó leyendo el POM verificado,
**antes** de solicitar el padre. No se descargó y no se intentó resolver
sus padres/transitivas. **0 descargas de componentes fuera de lista**.
No se añade automáticamente el padre a la lista ni se interpreta la aprobación
del plugin como aprobación de todo su cierre: la lista imponía ese límite.

Solicitud bloqueada, **NO realizada**:

```text
org.apache.maven.plugins:maven-plugins:43 (POM padre)
https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-plugins/43/maven-plugins-43.pom
```

## Insumos realmente obtenidos

Raíz nueva y aislada, ignorada por Git:
`native/build/fase3-etapa3-20261125-01/`.
No se copió ni modificó la imagen congelada de referencia.

| Insumo / versión | Bytes | Verificación ejecutada | Resultado / límite |
|---|---:|---|---|
| Temurin Windows x64 HotSpot JDK **25.0.5.1+2** ZIP | 141.295.462 | SHA-256 comparado con `.zip.sha256.txt` del release oficial | **COINCIDE**; extraído después de comprobar. release identifica Eclipse Adoptium y 25.0.5.1+2-LTS. No se ejecutó java.exe. |
| Apache Maven **3.9.16** ZIP | 9.395.475 | SHA-512 comparado con `.zip.sha512` oficial Apache | **COINCIDE**; extraído después de comprobar. pom.properties de maven-core confirma 3.9.16. No se ejecutó Maven. |
| maven-dependency-plugin **3.8.1**, solo POM | 17.998 | Sidecar SHA-1 publicado por Central; `.sha512` y `.sha256` devolvieron 404 | **COINCIDE CON SHA-1**, evidencia limitada: no firma ni checksum fuerte de origen. Solo se leyó XML; no se obtuvo/ejecutó su JAR. |
| Tres sidecars de los anteriores | 284 en total | Leídos como metadatos de comprobación desde sus URLs oficiales | No son firmas ni una fuente independiente del mismo canal. |

**6 transferencias guardadas / 150.709.219 bytes**: 3 insumos y 3 sidecars.
No se descargaron `.sig`/`.asc` ni claves; GPG no está disponible. Firmas
OpenPGP y confianza de claves **NO VERIFICADAS**, sin instalar herramientas.
Los checksums del mismo canal HTTPS detectan discrepancias, no sustituyen
autenticación independiente del editor. SHA-1 se registra expresamente como
limitado; no se presenta como una firma o una validación fuerte del POM.

Fuentes exactas, consultadas/descargadas el 25/11/2026:

- [ZIP Temurin fijado](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip)
  y [SHA-256 oficial](https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5.1_2.zip.sha256.txt).
- [ZIP Maven fijado](https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip)
  y [SHA-512 oficial](https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512).
- [POM del plugin](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-dependency-plugin/3.8.1/maven-dependency-plugin-3.8.1.pom)
  y [SHA-1 publicado](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-dependency-plugin/3.8.1/maven-dependency-plugin-3.8.1.pom.sha1).

Índice público saneado: [FASE3-ETAPA3-TRANSFERENCIAS.csv](FASE3-ETAPA3-TRANSFERENCIAS.csv),
con URL, archivo, tamaño, método y estado de firma. Versiones explícitas arriba.
Índices operativos privados bajo `evidence/`: `downloads-private.json` y
`transfers-private.json`, con digests completos para reproducción; **ignorados,
no incluidos en commit, no adjuntar sin sanear**. Este último indexa los seis
archivos transferidos, no los archivos extraídos como si fueran nuevas descargas.

## Caché fría, SDK y qué no se resolvió

- `deps/m2` se creó vacío: ahora tiene **1 POM + su sidecar**. No se importó
  la caché Maven del usuario ni sus settings, credenciales o extensiones.
- `deps/nuget` se creó vacío y conserva **0 archivos**. No restore, SDK packs
  ni descargas NuGet después de encontrar el bloqueo Maven.
- SDK **10.0.401** existente comprobado por presencia de su directorio junto
  al host dotnet; no se instaló/actualizó ni ejecutó. No descarga de SDK nueva.
- Extracción: **431 archivos JDK y 96 Maven**. No son conteos de candidata
  ni de imagen; no se generaron helper/manifiestos/pins ni se aplicaron diffs.
- No `dependency:tree`, effective-pom, resolución Maven/NuGet completa, cierre
  de plugins/SDK, compilación, jlink, jpackage o regresión. **NO VERIFICADO**.
  Los objetivos históricos de tests no son resultados nuevos de esta etapa.

## Scripts y comprobaciones efectuadas

`auditoria-licencias/preparar-descargas-etapa3.py`: receta del lote ejecutado;
URLs fijadas, raíz acotada, comparación antes de extracción, rechazo de rutas
ZIP que escapan y de redirects ajenos, índice privado y detección del primer
padre no aprobado. No delega transferencias a Maven/NuGet. Esta receta no es
un resolvedor completo; **no volver a ejecutarla con red para ampliar el cierre
antes de la aprobación correspondiente**. --resume sirve para reusar bytes
ya verificados tras una interrupción, no autoriza nuevas coordenadas.

`auditoria-licencias/test-descargas-etapa3.py`: **12 tests offline aprobados**,
incluyendo parser de parent, versión no autorizada, origen/redirect ajenos,
checksum correcto/incorrecto y escape ZIP. No invocan producto ni red.

```powershell
python -B .\helper\auditoria-licencias\test-descargas-etapa3.py
$LASTEXITCODE
```

Incidencias conservadas: el primer acceso de red fue bloqueado por el
aislamiento de herramientas; se repitió con permiso de salida del aislamiento,
**sin elevación Windows**. Un primer intento de POM se detuvo por 404 de su
sidecar SHA-512; se registró el algoritmo SHA-1 finalmente disponible, sin
ocultar el límite. Tres tests de temporales fallaron inicialmente por acceso
denegado del aislamiento; los mismos 12 pasaron fuera de él. No fue una prueba
en consola de administrador ni se alteraron ACL. El programa devuelve 2 ante
el gate de lista; la envoltura PowerShell de la herramienta mostró 1 al no
propagar explícitamente el código nativo. El motivo/estado guardado es
UNAPPROVED_MAVEN_PARENT, no se confunde con un ensayo físico Mono código 2.

## Decisión requerida para continuar la Etapa 3

Opción acotada: aprobar **solo el POM padre maven-plugins:43 y su checksum**;
si aparece otro componente no listado se repetirá la parada.

Para evitar aprobaciones una por una, la alternativa recomendada es autorizar
**solo lectura/descarga de POM y metadatos de resolución** desde Maven Central,
con versiones fijas y un índice completo para revisión; sin JAR adicionales,
sin ejecución de plugins ni build. El usuario aprobaría después el lote binario
cerrado. Esa ampliación **NO está autorizada ni ejecutada ahora**.

Hasta esa decisión B09 tiene ZIPs/versiones comprobados, no migración ejecutada;
B11 **sigue abierto**. Se detiene esta entrega antes de Etapa 4. La primera
candidata futura sigue CONSERVAR Mono; no se pide otra vez esa elección.
