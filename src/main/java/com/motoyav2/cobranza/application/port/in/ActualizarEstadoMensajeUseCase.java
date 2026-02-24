package com.motoyav2.cobranza.application.port.in;

import reactor.core.publisher.Mono;

public interface ActualizarEstadoMensajeUseCase {

    /**
     * Actualiza el estado de un mensaje WhatsApp a partir del webhook de Twilio.
     * Si no se encuentra el wamid se ignora silenciosamente (sin error).
     *
     * @param nuevoEstado EstadoMensajeWa: ENTREGADO | LEIDO | FALLIDO
     */
    Mono<Void> ejecutar(String wamid, String nuevoEstado, java.util.Date timestamp);
}
