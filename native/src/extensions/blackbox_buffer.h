#ifndef MACE_BLACKBOX_BUFFER_H
#define MACE_BLACKBOX_BUFFER_H

/* Called by CP_ShutdownTelemetry after stopping ordinary telemetry polling. */
void Blackbox_CloseAll(void);
void SharedBuffer_CloseAll(void);

#endif
