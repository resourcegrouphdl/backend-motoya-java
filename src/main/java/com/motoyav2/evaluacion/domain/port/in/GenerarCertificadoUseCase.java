package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface GenerarCertificadoUseCase {
    Mono<String> ejecutar(String numeroSolicitud);
}
