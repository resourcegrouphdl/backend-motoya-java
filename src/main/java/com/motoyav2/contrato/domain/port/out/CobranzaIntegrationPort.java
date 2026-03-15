package com.motoyav2.contrato.domain.port.out;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

/**
 * Output port del módulo Contrato hacia Cobranzas.
 * Se invoca al aprobar un contrato para iniciar el caso de cobranza
 * con el cronograma de cuotas generado.
 */
public interface CobranzaIntegrationPort {

    /**
     * Inicia el caso de cobranza correspondiente al contrato aprobado.
     * El contrato debe tener ya su cronograma de cuotas generado.
     */
    Mono<Void> iniciarCasoDesdeContrato(Contrato contrato);
}
