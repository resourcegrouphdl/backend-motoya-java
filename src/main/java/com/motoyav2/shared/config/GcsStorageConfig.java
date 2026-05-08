package com.motoyav2.shared.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@Configuration
public class GcsStorageConfig {

    @Value("${GCP_SA_KEY_JSON:}")
    private String saKeyJsonBase64;

    @Bean
    @Primary
    public Storage storage() {
        if (saKeyJsonBase64 == null || saKeyJsonBase64.isBlank()) {
            log.info("[GCS] Usando credenciales ADC (sin GCP_SA_KEY_JSON)");
            return StorageOptions.getDefaultInstance().getService();
        }
        try {
            byte[] jsonBytes = Base64.getDecoder().decode(saKeyJsonBase64);
            ServiceAccountCredentials credentials = ServiceAccountCredentials
                    .fromStream(new ByteArrayInputStream(jsonBytes));
            log.info("[GCS] Storage inicializado con service account: {}", credentials.getClientEmail());
            return StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (Exception e) {
            log.error("[GCS] Error cargando credenciales desde GCP_SA_KEY_JSON, usando ADC: {}", e.getMessage());
            return StorageOptions.getDefaultInstance().getService();
        }
    }
}
