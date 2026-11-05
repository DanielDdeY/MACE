package com.mace.domain.model;

/**
 * Estado individual de una métrica de hardware (contrato V2, {@code MaceMetricState}).
 *
 * <p>Regla del contrato: un valor numérico solo se muestra, grafica o usa en alertas si su
 * estado es {@link #VALID}. Un {@code 0} con cualquier otro estado <strong>no</strong> es una
 * lectura física de cero.</p>
 */
public enum MetricState {
    /** Lectura física válida. */
    VALID("Válido"),
    /** No existe el dispositivo que aporta la métrica. */
    NO_DEVICE("Sin dispositivo"),
    /** El dispositivo existe, pero no hay proveedor/SDK que lea esta métrica. */
    NO_PROVIDER("Sin proveedor"),
    /** El proveedor existe, pero el modelo no expone esta métrica (p. ej. iGPU sin ventilador). */
    UNSUPPORTED("No soportado"),
    /** Faltan permisos (elevación, controlador) para leer la métrica. */
    ACCESS_DENIED("Acceso denegado"),
    /** Primera muestra diferencial: aún no hay intervalo para calcular el valor. */
    WARMING_UP("Calentando"),
    /** El proveedor falló al leer en este sondeo. */
    READ_FAILED("Error de lectura"),
    /** El dispositivo está apagado o en reposo (p. ej. dGPU de portátil híbrido). */
    DEVICE_INACTIVE("Inactivo"),
    /** La telemetría nativa no está inicializada. */
    NOT_INITIALIZED("No inicializado");

    private final String label;

    MetricState(String label) {
        this.label = label;
    }

    /** @return {@code true} solo para {@link #VALID} */
    public boolean isValid() {
        return this == VALID;
    }

    /** Texto corto en español para mostrar el motivo de la ausencia («N/D: Sin proveedor»). */
    public String label() {
        return label;
    }
}
