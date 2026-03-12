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
     * Crea la factura en el módulo de finanzas cuando la facturaVehiculo
     * del contrato es validada como APROBADO.
     * Solo procesa si facturaVehiculo.estadoValidacion == APROBADO.
     * Genera automáticamente los 2 pagos (P1 INICIAL 20% y P2 SALDO 80%).
     * Es idempotente: si la factura ya existe en finanzas_facturas, no duplica.
     */
    Mono<Void> iniciarFacturaDesdeContrato(Contrato contrato);
}
