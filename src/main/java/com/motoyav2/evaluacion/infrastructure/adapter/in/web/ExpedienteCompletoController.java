package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.ObtenerExpedienteCompletoUseCase;
import com.motoyav2.evaluacion.application.port.in.ObtenerPreContratoUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.precontrato.PreContratoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint principal para obtener el expediente completo de una solicitud.
 *
 * A diferencia de /api/v1/evaluaciones/{evaluacionId}/expediente (que lee de evaluacionDeCredito),
 * este endpoint lee DIRECTAMENTE de las colecciones Firebase originales y calcula
 * scores y perfil de riesgo en tiempo real.
 *
 * GET /api/v1/expediente/{solicitudId}
 *   solicitudId = formularioId (document ID en colección `solicitudes`)
 */
@RestController
@RequestMapping("/api/v1/expediente")
@RequiredArgsConstructor
@Tag(name = "Expediente Completo", description = "Expediente de crédito con scores calculados y análisis de riesgo")
public class ExpedienteCompletoController {

    private final ObtenerExpedienteCompletoUseCase useCase;
    private final ObtenerPreContratoUseCase preContratoUseCase;

    @GetMapping("/{solicitudId}")
    @Operation(
        summary = "Obtener expediente completo",
        description = "Retorna el expediente completo (titular, fiador, vehículo, referencias) " +
                      "con scores calculados en backend y perfil de riesgo. " +
                      "solicitudId = formularioId del documento en la colección `solicitudes`."
    )
    public Mono<ResponseEntity<ExpedienteCompletoResponse>> obtener(
            @Parameter(description = "ID del documento en la colección solicitudes (formularioId)")
            @PathVariable String solicitudId) {

        return useCase.ejecutar(solicitudId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{solicitudId}/pre-contrato")
    @Operation(
        summary = "Obtener datos pre-llenados para crear contrato",
        description = "Retorna los datos del expediente aprobado mapeados al formato de " +
                      "CrearContratoManualRequest. El frontend puede usar estos datos para " +
                      "pre-llenar el formulario de creación de contrato y luego llamar " +
                      "POST /api/v1/contract para crear el contrato."
    )
    public Mono<ResponseEntity<PreContratoResponse>> preContrato(
            @Parameter(description = "ID del documento en la colección solicitudes (formularioId)")
            @PathVariable String solicitudId) {

        return preContratoUseCase.ejecutar(solicitudId)
                .map(ResponseEntity::ok);
    }
}
