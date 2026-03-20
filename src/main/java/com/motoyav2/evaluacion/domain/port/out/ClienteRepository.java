package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Cliente;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ClienteRepository {
    Mono<Cliente> findById(String id);
    Mono<Cliente> findByDocumentNumber(String documentNumber);
    Mono<String> create(Map<String, Object> fields);
    Mono<Void> updateFields(String id, Map<String, Object> fields);
}
