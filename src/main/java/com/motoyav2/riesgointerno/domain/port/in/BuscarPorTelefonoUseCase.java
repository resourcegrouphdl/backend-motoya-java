package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import reactor.core.publisher.Flux;

public interface BuscarPorTelefonoUseCase {
    Flux<RegistroRiesgo> buscar(String telefono);
}
