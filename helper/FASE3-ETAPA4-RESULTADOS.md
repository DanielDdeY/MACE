# Etapa 4 — candidata aislada: resultados parciales y parada offline

Fecha: **2026-11-26**, America/Lima. **DETENIDA por dependencia faltante**.
No se ha generado la app-image candidata ni se declara terminada la Etapa 4.
No se avanzó a 3.2 ni a 3.3. Este informe no sustituye una revisión legal.

## 1. Decisiones y aislamiento

- El usuario confirma cuatro ensayos físicos Mono con código 0 en su equipo y
  selecciona **EXCLUIR Mono**. Esa evidencia anterior no se atribuye a esta build.
- Punto de partida: **8de433b**, rama `LHM_implementado`, sin cambios tracked.
  Había un informe ajeno a esta tarea y un TXT Mono sin seguimiento; no se añaden.
- Fuente candidata: `native/build/candidata-20261126-02/source`, rama local
  `candidate/etapa4-excluir-mono`, commit **0352f58**. Árbol de fuente limpio al
  cierre. No comparte outputs con la referencia ni sustituye el checkout actual.
- En esa copia se aplicaron, en orden: `MONO-EXPERIMENTO-COMPLETO.patch`,
  `CANDIDATA-CONSERVAR-MONO.patch` y
  `DISABLE-TRANSITIVE-FRAMEWORK-DOWNLOADS.patch`. El segundo aporta los cambios
  **comunes** MIT/Java; su nombre no revierte la exclusión aplicada por el primero.
- Diff candidato: **8 archivos, 58 inserciones y 12 eliminaciones**: POM,
  Directory.Build.props, tres csproj, dos locks y stage-runtime.ps1.
  Sin ediciones a fuentes nativas, manifiestos UAC, launchers, ABI, wire o 4A.
- Temurin **25.0.5.1+2**, Maven **3.9.16**, JavaFX **27.0.1.1**, Mockito
  **5.20.0**, Byte Buddy **1.17.7**. ZIP y archivos extraídos de las herramientas
  se cotejaron con los insumos ya verificados. No se cambia el JDK instalado.
- Caches aisladas y feeds locales; Maven `-o`, restore `--locked-mode`.
  `NuGetAudit=false` solo en CLI de restore. Ninguna descarga nueva ni solicitud
  remota iniciada por los scripts de esta entrega; no se realizó captura global
  del tráfico del sistema operativo. No se importan claves ni se contacta a terceros.
- JAVA_HOME/PATH se pasan, cuando hace falta, solo mediante el entorno del hijo.
  Sin cambios a variables de usuario/máquina, servicios, PawnIO o HVCI.
- El primer clone aislado `candidata-20261126-01` falló por restricciones del
  entorno y quedó preservado. La campaña válida usa exclusivamente `-02`.

## 2. Gates, compilación y publicaciones realmente ejecutadas

| Comprobación | Resultado y alcance |
|---|---|
| dependency:tree sobre el POM de aplicación ya parcheado | **0**, offline; grafo de aplicación/pruebas con las versiones seleccionadas |
| Restore Tests y referencias, locked-mode | **0**, feed local, sin packs Desktop/AspNetCore nuevos |
| Java development: recursos y test-compile | **0**; 49 fuentes de prueba compiladas, sin ejecutar JUnit |
| Helper Tests Release | **1 compilación satisfactoria**; hubo un intento previo bloqueado por sandbox, preservado |
| DLL dev y producción | Compiladas con CMake de Visual Studio 2026, MSVC x64 instalado; versiones PE **1.5.0.0** |
| Helper publicado | Versión PE **1.1.1.0**, protocolo 2; publicación self-contained, sin ejecutarlo físicamente |
| Propiedad false frente a true | **2 publishes --no-build --no-restore** tras la misma compilación; **204 archivos por directorio, 0 diferencias de nombres o SHA-256**, incluidos ambos manifiestos y símbolos |

Lectura estática adicional: el pin compilado en la DLL de producción coincide
con el SHA-256 real del manifiesto helper seleccionado. RAMSPDToolkit-NDD
publicado conserva el hash aprobado y exactamente los **2 símbolos** de
compatibilidad en sus offsets aprobados. Esto acredita ese archivo del publish,
no una nueva imagen ni el resultado aún pendiente de audit-retired-providers.

Los outputs son `helper-publish-false` y `helper-publish-true` bajo la raíz
aislada. `true` es el seleccionado para una futura imagen, todavía no creada.
El publish con propiedad false usa el mismo restore ya aprobado y no solicita
los packs especulativos. La igualdad prueba los bytes publicados en esta campaña;
no prueba que un restore nuevo con false pueda pasar sin dichos packs.

| Conteo | Referencia anterior | Candidata medida |
|---|---:|---:|
| Entradas artifact-manifest del helper | 205 | **202** |
| Archivos en directorio publicado, incluidos los dos manifiestos | 207 por convención anterior; no recertificado aquí | **204** |
| Entradas helper-runtime.manifest | 202 | **199** |
| Imagen app completa | 370 | **NO VERIFICADO: no existe aún** |

Las tres entradas publicadas fuera del manifiesto runtime son `createdump.exe`
y los dos PDB propios; no son archivos destinados al runtime de la app.
**0 binarios Mono en ambas publicaciones**: `Mono.Posix.NETStandard.dll`,
`MonoPosixHelper.dll` y `libMonoPosixHelper.dll` ausentes. El output de **Tests**
sí contiene esos tres como dependencia del arnés: no confundirlo con el publish
seleccionado, ni copiarlo al producto. La ausencia en una imagen final sigue NV.

## 3. Regresión: resultados, no objetivos

| Suite | Objetivo histórico | Resultado actual | Consola y límites |
|---|---:|---|---|
| Helper | 105 | **105/105** | Normal, checks por defecto; sin argumentos de helper real |
| CTest desarrollo | 76 | **76/76** | Normal; fixtures externos auditados |
| CTest producción | 77 | **77/77** | Normal; incluye rechazo de anulaciones en la DLL de producción real |
| Java | 347 + 1 omitida | **0 casos ejecutados; Maven salió 1 antes de JUnit** | Fallo de resolución offline del realm Surefire, no una aserción de aplicación |
| Payload | 8 + rechazos desarrollo | **NO VERIFICADO / NO EJECUTADO** | Se detiene antes de continuar después del faltante |
| check-protected-telemetry | Aprobado histórico | **NO VERIFICADO en esta campaña** | No reutilizar el aprobado anterior |
| audit-retired-providers | 181 aceptadas / 0 inesperadas | **NO VERIFICADO en esta campaña** | No se reporta el objetivo como medición |
| verify-image / test-image | Aprobados históricos | **NO VERIFICADO / NO EJECUTADO** | La candidata no tiene imagen |
| Todas las suites en administrador | Varias evidencias históricas | **NO VERIFICADO para esta candidata** | Las ejecuta el usuario |

Los checks CPU/telemetría que leen la DLL no acreditan temperatura física válida
ni nuevos Intel/NVIDIA. No se lanzó el helper real, MACE ni Preview.
La API FFM y las clases Java compilaron; **no** se acredita compatibilidad visual
JavaFX/AtlantaFX ni ejecución de Java 25 mediante esa compilación sola.

Incidencias preservadas:

1. CMake del PATH era el de MinGW y no reconocía VS 2026. Se seleccionó por ruta
   absoluta el CMake ya instalado con Visual Studio; no se instaló nada.
2. Primer generador de auditoría incluyó un target UTILITY. Se corrigió solo
   el script externo para excluirlo. MSBuild y tuberías fueron bloqueados por
   sandbox en intentos iniciales; se repitieron fuera de sandbox, **sin UAC**.
3. Primer `lhm_retry_retry-loop` falló por un **defecto introducido en nuestra
   instrumentación**, no por un defecto demostrado de MACE: el fake exigía el
   marcador bajo TEMP, pero el entorno seguro del transporte normaliza TEMP a
   una ubicación del sistema de usuario. El fake salía antes de incrementar el
   contador. Se usó un puntero de diario local exclusivo del fixture; no se
   relajó la lista de entorno seguro ni el protocolo. Fixtures recompilados;
   todos los casos se acreditaron con sus bytes finales, incluidos seis casos
   de política repetidos por esa razón. La DLL de producto queda sin inyección.
4. El primer generador de manifiesto PS 5.1 no encontraba Get-FileHash en el
   entorno del hijo. Un wrapper importa el módulo Utility incluido con Windows;
   los scripts de manifiesto de producto conservan sus bytes. Publicación ya
   hecha se reutilizó; no se recompiló el helper para corregir esta preparación.
5. Surefire falla offline según el apartado siguiente. No se fuerzan versiones,
   no se usa el caché del usuario ni se continúa con una regresión parcial.

## 4. Parada obligatoria: cierre Maven incompleto

**Faltante fatal observado:** `commons-io:commons-io:jar:2.15.1`.
`surefire-3.2.5.pom` local declara `<commonsIoVersion>2.15.1</commonsIoVersion>`
en línea 94. Ese JAR y su POM no están en la caché aislada ni son parte del lote
cerrado de 192 JAR. Las versiones 2.6, 2.11.0, 2.16.1 y 2.21.0 descargadas no
autorizan sustitución ni demuestran compatibilidad del realm de Surefire.

La resolución/tree anterior no acreditó el realm ejecutable completo del plugin.
Hay además **17 coordenadas POM con advertencias**; se enumeran en
[FASE3-ETAPA4-BLOQUEO-MAVEN.md](FASE3-ETAPA4-BLOQUEO-MAVEN.md).
No se afirma que todas sean fatales, ni que bajar un único JAR cerraría el grafo.
El JUnit provider dinámico y fases de empaquetado todavía podrían descubrir más
faltantes. No se ejecutan para buscarlos sin autorización posterior.

**No se descarga, construye la imagen ni ejecuta ninguna suite adicional tras
el faltante.** Solo se consolidan documentos y consultas locales de evidencia.
Se necesita decisión del usuario sobre una ampliación **cerrada** de metadatos
y binarios verificados; no un permiso a Maven para resolver con red.

## 5. Autorización de procesos y evidencia

Se aplicó la autorización exclusiva a simulaciones CTest/Java, token normal.
Antes de cada lote/caso se comprobó por lectura que no hubiera MACE, Preview
ni Mace.Lhm.Helper activos. Nunca selección de objetivos de terminación por nombre.
No hubo elevación ni terminación desde el runner de supervisión.

- Diario: PID + fecha real de creación de Windows, padre y evento. En fixtures
  se registra CreateProcess antes de reanudar al hijo suspendido; en Java se
  prepararon wrappers de ProcessBuilder y una guarda de identidad antes de
  destroyForcibly. **Los casos Java no llegaron a ejecutarse.**
- Instrumentación externa por CMake /FI solo para fixtures, fake y ejecutables
  de tests. Se comprobó su ausencia en el vcxproj de `mace_native` de producción.
  Un overlay de test_lhm_security elimina la selección del hijo por nombre;
  mantiene parentesco y verifica PID/creación contra el registro antes de cerrar.
- Overlays externos Java: cuatro clases de prueba, mismas aserciones; selección
  del helper por descendencia y registro, no por nombre. Agente de diario solo
  de pruebas, nunca destinado al producto. Fuente Git candidata limpia.
- **611 identidades distintas** registradas, contando orquestadores, simulados,
  reintentos y procesos de intentos fallidos. **0 supervivientes** al finalizar
  cada caso y en la consulta final de PID/creación; **0 identidades sin resolver**
  en la comprobación CIM final. No equivale a 611 procesos terminados a la fuerza.
- Las cuatro peticiones explícitas de TerminateProcess correspondieron a
  `lhm_job_abrupt-parent` y `lhm_security_relaunch`, una por perfil. Job Object,
  EOF y cierre de transporte también cierran descendientes según las aserciones;
  el diario distingue petición explícita de salida observada, sin atribuir un
  mecanismo individual que no registró.
- Índice detallado por caso, PID, creación, eventos y salida:
  `native/build/candidata-20261126-02/evidence/procesos-saneado.csv`, **no versionado**.
  No contiene rutas, usuarios, equipo, SID ni hashes. Journals/digests/logs
  privados permanecen ignorados; no se incluyen en commits.
- Al reconsultar PID históricos, OpenProcess puede denegar un PID reutilizado
  por un proceso ajeno protegido. No se interpreta como superviviente: se coteja
  por CIM la fecha de creación, sin actuar sobre el proceso. Todos resueltos.

## 6. Puntos 3, 4 y 5 previos

[FASE3-FIRMAS-VULNERABILIDADES-PROPUESTA.md](FASE3-FIRMAS-VULNERABILIDADES-PROPUESTA.md)
recoge la verificación local adicional del runtime pack y las dos propuestas:

- NETCore 10.0.12: integridad del paquete y matemática CMS aprobadas; 188 archivos
  coincidentes con el runtime instalado, 0 distintos, 1 sin
  contraparte. Cadena/revocación offline y checksum independiente: **NO VERIFICADO**.
- Claves AtlantaFX/JavaFX: autenticación **solo propuesta**, sin importar claves
  ni visitar los dominios propuestos. La confianza del editor sigue NV.
- Vulnerabilidades de NuGet y 9 JAR runtime: **solo propuesta**, no consultada.
  Desactivar NuGetAudit offline no acredita ausencia de vulnerabilidades.

## 7. Referencia, artefactos y límites

La referencia conserva **370 archivos, 147 787 297 bytes y todos los SHA-256**
de la instantánea inicial. El resultado completo queda en evidencia privada.
No hay reemplazo de `packaging/dist/MACE` original, candidato publicado o release.
No se ejecutó PawnIO ni se tocaron servicios, HVCI, 3010 o las laptops.

**NO VERIFICADO**: imagen nueva, conteos finales/pins de imagen, selección de
los 9 JAR y exclusión de los otros 183, duplicación JavaFX en la imagen, UAC
efectivo de los launchers nuevos, hashes de imagen, payload/rechazos, auditoría
y protección de esta campaña, Java/JUnit, UI/HUD/AtlantaFX, privilegios mixtos,
CPU/GPU física de la nueva candidata, Intel/NVIDIA, suspensión/HVCI, instalación
limpia, parada real/ausente→instalado de PawnIO y 3010 real. Firma MACE,
autenticación PGP, vulnerabilidades y revisión legal siguen pendientes.

Los tres scripts de imagen/empaquetado nuevos son **receta NO EJECUTADA** y
exigen los gates anteriores. El wrapper de empaquetado propone validación
WinVerifyTrust con CACHE_ONLY_URL_RETRIEVAL, sin acceso a URLs; tampoco se
ejecutó. No constituye validación Authenticode nueva del setup de PawnIO.
Se verificó sintaxis por AST/Parser de **12 scripts Python y 3 PowerShell**,
sin errores de sintaxis; esto no sustituye la ejecución de la receta pendiente.

## 8. Archivos de esta entrega y continuación

Commit del arnés y recetas: **b138489** (15 scripts, sin producto ni evidencia
privada). La documentación se guarda aparte; el commit de fuente candidata
sigue siendo **0352f58** y no se reescribe ningún historial.

| Archivo bajo helper/ | Finalidad |
|---|---|
| FASE3-ETAPA4-RESULTADOS.md | Este informe y límites |
| FASE3-ETAPA4-BLOQUEO-MAVEN.md | Faltante fatal, advertencias y autorización pendiente |
| FASE3-CANDIDATA-VALIDACION-USUARIO.md | Comandos por token y estado de disponibilidad |
| FASE3-FIRMAS-VULNERABILIDADES-PROPUESTA.md | Puntos 3–5, sin solicitudes remotas |
| auditoria-licencias/preparar-candidata-etapa4.py | Clone aislado, parches y gates offline ejecutados |
| auditoria-licencias/publicar-helper-candidata.py | Compilación/publicaciones y comparación |
| auditoria-licencias/invocar-manifiesto-ps51.ps1 | Import Utility y scripts de manifiesto existentes |
| auditoria-licencias/verificar-runtimepack-local.ps1 | Firma/integridad local del nupkg |
| auditoria-licencias/candidata_contexto.py | Contexto local, herramientas, entorno hijo y preflight |
| auditoria-licencias/compilar-nativo-candidata.py | Dev/prod con VS instalado |
| auditoria-licencias/preparar-auditoria-procesos-candidata.py | Inyección externa solo de fixtures |
| auditoria-licencias/preparar-java-audit-candidata.py | Overlays/agente de pruebas; compilados, casos no ejecutados |
| auditoria-licencias/procesos_candidata.py | PID/creación, diario y consulta, sin terminación |
| auditoria-licencias/lanzador-test-candidata.py | Launcher CTest con registro del hijo |
| auditoria-licencias/ejecutar-regresion-candidata.py | Helper/CTest ejecutados; intento Java detenido |
| auditoria-licencias/cerrar-evidencia-candidata.py | Consolidación local y CSV saneado, sin repetir pruebas |
| auditoria-licencias/construir-imagen-candidata.py | **NO EJECUTADO**, bloqueado por gate Java |
| auditoria-licencias/empaquetado-offline-candidata.ps1 | **NO EJECUTADO**, adaptador offline de staging/payload |
| auditoria-licencias/verificar-imagen-candidata.py | **NO EJECUTADO**, checks finales/payload/inventario |

Se espera autorización para investigar/completar un lote cerrado de resolución;
no se solicita permiso para red implícita. Tras resolverlo, reanudar desde Java
en la misma candidata: no repetir helper, publicaciones ni suites CTest sin
cambios o nueva causa. Entonces crear una sola imagen, medirla y entregar sus
comandos físicos/UI. **Detenida; no avanzar de fase.**
