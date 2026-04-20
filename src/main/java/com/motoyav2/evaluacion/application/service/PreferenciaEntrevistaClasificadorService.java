package com.motoyav2.evaluacion.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Usa Claude para extraer preferencia de horario de entrevista de un mensaje de WhatsApp.
 * Devuelve fecha ISO (YYYY-MM-DD), hora (HH:mm) y confianza.
 */
@Slf4j
@Service
public class PreferenciaEntrevistaClasificadorService {

    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String API_VERSION    = "2023-06-01";

    public record ResultadoPreferencia(
            String fechaIso,    // "YYYY-MM-DD" o null
            String hora,        // "HH:mm" o null
            String confianza,   // "ALTA" | "MEDIA" | "BAJA"
            boolean tienePreferencia
    ) {}

    private final WebClient    webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.anthropic.enabled:true}")
    private boolean enabled;

    public PreferenciaEntrevistaClasificadorService(WebClient.Builder builder, ObjectMapper mapper) {
        this.webClient    = builder.baseUrl(ANTHROPIC_BASE).build();
        this.objectMapper = mapper;
    }

    public Mono<ResultadoPreferencia> extraer(String textoRespuesta) {
        if (!enabled || apiKey == null || apiKey.isBlank() || textoRespuesta == null || textoRespuesta.isBlank()) {
            return Mono.just(sinPreferencia());
        }

        String hoy       = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String maniana   = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE);

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", 150,
                "messages",   List.of(Map.of("role", "user", "content", buildPrompt(textoRespuesta, hoy, maniana)))
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
                .doOnSuccess(r -> log.info("[PREF-ENTREVISTA] fecha={} hora={} confianza={}",
                        r.fechaIso(), r.hora(), r.confianza()))
                .onErrorResume(ex -> {
                    log.warn("[PREF-ENTREVISTA] Error llamando Claude: {}", ex.getMessage());
                    return Mono.just(sinPreferencia());
                });
    }

    private String buildPrompt(String texto, String hoy, String maniana) {
        return """
                Eres un asistente que extrae preferencias de horario de mensajes de WhatsApp.
                Una persona respondió a una pregunta sobre su disponibilidad para una entrevista.

                Fecha de hoy: %s
                Fecha de mañana: %s

                Extrae la preferencia de fecha y hora si existe.
                - "hoy" = %s
                - "mañana" = %s
                - Si indica una hora como "3pm", "15:00", "3 de la tarde" → convertir a HH:mm
                - Si no hay preferencia clara → fecha y hora son null

                Responde ÚNICAMENTE con JSON válido, sin texto adicional:
                {"fecha": "YYYY-MM-DD o null", "hora": "HH:mm o null", "confianza": "ALTA|MEDIA|BAJA", "tienePreferencia": true|false}

                Mensaje: "%s"
                """.formatted(hoy, maniana, hoy, maniana,
                texto.replace("\"", "'").substring(0, Math.min(texto.length(), 300)));
    }

    @SuppressWarnings("unchecked")
    private ResultadoPreferencia parseRespuesta(Map<?, ?> response) {
        try {
            List<?> content = (List<?>) response.get("content");
            if (content == null || content.isEmpty()) return sinPreferencia();
            String text = (String) ((Map<?, ?>) content.get(0)).get("text");
            if (text == null || text.isBlank()) return sinPreferencia();
            text = text.trim().replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();

            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            Object fechaRaw = parsed.get("fecha");
            Object horaRaw  = parsed.get("hora");
            String fecha    = (fechaRaw != null && !"null".equals(String.valueOf(fechaRaw)))
                    ? String.valueOf(fechaRaw) : null;
            String hora     = (horaRaw != null && !"null".equals(String.valueOf(horaRaw)))
                    ? String.valueOf(horaRaw) : null;
            String confianza = String.valueOf(parsed.getOrDefault("confianza", "BAJA")).toUpperCase();
            boolean tiene    = Boolean.TRUE.equals(parsed.get("tienePreferencia"));

            return new ResultadoPreferencia(fecha, hora, confianza, tiene);
        } catch (Exception e) {
            log.warn("[PREF-ENTREVISTA] Error parseando respuesta Claude: {}", e.getMessage());
            return sinPreferencia();
        }
    }

    private ResultadoPreferencia sinPreferencia() {
        return new ResultadoPreferencia(null, null, "BAJA", false);
    }
}
