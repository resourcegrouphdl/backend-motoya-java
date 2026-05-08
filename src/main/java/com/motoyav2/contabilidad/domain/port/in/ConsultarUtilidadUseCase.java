package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.UtilidadPeriodo;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConsultarUtilidadUseCase {
    Mono<UtilidadPeriodo> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId);
}
