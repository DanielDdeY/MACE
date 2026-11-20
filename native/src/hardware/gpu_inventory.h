#ifndef MACE_GPU_INVENTORY_H
#define MACE_GPU_INVENTORY_H

#include <stdbool.h>
#include "mace_types_v2.h"

MaceStatus GpuInventory_Enumerate(MaceGpuAdapterInfoV2* out_adapters,
                                  uint32_t capacity, uint32_t* out_count);
MaceStatus GpuInventory_Find(const MaceAdapterLuidV2* luid,
                             MaceGpuAdapterInfoV2* out_adapter);
bool GpuInventory_GetPciAddress(const MaceAdapterLuidV2* luid,
                                uint32_t* bus, uint32_t* device,
                                uint32_t* function);

#endif
