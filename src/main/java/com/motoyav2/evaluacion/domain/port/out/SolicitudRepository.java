package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Solicitud;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface SolicitudRepository {
    Mono<Solicitud> findById(String id);
    Mono<Solicitud> findByNumeroSolicitud(String numeroSolicitud);
    Flux<Solicitud> findByEstado(String estado, int limit, int offset);
    Flux<Solicitud> findAll(String estado, String prioridad, String search, int limit, int offset);
    Mono<Long> countAll(String estado, String prioridad, String search);
    Flux<Solicitud> findByVendedorId(String vendedorId, int limit, int offset);
    Mono<Long> countByVendedorId(String vendedorId);
    Mono<String> create(Map<String, Object> fields);
    Mono<Void> updateFields(String id, Map<String, Object> fields);
}
