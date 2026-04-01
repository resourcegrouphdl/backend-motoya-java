package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ReporteContabilidadDocument;
import reactor.core.publisher.Flux;

public interface ListarReportesContabilidadUseCase {
    Flux<ReporteContabilidadDocument> ejecutar();
}
