package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.application.command.DecisionFinalCommand;
import com.motoyav2.evaluacion.domain.enums.Decision;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.RegistrarDecisionFinalUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.financiamiento.domain.model.ResultadoSimulacion;
import com.motoyav2.financiamiento.domain.model.SolicitudSimulacion;
import com.motoyav2.financiamiento.domain.service.MotorFinancieroService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrarDecisionFinalUseCaseImpl implements RegistrarDecisionFinalUseCase {

    private final SolicitudRepository solicitudRepository;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;

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
                            .then(solicitudRepository.findById(command.solicitudId()));
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

            BigDecimal tea = command.tea() != null
                    ? command.tea()
                    : MotorFinancieroService.TEA_DEFAULT;

            try {
                ResultadoSimulacion sim = MotorFinancieroService.simular(SolicitudSimulacion.builder()
                        .precioVehiculo(precio)
                        .cuotaInicial(inicial)
                        .numeroCuotas(plazo)
                        .tea(tea)
                        .build());

                BigDecimal costoTotal = precio.add(MotorFinancieroService.GASTOS_ADMINISTRATIVOS_DEFAULT);
                BigDecimal pct = costoTotal.compareTo(BigDecimal.ZERO) > 0
                        ? inicial.divide(costoTotal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                updates.put("inicial", inicial.doubleValue());
                updates.put("montoCuota", sim.getCuotaQuincenal().doubleValue());
                updates.put("plazoQuincenas", plazo);

                if (solicitud.getDatosFinancieros() != null) {
                    Map<String, Object> df = new HashMap<>();
                    df.put("inicial", inicial.doubleValue());
                    df.put("montoCuotaQuincenal", sim.getCuotaQuincenal().doubleValue());
                    df.put("numeroCuotasQuincenales", plazo);
                    df.put("montoFinanciar", sim.getMontoFinanciado().doubleValue());
                    df.put("totalAPagar", sim.getTotalPagar().doubleValue());
                    df.put("porcentajeInicial", pct.doubleValue());
                    df.put("tea", tea.doubleValue());
                    df.put("tcea", sim.getTcea().doubleValue());
                    df.put("tasaQuincenal", sim.getTasaQuincenal().doubleValue());
                    updates.put("datosFinancieros", df);
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
