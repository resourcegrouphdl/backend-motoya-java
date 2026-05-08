package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.PuntoRecaudacion;
import com.motoyav2.contabilidad.domain.port.in.ConsultarFlujoCajaUseCase;
import com.motoyav2.contabilidad.domain.port.out.CronogramaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarFlujoCajaUseCaseImpl implements ConsultarFlujoCajaUseCase {

    private final CronogramaPort cronogramaPort;

    @Override
    public Flux<PuntoRecaudacion> ejecutar(int meses, String tiendaId) {
        log.debug("Consultando flujo de caja meses={} tiendaId={}", meses, tiendaId);
        return cronogramaPort.proyectarFlujo(meses, tiendaId)
                .onErrorResume(e -> {
                    log.error("Error consultando flujo de caja: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
