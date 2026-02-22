package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.ExpedienteSeed;
import reactor.core.publisher.Mono;

public interface SolicitudPort {

    Mono<ExpedienteSeed> obtenerSolicitud(String formularioId);
}
