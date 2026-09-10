package com.corepulse.infrastructure.nativebridge;

/**
 * Error de infraestructura al cargar o invocar la biblioteca nativa de MACE.
 *
 * <p>Es una excepción no comprobada para que los adaptadores puedan cumplir los puertos de
 * dominio (que no declaran excepciones) y, a la vez, la capa de composición pueda decidir un
 * fallback al modo simulado ({@code infrastructure.mock}) cuando la DLL no está disponible.</p>
 */
public class NativeLibraryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NativeLibraryException(String message) {
        super(message);
    }

    public NativeLibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
