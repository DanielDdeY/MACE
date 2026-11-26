# 3.1d — Etapa 1: motivos y descubrimiento del helper

Fecha: **2026-11-25**, America/Lima. Rama `LHM_implementado`; partida
**4cf5161**, árbol seguido limpio. Dos archivos locales sin seguimiento
preexistentes se mantienen fuera de los commits, incluido `mono-read-normal.txt`.
Alcance: arnés y documentación; no se ejecutó el producto ni un helper real.
**DETENIDO al terminar la Etapa 1**, esperando los cuatro resultados físicos.

## Decisiones recibidas, sin extrapolarlas

- B01: metadatos MIT autorizados **solo en la futura candidata aislada**.
- B07: MONO-MOTIVOS autorizado y aplicado literalmente en **808d3bc**, commit
  exclusivo del script. No volver a aplicar ese parche histórico.
- B18: **VERIFICADO POR EL USUARIO** que marcó como borrador v1.0.0,
  v1.1.0-rc3 y v1.1.0. No se comprobó el remoto ni se inventarían sus binarios.
- El usuario declara **dos laptops Intel con NVIDIA**; no acredita ensayos
  físicos de esta combinación ni autoriza acciones en ellas por el agente.
- Descargas aprobadas bajo las condiciones del nuevo prompt; **ninguna
  iniciada en esta etapa**. La futura candidata se construirá primero con
  **CONSERVAR Mono**. EXCLUIR sigue bloqueada hasta resultados físicos código 0
  y su aprobación de integración; el cero no acredita licencia ni SignPath.
- Este registro actualiza esas decisiones frente a los estados históricos de
  PENDIENTE-FASE-3B y las propuestas. La consolidación única pedida en Etapa 7
  todavía no se ha realizado. No se avanzó a Etapas 2–7 ni a 3.2/3.3.

## Evidencia e hipótesis sobre las 135 muestras

Se leyó el log local sin incorporarlo: **135 muestras, 135 helper_pid=0,
134 CPU t/p=9, provider=8 en las 135**, contexto normal/Read. CLOSE count=0
y EXPECTED_OBSERVATION_MISSING. Explica baseline=false de **ese ensayo**.
No se leyeron logs de los otros tres: sus causas individuales son
**NO VERIFICADAS**. Un código 2 no demuestra un fallo de Mono ni su necesidad.

| Hipótesis | Evidencia y conclusión |
|---|---|
| HelperDirectory relativo | El script convierte el EXE con Resolve-Path antes de usarlo; Verify-Artifacts también resuelve Directory. No se halló un defecto por usar ruta relativa desde la raíz correcta. Un directorio actual equivocado sí puede causar preflight fallido. |
| Comparación textual de rutas | **Defecto demostrado y corregido**: lhm_trust.c/final_name usa GetFinalPathNameByHandleW con VOLUME_NAME_DOS; LhmTrust_Acquire reconstruye el EXE desde esa ruta; LhmTransport_OpenEx la entrega a CreateProcessW. El arnés comparaba literalmente con Resolve-Path, sin reconocer el prefijo DOS extendido. También repetía la comparación antes de Kill. |
| Visibilidad de ExecutablePath | **NO VERIFICADO físicamente**. La propiedad CIM tiene calificador SeDebugPrivilege; un dato vacío se sigue rechazando. No se habilitan privilegios ni se adopta un proceso por nombre. |
| Momento de medición | birth se registra antes de Load/Start; el hilo receptor se crea al inicializar, no se encontró un error de orden que por sí solo descarte todos los hijos. CloseEarly conserva su carrera de un segundo y CIM puede tardar. **NO VERIFICADO** si influyó en los ensayos físicos. |
| Varios candidatos, otro padre o fecha anterior | Los filtros se conservan. No hay metadatos históricos que permitan excluir estas causas de los cuatro resultados. |

Comprobación local de **solo lectura** del EXE aislado: se abrió como archivo
y se consultó su nombre final; la comparación anterior devolvió **0** y la
corregida **1**. No se cargó ni ejecutó ese EXE. Reproduce el defecto de
representación; **no prueba** qué devolvió CIM durante los ensayos anteriores.
El código de LhmReceiver_ApplyCpu requiere una trama con PID para producir el
estado 9: las 134 muestras son compatibles con un helper funcionando que el
detector no reconocía; provider=8 por sí solo no demuestra esa ejecución.

Fuentes oficiales leídas el **25/11/2026**, sin descargar herramientas:
[GetFinalPathNameByHandleW](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfinalpathnamebyhandlew)
documenta el prefijo DOS extendido;
[Win32_Process](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-process)
documenta CreationDate y el calificador de ExecutablePath. No se consultaron
identidades de procesos ni se usaron APIs no documentadas.

## Corrección y comprobaciones

`probar-mono-manual.ps1` reconoce como equivalentes únicamente rutas DOS que
difieren en el prefijo extendido; el resto de la ruta completa y la comparación
OrdinalIgnoreCase siguen siendo obligatorios. Se aplica tanto al descubrimiento
como a la comprobación del Process retenido antes de Kill. No busca por nombre,
sufijo, PID global ni ruta relativa. No resuelve alias/reparse points para
aceptar más procesos; discrepancias distintas siguen rechazándose.

Invariantes conservadas: padre directo del PowerShell de la prueba, nacimiento
desde birth−1 s, exactamente un candidato para childId, identidad antes de Kill,
confirmación RECONECTAR, 1/20/75 s, sondeo 100 ms, cierre hasta 5 s, criterios
de CPU/intervalo inválido/PID nuevo/recuperación y puente C# sin cambios.
La DLL, helper, ABI/protocolo, servicios, imagen y pins no se modificaron.

Se añade `DISCOVERY` con contadores acumulados de consultas, filas, ruta
ausente, equivalencia por prefijo y rechazo por fecha. **No son procesos
únicos**: el mismo hijo puede contarse en muchos sondeos. Junto con DETAIL,
permiten distinguir observación incompleta sin imprimir rutas, nombres o hashes.
El parche de motivos conserva las líneas genéricas y agrega reason/stage y
banderas a las salidas ordinarias código 2. Ctrl+C, caída nativa, errores del
finally o del enlace de parámetros pueden impedir esa salida; no se promete
DETAIL en abortos que no llegan al `exit 2` del script.

**35 comprobaciones aprobadas** con Windows PowerShell 5.1 x64:

- 20 casos de comparación/descubrimiento simulado, incluidos prefijo, espacios,
  otro ejecutable/volumen/padre, fecha límite, ruta invisible y dos candidatos.
- 2 cotejos AST con 808d3bc: asignaciones de aprobación/ventanas y sleeps intactos.
- 9 motivos finales de condiciones incompletas ejercitados con datos simulados.
- 2 comprobaciones de preflight en PowerShell hijo: salida 2 con motivo y salida
  saneada; se provoca una DLL inexistente **antes** de cargar el puente.
- 2 comprobaciones del nombre final real del archivo, abierto de solo lectura.

Reproducir desde la raíz del repositorio (no requiere administrador):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\test-mono-discovery.ps1 -CanonicalFile .\native\build\mono-exp-c1d2cc79\published\Mace.Lhm.Helper.exe
$LASTEXITCODE
```

Esperado: CANONICAL_READONLY old_equal=0 corrected_equal=1 y PASS checks=35,
código 0. Sin CanonicalFile: 33 checks sintéticos/preflight, sin consultar el
nombre final de un archivo real. No sustituye las cuatro pruebas físicas.
No se ejecutó regresión de producto: no hay cambios de producto en esta etapa.

## Cuatro ensayos físicos — los ejecuta el usuario

Desde la **raíz de mace-native-dev**, Windows PowerShell 5.1 **x64**, un proceso
nuevo por comando. Cerrar MACE manualmente antes de comenzar. Deben existir la
publicación aislada del experimento y la DLL development 1.5.0 indicadas abajo;
no usar la DLL production ni reconstruir/modificar la imagen congelada.
Si faltan, detenerse y comunicarlo; no instalar nada para improvisar el ensayo.
El verificador de manifiestos corre antes de cargar la DLL. Las variables MACE_*
se limpian/restauran solo en el proceso hijo, no en usuario/máquina.
Para RequireCpu: PawnIO ya instalado/operativo y sensores compatibles; si no,
documentar precondición incumplida, sin forzar servicios ni instalar PawnIO.

**1. Consola NORMAL — Read sin RequireCpu:**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory .\native\build\mono-exp-c1d2cc79\published -NativeDll .\native\build\fase2-paso4b\dev\Release\mace_native.dll -Scenario Read
Write-Host ('EXIT_CODE='+$LASTEXITCODE)
```

**2. Consola ADMINISTRADOR — Read con RequireCpu:**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory .\native\build\mono-exp-c1d2cc79\published -NativeDll .\native\build\fase2-paso4b\dev\Release\mace_native.dll -Scenario Read -RequireCpu
Write-Host ('EXIT_CODE='+$LASTEXITCODE)
```

**3. Consola ADMINISTRADOR — Reconnect con RequireCpu:** escribe RECONECTAR
cuando lo pida. Esta prueba termina **solo su helper hijo verificado**; no MACE
ni procesos de otros programas. El agente no ejecutó este comando.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory .\native\build\mono-exp-c1d2cc79\published -NativeDll .\native\build\fase2-paso4b\dev\Release\mace_native.dll -Scenario Reconnect -RequireCpu
Write-Host ('EXIT_CODE='+$LASTEXITCODE)
```

**4. Consola ADMINISTRADOR — CloseEarly:**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory .\native\build\mono-exp-c1d2cc79\published -NativeDll .\native\build\fase2-paso4b\dev\Release\mace_native.dll -Scenario CloseEarly
Write-Host ('EXIT_CODE='+$LASTEXITCODE)
```

| Ensayo / consola | Código 0 exige | Causas posibles de 2 |
|---|---|---|
| Read / normal | baseline, t/p=9 observadas y detector vacío al cerrar | No baseline/PID; nunca falta de elevación; cierre no vacío; preflight/puente |
| Read RequireCpu / admin | baseline, t/p=0 observadas y cierre vacío | Sensores/PawnIO no válidos; detector; preflight/puente |
| Reconnect RequireCpu / admin | CPU válida antes; termina hijo propio; observa t/p no válidas, PID diferente y recuperación a estados previos; cierre vacío | Cancelación; no baseline; no intervalo capturado; no PID nuevo o recuperación en ventana; identidad/cierre/puente |
| CloseEarly / admin | Detecta hijo dentro de ventana nominal 1 s y cierre vacío | Arranque/CIM lento, hijo no reconocido, cierre no vacío o excepción |

Baseline exacta: childId no cero, provider=8 y cpu_t fuera de
{5,8,13,14,15,16}; no equivale a CPU válida. Las observaciones se acumulan.
CloseEarly **no exige WARMING_UP** y no acredita CloseWarming. Los plazos son
ventanas del bucle, no límites superiores para cada consulta CIM.

**Ante código 2:** no declarar fallo de Mono ni repetir hasta obtener un cero.
Enviar escenario/consola, EXIT_CODE, CONTEXT, NO_VERIFICADO, DISCOVERY, DETAIL
y CLOSE; añadir muestras relevantes con estados/PID y mediciones, sin datos
locales. prefix_match>0 demuestra que se reconoció esa diferencia en la nueva
ejecución; path_missing>0 indica filas sin ruta; birth_rejected>0 rechazos por
fecha. rows=0 no distingue por sí solo ausencia de helper de una consulta sin
hijos. Si falta DETAIL o hay código distinto de 0/2, comunicarlo como fallo
de ejecución no clasificado, no aprobación ni conclusión sobre exclusión.
No compartir excepciones crudas con rutas de perfil; sanear antes de enviarlas.

## NO VERIFICADO y parada

- Los cuatro ensayos físicos después del arreglo, en normal y administrador.
- Causa exacta de cada uno de los cuatro códigos 2 anteriores; visibilidad CIM
  histórica y representación de ExecutablePath en aquellas ejecuciones.
- Cierre durante WARMING_UP, sensores Intel/NVIDIA del conjunto candidato,
  licencia histórica Mono, cumplimiento de terceros y admisión SignPath.
- Descargas/build/regresión/candidata, VM/HVCI/3010 y las demás etapas futuras.

No se avanza hasta recibir los resultados. No se solicita elegir otra vez
variante: la instrucción vigente para la futura primera candidata es CONSERVAR.
