package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComisionDocument {
    private String id;
    private String vendedorId;
    private String vendedorNombre;
    private String tiendaId;
    private String tiendaNombre;
    private String periodoInicio;
    private String periodoFin;
    private Integer totalVentas;
    private Double montoComision;
    private String estado;
    private String pagadoEn;
    private String creadoEn;
    private String actualizadoEn;
}
