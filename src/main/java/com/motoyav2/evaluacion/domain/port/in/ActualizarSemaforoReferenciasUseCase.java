package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

/** Recalcula y persiste el semáforo de referencias de una solicitud. */
public interface ActualizarSemaforoReferenciasUseCase {
    Mono<Void> actualizar(String solicitudId);
}
