package com.mace.infrastructure.nativebridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Verificación previa al empaquetado: carga la DLL indicada con el mismo código que usará la
 * aplicación (hash, {@code CP_GetAbiInfo}, requisitos de ABI y los 24 símbolos) y hace una llamada
 * V2 que no requiere inicializar sensores. No inicializa telemetría ni toca procesos.
 *
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -cp mace-app.jar \
 *      com.mace.infrastructure.nativebridge.NativeAbiVerifier &lt;mace_native.dll&gt; [sha256]
 * </pre>
 *
 * <p>Código de salida 0 si la DLL es compatible; 2 si no lo es; 64 si faltan argumentos.</p>
 */
public final class NativeAbiVerifier {

    private NativeAbiVerifier() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Uso: NativeAbiVerifier <mace_native.dll> [sha256-esperado]");
            System.exit(64);
        }
        Optional<String> expected = args.length == 2 ? Optional.of(args[1]) : Optional.empty();
        try (NativeFunctions functions = NativeFunctions.load(Path.of(args[0]), expected)) {
            AbiInfo abi = functions.abiInfo();
            int memoryStatus;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment memory = arena.allocate(NativeLayouts.SYSTEM_MEMORY);
                memoryStatus = functions.pollSystemMemoryV2(memory, (int) NativeLayouts.SYSTEM_MEMORY_SIZE);
                if (memoryStatus == NativeStatus.OK) {
                    V2Records.decodeSystemMemory(memory);
                }
            }
            System.out.println("DLL=" + functions.libraryPath());
            System.out.println("SHA256=" + functions.sha256());
            System.out.println("ABI=" + abi.abiVersion());
            System.out.println("BUILD=" + abi.buildVersion());
            System.out.println("CAPABILITIES=0x" + Long.toHexString(abi.capabilities()));
            System.out.println("CP_PollSystemMemoryV2=" + NativeStatus.name(memoryStatus));
            if (memoryStatus != NativeStatus.OK) {
                System.exit(2);
            }
        } catch (NativeLibraryException e) {
            System.err.println("DLL incompatible: " + e.getMessage());
            System.exit(2);
        }
    }
}
