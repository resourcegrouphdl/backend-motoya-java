package com.motoyav2.evaluacion.infrastructure.adapter.out.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.motoyav2.evaluacion.domain.port.out.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URL;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component("evaluacionGcsStorageAdapter")
@RequiredArgsConstructor
public class GcsStorageAdapter implements StoragePort {

    private final Storage storage;

    @Value("${app.gcs.bucket-name:motoya-form.appspot.com}")
    private String bucketName;

    @Override
    public Mono<String> uploadPdf(byte[] content, String path, String fileName) {
        return Mono.fromCallable(() -> {
            String fullPath = path + "/" + fileName;
            BlobId blobId = BlobId.of(bucketName, fullPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("application/pdf")
                    .build();
            storage.create(blobInfo, content);
            // Signed URL válida por 7 días
            URL signedUrl = storage.signUrl(blobInfo, 7, TimeUnit.DAYS,
                    Storage.SignUrlOption.withV4Signature());
            return signedUrl.toString();
        }).subscribeOn(Schedulers.boundedElastic())
          .doOnError(e -> log.error("Error al subir PDF a GCS: {}", e.getMessage()));
    }
}
