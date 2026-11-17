#include "../src/telemetry/lhm_wire.h"
#include <stdio.h>
#include <string.h>
#include <math.h>
#define CHECK(x) do { checks++; if (!(x)) { fprintf(stderr,"line %d: %s\n",__LINE__,#x); return 1; } } while(0)
static void put32(unsigned char* b,uint32_t v) { memcpy(b,&v,4); }
static void put64(unsigned char* b,uint64_t v) { memcpy(b,&v,8); }
static bool decode(const unsigned char* b,uint32_t size,LhmFrame* f) { return LhmWire_Decode(b,size,123,0,10000,f); }
int main(void) {
    unsigned checks=0;
    unsigned char valid[192]={0},bad[LHM_MAX_FRAME]={0}; LhmFrame f;
    put32(valid,0x314D484C); put32(valid+4,(LHM_HEADER_SIZE<<16)|LHM_PROTOCOL_VERSION); put32(valid+8,192);
    put32(valid+12,1); put64(valid+16,1); put64(valid+24,10000); put32(valid+32,123);
    put32(valid+36,3); put32(valid+40,2); put32(valid+44,1);
    put32(valid+48,1); put32(valid+52,1);
    for (unsigned i=0;i<7;i++) put32(valid+88+i*16,4);
    CHECK(decode(valid,192,&f));
    memcpy(bad,valid,192); put32(bad+4,(LHM_HEADER_SIZE<<16)|1u); CHECK(!decode(bad,192,&f));
    memcpy(bad,valid,192); put32(bad+4,(LHM_HEADER_SIZE<<16)|3u); CHECK(!decode(bad,192,&f));
    CHECK(!decode(valid,47,&f)); CHECK(!decode(bad,LHM_MAX_FRAME+1,&f));
    CHECK(!decode(valid,191,&f));
    const uint32_t offsets[]={0,4,8,12,32,36,40,44,48,52,56,88,92};
    const uint32_t values[]={0,2,4096,2,124,0,99,18,99,99,1,99,99};
    for (unsigned i=0;i<sizeof(offsets)/sizeof(offsets[0]);i++) {
        memcpy(bad,valid,192); put32(bad+offsets[i],values[i]); CHECK(!decode(bad,192,&f));
    }
    CHECK(!LhmWire_Decode(valid,192,123,1,10000,&f)); /* replay */
    CHECK(!LhmWire_Decode(valid,192,123,0,9999,&f)); /* future */
    CHECK(LhmWire_Decode(valid,192,123,0,13000,&f)); /* exact age boundary */
    CHECK(!LhmWire_Decode(valid,192,123,0,13001,&f));
    memcpy(bad,valid,192); put32(bad+88,1); put32(bad+92,1);
    const double invalids[]={NAN,INFINITY,-51,151};
    for (unsigned i=0;i<4;i++) { memcpy(bad+80,&invalids[i],8); CHECK(!decode(bad,192,&f)); }
    double temp=55; memcpy(bad+80,&temp,8); CHECK(decode(bad,192,&f));
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_VALID);
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,13001)==MACE_METRIC_READ_FAILED);
    f.flags=0;
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_NO_PROVIDER);
    f.flags=1; f.driver=1;
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_NO_PROVIDER);
    f.driver=3; f.helper=1;
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_WARMING_UP);
    f.helper=3;
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_READ_FAILED);
    f.helper=2; f.devices[0].metrics[0].value=0;
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],true,10000)==MACE_METRIC_READ_FAILED);
    CHECK(LhmWire_State(&f,&f.devices[0].metrics[0],false,10000)==MACE_METRIC_VALID);
    printf("%u native wire/promotion checks: OK\n",checks);
    return 0;
}
