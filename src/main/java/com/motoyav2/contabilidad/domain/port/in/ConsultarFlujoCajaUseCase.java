package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.PuntoRecaudacion;
import reactor.core.publisher.Flux;

public interface ConsultarFlujoCajaUseCase {

    Flux<PuntoRecaudacion> ejecutar(int meses, String tiendaId);
}
