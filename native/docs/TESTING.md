# Diagnóstico manual de MACE native

`native/test_main.c` es el único programa de prueba nativo que se mantiene. CMake lo compila como `test_mace.exe` junto a `mace_native.dll`; ambos deben estar en la misma carpeta al ejecutarlo. El programa identifica CPU, GPU integradas y dedicadas, RAM y procesos, e informa el estado de cada lectura disponible. No finaliza procesos.

Desde la raíz del repositorio, con Visual Studio 2022 o 2026, MSVC x64 y CMake 3.28+:

```powershell
.\native\scripts\build-native.ps1 -Configuration Release
```

El script imprime la carpeta de salida, por ejemplo `native\build\vs2026-x64\Release`. Desde allí ejecute `test_mace.exe`; use `--help` para ver sus opciones y `--all-processes` si necesita el inventario completo. En Visual Studio, abra `native/`, elija el preset de su versión y seleccione `test_mace.exe` como elemento de inicio.

Para compilar sensores Radeon y temperatura AMD Ryzen, pase los SDK oficiales externos mediante `-AdlxSdkDirectory` y `-RyzenSdkDirectory`. Sin ellos la DLL se compila y detecta los dispositivos, pero esas lecturas pueden aparecer como «sin proveedor». La temperatura AMD puede necesitar ejecutar el diagnóstico como administrador y tener instalado el controlador del Ryzen Master Monitoring SDK. Consulte [compatibilidad](HARDWARE_COMPATIBILITY.md) y la [guía para el grupo](TEAM_DIAGNOSTIC_GUIDE.md).

Para crear un paquete nativo identificable y después los dos ejecutables de la aplicación, siga las instrucciones de [README principal](../../README.md). Las pruebas automatizadas Java de `app/src/test` se mantienen en Maven.
