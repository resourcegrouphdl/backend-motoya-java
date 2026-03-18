package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.ListarSolicitudesQuery;
import com.motoyav2.evaluacion.application.dto.SolicitudResumenDto;
import com.motoyav2.evaluacion.application.dto.PagedResult;
import reactor.core.publisher.Mono;

public interface ListarSolicitudesUseCase {
    Mono<PagedResult<SolicitudResumenDto>> ejecutar(ListarSolicitudesQuery query);
}
