# Fase 2 — corrección del arnés de observación de la imagen

Fecha: 2026-11-17. Partida: `2e8f557ad88fe84b1a00167d0f367c76addf1bac`,
rama LHM_implementado, árbol limpio. Los tres TXT siguen ignorados localmente,
intactos y fuera del commit. No se avanza al 5.2.

## Evidencia y diagnóstico

El usuario ejecutó el script en administrador: runtime 223 archivos y
preflight Production correctos, proceso iniciado, fallo
MACE_EXITED_BEFORE_OBSERVATION, código 1. Confirmó que apareció el dashboard.
No informó aquí valores CPU/GPU, motivo del cierre ni código de salida de MACE.

El arnés tenía un defecto confirmado: suponía que el PID devuelto al iniciar
MACE.exe alojaba la JVM y que el helper era hijo directo de ese PID. En Windows,
el launcher de jpackage puede preparar PATH y ejecutar otro proceso de sí
mismo, que es el que aloja JVM/telemetría. El wrapper espera su finalización.
Este comportamiento está en la [fuente de OpenJDK 22](https://raw.githubusercontent.com/openjdk/jdk/jdk-22%2B36/src/jdk.jpackage/windows/native/applauncher/WinLauncher.cpp)
y se reprodujo con el jpackage **22.0.2 instalado**, sin usar MACE real.

En ese árbol, buscar la DLL en el wrapper y buscar el helper bajo el wrapper
no puede aprobar una ejecución sana. Ver el dashboard no prueba que las
cuatro anulaciones se ignorasen; tampoco justifica declarar fallo de la carga
Java o de la DLL. Se corrigió el observador, no las condiciones de integridad.

**Causa concreta de la terminación reportada: NO VERIFICADO.** La evidencia
anterior no dice si la ventana fue cerrada, si hubo un error posterior o cuál
fue la salida de la JVM. El nuevo arnés registra el código real del launcher,
que este propaga desde la JVM. El hresult anterior -2146233087 correspondía
a la excepción controlada que lanzaba el propio test: no era el código de
salida de MACE ni GetLastError de Windows.

## Corrección acotada

- Descubre solo procesos pertenecientes al árbol del launcher iniciado. No
  selecciona una JVM global por nombre. Contrasta ruta, tiempo de creación e
  identidad del proceso y exige una única JVM con el jvm.dll de esta imagen.
- Observa los módulos de esa JVM y comprueba que el helper sea hijo directo
  de ella. Conserva los controles de DLL/helper esperados, rutas ajenas,
  hash/pins/perfil Production, tres observaciones estables y timeout de 45 s.
- Acepta tanto un launcher que ya aloja la JVM como un wrapper con JVM hija.
  No cambia PATH para evitar el wrapper en la prueba real ni modifica launchers.
- Añade TREE y OBSERVED con conteos/relaciones. Si termina el launcher, registra
  LAUNCHER_EXIT con su código real. Salidas de la aplicación se reducen a flags
  fijos: modo nativo/mock, fallo de arranque JavaFX, clase ausente, enlace
  nativo, memoria y número de líneas. No conserva texto crudo, rutas, SID,
  nombres de usuario/equipo ni hashes completos. Callbacks C# no requieren un
  runspace de PowerShell en hilos secundarios.
- No inicia el producto sin administrador, solicita elevación, cierra ventanas,
  termina procesos ni modifica variables User/Machine. Se sigue entregando
  al usuario la ejecución elevada.

## Pruebas de la corrección

`packaging/tests/test-image-observer.ps1` construye una app de prueba desechable
con el jpackage 22.0.2 instalado y una copia del runtime existente. Sin JavaFX,
hardware ni MACE/helper real. Sus procesos terminan naturalmente.

| Caso | Resultado normal, Administrator=False |
|---|---|
| JVM en el propio PID, PATH preparado solo en el fixture | PASS: se elige ese PID, un proceso de imagen |
| Wrapper y JVM hija, PATH sin preparar | PASS: se elige la hija, dos procesos de imagen |
| Los dos árboles anteriores simultáneos, mismo EXE/nombre | PASS: JVM distintas por árbol; no adopta procesos de la otra ejecución |
| Auxiliar java.exe creado por la JVM de cada fixture | PASS: padre es la JVM real, no el wrapper |
| Captura de stdout/stderr | PASS: flags esperados, sin volcar líneas crudas |
| JVM que termina con código 17 | PASS: el launcher devuelve 17; no se sustituye por HRESULT del test |

El sandbox denegó Get-CimInstance; se repitió fuera de él sin elevar Windows.
No se provocó un crash ni se terminaron procesos por la fuerza. No se usa esta
simulación como verificación del producto elevado o de sensores físicos.

Comando reproducible, desde la raíz, sin administrador:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image-observer.ps1
```

Estas son pruebas PowerShell del arnés, separadas de helper 105, CTest 76/77 y
Java 347 + 1 omitida, cuyas fuentes y resultados previos no se cambian.
Se cotejan además imagen/hashes y auditorías de código protegido/referencias.
Resultado: los **370 hashes** de la imagen siguen coincidiendo; protegidos
PASS; **181 referencias aceptadas y 0 inesperadas**, TSV sin cambios. El control
sin administrador del script principal sigue devolviendo 2 antes de iniciar
un proceso. También se reprodujo el HRESULT de la excepción controlada de
PowerShell; no se utilizó como código del producto.

## Repetición del usuario

Cierra tú las ventanas MACE/Preview anteriores, incluidas las iniciadas desde
fuentes. Desde la raíz, en una consola de administrador abierta por ti:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\tests\test-image-environment.ps1
$LASTEXITCODE
```

**Deja el dashboard abierto hasta el resultado del script.** Esperado: TREE con
una JVM (puede mostrar dos procesos de imagen), OBSERVED con
helper_parent_is_jvm=1, PASS y código 0. Después cierras tú la ventana.
Si falla, comparte TREE, LAUNCHER_EXIT si aparece, OUTPUT y FAIL. Los datos de
result.txt están saneados. Un código 0 de cierre del launcher no sustituye PASS
de la selección de componentes; cerrar durante la observación sigue fallando.

No hace falta reempaquetar: se utiliza la misma imagen. El tramo elevado
corregido sigue **NO VERIFICADO** hasta tu resultado. No se declara aprobada la
prueba de cuatro variables de la imagen ni se avanza al 5.2.

## Archivos y límites

| Archivo | Cambio |
|---|---|
| packaging/tests/image-process-observer.ps1 | Descubrimiento de JVM dentro de su árbol y captura saneada |
| packaging/tests/test-image-environment.ps1 | Usa la JVM real para DLL/helper; diagnóstico de salida |
| packaging/tests/test-image-observer.ps1 | Fixtures jpackage directos/anidados/concurrentes y salida 17 |
| helper/FASE2-PASO5.1.md | Aclara el seguimiento de JVM, ventana abierta y el resultado pendiente |
| helper/FASE2-DIAGNOSTICO-5.1.md | Este diagnóstico, pruebas y comando |

No cambia código del producto, imagen, ABI 1.5, protocolo 2, helper 1.1.1,
política 4A, manifiestos, launchers, servicios, HVCI o señal 3010. Firma,
licencia y revisión legal permanecen pendientes. No se fusiona a principal.
