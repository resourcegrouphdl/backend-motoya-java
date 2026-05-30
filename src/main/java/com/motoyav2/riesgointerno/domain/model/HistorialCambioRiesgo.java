package com.motoyav2.riesgointerno.domain.model;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HistorialCambioRiesgo {
    Timestamp fecha;
    String usuario;
    String cambio;
    String motivoCambio;
}
