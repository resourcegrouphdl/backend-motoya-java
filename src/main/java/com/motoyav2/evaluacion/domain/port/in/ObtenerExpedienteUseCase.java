package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.domain.model.Expediente;
import reactor.core.publisher.Mono;

public interface ObtenerExpedienteUseCase {
    Mono<Expediente> ejecutar(String solicitudId);
}
