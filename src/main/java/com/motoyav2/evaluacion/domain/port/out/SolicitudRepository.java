package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Solicitud;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface SolicitudRepository {
    Mono<Solicitud> findById(String id);
    Mono<Solicitud> findByNumeroSolicitud(String numeroSolicitud);
    Flux<Solicitud> findByEstado(String estado, int limit, int offset);
    Flux<Solicitud> findAll(String estado, String prioridad, String search, String tiendaId, int limit, int offset);
    Mono<Long> countAll(String estado, String prioridad, String search, String tiendaId);
    Flux<Solicitud> findByVendedorId(String vendedorId, int limit, int offset);
    Mono<Long> countByVendedorId(String vendedorId);
    /** Busca solicitudes donde la persona fue titular (para central de riesgo). */
    Flux<Solicitud> findByTitularDni(String titularDni, int limit);
    /** Busca solicitudes donde la persona fue fiador (para central de riesgo). */
    Flux<Solicitud> findByFiadorDni(String fiadorDni, int limit);
    /** Busca la solicitud activa más reciente para un teléfono de titular (para webhook dispatcher). */
    Mono<Solicitud> findActivaByTitularTelefono(String telefono);
    /** Busca la solicitud activa más reciente para un teléfono de fiador (para webhook dispatcher). */
    Mono<Solicitud> findActivaByFiadorTelefono(String telefono);
    /** Solicitudes en estados tempranos sin actividad en los últimos N días. */
    Flux<Solicitud> findAbandonadas(int diasInactividad);
    Mono<String> create(Map<String, Object> fields);
    Mono<Void> updateFields(String id, Map<String, Object> fields);
    Mono<Void> delete(String id);
}
