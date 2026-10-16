# Entrega para los otros desarrolladores

El paquete verificado está bajo `native/dist/mace-native-1.2.0-win-x64-.../`. Usar `bin/mace_native.dll` de ese paquete y cotejar su SHA-256 con `build-info.json` o `SHA256SUMS.txt`. El `.lib` sirve para consumidores C; Java FFM carga directamente la DLL. Las cabeceras publicadas definen el contrato C.

El Java actual enlaza los siete símbolos anteriores y no necesita cambios inmediatos. Para pruebas se puede fijar `MACE_NATIVE_LIB` o `-Dmace.native.lib` a la ruta absoluta del binario de `dist/`; así se evita cargar una DLL antigua de `app/lib/`. El script `packaging/package-windows.bat` del repositorio aún no consulta el ABI ni los hashes; Dev 3 deberá integrarlo cuando se trabaje en `packaging/`.

`CP_GetAbiInfo` usa el descriptor FFM `FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)`. La llamada `NULL, 0, &required` devuelve `-2` y `required=64`; después se reserva un segmento de 64 bytes alineado a 8. El ejemplo en `examples/ffm/NativeContractProbe.java` valida ABI 1.2 y tamaños 1584/28/16. Para los cuatro símbolos nuevos, consultar `ABI.md`; el LUID de cada GPU se obtiene con `CP_EnumerateGpuAdapters` y solo vale en la sesión actual de Windows.

Los registros heredados mantienen exactamente su layout. El campo `gpu_fan_rpm` ahora contiene RPM reales o cero; para distinguir métrica no soportada, usar V2. `build-info.json` indica `amdAdlxCompiled`: una DLL compilada sin el SDK ADLX mantiene inventario Radeon, pero no sus sensores. No copiar los encabezados ADLX al repositorio; el SDK oficial se pasa como ruta externa de compilación. La DLL no exige que el usuario final instale el SDK; requiere un controlador AMD compatible con ADLX.

La prueba física Radeon está documentada en `PHASE2.md`. Intel GPU, NVIDIA GPU, APU híbrida e Intel CPU requieren equipos adicionales. Temperatura/potencia CPU AMD siguen sin proveedor y deben mostrarse como no disponibles. Los módulos de proceso seguro, respuesta detallada y caja negra corresponden a fases posteriores.
