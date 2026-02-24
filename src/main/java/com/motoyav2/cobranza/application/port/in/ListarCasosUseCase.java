package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.dto.CasoResumenDto;
import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import reactor.core.publisher.Flux;

public interface ListarCasosUseCase {

    /**
     * Lista casos activos con filtros opcionales, ordenados por nivel desc + diasMora desc.
     * diasMora y prioridad se calculan en tiempo real.
     */
    Flux<CasoResumenDto> ejecutar(ListarCasosQuery query);
}
