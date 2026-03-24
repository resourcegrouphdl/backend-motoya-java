package com.motoyav2.calculadora.domain.port.in;

import com.motoyav2.calculadora.domain.model.ParametrosSimulacion;
import com.motoyav2.calculadora.domain.model.ResultadoSimulacion;
import reactor.core.publisher.Mono;

public interface SimularCreditoUseCase {
    Mono<ResultadoSimulacion> simular(ParametrosSimulacion params);
}