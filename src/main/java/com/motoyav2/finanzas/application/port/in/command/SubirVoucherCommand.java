package com.motoyav2.finanzas.application.port.in.command;

import lombok.Value;

/**
 * Command para asociar un voucher ya subido a GCS a un pago de factura.
 * El archivo NO pasa por el backend — el frontend lo sube directamente a GCS
 * mediante la Signed URL obtenida en POST /api/finanzas/storage/signed-upload-url.
 */
@Value
public class SubirVoucherCommand {
    String facturaId;
    String pagoId;
    /** URL pública del archivo en GCS (retornada por el signed URL endpoint) */
    String voucherUrl;
    /** Ruta GCS sin bucket — usada para Document AI (ej: finanzas/vouchers/FAC-001/P1.jpg) */
    String gcsPath;
    /** MIME type del archivo — usado para Document AI (image/jpeg, application/pdf, etc.) */
    String mimeType;
}
