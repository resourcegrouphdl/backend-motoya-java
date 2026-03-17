package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.referencias;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificarReferenciaResponse {
    private final boolean success;
    private final String solicitudId;
    private final String referenciaId;
    private final Integer numeroReferencia;
    private final String estadoVerificacion;
    private final double scoreVerificacion;
    private final double scoreReferencias;      // score recalculado sobre todas las refs
    private final int referenciasVerificadas;
    private final int referenciasRechazadas;
    private final int totalReferencias;
    private final String mensaje;
}
