package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import reactor.core.publisher.Flux;

public interface ListarFacturasUseCase {
    Flux<Factura> ejecutar(FiltrosFacturaRequest filtros);
}
