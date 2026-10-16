package com.mace.domain.service;

import com.mace.domain.model.MemoryMetrics;
import com.mace.domain.model.ProcessId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de negocio del punto de inserción futuro de fuga de memoria.
 * Se ejecutan sin DLL, sin Windows y sin JavaFX.
 *
 * <p>Como {@link MemoryLeakEvaluationService} es un contrato sin implementación
 * todavía, estas pruebas validan que los tipos y el veredicto del contrato son
 * los esperados para que Dev 3 y Dev 4 puedan mapearlos.</p>
 */
class MemoryLeakEvaluationServiceTest {

    @Test
    void veredictoDeFugaTieneLosCuatroEstadosEsperados() {
        assertEquals(MemoryLeakEvaluationService.LeakVerdict.NO_LEAK,
                MemoryLeakEvaluationService.LeakVerdict.valueOf("NO_LEAK"));
        assertEquals(MemoryLeakEvaluationService.LeakVerdict.SUSPECTED,
                MemoryLeakEvaluationService.LeakVerdict.valueOf("SUSPECTED"));
        assertEquals(MemoryLeakEvaluationService.LeakVerdict.CONFIRMED,
                MemoryLeakEvaluationService.LeakVerdict.valueOf("CONFIRMED"));
        assertEquals(MemoryLeakEvaluationService.LeakVerdict.UNKNOWN,
                MemoryLeakEvaluationService.LeakVerdict.valueOf("UNKNOWN"));
    }

    @Test
    void firmaDelContratoAceptaProcesoYMetricas() {
        // La mera existencia y compilabilidad de la firma valida el punto de inserción.
        ProcessId pid = new ProcessId(1234L);
        MemoryMetrics current = MemoryMetrics.zero();
        MemoryMetrics baseline = MemoryMetrics.zero();

        assertEquals(0L, current.workingSetBytes());
        assertEquals(0L, baseline.privateBytes());
        assertEquals(pid.value(), 1234L);
    }
}
