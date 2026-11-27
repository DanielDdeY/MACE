# Fase 3 — Instalador, paso 1: coordinador de PawnIO

Fecha: 2026-11-27, America/Lima.
Partida: rama `feat/instalador-pawnio`, creada desde `LHM_implementado` en
**73918ec**, con CI completo en verde.
Alcance autorizado por el usuario: desarrollar el camino hacia un instalador
de Windows con el que el usuario final obtenga todos los datos que expone su
hardware, sin pasos manuales. También autorizó leer en GitHub y en pawnio.eu
la documentación y licencia de PawnIO, solo lectura.

## Decisiones del usuario en esta sesión

| Tema | Decisión | Relación con decisiones previas |
|---|---|---|
| Instalación de PawnIO | **Instalador + botón en MACE** | El instalador sigue D2. El botón **reemplaza** la exclusión de la opción C de [FASE3-DISENO.md](FASE3-DISENO.md) §2, pero solo como acción manual del usuario dentro de MACE; nunca instala de forma automática al arrancar |
| Casilla de PawnIO | **Marcada por defecto y visible**, en una página que explica qué es PawnIO | Actualiza la recomendación de casilla desmarcada. El consentimiento sigue siendo explícito porque el usuario ve la casilla y puede desmarcarla. En modo silencioso solo se instala con `ACCEPT_PAWNIO=1` |
| Rama | `feat/instalador-pawnio` | `LHM_implementado` queda estable hasta revisar y fusionar |

D1 (Inno Setup), D3 (180 s sin matar el proceso) y D4/D5 (señal 3010 y aviso
en Java) se mantienen sin cambios.

## Resultado

**Implementado:** el coordinador `installer/coordinator` (C, CRT estático), su
setup simulado, las pruebas y el job de CI `coordinador-pawnio`. El contrato y
los códigos de salida están en [installer/README.md](../installer/README.md).

**Verificación:** esta PC no tiene MSVC ni CMake, así que la compilación y las
pruebas se ejecutan **solo en GitHub Actions**. El resultado se registra
cuando termina el run.

## Evidencia consultada de PawnIO, en solo lectura

| Fuente | Dato |
|---|---|
| github.com/namazso/PawnIO | Licencia GPL-2.0-or-later con una excepción para módulos independientes que se comunican solo por IOCTL. Para otras licencias, contactar con el autor |
| pawnio.eu | La edición oficial está firmada; la edición «unrestricted» no tiene firma (no se usa). Sin garantía. No documenta switches ni códigos de salida |
| github.com/namazso/PawnIO.Setup | Solo hospeda releases; el README no documenta CLI ni licencia |
| Evidencia previa del binario 2.2.0 ([FASE3-DISENO.md](FASE3-DISENO.md) §4) | Contiene `-install` y `-silent`. Su texto permite redistribuir el instalador sin modificarlo |

**Sigue NO VERIFICADO:** los códigos reales de `-install -silent`, el
comportamiento con HVCI y la versión del archivo del driver instalado. La
revisión legal cualificada (B15/B17) sigue pendiente; esta lectura no es un
dictamen.

## Hoja de ruta

| Paso | Contenido | Responsable | Estado |
|---|---|---|---|
| 1 | Coordinador de PawnIO y pruebas en CI | Agente | Este informe |
| 2 | Script Inno: instalar MACE en Program Files, página de PawnIO, llamar al coordinador, cierre manual si MACE está abierto, desinstalación que conserva PawnIO y los datos. Instalador sin firma como artefacto de CI | Agente | Pendiente |
| 3 | Botón en MACE ante `PAWNIO_MISSING`/`PAWNIO_STOPPED`, que reutiliza el coordinador | Agente (diff de producto para revisar) | Pendiente |
| 4 | Señal 3010 y aviso «reinicia Windows» (D4/D5) | Agente | Pendiente |
| 5 | Pruebas en VM o equipos con respaldo: limpio sin PawnIO, Intel+NVIDIA, Intel iGPU, AMD Ryzen+Radeon, sin iGPU, HVCI activo y sin red | Usuario, con guía exacta | Pendiente |
| 6 | Avisos de terceros finales, revisión legal y solicitud a SignPath | Usuario o mantenedor; el agente prepara la documentación | Pendiente |
| 7 | `MACE_PUBLICAR_RELEASES=true` y tag `v*` | Mantenedor | Pendiente |

## Límites

- No se instaló ni ejecutó PawnIO en ningún equipo.
- El CI descarga el setup oficial con su hash fijado y solo lo **verifica**.
- No se tocó `native/`, el ABI, el protocolo, el helper, los launchers ni la
  imagen congelada.
