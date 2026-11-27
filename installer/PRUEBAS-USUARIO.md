# Pruebas del instalador de MACE: guía para el usuario

Fecha: 2026-11-27. Instalador: artefacto `mace-instalador-prueba-<n>` del
workflow «Entrega continua - Instalador de Windows», rama `feat/instalador-pawnio`.
**Sin firma**: SmartScreen mostrará un aviso (Más información → Ejecutar de todas formas).

> Instala un **controlador del sistema** (PawnIO). Usa una **VM con instantánea**
> o un equipo con **respaldo cuya restauración hayas probado** (decisión D9). No lo
> pruebes en un equipo prestado sin permiso del dueño.

## 0. Antes de empezar (anotar)

En **Windows PowerShell 5.1 como administrador**, en la VM o equipo de prueba:

```powershell
[Environment]::OSVersion.VersionString
(Get-CimInstance Win32_Processor).Name
(Get-CimInstance Win32_VideoController).Name
Get-Service PawnIO -ErrorAction SilentlyContinue | Format-List Name,Status
(Get-CimInstance -Namespace root\Microsoft\Windows\DeviceGuard -ClassName Win32_DeviceGuard).SecurityServicesRunning
```

Anota: versión de Windows, CPU, GPU(s), si PawnIO aparece (debería **no** aparecer
en un equipo limpio) y el último valor (`2` en la lista = HVCI / Integridad de
memoria activa). No compartas nombre de equipo, usuario ni correo.

Descarga el ZIP del artefacto, extrae `MACE-Setup.exe` y comprueba su hash con
el de `SHA256SUMS-MACE.txt` del mismo ZIP:

```powershell
Get-FileHash .\MACE-Setup.exe -Algorithm SHA256
```

## 1. Instalación normal (equipo sin PawnIO)

1. Doble clic en `MACE-Setup.exe` → acepta UAC.
2. Debe aparecer la página **«Instalar el controlador PawnIO»** con la casilla
   **marcada**. Déjala marcada y continúa.
3. En la página final anota el mensaje sobre PawnIO (instalado / reinicio /
   error con código).
4. Si pide reiniciar, reinicia.
5. Abre MACE (acepta UAC). Anota en **Inicio** y **CPU**: temperatura y potencia
   de CPU (valor o «N/D: motivo»), GPU(s) detectadas y sus lecturas, RAM, procesos.

Esperado: temperatura de CPU con valor real; GPU NVIDIA/Intel/Radeon con sus
sensores; lo que el hardware no tenga, «N/D» con motivo (p. ej. «Sin proveedor»).

Después, en PowerShell como administrador:

```powershell
Get-Service PawnIO | Format-List Name,Status
Get-Content "$env:ProgramData\MACE\InstallerLogs\pawnio.log"
```

Anota la línea del log (contiene `result=` y `code=`; no tiene rutas).

## 2. Casilla desmarcada y botón en MACE

En una instantánea **limpia** (sin PawnIO):

1. Instala MACE **desmarcando** la casilla de PawnIO.
2. Abre MACE: la temperatura de CPU debe decir «N/D» con «PawnIO no está
   instalado» y aparecer el botón **«Instalar PawnIO»** (en Inicio y en CPU).
3. Pulsa el botón → confirma → anota el mensaje del resultado.
4. Anota si la temperatura aparece sola, tras cerrar y abrir MACE, o tras reiniciar.

## 3. Reinstalar con PawnIO ya presente

Ejecuta de nuevo `MACE-Setup.exe`: la página de PawnIO **no** debe aparecer y
PawnIO no debe tocarse. Con MACE **abierto**, el instalador debe pedir cerrarlo
sin cerrarlo por su cuenta.

## 4. Desinstalar

Panel de control → Programas → MACE → Desinstalar. Anota: carpeta
`C:\Program Files\MACE` eliminada; `Get-Service PawnIO` **sigue** presente;
`%LOCALAPPDATA%\MACE` (caja negra) se conserva.

## 5. Matriz de equipos (lo que más falta)

| Equipo | Qué confirmar |
|---|---|
| AMD Ryzen + Radeon | Temperatura/potencia CPU; temperatura, uso, potencia, VRAM GPU |
| Intel + NVIDIA | CPU vía PawnIO; GPU NVIDIA por NVML |
| Intel con gráfica integrada | GPU Intel por IGCL |
| Equipo **sin** gráfica integrada | Que no aparezca GPU fantasma; ausentes como «Sin proveedor» |
| HVCI activo | Si PawnIO se instala y carga (resultado y log) |
| Sin Internet | Resultado esperado: código 22 (firma no verificable); MACE funciona igual |

## Qué enviarme

Por cada caso: equipo (CPU/GPU, Windows, HVCI), mensaje final del instalador,
línea del log, estado de `PawnIO` y lo que muestra MACE (valores o «N/D: motivo»).
Capturas de pantalla de MACE sirven. **No** envíes nombre de equipo, usuario,
correo, SID ni rutas de tu perfil.

**Abortar** si: Windows muestra pantalla azul, el instalador se queda más de
5 minutos sin avanzar, o el antivirus bloquea algo. Restaura la instantánea y
envíame lo observado.
