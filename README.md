# MACE

Monitor de hardware y gestor manual de procesos para Windows 10/11 x64. La capa nativa en C/C++ detecta CPU, GPU integradas y dedicadas, RAM y procesos; Java 22 y JavaFX presentan las lecturas, alertas, historial y caja negra persistente. MACE nunca finaliza procesos automáticamente. La sección Micro-HUD está reservada para una futura implementación. El consumo eléctrico total del equipo todavía no se muestra.

## Ejecutables

El empaquetado `app-image` genera dos accesos en `packaging/dist/MACE/`:

- `MACE.exe`: datos reales con la DLL verificada; solicita permisos de administrador mediante su manifiesto.
- `MACE-Preview.exe`: escenario simulado para revisar la interfaz sin DLL ni permisos de administrador.

Distribuya la **carpeta MACE completa** o comprímala como ZIP; los ejecutables solos no contienen el runtime ni las dependencias. Los binarios generados se excluyen del repositorio Git.

## Descarga y publicación de versiones

La página de descarga es <https://danielddey.github.io/MACE/> (carpeta `docs/`). Ofrece el `MACE-Setup.exe` de la última *Release*. Para publicar una versión basta con subir un tag:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

El flujo `.github/workflows/release-installer.yml` crea la Release y adjunta `MACE-Setup.exe`, `MACE-portable.zip` y sus hashes. Si la Release ya trae el paquete nativo verificado (`mace-native-*.zip`), se empaqueta esa DLL; si no, se compila en GitHub Actions sin los SDK de AMD.

## Compilar desde el código fuente

Se necesita Windows x64, Visual Studio 2022 o 2026 con MSVC y Windows SDK, CMake 3.28+, JDK 22+ y Maven. Desde la raíz del proyecto:

```powershell
.\native\scripts\package-native.ps1
.\packaging\package-windows.bat app-image
```

`package-native.ps1` compila `mace_native.dll` y el diagnóstico manual `test_mace.exe`, los agrupa en `native/dist/` y registra sus hashes. El empaquetador Windows toma el paquete nativo más reciente, comprueba su hash y ABI frente a Java, y crea ambos lanzadores. Puede fijar el paquete exacto con `MACE_NATIVE_LIB` apuntando a `bin\mace_native.dll`. Si modifica `app/`, ejecute de nuevo `package-windows.bat app-image` para actualizar los ejecutables.

La temperatura y la potencia de la CPU (Intel y AMD) y las lecturas de GPU Radeon se obtienen mediante el helper LibreHardwareMonitor (`helper/`), que se compila con el SDK de .NET indicado en `helper/global.json`. En ejecución, esas lecturas requieren el controlador PawnIO instalado y abrir MACE como administrador; sin ellos la interfaz muestra el motivo (por ejemplo, «Ejecuta MACE como administrador») en lugar de inventar valores. Las GPU NVIDIA usan NVML y las GPU Intel IGCL, con LibreHardwareMonitor como respaldo. El uso de CPU y la RAM provienen de Windows. Consulte [compatibilidad nativa](native/docs/HARDWARE_COMPATIBILITY.md).

Para revisar el hardware sin abrir Java, ejecute `test_mace.exe` junto a la DLL del mismo paquete nativo; `native/test_main.c` es la única prueba manual nativa mantenida. Las pruebas automatizadas Java se conservan en `app/src/test/` y se ejecutan con `mvn -f app/pom.xml test`. La [guía de diagnóstico](native/docs/TEAM_DIAGNOSTIC_GUIDE.md) describe las opciones y estados de los sensores.
