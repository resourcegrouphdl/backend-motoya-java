package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Alerta detectada durante una entrevista.
 * Parsed desde el Map embebido en evaluacionEntrevista.
 */
@Getter
@Builder
public class AlertaEntrevista {

    // tipos del contrato TypeScript
    private final String tipo;          // inconsistencia_datos | actitud_sospechosa | domicilio_dudoso
                                        // ingresos_no_comprobables | fiador_renuente | referencias_invalidas | otro
    private final String descripcion;
    private final String severidad;     // baja | media | alta | critica
    private final String timestamp;

    public boolean esCritica() {
        return "critica".equalsIgnoreCase(severidad);
    }

    public boolean esAlta() {
        return "alta".equalsIgnoreCase(severidad);
    }
}
