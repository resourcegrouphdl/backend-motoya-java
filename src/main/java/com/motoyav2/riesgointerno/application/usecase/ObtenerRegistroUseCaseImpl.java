package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import com.motoyav2.riesgointerno.domain.port.in.ObtenerRegistroUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ObtenerRegistroUseCaseImpl implements ObtenerRegistroUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<RegistroRiesgo> obtener(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Registro de riesgo no encontrado: " + id)));
    }
}
