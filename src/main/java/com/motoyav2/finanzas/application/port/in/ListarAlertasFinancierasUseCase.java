package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import reactor.core.publisher.Flux;

public interface ListarAlertasFinancierasUseCase {
    Flux<AlertaFinanciera> ejecutar();
}
