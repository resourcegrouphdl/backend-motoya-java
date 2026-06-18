package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notifications.meta")
public class MetaWhatsAppProperties {

    /** ID numérico del número en el WABA — obtenido desde Meta Business Manager. */
    private String phoneNumberId = "";

    /** Token de acceso permanente de la app de Meta. */
    private String accessToken = "";

    /** Versión del Graph API a usar. */
    private String apiVersion = "v21.0";

    /** Token secreto para verificación del webhook (configurar en Meta BM → Webhooks). */
    private String webhookVerifyToken = "";

    /**
     * App Secret de la Meta App — usado para validar la firma HMAC-SHA256
     * del header X-Hub-Signature-256 en cada webhook.
     * Obtener en: Meta Business Manager → App → Configuración básica → App Secret.
     * En prod: env var META_WA_APP_SECRET en Cloud Run.
     */
    private String appSecret = "";
}