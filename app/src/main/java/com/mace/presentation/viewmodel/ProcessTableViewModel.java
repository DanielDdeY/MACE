package com.mace.presentation.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.function.Consumer;

/**
 * ViewModel de la tabla de procesos.
 * Envuelve la lista observable del dashboard con filtro de busqueda por texto
 * y soporte de ordenamiento (via SortedList, se conecta al TableView.comparatorProperty()).
 */
public class ProcessTableViewModel {

    private final StringProperty searchText = new SimpleStringProperty("");
    private final FilteredList<ProcessRow> filteredProcesses;
    private final SortedList<ProcessRow> sortedProcesses;

    /** Accion que ejecuta el caso de uso real de terminacion (inyectada desde el controller). */
    private Consumer<ProcessRow> onTerminateRequested = row -> { /* no-op por defecto */ };

    public ProcessTableViewModel(ObservableList<ProcessRow> sourceProcesses) {
        this.filteredProcesses = new FilteredList<>(sourceProcesses, p -> true);
        this.sortedProcesses = new SortedList<>(filteredProcesses);

        searchText.addListener((obs, oldVal, newVal) -> applyFilter(newVal));
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredProcesses.setPredicate(row ->
                q.isEmpty()
                        || row.name().toLowerCase().contains(q)
                        || String.valueOf(row.pid()).contains(q)
        );
    }

    /** Comando invocado por la vista cuando el usuario confirma "Finalizar Proceso". */
    public void requestTerminate(ProcessRow row) {
        onTerminateRequested.accept(row);
    }

    public void setOnTerminateRequested(Consumer<ProcessRow> handler) {
        this.onTerminateRequested = handler;
    }

    public StringProperty searchTextProperty() { return searchText; }

    /** Lista lista para TableView.setItems(...); bindear tableView.comparatorProperty() a sortedProcesses.comparatorProperty(). */
    public SortedList<ProcessRow> getSortedProcesses() { return sortedProcesses; }
}