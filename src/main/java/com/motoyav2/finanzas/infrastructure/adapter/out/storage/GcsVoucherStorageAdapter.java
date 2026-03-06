package com.motoyav2.finanzas.infrastructure.adapter.out.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.motoyav2.finanzas.application.port.out.VoucherStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class GcsVoucherStorageAdapter implements VoucherStoragePort {

    private final Storage storage;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Override
    public Mono<String> upload(String facturaId, String pagoId, FilePart archivo) {
        String path = "finanzas/vouchers/" + facturaId + "/" + pagoId + "-" + archivo.filename();

        return archivo.content()
                .reduce(new ByteArrayOutputStream(), (baos, dataBuffer) -> {
                    try {
                        baos.write(dataBuffer.toByteBuffer().array());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return baos;
                })
                .map(ByteArrayOutputStream::toByteArray)
                .flatMap(bytes -> Mono.fromCallable(() -> {
                    BlobId blobId = BlobId.of(bucketName, path);
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType(archivo.headers().getContentType() != null
                                    ? archivo.headers().getContentType().toString()
                                    : "application/octet-stream")
                            .build();
                    storage.create(blobInfo, bytes);
                    return "https://storage.googleapis.com/" + bucketName + "/" + path;
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
