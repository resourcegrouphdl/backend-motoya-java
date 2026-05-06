package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.dto.ContextoDuplicadosDto;
import reactor.core.publisher.Mono;

public interface ConsultarDuplicadosVoucherUseCase {

    /**
     * Consulta si un voucher pendiente es potencialmente duplicado.
     * Combina:
     *  - Opción A: consulta el índice de operaciones bancarias (exacta por banco+numOp).
     *  - Opción B: busca vouchers ya aprobados del mismo contrato con monto y fecha similares.
     */
    Mono<ContextoDuplicadosDto> ejecutar(String voucherId);
}