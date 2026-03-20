package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ReemplazarReferenciasUseCase {
    Mono<Void> ejecutar(String solicitudId, List<IngresarSolicitudCommand.ReferenciaData> referencias);
}
