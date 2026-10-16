package com.mace.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricStateTest {

    @Test
    void contieneLosEstadosDefinidos() {
        assertNotNull(MetricState.VALID);
        assertNotNull(MetricState.NO_PROVIDER);
        assertNotNull(MetricState.UNSUPPORTED);
        assertNotNull(MetricState.WARMING_UP);
        assertNotNull(MetricState.ERROR);
    }

    @Test
    void todosSonDistintos() {
        assertSame(MetricState.VALID, MetricState.valueOf("VALID"));
        assertFalse(MetricState.VALID == MetricState.NO_PROVIDER);
        assertFalse(MetricState.NO_PROVIDER == MetricState.ERROR);
    }
}
