package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.infrastructure.telemetry.BlackboxRing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manejo del token opaco y de las lecturas por secuencia, contra la semántica simulada. */
class NativeBlackboxRingTest {

    private final FakeNativeApi api = new FakeNativeApi();

    private static byte[] payload(int i) {
        return V2Records.encodeSample(new ProcessSample(ProcessIdentity.of(100 + i, 5), i,
                LongMetricReading.valid(i), LongMetricReading.valid(i)));
    }

    @Test
    void escribeYLeePorSecuenciaConsultandoPrimeroElTamano() {
        NativeBlackboxRing ring = NativeBlackboxRing.create(api, 8);
        for (int i = 1; i <= 3; i++) {
            assertEquals(i, ring.append(BlackboxRing.RECORD_PROCESS_SAMPLE, payload(i)));
        }

        List<BlackboxRing.RingRecord> records = ring.read(2);

        assertEquals(2, records.size());
        assertEquals(2, records.get(0).sequence());
        assertArrayEquals(payload(3), records.get(1).payload());
        assertTrue(ring.read(4).isEmpty());
    }

    @Test
    void elDesbordamientoSeVeEnLasSecuenciasYEstadisticas() {
        NativeBlackboxRing ring = NativeBlackboxRing.create(api, 2);
        for (int i = 1; i <= 5; i++) {
            ring.append(BlackboxRing.RECORD_PROCESS_SAMPLE, payload(i));
        }

        BlackboxRing.RingStats stats = ring.stats();
        List<BlackboxRing.RingRecord> fromOverwritten = ring.read(1);

        assertEquals(3, stats.overwrittenRecords());
        assertEquals(6, stats.nextSequence());
        assertEquals(4, stats.oldestSequence());
        assertEquals(4, fromOverwritten.get(0).sequence(), "una secuencia sobrescrita empieza en la más antigua");
    }

    @Test
    void elDobleCierreNoLlamaDosVecesALaDllYElTokenNoSeReutiliza() {
        NativeBlackboxRing ring = NativeBlackboxRing.create(api, 2);

        ring.close();
        ring.close();

        assertEquals(1, api.blackboxCloseCalls);
        assertThrows(IllegalStateException.class, () -> ring.append(BlackboxRing.RECORD_PROCESS_SAMPLE, payload(1)));
        assertThrows(IllegalStateException.class, () -> ring.read(0));
        NativeBlackboxRing other = NativeBlackboxRing.create(api, 2);
        assertTrue(other.handle() != ring.handle());
    }

    @Test
    void unaCapacidadInvalidaSeInformaComoErrorNativo() {
        NativeLibraryException error = assertThrows(NativeLibraryException.class, () -> NativeBlackboxRing.create(api, 0));
        assertTrue(error.getMessage().contains("MACE_E_INVALID_ARGUMENT"));
    }
}
