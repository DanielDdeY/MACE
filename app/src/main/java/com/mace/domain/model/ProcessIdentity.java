package com.mace.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Identidad completa de un proceso ({@code MaceProcessIdentityV2}): PID <strong>más</strong>
 * fecha de creación. Windows reutiliza los PID; comparar ambos campos evita confundir un
 * proceso terminado con otro nuevo que recibió el mismo PID. Toda operación sobre un proceso
 * seleccionado (terminar, muestrear, consultar ventanas) usa esta identidad, nunca el PID solo.
 *
 * @param pid                    identificador de proceso
 * @param creationTime100nsUtc   FILETIME de creación: intervalos de 100 ns desde 1601-01-01 UTC
 */
public record ProcessIdentity(ProcessId pid, long creationTime100nsUtc) {

    /** Intervalos de 100 ns entre 1601-01-01 y 1970-01-01. */
    private static final long FILETIME_UNIX_EPOCH_OFFSET = 116_444_736_000_000_000L;

    public ProcessIdentity {
        Objects.requireNonNull(pid, "pid no puede ser null");
        if (creationTime100nsUtc == 0L) {
            throw new IllegalArgumentException("Una identidad completa requiere la fecha de creación (FILETIME != 0)");
        }
    }

    public static ProcessIdentity of(long pid, long creationTime100nsUtc) {
        return new ProcessIdentity(new ProcessId(pid), creationTime100nsUtc);
    }

    /** Fecha de creación como {@link Instant} UTC (el FILETIME se interpreta sin signo). */
    public Instant creationTime() {
        long sinceUnix100ns = creationTime100nsUtc - FILETIME_UNIX_EPOCH_OFFSET;
        if (creationTime100nsUtc < 0) {
            // FILETIME con el bit alto encendido: fuera del rango plausible; se satura.
            return Instant.MAX;
        }
        long seconds = Math.floorDiv(sinceUnix100ns, 10_000_000L);
        long nanos = Math.floorMod(sinceUnix100ns, 10_000_000L) * 100L;
        return Instant.ofEpochSecond(seconds, nanos);
    }

    /** Convierte un {@link Instant} a FILETIME (útil para mocks y pruebas). */
    public static long toFileTime(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 10_000_000L) + instant.getNano() / 100L,
                FILETIME_UNIX_EPOCH_OFFSET);
    }

    @Override
    public String toString() {
        return "PID(" + pid.value() + ")@" + Long.toUnsignedString(creationTime100nsUtc);
    }
}
