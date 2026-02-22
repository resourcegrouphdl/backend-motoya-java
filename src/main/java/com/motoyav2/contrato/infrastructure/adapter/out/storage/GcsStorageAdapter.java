package com.motoyav2.contrato.infrastructure.adapter.out.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.motoyav2.contrato.domain.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GcsStorageAdapter implements StoragePort {

    private final Storage storage;

    @Value("${app.gcs.bucket-name:motoya-form.appspot.com}")
    private String bucketName;

    /**
     * Sube el PDF a Firebase Storage y devuelve una URL de descarga pública
     * usando el mecanismo nativo de Firebase Storage (download token).
     *
     * Esta URL tiene la forma:
     *   https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{path}?alt=media&token={uuid}
     *
     * No requiere Signed URLs ni permisos IAM adicionales.
     * El token es un UUID generado por nosotros y guardado en los metadatos del objeto
     * (campo "firebaseStorageDownloadTokens"), que es el mecanismo estándar de Firebase Storage.
     */
    @Override
    public Mono<String> uploadPdf(String path, byte[] content, String contentType) {
        return Mono.fromCallable(() -> {
            String downloadToken = UUID.randomUUID().toString();

            BlobId blobId = BlobId.of(bucketName, path);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setContentDisposition("attachment; filename=\"" + extractFileName(path) + "\"")
                    .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                    .build();

            storage.create(blobInfo, content);

            // Firebase Storage download URL — descargable directamente desde el browser/frontend
            String encodedPath = path.replace("/", "%2F");
            return String.format(
                    "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
                    bucketName, encodedPath, downloadToken
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
