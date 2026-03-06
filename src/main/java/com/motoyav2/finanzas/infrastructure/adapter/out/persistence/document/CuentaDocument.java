package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaDocument {
    private String id;
    private String tipo;
    private String proveedor;
    private String descripcion;
    private String numeroDocumento;
    private Double montoTotal;
    private Integer numeroCuotas;
    private String estado;
    private String fechaVencimiento;
    private String creadoEn;
    private String actualizadoEn;
    private String creadoPor;
    private Boolean alertaActiva;
    private Boolean tieneVencidos;
}
