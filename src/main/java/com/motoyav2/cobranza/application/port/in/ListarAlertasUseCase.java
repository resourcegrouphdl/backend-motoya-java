package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import reactor.core.publisher.Flux;

public interface ListarAlertasUseCase {

    /**
     * Lista alertas no descartadas.
     * @param agenteId null = alertas de toda la tienda (rol SUPERVISOR/ADMIN)
     */
    Flux<AlertaCobranzaDocument> ejecutar(String storeId, String agenteId);
}
