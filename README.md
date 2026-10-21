# MACE

Monitor de hardware y gestor manual de procesos para Windows 10/11 x64. La capa nativa en C/C++ detecta CPU, GPU integradas y dedicadas, RAM y procesos; Java 22 y JavaFX presentan las lecturas, alertas, historial y caja negra persistente. MACE nunca finaliza procesos automáticamente. La sección Micro-HUD está reservada para una futura implementación. El consumo eléctrico total del equipo todavía no se muestra.

## Ejecutables

El empaquetado `app-image` genera dos accesos en `packaging/dist/MACE/`:

- `MACE.exe`: datos reales con la DLL verificada; solicita permisos de administrador mediante su manifiesto.
- `MACE-Preview.exe`: escenario simulado para revisar la interfaz sin DLL ni permisos de administrador.

Distribuya la **carpeta MACE completa** o comprímala como ZIP; los ejecutables solos no contienen el runtime ni las dependencias. Los binarios generados se excluyen del repositorio Git. Para ofrecer una descarga en GitHub, adjunte ese ZIP a una *Release*; el repositorio conserva el código fuente.

## Compilar desde el código fuente

Se necesita Windows x64, Visual Studio 2022 o 2026 con MSVC y Windows SDK, CMake 3.28+, JDK 22+ y Maven. Desde la raíz del proyecto:

```powershell
.\native\scripts\package-native.ps1
.\packaging\package-windows.bat app-image
```

`package-native.ps1` compila `mace_native.dll` y el diagnóstico manual `test_mace.exe`, los agrupa en `native/dist/` y registra sus hashes. El empaquetador Windows toma el paquete nativo más reciente, comprueba su hash y ABI frente a Java, y crea ambos lanzadores. Puede fijar el paquete exacto con `MACE_NATIVE_LIB` apuntando a `bin\mace_native.dll`. Si modifica `app/`, ejecute de nuevo `package-windows.bat app-image` para actualizar los ejecutables.

Los proveedores Radeon ADLX y temperatura AMD Ryzen se compilan solo si se dispone de sus SDK oficiales fuera del repositorio. Para incluir ambos:

```powershell
.\native\scripts\package-native.ps1 `
  -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' `
  -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
.\packaging\package-windows.bat app-image
```

Sin esos SDK la compilación sigue siendo válida, pero las lecturas AMD correspondientes pueden figurar como «sin proveedor». Para consultar la temperatura Ryzen en ejecución también puede requerirse la instalación del SDK/controlador en el equipo y elevación. Los sensores Intel y NVIDIA dependen del controlador y del hardware disponible. Consulte [compatibilidad nativa](native/docs/HARDWARE_COMPATIBILITY.md).

Para revisar el hardware sin abrir Java, ejecute `test_mace.exe` junto a la DLL del mismo paquete nativo; `native/test_main.c` es la única prueba manual nativa mantenida. Las pruebas automatizadas Java se conservan en `app/src/test/` y se ejecutan con `mvn -f app/pom.xml test`. La [guía de diagnóstico](native/docs/TEAM_DIAGNOSTIC_GUIDE.md) describe las opciones y estados de los sensores.
