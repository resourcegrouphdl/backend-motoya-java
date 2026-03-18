package com.motoyav2.voucherextraction.application.port.in;

import com.motoyav2.voucherextraction.domain.model.VoucherExtraccion;
import reactor.core.publisher.Mono;

/**
 * Puerto de entrada: solicita la extracción enriquecida de un voucher bancario.
 * Orquesta Form Parser → Regex por banco → Claude Haiku (si faltan campos críticos).
 */
public interface ExtraerVoucherUseCase {
    Mono<VoucherExtraccion> extraer(String gcsPath, String mimeType);
}
