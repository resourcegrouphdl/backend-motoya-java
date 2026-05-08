package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ResumenIgv;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConsultarIgvUseCase {

    Mono<ResumenIgv> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId);
}
