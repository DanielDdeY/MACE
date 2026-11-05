package com.mace.infrastructure.mock;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Locale;

/**
 * Perfiles reproducibles del modo simulado. Se eligen con {@code -Dmace.mock.scenario=<NOMBRE>}
 * y {@code -Dmace.mock.seed=<n>} (por defecto {@code NORMAL} y semilla {@value #DEFAULT_SEED}).
 *
 * <p>Los procesos simulados ({@link MockProcessAdapter}) son los mismos en todos los perfiles e
 * incluyen proceso colgado, sin ventana, que termina, PID reutilizado, protegido, sin permisos
 * y con crecimiento de memoria sostenido.</p>
 */
public enum MockScenario {
    NORMAL("Intel + NVIDIA, todas las métricas válidas"),
    CPU_NO_TEMPERATURE("CPU con uso válido y sin proveedor de temperatura ni potencia"),
    AMD_NO_POWER("Ryzen con temperatura (Ryzen Master SDK) y sin proveedor de potencia; Radeon por ADLX"),
    ACCESS_DENIED("Ryzen sin elevación: temperatura de CPU con acceso denegado"),
    IGPU_NO_FAN("GPU integrada Intel con temperatura y sin ventilador propio"),
    DUAL_GPU("iGPU Intel + dGPU NVIDIA con datos distintos"),
    GPU_INACTIVE("Portátil híbrido: dGPU NVIDIA en reposo (DEVICE_INACTIVE)"),
    GPU_NO_SENSOR("GPU detectada sin proveedor de sensores"),
    WARMING_UP("Primeras muestras: uso y potencia calentando"),
    THERMAL_WARNING("Temperaturas por encima de 75 °C (advertencia)"),
    THERMAL_CRITICAL("Temperaturas por encima de 85 °C (crítico)"),
    NO_GPU("Equipo sin GPU física detectada"),
    BLACKBOX_GAP("Anillo de caja negra mínimo: el archivo marca huecos de secuencia");

    public static final String PROPERTY = "mace.mock.scenario";
    public static final String SEED_PROPERTY = "mace.mock.seed";
    public static final long DEFAULT_SEED = 42L;

    /** Sondeos con métricas {@code WARMING_UP} en el perfil {@link #WARMING_UP}. */
    public static final int WARM_UP_POLLS = 3;

    private static final Logger LOG = System.getLogger(MockScenario.class.getName());

    private final String description;

    MockScenario(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Capacidad del anillo temporal de la caja negra en este perfil. */
    public int blackboxRingCapacity() {
        return this == BLACKBOX_GAP ? 2 : 4_096;
    }

    public static MockScenario fromSystemProperty() {
        String raw = System.getProperty(PROPERTY, NORMAL.name()).trim();
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Escenario simulado desconocido -D{0}={1}; se usa NORMAL", PROPERTY, raw);
            return NORMAL;
        }
    }

    public static long seedFromSystemProperty() {
        String raw = System.getProperty(SEED_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SEED;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Semilla simulada inválida -D{0}={1}; se usa {2}", SEED_PROPERTY, raw, DEFAULT_SEED);
            return DEFAULT_SEED;
        }
    }
}
