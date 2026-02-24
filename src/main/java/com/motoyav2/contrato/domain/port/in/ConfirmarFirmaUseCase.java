package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface ConfirmarFirmaUseCase {
    /**
     * Precondición: todas las evidenciasFirma en estado APROBADO.
     * Transición: FIRMA_PENDIENTE → FIRMADO
     */
    Mono<Contrato> confirmar(String contratoId, String confirmadoPor);
}
