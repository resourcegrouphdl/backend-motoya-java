package com.motoyav2.evaluacion.application.port.in;

import reactor.core.publisher.Mono;

public interface CrearExpedienteUseCase {

    Mono<String> ejecutar(String codigoDeSolicitud, String formularioId);
}
