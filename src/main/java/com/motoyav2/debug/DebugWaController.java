package com.motoyav2.debug;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /** Envía un mensaje de texto libre a cualquier número via Factiliza. */
    @PostMapping("/enviar")
    public Mono<Map<String, Object>> enviar(@RequestBody EnviarRequest body) {
        log.info("[DEBUG-WA] Enviar a={} texto={}", body.numero(), body.texto());
        return debugWaService.enviarMensaje(body.numero(), body.texto());
    }

    record EnviarRequest(String numero, String texto) {}
}
