package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.dto.PagedResult;
import com.motoyav2.evaluacion.application.dto.SolicitudTrackingDto;
import reactor.core.publisher.Mono;

public interface ListarSolicitudesVendedorUseCase {
    Mono<PagedResult<SolicitudTrackingDto>> ejecutar(String vendedorId, int page, int size);
}
