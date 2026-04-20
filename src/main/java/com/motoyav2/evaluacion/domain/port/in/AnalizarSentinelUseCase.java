package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.AnalizarSentinelCommand;
import com.motoyav2.evaluacion.application.dto.AnalizarSentinelResult;
import reactor.core.publisher.Mono;

public interface AnalizarSentinelUseCase {
    Mono<AnalizarSentinelResult> analizar(AnalizarSentinelCommand command);
}
