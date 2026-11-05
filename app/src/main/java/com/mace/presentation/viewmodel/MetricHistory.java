package com.mace.presentation.viewmodel;

import com.mace.domain.model.MetricReading;
import com.mace.domain.model.MetricState;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Historial acotado por sondeo físico. Conserva estados inválidos como cortes de línea. */
public final class MetricHistory {

    public record Point(long hardwareSequence, MetricState state, OptionalDouble value) {
        public Point {
            if (hardwareSequence <= 0) {
                throw new IllegalArgumentException("hardwareSequence debe ser positivo");
            }
            Objects.requireNonNull(state, "state no puede ser null");
            Objects.requireNonNull(value, "value no puede ser null");
            if (state.isValid() != value.isPresent()) {
                throw new IllegalArgumentException("El valor opcional debe coincidir con el estado");
            }
        }

        public boolean isValid() {
            return state.isValid();
        }
    }

    private final int capacity;
    private final ObservableList<Point> mutablePoints = FXCollections.observableArrayList();
    private final ObservableList<Point> points = FXCollections.unmodifiableObservableList(mutablePoints);
    private long lastSequence;

    public MetricHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity debe ser positiva");
        }
        this.capacity = capacity;
    }

    /** Ignora refrescos de procesos y snapshots atrasados con la misma secuencia física. */
    public void append(long hardwareSequence, MetricReading reading) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        if (hardwareSequence <= lastSequence) {
            return;
        }
        mutablePoints.add(new Point(hardwareSequence, reading.state(), reading.asOptional()));
        lastSequence = hardwareSequence;
        if (mutablePoints.size() > capacity) {
            mutablePoints.remove(0, mutablePoints.size() - capacity);
        }
    }

    public ObservableList<Point> points() {
        return points;
    }

    /** Tramos válidos independientes: la vista no debe unir puntos a través de N/D. */
    public List<List<Point>> validSegments() {
        List<List<Point>> segments = new ArrayList<>();
        List<Point> current = new ArrayList<>();
        for (Point point : points) {
            if (point.isValid()) {
                current.add(point);
            } else if (!current.isEmpty()) {
                segments.add(List.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            segments.add(List.copyOf(current));
        }
        return List.copyOf(segments);
    }
}
