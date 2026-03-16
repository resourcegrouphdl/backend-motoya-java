package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.model.DocumentAiExtraccion;
import reactor.core.publisher.Mono;

public interface DocumentAiPort {

    /**
     * Envía un documento almacenado en GCS al procesador de Google Document AI
     * y retorna los campos extraídos.
     *
     * @param gcsPath   ruta en GCS sin bucket (ej: finanzas/vouchers/FAC-001/P1.jpg)
     * @param mimeType  tipo MIME del archivo (image/jpeg, application/pdf, etc.)
     * @return extracción con los campos identificados por el modelo
     */
    Mono<DocumentAiExtraccion> procesar(String gcsPath, String mimeType);
}
