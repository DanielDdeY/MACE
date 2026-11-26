# D — Revisión legal sin revisor designado

Fecha de consulta: **2026-11-25**. El usuario aprueba el contenido del dossier,
pero **no dispone de revisor**. Este texto no es asesoría legal, contratación,
contacto ni autorización para enviar documentación a terceros.
Regla 5 confirmada: **no publicar releases con binarios de terceros hasta
cerrar la revisión legal**; el código fuente público no se ve afectado.
MIT confirmada y titular verificado por el usuario: no reabrir esa decisión.

## Perfil y encargo acotado que puede buscar el usuario

Profesional habilitado en la jurisdicción pertinente, con experiencia en
licencias FOSS, GPL/LGPL/excepciones y redistribución de binarios Windows,
componentes de drivers, runtimes y términos de herramientas de instalador.
Solicitar alcance escrito, confidencialidad, conflictos de interés, precio
por encargo o pro bono y un resultado accionable. No se presume la
jurisdicción del usuario a partir del idioma o zona horaria.

Entregar primero una versión saneada de FASE3-DOSSIER-REVISION.md, resumen
provisional de licencias, diagrama aplicación/DLL/helper/PawnIO y una tabla
corta de versiones/origen/modalidad de redistribución. Separar biblioteca
cargada del driver instalado en el equipo de un binario copiado en el setup.
No remitir el inventario completo de 103 filas como requisito de primer contacto.
El usuario revisa y autoriza el paquete de envío; el agente no lo envía.

Preguntas centrales para presupuesto/revisión:

1. PawnIO setup exacto y módulos: permiso y fuente correspondiente, avisos,
   condiciones de las excepciones y obligaciones de distribución reales.
2. IGCL/CRT/NVML: qué se enlaza, copia o usa desde Windows/driver y bajo qué
   derechos; qué avisos y fuentes deben acompañar cada modalidad.
3. Mono: acreditar licencia exacta aunque se experimente su exclusión; revisar
   si se sigue distribuyendo algún activo o solo queda dependencia de build.
4. Inno: partes redistribuidas, obligaciones del output y límites de modificarlo.
5. Distinguir derecho de redistribución de **elegibilidad privada SignPath**:
   esta última la decide SignPath, no un dictamen de terceros ni el agente.

Pedir respuesta permitido/condicionado/bloqueado/NO VERIFICADO por grupo,
con texto/licencia de versión exacta, obligación y evidencia faltante. Acordar
qué material adicional haría falta, antes de remitirlo.

**No enviar sin autorización expresa:** binarios, instaladores/SDK, dumps,
logs sin sanear, datos de usuarios/equipos, claves, tokens, certificados,
identidad legal o correos privados. Que el repositorio sea público no autoriza
al agente a abrir solicitudes, subir archivos o contratar representación.

## Vías públicas verificadas; no se contactó a ninguna

**Reconsulta en Etapa 2, 2026-11-25:** las fuentes siguientes se volvieron
a abrir. No se infiere disponibilidad de un profesional por existir la página.
El estado técnico actualizado y la ruta crítica están en
[FASE3-ETAPA2-EVIDENCIA.md](FASE3-ETAPA2-EVIDENCIA.md); LICENSE ya se incorporó
en 7be9ab0 y los cuatro ensayos físicos Mono fueron comunicados con código 0.
Eso no cierra la revisión legal. Las releases históricas a borrador están
**VERIFICADAS POR EL USUARIO**, no por una nueva consulta remota.

| Vía oficial | Qué acredita la fuente | Límites/costo para MACE |
|---|---|---|
| [SFLC](https://softwarefreedom.org/), [servicios](https://softwarefreedom.org/services/) | Publica servicios pro bono para desarrolladores FOSS y ayuda de licencias; asesoramiento y análisis de copyright | Admisión, capacidad actual, jurisdicción y aceptación de este encargo: **NO VERIFICADO**. No se promete atención gratuita automática; las páginas no fijan plazo |
| [FSFE Licence Questions/FAQ](https://fsfe.org/freesoftware/legal/faq.en.html) | Recursos públicos y equipo voluntario; la FAQ indica expresamente que FSFE no puede dar asesoría legal y puede orientar hacia profesionales | No se publica tarifa para esa orientación ni se garantiza respuesta o admisión; recomiendan consultas en inglés. No representación ni dictamen sobre el setup |
| [Conservancy servicios](https://sfconservancy.org/projects/services/), [admisión](https://sfconservancy.org/projects/apply/) | Asistencia jurídica básica para proyectos que **ya son miembros**; selección y requisitos publicados | No es consulta gratuita inmediata. Admisión puede durar meses; la página exige a nuevos miembros el **10 % de ingresos procesados por Conservancy**, sin cuota mensual fija acreditada. Admisión/encaje y acuerdo concreto de MACE: **NO VERIFICADO** |

Las páginas se abrieron por lectura el 25/11/2026. SFLC anuncia pro bono en su
portada, pero ello no prueba que esté aceptando nuevos asuntos hoy. Conservancy
requiere estructura/membresía: no afiliar MACE solo para conseguir una consulta.
FSFE ofrece información abierta y voluntarios; no se presupone que todos sean
abogados. No se verifica ninguna tarifa comercial baja concreta y no se
presenta como tal un proveedor sin precios oficiales. El usuario puede buscar
clínicas universitarias/colegio profesional local, pero aquí no se recomienda
ningún nombre ni programa local sin verificar admisión y jurisdicción.

## Qué puede documentarse y qué sigue bloqueado

Puede quedar cerrado como **hecho documental**, sin revisor: decisión propia
MIT, titular declarado, presencia de LICENSE en main, fuentes/metadatos leídos,
conteos y correspondencia técnica de archivos, distinción source/binario,
lista de carencias, borradores y el resultado del experimento aislado.
No convertir esos hechos en “compatibilidad jurídica aprobada”. La fuente
pública permanece disponible según decisión del usuario, sin que ello
certifique derechos sobre cualquier componente externo agregado.

Bloqueados para una nueva release binaria: acreditación de los componentes
pendientes, permisos/obligaciones del setup y módulos PawnIO, avisos finales,
materialización de textos/fuentes cuando corresponda y **cierre de revisión
cualificada**. Un test que pasa, una exclusión o una firma no elimina el bloqueo.
Aprobación del dossier no es aprobación de distribución ni revisión ya realizada.
La admisión SignPath, configuración y firma son gates adicionales independientes.
Las releases históricas detectadas en la auditoría pública requieren decisión
separada del usuario; no se borran ni se reclasifican automáticamente.

## Decisiones del usuario y parada

Elegir vía y profesional/jurisdicción, acordar presupuesto si procede y
autorizar expresamente cualquier contacto/envío futuro. Después revisar el
resultado y los cambios que exija. No hace falta aportar datos personales
al agente ahora. No se avanza a solicitud, política o pipeline de firma,
ni a 3.2/3.3; no se ha contratado, contactado ni enviado nada.
