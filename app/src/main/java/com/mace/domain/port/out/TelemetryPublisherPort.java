package com.mace.domain.port.out;

import com.mace.domain.model.TelemetrySnapshot;

import java.util.concurrent.Flow;

/**
 * Puerto de salida para la publicación reactiva de telemetría (Flow API estándar).
 *
 * El caso de uso publica cada snapshot; los interesados (por ejemplo, los
 * ViewModels de la capa de presentación) se suscriben sin acoplarse a la
 * implementación concreta del publicador.
 */
public interface TelemetryPublisherPort {

    /**
     * Publica un snapshot de telemetría a todos los suscriptores activos.
     *
     * @param snapshot lectura de telemetría a difundir
     */
    void publish(TelemetrySnapshot snapshot);

    /**
     * Registra un suscriptor reactivo que recibirá los snapshots publicados.
     *
     * @param subscriber suscriptor conforme a la Flow API de Java
     */
    void subscribe(Flow.Subscriber<? super TelemetrySnapshot> subscriber);
}
