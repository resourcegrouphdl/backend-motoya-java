package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de conexión a la Meta WhatsApp Business Cloud API.
 *
 * Configura en application.properties (o env vars en Cloud Run):
 *
 *   notifications.meta.phone-number-id  → ID del número registrado en Meta Business
 *   notifications.meta.access-token     → Token permanente de la app Meta
 *   notifications.meta.api-version      → Versión de la Graph API (ej: v21.0)
 */
@Data
@Component
@ConfigurationProperties(prefix = "notifications.meta")
public class MetaWhatsAppProperties {

    /** ID del número de teléfono registrado en Meta Business Manager. */
    private String phoneNumberId;

    /** Token de acceso permanente de la aplicación Meta. */
    private String accessToken;

    /** Versión de la Meta Graph API. Ej: v21.0 */
    private String apiVersion = "v21.0";
}
