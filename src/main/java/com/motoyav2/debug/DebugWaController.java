package com.motoyav2.debug;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/debug/wa")
public class DebugWaController {

    private final DebugWaService debugWaService;

    /** Retorna los últimos 50 payloads recibidos por el webhook. */
    @GetMapping("/mensajes")
    public Flux<Map<String, Object>> getMensajes() {
        return debugWaService.listarRecientes();
    }

    /** Envía un mensaje de texto libre a cualquier número via Meta Cloud API. */
    @PostMapping("/enviar")
    public Mono<Map<String, Object>> enviar(@RequestBody EnviarRequest body) {
        log.info("[DEBUG-WA] Enviar a={} texto={}", body.numero(), body.texto());
        return debugWaService.enviarMensaje(body.numero(), body.texto())
                .onErrorResume(e -> {
                    log.error("[DEBUG-WA] Error enviando texto: {}", e.getMessage());
                    return Mono.just(Map.of("status", "ERROR", "message", e.getMessage()));
                });
    }

    /**
     * Envía un template de Meta directamente sin requerir contratoId.
     * Úsalo para probar plantillas registradas en Meta Business Manager.
     *
     * @param body.metaTemplateName  Nombre exacto en Meta (ej: motoya_recordatorio_cuota)
     * @param body.languageCode      Código de idioma (ej: es_PE). Default: es_PE.
     * @param body.params            Lista de valores posicionales: {{1}}, {{2}}, ...
     */
    @PostMapping("/enviar-plantilla")
    public Mono<Map<String, Object>> enviarConPlantilla(@RequestBody EnviarPlantillaRequest body) {
        log.info("[DEBUG-WA] Enviar plantilla={} a={}", body.metaTemplateName(), body.numero());
        String lang = (body.languageCode() != null && !body.languageCode().isBlank())
                ? body.languageCode() : "es_PE";
        List<String> params = body.params() != null ? body.params() : List.of();
        return debugWaService.enviarConPlantilla(body.numero(), body.metaTemplateName(), lang, params)
                .onErrorResume(e -> {
                    log.error("[DEBUG-WA] Error enviando plantilla={} a={}: {}", body.metaTemplateName(), body.numero(), e.getMessage());
                    return Mono.just(Map.of("status", "ERROR", "message", e.getMessage()));
                });
    }

    record EnviarRequest(String numero, String texto) {}
    record EnviarPlantillaRequest(String numero, String metaTemplateName, String languageCode, List<String> params) {}
}
