package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface EnviarVerificacionWhatsAppUseCase {
    /**
     * Envía un mensaje WhatsApp a la referencia solicitando confirmación,
     * y actualiza el estado de la referencia a "wa_enviado".
     *
     * @param referenciaId ID de la referencia
     * @param solicitudId  ID de la solicitud (para obtener nombre del titular y para umbral)
     */
    Mono<Void> ejecutar(String referenciaId, String solicitudId);
}
