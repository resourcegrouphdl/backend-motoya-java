package com.motoyav2.evaluacion.infrastructure.adapter.out.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Thin WebClient wrapper for the Factiliza identity-verification API.
 * <p>
 * Endpoints:
 *   GET /v1/dni/info/{dni}      – national ID (DNI)
 *   GET /v1/cee/info/{cee}      – carnet de extranjería
 *   GET /v1/licencia/info/{dni} – driver's licence (by DNI)
 */
@Slf4j
@Component
public class FactilizaApiClient {

    private final WebClient webClient;

    public FactilizaApiClient(
            @Value("${app.factiliza.base-url:https://api.factiliza.com/v1}") String baseUrl,
            @Value("${app.factiliza.token:}") String token) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** Consulta DNI peruano. Returns empty on 404/error. */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> consultarDni(String dni) {
        return webClient.get()
                .uri("/dni/info/{dni}", dni)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .doOnError(e -> log.warn("Factiliza DNI {}: {}", dni, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /** Consulta Carnet de Extranjería. Returns empty on 404/error. */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> consultarCee(String cee) {
        return webClient.get()
                .uri("/cee/info/{cee}", cee)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .doOnError(e -> log.warn("Factiliza CEE {}: {}", cee, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /** Consulta licencia de conducir por DNI. Returns empty on 404/error. */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> consultarLicencia(String dni) {
        return webClient.get()
                .uri("/licencia/info/{dni}", dni)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .doOnError(e -> log.warn("Factiliza licencia {}: {}", dni, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
