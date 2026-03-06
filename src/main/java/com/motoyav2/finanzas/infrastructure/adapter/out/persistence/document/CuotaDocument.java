package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaDocument {
    private String id;
    private String cuentaId;
    private Integer numero;
    private Double monto;
    private String fechaVencimiento;
    private String fechaPago;
    private String estado;
    private String proveedor;
    private String descripcion;
    private String tipo;
    private String actualizadoEn;
}
