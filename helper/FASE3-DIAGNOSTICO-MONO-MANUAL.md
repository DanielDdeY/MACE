# C — Diagnóstico estático de probar-mono-manual.ps1

Fecha: **2026-11-25**. Script leído en **4668085**; no ejecutado ni modificado.
No se cargó DLL, ejecutó puente C#, lanzó helper, elevó o terminó proceso.
El usuario comunica cuatro códigos **2**, usando consolas normal y administrador.
No se atribuye un token concreto a cada ejecución sin su evidencia individual.
**No son evidencia a favor ni en contra de excluir Mono**. Exclusión bloqueada
para cualquier candidata y no recomendada hasta resultados físicos con código 0.

## Condiciones comunes

El código inicia en 2 y solo pasa a 0 en la rama final `if($ok)`.
Preflight: PowerShell x64, rutas existentes, DLL **development 1.5.0**, manifiesto
del helper verificado, API/puente cargables, CP_InitializeTelemetry=0.
Proceso nuevo por invocación para evitar colisión del tipo Add-Type y DLL ya
cargada. No usar DLL de producción con anulación de helper por entorno.

Cada muestra exige retorno nativo 0, estructura 56/version 2, temperatura y
potencia finitas; si su estado no es VALID, valor cero. No comprueba frescura
independiente del timestamp, estado de uso ni GPU en ese filtro de coherencia.
Un fallo lanza SAMPLE_FAILED/LAYOUT_FAILED o una excepción del puente.
No se altera la política de validez de la DLL por este ensayo.

Own-Helpers solo acepta hijos directos del PID PowerShell actual, con
ExecutablePath textual igual al helper resuelto y CreationDate >= birth−1 s.
Solo **exactamente un** hijo filtrado produce childId distinto de cero; cero
o varios dejan childId=0. No busca por nombre global ni puede adoptar procesos
ajenos. Cierre pide Shutdown y durante hasta 5 s sondea ese mismo filtro.
`left.Count=0` prueba ausencia **según ese detector**, no ausencia universal si
el detector no identifica el hijo. Un servicio CIM lento puede prolongar el
ensayo: 1/20/75 s son ventanas del bucle, no timeouts de todas las llamadas.

## Tabla comando → consola → esperado → causas de 2

Abreviaturas: `T` es el script original; `H` publicación aislada; comandos
completos al final. Los esperados describen criterios del arnés, no garantías
físicas ni resultados ya aprobados.

| Comando | Consola/precondición | Qué exige 0 | Causas posibles de 2 |
|---|---|---|---|
| T -HelperDirectory H -Scenario Read | **Normal**, x64, manifiesto válido; PawnIO no necesita instalarse para observar falta de elevación | En 20 s: baseline al menos una vez; t/p=9 al menos una vez; detector vacío después de Shutdown | Ningún hijo reconocido/baseline, nunca t/p=9, helper restante, preflight o excepción |
| T -HelperDirectory H -Scenario Read -RequireCpu | **Administrador**, PawnIO ya instalado/operativo y CPU con sensores compatibles; no forzar servicios | En 20 s: baseline y t/p=0 al menos una vez, cierre vacío | RequireCpu sin admin; sensores no válidos, PawnIO ausente/detenido, otro estado, detector o excepción |
| T -HelperDirectory H -Scenario Reconnect -RequireCpu | **Administrador**, baseline CPU t/p válidas y confirmación RECONECTAR | En 75 s totales: termina su hijo identificado; observa ambos estados no válidos, PID nuevo y vuelta a estados previos; cierre vacío | Cancelación, no baseline válido, detector falla, identidad no confirmada, no intervalo inválido capturado, no PID nuevo o no recuperación antes del límite |
| T -HelperDirectory H -Scenario CloseEarly | **Normal o administrador**, helper existente y descubrible; para comparar con la secuencia anterior usar admin | En ventana nominal de 1 s ve al menos un hijo identificado; después Shutdown y detector vacío | Hijo no observado a tiempo, ruta/fecha no coincidente o metadato inaccesible, hijo restante o excepción |

Read **en administrador sin RequireCpu** también es legal: baseline y cierre
vacío bastan; no exige t/p válidas. Reconnect **sin RequireCpu** acepta token
normal o administrador: basta baseline, incluso estados no válidos como 9,
y regresar a los estados previos; no acredita recuperación de sensores válidos.
Con RequireCpu en consola normal se lanza REQUIRE_CPU_NEEDS_ADMIN antes del bucle.

Baseline exacta: childId!=0, provider=8 y temperatura fuera de
{5,8,13,14,15,16}. Por tanto puede incluir NO_PROVIDER/UNSUPPORTED/READ_FAILED
u otros estados no válidos; no significa por sí sola una lectura CPU válida.
Las banderas son acumuladas: Read no exige que la última muestra sea válida
ni correlaciona todas las observaciones en un mismo sondeo.

Reconnect exige `killed && invalidAfter && newId && recovered`:
invalidAfter requiere **t y p distintas de 0 simultáneamente**; newId requiere
un hijo filtrado con PID diferente; recovered requiere ambas banderas anteriores
y t/p iguales a los guardados antes de terminar el hijo. Con RequireCpu esos
estados previos son 0. No espera 75 s adicionales tras terminarlo: la espera de
baseline consume esa ventana. Una transición breve puede no observarse.

## CloseEarly: puede devolver 0, sin exigir un transitorio

**Sí, es alcanzable**: el hijo es observado durante el primer segundo y el
detector queda vacío al cerrar. **No exige WARMING_UP, ni temperatura válida**.
Si el helper ya está Ready también puede aprobar. No hay una condición que
obligue a ver un estado transitorio imposible; sí hay una carrera temporal y
una dependencia fuerte del descubrimiento. Arranque lento/CIM lento pueden
impedir sawHelper. Código 0 aquí no demostraría cierre durante calentamiento.
CloseWarming reproducible sigue **NO VERIFICADO**, no se confunde con CloseEarly.

## Puente C# y lo visible hoy

- Add-Type, LoadLibraryExW, resolución de exportaciones, marshaling, Start y
  Sample pueden fallar. Una excepción administrada normalmente llega al catch:
  `NO_VERIFICADO reason=MANUAL_PROBE_INCOMPLETE hresult=<entero>` y código 2.
  El HRESULT puede ser el del envoltorio PowerShell, sin la causa interior.
- LoadLibrary devuelve cero → LOAD_FAILED; no conserva GetLastError. Una
  exportación ausente puede producir excepción al crear el delegado. Hoy no
  se distinguen esos casos por el texto final. No se puede atribuirlo a Mono.
- Una muestra incoherente/retorno no cero lanza antes de imprimir SAMPLE.
  Comparar último SAMPLE/CLOSE ayuda a acotar, no reconstruye la causa.
- Un fallo puramente de condición llega a CLOSE y luego imprime
  `NO_VERIFICADO reason=EXPECTED_OBSERVATION_MISSING`. Hay motivo genérico,
  pero faltan los nombres de condiciones. No es el mismo camino que el catch.
- Fallo de Shutdown en finally imprime `NO_VERIFICADO reason=SHUTDOWN_FAILED`.
  Errores no capturados del finally/restauración de entorno, Ctrl+C, abortos del
  host o fallos nativos graves pueden impedir llegar a `exit $code`. **No todo
  fallo está garantizado como 2**, ni toda caída deja una línea diagnóstica.

## Evidencia local adicional leída, sin copiar el log

Archivo sin seguimiento `mono-read-normal.txt`, no incluido en commit:
135 SAMPLE; contexto administrator=0/Read; provider=8 en todas; CPU t/p=5
una vez, 9 en 134; uso=5 una vez y 0 en 134. **helper_pid=0 en las 135**;
CLOSE own_helpers_remaining=0; salida EXPECTED_OBSERVATION_MISSING.
Por inspección, notElevated=true pero baseline=false: basta para explicar
**ese** código 2. No explica por sí solo los otros tres, cuyos logs no se leyeron.

Posibles causas del detector: ExecutablePath vacío/inaccesible, diferencias de
representación de ruta, filtro de nacimiento, cero/varios candidatos o distinta
relación padre. **NO VERIFICADO cuál ocurrió**. No se altera ni relaja el filtro.
Microsoft documenta ExecutablePath con calificador SeDebugPrivilege:
[Win32_Process](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-process),
leído el 25/11. Eso respalda investigar visibilidad del metadato; no prueba
que una consola elevada lo resuelva ni autoriza habilitar privilegios.

En el código C, LhmReceiver_ApplyCpu asigna estado 9 cuando una trama con PID
informa falta de elevación. Es compatible con que exista un helper aunque CIM
no lo identifique; **no** prueba su ausencia ni acredita sensores válidos.

## Diff de diagnóstico propuesto, SIN APLICAR

[MONO-MOTIVOS.patch](pendientes/MONO-MOTIVOS.patch) añade etapas estáticas,
motivos permitidos y una línea DETAIL para cada salida 2 que alcanza el final.
Sin ejecutar el script, cambiar criterios $ok, plazos, filtros, kills, DLL o UI.
Conserva las dos líneas genéricas actuales y sus HRESULT. El primer criterio
faltante tiene nombre concreto; las banderas permiten ver los demás.
Excepciones solo imprimen mensajes de una lista literal; jamás Message bruto,
stack, rutas, identidades, hash o excepción interior sin filtrar.

Ejemplos de motivo: BASELINE_NOT_OBSERVED, CPU_VALID_NOT_OBSERVED,
NOT_ELEVATED_NOT_OBSERVED, OWN_HELPER_NOT_TERMINATED,
INVALID_INTERVAL_NOT_OBSERVED, NEW_PID_NOT_OBSERVED, RECOVERY_NOT_OBSERVED,
OWN_HELPER_NOT_OBSERVED y OWN_HELPERS_REMAIN. Excepción desconocida:
EXCEPTION_AT_STAGE con etapa BRIDGE_COMPILE/LOAD/START/SAMPLE, MANIFEST,
OWN_HELPER_DISCOVERY, SHUTDOWN, etc. No es una corrección del detector.
Un fallo que impida finalizar el proceso sigue sin garantizar DETAIL.

Solo comprobación textual autorizada ahora:

```powershell
git apply --check helper/pendientes/MONO-MOTIVOS.patch
```

**Tras aprobación y aplicación separada del diagnóstico**, el usuario podrá
repetir (desde raíz, proceso nuevo por comando), sin tocar servicios:

```powershell
$h = '.\native\build\mono-exp-c1d2cc79\published'
$t = '.\helper\auditoria-licencias\probar-mono-manual.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $t -HelperDirectory $h -Scenario Read
```

Ese Read en normal. En consola administrador, volver a definir h/t y ejecutar
individualmente, **después de aprobar/aplicar el diagnóstico**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $t -HelperDirectory $h -Scenario Read -RequireCpu
$LASTEXITCODE
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $t -HelperDirectory $h -Scenario Reconnect -RequireCpu
$LASTEXITCODE
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $t -HelperDirectory $h -Scenario CloseEarly
$LASTEXITCODE
```

Anotar también $LASTEXITCODE tras el Read normal, CONTEXT, DETAIL y CLOSE;
no compartir logs sin revisar. No se solicita repetirlos ahora con un parche
que permanece pendiente.

## Guía de revisión sin revisor

**Existe** [FASE3-GUIA-REVISION-SIN-REVISOR.md](FASE3-GUIA-REVISION-SIN-REVISOR.md),
89 líneas en la base. No se completa ni modifica aquí. Describe perfil FOSS y
redistribución binaria, dossier acotado/saneado, qué no enviar, vías oficiales
SFLC/FSFE/Conservancy con límites, y separación entre hechos documentales,
dictamen jurídico y elegibilidad SignPath. No hay revisor designado, contacto,
contratación ni aprobación legal. Disponibilidad actual de esas vías no se
reverifica en esta tarea; conserva la fecha/alcance de su consulta original.
