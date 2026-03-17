package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.EvaluarDocumentosUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.documentos.EvaluarDocumentosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Endpoints para la evaluación de documentos de un cliente.
 *
 * POST /api/v1/evaluaciones/{solicitudId}/documentos/evaluar
 *   Registra el resultado de la revisión documental en clientes_v1.evaluacionDocumentos
 *   y recalcula el scoreDocumental en solicitudes.
 */
@RestController
@RequestMapping("/api/v1/evaluaciones/{solicitudId}/documentos")
@RequiredArgsConstructor
@Tag(name = "Evaluación Documental", description = "Evaluación de documentos del titular y fiador")
public class DocumentosEvalController {

    private final EvaluarDocumentosUseCase evaluarDocumentosUseCase;

    @PostMapping("/evaluar")
    @Operation(
        summary = "Evaluar documentos de un cliente",
        description = "Registra la evaluación (aprobado/observado/rechazado) de cada documento " +
                      "en clientes_v1.evaluacionDocumentos y recalcula el scoreDocumental."
    )
    public Mono<ResponseEntity<EvaluarDocumentosResponse>> evaluar(
            @PathVariable String solicitudId,
            @RequestBody EvaluarDocumentosRequest request) {

        return evaluarDocumentosUseCase.ejecutar(solicitudId, request)
                .map(ResponseEntity::ok);
    }
}
