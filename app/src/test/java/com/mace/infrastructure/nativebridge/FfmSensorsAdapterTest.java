package com.mace.infrastructure.nativebridge;

import com.mace.domain.model.CpuReading;
import com.mace.domain.model.CpuVendor;
import com.mace.domain.model.GpuAdapter;
import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.HardwareSnapshot;
import com.mace.domain.model.LongMetricReading;
import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import com.mace.domain.model.PowerScope;
import com.mace.domain.model.SensorProvider;
import com.mace.domain.model.SystemMemoryReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adaptador de sensores V2 contra la semántica simulada de la DLL; no requiere Windows. */
class FfmSensorsAdapterTest {

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final GpuAdapter IGPU = new GpuAdapter(new GpuAdapterId(0x1111, 0), 0x8086, 0x9A49, 0, 1, 0, 0,
            "Intel(R) Iris(R) Xe Graphics");
    private static final GpuAdapter DGPU = new GpuAdapter(new GpuAdapterId(0x2222, 1), 0x10DE, 0x2504, 0, 1, 0, 1,
            "NVIDIA GeForce RTX 3060");

    private FakeNativeApi api;

    @BeforeEach
    void setUp() {
        api = new FakeNativeApi();
        api.cpuReadings = poll -> new CpuReading(CpuVendor.AMD, SensorProvider.AMD_RYZEN_SDK, PowerScope.UNKNOWN,
                MetricReading.valid(61.5), MetricReading.unavailable(MetricState.NO_PROVIDER),
                poll == 1 ? MetricReading.unavailable(MetricState.WARMING_UP) : MetricReading.valid(23.0), 1_000L * poll);
        api.memoryReading = new SystemMemoryReading(LongMetricReading.valid(16 * GIB), LongMetricReading.valid(9 * GIB), 5L);
        api.adapters.put(IGPU.id(), IGPU);
        api.adapters.put(DGPU.id(), DGPU);
        api.gpuReadings.put(IGPU.id(), poll -> new GpuReading(IGPU, SensorProvider.INTEL_IGCL, PowerScope.GPU_CHIP,
                MetricReading.valid(55), MetricReading.valid(8), MetricReading.valid(12),
                LongMetricReading.unavailable(MetricState.UNSUPPORTED), LongMetricReading.unavailable(MetricState.UNSUPPORTED),
                LongMetricReading.valid(GIB), LongMetricReading.unavailable(MetricState.UNSUPPORTED), 2L));
        api.gpuReadings.put(DGPU.id(), poll -> new GpuReading(DGPU, SensorProvider.NVIDIA_NVML, PowerScope.GPU_BOARD,
                MetricReading.valid(71), MetricReading.valid(180), MetricReading.valid(97),
                LongMetricReading.valid(2100), LongMetricReading.valid(58),
                LongMetricReading.valid(10 * GIB), LongMetricReading.valid(12 * GIB), 3L));
    }

    @Test
    void inicializaLaTelemetriaUnaVezYFallaClaramenteSiLaDllLoRechaza() {
        new FfmSensorsAdapter(api);
        assertEquals(1, api.initCalls);

        api.initResult = 87;
        NativeLibraryException error = assertThrows(NativeLibraryException.class, () -> new FfmSensorsAdapter(api));
        assertTrue(error.getMessage().contains("CP_InitializeTelemetry"));
    }

    @Test
    void dosGpuConservanSusDatosYEstadosSinMezclarse() {
        HardwareSnapshot snapshot = new FfmSensorsAdapter(api).fetchHardwareSnapshot();

        assertEquals(2, snapshot.gpus().size());
        GpuReading igpu = snapshot.gpu(IGPU.id()).orElseThrow();
        GpuReading dgpu = snapshot.gpu(DGPU.id()).orElseThrow();
        assertEquals("Intel(R) Iris(R) Xe Graphics", igpu.adapter().description());
        assertEquals(55.0, igpu.temperatureCelsius().value(), 1e-6);
        assertEquals(MetricState.UNSUPPORTED, igpu.fanRpm().state(), "iGPU con temperatura y sin ventilador propio");
        assertEquals(SensorProvider.INTEL_IGCL, igpu.provider());
        assertEquals(71.0, dgpu.temperatureCelsius().value(), 1e-6);
        assertEquals(2100L, dgpu.fanRpm().value());
        assertEquals(PowerScope.GPU_BOARD, dgpu.powerScope());
        assertEquals(1, dgpu.adapter().adapterIndex());
    }

    @Test
    void mapeaEstadosProveedorYAmbitoDeLaCpu() {
        FfmSensorsAdapter adapter = new FfmSensorsAdapter(api);

        CpuReading first = adapter.fetchHardwareSnapshot().cpu();
        CpuReading second = adapter.fetchHardwareSnapshot().cpu();

        assertEquals(CpuVendor.AMD, first.vendor());
        assertEquals(SensorProvider.AMD_RYZEN_SDK, first.provider());
        assertEquals(MetricState.NO_PROVIDER, first.powerWatts().state(), "AMD sin proveedor de potencia");
        assertEquals(MetricState.WARMING_UP, first.usagePercent().state());
        assertTrue(Double.isNaN(first.usagePercent().value()));
        assertEquals(23.0, second.usagePercent().value(), 1e-6);
        assertEquals(61.5, second.temperatureCelsius().value(), 1e-6);
    }

    @Test
    void unaGpuInactivaSigueApareciendoConSuEstado() {
        api.gpuReadings.put(DGPU.id(), poll -> GpuReading.unavailable(DGPU, MetricState.DEVICE_INACTIVE, 9L));

        GpuReading dgpu = new FfmSensorsAdapter(api).fetchHardwareSnapshot().gpu(DGPU.id()).orElseThrow();

        assertTrue(dgpu.isInactive());
        assertFalse(dgpu.hasAnyValidMetric());
    }

    @Test
    void reintentaCuandoElNumeroDeGpuCambiaEntreConsultaYLectura() {
        api.gpuGrowthOnRead = 2;

        List<GpuAdapter> adapters = new FfmSensorsAdapter(api).listGpuAdapters();

        assertEquals(2, adapters.size());
        assertEquals(6, api.gpuEnumerationCalls, "dos intentos fallidos y uno correcto, cada uno con consulta + lectura");
    }

    @Test
    void siElConteoNoSeEstabilizaConservaLaListaAnteriorSinRomperElSondeo() {
        FfmSensorsAdapter adapter = new FfmSensorsAdapter(api);
        adapter.fetchHardwareSnapshot();
        api.gpuGrowthOnRead = 10;

        List<GpuAdapter> adapters = adapter.listGpuAdapters();

        assertEquals(2, adapters.size());
        assertEquals(2, adapter.fetchHardwareSnapshot().gpus().size());
    }

    @Test
    void unaGpuQueDesapareceSeMarcaSinDispositivoYSeReenumera() {
        FfmSensorsAdapter adapter = new FfmSensorsAdapter(api);
        adapter.fetchHardwareSnapshot();
        api.adapters.remove(DGPU.id());

        HardwareSnapshot afterRemoval = adapter.fetchHardwareSnapshot();
        HardwareSnapshot next = adapter.fetchHardwareSnapshot();

        assertEquals(MetricState.NO_DEVICE, afterRemoval.gpu(DGPU.id()).orElseThrow().temperatureCelsius().state());
        assertEquals(1, next.gpus().size(), "la siguiente lectura vuelve a enumerar");
    }

    @Test
    void descartaDatosDeOtroAdaptador() {
        api.reportedLuid.put(DGPU.id(), IGPU.id());

        GpuReading dgpu = new FfmSensorsAdapter(api).fetchHardwareSnapshot().gpu(DGPU.id()).orElseThrow();

        assertEquals(MetricState.READ_FAILED, dgpu.temperatureCelsius().state());
        assertFalse(dgpu.hasAnyValidMetric());
    }

    @Test
    void telemetriaNoInicializadaSeMuestraComoTal() {
        api.cpuStatus = NativeStatus.NOT_INITIALIZED;
        api.gpuPollStatus.put(IGPU.id(), NativeStatus.NOT_INITIALIZED);

        HardwareSnapshot snapshot = new FfmSensorsAdapter(api).fetchHardwareSnapshot();

        assertEquals(MetricState.NOT_INITIALIZED, snapshot.cpu().usagePercent().state());
        assertEquals(MetricState.NOT_INITIALIZED, snapshot.gpu(IGPU.id()).orElseThrow().temperatureCelsius().state());
        assertTrue(snapshot.memory().totalBytes().isValid(), "la memoria no requiere telemetría inicializada");
    }

    @Test
    void sinGpuFisicaLaListaQuedaVacia() {
        api.adapters.clear();

        HardwareSnapshot snapshot = new FfmSensorsAdapter(api).fetchHardwareSnapshot();

        assertFalse(snapshot.hasGpus());
    }

    @Test
    void interpretaUint32YUint64SinSignoYDescartaFloatsNoFinitos() {
        // Bytes escritos con los offsets de ABI.md, independientes de NativeLayouts.
        byte[] raw = V2Records.encodeGpu(api.gpuReadings.get(DGPU.id()).apply(1));
        MemorySegment record = MemorySegment.ofArray(raw);
        record.set(JAVA_FLOAT_UNALIGNED, 32, Float.NaN);                 // temperature_c.value
        record.set(JAVA_INT_UNALIGNED, 36, 0);                           // VALID
        record.set(JAVA_INT_UNALIGNED, 56, 0xFFFF_FFF0);                 // fan_rpm.value (uint32)
        record.set(JAVA_LONG_UNALIGNED, 88, 0x8000_0000_0000_0000L);     // vram total (uint64)
        api.rawGpuRecords.put(DGPU.id(), raw);

        GpuReading dgpu = new FfmSensorsAdapter(api).fetchHardwareSnapshot().gpu(DGPU.id()).orElseThrow();

        assertEquals(MetricState.READ_FAILED, dgpu.temperatureCelsius().state());
        assertEquals(4_294_967_280L, dgpu.fanRpm().value());
        assertEquals(Long.MAX_VALUE, dgpu.videoMemoryTotalBytes().value());
    }

    @Test
    void unRegistroConVersionDistintaSeRechaza() {
        byte[] raw = V2Records.encodeCpu(api.cpuReadings.apply(2));
        MemorySegment.ofArray(raw).set(JAVA_INT_UNALIGNED, 4, 3);
        api.rawCpuRecord = raw;

        NativeLibraryException error = assertThrows(NativeLibraryException.class,
                () -> new FfmSensorsAdapter(api).fetchHardwareSnapshot());
        assertTrue(error.getMessage().contains("MaceCpuTelemetryV2"));
    }

    @Test
    void unEstadoDesconocidoNuncaSeInterpretaComoValido() {
        byte[] raw = V2Records.encodeCpu(api.cpuReadings.apply(2));
        MemorySegment.ofArray(raw).set(JAVA_INT_UNALIGNED, 28, 99); // temperature_c.state
        api.rawCpuRecord = raw;

        CpuReading cpu = new FfmSensorsAdapter(api).fetchHardwareSnapshot().cpu();

        assertFalse(cpu.temperatureCelsius().isValid());
    }
}
