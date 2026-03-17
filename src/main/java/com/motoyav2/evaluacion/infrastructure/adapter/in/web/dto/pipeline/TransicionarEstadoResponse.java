package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class TransicionarEstadoResponse {
    private final boolean success;
    private final String solicitudId;
    private final String estadoAnterior;
    private final String estadoNuevo;
    private final String mensaje;
    private final Set<String> transicionesDisponibles;  // próximos estados válidos
}
