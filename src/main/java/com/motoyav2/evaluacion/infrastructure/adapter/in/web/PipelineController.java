package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.TransicionarEstadoUseCase;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.service.MotorDePipeline;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints del motor de pipeline de evaluación.
 *
 * POST /api/v1/evaluaciones/{solicitudId}/pipeline/transicionar
 *   Transiciona el estado de una solicitud validando contra la máquina de estados.
 *
 * GET /api/v1/evaluaciones/{solicitudId}/pipeline/transiciones
 *   Retorna los estados válidos desde el estado actual de la solicitud.
 */
@RestController
@RequestMapping("/api/v1/evaluaciones/{solicitudId}/pipeline")
@RequiredArgsConstructor
@Tag(name = "Pipeline de Evaluación", description = "Gestión del flujo de estados de la solicitud")
public class PipelineController {

    private final TransicionarEstadoUseCase transicionarEstadoUseCase;
    private final MotorDePipeline motorDePipeline;

    @PostMapping("/transicionar")
    @Operation(
        summary = "Transicionar estado de solicitud",
        description = "Valida y aplica una transición de estado. Registra el cambio en cambios_estado_solicitud."
    )
    public Mono<ResponseEntity<TransicionarEstadoResponse>> transicionar(
            @PathVariable String solicitudId,
            @RequestBody TransicionarEstadoRequest request) {

        return transicionarEstadoUseCase.ejecutar(solicitudId, request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/transiciones")
    @Operation(
        summary = "Consultar transiciones disponibles",
        description = "Retorna los estados válidos desde el estado indicado."
    )
    public ResponseEntity<Map<String, Object>> transicionesDesde(@RequestParam String estado) {
        EstadoSolicitud estadoEnum = EstadoSolicitud.fromString(estado);
        Set<String> posibles = motorDePipeline.transicionesPosibles(estadoEnum)
                .stream().map(EstadoSolicitud::name).collect(Collectors.toSet());

        return ResponseEntity.ok(Map.of(
                "estadoActual", estadoEnum.name(),
                "esTerminal", estadoEnum.esTerminal(),
                "transicionesPosibles", posibles
        ));
    }
}
