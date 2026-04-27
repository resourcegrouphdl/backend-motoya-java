package com.motoyav2.cobranza.application.port.in.command;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;

public record RecibirVoucherCommand(
        /** null si el contrato se identificará por OCR */
        String contratoId,
        String storeId,
        /** GCS path relativo: cobranza-vouchers/{contratoId}/{uuid}.jpg */
        String imagenPath,
        String thumbPath,
        Double montoDetectado,
        /** Monto de la próxima cuota vigente — para comparación del revisor */
        Double montoEsperado,
        /** Datos extraídos por Document AI + Claude — null si es upload manual sin OCR */
        OcrResultadoDocument ocrResultado,
        String subioPor,
        /** WHATSAPP | PAGO_MANUAL | ADMIN_UPLOAD */
        String fuente,
        /** Nombre del cliente — null en uploads manuales sin contexto */
        String clienteNombre
) {}
