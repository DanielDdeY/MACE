package com.mace.infrastructure;

import com.mace.domain.port.out.BlackboxSinkPort;
import com.mace.domain.port.out.HardwareSensorsPort;
import com.mace.domain.port.out.ProcessLifecyclePort;
import com.mace.infrastructure.mock.MockProcessAdapter;
import com.mace.infrastructure.mock.MockScenario;
import com.mace.infrastructure.mock.MockSensorsAdapter;
import com.mace.infrastructure.nativebridge.AbiInfo;
import com.mace.infrastructure.nativebridge.FfmProcessAdapter;
import com.mace.infrastructure.nativebridge.FfmSensorsAdapter;
import com.mace.infrastructure.nativebridge.NativeBlackboxRing;
import com.mace.infrastructure.nativebridge.NativeFunctions;
import com.mace.infrastructure.nativebridge.NativeLibraryException;
import com.mace.infrastructure.telemetry.BlackboxRing;
import com.mace.infrastructure.telemetry.BlackboxSettings;
import com.mace.infrastructure.telemetry.InMemoryBlackboxRing;
import com.mace.infrastructure.telemetry.NoOpBlackboxSink;
import com.mace.infrastructure.telemetry.PersistentBlackboxSink;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Punto único de composición de la capa de infraestructura: entrega las implementaciones de
 * los puertos de salida ({@link HardwareSensorsPort}, {@link ProcessLifecyclePort},
 * {@link BlackboxSinkPort}) eligiendo entre el puente FFM real y los adaptadores simulados.
 *
 * <p>Modo de selección (propiedad del sistema {@value #MODE_PROPERTY}):</p>
 * <ul>
 *   <li>{@code auto} (por defecto): usa la DLL si está en Windows, se localiza y su ABI es
 *       compatible; si no, cae a los mocks y lo informa en {@link #fallbackReason()} para que la
 *       UI muestre «Datos simulados».</li>
 *   <li>{@code native}: exige la DLL; lanza {@link NativeLibraryException} si falta, no coincide
 *       con el hash esperado o es incompatible.</li>
 *   <li>{@code mock}: siempre simulado; <strong>nunca</strong> carga la DLL ni requiere
 *       administrador. Escenario con {@code -Dmace.mock.scenario}.</li>
 * </ul>
 *
 * <p>Orden de cierre ({@link #close()}): primero la caja negra (vacía el anillo y cierra el token
 * V2) y después la telemetría nativa (una sola llamada a {@code CP_ShutdownTelemetry}). La DLL
 * sigue cargada hasta el fin del proceso. La composición debe detener antes el monitor y los
 * suscriptores de la UI.</p>
 */
public final class InfrastructureAdapters implements AutoCloseable {

    /** Propiedad del sistema que fija el modo: {@code auto}, {@code native} o {@code mock}. */
    public static final String MODE_PROPERTY = "mace.infra.mode";

    private static final Logger LOG = System.getLogger(InfrastructureAdapters.class.getName());

    public enum Mode {
        AUTO,
        NATIVE,
        MOCK;

        /** Lee {@value #MODE_PROPERTY}; valores desconocidos o ausentes equivalen a {@link #AUTO}. */
        public static Mode fromSystemProperty() {
            String raw = System.getProperty(MODE_PROPERTY, "auto").trim();
            try {
                return Mode.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Valor desconocido para -D{0}={1}; se usa AUTO", MODE_PROPERTY, raw);
                return AUTO;
            }
        }
    }

    private final HardwareSensorsPort sensors;
    private final ProcessLifecyclePort processes;
    private final NativeFunctions nativeFunctions;
    private final MockScenario mockScenario;
    private final String fallbackReason;

    private BlackboxSinkPort blackbox;
    private String blackboxFailure;
    private boolean closed;

    private InfrastructureAdapters(HardwareSensorsPort sensors, ProcessLifecyclePort processes,
                                   NativeFunctions nativeFunctions, MockScenario mockScenario, String fallbackReason) {
        this.sensors = Objects.requireNonNull(sensors, "sensors no puede ser null");
        this.processes = Objects.requireNonNull(processes, "processes no puede ser null");
        this.nativeFunctions = nativeFunctions;
        this.mockScenario = mockScenario;
        this.fallbackReason = fallbackReason;
    }

    /** Crea los adaptadores según {@link Mode#fromSystemProperty()}. */
    public static InfrastructureAdapters create() {
        return create(Mode.fromSystemProperty());
    }

    public static InfrastructureAdapters create(Mode mode) {
        Objects.requireNonNull(mode, "mode no puede ser null");
        return switch (mode) {
            case MOCK -> mock();
            case NATIVE -> nativeOrThrow();
            case AUTO -> autoDetect();
        };
    }

    /** Adaptadores simulados con el escenario y la semilla de las propiedades del sistema. */
    public static InfrastructureAdapters mock() {
        return mock(MockScenario.fromSystemProperty(), MockScenario.seedFromSystemProperty());
    }

    /** Adaptadores simulados; no requieren Windows ni la DLL. */
    public static InfrastructureAdapters mock(MockScenario scenario, long seed) {
        return mock(scenario, seed, null);
    }

    private static InfrastructureAdapters mock(MockScenario scenario, long seed, String fallbackReason) {
        Objects.requireNonNull(scenario, "scenario no puede ser null");
        return new InfrastructureAdapters(
                new MockSensorsAdapter(scenario, seed, Clock.systemUTC(), System::nanoTime),
                new MockProcessAdapter(seed),
                null, scenario, fallbackReason);
    }

    /**
     * Adaptadores FFM sobre la DLL nativa (ABI verificado antes de inicializar telemetría).
     *
     * @throws NativeLibraryException si la DLL no se encuentra, no carga, no es la esperada,
     *                                es incompatible o no exporta los símbolos
     */
    public static InfrastructureAdapters nativeOrThrow() {
        return nativeFrom(NativeFunctions.load());
    }

    /** Adaptadores FFM sobre una DLL concreta. */
    public static InfrastructureAdapters nativeFrom(Path libraryPath) {
        return nativeFrom(NativeFunctions.load(libraryPath));
    }

    private static InfrastructureAdapters nativeFrom(NativeFunctions functions) {
        try {
            return new InfrastructureAdapters(
                    new FfmSensorsAdapter(functions),
                    new FfmProcessAdapter(functions),
                    functions, null, null);
        } catch (RuntimeException e) {
            functions.close();
            throw e;
        }
    }

    private static InfrastructureAdapters autoDetect() {
        if (!isWindows()) {
            String reason = "Sistema operativo no Windows";
            LOG.log(Level.INFO, "{0}: se usan adaptadores simulados", reason);
            return mock(MockScenario.fromSystemProperty(), MockScenario.seedFromSystemProperty(), reason);
        }
        try {
            InfrastructureAdapters adapters = nativeOrThrow();
            LOG.log(Level.INFO, "Puente FFM activo con {0}", adapters.nativeFunctions.libraryPath());
            return adapters;
        } catch (NativeLibraryException e) {
            LOG.log(Level.WARNING, "Biblioteca nativa no disponible; se usan adaptadores simulados. Motivo: {0}",
                    e.getMessage());
            return mock(MockScenario.fromSystemProperty(), MockScenario.seedFromSystemProperty(), e.getMessage());
        }
    }

    public HardwareSensorsPort sensors() {
        return sensors;
    }

    public ProcessLifecyclePort processes() {
        return processes;
    }

    /** @return {@code true} si los adaptadores hablan con la DLL real */
    public boolean isNative() {
        return nativeFunctions != null;
    }

    /** Motivo por el que el modo {@code auto} eligió datos simulados (vacío si no hubo caída). */
    public Optional<String> fallbackReason() {
        return Optional.ofNullable(fallbackReason);
    }

    /** Escenario simulado activo (vacío en modo nativo). */
    public Optional<MockScenario> mockScenario() {
        return Optional.ofNullable(mockScenario);
    }

    /** Ruta de la DLL cargada, o vacío en modo simulado. */
    public Optional<Path> nativeLibrary() {
        return nativeFunctions == null ? Optional.empty() : Optional.of(nativeFunctions.libraryPath());
    }

    /** SHA-256 de la DLL cargada, para el informe de integración. */
    public Optional<String> nativeLibrarySha256() {
        return nativeFunctions == null ? Optional.empty() : Optional.of(nativeFunctions.sha256());
    }

    /** ABI y versión de compilación anunciados por la DLL. */
    public Optional<AbiInfo> abiInfo() {
        return nativeFunctions == null ? Optional.empty() : Optional.of(nativeFunctions.abiInfo());
    }

    /** Texto para la UI: «Datos reales · DLL 1.4.2 (ABI 1.4)» o «Datos simulados · NORMAL». */
    public String dataSourceDescription() {
        if (isNative()) {
            AbiInfo abi = nativeFunctions.abiInfo();
            return "Datos reales · DLL " + abi.buildVersion() + " (ABI " + abi.abiVersion() + ")";
        }
        return "Datos simulados · " + mockScenario.name() + (fallbackReason == null ? "" : " (DLL no disponible)");
    }

    /**
     * Abre la caja negra persistente en la carpeta de datos de usuario por defecto
     * ({@link BlackboxSettings#defaultDirectory(boolean)}).
     */
    public synchronized BlackboxSinkPort openBlackbox() {
        return openBlackbox(BlackboxSettings.defaults(BlackboxSettings.defaultDirectory(!isNative())));
    }

    /**
     * Abre la caja negra una sola vez: anillo de la DLL en modo nativo, anillo Java en simulado,
     * y el archivo histórico del sink Java. Si no puede abrirse, devuelve un sink sin persistencia
     * y deja el motivo en {@link #blackboxFailure()}; la aplicación sigue funcionando.
     */
    public synchronized BlackboxSinkPort openBlackbox(BlackboxSettings settings) {
        Objects.requireNonNull(settings, "settings no puede ser null");
        if (closed) {
            throw new IllegalStateException("InfrastructureAdapters ya fue cerrado");
        }
        if (blackbox != null) {
            return blackbox;
        }
        BlackboxRing ring = null;
        try {
            ring = isNative()
                    ? NativeBlackboxRing.create(nativeFunctions, settings.ringCapacityRecords())
                    : new InMemoryBlackboxRing(Math.min(settings.ringCapacityRecords(), mockScenario.blackboxRingCapacity()));
            blackbox = PersistentBlackboxSink.open(ring, settings, !isNative(), Clock.systemUTC(), System::nanoTime);
        } catch (RuntimeException e) {
            if (ring != null) {
                ring.close();
            }
            blackboxFailure = e.getMessage();
            LOG.log(Level.WARNING, "Caja negra desactivada: {0}", e.getMessage());
            blackbox = new NoOpBlackboxSink();
        }
        return blackbox;
    }

    /** Motivo por el que la caja negra no pudo abrirse. */
    public synchronized Optional<String> blackboxFailure() {
        return Optional.ofNullable(blackboxFailure);
    }

    /**
     * Cierra la caja negra (vacía el anillo y libera el token) y apaga la telemetría nativa una
     * sola vez. Idempotente.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (blackbox instanceof AutoCloseable closeable) {
                closeable.close();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "No se pudo cerrar la caja negra", e);
        } finally {
            if (nativeFunctions != null) {
                nativeFunctions.close();
            }
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
