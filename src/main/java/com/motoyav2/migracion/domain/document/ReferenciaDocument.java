package com.motoyav2.migracion.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persona de referencia del cliente, embebida en MigracionStagingDocument.
 * Se migra al campo referencias del CasoCobranza.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferenciaDocument {

    /** Nombre completo */
    private String nombre;
    /** Teléfono de contacto */
    private String telefono;
    /** Relación con el titular: FAMILIAR, VECINO, TRABAJO, OTRO */
    private String parentesco;
    /** Dirección referencial */
    private String direccion;
}
