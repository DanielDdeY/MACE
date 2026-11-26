# Consulta a LibreHardwareMonitor — BORRADOR, NO ENVIADO

Fecha: **2026-11-25**. Preparado para el usuario, quien decide si lo publica.
No se abrió incidencia, envió correo, contactó a mantenedores ni adjuntó binario.
No supone infracción ni solicita asesoría legal o certificación de MACE.

---

Hello, I maintain MACE, an MIT-licensed Windows monitoring application using
the restored **LibreHardwareMonitorLib 0.9.6** NuGet package. We are documenting
the sources and licenses of the components we would redistribute before
publishing a new binary installer.

The package metadata points to source revision `3d331e3`. In that revision,
the README beside the PawnIO module resources refers to **PawnIO.Modules
0.1.6**, and COPYING contains LGPL 2.1. We found twelve embedded module names:

```text
AMDFamily0F.bin   AMDFamily10.bin   AMDFamily17.bin   IntelMSR.bin
IsaBridgeEC.bin   LpcACPIEC.bin     LpcCrOSEC.bin     LpcIO.bin
RyzenSMU.bin      SmbusI801.bin     SmbusNCT6793.bin  SmbusPIIX4.bin
```

Could you help us identify:

1. The corresponding source revision/release for each of these twelve modules,
   including any differences from the common release named in the README?
2. The source for `IsaBridgeEC.bin` in particular? The source tree of
   PawnIO.Modules 0.1.6 does not contain `IsaBridgeEC.p`. We understand the
   README may describe only part of the set or may need a different reference;
   we are not inferring a licensing violation from this observation.
3. The license notices applicable to each module and any accompanying source,
   build scripts or other materials intended for downstream redistribution?
   Does the adjacent COPYING cover the full set, with any per-file exceptions?
4. The documented procedure, if any, to match the module resources in the
   published 0.9.6 assembly to the sources/releases, including the build and
   module-signing requirements? We do not propose modifying or signing third
   party modules ourselves.

We would like to preserve the upstream notices and make the appropriate
corresponding sources available. Links to existing documentation or releases
would be very helpful. Thank you.

---

Referencias públicas ya consultadas en Etapa 2 (25/11/2026), sin nueva descarga
de binarios en esta entrega:

- [README fijado de LHM](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Resources/PawnIo/README).
- [COPYING fijado de LHM](https://github.com/LibreHardwareMonitor/LibreHardwareMonitor/blob/3d331e3/LibreHardwareMonitorLib/Resources/PawnIo/COPYING).
- [Fuentes Modules 0.1.6](https://github.com/namazso/PawnIO.Modules/tree/0.1.6).

Complementa la [consulta sobre el setup/driver](FASE3-CONSULTA-PAWNIO-BORRADOR.md).
Las respuestas pueden aportar evidencia; la revisión cualificada y admisión
SignPath siguen siendo decisiones separadas y pendientes.
