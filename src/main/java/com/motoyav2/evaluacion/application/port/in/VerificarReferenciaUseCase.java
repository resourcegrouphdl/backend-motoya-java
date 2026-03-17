package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaResponse;
import reactor.core.publisher.Mono;

public interface VerificarReferenciaUseCase {
    Mono<VerificarReferenciaResponse> ejecutar(
            String solicitudId,
            String referenciaId,
            VerificarReferenciaRequest request);
}
