package com.corepulse.infrastructure.nativebridge;

import com.corepulse.domain.model.MemoryMetrics;
import com.corepulse.domain.model.ProcessEntry;
import com.corepulse.domain.model.ProcessId;
import com.corepulse.domain.port.out.ProcessLifecyclePort;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Adaptador de salida que implementa {@link ProcessLifecyclePort} sobre la DLL nativa
 * (Toolhelp32 + PSAPI para enumerar, {@code TerminateProcess} para finalizar,
 * {@code IsHungAppWindow} para detectar cuelgues).
 *
 * <p>La enumeración reserva un buffer de {@code maxProcesses × sizeof(ProcessData)} bytes en un
 * {@code Arena.ofConfined()} por llamada, lo recorre registro a registro y decodifica los
 * campos {@code wchar_t} a {@link String}. Toda la memoria nativa se libera al terminar.</p>
 *
 * <p>{@link #terminateProcess(ProcessId)} es un mecanismo de bajo nivel; la decisión de
 * invocarlo pertenece exclusivamente al usuario a través del caso de uso
 * {@code TerminateProcessService}. Este adaptador nunca toma esa decisión por su cuenta.</p>
 */
public final class FfmProcessAdapter implements ProcessLifecyclePort {

    /** Registros que caben en el buffer de enumeración (1024 × 1584 bytes ≈ 1,6 MiB). */
    public static final int DEFAULT_MAX_PROCESSES = 1024;

    private static final long MAX_UINT32 = 0xFFFF_FFFFL;
    private static final int RESPONSIVENESS_HUNG = 1;

    private final NativeFunctions nativeFunctions;
    private final int maxProcesses;

    public FfmProcessAdapter(NativeFunctions nativeFunctions) {
        this(nativeFunctions, DEFAULT_MAX_PROCESSES);
    }

    /**
     * @param nativeFunctions enlace FFM ya cargado
     * @param maxProcesses    capacidad del buffer de enumeración; los procesos que excedan
     *                        este límite no se reportan
     */
    public FfmProcessAdapter(NativeFunctions nativeFunctions, int maxProcesses) {
        this.nativeFunctions = Objects.requireNonNull(nativeFunctions, "nativeFunctions no puede ser null");
        if (maxProcesses <= 0) {
            throw new IllegalArgumentException("maxProcesses debe ser positivo: " + maxProcesses);
        }
        this.maxProcesses = maxProcesses;
    }

    /**
     * @return procesos de espacio de usuario ya filtrados por la capa nativa (sin System32,
     *         SysWOW64 ni la lista negra de procesos críticos)
     * @throws NativeLibraryException si la DLL reporta un error Win32 o la llamada nativa falla
     */
    @Override
    public List<ProcessEntry> listUserlandProcesses() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(NativeLayouts.PROCESS_DATA, maxProcesses);
            MemorySegment outCount = arena.allocate(JAVA_INT);
            outCount.set(JAVA_INT, 0L, 0);

            int result = nativeFunctions.enumerateUserlandProcesses(buffer, maxProcesses, outCount);
            if (result != 0) {
                throw new NativeLibraryException(NativeFunctions.SYMBOL_ENUMERATE_PROCESSES
                        + " devolvió el código Win32 " + result);
            }

            long count = Math.min(Integer.toUnsignedLong(outCount.get(JAVA_INT, 0L)), maxProcesses);
            List<ProcessEntry> entries = new ArrayList<>((int) count);
            for (long index = 0; index < count; index++) {
                MemorySegment record = buffer.asSlice(index * NativeLayouts.PROCESS_DATA_SIZE,
                        NativeLayouts.PROCESS_DATA_SIZE);
                toProcessEntry(record).ifPresent(entries::add);
            }
            return List.copyOf(entries);
        }
    }

    /**
     * Finaliza el proceso indicado. Debe invocarse únicamente por acción manual del usuario.
     *
     * @return {@code true} si la DLL devolvió 0 (proceso terminado); {@code false} si el PID
     *         está protegido, no existe, no hay permisos o excede el rango {@code uint32_t}
     */
    @Override
    public boolean terminateProcess(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");
        if (pid.value() > MAX_UINT32) {
            return false;
        }
        return nativeFunctions.terminateProcess((int) pid.value()) == 0;
    }

    /**
     * @return {@code false} solo si Windows reporta la ventana principal del proceso como
     *         colgada ({@code IsHungAppWindow}); procesos sin ventana se consideran responsivos
     */
    @Override
    public boolean isWindowResponsive(ProcessId pid) {
        Objects.requireNonNull(pid, "pid no puede ser null");
        if (pid.value() > MAX_UINT32) {
            return true;
        }
        return nativeFunctions.checkProcessResponsiveness((int) pid.value()) != RESPONSIVENESS_HUNG;
    }

    /**
     * Decodifica un registro {@code ProcessData} nativo al modelo de dominio.
     *
     * @param record segmento de exactamente (o al menos) {@link NativeLayouts#PROCESS_DATA_SIZE} bytes
     * @return la entrada, o vacío si el registro no tiene nombre o ruta (no debería ocurrir con
     *         la DLL actual, pero el dominio exige ambos campos)
     */
    static Optional<ProcessEntry> toProcessEntry(MemorySegment record) {
        Objects.requireNonNull(record, "record no puede ser null");
        long pid = Integer.toUnsignedLong(record.get(JAVA_INT, NativeLayouts.PROCESS_PID_OFFSET));
        String name = WideStrings.read(record, NativeLayouts.PROCESS_NAME_OFFSET, NativeLayouts.PROCESS_NAME_CHARS);
        String path = WideStrings.read(record, NativeLayouts.PROCESS_PATH_OFFSET, NativeLayouts.PROCESS_PATH_CHARS);
        if (name.isBlank() || path.isBlank()) {
            return Optional.empty();
        }
        long workingSet = toNonNegativeLong(record.get(JAVA_LONG, NativeLayouts.PROCESS_WORKING_SET_OFFSET));
        long privateBytes = toNonNegativeLong(record.get(JAVA_LONG, NativeLayouts.PROCESS_PRIVATE_BYTES_OFFSET));
        return Optional.of(new ProcessEntry(
                new ProcessId(pid),
                name,
                path,
                new MemoryMetrics(workingSet, privateBytes)));
    }

    /** Un {@code uint64_t} con el bit alto encendido se satura en lugar de volverse negativo. */
    private static long toNonNegativeLong(long unsignedValue) {
        return unsignedValue < 0 ? Long.MAX_VALUE : unsignedValue;
    }
}
