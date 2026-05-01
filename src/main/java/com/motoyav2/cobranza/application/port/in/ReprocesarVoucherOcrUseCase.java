package com.motoyav2.cobranza.application.port.in;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ReprocesarVoucherOcrUseCase {

    Mono<ReprocesarOcrResult> ejecutar(String voucherId);

    record ReprocesarOcrResult(
            String voucherId,
            Double montoDetectadoAnterior,
            Double montoDetectadoNuevo,
            String banco,
            Map<String, String> campos,
            boolean enriquecidoConLlm,
            boolean montoModificado,
            String procesador
    ) {}
}
