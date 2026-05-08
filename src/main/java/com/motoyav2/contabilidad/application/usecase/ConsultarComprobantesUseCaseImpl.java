package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.ComprobanteContable;
import com.motoyav2.contabilidad.domain.port.in.ConsultarComprobantesUseCase;
import com.motoyav2.contabilidad.domain.port.out.ComprobanteLedgerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarComprobantesUseCaseImpl implements ConsultarComprobantesUseCase {

    private final ComprobanteLedgerPort comprobanteLedgerPort;

    @Override
    public Flux<ComprobanteContable> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId, String tipo) {
        log.debug("Consultando comprobantes desde={} hasta={} tiendaId={} tipo={}", desde, hasta, tiendaId, tipo);
        return comprobanteLedgerPort.findByPeriodo(desde, hasta, tiendaId, tipo)
                .onErrorResume(e -> {
                    log.error("Error consultando comprobantes: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
