package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.port.in.ListarRegistrosUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ListarRegistrosUseCaseImpl implements ListarRegistrosUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<PagedResult> listar(String nivelRiesgo, String estadoRegistro, String search, int page, int size) {
        int offset = page * size;
        return Mono.zip(
                repository.findAll(nivelRiesgo, estadoRegistro, search, size, offset).collectList(),
                repository.count(nivelRiesgo, estadoRegistro)
        ).map(t -> new PagedResult(t.getT1(), t.getT2(), page, size));
    }
}
