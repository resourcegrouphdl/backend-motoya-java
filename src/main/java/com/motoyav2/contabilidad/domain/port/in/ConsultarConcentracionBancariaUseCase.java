package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ConcentracionBancaria;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ConsultarConcentracionBancariaUseCase {

    Flux<ConcentracionBancaria> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId);
}
