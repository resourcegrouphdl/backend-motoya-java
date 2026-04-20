package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ProcesarPreferenciaEntrevistaUseCase {

    /**
     * Procesa la respuesta de WhatsApp de un titular o fiador.
     * Intenta extraer una preferencia de horario con Claude.
     * Actualiza el campo entrevista en la solicitud.
     *
     * @param solicitudId       ID de la solicitud
     * @param fromPhone         Teléfono del remitente
     * @param textoRespuesta    Texto del mensaje recibido
     * @param esFiador          true = fiador, false = titular
     */
    Mono<Void> procesar(String solicitudId, String fromPhone, String textoRespuesta, boolean esFiador);
}
