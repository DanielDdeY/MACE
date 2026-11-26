# Estado de 3B y del Paso C de 3.1c

Actualización documental: **2026-11-26**, America/Lima. Partida de esta
actualización: **3767f3d**, rama `LHM_implementado`. Registro original del
25/11/2026, partida d44f7bc. Solo se actualiza este Markdown: no se ejecutan
pruebas, builds, descargas ni producto y no se cambia la imagen de referencia.

Se distinguen **7 puntos B resueltos** y **14 todavía abiertos**, además del
pendiente de CI. «Implementado en candidata aislada» no significa integrado en
el checkout principal, distribuido, firmado o validado físicamente.

## IMPLEMENTADO

### Puntos B resueltos, con alcance exacto

| ID | Implementado / resuelto | Evidencia y límite |
|---|---|---|
| B01 | [x] Metadatos MIT del POM y los tres csproj aplicados **solo en la candidata aislada** | Commit candidato **0352f58**; helper publicado y DLL compiladas. La cadena completa de hashes de una imagen nueva sigue pendiente en B12/B13. El producto y la imagen de referencia no se sustituyen. |
| B05 | [x] Los cuatro ensayos físicos Mono terminaron con **código 0, confirmado por el usuario** | Read, Read RequireCpu, Reconnect RequireCpu y CloseEarly. Superan los cuatro códigos 2 históricos; no se inventan logs, tokens individuales, mediciones ni hardware. Son ensayos del experimento anterior, no de la nueva candidata. |
| B06 | [x] Detección del helper corregida en el arnés | Commit **530f0ec**, equivalencia estricta de rutas DOS con/sin prefijo extendido; 35 comprobaciones documentadas y cuatro ensayos físicos posteriores con 0 comunicados por el usuario. No se atribuye a ese defecto la causa individual de cada código 2 histórico. |
| B07 | [x] MONO-MOTIVOS autorizado y aplicado al script | Commit **808d3bc**; motivos/etapas de salidas ordinarias 2 y contadores de descubrimiento, sin cambiar criterios, filtros ni plazos. No volver a aplicar el parche histórico. |
| B08 | [x] Exclusión Mono elegida y aplicada **en la candidata aislada** | Elección explícita del usuario tras los cuatro ceros; MONO-EXPERIMENTO-COMPLETO aplicado en **0352f58**. Ambas publicaciones del helper carecen de los tres binarios Mono; no existe todavía imagen candidata. La licencia histórica no queda acreditada por excluirlos. |
| B09 | [x] Temurin **25.0.5.1+2** obtenido, verificado y usado de forma aislada | ZIP y archivos extraídos cotejados; Maven 3.9.16 y compilación Java development con esa JVM. Sin cambiar JDK instalado ni JAVA_HOME/PATH del usuario o máquina. No acredita UI o migración final de la imagen. |
| B18 | [x] Confirmación del usuario recibida: releases v1.0.0, v1.1.0-rc3 y v1.1.0 marcadas como borrador | Confirmación explícita en prompt_mace_cierre_pendientes_3b.md y registro en FASE3-ETAPA1-MONO.md. No se consultó de nuevo el remoto, no se inventarían sus binarios y siguen siendo anteriores a LHM. |

### Entregas realizadas y avances parciales de puntos que siguen abiertos

- [x] **Paso C documental:** README.md adaptado, CODE_SIGNING_POLICY.md
  redactado desde cero y docs/index.html preparado localmente. Commit
  **4cf5161**; las dudas del borrador y de la página destino quedaron resueltas
  por el usuario. Publicación web, firma y admisión SignPath siguen pendientes.
- [x] **B10, avance técnico:** dependency:tree con el POM parcheado terminó con
  0 y confirmó JavaFX 27.0.1.1, AtlantaFX 2.0.1, Mockito 5.20.0 y Byte Buddy
  1.17.7; recursos y test-compile development terminaron con 0. La compatibilidad
  de ejecución/UI/FFM y JUnit no está aprobada.
- [x] **B11, lote y restore realizados:** 192 JAR y 28 nupkg descargados con
  lista cerrada y verificados con los límites de checksum/firma documentados;
  restore bloqueado de la candidata con código 0. La resolución de Surefire
  sigue incompleta; no se declara exhaustivo el cierre Maven/plugins.
- [x] **B12, parte construida:** copia candidata nueva en
  `native/build/candidata-20261126-02/source`, commit **0352f58**; parches
  Mono/MIT/JDK/JavaFX/Mockito y DISABLE-TRANSITIVE aplicados solo allí.
  Helper publicado y DLL dev/prod compiladas. Las dos publicaciones false/true
  tienen **204 archivos cada una y 0 diferencias**; 202 entradas publicadas y
  199 de runtime. Falta construir y validar la app-image.
- [x] **B13, regresión parcial en consola normal:** helper **105/105**, CTest
  desarrollo **76/76** y producción **77/77**. Diario de 611 identidades y
  0 supervivientes al cierre. Java ejecutó **0 pruebas** por fallo de resolución;
  payload, protección, auditoría y administrador siguen pendientes para esta
  campaña. No se adjudican resultados históricos a bytes nuevos.
- [x] **Verificación local adicional NETCore 10.0.12:** integridad y matemática
  de firma comprobadas; 188 archivos coinciden con el runtime instalado,
  0 difieren y 1 no tiene contraparte. Confianza de cadena/revocación y checksum
  independiente del paquete completo siguen **NO VERIFICADOS**.
- [x] **Propuestas de autenticación de firmas y revisión de vulnerabilidades**
  documentadas, sin ejecutarlas, importar claves ni consultar sus dominios.

### Decisiones ya registradas, no volver a solicitar

- **MIT propia**, incluidos derivados cerrados; titular público DanielDdeY.
  LICENSE incorporado sin editar, **1067 bytes** iguales al adjunto y al blob
  de main, SHA-256 esperado coincidente. Commit **7be9ab0**. La comprobación
  de esa entrega no garantiza finales de línea después de un checkout futuro.
- El mismo usuario asume autoría, revisión y aprobación. Contacto público por
  GitHub Issues. 2FA de GitHub y Gmail declarada por el usuario, no auditada
  técnicamente. No publicar correo alternativo ni extraer identidad de Git.
- Temurin **25 LTS** como línea; JavaFX **27.0.1.1 JAR** provisional, AtlantaFX
  **2.0.1**; Mockito **5.20.0** con Byte Buddy **1.17.7**, sujeto a árbol efectivo.
- Inno se mantiene **sujeto a consulta**; precedentes Stellarium/VSCodium son
  contexto, no aceptación de MACE. NSIS no adoptado. DCO **no por ahora**.
- CONTRIBUTING y plantillas permanecen en rama de trabajo; PR/push/merge a
  main los decide el usuario después. No se abren en esta entrega.
- Modelos CPU/GPU y mediciones pueden publicarse según el usuario. Continúan
  excluidos perfiles, identidades locales, correos, SID y datos de máquina.
- No nuevas releases con binarios de terceros hasta cerrar revisión legal;
  el código fuente público no se ve afectado por esa regla.

## PENDIENTE

Las siguientes **14 filas conservan literalmente su redacción original**,
como solicita el usuario. Sus expresiones de estado son las del registro del
25/11; no anulan los avances parciales acreditados arriba. En particular:
B11 sigue abierto por resolución incompleta; B12 por falta de imagen validada;
B13 por regresión incompleta. No interpretar «solo diseñada» o «no ejecutada»
de esas filas históricas como negación de las compilaciones y suites parciales
ya realizadas. No se cambian aquí sus requisitos, acciones ni bloqueos.

**Bloqueo actual de B11/B12/B13:** falta
`commons-io:commons-io:jar:2.15.1` en la caché aislada; Surefire sale con 1 antes
de JUnit y hay 17 advertencias de POM ausente. Etapa 4 detenida, sin descargas
nuevas ni app-image. Se espera autorización de un suplemento cerrado, sin
Maven online ni ampliaciones implícitas del lote.

| ID | Pendiente / estado exacto | Evidencia o acción para cerrarlo | Qué bloquea |
|---|---|---|---|
| B02 | THIRD-PARTY-NOTICES **BORRADOR** | Acreditar componentes/versiones del payload final, textos, avisos y fuentes aplicables; revisión cualificada | Avisos definitivos y distribución binaria |
| B03 | Inventario legal **PROVISIONAL**, no dictamen | Revisión de las 16 filas históricas con evidencia insuficiente y cuatro cotejos NV; LICENSE local ya incorporada no acredita todo el paquete | Cierre legal y solicitud respaldada como conforme |
| B04 | Licencia/fuentes históricas Mono 1.0.0 y dos activos nativos **NO VERIFICADO** | Evidencia del paquete exacto, correspondencia de fuentes y revisión; no asignar licencia por nombre | Dar por acreditada su redistribución o el cumplimiento SignPath |
| B10 | JavaFX/AtlantaFX y Mockito/Byte Buddy nuevos **NO VERIFICADO por ejecución** | dependency:tree antes de compilar, regresión UI/FFM/tests; 25.0.5.1 solo alternativa a presentar si 27 falla de forma no corregible, nunca cambio automático | Declarar compatible o verificada la combinación elegida |
| B11 | Cierre completo Maven/plugins/SDK **NO VERIFICADO** | Aprobar resolución e índice cerrado; 28 entradas NuGet propuestas no equivalen a todo el tráfico de un caché frío; no descargas desconocidas implícitas | Declarar lista de transferencia exhaustiva o construir offline sin faltantes |
| B12 | Candidata unificada **SOLO DISEÑADA**, diffs alternativos sin aplicar | Elegir variante cuando corresponda, aprobar insumos/diffs; construir una vez en raíz nueva, medir manifiestos/pins y comprobar | Afirmar que existe candidata validada o reemplazar imagen congelada |
| B13 | Regresión de candidata **NO EJECUTADA** | Objetivos históricos: helper 105; CTest 76/77; Java 347 + 1 omitida histórica; payload 8 y rechazo dev; protección y auditoría 181/0. Registrar cifras reales, contexto y diferencias | Aprobación técnica de nuevos bytes. No convertir objetivos en resultados |
| B14 | Clon limpio con espacios y comandos README **NO VERIFICADO** | Aprobar primero operaciones/insumos necesarios y documentar ejecución exacta; comandos aún no probados deben llevar esa marca | Publicarlos como instrucciones comprobadas, no redactar C con límites explícitos |
| B15 | PawnIO setup/módulos, CRT/IGCL/NVML y subcomponentes Java/.NET **REVISIÓN PENDIENTE** | Fuentes/permisos y obligaciones de versiones exactas, biblioteca de sistema vs binario copiado; revisión cualificada | Distribución jurídica conforme; firma upstream no sustituye permiso |
| B16 | Inno, motor/desinstalador y terceros sin refirma **ADMISIÓN NO VERIFICADA** | Preguntas preparadas; usuario decide presentarlas. No enviada solicitud ni atribuir respuesta a precedentes | Declarar aceptado el paquete o prometer cobertura de firma |
| B17 | Revisor cualificado **NO DESIGNADO** | Guía y dossier existen, contenido aprobado; usuario designa/autoriza cualquier contacto | Cierre jurídico y nuevas releases binarias |
| B19 | 2FA/cuenta/admisión/configuración real SignPath **NO VERIFICADO** | Solo fases posteriores y acciones expresamente autorizadas del usuario | Afirmar firma activa, solicitud presentada o cumplimiento técnico del servicio |
| B20 | Privacidad integral/dinámica de terceros **NO VERIFICADO** | Existe auditoría estática del código propio y escrituras locales; no captura completa de red de runtimes/drivers/instaladores | Promesas absolutas de ausencia de red o de todo tratamiento de datos |
| B21 | VM limpia, HVCI, Intel/NVIDIA con combinación nueva, 3010 real y recuperación PawnIO por parada/instalación **NO VERIFICADO** | Pruebas futuras del usuario con entorno y permisos aprobados. Parada rechazada con 1052 no prueba recuperación real | Cobertura física/publicación como validada; no bloquea redactar política provisional |

**Pendiente adicional detectado por inspección en C:** el workflow CI local
solo contiene tests Java y build nativo básico; no publica el helper ni pasa
el manifiesto requerido por producción. El workflow de instalador citado en
el README aportado no existe en esta rama. No se ejecutó Actions ni se
corrigió CI. Alinear configuración y acreditar su cobertura queda fuera de C;
no presentar el borrador del README como evidencia de pipeline operativo.

La revisión histórica de B03 no se reactiva por esta reorganización: se mantiene
la decisión posterior de concentrar la reauditoría en el payload real de la
candidata y no repetir las filas reemplazadas por la migración. B04 no queda
cerrado legalmente por excluir Mono de la publicación.

La integración/publicación de los enlaces de política de firma en la página
por el usuario sigue pendiente; la preparación local del Paso C no la acredita.
No se acredita firma activa, solicitud presentada, nueva release ni aprobación
legal. No avanzar a D/E del prompt de firma, 3.2 o 3.3 por esta actualización.

### Referencias de evidencia

- [Paso C documental](FASE3-PASOC.md).
- [Arnés Mono y corrección](FASE3-ETAPA1-MONO.md) y
  [resultados físicos comunicados y revisión](FASE3-ETAPA2-EVIDENCIA.md).
- [Decisiones sobre candidata y metadatos](FASE3-DECISIONES-METADATOS.md).
- [Lote cerrado y límites de verificación](FASE3-LOTE-CERRADO-VERIFICACION.md).
- [Propiedad de descargas transitivas y restore](FASE3-NU1101-PROPIEDAD.md).
- [Resultados parciales de Etapa 4](FASE3-ETAPA4-RESULTADOS.md) y
  [bloqueo Maven](FASE3-ETAPA4-BLOQUEO-MAVEN.md).
- [Firmas y vulnerabilidades: propuestas](FASE3-FIRMAS-VULNERABILIDADES-PROPUESTA.md).
- [Validación pendiente del usuario](FASE3-CANDIDATA-VALIDACION-USUARIO.md).
- [Preguntas no enviadas](FASE3-PREGUNTAS-SIGNPATH.md),
  [guía sin revisor](FASE3-GUIA-REVISION-SIN-REVISOR.md) e
  [inventario de escrituras](FASE2-INVENTARIO-ESCRITURAS.md).
