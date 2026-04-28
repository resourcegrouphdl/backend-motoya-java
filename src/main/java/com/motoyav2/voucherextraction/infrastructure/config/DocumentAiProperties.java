package com.motoyav2.voucherextraction.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Propiedades tipadas para Document AI y Claude Haiku.
 * Falla rápido en startup si la configuración es incoherente:
 *   - Document AI habilitado pero processor-id vacío → WARN
 *   - LLM habilitado pero api-key vacía            → WARN
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.documentai")
public class DocumentAiProperties {

    private boolean enabled    = true;
    private String  projectId  = "motoya-form";
    private String  processorId = "";
    private String  location   = "us";

    @PostConstruct
    void validate() {
        if (!enabled) {
            log.warn("[DocumentAI-CONFIG] Document AI DESHABILITADO " +
                     "(app.documentai.enabled=false / DOCUMENTAI_ENABLED=false). " +
                     "Los vouchers se registrarán sin extracción OCR.");
            return;
        }
        if (!StringUtils.hasText(processorId)) {
            log.warn("[DocumentAI-CONFIG] Document AI habilitado pero " +
                     "app.documentai.processor-id está VACÍO. " +
                     "Todas las llamadas a Document AI fallarán. " +
                     "Configura la variable de entorno DOCUMENTAI_PROCESSOR_ID.");
        } else {
            log.info("[DocumentAI-CONFIG] Document AI OK — projectId={} processorId={} location={}",
                     projectId, processorId, location);
        }
    }
}
