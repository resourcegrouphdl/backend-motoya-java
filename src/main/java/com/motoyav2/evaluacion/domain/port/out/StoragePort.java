package com.motoyav2.evaluacion.domain.port.out;

import reactor.core.publisher.Mono;

public interface StoragePort {
    Mono<String> uploadPdf(byte[] content, String path, String fileName);
}
