package com.mace.presentation.viewmodel;

import com.mace.domain.model.*;
import com.mace.infrastructure.mock.*;
import com.mace.infrastructure.nativebridge.*;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class SensorReasonTest {
    @ParameterizedTest @EnumSource(value=MetricState.class, names={"NOT_ELEVATED","PAWNIO_MISSING","PAWNIO_STOPPED",
            "REBOOT_REQUIRED","HELPER_UNAVAILABLE","HELPER_RETRYING","HELPER_UNTRUSTED","HELPER_CONTAINMENT_FAILED"})
    void newCodesRoundTripWithoutValueOrSensitiveDetails(MetricState state) {
        int code=NativeCodes.metricStateCode(state);
        int expected=switch (state) {
            case NOT_ELEVATED -> 9; case PAWNIO_MISSING -> 10; case PAWNIO_STOPPED -> 11; case REBOOT_REQUIRED -> 12;
            case HELPER_UNAVAILABLE -> 13; case HELPER_RETRYING -> 14; case HELPER_UNTRUSTED -> 15;
            case HELPER_CONTAINMENT_FAILED -> 16; default -> throw new AssertionError(state);
        };
        assertEquals(expected,code); assertSame(state,NativeCodes.metricState(code)); assertFalse(state.isValid());
        try (Arena a=Arena.ofConfined()) {
            var out=a.allocate(16);
            V2Records.writeF32(out,0,new MetricReading(99,state));
            assertEquals(0,out.get(ValueLayout.JAVA_FLOAT,0));
            assertFalse(V2Records.readF32(out,0).isValid());
            V2Records.writeU32(out,0,LongMetricReading.unavailable(state));
            assertEquals(0,out.get(ValueLayout.JAVA_INT,0));
            assertEquals(state,V2Records.readU32(out,0).state());
            V2Records.writeU64(out,0,LongMetricReading.unavailable(state));
            assertEquals(0,out.get(ValueLayout.JAVA_LONG,0));
            assertEquals(0,out.get(ValueLayout.JAVA_INT,12));
        }
        var display=MetricDisplay.decimal(MetricReading.unavailable(state),"°C",1);
        assertTrue(display.text().startsWith("N/D")); assertTrue(display.value().isEmpty());
        assertFalse(display.message().isBlank());
        for (String forbidden:new String[]{"C:\\","\\Users\\","SHA","PID","hash","manifest","DACL"})
            assertFalse(display.message().contains(forbidden));
    }
    @Test void unknownNativeCodeIsGenericNdAndLegitimateZeroStaysValid() {
        try (Arena a=Arena.ofConfined()) {
            var data=a.allocate(8); data.set(ValueLayout.JAVA_FLOAT,0,55);
            data.set(ValueLayout.JAVA_INT,4,999);
            var unknown=V2Records.readF32(data,0);
            assertEquals(MetricState.READ_FAILED,unknown.state());
            assertTrue(MetricDisplay.decimal(unknown,"W",1).text().startsWith("N/D"));
            assertEquals("La lectura no está disponible.",MetricDisplay.decimal(unknown,"W",1).message());
            data.set(ValueLayout.JAVA_FLOAT,0,0); data.set(ValueLayout.JAVA_INT,4,0);
            assertTrue(V2Records.readF32(data,0).isValid());
            assertEquals("0 RPM",MetricDisplay.whole(LongMetricReading.valid(0),"RPM").text());
        }
    }
    @Test void rebootAndRetryMessagesAreExactAndStoppedIsNotReboot() {
        assertEquals("Para habilitar los sensores de CPU, reinicia Windows. Las demás métricas disponibles seguirán funcionando",
                MetricMessages.message(MetricState.REBOOT_REQUIRED));
        assertEquals("El componente de sensores no responde. MACE seguirá intentándolo.",MetricMessages.message(MetricState.HELPER_RETRYING));
        assertFalse(MetricMessages.message(MetricState.PAWNIO_STOPPED).contains("reinicia"));
        assertTrue(MetricMessages.message(MetricState.NOT_ELEVATED).contains("administrador"));
        assertEquals("El componente de sensores no pudo verificarse. Repara la instalación de MACE.",
                MetricMessages.message(MetricState.HELPER_UNTRUSTED));
    }
    @Test void abi14And15AcceptedWithoutChangingProviderIds() {
        MetricState[] original={MetricState.VALID,MetricState.NO_DEVICE,MetricState.NO_PROVIDER,MetricState.UNSUPPORTED,
                MetricState.ACCESS_DENIED,MetricState.WARMING_UP,MetricState.READ_FAILED,MetricState.DEVICE_INACTIVE,MetricState.NOT_INITIALIZED};
        for (int i=0;i<original.length;i++) { assertEquals(original[i],NativeCodes.metricState(i)); assertEquals(i,NativeCodes.metricStateCode(original[i])); }
        for (int minor:new int[]{4,5}) {
            var abi=new AbiInfo(64,2,1,minor,1584,28,16,2,8,0,AbiRequirements.REQUIRED_CAPABILITIES,1,minor,0,0);
            assertTrue(AbiRequirements.problems(abi).isEmpty());
        }
        assertEquals(8,NativeCodes.providerCode(SensorProvider.LHM));
    }
    @Test void radeonShortRecoveryIsHeldAndLongRecoveryBecomesNdWithoutChangingRawData() {
        for (var scenario:new MockScenario[]{MockScenario.LHM_RECOVERY_SHORT,MockScenario.LHM_RECOVERY_LONG}) {
            AtomicLong now=new AtomicLong();
            var presentation=new TelemetryPresentation(now::get);
            var sensors=new MockSensorsAdapter(scenario,42,Clock.systemUTC(),now::get);
            for (int n=1;n<=6;n++) {
                now.set(n*1_000_000_000L); var raw=sensors.fetchHardwareSnapshot(); var shown=presentation.apply(raw);
                if (n==3) {
                    assertEquals(SensorProvider.LHM,raw.gpus().getFirst().provider());
                    assertEquals(MetricState.WARMING_UP,raw.gpus().getFirst().temperatureCelsius().state());
                    assertTrue(shown.gpus().getFirst().temperatureCelsius().isValid());
                }
                if (n==4) assertEquals(scenario==MockScenario.LHM_RECOVERY_SHORT,
                        shown.gpus().getFirst().temperatureCelsius().isValid());
                if (n==6) assertTrue(shown.gpus().getFirst().temperatureCelsius().isValid());
            }
        }
    }
}
