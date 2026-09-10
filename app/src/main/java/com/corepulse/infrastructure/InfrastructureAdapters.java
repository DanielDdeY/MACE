package com.corepulse.infrastructure;

import com.corepulse.domain.port.out.HardwareSensorsPort;
import com.corepulse.domain.port.out.ProcessLifecyclePort;
import com.corepulse.infrastructure.mock.MockProcessAdapter;
import com.corepulse.infrastructure.mock.MockSensorsAdapter;
import com.corepulse.infrastructure.nativebridge.FfmProcessAdapter;
import com.corepulse.infrastructure.nativebridge.FfmSensorsAdapter;
import com.corepulse.infrastructure.nativebridge.NativeFunctions;
import com.corepulse.infrastructure.nativebridge.NativeLibraryException;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Punto único de composición de la capa de infraestructura: entrega las implementaciones de
 * los puertos de salida ({@link HardwareSensorsPort}, {@link ProcessLifecyclePort}) que los
 * casos de uso necesitan, eligiendo entre el puente FFM real y los adaptadores simulados.
 *
 * <p>Modo de selección (propiedad del sistema {@value #MODE_PROPERTY}):</p>
 * <ul>
 *   <li>{@code auto} (por defecto): usa la DLL nativa si está en Windows y se localiza;
 *       si no, cae a los mocks registrando una advertencia.</li>
 *   <li>{@code native}: exige la DLL; lanza {@link NativeLibraryException} si falta.</li>
 *   <li>{@code mock}: siempre simulado (útil para la UI y para CI sin Windows).</li>
 * </ul>
 *
 * <p>Uso típico desde el arranque de la aplicación:</p>
 * <pre>{@code
 * try (InfrastructureAdapters adapters = InfrastructureAdapters.create()) {
 *     var poll = new PollSystemMetricsService(adapters.sensors(), publisher, blackbox, new ThermalAlertEvaluator());
 *     var terminate = new TerminateProcessService(adapters.processes(), new SystemProcessGuard());
 *     ...
 * }
 * }</pre>
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

    private InfrastructureAdapters(HardwareSensorsPort sensors, ProcessLifecyclePort processes,
                                   NativeFunctions nativeFunctions) {
        this.sensors = Objects.requireNonNull(sensors, "sensors no puede ser null");
        this.processes = Objects.requireNonNull(processes, "processes no puede ser null");
        this.nativeFunctions = nativeFunctions;
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

    /** Adaptadores simulados; no requieren Windows ni la DLL. */
    public static InfrastructureAdapters mock() {
        return new InfrastructureAdapters(new MockSensorsAdapter(), new MockProcessAdapter(), null);
    }

    /**
     * Adaptadores FFM sobre la DLL nativa.
     *
     * @throws NativeLibraryException si la DLL no se encuentra, no carga o no exporta los símbolos
     */
    public static InfrastructureAdapters nativeOrThrow() {
        NativeFunctions functions = NativeFunctions.load();
        try {
            return new InfrastructureAdapters(
                    new FfmSensorsAdapter(functions),
                    new FfmProcessAdapter(functions),
                    functions);
        } catch (RuntimeException e) {
            functions.close();
            throw e;
        }
    }

    private static InfrastructureAdapters autoDetect() {
        if (!isWindows()) {
            LOG.log(Level.INFO, "Sistema operativo no Windows: se usan adaptadores simulados");
            return mock();
        }
        try {
            InfrastructureAdapters adapters = nativeOrThrow();
            LOG.log(Level.INFO, "Puente FFM activo con {0}", adapters.nativeFunctions.libraryPath());
            return adapters;
        } catch (NativeLibraryException e) {
            LOG.log(Level.WARNING, "Biblioteca nativa no disponible; se usan adaptadores simulados. Motivo: {0}",
                    e.getMessage());
            return mock();
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

    /** Ruta de la DLL cargada, o vacío en modo simulado. */
    public Optional<Path> nativeLibrary() {
        return nativeFunctions == null ? Optional.empty() : Optional.of(nativeFunctions.libraryPath());
    }

    /** Apaga la telemetría nativa y descarga la DLL (no-op en modo simulado). */
    @Override
    public void close() {
        if (nativeFunctions != null) {
            nativeFunctions.close();
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
