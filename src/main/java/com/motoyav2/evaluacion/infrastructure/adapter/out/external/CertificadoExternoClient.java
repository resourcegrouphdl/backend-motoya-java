package com.motoyav2.evaluacion.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.DecimalFormat;

/**
 * Cliente HTTP para el servicio externo de generación de certificados de aprobación.
 *
 * POST https://backen-motoya-cloud-26647667439.southamerica-west1.run.app/api/v1/certificado
 *  → genera el certificado y retorna { urlDelCertificadoGenerado }
 *
 * GET  https://backen-motoya-cloud-26647667439.southamerica-west1.run.app/api/v1/certificado/{numeroDeSolicitud}
 *  → recupera un certificado ya generado
 */
@Slf4j
@Component
public class CertificadoExternoClient {

    private static final String BASE_URL =
            "https://backen-motoya-cloud-26647667439.southamerica-west1.run.app";
    private static final String PATH = "/api/v1/certificado";

    private static final DecimalFormat DECIMAL_FMT = new DecimalFormat("#.00");

    private final WebClient webClient;

    public CertificadoExternoClient() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── POST — generar ────────────────────────────────────────────────────────

    public Mono<String> generarCertificado(CertificadoRequest request) {
        log.info("[CERT] Generando certificado para solicitud={}", request.numeroDeSolicitud());
        return webClient.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Servicio certificado error " + response.statusCode().value() + ": " + body))))
                .bodyToMono(CertificadoResponse.class)
                .map(CertificadoResponse::urlDelCertificadoGenerado)
                .doOnSuccess(url -> log.info("[CERT] Certificado generado OK | solicitud={} url={}",
                        request.numeroDeSolicitud(), url))
                .doOnError(ex -> log.error("[CERT] Error generando certificado | solicitud={}: {}",
                        request.numeroDeSolicitud(), ex.getMessage()));
    }

    // ── GET — recuperar existente ─────────────────────────────────────────────

    public Mono<String> buscarCertificado(String numeroDeSolicitud) {
        return webClient.get()
                .uri(PATH + "/{numero}", numeroDeSolicitud)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        response -> Mono.empty())   // 404 → no existe aún
                .bodyToMono(CertificadoResponse.class)
                .map(CertificadoResponse::urlDelCertificadoGenerado)
                .onErrorResume(ex -> {
                    log.debug("[CERT] Certificado no encontrado en servicio externo para {}", numeroDeSolicitud);
                    return Mono.empty();
                });
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /**
     * Request alineado exactamente con CrearCertificadoRequest del servicio externo.
     * Todos los campos numéricos se envían como String según la API.
     */
    public record CertificadoRequest(
            String numeroDeSolicitud,
            String titularDelCredito,
            String fiadorDelCredito,
            String nombreDeLaTienda,
            String asesorDeVenta,
            String marcaDelVehiculo,
            String modeloDelVehiculo,
            String anioDelVehiculo,
            String colorDelVehiculo,
            String precioDelVehiculo,
            String inicialEngancheDelCredito,
            String cuotaQuincenalDelCredito,
            String numeroDeCuotasQuincenalesDelCredito
    ) {
        /**
         * Factory que construye el request desde los objetos del dominio.
         * Los montos se formatean a 2 decimales: "1234.50"
         */
        public static CertificadoRequest of(
                String numeroDeSolicitud,
                String titularNombre,
                String fiadorNombre,
                String nombreTienda,
                String asesorNombre,
                String marca,
                String modelo,
                String anio,
                String color,
                double precioMoto,
                double inicial,
                double cuotaQuincenal,
                int numeroCuotas) {

            return new CertificadoRequest(
                    safe(numeroDeSolicitud),
                    safe(titularNombre),
                    fiadorNombre != null ? fiadorNombre : "",
                    safe(nombreTienda),
                    safe(asesorNombre),
                    safe(marca),
                    safe(modelo),
                    safe(anio),
                    safe(color),
                    DECIMAL_FMT.format(precioMoto),
                    DECIMAL_FMT.format(inicial),
                    DECIMAL_FMT.format(cuotaQuincenal),
                    String.valueOf(numeroCuotas)
            );
        }

        private static String safe(String s) {
            return s != null ? s : "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CertificadoResponse(String urlDelCertificadoGenerado) {}
}
