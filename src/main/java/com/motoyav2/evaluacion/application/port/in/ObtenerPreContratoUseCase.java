package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse;
import reactor.core.publisher.Mono;

/**
 * Genera los datos pre-llenados para crear un contrato a partir de un expediente aprobado.
 *
 * @param solicitudId ID del documento en la colección `solicitudes` (formularioId)
 */
public interface ObtenerPreContratoUseCase {
    Mono<PreContratoResponse> ejecutar(String solicitudId);
}
