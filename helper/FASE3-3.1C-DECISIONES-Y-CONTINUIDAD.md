# Fase 3 — registro de decisiones 3.1c y requisitos para continuar

**Actualización prioritaria, 2026-11-25:** el usuario aprueba la auditoría A
solo como base provisional, no como respaldo legal ni evidencia suficiente
para SignPath. Confirma MIT y derivados cerrados; LICENSE existe en GitHub/main,
pero no en este checkout. Se entrega [B parcial](FASE3-B-PARCIAL.md): resumen
con las 16 filas/cuatro cotejos, contribución/plantillas y cuatro propuestas.
Inno se mantiene sujeto a consulta; se prioriza proponer exclusión Mono y
migración JDK antes de 3.3. Ningún diff de producto está autorizado para aplicar.
No se finalizan avisos ni se avanza a C/D/E. Esta nota actualiza las referencias
históricas inferiores a «A sin aprobar» sin convertirla en aprobación legal.

Fecha: **2026-11-23**, America/Lima. Partida local: **cd96559**,
rama `LHM_implementado`. Al iniciar había dos documentos propios de entregas
anteriores sin seguimiento: `GUIA-RELEVO-AGENTE.md` e
`INFORME-LHM-PAWNIO-DOTNET-FIRMAS-LICENCIAS.md`, ambos bajo `helper/`.
Se conservan y no se incorporan al commit de esta actualización.

**Punto actual: 3.1c, consolidación de decisiones anterior al Paso A.**
Esta entrega registra las confirmaciones y el itinerario solicitado. No es el
inventario exhaustivo A, una solicitud de firma ni una implementación de B–E.

**Actualización posterior, 2026-11-23:** con autorización del usuario se entrega
el [Paso A: inventario y conflictos](FASE3-LICENCIA-INVENTARIO.md) para revisión.
Se conservan abajo el alcance y comprobaciones de este registro previo; sus
referencias a «A pendiente» quedan actualizadas por esta nota y la tabla de
estado. A identifica bloqueos legales/técnicos; no los resuelve por aprobación
implícita. Detenerse antes de B y 3.2.

## 1. Fuentes y prioridad de instrucciones

Se releen los adjuntos `prompt_mace_fase3.md` y `3.1c.md`, el
[diseño 3.1](FASE3-DISENO.md), su [adenda](FASE3-DISENO-ADENDA.md) y el
[cierre de Fase 2](FASE2-CIERRE.md). Las confirmaciones actuales del usuario
y las decisiones de `3.1c.md` prevalecen sobre propuestas anteriores. No se
presentan como aprobaciones que hubieran ocurrido al entregar 3.1b.

El prompt general sigue definiendo las tareas de Fase 3; 3.1c se intercala
antes de 3.2 y no las elimina. Releer la orden original «comienza por 3.0»
no reinicia el trabajo ya realizado. Los adjuntos originales de Downloads
se conservan como referencias; el estado operativo actualizado queda en el
repositorio, sin modificar aquellos archivos externos.

La numeración operativa sigue el ajuste explícito de 3.1b: **3.2 guía/VM,
3.3 instalador, 3.4 señal/consumo 3010**. El prompt general recibido aún
usa 3.2 instalador, 3.3 señal y 3.4 entorno. Este registro mantiene la
equivalencia para no confundir orden o permisos; 3.1c no dispone volver a
la numeración antigua. No se omite preparar la VM antes de instalar.

## 2. Decisiones y permisos recibidos

| Asunto | Estado vigente y alcance |
|---|---|
| MIT / C1 | **ELEGIDA y CONFIRMADA**. El usuario acepta derivados cerrados. No volver a pedir esa confirmación. LICENSE todavía no existe: su incorporación corresponde al Paso B tras aprobar A |
| Titular | **DanielDdeY**, alias GitHub declarado por el usuario. Texto previsto para el código propio: `Copyright (c) 2026 DanielDdeY`. No reasigna derechos ni elimina atribuciones de terceros |
| Equipo / derechos | Se registra la declaración de 3.1c sobre aceptación del equipo y ausencia de derechos de una organización. No es una auditoría jurídica de titularidad ni prueba independiente de acuerdos previos |
| Firma / D8 | **SignPath Foundation elegido**, programa gratuito, sin certificados de pago. Estado: solicitud por preparar, no presentada; admisión y firma NO VERIFICADAS. El usuario presentará la solicitud |
| Instalador / D1 | **Inno Setup elegido** como nueva decisión prioritaria. La propuesta WiX/MSI + Burn deja de ser selección vigente. Compatibilidad del resultado con SignPath pendiente (C2); no cambiar de herramienta automáticamente |
| Orden / D2 | Instalar MACE primero y PawnIO después. «MSI primero» se reformula por la elección de Inno. Se conservan consentimiento explícito UI/silencioso, versión compatible inicial exacta 2.2.0.0 y preservación de otras versiones |
| Timeout / D3 | Máximo 180 s de espera sin matar instalador; indeterminado, sin inventar 3010 ni segundo intento. No se cambia la política aprobada |
| Señal / D4 | **Opción (a) aprobada**, clave volátil + permisos/propietario + validación estricta, sin BootIdentifier. Se conserva valor de 32 bytes y límite documentado frente a falsificación privilegiada. Revisar concreción de ruta/campos/consumo antes del diff de implementación; no tocar registro ahora |
| Aviso / D5 | Diseño de aviso Java separado aprobado. No cambia V2/DLL/helper/protocolo/4A. Diff mínimo antes de implementar, con su aprobación separada |
| Instalación / D6 | Aprobado lo independiente de MSI: Program Files x64, datos y PawnIO conservados, cierre manual, bloqueo downgrade y retención de logs. Identidad, actualización/reparación y rollback de Inno deben diseñarse; no heredan automáticamente garantías MSI |
| JDK | Migración aprobada como **trabajo separado**. Proveedor, build concreta y pareja JavaFX aún por concretar. No se actualiza el JDK instalado ni la imagen congelada con esta entrega |
| Roles | El usuario asigna a **DanielDdeY** autoría, revisión y aprobación de firma. No inventar otros integrantes ni dar por verificado que SignPath aceptó esa concentración de roles |
| Contacto | Preferido y autorizado: [GitHub Issues de MACE](https://github.com/DanielDdeY/MACE/issues). No se promete tiempo de respuesta. Correo alternativo aportado en conversación, reservado al formulario si lo requiere: no se publica en este repositorio ni se envía ahora |
| Autenticación | **VERIFICADO POR EL USUARIO:** declara 2FA en GitHub y Gmail. No se inspeccionaron ajustes ni se recibieron secretos. MFA de una futura cuenta SignPath: **NO VERIFICADO** |
| Datos públicos | Se autoriza lo necesario para copyright y política de firma. No equivale a publicar perfiles locales, correos extraídos del historial, SID, nombres de equipo, hashes completos o logs sin sanear |
| Procesos y pruebas | Se mantiene el permiso previo para cierre de procesos desechables propios del arnés. No se autoriza cerrar aplicaciones/procesos ajenos, elevar, instalar PawnIO, tocar servicios/HVCI ni forzar paradas |

Los contratos congelados siguen siendo DLL **1.5.0**, ABI **1.5**, protocolo
privado **2**, helper **1.1.1**, estados **9–16** y política **4A**. Producto,
imagen, launchers y manifiestos permanecen intactos. No hay push, fusión a
principal, cuentas nuevas, aceptación de EULA ni envíos de artefactos.

## 3. Qué información personal falta

Para redactar copyright y los roles **ya no falta una identidad**: el usuario
eligió su alias y asignó todos los roles. No se solicita nombre legal, DNI,
domicilio, contraseña, token ni códigos de autenticación. La plantilla MIT
requiere una atribución, no establece por sí sola ese procedimiento de
identificación personal. [Texto MIT de OSI](https://opensource.org/license/mit).

SignPath Foundation anuncia que verifica procedencia del repositorio sin
identificación personal del tipo de una CA tradicional. Eso no demuestra la
admisión de MACE ni anticipa todos los datos de su formulario.
[Descripción oficial](https://signpath.org/).

Lo pendiente es **operativo**: confirmar qué datos pide el formulario real
cuando el usuario lo presente, configurar MFA de SignPath y verificar los
permisos de revisión/aprobación. Si exige contacto por correo, el usuario ya
aportó uno para ese fin; no se necesita pedirlo otra vez. La publicación de
copyright/roles en documentación no autoriza al agente a presentar la solicitud.

El titular del copyright propio y el titular del certificado son cuestiones
distintas: en el programa Foundation el certificado lo emite la fundación a
su nombre; seleccionar DanielDdeY para copyright no promete un certificado
personal con ese alias. Véanse las [condiciones del programa](https://signpath.org/terms).

## 4. En qué punto está cada paso

| Paso | Estado y siguiente entrega |
|---|---|
| 3.0 | Entregado, commit `ee15c3b`; no reiniciarlo por releer el prompt |
| 3.1 | Diseño entregado, `73b0548`; sus decisiones se actualizan aquí |
| 3.1b | Adenda entregada, `cd96559`; las elecciones actuales superan sus pendientes de elección |
| 3.1c, preparación | **Esta entrega:** registro de decisiones, conflictos y continuidad |
| 3.1c A | **ENTREGADO PARA REVISIÓN**: inventario técnico, licencias acreditadas y NO VERIFICADO, C1–C10 y preguntas a SignPath; esperar aprobación |
| 3.1c B | Pendiente de aprobación de A. C1 ya está confirmado; crear LICENSE MIT, preparar metadatos, avisos y contribuciones con sus gates de producto |
| 3.1c C | Pendiente de su aprobación y de los borradores; política/README verificados, roles y privacidad, sin afirmar firma/admisión |
| 3.1c D | Pendiente: diseño CI multietapa, sin crear ni ejecutar el workflow de firma |
| 3.1c E | Pendiente: auditoría del repositorio ya público, clon limpio y dossier; sin enviar solicitud |
| 3.2 | Pendiente: guía y preparación de VM limpia con instantánea, por el usuario |
| JDK separado | Aprobado en alcance, sin migración aplicada. Concretar distribución/build/JavaFX y diff antes de usar un payload nuevo |
| 3.3 | Pendiente: implementar instalador Inno según contrato revisado y aprobado |
| 3.4 | Pendiente: implementar señal/consumo 3010 tras aprobar diff mínimo |
| 3.5 | Pendiente: actualización, reparación, downgrade y desinstalación que conserva PawnIO |
| 3.6 | Pendiente: implementación del pipeline y firma real cuando haya admisión y permisos; antes pueden diseñarse ganchos/builds explícitamente sin firma |
| 3.7 | Pendiente: avisos/licencias completos del artefacto final, no solo un inventario preliminar |
| 3.8 | Pendiente: validación de la release en VM/hardware acordados por el usuario |
| 3.9 | Pendiente: cierre con evidencias, deudas y condiciones de publicación |

No se informa A como terminado por haber revisado algunos ejemplos de licencia.
Tampoco el README actual acredita un clon limpio del futuro JDK/instalador.

## 5. Qué falta para poder firmar

**Se puede preparar elegibilidad y diseño ahora**, sin esperar a terminar toda
la Fase 3. **No puede darse por listo un instalador definitivo para firma**
cuando aún no se ha implementado ni verificado esa forma de distribución.
SignPath exige una publicación previa en la forma que se pretende firmar,
procedencia verificable, política/roles/MFA y aprobación manual de releases;
permite incluir determinados binarios OSS ajenos sin firmarlos con la cuenta
del proyecto. [Condiciones oficiales consultadas el 23/11](https://signpath.org/terms).

Itinerario de preparación; no es una orden de ejecución automática:

1. **A:** resolver o clasificar los bloqueos de componentes/OSI, derechos y
   redistribución. MIT del código propio no los resuelve por extensión.
2. **B/C:** incorporar licencia propia y política/privacidad reales, con
   approvals entre pasos. Revisar metadatos/diffs antes de generar nuevos hashes.
3. **D/E:** diseñar compilación/firma reproducible y preparar el dossier. Los
   apartados que dependan del instalador futuro quedan pendientes, no simulados
   como prueba realizada ni completados con datos inventados.
4. **3.2, JDK separado y 3.3–3.5:** preparar entorno, migración aprobada y
   paquete definitivo funcional, con mecanismos de instalación/desinstalación.
   Hacer los cambios de producto solo tras sus aprobaciones.
5. **Preparación de 3.6, 3.7 y pruebas de 3.8:** construir/verificar una versión
   explícitamente sin firma, cerrar licencias/avisos y validar su instalación.
   No es necesario comprar un certificado para este recorrido de pruebas.
6. **C9:** una primera publicación sin firma requiere aprobación de publicación
   y revisión legal; la realiza el usuario. No publicar ahora la imagen 5.1 como
   si fuese ese instalador definitivo. Un repositorio público no equivale a
   tener publicada esa forma de release.
7. **Solicitud por el usuario y admisión:** entregar el dossier antes del envío.
   El agente no consulta a SignPath en nombre del proyecto ni envía el correo
   alternativo. La decisión de admisión sigue siendo externa y NO VERIFICADA.
8. **Firma real 3.6:** generar una **release nueva** respetando helper firmado
   → manifiesto → DLL con pins → paquete → instalador. Nunca refirmar la imagen
   congelada. Revalidar avisos/payload/firmas y la cobertura afectada antes de
   cerrar 3.9; pruebas anteriores sin firma no acreditan los bytes nuevos.

Este recorrido distingue preparar 3.6 sin credenciales de activar firma real
cuando el programa lo permita. Su planificación evita una dependencia circular
entre «necesitar una release previa» y «querer que la primera prueba ya esté
firmada». No introduce un nuevo paso de producto ni permite saltar los existentes.

## 6. Conflictos: cuáles se resolvieron y cuáles siguen abiertos

| ID | Estado / requisito para continuar |
|---|---|
| C1 — MIT y derivados cerrados | **RESUELTO POR EL USUARIO**; no volver a plantear copyleft como elección pendiente. Compatibilidad de terceros se estudia en C7 |
| C2 — Inno/OSI/SignPath | **ABIERTO**. SPDX marca `InnoSetup` como no aprobada por OSI. Identificar código del motor redistribuido y redactar consulta para el dossier; no sustituir herramienta por cuenta propia |
| C3 — PawnIO y módulos | **ABIERTO**. Identificar licencia/fuentes del setup, driver y módulos exactos, excepción y posible licencia alternativa comercial; mantener firma original. No se resuelve firmando el contenedor |
| C4 — Mono.Posix.NETStandard | **ABIERTO**. Licencia/fuentes exactas no acreditadas. No observar carga en una ejecución Windows no prueba que sea seguro excluirla; cualquier cambio de payload requiere diff/aprobación y nuevo manifiesto |
| C5 — Java y JavaFX | **ABIERTO técnicamente**, migración separada ya elegida. Acreditar distribución/build/licencias reales y estudiar si alguna excepción del programa aplica, sin presumir aceptación de Oracle NFTC ni actualizar por este registro |
| C6 — otras dependencias | **ABIERTO**. Incluir NDD, HidSharp, runtimes, contenido enlazado y cabecera IGCL versionada, además de dependencias dinámicas como NVML. Que un runtime ajeno no se copie no elimina la revisión de su interfaz/cabecera |
| C7 — MIT y conjunto | **ABIERTO**. MIT se aplicará al código propio, no relicencia archivos LHM/MPL ni terceros. Estudiar excepciones y obligaciones del conjunto exacto; no declarar compatibilidad universal |
| C8 — privacidad | **ABIERTO**. Auditar red, archivos y registro antes de redactar afirmaciones. El inventario de escrituras de Fase 2 es evidencia parcial, no captura completa de terceros. Permiso de contacto no autoriza telemetría ni una declaración falsa de ausencia de red |
| C9 — release previa | **ABIERTO**. Planificar instalador definitivo sin firma, validación y publicación por el usuario cuando corresponda. GitHub público no demuestra que exista ese artefacto |
| C10 — metadatos y congelación | **NUEVO HALLAZGO A INCLUIR EN A**. La condición de versiones/nombre de producto para firma necesita encaje con versiones distintas de componentes congelados; consultar interpretación o proponer diff separado, sin tocar ABI/wire ni modificar silenciosamente metadatos |

Evidencias concretas, sin convertirlas en una evaluación exhaustiva A:

- **Inno:** [SPDX](https://spdx.org/licenses/InnoSetup.json),
  `isOsiApproved=false`; no significa que MIT sea incompatible con Inno, sino
  que el programa de firma tiene un criterio adicional pendiente de resolver.
- **IGCL:** [licencia local versionada](../native/vendor_headers/intel_igcl/LICENSE)
  incluye restricciones de uso/plataforma; no se trata de una licencia MIT
  por inferencia. Su interpretación/encaje en SignPath no se da por aprobada.
- **C10, PE leídos sin ejecutar:** MACE.exe: producto MACE, versión 1.0.0;
  DLL: producto MACE, versión 1.5.0.0; helper: producto Mace.Lhm.Helper,
  versión de archivo 1.1.1.0. Las condiciones de metadatos del programa
  requieren revisión explícita; no se cambian esos archivos.
- **Estado público:** consulta de solo lectura a GitHub confirma
  [DanielDdeY/MACE](https://github.com/DanielDdeY/MACE) público. El Paso E se
  aplica como auditoría de un repositorio ya público, no como autorización de push.

La elegibilidad, excepciones y concentración de roles las decide SignPath;
ninguna conclusión de este documento sustituye esa admisión. Cuando se trate
de derechos/compatibilidad legal se requiere revisión cualificada; esta es
una revisión técnica e informativa.

## 7. Archivos mencionados por 3.1c: tratamiento de esta entrega

| Archivo | Acción / motivo |
|---|---|
| `helper/FASE3-DISENO.md` | Actualizar estado actual y tabla D1–D9; conservar cuerpo original como propuesta histórica subordinada |
| `helper/FASE3-DISENO-ADENDA.md` | Nota de prioridad y sección fechada con decisiones posteriores; preservar el análisis del 18/11 |
| `helper/FASE2-CIERRE.md` | Añadir nota/postscript fechado, sin cambiar resultados ni alcance de pruebas históricas |
| Este informe | Crear registro de confirmaciones, estado, requisitos y conflictos |
| `prompt_mace_fase3.md` y `3.1c.md` de Downloads | Releídos y conservados como entradas; no se editan fuera del checkout. Este registro fija la prioridad y equivalencias vigentes |
| `LICENSE`, `CODE_SIGNING_POLICY.borrador.md`, `README.borrador.md` | No existen en las ubicaciones revisadas del repo ni junto a los adjuntos con esos nombres. No se inventan borradores ni se crea LICENSE antes de aprobar A |
| `helper/FASE3-LICENCIA-INVENTARIO.md` / `helper/FASE3-CI-DISENO.md` | Inventario A entregado para revisión; diseño D todavía pendiente. A no acredita los extremos marcados NO VERIFICADO |
| `pom.xml`, `.csproj`, `packaging/THIRD-PARTY-NOTICES.txt` | Existentes, sin cambios: son entradas de artefactos/payload, no simple registro de decisiones. Diff/actualización en B y revisión de artefacto final en 3.7 |
| README raíz / CONTRIBUTING / plantillas | No reescribir README en lugar del borrador ausente; CONTRIBUTING y los borradores no están. Su preparación corresponde a B/C tras sus aprobaciones |

Para B se conserva la condición literal de 3.1c: «Solo tras mi aprobación del
Paso A y la confirmación de C1». **C1 ya está confirmado; A está entregado para
revisión, no aprobado.** La autorización actual cubre inventario e informe,
no avanzar automáticamente hasta añadir una licencia o un workflow de firma.

## 8. Verificación, NO VERIFICADO y siguiente acción

Realizado en esta entrega: lectura de adjuntos/documentos, comprobación de
existencia de borradores, lectura de metadatos PE y de condiciones públicas,
consulta de estado público del repositorio, revisión de diff/enlaces y
comprobaciones documentales. Resultados de esta entrega:

| Comprobación | Resultado |
|---|---|
| Enlaces locales de los cuatro documentos | 0 rotos |
| `git diff --check` y revisión del archivo nuevo | Sin errores de espacios; cambios limitados a documentación |
| Datos locales/correo personal en documentos cambiados | No se incluyeron rutas de perfil, SID ni el correo alternativo |
| `check-protected-telemetry.ps1`, Windows PowerShell 5.1 | **PASS**, sin cambios a los bloques/archivos protegidos |
| `audit-retired-providers.ps1`, Windows PowerShell 5.1 | **181 aceptadas, 0 inesperadas**, código 0; reporte local ignorado bajo `native/build/fase3-reglas-3.1c/`, sin tocar el TSV versionado |

No se recompilan ni repiten helper, CTest, Java o payload por un registro de
reglas. Sus cifras históricas no se presentan como pruebas nuevas de hoy.
No se ejecuta MACE/helper, PawnIO, servicios, registro de señal ni pruebas físicas.

**NO VERIFICADO:** admisión/certificado/firma de SignPath, MFA/roles de su futura
cuenta y aprobación de la configuración multietapa; fuentes/licencias exactas
pendientes, compatibilidad global de MIT, excepción aplicable a componentes,
clon limpio del futuro instalador/runtime, migración JDK, funcionamiento de
Inno, VM disponible/restauración, 3010 real, instalación limpia, HVCI y la
cobertura física pendiente. Titularidad/acuerdos se registran por declaración,
no como dictamen legal independiente.

**Qué falta del usuario:** aportar los tres borradores, si quiere que B/C
partan de ellos, o autorizar su redacción desde cero al llegar a esos pasos;
concretar después JDK/JavaFX y disponibilidad de VM. No volver a pedir MIT,
derivados cerrados, alias, roles, contacto preferido o el correo alternativo.
Las decisiones sobre conflictos de elegibilidad se pedirán sobre evidencia
del inventario, no como aceptación anticipada de un servicio.

**Siguiente acción actualizada:** revisar el Paso A de 3.1c ya entregado y
esperar aprobación. Esta consolidación histórica no lo sustituye ni avanza al 3.2.
Un commit documental propio; solo los cuatro archivos indicados, sin los
dos documentos previos sin seguimiento ni los tres TXT locales excluidos.
