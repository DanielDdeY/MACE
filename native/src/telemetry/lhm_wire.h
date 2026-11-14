#ifndef MACE_LHM_WIRE_H
#define MACE_LHM_WIRE_H
#include <stdint.h>
#include <stdbool.h>
#include "mace_types_v2.h"
#define LHM_HEADER_SIZE 48u
#define LHM_DEVICE_SIZE 144u
#define LHM_MAX_DEVICES 17u
#define LHM_MAX_FRAME 2496u
#define LHM_MAX_AGE_MS 3000u
typedef struct { double value; uint32_t state, meaning; } LhmMetric;
typedef struct {
    uint32_t kind, identity, vendor, device, subsystem, revision;
    LhmMetric metrics[7];
} LhmDevice;
typedef struct {
    uint64_t sequence, timestamp;
    uint32_t flags, pid, driver, helper, count;
    LhmDevice devices[LHM_MAX_DEVICES];
} LhmFrame;
uint32_t LhmWire_U32(const unsigned char* bytes);
bool LhmWire_Decode(const unsigned char* bytes, uint32_t size, uint32_t pid,
                    uint64_t previous, uint64_t now, LhmFrame* frame);
MaceMetricState LhmWire_State(const LhmFrame* frame, const LhmMetric* metric,
                              bool cpu, uint64_t now);
#endif
