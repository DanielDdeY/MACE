# Auditoría del Paso A — resumen

Fecha: 2026-11-23. Revisión propia, no auditoría externa ni asesoría legal.

## a. Veredicto y cifras

**Informe corregido; todavía no listo para solicitar firma a SignPath.**
Se encontraron **9 hallazgos: 1 error, 1 afirmación desactualizada y 7 carencias**, con 9 correcciones documentadas.
Inventario: **103 filas** (34 principales + 23 avisos Java + 46 avisos .NET); incluye agregaciones.
Fuentes por fila: local **74 (71,84 %)**; metadato **8 (7,77 %)**;
URL oficial **5 (4,85 %)**; sin evidencia suficiente **16 (15,53 %)**.
Esos porcentajes describen fuentes, no cumplimiento legal del conjunto.
Imagen: **370/370 intactos, 0 sin componente**; helper **202/202** y publicado **205/205**.
Corrección principal: **10 JAR, no 11**. Runtime: **11 módulos**.
Versiones: **45 cotejos coinciden, 0 discrepancias; 58 filas NO VERIFICADAS**.
Muestreo de 13: **9 cotejos sin contradicción en alcance limitado, 4 NO VERIFICADOS**.
Once columnas: **103/103**; conflictos completos: **9/9**; datos locales y lenguaje dubitativo: **0**.
Fuentes: **26 URLs abiertas y 1 intento fallido**, fechado y no usado como evidencia.
MIT sigue **confirmada por el usuario**; no se reabre C1 ni se crea LICENSE.
Commits: arnés `b71dc07`; corrección separada titulada `docs: corrección tras auditoría`.

## b. Bloqueos para la solicitud a SignPath

| Componente | Motivo | Evidencia | Opción recomendada |
|---|---|---|---|
| Inno Setup | No listado OSI; admisión no acreditada | [SPDX](https://spdx.org/licenses/InnoSetup.json) y [OSI](https://opensource.org/licenses) | Consultar; conservar elección hasta decisión |
| Oracle Java 22.0.2 | Runtime NFTC, no acreditado para el programa | LICENSE empaquetado y [NFTC](https://www.oracle.com/downloads/licenses/no-fee-license.html) | Concretar migración OpenJDK ya aprobada aparte |
| Mono.Posix 1.0.0 | Enlace abre main de Mono, sin correspondencia histórica acreditada | Nuspec y [enlace](https://go.microsoft.com/fwlink/?linkid=869050) | Acreditar términos/fuentes; exclusión solo con propuesta aprobada |
| PawnIO y módulos | Licencias/fuentes exactas del setup y recursos pendientes; alternativas anunciadas | Paquete fijado y [fuente actual](https://raw.githubusercontent.com/namazso/PawnIO/master/README.md) | Completar dossier, conservar bytes/firma ajenos |
| IGCL, CRT y dependencias del sistema | Restricciones/excepción de bibliotecas de sistema sin resolver | Cabecera local y [Microsoft](https://learn.microsoft.com/cpp/windows/redistributing-visual-cpp-files) | Revisión cualificada y consulta de alcance |
| Otros componentes de runtimes | Avisos/identificadores no prueban todas las porciones binarias | Textos locales y listas | Completar correspondencia y obligaciones |
| Preparación de MACE | Licencia formal, política, release y metadatos firmables aún pendientes | Estado del repositorio/diseño | Avanzar por gates B–E y Fase 3 tras aprobación |

## c. Riesgos no bloqueantes para revisar el Paso A

- W3C: [SPDX](https://raw.githubusercontent.com/spdx/license-list-data/main/json/licenses.json) lo marca aprobado; el ID exacto no figura en la tabla OSI actual. No implica revocación.
- Los avisos .NET/Java son agregados: no prueban carga de cada componente en Windows.
- La búsqueda propia de HTTP/socket dio 0; actividad dinámica/terceros sigue sin comprobarse.
- El código 0 del arnés acredita comprobaciones mecánicas, no aprobación legal ni del programa.

## d. Decisiones necesarias — máximo cinco

1. Aprobar el inventario corregido o pedir otra revisión; el Paso B sigue detenido.
2. Inno: mantenerlo sujeto a consulta o autorizar estudiar NSIS; no se cambia automáticamente.
3. Mono: priorizar acreditar licencia histórica o autorizar una propuesta separada de exclusión.
4. JDK: concretar distribución/build/JavaFX mediante propuesta separada; no modificar la imagen actual.
5. Encargar/completar revisión cualificada de redistribución antes de publicar; no equivale a comprar certificados.

## e. Preguntas para SignPath — no enviadas

- ¿Admiten motor/desinstalador Inno y cómo califican ese código redistribuido?
- ¿Admiten setup PawnIO intacto y qué requieren de fuentes, excepción y licencias alternativas?
- ¿Qué consideran bibliotecas de sistema: CRT/VC/UCRT, cabecera IGCL y NVML/IGCL externos?
- ¿Cómo tratan los subcomponentes de runtimes no listados por OSI?
- ¿Aceptan un único responsable para autoría/revisión/aprobación y qué MFA exigen?
- ¿Qué release previa, metadatos comunes y aprobaciones multietapa necesitan, excluyendo firmas ajenas?

## f. NO VERIFICADO y parada

Licencias/alcances sin acreditar; ausencia de doble licencia comercial (103 filas);
58 versiones; carga/exclusión Mono; fuentes exactas PawnIO/recursos; actividad dinámica;
admisión, firma y MFA SignPath; nuevo JDK/instalador y verificaciones físicas pendientes.
No se ejecutó ni compiló el producto ni se alteraron imagen, manifiestos o avisos.
**Detenido: sin Paso B, sin envíos y sin publicación.**
