package com.motoyav2.contrato.domain.port.out;

import reactor.core.publisher.Mono;

public interface StoragePort {

    Mono<String> uploadPdf(String path, byte[] content, String contentType);

    /**
     * Devuelve la URL de descarga pública de un archivo ya existente en Storage.
     * Útil para obtener la URL de una imagen y pasársela a Factiliza sendmedia.
     *
     * @param path Ruta del objeto en el bucket. Ej: "imagenes/contrato-abc/foto.jpg"
     * @return URL de descarga Firebase con token
     */
    Mono<String> getDownloadUrl(String path);
}
