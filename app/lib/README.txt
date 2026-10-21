MACE - biblioteca nativa
========================

No copies mace_native.dll en esta carpeta ni la subas a Git. Usa siempre la DLL del
paquete verificado por Dev 1 (native\scripts\package-native.ps1 o el ZIP entregado):

    <paquete>\bin\mace_native.dll        DLL 1.4.x, ABI 1.4
    <paquete>\build-info.json            SHA-256 (dllSha256) y SDK compilados (ADLX / Ryzen)
    <paquete>\bin\test_mace.exe          diagnostico C para separar un problema de sensor de uno de FFM/UI

Ejecutar con datos reales (Windows 10/11 x64, JDK 22+):

    set MACE_NATIVE_LIB=C:\ruta\al\paquete\bin\mace_native.dll
    set MACE_NATIVE_SHA256=<dllSha256 de build-info.json>     (opcional: rechaza otra DLL)
    mvn javafx:run

o con propiedades JVM: -Dmace.native.lib=<ruta> -Dmace.native.sha256=<hash>
y siempre --enable-native-access=ALL-UNNAMED.

Antes de usar cualquier funcion V2, Java llama a CP_GetAbiInfo y rechaza la DLL si no es
ABI 1.x (x >= 4), version 2, punteros de 8 bytes, wchar_t de 2 bytes y tamanos heredados
1584/28/16. Una DLL anterior (sin CP_GetAbiInfo) se rechaza con un mensaje claro. La DLL
permanece cargada hasta que termina el proceso (requisito de ADLX, native/docs/PHASE5.md).

Modos (-Dmace.infra.mode):
    native  exige la DLL; falla con el motivo si falta, no coincide el hash o es incompatible
    mock    datos simulados; nunca carga la DLL ni requiere administrador
    auto    usa la DLL si es compatible; si no, cae a mock e informa el motivo a la UI

Vista previa sin DLL ni administrador:

    mvn javafx:run -Ppreview
    mvn javafx:run -Ppreview -Dmace.mock.scenario=DUAL_GPU

Escenarios: NORMAL, CPU_NO_TEMPERATURE, AMD_NO_POWER, ACCESS_DENIED, IGPU_NO_FAN, DUAL_GPU,
GPU_INACTIVE, GPU_NO_SENSOR, WARMING_UP, THERMAL_WARNING, THERMAL_CRITICAL, NO_GPU, BLACKBOX_GAP.

Pruebas de integracion con la DLL real (se omiten si no hay DLL):

    set MACE_NATIVE_LIB=C:\ruta\al\paquete\bin\mace_native.dll
    mvn test

Disponibilidad fisica: un bit de capacidad indica funcion implementada, no sensor presente.
La temperatura de CPU AMD requiere Ryzen Master Monitoring SDK instalado en el equipo y
ejecutar elevado; la potencia AMD no tiene proveedor; Intel depende de WinRing0 (no se
distribuye). Las ausencias se muestran por estado (NO_PROVIDER, ACCESS_DENIED...), no son
fallos del puente FFM.
