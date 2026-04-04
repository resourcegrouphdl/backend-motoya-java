package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Referencia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface ReferenciaRepository {
    Mono<Referencia> findById(String id);
    Flux<Referencia> findByIds(List<String> ids);
    /** Busca la referencia activa con ese teléfono en estado "wa_enviado" (para correlación webhook). */
    Mono<Referencia> findByTelefonoAndEstadoWaEnviado(String telefono);
    Mono<String> create(Map<String, Object> fields);
    Mono<Void> updateFields(String id, Map<String, Object> fields);
}
