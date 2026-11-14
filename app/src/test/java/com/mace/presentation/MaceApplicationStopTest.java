package com.mace.presentation;

import com.mace.application.usecase.MonitoringService;
import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.infrastructure.telemetry.SubmissionTelemetryPublisher;
import com.mace.presentation.view.MainDashboardController;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaceApplicationStopTest {
    @Test void unErrorEnLaVistaNoImpideCerrarMonitorYTelemetriaNativa() throws Exception {
        var app=new MaceApplication();
        var controller=mock(MainDashboardController.class);
        var monitoring=mock(MonitoringService.class);
        var viewModel=mock(MainDashboardViewModel.class);
        var publisher=mock(SubmissionTelemetryPublisher.class);
        var adapters=mock(InfrastructureAdapters.class);
        set(app,"dashboardController",controller); set(app,"monitoringService",monitoring);
        set(app,"dashboardViewModel",viewModel); set(app,"telemetryPublisher",publisher); set(app,"adapters",adapters);
        doThrow(new IllegalStateException("vista")).when(controller).close();
        assertThrows(IllegalStateException.class,app::stop);
        var order=inOrder(controller,monitoring,viewModel,publisher,adapters);
        order.verify(controller).close(); order.verify(monitoring).close(); order.verify(viewModel).close();
        order.verify(publisher).close(); order.verify(adapters).close();
    }
    private static void set(Object target,String name,Object value) throws Exception {
        var field=MaceApplication.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
