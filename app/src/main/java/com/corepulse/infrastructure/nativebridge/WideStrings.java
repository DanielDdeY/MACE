package com.corepulse.infrastructure.nativebridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_CHAR;

/**
 * Conversión entre {@link String} de Java y cadenas {@code wchar_t} de Windows
 * (UTF-16LE terminadas en NUL) alojadas en memoria nativa.
 *
 * <p>Se implementa de forma acotada: la lectura nunca sobrepasa la capacidad declarada del
 * arreglo de C aunque falte el terminador, y la escritura siempre agrega el NUL final.</p>
 */
public final class WideStrings {

    /** Tamaño en bytes de un {@code wchar_t} en Windows. */
    public static final long WCHAR_SIZE = JAVA_CHAR.byteSize();

    private WideStrings() {
    }

    /**
     * Lee una cadena {@code wchar_t[maxChars]} desde {@code segment} a partir de {@code offset}.
     * Se detiene en el primer NUL o al alcanzar {@code maxChars}, lo que ocurra primero.
     *
     * @param segment  segmento que contiene el arreglo
     * @param offset   desplazamiento en bytes del primer carácter
     * @param maxChars capacidad del arreglo en caracteres (no en bytes)
     * @return cadena decodificada (posiblemente vacía), nunca {@code null}
     */
    public static String read(MemorySegment segment, long offset, int maxChars) {
        Objects.requireNonNull(segment, "segment no puede ser null");
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars no puede ser negativo: " + maxChars);
        }
        char[] buffer = new char[maxChars];
        int length = 0;
        while (length < maxChars) {
            char c = segment.get(JAVA_CHAR, offset + length * WCHAR_SIZE);
            if (c == '\0') {
                break;
            }
            buffer[length++] = c;
        }
        return new String(buffer, 0, length);
    }

    /**
     * Reserva en {@code arena} una copia UTF-16LE de {@code value} terminada en NUL,
     * lista para pasarse como {@code const wchar_t*} a una función nativa.
     *
     * @param arena arena que gobierna el ciclo de vida del segmento
     * @param value texto a copiar
     * @return segmento de {@code (value.length() + 1) * 2} bytes
     */
    public static MemorySegment allocate(Arena arena, String value) {
        Objects.requireNonNull(arena, "arena no puede ser null");
        Objects.requireNonNull(value, "value no puede ser null");
        MemorySegment segment = arena.allocate((value.length() + 1) * WCHAR_SIZE, WCHAR_SIZE);
        for (int i = 0; i < value.length(); i++) {
            segment.set(JAVA_CHAR, i * WCHAR_SIZE, value.charAt(i));
        }
        segment.set(JAVA_CHAR, value.length() * WCHAR_SIZE, '\0');
        return segment;
    }

    /**
     * Escribe {@code value} en un arreglo {@code wchar_t[maxChars]} ya existente, truncando si es
     * necesario y garantizando el NUL final (semántica de {@code wcsncpy_s(..., _TRUNCATE)}).
     * Útil para construir registros sintéticos en pruebas.
     *
     * @param segment  segmento que contiene el arreglo destino
     * @param offset   desplazamiento en bytes del primer carácter
     * @param maxChars capacidad del arreglo en caracteres (incluye el NUL)
     * @param value    texto a escribir
     */
    public static void write(MemorySegment segment, long offset, int maxChars, String value) {
        Objects.requireNonNull(segment, "segment no puede ser null");
        Objects.requireNonNull(value, "value no puede ser null");
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars debe ser positivo: " + maxChars);
        }
        int length = Math.min(value.length(), maxChars - 1);
        for (int i = 0; i < length; i++) {
            segment.set(JAVA_CHAR, offset + i * WCHAR_SIZE, value.charAt(i));
        }
        segment.set(JAVA_CHAR, offset + length * WCHAR_SIZE, '\0');
    }
}
