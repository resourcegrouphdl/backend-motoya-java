package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class DiscrepanciaVoucher {

    String voucherId;
    String contratoId;
    String clienteNombre;
    Double montoDetectado;
    Double montoEsperado;
    Double diferencia;
    Double confianzaOcr;
    String banco;
    String estado;
    LocalDate fecha;
}
