#ifndef MACE_SHARED_BUFFER_H
#define MACE_SHARED_BUFFER_H

#include <stdint.h>
#include <wchar.h>
void* SetupSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);

#endif
