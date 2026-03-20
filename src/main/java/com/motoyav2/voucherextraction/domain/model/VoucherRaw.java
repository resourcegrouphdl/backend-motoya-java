package com.motoyav2.voucherextraction.domain.model;

import java.util.Map;

/**
 * Resultado crudo de Google Document AI Form Parser.
 * Contiene el texto OCR completo y los campos detectados sin procesar,
 * listos para ser enriquecidos por las estrategias de banco y el LLM.
 */
public record VoucherRaw(
        String gcsPath,
        /** Texto OCR completo del documento — base para la extracción por regex. */
        String fullText,
        /** Key-value pairs detectados por Form Parser (label → valor como texto plano). */
        Map<String, String> formFields,
        /** Entidades tipadas detectadas por Document AI (tipo → mentionText). */
        Map<String, String> entities
) {}
