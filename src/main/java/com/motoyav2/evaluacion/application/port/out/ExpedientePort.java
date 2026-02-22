package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.ExpedienteDeEvaluacion;
import reactor.core.publisher.Mono;

public interface ExpedientePort {

    Mono<String> guardar(ExpedienteDeEvaluacion expediente);
}
