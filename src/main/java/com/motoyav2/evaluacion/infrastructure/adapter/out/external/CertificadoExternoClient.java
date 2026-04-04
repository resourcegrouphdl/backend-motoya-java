package com.motoyav2.evaluacion.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Cliente HTTP para el servicio externo de generación de certificados de aprobación.
 * Endpoint: POST https://backen-motoya-cloud-26647667439.southamerica-west1.run.app/api/v1/certificado
 * Sin autenticación por el momento (pública).
 */
@Slf4j
@Component
public class CertificadoExternoClient {

    private static final String BASE_URL =
            "https://backen-motoya-cloud-26647667439.southamerica-west1.run.app";
    private static final String PATH = "/api/v1/certificado";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WebClient webClient;

    public CertificadoExternoClient() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Llama al servicio externo y retorna la URL del certificado PNG generado.
     */
    public Mono<String> generarCertificado(CertificadoRequest request) {
        return webClient.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CertificadoResponse.class)
                .map(CertificadoResponse::urlDelCertificadoGenerado)
                .doOnSuccess(url -> log.info("[CERT] Certificado generado OK: {}", url))
                .doOnError(ex -> log.error("[CERT] Error generando certificado: {}", ex.getMessage()));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record CertificadoRequest(
            String codigoDeSolicitud,
            String titularNombreCompleto,
            String titularDni,
            String titularTelefono,
            String titularEmail,
            String fiadorNombreCompleto,
            String fiadorDni,
            String vehiculoDescripcion,
            Double precioMoto,
            Double inicial,
            Integer plazoQuincenas,
            Double cuotaQuincenal,
            String fechaAprobacion
    ) {
        public static CertificadoRequest of(
                String codigo, String titularNombre, String titularDni,
                String titularTel, String titularEmail,
                String fiadorNombre, String fiadorDni,
                String vehiculoDesc,
                double precioMoto, double inicial,
                int plazoQuincenas, double cuotaQuincenal) {
            return new CertificadoRequest(
                    codigo, titularNombre, titularDni, titularTel, titularEmail,
                    fiadorNombre, fiadorDni, vehiculoDesc,
                    precioMoto, inicial, plazoQuincenas, cuotaQuincenal,
                    LocalDate.now().format(DATE_FMT)
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CertificadoResponse(String urlDelCertificadoGenerado) {}
}
