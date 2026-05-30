package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.enums.NivelRiesgo;
import reactor.core.publisher.Mono;

public interface CambiarNivelRiesgoUseCase {
    Mono<Void> cambiarNivel(String id, NivelRiesgo nuevoNivel, String motivo, String uid);
}
