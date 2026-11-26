# Code signing policy

Estado: **preparación de solicitud; aprobación no acreditada; builds de
prueba sin firma de MACE**. Actualizado el **25/11/2026**.

Repositorio: <https://github.com/DanielDdeY/MACE>.
Contacto público: [GitHub Issues](https://github.com/DanielDdeY/MACE/issues).
No consta confirmación del mantenedor de una solicitud presentada o aceptada.
Esta política no anuncia un certificado disponible ni un servicio activo.

## Programa elegido y atribución condicionada

Se ha elegido el programa gratuito de **SignPath Foundation**. No se afirma
que MACE sea elegible o haya sido aprobado. Los derechos de redistribución,
el encaje del instalador y la admisión del paquete siguen pendientes.

**Texto de atribución previsto únicamente si se acepta el proyecto y se
habilita efectivamente la firma; no describe el estado actual:**

> Free code signing provided by SignPath.io, certificate by SignPath Foundation

Hasta entonces no se presentará ese texto como patrocinio o firma existentes.
La documentación pública indicará el estado sin firma de MACE. La firma
original de un componente ajeno no implica que el producto MACE esté firmado.

## Responsables y autenticación

Datos confirmados por el mantenedor, sin extraer identidades del historial:

| Rol del proyecto | Responsable |
|---|---|
| Propietario/mantenedor | [DanielDdeY](https://github.com/DanielDdeY) |
| Autor con el rol declarado en esta política | DanielDdeY |
| Revisor | DanielDdeY |
| Aprobador de firma | DanielDdeY |

Las contribuciones externas se revisan antes de integrarse. Esta lista no
reasigna derechos de otros colaboradores ni de dependencias. La aceptación
de esa combinación de roles por el servicio es **NO VERIFICADO**.

El responsable declara 2FA en GitHub; no se ha auditado técnicamente aquí.
Antes de usar SignPath, las cuentas involucradas deberán tener MFA también
en ese servicio y los permisos aprobados. Cuenta, MFA y roles efectivos en
SignPath: **NO VERIFICADO**; no se ha creado o configurado una cuenta en este paso.

Cada versión y cada etapa que solicite firma necesitará aprobación **manual**
del responsable designado. Un tag, un check CI verde o la existencia de este
documento no autorizan por sí solos firmar o publicar.

## Artefactos: lista prevista, no firma aplicada

Actualmente **ninguna firma de MACE se acredita por esta política**. Esta es
la lista cerrada prevista de PE propios a presentar para revisión del servicio;
la configuración técnica aún no está implementada/validada para SignPath.
No seleccionar archivos solo por extensión, carpeta o un prefijo amplio.

| Ruta/nombre previsto en el producto | Tratamiento previsto, sujeto a aprobación |
|---|---|
| `app/lhm/Mace.Lhm.Helper.exe` | Firmar el apphost propio final antes del manifiesto |
| `app/lhm/Mace.Lhm.Helper.dll` | Firmar assembly propio final antes del manifiesto |
| `app/lhm/Mace.Lhm.Protocol.dll` | Firmar assembly propio final antes del manifiesto |
| `app/mace_native.dll` | Firmar la DLL que ya fija el manifiesto final del helper |
| `MACE.exe` | Firmar launcher propio generado, con manifiesto UAC y pin de DLL finalizados |
| `MACE-Preview.exe` | Firmar launcher propio generado, manteniendo su modo simulado sin elevar |
| `MACE-Setup.exe` | Nombre de distribución previsto; firma del contenedor Inno solo después de resolver su admisión y construirlo/verificarlo |

La firma de PE generados por apphost/jpackage, el motor incluido y las
restricciones de metadatos deben confirmarse con SignPath. La lista prevista
no transforma código ajeno incluido por una herramienta en código propio.
El instalador definitivo aún no está construido/validado; no se afirma que
las releases antiguas con ese nombre correspondan a la nueva base LHM.

**Fuera de la firma con la suscripción del proyecto:**

- `app/prerequisites/PawnIO_setup.exe`: conservar intacto y su firma upstream;
  no extraer, modificar o refirmar para incluirlo. Derecho de redistribución
  y tratamiento de controlador/módulos siguen sujetos a revisión.
- `LibreHardwareMonitorLib.dll`, `RAMSPDToolkit-NDD.dll`, `HidSharp.dll`,
  los tres activos Mono si se conservan y **cualquier otra dependencia ajena**.
- Runtime .NET, Java, JavaFX, CRT y bibliotecas del sistema/controlador:
  conservar avisos y las firmas originales existentes. No se promete que
  todas las dependencias estén firmadas ni aceptadas por el programa.
- JAR de aplicación/dependencias, ZIP portátil, JSON, manifiestos y archivos
  de sumas: no se prevé firma individual con Authenticode en este diseño.
  Sus hashes y el contenedor no equivalen a firma individual de cada archivo.
- Pruebas, diagnósticos, scripts de desarrollo, PDB y herramientas: no forman
  parte del producto distribuido ni se añaden por ampliar esta política.

**Desinstalador Inno:** nombre final, licencia, procedencia y eventual firma
**NO VERIFICADOS**. No está incluido como artefacto con autorización de firma.
Firmar el setup no demuestra que su desinstalador esté firmado. Resolverlo
antes de cerrar el instalador y la configuración de artefactos; no omitirlo
silenciosamente ni asumir que el servicio inspecciona cualquier contenedor.

## Orden obligatorio previsto

1. Finalizar/verificar el helper y firmar sus tres archivos propios autorizados.
2. Generar el manifiesto del helper a partir de los **bytes finales** de todo
   su runtime, conservando terceros intactos.
3. Compilar la DLL de producción con el pin de ese manifiesto y firmar la DLL.
4. Fijar el hash de la DLL final en el paquete; generar/finalizar los launchers
   y su UAC correspondiente, firmar los propios y verificar la app-image.
5. Construir el instalador desde esa imagen final, resolver su desinstalador,
   firmar el contenedor autorizado y verificar firmas, sellos y paquete final.

Corresponde al diseño del paso 3.6. No se implementa aquí un workflow, hook
nuevo, proveedor de timestamp o configuración del servicio. La procedencia
desde fuente/CI y la aprobación manual deberán quedar verificables en cada
etapa; restricciones de metadatos y compatibilidad del servicio siguen abiertas.

No cambiar después un archivo ya consumido por un pin: un cambio obliga a
reconstruir desde esa etapa y recalcular los consumidores. No refirmar ni
reemplazar la imagen congelada como efecto de redactar esta política.
DLL 1.5.0, ABI 1.5, helper 1.1.1 y protocolo 2 siguen congelados; no uniformar
esas versiones arbitrariamente para satisfacer un supuesto requisito externo.

## Publicación y límites actuales

No publicar nuevas releases con binarios de terceros hasta cerrar la revisión
legal cualificada. Los avisos de terceros siguen como borrador. El código
fuente propio permanece bajo [MIT](LICENSE); eso no acredita las licencias
de todos los componentes ni una aceptación de SignPath.

Las releases v1.0.0, v1.1.0-rc3 y v1.1.0 son pre-LHM según el mantenedor;
no son base de la nueva candidata. El mantenedor las pasará a borrador, pero
falta su confirmación de ejecución. Esta entrega no cambia el remoto.

No se promete ausencia de avisos de Windows por usar una firma. Hashes,
firmas, pruebas de sensores y revisión legal responden preguntas distintas;
una firma no sustituye las demás. El proceso efectivo de CI/firma y los
comandos en clon limpio siguen **NO VERIFICADOS** para la candidata futura.

## Privacidad

MACE consulta hardware y procesos para presentar métricas y permitir acciones
manuales. Conserva caja negra y preferencias localmente. La caja negra se
guarda por defecto bajo la carpeta de datos del usuario, con destino
configurable y límites de retención; las preferencias del HUD usan Java
Preferences, cuyo backend Windows puede emplear el registro de usuario.

La auditoría estática del código propio no identificó un cliente que envíe
automáticamente esas métricas a un servidor. El helper intercambia muestras
por una tubería local; eso no constituye por sí solo una conexión a Internet.
No se ha realizado una captura exhaustiva del tráfico o diagnóstico del
conjunto, runtimes, controladores, Windows, verificación de certificados o
futuro instalador. **NO VERIFICADO** su comportamiento indirecto completo.
No se formula una promesa universal de ausencia de red o escrituras de terceros.

Descargar fuentes/dependencias, visitar la web del proyecto, abrir Issues y
una futura solicitud de firma son actividades distintas de la telemetría
del producto. La página preparada usa enlaces a GitHub y no incorpora su
antigua consulta automática de releases ni fuentes externas. El alojamiento
y los sitios que el usuario visite se rigen por sus respectivas políticas,
por ejemplo [privacidad de GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
No se declara auditado su tratamiento de datos como parte del código de MACE.

Los informes, rutas de procesos y cajas negras pueden contener datos sensibles.
Antes de compartir, revisar y excluir perfiles, identidades locales, correos,
SID, secretos y datos de máquina. Los modelos CPU/GPU y mediciones pueden
compartirse según la decisión del mantenedor. Abrir un Issue no autoriza
enviar automáticamente archivos desde MACE.

## Evidencia y revisión pendiente

Esta política se apoya en inspección de fuentes y decisiones documentadas,
no en una firma de prueba o dictamen legal. Fuentes públicas consultadas el
25/11/2026: [condiciones de SignPath Foundation](https://signpath.org/terms).
Confirmar con el servicio su aplicación al conjunto concreto antes de afirmar
cumplimiento. La documentación técnica no es asesoría legal.

[Pendientes de B](helper/PENDIENTE-FASE-3B.md),
[preguntas no enviadas](helper/FASE3-PREGUNTAS-SIGNPATH.md) y
[guía de revisión cualificada](helper/FASE3-GUIA-REVISION-SIN-REVISOR.md).
No se envía una solicitud, crea cuenta, firma, hace push o publica nada
mediante este documento.
