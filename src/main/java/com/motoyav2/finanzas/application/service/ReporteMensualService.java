package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.GenerarReporteMensualUseCase;
import com.motoyav2.finanzas.application.port.in.ListarReportesContabilidadUseCase;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ReporteContabilidadDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter.ReporteMensualPortAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteMensualService implements
        GenerarReporteMensualUseCase,
        ListarReportesContabilidadUseCase {

    private final ReporteMensualPortAdapter adapter;

    @Override
    public Mono<String> ejecutar(String mes) {
        log.info("[ReporteMensual] Generando reporte para mes={}", mes);
        return adapter.generarReporteMes(mes);
    }

    @Override
    public Flux<ReporteContabilidadDocument> ejecutar() {
        return adapter.findAll();
    }
}
