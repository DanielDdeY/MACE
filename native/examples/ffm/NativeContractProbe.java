import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/** Prueba independiente de app/: requiere JDK 22 y la ruta de la DLL concreta. */
public class NativeContractProbe {
    private static final int ABI_INFO_SIZE = 64;
    private static final int BUFFER_TOO_SMALL = -2;

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            throw new IllegalArgumentException("Uso: java NativeContractProbe.java <mace_native.dll>");
        }
        Path library = Path.of(args[0]).toAbsolutePath().normalize();

        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup lookup = SymbolLookup.libraryLookup(library, arena);
            MethodHandle getAbiInfo = Linker.nativeLinker().downcallHandle(
                    lookup.find("CP_GetAbiInfo").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            MemorySegment required = arena.allocate(ValueLayout.JAVA_INT);
            int probe = (int) getAbiInfo.invokeExact(MemorySegment.NULL, 0, required);
            int requiredSize = required.get(ValueLayout.JAVA_INT, 0);
            if (probe != BUFFER_TOO_SMALL || requiredSize != ABI_INFO_SIZE) {
                throw new AssertionError("Consulta de tamaño ABI incorrecta");
            }

            MemorySegment record = arena.allocate(ABI_INFO_SIZE, 8);
            record.fill((byte) 0xA5);
            int result = (int) getAbiInfo.invokeExact(record, ABI_INFO_SIZE, required);
            if (result != 0 || required.get(ValueLayout.JAVA_INT, 0) != ABI_INFO_SIZE) {
                throw new AssertionError("Consulta ABI falló: " + result);
            }

            int size = record.get(ValueLayout.JAVA_INT, 0);
            int recordVersion = record.get(ValueLayout.JAVA_INT, 4);
            int abiMajor = record.get(ValueLayout.JAVA_INT, 8);
            int abiMinor = record.get(ValueLayout.JAVA_INT, 12);
            int processSize = record.get(ValueLayout.JAVA_INT, 16);
            int telemetrySize = record.get(ValueLayout.JAVA_INT, 20);
            int blackboxSize = record.get(ValueLayout.JAVA_INT, 24);
            int wcharSize = record.get(ValueLayout.JAVA_INT, 28);
            int pointerSize = record.get(ValueLayout.JAVA_INT, 32);
            long capabilities = record.get(ValueLayout.JAVA_LONG, 40);
            long requiredCapabilities = (1L << 0) | (1L << 3) | (1L << 14) | (1L << 15);
            int buildMajor = record.get(ValueLayout.JAVA_INT, 48);
            int buildMinor = record.get(ValueLayout.JAVA_INT, 52);
            int buildPatch = record.get(ValueLayout.JAVA_INT, 56);

            if (size != 64 || recordVersion != 2 || abiMajor != 1 || abiMinor != 2 ||
                    processSize != 1584 || telemetrySize != 28 || blackboxSize != 16 ||
                    wcharSize != 2 || pointerSize != 8 ||
                    (capabilities & requiredCapabilities) != requiredCapabilities ||
                    buildMajor != 1 || buildMinor != 2 || buildPatch != 0) {
                throw new AssertionError("La DLL no coincide con el contrato FFM esperado");
            }
            System.out.printf("FFM OK: ABI %d.%d, DLL %d.%d.%d, layouts %d/%d/%d, %s%n",
                    abiMajor, abiMinor, buildMajor, buildMinor, buildPatch,
                    processSize, telemetrySize, blackboxSize, library);
        }
    }
}
