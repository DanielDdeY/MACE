# Fase 2 — H-5.0-JAVA: carga de producción cerrada

Fecha: 2026-11-17. Commit de partida confirmado:
`b305d3f10cc4b0b09ef5e233d871040d4ddaa556`, rama `LHM_implementado`.
Al empezar solo estaban sin seguimiento `diagnostico-admin.txt`,
`diagnostico-normal.txt` y `prueba-cpu.txt`. Por autorización del usuario se
añadieron sus tres entradas exactas a `.git/info/exclude`, sin modificar
`.gitignore`, los TXT ni incluirlos en el historial.

Alcance: endurecimiento previo al 5.1, con pruebas y un commit propio.
No se regenera `packaging/dist/MACE`, no se adelanta 5.2/5.3 ni Fase 3.
La evidencia del inventario de escrituras se guarda ahora para incorporarla
al 5.3; no se corrige persistencia.

## Política y selección de perfiles

| Artefacto | Selección | Política de carga |
|---|---|---|
| Producción | Maven por defecto; metadato filtrado `profile=production` dentro del JAR | Solo propiedades `mace.native.lib` y `mace.native.sha256`. Ruta absoluta a un archivo y SHA-256 de exactamente 64 dígitos hexadecimales obligatorios. Sin búsquedas alternativas ni lecturas de MACE_* |
| Desarrollo desde fuentes | Maven `-Pdevelopment`; el script compila en `app/target/source-development` | Conserva ruta/hash por propiedades, fallback a MACE_NATIVE_LIB/MACE_NATIVE_SHA256 y búsqueda anterior. Helper Development conserva su política nativa previa |
| Pruebas Java | Recurso `profile=development` solo en `src/test/resources`, primero en classpath de Surefire | Mantiene las 334 pruebas existentes y sus fixtures. El recurso de test no entra en el JAR de aplicación |
| JVM hijas de las pruebas de producción | Copia de clases principales, metadato de producción y probe; se excluyen classpaths originales de clases/test-classes | Comprueba política real de producción sin que el recurso de test active desarrollo; también prueba metadato ausente/desconocido |

`NativeLoadProfile` lee exclusivamente el recurso del artefacto, una vez.
No lee flags de perfil del entorno ni de System properties. Metadato ausente,
ilegible, malformado o desconocido nunca activa desarrollo: aplica producción.
Ni `MACE_NATIVE_PROFILE`, `MACE_DEVELOPMENT` ni `-Dmace.native.profile`,
`-Dmace.native.development` o `-Dmace.build.nativeProfile` en la JVM cambian esa
decisión. La propiedad Maven de compilación es entrada del constructor, no un
selector de ejecución.

La política vale para las tres sobrecargas públicas de `NativeFunctions.load`.
Una ruta/hash pasados a una sobrecarga tampoco pueden eludir los pins de
producción. El hash es obligatorio y se comprueba antes de `libraryLookup`.
Los errores de carga se convierten en el mensaje fijo, sin conservar causa con
rutas/hashes. `isAvailable()` devuelve false ante rechazo de configuración.
`InfrastructureAdapters.AUTO` en producción tampoco cae silenciosamente a mocks.

En la carga de producción, las únicas lecturas Java de las dos variables
nativas quedaron detrás del guard de desarrollo. Las otras variables MACE_*
no tienen un lector Java de producción. Las dos anulaciones del helper siguen
compiladas fuera de la DLL de producción, sin tocar código C.
LOCALAPPDATA se sigue usando para datos de usuario: no es una anulación MACE_*
de carga ni se cambió la configuración de persistencia.

## Arranque sin parámetros o componente rechazado

MACE en modo nativo muestra un aviso de error con este contenido:

> No se pudo verificar el componente nativo de MACE. Repara la instalación y vuelve a intentarlo.

Al aceptar, solicita `Platform.exit()` y utiliza el cierre existente. No abre
el dashboard ni pasa a datos simulados. Cuando faltan pins, el rechazo sucede
antes de cargar la DLL, inicializar telemetría/helper o abrir la caja negra.
El proceso termina mediante el cierre normal; se conserva el watchdog de
último recurso de 10 s si ese cierre se atasca. No se añade otro temporizador.
El test CLI informa éxito cuando demuestra el rechazo esperado; eso no
significa que la carga rechazada se haya aceptado.

Se elige este comportamiento para evitar presentar métricas reales o guardadas
con un componente cuya selección no está fijada. No se sugiere al usuario
resolverlo definiendo variables. Tampoco se publican ruta, hash ni excepción
interna. En producción se omite la impresión de la ruta de DLL al arrancar.

Preview sigue siendo simulación explícita mediante su propiedad existente:
no intenta cargar DLL y no necesita pins nativos. No se cambió ningún launcher,
manifiesto UAC ni la configuración de Preview.

## Diseño y límites

Se descartó activar desarrollo mediante una variable MACE_* o una propiedad
JVM: además del entorno directo, opciones de JVM heredadas podrían suministrar
esa propiedad. Se eligió un recurso de compilación dentro del artefacto.
Modificar JAR, recurso, clases o classpath es modificar la base de código
confiada; no se afirma protección frente a esa sustitución. Instalación con
permisos adecuados y firma siguen pendientes para Fase 3.

Los parámetros JVM son entradas confiadas del launcher; Java no autentica
quién los escribió. Ejecutar Java directamente con los mismos pins explícitos
está permitido y exige la misma verificación. Lo que se cerró es el fallback
ambiental o implícito sin esos parámetros, también en arranques directos.
No se amplía el alcance a impedir que alguien sustituya el programa completo.

Se rechaza el artefacto Java Development o sin metadato exacto de producción
en `stage-runtime.ps1`, antes de copiar el payload. Es una comprobación de
construcción; no se cambia el launcher. `verify-native-dll.ps1` entrega los pins
al verificador Java existente para que el chequeo previo al empaquetado siga
utilizando la misma carga endurecida. No instala ni inicializa sensores.

ABI 1.5 / DLL 1.5.0, protocolo 2, helper 1.1.1, tamaños/offsets y 24 exports
permanecen intactos. No hay cambios en native/src, native/include, helper de
producción, NVML, IGCL, GetSystemTimes, DXGI, RAM, reintentos/cooldown 4A,
estados 4B, job, instancia única, cierre o watchdog.

## Pruebas nuevas y regresión

`NativeProductionPolicyTest` ejecuta 13 casos en procesos JVM aislados:
cuatro variables ajenas con pins buenos; falta de ambos pins; falta de ruta;
falta de hash; ruta relativa; hash malformado; hash distinto; archivo ausente;
sobrecarga explícita sin pins; ruta explícita alternativa con pins; AUTO sin
pins; metadato ausente; metadato desconocido. Cada rechazo exige exactamente
el mensaje genérico y causa nula. Se añaden flags/variables que intentan activar
desarrollo y no lo consiguen. Los procesos hijos no conservan opciones JVM
ambientales que puedan contaminar el experimento; no se cambian las del equipo.

El positivo usa DLL real de producción aislada sin carpeta lhm adyacente.
MACE_NATIVE_LIB apunta a un archivo ajeno y su hash; MACE_LHM_HELPER apunta al
ejecutable simulado válido y su hash correcto. Debe cargar la DLL fijada,
producir HELPER_UNAVAILABLE=13 y uso CPU de Windows válido. No se usa un helper
ajeno inválido que pudiera fallar y dar un falso positivo de rechazo. No se
arranca el helper real ni se requiere hardware para esta prueba.

| Batería en consola normal | Resultado de esta entrega |
|---|---|
| Helper recompilado | 105 PASS |
| CTest Development recompilado | 76/76 PASS |
| CTest Production recompilado | 77/77 PASS |
| Java | 347 PASS + 1 omitida; 348 total, cero fallos/errores |
| Producción Java aislada | 13/13 PASS, incluidas en las 347; comando entregable también comprobado |
| Payload | 8 PASS + rechazo DLL Development PASS + nuevo rechazo Java Development PASS |
| check-protected-telemetry | PASS |
| audit-retired-providers | 181 aceptadas, 0 inesperadas; TSV sin cambios |
| Script desde fuentes | PrepareOnly PASS; perfil development y classpath generados en carpeta separada |

Las cifras Java aumentan en 13 respecto a 334 + 1 omitida. La omitida sigue
siendo la DLL antigua externa opcional. Los ocho tests de watchdog/instancia
única que requieren fixture sí se ejecutan; `test-java-regression.ps1` prepara
MACE_WATCHDOG_FAKE_HELPER para evitar omisiones accidentales.

La primera ejecución de Java se solapó con la reconstrucción de Production y
encontró DLL ocupada. La repetición fue secuencial y pasó. Los primeros intentos
MSBuild/CTest dentro del sandbox encontraron restricciones (incluido Win32 5
en create-bootstrap-pipe); se repitieron fuera del sandbox, sin RunAs ni elevar
Windows, con las mismas aserciones. El comando de producción registra
`TEST_CONTEXT Administrator=False`. No se debilitaron tests para hacerlos pasar.
El usuario autorizó explícitamente los cierres de procesos propios del arnés.

Logs locales de herramientas en `native/build/fase2-java-hardening`, XML en
`app/target`: ignorados, pueden contener rutas de herramientas/perfil y hashes
de artefactos; no se incorporan al commit ni al producto. La regeneración y
prueba de las cuatro variables sobre MACE.exe final siguen para 5.1.

## Evidencia adicional del usuario y NO VERIFICADO

El usuario confirmó `test-lhm1b.ps1 -BuildDirectory
.\native\build\fase2-paso4b\dev -RealHelper -RequireCpu` en consola administradora
limpia sin MACE_*: todos los tests, CPU real y código 0, con DLL 1.5.0 de
desarrollo. Cubre Development elevado y esa CPU; no CTest Production, Java,
helper, payload ni nueva imagen en administrador. Se actualizó la consolidación.

**NO VERIFICADO:** matriz completa Production/Java/helper/payload elevada;
revisión visual del nuevo rechazo genérico; arranque interactivo completo desde
fuentes tras este cambio; imagen nueva; recuperación física por parada de
PawnIO (ERROR 1052 anterior, no se fuerza); ausente → instalado; origen real
3010; Intel/NVIDIA, HVCI y equipo limpio. Firma, licencia y revisión legal
pendientes. No se ejecutó PawnIO ni se tocaron servicios/HVCI o señal de registro.

## Comandos de validación del usuario

Desde la raíz del repositorio. JDK 22 por defecto; los scripts aceptan -JavaExe
si está en otro lugar. No elevan automáticamente. Primero deben estar
preparados los builds nativos Development y Production indicados en 4B.

```powershell
# Prueba de producción sin UI: trece casos, salida final PASS, código 0.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-java-production.ps1
# El mismo comando, abriendo al final el aviso genérico sin pins.
# Cierra tú otras instancias MACE/Preview antes de solicitar la vista.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-java-production.ps1 -ShowFailure
# Regresión Java completa y payload, sin regenerar packaging/dist/MACE.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-java-regression.ps1 -Payload
```

Puedes ejecutar el primer comando desde una consola de administrador abierta
por ti para validar el endurecimiento con ese token: debe registrar
Administrator=True y los mismos trece éxitos. Comunica código final y cualquier
caso fallido; no hace falta publicar los logs brutos con datos locales.

Para ejecutar desde fuentes sin preparar variables a mano:

```powershell
# Desarrollo Java + DLL Development 4B + publicación verificada del helper.
powershell -NoProfile -ExecutionPolicy Bypass -File .\app\scripts\run-source.ps1
# Preview explícito, sin cargar sensores reales.
powershell -NoProfile -ExecutionPolicy Bypass -File .\app\scripts\run-source.ps1 -Preview -Scenario PAWNIO_STOPPED
# Compatibilidad del script anterior: prueba real SIN elevar.
powershell -NoProfile -ExecutionPolicy Bypass -File .\native\scripts\test-sensor-ui.ps1 -ConsoleProbe
```

`run-source.ps1` acepta -NativeDll y -HelperDirectory; por defecto usa el build
dev 4B y helper/artifacts/latest.txt. Verifica el helper y calcula los pins del
artefacto de desarrollo; el constructor controla esas entradas. No cambia
manifiestos ni launchers y no solicita elevación. Si quieres comprobar CPU
física elevada, abres tú la consola administradora antes de usarlo. Los datos
de caja negra se aíslan en app/target/source-development/blackbox; las
preferencias HUD conservan su backend existente.

Para la regresión nativa, usar procesos limpios y ejecutar antes de Java:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\native\scripts\test-lhm1b.ps1 -BuildDirectory .\native\build\fase2-paso4b\dev'
powershell -NoProfile -ExecutionPolicy Bypass -Command 'Get-ChildItem Env:MACE_* | Remove-Item; & .\native\scripts\build-native.ps1 -BuildDirectory .\native\build\fase2-paso4b\prod'
```

## Cambios por archivo

| Archivo | Cambio |
|---|---|
| app/pom.xml | Perfil production por defecto, development explícito, filtrado de metadato y directorio de salida configurable para separar builds |
| app/src/main/profile/com/mace/infrastructure/nativebridge/native-profile.properties | Metadato de compilación que se incluye en el JAR |
| app/src/main/java/com/mace/infrastructure/nativebridge/NativeLoadProfile.java | Selección de perfil sin entorno y validación de pins obligatorios, error fijo |
| NativeLibraryLocator.java; NativeLibraryIntegrity.java, en el mismo paquete | Producción no lee anulaciones ni busca alternativas; desarrollo conserva el flujo |
| NativeFunctions.java, mismo paquete | Todas las sobrecargas respetan pins; fallo de producción genérico sin causa |
| app/src/main/java/com/mace/infrastructure/InfrastructureAdapters.java | AUTO producción no oculta el rechazo con mocks |
| app/src/main/java/com/mace/presentation/MaceApplication.java | Aviso genérico y cierre sin dashboard al fallar arranque nativo; ruta de DLL solo en desarrollo |
| app/src/test/resources/com/mace/infrastructure/nativebridge/native-profile.properties | Desarrollo solo en classpath de tests, fuera del JAR |
| app/src/test/java/com/mace/infrastructure/nativebridge/NativeProductionPolicyTest.java; NativeProductionProbe.java | Trece pruebas de proceso, cuatro variables ajenas, pins, sobrecargas, metadato y error genérico |
| app/scripts/run-source.ps1 | Compilación y arranque de desarrollo/Preview con entradas verificadas y restauración de entorno |
| native/scripts/test-sensor-ui.ps1 | Prepara y usa clases Java Development de la carpeta separada |
| native/scripts/test-java-production.ps1 | Comando reproducible de las pruebas de producción y vista de fallo opcional |
| native/scripts/test-java-regression.ps1 | Regresión Java con fixtures explícitos, restauración del entorno y payload opcional |
| packaging/verify-native-dll.ps1 | Pins explícitos al verificador de construcción, sin cambiar launcher |
| packaging/stage-runtime.ps1 | Rechaza Java sin perfil production exacto antes de copiar payload |
| packaging/tests/test-runtime-payload.ps1 | Nuevo negativo de Java Development, conservando los ocho checks y rechazo DLL |
| native/README.md; native/tests/README.md | Perfiles, comandos, exclusión local y orden secuencial de pruebas |
| helper/FASE2-PASO5.0.md; FASE2-PASO5.0-AUDITORIA.md | Evidencia nueva del usuario y referencia al endurecimiento posterior |
| helper/FASE2-INVENTARIO-ESCRITURAS.md | Inventario estático y riesgo de halt para incorporar en 5.3, sin corregir |
| helper/FASE2-H-5.0-JAVA.md | Este informe y procedimiento |

La exclusión local no se versiona. No hay cambios de manifiestos, launchers,
ABI, helper/protocolo, política 4A ni ramas principales. **Detenerse aquí y
esperar confirmación antes de regenerar la imagen en 5.1.**
