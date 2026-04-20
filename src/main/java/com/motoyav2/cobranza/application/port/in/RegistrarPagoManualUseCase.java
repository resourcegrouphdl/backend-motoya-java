package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.RegistrarPagoManualCommand;
import reactor.core.publisher.Mono;

public interface RegistrarPagoManualUseCase {

    record Result(
            String contratoId,
            double saldoNuevo,
            int cuotasMarcadas,
            /** null si no se proporcionó imagen */
            String voucherId
    ) {}

    Mono<Result> ejecutar(RegistrarPagoManualCommand command);
}
