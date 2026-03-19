package com.motoyav2.voucherextraction.infrastructure.adapter.out.integration;

import com.motoyav2.finanzas.application.port.out.DocumentAiPort;
import com.motoyav2.finanzas.domain.model.DocumentAiExtraccion;
import com.motoyav2.voucherextraction.application.port.in.ExtraerVoucherUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptador puente: implementa DocumentAiPort (módulo finanzas) delegando al
 * nuevo módulo voucherextraction. Es @Primary para que Spring lo prefiera sobre
 * el DocumentAiAdapter original sin necesidad de modificarlo.
 *
 * Todos los servicios que inyectan DocumentAiPort (DocumentAiService, etc.)
 * reciben automáticamente esta implementación enriquecida sin cambios.
 *
 * Convierte VoucherExtraccion → DocumentAiExtraccion manteniendo
 * compatibilidad total con la estructura existente.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class VoucherExtractionDocumentAiAdapter implements DocumentAiPort {

    private final ExtraerVoucherUseCase extraerVoucherUseCase;

    @Override
    public Mono<DocumentAiExtraccion> procesar(String gcsPath, String mimeType) {
        log.info("[VoucherAdapter] procesar() invocado — gcsPath={} mimeType={}", gcsPath, mimeType);
        return extraerVoucherUseCase.extraer(gcsPath, mimeType)
                .map(v -> DocumentAiExtraccion.builder()
                        .status(v.status())
                        .procesadorId("form-parser-enriquecido-v2")
                        .campos(v.campos())
                        .error(v.error())
                        .procesadoEn(v.procesadoEn())
                        .build())
                .doOnSuccess(r -> log.info("[VoucherAdapter] Conversión OK — status={} banco={} campos={}",
                        r.status(),
                        r.campos() != null ? r.campos().get("banco") : "?",
                        r.campos() != null ? r.campos().size() : 0))
                .doOnError(e -> log.error("[VoucherAdapter] Error en procesar() — {}", e.getMessage(), e));
    }
}
