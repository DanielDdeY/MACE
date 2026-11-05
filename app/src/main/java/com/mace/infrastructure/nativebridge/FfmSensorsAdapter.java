package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.SystemMemoryReading;
import com.mace.domain.port.out.HardwareSensorsPort;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Adaptador de salida que implementa {@link HardwareSensorsPort} con las funciones V2 de la DLL:
 * {@code CP_EnumerateGpuAdapters}, {@code CP_PollCpuTelemetryV2}, {@code CP_PollGpuTelemetryV2}
 * (una llamada por LUID) y {@code CP_PollSystemMemoryV2}.
 *
 * <ul>
 *   <li>Las GPU se enumeran al arrancar, cada {@value #REENUMERATE_EVERY_POLLS} sondeos y cuando
 *       un LUID deja de existir ({@code MACE_E_DEVICE_NOT_FOUND}). Si el conteo cambia entre la
 *       consulta y la lectura se reintenta hasta {@value #MAX_ENUMERATION_ATTEMPTS} veces.</li>
 *   <li>Cada lectura de GPU se valida contra el LUID pedido: nunca se mezclan adaptadores.</li>
 *   <li>La disponibilidad de una GPU no se deduce de su temperatura: una GPU enumerada siempre
 *       aparece en el snapshot, con el estado que corresponda a cada métrica.</li>
 * </ul>
 *
 * <p>Cada llamada usa un {@code Arena.ofConfined()} propio. Solo lee e informa.</p>
 */
public final class FfmSensorsAdapter implements HardwareSensorsPort {

    static final int MAX_ENUMERATION_ATTEMPTS = 3;
    static final int MAX_GPU_ADAPTERS = 64;
    static final int REENUMERATE_EVERY_POLLS = 60;

    private static final Logger LOG = System.getLogger(FfmSensorsAdapter.class.getName());

    private final NativeApi nativeApi;
    private final Clock clock;

    private List<GpuAdapter> adapters = List.of();
    private boolean adaptersDirty = true;
    private int pollsSinceEnumeration;

    /**
     * @param nativeApi enlace FFM ya cargado y con ABI verificado (la telemetría se inicializa aquí)
     * @throws NativeLibraryException si {@code CP_InitializeTelemetry} falla
     */
    public FfmSensorsAdapter(NativeApi nativeApi) {
        this(nativeApi, Clock.systemUTC());
    }

    public FfmSensorsAdapter(NativeApi nativeApi, Clock clock) {
        this.nativeApi = Objects.requireNonNull(nativeApi, "nativeApi no puede ser null");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser null");
        int result = nativeApi.ensureTelemetryInitialized();
        if (result != 0) {
            throw new NativeLibraryException(NativeFunctions.SYMBOL_INITIALIZE_TELEMETRY
                    + " devolvió el código " + result);
        }
    }

    @Override
    public synchronized HardwareSnapshot fetchHardwareSnapshot() {
        if (adaptersDirty || pollsSinceEnumeration >= REENUMERATE_EVERY_POLLS) {
            refreshAdapters();
        }
        pollsSinceEnumeration++;
        try (Arena arena = Arena.ofConfined()) {
            CpuReading cpu = pollCpu(arena);
            List<GpuReading> gpus = new ArrayList<>(adapters.size());
            for (GpuAdapter adapter : adapters) {
                gpus.add(pollGpu(arena, adapter));
            }
            SystemMemoryReading memory = pollMemory(arena);
            return new HardwareSnapshot(clock.instant(), cpu, gpus, memory);
        }
    }

    @Override
    public synchronized List<GpuAdapter> listGpuAdapters() {
        refreshAdapters();
        return adapters;
    }

    // ------------------------------------------------------------------
    // GPU: inventario
    // ------------------------------------------------------------------

    private void refreshAdapters() {
        try {
            adapters = enumerateAdapters(nativeApi);
            adaptersDirty = false;
            pollsSinceEnumeration = 0;
        } catch (NativeLibraryException e) {
            // Se conserva la lista anterior y se reintenta en el próximo sondeo.
            adaptersDirty = true;
            LOG.log(Level.WARNING, "No se pudo enumerar las GPU: {0}", e.getMessage());
        }
    }

    /**
     * Consulta el número de GPU y las lee con un buffer exacto; si el conteo cambia en medio
     * ({@code BUFFER_TOO_SMALL} o {@code BUSY}) repite la consulta con límite.
     */
    static List<GpuAdapter> enumerateAdapters(NativeApi nativeApi) {
        int lastStatus = NativeStatus.OK;
        for (int attempt = 0; attempt < MAX_ENUMERATION_ATTEMPTS; attempt++) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment count = arena.allocate(JAVA_INT);
                int status = nativeApi.enumerateGpuAdapters(MemorySegment.NULL, 0, count);
                int required = count.get(JAVA_INT, 0);
                if (status == NativeStatus.OK && required == 0) {
                    return List.of();
                }
                if (status != NativeStatus.BUFFER_TOO_SMALL) {
                    throw new NativeLibraryException(NativeFunctions.SYMBOL_ENUMERATE_GPU_ADAPTERS
                            + " (consulta de conteo) devolvió " + NativeStatus.name(status));
                }
                if (Integer.compareUnsigned(required, MAX_GPU_ADAPTERS) > 0) {
                    throw new NativeLibraryException("La DLL informa " + Integer.toUnsignedString(required)
                            + " GPU; el límite seguro es " + MAX_GPU_ADAPTERS);
                }
                MemorySegment buffer = arena.allocate(NativeLayouts.GPU_ADAPTER_INFO, required);
                status = nativeApi.enumerateGpuAdapters(buffer, required, count);
                lastStatus = status;
                if (status == NativeStatus.OK) {
                    int written = count.get(JAVA_INT, 0);
                    List<GpuAdapter> result = new ArrayList<>(written);
                    for (int i = 0; i < written && i < required; i++) {
                        result.add(decodeAdapter(buffer.asSlice(i * NativeLayouts.GPU_ADAPTER_INFO_SIZE,
                                NativeLayouts.GPU_ADAPTER_INFO_SIZE)));
                    }
                    return List.copyOf(result);
                }
                if (status != NativeStatus.BUFFER_TOO_SMALL && status != NativeStatus.BUSY) {
                    throw new NativeLibraryException(NativeFunctions.SYMBOL_ENUMERATE_GPU_ADAPTERS
                            + " devolvió " + NativeStatus.name(status));
                }
            }
        }
        throw new NativeLibraryException("El número de GPU cambió en " + MAX_ENUMERATION_ATTEMPTS
                + " intentos seguidos (último estado " + NativeStatus.name(lastStatus) + ")");
    }

    static GpuAdapter decodeAdapter(MemorySegment record) {
        V2Records.requireHeader(record, NativeLayouts.GPU_ADAPTER_INFO_SIZE, "MaceGpuAdapterInfoV2");
        return new GpuAdapter(
                V2Records.readLuid(record, NativeLayouts.GPU_ADAPTER_LUID_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_VENDOR_ID_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_DEVICE_ID_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_SUBSYSTEM_ID_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_REVISION_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_DXGI_FLAGS_OFFSET),
                record.get(JAVA_INT, NativeLayouts.GPU_ADAPTER_INDEX_OFFSET),
                WideStrings.read(record, NativeLayouts.GPU_ADAPTER_DESCRIPTION_OFFSET,
                        NativeLayouts.GPU_DESCRIPTION_CHARS));
    }

    // ------------------------------------------------------------------
    // Sondeos
    // ------------------------------------------------------------------

    private CpuReading pollCpu(Arena arena) {
        MemorySegment record = arena.allocate(NativeLayouts.CPU_TELEMETRY);
        int status = nativeApi.pollCpuTelemetryV2(record, (int) NativeLayouts.CPU_TELEMETRY_SIZE);
        if (status == NativeStatus.OK) {
            return V2Records.decodeCpu(record);
        }
        LOG.log(Level.DEBUG, "{0} devolvió {1}", NativeFunctions.SYMBOL_POLL_CPU_V2, NativeStatus.name(status));
        return CpuReading.unavailable(stateForFailedPoll(status), 0L);
    }

    private GpuReading pollGpu(Arena arena, GpuAdapter adapter) {
        MemorySegment luid = arena.allocate(NativeLayouts.ADAPTER_LUID);
        V2Records.writeLuid(luid, 0L, adapter.id());
        MemorySegment record = arena.allocate(NativeLayouts.GPU_TELEMETRY);
        int status = nativeApi.pollGpuTelemetryV2(luid, record, (int) NativeLayouts.GPU_TELEMETRY_SIZE);
        if (status == NativeStatus.OK) {
            GpuAdapterId reported = V2Records.readLuid(record, NativeLayouts.GPU_LUID_OFFSET);
            if (!reported.equals(adapter.id())) {
                LOG.log(Level.WARNING, "La DLL devolvió datos de {0} al consultar {1}; se descartan",
                        reported, adapter.id());
                return GpuReading.unavailable(adapter, MetricState.READ_FAILED, 0L);
            }
            return V2Records.decodeGpu(record, adapter);
        }
        if (status == NativeStatus.DEVICE_NOT_FOUND) {
            adaptersDirty = true;
            return GpuReading.unavailable(adapter, MetricState.NO_DEVICE, 0L);
        }
        LOG.log(Level.DEBUG, "{0} devolvió {1} para {2}", NativeFunctions.SYMBOL_POLL_GPU_V2,
                NativeStatus.name(status), adapter.id());
        return GpuReading.unavailable(adapter, stateForFailedPoll(status), 0L);
    }

    private SystemMemoryReading pollMemory(Arena arena) {
        MemorySegment record = arena.allocate(NativeLayouts.SYSTEM_MEMORY);
        int status = nativeApi.pollSystemMemoryV2(record, (int) NativeLayouts.SYSTEM_MEMORY_SIZE);
        if (status == NativeStatus.OK) {
            return V2Records.decodeSystemMemory(record);
        }
        return SystemMemoryReading.unavailable(stateForFailedPoll(status), 0L);
    }

    private static MetricState stateForFailedPoll(int status) {
        return switch (status) {
            case NativeStatus.NOT_INITIALIZED -> MetricState.NOT_INITIALIZED;
            case NativeStatus.ACCESS_DENIED -> MetricState.ACCESS_DENIED;
            case NativeStatus.DEVICE_NOT_FOUND -> MetricState.NO_DEVICE;
            case NativeStatus.SENSOR_UNAVAILABLE -> MetricState.NO_PROVIDER;
            default -> MetricState.READ_FAILED;
        };
    }
}
