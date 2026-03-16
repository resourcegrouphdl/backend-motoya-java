package com.motoyav2.finanzas.infrastructure.adapter.out.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.motoyav2.finanzas.application.port.out.SignedUrlStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * Genera Signed URLs V4 de GCS para que el cliente suba archivos directamente
 * sin pasar el binario por el backend.
 *
 * Flow correcto:
 *   1. Frontend pide POST /api/finanzas/storage/signed-upload-url
 *   2. Backend devuelve {signedUploadUrl, publicUrl, gcsPath}
 *   3. Frontend hace PUT signedUploadUrl con el binario del archivo
 *   4. Frontend envía publicUrl al endpoint de pago/cuota
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GcsSignedUrlAdapter implements SignedUrlStoragePort {

    private final Storage storage;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Override
    public Mono<SignedUrlResult> generarUrlCarga(String gcsPath, String contentType) {
        return Mono.fromCallable(() -> {
                    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, gcsPath))
                            .setContentType(contentType)
                            .build();

                    String signedUploadUrl = storage.signUrl(
                            blobInfo,
                            15, TimeUnit.MINUTES,
                            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                            Storage.SignUrlOption.withV4Signature()
                    ).toString();

                    String publicUrl = "https://storage.googleapis.com/" + bucketName + "/" + gcsPath;

                    log.debug("[GcsSignedUrl] URL firmada generada — path={}", gcsPath);
                    return new SignedUrlResult(signedUploadUrl, publicUrl, gcsPath);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
