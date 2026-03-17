package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.assembler.ExpedienteCompletoAssembler;
import com.motoyav2.evaluacion.application.port.in.ObtenerExpedienteCompletoUseCase;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto.ExpedienteCompletoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ObtenerExpedienteCompletoService implements ObtenerExpedienteCompletoUseCase {

    private final ExpedienteCompletoAssembler assembler;

    @Override
    public Mono<ExpedienteCompletoResponse> ejecutar(String solicitudId) {
        return assembler.ensamblar(solicitudId);
    }
}
