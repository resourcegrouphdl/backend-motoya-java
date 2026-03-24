package com.motoyav2.calculadora.domain.port.in;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import reactor.core.publisher.Mono;

public interface ObtenerConfiguracionUseCase {
    Mono<ConfiguracionCrediticia> obtener();
}