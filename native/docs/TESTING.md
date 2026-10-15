# Pruebas del módulo nativo

Desde la raíz del repositorio ejecutar `native/scripts/build-native.ps1 -Configuration Release` y, para Debug, `native/scripts/build-native.ps1 -Configuration Debug`. El script detecta Visual Studio 2022/2026 y CMake 3.28+ mediante `vswhere`; usa carpetas de compilación por generador. No elimina ni mueve directorios existentes. También se puede usar `CMakePresets.json` desde `native/`.

CTest ejecuta `abi_layouts` (compilación y offsets antiguos/V2) y `abi_exports` (carga por ruta de la DLL real, ocho símbolos, consulta ABI antes de inicializar, buffer corto intacto, canary y versión de Windows). `scripts/verify-native.ps1` repite la prueba de exportaciones sobre la ruta de DLL indicada y ejecuta el ejemplo FFM con JDK 22. `scripts/package-native.ps1` llama a ambos sobre el artefacto que distribuye.

`hardware_probe.exe` es una prueba manual de CPUID/DXGI: identifica CPU y adaptadores sin pedir marcas al usuario. `test_mace.exe` es una demostración heredada dependiente del equipo; no constituye una prueba automática de sensores. Las lecturas físicas Intel/AMD, APUs móviles y GPU de las tres marcas se registrarán en `HARDWARE_COMPATIBILITY.md` cuando existan los proveedores y equipos correspondientes.
