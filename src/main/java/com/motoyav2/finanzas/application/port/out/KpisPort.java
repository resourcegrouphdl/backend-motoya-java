package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

public interface KpisPort {
    Mono<Map<String, Object>> obtenerKpis();
    Mono<Void> incrementar(Map<String, Object> incrementos);
    Mono<Void> recalcularCompleto();
}
