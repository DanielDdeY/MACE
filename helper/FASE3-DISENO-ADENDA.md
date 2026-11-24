# Fase 3 — Subpaso 3.1b: aclaraciones del diseño

Fecha de consulta: **2026-11-18**. Partida: **73b054843bf92c86919c176531aa501a8154cfc0**,
rama `LHM_implementado`, árbol limpio antes de empezar. Alcance: documentación.
Se releen [FASE3-DISENO.md](FASE3-DISENO.md) y `prompt_mace_fase3.md` proporcionado
por el usuario. Este análisis técnico y de condiciones publicadas **no es asesoría
legal** ni una aprobación de redistribución.

La aprobación condicionada del usuario es la autoridad para D1–D9. Las
recomendaciones de esta adenda siguen siendo propuestas. No se acepta ninguna
EULA, adquiere certificado, solicita servicio de firma ni envía binarios a terceros.
No se descarga tooling, ejecuta instaladores, consulta/escribe la señal de registro,
llama a una API de arranque no documentada ni cambia producto/JDK/imagen.

## 1. WiX: existencia, condiciones y alternativas (D1 pendiente)

### Evidencia de versión y condiciones

**Existencia documental verificada:** la [release oficial v7.0.1](https://github.com/wixtoolset/wix/releases/tag/v7.0.1)
publica ese tag y el commit abreviado `642f1a0`; los [release notes oficiales](https://docs.firegiant.com/wix/whatsnew/releasenotes/)
lo identifican como actualización del 26 de octubre de 2026. No se ha descargado
ni comprobado un ejecutable de WiX: instalación, firma, hash y compilación del
toolchain local siguen **NO VERIFICADOS**. No se presupone que esté instalado.

Se leyó la **EULA concreta del tag**, no solo una plantilla:
[OSMFEULA.txt de v7.0.1](https://raw.githubusercontent.com/wixtoolset/wix/v7.0.1/OSMFEULA.txt).
Sus condiciones relevantes son:

- Abarca el acceso/uso de los binarios precompilados ofrecidos por WiX.
- La cuota aplica si concurren uso en actividades que generan ingresos e
  ingresos brutos anuales del usuario de **al menos US$10 000**. Por debajo del
  umbral hay exención. Existe también exención por soporte/mantenimiento ya
  pagado a los mantenedores, para evitar duplicidad.
- La cuota, cuando aplica, es mensual bajo las condiciones del proyecto; el
  texto no fija una tarifa. No acredita una compra única que sustituya esa obligación.
- Mantiene los derechos de la MS-RL, la redistribución conforme a ella y la
  posibilidad de compilar desde fuentes sin esta EULA; contiene exclusiones
  de garantía y responsabilidad. No se ha preparado ni aprobado ese toolchain alternativo.

La [documentación oficial de aceptación](https://docs.firegiant.com/wix/osmf/)
asocia WiX 7 con OSMF 1.1 y aceptación `wix7`, directa en compilación/CI o
registrada por usuario/equipo. Es una aceptación real, aunque corresponda
exención de cuota; no se añadirá a un script como detalle rutinario. La página
resume el umbral como «más de» US$10 000; para el límite exacto se toma la
EULA del tag, que dice **mayor o igual**. No se acepta nada en este subpaso.

**Aplicación al supuesto solicitado, libre y sin ingresos:** con ingresos
brutos anuales cero del titular que usa WiX, la EULA prevé exención de cuota.
Que MACE se distribuya gratis o con fuentes no basta si quien lo construye
pertenece a una organización que sí genera ingresos. La [FAQ oficial OSMF](https://opensourcemaintenancefee.org/consumers/faq/)
distingue ingresos de la organización, productos gratuitos, patrocinios y
contribuidores. No se ha auditado la situación económica del usuario, una
institución o una futura organización; no se certifica elegibilidad perpetua.

El aceptante debe ser la persona o entidad que realmente use los binarios
para construir MACE, mediante alguien con autoridad para obligarla. No se
designa al usuario, a su universidad ni a una entidad inexistente como titular
por inferencia. Hay que resolver quién publica y opera CI, y revisar su
situación si aparecen ingresos/patrocinios. La lectura conjunta de estas
fuentes no establece una cuota por cada usuario que simplemente ejecuta el
instalador generado; tampoco autoriza a ocultar componentes WiX redistribuidos.
Identidad contractual definitiva y evaluación jurídica: **NO VERIFICADAS**.

### Licencia de la salida y del motor redistribuido

El proyecto conserva la [MS-RL en el tag v7.0.1](https://raw.githubusercontent.com/wixtoolset/wix/v7.0.1/LICENSE.TXT).
La [aclaración oficial de la .NET Foundation](https://docs.firegiant.com/wix/#source-code-license)
separa el paquete generado de una obra derivada por el mero uso de WiX:
**MSI/EXE y código propio de MACE no adquieren automáticamente la MS-RL**.
La licencia propia de MACE sigue pendiente. Eso no cambia la licencia del
código de WiX incorporado: motor Burn, bibliotecas o acciones redistribuidas
requieren conservar sus avisos y cumplir la MS-RL para sus archivos, incluida
la disponibilidad del código fuente correspondiente. Modificaciones a esos
archivos siguen cubiertas; archivos propios independientes pueden tener otra licencia.

La salida no es un único bloque legal: aplicación, runtime, motor, dependencias
y PawnIO conservan sus términos. El futuro inventario 3.7 debe identificar los
componentes efectivamente enlazados y sus fuentes exactas; no basta incluir
la EULA del compilador ni llamar «libre» a todo el EXE. No se incluye FireGiant
comercial por asociación con WiX. Cumplimiento del paquete final: **NO VERIFICADO**.

### Comparación de herramientas mantenidas, sin instalarlas

Se contrastaron también [Inno Setup 7.1.1, 2026-10-28](https://jrsoftware.org/files/is7-whatsnew.htm)
y [NSIS 3.13, 2026-09-27](https://nsis.sourceforge.io/Docs/AppendixF.html).
NSIS 3.14 figura como trabajo sin fecha de release, no como versión publicada.
Estas publicaciones acreditan actividad; no prueban la compatibilidad de un
instalador MACE todavía inexistente.

| Criterio | WiX 7 / MSI + Burn | Inno Setup | NSIS |
|---|---|---|---|
| Licencia y costo | MS-RL para fuentes/componentes; EULA OSMF para binarios oficiales, exención condicionada anterior | Licencia Inno Setup permisiva, admite uso comercial; conservar avisos y marcar modificaciones | Base zlib/libpng; compresores y plugins tienen términos propios |
| Silencioso y consentimiento | MSI/Burn admiten silencioso; consentimiento separado de PawnIO debe programarse | `/SILENT` o `/VERYSILENT`; consentimiento PawnIO debe programarse | `/S` distingue mayúsculas; consentimiento PawnIO debe programarse |
| Reinicio pendiente | Tratar 3010 y prohibir reinicio automático en el coordinador; no inferirlo del servicio | `/NORESTART` y `/RESTARTEXITCODE`; sin el primero, muy silencioso puede reiniciar sin preguntar | Capturar salida del hijo y estado de reinicio mediante script; no hay mapeo automático de nuestro contrato |
| Rollback | MSI ofrece rollback de sus operaciones; D2 confirma MACE antes del driver, cuya instalación queda fuera de esa transacción | Recuperación propia del instalador y script; no equivale a rollback transaccional MSI del driver | Implementación por script; no asumir rollback ni reparación MSI |
| Actualización/reparación | Familias/versiones y reparación MSI; autoría correcta necesaria | `AppId`/versionado/desinstalador; no proporciona semántica MSI de reparación | Detección, bloqueo downgrade, actualización y reparación propios |
| Administrador | Instalación por máquina elevada; no implica lanzar MACE como SYSTEM | `PrivilegesRequired=admin` | `RequestExecutionLevel admin` |
| Firma | MSI, motor Burn y bundle según cadena aprobada; no refirmar PawnIO | Integración `SignTool` para instalador/desinstalador | Firma del instalador/desinstalador integrada en el proceso de build externo |
| Tamaño | MSI + motor + payload; medida final NO VERIFICADA | Stub EXE + payload; medida NO VERIFICADA | Stub/compresión + payload; medida NO VERIFICADA |
| Mantenimiento | Autoría MSI/Burn y revisión EULA/actualizaciones; mejor ajuste a familia MSI de D6 | Menos autoría para EXE propio; scripts adicionales para contratos específicos | Flexible, más responsabilidad de scripts/plugins y mantenimiento de recuperación |

Fuentes funcionales: [WiX/Burn](https://docs.firegiant.com/wix/tools/burn/),
[firma WiX](https://docs.firegiant.com/wix/tools/signing/),
[parámetros Inno](https://jrsoftware.org/inno-setup/documentation/topic_setupcmdline.htm),
[privilegios Inno](https://jrsoftware.org/inno-setup/documentation/topic_setup_privilegesrequired.htm),
[firma Inno](https://jrsoftware.org/inno-setup/documentation/topic_setup_signtool.htm),
[referencia NSIS](https://nsis.sourceforge.io/Docs/Chapter4.html) y
[uso de instaladores NSIS](https://nsis.sourceforge.io/Docs/Chapter3.html).
Ninguna herramienta resuelve por sí sola hash/firma de PawnIO, espera 180 s
sin kill, consentimiento silencioso, logs saneados y señal 3010. Se necesita
el coordinador específico previsto, con pruebas mediante sustitutos.

La [licencia fuente de Inno](https://raw.githubusercontent.com/jrsoftware/issrc/main/license.txt)
permite uso, modificación y redistribución bajo sus condiciones. La
[página comercial oficial](https://jrsoftware.org/inno-setup/purchase) pide
compra a usuarios comerciales pero aclara que no es estrictamente obligatoria;
ofrece licencias perpetuas de pago único con período de actualizaciones, cuya
renovación posterior es separada. No se confunde esta petición con una cuota
mensual obligatoria ni con garantía de actualizaciones perpetuas. No se compra nada.

En [NSIS](https://nsis.sourceforge.io/License), zlib evita sumar las condiciones
de bzip2 o LZMA; este último tiene CPL-1.0 y una excepción de enlace que no
elimina sus propias obligaciones. Revisar cualquier plugin antes de incorporarlo.
La licencia del stub no obliga por sí sola a relicenciar MACE.

**Recomendación condicionada:** conservar como primera opción WiX directo +
Burn por MSI, reparación y familia de D6, solo si el titular acepta explícitamente
los términos tras confirmar la exención. Es compatible con costo cero en el
supuesto sin ingresos; no garantiza ausencia de pagos futuros si cambia ese
supuesto. Si se exige excluir contractualmente ese posible costo, preferir
evaluar Inno como EXE propio; NSIS es otra opción libre con más lógica propia.
Cambiar a EXE directo obliga a revisar la parte MSI de D6; envolver un MSI con
Inno/NSIS no elimina la necesidad de elegir un generador MSI. **D1 sigue NO APROBADA**.

## 2. Mono.Posix.NETStandard 1.0.0 y licencia de MACE

Inspección de solo lectura del paquete **ya restaurado**, sin restauración nueva:

| Evidencia | Resultado comprobado |
|---|---|
| Identidad en `Mono.Posix.NETStandard.nuspec` | `Mono.Posix.NETStandard`, `1.0.0`; autor/owner Microsoft; copyright Microsoft; `requireLicenseAcceptance=true` |
| Declaración de licencia | `licenseUrl=https://go.microsoft.com/fwlink/?linkid=869050`; no expresión SPDX ni archivo de licencia declarado |
| Fuente declarada | `projectUrl=https://go.microsoft.com/fwlink/?linkid=869051`; sin elemento repository, commit ni tag |
| ZIP local `.nupkg` | 26 entradas; ninguna `LICENSE`, `LICENCE`, `COPYING` o `NOTICE`; sí `.signature.p7s`, nuspec y binarios administrados/nativos |
| Metadatos de restauración | `.nupkg.metadata` declara origen `https://api.nuget.org/v3/index.json`; su contentHash coincide con el lock. Esto no verifica jurídicamente la licencia ni equivale a verificar la firma NuGet |
| DLL real de la imagen | `packaging/dist/MACE/app/lhm/Mono.Posix.NETStandard.dll` coincide por SHA-256 con `runtimes/win-x64/lib/netstandard2.0/Mono.Posix.NETStandard.dll` del paquete restaurado; no se publica el hash completo |

El paquete está fijado en [packages.lock.json del helper](Mace.Lhm.Helper/packages.lock.json).
La [ficha oficial NuGet 1.0.0](https://www.nuget.org/packages/Mono.Posix.NETStandard/1.0.0)
corrobora identidad/versionado. Los enlaces FWLink no pudieron resolverse con
la herramienta de consulta; eso **no demuestra que estén rotos para todos**.
No se obtuvo de esas fuentes un repositorio y etiqueta/commit atribuibles a
esta versión concreta.

El [LICENSE del repositorio Mono actual](https://raw.githubusercontent.com/mono/mono/main/LICENSE)
habla de MIT para gran parte del runtime/bibliotecas y enumera otras licencias.
No vincula por sí solo ese contenido actual con este `.nupkg` 1.0.0 ni con todos
sus binarios nativos. **No se adjudica MIT, BSD o Apache por nombre, autor o
parecido con otro paquete. Licencia exacta, repositorio y etiqueta: NO VERIFICADOS.**

**Bloqueo explícito de revisión legal y distribución:** obtener evidencia
versionada del publicador que una paquete → fuentes/commit/tag → licencias y
copyright aplicables, incluidos sus componentes nativos; incorporar los avisos
correspondientes en 3.7. La firma del paquete o un hash coincidente no sustituyen
esa evidencia. No se retira, actualiza ni sustituye esta dependencia en 3.1b.

**D7 permanece PENDIENTE.** Para analizar compatibilidad, sin elegir por el usuario:

| Familia posible para el código propio | Condiciones de compatibilidad a revisar antes de elegir |
|---|---|
| MIT / Apache-2.0 | Código propio permisivo; conservar avisos/licencias de terceros y, en Apache, revisar patentes/NOTICE. No convierte componentes MPL o GPL con excepción en código permisivo |
| MPL-2.0 | Reciprocidad por archivo para código cubierto; revisar modificaciones/enlace con componentes existentes y acceso a sus fuentes. No obliga por mera agregación a relicenciar todos los binarios |

El inventario de 3.1 sigue siendo la base por componente; no existe una conclusión
global de compatibilidad mientras Mono y el runtime exacto de release estén sin
cerrar y no se haya elegido licencia propia. Referencias: [MIT](https://opensource.org/license/mit),
[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) y
[MPL-2.0](https://www.mozilla.org/en-US/MPL/2.0/).

## 3. D4: señal de reinicio sin depender automáticamente de BootIdentifier

### Modelo de amenaza y validación común

La señal informa de **un retorno real 3010** del instalador verificado; no mide
salud del driver ni fuerza que las métricas sean no válidas. Se busca impedir
que una señal vieja, mal formada o plantada por un usuario sin privilegios
produzca un aviso falso. Proteger el padre HKLM, propietario y DACL, y fijar
vista de 64 bits evita aceptar un sustituto en HKCU u otra vista.

**Plantar una clave persistente falsa en HKLM con los permisos previstos exige
administrador/SYSTEM**, o un fallo de un componente privilegiado. Ese atacante
también podría alterar el producto; un BootIdentifier no crea una frontera
frente a él, pues puede copiar un identificador actual. Sí interesa detectar
errores de instalación, restauraciones antiguas o claves preexistentes ajenas.

Las opciones conservan, para el futuro diff, un único valor binario de 32 bytes,
propietario Administradores o SYSTEM, DACL protegida sin escritores no confiables
y lectura autorizada; rechazo de DACL nula/inesperada, propietario inesperado,
tipo/tamaño/versión/magic/reservados/código distintos. No seguir redirecciones
de clave ni admitir ubicaciones alternativas. Ausente, inválida o lectura fallida
no equivale a 3010, no oculta otras causas ni sustituye V2.
Son requisitos de diseño, **todavía no implementados ni probados**.

### Alternativas

| Opción | Ventaja y límite | Recomendación |
|---|---|---|
| (a) Volátil + ACL + propietario + validación estricta, sin identidad de arranque | API pública sencilla; una clave realmente volátil caduca al descargarse el hive. El lector no puede demostrar solo por el contenido que una clave existente fue creada volátil | Preferida bajo el modelo anterior, con límite explícito y aprobación del ajuste de campos/ruta |
| (b) Fuente de arranque documentada: `Win32_OperatingSystem.LastBootUpTime` | Propiedad WMI documentada. Es una fecha, no GUID ni prueba criptográfica; requiere WMI/COM/proveedor y resolver latencia, fallos y comparación entre transiciones | No preferida: introduce una dependencia adicional para un aviso. Necesitaría excepción de arquitectura y pruebas antes de adoptarla |
| (c) BootIdentifier consultado defensivamente con API nativa | Añade comparación de sesión de arranque y ayuda contra datos de otro arranque; no impide falsificación por administrador | No preferida: el contrato concreto de `SystemBootEnvironmentInformation` no queda garantizado por la documentación pública consultada; dependencia NO APROBADA |

La fuente de (b) está en [Microsoft: Win32_OperatingSystem](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-operatingsystem).
No se ejecutó WMI. Derivar una fecha de arranque restando uptime a la hora civil
no es equivalente: cambios de reloj y precisión impiden tratarla como identidad
única; [GetTickCount64](https://learn.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-gettickcount64)
documenta tiempo transcurrido, no un identificador de arranque.

Para (c), [Microsoft documenta limitaciones de NtQuerySystemInformation](https://learn.microsoft.com/en-us/windows/win32/api/winternl/nf-winternl-ntquerysysteminformation).
La presencia de una clase/estructura en cabeceras de terceros no la convierte
en contrato público soportado. Acceso defensivo significaría resolver
disponibilidad/retorno/longitud, rechazar resultados incompletos y no inventar
una identidad al fallar. Eso reduce fallos de ejecución, no el riesgo de cambio
de contrato. No se llamó a esta API ni se considera su adopción aprobada.

### Volatilidad y transiciones de energía

[RegCreateKeyExW](https://learn.microsoft.com/en-us/windows/win32/api/winreg/nf-winreg-regcreatekeyexw)
documenta que `REG_OPTION_VOLATILE` guarda en memoria hasta descargar el hive;
para HKLM, al apagado completo del sistema. También documenta que la opción
**se ignora si la clave ya existe**. No basta abrirla con ese flag para convertir
una clave persistente. El escritor debe distinguir `REG_CREATED_NEW_KEY` de
una apertura; no adoptar una preexistente desconocida como señal propia.

| Transición | Base oficial e interpretación para el diseño | Verificación física de esta señal |
|---|---|---|
| Reiniciar Windows | Ciclo completo del kernel; se espera descarga del hive y desaparición de la clave realmente volátil | **NO VERIFICADO** en VM |
| Apagado completo y nuevo inicio | RegCreateKeyExW establece pérdida al descargar el hive HKLM | **NO VERIFICADO** en VM |
| Apagar con Inicio rápido | Hiberna/restaura sesión de kernel; no equiparar a reinicio completo ni prometer borrado | **NO VERIFICADO**; comprobar persistencia efectiva y disponibilidad en VM |
| Suspensión S3 / Modern Standby | Conserva estado en RAM; se espera conservar el aviso pendiente | **NO VERIFICADO** en VM |
| Hibernación S4 / suspensión híbrida | Guarda/restaura estado; se espera conservar el aviso pendiente, no tratarlo como reinicio | **NO VERIFICADO** en VM |

Microsoft describe RAM, hibernación y conservación del kernel en
[estados de energía](https://learn.microsoft.com/en-us/windows/win32/power/system-power-states)
y aclara que Inicio rápido no se aplica a «Reiniciar» en
[documentación de Inicio rápido](https://learn.microsoft.com/en-us/troubleshoot/windows-client/setup-upgrade-and-drivers/fast-startup-causes-system-hibernation-shutdown-fail).
Las expectativas sobre esta clave concreta combinan esos contratos; **no son
resultados de una prueba de MACE**. La documentación no sustituye la matriz
por versión/configuración de Windows. Una VM puede no ofrecer S3/hibernación/Inicio
rápido; en ese caso se registra NO VERIFICADO, sin habilitarlos en el host.

### Recomendación y cambio de esquema que aún requiere decisión

Proponer (a), conservando las aprobaciones de D4. El contrato del escritor debe
crear la señal fresca bajo padre protegido y emitirla solo al recibir 3010;
no borrarla por retorno 0 posterior, error, timeout ni primera lectura. El
lector valida estrictamente y trata la señal como aviso separado. Una clave
persistente falsa con propietario/DACL/contenido perfectos, introducida por
administrador o por un escritor defectuoso, **seguiría sin poder distinguirse
de una genuina** mediante (a). Si se exige rechazarla incluso en ese escenario,
esta alternativa no cumple y D4 debe permanecer bloqueada para nuevo diseño.

**No hay autorización implícita para cambiar el significado de los 32 bytes:**
el diseño anterior reservaba bytes 12–27 para GUID y usaba un subnombre de
clave dependiente de BootIdentifier. Para (a) se propone, pendiente de aprobación,
usar esos 16 bytes como **reservados obligatoriamente cero** y una subclave
volátil versionada fija, por ejemplo `BootState\PendingV1`, conservando los
otros campos y el valor `Pending`. Esto altera ruta/semántica propuestas, no
el tamaño aprobado; requiere tu decisión antes del diff de 3.4. No se cambia
el esquema original en código ni se escribe una clave en este paso.

Pruebas futuras en VM limpia con instantánea: señal aislada válida/ausente,
tipo/tamaño/versión/ACL/propietario erróneos, clave persistente preexistente
rechazada por el escritor, dos instalaciones en el mismo arranque y toda la
matriz de energía. Debe observarse que una clave persistente maliciosa válida
no es detectable por (a), sin prometer lo contrario. El 3010 real exige retorno
real de PawnIO; un sustituto solo acredita el mapeo. No se autoriza ejecutar
estas pruebas ahora ni usar el espacio real de la señal en el host.

## 4. Runtime Java de release: mantenimiento y propuesta separada

**Verificado localmente por lectura:** el `release` de
`packaging/dist/MACE/runtime` declara Java **22.0.2**. El diseño 3.1 identificó
el JDK Oracle 22.0.2+9-70 y sus términos NFTC; no se reclasifica ese binario como
GPL. [app/pom.xml](../app/pom.xml) fija compilación `release=22`, JavaFX 22.0.2
y acceso nativo explícito. La imagen congelada sigue siendo esa referencia.

Según la [hoja de soporte Oracle, actualizada 2026-10-20](https://www.oracle.com/java/technologies/java-se-support-roadmap.html),
22 fue una versión no LTS, sucedida en septiembre de 2024; no pertenece a una
línea actual de actualizaciones de seguridad. A fecha de consulta, 25 es LTS y
27 es la versión semestral vigente. **Conservar 22.0.2 en una nueva distribución
aceptaría una base sin mantenimiento actual**, aunque la app arranque. No se
ha ejecutado un análisis de vulnerabilidades del runtime MACE ni demostrado
una vulnerabilidad explotable concreta; ese alcance sigue NO VERIFICADO.

La [guía FFM de Java 22](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html)
remite a JEP 454, que finaliza esta API en 22. Java 21 y anteriores quedan
descartados para esta migración: una línea mantenida no compensa que la API
requerida fuese preliminar/incubadora allí. No se propone volver a preview.

| Distribución candidata | Licencia / mantenimiento acreditado | Límite de esta revisión |
|---|---|---|
| Eclipse Temurin **25 LTS** | GPLv2 + Classpath Exception; binarios sin costo, [FAQ oficial](https://adoptium.net/docs/faq); actualizaciones de seguridad activas | Recomendación principal, sin promesa de SLA gratuito. Build Windows x64 exacta y hash por fijar |
| Microsoft Build of OpenJDK **25 LTS** | GPLv2 + Classpath Exception; actualizaciones trimestrales públicas gratuitas, [política oficial](https://learn.microsoft.com/en-us/java/openjdk/support) | Alternativa válida; soporte comercial de Azure no se presupone incluido para MACE |
| Amazon Corretto **25 LTS** | OpenJDK libre, GPLv2 + Classpath Exception; actualizaciones de rendimiento/seguridad sin costo, [descripción oficial](https://docs.aws.amazon.com/corretto/latest/corretto-25-ug/what-is-corretto-25.html) y [FAQ/licencia](https://aws.amazon.com/corretto/faqs/) | Alternativa válida; versión Windows x64 y avisos por verificar antes de seleccionarla |

No fijar una build solo por decir «25». La [actualización oficial Temurin del
18 de noviembre de 2026](https://adoptium.net/en-GB/news/2026/11/temurin-november-2026-cspu-java4shell)
anuncia una corrección extraordinaria, identifica 25.0.5.1+2 y explica que los
binarios se publican conforme terminan las pruebas. Por ello el anuncio de
octubre 25.0.5 no se toma automáticamente como candidato final. Disponibilidad,
firma y hash del artefacto Windows elegido: **NO VERIFICADOS**, sin descargarlo.
Esta mención no atribuye automáticamente ese fallo a la imagen actual.

**Recomendación pendiente de aprobación:** migración separada a una build
Temurin 25 LTS con las correcciones vigentes y verificación de procedencia;
Microsoft/Corretto son alternativas. Preferir LTS frente a 27 por cadencia de
mantenimiento, sin asumir equivalencia binaria entre proveedores.

| Área | Impacto y verificación exigida antes de aceptar una imagen nueva |
|---|---|
| Toolchain y Maven | JDK paralelo aislado, sin reemplazar el instalado. Revisar compilador/plugins; decidir explícitamente conservar `--release 22` o cambiarlo. Mantener perfil producción/desarrollo y pruebas de carga cerrada |
| FFM y JavaFX | Probar invocaciones reales, avisos/restricciones de acceso nativo y flags actuales. JavaFX 22.0.2 + AtlantaFX no se declara compatible/mantenido en 25 por inferencia; revisar y presentar actualización JavaFX por separado |
| Versión JavaFX | [OpenJFX 25 exige al menos JDK 23](https://docs.oracle.com/en/java/java-components/javafx/25/release-notes/). Es un mínimo técnico, no garantía de compatibilidad con MACE ni de que cualquier distribución/patch JavaFX tenga iguales términos. Elegir pareja mantenida con evidencia del artefacto libre real |
| jlink | Regenerar runtime desde los módulos del JDK aprobado, conservar archivos legales y excluir desarrollo. Verificar módulos presentes y sus dependencias; no mezclar jmods de distintas versiones |
| jpackage / MSI | El nuevo jpackage puede cambiar launchers, árbol launcher/JVM y opciones. Revalidar UAC, instancia única y cierre; no presumir que sus requisitos WiX son los del JDK 22. Si se aprueba WiX directo, jpackage sigue generando app-image, no su MSI |
| Hashes/pins/manifiestos | Imagen nueva separada: nuevos hashes de runtime/JAR/launchers y manifiesto de payload. Mantener pins de DLL/helper si sus bytes no cambian; cambiar JDK no exige por sí solo recompilar la DLL. Si cambia un artefacto fijado, actualizar su pin en el orden de confianza aprobado |
| Regresión | Java completa, helper, CTest Development/Production, payload y rechazos, protegidos, auditoría; después selección de componentes con variables ajenas, UI real y cierre launcher/JVM/helper en VM. Pruebas que exigen administrador las ejecuta el usuario |
| Release y firma | Nuevo inventario/versiones/licencias y tamaño; Windows PowerShell 5.1 para manifiestos/payload. Firmar después de estabilizar y fijar hashes; no modificar retroactivamente la imagen 5.1 |

Orden propuesto: resolver esta decisión y el alcance del cambio; 3.2 prepara
la guía/VM sin instalar en el host; aplicar la migración **solo con aprobación
específica y en un cambio separado**, completar su regresión antes de usar el
payload nuevo en 3.3. Así no se firma/congela un instalador para luego cambiar
su runtime. La migración no se esconde dentro de la implementación del
instalador ni autoriza cambiar ABI/protocolo/helper/política 4A.

## 5. Numeración vigente y decisiones D1–D9

**Discrepancia comprobada:** el `prompt_mace_fase3.md` local leído todavía
enumera 3.2 instalador, 3.3 señal y 3.4 entorno/laptop. El diseño 3.1 siguió esa
versión. El mensaje actual del usuario dispone otra secuencia y otra política
de máquinas; **se adopta ese mensaje como instrucción más reciente**. No se
edita el archivo recibido ni se simula que ya tenía esa numeración.

| Paso vigente | Entregable / alcance | Correspondencia anterior |
|---|---|---|
| 3.1b | Esta aclaración documental; detenerse | Subpaso nuevo autorizado |
| 3.2 | Entorno de pruebas: guía de VM limpia e instantánea | Antes 3.4; físico prestado queda solo para sensores y con permisos/aprobación |
| 3.3 | Instalador | Antes 3.2 |
| 3.4 | Señal del 3010 y consumo, diff mínimo previo a aprobación | Antes 3.3 |
| 3.5 | Desinstalación, actualización y reparación | Sin cambio |
| 3.6 | Firma y pipeline | Sin cambio |
| 3.7 | Avisos y licencias | Sin cambio |
| 3.8 | Verificación de instalación en VM | Se aplica el criterio actual de VM, sin atribuir disponibilidad |
| 3.9 | Cierre | Sin cambio |

Las referencias antiguas del cuerpo de 3.1 se conservan como propuesta
histórica, subordinadas a esta tabla y al estado actualizado de su sección 11.
Ninguna referencia a «3.2 instalador», «3.3 consumo» o «3.4 laptop» autoriza
ahora saltarse la guía de VM o instalar en un equipo físico prestado.

| Decisión | Estado recibido y límites |
|---|---|
| D1 | **NO APROBADA**. WiX directo MSI + Burn queda condicionado a elección tras este resumen; no descargar/instalar/aceptar EULA |
| D2 | **APROBADA**. MSI primero, PawnIO después; consentimiento explícito UI/silencioso; lista exacta inicial 2.2.0.0; conservar otras versiones sin actualización automática |
| D3 | **APROBADA**. Esperar como máximo 180 s, sin matar el instalador; timeout indeterminado, sin señal 3010 ni segundo intento |
| D4 | **APROBADOS** tamaño 32 B, un valor, propietario, ACL y `REG_OPTION_VOLATILE`. **NO APROBADO** BootIdentifier. Alternativa (a), ruta y significado de sus 16 bytes propuestos requieren decisión |
| D5 | **APROBADA EN PRINCIPIO**. Aviso Java separado mediante `MetricState.REBOOT_REQUIRED`, conservando contratos; presentar diff mínimo aparte y esperar aprobación antes de aplicar en 3.4 |
| D6 | **APROBADA**. Program Files x64 fijo, datos y PawnIO conservados, cierre manual sin kill, bloqueo downgrade, familia MSI propuesta y retención de logs propios (10 archivos, 1 MiB cada uno). Si cambia D1 a EXE puro, revisar lo relativo a MSI |
| D7 | **PENDIENTE**, campo sin completar. No se elige licencia de MACE |
| D8 | **APROBADO** build de prueba explícitamente sin firma con aviso. Selección de firma pendiente; preferencia previa sin mensualidad, campo de confirmación actual sin completar. Sin servicios, envíos ni compras autorizados |
| D9 | Disponibilidad VM Hyper-V/otro físico, permiso y restauración probada: **NO VERIFICADOS**, campos sin completar. Criterio provisional recibido: instalación solo en VM limpia con instantánea; físico prestado solo sensores, con permiso, respaldo probado y aprobación específica |

### D3: límite y recuperación manual a documentar al usuario

Los 180 s acotan la espera del coordinador, **no la vida del instalador del
driver ni una operación de Windows**. MACE ya está instalado por D2. Tras el
timeout puede continuar un proceso y perderse un retorno tardío; no registrar
éxito/fracaso definitivo ni inventar 3010. No dejar un observador residente ni
un segundo intento automático. No se garantiza rollback de cambios de PawnIO.

Procedimiento previsto para las pruebas autorizadas en VM, aún no ejecutado:

1. Guardar resultado «indeterminado por timeout» y tiempo, sin datos locales.
   Esperar la finalización del instalador oficial si sigue activo; no lanzar
   otro instalador, forzar su cierre, parar servicios ni desinstalar el driver.
2. Cuando termine la actividad y tras guardar el trabajo, el usuario puede
   reiniciar Windows. Un reinicio no convierte un retorno perdido en 3010
   observado ni demuestra que PawnIO se instaló correctamente.
3. Después, verificar de solo lectura instalación/versión y estado, y comprobar
   sensores en MACE. Revisar el diagnóstico antes de cualquier reintento manual;
   no hay reintento automático aprobado en este caso.
4. Si queda bloqueado o el resultado no se puede determinar con seguridad,
   registrar la limitación y restaurar la instantánea limpia mediante el
   hipervisor por decisión del usuario. No aplicar este flujo de instalación a
   un físico prestado. La guía 3.2 concretará preparación y observaciones.

## 6. Verificación, pendientes y parada

**Comprobado en 3.1b:** lectura de los dos documentos, estado Git inicial limpio,
fuentes oficiales enlazadas, EULA/MS-RL del tag WiX, metadatos/contenido del
paquete Mono ya restaurado y coincidencia de su DLL con la imagen; lectura
de `runtime/release`, POM y script de empaquetado. Ningún binario nuevo ejecutado.
Se revisan diff, enlaces locales y formato; no se repite regresión de producto
por una modificación exclusivamente documental. Los resultados históricos de
Fase 2 no se presentan como pruebas de 3.1b.

**NO VERIFICADO / pendiente:**

- Elegibilidad jurídica/económica definitiva del titular WiX y cumplimiento
  del instalador final; toolchain descargado, compilado o ejecutado.
- Licencia, fuentes y tag exactos de Mono 1.0.0: bloqueo legal de distribución.
- Volatilidad de la señal concreta, su ACL/escritor/lector, todas las
  transiciones de energía, boot alternativo y 3010 real; no hay implementación.
- Compatibilidad de MACE/JavaFX/AtlantaFX/FFM con el JDK nuevo, build Windows
  segura seleccionada, cambios de empaquetado y regresión posterior.
- Disponibilidad de VM limpia/instantánea, físico autorizado/restauración,
  instalación/rollback/reparación/actualización y pruebas físicas pendientes
  de Fase 2 (incluidos hardware, HVCI y recuperación real de PawnIO).
- Firma propia, licencia MACE elegida y revisión legal completa.

**Decisiones solicitadas para continuar:** D1 (WiX condicionado a exención/EULA
o alternativa); D4 (aceptar (a) con su límite y ajustar ruta/campos, o exigir
otro mecanismo); autorizar por separado propuesta de migración JDK 25 LTS
y alcance de JavaFX. D7 y campos D8/D9 siguen pendientes, sin rellenarlos por
el usuario. Aprobar esta adenda no equivale a aceptar una EULA ni a autorizar
un diff de producto.

Únicos cambios versionados: esta adenda y estados D1–D9/notificación de
precedencia en [FASE3-DISENO.md](FASE3-DISENO.md). Sin logs locales ni los tres
TXT excluidos. Congelados DLL 1.5.0, ABI 1.5, protocolo 2, helper 1.1.1,
estados 9–16 y política 4A. No se toca la imagen, el JDK instalado ni launchers.

**DETENERSE al terminar 3.1b. No avanzar a 3.2 sin aprobación.**
