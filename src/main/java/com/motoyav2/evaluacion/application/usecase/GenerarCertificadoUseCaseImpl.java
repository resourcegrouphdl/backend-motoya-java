package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.DatosVendedor;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.GenerarCertificadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.external.CertificadoExternoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Si el certificado ya fue generado (urlCertificado en Firestore) lo devuelve.
 * Si no, lo genera on-demand llamando al servicio externo, guarda la URL y la devuelve.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerarCertificadoUseCaseImpl implements GenerarCertificadoUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final CertificadoExternoClient certificadoExternoClient;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;

    @Override
    public Mono<String> ejecutar(String solicitudIdOrNumero) {
        return solicitudRepository.findById(solicitudIdOrNumero)
                .switchIfEmpty(solicitudRepository.findByNumeroSolicitud(solicitudIdOrNumero))
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(solicitudIdOrNumero)))
                .flatMap(solicitud -> {
                    // Ya existe → devolver directamente
                    if (solicitud.getUrlCertificado() != null && !solicitud.getUrlCertificado().isBlank()) {
                        log.info("[CERT] Certificado ya existente para solicitud {}", solicitudIdOrNumero);
                        return Mono.just(solicitud.getUrlCertificado());
                    }

                    // No existe → generar on-demand
                    log.info("[CERT] Generando certificado on-demand para solicitud {}", solicitudIdOrNumero);
                    return obtenerExpedienteUseCase.ejecutar(solicitud.getId())
                            .flatMap(expediente -> {
                                Cliente titular      = expediente.getTitular();
                                Cliente fiador       = expediente.getFiador();
                                Vehiculo vehiculo    = expediente.getVehiculo();
                                DatosVendedor vend   = solicitud.getVendedor();

                                CertificadoExternoClient.CertificadoRequest request =
                                        CertificadoExternoClient.CertificadoRequest.of(
                                                solicitud.getCodigoDeSolicitud() != null
                                                        ? solicitud.getCodigoDeSolicitud()
                                                        : solicitud.getNumeroSolicitud(),
                                                titular != null ? titular.getNombreCompleto()  : solicitud.getTitularNombreCompleto(),
                                                fiador  != null ? fiador.getNombreCompleto()   : null,
                                                vend    != null ? vend.getTienda()             : "",
                                                vend    != null ? vend.getNombre()             : solicitud.getVendedorNombre(),
                                                vehiculo != null ? vehiculo.getMarca()         : "",
                                                vehiculo != null ? vehiculo.getModelo()        : "",
                                                vehiculo != null ? vehiculo.getAnio()          : "",
                                                vehiculo != null ? vehiculo.getColor()         : "",
                                                solicitud.getPrecioCompraMoto() != null
                                                        ? solicitud.getPrecioCompraMoto().doubleValue() : 0.0,
                                                solicitud.getInicial() != null
                                                        ? solicitud.getInicial().doubleValue() : 0.0,
                                                solicitud.getMontoCuota() != null
                                                        ? solicitud.getMontoCuota().doubleValue() : 0.0,
                                                solicitud.getPlazoQuincenas() != null
                                                        ? solicitud.getPlazoQuincenas() : 0
                                        );

                                return certificadoExternoClient.generarCertificado(request)
                                        .flatMap(url -> {
                                            Map<String, Object> updates = new HashMap<>();
                                            updates.put("urlCertificado",             url);
                                            updates.put("certificadoGenerado",         true);
                                            updates.put("fechaGeneracionCertificado",  Timestamp.now());
                                            updates.put("updatedAt",                   Timestamp.now());

                                            Mono<Void> transicion = EstadoSolicitud.APROBADO
                                                    .equals(solicitud.getEstado())
                                                    ? cambiarEstadoUseCase.ejecutar(new CambiarEstadoCommand(
                                                            solicitud.getId(),
                                                            EstadoSolicitud.CERTIFICADO_GENERADO,
                                                            "sistema-automatico",
                                                            "Generación manual de certificado",
                                                            null))
                                                    .then()
                                                    : Mono.empty();

                                            return solicitudRepository.updateFields(solicitud.getId(), updates)
                                                    .then(transicion)
                                                    .thenReturn(url);
                                        });
                            });
                });
    }
}
