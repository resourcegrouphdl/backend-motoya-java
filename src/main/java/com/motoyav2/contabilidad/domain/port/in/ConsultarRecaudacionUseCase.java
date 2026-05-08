package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ResumenRecaudacion;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConsultarRecaudacionUseCase {

    Mono<ResumenRecaudacion> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId, String agruparPor);
}
