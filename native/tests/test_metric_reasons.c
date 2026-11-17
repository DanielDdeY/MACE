#include "../src/telemetry/lhm_transport.h"
#include "../src/telemetry/lhm_wire.h"
#include <stdio.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"reason line %d: %s\n",__LINE__,#x); return 1; } } while(0)
int main(void) {
    CHECK(MACE_METRIC_VALID==0 && MACE_METRIC_NOT_INITIALIZED==8);
    CHECK(MACE_METRIC_NOT_ELEVATED==9 && MACE_METRIC_PAWNIO_MISSING==10 && MACE_METRIC_PAWNIO_STOPPED==11);
    CHECK(MACE_METRIC_REBOOT_REQUIRED==12 && MACE_METRIC_HELPER_UNAVAILABLE==13 && MACE_METRIC_HELPER_RETRYING==14);
    CHECK(MACE_METRIC_HELPER_UNTRUSTED==15 && MACE_METRIC_HELPER_CONTAINMENT_FAILED==16);
    CHECK(sizeof(MaceCpuTelemetryV2)==56 && sizeof(MaceGpuTelemetryV2)==104 && sizeof(MaceGpuAdapterInfoV2)==296);
    CHECK(LhmTransport_FailureState(LHM_OPEN_TRUST)==MACE_METRIC_HELPER_UNTRUSTED);
    CHECK(LhmTransport_FailureState(LHM_OPEN_CONTAINMENT)==MACE_METRIC_HELPER_CONTAINMENT_FAILED);
    CHECK(LhmTransport_FailureState(LHM_OPEN_LAUNCH_BLOCKED)==MACE_METRIC_HELPER_UNAVAILABLE);
    CHECK(LhmTransport_FailureState(LHM_OPEN_TRANSIENT)==MACE_METRIC_READ_FAILED);
    LhmFrame f={0}; f.timestamp=100; f.helper=2; f.driver=3;
    LhmMetric m={55,1,1};
    CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_NOT_ELEVATED);
    CHECK(LhmWire_State(&f,&m,false,100)==MACE_METRIC_VALID);
    f.flags=1; f.driver=1; CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_PAWNIO_MISSING);
    f.driver=2; CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_PAWNIO_STOPPED);
    f.driver=4; CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_NO_PROVIDER);
    f.driver=3; f.helper=1; CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_WARMING_UP);
    f.helper=2; m.value=0;
    CHECK(LhmWire_State(&f,&m,true,100)==MACE_METRIC_READ_FAILED);
    CHECK(LhmWire_State(&f,&m,false,100)==MACE_METRIC_VALID);
    puts("ABI 1.5 additive reasons, private failure mapping and unchanged validity: OK"); return 0;
}
