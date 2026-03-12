package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de conexión a la API de Factiliza WhatsApp.
 *
 * Configura en application.properties (o env vars en Cloud Run):
 *
 *   notifications.factiliza.base-url=https://api.factiliza.com/v1
 *   notifications.factiliza.token=eyJhbGci...
 *   notifications.factiliza.instance=mi-instancia
 */
@Data
@Component
@ConfigurationProperties(prefix = "notifications.factiliza")
public class FactilizaProperties {

    /** URL base de la API de Factiliza. Ej: https://api.factiliza.com/v1 */
    private String baseUrl;

    /** Bearer token de autenticación proporcionado por Factiliza. */
    private String token;

    /** Nombre de la instancia WhatsApp configurada en el panel de Factiliza. */
    private String instance;
}
