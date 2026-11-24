# Fase 3 — Paso 3.0: preparación y deuda documental

Fecha: 2026-11-18, America/Lima. Alcance: **solo preparación documental y
comprobación de integridad de lectura**, según prompt_mace_fase3.md.

## Partida y referencias

- Commit inicial: **8f594595a9313ecb97ec12f349f1e57d0b87ddb5**.
- Rama: **LHM_implementado**. `git status --short` vacío antes de empezar.
- Se leyeron [FASE2-CIERRE.md](FASE2-CIERRE.md),
  [FASE2-PENDIENTES.md](FASE2-PENDIENTES.md),
  [FASE2-PASO5.1.md](FASE2-PASO5.1.md),
  [FASE2-INVENTARIO-ESCRITURAS.md](FASE2-INVENTARIO-ESCRITURAS.md),
  [PROTOCOL.md](PROTOCOL.md), [packaging/README.md](../packaging/README.md),
  prompt_mace_fase1.md y el nuevo prompt_mace_fase3.md.
- La Fase 3 del prompt antiguo queda reemplazada por el nuevo. Sus órdenes de
  comenzar por 1A o ejecutar otras fases no se vuelven a aplicar.
- Se conserva la instrucción del proyecto: sources/ es referencia de solo lectura.

El mensaje actual autoriza comenzar la Fase 3 **por este paso**. La casilla de
aprobación del cierre 5.3 del adjunto está sin marcar: no se registra como una
casilla aprobada ni se cambia retrospectivamente el alcance de sus evidencias.
El cierre con deuda y sus verificaciones pendientes siguen como referencia.

## Deuda documental: aplicación condicionada

El Paso 3.0 indica: «Si aprobé las correcciones documentales, aplícalas en un
commit aparte. Sin tocar los informes históricos fechados ni cambiar código».
No consta una aprobación explícita de la lista de correcciones del 5.3. Por
ello **no se aplican** ni se modifican documentos operativos anteriores.

La propuesta concreta permanece en
[Coherencia del repositorio](FASE2-CIERRE.md#coherencia-del-repositorio-propuestas-sin-modificar-los-originales).
Al aprobarla, su ejecución deberá distinguir:

| Material | Tratamiento pendiente |
|---|---|
| packaging/README.md, helper/README.md, native/README.md y README.md raíz | Correcciones operativas propuestas de ABI vigente, app-image frente a instalador, firma opcional/pendiente, enlaces y límites de afirmaciones físicas; requieren aprobación |
| helper/PROTOCOL.md | Distinguir el relato histórico del 4A del ABI 1.5 vigente, sin cambiar wire, política ni estados; requiere aprobación |
| Informes históricos fechados de Fase 2 | Conservar íntegros; la evidencia posterior se consulta en el cierre, no se reescribe la historia |
| Mensajes antiguos en scripts y diferencia PS5.1/PS7 | No cambiar código en 3.0; documentar límites y reservar cualquier corrección de tooling para aprobación separada |

Este informe es un registro nuevo del 3.0, no la aplicación de esa lista.

## Integridad de la imagen 5.1

Comprobación ejecutada con **Windows PowerShell 5.1.19041.6456 x64**, token
normal (`administrator=0`), sin ejecutar MACE, helper, DLL ni instaladores.
Se leyó packaging/dist/MACE-SHA256SUMS.txt, se contrastó su hash con el fijado
en el informe 5.1 y cada archivo se comparó mediante SHA-256. También se
enumeraron los archivos reales para detectar extras y se rechazaron entradas
duplicadas o rutas inesperadas en el inventario.

| Comprobación nueva | Resultado |
|---|---|
| Hash del inventario frente al registrado en 5.1 | Coincide |
| Entradas únicas del inventario | 370 |
| Archivos reales en packaging/dist/MACE | 370 |
| Archivos faltantes | 0 |
| Archivos con hash distinto | 0 |
| Archivos adicionales | 0 |
| Tamaño real total | 147.787.297 bytes |
| Recurso FileVersion de app/mace_native.dll | 1.5.0.0 |
| Recurso FileVersion de app/lhm/Mace.Lhm.Helper.exe | 1.1.1.0 |
| Salida de la comprobación | PASS, código 0 |

Salida saneada de la comprobación, sin rutas locales ni hashes completos:

```text
CONTEXT ps=5.1.19041.6456 administrator=0
IMAGE inventory_unchanged=1 listed=370 actual=370 missing=0 mismatches=0 additional=0 bytes=147787297
VERSIONS dll=1.5.0.0 helper=1.1.1.0
PASS readonly_image_370=1
```

Esto acredita igualdad de bytes con la imagen de referencia; no es una nueva
prueba de sensores, instalación, firma de publicación ni de ejecución elevada.
Tampoco sustituye los controles inmediatamente anteriores a una ejecución
futura del instalador de PawnIO. No se ejecutaron tests que añadan archivos
temporales a la imagen, ni se regeneraron manifiestos o payload con PS7.

**Regeneración posterior:** la imagen 5.1 se conserva congelada en este paso.
Un consumo aprobado de la señal 3010 o una firma futura cambiará bytes y exigirá
una nueva construcción/verificación coherente y sus pins. El diseño deberá
definir cómo generar ese conjunto sin refirmar ni alterar esta imagen en sitio.
No se ha decidido ni ejecutado una regeneración en el 3.0.

## Decisiones recibidas para el diseño posterior

Se registran las selecciones escritas por el usuario, sin adelantar su
implementación ni afirmar que ya son una tecnología validada en este entorno.

| Punto | Decisión o límite recibido |
|---|---|
| Instalador | EXE/bootstrapper que encadene PawnIO y MACE; herramienta concreta y encadenamiento se evaluarán en 3.1 |
| Alcance | Por máquina, con administrador; no cambiar el UAC actual |
| PawnIO existente | Respetarlo y no reinstalar si la versión es compatible; el criterio exacto de compatibilidad se definirá en 3.1 |
| Fallo al instalar PawnIO | MACE se instala y funciona degradado, CPU en N/D con su mensaje; no abortar toda la instalación por esa causa |
| Desinstalación de MACE | Conservar PawnIO compartido |
| Firma | Certificado/servicio pendiente; se mantiene la preferencia previa de excluir pagos mensuales, sin adquirir nada |
| Licencia MACE | Preparar análisis de compatibilidad; licencia propia todavía no elegida |
| 3010 | Clave volátil aprobada solo en principio; falta revisar esquema, ACL y consumo en 3.1. No se lee ni escribe la señal |
| Reintento manual | Diferido a futuras versiones; no implementarlo ni volver a proponerlo en esta fase |
| Pruebas reales de instalación | Otra máquina; el nuevo plan menciona laptop prestada Windows 11/HVCI/instantánea. Estado real y disponibilidad: NO VERIFICADO |

Se mantiene la congelación de DLL 1.5.0/ABI 1.5, wire privado 2, helper 1.1.1,
estados 9–16 y política 4A. El diseño debe proponer por separado cualquier cambio
de producto para consumir 3010; el diff mínimo espera aprobación antes de
aplicarse. No se adelanta ese diseño ni el instalador en este paso.

## Pruebas, pendientes y alcance del commit

Única verificación de artefactos nueva: lectura de los 370 hashes y metadatos
anteriores. No se repite regresión helper/CTest/Java/payload al añadir solamente
este informe; las cifras y límites anteriores permanecen en el cierre 5.3.

**NO VERIFICADO:** instalación limpia/silenciosa, detección/versiones compatibles
de PawnIO como contrato del futuro instalador, ejecución y códigos reales de
su instalación, 3010 real y su señal, rollback/actualización/reparación y
desinstalación, hardware/HVCI/equipo limpio con el conjunto final, firma propia,
licencia y revisión legal. La deuda física de Fase 2 no se convierte en PASS.
La disponibilidad real de la laptop y su mecanismo de restauración también
deben confirmarse antes de pruebas destructivas en otro equipo.

Archivo añadido: **helper/FASE3-PASO0.md**. No se incluyen adjuntos originales,
logs, los tres TXT excluidos localmente ni datos personales. Se comprueban
enlaces locales y formato del diff antes de registrar este cambio documental.
No cambia producto/imagen, código, manifiestos/launchers, fuentes sincronizadas,
servicios, HVCI ni registro. No hay instalación, elevación, terminación de
procesos ni fusión a principal.

**Fin del Paso 3.0: detenerse y esperar aprobación antes del Paso 3.1.**
Quedan para decisión la aprobación expresa de las correcciones documentales
y la ratificación del cierre 5.3 cuya casilla del adjunto quedó sin marcar.
