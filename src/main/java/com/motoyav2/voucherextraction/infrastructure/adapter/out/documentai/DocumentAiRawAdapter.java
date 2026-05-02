package com.motoyav2.voucherextraction.infrastructure.adapter.out.documentai;

import com.google.auth.oauth2.GoogleCredentials;
import com.motoyav2.voucherextraction.application.port.out.DocumentAiRawPort;
import com.motoyav2.voucherextraction.domain.model.VoucherRaw;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Llama a Google Document AI Form Parser y devuelve el documento RAW completo:
 *   - fullText: texto OCR completo (document.text)
 *   - formFields: todos los key-value del Form Parser (páginas → formFields)
 *   - entities: entidades tipadas detectadas (entities[].type → mentionText)
 *
 * A diferencia de DocumentAiAdapter (que parsea y normaliza),
 * este adaptador entrega los datos crudos para que el módulo voucherextraction
 * aplique sus propias estrategias de enriquecimiento.
 */
@Slf4j
@Component
public class DocumentAiRawAdapter implements DocumentAiRawPort {

    private static final String DOCAI_BASE  = "https://documentai.googleapis.com/v1";
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

    public DocumentAiRawAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10 MB
                .baseUrl(DOCAI_BASE)
                .build();
    }

    @Override
    @CircuitBreaker(name = "documentAi", fallbackMethod = "fallbackObtenerRaw")
    public Mono<VoucherRaw> obtenerRaw(String gcsPath, String mimeType) {
        String gcsUri = gcsPath.startsWith("gs://") ? gcsPath : "gs://" + bucketName + "/" + gcsPath;
        String processorUri = String.format(
                "/projects/%s/locations/%s/processors/%s:process",
                projectId, location, processorId);

        Map<String, Object> body = Map.of(
                "gcsDocument", Map.of("gcsUri", gcsUri, "mimeType", mimeType)
        );

        return getAccessToken()
                .flatMap(token -> webClient.post()
                        .uri(processorUri)
                        .header("Authorization", "Bearer " + token)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> parseRaw(response, gcsPath)))
                .doOnSuccess(r -> log.info(
                        "[DocAiRaw] OK — path={} textLen={} formFields={} entities={}",
                        gcsPath,
                        r.fullText() != null ? r.fullText().length() : 0,
                        r.formFields().size(),
                        r.entities().size()));
    }

    @SuppressWarnings("unused")
    private Mono<VoucherRaw> fallbackObtenerRaw(String gcsPath, String mimeType, Throwable t) {
        log.warn("[DocAiRaw] Circuit breaker activo — {}", t.getMessage());
        return Mono.just(new VoucherRaw(gcsPath, "", Map.of(), Map.of()));
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private VoucherRaw parseRaw(Map<?, ?> response, String gcsPath) {
        Map<?, ?> doc = (Map<?, ?>) response.get("document");
        if (doc == null) {
            log.warn("[DocAiRaw] Respuesta sin 'document' — path={}", gcsPath);
            return new VoucherRaw(gcsPath, "", Map.of(), Map.of());
        }

        Object textObj = doc.get("text");
        String fullText = textObj != null ? textObj.toString() : "";

        // ── Entities ─────────────────────────────────────────────────────────
        Map<String, String> entities = new LinkedHashMap<>();
        List<?> entityList = (List<?>) doc.get("entities");
        if (entityList != null) {
            for (Object e : entityList) {
                Map<?, ?> entity = (Map<?, ?>) e;
                String type  = (String) entity.get("type");
                String value = (String) entity.get("mentionText");
                if (type != null && value != null && !value.isBlank()) {
                    entities.put(type, value.trim());
                }
            }
        }

        // ── Form fields de todas las páginas ─────────────────────────────────
        Map<String, String> formFields = new LinkedHashMap<>();
        List<?> pages = (List<?>) doc.get("pages");
        if (pages != null && !fullText.isBlank()) {
            for (Object pageObj : pages) {
                Map<?, ?> page = (Map<?, ?>) pageObj;
                List<?> fields = (List<?>) page.get("formFields");
                if (fields == null) continue;
                for (Object fieldObj : fields) {
                    Map<?, ?> field = (Map<?, ?>) fieldObj;
                    String key = extractText(field.get("fieldName"), fullText);
                    String val = extractText(field.get("fieldValue"), fullText);
                    if (key != null && val != null && !key.isBlank() && !val.isBlank()) {
                        formFields.put(key.trim(), val.trim());
                    }
                }
            }
        }

        return new VoucherRaw(gcsPath, fullText,
                Collections.unmodifiableMap(formFields),
                Collections.unmodifiableMap(entities));
    }

    /**
     * Extrae texto de un layout usando textAnchor.textSegments + document.text.
     * Idéntico al método en DocumentAiAdapter — necesario para Form Parser.
     */
    @SuppressWarnings("unchecked")
    private String extractText(Object layoutObj, String docText) {
        if (!(layoutObj instanceof Map<?, ?> layout)) return null;
        Map<?, ?> textAnchor = (Map<?, ?>) layout.get("textAnchor");
        if (textAnchor == null) return null;
        List<?> segments = (List<?>) textAnchor.get("textSegments");
        if (segments == null || segments.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Object segObj : segments) {
            Map<?, ?> seg = (Map<?, ?>) segObj;
            int start = seg.get("startIndex") != null ? Integer.parseInt(seg.get("startIndex").toString()) : 0;
            int end   = seg.get("endIndex")   != null ? Integer.parseInt(seg.get("endIndex").toString())   : 0;
            if (end > start && end <= docText.length()) {
                sb.append(docText, start, end);
            }
        }
        return sb.toString().trim();
    }

    private Mono<String> getAccessToken() {
        return Mono.fromCallable(() -> {
            GoogleCredentials base = (serviceAccountPath != null && !serviceAccountPath.isBlank())
                    ? GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath))
                    : GoogleCredentials.getApplicationDefault();
            GoogleCredentials scoped = base.createScoped(Collections.singletonList(DOCAI_SCOPE));
            scoped.refreshIfExpired();
            return scoped.getAccessToken().getTokenValue();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
