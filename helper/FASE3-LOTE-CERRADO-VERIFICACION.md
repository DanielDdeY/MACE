# Etapa 3 — Lote cerrado y resolución offline

Fecha: **2026-11-26**, America/Lima. Partida **deb4aaf**, rama
`LHM_implementado`, árbol seguido limpio. Los dos archivos locales sin
seguimiento se mantienen fuera. Punto de retorno existente **053277e, mono-in**.

## Confirmación y regla de dominios

**VERIFICADO POR EL USUARIO:** vuelve a confirmar código 0 en su equipo para
Read, Read RequireCpu, Reconnect RequireCpu y CloseEarly. La primera candidata
**EXCLUYE Mono**. No se atribuyen tokens individuales, nuevos modelos, medidas,
logs o pruebas en las laptops. No se aplicó ningún parche de producto.

La regla literal comprende **también documentación**: cualquier solicitud
fuera de la lista autorizada exige detenerse y preguntar antes. La aceptación
del aviso anterior no convalida la desviación ni autoriza repetirla.
La adenda del [informe previo](FASE3-ETAPA3-CIERRE-METADATOS.md) lista los dominios
de Etapas 2 y 3, sus motivos y los redirects históricos no acreditados.
En esta ampliación no se consultó documentación remota: se usaron archivos
locales y API del SDK existente. El descargador solo solicitó URLs permitidas
en **repo.maven.apache.org** y **api.nuget.org**, sin seguir redirects ajenos.

## Descarga y comprobaciones del punto 1

Se descargaron **exactamente 192 JAR y 28 nupkg**, **127.354.376 bytes** binarios,
con script propio; no Maven/NuGet como descargadores. Ningún artefacto adicional
ni importación de clave. Raíz nueva, ignorada por Git:
`native/build/fase3-lote-cerrado-20261126-01/`.

- Maven: se consultaron los cuatro sidecars convencionales SHA-512, SHA-256,
  SHA-1 y MD5 de cada JAR; todos los disponibles coincidieron. **22 JAR** tienen
  checksum fuerte publicado; **170** solo SHA-1/MD5, controles débiles. Los
  404 quedan en el registro privado, sin inventar un checksum o una firma.
- NuGet: **27/27 contentHash presentes en los locks coinciden**, incluidos los
  duplicados por TFM, y no hay discrepancias. El runtime pack
  **Microsoft.NETCore.App.Runtime.win-x64 10.0.12** es la entrada adicional
  aprobada del lote pero **no tiene contentHash en esos locks**: esa comparación
  no se afirma verificada. No se alteró ni añadió una entrada para ocultarlo.
- Los 28 nupkg pasaron la comprobación local de integridad del SDK. Para los
  firmados se comprueba CMS matemáticamente sin construir cadena de confianza
  y se llama a ValidateIntegrityAsync. GetContentHash obtiene el hash NuGet
  que se compara con el lock; el SHA-512 del ZIP firmado no es un sustituto.
  La confianza del certificado/editor permanece **NO VERIFICADA**.
- No se solicitaron registros NuGet o sidecars adicionales no listados. Para
  NuGet se verifican los hashes publicados en los locks y el contenido firmado;
  no se afirma haber inventariado todos los hashes de servicios externos.
- Se calculó SHA-256 local por binario y por transferencia guardada. Digests,
  contentHash completos y logs solo en índices privados ignorados; esos hashes
  locales permiten detectar cambios posteriores, no autentican por sí solos
  al editor ni acreditan licencia.

Índices públicos: [220 binarios](FASE3-LOTE-DESCARGAS.csv),
[todas las transferencias guardadas](FASE3-LOTE-TRANSFERENCIAS.csv).
Cada fila incluye URL, tamaño y método, sin hashes completos ni datos locales.
Son **657 archivos transferidos**, **127.376.792 bytes**: los 220 binarios,
428 sidecars de checksum y nueve firmas. Hubo 340 respuestas 404 de sidecars
no publicados, registradas como intentos sin archivo guardado.
Índices privados: evidence/downloads-private.json y all-transfers-private.json.
Los POM de la campaña anterior se copiaron a la caché aislada; no se tocó la
caché del usuario ni se consultó otra fuente para completar faltantes.

## Firmas del punto 2 — ninguna clave importada

Se obtuvieron **9 .asc**, solo de los ocho JavaFX **27.0.1.1** y AtlantaFX
**2.0.1** que viajarían en el producto. GnuPG **2.4.8** existente se ejecutó
con homedir aislado, --no-options, --no-auto-key-retrieve y auto-key-locate clear.
Las nueve verificaciones devolvieron **2 por falta de clave pública**.
No es un resultado de firma inválida ni una firma autenticada.

| Artefactos | Identificador declarado por la firma | Origen / autenticación |
|---|---|---|
| AtlantaFX 2.0.1 | Huella **12ED17B48803C23BE8B1DE9179DD1EB96702CC23**, key ID 79DD1EB96702CC23 | .asc junto al JAR en Central. Huella declarada, sin clave obtenida ni vínculo autenticado con el editor: **NO VERIFICADO**. |
| Ocho JavaFX 27.0.1.1 | Key ID **6A9FBE152D4C64D1**; no se obtuvo huella completa acreditada | .asc junto a cada JAR en Central. El key ID corto no acredita identidad: **NO VERIFICADO**. |

[Tabla de las nueve firmas](FASE3-FIRMAS-PRODUCTO.csv). No se accedió a
servidores de claves ni otros dominios, y no se importó ninguna clave. Antes
de hacerlo hace falta presentar y autenticar la huella completa y su fuente
con permiso para consultarla. No se sustituye esa comprobación por el nombre
del artefacto o por que la firma esté en el mismo servidor HTTPS.

## Resolución Maven offline del punto 3

Temurin **25.0.5.1+2** y Maven **3.9.16** se verificaron de nuevo contra los
checksums privados de sus ZIP oficiales ya descargados. Se compararon también
los archivos extraídos con los ZIP antes de ejecutar sus herramientas.

| Goal ejecutado, versión explícita 3.8.1 | Resultado |
|---|---|
| maven-dependency-plugin:tree, Maven -o | **Código 0** |
| maven-dependency-plugin:resolve, Maven -o | **Código 0** |

[Árbol offline saneado](FASE3-MAVEN-ARBOL-OFFLINE.tgf): **23 nodos**, incluido
el proyecto de resolución. Los logs crudos se conservan privados y no se versionan.

Sin descargas implícitas ni faltantes detectados en esos dos goals. La frase
genérica BUILD SUCCESS de Maven no implica compilación: no se invocó ciclo
de build, compile, package, test ni otro plugin. No go-offline/resolve-plugins,
que ampliarían el alcance a herramientas no necesarias para esta receta.

Se usó un **POM de resolución separado**, sin fuentes de aplicación ni fases
de build, que describe el conjunto futuro autorizado. No es una edición de
app/pom.xml y no se aplicó el parche JDK/JavaFX/MIT. Por tanto verifica este
conjunto de dependencias, **no** una candidata ya parcheada ni la ejecución del
POM de producto actual, que aún declara las versiones antiguas. El contraste
con el producto modificado corresponde a la futura Etapa 4.

JAVA_HOME y PATH se fijaron solo en el environment del proceso hijo. Settings,
user.home y repositorio m2 aislados; sin importar credenciales/settings del usuario.
El proceso padre conservó sus variables. No se ejecutó Java de MACE ni el helper.

## Tabla informativa de los 192 JAR, preparada antes del punto 4

[FASE3-JAR-REALMS.csv](FASE3-JAR-REALMS.csv) tiene **192 filas**, versión,
classifier, realms, rol aplicación/pruebas/plugin, necesidad para receta y
si viajaría al producto. **9 previstos como runtime**; los JAR de pruebas y
plugins **NO deben viajar al instalador**. Algunos JAR sirven a varios realms;
no se confunde una dependencia declarada con prueba de carga individual.

| Plugin | Receta actual de resolución | Receta futura mínima |
|---|---|---|
| dependency 3.8.1 | **Necesario y ejecutado** para tree/resolve | properties del agente de tests y copy-dependencies están en la propuesta, sin ejecutar ahora. |
| javafx-maven-plugin 0.0.8 | **No necesario**, no ejecutado; descargado porque el lote lo incluye | No necesario si Preview se lanza con Java explícito y jlink/jpackage se invocan por ruta. Podría servir al flujo javafx:run, fuera de esta receta. No se retiró del POM. |
| compiler 3.13.0 | No ejecutado ni necesario para estos dos goals | Compilación futura, pendiente de autorización. |
| surefire 3.2.5 | No ejecutado ni necesario para estos dos goals | Tests futuros; los providers/booter son herramientas, no runtime del producto. |
| resources 3.4.0 | No ejecutado ni necesario para estos dos goals | Copia/filtrado de recursos en build futura. |
| jar 3.5.0 | No ejecutado ni necesario para estos dos goals | JAR futuro; no empaquetado ahora. |

Se descargó el lote entero sin reducirlo unilateralmente. Esta tabla permite
revisar una reducción posterior, no autoriza borrar dependencias del proyecto.

## Punto 4 — restore bloqueado

**FALLO OBSERVADO: dotnet restore devolvió 1**; el script envoltorio devolvió 2
y se detuvo. No se repitió ni se descargó nada para hacer pasar la prueba.

Se restauraron copias de los tres csproj, sus locks, Directory.Build.props y
global.json **sin parches** en restore-only/helper, no una build candidata.
SDK fijado 10.0.401, runtime 10.0.12; --locked-mode, sin caché HTTP ni fuentes
remotas, únicamente el feed local de los 28 nupkg. NUGET_PACKAGES, scratch,
cachés y DOTNET_CLI_HOME aislados; certificados de desarrollo y servidores
persistentes de build deshabilitados en ese proceso. Sin compile/test/publish.
**Los tres locks permanecen byte a byte iguales** a los originales.

| Diagnóstico | Evidencia / significado |
|---|---|
| NU1101: Microsoft.WindowsDesktop.App.Runtime.win-x64 **10.0.12** | No está entre los 28 aprobados ni en el feed local. project.assets.json generado lo enumera en downloadDependencies, versión exacta [10.0.12, 10.0.12]. **No descargado**. |
| NU1101: Microsoft.AspNetCore.App.Runtime.win-x64 **10.0.12** | Misma situación: petición implícita del SDK no cubierta por el lote. **No descargado**; no se afirma que deba viajar en el producto. |
| NU1905 convertido en error | El arnés que preparé configuró el feed de paquetes local también como auditSource, pero no contiene una base de vulnerabilidades. Es un problema de preparación de esa configuración offline, **no un fallo de MACE ni evidencia de vulnerabilidades**. No desactivé NuGetAudit ni fabriqué datos para ocultarlo. |

Las dos URLs informativas siguientes se derivan de esos nombres/versiones;
**no se solicitaron ni quedan autorizadas por estar documentadas aquí**:

```text
https://api.nuget.org/v3-flatcontainer/microsoft.windowsdesktop.app.runtime.win-x64/10.0.12/microsoft.windowsdesktop.app.runtime.win-x64.10.0.12.nupkg
https://api.nuget.org/v3-flatcontainer/microsoft.aspnetcore.app.runtime.win-x64/10.0.12/microsoft.aspnetcore.app.runtime.win-x64.10.0.12.nupkg
```

El restore completo y el cierre de packs SDK quedan **NO VERIFICADOS**. Para
retomarlos hay que decidir si se investiga primero por qué el SDK pide esos
packs o se amplía de forma expresa el lote, y acordar cómo tratar la auditoría
de vulnerabilidades sin red. No se cambiaron properties o firmas para forzar
un resultado verde. El diagnóstico se limita a los archivos/logs locales.

La resolución/restauración se ejecutó con flags y feeds offline; no hubo
descargador de repositorios externo configurado. No se hizo una captura de
tráfico de todos los procesos del sistema, ni se presenta como tal.

## Comprobaciones y límites de la entrega

**14 tests offline aprobados** del descargador: lista exacta, checksums,
firmas acotadas, rechazo de dominios/URL/tipos/queries no listados y parada.
No son pruebas de producto ni sustituyen autenticación de firma. La primera
ejecución de red fue bloqueada por el aislamiento de herramientas y se repitió
fuera de él con permiso, **sin elevación UAC**. No se ocultó como fallo del lote.

DLL 1.5.0, ABI 1.5, protocolo 2, helper 1.1.1, 4A, imagen de referencia y fuentes
de producto permanecen intactos. No se instalaron herramientas/PawnIO ni se
tocaron HVCI, servicios, laptops, manifiestos, launchers o registro. Sin push,
PR, merge, contactos ni claves importadas. Mono/MIT/JDK/JavaFX siguen sin
parchear; candidata EXCLUIR es decisión confirmada, **no build realizada**.

Tras el punto 4, **DETENERSE** y esperar aprobación. No Etapa 4, 3.2 o 3.3.
