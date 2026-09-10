package com.corepulse.infrastructure.nativebridge;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WideStringsTest {

    @Test
    void leeHastaElPrimerNul() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(20 * WideStrings.WCHAR_SIZE);
            WideStrings.write(segment, 0L, 20, "chrome.exe");

            assertEquals("chrome.exe", WideStrings.read(segment, 0L, 20));
        }
    }

    @Test
    void sinTerminadorLeeSoloHastaLaCapacidadDeclarada() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(8 * WideStrings.WCHAR_SIZE);
            for (int i = 0; i < 8; i++) {
                segment.set(JAVA_CHAR, i * WideStrings.WCHAR_SIZE, 'A');
            }

            assertEquals("AAAA", WideStrings.read(segment, 0L, 4));
        }
    }

    @Test
    void escrituraTruncaYGarantizaElNulFinal() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(5 * WideStrings.WCHAR_SIZE);
            WideStrings.write(segment, 0L, 5, "abcdefgh");

            assertEquals("abcd", WideStrings.read(segment, 0L, 5));
            assertEquals('\0', segment.get(JAVA_CHAR, 4 * WideStrings.WCHAR_SIZE));
        }
    }

    @Test
    void allocateProduceUtf16TerminadoEnNul() {
        String name = "Local\\MaceBlackbox";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = WideStrings.allocate(arena, name);

            assertEquals((name.length() + 1) * WideStrings.WCHAR_SIZE, segment.byteSize());
            assertEquals('\0', segment.get(JAVA_CHAR, name.length() * WideStrings.WCHAR_SIZE));
            assertEquals(name, WideStrings.read(segment, 0L, name.length() + 1));
        }
    }

    @Test
    void conservaCaracteresNoAscii() {
        String name = "señal-ü-日本語.exe";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = WideStrings.allocate(arena, name);

            assertEquals(name, WideStrings.read(segment, 0L, NativeLayouts.PROCESS_NAME_CHARS));
        }
    }

    @Test
    void cadenaVaciaCuandoElPrimerCaracterEsNul() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(4 * WideStrings.WCHAR_SIZE);
            segment.fill((byte) 0);

            assertEquals("", WideStrings.read(segment, 0L, 4));
        }
    }

    @Test
    void respetaElOffsetDentroDelSegmento() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64);
            segment.fill((byte) 0x7F);
            long offset = 10L;
            WideStrings.write(segment, offset, 10, "java.exe");

            assertEquals("java.exe", WideStrings.read(segment, offset, 10));
        }
    }

    @Test
    void rechazaCapacidadesInvalidas() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(8);
            assertThrows(IllegalArgumentException.class, () -> WideStrings.read(segment, 0L, -1));
            assertThrows(IllegalArgumentException.class, () -> WideStrings.write(segment, 0L, 0, "x"));
        }
    }
}
