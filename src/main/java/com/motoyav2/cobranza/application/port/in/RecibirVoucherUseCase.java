package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.RecibirVoucherCommand;
import reactor.core.publisher.Mono;

public interface RecibirVoucherUseCase {

    /** Registra el voucher recibido y genera la alerta VOUCHER_PENDIENTE. Retorna el voucherId. */
    Mono<String> ejecutar(RecibirVoucherCommand command);
}
