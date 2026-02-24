package com.motoyav2.cobranza.application.port.in.command;

public record RecibirVoucherCommand(
        /** null si el contrato se identificará por OCR */
        String contratoId,
        String storeId,
        /** GCS path: vouchers/{voucherId}/original.jpg */
        String imagenPath,
        String thumbPath,
        Double montoDetectado,
        String subioPor
) {}
