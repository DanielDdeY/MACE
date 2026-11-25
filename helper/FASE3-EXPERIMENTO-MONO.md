# B — Exclusión Mono: experimento aislado

Fecha: **2026-11-25**. Base **d563cbc**, rama `LHM_implementado`.
Autorización limitada a copia nueva; ningún cambio aplicado al producto.
DLL 1.5.0 / ABI 1.5 / protocolo 2 / helper 1.1.1 y política 4A intactos.

## Procedimiento y resultado observado

Copia en `native/build/mono-exp-c1d2cc79/source/helper`, publicación nueva
`native/build/mono-exp-c1d2cc79/published`. No se cambia artifacts/latest.txt.
SDK ya instalado 10.0.401, runtime fijado 10.0.12. Restore usa una caché nueva
y el almacén NuGet existente como feed local, sin instalar herramientas.
Los archivos privados de trabajo y manifiestos con hashes completos quedan
ignorados bajo native/build; no se adjuntan ni versionan.

| Paso ejecutado | Resultado |
|---|---|
| Referencia directa exacta Mono 1.0.0, ExcludeAssets=all, solo copia | Aplicada |
| Restore --force-evaluate en copia, revisión de ambos locks | Código 0 |
| Restore --locked-mode de la copia | Código 0 |
| Build Release de Tests, --no-restore | Código 0 |
| Tests.exe sin argumentos dentro del aislamiento de herramientas | Falló: acceso a tubería local rechazado, no error de resolución Mono |
| Mismo ejecutable sin argumentos, fuera de ese aislamiento, token normal | **105 checks, código 0; Administrator=False** |
| Publish self-contained del helper en carpeta nueva, --no-build --no-restore | Código 0 |
| Write-RuntimeManifest + Verify-Artifacts, Windows PowerShell 5.1 | PASS |
| Restore locked posterior a normalizar orden de claves | Código 0 |
| git apply --check --unidiff-zero del diff completo | PASS; no aplicado |

La segunda ejecución requirió permiso del entorno de herramientas, **no**
elevación UAC. Se comprobó token no administrador antes de ejecutar. El
resultado es compatible con una restricción de tuberías del aislamiento;
no se presenta como diagnóstico completo de todas sus reglas. No se ocultó
la primera ejecución fallida. No se invocaron modos de tests que lanzan
procesos auxiliares con terminación de respaldo; no se ejecutó el helper real.

## Conteos reales y coherencia

| Conjunto | Referencia conservada | Experimento medido |
|---|---:|---:|
| Entradas artifact-manifest del publicado | 205 | **202** |
| Entradas helper-runtime-manifest | 202 | **199** |
| Archivos de la nueva carpeta publicada, incluidos dos manifiestos | — | **204** |
| Archivos imagen app-image | **370** | **No se construyó imagen nueva** |

Ausentes: Mono.Posix.NETStandard.dll, MonoPosixHelper.dll y
libMonoPosixHelper.dll. El deps nuevo no nombra Mono; **197 activos** runtime,
native y resources declarados resuelven a archivos presentes: **0 faltantes**.
El chequeo plano de nombres aplica al publish win-x64 actual, no es un
validador general de todos los modelos de carga .NET.
Se recalcularon hashes completos en los manifiestos operativos ignorados:
202/202 y 199/199 coinciden con bytes reales. No se publican hashes completos.
No se atribuyen 367 archivos a una imagen inexistente. Los conteos previos
205/202/370 siguen describiendo la referencia; 202/199 describen solo esta
publicación aislada. El binario experimental no sustituye al aprobado.

## Diff completo y revisión de locks

[MONO-EXPERIMENTO-COMPLETO.patch](pendientes/MONO-EXPERIMENTO-COMPLETO.patch)
es el diff completo propuesto de tres archivos, revisable con:

```powershell
git apply --check --unidiff-zero helper/pendientes/MONO-EXPERIMENTO-COMPLETO.patch
```

No ejecutarlo sin --check sobre el producto. Usa contexto cero para no
reproducir hashes de dependencias, y conserva marcadores de fin de archivo.
El restore movió el bloque Mono al grupo de dependencias directas. Se
normalizó únicamente el **orden de claves de paquetes** al orden previo,
comprobando igualdad JSON semántica antes/después; se volvió a pasar locked.
No se borraron discrepancias de versiones ni dependencias: **0 cambios en
versiones resueltas o contentHash**, cotejados en ambos TFM de ambos locks.
Cambios efectivos: Mono pasa Transitive → Direct y añade requested exacto
en los dos TFM del helper; Tests refleja esa dependencia directa del proyecto.
El csproj añade la referencia exacta con ExcludeAssets=all.

La exclusión afecta activos del consumo/publicación, no elimina el paquete
del grafo de restore ni acredita su licencia. **Acreditación legal abierta**.
Cambios de bytes del helper → manifiestos nuevos → pins nativos/manifestación
de imagen futuros: requieren aprobación y reconstrucción completa aparte.
La DLL de producción congelada no admitirá este helper mediante variables de
desarrollo; las pruebas propuestas usan únicamente una DLL de desarrollo.

Comprobaciones repetibles sobre la copia existente (desde raíz, sin tocar
artifacts/latest). El NuGet.Config privado usa el feed local; no compartirlo:

```powershell
$e = '.\native\build\mono-exp-c1d2cc79'
dotnet restore "$e\source\helper\Mace.Lhm.Tests\Mace.Lhm.Tests.csproj" --locked-mode --configfile "$e\NuGet.Config" --packages "$e\packages"
& "$e\source\helper\Mace.Lhm.Tests\bin\Release\net10.0-windows\win-x64\Mace.Lhm.Tests.exe"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\Verify-Artifacts.ps1 -Directory "$e\published"
```

La prueba .NET es **sin argumentos**: 105 checks, no sensor físico. No ejecutar
modos live/control-process como parte de esta repetición automática. Registrar
$LASTEXITCODE inmediatamente después de cada herramienta. Si la estructura del
experimento no existe en otro clon, no apuntar a la referencia como sustituto;
recrear una copia nueva y aprobar sus resultados por separado.

## Pruebas físicas pendientes: ejecución del usuario

**NO VERIFICADO**: arranque frío, sensores con/sin elevación, reconexión,
cierre real, ausencia de dependencia diferida en hardware Intel/NVIDIA,
y UI. Las 105 comprobaciones son de contrato, no prueba de sensores.
Script nuevo de ensayo manual: `helper/auditoria-licencias/probar-mono-manual.ps1`.
No lo ejecutó el agente. Parser PowerShell 5.1 sin errores y puente C# compilado
sin invocar métodos nativos; su funcionamiento
físico queda NO VERIFICADO. No instala ni solicita cambios de servicios.

Desde la raíz del checkout, Windows PowerShell 5.1 x64 **normal**, cierre
manual de anteriores ensayos antes de cada paso. Carpeta del experimento
local; si se clona en otro equipo primero habrá que reproducir el experimento.

```powershell
$h = '.\native\build\mono-exp-c1d2cc79\published'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory $h -Scenario Read
$LASTEXITCODE
```

Esperado: helper propio, CPU t/p=9 (NOT_ELEVATED), valores no válidos cero;
usage puede ser válido. Código 0 solo si se observa la condición requerida y
cierre sin helper restante. Si otra condición impide verla: código 2/NV,
no falso aprobado. No evalúa la GPU ni la interfaz.

Luego abrir **manualmente** una consola administrador. PawnIO debe estar ya
instalado/en ejecución y hardware compatible; no forzar esa precondición.

```powershell
$h = '.\native\build\mono-exp-c1d2cc79\published'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory $h -Scenario Read -RequireCpu
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory $h -Scenario Reconnect -RequireCpu
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\helper\auditoria-licencias\probar-mono-manual.ps1 -HelperDirectory $h -Scenario CloseEarly
```

Los campos temp_milli_c y power_milli_w son grados Celsius y vatios multiplicados
por 1000. Anotar código de salida **después de cada comando**, token, estados, PID de
base, PID nuevo y cierre remaining=0. Read exige CPU t/p válidas con
-RequireCpu; que una GPU integrada no dé temperatura no invalida por sí sola
CPU. Reconnect pide RECONECTAR: termina únicamente el hijo directo propio,
verificado por ruta interna y fecha de creación; debe ver estado no válido,
PID distinto y retorno a estados previos. Si no se captura el intervalo no
válido o la recuperación, código 2/NV; no inventar transición. CloseEarly
solicita cierre al segundo: **no demuestra WARMING_UP si no se observó**.
Shutdown sin terminación de respaldo; un fallo requiere informar, no matar
procesos por nombre. Usar proceso PowerShell nuevo en cada ensayo.
Salida deliberadamente numérica, sin rutas, nombres, SID ni hashes. Para
capturarla usar Tee-Object hacia native/build, nunca compartir stderr bruto
de otras herramientas sin sanear. Cierre de consola/caída del host interrumpe
la observación y no cuenta como cierre normal verificado.

Para comprobar UI y GPU por separado, usuario puede lanzar desde fuentes
con JDK actual y `app/scripts/run-source.ps1 -HelperDirectory $h`; selecciona
perfil development y la DLL dev existente, sin tocar imagen. Observar GPU,
CPU, mensajes y cerrar con X. No usar el launcher de producción con el helper
experimental. Ese script compila Java en app/target/source-development:
requiere Maven/JDK actual y autorización del usuario al ejecutarlo.

## Decisión y parada

Esperar resultados manuales; decidir si se autoriza integrar el diff completo
y regenerar la cadena de artefactos en una fase aparte. Exclusión aprobada
solo para este experimento, **no para distribución ni cierre de licencia**.
