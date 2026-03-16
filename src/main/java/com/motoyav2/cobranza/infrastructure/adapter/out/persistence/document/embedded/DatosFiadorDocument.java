package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del fiador/garante embebidos en CasoCobranzaDocument.
 * Firestore lo almacena como sub-map dentro del documento raíz.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DatosFiadorDocument {
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefono;
    private String email;
    private String parentesco;

    public String nombreCompleto() {
        String n = nombres == null ? "" : nombres.trim();
        String a = apellidos == null ? "" : apellidos.trim();
        return (n + " " + a).trim();
    }
}
