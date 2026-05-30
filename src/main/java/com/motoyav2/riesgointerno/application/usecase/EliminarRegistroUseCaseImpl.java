package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.port.in.EliminarRegistroUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class EliminarRegistroUseCaseImpl implements EliminarRegistroUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<Void> eliminar(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Registro de riesgo no encontrado: " + id)))
                .flatMap(r -> repository.delete(id));
    }
}
