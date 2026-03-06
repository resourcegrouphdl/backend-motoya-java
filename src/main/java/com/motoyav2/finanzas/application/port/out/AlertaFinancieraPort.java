package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AlertaFinancieraPort {
    Flux<AlertaFinanciera> findAllAlertas();
    Mono<java.util.List<DashboardFinanzas.ProximoPago>> findProximosPagos();
}
