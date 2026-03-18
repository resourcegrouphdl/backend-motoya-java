package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.AjustarFinanciamientoCommand;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface AjustarFinanciamientoUseCase {
    Mono<Map<String, Object>> ejecutar(AjustarFinanciamientoCommand command);
}
