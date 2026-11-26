# Consulta sobre redistribución PawnIO — BORRADOR, NO ENVIADO

Fecha: **2026-11-25**. El usuario decide si lo publica; no se abrió incidencia,
envió correo ni adjuntó binario. Texto sin datos de máquina o contacto privado.
No supone incumplimiento ni atribuye condiciones no declaradas al autor.

---

Hello, I maintain MACE, an MIT-licensed Windows hardware-monitoring project.
We are reviewing redistribution requirements before publishing a new binary
installer. We plan to bundle the official PawnIO installer **2.2.0.0** unchanged,
preserving its upstream signature and not signing it again ourselves.

Could you point us to:

1. The license or redistribution terms covering the **complete 2.2.0 installer**
   and its included components, and whether distributing that original EXE
   unchanged is permitted under those terms?
2. The corresponding source revision, build scripts and notices for that exact
   installer/driver release, and any source delivery or offer obligations for
   a downstream distributor?
3. The corresponding sources and terms for the twelve PawnIO module resources
   distributed in LibreHardwareMonitorLib 0.9.6? Its pinned README refers to
   Modules 0.1.6, but the source tree of that tag does not include
   `IsaBridgeEC.p`, while the LHM resources include `IsaBridgeEC.bin`.
   What source revision/build should be used for that module and the set?
4. Any additional notices or conditions we should preserve when bundling the
   installer alongside an independent application using LHM, particularly the
   distinction between the IOCTL exception and modules using the Pawn interface?

We are asking for existing license/source information, not a signing service
or a license exception. Thank you for clarifying the appropriate references.

---

Alcance exacto de los doce recursos y fuentes consultadas:
[informe Etapa 2](FASE3-ETAPA2-EVIDENCIA.md).
Una respuesta del autor podría aportar evidencia; no sustituye la revisión
cualificada del conjunto ni la decisión de admisión de SignPath.
