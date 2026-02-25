package com.motoyav2.calendar.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Configura el cliente de Google Calendar API usando credenciales de service account
 * definidas en application.properties (prefijo google.calendar.*).
 *
 * El bean es @Lazy: no se instancia al arrancar la app sino en el primer uso,
 * lo que permite iniciar la aplicación aunque las credenciales no estén configuradas.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GoogleCalendarConfig {

    private final GoogleCalendarProperties props;

    /**
     * Bean @Lazy para evitar fallo en startup cuando las credenciales no están configuradas.
     * Falla en el primer request al endpoint /api/calendar/cronograma si faltan credenciales.
     */
    @Lazy
    @Bean("provisionalCalendarApi")
    public Calendar provisionalCalendarApi() throws Exception {
        log.info("[Calendar Provisional] Inicializando Google Calendar API — SA: {}", props.getClientEmail());

        String credentialsJson = buildCredentialsJson();

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Motoya v2 Calendar Provisional").build();
    }

    /**
     * Construye el JSON de service account a partir de las properties.
     *
     * GoogleCredentials.fromStream() requiere exactamente estos 4 campos:
     *   client_id, client_email, private_key, private_key_id
     *
     * Cómo obtener los valores desde el JSON descargado de GCP:
     *   "client_email"   → google.calendar.client-email
     *   "client_id"      → google.calendar.client-id
     *   "private_key_id" → google.calendar.private-key-id
     *   "private_key"    → google.calendar.private-key  (con \n como literales)
     */
    private String buildCredentialsJson() {
        // Normalizar la private key:
        // Spring lee \n en .properties como newline real (carácter 0x0A).
        // Si llega como literal \\n (desde env var mal escapada) también se convierte.
        String rawKey = props.getPrivateKey().replace("\\n", "\n");

        // Para JSON la clave necesita los newlines como \n escapados
        String jsonKey = rawKey.replace("\n", "\\n");

        return String.format("""
                {
                  "type": "service_account",
                  "project_id": "%s",
                  "client_email": "%s",
                  "client_id": "%s",
                  "private_key_id": "%s",
                  "private_key": "%s",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """,
                props.getProjectId(),
                props.getClientEmail(),
                nullToEmpty(props.getClientId()),
                nullToEmpty(props.getPrivateKeyId()),
                jsonKey);
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
