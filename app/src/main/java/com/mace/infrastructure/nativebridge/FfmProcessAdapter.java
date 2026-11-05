package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.ImagePath;
import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessInventory;
import com.mace.domain.model.ProcessQueryStatus;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.ProcessSampleResult;
import com.mace.domain.model.ResponsivenessOutcome;
import com.mace.domain.model.ResponsivenessResult;
import com.mace.domain.model.TerminationAttempt;
import com.mace.domain.model.TerminationOutcome;
import com.mace.domain.port.out.ProcessLifecyclePort;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Adaptador de salida que implementa {@link ProcessLifecyclePort} con las funciones V2 de la DLL.
 *
 * <ul>
 *   <li>Inventario: {@code CP_EnumerateUserlandProcessesV2} con buffer dinámico. Si el número
 *       de procesos crece entre la consulta y la lectura ({@code BUFFER_TOO_SMALL}) se reintenta
 *       con el tamaño informado más un margen, hasta {@value #MAX_PROCESSES} registros.</li>
 *   <li>Ruta: {@code CP_GetProcessImagePathV2} (UTF-16 sin truncar). Se guarda por identidad
 *       mientras el proceso siga en el inventario. Si no está disponible se conserva el motivo.</li>
 *   <li>Terminación, muestra y ventanas: siempre con la identidad completa; la DLL verifica
 *       PID + fecha de creación sobre el mismo handle.</li>
 * </ul>
 *
 * <p>{@link #terminate(ProcessIdentity)} es un mecanismo de bajo nivel; la decisión de
 * invocarlo pertenece exclusivamente al usuario a través de {@code TerminateProcessService}.</p>
 */
public final class FfmProcessAdapter implements ProcessLifecyclePort {

    /** Límite de seguridad del inventario (16 384 × 576 B ≈ 9 MiB). */
    public static final int MAX_PROCESSES = 16_384;
    /** Registros extra al reintentar, para absorber procesos que nacen entre llamadas. */
    static final int CAPACITY_MARGIN = 64;
    static final int MAX_ENUMERATION_ATTEMPTS = 4;
    /** Capacidad inicial de ruta en caracteres (MAX_PATH); se amplía al tamaño requerido. */
    static final int INITIAL_PATH_CHARS = 260;
    /** Máximo de una ruta extendida de Windows (32 767 caracteres + NUL). */
    static final int MAX_PATH_CHARS = 32_768;

    private static final Logger LOG = System.getLogger(FfmProcessAdapter.class.getName());
    private static final long MAX_UINT32 = 0xFFFF_FFFFL;

    private final NativeApi nativeApi;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Map<ProcessIdentity, ImagePath> pathCache = new HashMap<>();
    private int lastCapacity = 256;

    public FfmProcessAdapter(NativeApi nativeApi) {
        this(nativeApi, Clock.systemUTC(), System::nanoTime);
    }

    public FfmProcessAdapter(NativeApi nativeApi, Clock clock, LongSupplier nanoTime) {
        this.nativeApi = Objects.requireNonNull(nativeApi, "nativeApi no puede ser null");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime no puede ser null");
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    /**
     * @throws NativeLibraryException si Windows falla al enumerar o se supera el límite seguro
     */
    @Override
    public synchronized ProcessInventory enumerateProcesses() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment required = arena.allocate(JAVA_INT);
            MemorySegment skipped = arena.allocate(JAVA_INT);
            MemorySegment win32 = arena.allocate(JAVA_INT);
            int capacity = Math.min(lastCapacity, MAX_PROCESSES);
            for (int attempt = 0; attempt < MAX_ENUMERATION_ATTEMPTS; attempt++) {
                try (Arena bufferArena = Arena.ofConfined()) {
                    MemorySegment buffer = bufferArena.allocate(NativeLayouts.PROCESS_INFO, capacity);
                    int status = nativeApi.enumerateUserlandProcessesV2(buffer, capacity, required, skipped, win32);
                    long requiredCount = Integer.toUnsignedLong(required.get(JAVA_INT, 0));
                    if (status == NativeStatus.OK) {
                        long nanos = nanoTime.getAsLong();
                        List<ProcessEntry> entries = decodeInventory(arena, buffer, (int) Math.min(requiredCount, capacity));
                        lastCapacity = (int) Math.min(MAX_PROCESSES, requiredCount + CAPACITY_MARGIN);
                        return new ProcessInventory(entries, (int) Math.min(Integer.MAX_VALUE,
                                Integer.toUnsignedLong(skipped.get(JAVA_INT, 0))), clock.instant(), nanos);
                    }
                    if (status != NativeStatus.BUFFER_TOO_SMALL) {
                        throw new NativeLibraryException(NativeFunctions.SYMBOL_ENUMERATE_PROCESSES_V2 + " devolvió "
                                + NativeStatus.name(status) + " (Win32 " + win32.get(JAVA_INT, 0) + ")");
                    }
                    if (requiredCount > MAX_PROCESSES) {
                        throw new NativeLibraryException("La DLL informa " + requiredCount
                                + " procesos; el límite seguro es " + MAX_PROCESSES);
                    }
                    capacity = (int) Math.min(MAX_PROCESSES, requiredCount + CAPACITY_MARGIN);
                }
            }
            throw new NativeLibraryException("El número de procesos siguió creciendo en "
                    + MAX_ENUMERATION_ATTEMPTS + " intentos");
        }
    }

    private List<ProcessEntry> decodeInventory(Arena arena, MemorySegment buffer, int count) {
        List<ProcessEntry> entries = new ArrayList<>(count);
        Set<ProcessIdentity> live = new HashSet<>();
        for (int i = 0; i < count; i++) {
            MemorySegment record = buffer.asSlice(i * NativeLayouts.PROCESS_INFO_SIZE, NativeLayouts.PROCESS_INFO_SIZE);
            ProcessEntry entry = decodeProcessInfo(record, identity -> imagePathCached(arena, identity));
            if (entry != null) {
                entries.add(entry);
                live.add(entry.identity());
            }
        }
        pathCache.keySet().retainAll(live);
        return List.copyOf(entries);
    }

    /**
     * Decodifica un {@code MaceProcessInfoV2}.
     *
     * @return la entrada, o {@code null} si el registro no tiene identidad completa o nombre
     */
    static ProcessEntry decodeProcessInfo(MemorySegment record, Function<ProcessIdentity, ImagePath> paths) {
        V2Records.requireHeader(record, NativeLayouts.PROCESS_INFO_SIZE, "MaceProcessInfoV2");
        ProcessIdentity identity = V2Records.readIdentity(record, NativeLayouts.PROCESS_INFO_IDENTITY_OFFSET);
        String name = WideStrings.read(record, NativeLayouts.PROCESS_INFO_NAME_OFFSET, NativeLayouts.PROCESS_NAME_CHARS);
        if (identity == null || name.isBlank()) {
            return null;
        }
        MemoryMetrics memory = new MemoryMetrics(
                V2Records.readU64(record, NativeLayouts.PROCESS_INFO_WORKING_SET_OFFSET),
                V2Records.readU64(record, NativeLayouts.PROCESS_INFO_PRIVATE_BYTES_OFFSET));
        return new ProcessEntry(identity, name, paths.apply(identity), memory);
    }

    // ------------------------------------------------------------------
    // Ruta completa
    // ------------------------------------------------------------------

    private ImagePath imagePathCached(Arena arena, ProcessIdentity identity) {
        ImagePath cached = pathCache.get(identity);
        if (cached != null) {
            return cached;
        }
        ImagePath path = queryImagePath(arena, identity);
        if (path.status() != ProcessQueryStatus.ERROR) {
            pathCache.put(identity, path);
        }
        return path;
    }

    /** Ruta del proceso identificado; no se inventa si la DLL no la entrega. */
    public synchronized ImagePath imagePath(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        try (Arena arena = Arena.ofConfined()) {
            return queryImagePath(arena, identity);
        }
    }

    private ImagePath queryImagePath(Arena arena, ProcessIdentity identity) {
        if (identity.pid().value() > MAX_UINT32) {
            return ImagePath.unavailable(ProcessQueryStatus.ERROR);
        }
        MemorySegment id = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
        V2Records.writeIdentity(id, 0L, identity);
        MemorySegment required = arena.allocate(JAVA_INT);
        MemorySegment win32 = arena.allocate(JAVA_INT);
        int capacity = INITIAL_PATH_CHARS;
        for (int attempt = 0; attempt < 2; attempt++) {
            MemorySegment buffer = arena.allocate(WideStrings.WCHAR_SIZE * capacity, WideStrings.WCHAR_SIZE);
            int status = nativeApi.getProcessImagePathV2(id, buffer, capacity, required, win32);
            if (status == NativeStatus.OK) {
                String path = WideStrings.read(buffer, 0L, capacity);
                return path.isBlank() ? ImagePath.unavailable(ProcessQueryStatus.ERROR) : ImagePath.of(path);
            }
            if (status != NativeStatus.BUFFER_TOO_SMALL) {
                return ImagePath.unavailable(NativeCodes.processQueryStatus(status));
            }
            long requiredChars = Integer.toUnsignedLong(required.get(JAVA_INT, 0));
            if (requiredChars <= capacity || requiredChars > MAX_PATH_CHARS) {
                break;
            }
            capacity = (int) requiredChars;
        }
        return ImagePath.unavailable(ProcessQueryStatus.ERROR);
    }

    // ------------------------------------------------------------------
    // Terminación, muestra y ventanas
    // ------------------------------------------------------------------

    /**
     * Finaliza el proceso identificado. Debe invocarse únicamente por acción manual del usuario.
     * No hay ninguna caída al cierre heredado por PID.
     */
    @Override
    public TerminationAttempt terminate(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        if (identity.pid().value() > MAX_UINT32) {
            return new TerminationAttempt(identity, TerminationOutcome.INVALID_IDENTITY, 0);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
            V2Records.writeIdentity(id, 0L, identity);
            MemorySegment win32 = arena.allocate(JAVA_INT);
            int status = nativeApi.terminateProcessV2(id, win32);
            TerminationOutcome outcome = NativeCodes.terminationOutcome(status);
            if (outcome == TerminationOutcome.FAILED) {
                LOG.log(Level.WARNING, "{0} devolvió {1} (Win32 {2})", NativeFunctions.SYMBOL_TERMINATE_PROCESS_V2,
                        NativeStatus.name(status), win32.get(JAVA_INT, 0));
            }
            synchronized (this) {
                pathCache.remove(identity);
            }
            return new TerminationAttempt(identity, outcome, win32.get(JAVA_INT, 0));
        }
    }

    @Override
    public ProcessSampleResult sample(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        if (identity.pid().value() > MAX_UINT32) {
            return ProcessSampleResult.failed(identity, ProcessQueryStatus.ERROR, 0);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
            V2Records.writeIdentity(id, 0L, identity);
            MemorySegment record = arena.allocate(NativeLayouts.PROCESS_SAMPLE);
            MemorySegment win32 = arena.allocate(JAVA_INT);
            int status = nativeApi.sampleProcessV2(id, record, (int) NativeLayouts.PROCESS_SAMPLE_SIZE, win32);
            if (status != NativeStatus.OK) {
                return ProcessSampleResult.failed(identity, NativeCodes.processQueryStatus(status), win32.get(JAVA_INT, 0));
            }
            ProcessSample sample = V2Records.decodeSample(record);
            if (sample == null || !sample.identity().equals(identity)) {
                return ProcessSampleResult.failed(identity, ProcessQueryStatus.PID_REUSED, 0);
            }
            return ProcessSampleResult.of(sample);
        }
    }

    @Override
    public ResponsivenessResult checkResponsiveness(ProcessIdentity identity) {
        Objects.requireNonNull(identity, "identity no puede ser null");
        if (identity.pid().value() > MAX_UINT32) {
            return ResponsivenessResult.of(identity, ResponsivenessOutcome.ERROR);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocate(NativeLayouts.PROCESS_IDENTITY);
            V2Records.writeIdentity(id, 0L, identity);
            MemorySegment record = arena.allocate(NativeLayouts.RESPONSIVENESS);
            int status = nativeApi.checkProcessResponsivenessV2(id, record, (int) NativeLayouts.RESPONSIVENESS_SIZE);
            if (status != NativeStatus.OK) {
                return ResponsivenessResult.of(identity, NativeCodes.responsivenessFromStatus(status));
            }
            return V2Records.decodeResponsiveness(record, identity);
        }
    }
}
