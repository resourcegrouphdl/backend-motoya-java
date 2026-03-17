package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.VerificarReferenciaUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias.VerificarReferenciaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * POST /api/v1/evaluaciones/{solicitudId}/referencias/{referenciaId}/verificar
 *   Registra el resultado de la verificación de una referencia personal.
 *   Recalcula scoreReferencias sobre todas las referencias de la solicitud.
 */
@RestController
@RequestMapping("/api/v1/evaluaciones/{solicitudId}/referencias")
@RequiredArgsConstructor
@Tag(name = "Verificación de Referencias", description = "Gestión de referencias del titular")
public class ReferenciaVerificacionController {

    private final VerificarReferenciaUseCase useCase;

    @PostMapping("/{referenciaId}/verificar")
    @Operation(
        summary = "Verificar referencia",
        description = "Registra el resultado de la verificación de una referencia " +
                      "(estadoVerificacion, score, observaciones). " +
                      "Recalcula scoreReferencias para la solicitud."
    )
    public Mono<ResponseEntity<VerificarReferenciaResponse>> verificar(
            @PathVariable String solicitudId,
            @PathVariable String referenciaId,
            @RequestBody VerificarReferenciaRequest request) {

        return useCase.ejecutar(solicitudId, referenciaId, request)
                .map(ResponseEntity::ok);
    }
}
