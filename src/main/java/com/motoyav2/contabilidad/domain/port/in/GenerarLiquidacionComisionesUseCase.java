package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ReporteLiquidacionResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface GenerarLiquidacionComisionesUseCase {
    Mono<ReporteLiquidacionResponse> generar(LocalDate desde, LocalDate hasta, String tiendaId);
}
