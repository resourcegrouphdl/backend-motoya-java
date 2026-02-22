package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.VendedorInfo;
import reactor.core.publisher.Mono;

public interface VendedorPort {

    Mono<VendedorInfo> obtenerInfoVendedor(String vendedorId);
}
