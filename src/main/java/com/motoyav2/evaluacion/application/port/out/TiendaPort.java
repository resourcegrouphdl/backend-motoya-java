package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.TiendaInfo;
import reactor.core.publisher.Mono;

public interface TiendaPort {

    Mono<TiendaInfo> obtenerInfoTienda(String tiendaId);
}
