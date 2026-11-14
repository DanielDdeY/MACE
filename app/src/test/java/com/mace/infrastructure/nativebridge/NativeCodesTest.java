package com.mace.infrastructure.nativebridge;
import com.mace.domain.model.SensorProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeCodesTest {
    @Test void lhmEsOchoYLosProveedoresAnterioresConservanSuCodigo() {
        SensorProvider[] expected={SensorProvider.NONE,SensorProvider.WINDOWS,SensorProvider.INTEL_MSR,
            SensorProvider.AMD_RYZEN_SDK,SensorProvider.AMD_UPROF,SensorProvider.NVIDIA_NVML,
            SensorProvider.AMD_ADLX,SensorProvider.INTEL_IGCL,SensorProvider.LHM};
        for (int i=0;i<expected.length;i++) {
            assertEquals(expected[i],NativeCodes.provider(i)); assertEquals(i,NativeCodes.providerCode(expected[i]));
        }
        assertEquals(SensorProvider.NONE,NativeCodes.provider(99));
        assertEquals(SensorProvider.NONE,NativeCodes.provider(-1));
    }
}
