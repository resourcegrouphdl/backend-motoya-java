package com.motoyav2.evaluacion.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.application.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente.ExpedienteApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/evaluaciones")
@RequiredArgsConstructor
public class ExpedienteController {

    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;

    @GetMapping("/{evaluacionId}/expediente")
    public Mono<ResponseEntity<ExpedienteApiResponse>> obtenerExpediente(
            @PathVariable String evaluacionId) {

        return obtenerExpedienteUseCase.ejecutar(evaluacionId)
                .map(expediente -> ResponseEntity.ok(
                        ExpedienteApiResponse.builder()
                                .success(true)
                                .expediente(expediente)
                                .build()));
    }
}
