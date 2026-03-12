package com.motoyav2.contrato.domain.port.out;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface CrearEventoEnCalendar {

  public Mono<Void> crearEventoEnCalendar(Contrato contrato);

}
