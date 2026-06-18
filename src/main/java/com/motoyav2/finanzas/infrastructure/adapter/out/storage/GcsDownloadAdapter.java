package com.motoyav2.finanzas.infrastructure.adapter.out.storage;

import com.google.cloud.storage.Storage;
import com.motoyav2.finanzas.application.port.out.GcsDownloadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class GcsDownloadAdapter implements GcsDownloadPort {

    private final Storage storage;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Override
    public Mono<byte[]> descargar(String gcsPath) {
        return Mono.fromCallable(() -> storage.readAllBytes(bucketName, gcsPath))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.warn("[GcsDownload] Error descargando {} — {}", gcsPath, e.getMessage()));
    }
}
