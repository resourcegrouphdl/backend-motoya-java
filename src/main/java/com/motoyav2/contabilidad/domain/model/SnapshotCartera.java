package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class SnapshotCartera {

    int totalContratos;
    Double capitalOriginalTotal;
    Double saldoPendienteTotal;
    Double totalPagado;
    Double totalMora;
    Double porcentajeRecuperacion;
    LocalDate fechaCorte;
}
