# Método de trabajo y entrega

Fecha: **26/11/2026, America/Lima**. Aplicar junto con [CONTEXTO.md](CONTEXTO.md).
Este método organiza el trabajo; no amplía permisos ni levanta una parada.

## 1. Avanzar por partes con una salida revisable

1. **Leer:** último encargo/aprobaciones, estado Git, contexto, pendiente
   concreto e informes relevantes. Confirmar commit/rama y cambios previos
   cuando se inicia una intervención. No descartar trabajo ajeno.
2. **Delimitar:** indicar objetivo del subpaso, archivos previstos, qué está
   autorizado y qué necesita decisión. No reiniciar toda la fase ni pedir de
   nuevo decisiones ya confirmadas. Si el usuario pide revisar antes de código,
   entregar análisis/diff y esperar; no aplicarlo por adelantado.
3. **Preparar:** resolver lo autorizado hasta obtener una propuesta concreta.
   Los cambios de producto con hashes fijados se presentan como diff completo,
   con efectos en manifiestos/pins, antes de aplicarlos si así se exige.
4. **Ejecutar solo ese alcance:** entorno aislado, versiones seleccionadas y
   verificaciones apropiadas. No instalar herramientas, activar red, cambiar
   versiones o relajar pruebas para resolver silenciosamente un fallo.
5. **Informar:** crear el Markdown de resultados separado, con evidencia y
   límites. Actualizar un tracker cuando se solicite; no reemplazar evidencia
   histórica por un relato que oculte fallos o decisiones anteriores.
6. **Detenerse en el límite:** cuando el subpaso pida aprobación, entregarlo y
   esperar. Una aprobación parcial no cierra lo que quedó NO VERIFICADO ni
   autoriza el siguiente paso entero. No avanzar por silencio o por tiempo.

Los trabajos independientes y permitidos pueden continuar mientras falta una
aclaración; no ejecutar acciones que dependan de una aprobación pendiente.
La regla actual de candidata exige detenerse ante un insumo offline faltante.

## 2. Estados de evidencia

| Estado | Cuándo usarlo | Qué debe acompañarlo |
|---|---|---|
| **VERIFICADO — AUTOMATIZADO** | Se ejecutó la comprobación y pasó con la salida esperada | Comando, versión/revisión, entorno, código, cifras, artefacto exacto y límite de cobertura |
| **VERIFICADO POR EL USUARIO** | El usuario comunicó explícitamente un resultado | Sus observaciones con alcance exacto; no inventar token, hardware, logs o mediciones |
| **NO VERIFICADO** | Falta ejecución, acceso, precondición o evidencia suficiente | Motivo, qué falta, cómo comprobarlo y quién debe hacerlo |
| **FALLO OBSERVADO** | Hubo ejecución y el resultado incumplió la condición | Código/aserción/causa observada, reproducción, impacto y lo que no se alcanzó a probar |
| **PENDIENTE** | Sigue abierto un trabajo o una decisión | Requisito previo, siguiente acción concreta y qué bloquea |
| **IMPLEMENTADO** | El cambio existe en el alcance indicado | Archivo/revisión/ubicación; distinguirlo de probado, aprobado o desplegado |

Para aprobación técnica siempre declarar **VERIFICADO o NO VERIFICADO** por
afirmación. Los otros estados describen avance o incidencias, no sustituyen
evidencia. Una implementación puede seguir NO VERIFICADA en UI o hardware.
«Aprobado por el usuario como base provisional» no significa conformidad legal.

Ejemplos de esta campaña:

- **VERIFICADO:** helper 105/105 y CTest dev 76/76, prod 77/77 en consola normal.
- **FALLO OBSERVADO:** Surefire salió 1 por resolución offline incompleta.
  **NO VERIFICADO:** regresión Java; se ejecutaron 0 casos, no una suite fallida
  de aserciones de MACE ni el objetivo histórico 347 + 1 omitida.
- **IMPLEMENTADO en copia aislada:** exclusión Mono. **NO VERIFICADO:** su
  ausencia en una imagen candidata final, porque esa imagen aún no existe.
- Cuatro ceros físicos del experimento: **VERIFICADOS POR EL USUARIO**; no
  trasladarlos a las laptops o al nuevo helper sin esos ensayos específicos.

## 3. No ampliar lo que prueba una evidencia

- Separar **simulación de hardware real**, consola normal de administrador,
  DLL development de producción, fuentes de imagen empaquetada y presencia
  de un archivo de uso efectivo por el proceso.
- Parser, `git apply --check`, tree o compilación no prueban ejecución de tests,
  apariencia de UI, instalador, licencia, seguridad completa o admisión externa.
- Código 0 requiere las precondiciones y aserciones correctas; código 2 del
  arnés Mono significa **NO VERIFICADO**, no «Mono funciona/no funciona».
- Una parada PawnIO rechazada con 1052 no prueba recuperación real. CloseEarly
  no acredita CloseWarming. Datos visibles después de reanudar no prueban que
  nunca se mostraran valores antiguos en el intervalo inmediato.
- Hash coincidente acredita igualdad/integridad respecto de esa referencia;
  no autentica por sí solo al editor. Firma matemática no equivale a cadena
  confiable/revocación comprobada; firma tampoco equivale a licencia suficiente.
- Observaciones visuales del usuario prevalecen sobre inferencias de logs.
  Los logs complementan lo que no vio; si hay discrepancia, registrar ambas
  evidencias y el límite, sin inventar una resolución.
- No atribuir al usuario una prueba por encontrar un log compatible. Pedir
  aclaración si afecta la conclusión; lo no confirmado queda NO VERIFICADO.

## 4. Un informe Markdown por avance

Guardar informes de avances en **archivos separados bajo `helper/`**, con
nombres descriptivos por fase/etapa. Ejemplo:
`FASE3-ETAPA4-SUPLEMENTO-METADATOS.md`; el nombre no autoriza ejecutar ese paso.
Referenciar el informe previo y dejar claro qué lo actualiza. No concentrar
todo en el chat ni en un archivo que cambie sin rastro.

Plantilla recomendada:

```markdown
# Fase / etapa / subpaso — objetivo

Fecha: AAAA-MM-DD, America/Lima.
Partida: commit, rama, estado del árbol y trabajo previo preservado.
Alcance autorizado y límite de parada.

## Resultado
Qué se consiguió y qué sigue bloqueado; sin presentar objetivos como resultados.

## Cambios por archivo
Archivo | cambio | motivo | producto o documentación | efecto sobre hashes/pins.

## Decisiones y diseño
Decisiones confirmadas, recomendaciones y alternativas descartadas con motivo.
No dar por aprobada una propuesta.

## Verificación
Estado | caso/comando | entorno/artefacto | esperado | observado | código/evidencia.
Comparar cifras reales con objetivos históricos y explicar diferencias.

## Riesgos y NO VERIFICADO
Punto | motivo | requisito previo | cómo verificar | responsable | qué bloquea.

## Procedimiento del usuario
Consola, directorio, variables locales, comandos exactos en orden,
resultado esperado, qué anotar y cuándo abortar. Marcar lo todavía no ejecutable.

## Decisiones necesarias y continuación
Opciones, recomendación razonada y permiso concreto pendiente.
Commits, si los hubo; ubicación de evidencia ignorada; DETENERSE en el límite.
```

Escalar la extensión al cambio: un ajuste documental no necesita un expediente
de compilación. Conservar los apartados que ayuden a revisar el resultado.
No incluir salidas largas, logs crudos o inventarios privados en el informe.

## 5. Cómo entregar recomendaciones y pedir aprobación

Por cada decisión real, explicar **problema → opciones → recomendación → coste
y riesgo residual → acción exacta que se propone**. Nombrar qué bloquea:
continuar un subpaso, empezar otra fase, fusionar o distribuir son límites distintos.

- Separar propuesta de autorización: «se recomienda» no es «se implementará».
- Antes de pedir aprobar un cambio, preparar el diff/receta revisable que se
  pueda completar sin la acción restringida. Si faltan datos, no inventarlos.
- Para descargas, entregar lista cerrada de URL, dominio, archivo, versión,
  destino y verificación de integridad/firma. No resolver padres o redirects
  desconocidos automáticamente. Un checksum SHA-1/MD5 se marca débil.
- Para consultas remotas, distinguir propuesta y URL realmente consultada;
  indicar fecha/fuente cuando se consulta. **La restricción de dominios incluye
  documentación.** No buscar para completar este tipo de guía sin permiso.
- Para cambios con pins, mostrar la cadena de reconstrucción necesaria. No
  editar un hash esperado para ocultar que cambió un insumo.
- Si hay incertidumbre legal, describir evidencia y preguntas acotadas para
  revisión cualificada; no dar asesoría legal ni inferir respuesta de SignPath.

**Recomendación vigente, no autorización:** revisar los 17 POM ausentes mediante
un suplemento expresamente autorizado; después presentar el suplemento binario
cerrado. Reanudar Java offline en la misma candidata, luego imagen/verificación y
ensayos del usuario. No repetir helper/publicaciones/CTest ya aprobados sin
cambios o una nueva preocupación que lo justifique. No probar otras fases para
sortear el bloqueo de la actual.

## 6. Pruebas y comandos para el usuario

- Indicar **consola exacta**, arquitectura, versión PowerShell/Java y directorio
  inicial. No confiar en JAVA_HOME/PATH, latest.txt, una DLL copiada o un JDK
  histórico si se requiere acreditar otro output.
- Proporcionar comandos copiables con rutas resueltas desde la raíz, opciones
  completas y captura inmediata de `$LASTEXITCODE`. Variables solo del proceso
  cuando estén autorizadas; nunca `setx` o cambios globales por conveniencia.
- Describir precondiciones, resultado esperado y datos a anotar, incluidos
  estados V2, PID/creación y mensajes visibles cuando correspondan.
- Separar pruebas normales, administrativas, visuales y físicas. No elevar ni
  ejecutar las del usuario para completar una tabla.
- No terminar procesos por nombre. Aplicar los límites de la excepción de
  simulaciones de [CONTEXTO.md](CONTEXTO.md), preflight y verificación de hijos
  vivos; registrar supervivientes y detenerse sin limpieza ajena.
- Si el comando requiere una imagen inexistente, caché incompleta, VM o
  precondición no reproducible, decirlo. Marcarlo como futuro/NO EJECUTABLE
  todavía, no presentarlo como validación lista ni pedir repetir pruebas inútiles.
- Conservar fallos de arnés y diferencias de entorno. Diagnosticar antes de
  corregir; no atribuir a producto, arnés o entorno una causa sin evidencia.

## 7. Evidencia, privacidad y Git

Informes compartibles saneados; logs extensos e índices de hashes en una raíz
de evidencia ignorada. Registrar lo necesario para reproducir sin publicar
perfil, usuario/equipo, correo, SID, secretos o hashes completos. Los modelos
CPU/GPU y mediciones sí están autorizados. No quitar pins del producto para
cumplir una regla de saneado de documentación.

No incluir logs, caches, binarios descargados, índices privados ni TXT locales
en commits, aunque algún log ya esté saneado. Verificar la selección explícita
de archivos; evitar `git add .` en este checkout. Respetar `sources/` de solo
lectura y no tocar el trabajo previo sin autorización.

Si corresponde guardar cambios, usar un commit por cambio lógico, sin reescribir
historial. Separar correcciones de un fallo de cambios de diseño o documentación
cuando facilite revisión. No hacer push, abrir PR o fusionar por haber hecho un
commit local. Una petición documental no obliga a incluir cambios pendientes
ajenos ni a crear automáticamente un commit.

Antes de entregar: revisar diff, enlaces locales, coherencia de estados y
ausencia de datos privados; ejecutar solo comprobaciones proporcionadas al
alcance. No repetir regresiones de producto por editar Markdown.

## 8. Entrega en el chat y cierre del subpaso

Dar un resumen breve con:

1. Resultado principal y bloqueo, si existe.
2. Cifras realmente verificadas y NO VERIFICADO relevante.
3. Enlaces a los Markdown entregados y comandos de usuario que procedan.
4. Archivos/commits y diferencias importantes, sin volcar el informe completo.
5. Decisión necesaria y punto exacto de parada, si el encargo lo exige.

Usar el límite de líneas que pida el usuario; en esta campaña se han pedido
resúmenes de hasta 120 líneas, no informes enteros pegados al chat. Durante
trabajo largo, comunicar hallazgos y bloqueos con actualizaciones breves.

No declarar completada una fase con verificaciones pendientes salvo aceptación
explícita de esa deuda por el usuario; incluso entonces siguen etiquetadas como
pendientes, no aprobadas. Terminar donde pidió el usuario y esperar su aprobación.
