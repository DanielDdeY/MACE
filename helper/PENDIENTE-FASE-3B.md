# Pendientes de 3B y revisión previa al Paso C de 3.1c

Fecha: **2026-11-25**, America/Lima. Partida **d44f7bc**, rama
`LHM_implementado`; árbol seguido limpio al comenzar. Se conservan sin
incorporar los documentos/logs locales sin seguimiento. No producto ejecutado,
descargas de artefactos, builds, cambios de servicios, registro o imagen.

Este archivo consolida deuda del **Paso B de 3.1c** y sus propuestas posteriores;
no denomina “3B” al instalador o al consumidor del 3010. El usuario autoriza
ahora avanzar al **Paso C: política de firma y README**, pidiendo primero
dudas/sugerencias. Esa instrucción posterior prevalece sobre las paradas
históricas que decían C no autorizado. D/E del prompt original y 3.2/3.3
**no** quedan autorizados por avanzar a C.

## Ya resuelto, no volver a solicitar

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

## Pendientes, evidencia y qué bloquean

| ID | Pendiente / estado exacto | Evidencia o acción para cerrarlo | Qué bloquea |
|---|---|---|---|
| B01 | Metadatos MIT de POM y tres csproj **PROPUESTOS, NO APLICADOS** | Aprobar diff de empaquetado antes de cambiar bytes; medir nueva cadena de hashes | Declarar esos metadatos integrados; regenerar candidata con ellos |
| B02 | THIRD-PARTY-NOTICES **BORRADOR** | Acreditar componentes/versiones del payload final, textos, avisos y fuentes aplicables; revisión cualificada | Avisos definitivos y distribución binaria |
| B03 | Inventario legal **PROVISIONAL**, no dictamen | Revisión de las 16 filas históricas con evidencia insuficiente y cuatro cotejos NV; LICENSE local ya incorporada no acredita todo el paquete | Cierre legal y solicitud respaldada como conforme |
| B04 | Licencia/fuentes históricas Mono 1.0.0 y dos activos nativos **NO VERIFICADO** | Evidencia del paquete exacto, correspondencia de fuentes y revisión; no asignar licencia por nombre | Dar por acreditada su redistribución o el cumplimiento SignPath |
| B05 | Cuatro ensayos Mono comunicados: **código 2**, consolas normal y administrador | Diagnosticar y repetir con criterios preservados tras aprobación del parche de motivos; no inferir qué token tuvo cada caso sin su log | Afirmar éxito/fallo de la exclusión a partir de esos códigos |
| B06 | Detección del helper en el arnés **NO VERIFICADO** | Log local Read normal: 135 muestras sin PID reconocido, 134 con CPU t/p=9; baseline no cumplida. No prueba que el helper estuviera ausente. Causa concreta y otros tres casos no acreditados | Considerar esos ensayos físicos aprobados |
| B07 | Parche MONO-MOTIVOS **SOLO PROPUESTO** | Aprobar/aplicar aparte diagnóstico seguro; no cambiar filtros, plazos, criterios ni producto para hacer pasar pruebas | Obtener motivo preciso nuevo; el detector aún no está corregido |
| B08 | Exclusión Mono **BLOQUEADA para candidata**, ninguna variante recomendada | Resultados físicos con código 0 requeridos por el usuario; después elección explícita entre CONSERVAR/EXCLUIR y aprobación del diff | Integrar exclusión en cualquier candidata; la licencia no se cierra por excluir |
| B09 | Build concreta Temurin **25.0.5.1+2 PROPUESTA** | Aprobar descarga/build, verificar ZIP e integridad/firma en ubicación aislada; no modificar JDK instalado, JAVA_HOME o PATH | Dar migración por implementada o usarla como base final |
| B10 | JavaFX/AtlantaFX y Mockito/Byte Buddy nuevos **NO VERIFICADO por ejecución** | dependency:tree antes de compilar, regresión UI/FFM/tests; 25.0.5.1 solo alternativa a presentar si 27 falla de forma no corregible, nunca cambio automático | Declarar compatible o verificada la combinación elegida |
| B11 | Cierre completo Maven/plugins/SDK **NO VERIFICADO** | Aprobar resolución e índice cerrado; 28 entradas NuGet propuestas no equivalen a todo el tráfico de un caché frío; no descargas desconocidas implícitas | Declarar lista de transferencia exhaustiva o construir offline sin faltantes |
| B12 | Candidata unificada **SOLO DISEÑADA**, diffs alternativos sin aplicar | Elegir variante cuando corresponda, aprobar insumos/diffs; construir una vez en raíz nueva, medir manifiestos/pins y comprobar | Afirmar que existe candidata validada o reemplazar imagen congelada |
| B13 | Regresión de candidata **NO EJECUTADA** | Objetivos históricos: helper 105; CTest 76/77; Java 347 + 1 omitida histórica; payload 8 y rechazo dev; protección y auditoría 181/0. Registrar cifras reales, contexto y diferencias | Aprobación técnica de nuevos bytes. No convertir objetivos en resultados |
| B14 | Clon limpio con espacios y comandos README **NO VERIFICADO** | Aprobar primero operaciones/insumos necesarios y documentar ejecución exacta; comandos aún no probados deben llevar esa marca | Publicarlos como instrucciones comprobadas, no redactar C con límites explícitos |
| B15 | PawnIO setup/módulos, CRT/IGCL/NVML y subcomponentes Java/.NET **REVISIÓN PENDIENTE** | Fuentes/permisos y obligaciones de versiones exactas, biblioteca de sistema vs binario copiado; revisión cualificada | Distribución jurídica conforme; firma upstream no sustituye permiso |
| B16 | Inno, motor/desinstalador y terceros sin refirma **ADMISIÓN NO VERIFICADA** | Preguntas preparadas; usuario decide presentarlas. No enviada solicitud ni atribuir respuesta a precedentes | Declarar aceptado el paquete o prometer cobertura de firma |
| B17 | Revisor cualificado **NO DESIGNADO** | Guía y dossier existen, contenido aprobado; usuario designa/autoriza cualquier contacto | Cierre jurídico y nuevas releases binarias |
| B18 | Releases v1.0.0, v1.1.0-rc3, v1.1.0 a borrador **PENDIENTE DE CONFIRMACIÓN** | Usuario dice que son pre-LHM y no base; él realizará el cambio. No se inventaría su contenido ni modifica el remoto | Afirmar que ya están ocultas o usarlas como evidencia del producto nuevo |
| B19 | 2FA/cuenta/admisión/configuración real SignPath **NO VERIFICADO** | Solo fases posteriores y acciones expresamente autorizadas del usuario | Afirmar firma activa, solicitud presentada o cumplimiento técnico del servicio |
| B20 | Privacidad integral/dinámica de terceros **NO VERIFICADO** | Existe auditoría estática del código propio y escrituras locales; no captura completa de red de runtimes/drivers/instaladores | Promesas absolutas de ausencia de red o de todo tratamiento de datos |
| B21 | VM limpia, HVCI, Intel/NVIDIA con combinación nueva, 3010 real y recuperación PawnIO por parada/instalación **NO VERIFICADO** | Pruebas futuras del usuario con entorno y permisos aprobados. Parada rechazada con 1052 no prueba recuperación real | Cobertura física/publicación como validada; no bloquea redactar política provisional |

El experimento aislado anterior publicó sin tres activos Mono y pasó 105
checks de contrato; **no** equivale a las cuatro físicas código 0 pendientes.
Los conteos 202/199 del experimento no se adjudican a una candidata no construida.
La referencia de 370 archivos conserva sus resultados históricos de verificación,
no es una ejecución nueva de regresión en esta preparación del Paso C.

## Dudas previas de C y sugerencias concretas

### 1. Faltan las fuentes que el propio Paso C exige

El Paso C.1 de `3.1c.md` dice: «Parte de CODE_SIGNING_POLICY.borrador.md y
README.borrador.md». No se localizaron esos dos borradores en el repositorio,
Downloads, Desktop, adjuntos locales ni fuentes sincronizadas revisadas.
README.md actual **sí** existe, pero no se presenta como README.borrador.md.
**Decisión necesaria:** facilitar esos textos/ruta o autorizar redactarlos
desde cero aprovechando README actual y los datos ya confirmados. No inventar
el contenido de un borrador ni atribuir al usuario frases ausentes.

### 2. Página principal frente a descargas, ramas y publicación

README actual no contiene política de firma ni instrucciones completas de build.
Por GET de GitHub el 25/11 se comprobó que **docs/index.html existe en main**
y no menciona Code signing policy; no existe en este checkout de LHM_implementado.
No se importó ese archivo, hizo merge, editó el sitio ni consultó binarios.
La URL y despliegue vigente de la página de descargas **NO VERIFICADO** aquí.

Sugerencia: preparar en la rama README + CODE_SIGNING_POLICY.md y el texto/enlace
para la página de descargas; coordinar después su integración/publicación por
el usuario. **Decisión necesaria:** confirmar si la página destino es
docs/index.html de main u otra. No se crea otra web, copia main o publica nada
como efecto implícito de redactar la política.

### 3. Estado honesto y atribución condicional

No consta confirmación del usuario de una solicitud presentada ni admisión.
Usar «Preparación de solicitud; aprobación no acreditada; builds de prueba
sin firma de MACE», distinguiendo intención de hechos. El literal que pide
SignPath sobre quién proporciona firma/certificado debe aparecer **solo como
atribución prevista, condicionada a aceptación**, no como servicio activo.
No quitar [VERIFICAR] por cambiar la etiqueta; cerrar cada punto con evidencia
o trasladarlo expresamente a NO VERIFICADO.

### 4. Lista de artefactos y cadena todavía prevista

La política puede enumerar artefactos propios previstos, excluidos y pendientes
sin afirmar que el instalador final ya existe. Coincidir con el diseño 3.6:
helper final → manifiesto → DLL con pins → paquete/launchers → instalador.
No firmar/refirmar terceros ni la imagen congelada. Motor/desinstalador Inno,
PE generados y restricciones de metadatos siguen sujetos a consulta; no
prometer firma de JAR, de cada dependencia o de todo lo contenido en el setup.
Implementar hooks o workflow sería otro paso, no C.

### 5. README y privacidad sin adelantar la candidata

Separar estado actual de la propuesta Temurin/JavaFX; no describir migración,
exclusión Mono o metadatos como integrados. Comandos sin prueba en clon limpio:
**NO VERIFICADO**, aunque hayan pasado parser o git apply --check.
No descargar, compilar o ejecutar producto para eliminar esa marca sin la
autorización pendiente. Política de privacidad limitada a lo acreditado:
métricas/caja negra/preferencias locales y alcance de auditoría estática,
sin promesa universal sobre terceros o ausencia de todo tráfico.

## Referencias y alcance de esta entrega

- [Decisiones vigentes](FASE3-DECISIONES-CANDIDATA.md).
- [Diagnóstico Mono](FASE3-DIAGNOSTICO-MONO-MANUAL.md) y
  [plan de candidata](FASE3-PLAN-CANDIDATA-AISLADA.md).
- [Descargas propuestas](FASE3-CANDIDATA-DESCARGAS.md) y
  [preguntas no enviadas](FASE3-PREGUNTAS-SIGNPATH.md).
- [Guía sin revisor](FASE3-GUIA-REVISION-SIN-REVISOR.md) y
  [inventario de escrituras](FASE2-INVENTARIO-ESCRITURAS.md).
- `3.1c.md` Paso C y `prompt_mace_fase3.md` 3.6 releídos como instrucciones
  del usuario; los textos históricos no revocan decisiones posteriores.

Solo se añade este registro documental. Ningún informe histórico, README,
política, plantilla, workflow, metadato, fuente o artefacto de producto se
modifica. No tests ni builds nuevos. Revisar dudas antes de redactar C;
después completar C y detenerse para aprobación, sin avanzar a D/E ni 3.2/3.3.

## Actualización del Paso C, 25/11/2026

El usuario aportó README.md como borrador, autorizó crear CODE_SIGNING desde
cero, confirmó docs/index.html como página de descargas y aprobó las tres
sugerencias de estado/atribución, alcance de artefactos y marcas NO VERIFICADO.
**Las dos dudas previas quedan resueltas**; los apartados anteriores conservan
su contexto de preflight y no son solicitudes todavía pendientes.

Se redacta CODE_SIGNING_POLICY.md, se adapta README.md y se prepara localmente
docs/index.html a partir de main, sin merge ni publicación. Ver
[entrega C](FASE3-PASOC.md). La atribución al servicio es condicional y los
enlaces de la política en la web requieren integración posterior por el usuario.

Los pendientes B01–B21 no se cierran por esa redacción. En particular, no se
aplicaron metadatos MIT/Mono/JDK, no se resolvieron los cuatro códigos 2 de Mono,
no se eligió/recomendó una variante y no se probaron comandos en clon limpio.
Continúan la revisión cualificada, avisos borrador, consultas y admisión pendientes.
No se acredita firma, solicitud presentada, nueva release ni cambio de las
releases históricas a borrador. No avanzar a D/E del prompt de firma ni 3.2/3.3.

**Pendiente adicional detectado por inspección en C:** el workflow CI local
solo contiene tests Java y build nativo básico; no publica el helper ni pasa
el manifiesto requerido por producción. El workflow de instalador citado en
el README aportado no existe en esta rama. No se ejecutó Actions ni se
corrigió CI. Alinear configuración y acreditar su cobertura queda fuera de C;
no presentar el borrador del README como evidencia de pipeline operativo.
