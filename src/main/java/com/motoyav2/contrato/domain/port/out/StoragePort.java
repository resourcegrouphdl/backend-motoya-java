package com.motoyav2.contrato.domain.port.out;

import reactor.core.publisher.Mono;

public interface StoragePort {

    Mono<String> uploadPdf(String path, byte[] content, String contentType);
}
