package com.mace.infrastructure.telemetry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El anillo simulado reproduce la semántica de {@code blackbox_buffer.c}. */
class InMemoryBlackboxRingTest {

    @Test
    void lasSecuenciasEmpiezanEnUnoYSeConservanAlLeer() {
        InMemoryBlackboxRing ring = new InMemoryBlackboxRing(4, () -> 9L);
        assertEquals(1, ring.append(BlackboxRing.RECORD_CPU, new byte[]{1}));
        assertEquals(2, ring.append(BlackboxRing.RECORD_GPU, new byte[]{2}));

        List<BlackboxRing.RingRecord> records = ring.read(0);

        assertEquals(2, records.size());
        assertEquals(BlackboxRing.RECORD_GPU, records.get(1).recordType());
        assertEquals(9L, records.get(1).monotonicNanos());
        assertTrue(ring.read(3).isEmpty());
    }

    @Test
    void alDesbordarSobrescribeElMasAntiguoYLoCuenta() {
        InMemoryBlackboxRing ring = new InMemoryBlackboxRing(3);
        for (int i = 1; i <= 7; i++) {
            ring.append(BlackboxRing.RECORD_CPU, new byte[]{(byte) i});
        }

        BlackboxRing.RingStats stats = ring.stats();
        List<BlackboxRing.RingRecord> records = ring.read(2);

        assertEquals(4, stats.overwrittenRecords());
        assertEquals(5, stats.oldestSequence());
        assertEquals(List.of(5L, 6L, 7L), records.stream().map(BlackboxRing.RingRecord::sequence).toList());
        assertArrayEquals(new byte[]{7}, records.get(2).payload());
    }

    @Test
    void lasLecturasSonCopias() {
        InMemoryBlackboxRing ring = new InMemoryBlackboxRing(2);
        byte[] payload = {1, 2};
        ring.append(BlackboxRing.RECORD_CPU, payload);
        payload[0] = 9;
        ring.read(0).get(0).payload()[1] = 9;

        assertArrayEquals(new byte[]{1, 2}, ring.read(0).get(0).payload());
    }

    @Test
    void validaTipoTamanoYCierre() {
        InMemoryBlackboxRing ring = new InMemoryBlackboxRing(2);
        assertThrows(IllegalArgumentException.class, () -> ring.append(9, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> ring.append(BlackboxRing.RECORD_CPU, new byte[129]));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryBlackboxRing(0));

        ring.close();
        ring.close();
        assertThrows(IllegalStateException.class, () -> ring.read(0));
    }
}
