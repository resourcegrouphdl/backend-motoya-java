package com.motoyav2.finanzas.application.port.out;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface VoucherStoragePort {
    Mono<String> upload(String facturaId, String pagoId, FilePart archivo);
}
