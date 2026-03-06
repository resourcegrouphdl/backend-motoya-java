package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.ListarAlertasFinancierasUseCase;
import com.motoyav2.finanzas.application.port.in.ObtenerDashboardUseCase;
import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/finanzas")
@RequiredArgsConstructor
public class DashboardController {

    private final ObtenerDashboardUseCase obtenerDashboard;
    private final ListarAlertasFinancierasUseCase listarAlertas;

    @GetMapping("/dashboard")
    public Mono<DashboardFinanzas> dashboard() {
        return obtenerDashboard.ejecutar();
    }

    @GetMapping("/alertas")
    public Flux<AlertaFinanciera> alertas() {
        return listarAlertas.ejecutar();
    }
}
