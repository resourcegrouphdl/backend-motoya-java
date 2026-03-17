package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.DecisionFinalUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * POST /api/v1/evaluaciones/{solicitudId}/decision
 *   Registra la decisión final (APROBADO / RECHAZADO / CONDICIONAL).
 *   Calcula scores en tiempo real, aplica MotorDeDecision,
 *   permite override manual, y persiste en solicitudes.
 */
@RestController
@RequestMapping("/api/v1/evaluaciones/{solicitudId}")
@RequiredArgsConstructor
@Tag(name = "Decisión Final", description = "Motor de decisión crediticia")
public class DecisionController {

    private final DecisionFinalUseCase useCase;

    @PostMapping("/decision")
    @Operation(
        summary = "Registrar decisión final",
        description = "Calcula scores y riesgo en tiempo real, aplica el MotorDeDecision " +
                      "para obtener una recomendación automática, y persiste la decisión " +
                      "(con posibilidad de override manual) en la colección solicitudes. " +
                      "Transiciona el estado a aprobado | rechazado | condicional."
    )
    public Mono<ResponseEntity<DecisionFinalResponse>> decidir(
            @PathVariable String solicitudId,
            @RequestBody DecisionFinalRequest request) {

        return useCase.ejecutar(solicitudId, request)
                .map(ResponseEntity::ok);
    }
}
