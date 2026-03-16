package com.motoyav2.finanzas.infrastructure.adapter.out.documentai;

import com.google.auth.oauth2.GoogleCredentials;
import com.motoyav2.finanzas.application.port.out.DocumentAiPort;
import com.motoyav2.finanzas.domain.model.DocumentAiExtraccion;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador que llama a Google Document AI REST API para extraer campos
 * de vouchers, comprobantes y facturas subidos a GCS.
 *
 * Usa la REST API directamente (no el cliente Java) para mantener el modelo
 * reactivo sin wrapping de llamadas bloqueantes pesadas.
 *
 * Requiere:
 *   - app.documentai.project-id
 *   - app.documentai.location (us | eu)
 *   - app.documentai.processor-id  (creado en Google Cloud Console)
 *   - app.documentai.enabled=true
 *   - app.gcs.bucket-name
 *
 * El procesador recomendado es "Form Parser" que maneja tanto facturas
 * como vouchers de transferencia bancaria.
 */
@Slf4j
@Component
public class DocumentAiAdapter implements DocumentAiPort {

    private static final String DOCAI_BASE = "https://documentai.googleapis.com/v1";
    private static final String DOCAI_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final WebClient webClient;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${app.gcs.bucket-name}")
    private String bucketName;

    @Value("${app.documentai.project-id:motoya-form}")
    private String projectId;

    @Value("${app.documentai.location:us}")
    private String location;

    @Value("${app.documentai.processor-id:}")
    private String processorId;

    @Value("${app.documentai.enabled:false}")
    private boolean enabled;

    public DocumentAiAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(DOCAI_BASE).build();
    }

    @Override
    @CircuitBreaker(name = "documentAi", fallbackMethod = "fallbackProcesar")
    public Mono<DocumentAiExtraccion> procesar(String gcsPath, String mimeType) {
        if (!enabled || processorId.isBlank()) {
            log.debug("[DocumentAI] Deshabilitado o processorId vacío — omitiendo extracción para {}", gcsPath);
            return Mono.just(DocumentAiExtraccion.builder()
                    .status("OMITIDO")
                    .procesadoEn(Instant.now().toString())
                    .campos(Map.of())
                    .build());
        }

        String gcsUri = "gs://" + bucketName + "/" + gcsPath;

        return getAccessToken()
                .flatMap(token -> {
                    String processorUri = String.format(
                            "/projects/%s/locations/%s/processors/%s:process",
                            projectId, location, processorId);

                    Map<String, Object> body = Map.of(
                            "gcsDocument", Map.of(
                                    "gcsUri", gcsUri,
                                    "mimeType", mimeType
                            )
                    );

                    return webClient.post()
                            .uri(processorUri)
                            .header("Authorization", "Bearer " + token)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> parseResponse(response, gcsPath))
                            .doOnSuccess(r -> log.info("[DocumentAI] Extracción completada — path={} campos={}",
                                    gcsPath, r.campos().size()));
                })
                .onErrorMap(ex -> {
                    log.error("[DocumentAI] Error procesando {} — {}", gcsPath, ex.getMessage());
                    return ex;
                });
    }

    // ── Fallback para circuit breaker ─────────────────────────────────────────

    @SuppressWarnings("unused")
    private Mono<DocumentAiExtraccion> fallbackProcesar(String gcsPath, String mimeType, Throwable t) {
        log.warn("[DocumentAI] Circuit breaker activo para {} — {}", gcsPath, t.getMessage());
        return Mono.just(DocumentAiExtraccion.builder()
                .status("ERROR")
                .error("Servicio temporalmente no disponible: " + t.getMessage())
                .procesadoEn(Instant.now().toString())
                .campos(Map.of())
                .build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Obtiene un Bearer token fresco. Carga las credenciales del mismo service account
     * que usa Firebase/GCS (firebase.service-account-path); si está vacío usa ADC (Cloud Run).
     * Se ejecuta en boundedElastic para no bloquear el event loop.
     */
    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
                    GoogleCredentials base;
                    if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                        base = GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath));
                    } else {
                        base = GoogleCredentials.getApplicationDefault();
                    }
                    GoogleCredentials scoped = base.createScoped(
                            Collections.singletonList(DOCAI_SCOPE));
                    scoped.refreshIfExpired();
                    return scoped.getAccessToken().getTokenValue();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Parsea la respuesta JSON de Document AI y extrae las entidades clave
     * en un mapa plano de {tipo → valorTexto}.
     *
     * Document AI retorna entities con tipos como:
     *   total_amount, invoice_date, invoice_id, supplier_name,
     *   receiver_account_number, payment_terms, etc.
     */
    @SuppressWarnings("unchecked")
    private DocumentAiExtraccion parseResponse(Map<?, ?> response, String gcsPath) {
        Map<String, String> campos = new HashMap<>();

        try {
            Map<?, ?> document = (Map<?, ?>) response.get("document");
            if (document != null) {
                List<?> entities = (List<?>) document.get("entities");
                if (entities != null) {
                    for (Object entityObj : entities) {
                        Map<?, ?> entity = (Map<?, ?>) entityObj;
                        String tipo = (String) entity.get("type");
                        String texto = (String) entity.get("mentionText");
                        if (tipo != null && texto != null && !texto.isBlank()) {
                            // Normalizar tipos Document AI → nombres en español
                            campos.put(normalizarTipo(tipo), texto.trim());
                        }
                    }
                }

                // Fallback: si no hay entities, intentar extraer de form fields (Form Parser)
                if (campos.isEmpty()) {
                    List<?> pages = (List<?>) document.get("pages");
                    if (pages != null) {
                        extraerFormFields(pages, campos);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DocumentAI] Error parseando respuesta para {}: {}", gcsPath, e.getMessage());
        }

        return DocumentAiExtraccion.builder()
                .status(campos.isEmpty() ? "COMPLETADO_SIN_CAMPOS" : "COMPLETADO")
                .procesadorId(processorId)
                .campos(campos)
                .procesadoEn(Instant.now().toString())
                .build();
    }

    /** Extrae key-value pairs del Form Parser (formFields dentro de pages) */
    @SuppressWarnings("unchecked")
    private void extraerFormFields(List<?> pages, Map<String, String> campos) {
        for (Object pageObj : pages) {
            Map<?, ?> page = (Map<?, ?>) pageObj;
            List<?> formFields = (List<?>) page.get("formFields");
            if (formFields == null) continue;
            for (Object fieldObj : formFields) {
                Map<?, ?> field = (Map<?, ?>) fieldObj;
                String key = extraerTextoLayout(field.get("fieldName"));
                String value = extraerTextoLayout(field.get("fieldValue"));
                if (key != null && value != null && !key.isBlank() && !value.isBlank()) {
                    campos.put(key.toLowerCase().replace(" ", "_"), value.trim());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extraerTextoLayout(Object layoutObj) {
        if (!(layoutObj instanceof Map<?, ?> layout)) return null;
        Object tv = layout.get("textAnchor");
        if (!(tv instanceof Map<?, ?> textAnchor)) return null;
        return (String) textAnchor.get("content");
    }

    /** Normaliza tipos del Invoice Processor de Document AI a nombres legibles */
    private String normalizarTipo(String tipo) {
        return switch (tipo) {
            case "total_amount"             -> "monto";
            case "invoice_date"             -> "fechaEmision";
            case "due_date"                 -> "fechaVencimiento";
            case "invoice_id"               -> "numeroDocumento";
            case "supplier_name"            -> "emisor";
            case "supplier_tax_id"          -> "rucEmisor";
            case "receiver_name"            -> "receptor";
            case "receiver_tax_id"          -> "rucReceptor";
            case "payment_terms"            -> "condicionesPago";
            case "net_amount"               -> "subtotal";
            case "total_tax_amount"         -> "igv";
            case "receiver_account_number"  -> "cuentaBancaria";
            case "bank_name"                -> "banco";
            default                         -> tipo.replace("/", "_");
        };
    }
}
