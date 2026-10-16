# Temperatura de CPU AMD: Ryzen Master Monitoring SDK

El proveedor se compila solo si se proporciona el SDK oficial de AMD fuera del repositorio. La DLL de MACE detecta AMD mediante CPUID, busca la instalación del Monitoring SDK en `HKLM\SOFTWARE\AMD\RyzenMasterMonitoringSDK\InstallationPath` y carga `bin\Platform.dll` por ruta absoluta. No se enlaza estáticamente ni se empaquetan encabezados, DLL o controlador de AMD.

El adaptador se verificó con el SDK **3.1.1.5478** y exige esa versión al compilar y al cargarla. La interfaz C++ del fabricante podría cambiar entre versiones; una actualización del SDK requiere revisar los encabezados, adaptar MACE y repetir la prueba física antes de permitirla.

```powershell
.\native\scripts\build-native.ps1 -Configuration Release -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
```

Para comprobar el sensor, abrir una consola **como administrador** y ejecutar `native\build\vs2026-x64\Release\test_mace.exe` (o `vs2022-x64`, según Visual Studio). La aplicación final usa el token elevado de su manifiesto. No ejecutar simultáneamente Ryzen Master u otro sondeador del SMU durante la comparación. AMD indica que `GetCPUParameters` no debe llamarse más de una vez por segundo; MACE reutiliza la última lectura si recibe llamadas más frecuentes.

En V2, una temperatura válida aparece con `provider_id=3` (`AMD_RYZEN_SDK`) y `temperature_c.state=VALID`. Si el SDK se instaló pero el proceso no está elevado, aparece `ACCESS_DENIED`. Si faltan el SDK o sus DLL, aparece `NO_PROVIDER`; un modelo no soportado o una lectura fallida no se convierten en 0 °C válido. El uso de CPU sigue siendo de Windows. Este cambio **no** añade potencia AMD: `power_w` sigue `NO_PROVIDER` hasta acordar si se mostrará potencia del paquete/PPT o del rail CPU, especialmente para APUs.

La versión 3.1.1 del SDK incluye soporte declarado para Ryzen y Threadripper y la documentación instalada enumera familias 17h, 19h y 1Ah. La presencia de una familia no garantiza que todo portátil/APU entregue temperatura; se debe probar el modelo concreto. En el Ryzen 5 3600 local, las pruebas automáticas pasaron; sin elevación apareció `ACCESS_DENIED` y, con elevación, `test_mace.exe` leyó 60,5 °C y 56,5 °C en dos ejecuciones del 15-10-2026. Son muestras reales de este equipo, no temperaturas fijas ni una certificación de otros modelos.

El SDK instalado incluye una licencia de evaluación. Antes de distribuir una aplicación junto con binarios AMD, revisar sus términos y exigir a cada destinatario la licencia correspondiente. MACE no incluye esos binarios. Si el SDK no funciona en una APU concreta, evaluar la Power Profiler API de AMD uProf 5.3; como alternativa más amplia, LibreHardwareMonitorLib requiere un puente .NET y deja de ser un proveedor nativo de AMD. No usar temperaturas ACPI/WMI genéricas como si fueran del CPU.

Fuentes: [AMD Ryzen Master Monitoring SDK](https://www.amd.com/en/developer/ryzen-master-monitoring-sdk.html), [AMD uProf Power Profiler API](https://docs.amd.com/r/en-US/57368-uProf-user-guide/12.4.2.-AMDPowerProfileAPI-Library?contentId=_b16YwYI4vYCojehBnPIYQ), [LibreHardwareMonitor](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor).
