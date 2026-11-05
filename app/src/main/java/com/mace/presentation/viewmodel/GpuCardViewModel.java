package com.mace.presentation.viewmodel;

import com.mace.domain.model.GpuAdapterId;
import com.mace.domain.model.GpuReading;
import com.mace.domain.model.MetricState;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.Objects;

/** Estado visual de una GPU concreta, identificado por su LUID durante esta sesión. */
public final class GpuCardViewModel {

    private static final double GIB = 1024.0 * 1024.0 * 1024.0;

    private final GpuAdapterId id;
    private final ReadOnlyStringWrapper name = new ReadOnlyStringWrapper("GPU");
    private final ReadOnlyStringWrapper vendor = new ReadOnlyStringWrapper("Otro");
    private final ReadOnlyStringWrapper provider = new ReadOnlyStringWrapper("Ninguno");
    private final ReadOnlyStringWrapper powerScope = new ReadOnlyStringWrapper("Ámbito desconocido");
    private final ReadOnlyBooleanWrapper inactive = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<MetricDisplay> temperature = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> power = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> usage = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> fanRpm = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> fanPercent = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> vramUsed = missing();
    private final ReadOnlyObjectWrapper<MetricDisplay> vramTotal = missing();
    private final ReadOnlyObjectWrapper<GpuReading> reading = new ReadOnlyObjectWrapper<>();
    private final MetricHistory temperatureHistory;
    private final MetricHistory usageHistory;
    private final MetricHistory powerHistory;

    GpuCardViewModel(GpuAdapterId id, int historyCapacity) {
        this.id = Objects.requireNonNull(id, "id no puede ser null");
        temperatureHistory = new MetricHistory(historyCapacity);
        usageHistory = new MetricHistory(historyCapacity);
        powerHistory = new MetricHistory(historyCapacity);
    }

    void update(GpuReading next, long hardwareSequence) {
        Objects.requireNonNull(next, "next no puede ser null");
        if (!id.equals(next.adapterId())) {
            throw new IllegalArgumentException("La lectura pertenece a otra GPU: " + next.adapterId());
        }
        reading.set(next);
        name.set(next.adapter().displayName());
        vendor.set(next.adapter().vendor().label());
        provider.set(next.provider().label());
        powerScope.set(next.powerScope().label());
        inactive.set(next.isInactive());
        temperature.set(MetricDisplay.decimal(next.temperatureCelsius(), "°C", 1));
        power.set(MetricDisplay.decimal(next.powerWatts(), "W", 1));
        usage.set(MetricDisplay.decimal(next.usagePercent(), "%", 1));
        fanRpm.set(MetricDisplay.whole(next.fanRpm(), "RPM"));
        fanPercent.set(MetricDisplay.whole(next.fanPercent(), "%"));
        vramUsed.set(MetricDisplay.scaled(next.videoMemoryUsedBytes(), GIB, "GiB", 1));
        vramTotal.set(MetricDisplay.scaled(next.videoMemoryTotalBytes(), GIB, "GiB", 1));
        temperatureHistory.append(hardwareSequence, next.temperatureCelsius());
        usageHistory.append(hardwareSequence, next.usagePercent());
        powerHistory.append(hardwareSequence, next.powerWatts());
    }

    private static ReadOnlyObjectWrapper<MetricDisplay> missing() {
        return new ReadOnlyObjectWrapper<>(MetricDisplay.unavailable(MetricState.NOT_INITIALIZED));
    }

    public GpuAdapterId id() { return id; }
    public ReadOnlyStringProperty nameProperty() { return name.getReadOnlyProperty(); }
    public ReadOnlyStringProperty vendorProperty() { return vendor.getReadOnlyProperty(); }
    public ReadOnlyStringProperty providerProperty() { return provider.getReadOnlyProperty(); }
    public ReadOnlyStringProperty powerScopeProperty() { return powerScope.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty inactiveProperty() { return inactive.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> temperatureProperty() { return temperature.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> powerProperty() { return power.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> usageProperty() { return usage.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> fanRpmProperty() { return fanRpm.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> fanPercentProperty() { return fanPercent.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> vramUsedProperty() { return vramUsed.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<MetricDisplay> vramTotalProperty() { return vramTotal.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<GpuReading> readingProperty() { return reading.getReadOnlyProperty(); }
    public MetricHistory temperatureHistory() { return temperatureHistory; }
    public MetricHistory usageHistory() { return usageHistory; }
    public MetricHistory powerHistory() { return powerHistory; }
}
