# Fase 2 — inventario de escrituras y riesgo de halt(70)

Fecha: 2026-11-17. Inspección de solo lectura del código propio de Java, DLL y
helper, preparada para incorporar al informe 5.3. No se cambia persistencia,
configuración ni política de retención. No es una traza física de todo el sistema.
La atomicidad significa publicación completa o conservación de la versión
anterior; `synchronized`, CREATE_NEW, CRC o `force()` por separado no la garantizan.

## Escrituras propias durante la ejecución

Las rutas de fuentes son relativas al repositorio. Las ubicaciones de datos
son simbólicas, sin consultar carpetas ni claves personales del equipo.

| Datos / destino | Quién escribe y cuándo | Atomicidad y riesgo con halt(70) | Qué ocurre al siguiente arranque |
|---|---|---|---|
| Carpeta `%LOCALAPPDATA%/MACE/blackbox`; subcarpeta `simulated` para mocks. Se admite propiedad `mace.blackbox.dir`; fallback `user.home/.mace/blackbox` | `BlackboxSettings.defaultDirectory()`, `PersistentBlackboxSink.open()`: `Files.createDirectories` al abrir caja negra | Creación de directorios, sin transacción conjunta con el archivo. Puede quedar carpeta vacía. Un destino configurado puede ser distinto; el código no lo limita a AppData | Reintenta crear carpeta y abre sesión nueva. Si falla, `InfrastructureAdapters.openBlackbox()` conserva telemetría y usa `NoOpBlackboxSink`, registrando el fallo |
| Archivo nuevo `mace-blackbox-<UTC>-<pid>-<parte>.mbbx`, cabecera inicial de 64 bytes | `PersistentBlackboxSink.rotate()` y `BlackboxFileWriter.create()`: CREATE_NEW, cabecera, `force(true)` | No hay temporal + rename. CREATE_NEW evita sobrescribir un nombre existente, pero halt durante creación/cabecera puede dejar archivo vacío o parcial. La fecha/PID del nombre son datos del producto, no un formato permitido para los futuros logs saneados de prueba | No se reabre para escribir ni se repara. `scanIncomplete()` lo lee; menos de 64 bytes → cabecera no válida y lista vacía; se informa como incompleto. Después se crea otro archivo |
| Registros CPU, GPU, RAM, muestras de procesos y eventos; registros GAP | `record()` agrega a anillo RAM; `drain()` escribe en `flush()`, al registrar eventos y al cerrar. `BlackboxFileWriter.append()`: registro de 192 bytes con CRC y luego actualización de cabecera en posición 0 | No atómico: registro y cabecera son escrituras separadas y cada una puede requerir varios `channel.write()`. Halt puede dejar registro truncado, cabecera con CRC inválido, contador atrasado, o perder lo que seguía solo en RAM. CRC detecta daños, no los evita | Lector de solo lectura recorre registros completos consecutivos con CRC/secuencia válidos; se detiene en primer registro inválido o truncado. Puede recuperar registros aunque el contador de cabecera esté atrasado; no se limita a ese contador. Cabecera inválida se marca como tal; no autentica origen real/simulado |
| Sincronización periódica de caja negra | `MonitoringService.publish()` llama `blackbox.flush()` por ciclo; `PersistentBlackboxSink.flush()` drena y `writer.force()` usa `channel.force(false)` | Reduce pérdida, pero no es una transacción ni una garantía ante fallo de energía/disco. Halt durante drenaje/force puede conservar solo un prefijo de registros. Eventos ya escritos desde `recordEvent()` pueden esperar al siguiente force | Se recupera el prefijo verificable y se marca sesión incompleta; no se inventa el resto |
| Fin de sesión y cabecera de cierre limpio | `PersistentBlackboxSink.close()`: drena, SESSION_END, `BlackboxFileWriter.close()` reescribe cabecera con cleanClose y `force(true)`, cierra canal y anillo | Halt no ejecuta estos cierres. Puede faltar SESSION_END o quedar cabecera parcialmente reescrita. Incluso si hay bytes de cleanClose, cabecera/CRC y cola deben ser coherentes; no se promete atomicidad entre bandera, registros y durabilidad | `Contents.isIncomplete()` considera cabecera, cierre y cola dañada. La UI de historial clasifica sesión actual/completa/incompleta y ofrece registros recuperables. No se modifica el original para hacerlo parecer completo |
| Rotación y borrado por retención | `rotate()` cierra el archivo previo, `applyRetention()` borra los antiguos antes de crear la parte nueva; límites por defecto 20 archivos / 64 MiB, 8.192 registros por archivo | No transacción de conjunto: halt después de borrar y antes de crear la nueva parte pierde archivos retenidos sin producir reemplazo. Las eliminaciones completadas no se revierten. Archivos incompletos también participan en retención | Vuelve a enumerar archivos presentes por nombre. No hay journal de retención ni papelera propia; los borrados no se restauran. La frase "se conservan incompletos" describe que no se reescriben al recuperar, no inmunidad frente a retención |
| Preferencias Micro-HUD: enabled, gpuIndex, opacity, campos, posición x/y | `presentation/view/hud/HudPane.java`: `Preferences.userNodeForPackage`, llamadas `putBoolean/putInt/putDouble` al cambiar controles, cerrar/ocultar o mover HUD. En Windows backend de Java Preferences, fuera de archivos propios de caja negra | No temporal + rename ni transacción multiclave en MACE; no hay `flush()` explícito. No se garantiza aquí la durabilidad del backend ni que todos los valores se guarden juntos. Halt puede perder los cambios recientes o dejar combinación vieja/nueva | HUD lee cada clave con su valor por defecto (`getBoolean/getInt/getDouble`); valores ausentes o no convertibles usan el fallback de la API. No hay reparación propia. Fallos de permisos/backend, coordenadas fuera de pantalla o combinaciones incoherentes no tienen recuperación específica demostrada |

La cabecera de la caja negra se reescribe **en el mismo archivo**, no por
reemplazo atómico. Su CRC cubre los primeros 56 bytes; cada registro tiene su
CRC independiente. El lector puede recuperar un prefijo sin arreglar la
cabecera y sin garantizar recuperación total. Un archivo ilegible causa aviso
y se continúa con los demás durante `scanIncomplete()`. Lectura concurrente de
archivo activo puede observar un estado transitorio; no se afirma snapshot atómico.

`PersistentBlackboxSinkTest` ya prueba formato, orden, GAP, rotación, retención,
archivo previo intacto y recuperación de archivo truncado. Son pruebas de
archivos preparados; **NO VERIFICADO**: corrupción real por halt mientras el
kernel/disco ejecuta cada escritura, corte de energía, disco lleno, pérdida de
permisos y garantía física de durabilidad. No se han provocado esos fallos aquí.

## Datos que permanecen en memoria o se leen solamente

| Componente | Resultado de inspección |
|---|---|
| Gráficas y métricas recientes | `MetricHistory` mantiene listas RAM; no hay archivo propio de historial de gráficas. El historial persistente visible proviene de `.mbbx` |
| Vista del historial | `BlackboxHistoryViewModel` enumera y `BlackboxFileReader` abre READ; no repara, exporta ni reescribe archivos |
| Anillo nativo / compartido | `native/src/extensions/blackbox_buffer.c` y `shared_buffer.c` usan CreateFileMapping con INVALID_HANDLE_VALUE: no archivo de datos propio. La paginación del sistema queda fuera de la atomicidad del formato `.mbbx` |
| Receptor, trust y transporte nativo | Archivos runtime abiertos para verificar/retener, no escribir. WriteFile se usa en pipe/bootstrap/control; stdout/stderr van a NUL. No hay logs propios persistentes ni archivo de PID/lock |
| Instancia única y job | Objetos de Windows, no lockfile. No crean persistencia propia que reparar al arrancar |
| Helper propio | `Program`, muestreador y recuperación transmiten por pipe; no se encontró escritor propio de configuración, historial, logs o caché. No se ejecuta instalador ni se cambia registro de 3010 |
| Configuración nativa, política de reintentos y estados | No se persisten; se reconstruyen en cada proceso. Presupuesto/backoff no se escriben a disco |
| Logger Java / salida de helper | Código propio usa System.Logger/stdout/stderr; no configura FileHandler o fichero de log. Redirecciones del usuario y configuración externa de la JVM pueden crear archivos fuera de esta política |

## Límites, herramientas de desarrollo y riesgos para 5.3

- **NO VERIFICADO:** escrituras indirectas de LHM, runtime .NET/JVM, JavaFX,
  controladores gráficos, Windows Preferences, WER, antivirus o servicios.
  Inventario estático del código propio no equivale a una captura exhaustiva
  de actividad de terceros ni acredita ausencia de toda escritura en Program Files.
- Compilación/pruebas/empaquetado escriben `app/target`, `native/build`,
  `helper/.../bin`, `obj`, `helper/artifacts`, caches de herramientas y
  `packaging/runtime`, `output`, `dist`. Generan clases/binarios, manifiestos,
  hashes, reportes, logs y fixtures. No forman parte de la ejecución normal
  del producto ni se garantiza su atomicidad ante interrupción. La imagen
  final debe excluir documentación/pruebas/scripts/artifacts/logs.
- El script desde fuentes agrega datos de caja negra bajo
  `app/target/source-development/blackbox`; no cambia el destino por defecto
  del producto. Las preferencias del HUD siguen utilizando su backend existente.
- Riesgo residual para 5.3: pérdida de la cola RAM, último registro o cabecera
  y cambios recientes del HUD al forzar halt. La retención puede haber borrado
  sesiones anteriores antes del halt. La recuperación por CRC conserva solo
  un prefijo verificable; no sustituye una estrategia transaccional o backups.
- No se propone ni aplica una corrección de persistencia sin aprobación del
  usuario. Incorporar este inventario y su estado a `FASE2-CIERRE.md` cuando
  corresponda el 5.3; no se adelanta aquí el informe de cierre de la fase.
