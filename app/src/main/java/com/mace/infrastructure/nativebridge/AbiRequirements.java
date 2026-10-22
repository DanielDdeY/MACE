package com.mace.infrastructure.nativebridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Requisitos que la DLL debe cumplir antes de usar cualquier función V2. Se comprueban con el
 * resultado de {@code CP_GetAbiInfo}, que se llama antes de inicializar telemetría.
 *
 * <p>No se exige un número de parche concreto: la DLL exacta se identifica por su SHA-256
 * ({@link NativeLibraryIntegrity}).</p>
 */
public final class AbiRequirements {

    private AbiRequirements() {
    }

    public static final int REQUIRED_ABI_MAJOR = 1;
    public static final int MINIMUM_ABI_MINOR = 4;

    /**
     * Funciones que el puente Java usa: uso de CPU, inventario de GPU, memoria global, identidad,
     * terminación segura, muestras, ventanas y anillo de caja negra. Los sensores físicos
     * (temperatura, potencia, ventilador) no se exigen: su ausencia se muestra por estado.
     */
    public static final long REQUIRED_CAPABILITIES = NativeStatus.CAP_CPU_USAGE
            | NativeStatus.CAP_GPU_INVENTORY
            | NativeStatus.CAP_SYSTEM_MEMORY
            | NativeStatus.CAP_PROCESS_IDENTITY
            | NativeStatus.CAP_SAFE_TERMINATION
            | NativeStatus.CAP_PROCESS_SAMPLES
            | NativeStatus.CAP_WINDOW_RESPONSIVENESS
            | NativeStatus.CAP_BLACKBOX_BUFFER;

    /** @return lista de incompatibilidades; vacía si la DLL cumple el contrato */
    public static List<String> problems(AbiInfo info) {
        Objects.requireNonNull(info, "info no puede ser null");
        List<String> problems = new ArrayList<>();
        expect(problems, "struct_size de MaceAbiInfoV2", NativeLayouts.ABI_INFO_SIZE, info.structSize());
        expect(problems, "versión de estructura", NativeLayouts.V2_VERSION, info.version());
        expect(problems, "abi_major", REQUIRED_ABI_MAJOR, info.abiMajor());
        if (Integer.compareUnsigned(info.abiMinor(), MINIMUM_ABI_MINOR) < 0) {
            problems.add("abi_minor " + Integer.toUnsignedString(info.abiMinor())
                    + " < " + MINIMUM_ABI_MINOR + " (DLL anterior al contrato V2 1.4)");
        }
        expect(problems, "pointer_size", 8, info.pointerSize());
        expect(problems, "wchar_size", 2, info.wcharSize());
        expect(problems, "sizeof(ProcessData)", NativeLayouts.PROCESS_DATA_SIZE, info.processDataSize());
        expect(problems, "sizeof(TelemetryData)", NativeLayouts.TELEMETRY_DATA_SIZE, info.telemetryDataSize());
        expect(problems, "sizeof(BlackboxSharedHeader)", NativeLayouts.BLACKBOX_SHARED_HEADER_SIZE,
                info.blackboxHeaderSize());
        long missing = REQUIRED_CAPABILITIES & ~info.capabilities();
        if (missing != 0) {
            problems.add("faltan capacidades implementadas: 0x" + Long.toHexString(missing));
        }
        return List.copyOf(problems);
    }

    /** @throws NativeLibraryException con todas las incompatibilidades si las hay */
    public static void verify(AbiInfo info, String libraryDescription) {
        List<String> problems = problems(info);
        if (!problems.isEmpty()) {
            throw new NativeLibraryException("La biblioteca nativa " + libraryDescription
                    + " es incompatible con MACE (ABI " + info.abiVersion() + ", DLL " + info.buildVersion()
                    + "): " + String.join("; ", problems)
                    + ". Usa la mace_native.dll 1.4.x verificada por Dev 1.");
        }
    }

    private static void expect(List<String> problems, String field, long expected, int actual) {
        if (Integer.toUnsignedLong(actual) != expected) {
            problems.add(field + " = " + Integer.toUnsignedString(actual) + " (se esperaba " + expected + ")");
        }
    }
}
