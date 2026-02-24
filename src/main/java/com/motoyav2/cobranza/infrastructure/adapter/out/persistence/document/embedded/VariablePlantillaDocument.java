package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VariablePlantillaDocument {

    /** Ej: "nombre_cliente" */
    private String nombre;
    /** Ej: "Nombre completo del cliente" */
    private String descripcion;
    /** Ej: "Juan Pérez" */
    private String valorEjemplo;
}
