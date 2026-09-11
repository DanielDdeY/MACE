package com.corepulse.infrastructure.nativebridge;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Enlace FFM (Project Panama, JEP 454) con las funciones exportadas por la DLL nativa,
 * declaradas en {@code native/include/mace_api.h}:
 *
 * <pre>
 * int   CP_InitializeTelemetry(void);
 * void  CP_ShutdownTelemetry(void);
 * int   CP_PollTelemetry(TelemetryData* out_data);
 * int   CP_EnumerateUserlandProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count);
 * int   CP_TerminateProcess(uint32_t pid);
 * int   CP_CheckProcessResponsiveness(uint32_t pid);
 * void* CP_InitSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size);
 * </pre>
 *
 * <p>Una instancia posee la biblioteca cargada (mediante un {@link Arena} compartido que
 * gobierna su ciclo de vida) y los {@link MethodHandle} de bajada. Los handles son inmutables
 * y seguros para uso concurrente; la memoria fuera del heap que cada llamada necesita la
 * aporta el invocador, normalmente con un {@code Arena.ofConfined()} por llamada
 * (ver {@link FfmSensorsAdapter} y {@link FfmProcessAdapter}).</p>
 *
 * <p>Ejecutar la JVM con {@code --enable-native-access=ALL-UNNAMED} para suprimir las
 * advertencias de acceso restringido de la FFM API (JDK 22+).</p>
 */
public final class NativeFunctions implements AutoCloseable {

    static final String SYMBOL_INITIALIZE_TELEMETRY = "CP_InitializeTelemetry";
    static final String SYMBOL_SHUTDOWN_TELEMETRY = "CP_ShutdownTelemetry";
    static final String SYMBOL_POLL_TELEMETRY = "CP_PollTelemetry";
    static final String SYMBOL_ENUMERATE_PROCESSES = "CP_EnumerateUserlandProcesses";
    static final String SYMBOL_TERMINATE_PROCESS = "CP_TerminateProcess";
    static final String SYMBOL_CHECK_RESPONSIVENESS = "CP_CheckProcessResponsiveness";
    static final String SYMBOL_INIT_SHARED_BUFFER = "CP_InitSharedTelemetryBuffer";

    private static final Linker LINKER = Linker.nativeLinker();

    private final Arena libraryArena;
    private final Path libraryPath;

    private final MethodHandle initializeTelemetryHandle;
    private final MethodHandle shutdownTelemetryHandle;
    private final MethodHandle pollTelemetryHandle;
    private final MethodHandle enumerateProcessesHandle;
    private final MethodHandle terminateProcessHandle;
    private final MethodHandle checkResponsivenessHandle;
    private final MethodHandle initSharedBufferHandle;

    private final Object telemetryLock = new Object();
    private final AtomicBoolean telemetryInitialized = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private NativeFunctions(Arena libraryArena, Path libraryPath, SymbolLookup lookup) {
        this.libraryArena = libraryArena;
        this.libraryPath = libraryPath;
        this.initializeTelemetryHandle = downcall(lookup, SYMBOL_INITIALIZE_TELEMETRY,
                FunctionDescriptor.of(JAVA_INT));
        this.shutdownTelemetryHandle = downcall(lookup, SYMBOL_SHUTDOWN_TELEMETRY,
                FunctionDescriptor.ofVoid());
        this.pollTelemetryHandle = downcall(lookup, SYMBOL_POLL_TELEMETRY,
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        this.enumerateProcessesHandle = downcall(lookup, SYMBOL_ENUMERATE_PROCESSES,
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
        this.terminateProcessHandle = downcall(lookup, SYMBOL_TERMINATE_PROCESS,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));
        this.checkResponsivenessHandle = downcall(lookup, SYMBOL_CHECK_RESPONSIVENESS,
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));
        this.initSharedBufferHandle = downcall(lookup, SYMBOL_INIT_SHARED_BUFFER,
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    }

    // ------------------------------------------------------------------
    // Carga
    // ------------------------------------------------------------------

    /**
     * Localiza la DLL con {@link NativeLibraryLocator} y la carga.
     *
     * @throws NativeLibraryException si no se encuentra, no se puede cargar o no exporta
     *                                todos los símbolos esperados
     */
    public static NativeFunctions load() {
        Path path = NativeLibraryLocator.locate()
                .orElseThrow(() -> new NativeLibraryException(NativeLibraryLocator.notFoundMessage()));
        return load(path);
    }

    /**
     * Carga la DLL ubicada en {@code libraryPath}.
     *
     * @throws NativeLibraryException si el archivo no existe, no se puede cargar o no exporta
     *                                todos los símbolos esperados
     */
    @SuppressWarnings("restricted") // SymbolLookup.libraryLookup es un método restringido de FFM
    public static NativeFunctions load(Path libraryPath) {
        Objects.requireNonNull(libraryPath, "libraryPath no puede ser null");
        Path absolute = libraryPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new NativeLibraryException("No existe la biblioteca nativa: " + absolute);
        }
        Arena arena = Arena.ofShared();
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(absolute, arena);
            return new NativeFunctions(arena, absolute, lookup);
        } catch (RuntimeException e) {
            arena.close();
            if (e instanceof NativeLibraryException nativeError) {
                throw nativeError;
            }
            throw new NativeLibraryException(
                    "No se pudo cargar la biblioteca nativa '" + absolute + "': " + e.getMessage(), e);
        }
    }

    /** @return {@code true} si {@link NativeLibraryLocator} encuentra la DLL en disco. */
    public static boolean isAvailable() {
        return NativeLibraryLocator.locate().isPresent();
    }

    /** Ruta absoluta de la DLL cargada. */
    public Path libraryPath() {
        return libraryPath;
    }

    @SuppressWarnings("restricted") // Linker.downcallHandle es un método restringido de FFM
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = lookup.find(symbol).orElseThrow(() -> new NativeLibraryException(
                "La biblioteca nativa no exporta el símbolo '" + symbol
                        + "'. Verifica que se compiló con MACE_EXPORTS y la misma versión de mace_api.h."));
        return LINKER.downcallHandle(address, descriptor);
    }

    // ------------------------------------------------------------------
    // Telemetría
    // ------------------------------------------------------------------

    /** {@code int CP_InitializeTelemetry(void)}: inicializa NVML (si existe). Devuelve 0 en éxito. */
    public int initializeTelemetry() {
        ensureOpen();
        try {
            int result = (int) initializeTelemetryHandle.invokeExact();
            telemetryInitialized.set(true);
            return result;
        } catch (Throwable t) {
            throw failure(SYMBOL_INITIALIZE_TELEMETRY, t);
        }
    }

    /**
     * Garantiza una única inicialización de telemetría aunque varios adaptadores compartan
     * esta instancia.
     *
     * @return código devuelto por la DLL en la primera inicialización, o 0 si ya estaba lista
     */
    public int ensureTelemetryInitialized() {
        if (telemetryInitialized.get()) {
            return 0;
        }
        synchronized (telemetryLock) {
            if (telemetryInitialized.get()) {
                return 0;
            }
            return initializeTelemetry();
        }
    }

    /** {@code void CP_ShutdownTelemetry(void)}: libera NVML. */
    public void shutdownTelemetry() {
        ensureOpen();
        try {
            shutdownTelemetryHandle.invokeExact();
            telemetryInitialized.set(false);
        } catch (Throwable t) {
            throw failure(SYMBOL_SHUTDOWN_TELEMETRY, t);
        }
    }

    /**
     * {@code int CP_PollTelemetry(TelemetryData* out_data)}.
     *
     * @param outData segmento de al menos {@link NativeLayouts#TELEMETRY_DATA_SIZE} bytes
     * @return 0 en éxito, -1 si el puntero es nulo
     */
    public int pollTelemetry(MemorySegment outData) {
        ensureOpen();
        requireCapacity(outData, NativeLayouts.TELEMETRY_DATA_SIZE, "outData");
        try {
            return (int) pollTelemetryHandle.invokeExact(outData);
        } catch (Throwable t) {
            throw failure(SYMBOL_POLL_TELEMETRY, t);
        }
    }

    // ------------------------------------------------------------------
    // Procesos
    // ------------------------------------------------------------------

    /**
     * {@code int CP_EnumerateUserlandProcesses(ProcessData* out_buffer, uint32_t max_count, uint32_t* out_count)}.
     *
     * @param outBuffer segmento con capacidad para {@code maxCount} registros {@code ProcessData}
     * @param maxCount  cantidad máxima de registros que la DLL puede escribir
     * @param outCount  segmento de 4 bytes donde la DLL escribe la cantidad real
     * @return 0 en éxito o un código de error Win32
     */
    public int enumerateUserlandProcesses(MemorySegment outBuffer, int maxCount, MemorySegment outCount) {
        ensureOpen();
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount debe ser positivo: " + maxCount);
        }
        requireCapacity(outBuffer, NativeLayouts.PROCESS_DATA_SIZE * maxCount, "outBuffer");
        requireCapacity(outCount, JAVA_INT.byteSize(), "outCount");
        try {
            return (int) enumerateProcessesHandle.invokeExact(outBuffer, maxCount, outCount);
        } catch (Throwable t) {
            throw failure(SYMBOL_ENUMERATE_PROCESSES, t);
        }
    }

    /**
     * {@code int CP_TerminateProcess(uint32_t pid)}. Solo debe invocarse como consecuencia
     * directa de una acción manual del usuario.
     *
     * @return 0 en éxito, -1 si el PID está protegido, o un código de error Win32
     */
    public int terminateProcess(int pid) {
        ensureOpen();
        try {
            return (int) terminateProcessHandle.invokeExact(pid);
        } catch (Throwable t) {
            throw failure(SYMBOL_TERMINATE_PROCESS, t);
        }
    }

    /**
     * {@code int CP_CheckProcessResponsiveness(uint32_t pid)}.
     *
     * @return 1 si la ventana principal del proceso no responde ({@code IsHungAppWindow}), 0 en caso contrario
     */
    public int checkProcessResponsiveness(int pid) {
        ensureOpen();
        try {
            return (int) checkResponsivenessHandle.invokeExact(pid);
        } catch (Throwable t) {
            throw failure(SYMBOL_CHECK_RESPONSIVENESS, t);
        }
    }

    // ------------------------------------------------------------------
    // Extensiones (caja negra en memoria compartida)
    // ------------------------------------------------------------------

    /**
     * {@code void* CP_InitSharedTelemetryBuffer(const wchar_t* mapping_name, uint32_t buffer_size)}.
     *
     * @param mappingName cadena {@code wchar_t} terminada en NUL (ver {@link WideStrings#allocate})
     * @param bufferSize  tamaño del mapeo en bytes
     * @return puntero de tamaño cero (usar {@link MemorySegment#reinterpret(long)} para leerlo)
     *         o {@link MemorySegment#NULL} si la DLL no pudo crear el mapeo
     */
    public MemorySegment initSharedTelemetryBuffer(MemorySegment mappingName, int bufferSize) {
        ensureOpen();
        Objects.requireNonNull(mappingName, "mappingName no puede ser null");
        try {
            return (MemorySegment) initSharedBufferHandle.invokeExact(mappingName, bufferSize);
        } catch (Throwable t) {
            throw failure(SYMBOL_INIT_SHARED_BUFFER, t);
        }
    }

    /**
     * Variante de conveniencia: crea el mapeo con nombre {@code mappingName} y devuelve un
     * segmento ya redimensionado a {@code bufferSize} bytes, o {@link MemorySegment#NULL}.
     *
     * @param arena arena temporal para la cadena del nombre (el mapeo sobrevive a la arena)
     */
    @SuppressWarnings("restricted") // MemorySegment.reinterpret es un método restringido de FFM
    public MemorySegment initSharedTelemetryBuffer(Arena arena, String mappingName, int bufferSize) {
        Objects.requireNonNull(arena, "arena no puede ser null");
        Objects.requireNonNull(mappingName, "mappingName no puede ser null");
        if (bufferSize < NativeLayouts.BLACKBOX_SHARED_HEADER_SIZE) {
            throw new IllegalArgumentException("bufferSize debe ser >= " + NativeLayouts.BLACKBOX_SHARED_HEADER_SIZE);
        }
        MemorySegment pointer = initSharedTelemetryBuffer(WideStrings.allocate(arena, mappingName), bufferSize);
        if (pointer.address() == 0L) {
            return MemorySegment.NULL;
        }
        return pointer.reinterpret(bufferSize);
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Apaga la telemetría (si estaba inicializada) y descarga la DLL. Idempotente.
     * Ninguna función nativa puede invocarse después de cerrar.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (telemetryInitialized.getAndSet(false)) {
                shutdownTelemetryHandle.invokeExact();
            }
        } catch (Throwable ignored) {
            // La DLL se descarga de todos modos; no hay nada más que hacer con el error.
        } finally {
            libraryArena.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("NativeFunctions ya fue cerrado; la biblioteca nativa se descargó");
        }
    }

    private static void requireCapacity(MemorySegment segment, long minimumBytes, String name) {
        Objects.requireNonNull(segment, name + " no puede ser null");
        if (segment.byteSize() < minimumBytes) {
            throw new IllegalArgumentException(
                    name + " necesita al menos " + minimumBytes + " bytes, tiene " + segment.byteSize());
        }
    }

    private static NativeLibraryException failure(String symbol, Throwable cause) {
        return new NativeLibraryException("Fallo al invocar " + symbol + ": " + cause, cause);
    }
}
