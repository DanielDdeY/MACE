# MACE native

Biblioteca C17 para Windows 10/11 x64. La versión nativa 1.1.0 conserva la API usada por Java y añade una consulta de contrato (`CP_GetAbiInfo`). Los proveedores CPU/GPU adicionales y las extensiones siguen en desarrollo. Véanse [ABI](docs/ABI.md), [estado de fase 1](docs/PHASE1.md) y [entrega a los demás desarrolladores](docs/HANDOFF.md).

Desde la raíz del repositorio, con Visual Studio 2022 o 2026 y las herramientas C++/Windows SDK instaladas:

```powershell
.\native\scripts\build-native.ps1 -Configuration Release
.\native\scripts\build-native.ps1 -Configuration Debug
.\native\scripts\package-native.ps1
```

El script encuentra un CMake 3.28+ y ejecuta CTest. Para distribuir también exige JDK 22, porque verifica la DLL copiada en `native/dist/` mediante el ejemplo FFM. Las carpetas `native/build/` y `native/dist/` son salidas regenerables ignoradas por Git. El empaquetado falla si hay cambios de fuente en `native/` sin registrar, para que `build-info.json` pueda señalar una revisión exacta.

La aplicación Java puede utilizar el binario verificado indicando su ruta absoluta mediante `MACE_NATIVE_LIB` o `-Dmace.native.lib`. La DLL no solicita elevación por sí misma: hereda el token del proceso que la carga. El manifiesto de `MACE.exe` y su script de empaquetado están en `packaging/`.
