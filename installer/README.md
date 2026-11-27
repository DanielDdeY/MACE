# Instalador de MACE para Windows

Estado: **en desarrollo en la rama `feat/instalador-pawnio`**. Nada de esta
carpeta está en una release ni se ha probado en un equipo físico.

Objetivo: quien instale MACE obtiene todos los datos que su hardware expone,
sin pasos manuales; si un componente no existe (por ejemplo, un equipo sin GPU
integrada), MACE lo muestra como **N/D: Sin proveedor**.

## Piezas

| Pieza | Estado |
|---|---|
| `coordinator/`: `mace-pawnio-setup.exe` | Implementado; probado en CI con setup simulado y verificación del setup oficial |
| Script Inno Setup (instala MACE y después llama al coordinador) | Pendiente (paso 2) |
| Botón «Instalar/Iniciar PawnIO» en MACE, reutilizando el coordinador | Pendiente (paso 3) |
| Aviso «reinicia Windows» tras 3010 (D4/D5) | Pendiente (paso 4) |

## Coordinador de PawnIO

Ejecutable nativo pequeño (C, CRT estático, sin .NET ni PowerShell en destino).
Lo invoca el instalador después de instalar MACE y, más adelante, MACE desde
su botón (MACE ya se ejecuta como administrador).

```text
mace-pawnio-setup --setup <PawnIO_setup.exe> [--log <archivo>]   instala si falta
mace-pawnio-setup --verify-only --setup <PawnIO_setup.exe>        solo hash y firma
mace-pawnio-setup --detect-only                                   solo estado
```

Reglas (decisiones D2/D3 de [FASE3-DISENO.md](../helper/FASE3-DISENO.md)):

1. Si el servicio `PawnIO` ya existe, con cualquier versión, **no lo toca**.
2. Requiere token elevado para instalar.
3. Solo ejecuta el setup si la ruta es un archivo ordinario y tanto el
   **SHA-256** como la **firma Authenticode** del firmante `namazso.eu`
   coinciden con [packaging/pawnio-2.2.0.json](../packaging/pawnio-2.2.0.json).
   Mientras lo verifica y lo ejecuta, mantiene el archivo abierto sin permitir
   escritura ni borrado.
4. Usa siempre los argumentos `-install -silent`, sin otros switches.
5. Espera **180 s** como máximo. Si se vence el plazo, **no mata** el proceso.
6. Un 0 o un 3010 del setup solo cuenta si después el servicio queda registrado.
7. Escribe una línea de registro con campos fijos, sin rutas, usuarios ni hashes.

### Códigos de salida

| Código | Resultado | Significado |
|---|---|---|
| 0 | INSTALLED / VERIFIED | Instalado y corroborado, o `--verify-only` correcto |
| 3010 | INSTALLED_REBOOT_REQUIRED | Instalado; Windows debe reiniciarse |
| 10 | ALREADY_COMPATIBLE | PawnIO 2.2.0.0 ya presente; no se hizo nada |
| 11 | OTHER_VERSION_KEPT | Otra versión presente; se conserva sin cambios |
| 12 | STATE_UNKNOWN | No se pudo determinar el estado; no se instala encima |
| 13 | NOT_ELEVATED | Falta token de administrador |
| 14 | ABSENT | Solo con `--detect-only`: PawnIO no está registrado |
| 20 | SETUP_UNUSABLE | Setup ausente, ilegible, no ordinario o con hash distinto |
| 21 | SIGNATURE_INVALID | Firma ausente, inválida o de otro firmante |
| 22 | SIGNATURE_UNVERIFIED | No se pudo comprobar la revocación (p. ej. sin red); no se ejecuta |
| 30 | SETUP_FAILED | El setup devolvió otro código (queda en `child=`) o no arrancó |
| 31 | NOT_CORROBORATED | El setup devolvió 0/3010 pero el servicio no aparece |
| 40 | TIMEOUT | Pasaron 180 s; el setup puede seguir ejecutándose |
| 2 | USAGE | Argumentos inválidos |

### Compilar y probar

Requiere Windows x64, MSVC y CMake 3.28+. Desde la raíz del repositorio:

```powershell
cmake -S installer\coordinator -B installer\coordinator\build -A x64
cmake --build installer\coordinator\build --config Release
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\installer\coordinator\tests\test-coordinator.ps1 -BuildDirectory .\installer\coordinator\build
```

`mace-pawnio-setup-test.exe` y `stub_setup.exe` existen **solo para pruebas** y
nunca se empaquetan. El CI ejecuta además `--verify-only` sobre el setup
oficial descargado con su hash fijado; no lo ejecuta ni instala drivers.

### NO VERIFICADO

- Ejecución real de `-install -silent` y sus códigos de retorno reales (0, 3010 u otros).
- Versión del archivo del driver instalado. Si no es 2.2.0.0, se informa
  `OTHER`, sin consecuencias porque no se reinstala.
- Comportamiento con HVCI (Integridad de memoria), sin red (código 22) y en
  Windows 10/11 limpios. Se probará en una VM o equipo con respaldo, según D9.
