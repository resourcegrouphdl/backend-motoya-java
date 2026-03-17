package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse;
import reactor.core.publisher.Mono;

/**
 * Caso de uso: obtener el expediente completo de una solicitud,
 * con scores calculados y perfil de riesgo.
 *
 * @param solicitudId ID del documento en la colección `solicitudes` (formularioId)
 */
public interface ObtenerExpedienteCompletoUseCase {
    Mono<ExpedienteCompletoResponse> ejecutar(String solicitudId);
}
