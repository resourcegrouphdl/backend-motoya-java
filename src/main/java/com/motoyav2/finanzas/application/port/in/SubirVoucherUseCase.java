package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.application.port.in.command.SubirVoucherCommand;
import reactor.core.publisher.Mono;

public interface SubirVoucherUseCase {
    Mono<String> ejecutar(SubirVoucherCommand command);
}
