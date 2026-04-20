package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.domain.model.PersonaResumen;
import reactor.core.publisher.Mono;

/**
 * Busca una persona por número de documento y retorna sus datos de autocomplete
 * junto con las alertas crediticias del historial interno.
 */
public interface BuscarPersonaUseCase {
    Mono<PersonaResumen> ejecutar(String documentNumber);
}
