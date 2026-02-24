package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.EnviarMensajeWhatsappCommand;
import reactor.core.publisher.Mono;

public interface EnviarMensajeWhatsappUseCase {

    /** Envía el mensaje vía Twilio, lo persiste y registra el evento. Retorna el mensajeId. */
    Mono<String> ejecutar(EnviarMensajeWhatsappCommand command);
}
