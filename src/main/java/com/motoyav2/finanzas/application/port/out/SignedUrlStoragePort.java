package com.motoyav2.finanzas.application.port.out;

import reactor.core.publisher.Mono;

public interface SignedUrlStoragePort {

    /**
     * Genera una Signed URL V4 de GCS que permite al cliente subir directamente
     * un archivo mediante HTTP PUT. Expira en 15 minutos.
     *
     * @param gcsPath     ruta dentro del bucket (ej: finanzas/vouchers/FAC-001/P1.jpg)
     * @param contentType MIME type del archivo (image/jpeg, image/png, application/pdf)
     * @return resultado con signedUploadUrl, publicUrl y gcsPath
     */
    Mono<SignedUrlResult> generarUrlCarga(String gcsPath, String contentType);

    record SignedUrlResult(
            /** URL firmada para que el cliente haga PUT directo a GCS */
            String signedUploadUrl,
            /** URL pública permanente para guardar en Firestore */
            String publicUrl,
            /** Ruta GCS sin bucket (para referencia) */
            String gcsPath
    ) {}
}
