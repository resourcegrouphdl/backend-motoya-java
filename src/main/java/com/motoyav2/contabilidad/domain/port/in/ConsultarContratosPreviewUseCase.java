package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ContratosPreviewResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConsultarContratosPreviewUseCase {
    Mono<ContratosPreviewResponse> consultarPreview(LocalDate desde, LocalDate hasta, String tiendaId);
}
