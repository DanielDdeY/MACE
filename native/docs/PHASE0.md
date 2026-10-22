# Fase 0 — línea base y contrato de integración

Fecha: 15-10-2026. Copia aislada `mace-native-dev`, rama local `codex/native-phase0`, base `5a1d534e0fe09e27c94c29ff19483a212516adb4`. La copia incluye los archivos **versionados** del repositorio original, incluidos `.github/workflows/build-and-test.yml`, `.gitattributes`, `.gitignore`, `app/` y `packaging/`. El ZIP sin registrar, `.vs/`, `.vscode/`, salidas de compilación y la DLL ignorada de `app/lib/` no se copiaron. El remoto Git de la copia se retiró para evitar envíos o modificaciones accidentales del repositorio de origen.

La carpeta de origen conserva `MACE.zip` como referencia y no se editó. Todas las modificaciones de esta fase están dentro de `native/`; `app/` y `packaging/` de la copia siguen iguales al commit base.

## Entregables completados

- `docs/ABI.md`: siete símbolos heredados, retornos reales, tres layouts existentes y offsets FFM, unidades, errores, propiedad de buffers, límites del manifiesto y alcance Windows 10/11 x64.
- `include/mace_status.h`, `mace_types_v2.h`, `mace_extensions.h`: tipos y estados V2 para telemetría por métrica/adaptador, identidad de procesos, respuesta de ventanas y buffer circular. **Son definiciones de contrato; la DLL todavía no exporta funciones V2.**
- `tests/test_abi_layouts.c` y CTest: asserts de tamaños, alineaciones, offsets y premisas MSVC x64/UTF-16. Incluye tanto las tres estructuras anteriores como las nuevas.
- `tests/hardware_probe.c`: prueba opcional CPUID/DXGI de identificación automática. No requiere que el usuario indique marcas y no se ejecuta como prueba determinista en CI.
- `docs/HARDWARE_COMPATIBILITY.md`: matriz de lo medido en este equipo y lo que aún requiere hardware/proveedor. Propone probar primero AMDPowerProfileAPI para CPU/APU, sin asumir cobertura universal.

## Verificación realizada

La copia se configuró y compiló en Release con CMake de Visual Studio 2026, MSVC 19.51 y SDK de Windows 10.0.26100.0. Se generó `native/build/Release/mace_native.dll` dentro de la copia (SHA-256 `C92D8E0FEA6E70FBB0A72D9FAFAC45931901C6C1D270533281CEFE893C3AECD1`), junto con `test_mace.exe`, `test_abi_layouts.exe` y `hardware_probe.exe`. La DLL coincide funcionalmente con la línea base: la fase 0 no corrigió sensores ni otros defectos.

`ctest --test-dir native/build -C Release --output-on-failure`: **1/1 prueba aprobada** (`abi_layouts`). La salida del probe identificó CPU AMD familia 23/modelo 113 y GPU Radeon RX 5500 XT mediante DXGI; descartó como adaptador físico a Microsoft Basic Render Driver. El programa heredado entregó CPU usage >0 y 91 procesos visibles en esta ejecución; indicó CPU temperatura/potencia 0 y `is_cpu_sensor_available=0`, así como GPU no disponible para su proveedor NVML.

`mvn -B test` en `app/` de la copia: **71/71 pruebas aprobadas** sin cambiar archivos Java. La aplicación localizó la DLL recién compilada en `native/build/Release` durante sus pruebas de integración. El manifiesto real de `packaging/app.manifest` pasó `mt.exe -validate_manifest -check_for_duplicates`.

La carpeta `native/build/` está ignorada por `.gitignore`. Para ejecutar la aplicación Java de esta copia en modo nativo se debe reconstruir la DLL y fijar `MACE_NATIVE_LIB` a su ruta absoluta, o permitir que `NativeLibraryLocator` encuentre `native/build/Release`. Esto evita tomar la DLL antigua de `app/lib/` del proyecto original. No se modificó `app/` para forzar esa selección.

El workflow recibido ejecuta Maven en `app/` y compila `native/` en `windows-latest`, pero **no llama a CTest**. En esta fase la prueba ya está conectada a CMake; el cambio de `.github/workflows/build-and-test.yml` se deja a la integración que puede tocar archivos fuera de `native/`. `packaging/package-windows.bat` ya compila `native/`, copia la DLL y usa `mt.exe` para el manifiesto; no hay que crear otro empaquetador del launcher. La fase 1 debe hacer verificable qué DLL se entrega.

## Límite de la prueba técnica de sensores

El equipo disponible es AMD de escritorio y no tiene en este repositorio un proveedor oficial de temperatura/potencia AMD; tampoco hay aquí una CPU Intel ni una APU portátil para medir. La detección automática de CPU/GPU quedó probada, pero **las lecturas físicas Intel/AMD y la elección final del proveedor AMD no están verificadas**. La documentación oficial hace viable una prueba con AMD uProf PowerProfileAPI, cuya cobertura efectiva, dependencias y términos de redistribución se comprobarán en la fase 2. No se debe afirmar que la capa nativa ya soporta sensores de ambas marcas.

## Próxima intervención

La fase 1 incorporará identificación ABI real, asserts en la biblioteca, empaquetado reproducible de la DLL y pruebas del binario distribuido. La fase 2 implementará proveedores y métricas por adaptador tras la prueba física de los SDK. Antes de integrar en la rama principal, Dev 3 deberá conectar las nuevas consultas FFM y actualizar el CI para ejecutar CTest; esta copia aislada permite revisar los cambios sin tocar `main`.
