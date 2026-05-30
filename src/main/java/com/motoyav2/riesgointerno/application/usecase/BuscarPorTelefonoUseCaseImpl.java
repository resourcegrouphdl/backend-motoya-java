package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import com.motoyav2.riesgointerno.domain.port.in.BuscarPorTelefonoUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class BuscarPorTelefonoUseCaseImpl implements BuscarPorTelefonoUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Flux<RegistroRiesgo> buscar(String telefono) {
        return repository.findByTelefono(telefono);
    }
}
