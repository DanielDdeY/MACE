#include "../src/telemetry/lhm_retry.h"
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr,"retry line %d: %s\n",__LINE__,#x); return 1; } } while(0)
int main(int argc,char** argv) {
    CHECK(argc==2); LhmRetry r; LhmRetry_Init(&r);
    CHECK(LhmRetry_Start(&r,0));
    if (!strcmp(argv[1],"streak")) {
        LhmRetry_Failed(&r,0,false); CHECK(r.backoff_ms==2000);
        CHECK(LhmRetry_Start(&r,1000));
        // Arbitrarily high sequence numbers cannot affect this policy.
        for (unsigned i=0;i<20;i++) LhmRetry_Sample(&r,false);
        CHECK(r.backoff_ms==2000 && r.consecutive==0);
        LhmRetry_Sample(&r,true); LhmRetry_Sample(&r,true); CHECK(r.backoff_ms==2000);
        LhmRetry_Sample(&r,false); CHECK(r.consecutive==0);
        for (unsigned i=0;i<3;i++) LhmRetry_Sample(&r,true);
        CHECK(r.backoff_ms==1000 && r.count==1);
    } else if (!strcmp(argv[1],"permanent")) {
        LhmRetry_Failed(&r,1,true); CHECK(r.blocked);
        CHECK(!LhmRetry_Start(&r,UINT64_C(9999999)));
    } else {
        uint64_t now=0;
        for (unsigned i=0;i<3;i++) {
            LhmRetry_Failed(&r,now,false); CHECK(r.next_ms==now+(UINT64_C(1000)<<i));
            now=r.next_ms; CHECK(LhmRetry_Start(&r,now));
        }
        CHECK(r.attempts==4 && r.count==3);
        LhmRetry_Failed(&r,now,false); CHECK(r.slow && r.next_ms==61000);
        CHECK(!LhmRetry_Start(&r,60000));
        if (!strcmp(argv[1],"suspend")) {
            // The injected clock is unbiased/awake time. Advancing wall time has no effect.
            uint64_t wall=now+UINT64_C(86400000); CHECK(wall>now);
            CHECK(!LhmRetry_Start(&r,now) && r.count==3 && r.attempts==4);
        } else {
            now=r.next_ms; CHECK(LhmRetry_Start(&r,now)); CHECK(r.attempts==5);
            if (!strcmp(argv[1],"recovery")) {
                LhmRetry_Sample(&r,true); LhmRetry_Sample(&r,true);
                CHECK(r.slow); LhmRetry_Sample(&r,true); CHECK(!r.slow && r.backoff_ms==1000);
                // Healthy samples do not erase restart timestamps.
                CHECK(r.count>0);
            } else if (!strcmp(argv[1],"slow-cap")) {
                for (unsigned i=0;i<12;i++) {
                    LhmRetry_Failed(&r,now,false);
                    CHECK(r.next_ms>now && r.next_ms-now<=300000);
                    now=r.next_ms; CHECK(LhmRetry_Start(&r,now));
                }
                CHECK(r.slow_ms==300000);
            }
        }
    }
    printf("retry policy %s: expected budget/state transitions: OK\n",argv[1]); return 0;
}
