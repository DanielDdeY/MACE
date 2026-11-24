package com.mace.domain.model;

/** Serie de memoria de un proceso que se analiza por separado en la detección de fugas. */
public enum MemorySeries {
    WORKING_SET("Working Set"),
    PRIVATE_BYTES("Private Bytes");

    private final String label;

    MemorySeries(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Lectura de esta serie dentro de una muestra. */
    public LongMetricReading readFrom(ProcessSample sample) {
        return this == WORKING_SET ? sample.workingSet() : sample.privateBytes();
    }
}
