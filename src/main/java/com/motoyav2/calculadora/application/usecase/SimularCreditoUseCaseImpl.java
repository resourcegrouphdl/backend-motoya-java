package com.motoyav2.calculadora.application.usecase;

import com.motoyav2.calculadora.domain.model.ParametrosSimulacion;
import com.motoyav2.calculadora.domain.model.ResultadoSimulacion;
import com.motoyav2.calculadora.domain.port.in.SimularCreditoUseCase;
import com.motoyav2.calculadora.domain.port.out.ConfiguracionCrediticiaRepository;
import com.motoyav2.calculadora.domain.service.MotorCalculoCrediticio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SimularCreditoUseCaseImpl implements SimularCreditoUseCase {

    private final ConfiguracionCrediticiaRepository configRepo;

    @Override
    public Mono<ResultadoSimulacion> simular(ParametrosSimulacion params) {
        return configRepo.findDefault()
                .map(config -> MotorCalculoCrediticio.calcular(params, config));
    }
}
