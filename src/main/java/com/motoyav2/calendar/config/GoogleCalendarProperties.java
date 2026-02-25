package com.motoyav2.calendar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración para el módulo provisional de Google Calendar.
 * Se leen desde application.properties con el prefijo "google.calendar".
 *
 * Campos requeridos por GoogleCredentials.fromStream() (service_account):
 *   client_id, client_email, private_key, private_key_id
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "google.calendar")
public class GoogleCalendarProperties {

    /** ID del proyecto GCP (ej. motoya-form) */
    private String projectId;

    /** Email de la cuenta de servicio (campo client_email en el JSON) */
    private String clientEmail;

    /** ID numérico de la cuenta de servicio (campo client_id en el JSON) */
    private String clientId;

    /**
     * ID de la clave privada (campo private_key_id en el JSON).
     * Es el identificador corto que aparece en el JSON del service account.
     */
    private String privateKeyId;

    /**
     * Clave privada RSA (campo private_key en el JSON).
     * En application.properties los saltos de línea se escriben como \n literal:
     *   google.calendar.private-key=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END...
     * En variables de entorno se pueden incluir saltos de línea reales.
     */
    private String privateKey;

    /** ID del calendario destino (ej. primary o ID completo de Google Calendar) */
    private String calendarId;
}
