package com.mace.domain.port.out;

import com.mace.domain.model.HardwareSnapshot;

import java.util.concurrent.Flow;

/**
 * Puerto de salida para la publicación reactiva de telemetría V2 (Flow API estándar).
 *
 * <p>Es el equivalente V2 de {@link TelemetryPublisherPort}: publica
 * {@link HardwareSnapshot} con estado por métrica. Se mantiene separado del puerto
 * V1 para no romper sus consumidores durante la migración; Dev 4 puede suscribirse
 * aquí para mostrar la telemetría V2.</p>
 */
public interface TelemetryPublisherV2Port {

    /**
     * Publica un snapshot V2 a todos los suscriptores activos.
     *
     * @param snapshot lectura de telemetría V2 a difundir
     */
    void publish(HardwareSnapshot snapshot);

    /**
     * Registra un suscriptor reactivo que recibirá los snapshots V2 publicados.
     *
     * @param subscriber suscriptor conforme a la Flow API de Java
     */
    void subscribe(Flow.Subscriber<? super HardwareSnapshot> subscriber);
}
