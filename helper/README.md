# Helper LHM de MACE — Subfases 1A y 1B

Helper Windows x64 autocontenido, con .NET 10.0.12 y LibreHardwareMonitorLib 0.9.6. Se compila con SDK 10.0.401, dependencias exactas y archivos `packages.lock.json`. `global.json` rechaza otros SDK para conservar la configuración verificada.

Desde PowerShell, en esta carpeta, ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File .\Build-Helper.ps1`. La restauración usa `--locked-mode`. Las pruebas automáticas se ejecutan antes de publicar. Cada publicación se crea en una carpeta nueva de `artifacts`. `artifacts/latest.txt` indica la última que pasó las comprobaciones.

`Build-Helper.ps1 -SigningScript C:\ruta\Firmar.ps1` admite un paso de firma. El script externo recibe `-FilePath` para el EXE y las DLL propias y debe lanzar un error al fallar. Se exige firma Authenticode válida si se usa ese paso. Sin ese parámetro aparece una advertencia y la compilación continúa. La verificación SHA-256 se ejecuta en ambos casos.

`Verify-Artifacts.ps1 -Directory <publicacion>` comprueba archivos, hashes, versión de protocolo y runtime incluido. El manifiesto de hashes detecta cambios frente a ese manifiesto; la autenticidad del publicador depende de la firma pendiente y de la distribución confiable del propio manifiesto. El empaquetado final y los avisos completos de terceros corresponden a la Fase 3.

La DLL crea la tubería y lanza `Mace.Lhm.Helper.exe --bootstrap-stdin`. El nombre aleatorio y el PID del receptor viajan en un bloque privado de 128 bytes por stdin heredado, con una lista explícita de handles. El nombre no figura en argumentos ni variables de entorno. El helper verifica el PID real del servidor antes de abrir LHM. Escribe WARMING_UP, abre los sensores y emite muestras a 1 Hz. Usa un plazo de conexión de 5 s y uno de escritura de 2 s. Al perder la conexión termina y libera sus recursos. Los argumentos `--pipe ... --parent-pid ...` se conservan para compatibilidad con el receptor de referencia de 1A; la DLL no los usa.

La DLL implementa el servidor en C con SID del usuario, SYSTEM, DACL protegida, primera instancia exclusiva y rechazo de clientes remotos. Comprueba `GetNamedPipeClientProcessId` frente al hijo creado, y el helper comprueba `GetNamedPipeServerProcessId`. `PrivatePipe.cs` sigue siendo el servidor de referencia de las pruebas 1A. El Job Object del transporte contiene exclusivamente el hijo de esta DLL; no se buscan ni terminan instancias de LHM ajenas. Otros procesos con control del mismo usuario, acceso a depuración o privilegios de administrador siguen dentro del límite de confianza de Windows.

Para la prueba física, desde PowerShell como administrador, ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File .\Smoke-Test.ps1`. El receptor de prueba inicia el artefacto publicado, comprueba PID y protocolo, lee cinco mensajes y cierra la tubería. El resultado y el tiempo de la primera muestra operativa se guardan en `artifacts/smoke-*.txt`.

El helper consulta el estado de PawnIO mediante Service Control Manager. LHM devuelve números crudos identificados como NUMERIC_REPORTED. El bit de elevación, el estado del driver y el estado del helper se transmiten por separado. El receptor nativo debe aplicar las reglas de disponibilidad, coherencia y antigüedad antes de marcar una métrica V2 como válida. CPU sin driver en ejecución se informa como NO_PROVIDER. El uso de CPU seguirá procediendo de GetSystemTimes en la DLL.

Las GPU se asocian a una identidad PCI extraída del DeviceId público de LHM, contrastada con Enum/PCI de Windows. Se admite solo una instancia instalada de esa tupla. Una tupla incompleta, duplicada o no encontrada produce identidad Unknown y métricas UNSUPPORTED. No se utilizan nombres ni índices para inferir identidad. La DLL debe confirmar a su vez que la tupla identifica una sola GPU en su inventario de sesión. Los dispositivos fantasma del registro pueden hacer esta política conservadora y dejar sensores N/D.

El contrato binario está en `PROTOCOL.md`. La política por métrica, las evidencias, los comandos y los pendientes están en `RESULTADOS-1B.md`. `RESULTADOS-1A.md` conserva la evidencia histórica.
