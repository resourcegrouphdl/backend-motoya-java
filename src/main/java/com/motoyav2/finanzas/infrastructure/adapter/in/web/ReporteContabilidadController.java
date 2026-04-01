package com.motoyav2.finanzas.infrastructure.adapter.in.web;

import com.motoyav2.finanzas.application.port.in.GenerarReporteMensualUseCase;
import com.motoyav2.finanzas.application.port.in.ListarReportesContabilidadUseCase;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.response.FinanzasActionResponse;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ReporteContabilidadDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/finanzas/reportes")
@RequiredArgsConstructor
public class ReporteContabilidadController {

    private final GenerarReporteMensualUseCase  generar;
    private final ListarReportesContabilidadUseCase listar;

    /** GET /api/finanzas/reportes — lista reportes mensuales (últimos 24 meses) */
    @GetMapping
    public Flux<ReporteContabilidadDocument> listar() {
        return listar.ejecutar();
    }

    /**
     * POST /api/finanzas/reportes/generar?mes=2026-03
     * Trigger manual. Si ya existe, lo regenera (sobreescribe).
     */
    @PostMapping("/generar")
    public Mono<FinanzasActionResponse> generar(@RequestParam String mes) {
        return generar.ejecutar(mes)
                .map(url -> FinanzasActionResponse.ok("Reporte generado: " + url));
    }
}
