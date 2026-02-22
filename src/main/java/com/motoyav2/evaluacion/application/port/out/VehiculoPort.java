package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.Vehiculo;
import reactor.core.publisher.Mono;

public interface VehiculoPort {

    Mono<Vehiculo> buscarPorId(String vehiculoId);
}
