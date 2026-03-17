package com.motoyav2.evaluacion.domain.model.riesgo;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object que representa una señal de riesgo detectada en el expediente.
 */
@Getter
@Builder
public class FlagRiesgo {

    public enum TipoFlag {
        SENTINEL_NEGATIVO,
        SENTINEL_MODERADO,
        PAPELETAS_PENDIENTES,
        LICENCIA_VENCIDA,
        DOCUMENTO_IDENTIDAD_VENCIDO,
        RATIO_CUOTA_ALTO,
        RATIO_CUOTA_MUY_ALTO,
        REFERENCIAS_NEGATIVAS,
        REFERENCIAS_INSUFICIENTES,
        ALERTA_ENTREVISTA_CRITICA,
        ALERTA_ENTREVISTA_ALTA,
        SCORE_DOCUMENTAL_BAJO,
        SCORE_FINAL_BAJO,
        SIN_ENTREVISTA,
        SIN_FIADOR_CASADO,
        INGRESOS_NO_COMPROBABLES,
        CAPACIDAD_PAGO_INSUFICIENTE
    }

    private final TipoFlag tipo;
    private final NivelRiesgo severidad;
    private final String descripcion;
    private final String origen;        // "titular" | "fiador" | "referencias" | "financiamiento"
}
