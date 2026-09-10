package com.corepulse.infrastructure.nativebridge;

import com.corepulse.domain.model.ProcessEntry;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba la decodificación de registros {@code ProcessData} construidos en memoria nativa
 * con el mismo layout que escribe {@code process_enumerator.c}; no requiere la DLL.
 */
class FfmProcessAdapterTest {

    private static final long MIB = 1024L * 1024L;

    @Test
    void decodificaUnRegistroProcessData() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = newRecord(arena, 4120, "chrome.exe",
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", 512L * MIB, 380L * MIB);

            ProcessEntry entry = FfmProcessAdapter.toProcessEntry(record).orElseThrow();

            assertEquals(4120L, entry.pid().value());
            assertEquals("chrome.exe", entry.name());
            assertEquals("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", entry.fullPath());
            assertEquals(512L * MIB, entry.memory().workingSetBytes());
            assertEquals(380L * MIB, entry.memory().privateBytes());
        }
    }

    @Test
    void elPidSeInterpretaComoUint32() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = newRecord(arena, 0xFFFF_FFF0, "app.exe", "C:\\app.exe", 1L, 1L);

            ProcessEntry entry = FfmProcessAdapter.toProcessEntry(record).orElseThrow();

            assertEquals(4_294_967_280L, entry.pid().value());
        }
    }

    @Test
    void memoriaConBitAltoSeSaturaEnLugarDeSerNegativa() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = newRecord(arena, 10, "app.exe", "C:\\app.exe", -1L, -2L);

            ProcessEntry entry = FfmProcessAdapter.toProcessEntry(record).orElseThrow();

            assertEquals(Long.MAX_VALUE, entry.memory().workingSetBytes());
            assertEquals(Long.MAX_VALUE, entry.memory().privateBytes());
        }
    }

    @Test
    void registroSinNombreORutaSeDescarta() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sinNombre = newRecord(arena, 10, "", "C:\\app.exe", 1L, 1L);
            MemorySegment sinRuta = newRecord(arena, 11, "app.exe", "", 1L, 1L);

            assertEquals(Optional.empty(), FfmProcessAdapter.toProcessEntry(sinNombre));
            assertEquals(Optional.empty(), FfmProcessAdapter.toProcessEntry(sinRuta));
        }
    }

    @Test
    void decodificaRegistrosContiguosComoLoHaceListUserlandProcesses() {
        try (Arena arena = Arena.ofConfined()) {
            int count = 3;
            MemorySegment buffer = arena.allocate(NativeLayouts.PROCESS_DATA, count);
            for (int i = 0; i < count; i++) {
                MemorySegment slot = buffer.asSlice(i * NativeLayouts.PROCESS_DATA_SIZE, NativeLayouts.PROCESS_DATA_SIZE);
                fillRecord(slot, 100 + i, "proc" + i + ".exe", "C:\\proc" + i + ".exe", (i + 1) * MIB, i * MIB);
            }

            List<ProcessEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                MemorySegment slot = buffer.asSlice(i * NativeLayouts.PROCESS_DATA_SIZE, NativeLayouts.PROCESS_DATA_SIZE);
                FfmProcessAdapter.toProcessEntry(slot).ifPresent(entries::add);
            }

            assertEquals(count, entries.size());
            for (int i = 0; i < count; i++) {
                assertEquals(100L + i, entries.get(i).pid().value());
                assertEquals("proc" + i + ".exe", entries.get(i).name());
                assertEquals((i + 1) * MIB, entries.get(i).memory().workingSetBytes());
            }
        }
    }

    @Test
    void nombreYRutaAlLimiteDeCapacidadSeLeenSinDesbordar() {
        try (Arena arena = Arena.ofConfined()) {
            String longName = "n".repeat(NativeLayouts.PROCESS_NAME_CHARS + 50);
            String longPath = "p".repeat(NativeLayouts.PROCESS_PATH_CHARS + 50);
            MemorySegment record = newRecord(arena, 1, longName, longPath, 1L, 1L);

            ProcessEntry entry = FfmProcessAdapter.toProcessEntry(record).orElseThrow();

            assertEquals(NativeLayouts.PROCESS_NAME_CHARS - 1, entry.name().length());
            assertEquals(NativeLayouts.PROCESS_PATH_CHARS - 1, entry.fullPath().length());
            assertTrue(entry.name().chars().allMatch(c -> c == 'n'));
        }
    }

    @Test
    void elConstructorValidaSusArgumentos() {
        assertThrows(NullPointerException.class, () -> new FfmProcessAdapter(null));
    }

    // ------------------------------------------------------------------

    private static MemorySegment newRecord(Arena arena, int pid, String name, String path,
                                           long workingSet, long privateBytes) {
        MemorySegment record = arena.allocate(NativeLayouts.PROCESS_DATA);
        fillRecord(record, pid, name, path, workingSet, privateBytes);
        return record;
    }

    /** Escribe el registro exactamente como lo hace {@code process_enumerator.c}. */
    private static void fillRecord(MemorySegment record, int pid, String name, String path,
                                   long workingSet, long privateBytes) {
        record.fill((byte) 0);
        record.set(JAVA_INT, NativeLayouts.PROCESS_PID_OFFSET, pid);
        WideStrings.write(record, NativeLayouts.PROCESS_NAME_OFFSET, NativeLayouts.PROCESS_NAME_CHARS, name);
        WideStrings.write(record, NativeLayouts.PROCESS_PATH_OFFSET, NativeLayouts.PROCESS_PATH_CHARS, path);
        record.set(JAVA_LONG, NativeLayouts.PROCESS_WORKING_SET_OFFSET, workingSet);
        record.set(JAVA_LONG, NativeLayouts.PROCESS_PRIVATE_BYTES_OFFSET, privateBytes);
    }
}
