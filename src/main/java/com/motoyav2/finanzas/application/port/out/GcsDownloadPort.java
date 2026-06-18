package com.motoyav2.finanzas.application.port.out;

import reactor.core.publisher.Mono;

public interface GcsDownloadPort {
    Mono<byte[]> descargar(String gcsPath);
}
