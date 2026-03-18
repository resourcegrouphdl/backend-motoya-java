package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HistorialEstadoRepository {
    Mono<Void> save(HistorialEstado historial);
    Flux<HistorialEstado> findBySolicitudId(String solicitudId);
}
