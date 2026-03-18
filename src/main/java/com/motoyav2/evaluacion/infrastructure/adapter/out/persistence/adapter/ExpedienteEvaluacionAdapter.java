package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.ExpedientePort;
import com.motoyav2.evaluacion.domain.model.ExpedienteDeEvaluacion;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * La solicitud ya existe en 'solicitudes' (creada por el frontend).
 * No se persiste nada en 'evaluacionDeCredito' (colección eliminada).
 * Solo confirmamos la operación devolviendo el ID de la solicitud.
 */
@Component
public class ExpedienteEvaluacionAdapter implements ExpedientePort {

    @Override
    public Mono<String> guardar(ExpedienteDeEvaluacion expediente) {
        String id = expediente.getEvaluacion().getSolicitudFirebaseId();
        if (id == null || id.isBlank()) {
            id = expediente.getEvaluacion().getCodigoDeSolicitud();
        }
        return Mono.just(id != null ? id : "");
    }
}