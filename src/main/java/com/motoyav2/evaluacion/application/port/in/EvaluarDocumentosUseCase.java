package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosResponse;
import reactor.core.publisher.Mono;

/**
 * Caso de uso: evaluar documentos de un cliente.
 * Escribe en clientes_v1.evaluacionDocumentos y recalcula scoreDocumental.
 */
public interface EvaluarDocumentosUseCase {
    Mono<EvaluarDocumentosResponse> ejecutar(String solicitudId, EvaluarDocumentosRequest request);
}
