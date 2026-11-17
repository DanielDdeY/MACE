#include "lhm_wire.h"
#include <math.h>
#include <string.h>

uint32_t LhmWire_U32(const unsigned char* b) { uint32_t v; memcpy(&v,b,4); return v; }
static uint64_t u64(const unsigned char* b) { uint64_t v; memcpy(&v,b,8); return v; }
static bool same_pci(const LhmDevice* a, const LhmDevice* b) {
    return a->vendor == b->vendor && a->device == b->device &&
        a->subsystem == b->subsystem && a->revision == b->revision;
}
bool LhmWire_Decode(const unsigned char* b, uint32_t size, uint32_t pid,
                    uint64_t previous, uint64_t now, LhmFrame* out) {
    if (size < LHM_HEADER_SIZE || size > LHM_MAX_FRAME ||
        (size-LHM_HEADER_SIZE)%LHM_DEVICE_SIZE || LhmWire_U32(b)!=0x314D484Cu ||
        LhmWire_U32(b+4)!=((LHM_HEADER_SIZE<<16)|LHM_PROTOCOL_VERSION) || LhmWire_U32(b+8)!=size) return false;
    LhmFrame f = {0};
    f.flags=LhmWire_U32(b+12); f.sequence=u64(b+16); f.timestamp=u64(b+24);
    f.pid=LhmWire_U32(b+32); f.driver=LhmWire_U32(b+36);
    f.helper=LhmWire_U32(b+40); f.count=LhmWire_U32(b+44);
    if ((f.flags&~1u) || f.sequence<=previous || f.pid!=pid ||
        f.timestamp>now || now-f.timestamp>LHM_MAX_AGE_MS ||
        f.driver<1 || f.driver>4 || f.helper<1 || f.helper>3 ||
        f.count>LHM_MAX_DEVICES || LHM_HEADER_SIZE+f.count*LHM_DEVICE_SIZE!=size) return false;
    unsigned cpus=0, gpus=0;
    for (uint32_t i=0;i<f.count;i++) {
        const unsigned char* d=b+LHM_HEADER_SIZE+i*LHM_DEVICE_SIZE;
        LhmDevice* dst=&f.devices[i];
        dst->kind=LhmWire_U32(d); dst->identity=LhmWire_U32(d+4);
        dst->vendor=LhmWire_U32(d+16); dst->device=LhmWire_U32(d+20);
        dst->subsystem=LhmWire_U32(d+24); dst->revision=LhmWire_U32(d+28);
        if (u64(d+8) || dst->kind<1 || dst->kind>4 || dst->identity>2) return false;
        if (dst->kind==1) {
            if (++cpus>1 || dst->identity!=1 || dst->vendor || dst->device || dst->subsystem || dst->revision) return false;
        } else {
            if (++gpus>16 || dst->identity==1) return false;
            uint32_t vendor=dst->kind==2 ? 0x10DEu : (dst->kind==3 ? 0x1002u : 0x8086u);
            if (dst->identity==2 && (dst->vendor!=vendor || dst->device>0xFFFF || dst->revision>0xFF)) return false;
            for (uint32_t j=0;j<i;j++)
                if (dst->identity==2 && f.devices[j].identity==2 && same_pci(dst,&f.devices[j])) return false;
        }
        for (unsigned m=0;m<7;m++) {
            LhmMetric* metric=&dst->metrics[m];
            memcpy(&metric->value,d+32+m*16,8);
            metric->state=LhmWire_U32(d+40+m*16); metric->meaning=LhmWire_U32(d+44+m*16);
            double maximum=m==0 ? 150 : m==1 ? 2000 : m==2 ? 100 : m==3 ? 100000 : m==4 ? 200 : 1125899906842624.0;
            if (!isfinite(metric->value) || metric->state<1 || metric->state>5 || metric->meaning>6 ||
                metric->value<(m==0 ? -50 : 0) || metric->value>maximum ||
                (m>=3 && floor(metric->value)!=metric->value)) return false;
            if (metric->state!=1 && (metric->value!=0 || metric->meaning!=0)) return false;
            if (metric->state==1) {
                bool meaning_ok=m==0 ? (dst->kind==1 ? metric->meaning==1 || metric->meaning==2 : metric->meaning==3) :
                    m==1 ? (dst->kind==1 ? metric->meaning==1 : metric->meaning>=4 && metric->meaning<=6) : metric->meaning==0;
                if (!meaning_ok || dst->identity==0) return false;
            }
        }
        if (dst->metrics[5].state==1 && dst->metrics[6].state==1 && dst->metrics[5].value>dst->metrics[6].value) return false;
    }
    *out=f;
    return true;
}

MaceMetricState LhmWire_State(const LhmFrame* f, const LhmMetric* m, bool cpu, uint64_t now) {
    if (f->timestamp>now || now-f->timestamp>LHM_MAX_AGE_MS) return MACE_METRIC_READ_FAILED;
    if (f->helper==1) return MACE_METRIC_WARMING_UP;
    if (f->helper!=2) return MACE_METRIC_READ_FAILED;
    if (cpu && !(f->flags&1)) return MACE_METRIC_NOT_ELEVATED;
    if (cpu && f->driver==1) return MACE_METRIC_PAWNIO_MISSING;
    if (cpu && f->driver==2) return MACE_METRIC_PAWNIO_STOPPED;
    if (cpu && f->driver!=3) return MACE_METRIC_NO_PROVIDER;
    if (m->state==2) return MACE_METRIC_WARMING_UP;
    if (m->state==3) return MACE_METRIC_NO_PROVIDER;
    if (m->state==4) return MACE_METRIC_UNSUPPORTED;
    if (m->state!=1) return MACE_METRIC_READ_FAILED;
    /* LHM1 cannot prove that a zero CPU register was read successfully. */
    if (cpu && m->value==0) return MACE_METRIC_READ_FAILED;
    return MACE_METRIC_VALID;
}
