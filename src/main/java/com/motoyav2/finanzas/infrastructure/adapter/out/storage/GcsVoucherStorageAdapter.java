package com.motoyav2.finanzas.infrastructure.adapter.out.storage;

/**
 * @deprecated Eliminado — la subida de archivos ahora se hace directamente desde el
 * frontend a GCS usando Signed URLs generadas por {@link GcsSignedUrlAdapter}.
 *
 * El backend ya NO recibe binarios de vouchers. El flujo es:
 *   1. Frontend: POST /api/finanzas/storage/signed-upload-url → obtiene PUT URL
 *   2. Frontend: PUT {signedUploadUrl} con el archivo binario → sube a GCS
 *   3. Frontend: POST /api/pagos/{id}/voucher con {voucherUrl} → backend guarda metadata
 */
@Deprecated
public class GcsVoucherStorageAdapter {
    // Vacío — mantenido como placeholder hasta next cleanup
}
