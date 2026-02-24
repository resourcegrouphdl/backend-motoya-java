package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VariablePlantilla {

    private String nombre;
    private String descripcion;
    private String valorEjemplo;
}
