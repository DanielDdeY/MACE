> El documento anterior íntegro se conserva en [el histórico](../docs/historico/native/pre1D-native__README.md). Corresponde al respaldo `1b62000`.

# MACE native

Biblioteca C17, Windows x64/MSVC, ABI 1.4. CPU uso: GetSystemTimes; RAM e inventario:
Windows/DXGI. Sensores CPU y AMD GPU: helper LHM; NVML e IGCL se conservan primarios.
La inicialización y los sondeos no esperan al helper. Los estados V2 distinguen
ausencia, calentamiento, fallo y lecturas válidas.

```powershell
.\native\scripts\build-native.ps1 -Configuration Release
.\native\scripts\package-native.ps1
```

El segundo comando genera un paquete de desarrollo con cabeceras y diagnóstico;
no es el instalador del producto. packaging/package-windows.bat copia solo runtime
y exige helper publicado, PawnIO intacto y avisos. Véanse docs/TESTING.md y
../packaging/README.md. Firma/licencia pendientes; no se instalan componentes.
