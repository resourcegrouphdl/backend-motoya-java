package com.motoyav2.calculadora.application.usecase;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import com.motoyav2.calculadora.domain.port.in.ActualizarConfiguracionUseCase;
import com.motoyav2.calculadora.domain.port.out.ConfiguracionCrediticiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ActualizarConfiguracionUseCaseImpl implements ActualizarConfiguracionUseCase {

    private final ConfiguracionCrediticiaRepository configRepo;

    @Override
    public Mono<ConfiguracionCrediticia> actualizar(ConfiguracionCrediticia nuevaConfig, String usuarioId) {
        ConfiguracionCrediticia toSave = nuevaConfig.toBuilder()
                .id("default")
                .actualizadoEn(Instant.now())
                .actualizadoPor(usuarioId)
                .build();
        return configRepo.save(toSave);
    }
}
