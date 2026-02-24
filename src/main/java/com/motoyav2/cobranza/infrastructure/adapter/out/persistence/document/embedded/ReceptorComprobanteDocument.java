package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReceptorComprobanteDocument {

    /** TipoDocumentoReceptor: DNI | RUC | CE */
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombreCompleto;
    private String direccion;
    private String email;
}
