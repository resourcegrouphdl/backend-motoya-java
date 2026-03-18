package com.motoyav2.evaluacion.domain.port.out;

import com.motoyav2.evaluacion.domain.model.Vehiculo;
import reactor.core.publisher.Mono;

public interface VehiculoRepository {
    Mono<Vehiculo> findById(String id);
}
