package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import lombok.Data;

@Data
public class TiendaInfoEmbedded {
    private String tiendaId;
    private String nombreTienda;
    private String direccion;
    private String ciudad;
}
