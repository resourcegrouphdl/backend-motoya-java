package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EvaluacionDocumento {
    String estado;          // pendiente | aprobado | observado | rechazado
    String observaciones;
    Timestamp fechaEvaluacion;
    String evaluador;
}
