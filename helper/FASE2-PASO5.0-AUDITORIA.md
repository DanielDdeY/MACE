# Fase 2 — 5.0: auditoría de anulaciones de entorno

Fecha: 2026-11-17. Base: `5f818cb7c0e6cceafab46300d8d350209088d208`.
Inspección de fuentes y evidencia ya existente; sin cambiar código ni ejecutar
de nuevo las pruebas. No se modificaron variables de usuario/máquina, launchers,
manifiestos ni claves de registro. La imagen final de Fase 2 aún no se regeneró.

## Conclusión y límite de seguridad

La DLL de producción ignora las dos anulaciones del helper por compilación.
Para las dos anulaciones de la DLL en Java, la protección depende de las
propiedades JVM que fija el launcher: **no hay una prohibición compilada en Java**.
Con `MACE.exe` y su configuración íntegra, las propiedades fijadas prevalecen
sobre esas variables. Una ejecución Java directa sin ellas sigue aceptándolas,
incluso al seleccionar una DLL de producción.

**Hallazgo H-5.0-JAVA:** si se considera ejecución de producción el arranque
Java directo sin los pins del launcher, continúan activas `MACE_NATIVE_LIB` y
`MACE_NATIVE_SHA256`. Controlar ambas permite seleccionar una DLL compatible y
su hash; ese hash suministrado por el mismo entorno no autentica al publicador.
Si además falta todo hash esperado, la carga directa no lo exige. La política
de producción del helper dentro de la DLL no protege por sí misma la selección
previa de esa biblioteca por Java.

Esto no demuestra que las cuatro variables puedan sustituir componentes al
ejecutar el `MACE.exe` configurado correctamente. Tampoco se declara superada
la prueba integral de la nueva imagen. No se cambia código para resolver el
hallazgo: se espera la decisión del usuario sobre el alcance de producto.

## Matriz de evidencia

Las rutas de esta tabla son relativas a la raíz del repositorio.

| Variable | Capa y comportamiento | Archivo / función | Evidencia existente y límite |
|---|---|---|---|
| `MACE_LHM_HELPER` | Development: selecciona ruta. Producción: no se lee; se deriva `lhm/Mace.Lhm.Helper.exe` junto a la DLL | `native/src/telemetry/lhm_receiver.c`, `helper_path()` | CTest `lhm_actual_production_ignores_environment` carga la DLL real de producción con ruta ajena y sin runtime esperado: estado 13 y uso CPU de Windows válido; no usa el helper ajeno |
| `MACE_LHM_HELPER_SHA256` | Lectura solo bajo `MACE_LHM_DEVELOPMENT`. Producción usa el hash del manifiesto compilado | `native/src/telemetry/lhm_trust.c`, `LhmTrust_Acquire()`, `production_root()`, `manifest()` | El caso anterior y `lhm_security_ignore-env` suministran hash ajeno. Los casos `missing`, `manifest`, `tampered`, `extra`, `locks`, `relaunch` comprueban rechazo y conservación de archivos verificados |
| `MACE_NATIVE_LIB` | Java usa primero `mace.native.lib`; si falta o está vacía, usa el entorno sin distinguir Development/Production | `app/src/main/java/com/mace/infrastructure/nativebridge/NativeLibraryLocator.java`, `explicitLocation()`, `locate()` | `NativeFunctionsTest`: propiedad con carpeta, archivo y ruta inexistente. El procedimiento real sin elevar de 4B usa deliberadamente la variable con DLL de producción; no prueba inmunidad del launcher |
| `MACE_NATIVE_SHA256` | Java usa primero `mace.native.sha256`; sin ella lee entorno. Sin ambas, hash esperado opcional | `app/src/main/java/com/mace/infrastructure/nativebridge/NativeLibraryIntegrity.java`, `expectedSha256()`, `verify()`; `NativeFunctions.java`, `load()` | `NativeFunctionsTest.unaDllConHashDistintoSeRechazaSinCargarla` prueba rechazo de hash explícito. No demuestra que una variable ajena sea ignorada en la imagen completa |

La DLL no acepta un número del helper por el mero hecho de ser finito:
siguen aplicándose elevación, PawnIO, estado, coherencia y frescura. Esta
auditoría no modifica los estados ni la política de reintentos.

## Qué fija el empaquetador y qué falta verificar

`packaging/package-windows.bat`, bloque de `jpackage` (líneas 316–320 a esta
base), suministra al launcher:

```text
-Dmace.infra.mode=native
-Dmace.native.lib=$APPDIR\mace_native.dll
-Dmace.native.sha256=<hash verificado durante el empaquetado>
-Djava.library.path=$APPDIR
```

`explicitLocation()` elige una propiedad no vacía antes de consultar el entorno.
Si esa ruta explícita no existe, `locate()` no busca otra DLL indicada por la
variable. `expectedSha256()` también da prioridad a la propiedad no vacía.
La configuración de la imagen antigua en `packaging/dist/MACE/app/MACE.cfg`
contiene esos pins; **no es todavía la imagen regenerada de Fase 2**.
`MACE-Preview` utiliza datos simulados y no demuestra la carga nativa real.

El empaquetador acepta `MACE_NATIVE_LIB`/`MACE_NATIVE_SHA256` como entradas del
proceso de construcción. Es una elección de artefacto por quien construye,
distinta de una anulación al arrancar el producto ya generado.

La raíz de confianza del helper está en la DLL: el hash compilado del
manifiesto fija 202 archivos runtime. `LhmTransport_OpenEx()` adquiere una nueva
verificación antes de cada lanzamiento, con archivos retenidos, ruta canónica,
directorio y entorno controlados. `packaging/verify-runtime.ps1` rechaza el
marcador Development. Los fixtures con hooks no son la DLL de producto.

Límite de las verificaciones actuales: `verify-image.ps1` comprueba contenido
prohibido e invoca `verify-runtime.ps1`; este último excluye `MACE.cfg`,
`MACE-Preview.cfg` y `.jpackage.xml` del cotejo del inventario de la app-image.
Por tanto, ese cotejo no acredita por sí solo los valores de las propiedades
JVM. La cadena depende también de la integridad del launcher/configuración y
de los permisos de instalación. Firma y ACL del instalador siguen pendientes.

**PENDIENTE DE MANUAL / 5.1:** comprobar la configuración nueva y ejecutar la
prueba controlada con las cuatro variables ajenas en el entorno del proceso,
sin cambiar variables persistentes. Los dos casos CTest del helper no sustituyen
esa prueba Java + launcher + DLL de la imagen. Si requiere elevación, la
ejecutará el usuario; este agente no elevará el producto.

## Decisión requerida antes de continuar con la imagen

Confirmar si el producto soportado se limita a `MACE.exe` con su configuración
íntegra y los arranques Java directos quedan como herramientas de desarrollo,
o si también deben quedar prohibidas las anulaciones en Java directo usando
artefactos de producción. La segunda opción requiere aprobar un cambio de
código y sus pruebas fuera de esta consolidación sin código. No se implementa
silenciosamente ni se cambia ABI, protocolo, helper o launcher.

La decisión no reemplaza la prueba de las cuatro variables sobre la imagen.
No se ha demostrado una sustitución mediante esas variables con los pins
correctos del launcher presentes; no se afirma que el caso Java directo esté
bloqueado cuando dichos pins faltan.
