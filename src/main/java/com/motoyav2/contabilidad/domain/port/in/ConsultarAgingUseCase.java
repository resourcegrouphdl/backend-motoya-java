package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.BucketMora;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ConsultarAgingUseCase {

    Flux<BucketMora> ejecutar(LocalDate fechaCorte, String tiendaId);
}
