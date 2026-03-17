package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DecisionFinalResponse {
    private final boolean success;
    private final String solicitudId;

    // Decisión
    private final String decisionFinal;             // APROBADO | RECHAZADO | CONDICIONAL
    private final boolean decisionFueManual;        // true si el usuario hizo override
    private final String decisionAutomatica;        // lo que habría decidido el motor
    private final String estadoNuevo;               // estado de solicitud resultante

    // Financiero
    private final Double montoAprobado;
    private final Double montoSolicitado;
    private final double porcentajeMontoAprobado;

    // Justificación
    private final String motivoDecision;
    private final String motivoRechazo;
    private final List<String> condicionesAprobacion;
    private final String fortalezasCaso;
    private final String debilidadesCaso;
    private final String justificacionMotor;        // texto generado por MotorDeDecision

    // Scores que respaldaron la decisión
    private final double scoreFinal;
    private final String nivelRiesgo;
    private final String nivelCapacidadPago;

    private final String mensaje;
}
