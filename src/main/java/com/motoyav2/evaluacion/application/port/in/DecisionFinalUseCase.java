package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalResponse;
import reactor.core.publisher.Mono;

public interface DecisionFinalUseCase {
    Mono<DecisionFinalResponse> ejecutar(String solicitudId, DecisionFinalRequest request);
}
