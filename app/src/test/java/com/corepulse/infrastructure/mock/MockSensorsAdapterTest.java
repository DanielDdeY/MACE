package com.corepulse.infrastructure.mock;

import com.corepulse.domain.model.TelemetrySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSensorsAdapterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void generaValoresDentroDeRangosPlausibles() {
        MockSensorsAdapter adapter = new MockSensorsAdapter(MockSensorsAdapter.Profile.NORMAL, true, 42L, FIXED_CLOCK);

        for (int i = 0; i < 500; i++) {
            TelemetrySnapshot s = adapter.fetchCurrentTelemetry();
            assertTrue(s.cpuTemp() >= MockSensorsAdapter.CPU_TEMP_MIN && s.cpuTemp() <= MockSensorsAdapter.CPU_TEMP_MAX,
                    "cpuTemp fuera de rango: " + s.cpuTemp());
            assertTrue(s.cpuWatts() >= 5f && s.cpuWatts() <= MockSensorsAdapter.CPU_WATTS_MAX,
                    "cpuWatts fuera de rango: " + s.cpuWatts());
            assertTrue(s.gpuTemp() >= MockSensorsAdapter.GPU_TEMP_MIN && s.gpuTemp() <= MockSensorsAdapter.GPU_TEMP_MAX,
                    "gpuTemp fuera de rango: " + s.gpuTemp());
            assertTrue(s.gpuWatts() >= 5f && s.gpuWatts() <= MockSensorsAdapter.GPU_WATTS_MAX,
                    "gpuWatts fuera de rango: " + s.gpuWatts());
            assertTrue(s.gpuFanRpm() >= 0 && s.gpuFanRpm() <= MockSensorsAdapter.GPU_FAN_RPM_MAX,
                    "gpuFanRpm fuera de rango: " + s.gpuFanRpm());
            assertTrue(s.gpuAvailable());
            assertNotNull(s.timestamp());
        }
    }

    @Test
    void esDeterministaConLaMismaSemilla() {
        MockSensorsAdapter a = new MockSensorsAdapter(MockSensorsAdapter.Profile.HEAVY_LOAD, true, 7L, FIXED_CLOCK);
        MockSensorsAdapter b = new MockSensorsAdapter(MockSensorsAdapter.Profile.HEAVY_LOAD, true, 7L, FIXED_CLOCK);

        for (int i = 0; i < 50; i++) {
            assertEquals(a.fetchCurrentTelemetry(), b.fetchCurrentTelemetry());
        }
    }

    @Test
    void sinGpuDevuelveLosCamposDeGpuEnCero() {
        MockSensorsAdapter adapter = new MockSensorsAdapter(MockSensorsAdapter.Profile.NORMAL, false, 1L, FIXED_CLOCK);

        for (int i = 0; i < 20; i++) {
            TelemetrySnapshot s = adapter.fetchCurrentTelemetry();
            assertFalse(s.gpuAvailable());
            assertEquals(0f, s.gpuTemp());
            assertEquals(0f, s.gpuWatts());
            assertEquals(0, s.gpuFanRpm());
            assertTrue(s.cpuTemp() > 0f);
        }
        assertFalse(MockSensorsAdapter.withoutGpu().gpuAvailable());
    }

    @Test
    void elPerfilCriticalSuperaElUmbralCriticoDelDominio() {
        MockSensorsAdapter adapter = new MockSensorsAdapter(MockSensorsAdapter.Profile.CRITICAL, true, 3L, FIXED_CLOCK);

        boolean cpuCritical = false;
        boolean gpuCritical = false;
        for (int i = 0; i < 30; i++) {
            TelemetrySnapshot s = adapter.fetchCurrentTelemetry();
            cpuCritical |= s.cpuTemp() > 85f;
            gpuCritical |= s.gpuTemp() > 85f;
        }
        assertTrue(cpuCritical, "el perfil CRITICAL debe producir CPU > 85 °C");
        assertTrue(gpuCritical, "el perfil CRITICAL debe producir GPU > 85 °C");
    }

    @Test
    void elPerfilNormalRaraVezAlcanzaElUmbralDeAdvertencia() {
        MockSensorsAdapter adapter = new MockSensorsAdapter(MockSensorsAdapter.Profile.NORMAL, true, 11L, FIXED_CLOCK);

        int warnings = 0;
        int samples = 300;
        for (int i = 0; i < samples; i++) {
            if (adapter.fetchCurrentTelemetry().cpuTemp() > 75f) {
                warnings++;
            }
        }
        assertTrue(warnings < samples / 10, "demasiadas lecturas > 75 °C en perfil NORMAL: " + warnings);
    }

    @Test
    void usaElRelojInyectado() {
        MockSensorsAdapter adapter = new MockSensorsAdapter(MockSensorsAdapter.Profile.NORMAL, true, 5L, FIXED_CLOCK);

        assertEquals(FIXED_CLOCK.instant(), adapter.fetchCurrentTelemetry().timestamp());
        assertEquals(MockSensorsAdapter.Profile.NORMAL, adapter.profile());
    }
}
