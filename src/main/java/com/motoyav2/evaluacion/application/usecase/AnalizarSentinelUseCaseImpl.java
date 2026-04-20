package com.motoyav2.evaluacion.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.AnalizarSentinelCommand;
import com.motoyav2.evaluacion.application.command.AnalizarSentinelCommand.FilaHistorial;
import com.motoyav2.evaluacion.application.dto.AnalizarSentinelResult;
import com.motoyav2.evaluacion.domain.port.in.AnalizarSentinelUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recibe el historial Sentinel (parseado en el frontend) y:
 *  1. Construye un prompt y llama a Claude para obtener el análisis de riesgo.
 *  2. Guarda el perfil resumen en el campo `perfilSentinel` del cliente.
 *  3. Guarda el análisis estructurado en `datosSentinel` para visualización posterior.
 */
@Slf4j
@Service
public class AnalizarSentinelUseCaseImpl implements AnalizarSentinelUseCase {

    private static final String ANTHROPIC_BASE = "https://api.anthropic.com";
    private static final String API_VERSION    = "2023-06-01";

    private final ClienteRepository clienteRepository;
    private final WebClient         webClient;
    private final ObjectMapper      objectMapper;

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Value("${app.anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.anthropic.enabled:true}")
    private boolean enabled;

    public AnalizarSentinelUseCaseImpl(ClienteRepository clienteRepository,
                                       WebClient.Builder webClientBuilder,
                                       ObjectMapper objectMapper) {
        this.clienteRepository = clienteRepository;
        this.webClient         = webClientBuilder.baseUrl(ANTHROPIC_BASE).build();
        this.objectMapper      = objectMapper;
    }

    @Override
    public Mono<AnalizarSentinelResult> analizar(AnalizarSentinelCommand command) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("[SENTINEL] Claude deshabilitado — guardando sin análisis IA");
            return guardarSinAnalisis(command);
        }

        return llamarClaude(command)
                .flatMap(result -> guardarResultado(command.clienteId(), command, result)
                        .thenReturn(result))
                .onErrorResume(ex -> {
                    log.warn("[SENTINEL] Error en Claude — guardando sin análisis IA: {}", ex.getMessage());
                    return guardarSinAnalisis(command);
                });
    }

    // ── Claude ────────────────────────────────────────────────────────────────

    private Mono<AnalizarSentinelResult> llamarClaude(AnalizarSentinelCommand cmd) {
        String prompt = buildPrompt(cmd);

        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", 600,
                "messages",   List.of(Map.of("role", "user", "content", prompt))
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
                .doOnSuccess(r -> log.info("[SENTINEL] nivelRiesgo={} tendencia={} recomendacion={}",
                        r.nivelRiesgo(), r.tendencia(), r.recomendacion()));
    }

    private String buildPrompt(AnalizarSentinelCommand cmd) {
        String filas = cmd.filas().stream()
                .limit(24)
                .map(this::filaToRow)
                .collect(Collectors.joining("\n"));

        String semaforoActual = cmd.filas().isEmpty() ? "SIN_DATOS"
                : cmd.filas().get(0).semaforoActual();

        return """
                Eres un analista de riesgo crediticio especializado en microfinanzas peruanas.
                Analiza el historial Sentinel Peru para evaluar una solicitud de crédito vehicular (motocicleta).

                DATOS DEL SOLICITANTE:
                - Documento: %s %s
                - Nombre: %s
                - Semáforo más reciente: %s

                HISTORIAL (más reciente → más antiguo):
                Fecha | Semáforo | Score | #EntSBS | DeudaSBS | %%Normal | PeorCalif | DeudaVenc | DiasVenc | Protestos | DiasVProt | DocsImpagos | DiasVDocs | DeudaTrib | DiasVTrib
                %s

                CRITERIOS:
                - VERDE: al día | GRIS: sin historial | ROJO: morosidades o impagos
                - PeorCalif SBS: NOR=Normal, CPP=Con Problemas Potenciales, DEF=Deficiente, DUD=Dudoso, PER=Pérdida
                - Considera la TENDENCIA en el tiempo (¿el perfil mejoró, se deterioró o está estable?)
                - Para microfinanzas en Perú, un historial GRIS prolongado es neutro, no negativo
                - Deuda tributaria o protestos son señales de alerta importantes

                Responde ÚNICAMENTE con JSON válido, sin texto adicional ni bloques markdown:
                {"nivelRiesgo":"BAJO|MEDIO|ALTO|MUY_ALTO","tendencia":"MEJORANDO|ESTABLE|DETERIORANDO","recomendacion":"APROBAR|CONDICIONAL|RECHAZAR","resumen":"descripción concisa 2-3 líneas","hallazgos":["hallazgo1","hallazgo2"],"alertas":["alerta1"]}
                """.formatted(
                cmd.tipoDocumento(), cmd.numeroDocumento(),
                cmd.nombreRazonSocial(),
                semaforoActual,
                filas
        );
    }

    private String filaToRow(FilaHistorial f) {
        return "%s | %s | %.3f | %d | %.2f | %.2f | %s | %.2f | %d | %.2f | %d | %.2f | %d | %.2f | %d"
                .formatted(
                        f.fechaProceso(), f.semaforoActual(), f.score(),
                        f.entidadesSBS(), f.deudaTotalSBS(), f.pctNormalSBS(),
                        f.peorCalificacionSBS(), f.deudaVencidaSBS(), f.diasVencSBS(),
                        f.protestos(), f.diasVencProtestos(),
                        f.documentosImpagos(), f.diasVencDocumentosImpagos(),
                        f.deudaTributaria(), f.diasVencDeudaTributaria()
                );
    }

    @SuppressWarnings("unchecked")
    private AnalizarSentinelResult parseRespuesta(Map<?, ?> response) {
        try {
            List<?> content = (List<?>) response.get("content");
            if (content == null || content.isEmpty()) return fallback();
            String text = (String) ((Map<?, ?>) content.get(0)).get("text");
            if (text == null || text.isBlank()) return fallback();

            // Eliminar posibles bloques markdown
            text = text.trim()
                    .replaceAll("(?s)^```(?:json)?\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);

            String nivelRiesgo   = safe(parsed, "nivelRiesgo",   "MEDIO");
            String tendencia     = safe(parsed, "tendencia",     "ESTABLE");
            String recomendacion = safe(parsed, "recomendacion", "CONDICIONAL");
            String resumen       = safe(parsed, "resumen",       "Análisis completado.");

            List<String> hallazgos = toStringList(parsed.get("hallazgos"));
            List<String> alertas   = toStringList(parsed.get("alertas"));

            return new AnalizarSentinelResult(nivelRiesgo, tendencia, recomendacion,
                    resumen, hallazgos, alertas);
        } catch (Exception e) {
            log.warn("[SENTINEL] Error parseando respuesta Claude: {}", e.getMessage());
            return fallback();
        }
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private Mono<Void> guardarResultado(String clienteId,
                                        AnalizarSentinelCommand cmd,
                                        AnalizarSentinelResult result) {
        String perfilResumen = "%s | %s | %s".formatted(
                result.nivelRiesgo(), result.tendencia(), result.recomendacion());

        String semaforoActual = cmd.filas().isEmpty() ? ""
                : cmd.filas().get(0).semaforoActual();

        Map<String, Object> datosSentinel = Map.of(
                "semaforoActual",  semaforoActual,
                "nivelRiesgo",     result.nivelRiesgo(),
                "tendencia",       result.tendencia(),
                "recomendacion",   result.recomendacion(),
                "resumen",         result.resumen(),
                "hallazgos",       result.hallazgos(),
                "alertas",         result.alertas(),
                "consultadoEn",    Timestamp.now().toString()
        );

        Map<String, Object> fields = Map.of(
                "perfilSentinel", perfilResumen,
                "datosSentinel",  datosSentinel,
                "updatedAt",      Timestamp.now()
        );

        return clienteRepository.updateFields(clienteId, fields)
                .doOnSuccess(v -> log.info("[SENTINEL] Guardado en cliente {}", clienteId));
    }

    private Mono<AnalizarSentinelResult> guardarSinAnalisis(AnalizarSentinelCommand cmd) {
        AnalizarSentinelResult result = fallback();
        return guardarResultado(cmd.clienteId(), cmd, result)
                .thenReturn(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AnalizarSentinelResult fallback() {
        return new AnalizarSentinelResult(
                "MEDIO", "ESTABLE", "CONDICIONAL",
                "No se pudo completar el análisis automático.",
                List.of(), List.of()
        );
    }

    private String safe(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(i -> i instanceof String)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
