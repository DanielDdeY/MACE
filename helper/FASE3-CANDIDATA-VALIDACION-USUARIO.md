# Validación del usuario — candidata parcial Etapa 4

Fecha: **2026-11-26**. **No es aprobación de la imagen**: todavía no existe.
La campaña está detenida por el faltante Maven documentado en
[FASE3-ETAPA4-BLOQUEO-MAVEN.md](FASE3-ETAPA4-BLOQUEO-MAVEN.md).
No ejecutar una reconstrucción general ni un restore con red para sortearlo.
No usar `packaging/dist/MACE` original para atribuir resultados a esta candidata.

## 0. Selección explícita de los outputs existentes

Los comandos se escriben desde la **raíz de mace-native-dev**. No requieren
cambiar JAVA_HOME/PATH ni variables de usuario o máquina. Abrir las consolas
manualmente; ningún script invoca RunAs. Cerrar manualmente MACE/Preview y su
helper antes de los ensayos; si quedan activos, detenerse sin matarlos.

```powershell
$ErrorActionPreference='Stop'
$R=(Resolve-Path '.\native\build\candidata-20261126-02').Path
$S=Join-Path $R 'source'
$H=Join-Path $R 'helper-publish-true'
$DevDll=Join-Path $R 'native\dev\Release\mace_native.dll'
$ProdDll=Join-Path $R 'native\prod\Release\mace_native.dll'
$T=Join-Path $S 'helper\auditoria-licencias\probar-mono-manual.ps1'
$Active=@(Get-Process | Where-Object { $_.ProcessName -in @('MACE','MACE-Preview','Mace.Lhm.Helper') })
if($Active.Count){throw 'CIERRA_MACE_MANUALMENTE_Y_REINTENTA'}
```

Los cuatro ensayos físicos previos del usuario con 0 se reconocen, pero **no**
se trasladan automáticamente al nuevo helper publicado con Temurin/metadata
nuevos. Estos comandos quedan preparados; **no te solicito repetirlos antes de
resolver el gate Java**. Reconnect termina su propio helper real: acción del
usuario, fuera de la autorización limitada concedida al agente.

## 1. Hardware con helper nuevo — preparado, NO VERIFICADO

Tras reanudar y aprobar el gate pendiente, orden propuesto para una sola campaña:

| Orden / consola | Comando (tras el bloque 0) | Resultado esperado y registro |
|---|---|---|
| 1, Windows PowerShell 5.1 x64 normal | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File $T -HelperDirectory $H -NativeDll $DevDll -Scenario Read` | Código 0 si satisface las condiciones Read; CPU t/p sin elevación puede ser N/D. Anotar código y motivo saneado |
| 2, administrador abierto por el usuario | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File $T -HelperDirectory $H -NativeDll $DevDll -Scenario Read -RequireCpu` | Código 0 y CPU requerida válida; anotar CPU/GPU, estados, sin asumir que NVIDIA/Intel estén cubiertos |
| 3, administrador | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File $T -HelperDirectory $H -NativeDll $DevDll -Scenario Reconnect -RequireCpu` | Código 0, cambio de PID propio y recuperación según criterios del arnés |
| 4, administrador | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File $T -HelperDirectory $H -NativeDll $DevDll -Scenario CloseEarly` | Código 0 con precondición observada; código 2 es NO VERIFICADO, no PASS |

Capturar `$LASTEXITCODE` inmediatamente tras **cada** comando. Una consola nueva
por ensayo. No ejecutar sobre las laptops por inferencia de disponibilidad.
Los modelos/mediciones se pueden compartir; sanear rutas, usuario, correo, SID
y nombre de máquina. No subir stdout sin revisar ni hashes completos.

## 2. Regresión elevada — aún NO VERIFICADA y sin comandos engañosos

El runner del agente `ejecutar-regresion-candidata.py` **rechaza administrador**:
su autorización solo cubre token normal. Los CTest de esta campaña usan un
TEST_LAUNCHER y diario externos; un `ctest` directo sin el contexto de diario
no reproduce el arnés y puede fallar por precondición. No se recomienda como
comando listo una invocación que carezca de ese contexto.

Una vez resuelto el bloqueo, se entregará el wrapper de **ejecución manual del
usuario** para helper 105, CTest 76/77, Java y payload sobre estos mismos outputs,
sin recompilar ni cambiar sus criterios. No se inventa ahora que tal wrapper
administrador esté implementado o verificado. Tampoco se afirma Java 347+1:
JUnit no llegó a arrancar. La regresión normal CTest y helper sí pasó.

## 3. Imagen, UI y elevación mixta — bloqueadas, NO VERIFICADAS

El destino previsto, todavía **inexistente**, es:

```powershell
$Image=Join-Path $S 'packaging\dist\MACE'
if(-not (Test-Path -LiteralPath $Image)){throw 'IMAGEN_CANDIDATA_AUN_NO_CONSTRUIDA'}
```

Después de completar Java y generar/verificar **una** imagen, los comandos de
apertura serán, uno por vez y cerrando manualmente con X entre casos:

```powershell
# CONSOLA NORMAL: Preview; simulación, no acredita sensores físicos.
& (Join-Path $Image 'MACE-Preview.exe')
# CONSOLA ADMINISTRADOR DEL USUARIO: app real; no lanzado por el agente.
& (Join-Path $Image 'MACE.exe')
```

Observar dashboard y Micro-HUD, FXML/CSS/AtlantaFX, escalado, entradas, ventanas,
valores por proveedor y N/D correcto. Registrar imagen exacta, consola, GPU
Intel o NVIDIA presente, mensaje y recuperación. No atribuir física a Preview.
El árbol launcher/JVM/helper debe **observarse de nuevo**: jpackage 25 no queda
validado por el árbol de dos MACE.exe del empaquetado anterior.

Elevación mixta: dejar una instancia normal real de fuentes con Temurin 25
abierta, abrir la imagen real en administrador de la misma sesión/cuenta y
repetir en orden inverso. Esperado: aviso de instancia abierta/cerrándose,
sin segundo propietario. **Falta el comando normal de fuentes con classpath
final medido** porque Stage no existe. No usar un script que elija el JDK o
clases históricos como sustituto. Se entregará al completar la imagen.

Los arneses de imagen históricos se ejecutarán desde `$S`, con el destino
candidato comprobado, no desde el checkout original. Cierre X, kills elegidos
por PID/fecha, suspensión y recuperación física requieren la intervención del
usuario y los comandos finales tras medir el árbol. No ejecutar PawnIO,
instaladores, cambios de HVCI o pruebas de parada de servicios en este subpaso.

## 4. Evidencia ya disponible, solo lectura

- `helper-publish-comparison.json`: igualdad completa de 204 archivos por lado.
- `helper-unit-summary.json`: 105 checks.
- `ctest-dev-summary.json` y `ctest-prod-summary.json`: 76 y 77.
- `ctest-final-fixture-check.json`: seis casos de política revalidados con los
  fixtures finales, sin reutilizar bytes anteriores a corregir su diario.
- `stage4-stop-summary.json` y `final-process-check.json`: alcance y supervivientes.
- `procesos-saneado.csv`: PID/fecha/eventos por caso, sin rutas ni hashes.

Todos bajo `$R\evidence`, ignorados y no versionados. Los logs largos privados
no se publican ni se incluyen en commits. El cierre de Etapa 4 y los comandos
faltantes quedan **pendientes**, sin ampliar la autorización actual.
