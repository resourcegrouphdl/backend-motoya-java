package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.decision;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request para registrar la decisión final sobre una solicitud.
 *
 * Si decisionManual es null → se usa la decisión calculada por MotorDeDecision.
 * Si decisionManual está presente → overrides la decisión automática.
 *
 * Estados válidos para decisionManual: 'aprobado' | 'rechazado' | 'condicional'
 */
@Getter
@NoArgsConstructor
public class DecisionFinalRequest {

    private String decisionManual;              // opcional — override de la decisión automática
    private Double montoAprobadoManual;         // opcional — override del monto calculado
    private String motivoDecision;
    private String motivoRechazo;               // requerido si decision == rechazado
    private List<String> condicionesAprobacion; // requerido si decision == condicional
    private String fortalezasCaso;
    private String debilidadesCaso;
    private String usuarioId;
    private String usuarioNombre;
}
