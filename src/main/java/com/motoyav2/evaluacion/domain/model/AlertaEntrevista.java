package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AlertaEntrevista {
    String tipo;        // inconsistencia_datos | actitud_sospechosa | ...
    String descripcion;
    String severidad;   // baja | media | alta | critica
    Timestamp timestamp;
}
