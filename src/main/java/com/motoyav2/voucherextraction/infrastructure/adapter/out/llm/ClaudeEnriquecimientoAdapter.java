package com.motoyav2.voucherextraction.infrastructure.adapter.out.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.voucherextraction.application.port.out.LlmEnriquecimientoPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Enriquecimiento de campos faltantes usando Claude Haiku (Anthropic API).
 * Solo se invoca cuando los patrones regex no capturaron algún campo crítico.
 *
 * Costo estimado: ~$0.0005 por llamada con Claude Haiku → insignificante en producción.
 */
@Slf4j
@Component
public class ClaudeEnriquecimientoAdapter implements LlmEnriquecimientoPort {

    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String API_VERSION     = "2023-06-01";
    private static final int    MAX_TEXT_CHARS  = 2000; // evitar tokens excesivos

    private final WebClient    webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.anthropic.enabled:true}")
    private boolean enabled;

    public ClaudeEnriquecimientoAdapter(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient    = webClientBuilder.baseUrl(ANTHROPIC_BASE).build();
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "claudeEnriquecimiento", fallbackMethod = "fallbackEnriquecer")
    public Mono<Map<String, String>> enriquecer(String fullText, Set<String> camposFaltantes) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.debug("[Claude] Deshabilitado o API key no configurada — omitiendo enriquecimiento");
            return Mono.just(Map.of());
        }

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", 512,
                "messages",   List.of(Map.of(
                        "role",    "user",
                        "content", buildPrompt(fullText, camposFaltantes)
                ))
        );

        return webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseClaudeResponse)
                .doOnSuccess(r -> log.info("[Claude] Enriquecimiento OK — {} campos extraídos", r.size()))
                .onErrorMap(ex -> {
                    log.error("[Claude] Error — {}", ex.getMessage());
                    return ex;
                });
    }

    @SuppressWarnings("unused")
    private Mono<Map<String, String>> fallbackEnriquecer(String fullText, Set<String> campos, Throwable t) {
        log.warn("[Claude] Circuit breaker activo — {}", t.getMessage());
        return Mono.just(Map.of());
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(String fullText, Set<String> camposFaltantes) {
        return "Eres un asistente especializado en extraer datos de vouchers bancarios peruanos.\n"
                + "Extrae SOLO los campos solicitados. Responde ÚNICAMENTE con JSON válido, sin texto adicional.\n\n"
                + "CAMPOS A EXTRAER: " + String.join(", ", camposFaltantes) + "\n\n"
                + "Referencia de campos:\n"
                + "  montoPagado     → monto en soles (ej: \"S/ 1,200.00\")\n"
                + "  fechaPago       → fecha y hora del pago (ej: \"Martes, 17 marzo 2026 - 5:35 p.m.\")\n"
                + "  pagadoA         → nombre del beneficiario o empresa destinataria\n"
                + "  servicio        → tipo de servicio o concepto del pago\n"
                + "  codigoUsuario   → código numérico del usuario o empresa\n"
                + "  desde           → cuenta de origen (ej: \"Corriente Soles ***3068\")\n"
                + "  canal           → canal de pago (ej: \"Banca por Internet BCP\")\n"
                + "  numeroOperacion → número de operación o transacción\n"
                + "  banco           → nombre del banco emisor\n\n"
                + "TEXTO DEL VOUCHER:\n"
                + truncate(fullText, MAX_TEXT_CHARS) + "\n\n"
                + "Responde SOLO con JSON. Ejemplo: {\"montoPagado\":\"S/ 1,200.00\",\"banco\":\"BCP\"}\n"
                + "Si un campo no está claro en el texto, no lo incluyas en el JSON.";
    }

    // ── Parsing de respuesta ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> parseClaudeResponse(Map<?, ?> response) {
        try {
            List<?> content = (List<?>) response.get("content");
            if (content == null || content.isEmpty()) return Map.of();

            Map<?, ?> first = (Map<?, ?>) content.get(0);
            String text = (String) first.get("text");
            if (text == null || text.isBlank()) return Map.of();

            // Claude ocasionalmente envuelve el JSON en ```json ... ```
            text = text.trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("(?s)^```(?:json)?\\s*", "")
                           .replaceAll("\\s*```$", "")
                           .trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(
                    text, new TypeReference<Map<String, Object>>() {});

            Map<String, String> result = new LinkedHashMap<>();
            parsed.forEach((k, v) -> {
                if (v != null && !v.toString().isBlank()) {
                    result.put(k, v.toString().trim());
                }
            });
            return result;

        } catch (Exception e) {
            log.warn("[Claude] Error parseando respuesta JSON — {}", e.getMessage());
            return Map.of();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
