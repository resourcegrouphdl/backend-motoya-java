package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoResponse;
import reactor.core.publisher.Mono;

/**
 * Caso de uso: transicionar el estado de una solicitud.
 * Valida la transición contra el motor de pipeline antes de persistir.
 */
public interface TransicionarEstadoUseCase {
    Mono<TransicionarEstadoResponse> ejecutar(String solicitudId, TransicionarEstadoRequest request);
}
