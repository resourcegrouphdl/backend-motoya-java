package com.motoyav2.calculadora.application.usecase;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import com.motoyav2.calculadora.domain.port.in.ObtenerConfiguracionUseCase;
import com.motoyav2.calculadora.domain.port.out.ConfiguracionCrediticiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ObtenerConfiguracionUseCaseImpl implements ObtenerConfiguracionUseCase {

    private final ConfiguracionCrediticiaRepository configRepo;

    @Override
    public Mono<ConfiguracionCrediticia> obtener() {
        return configRepo.findDefault();
    }
}
