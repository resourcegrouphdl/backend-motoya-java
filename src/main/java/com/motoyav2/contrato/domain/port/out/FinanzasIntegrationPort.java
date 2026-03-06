package com.motoyav2.contrato.domain.port.out;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

/**
 * Output port del módulo Contrato hacia Finanzas.
 * Permite que el dominio de contratos notifique a finanzas
 * sin acoplarse directamente a su implementación.
 */
public interface FinanzasIntegrationPort {

    /**
     * Crea la factura inicial en el módulo de finanzas cuando
     * un contrato pasa al estado FIRMADO.
     * Genera automáticamente los 2 pagos (P1 INICIAL 20% y P2 SALDO 80%).
     */
    Mono<Void> iniciarFacturaDesdeContrato(Contrato contrato);
}
