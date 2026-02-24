package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemComprobanteDocument {

    /** Ej: "Cuota N°4 - Contrato CTR-1001" */
    private String descripcion;
    private Integer cantidad;
    private Double precioUnitario;
    private Double totalItem;
}
