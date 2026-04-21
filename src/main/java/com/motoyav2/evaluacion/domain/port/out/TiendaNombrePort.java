package com.motoyav2.evaluacion.domain.port.out;

import reactor.core.publisher.Mono;

/** Resuelve el nombre comercial de una tienda a partir de su UID. */
public interface TiendaNombrePort {
    /** Devuelve el businessName de tienda_profiles/{tiendaId}, o el tiendaId si no existe. */
    Mono<String> resolverNombre(String tiendaId);
}
