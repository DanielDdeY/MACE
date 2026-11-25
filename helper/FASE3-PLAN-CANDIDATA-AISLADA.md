# D — Plan de una candidata aislada, dos variantes sin preferencia

Fecha: **2026-11-25**. Solo diseño y diffs; **0 builds, 0 artefactos descargados,
0 parches aplicados al producto** en esta entrega. DLL 1.5.0 / ABI 1.5 / wire 2 /
helper 1.1.1 / política 4A y estados 9–16 conservados. No 3.2/3.3 ni instalador.
Los comandos de construcción de este documento **requieren aprobación futura**.

## Decisión aplazada antes de construir

| Variante | Contenido común | Diferencia Mono | Gate |
|---|---|---|---|
| CONSERVAR | Temurin 25; JavaFX 27.0.1.1 JAR; Mockito 5.20.0/Byte Buddy 1.17.7; metadatos propios MIT | Conserva activos y locks actuales | Elección expresa y aprobación del diff/descargas; no se recomienda como preferida ni resuelve su licencia |
| EXCLUIR | El mismo contenido común | ExcludeAssets=all, dos locks del experimento | **Bloqueada para candidata hasta resultados físicos código 0**, según decisión 8; después elección/aprobación expresa. No se recomienda |

Los cuatro códigos 2 comunicados no avalan ni refutan exclusión. Primero
diagnosticar el arnés con el diff C aprobado aparte y comprobar el experimento
ya existente. No construir una imagen para decidir después si se excluye:
esa elección se toma **antes** de la única campaña de construcción/regresión.
Si aún no puede decidirse, la candidata queda **PENDIENTE**, no se elige default.

## Diffs completos de las dos alternativas

- [CANDIDATA-CONSERVAR-MONO.patch](pendientes/CANDIDATA-CONSERVAR-MONO.patch):
  **5 archivos**, común completo para esa variante.
- [CANDIDATA-EXCLUIR-MONO.patch](pendientes/CANDIDATA-EXCLUIR-MONO.patch):
  **7 archivos**, común más exclusión y ambos locks completos como diff.

Son **alternativos**, no aplicar ambos, ni concatenarlos con los parches
históricos MIT/JDK/Mono. Contexto cero para no copiar hashes completos de locks.
`git apply --check --unidiff-zero` verifica aplicación textual, no aprobación.
El parche C de motivos es independiente del producto y no va dentro de estos
dos; si se aprueba antes, sus archivos no entran en conflicto con los de D.

| Archivo propuesto | Cambio y razón |
|---|---|
| app/pom.xml | Licencia MIT, release 25 en propiedad y compiler, JavaFX 27.0.1.1, Mockito 5.20.0 |
| app/pom.xml, tests | dependency-plugin **3.8.1**, goal properties en initialize y agente Mockito explícito en Surefire, conservando native-access; evita depender de autoattach dinámico |
| Tres csproj propios | PackageLicenseExpression MIT, Copyright 2026/Authors con alias público autorizado; versiones y TFM no cambian |
| packaging/stage-runtime.ps1 | Exige LICENSE propio y copia bytes a **third-party/MACE-LICENSE.txt**, ruta ya permitida por verify-runtime; manifiesto incluye ese archivo. Avisos de terceros siguen borrador |
| Solo EXCLUIR: csproj helper + dos locks | Referencia exacta Mono 1.0.0/ExcludeAssets=all, paso Transitive→Direct y dependencia de proyecto en lock Tests; versiones/contentHash conservados |

El agente de Mockito solo existe en los tests, nunca launcher/runtime. La
[documentación v5.20.0](https://github.com/mockito/mockito/blob/v5.20.0/mockito-core/src/main/java/org/mockito/Mockito.java),
sección 0.3, leída el 25/11, propone properties + javaagent para Java 21+.
No se añaden flags experimentales, EnableDynamicAgentLoading ni exclusiones.
Se conserva el permiso FFM. No se fuerza otra Byte Buddy mediante override:
**dependency:tree debe confirmar 1.17.7 antes de compilar**. Si difiere, detener.

XML y aplicación textual comprobables sin build. Compatibilidad efectiva,
resolución, agente y tests con Java 25 **NO VERIFICADOS**. Estos son diffs
completos de edición sobre la base; no se presentan como cambios ya validados.

## Herramientas propuestas y aislamiento

Temurin ZIP Windows x64 HotSpot **25.0.5.1+2**, release publicada 18/11/2026,
identificada por [API oficial GitHub](https://api.github.com/repos/adoptium/temurin25-binaries/releases/latest)
el 25/11. Línea 25 aprobada; **build concreta/descarga aún pendientes**.
No MSI, winget, instalación o registro. JDK actual e imagen congelada intactos.
Maven **3.9.16** ya detectado por nombre de su maven-core local; se propone
copia aislada verificada o ZIP de esa misma versión. Su rama sigue mantenida
según [Apache](https://maven.apache.org/download.cgi); no actualizar a 3.10/4
por esta tarea. Ejecución real con JDK 25 sigue NO VERIFICADA.
.NET SDK **10.0.401**, runtime **10.0.12**, paquetes/locks vigentes; no actualizar.
CMake >=3.28, MSVC x64 y mt.exe de Windows SDK ya existentes: preflight de
versión/ruta/firma antes de uso. Si faltan, detener y pedir aprobación, no instalar.

Un único directorio nuevo (propuesto, **no creado**):

```text
native/build/candidata-20261125-01/
  source/             clon local nuevo de la revisión aprobada; una variante
  tools/              Temurin ZIP extraído, Maven aislado
  deps/               m2, NuGet y cachés exclusivos
  evidence/           informes saneados, árboles y checks
```

Salidas dentro de source: helper/artifacts/<id>, native/build/candidate/dev y
prod, app/target y target/candidate-development, packaging/output/runtime/dist.
No copias de la imagen vieja ni base en v1.0.0/v1.1.0-rc3/v1.1.0. El setup
PawnIO intacto puede leerse de la referencia congelada actual como insumo
verificado, **no** de releases históricos ni ejecutarse. Todo nuevo queda
fuera de packaging/dist del checkout original. No worktree/merge/push nuevos.

No alterar JAVA_HOME ni PATH, tampoco temporalmente para seleccionar JDK.
Maven se invoca mediante **java.exe de Temurin + Classworlds**, no mvn.cmd.
jlink, jpackage y java se invocan por ruta absoluta bajo tools; no usar
package-windows.bat/run-source.ps1/verify-native.ps1 para esta selección:
los dos primeros reasignan JAVA_HOME y el último busca Java por PATH.
El bat histórico no se modifica. Se reutilizan sus verificadores y opciones
de app-image, UAC y pins mediante comandos explícitos.

## Orden de la única campaña

1. **Gates previos:** aprobar diagnóstico, cerrar observaciones físicas de Mono
   si se considera EXCLUIR, elegir variante, aprobar build JDK/diffs y lista de
   descargas. Sin esos gates no se clona, aplica, descarga ni construye.
2. Clon local nuevo, comprobar revisión/árbol y LICENSE exacto; aplicar **un**
   diff solo allí. Inventario inicial y sentinel de raíz para impedir escribir
   fuera. No copiar bin/obj/artifacts/imagen ni logs del checkout original.
   Registrar el diff aprobado en un commit **solo del clon aislado**, antes
   del build, para que la procedencia no apunte a una fuente modificada sin
   registrar. Sin merge, push ni cambios de identidad Git por el agente.
3. Extraer herramientas previamente aprobadas y verificadas en tools; preparar
   caches privadas. Resolver metadata/árbol **antes de compilación**, confirmar
   JavaFX y Mockito/Byte Buddy, sin mirrors privados ni snapshots. Congelar
   árbol/índice de artefactos y aprobación de cierre de descargas si faltaba.
4. Helper: locked restore → build/test default 105 → publish **una vez** tras
   metadatos/variante definitivos. Firma MACE omitida explícitamente; PawnIO
   conserva original. Generar/verificar ambos manifiestos con PS 5.1.
5. Compilar DLL development para ensayos y production con **manifiesto final**
   del helper. Misma fuente, ABI/tamaños intactos. La DLL de referencia no sirve
   para ese pin nuevo. Ningún hook de pruebas en producción.
6. Campaña de CTest/Java/FFM y físicas según responsabilidades abajo. Java dev
   y prod en salidas separadas por el recurso de perfil, no como builds por
   cambio. Un fallo exige detener, registrar causa y aprobar corrección; no
   omitir tests, editar aprobaciones ni prometer no repetir lo que invalide.
7. Desde las **clases production ya probadas**, crear JAR y dependencias
   runtime; staging cinco componentes + LICENSE. jlink Temurin, JavaFX JAR
   (sin JMOD), jpackage app-image **una vez**; requireAdministrator idéntico
   al manifiesto actual solo en MACE.exe; Preview con configuración vigente.
8. verify-image/test-image/test-runtime-payload y tests de imagen elevada;
   medir conteos/tamaños/hash nuevos. No reemplazar referencia ni publicar.

El objetivo “una vez” evita reconstruir por cada cambio de licencia/JDK/Mono;
no elimina los dos perfiles nativos/Java necesarios ni los contextos normal y
administrador. No garantiza cero repeticiones tras un fallo real o cambio de fuente.

## Efecto sobre bytes, manifiestos y pins

| Cambio | Qué puede cambiar | Orden obligatorio / verificación |
|---|---|---|
| Temurin 25.0.5.1+2 | runtime jlink, launchers jpackage, archivos legales JDK | SHA y procedencia ZIP antes de ejecutar; inventariar runtime nuevo. No heredar conteo/hash de JDK 22 |
| JavaFX 27.0.1.1 | JAR, DLL embebidas, CRT/licencias y UI | Árbol, classifier win, módulos alineados; hashes nuevos y revisión legal pendiente |
| Mockito/Byte Buddy/agente | Test classpath y clases de tests | No deben viajar al producto; test-runtime-payload debe rechazar presencia |
| MIT POM | POM embebido en JAR, bytes del JAR | Crear JAR una vez desde prod probado y medir |
| MIT csproj | Recursos/atributos de EXE/DLL propios del helper | Generar helper final **antes** de hash/manifiesto; versión helper sigue 1.1.1 |
| Variante Mono | Activos y deps del helper, manifiestos | Nuevo publish limpio, ausencia/presencia de tres activos según variante, 0 activos deps faltantes; licencia sigue abierta |
| Manifest helper nuevo | Hash del manifiesto compilado en DLL production | Reconfigurar CMake contra ese manifiesto, medir DLL final, nunca reutilizar pin viejo |
| LICENSE en staging | runtime-manifest y conteos del payload | Copia idéntica, no finaliza avisos de terceros |
| DLL final | Pin Java del launcher, SHA y sumas globales | Pin después de finalizar DLL; no modificarla después |
| mt.exe | Bytes del launcher MACE | Mismo UAC de origen; hash global posterior. No quitar UAC ni modificar plantilla |
| Firma futura | Modificaría bytes/orden de pins | **Fuera de esta candidata**, sin hook de firma. No refirmar terceros |

Baseline congelada: 370 archivos/147787297 bytes; publicado 205/runtime 202.
Experimento anterior sin metadatos MIT: 202/199. **Ninguna cifra se atribuye
a la candidata no construida**. Los dos casos medirán sus propias cifras.
La excepción NDD sigue limitada a ruta, hash y offsets aprobados: si cambia
el paquete, falla el gate, no ampliar la excepción para hacer pasar el build.

## Regresión y quién la ejecutaría (nada ejecutado ahora)

| Grupo | Objetivo | Futuro responsable |
|---|---|---|
| Diff/XML, dependency:tree, manifiestos/hashes, inventarios, protección, auditoría | Cero discrepancias no explicadas, perfil correcto | Agente en consola normal tras autorización de operaciones necesarias |
| Compile/publish/jlink/jpackage sin elevar | Solo raíz aislada; una variante | Agente, tras aprobar diffs/descargas/build; ninguna ejecución elevada |
| Helper default sin argumentos | **105**, normales; no hardware ni terminaciones | Agente normal si autorizado; usuario para repetir contexto administrador |
| CTest | **76 dev / 77 prod**, consola normal y administrador | **Usuario**, porque contiene terminaciones/contención de procesos de prueba |
| Java | Baseline **347 aprobadas + 1 omitida histórica**, dev/prod y normal/admin según precondiciones | **Usuario**, incluye watchdog/instancia única y procesos hijos. Sin nuevas omisiones ni flags experimentales |
| Payload | **8 escenarios y rechazo de development** | Usuario; registrar conteo real y por qué cambia si cambia |
| Protección / proveedores | PASS, **181 aceptadas / 0 inesperadas** | Agente normal; usuario en administrador para completar ese contexto |
| UI/AtlantaFX | dashboard, HUD, CSS, FXML, DPI/escala, gráficos, input, mensajes 9–16, calentamiento, frescura, cierre y Preview | Usuario visual, misma build; prueba simulada distinta de física |
| Físicas | Cold/read normal/admin, PID nuevo y recuperación, cierre, proceso launcher/JVM, suspender/reanudar | Usuario. Intel integrada y NVIDIA siguen **NO VERIFICADO** en combinación nueva |
| Imagen | UAC, pins, árbol real launcher/JVM/helper, anulaciones ignoradas, ausencia docs/tests/logs | Verificadores normales por agente; aplicación elevada y kills por usuario |

La omisión histórica corresponde al ensayo contra DLL legado externa
(NativeV2IntegrationTest, MACE_LEGACY_DLL), no a un escape de Java 25.
Faltan precondiciones de DLL/helper → aparecen otras omisiones: **rechazar**
esa corrida como regresión completa. No fabricar un binario legado para evitarla.
No se instalan/paran servicios para cumplir pruebas. Recuperación real PawnIO,
ausente→instalado, 3010 real, HVCI/equipo limpio permanecen NO VERIFICADOS y
fuera de esta tarea. Los comandos están en el anexo siguiente.

## Comprobaciones de esta entrega, sin construir

| Comprobación | Resultado |
|---|---|
| Tres parches: motivos y ambas variantes | git apply --check aprobado; ninguno aplicado al producto |
| XML reconstruido en memoria | POM y tres csproj bien formados |
| Parser Windows PowerShell 5.1 | **17 entradas, 0 errores**, incluyendo bloques de receta y propuesta de diagnóstico; 0 bloques ejecutados |
| Referencia congelada, auditor de solo lectura | **370/370 pins**, 0 extras; publicado 205/205 y runtime 202/202 sin cambios |
| Protección de telemetría / auditoría de proveedores | PASS; **181 aceptadas y 0 inesperadas** |
| Inventario jurídico histórico | 103 filas, **16 sin evidencia suficiente**; no se convierte en cierre legal de los nuevos componentes |
| Ensayos físicos / builds / paquetes nuevos | **Ninguno ejecutado**; no presentar objetivos de regresión como resultados |

## Anexos, NO VERIFICADO y decisiones

- [Comandos futuros](FASE3-CANDIDATA-COMANDOS.md): preparación, build única,
  verificadores y comandos del usuario con rutas de la candidata.
- [Descargas necesarias](FASE3-CANDIDATA-DESCARGAS.md): URL/versiones,
  verificación, ubicaciones y límites de cierre de dependencias.
- Archivo operativo NuGet propuesto: [lista de 28 entradas](pendientes/CANDIDATA-NUGET-DESCARGAS.json).

**NO VERIFICADO:** árbol efectivo Maven con nuevos números, cierre completo de
transitivas/plugins no declarados hasta resolución aprobada, integridad/firma de
artefactos no descargados, ejecución Temurin/JavaFX/AtlantaFX, build/imagen/pins
nuevos, sensores/reconexión/cierre de Mono y revisión legal. No se simulan éxitos.
No se altera THIRD-PARTY-NOTICES ni los informes históricos para ocultar carencias.

Decisiones: aprobar o ajustar los diffs y Temurin exacto; aprobar alcance de
descargas (incluida resolución de metadata para congelar cierre); aplicar o no
diagnóstico C; luego decidir variante cuando sus gates se cumplan. Selección
provisional JavaFX y DCO ya resueltos; no volver a preguntar por ellos.
