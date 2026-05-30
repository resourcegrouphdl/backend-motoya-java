package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import com.motoyav2.riesgointerno.domain.port.in.BuscarPorDniUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class BuscarPorDniUseCaseImpl implements BuscarPorDniUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Flux<RegistroRiesgo> buscar(String dni) {
        return repository.findByDni(dni);
    }
}
