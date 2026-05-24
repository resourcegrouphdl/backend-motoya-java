package com.motoyav2.evaluacion.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Clasifica la respuesta de una referencia personal usando Claude Haiku.
 * Retorna POSITIVA / NEGATIVA / DUDOSA.
 *
 * Mismo API key que el módulo de vouchers: ${app.anthropic.api-key}
 */
@Slf4j
@Service
public class ReferenciaClasificadorService {

    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String API_VERSION    = "2023-06-01";

    public enum Clasificacion { POSITIVA, NEGATIVA, DUDOSA }

    public record ResultadoClasificacion(Clasificacion clasificacion, double confianza) {}

    private final WebClient    webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.anthropic.enabled:true}")
    private boolean enabled;

    public ReferenciaClasificadorService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient    = builder.baseUrl(ANTHROPIC_BASE).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Clasifica el texto de respuesta de la referencia.
     * En caso de error retorna DUDOSA con confianza 0 (no bloquea el flujo).
     */
    public Mono<ResultadoClasificacion> clasificar(String respuesta) {
        // Fast-path: payload de botón del template Meta — determinístico, sin Claude
        if (esBotonPositivo(respuesta)) {
            log.info("[CLASIFICADOR-REF] Botón confirmación → POSITIVA (confianza=1.0)");
            return Mono.just(new ResultadoClasificacion(Clasificacion.POSITIVA, 1.0));
        }
        if (esBotonNegativo(respuesta)) {
            log.info("[CLASIFICADOR-REF] Botón negación → NEGATIVA (confianza=1.0)");
            return Mono.just(new ResultadoClasificacion(Clasificacion.NEGATIVA, 1.0));
        }

        if (!enabled || apiKey == null || apiKey.isBlank() || respuesta == null || respuesta.isBlank()) {
            return Mono.just(new ResultadoClasificacion(Clasificacion.DUDOSA, 0.0));
        }

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", 100,
                "messages",   List.of(Map.of(
                        "role",    "user",
                        "content", buildPrompt(respuesta)
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
                .map(this::parseRespuesta)
                .doOnSuccess(r -> log.info("[CLASIFICADOR-REF] clasificacion={} confianza={}", r.clasificacion(), r.confianza()))
                .onErrorResume(ex -> {
                    log.warn("[CLASIFICADOR-REF] Error llamando Claude — fallback DUDOSA: {}", ex.getMessage());
                    return Mono.just(new ResultadoClasificacion(Clasificacion.DUDOSA, 0.0));
                });
    }

    private String buildPrompt(String respuesta) {
        return """
                Eres un asistente que clasifica respuestas de referencias personales en solicitudes de crédito.

                Una persona fue contactada por WhatsApp para confirmar si conoce al solicitante de un crédito de motocicleta.

                Clasifica la siguiente respuesta:
                - POSITIVA: la persona confirma conocer al solicitante y/o da buenas referencias
                - NEGATIVA: la persona niega conocer al solicitante, no quiere ser referencia, o da referencias negativas
                - DUDOSA: respuesta ambigua, no relacionada, incomprensible, o no hay suficiente información

                Responde ÚNICAMENTE con JSON válido, sin texto adicional:
                {"clasificacion": "POSITIVA|NEGATIVA|DUDOSA", "confianza": 0.0-1.0}

                Respuesta a clasificar: "%s"
                """.formatted(respuesta.replace("\"", "'").substring(0, Math.min(respuesta.length(), 500)));
    }

    private static boolean esBotonPositivo(String r) {
        if (r == null) return false;
        String lower = r.toLowerCase().trim();
        return r.equals("REFERENCIA_CONFIRMA")
                || lower.startsWith("sí, lo conozco")
                || lower.startsWith("si, lo conozco");
    }

    private static boolean esBotonNegativo(String r) {
        if (r == null) return false;
        String lower = r.toLowerCase().trim();
        return r.equals("REFERENCIA_NIEGA")
                || lower.startsWith("no lo conozco");
    }

    @SuppressWarnings("unchecked")
    private ResultadoClasificacion parseRespuesta(Map<?, ?> response) {
        try {
            List<?> content = (List<?>) response.get("content");
            if (content == null || content.isEmpty()) return new ResultadoClasificacion(Clasificacion.DUDOSA, 0.0);
            String text = (String) ((Map<?, ?>) content.get(0)).get("text");
            if (text == null || text.isBlank()) return new ResultadoClasificacion(Clasificacion.DUDOSA, 0.0);

            text = text.trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            String clsStr    = String.valueOf(parsed.getOrDefault("clasificacion", "DUDOSA")).toUpperCase();
            double confianza = parsed.get("confianza") instanceof Number n ? n.doubleValue() : 0.5;

            Clasificacion cls = switch (clsStr) {
                case "POSITIVA" -> Clasificacion.POSITIVA;
                case "NEGATIVA" -> Clasificacion.NEGATIVA;
                default         -> Clasificacion.DUDOSA;
            };
            return new ResultadoClasificacion(cls, confianza);
        } catch (Exception e) {
            log.warn("[CLASIFICADOR-REF] Error parseando respuesta Claude: {}", e.getMessage());
            return new ResultadoClasificacion(Clasificacion.DUDOSA, 0.0);
        }
    }
}
