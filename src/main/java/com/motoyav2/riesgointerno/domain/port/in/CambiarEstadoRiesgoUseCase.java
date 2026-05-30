package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import reactor.core.publisher.Mono;

public interface CambiarEstadoRiesgoUseCase {
    Mono<Void> cambiarEstado(String id, EstadoRegistro nuevoEstado, String motivo, String uid);
}
