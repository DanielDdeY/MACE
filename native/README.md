# MACE native — copia aislada

Esta rama contiene la línea base del repositorio y los entregables de la fase 0. La DLL sigue teniendo las funciones heredadas; los tipos V2 ya están definidos en cabeceras, pero sus funciones aún no están implementadas. Véanse [contrato ABI](docs/ABI.md), [pruebas de hardware](docs/HARDWARE_COMPATIBILITY.md) y [estado de la fase 0](docs/PHASE0.md).

Desde la raíz del repositorio, con CMake 3.28+, Visual Studio con MSVC x64 y el Windows SDK:

```powershell
cmake -S native -B native/build -A x64
cmake --build native/build --config Release
ctest --test-dir native/build -C Release --output-on-failure
native/build/Release/hardware_probe.exe
```

`hardware_probe.exe` identifica CPU y adaptadores GPU de este equipo; no prueba los sensores. `test_mace.exe` es la demostración heredada, con los límites documentados en `docs/ABI.md`. Para que la aplicación Java de la copia use la DLL recién compilada, se puede indicar la ruta absoluta mediante `MACE_NATIVE_LIB`. El launcher empaquetado tiene un manifiesto propio en `packaging/app.manifest`; ejecutar desde un IDE o Maven no aplica automáticamente su elevación.

Las salidas de `native/build/` se regeneran y están ignoradas por Git. El trabajo funcional de la DLL continuará en esta rama y solo dentro de `native/`.
