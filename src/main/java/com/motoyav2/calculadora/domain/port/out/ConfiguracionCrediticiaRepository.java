package com.motoyav2.calculadora.domain.port.out;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import reactor.core.publisher.Mono;

public interface ConfiguracionCrediticiaRepository {
    Mono<ConfiguracionCrediticia> findDefault();
    Mono<ConfiguracionCrediticia> save(ConfiguracionCrediticia config);
}
