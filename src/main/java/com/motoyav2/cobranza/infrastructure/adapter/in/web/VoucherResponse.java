package com.motoyav2.cobranza.infrastructure.adapter.in.web;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;

/**
 * DTO de salida para GET /api/v1/cobranzas/vouchers.
 * Incluye la Signed URL de la imagen generada on-demand (15 min),
 * más los campos de OCR y la fecha mapeada desde creadoEn.
 */
public record VoucherResponse(
        String id,
        String contratoId,
        String cliente,
        String clienteDni,
        String storeId,
        String estado,
        String fuente,
        /** Signed URL GCS válida 15 minutos — lista para <img src> */
        String imagenUrl,
        /** GCS path relativo — para referencia interna */
        String imagenPath,
        Double montoDetectado,
        Double montoEsperado,
        OcrResultadoDocument ocrResultado,
        String aprobadoPor,
        String aprobadoPorNombre,
        String rechazadoPor,
        String motivoRechazo,
        String observacionesRechazo,
        String comprobanteId,
        /** ISO timestamp — mapeado desde creadoEn */
        String fechaDeteccion,
        String creadoPor
) {}
