# E — Lista actualizada de preguntas para SignPath, NO ENVIADA

Fecha: **2026-11-25**. Actualiza como documento nuevo la sección de preguntas
del inventario provisional, sin tocar ese informe histórico. **Solo preguntas**:
no solicitud, formulario, cuenta, política operativa, pipeline o dossier de
admisión del antiguo Paso E. La solicitud la presentará el usuario cuando
lo decida. No contactar ni transmitir archivos por el agente.

## Contexto que puede acompañar la consulta del usuario

MACE propio MIT; preparación de candidata Temurin 25/JavaFX 27.0.1.1 pendiente
de aprobación/validación. DLL 1.5.0, ABI 1.5, helper 1.1.1, wire 2; metadatos
MIT todavía propuestos. PawnIO intacto del editor, sin refirma. Dos variantes
Mono sin elección ni recomendación; exclusión bloqueada hasta físicas 0 y su
licencia abierta en ambos casos. No afirmar que el paquete cumple ya las
condiciones, ni que MIT propia resuelve las de dependencias.

Usuario acepta precedentes **solo como contexto**: Stellarium usa plantillas
Inno y figura en la lista Foundation; VSCodium publica cadena de setup/firma y
figura en ficha oficial. Ver [evidencia fechada](FASE3-EVIDENCIA-INNO-SIGNPATH.md).
No afirmar que esos proyectos tienen el mismo payload, versión de motor,
licencias o excepciones que MACE ni que eso garantice aceptación.
NSIS no se adopta; no se vuelve a proponer migración como decisión pendiente.

## Preguntas concretas y respuesta que se necesita

| ID | Pregunta para el usuario presentar | Qué debe aclarar la respuesta |
|---|---|---|
| Q1 | Stellarium y VSCodium son precedentes documentales de Inno + Foundation. ¿Qué reglas actuales y evidencia aplican **a MACE** para el motor/loader de Inno redistribuido dentro del EXE, dada la licencia concreta de la versión que se fije? | No una aprobación genérica de “Inno”: versión/licencia, tratamiento de componentes generados y condición OSI/excepción aplicable |
| Q2 | ¿Cómo debe tratarse el **desinstalador generado de Inno**, incluido cuando solo se materializa durante la instalación: procedencia, licencia del motor, posible firma del desinstalador, aprobación y verificación de origen? | Si admite el formato/herramienta, qué puede firmarse, en qué etapa y qué evidencia de build exige; no se asume que firmar setup firme también uninstaller |
| Q3 | ¿Se pueden incluir **binarios de terceros que no se firman ni se refirman** con el certificado del proyecto —en particular PawnIO oficial intacto y dependencias del helper/JDK/JavaFX— dentro de un instalador cuyo contenedor sí se firme? | Distinguir admisión del paquete, cobertura de firma del contenedor, exclusiones de firma y derechos sobre cada tercero. No afirmar que excluir de firma exime de licencia/procedencia |
| Q4 | Para PawnIO setup 2.2.0/FileVersion 2.2.0.0, driver y módulos embebidos de LHM 0.9.6, ¿qué correspondencia de fuentes/licencias/excepción, avisos y permisos necesitan? ¿Qué efecto tienen las licencias comerciales alternativas ofrecidas por el editor aunque MACE no contrate una? | Evidencia exacta por artefacto y criterio de doble licencia; no derivar la licencia de todo el setup de un archivo fuente actual |
| Q5 | ¿Qué configuración de artefactos permite dejar **PawnIO byte a byte intacto**, conservar su firma upstream y verificar su pin sin que la firma de MACE alcance su interior? | Modelo técnico admitido e identificación por ruta/artefacto; no enviar binarios para experimentar con su servicio |
| Q6 | ¿Cómo evalúan VC/UCRT redistribuidos con JavaFX/JDK, CRT enlazado estáticamente, cabecera IGCL versionada con términos Intel y carga de IGCL/NVML del driver sin copiarlos? | Qué califica como System Library para su política y evidencia necesaria, separado de la revisión jurídica de redistribución |
| Q7 | ¿Qué revisarían de Temurin GPL con Classpath y de JavaFX con sus nativos/licencias, además del código MIT propio? | Alcance de componentes y fuentes/avisos. No pedir exención para Oracle de la imagen vieja ni presentarla como nueva base |
| Q8 | Si Mono sigue en restore pero se excluyen sus tres activos del payload, ¿qué evidencia exige el programa sobre dependencia de build y su licencia? Si se conserva, ¿qué evidencia exacta falta para admitir sus binarios? | No asumir que ExcludeAssets resuelve licencia; no afirmar aprobada ninguna variante antes de pruebas/revisión |
| Q9 | ¿Admiten que el mismo usuario del proyecto asuma autoría, revisión y aprobación manual, o necesitan separación de personas/permisos? ¿Qué MFA requiere la futura cuenta SignPath? | Roles y permisos exigibles. MFA GitHub declarado no acredita una cuenta SignPath existente; DCO no adoptado por el proyecto |
| Q10 | Las releases v1.0.0/v1.1.0-rc3/v1.1.0 son pre-LHM según el usuario, no son base; el usuario prevé pasarlas a borrador y falta su confirmación. ¿Qué evidencia de reputación/release en la forma nueva requiere el programa, dada la prohibición propia de publicar terceros antes del cierre legal? | Secuencia admisible sin usar releases viejas como prueba del producto nuevo ni publicar para eludir el gate. No se presupone aceptación de builds privados |
| Q11 | ¿Qué campos de ProductVersion/FileVersion/CompanyName necesitan coherencia entre apphost/jpackage, helper y DLL? ¿Pueden conservarse versiones separadas 1.1.1/1.5.0 y ABI/wire congelados, con los metadatos propios MIT/titular correctos? | Campos concretos, no uniformar arbitrariamente ABI/protocolo/semver o atribuir código ajeno al titular propio |
| Q12 | ¿Qué orden de aprobaciones y pruebas de procedencia admiten cuando una firma modifica el helper, después su manifiesto se fija en DLL y esta en launchers? | Procedencia reproducible por etapas y cuáles PE generados son firmables, sin modificar/refirmar terceros ni permitir variables de desarrollo en producción |
| Q13 | ¿Qué formatos de artefacto y comprobaciones de Inno permiten verificar el contenido y excluir componentes ajenos de firma, incluido desinstalador, sin prometer que el servicio inspecciona automáticamente cualquier EXE? | Limitaciones de formato/inspección y evidencia solicitada. No inventar soporte de contenedores a partir de un precedente |
| Q14 | ¿Aceptan GitHub Issues como contacto público y qué datos requiere el formulario del representante? | Pedir solo lo necesario cuando el usuario presente solicitud. No publicar correo alternativo ni identidad legal por anticipado |

## Fuentes y límites

[Términos](https://signpath.org/terms), [lista de proyectos](https://signpath.org/projects.html)
y [ficha VSCodium](https://signpath.org/projects/vscodium/) consultados por
lectura pública el 25/11/2026. Evidencia de pipelines/plantillas en el documento
enlazado, con su fecha y límites; no descargados instaladores ni comprobadas
firmas de releases para esta actualización. Preguntas abiertas, **ninguna
respuesta recibida ni aceptación de MIT/Inno/PawnIO para MACE acreditada**.

No solicitar una excepción jurídica a SignPath como sustituto de revisión
cualificada; su decisión privada y el derecho de redistribución son distintos.
THIRD-PARTY-NOTICES sigue borrador y la regla de no nuevas releases con terceros
hasta cierre legal permanece. El cambio a borrador de releases históricos lo
hará el usuario, no se afirma hecho. No adjuntar binarios/datos personales.

## Decisión y parada

Usuario revisa/ajusta preguntas y decide cuándo presentarlas, después de los
gates técnicos/jurídicos pertinentes. No falta elegir MIT, titular, JavaFX
provisional, NSIS o DCO: sus decisiones ya constan. No avanzar al antiguo
C/D/E de firma ni a 3.2/3.3 por aprobar esta lista.
