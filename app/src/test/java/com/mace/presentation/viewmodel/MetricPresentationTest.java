package com.mace.presentation.viewmodel;

import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricPresentationTest {

    @Test
    void distingueCeroFisicoDeLecturaAusente() {
        MetricDisplay zero = MetricDisplay.whole(LongMetricReading.valid(0), "RPM");
        MetricDisplay absent = MetricDisplay.whole(LongMetricReading.unavailable(MetricState.UNSUPPORTED), "RPM");
        assertEquals("0 RPM", zero.text());
        assertTrue(zero.value().isPresent());
        assertEquals(0, zero.value().orElseThrow());
        assertEquals(MetricState.UNSUPPORTED, absent.state());
        assertEquals("N/D: No soportado", absent.text());
        assertFalse(absent.value().isPresent());
    }

    @Test
    void historialCortaLaLineaEnEstadoInvalidoYAceptaUnPuntoPorSondeo() {
        MetricHistory history = new MetricHistory(3);
        history.append(1, MetricReading.valid(50));
        history.append(2, MetricReading.unavailable(MetricState.READ_FAILED));
        history.append(2, MetricReading.valid(99));
        history.append(3, MetricReading.valid(52));
        assertEquals(3, history.points().size());
        assertEquals(2, history.validSegments().size());
        assertEquals(MetricState.READ_FAILED, history.points().get(1).state());
        history.append(4, MetricReading.valid(53));
        assertEquals(3, history.points().size());
        assertEquals(2, history.points().getFirst().hardwareSequence());
    }
}
