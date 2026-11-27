# Fase 3 — Instalador: estado y traspaso (cierre de sesión 2026-11-27)

Fecha: 2026-11-27, America/Lima. Autor de los commits: ALI-SKAN.
Actualiza [paso 1, coordinador](FASE3-INSTALADOR-PASO1-COORDINADOR.md). Para
retomar, leer este archivo, [installer/README.md](../installer/README.md) y
[installer/PRUEBAS-USUARIO.md](../installer/PRUEBAS-USUARIO.md).

## Ramas y estado verificado

| Rama | Último commit | CI | Contenido |
|---|---|---|---|
| `LHM_implementado` | 73918ec | **Verde** (run 45923697041) | `main` fusionado y CI adaptado a Fase 3: job `herramientas-auditoria`; publicación de releases bloqueada sin `MACE_PUBLICAR_RELEASES=true` |
| `feat/instalador-pawnio` | 32c1c45 verificado; después solo documentación, página de plataformas y acciones v5 (ver `git log`) | **Verde** CI (run 45927167375) y entrega (run 45927167361) en 32c1c45 | Coordinador PawnIO, instalador Inno, botón «Instalar PawnIO», guía de pruebas, página con apartados Windows/Linux/macOS |

Instalador de prueba: artefacto `mace-instalador-prueba-7` del run 45927167361,
68,4 MiB, **sin firma**, retención de 3 días (decisión deliberada mientras la
revisión legal siga abierta). Para generar otro: push a `feat/instalador-**` o
Actions → «Entrega continua» → Re-run.

## Qué está VERIFICADO y qué no

| Afirmación | Estado |
|---|---|
| CI completo (helper, nativo dev/prod, Java, auditorías, herramientas) | VERIFICADO — AUTOMATIZADO en GitHub Actions |
| Coordinador: éxito, 3010, fallo, hash, firma, servicio presente, timeout sin kill, log saneado, binario de producto sin ganchos | VERIFICADO — AUTOMATIZADO, con setup simulado |
| Setup oficial PawnIO 2.2.0: hash y firma `namazso.eu` | VERIFICADO — AUTOMATIZADO (`--verify-only`; **no ejecutado**) |
| App-image + `MACE-Setup.exe` compilan con Inno 6 | VERIFICADO — AUTOMATIZADO |
| `PawnIoInstallerTest` (6) y pruebas de UI afectadas (17 en total) | VERIFICADO en local con JDK 23 y en CI con JDK 22 |
| Instalar MACE/PawnIO en un Windows real, botón, desinstalación, HVCI, sin red | **NO VERIFICADO** — requiere las pruebas de [PRUEBAS-USUARIO.md](../installer/PRUEBAS-USUARIO.md) |
| Sensores en AMD Ryzen+Radeon, Intel+NVIDIA, iGPU Intel, equipo sin iGPU con la imagen nueva | **NO VERIFICADO** |
| Recuperación en caliente de la temperatura tras instalar PawnIO sin reiniciar MACE | **NO VERIFICADO** (el mensaje sugiere reabrir MACE o reiniciar Windows) |

## Decisiones tomadas por el usuario en esta sesión

- Commits como **ALI-SKAN**, sin atribución a Claude.
- Antes de fusionar a `main`, el CI adaptado debe estar terminado: **cumplido**
  en `LHM_implementado`.
- Releases bloqueadas hasta la revisión legal (variable `MACE_PUBLICAR_RELEASES`).
- PawnIO: **instalador + botón en MACE**. Casilla **marcada y visible**; en modo
  silencioso solo con `/ACCEPT_PAWNIO=1`.
- Desarrollo en `feat/instalador-pawnio`; lectura (solo) de documentación y
  licencia de PawnIO autorizada.

## Pendientes, en orden

| # | Tarea | Responsable | Bloquea |
|---|---|---|---|
| 1 | Ejecutar [PRUEBAS-USUARIO.md](../installer/PRUEBAS-USUARIO.md) en una VM con instantánea o un equipo con respaldo (no en la PC de la universidad: sin admin) | Usuario | Todo lo demás |
| 2 | Corregir lo que salga de las pruebas | Agente/equipo | Fusión |
| 3 | Aviso «Reinicia Windows» tras 3010 (D4/D5): clave volátil y aviso Java | Agente/equipo | Experiencia completa tras reinicio |
| 4 | PR `feat/instalador-pawnio` → `LHM_implementado`, después `LHM_implementado` → `main` (revisión del mantenedor) | Usuario y DanielDdeY | Publicación |
| 5 | Avisos de terceros finales (incluidos Inno y el coordinador con CRT estático), revisión legal y solicitud a SignPath | Mantenedor; el agente prepara la documentación | Release pública |
| 6 | `MACE_PUBLICAR_RELEASES=true` y tag `v*` | Mantenedor | — |
| — | Bloqueo previo de la candidata Temurin 25/JavaFX 27 (commons-io offline, B11–B13) | Sin cambios; ver [PENDIENTE-FASE-3B.md](PENDIENTE-FASE-3B.md) | Migración de JDK, no este instalador |

Avisos menores: `actions/setup-java@v4` y `checkout@v4` emiten avisos de
deprecación (Node 20). Conviene actualizarlos a v5 en un cambio aparte.

## Continuar en mi PC (paso a paso)

### Qué esperar antes de los PR

1. Tus compañeros prueban el instalador con [PRUEBAS-USUARIO.md](../installer/PRUEBAS-USUARIO.md)
   y te pasan lo que anotaron.
2. Se corrige lo que haya fallado en `feat/instalador-pawnio`.
3. Después, PR `feat/instalador-pawnio` → `LHM_implementado` y luego
   `LHM_implementado` → `main` (revisión de DanielDdeY). Abrirlos como
   **borrador** si se crean antes de terminar las pruebas.

La página de descargas (GitHub Pages) solo cambia cuando `docs/` llega a `main`.

### Preparar tu PC (una sola vez)

Necesitas Git y una cuenta de GitHub con acceso al repositorio. En PowerShell:

```powershell
git clone https://github.com/DanielDdeY/MACE.git
cd MACE
git checkout feat/instalador-pawnio
git config user.name ALI-SKAN
git config user.email <el correo de tu cuenta de GitHub>
git log --oneline -3      # el primero debe ser el último commit de este documento o posterior
```

Para compilar y probar Java en local: JDK 22 o posterior y Maven
(`mvn -f app/pom.xml test`). Lo nativo, el coordinador y el instalador se
compilan en GitHub Actions; en local harían falta Visual Studio con C++,
CMake 3.28+ e Inno Setup 6.

### Conseguir un instalador nuevo para las pruebas

El artefacto caduca a los 3 días. Para generar otro: GitHub → pestaña
**Actions** → «Entrega continua - Instalador de Windows» → último run de
`feat/instalador-pawnio` → **Re-run all jobs**. Al terminar (~15 min), el ZIP
`mace-instalador-prueba-<n>` aparece al final de la página del run.

### Retomar con Claude Code

Abre la carpeta `MACE` en VS Code con Claude Code y escribe:

> Lee helper/FASE3-INSTALADOR-ESTADO-20261127.md, installer/README.md e
> installer/PRUEBAS-USUARIO.md. Estoy en la rama feat/instalador-pawnio.
> Commits a nombre de ALI-SKAN, sin atribución a Claude. Estos son los
> resultados de las pruebas de mis compañeros: <pega aquí lo anotado>.
> Continúa con lo pendiente.

Adjunta también `CONTEXTO.md` y `METODO_ENTREGA.md` (las reglas del proyecto)
si los tienes: se borraron del repositorio y Claude en tu PC no los verá de otro modo.

Compilar Java en local: JDK 22+ y Maven (`mvn -f app/pom.xml test`). El
coordinador y el instalador se compilan en Actions; en local requieren MSVC,
CMake 3.28+ e Inno Setup 6 (ver [installer/README.md](../installer/README.md)).
