package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.Factura;
import reactor.core.publisher.Mono;

public interface ObtenerFacturaUseCase {
    Mono<Factura> ejecutar(String facturaId);
}
