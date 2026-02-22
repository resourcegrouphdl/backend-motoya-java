package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class CuotaCronogramaEmbedded {
    private Integer numeroCuota;
    private Timestamp fechaVencimiento;
    private Double montoCuota;
    private Double montoCapital;
    private Double montoInteres;
    private Double saldoPendiente;
    private String estadoPago;
    private Timestamp fechaPago;
    private Double montoPagado;
    private Integer diasMora;
    private Double montoMora;
}
