package com.motoyav2.riesgointerno.domain.port.out;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface RegistroRiesgoRepository {

    Mono<RegistroRiesgo> findById(String id);

    Flux<RegistroRiesgo> findAll(String nivelRiesgo, String estadoRegistro, String search, int limit, int offset);

    Mono<Long> count(String nivelRiesgo, String estadoRegistro);

    /** Busca por teléfono exacto usando array-contains sobre el campo {@code telefonos}. */
    Flux<RegistroRiesgo> findByTelefono(String telefono);

    Flux<RegistroRiesgo> findByDni(String dni);

    Mono<RegistroRiesgo> create(RegistroRiesgo registro);

    Mono<Void> updateFields(String id, Map<String, Object> fields);

    Mono<Void> delete(String id);
}
