package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Usuario;
import reactor.core.publisher.Mono;

public interface UsuarioRepository {
    Mono<Usuario> findById(String id);
}
