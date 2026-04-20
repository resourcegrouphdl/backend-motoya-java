package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface EliminarSolicitudUseCase {
    Mono<Void> eliminar(String solicitudId);
}
