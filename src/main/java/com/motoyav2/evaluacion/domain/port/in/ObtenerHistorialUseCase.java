package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import reactor.core.publisher.Flux;

public interface ObtenerHistorialUseCase {
    Flux<HistorialEstado> ejecutar(String solicitudId);
}
