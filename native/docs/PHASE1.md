# Fase 1 — DLL identificable y paquete verificable

La biblioteca mantiene los siete símbolos del Java actual y añade `CP_GetAbiInfo`, consultable antes de iniciar sensores. Devuelve ABI 1.1, versión de la estructura 2, compilación nativa 1.1.0, tamaños de los tres registros existentes, tamaños de puntero/`wchar_t` y capacidades honestamente implementadas. `MACE_CAP_CPU_USAGE` es el único bit activo en esta fase. Los demás tipos V2 son contratos preparados, sin funciones todavía.

CMake exige Windows, el compilador MSVC y proceso x64. Se probó Release y Debug; el runtime C de Release se enlaza estáticamente (`/MT`) para no añadir al paquete una dependencia del VC++ Redistributable. La DLL incluye recurso Windows de versión y asserts de layout compilados dentro de ella. La inicialización de sensores sigue ocurriendo al llamar `CP_InitializeTelemetry`, no al cargar la DLL. Los problemas de sensores y procesos documentados en la fase 0 continúan pendientes de fases 2–5.

`CMakePresets.json` ofrece Visual Studio 2022/2026 x64 en Debug/Release. `scripts/build-native.ps1` usa `vswhere` para encontrar MSVC y un CMake 3.28+; configura, compila y ejecuta CTest. No contiene rutas de usuario. La carpeta `build/` se regenera y está ignorada por Git.

`scripts/package-native.ps1` exige la fuente `native/` registrada en Git, recompila Release, ejecuta CTest, copia la DLL y su `.lib` desde esa salida concreta y verifica sus hashes. Después carga **la DLL copiada** para comprobar los ocho exports, los errores de capacidad, los campos ABI, el recurso de versión y un downcall FFM con JDK 22. Solo si pasa escribe `build-info.json` y `SHA256SUMS.txt` en un directorio nuevo de `native/dist/`. No copia nada a `app/` ni altera `packaging/`.

El paquete contiene `bin/mace_native.dll`, `lib/mace_native.lib`, `include/*.h`, documentación, el ejemplo FFM y metadatos/hash. El nombre incluye versión, fecha UTC y prefijo del hash de la DLL. `native/dist/` está ignorado para que un artefacto compilado no entre accidentalmente al control de fuentes.

Verificación de fase 1: compilación limpia Release y Debug, CTest de layouts/exports, FFM JDK 22 contra la DLL distribuida y comparación SHA-256 entre salida y paquete. La validación física CPU Intel/AMD y GPU Intel/AMD/NVIDIA pertenece a la fase 2. El workflow raíz todavía no ejecuta CTest; Dev 3 puede añadir `ctest --test-dir native/build -C Release --output-on-failure` al job `native-build` cuando integre el cambio fuera de `native/`.
