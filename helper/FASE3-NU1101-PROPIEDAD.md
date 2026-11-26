# Punto 1 — NU1101 y DisableTransitiveFrameworkReferenceDownloads

Fecha: **2026-11-26**, America/Lima. Partida **47b6717**, rama
`LHM_implementado`; árbol seguido limpio y dos archivos locales sin seguimiento
preexistentes excluidos. Solo archivos locales y SDK **10.0.401** existente:
**ninguna solicitud remota, instalación, compilación o publicación** del arnés.

## Resultado

**La propiedad basta en este grafo:** el restore bloqueado offline pasa con
**código 0** usando DisableTransitiveFrameworkReferenceDownloads=true y
NuGetAudit=false. No se descargaron los dos packs no autorizados, no aparecen
en la caché aislada y no se cambiaron los locks o proyectos del producto.
La copia ensayada conserva los proyectos originales **con Mono**: no se aplicó
el parche de exclusión. Esto no se presenta como una candidata ya construida.

| Ensayo local | Propiedad de descarga | NuGetAudit | Resultado |
|---|---|---|---|
| Control | false | false, autorizado solo para este modo offline | **1**, únicamente NU1101; ya no NU1905 |
| Variante | true | false, misma condición | **0**, sin códigos de error |

Los dos ensayos usan el mismo SDK, fuentes/locks copiados sin editar, RID win-x64,
runtime **10.0.12**, feed local de los **28 nupkg aprobados**, fuentes remotas
eliminadas y directorios privados de caché/CLI. Los dos packs ausentes siguen
ausentes; por ello el resultado no se explica por haberlos descargado entre
el control y la variante. No se usó la caché del usuario.

## Por qué se pedían packs de frameworks no usados

Se leyó el archivo local del SDK
`Sdks/Microsoft.NET.Sdk/targets/Microsoft.NET.Sdk.FrameworkReferenceResolution.targets`:

- El comentario del target ProcessFrameworkReferences, alrededor de línea 80,
  explica la generación de PackageDownload cuando faltan packs.
- El target (línea 90) pasa **FrameworkReference y KnownFrameworkReference** al
  task, además de SelfContained, RuntimeFrameworkVersion y el RID.
- La propiedad se transmite en línea **158**. También se transmite a
  ResolveTargetingPackAssets (**446**) y ResolveRuntimePackAssets (**550**).

Se ejecutaron **solo targets de resolución**, sin Build/CoreCompile/Publish,
y se leyeron sus items. La evidencia observada en Helper es:

| Item del SDK | Propiedad false | Propiedad true |
|---|---|---|
| FrameworkReference explícita/efectiva | Solo Microsoft.NETCore.App | La misma |
| RuntimePack potencial | NETCore, WindowsDesktop, AspNetCore, versión 10.0.12 | Los mismos tres items potenciales |
| _PackageToDownload | Los tres packs | **Solo NETCore 10.0.12** |
| Archivos seleccionados por ResolveRuntimePackAssets | **187** de NETCore | **Los mismos 187** |

El grafo de paquetes de los assets no declara FrameworkReferences transitivas
a Desktop/ASP.NET. La explicación sustentada por XML e items es una preparación
preventiva del SDK para posibles frameworks transitivos, combinada con
SelfContained=true y RuntimeFrameworkVersion=10.0.12; no una referencia de MACE
a WPF, WinForms o ASP.NET. La propiedad evita **esas descargas preventivas**;
no elimina referencias explícitas ni borra los items potenciales del SDK.
No se obtuvo ni se atribuye una lectura del código C# remoto del SDK.

## Comparación de entradas y alcance sobre bytes

**VERIFICADO localmente:**

- Targets y libraries de project.assets.json idénticos entre control y variante,
  para Helper, Protocol y Tests. El cambio relevante en el plan de restore es
  downloadDependencies: **3 → 1** para Helper/Tests, **0 → 0** para Protocol.
- Tres locks byte a byte iguales, sin entradas nuevas ni contentHash alterado.
  Los seis archivos csproj/lock originales medidos permanecen iguales.
- **187/187 entradas de runtime** con igual ruta de paquete, destino, tipo y
  SHA-256; se comparan selecciones sobre el mismo almacén privado. Son archivos
  de entrada, no dos publicaciones nuevas.
- **370/370 archivos de la imagen de referencia** conservan su SHA-256 previo;
  no se modificó ningún byte de esa imagen publicada. No se editó la propiedad
  en helper/Directory.Build.props del producto.

[Tabla saneada de las 187 entradas](FASE3-RUNTIMEPACKS-COMPARACION.csv).
Los digests completos y JSON del SDK se conservan privados e ignorados bajo
`native/build/fase3-restore-propiedad-20261126-02/evidence/`.

**NO VERIFICADO:** identidad byte a byte de **una publicación futura** con y
sin la propiedad. No sería correcto confirmarla sin generar ambas salidas:
en este paso no se autorizó build/publish y no se hizo. La propiedad también
se entrega a tasks de selección de assets; la comprobación de sus 187 entradas
es evidencia acotada, no una prueba de todos los archivos finales, deps,
runtimeconfig, metadatos, firmas o hashes de la futura candidata.
Por tanto se confirma que los bytes **ya publicados** están intactos y que
las entradas de runtime seleccionadas no cambian en el grafo ensayado; no se
promete una equivalencia binaria futura que no se midió.

## Diff propuesto para la candidata — NO APLICADO

[DISABLE-TRANSITIVE-FRAMEWORK-DOWNLOADS.patch](pendientes/DISABLE-TRANSITIVE-FRAMEWORK-DOWNLOADS.patch)
añade únicamente en helper/Directory.Build.props:

```xml
<DisableTransitiveFrameworkReferenceDownloads Condition="'$(DisableTransitiveFrameworkReferenceDownloads)' == ''">true</DisableTransitiveFrameworkReferenceDownloads>
```

Se propone valor por defecto anulable para permitir diagnósticos explícitos.
**git apply --check aprobado**, sin apply efectivo. NuGetAudit=false **no**
forma parte del diff: solo se pasó en CLI a estos ensayos autorizados. Auditoría
desactivada offline **no es evidencia de ausencia de vulnerabilidades**.

Si cambian dependencias o aparecen FrameworkReferences transitivas reales,
revalidar la selección y los packs requeridos. La propuesta no evita descargar
un pack realmente necesario ni modifica el runtime 10.0.12 del helper.
No hace falta solicitar ahora los dos packs alternativos: la variante pasa sin
ellos. No se solicitaron tamaños o checksums remotos de paquetes no autorizados.

## Reproducción e incidencias conservadas

Script de laboratorio: `auditoria-licencias/investigar-runtimepacks-local.py`.
Solo crea una raíz nueva bajo native/build, copia metadatos de proyectos y
ejecuta restore/targets locales con variables de caché limitadas a sus hijos.
No llama a Maven, al producto, al helper real ni a código de sensores.
Ejemplo de repetición, desde la raíz y sin administrador, con un nombre nuevo:

```powershell
python -B .\helper\auditoria-licencias\investigar-runtimepacks-local.py --run-name fase3-restore-propiedad-repeticion-01
$LASTEXITCODE
```

El código global esperado es 0, **con control 1 y variante 0**. Falla si la
raíz ya existe; no borra ensayos anteriores. Requiere el feed aislado previo,
SDK instalado exacto y la referencia local para comprobar que sigue intacta.

Incidencias del arnés, no del producto: el ensayo inicial `...-01` escribió una
declaración XML con nombre de encoding `utf8`, rechazado por NuGet; se corrigió
a **utf-8**, conservando esos logs, y se usó `...-02`. Una primera consulta al
target de assets sin inicializar sus items devolvió cero filas; **no se aceptó
como comparación válida**. La secuencia corregida es ProcessFrameworkReferences;
ResolveTargetingPackAssets;ResolveRuntimePackAssets, que produjo las 187 filas.
No se compiló ni publicó para corregir esas consultas.

## Decisiones registradas y parada

- El usuario acepta el lote anterior y confirma que los cuatro ensayos Mono
  siguen en 0 en su equipo; la candidata **EXCLUYE Mono**. Sin atribuir nuevos
  modelos, tokens, mediciones, logs o ensayos a sus laptops.
- Aclaración explícita del usuario: los **183 JAR de pruebas/plugins NO deben
  viajar al instalador**. No se interpreta el texto anterior sin ese «no»
  como permiso para incluirlos. No se generó instalador ni se examinó en este
  punto la duplicación de clases de JavaFX; esa comprobación del punto 6 queda
  pendiente de su paso correspondiente.
- Las firmas NO VERIFICADAS no bloquean técnicamente la Etapa 4 según su
  decisión; no se importan claves ni se considera aprobada la redistribución.
- Puntos 3–5 (checksum/firma adicional del runtime pack y propuestas de
  autenticación/vulnerabilidades) **no ejecutados ni adelantados**: se respeta
  la parada al terminar el punto 1. No solicitudes remotas nuevas.

El restore ha superado la condición técnica con esta variante; **no implica
aprobación automática del diff o de Etapa 4**. Se entrega el diff pendiente y
se **DETIENE** para aprobación. Imagen, DLL/ABI/protocolo/helper/4A, launchers,
servicios, PawnIO, HVCI y laptops intactos. Sin push, PR, merge o contactos.
