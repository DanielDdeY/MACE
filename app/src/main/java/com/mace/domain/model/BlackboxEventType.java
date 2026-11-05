package com.mace.domain.model;

/**
 * Eventos de aplicación que el sink Java persiste en la caja negra, además de las muestras de
 * telemetría que pasan por el anillo nativo. El código numérico forma parte del formato de
 * archivo y no debe cambiar.
 */
public enum BlackboxEventType {
    SESSION_START(60),
    SESSION_END(61),
    THERMAL_ALERT(70),
    LEAK_ALERT(71),
    HANG_ALERT(72),
    TERMINATION_REQUEST(73),
    TERMINATION_RESULT(74);

    private final int code;

    BlackboxEventType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static BlackboxEventType fromCode(int code) {
        for (BlackboxEventType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Código de evento desconocido: " + code);
    }
}
