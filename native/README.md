# MACE native

Biblioteca C17/C++17 para Windows 10/11 x64. La versión nativa 1.4.2 conserva la API usada por Java y añade telemetría V2, gestión protegida de procesos, bases nativas de las extensiones y un ciclo de vida sincronizado. Véanse [ABI](docs/ABI.md), [fase 5](docs/PHASE5.md), [extensiones](docs/EXTENSIONS.md), [guía de prueba para el grupo](docs/TEAM_DIAGNOSTIC_GUIDE.md) y [entrega a los demás desarrolladores](docs/HANDOFF.md).

Desde la raíz del repositorio, con Visual Studio 2022 o 2026 y las herramientas C++/Windows SDK instaladas:

```powershell
.\native\scripts\build-native.ps1 -Configuration Release
.\native\scripts\build-native.ps1 -Configuration Debug
.\native\scripts\package-native.ps1
```

Para compilar el proveedor Radeon, descargue el SDK oficial ADLX de AMD y pase su carpeta raíz con `-AdlxSdkDirectory "C:\ruta\ADLX"` a los scripts de compilación y empaquetado. La DLL usa en ejecución `amdadlx64.dll` del controlador AMD; no se distribuyen encabezados ADLX con MACE. Sin el SDK, se compila el inventario DXGI y la capacidad de memoria, pero los sensores Radeon quedan sin proveedor. El encabezado IGCL de Intel se incluye con su licencia en `vendor_headers/intel_igcl`.

Para compilar la temperatura de CPU AMD, instale Ryzen Master **Monitoring SDK** y pase su raíz con `-RyzenSdkDirectory "C:\Program Files\AMD\RyzenMasterMonitoringSDK"`. En ejecución, la DLL busca `Platform.dll` en la instalación registrada por el SDK; requiere su controlador y un proceso elevado. La app Ryzen Master sin el Monitoring SDK no basta. El SDK no se copia al repositorio ni al paquete MACE. Consulte [prueba y límites de CPU AMD](docs/AMD_CPU_TEMPERATURE.md).

Para incluir ambos proveedores AMD en la misma DLL, pase las dos rutas al compilar y empaquetar:

```powershell
.\native\scripts\build-native.ps1 -Configuration Release -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
.\native\scripts\package-native.ps1 -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
```

El script encuentra un CMake 3.28+ y ejecuta CTest. Para distribuir también exige JDK 22, porque verifica la DLL copiada en `native/dist/` mediante el ejemplo FFM. Las carpetas `native/build/` y `native/dist/` son salidas regenerables ignoradas por Git. El empaquetado falla si hay cambios de fuente en `native/` sin registrar, para que `build-info.json` pueda señalar una revisión exacta. El paquete indica por separado si se compilaron los proveedores ADLX y Ryzen Master Monitoring SDK.

La aplicación Java puede utilizar el binario verificado indicando su ruta absoluta mediante `MACE_NATIVE_LIB` o `-Dmace.native.lib`. La DLL no solicita elevación por sí misma: hereda el token del proceso que la carga. El manifiesto de `MACE.exe` y su script de empaquetado están en `packaging/`.
