package com.mace.presentation;

import com.mace.application.usecase.MonitoringService;
import com.mace.infrastructure.InfrastructureAdapters;
import com.mace.infrastructure.telemetry.SubmissionTelemetryPublisher;
import com.mace.presentation.view.MainDashboardController;
import com.mace.presentation.viewmodel.MainDashboardViewModel;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaceApplicationStopTest {
    @Test void unErrorEnLaVistaNoImpideCerrarMonitorYTelemetriaNativa() throws Exception {
        var app=new MaceApplication(new ShutdownCoordinator(java.time.Duration.ofSeconds(10), code -> {}));
        var controller=mock(MainDashboardController.class);
        var monitoring=mock(MonitoringService.class);
        var viewModel=mock(MainDashboardViewModel.class);
        var publisher=mock(SubmissionTelemetryPublisher.class);
        var adapters=mock(InfrastructureAdapters.class);
        set(app,"dashboardController",controller); set(app,"monitoringService",monitoring);
        set(app,"dashboardViewModel",viewModel); set(app,"telemetryPublisher",publisher); set(app,"adapters",adapters);
        doThrow(new IllegalStateException("vista")).when(controller).close();
        var closed=new CountDownLatch(1);
        var caller=Thread.currentThread();
        doAnswer(invocation -> { assertNotSame(caller,Thread.currentThread()); closed.countDown(); return null; }).when(adapters).close();
        assertDoesNotThrow(app::stop);
        assertTrue(closed.await(3,TimeUnit.SECONDS));
        assertDoesNotThrow(app::stop);
        var order=inOrder(controller,viewModel,monitoring,publisher,adapters);
        order.verify(controller).close(); order.verify(viewModel).close(); order.verify(monitoring).close();
        order.verify(publisher).close(); order.verify(adapters).close();
        verify(adapters,times(1)).close();
    }
    private static void set(Object target,String name,Object value) throws Exception {
        var field=MaceApplication.class.getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
