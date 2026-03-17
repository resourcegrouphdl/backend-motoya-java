package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.DecisionFinalUseCase;
import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.application.port.out.ReferenciasPort;
import com.motoyav2.evaluacion.application.port.out.SolicitudActualizacionPort;
import com.motoyav2.evaluacion.application.port.out.VehiculoPort;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.domain.model.decision.ResultadoDecision;
import com.motoyav2.evaluacion.domain.model.riesgo.PerfilRiesgo;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreResult;
import com.motoyav2.evaluacion.domain.service.AnalizadorRiesgo;
import com.motoyav2.evaluacion.domain.service.CalculadoraScore;
import com.motoyav2.evaluacion.domain.service.MotorDeDecision;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision.DecisionFinalResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseSolicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionFinalService implements DecisionFinalUseCase {

    private final FirebaseSolicitudRepository solicitudRepository;
    private final ClientePort clientePort;
    private final VehiculoPort vehiculoPort;
    private final ReferenciasPort referenciasPort;
    private final SolicitudActualizacionPort solicitudActualizacion;
    private final CalculadoraScore calculadoraScore;
    private final AnalizadorRiesgo analizadorRiesgo;
    private final MotorDeDecision motorDeDecision;

    @Override
    public Mono<DecisionFinalResponse> ejecutar(String solicitudId, DecisionFinalRequest req) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(sol -> cargarDatosYDecidir(sol, req));
    }

    private Mono<DecisionFinalResponse> cargarDatosYDecidir(FirebaseSolicitud sol, DecisionFinalRequest req) {
        String titularId  = sol.getTitularId();
        String fiadorId   = sol.getFiadorId();
        List<String> refsIds = sol.getReferenciasIds() != null ? sol.getReferenciasIds() : List.of();
        String montoCuotaStr = sol.getMontoCuota() != null ? sol.getMontoCuota().toString() : null;

        Mono<Persona> titularMono = clientePort.buscarPorId(titularId);
        Mono<Persona> fiadorMono  = fiadorId != null && !fiadorId.isBlank()
                ? clientePort.buscarPorId(fiadorId).defaultIfEmpty(Persona.builder().build())
                : Mono.just(Persona.builder().build());
        Mono<List<ReferenciasDelTitular>> refsMono = referenciasPort.buscarPorIds(refsIds).collectList();

        return Mono.zip(titularMono, fiadorMono, refsMono)
                .flatMap(t -> {
                    Persona titular  = t.getT1();
                    Persona fiadorRaw = t.getT2();
                    Persona fiador   = fiadorRaw.getId() != null ? fiadorRaw : null;
                    List<ReferenciasDelTitular> refs = t.getT3();

                    // Calcular scores en tiempo real
                    ScoreResult scores = calculadoraScore.calcularTodo(titular, fiador, refs, montoCuotaStr);
                    PerfilRiesgo perfil = analizadorRiesgo.analizar(titular, fiador, refs, scores);

                    // Motor de decisión automático
                    ResultadoDecision autoDecision = motorDeDecision.evaluar(scores, perfil);

                    // Resolución final: manual override o automático
                    boolean esManual = req.getDecisionManual() != null && !req.getDecisionManual().isBlank();
                    String decisionFinal = esManual ? req.getDecisionManual().toLowerCase() : autoDecision.toEstadoSolicitud();
                    Double montoSolicitado = sol.getPrecioCompraMoto() != null
                            ? (double) sol.getPrecioCompraMoto() : null;
                    Double montoAprobado = resolverMonto(req, autoDecision, montoSolicitado, decisionFinal);
                    List<String> condiciones = resolverCondiciones(req, autoDecision, decisionFinal);

                    // Extraer valores numéricos de los scores compuestos
                    Double scoreDocumentalValor = scores.getScoreDocumental() != null
                            ? scores.getScoreDocumental().getValor() : null;
                    Double scoreGarantesValor = scores.getScoreGarantes() != null
                            ? scores.getScoreGarantes().getValor() : null;
                    Double scoreEntrevistaValor = scores.getScoreEntrevista() != null
                            ? scores.getScoreEntrevista().getValor() : null;
                    String nivelCapacidad = scores.getCapacidadDePago() != null
                            ? scores.getCapacidadDePago().getNivelCapacidad() : "INSUFICIENTE";

                    log.info("Decisión final — solicitud: {}, decisionAuto: {}, decisionFinal: {}, score: {}",
                            sol.getFormularioId(), autoDecision.toEstadoSolicitud(), decisionFinal, scores.getScoreFinal());

                    // Persistir en Firestore
                    return solicitudActualizacion.actualizarDecisionFinal(
                            sol.getFormularioId(),
                            decisionFinal,
                            montoAprobado,
                            req.getMotivoDecision(),
                            req.getMotivoRechazo(),
                            condiciones,
                            req.getFortalezasCaso(),
                            req.getDebilidadesCaso(),
                            req.getUsuarioId(),
                            scores.getScoreFinal(),
                            scoreDocumentalValor,
                            scoreGarantesValor,
                            scoreEntrevistaValor,
                            scores.getScoreFinal()
                    ).thenReturn(
                        DecisionFinalResponse.builder()
                                .success(true)
                                .solicitudId(sol.getFormularioId())
                                .decisionFinal(decisionFinal.toUpperCase())
                                .decisionFueManual(esManual)
                                .decisionAutomatica(autoDecision.toEstadoSolicitud().toUpperCase())
                                .estadoNuevo(decisionFinal)
                                .montoAprobado(montoAprobado)
                                .montoSolicitado(montoSolicitado)
                                .porcentajeMontoAprobado(calcularPorcentaje(montoAprobado, montoSolicitado))
                                .motivoDecision(req.getMotivoDecision())
                                .motivoRechazo(req.getMotivoRechazo())
                                .condicionesAprobacion(condiciones)
                                .fortalezasCaso(req.getFortalezasCaso())
                                .debilidadesCaso(req.getDebilidadesCaso())
                                .justificacionMotor(autoDecision.getJustificacion())
                                .scoreFinal(scores.getScoreFinal())
                                .nivelRiesgo(perfil.getNivelGeneral().name())
                                .nivelCapacidadPago(nivelCapacidad)
                                .mensaje("Decisión registrada: " + decisionFinal.toUpperCase())
                                .build()
                    );
                });
    }

    private Double resolverMonto(DecisionFinalRequest req, ResultadoDecision auto,
                                  Double montoSolicitado, String decisionFinal) {
        if (req.getMontoAprobadoManual() != null) return req.getMontoAprobadoManual();
        if ("rechazado".equals(decisionFinal)) return null;
        if (montoSolicitado == null) return null;
        return montoSolicitado * auto.getPorcentajeMontoRecomendado();
    }

    private List<String> resolverCondiciones(DecisionFinalRequest req, ResultadoDecision auto, String decision) {
        if (req.getCondicionesAprobacion() != null && !req.getCondicionesAprobacion().isEmpty()) {
            return req.getCondicionesAprobacion();
        }
        return "condicional".equals(decision) ? auto.getCondicionesRecomendadas() : List.of();
    }

    private double calcularPorcentaje(Double montoAprobado, Double montoSolicitado) {
        if (montoAprobado == null || montoSolicitado == null || montoSolicitado == 0) return 0;
        return Math.round((montoAprobado / montoSolicitado) * 100.0) / 100.0;
    }
}
