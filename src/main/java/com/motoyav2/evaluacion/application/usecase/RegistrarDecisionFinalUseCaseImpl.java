package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.application.command.DecisionFinalCommand;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.Cliente;
import com.motoyav2.evaluacion.domain.model.DatosVendedor;
import com.motoyav2.evaluacion.domain.model.Expediente;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.ObtenerExpedienteUseCase;
import com.motoyav2.evaluacion.domain.port.in.RegistrarDecisionFinalUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.model.OpcionFinanciamiento;
import com.motoyav2.evaluacion.domain.model.ResultadoCalculoFinanciamiento;
import com.motoyav2.evaluacion.domain.service.CalculadoraFinanciamientoService;
import com.motoyav2.evaluacion.infrastructure.adapter.out.external.CertificadoExternoClient;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrarDecisionFinalUseCaseImpl implements RegistrarDecisionFinalUseCase {

    private final SolicitudRepository solicitudRepository;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;
    private final ObtenerExpedienteUseCase obtenerExpedienteUseCase;
    private final CertificadoExternoClient certificadoExternoClient;
    private final NotificationFacade notificationFacade;

    @Override
    public Mono<Solicitud> ejecutar(DecisionFinalCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = buildUpdates(solicitud, command, ahora);

                    EstadoSolicitud nuevoEstado = resolverEstado(command.decision());
                    CambiarEstadoCommand estadoCmd = new CambiarEstadoCommand(
                            command.solicitudId(),
                            nuevoEstado,
                            command.usuarioId(),
                            command.usuarioNombre(),
                            command.motivo()
                    );

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .then(cambiarEstadoUseCase.ejecutar(estadoCmd))
                            .then(solicitudRepository.findById(command.solicitudId()))
                            .flatMap(solicitudActualizada -> {
                                if (command.decision() != Decision.APROBADO) {
                                    return Mono.just(solicitudActualizada);
                                }
                                // Fire-and-forget: certificado + notificaciones no bloquean la respuesta
                                dispararFlujoCertificadoYNotificaciones(solicitudActualizada)
                                        .subscribe(
                                                null,
                                                ex -> log.error("[DECISION] Error en flujo post-aprobación para {}: {}",
                                                        command.solicitudId(), ex.getMessage())
                                        );
                                return Mono.just(solicitudActualizada);
                            });
                });
    }

    /**
     * Genera el certificado de aprobación via servicio externo, guarda la URL en Firestore
     * y envía notificaciones por WhatsApp y email al titular y fiador.
     * Se ejecuta en background (fire-and-forget).
     */
    private Mono<Void> dispararFlujoCertificadoYNotificaciones(Solicitud solicitud) {
        return obtenerExpedienteUseCase.ejecutar(solicitud.getId())
                .flatMap(expediente -> {
                    Cliente titular = expediente.getTitular();
                    Cliente fiador  = expediente.getFiador();

                    Vehiculo vehiculo = expediente.getVehiculo();
                    DatosVendedor vendedor = solicitud.getVendedor();

                    CertificadoExternoClient.CertificadoRequest request = CertificadoExternoClient.CertificadoRequest.of(
                            solicitud.getCodigoDeSolicitud() != null
                                    ? solicitud.getCodigoDeSolicitud() : solicitud.getNumeroSolicitud(),
                            titular != null ? titular.getNombreCompleto()  : solicitud.getTitularNombreCompleto(),
                            fiador  != null ? fiador.getNombreCompleto()   : null,
                            vendedor != null ? vendedor.getTienda()        : "",
                            vendedor != null ? vendedor.getNombre()        : solicitud.getVendedorNombre(),
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
                                Map<String, Object> certUpdates = new HashMap<>();
                                certUpdates.put("urlCertificado",              url);
                                certUpdates.put("certificadoGenerado",         true);
                                certUpdates.put("fechaGeneracionCertificado",  Timestamp.now());
                                certUpdates.put("updatedAt",                   Timestamp.now());

                                CambiarEstadoCommand certCmd = new CambiarEstadoCommand(
                                        solicitud.getId(),
                                        EstadoSolicitud.CERTIFICADO_GENERADO,
                                        "sistema-automatico",
                                        "Generación automática de certificado",
                                        "Certificado generado tras aprobación"
                                );

                                return solicitudRepository.updateFields(solicitud.getId(), certUpdates)
                                        .then(cambiarEstadoUseCase.ejecutar(certCmd))
                                        .then(notificationFacade.notificarCreditoAprobado(
                                                solicitud.getId(),
                                                titular != null ? titular.getTelefono1()    : solicitud.getTitularTelefono(),
                                                titular != null ? titular.getEmail()         : solicitud.getTitularEmail(),
                                                titular != null ? titular.getNombreCompleto(): solicitud.getTitularNombreCompleto(),
                                                fiador  != null ? fiador.getTelefono1()     : null,
                                                fiador  != null ? fiador.getEmail()          : null,
                                                fiador  != null ? fiador.getNombreCompleto(): null,
                                                request.numeroDeSolicitud(),
                                                url
                                        ));
                            });
                })
                .onErrorResume(ex -> {
                    log.error("[DECISION] Error en dispararFlujoCertificadoYNotificaciones para solicitud={}: {}",
                            solicitud.getId(), ex.getMessage(), ex);
                    return Mono.empty();
                });
    }

    private Map<String, Object> buildUpdates(Solicitud solicitud, DecisionFinalCommand command, Timestamp ahora) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("decisionFinal", command.decision().getFirestoreValue());
        updates.put("motivoDecision", command.motivo());
        updates.put("fortalezasCaso", command.fortalezasCaso());
        updates.put("debilidadesCaso", command.debilidadesCaso());
        updates.put("evaluador", command.evaluador());
        updates.put("usuarioDecision", command.usuarioId());
        updates.put("fechaDecisionFinal", ahora);
        updates.put("updatedAt", ahora);
        updates.put("resultadoFinal", resolverResultadoFinal(command.decision()));

        if (command.condiciones() != null && !command.condiciones().isEmpty()) {
            updates.put("condicionesAprobacion", command.condiciones());
        }

        // Recalcular financieros si se ajustaron parámetros
        if (command.inicialAjustada() != null || command.plazoAjustado() != null) {
            BigDecimal precio = solicitud.getDatosFinancieros() != null
                    ? solicitud.getDatosFinancieros().getMontoVehiculo()
                    : solicitud.getPrecioCompraMoto();

            BigDecimal inicial = command.inicialAjustada() != null
                    ? command.inicialAjustada()
                    : (solicitud.getDatosFinancieros() != null
                            ? solicitud.getDatosFinancieros().getInicial()
                            : solicitud.getInicial());

            int plazo = command.plazoAjustado() != null
                    ? command.plazoAjustado()
                    : solicitud.getPlazoQuincenas();

            try {
                ResultadoCalculoFinanciamiento calc = CalculadoraFinanciamientoService
                        .calcularFinanciamientoCompleto(precio, inicial);
                OpcionFinanciamiento opcion = calc.getOpciones().stream()
                        .filter(op -> op.getQuincenas() == plazo)
                        .findFirst()
                        .orElse(null);

                if (opcion != null) {
                    BigDecimal costoTotal = CalculadoraFinanciamientoService.calcularPrecioTotal(precio);
                    BigDecimal pct = costoTotal.compareTo(BigDecimal.ZERO) > 0
                            ? inicial.divide(costoTotal, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    updates.put("inicial",        inicial.doubleValue());
                    updates.put("montoCuota",     opcion.getCuotaQuincenal().doubleValue());
                    updates.put("plazoQuincenas", plazo);

                    if (solicitud.getDatosFinancieros() != null) {
                        Map<String, Object> df = new HashMap<>();
                        df.put("inicial",                  inicial.doubleValue());
                        df.put("montoCuotaQuincenal",       opcion.getCuotaQuincenal().doubleValue());
                        df.put("numeroCuotasQuincenales",   plazo);
                        df.put("montoFinanciar",            calc.getDatosCalculados().getMontoFinanciar().doubleValue());
                        df.put("totalAPagar",               opcion.getSumaTotal().doubleValue());
                        df.put("porcentajeInicial",         pct.doubleValue());
                        df.put("interesTotal",              opcion.getInteresTotal().doubleValue());
                        df.put("tasaLineal",                opcion.getTasa().doubleValue());
                        df.put("modoCalculadora",           "SIMPLIFICADO");
                        updates.put("datosFinancieros", df);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Si la validación falla por datos inconsistentes del sistema legado,
                // no bloqueamos la decisión final — solo omitimos el recálculo financiero.
            }
        }
        return updates;
    }

    private EstadoSolicitud resolverEstado(Decision decision) {
        return switch (decision) {
            case APROBADO    -> EstadoSolicitud.APROBADO;
            case RECHAZADO   -> EstadoSolicitud.RECHAZADO;
            case CONDICIONAL -> EstadoSolicitud.CONDICIONAL;
            default          -> EstadoSolicitud.EN_REVISION_FINAL;
        };
    }

    private String resolverResultadoFinal(Decision decision) {
        return switch (decision) {
            case APROBADO    -> "aprobado";
            case RECHAZADO   -> "rechazado";
            case CONDICIONAL -> "observado / subsanar";
            default          -> null;
        };
    }
}
