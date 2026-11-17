> El documento anterior íntegro se conserva en [el histórico](../docs/historico/native/pre1D-native__README.md). Corresponde al respaldo `1b62000`.

# MACE native

Biblioteca C17, Windows x64/MSVC, ABI 1.4. CPU uso: GetSystemTimes; RAM e inventario:
Windows/DXGI. Sensores CPU y AMD GPU: helper LHM; NVML e IGCL se conservan primarios.
La inicialización y los sondeos no esperan al helper. Los estados V2 distinguen
ausencia, calentamiento, fallo y lecturas válidas.

```powershell
# Produccion: publicar/firmar primero el helper; package-native exige Git limpio.
.\helper\Build-Helper.ps1
.\native\scripts\build-native.ps1 -Configuration Release
.\native\scripts\package-native.ps1
# Desarrollo/pruebas: autoriza expresamente las anulaciones del helper.
.\native\scripts\build-native.ps1 -Development -BuildDirectory .\native\build\dev
```

Desde Fase 2 Paso 2, MACE_LHM_DEVELOPMENT está OFF por defecto. Producción fija
el SHA-256 del helper-runtime.manifest después de firmar el helper y verifica
sus 202 dependencias antes de cada creación de proceso. Development mantiene
MACE_LHM_HELPER/MACE_LHM_HELPER_SHA256; esa DLL no se puede empaquetar como producto.
test-lhm1b.ps1 selecciona desarrollo explícitamente y también ejecuta fixtures
con perfil de producción. CMake directo requiere, en producción,
-DMACE_LHM_RUNTIME_MANIFEST=<publicación/helper-runtime.manifest>; en desarrollo
-DMACE_LHM_DEVELOPMENT=ON. No se cambia el ABI 1.4.

El segundo comando genera un paquete de desarrollo con cabeceras y diagnóstico;
no es el instalador del producto. packaging/package-windows.bat copia solo runtime
y exige helper publicado, PawnIO intacto y avisos. Véanse docs/TESTING.md y
../packaging/README.md. Firma/licencia pendientes; no se instalan componentes.
