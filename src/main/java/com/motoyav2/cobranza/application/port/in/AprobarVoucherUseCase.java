package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.AprobarVoucherCommand;
import reactor.core.publisher.Mono;

public interface AprobarVoucherUseCase {

    /**
     * Aprueba el voucher y ejecuta la saga:
     * Voucher → Movimiento → Comprobante → Actualizar saldo → Eventos.
     * Es idempotente: si el voucher ya está APROBADO retorna el comprobanteId existente.
     *
     * @return comprobanteId generado
     */
    Mono<String> ejecutar(AprobarVoucherCommand command);
}
