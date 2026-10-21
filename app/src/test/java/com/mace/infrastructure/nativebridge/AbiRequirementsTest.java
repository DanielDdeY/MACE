package com.mace.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbiRequirementsTest {

    private static AbiInfo abi(int structSize, int version, int major, int minor, int processSize, int telemetrySize,
                               int blackboxSize, int wchar, int pointer, long capabilities) {
        return new AbiInfo(structSize, version, major, minor, processSize, telemetrySize, blackboxSize, wchar, pointer,
                0, capabilities, 1, minor, 2, 0);
    }

    private static AbiInfo valid() {
        return abi(64, 2, 1, 4, 1584, 28, 16, 2, 8, 0xFFFFL);
    }

    @Test
    void laDllDeReferenciaEsCompatible() {
        assertTrue(AbiRequirements.problems(valid()).isEmpty());
    }

    @Test
    void unaVersionMenorPosteriorEsCompatibleYElParcheNoSeExige() {
        AbiInfo future = new AbiInfo(64, 2, 1, 5, 1584, 28, 16, 2, 8, 0, 0xFFFFL, 1, 5, 9, 0);
        assertTrue(AbiRequirements.problems(future).isEmpty());
    }

    @Test
    void detectaCadaIncompatibilidad() {
        assertProblem(abi(64, 2, 2, 4, 1584, 28, 16, 2, 8, 0xFFFFL), "abi_major");
        assertProblem(abi(64, 2, 1, 3, 1584, 28, 16, 2, 8, 0xFFFFL), "abi_minor");
        assertProblem(abi(64, 3, 1, 4, 1584, 28, 16, 2, 8, 0xFFFFL), "versión");
        assertProblem(abi(60, 2, 1, 4, 1584, 28, 16, 2, 8, 0xFFFFL), "struct_size");
        assertProblem(abi(64, 2, 1, 4, 1584, 28, 16, 2, 4, 0xFFFFL), "pointer_size");
        assertProblem(abi(64, 2, 1, 4, 1584, 28, 16, 4, 8, 0xFFFFL), "wchar_size");
        assertProblem(abi(64, 2, 1, 4, 1580, 28, 16, 2, 8, 0xFFFFL), "ProcessData");
        assertProblem(abi(64, 2, 1, 4, 1584, 24, 16, 2, 8, 0xFFFFL), "TelemetryData");
        assertProblem(abi(64, 2, 1, 4, 1584, 28, 12, 2, 8, 0xFFFFL), "BlackboxSharedHeader");
        assertProblem(abi(64, 2, 1, 4, 1584, 28, 16, 2, 8, 0xFFFFL & ~NativeStatus.CAP_BLACKBOX_BUFFER), "capacidades");
    }

    @Test
    void lasCapacidadesDeSensoresFisicosNoSonObligatorias() {
        long sinSensores = AbiRequirements.REQUIRED_CAPABILITIES;
        assertTrue(AbiRequirements.problems(abi(64, 2, 1, 4, 1584, 28, 16, 2, 8, sinSensores)).isEmpty());
    }

    @Test
    void verifyLanzaConTodasLasIncompatibilidades() {
        AbiInfo old = abi(64, 2, 1, 3, 1584, 28, 16, 2, 4, 0);
        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> AbiRequirements.verify(old, "prueba.dll"));
        assertTrue(error.getMessage().contains("abi_minor"));
        assertTrue(error.getMessage().contains("pointer_size"));
        assertTrue(error.getMessage().contains("prueba.dll"));
    }

    @Test
    void decodificaLosSesentaYCuatroBytesDeCpGetAbiInfo() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = arena.allocate(64, 8);
            int[] ints = {64, 2, 1, 4, 1584, 28, 16, 2, 8, 0};
            for (int i = 0; i < ints.length; i++) {
                record.set(JAVA_INT, i * 4L, ints[i]);
            }
            record.set(JAVA_LONG, 40, 0xFFFFL);
            record.set(JAVA_INT, 48, 1);
            record.set(JAVA_INT, 52, 4);
            record.set(JAVA_INT, 56, 2);

            AbiInfo info = AbiInfo.read(record);

            assertEquals("1.4.2", info.buildVersion());
            assertEquals("1.4", info.abiVersion());
            assertTrue(info.hasCapability(NativeStatus.CAP_WINDOW_RESPONSIVENESS));
            assertTrue(AbiRequirements.problems(info).isEmpty());
        }
    }

    private static void assertProblem(AbiInfo info, String fragment) {
        List<String> problems = AbiRequirements.problems(info);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(fragment)),
                "se esperaba un problema con '" + fragment + "': " + problems);
    }
}
