package com.motoyav2.finanzas.infrastructure.adapter.out.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

/**
 * Sube PDFs generados por el módulo finanzas a Firebase Storage (GCS)
 * y devuelve una URL de descarga pública con token, igual al patrón
 * del módulo contrato (GcsStorageAdapter).
 */
@Component
@RequiredArgsConstructor
public class FinanzasPdfStorageService {

    private final Storage storage;

    @Value("${app.gcs.bucket-name:motoya-form.appspot.com}")
    private String bucketName;

    /**
     * Sube bytes PDF a GCS y retorna la URL de descarga Firebase.
     * @param gcsPath ruta dentro del bucket, ej: finanzas/comprobantes-comision/{pagoId}.pdf
     * @param content bytes del PDF
     */
    public Mono<String> subirPdf(String gcsPath, byte[] content) {
        return Mono.fromCallable(() -> {
            String token = UUID.randomUUID().toString();

            BlobId blobId = BlobId.of(bucketName, gcsPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("application/pdf")
                    .setContentDisposition("inline; filename=\"" + extractFileName(gcsPath) + "\"")
                    .setMetadata(Map.of("firebaseStorageDownloadTokens", token))
                    .build();

            storage.create(blobInfo, content);

            String encodedPath = gcsPath.replace("/", "%2F");
            return String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
                    bucketName, encodedPath, token
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractFileName(String path) {
        int last = path.lastIndexOf('/');
        return last >= 0 ? path.substring(last + 1) : path;
    }
}
