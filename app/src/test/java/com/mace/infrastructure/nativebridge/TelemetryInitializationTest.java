package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.MetricState;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelemetryInitializationTest {
    @Test void busyNoBloqueaYSeRecuperaSinMarcarExitoAntesDeTiempo() {
        NativeApi api=mock(NativeApi.class);
        when(api.ensureTelemetryInitialized()).thenReturn(NativeStatus.BUSY,NativeStatus.BUSY,0);
        AtomicLong time=new AtomicLong();
        var init=new TelemetryInitialization(api,time::get);
        assertFalse(init.ready());
        assertEquals(MetricState.WARMING_UP,init.state());
        for (int i=0;i<100;i++) assertFalse(init.ready());
        verify(api,times(1)).ensureTelemetryInitialized();
        time.set(TelemetryInitialization.RETRY_NANOS);
        assertFalse(init.ready());
        time.addAndGet(TelemetryInitialization.RETRY_NANOS);
        assertTrue(init.ready()); assertTrue(init.ready());
        verify(api,times(3)).ensureTelemetryInitialized();
    }
    @Test void limitaRafagaYPausaSinConvertirBusyEnErrorPermanente() {
        NativeApi api=mock(NativeApi.class); when(api.ensureTelemetryInitialized()).thenReturn(NativeStatus.BUSY);
        AtomicLong time=new AtomicLong(); var init=new TelemetryInitialization(api,time::get);
        for (int i=0;i<TelemetryInitialization.MAX_ATTEMPTS;i++) {
            assertFalse(init.ready()); time.addAndGet(TelemetryInitialization.RETRY_NANOS);
        }
        assertEquals(MetricState.NOT_INITIALIZED,init.state());
        for (int i=0;i<100;i++) assertFalse(init.ready());
        verify(api,times(5)).ensureTelemetryInitialized();
        when(api.ensureTelemetryInitialized()).thenReturn(0);
        time.addAndGet(TelemetryInitialization.COOLDOWN_NANOS);
        assertTrue(init.ready());
        verify(api,times(6)).ensureTelemetryInitialized();
    }
    @Test void unaPausaLargaAgotaLaVentanaSinHacerReintentosAtrasados() {
        NativeApi api=mock(NativeApi.class); when(api.ensureTelemetryInitialized()).thenReturn(NativeStatus.BUSY);
        AtomicLong time=new AtomicLong(); var init=new TelemetryInitialization(api,time::get);
        assertFalse(init.ready()); time.set(TelemetryInitialization.WINDOW_NANOS+1);
        assertFalse(init.ready()); assertEquals(MetricState.NOT_INITIALIZED,init.state());
        verify(api,times(1)).ensureTelemetryInitialized();
    }
    @Test void errorDistintoDeBusySeInforma() {
        NativeApi api=mock(NativeApi.class); when(api.ensureTelemetryInitialized()).thenReturn(87);
        assertThrows(NativeLibraryException.class,()->new TelemetryInitialization(api,()->0).ready());
    }
}
