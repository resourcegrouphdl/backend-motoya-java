package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.application.port.out.EvaluacionQueryPort;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente.ExpedienteDto;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.mapper.ExpedienteResponseMapper;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ObtenerExpedienteService implements ObtenerExpedienteUseCase {

    private final EvaluacionQueryPort evaluacionQueryPort;

    @Override
    public Mono<ExpedienteDto> ejecutar(String evaluacionId) {
        return evaluacionQueryPort.buscarPorEvaluacionId(evaluacionId)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Evaluación no encontrada: " + evaluacionId)))
                .map(ExpedienteResponseMapper::toExpedienteDto);
    }
}
