package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.FacturaVehiculo;
import reactor.core.publisher.Mono;

public interface SubirFacturaUseCase {
    Mono<FacturaVehiculo> subir(String contratoId, FacturaVehiculo factura);
}
