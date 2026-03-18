package com.motoyav2.voucherextraction.application.port.out;

import com.motoyav2.voucherextraction.domain.model.VoucherRaw;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: obtiene el documento completo de Google Document AI Form Parser.
 * Retorna el texto OCR completo + formFields + entities sin procesamiento adicional.
 */
public interface DocumentAiRawPort {
    Mono<VoucherRaw> obtenerRaw(String gcsPath, String mimeType);
}
