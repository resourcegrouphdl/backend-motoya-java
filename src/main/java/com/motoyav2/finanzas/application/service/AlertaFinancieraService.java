package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.ListarAlertasFinancierasUseCase;
import com.motoyav2.finanzas.application.port.out.AlertaFinancieraPort;
import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class AlertaFinancieraService implements ListarAlertasFinancierasUseCase {

    private final AlertaFinancieraPort alertaPort;

    @Override
    public Flux<AlertaFinanciera> ejecutar() {
        return alertaPort.findAllAlertas();
    }
}
