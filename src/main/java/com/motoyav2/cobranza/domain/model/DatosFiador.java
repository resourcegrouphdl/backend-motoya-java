package com.motoyav2.cobranza.domain.model;

import lombok.Builder;

/**
 * Datos del fiador/garante del crédito embebidos directamente en el caso de cobranza.
 * Copia aislada — no depende del módulo de contratos.
 */
@Builder
public record DatosFiador(
        String nombres,
        String apellidos,
        String tipoDocumento,
        String numeroDocumento,
        String telefono,
        String email,
        String parentesco
) {
    public String nombreCompleto() {
        String n = nombres == null ? "" : nombres.trim();
        String a = apellidos == null ? "" : apellidos.trim();
        return (n + " " + a).trim();
    }
}