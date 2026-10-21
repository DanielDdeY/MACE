package com.mace.domain.port.out;

import com.mace.domain.model.MonitoringSnapshot;

import java.util.concurrent.Flow;

/**
 * Puerto de salida para difundir cada {@link MonitoringSnapshot} (Flow API estándar).
 *
 * <p>Dashboard y Micro-HUD se suscriben aquí: comparten el mismo flujo y nunca crean un segundo
 * lector de hardware. Cancelar la suscripción (cerrar el HUD) no afecta al sondeo.</p>
 */
public interface MonitoringPublisherPort {

    void publish(MonitoringSnapshot snapshot);

    void subscribeMonitoring(Flow.Subscriber<? super MonitoringSnapshot> subscriber);
}
