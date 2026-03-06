package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoDocument {
    private String id;
    private String facturaId;
    private Integer numero;
    private String concepto;
    private Double monto;
    private String fechaProgramada;
    private String fechaPago;
    private String estado;
    private String voucherUrl;
    private String metodoPago;
    private String tiendaId;
    private String tiendaNombre;
    private String clienteNombre;
    private String actualizadoEn;
}
