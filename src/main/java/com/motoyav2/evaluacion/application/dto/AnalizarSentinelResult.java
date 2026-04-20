package com.motoyav2.evaluacion.application.dto;

import java.util.List;

public record AnalizarSentinelResult(
        String nivelRiesgo,    // BAJO | MEDIO | ALTO | MUY_ALTO
        String tendencia,      // MEJORANDO | ESTABLE | DETERIORANDO
        String recomendacion,  // APROBAR | CONDICIONAL | RECHAZAR
        String resumen,
        List<String> hallazgos,
        List<String> alertas
) {}
