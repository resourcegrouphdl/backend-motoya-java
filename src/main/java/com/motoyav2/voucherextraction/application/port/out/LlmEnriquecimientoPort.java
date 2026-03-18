package com.motoyav2.voucherextraction.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * Puerto de salida: enriquece campos faltantes usando un modelo de lenguaje.
 * Implementación actual: Claude Haiku via Anthropic API.
 * Retorna solo los campos que pudo extraer — nunca lanza error (retorna Map.of() en fallo).
 */
public interface LlmEnriquecimientoPort {
    Mono<Map<String, String>> enriquecer(String fullText, Set<String> camposFaltantes);
}
