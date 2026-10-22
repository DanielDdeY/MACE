# Fase 3: procesos y terminación protegida

Estado: implementación en `native/` de la copia aislada; ABI 1.3, DLL 1.3.0. Los siete símbolos heredados y sus tres layouts congelados no cambian. La capa Java aún debe adoptar las consultas V2 para obtener identidad y rutas sin truncar.

## Comportamiento

- Enumeración y terminación aplican la misma política: excluyen PID 0–4, el propio proceso, nombres de la lista protegida, archivos bajo el directorio de Windows y procesos que Windows marque críticos mediante `IsProcessCritical`. La comparación de rutas no distingue mayúsculas/minúsculas, admite `/` y `\\?\`, y exige límites de directorio; `C:\WindowsBackup` no coincide con `C:\Windows`. Si la ruta o criticidad no se puede comprobar, la terminación se rechaza.
- El inventario abre cada proceso con `PROCESS_QUERY_LIMITED_INFORMATION`. La memoria se consulta por separado y cada métrica indica `VALID`, `ACCESS_DENIED` o `READ_FAILED`; un proceso identificable no necesita `PROCESS_VM_READ` para aparecer. Los procesos inaccesibles se omiten y V2 informa cuántos se omitieron por fallos de consulta. La lista negra y los procesos protegidos por política no se cuentan como fallos.
- La ruta se lee con un buffer interno ampliable. `ProcessData.path[520]` se conserva y puede truncarse en la API heredada. `CP_GetProcessImagePathV2` informa los caracteres UTF-16 necesarios **incluido NUL** y no escribe parcialmente si el buffer es corto.
- Cada identidad V2 es `PID + GetProcessTimes.creation_time` (FILETIME UTC, intervalos de 100 ns). `CP_TerminateProcessV2` compara esa identidad y vuelve a consultar ruta y criticidad usando el **mismo handle** con el que llama a `TerminateProcess`. Un PID reutilizado devuelve `MACE_E_PID_REUSED`; un objetivo propio o protegido devuelve `MACE_E_PROTECTED_PROCESS`. La función antigua conserva PID como único argumento y, por tanto, no puede proteger contra un cambio de identidad ocurrido desde una lista anterior.
- Los errores V2 usan `MaceStatus`; `out_win32_error` captura el código Windows asociado cuando una llamada del sistema falla. `MACE_E_BUFFER_TOO_SMALL` informa la cantidad necesaria en las consultas con capacidad variable. Las funciones de procesos no requieren inicializar telemetría y nunca terminan algo sin una llamada explícita del consumidor.

## Pruebas y límites

`process_security` comprueba nombres y rutas sintéticas, límites de directorio y protección del proceso propio. `process_api` verifica el inventario V2, tamaños, buffer centinela, identidad, ruta y que la API antigua/V2 no puedan terminar el proceso de prueba. La prueba de terminación se activa **solo** con `-EnableTerminationTest`; crea `process_helper.exe`, demuestra que una fecha de creación alterada no lo cierra y termina únicamente ese auxiliar con la identidad correcta. No se automatiza la terminación de aplicaciones del usuario.

```powershell
.\native\scripts\build-native.ps1 -Configuration Release -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK'
.\native\scripts\build-native.ps1 -Configuration Release -BuildDirectory '.\native\build\vs2026-process-tests' -AdlxSdkDirectory 'C:\Program Files\AMD\ADLX-2.0' -RyzenSdkDirectory 'C:\Program Files\AMD\RyzenMasterMonitoringSDK' -EnableTerminationTest
```

La primera ejecución hace seis pruebas sin terminación; la segunda añade la prueba controlada del auxiliar. Los permisos del launcher pueden permitir consultar más procesos, pero no anulan la protección de procesos críticos ni el acceso denegado por Windows. La fase 4 añadirá muestras temporales para fugas y estados detallados de ventanas; la fase 5 ampliará las pruebas de concurrencia y ciclo de vida.

Referencias de Windows: [derechos de acceso a procesos](https://learn.microsoft.com/en-us/windows/win32/procthread/process-security-and-access-rights), [IsProcessCritical](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-isprocesscritical), [GetProcessMemoryInfo](https://learn.microsoft.com/en-us/windows/win32/api/psapi/nf-psapi-getprocessmemoryinfo) y [TerminateProcess](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess).
