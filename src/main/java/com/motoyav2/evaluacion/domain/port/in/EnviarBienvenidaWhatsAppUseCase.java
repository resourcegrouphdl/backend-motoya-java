package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface EnviarBienvenidaWhatsAppUseCase {

    /**
     * Envía mensaje de bienvenida al titular y/o fiador al ingresar una solicitud.
     * Pregunta preferencia de horario para la entrevista.
     *
     * @param solicitudId   ID de la solicitud
     * @param telefono      Número del participante
     * @param nombre        Nombre completo del participante
     * @param esFiador      true = fiador, false = titular
     */
    Mono<Void> enviar(String solicitudId, String telefono, String nombre, boolean esFiador);
}
