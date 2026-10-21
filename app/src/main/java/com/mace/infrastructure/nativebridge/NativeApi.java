package com.mace.infrastructure.nativebridge;

import java.lang.foreign.MemorySegment;

/**
 * Funciones V2 de {@code native/include/mace_api.h} que usan los adaptadores, con la misma
 * semántica de C: capacidades en bytes para registros individuales, en registros para arreglos
 * y en caracteres UTF-16 (incluido NUL) para rutas; un buffer corto nunca recibe escritura
 * parcial; los retornos son {@code MaceStatus} ({@link NativeStatus}).
 *
 * <p>{@link NativeFunctions} la implementa sobre la DLL real. Las pruebas usan una
 * implementación en Java que reproduce los casos límite (conteos que cambian, buffers cortos,
 * GPU que desaparece...) sin necesitar Windows.</p>
 */
public interface NativeApi {

    /** Resultado de {@code CP_GetAbiInfo} ya verificado. */
    AbiInfo abiInfo();

    /**
     * Inicializa la telemetría una sola vez aunque varios adaptadores compartan la instancia.
     *
     * @return código de {@code CP_InitializeTelemetry} (0 aunque falten proveedores opcionales)
     */
    int ensureTelemetryInitialized();

    int enumerateGpuAdapters(MemorySegment outAdapters, int capacity, MemorySegment outCount);

    int pollCpuTelemetryV2(MemorySegment outData, int capacityBytes);

    int pollGpuTelemetryV2(MemorySegment luid, MemorySegment outData, int capacityBytes);

    int pollSystemMemoryV2(MemorySegment outData, int capacityBytes);

    int enumerateUserlandProcessesV2(MemorySegment outEntries, int capacity, MemorySegment outRequiredCount,
                                     MemorySegment outSkippedCount, MemorySegment outWin32Error);

    int getProcessIdentityV2(int pid, MemorySegment outIdentity, int capacityBytes, MemorySegment outWin32Error);

    int getProcessImagePathV2(MemorySegment identity, MemorySegment outPath, int capacityChars,
                              MemorySegment outRequiredChars, MemorySegment outWin32Error);

    int terminateProcessV2(MemorySegment identity, MemorySegment outWin32Error);

    int sampleProcessV2(MemorySegment identity, MemorySegment outSample, int capacityBytes, MemorySegment outWin32Error);

    int checkProcessResponsivenessV2(MemorySegment identity, MemorySegment outResult, int capacityBytes);

    int blackboxCreateV2(MemorySegment mappingName, int capacityRecords, MemorySegment outHandle,
                         MemorySegment outWin32Error);

    int blackboxGetStatsV2(long handle, MemorySegment outHeader, int capacityBytes);

    int blackboxAppendV2(long handle, int recordType, MemorySegment payload, int payloadSize, MemorySegment outSequence);

    int blackboxReadV2(long handle, long fromSequence, MemorySegment outRecords, int capacityRecords,
                       MemorySegment outRequiredCount);

    int blackboxCloseV2(long handle);
}
