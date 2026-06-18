package com.motoyav2.cobranza.application.port.out;

import reactor.core.publisher.Mono;
import java.util.List;

public interface WhatsAppSenderPort {
    /** Envía texto libre (solo válido dentro de la ventana 24h de Meta). */
    Mono<String> enviarTexto(String telefono, String texto);

    /**
     * Envía un template aprobado en Meta via Template API.
     * Válido fuera de la ventana 24h. Requiere que el template esté aprobado en Meta.
     *
     * @param metaTemplateName Nombre exacto del template en Meta (ej: motoya_cuota_vencida)
     * @param languageCode     Código de idioma (ej: es_PE)
     * @param paramsOrdenados  Valores posicionales en el orden de las variables de la plantilla
     */
    Mono<String> enviarConPlantilla(String telefono, String metaTemplateName,
                                    String languageCode, List<String> paramsOrdenados);
}
