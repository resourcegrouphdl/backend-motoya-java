package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.DesglosePeriodo;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ConsultarDesglosePeriodoUseCase {
    Flux<DesglosePeriodo> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId);
}
