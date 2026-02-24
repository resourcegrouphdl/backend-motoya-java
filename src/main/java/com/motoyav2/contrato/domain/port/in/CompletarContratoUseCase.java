package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface CompletarContratoUseCase {
    /**
     * Precondiciones:
     * - numeroDeTitulo registrado
     * - tive, evidenciaSOAT, evidenciaPlacaRodaje en estado APROBADO
     * Transición: FIRMADO → COMPLETADO
     */
    Mono<Contrato> completar(String contratoId, String completadoPor);
}
