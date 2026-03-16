package com.motoyav2.finanzas.domain.model;

import lombok.Builder;

import java.util.Map;

/**
 * Resultado de la extracción de campos de un voucher/comprobante via Google Document AI.
 * Almacenado como sub-map en el documento de pago o cuota en Firestore.
 */
@Builder
public record DocumentAiExtraccion(
        /** PENDIENTE | PROCESANDO | COMPLETADO | ERROR | OMITIDO */
        String status,
        /** Procesador Document AI utilizado (invoice, form_parser) */
        String procesadorId,
        /** Campos extraídos: monto, fecha, banco, numeroOperacion, etc. */
        Map<String, String> campos,
        /** Texto de error si status == ERROR */
        String error,
        /** ISO timestamp de cuando se procesó */
        String procesadoEn
) {}
