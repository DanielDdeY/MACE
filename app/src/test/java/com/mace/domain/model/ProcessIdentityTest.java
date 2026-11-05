package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessIdentityTest {

    @Test
    void unPidReutilizadoEsOtraIdentidad() {
        ProcessIdentity original = ProcessIdentity.of(6400, 133_500_000_000_000_000L);
        ProcessIdentity reutilizado = ProcessIdentity.of(6400, 133_500_000_100_000_000L);

        assertEquals(original.pid(), reutilizado.pid());
        assertNotEquals(original, reutilizado);
        assertEquals(original, ProcessIdentity.of(6400, 133_500_000_000_000_000L));
    }

    @Test
    void laIdentidadCompletaExigeFechaDeCreacion() {
        assertThrows(IllegalArgumentException.class, () -> ProcessIdentity.of(10, 0));
    }

    @Test
    void elFiletimeSeConvierteAInstantUtc() {
        Instant instant = Instant.parse("2026-01-01T08:00:00.1234567Z");
        long fileTime = ProcessIdentity.toFileTime(instant);

        assertEquals(instant, ProcessIdentity.of(1, fileTime).creationTime());
        // 1970-01-01 equivale a 116444736000000000 intervalos de 100 ns desde 1601.
        assertEquals(116_444_736_000_000_000L, ProcessIdentity.toFileTime(Instant.EPOCH));
    }

    @Test
    void laRutaNoDisponibleConservaSuMotivoYNoSeInventa() {
        ImagePath path = ImagePath.unavailable(ProcessQueryStatus.ACCESS_DENIED);
        ProcessEntry entry = new ProcessEntry(ProcessIdentity.of(1900, 1), "tool.exe", path,
                MemoryMetrics.unavailable(MetricState.ACCESS_DENIED));

        assertFalse(entry.imagePath().isAvailable());
        assertEquals(ProcessQueryStatus.ACCESS_DENIED, entry.imagePath().status());
        assertEquals("", entry.fullPath());
        assertFalse(entry.memory().workingSetReading().isValid());
        assertTrue(ProcessQueryStatus.PID_REUSED.processIsGone());
        assertThrows(IllegalArgumentException.class, () -> ImagePath.of(" "));
    }
}
