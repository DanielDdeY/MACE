# MACE

[![CI](https://github.com/DanielDdeY/MACE/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/DanielDdeY/MACE/actions/workflows/build-and-test.yml)
[![Entrega continua](https://github.com/DanielDdeY/MACE/actions/workflows/release-installer.yml/badge.svg)](https://github.com/DanielDdeY/MACE/actions/workflows/release-installer.yml)
[![Última versión](https://img.shields.io/github/v/release/DanielDdeY/MACE?include_prereleases&label=versi%C3%B3n)](https://github.com/DanielDdeY/MACE/releases)

Monitor de hardware y gestor manual de procesos para Windows 10/11 x64. La capa nativa en C/C++ detecta CPU, GPU integradas y dedicadas, RAM y procesos; Java 22 y JavaFX presentan las lecturas, alertas, historial y caja negra persistente. MACE nunca finaliza procesos automáticamente. El Micro-HUD muestra las métricas principales en una ventana compacta superpuesta. El consumo eléctrico total del equipo todavía no se muestra.

## Características

Uso de CPU, RAM y GPU. 
Temperatura y potencia de CPU, y temperatura, potencia y ventilador de algunas GPU.
Sin instalación manual de controladores ni SDK por parte del usuario: el instalador de MACE incluye lo necesario. 

## Requisitos

Windows 10 o 11, x64.
Permisos de administrador: MACE los solicita al abrirse para leer sensores de CPU.

## Ejecutables

El empaquetado `app-image` genera dos accesos en `packaging/dist/MACE/`:

- `MACE.exe`: datos reales con la DLL verificada; solicita permisos de administrador mediante su manifiesto.
- `MACE-Preview.exe`: escenario simulado para revisar la interfaz sin DLL ni permisos de administrador.

Distribuya la **carpeta MACE completa** o comprímala como ZIP; los ejecutables solos no contienen el runtime ni las dependencias. Los binarios generados se excluyen del repositorio Git.

## Descarga

La página de descarga es <https://danielddey.github.io/MACE/> (carpeta `docs/`, publicada por GitHub Pages en cada cambio de `main`). Ofrece el `MACE-Setup.exe` de la última versión estable de [Releases](https://github.com/DanielDdeY/MACE/releases).

Sin pasos extra, MACE muestra uso de CPU, RAM, procesos y GPU NVIDIA/Intel. La temperatura y la potencia de la CPU son **opcionales**: requieren el controlador PawnIO (su instalador oficial va incluido, sin modificar, en `app\prerequisites\`; MACE nunca lo ejecuta por su cuenta) y abrir MACE como administrador.

## Flujo de trabajo del equipo

1. **Issue**: toda tarea o error se registra con la plantilla correspondiente, una etiqueta de tipo (`bug`, `enhancement`, `documentation`) y de área (`area:native`, `area:java`, `area:ui`, `area:ci-cd`), un responsable y un hito (`v1.1 Windows completo`, `v1.2 Linux`).
2. **Rama**: se trabaja en una rama por issue (`feat/…`, `fix/…`, `docs/…`, `ci/…`), nunca directamente en `main`.
3. **Pull request**: usa la plantilla, enlaza el issue con `Closes #N` (se cierra solo al fusionar) y pide revisión a otro integrante.
4. **CI**: el PR solo se fusiona con el check en verde.
5. **Entrega**: un tag de versión publica la Release automáticamente.

## Integración continua (CI)

`.github/workflows/build-and-test.yml` se ejecuta en cada push y pull request a `main`, en Windows con MSVC, JDK 22 y el SDK de .NET de `helper/global.json`:

| Paso | Script | Comprueba |
|---|---|---|
| 1 | `helper/Build-Helper.ps1` | Restauración bloqueada, pruebas y publicación del helper LibreHardwareMonitor |
| 2 | `native/scripts/test-lhm1b.ps1` | DLL perfil desarrollo y sus pruebas CTest |
| 3 | `native/scripts/build-native.ps1` | DLL perfil producción ligada al manifiesto del helper y sus pruebas CTest |
| 4 | `native/scripts/test-java-regression.ps1` | Pruebas JUnit de Java contra la DLL real |
| 5 | `native/scripts/check-protected-telemetry.ps1` | Que no cambie el código de telemetría protegido (NVML, IGCL, DXGI, ABI) |
| 6 | `native/scripts/audit-retired-providers.ps1` | Que no vuelvan proveedores de hardware retirados |

Los informes de pruebas quedan como artefacto de cada ejecución en la pestaña *Actions*. No instala controladores, no se eleva y no publica binarios.

## Entrega continua (CD)

`.github/workflows/release-installer.yml` publica una versión al subir un tag:

```powershell
git tag v1.1.0-rc1; git push origin v1.1.0-rc1   # pre-release de prueba
git tag v1.1.0;     git push origin v1.1.0       # versión estable (la ofrece la página de descarga)
```

```text
tag v* ─► CI completo (compuerta) ─► helper LHM ─► DLL de producción ligada al helper
       ─► verificación de hash, ABI y contenido ─► MACE-Setup.exe + MACE-portable.zip
       ─► GitHub Release con SHA256SUMS ─► página de descarga (versiones estables)
```

Si cualquier prueba falla no se publica nada. Los tags con sufijo (`-rc1`, `-beta`) se publican como *pre-release* y no sustituyen la versión que ofrece la página. Pendiente: firma de código (Windows SmartScreen puede advertir) y la revisión de redistribución de PawnIO (`packaging/pawnio-2.2.0.json`).

## Compilar desde el código fuente

Se necesita Windows x64, Visual Studio 2022 o 2026 con MSVC y Windows SDK, CMake 3.28+, JDK 22+ y Maven. Desde la raíz del proyecto:

```powershell
.\native\scripts\package-native.ps1
.\packaging\package-windows.bat app-image
```

`package-native.ps1` compila `mace_native.dll` y el diagnóstico manual `test_mace.exe`, los agrupa en `native/dist/` y registra sus hashes. El empaquetador Windows toma el paquete nativo más reciente, comprueba su hash y ABI frente a Java, y crea ambos lanzadores. Puede fijar el paquete exacto con `MACE_NATIVE_LIB` apuntando a `bin\mace_native.dll`. Si modifica `app/`, ejecute de nuevo `package-windows.bat app-image` para actualizar los ejecutables.

La temperatura y la potencia de la CPU (Intel y AMD) y las lecturas de GPU Radeon se obtienen mediante el helper LibreHardwareMonitor (`helper/`), que se compila con el SDK de .NET indicado en `helper/global.json`. En ejecución, esas lecturas requieren el controlador PawnIO instalado y abrir MACE como administrador; sin ellos la interfaz muestra el motivo (por ejemplo, «Ejecuta MACE como administrador») en lugar de inventar valores. Las GPU NVIDIA usan NVML y las GPU Intel IGCL, con LibreHardwareMonitor como respaldo. El uso de CPU y la RAM provienen de Windows. Consulte [compatibilidad nativa](native/docs/HARDWARE_COMPATIBILITY.md).

Para revisar el hardware sin abrir Java, ejecute `test_mace.exe` junto a la DLL del mismo paquete nativo; `native/test_main.c` es la única prueba manual nativa mantenida. Las pruebas automatizadas Java se conservan en `app/src/test/` y se ejecutan con `mvn -f app/pom.xml test`. La [guía de diagnóstico](native/docs/TEAM_DIAGNOSTIC_GUIDE.md) describe las opciones y estados de los sensores.

## Componentes de terceros

MACE incluye o utiliza componentes de terceros con sus propias licencias, entre ellos LibreHardwareMonitor (MPL-2.0) y el controlador PawnIO. La lista completa está en THIRD-PARTY-NOTICES.txt.

## Licencia

MACE se distribuye bajo la licencia MIT. Los componentes de terceros conservan sus licencias originales.
