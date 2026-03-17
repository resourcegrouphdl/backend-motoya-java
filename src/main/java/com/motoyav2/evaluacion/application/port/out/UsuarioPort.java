package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.AsesorDto;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para consultar datos de usuarios internos del sistema.
 * Lee de la colección `usuarios`.
 */
public interface UsuarioPort {
    Mono<AsesorDto> buscarPorId(String usuarioId);
}
