package com.motoyav2.cobranza.application.port.in.command;

public record EnviarComprobanteWhatsappCommand(
        String voucherId,
        /** GCS path del PDF subido manualmente. Null = usar comprobante generado o voucher original. */
        String archivoGcsPath,
        String agenteId,
        String agenteNombre,
        String storeId
) {}
