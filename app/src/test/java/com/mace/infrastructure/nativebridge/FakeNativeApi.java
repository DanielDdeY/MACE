package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.ProcessEntry;
import com.mace.domain.model.ProcessIdentity;
import com.mace.domain.model.ProcessSample;
import com.mace.domain.model.SystemMemoryReading;
import com.mace.infrastructure.telemetry.BlackboxRing;
import com.mace.infrastructure.telemetry.InMemoryBlackboxRing;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Implementación Java de {@link NativeApi} que reproduce la semántica de la DLL descrita en
 * {@code native/docs/ABI.md}: capacidades explícitas, ninguna escritura con buffer corto,
 * {@code BUFFER_TOO_SMALL} con el tamaño requerido, estados negativos, etc. Los registros se
 * escriben con {@link V2Records}; las pruebas de offsets concretos escriben bytes a mano.
 */
final class FakeNativeApi implements NativeApi {

    // ---------------- Telemetría ----------------
    int initResult = 0;
    int initCalls;
    int cpuStatus = NativeStatus.OK;
    Function<Integer, CpuReading> cpuReadings;
    /** Si no es null, se copia tal cual en el registro de CPU (pruebas de bytes crudos). */
    byte[] rawCpuRecord;
    int memoryStatus = NativeStatus.OK;
    SystemMemoryReading memoryReading;
    int cpuPolls;

    // ---------------- GPU ----------------
    final Map<GpuAdapterId, GpuAdapter> adapters = new LinkedHashMap<>();
    final Map<GpuAdapterId, Function<Integer, GpuReading>> gpuReadings = new HashMap<>();
    final Map<GpuAdapterId, Integer> gpuPollStatus = new HashMap<>();
    final Map<GpuAdapterId, GpuAdapterId> reportedLuid = new HashMap<>();
    final Map<GpuAdapterId, byte[]> rawGpuRecords = new HashMap<>();
    /** Veces que la lectura (no la consulta) encontrará una GPU más que la contada. */
    int gpuGrowthOnRead;
    int gpuEnumerationCalls;
    int gpuPolls;

    // ---------------- Procesos ----------------
    final List<ProcessEntry> processes = new ArrayList<>();
    int processGrowthOnRead;
    int processExtraWhenGrowing = 10;
    int skipped;
    int processEnumerationCalls;
    final Map<ProcessIdentity, String> paths = new HashMap<>();
    final Map<ProcessIdentity, Integer> pathStatus = new HashMap<>();
    int pathCalls;
    final Map<ProcessIdentity, Integer> terminateStatus = new HashMap<>();
    final List<byte[]> terminateIdentityBytes = new ArrayList<>();
    final Map<ProcessIdentity, Integer> sampleStatus = new HashMap<>();
    final Map<ProcessIdentity, ProcessSample> samples = new HashMap<>();
    final Map<ProcessIdentity, int[]> responsiveness = new HashMap<>();
    final Map<ProcessIdentity, Integer> responsivenessStatus = new HashMap<>();

    // ---------------- Caja negra ----------------
    final Map<Long, InMemoryBlackboxRing> rings = new HashMap<>();
    long nextHandle = 1;
    int blackboxCloseCalls;

    @Override
    public AbiInfo abiInfo() {
        return new AbiInfo(64, 2, 1, 4, 1584, 28, 16, 2, 8, 0, AbiRequirements.REQUIRED_CAPABILITIES, 1, 4, 2, 0);
    }

    @Override
    public int ensureTelemetryInitialized() {
        initCalls++;
        return initResult;
    }

    @Override
    public int enumerateGpuAdapters(MemorySegment out, int capacity, MemorySegment outCount) {
        gpuEnumerationCalls++;
        List<GpuAdapter> list = new ArrayList<>(adapters.values());
        boolean isRead = out.address() != 0L;
        if (isRead && gpuGrowthOnRead > 0) {
            gpuGrowthOnRead--;
            outCount.set(JAVA_INT, 0, list.size() + 1);
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        outCount.set(JAVA_INT, 0, list.size());
        if (capacity < list.size()) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        for (int i = 0; i < list.size(); i++) {
            writeAdapter(out.asSlice(i * NativeLayouts.GPU_ADAPTER_INFO_SIZE), list.get(i));
        }
        return NativeStatus.OK;
    }

    static void writeAdapter(MemorySegment record, GpuAdapter adapter) {
        record.asSlice(0, NativeLayouts.GPU_ADAPTER_INFO_SIZE).fill((byte) 0);
        record.set(JAVA_INT, 0, (int) NativeLayouts.GPU_ADAPTER_INFO_SIZE);
        record.set(JAVA_INT, 4, 2);
        record.set(JAVA_INT, 8, adapter.id().lowPart());
        record.set(JAVA_INT, 12, adapter.id().highPart());
        record.set(JAVA_INT, 16, adapter.vendorId());
        record.set(JAVA_INT, 20, adapter.deviceId());
        record.set(JAVA_INT, 24, adapter.subsystemId());
        record.set(JAVA_INT, 28, adapter.revision());
        record.set(JAVA_INT, 32, adapter.dxgiFlags());
        record.set(JAVA_INT, 36, adapter.adapterIndex());
        WideStrings.write(record, 40, 128, adapter.description());
    }

    @Override
    public int pollCpuTelemetryV2(MemorySegment out, int capacity) {
        if (capacity < NativeLayouts.CPU_TELEMETRY_SIZE) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        if (cpuStatus != NativeStatus.OK) {
            return cpuStatus;
        }
        cpuPolls++;
        byte[] bytes = rawCpuRecord != null ? rawCpuRecord : V2Records.encodeCpu(cpuReadings.apply(cpuPolls));
        MemorySegment.copy(bytes, 0, out, JAVA_BYTE, 0, bytes.length);
        return NativeStatus.OK;
    }

    @Override
    public int pollGpuTelemetryV2(MemorySegment luid, MemorySegment out, int capacity) {
        if (capacity < NativeLayouts.GPU_TELEMETRY_SIZE) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        GpuAdapterId id = new GpuAdapterId(luid.get(JAVA_INT, 0), luid.get(JAVA_INT, 4));
        Integer status = gpuPollStatus.get(id);
        if (status != null) {
            return status;
        }
        if (!adapters.containsKey(id)) {
            return NativeStatus.DEVICE_NOT_FOUND;
        }
        gpuPolls++;
        byte[] bytes = rawGpuRecords.containsKey(id) ? rawGpuRecords.get(id) : V2Records.encodeGpu(gpuReadings.get(id).apply(gpuPolls));
        MemorySegment.copy(bytes, 0, out, JAVA_BYTE, 0, bytes.length);
        GpuAdapterId reported = reportedLuid.get(id);
        if (reported != null) {
            out.set(JAVA_INT, 16, reported.lowPart());
            out.set(JAVA_INT, 20, reported.highPart());
        }
        return NativeStatus.OK;
    }

    @Override
    public int pollSystemMemoryV2(MemorySegment out, int capacity) {
        if (capacity < NativeLayouts.SYSTEM_MEMORY_SIZE) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        if (memoryStatus != NativeStatus.OK) {
            return memoryStatus;
        }
        byte[] bytes = V2Records.encodeSystemMemory(memoryReading);
        MemorySegment.copy(bytes, 0, out, JAVA_BYTE, 0, bytes.length);
        return NativeStatus.OK;
    }

    @Override
    public int enumerateUserlandProcessesV2(MemorySegment out, int capacity, MemorySegment required,
                                            MemorySegment skippedOut, MemorySegment win32) {
        processEnumerationCalls++;
        int count = processes.size();
        if (processGrowthOnRead > 0) {
            processGrowthOnRead--;
            count += processExtraWhenGrowing;
            required.set(JAVA_INT, 0, count);
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        required.set(JAVA_INT, 0, count);
        skippedOut.set(JAVA_INT, 0, skipped);
        win32.set(JAVA_INT, 0, 0);
        if (capacity < count) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        for (int i = 0; i < count; i++) {
            writeProcessInfo(out.asSlice(i * NativeLayouts.PROCESS_INFO_SIZE, NativeLayouts.PROCESS_INFO_SIZE),
                    processes.get(i));
        }
        return NativeStatus.OK;
    }

    static void writeProcessInfo(MemorySegment record, ProcessEntry entry) {
        record.fill((byte) 0);
        record.set(JAVA_INT, 0, (int) NativeLayouts.PROCESS_INFO_SIZE);
        record.set(JAVA_INT, 4, 2);
        V2Records.writeIdentity(record, 8, entry.identity());
        V2Records.writeU64(record, 24, entry.memory().workingSetReading());
        V2Records.writeU64(record, 40, entry.memory().privateBytesReading());
        WideStrings.write(record, 56, 260, entry.name());
    }

    @Override
    public int getProcessIdentityV2(int pid, MemorySegment out, int capacity, MemorySegment win32) {
        return NativeStatus.INVALID_ARGUMENT;
    }

    @Override
    public int getProcessImagePathV2(MemorySegment identity, MemorySegment outPath, int capacityChars,
                                     MemorySegment required, MemorySegment win32) {
        pathCalls++;
        ProcessIdentity id = V2Records.readIdentity(identity, 0);
        required.set(JAVA_INT, 0, 0);
        Integer status = pathStatus.get(id);
        if (status != null) {
            return status;
        }
        String path = paths.get(id);
        if (path == null) {
            return NativeStatus.PROCESS_EXITED;
        }
        required.set(JAVA_INT, 0, path.length() + 1);
        if (capacityChars < path.length() + 1) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        for (int i = 0; i < path.length(); i++) {
            outPath.set(JAVA_CHAR, i * 2L, path.charAt(i));
        }
        outPath.set(JAVA_CHAR, path.length() * 2L, '\0');
        return NativeStatus.OK;
    }

    @Override
    public int terminateProcessV2(MemorySegment identity, MemorySegment win32) {
        terminateIdentityBytes.add(identity.asSlice(0, 16).toArray(JAVA_BYTE));
        ProcessIdentity id = V2Records.readIdentity(identity, 0);
        int status = terminateStatus.getOrDefault(id, NativeStatus.OK);
        win32.set(JAVA_INT, 0, status == NativeStatus.WINDOWS_ERROR ? 1450 : 0);
        return status;
    }

    @Override
    public int sampleProcessV2(MemorySegment identity, MemorySegment out, int capacity, MemorySegment win32) {
        if (capacity < NativeLayouts.PROCESS_SAMPLE_SIZE) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        ProcessIdentity id = V2Records.readIdentity(identity, 0);
        Integer status = sampleStatus.get(id);
        if (status != null) {
            win32.set(JAVA_INT, 0, 5);
            return status;
        }
        byte[] bytes = V2Records.encodeSample(samples.get(id));
        MemorySegment.copy(bytes, 0, out, JAVA_BYTE, 0, bytes.length);
        return NativeStatus.OK;
    }

    @Override
    public int checkProcessResponsivenessV2(MemorySegment identity, MemorySegment out, int capacity) {
        if (capacity < NativeLayouts.RESPONSIVENESS_SIZE) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        ProcessIdentity id = V2Records.readIdentity(identity, 0);
        Integer status = responsivenessStatus.get(id);
        if (status != null) {
            return status;
        }
        int[] values = responsiveness.get(id); // {outcome, examined, hung, win32}
        out.fill((byte) 0);
        out.set(JAVA_INT, 0, (int) NativeLayouts.RESPONSIVENESS_SIZE);
        out.set(JAVA_INT, 4, 2);
        V2Records.writeIdentity(out, 8, id);
        out.set(JAVA_INT, 24, values[1]);
        out.set(JAVA_INT, 28, values[2]);
        out.set(JAVA_INT, 32, values[0]);
        out.set(JAVA_INT, 36, values[3]);
        return NativeStatus.OK;
    }

    // ---------------- Caja negra: delega en el anillo Java con semántica de C ----------------

    @Override
    public int blackboxCreateV2(MemorySegment name, int capacity, MemorySegment outHandle, MemorySegment win32) {
        if (capacity <= 0 || capacity > 65_536) {
            return NativeStatus.INVALID_ARGUMENT;
        }
        long handle = nextHandle++;
        rings.put(handle, new InMemoryBlackboxRing(capacity, () -> 7L));
        outHandle.set(JAVA_LONG, 0, handle);
        return NativeStatus.OK;
    }

    @Override
    public int blackboxGetStatsV2(long handle, MemorySegment out, int capacity) {
        InMemoryBlackboxRing ring = rings.get(handle);
        if (ring == null) {
            return NativeStatus.INVALID_ARGUMENT;
        }
        BlackboxRing.RingStats stats = ring.stats();
        out.fill((byte) 0);
        out.set(JAVA_INT, 0, 48);
        out.set(JAVA_INT, 4, 2);
        out.set(JAVA_INT, 8, NativeLayouts.BLACKBOX_V2_MAGIC);
        out.set(JAVA_INT, 12, 160);
        out.set(JAVA_INT, 16, stats.capacityRecords());
        out.set(JAVA_INT, 24, stats.recordCount());
        out.set(JAVA_LONG, 32, stats.nextSequence());
        out.set(JAVA_LONG, 40, stats.overwrittenRecords());
        return NativeStatus.OK;
    }

    @Override
    public int blackboxAppendV2(long handle, int type, MemorySegment payload, int size, MemorySegment outSequence) {
        InMemoryBlackboxRing ring = rings.get(handle);
        if (ring == null) {
            return NativeStatus.INVALID_ARGUMENT;
        }
        long sequence = ring.append(type, payload.asSlice(0, size).toArray(JAVA_BYTE));
        outSequence.set(JAVA_LONG, 0, sequence);
        return NativeStatus.OK;
    }

    @Override
    public int blackboxReadV2(long handle, long from, MemorySegment out, int capacity, MemorySegment required) {
        InMemoryBlackboxRing ring = rings.get(handle);
        if (ring == null) {
            return NativeStatus.INVALID_ARGUMENT;
        }
        List<BlackboxRing.RingRecord> records = ring.read(from);
        required.set(JAVA_INT, 0, records.size());
        if (capacity < records.size()) {
            return NativeStatus.BUFFER_TOO_SMALL;
        }
        for (int i = 0; i < records.size(); i++) {
            MemorySegment record = out.asSlice(i * 160L, 160);
            BlackboxRing.RingRecord source = records.get(i);
            record.fill((byte) 0);
            record.set(JAVA_INT, 0, 160);
            record.set(JAVA_INT, 4, 2);
            record.set(JAVA_INT, 8, source.recordType());
            record.set(JAVA_INT, 12, source.payload().length);
            record.set(JAVA_LONG, 16, source.sequence());
            record.set(JAVA_LONG, 24, source.monotonicNanos());
            MemorySegment.copy(source.payload(), 0, record, JAVA_BYTE, 32, source.payload().length);
        }
        return NativeStatus.OK;
    }

    @Override
    public int blackboxCloseV2(long handle) {
        blackboxCloseCalls++;
        return rings.remove(handle) == null ? NativeStatus.INVALID_ARGUMENT : NativeStatus.OK;
    }
}
