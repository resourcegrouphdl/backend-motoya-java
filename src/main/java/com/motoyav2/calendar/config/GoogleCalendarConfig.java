package com.motoyav2.calendar.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;

/**
 * Configura el cliente de Google Calendar API.
 * Solo se activa si google.calendar.client-email está configurado.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GoogleCalendarConfig {

    private final GoogleCalendarProperties props;

    /**
     * Solo se crea si la propiedad google.calendar.client-email no está vacía.
     * Evita fallo en startup cuando las credenciales no están configuradas.
     */
    @Bean("provisionalCalendarApi")
    @ConditionalOnExpression("'${google.calendar.client-email:}' != ''")
    public Calendar provisionalCalendarApi() throws Exception {
        log.info("[Calendar Provisional] Inicializando Google Calendar API — SA: {}", props.getClientEmail());

        ServiceAccountCredentials credentials = buildCredentials();

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Motoya v2 Calendar Provisional").build();
    }

    /**
     * Construye las credenciales directamente desde los campos del service account,
     * sin pasar por JSON. Esto evita todos los problemas de escape de la private key.
     *
     * La private key puede llegar:
     *   - Con \n literales (env var de Cloud Run, application.properties)
     *   - Con newlines reales (algunos sistemas)
     * En ambos casos se normaliza antes del decode.
     */
    private ServiceAccountCredentials buildCredentials() throws Exception {
        PrivateKey privateKey = parsePrivateKey(props.getPrivateKey());

        return ServiceAccountCredentials.newBuilder()
                .setClientEmail(props.getClientEmail())
                .setClientId(emptyIfNull(props.getClientId()))
                .setPrivateKeyId(emptyIfNull(props.getPrivateKeyId()))
                .setPrivateKey(privateKey)
                .setScopes(Collections.singletonList(CalendarScopes.CALENDAR))
                .build();
    }

    /**
     * Parsea una clave privada PKCS#8 en formato PEM.
     * Maneja \n literales (de env vars y .properties) y newlines reales.
     */
    private PrivateKey parsePrivateKey(String pemKey) throws Exception {
        // 1. Convertir \n literales (dos chars) a newlines reales
        String normalized = pemKey.replace("\\n", "\n");

        // 2. Quitar encabezados PEM y todo espacio en blanco → base64 puro
        String base64 = normalized
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        // 3. Decodificar y construir la clave
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
