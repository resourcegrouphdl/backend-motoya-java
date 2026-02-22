package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente.ExpedienteDto;
import reactor.core.publisher.Mono;

public interface ObtenerExpedienteUseCase {

    Mono<ExpedienteDto> ejecutar(String evaluacionId);
}
