package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de conexión a la API WhatsApp de json.pe.
 *
 * Configurar en application.properties o variables de entorno:
 *
 *   notifications.factiliza.base-url → Base URL de la API (default: https://api.whatsapp.json.pe)
 *   notifications.factiliza.token    → Bearer token de autenticación
 */
@Data
@Component
@ConfigurationProperties(prefix = "notifications.factiliza")
public class FactilizaProperties {

    /** Base URL de la API WhatsApp. */
    private String baseUrl = "https://api.whatsapp.json.pe";

    /** Bearer token de autenticación. */
    private String token;
}
