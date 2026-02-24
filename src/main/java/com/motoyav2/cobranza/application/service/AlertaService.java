package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.in.ListarAlertasUseCase;
import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gestión de alertas operativas.
 *
 * MarcarAlertaLeidaUseCase y DescartarAlertaUseCase comparten la firma
 * ejecutar(String alertaId) — no pueden coexistir en el mismo bean.
 * El controller llama directamente a marcarLeida() y descartar().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaService implements ListarAlertasUseCase {

    private final AlertaCobranzaPort alertaPort;

    // -------------------------------------------------------------------------
    // ListarAlertasUseCase
    // -------------------------------------------------------------------------

    @Override
    public Flux<AlertaCobranzaDocument> ejecutar(String storeId, String agenteId) {
        if (agenteId != null) {
            return alertaPort.findByAgenteId(agenteId);
        }
        return alertaPort.findByStoreId(storeId);
    }

    // -------------------------------------------------------------------------
    // Operaciones de estado — invocadas directamente por el controller
    // -------------------------------------------------------------------------

    public Mono<Void> marcarLeida(String alertaId) {
        return alertaPort.findById(alertaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Alerta no encontrada: " + alertaId)))
                .flatMap(alerta -> {
                    alerta.setLeida(true);
                    return alertaPort.save(alerta);
                })
                .then();
    }

    public Mono<Void> descartar(String alertaId) {
        return alertaPort.findById(alertaId)
                .switchIfEmpty(Mono.error(new NotFoundException("Alerta no encontrada: " + alertaId)))
                .flatMap(alerta -> {
                    alerta.setDescartada(true);
                    return alertaPort.save(alerta);
                })
                .then();
    }
}
