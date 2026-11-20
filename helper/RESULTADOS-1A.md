# Entrega Subfase 1A — 14 de noviembre de 2026

Implementación confinada a `helper/`. Se leyeron el prompt de Fase 1, RESULTADOS.md de Fase 0.5 y Respuesta.md antes de comenzar. La entrega implementa el helper, el protocolo y un receptor de referencia de prueba. La creación de la tubería por la DLL, la caché, los puertos Java y el ciclo de vida con Job Object corresponden a las subfases siguientes.

| Archivos | Responsabilidad |
|---|---|
| global.json, Directory.Build.props | SDK 10.0.401, runtime 10.0.12, Windows x64 y compilación determinista |
| tres .csproj y tres packages.lock.json | Dependencias exactas, con LHM 0.9.6 |
| Mace.Lhm.Helper/Program.cs | CLI restringida, cliente local, autenticación del servidor, WARMING_UP y emisión a 1 Hz |
| SensorSampler.cs | CPU/GPU y métricas necesarias, nombres seleccionados explícitamente y scopes conservadores |
| PciAssociation.cs | Extracción y comprobación de identidad PCI inequívoca |
| PawnIoStatus.cs | Estado de PawnIO mediante Service Control Manager |
| Mace.Lhm.Protocol/Snapshot.cs, WireProtocol.cs | Mensajes binarios de tamaño acotado, estados, rangos y validación |
| Mace.Lhm.Protocol/PrivatePipe.cs | Servidor de referencia con ACL y banderas Win32, verificación de PID en ambos extremos |
| Mace.Lhm.Tests/Program.cs | Pruebas de protocolo, seguridad y receptor de prueba para hardware real |
| Build-Helper.ps1, Verify-Artifacts.ps1, Smoke-Test.ps1 | Compilación, prueba, publicación, firma opcional, hashes y prueba elevada |
| .gitignore, README.md, PROTOCOL.md, este archivo | Exclusión de binarios y contrato de integración |

La compilación final terminó con cero advertencias y cero errores. El proceso de publicación ejecutó 47 comprobaciones automáticas. La prueba física elevada completó 50 incluyendo comunicación real con el helper publicado, estado inicial WARMING_UP y cierre limpio al desconectar el receptor.

Se probaron tamaño y versión inválidos, flags y enums desconocidos, PID incorrecto, replay, muestra caducada y futura, números no finitos, rangos, GPU ambigua, PCI repetido, VRAM incoherente, prefijo de tamaño excesivo antes de reservar memoria, EOF truncado y entrega fragmentada byte a byte. Se comprobaron también la ACL real con solo usuario y SYSTEM, primera instancia exclusiva y comparación de los PID obtenidos mediante Windows.

En la prueba física, el helper informó token elevado y PawnIO Running. La Radeon RX 5500 XT se asoció a PCI vendor 0x1002, device 0x7340, subsystem 0x38221462 y revisión 0xC5. La última muestra de esa ejecución contenía CPU 66,125 °C y 30,661 W, GPU 34 °C y 4 W, ventilador 1.287 RPM y control 40 %. Son lecturas crudas de LHM. La CPU también se probó sin elevar, donde devolvió cero, con el bit Elevated desactivado. Esto refuerza la necesidad de la política de validez de 1B.

La primera muestra Ready en la ejecución elevada llegó a los 779,69 ms desde antes de iniciar el proceso. Es una medición local con cachés potencialmente calientes. El log está en `artifacts/smoke-20261114-*.txt` y su fecha usa el reloj del equipo de ejecución. El servicio de PawnIO ya estaba instalado.
Dos publicaciones, separadas por una recompilación completa, produjeron los mismos SHA-256 para los 205 archivos publicados. El resultado está en `artifacts/reproducibility.json`. El tamaño publicado, incluido su manifiesto, fue de 85.657.433 bytes, 81,69 MiB. Es evidencia local de reproducibilidad con el mismo SDK, runtime y lockfiles; la reproducción en otro agente limpio permanece NO VERIFICADA.

La firma se omitió con aviso por falta de certificado, como pide el prompt. Build-Helper acepta un paso de firma y Verify-Artifacts valida hashes. Un manifiesto sin firma no autentica por sí solo al autor. La prueba con otro usuario de Windows y una conexión SMB remota permanece NO VERIFICADA; la ACL real fue inspeccionada y el servidor solicita explícitamente PIPE_REJECT_REMOTE_CLIENTS. Sigue existiendo el límite de confianza frente a procesos del mismo usuario, administradores o depuradores.

La lectura de estado del servicio no prueba el éxito individual de cada acceso interno de LHM. Las cifras se transmiten como NUMERIC_REPORTED, incluyendo ceros. La DLL debe aplicar disponibilidad, elevación, antigüedad y coherencia antes de marcar VALID. La potencia GPU se conserva con ámbito UnknownPower. El soporte físico Intel/NVIDIA, los sensores no expuestos, la suspensión, el cambio de GPU en caliente y la cobertura de modelos distintos permanecen NO VERIFICADOS. La asociación PCI puede rechazar dispositivos por entradas fantasma del registro, conservando N/D.

Comandos de entrega, desde `helper/`, en PowerShell.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Build-Helper.ps1
```

Prueba física desde PowerShell como administrador.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Smoke-Test.ps1
```

La Subfase 1A se detiene en esta entrega a la espera de aprobación, tal como exige prompt_mace_fase1.md.
