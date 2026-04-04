package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ProcesarRespuestaReferenciaUseCase {
    /**
     * Procesa la respuesta de WhatsApp de una referencia:
     * clasifica con Claude, actualiza estado y verifica umbral de aprobación.
     *
     * @param fromPhone   Número de teléfono del remitente (formato Meta: "51XXXXXXXXX" o "9XXXXXXXX")
     * @param messageText Texto del mensaje recibido
     */
    Mono<Void> ejecutar(String fromPhone, String messageText);
}
