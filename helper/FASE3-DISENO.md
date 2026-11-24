# Fase 3 — Paso 3.1: diseño del instalador y contratos pendientes

Fecha: 2026-11-18, America/Lima. Partida **ee15c3bf30ea4249265e69ad594b6130bc80a912**,
rama LHM_implementado, árbol limpio confirmado. **Diseño sin implementación**.
El usuario aprobó avanzar desde 3.0; no se interpreta como aprobación anticipada
de las propuestas de este documento ni se aplican las correcciones históricas.

Referencias de alcance: [preparación 3.0](FASE3-PASO0.md),
[cierre Fase 2](FASE2-CIERRE.md), [deuda](FASE2-PENDIENTES.md),
[imagen 5.1](FASE2-PASO5.1.md), [escrituras](FASE2-INVENTARIO-ESCRITURAS.md),
[protocolo](PROTOCOL.md) y [empaquetado actual](../packaging/README.md).
Se relee prompt_mace_fase3.md; reemplaza su Fase 3 anterior. Ningún comando de
instalación de este diseño se ha ejecutado en el host.

**Estado vigente, 2026-11-23:** el usuario prioriza las decisiones de `3.1c.md`
y sus confirmaciones posteriores sobre las propuestas anteriores. MIT elegida,
derivados cerrados aceptados y titular declarado **DanielDdeY**; SignPath
Foundation e Inno Setup elegidos, con elegibilidad de firma pendiente.
D4 opción (a) y migración JDK como trabajo separado aprobadas. Se mantiene
aprobación previa de cada diff de producto. Estados actuales en sección 11 y
[registro de decisiones y continuidad](FASE3-3.1C-DECISIONES-Y-CONTINUIDAD.md).
Estamos en **3.1c, consolidación previa al Paso A**; no se ha entregado el
inventario A, creado LICENSE ni presentado solicitud a SignPath. Los análisis
WiX/MSI y decisiones pendientes del cuerpo original son evidencia histórica,
no selección vigente. No se modifican binarios ni la imagen congelada.

**Registro anterior, 3.1b (2026-11-18):** diseño aprobado con condiciones. La
[adenda 3.1b](FASE3-DISENO-ADENDA.md) contiene las aclaraciones y prevalece
sobre las propuestas del cuerpo original: **3.2 guía de VM, 3.3 instalador,
3.4 señal/consumo 3010**, después 3.5–3.9. No se ha aprobado D1, la dependencia
BootIdentifier, una licencia propia ni un cambio de JDK. Las referencias
originales a 3.2 instalador/3.3 consumo/3.4 laptop no son la secuencia vigente.
No se instala en un físico prestado: queda solo para sensores con las
condiciones de D9. **Detenerse tras 3.1b y esperar aprobación antes de 3.2.**

## 1. Decisiones recibidas y recomendación

Ya elegidos por el usuario: **EXE/bootstrapper**, instalación **por máquina**,
respetar PawnIO compatible, MACE instalado y disponible aun si PawnIO falla,
conservar PawnIO al desinstalar. Firma pendiente y análisis de licencia sin
elegirla. Señal volátil 3010 solo aprobada en principio. Reintento manual sigue
diferido, sin nueva propuesta. Pruebas reales exclusivamente en otra máquina.

**Recomendación sujeta a aprobación:** MSI de MACE y bootstrapper Burn con
**WiX 7.0.1**, tomando la app-image verificada como entrada inmutable; construir
el MSI directamente con WiX actual, sin pedir a jpackage 22 que genere ese MSI.
Un coordinador nativo pequeño del instalador valida/ejecuta PawnIO; no forma
parte de la telemetría ni requiere .NET adicional en la máquina destino.
El MSI se confirma primero y PawnIO se intenta después: así un fallo/timeout
del requisito no revierte la instalación funcional de MACE.

Esta elección evita incorporar un motor WiX 3 fuera de soporte al EXE público,
pero exige autoría MSI propia y revisar los términos actuales de WiX. **No es
un cambio ya aprobado ni una herramienta instalada**. Hay decisiones explícitas
al final, incluidas costo, timeout y la forma del consumidor 3010.

## 2. Evidencia del JDK y comparación A/B/C

Inspección local realizada, sin construir paquetes ni ejecutar instaladores:

| Elemento | Observación |
|---|---|
| JDK instalado | release: Oracle Corporation, 22.0.2+9-70, Windows x86_64; jpackage --version: 22.0.2 |
| jpackage --help | Admite app-image/msi/exe, --app-image, --resource-dir, --win-upgrade-uuid y --win-per-user-install. No expone una opción para encadenar un instalador arbitrario de PawnIO |
| jdk.jpackage.jmod | Contiene WixTool, WixPipeline, main.wxs y overrides.wxi; no se generó ni extrajo un instalador |
| javap de WixTool del JDK instalado | Enum Candle/Light, ejecutables candle.exe/light.exe, MINIMAL_VERSION = 3.0 |
| Herramientas en PATH | Get-Command no encontró candle.exe, light.exe ni wix.exe. Instalaciones fuera de PATH y compilación MSI/Burn: NO VERIFICADO |
| Scripts propios | package-windows.bat documenta WiX 3.x y app-image separada; APP_VERSION=1.0.0, Upgrade UUID existente; helper firmado antes de fijar su manifiesto |

Oracle documenta WiX 3.0 o posterior para JDK 22; el código instalado demuestra
qué ejecutables busca. **No significa que wix.exe de WiX 4–7 sea sustituto
directo de candle/light para este jpackage**. El recurso main.wxs debe preservar
el contrato de la plantilla; --resource-dir no incorpora cualquier fragmento
arbitrario sin incluirlo correctamente. Los WSF post-image/post-msi de jpackage
son de construcción, no un instalador de PawnIO al ejecutar MACE.
Fuentes: [requisitos JDK 22](https://docs.oracle.com/en/java/javase/22/jpackage/packaging-overview.html),
[opciones y recursos](https://docs.oracle.com/en/java/javase/22/docs/specs/man/jpackage.html).

| Criterio | A: jpackage MSI + personalización WiX 3 | B: EXE/Burn + MSI MACE | C: PawnIO al primer arranque elevado |
|---|---|---|---|
| Silencioso y 3010 | MSI soporta modo silencioso; ejecutar el EXE externo exige acciones adicionales cuidadosamente secuenciadas | Cadena explícita, resultado separado por paquete y modo silencioso; ajuste más directo a la elección del usuario | Requiere nueva lógica/UI de producto y consentimiento también en arranque; difícil integrarlo con despliegue silencioso |
| Rollback | Transacción MSI no deshace automáticamente un driver EXE compartido | MSI tiene su rollback; PawnIO se conserva incluso ante fallo posterior. No se promete transacción global | Instalación de app ya terminada; fallo del driver debe manejarlo cada arranque |
| Actualizar/reparar | MSI útil; mantener plantillas internas de jpackage y acciones propias | MSI para archivos; bootstrapper para requisitos y resultados. Dos capas a probar | Acopla vida del dashboard a mantenimiento del sistema |
| Privilegios | MSI por máquina elevado, independiente del UAC del launcher | Una elevación de instalación, app conserva su requireAdministrator | Usa elevación existente de MACE, pero necesita comportamiento nuevo de producto |
| Tamaño | Base 140,94 MiB de archivos más compresión/metadatos; no se midió MSI | Base idéntica más motor/coordinador; no se midió tamaño final ni se promete ahorro | Paquete sigue incluyendo PawnIO; no elimina su tamaño |
| Firma | Archivos propios y MSI | Archivos propios, coordinador, MSI, motor Burn y EXE exterior | Archivos propios/paquete, más la ruta de confianza en arranque |
| Mantenimiento | Compatible con herramientas antiguas del JDK; deuda WiX 3 | Más autoría, mejor separación y toolchain actual si se usa MSI WiX directo | Mayor superficie en aplicación, repeticiones y fallos de instalación al iniciar |
| Recomendación | Alternativa para prototipo aislado, no motor público por defecto | **Elegida por el usuario; recomendar variante actual independiente de jpackage MSI** | Descartar para esta fase; no cambiar el arranque para instalar drivers |

**Elección de versión y costo:** WiX 3 está fuera de servicio comunitario desde
2025-02-06. La documentación de mantenimiento también muestra fuera de soporte
comunitario WiX 4/5. WiX 7.0.1 figura publicado el 2026-10-26. Para WiX actual
hay OSMF/EULA: no prometer gratuidad universal por llamar open source al proyecto;
la documentación v7 indica un umbral de ingresos de US$10.000/año y condiciones
que deben revisarse para el titular real. No se acepta una EULA, adquiere una
suscripción ni instala tooling ahora. Si estas condiciones no convienen, hay
que escoger otra herramienta mantenida en un diseño separado, no recurrir
silenciosamente a una versión abandonada.
Fuentes: [fin de WiX 3](https://docs.firegiant.com/wix/wix3/),
[ciclo de soporte](https://docs.firegiant.com/wix/),
[versiones](https://docs.firegiant.com/wix/whatsnew/releasenotes/),
[OSMF](https://docs.firegiant.com/wix/osmf/).

## 3. Arquitectura de instalación propuesta

Flujo conceptual, **no un script ejecutable**:

```text
EXE de instalación (UI o silencioso, consentimiento y elevación)
  → validar conjunto firmado/pins y destino protegido
  → detectar MACE abierto; detener operación si no se puede cerrar manualmente
  → instalar/reparar/actualizar MSI de MACE; confirmar transacción
  → si se autorizó PawnIO: coordinador nativo, consulta y validación inmediata
       → compatible existente: no reinstalar
       → ausente: PawnIO original -install -silent
       → retorno observado: 0 / 3010 / error / espera vencida
  → resultado MACE separado del resultado PawnIO; sin reinicio automático
```

El coordinador sería un **componente de ejecución del instalador**, no un script
de desarrollo ni un proveedor de sensores. Leerá PawnIO_setup.exe desde el
destino protegido ya instalado por el MSI, evitando una segunda copia del EXE
en el bundle. Si se necesita cache propia, será protegida y verificada, sin
resolver rutas desde variables del usuario. No se usa PowerShell en destino.

Burn dispone de ExePackage, DetectCondition, Permanent y Vital: requisito no
vital y permanente para no revertir MACE ni desinstalar PawnIO. Se aplican al
coordinador con detección coherente; no se asume que el mero atributo valide
firma, versión o timeout del proceso que este ejecuta. Un EXE generado por
jpackage no es por ello esta cadena genérica.
Fuentes: [ExePackage](https://docs.firegiant.com/wix/schema/wxs/exepackage/),
[mapeo de retornos](https://docs.firegiant.com/wix/schema/wxs/exitcode/).

### Consentimiento y resultados separados

- UI: oferta explícita de instalar el componente compartido si falta, explicando
  privilegios, conservación al desinstalar MACE y posible reinicio; permitir
  continuar sin él. Recomendación: casilla sin seleccionar hasta consentimiento.
- Silencioso: consentimiento mediante opción **propia de MACE**, propuesta
  ACCEPT_PAWNIO=1; sintaxis definitiva y validación en 3.2. No es argumento de
  PawnIO ni comando ya disponible. Sin consentimiento: instalar MACE y omitir driver.
- MSI falla: devolver su error y aplicar rollback de MACE, sin iniciar PawnIO.
- MSI confirmado + PawnIO error/omisión: MACE queda instalado; resultado global
  de instalación de MACE correcto y resumen `pawnio=FAILED/SKIPPED`, sin afirmar
  éxito del driver. CPU será N/D si realmente no hay proveedor; no se fabrican
  métricas ni se altera la política de validez para coincidir con el instalador.
- 3010 de PawnIO: éxito con reinicio pendiente, señal propia según sección 5 y
  resultado global de reinicio requerido. **3010 solo del MSI no crea señal PawnIO**.
  Otros errores del MSI prevalecen sobre un éxito parcial. No se toma un HRESULT
  del arnés como código del paquete ni un código del wrapper como retorno real del driver.

Los códigos de Windows Installer se documentan en
[Microsoft](https://learn.microsoft.com/en-us/windows/win32/msi/error-codes).
El comportamiento final del bundle y su modo silencioso siguen NO VERIFICADOS.

## 4. Política concreta de PawnIO

### Evidencia del binario intacto

Se leyeron versión, bytes de texto visibles y firma de
`packaging/dist/MACE/app/prerequisites/PawnIO_setup.exe`, **sin ejecutarlo,
descomprimirlo ni extraer su driver**:

| Comprobación local | Resultado |
|---|---|
| FileVersion | 2.2.0.0 |
| Hash frente a packaging/pawnio-2.2.0.json | Coincide, SHA-256 fijado 1f519a22… |
| Authenticode | Valid, nombre simple del firmante namazso.eu; esto no sustituye verificar cadena/identidad completa al ejecutar |
| Argumentos en texto del binario | -install y -silent presentes; uso y descripción silent visibles |
| Texto de redistribución | Permite redistribuir este instalador sin modificarlo; conservarlo íntegro no resuelve todas las obligaciones del driver/módulos |
| Literal decimal 3010 | No aparece como texto; no se deduce de strings ni se afirma haber observado un retorno |

Origen fijado: URL exacta de [release 2.2.0](https://github.com/namazso/PawnIO.Setup/releases/tag/2.2.0)
y archivo indicado en [la política](../packaging/pawnio-2.2.0.json). Las notas
oficiales confirman códigos DOS para CLI y reinicio requerido en modo silencioso.
No se añade -unrestricted, -debuginfo, -uninstall ni flags supuestos para impedir
reinicio; -install -silent es la combinación estática comprobada. Ausencia de
reinicio espontáneo y los retornos reales deben probarse fuera del host.

### Detección sin inventar claves de terceros

El helper actual, PawnIoStatus.Read(), **solo consulta SCM**: PawnIO ausente,
detenido/ejecutándose/desconocido. No consulta versión. Para el instalador se
propone OpenService/QueryServiceStatusEx y QueryServiceConfigW, obtener el archivo
de driver configurado, resolver su ruta con reglas de Windows y leer el recurso
de versión, tipo de servicio y firma/catálogo cuando corresponda. No inventar
Uninstall/DisplayVersion de PawnIO ni depender de un nombre de usuario.
Configuración y archivo pueden diferir del driver ya cargado; no llamar a ese
recurso PE una prueba de la versión cargada. Si no puede acreditarse, UNKNOWN.
Fuente: [QueryServiceConfigW](https://learn.microsoft.com/en-us/windows/win32/api/winsvc/nf-winsvc-queryserviceconfigw).

| Detección | Política propuesta |
|---|---|
| Ausente en SCM y sin restos identificados de instalación/DriverStore | Ofrecer 2.2.0 con consentimiento; verificar otra vez justo antes de iniciar |
| 2.2.0.0 acreditada | Única versión inicialmente admitida por evidencia de este conjunto; no reinstalar, detener ni reparar PawnIO |
| Compatible pero detenido | Respetar instalación; no confundir con 3010, no reinstalar para intentar arrancarlo |
| Versión anterior | No sustituir automáticamente un componente compartido. Ofrecer actualización solo con aprobación específica y matriz previa; 2.1→2.2 admitido por upstream no equivale a prueba propia |
| Versión posterior | No degradar ni asumir compatibilidad por comparación >=; conservar y advertir soporte no verificado |
| Acceso denegado, versión desconocida, configuración dudosa o restos | No inferir ausencia ni instalar encima; MACE disponible con aviso de requisito no comprobado |

La lista compatible comienza con **exactamente 2.2.0.0**, ampliable por revisión
y pruebas, no por variables de entorno. Identidad del paquete y estado de SCM
son cosas diferentes de temperatura/potencia válidas. Una versión conservada
no comprobada puede funcionar con el producto actual; el instalador no puede
prometer que forzará N/D sin un cambio de producto adicional, que no se propone.

### Validación inmediata y espera máxima

Inmediatamente antes de ejecutar: ruta final protegida, archivo ordinario sin
redirecciones no autorizadas, hash exacto fijado, WinVerifyTrust con política
Authenticode, cadena/firmante esperado y verificación del sello aplicable. Retener
handle sin compartir escritura/borrado durante comprobación y lanzamiento para
reducir TOCTOU; documentar límite frente a administrador que controla el sistema.
Firma o estado de revocación no verificable: **no ejecutar**; MACE permanece.
No basta con aceptar cualquier certificado que diga el mismo nombre simple.
Permitir comprobar confianza antes con red; no prometer que toda llamada de red
del verificador puede interrumpirse a un plazo exacto del SO.

**Propuesta de máximo de espera: 180 s**, sin reintentos automáticos. 0 y 3010
solo se registran si se observó la salida real; cualquier otro retorno se
conserva como error numérico del driver, sin reinterpretar 183 u otro como éxito
por referencias de terceros. Después de 0, volver a consultar instalación/SCM;
0 sin instalación corroborable no acredita requisito listo.

**Conflicto a aprobar:** vencido el plazo, terminar a la fuerza un instalador
de driver puede dejar el sistema a medias. Se recomienda **no matarlo**: cerrar
la espera del coordinador con TIMEOUT/resultado indeterminado, sin señal 3010,
sin nuevo intento, y MACE ya instalado. El proceso PawnIO podría seguir vivo
y terminar después; no hay garantía de tiempo máximo de ejecución del driver,
ni se capturará un 3010 posterior al abandono. No dejar un monitor elevado
propio oculto para esperar indefinidamente. Esta limitación y la política de
recuperación manual deben aceptarse o rediseñarse **antes de 3.2**; no se ocultan
como una instalación abortada o completada. El host actual no se usa para probarlo.

### Logs propios y privacidad

Log estructurado con whitelist de campos: fase, token elevado sí/no, versión
del paquete, validación de hash/firma sí/no, resultado de detección por enum,
código numérico con dominio MSI/PawnIO/coordinador, duración y degradado sí/no.
No guardar rutas, comandos crudos, stdout/stderr, nombres, SID, GUID de arranque
ni hashes completos. Propuesta: ProgramData/MACE/InstallerLogs, ACL administrativa,
retención máxima 10 archivos/1 MiB por archivo; revisar política con el usuario.

Burn/MSI pueden producir logs predeterminados con rutas. Desactivar el logging
predeterminado controlable de Burn y no pedir logs MSI verbosos; capturar solo
indicadores propios. `Log.Disable` no impide que alguien solicite -l. Políticas
Windows/Defender/WER pueden generar logs independientes: **no se promete que
todo registro del SO esté saneado**. Antes de compartir cualquier log externo
hay que filtrarlo, no copiarlo al informe ni al paquete.
Fuente: [Log de Burn](https://docs.firegiant.com/wix/schema/wxs/log/).

## 5. Contrato 3010 propuesto, no implementado

### Clave propia, escritor, lector y caducidad

No es una clave de PawnIO descubierta: **es un espacio nuevo propuesto de MACE**,
vista HKLM de 64 bits. Parent persistente protegido:
`SOFTWARE\MACE\Installer\BootState`. Subclave por sesión de arranque:
`<BootIdentifier>`, creada con REG_OPTION_VOLATILE y clase `MACE.RebootSignal.v1`.
Solo el coordinador elevado escribe **después** de observar retorno 3010 del
PawnIO original previamente verificado. MSI/BA no escriben un 3010 inferido.

Propuesta de un solo valor para evitar una lectura entre varias escrituras:

| Valor / formato | Esquema propuesto |
|---|---|
| Nombre | Pending |
| Tipo y longitud | REG_BINARY, exactamente 32 bytes, little-endian salvo representación GUID acordada |
| Bytes 0–3 | Magic ASCII MRP1 |
| Bytes 4–7 | Versión de esquema u32 = 1 |
| Bytes 8–11 | Resultado real de PawnIO u32 = 3010 |
| Bytes 12–27 | GUID de la sesión de arranque, 16 bytes; orden Windows GUID fijado en los tests del contrato |
| Bytes 28–31 | Reservado u32 = 0 |

Esto **no amplía el wire privado 2** ni una estructura V2. Nombres, magic y
layout son una propuesta de contrato del instalador, pendientes de aprobación.
El escritor exige REG_CREATED_NEW_KEY para nueva publicación; el flag volatile
se ignora si la clave ya existía. Una existente solo se acepta como señal ya
publicada si todos sus campos/propiedad/ACL/arranque son válidos; se conserva,
no se convierte una clave persistente desconocida en volátil ni se repara a ciegas.

ACL protegida, sin heredar permisos de escritura de terceros: propietario
BUILTIN\Administrators o SYSTEM, SY/BA control completo y usuarios autenticados
solo lectura (query/read-control/enumeración necesaria). Sin WRITE_DAC,
WRITE_OWNER, DELETE, SET_VALUE o CREATE_SUB_KEY para usuarios normales. Se valida
también el parent para evitar sustitución. Lector abre solo QUERY/READ_CONTROL,
vista de 64 bits, contrasta propietario/DACL/tipo/tamaño/versión/arranque. No
usa HKCU, no registra identidades ni considera una ACL prueba de un administrador
honesto. No se pretende resistir al administrador que sustituye la instalación.
Fuentes: [RegCreateKeyEx](https://learn.microsoft.com/en-us/windows/win32/api/winreg/nf-winreg-regcreatekeyexw),
[seguridad del registro](https://learn.microsoft.com/en-us/windows/win32/sysinfo/registry-key-security-and-access-rights).

La garantía documentada es descarga del hive, no «cualquier apagado»:
REG_OPTION_VOLATILE en HKLM se pierde en un cierre completo. Reiniciar Windows
es el procedimiento recomendado; suspensión/hibernación no limpian esta señal,
e Inicio rápido debe comprobarse, no equipararse a reinicio real. No borrar por
primera lectura, inicio de MACE, reparación, resultado 0 posterior o error:
todos los usuarios necesitan ver un 3010 todavía pendiente en ese arranque.

### Señales antiguas y límite de API que requiere decisión

Solo ACL + nombre + un DWORD no permite probar desde Win32 que una clave
preexistente se creó volátil. No se recomienda KeyFlagsInformation: Microsoft
lo reserva al sistema. Timestamp de reloj civil, uptime o un GUID aleatorio del
instalador tampoco prueban por sí solos el arranque actual.

Para rechazar también una clave persistente antigua se propone vincular nombre
y payload al **BootIdentifier actual**. System Informer documenta la estructura
SystemBootEnvironmentInformation/BootIdentifier; no es una garantía de API
pública de Microsoft. NtQuerySystemInformation advierte que puede cambiar.
**NO VERIFICADO:** esta clase concreta y su estabilidad al reiniciar/hibernar/
Inicio rápido en las versiones soportadas. No se invocó en este paso.
Fuentes: [enumeración reservada](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/content/wdm/ne-wdm-_key_information_class),
[aviso de Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winternl/nf-winternl-ntquerysysteminformation),
[definición primaria de phnt](https://github.com/winsiderss/systeminformer/blob/master/phnt/include/ntexapi.h).

**Recomendación condicionada:** aprobar una lectura defensiva de esa identidad
mediante enlace dinámico, con estructura/longitud verificadas y tests en Windows
10/11. Si no está disponible o no supera validación, no publicar/consumir señal:
continuar telemetría normal, sin estado 12 inventado. Nada de fallback a clave
sin vínculo de arranque. Si no se acepta esta dependencia, **el consumo 3.3
queda bloqueado hasta aprobar otra fuente fiable de identidad/caducidad**;
no se afirma que el contrato esté resuelto solo con REG_OPTION_VOLATILE.

### Tabla de consumo y cambio mínimo de producto

| Entrada | Resultado propuesto |
|---|---|
| Señal ausente, tipo/versión/tamaño/ACL inválidos, BootIdentifier distinto o consulta imposible | No aviso 3010; métricas y errores originales se conservan |
| Resultado 0 o error sin señal válida anterior | No crear aviso; no inferir reinicio pendiente |
| 0/error posterior con 3010 válido en el mismo arranque | Mantener aviso anterior; no borrar una necesidad todavía pendiente |
| Señal 3010 válida y app ya abierta | Mostrar mensaje en aviso separado, tras siguiente lectura de contexto; no esperar a reiniciar MACE |
| PawnIO detenido sin señal | Código 11 y su mensaje, nunca 12 |
| Señal válida + otra causa de fallo | Mostrar aviso informativo además de la causa actual; no sustituir confianza/contención/elevación/parada/caducidad ni lecturas válidas |
| Reinicio real | Clave volátil ausente; además identidad de arranque distinta rechaza cualquier resto persistente |

**Recomendación de mínimo impacto: consumidor en Java, solo aviso de instalación**.
Reutilizar `MetricState.REBOOT_REQUIRED` y el texto ya existente en
`MetricMessages.message()` para un aviso del dashboard; no transformar una
lectura VALID en N/D, no fabricar métricas V2 ni ocultar otra explicación.
El texto exacto es:

> Para habilitar los sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando

No se está afirmando que la DLL actual emita 12 al leer el registro: **no lo
lee**. Si el usuario exige 12 dentro del registro CPU V2 para todo consumidor,
es una alternativa diferente, con cambio C/reconstrucción/hash nuevos y regla
de precedencia por aprobar; no basta con cambiar la UI ni debe introducir I/O
de registro en CP_Poll*. El aviso Java separado evita ese cambio de semántica.

Futuro diff mínimo, a presentar **antes de aplicarlo en 3.3**:

- Nuevo adaptador Java de solo lectura Win32/FFM y contrato de prueba inyectable,
  sin env para activar el resultado en Production. Validación y fuente de
  identidad de arranque separadas para simular sin tocar HKLM del host.
- Conexión en InfrastructureAdapters y propiedad/aviso en MainDashboardViewModel
  y su vista; reutilizar MetricMessages. Sin cambiar FfmSensorsAdapter.pollCpu,
  V2Records, snapshot físico, historial, HUD o reglas de validez.
- Lectura inicial y actualización propuesta cada **2 s**, fuera del hilo JavaFX
  y de los sondeos; un solo trabajador daemon, sin cola creciente. Si se atasca
  una consulta, aviso se invalida al vencer plazo propuesto de 2 s; no crear
  trabajadores sin límite ni bloquear el cierre. Revisar lifetimes FFM en el diff.
- Pruebas: 3010/0/error/ausente/ACL/datos parciales/antiguos, otra causa visible,
  métricas intactas, UI no bloqueada y cierre; escritor con stub aislado. DLL,
  helper, protocolo y política 4A permanecen byte a byte fuera de ese cambio.

Este aviso es **una propuesta de presentación**, no implementación aprobada.
Resolver esta elección frente a la alternativa de estado V2 nativo antes de
3.3. No se entrega un diff aplicado ni se modifica una clave ahora.

## 6. Ubicación, permisos y datos

Recomendación: `%ProgramFiles%\MACE` x64, sin selector de carpeta para la primera
release. MSI verifica destino sin junction/reparse no autorizado y ACL efectiva:
Administradores/SYSTEM escritura, usuarios lectura/ejecución; ningún grupo de
usuario con Modify/WRITE_DAC/WRITE_OWNER/DELETE en raíz o ancestros que permita
sustituir archivos. No configurar Everyone:FullControl para solucionar errores.
La elevación actual del launcher se conserva; no lanzar MACE automáticamente
como SYSTEM desde instalación silenciosa ni instalar un servicio de sensores.

Mover la app a una carpeta escribible por usuario pierde esta protección de
origen: hashes detectan cambio relativo al pin, pero un atacante que reemplaza
JAR/DLL/CFG completos puede reemplazar también esos pins. No declarar segura esa
distribución portable por el solo hecho de ser requireAdministrator. El límite
del [Paso 2](FASE2-PASO2.md) sigue vigente.

Datos: caja negra `%LOCALAPPDATA%\MACE\blackbox`, Preview en simulated, fallback
y propiedad configurable según inventario; preferencias HUD en Java Preferences.
No moverlos a Program Files ni atribuirles atomicidad. **Conservar datos de cada
usuario al desinstalar** por defecto; no recorrer perfiles ajenos ni borrar
claves de usuarios. Un borrado opcional futuro exigiría alcance/confirmación
separados y no es condición de desinstalar MACE.

## 7. Desinstalación, actualización, reparación y rollback

| Operación | Recomendación concreta y límite |
|---|---|
| Desinstalar | Retirar solo archivos/componentes MACE que instaló MSI, su registro/atajos y cache del bundle según motor; conservar PawnIO y datos de usuario. Explicarlo en UI y resultado saneado |
| MACE/Preview abierto | Detectar por archivos/identidad de instalación, ambos MACE.exe y helper. Pedir cierre manual; no matar por nombre ni un LHM de escritorio ajeno. Silencioso: fallar sin modificar si está ocupado |
| Restart Manager | Usarlo para detectar archivos en uso, sin cerrar automáticamente apps; configurar DisableShutdown y verificarlo. La conducta por defecto, especialmente silenciosa, puede cerrar aplicaciones; no confiar en defaults |
| Actualización | Misma familia UpgradeCode, ProductCode por major upgrade, versión MSI creciente en los primeros tres campos; retirar versión vieja dentro de transacción recuperable y probar fallo intermedio |
| Reparar | Reponer bytes de MACE desde cache confiable y verificar manifiesto; no reinstalar PawnIO compatible. Avisos/consentimiento del requisito siguen política original |
| Downgrade | Bloquear automáticamente, conservar la versión más nueva y datos. Un downgrade deliberado requiere procedimiento aprobado, no AllowDowngrades indiscriminado |
| Coexistencia anterior | Una instalación MSI de la misma familia por máquina. App-images antiguas/copias manuales no se borran ni migran a ciegas; mutex por usuario/sesión limita ejecuciones, no la presencia de archivos |
| Rollback | MSI revierte sus cambios si falla antes de confirmar. Después de confirmar, fallo de PawnIO es no vital, conserva MACE; nunca desinstalar PawnIO como compensación |

Fuentes: [Restart Manager con MSI](https://learn.microsoft.com/en-us/windows/win32/msi/using-windows-installer-with-restart-manager),
[DisableShutdown](https://learn.microsoft.com/en-us/windows/win32/msi/msirestartmanagercontrol),
[MajorUpgrade](https://docs.firegiant.com/wix/schema/wxs/majorupgrade/).
No ejecutar RmShutdown ni prometer que un proceso elevado reconocerá el cierre
correctamente sin las pruebas de la laptop. La captura EndTaskWindow y la
persistencia tras cierres forzados siguen pendientes de Fase 2.

Datos reales de construcción: package-windows.bat fija APP_VERSION=1.0.0 y
APP_UPGRADE_UUID=7f3c2a1e-9b4d-4c6e-8a1f-2d5e6b7c8d90. Se recomienda conservar
esa familia si se adopta MSI directo, comprobar ausencia de colisiones y usar
1.0.0 para el primer instalador, incrementando versión al actualizar. El JAR
sigue 1.0.0-SNAPSHOT; no confundir esa versión de fuente con prueba de una
instalación anterior ni cambiar versiones del producto en este diseño.

## 8. Cadena de firma y release

Orden propuesto, en **una nueva release**, nunca sobre packaging/dist/MACE de 5.1:

1. Compilar helper y firmar EXE/DLL propios; validar firma y sello de tiempo.
2. Generar/verificar manifiesto del helper con Windows PowerShell 5.1, después
   de todas esas firmas; fijar el hash final en la DLL.
3. Compilar/firmar DLL; validar antes de calcular su hash y fijarlo en Java/CFG.
4. Compilar Java Production y preparar imagen nueva; incrustar primero el UAC
   existente y después firmar launchers propios. No modificar después un PE firmado.
5. Verificar payload/whitelist, calcular inventario final y construir MSI;
   firmar MSI. Firmar también coordinador/BA propios antes de incluirlos.
6. Construir bundle con esos paquetes; firmar motor Burn, reinsertarlo y firmar
   EXE completo según procedimiento del toolset. Verificar firmas/pins del
   contenido y nuevo inventario tras firmar, sin recalcular pins a bytes erróneos.

PawnIO, bibliotecas/runtime de terceros y sus firmas originales se conservan.
Extraer el motor propio del bundle para firmarlo **no autoriza extraer PawnIO**.
Refirmar helper cambia su manifiesto y obliga a recompilar DLL/pins; refirmar
DLL cambia el pin del launcher; firmar launchers/MSI/bundle cambia sus hashes
externos. Firma SHA-256 y timestamp RFC3161 SHA-256, proveedor por elegir;
verificación de cadena, política y sello, no solo presencia de certificado.
Fuentes: [firma Burn](https://docs.firegiant.com/wix/tools/signing/),
[SignTool](https://learn.microsoft.com/en-us/windows/win32/seccrypto/signtool).

Sin certificado: hook omitido con aviso explícito y etiqueta de build no firmado;
pruebas locales permitidas, no simular editor verificado. Mantener revisión de
publicación pendiente. Opciones sin pagos mensuales: solicitar programa gratuito
OSS tras decidir licencia/CI, o certificado con pago anticipado por vigencia
limitada, verificando condiciones/custodia; no existe promesa de certificado
perpetuo. Autofirma solo vale para pruebas con confianza instalada expresamente,
no equivale a confianza pública. La fundación [SignPath](https://signpath.org/)
ofrece firma gratuita para OSS, sujeta a admisión; no se solicita ni envía nada.
Firma válida tampoco garantiza ausencia de avisos SmartScreen/Defender.

## 9. Inventario real y plan de licencias

Se inspeccionaron el payload real, Mace.Lhm.Helper.deps.json, lock/nuspec de
paquetes restaurados, avisos ya incluidos, JAR y sus metadatos y los archivos
legal del runtime. Tabla inicial, **no permiso legal de distribución**:

| Componente realmente presente | Versión / términos observados | Evidencia y tarea pendiente |
|---|---|---|
| MACE Java/DLL/helper | JAR 1.0.0-SNAPSHOT; DLL 1.5.0; helper 1.1.1; licencia propia pendiente | No añadir LICENSE sin decisión; verificar titularidad/contribuciones |
| LibreHardwareMonitorLib | 0.9.6, MPL-2.0, copyright LibreHardwareMonitor | Nuspec, deps y aviso; commit fuente 3d331e3370efb858411f19511373eff65a218701, fijar textos/fuente correspondiente |
| BlackSharp.Core / DiskInfoToolkit | 1.0.7 / 1.1.2, MPL-2.0, Florian K. | Deps/nuspec/avisos con revisiones exactas; conservar copyright y fuentes |
| RAMSPDToolkit-NDD | 1.4.2, MPL-2.0, Florian K. | Mismas fuentes/avisos; excepción binaria de compatibilidad no cambia licencia ni autoriza otros proveedores |
| HidSharp | 2.6.4, **Apache-2.0**, copyright 2010–2025 James F. Bellinger | Texto real third-party/HidSharp-LICENSE.txt; no quedarse con el valor metadata LICENSE.txt |
| Mono.Posix.NETStandard | 1.0.0; licencia no declarada en nuspec/aviso actual | Presente en deps, no inventar MIT por el autor Microsoft. Fuente/texto exactos NO VERIFICADOS; bloquea revisión legal de release |
| System.CodeDom / System.Management | 10.0.2, MIT, Microsoft | Deps/nuspec/avisos, revisiones en notice existente |
| System.IO.Ports | 10.0.3, MIT, Microsoft | Deps/nuspec; incluir avisos transitivos del contenido realmente seleccionado |
| Runtime .NET win-x64 | 10.0.12, MIT + avisos de terceros, Microsoft | Runtimepack en deps, LICENSE/THIRD-PARTY-NOTICES incluidos; no equiparar toda DLL System.* a un paquete NuGet separado |
| System.IO.FileSystem.AccessControl | Paquete 5.0.0 en grafo; DLL presente del runtime resuelto | No duplicar licencia/versión 5.0.0 como si fuera el asset seleccionado; cotejar deps/runtimepack y recurso real |
| PawnIO_setup.exe | 2.2.0, binario intacto/firma namazso.eu; texto permite redistribución sin modificar | Términos del instalador separados de licencia del código de driver; no extraerlo para reconstruir inventario |
| Código PawnIO | Upstream declara GPL-2.0-or-later **con excepción específica IOCTL/LGPL**, copyright namazso | Correspondencia exacta del source con driver firmado de este setup NO VERIFICADA; no decir que toda la app pasa automáticamente a GPL |
| Módulos PawnIO usados por LHM | Upstream tiene COPYING LGPL-2.1 y cabeceras LGPL-2.1-or-later | Enumerar recursos realmente incorporados a LHM y mapear commit/binarios/fuentes, sin ejecutar módulos; versión/conjunto exacto NO VERIFICADO |
| Runtime Java de la imagen | Oracle JDK 22.0.2 derivado con jlink; **NFTC** en runtime/legal/java.base/LICENSE | No llamarlo GPL por ser Java. COPYRIGHT/avisos por módulo presentes; revisar términos del runtime exacto |
| JavaFX | 22.0.2, GPLv2+CE en POM padre real | Ocho JAR (base/graphics/controls/fxml, general y win); buscar avisos/textos correspondientes, no darlos por cubiertos por el legal de java.base |
| AtlantaFX | 2.0.1, MIT en fuente etiquetada, copyright mkpaz | JAR incluye POM hijo; licencia no estaba en ese nodo. Correlacionar parent/tag y conservar texto en release |
| NVML/IGCL | Runtime aportado por driver gráfico, no DLL copiada en la imagen | Cabecera IGCL/aviso local y condiciones de redistribución de interfaces; no inventariar un SDK completo como payload |
| Futuro motor/coordinador del instalador | WiX/BA y código MACE, todavía no presentes en imagen | Añadir licencia/notices/runtime nativo que realmente se distribuya después de escoger herramienta |

La política [NDD](../packaging/compatibility-references.json) sigue limitada a
app/lhm/RAMSPDToolkit-NDD.dll, su hash y offsets 105819/127562. No ampliar lista
de excepciones al crear el MSI o un bundle comprimido; el verificador futuro
debe revisar también el contenido antes de empaquetar y la tabla de archivos.

**Procedimiento 3.7:** inventario por cada ruta realmente distribuida → archivo/
recurso a paquete y versión → licencia/copyright/fuente/revisión exactos → textos
literales/obligaciones → cobertura completa del payload y del instalador nuevo.
Usar deps/lock/manifest para runtime efectivo, nuspec/paquete para licencia,
POM padre/JAR para Java, legal por módulo del JDK. No copiar todo el lock:
incluye paquetes de otras plataformas que no están en Windows. Lo desconocido
se etiqueta pendiente; nunca inferir licencia por nombre de assembly.
Actualizar notices solo en 3.7 aprobado; no reemplazarlos ahora ni presentar
este inventario como revisión terminada.

### Compatibilidad preliminar y licencia propia

Opciones para el código propio, todas gratuitas sin adquirir una licencia:

| Opción | Recomendación / coste de obligaciones |
|---|---|
| MPL-2.0 | Recomendada si se quiere publicar modificaciones de archivos propios manteniendo combinación con componentes separados; no relicencia automáticamente dependencias |
| Apache-2.0 | Alternativa permisiva con cláusulas de patentes y avisos; facilita reutilización, sin obligación general de publicar modificaciones propias |
| MIT | Alternativa permisiva breve; conservar copyright/licencia, sin la concesión de patentes explícita de Apache |
| GPL-3.0-or-later | Si se busca copyleft más amplio; revisar combinación, excepciones y todas las dependencias antes de elegir; no afirmar compatibilidad universal |

MPL exige disponibilizar fuente de los archivos cubiertos y sus cambios, avisos
y forma de obtener fuente correspondiente; un enlace genérico a master no
sustituye identificar la versión distribuida. JavaFX necesita su licencia y
excepción correspondientes; PawnIO/módulos requieren revisión de su excepción
y fuentes, aunque se comuniquen por otro proceso. Separar procesos no es una
garantía automática de ausencia de obligaciones. Este análisis técnico es
preliminar, **no asesoría legal ni una licencia aplicada a MACE**.
Fuentes: [MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/),
[texto MPL](https://www.mozilla.org/en-US/MPL/2.0/),
[Apache](https://www.apache.org/licenses/LICENSE-2.0),
[PawnIO y excepción](https://github.com/namazso/PawnIO),
[COPYING módulos](https://github.com/namazso/PawnIO.Modules/blob/main/COPYING),
[cabecera de módulo](https://github.com/namazso/PawnIO.Modules/blob/main/IntelMSR.p),
[AtlantaFX 2.0.1](https://github.com/mkpaz/atlantafx/blob/v2.0.1/LICENSE).

**Runtime Java: decisión adicional.** El legal local es NFTC, cuyo texto/FAQ
permite runtime jlink bajo condiciones, incluida redistribución sin cobro.
No significa «prohibido redistribuir», pero tampoco un runtime libre GPL por
defecto. Recomendar evaluar una distribución OpenJDK con licencia libre para
release de MACE libre; proveedor/versión/suministro/soporte requieren aprobación
y nueva imagen/regresión, sin cambiarla ahora. JDK 22 es una versión antigua:
compatibilidad de pruebas no equivale a mantenimiento de seguridad vigente.
Fuentes: [FAQ Oracle](https://www.oracle.com/java/technologies/javase/jdk-faqs.html),
[NFTC](https://www.oracle.com/downloads/licenses/no-fee-license.html).

## 10. Plan de verificación en laptop prestada

**Disponibilidad, Windows 11 real, Intel/NVIDIA, HVCI activo y snapshot limpio:
NO VERIFICADO en esta entrega**. El usuario aporta/restaura el otro equipo.
Una laptop física no tiene por sí sola snapshots de VM: antes de modificarla
exigir permiso del propietario, respaldo/imagen de disco con restauración
probada o VM Windows con instantánea. Una VM no prueba el hardware GPU físico
ni hereda automáticamente el HVCI del host. Un punto de restauración no se
considera garantía de rollback de driver/DriverStore.

En 3.4 se entregarán guía/scripts de inventario **solo lectura**; en 3.8 comandos
exactos del instalador ya construido y fichas. No inventar hoy un comando de
MSI/EXE que todavía no existe ni pedir instalar herramientas al usuario final.

| Orden / entorno | Caso y evidencia esperada | Qué no demuestra |
|---|---|---|
| 0. Otra máquina antes de tocarla | Confirmar permiso/restauración, Windows, token, HVCI real, PawnIO ausente en SCM y DriverStore, ausencia de .NET extra, hashes del conjunto transportado | Consulta de sensores no prueba HVCI; una consulta SCM sola no demuestra DriverStore limpio |
| 1. Stubs aislados en desarrollo | Detección/versión, consentimiento, hash/firma inválidos, 0/3010/error/timeout; solo paquetes ficticios y store de señal en memoria | Ninguna instalación real o compatibilidad HVCI |
| 2. Snapshot limpio, instalación inicial | EXE sin SDKs adicionales, MSI instalado, PawnIO ausente→instalado silencioso con HVCI, CPU válida donde soporte | 0 por sí solo no prueba driver cargado ni cifras válidas |
| 3. Snapshot limpio alterno | Omitir/fallar PawnIO y abrir MACE degradado con motivo real, UI responde y GPU/uso CPU/RAM disponibles | No forzar N/D de métricas independientes |
| 4. Con MACE ya leyendo sin PawnIO | Instalar componente autorizado, observar recuperación caliente, PID/estado/UI, sin relanzar para ocultar fallo | Reiniciar MACE manualmente no prueba recuperación caliente |
| 5. 3010 simulado y real separado | Stub 3010 con esquema/ACL/boot, señal inválida/antigua no aceptada; si aparece 3010 real, registrar y comprobar mensaje; reiniciar y comprobar caducidad | Stub nunca se declara 3010 físico. Si no se logra real, NO VERIFICADO |
| 6. Energía | Suspensión/hibernación/Inicio rápido frente a Reiniciar; contexto de arranque y UI inmediata/frescura | No modificar HVCI para fabricar resultado; no extrapolar resume a reinicio |
| 7. Actualizar/reparar/downgrade | Versión anterior controlada, rechazo downgrade, fallo parcial/rollback y datos preservados | No asumir compatibilidad por pasar primera instalación |
| 8. MACE abierto | Ambos MACE.exe/Preview/helper, detección y petición de cierre manual; silencioso ocupado sin cambios | No matar globalmente procesos ni LHM ajeno |
| 9. Desinstalar | MACE retirado, PawnIO permanece y datos del usuario conservados; otras apps no tocadas | No desinstalar driver para volver al baseline; restaurar snapshot/imagen preparada |
| 10. Firma y SO | Registrar comportamiento Defender/SmartScreen con build firmado y no firmado, sin eludir avisos | Firma no es certificación antivirus ni HVCI |
| 11. Cierre | Estado final saneado, versión/resultados, restauración de máquina acordada; regresión completa normal/admin por usuario donde corresponda | No atribuir al host del agente verificaciones de laptop |

## 11. Riesgos, límites y decisiones necesarias

Riesgos centrales: toolchain antiguo frente a condiciones WiX actual; driver
compartido con resultados no transaccionales; timeout sin poder garantizar fin
del driver; señal antigua/volatilidad y API de boot no pública estable; logging
Windows externo; firma/redistribución/Mono pendiente; runtime Java NFTC/antiguo;
datos no atómicos ante halt; deuda de frescura UI/EndTask de Fase 2. No se
eliminan mediante una tabla de diseño ni una simulación.

| ID | Estado vigente confirmado el 2026-11-23; decisiones de 3.1c prioritarias |
|---|---|
| D1 | **ELEGIDO Inno Setup**. Reemplaza la propuesta WiX/MSI + Burn; su encaje con SignPath sigue pendiente (C2). No se cambia a otra herramienta ni se instala tooling por esta actualización documental |
| D2 | **APROBADA**: completar instalación de MACE primero, PawnIO después; consentimiento explícito UI/silencioso; lista inicial exacta 2.2.0.0; conservar otras versiones sin actualización automática. La referencia anterior a MSI se adapta a la elección de Inno, sin prometer una transacción global del driver |
| D3 | **APROBADA**: espera máxima 180 s sin matar el instalador de PawnIO; resultado indeterminado, sin señal 3010 ni segundo intento. Limitación y recuperación manual en la adenda |
| D4 | **APROBADA opción (a)**: clave volátil, ACL/propietario y validación estricta, sin BootIdentifier, conservando un valor de 32 B y el límite documentado sobre claves persistentes falsas privilegiadas. Concretar/revisar ruta, campos y consumo antes de su diff; no leer/escribir ahora la señal |
| D5 | **APROBADO el diseño** de aviso Java separado con MetricState.REBOOT_REQUIRED, sin cambiar V2/DLL/helper/protocolo/política 4A. El diff mínimo de implementación se presenta aparte y espera aprobación en 3.4 |
| D6 | **APROBADA en lo independiente de MSI**: Program Files x64 fijo, datos/PawnIO conservados, cierre manual sin kill, bloqueo downgrade y retención de logs indicada. Familia MSI/rollback/reparación MSI dejan de ser requisitos operativos; definir equivalentes de Inno en 3.3/3.5, sin asumirlos implementados |
| D7 | **MIT ELEGIDA** para código propio. Confirmado que permite derivados cerrados; titular declarado y alias público autorizado: **DanielDdeY**. Crear LICENSE en Paso B tras aprobación de A; no relicenciar terceros. Migración JDK aprobada como cambio separado; distribución/build/JavaFX por concretar |
| D8 | **SignPath Foundation ELEGIDO**, programa gratuito; no certificados de pago. Solicitud por preparar/no presentada, aceptación NO VERIFICADA; builds de prueba sin firma propia y con aviso. Solicitud y publicación las hace el usuario, sin envíos, cuentas ni push por el agente |
| D9 | VM Hyper-V/otro físico, permiso y respaldo con restauración probada: **NO VERIFICADOS**, campos sin completar. Criterio provisional: instalación en VM limpia con instantánea; físico prestado solo sensores con permiso, respaldo probado y aprobación específica |

Las elecciones anteriores no se vuelven a pedir. Elegir SignPath no equivale
a ser admitido, elegir MIT no aplica retrospectivamente una licencia a terceros
ni crea todavía LICENSE, y elegir Inno no resuelve su elegibilidad. La secuencia
de 3.1b conserva 3.2 guía de VM, 3.3 instalador y 3.4 señal; la equivalencia con
el prompt original está en el registro actual. Antes de avanzar se consolida
3.1c y después se entrega **solo A**. Se mantiene autorización para cierres de
procesos desechables propios del arnés; no para procesos ajenos. El usuario
asigna a **DanielDdeY** autoría, revisión y aprobación de firma; contacto público
por GitHub Issues. Confirma 2FA en GitHub/Gmail; MFA de la futura cuenta SignPath
NO VERIFICADA. El alias/roles públicos autorizados son excepción limitada al
saneado; el correo alternativo solo se usará si el formulario lo exige, sin
publicarlo ni enviarlo ahora. Nada autoriza instalar, firmar o publicar ahora.

## 12. Evidencias de este paso, cambio y parada

Verificado por lectura: jpackage 22.0.2/opciones y mínimo WiX 3.0 de la clase
instalada; selección de dependencias/metadata; binario PawnIO 2.2.0 hash/firma
y flags de texto, sin ejecutarlo; legal NFTC y archivo real de HidSharp;
documentación oficial citada. Se revalidaron **370/370 hashes**, cero faltantes,
extras o diferencias, con Windows PowerShell 5.1 x64 y token normal; allí se
confirmó también hash coincidente y firma Valid del PawnIO intacto. No se
regenera la imagen. Enlaces locales/formato revisados y sin literales nuevos
de proveedores retirados. **NO VERIFICADO físicamente:** compilación/ejecución
MSI/Burn, detección/versiones cargadas, tiempos/retornos reales del driver,
compatibilidad HVCI/hardware/laptop, rollback/desinstalación/reparación, esquema
de registro/ACL/boot/caducidad, aviso 3010 nuevo y firma propia. Las obligaciones
legales no se dan por cerradas por estas consultas.

Único cambio versionado: **helper/FASE3-DISENO.md**. Sin scripts nuevos, producto,
imagen, ABI 1.5/DLL 1.5.0, wire 2/helper 1.1.1, política 4A o launchers modificados.
No se ejecutan instaladores, pruebas con kill, servicios, registro de señal,
HVCI, firma o compra; no se envían binarios ni se fusiona a principal. Los
inventarios/logs locales y tres TXT excluidos no entran al commit.
Solo se revisan enlaces/formato y coherencia textual; no se repite regresión
de producto por un documento. Cifras anteriores: helper 105, CTest 76/77,
Java 347 + 1 omitida, payload 8 + ambos rechazos, protegidos PASS y auditoría
181/0, con su alcance en el cierre Fase 2; **no son pruebas nuevas del 3.1**.

**DETENERSE al terminar 3.1. Esperar aprobación del diseño; no avanzar a 3.2.**
