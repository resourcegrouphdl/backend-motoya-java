package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import reactor.core.publisher.Mono;

public interface ObtenerDashboardUseCase {
    Mono<DashboardFinanzas> ejecutar();
}
