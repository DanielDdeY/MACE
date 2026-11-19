package com.mace.presentation.viewmodel;
import com.mace.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static com.mace.domain.DomainFixtures.*;

class TelemetryPresentationTest {
    private final AtomicLong time=new AtomicLong();
    private final TelemetryPresentation filter=new TelemetryPresentation(time::get);
    private static final GpuAdapter GPU=gpuAdapter(123,0,"GPU");
    private HardwareSnapshot sample(MetricState state) {
        return snapshot(new CpuReading(CpuVendor.AMD,SensorProvider.LHM,PowerScope.CPU_PACKAGE,
            state==MetricState.VALID ? MetricReading.valid(60) : missing(state),
            state==MetricState.VALID ? MetricReading.valid(15) : missing(state),
            MetricReading.valid(10),0),
            state==MetricState.VALID ? gpu(GPU,44) : GpuReading.unavailable(GPU,state,0));
    }
    private HardwareSnapshot warming() {
        var raw=sample(MetricState.WARMING_UP);
        var gpu=raw.gpus().getFirst();
        return new HardwareSnapshot(raw.timestamp(),raw.cpu(),List.of(new GpuReading(GPU,SensorProvider.NVIDIA_NVML,
            PowerScope.UNKNOWN,gpu.temperatureCelsius(),gpu.powerWatts(),gpu.usagePercent(),gpu.fanRpm(),
            gpu.fanPercent(),gpu.videoMemoryUsedBytes(),gpu.videoMemoryTotalBytes(),0)),raw.memory());
    }
    @Test void soloUnWarmingConsecutivoRetieneLecturasValidasSinMutarLosDatosCrudos() {
        filter.apply(sample(MetricState.VALID)); time.set(SECOND);
        var raw=warming(); var shown=filter.apply(raw);
        assertEquals(60,shown.cpu().temperatureCelsius().value());
        assertEquals(44,shown.gpus().getFirst().temperatureCelsius().value());
        assertEquals(PowerScope.GPU_BOARD,shown.gpus().getFirst().powerScope());
        assertEquals(MetricState.WARMING_UP,raw.cpu().temperatureCelsius().state());
        assertFalse(filter.apply(warming()).cpu().temperatureCelsius().isValid(),"un segundo WARMING no prolonga el dato");
    }
    @Test void vencePorTiempoAunqueSeaElPrimerWarming() {
        filter.apply(sample(MetricState.VALID)); time.set(TelemetryPresentation.MAX_HOLD_NANOS+1);
        assertFalse(filter.apply(warming()).cpu().temperatureCelsius().isValid());
    }
    @Test void noHayRetencionSinValidPrevioNiDespuesDeUnFalloExplicito() {
        assertFalse(filter.apply(warming()).cpu().temperatureCelsius().isValid());
        for (MetricState state:MetricState.values()) {
            if (state==MetricState.VALID || state==MetricState.WARMING_UP) continue;
            filter.apply(sample(MetricState.VALID));
            var missing=filter.apply(sample(state));
            assertEquals(state,missing.cpu().temperatureCelsius().state());
            assertTrue(MetricDisplay.decimal(missing.cpu().temperatureCelsius(),"°C",1).text().startsWith("N/D"));
            assertFalse(filter.apply(warming()).cpu().temperatureCelsius().isValid());
        }
    }
    @Test void recuperacionValidaRearmaLaTolerancia() {
        filter.apply(sample(MetricState.VALID)); filter.apply(warming());
        filter.apply(sample(MetricState.VALID));
        assertTrue(filter.apply(warming()).cpu().temperatureCelsius().isValid());
    }
    @Test void gpuAmbiguaMuestraNdEnLasSieteMetricas() {
        filter.apply(sample(MetricState.VALID));
        var gpu=filter.apply(sample(MetricState.UNSUPPORTED)).gpus().getFirst();
        GpuCardViewModel card=new GpuCardViewModel(GPU.id(),30); card.update(gpu,1);
        for (var property:List.of(card.temperatureProperty(),card.powerProperty(),card.usageProperty(),
            card.fanRpmProperty(),card.fanPercentProperty(),card.vramUsedProperty(),card.vramTotalProperty()))
            assertTrue(property.get().text().startsWith("N/D"));
    }
    @Test void unknownPowerSoloOcultaPotenciaYAdmiteVentiladorCero() {
        var raw=sample(MetricState.VALID); var good=gpu(GPU,44);
        var mixed=new GpuReading(GPU,SensorProvider.LHM,PowerScope.UNKNOWN,good.temperatureCelsius(),
            missing(MetricState.UNSUPPORTED),good.usagePercent(),LongMetricReading.valid(0),good.fanPercent(),
            good.videoMemoryUsedBytes(),good.videoMemoryTotalBytes(),0);
        var result=filter.apply(new HardwareSnapshot(Instant.now(),raw.cpu(),List.of(mixed),raw.memory()));
        var card=new GpuCardViewModel(GPU.id(),30); card.update(result.gpus().getFirst(),1);
        assertTrue(card.powerProperty().get().text().startsWith("N/D"));
        assertTrue(card.temperatureProperty().get().isValid());
        assertEquals("0 RPM",card.fanRpmProperty().get().text());
        assertTrue(card.vramUsedProperty().get().isValid());
    }
    @Test void desaparicionDeGpuLimpiaSuValorAnterior() {
        var raw=sample(MetricState.VALID); filter.apply(raw);
        filter.apply(new HardwareSnapshot(raw.timestamp(),raw.cpu(),List.of(),raw.memory()));
        assertFalse(filter.apply(warming()).gpus().getFirst().temperatureCelsius().isValid());
    }
}
