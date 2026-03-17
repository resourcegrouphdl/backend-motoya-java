package com.motoyav2.evaluacion.domain.model;

public record TiendaInfo(
    String tiendaId,   // UID del documento en tienda_profiles
    String nombre,
    String codigo,     // taxId / RUC
    String direccion,
    String ciudad,
    String email,
    String telefono
) {
    /** Constructor de compatibilidad con código existente que solo pasaba nombre+codigo. */
    public TiendaInfo(String nombre, String codigo) {
        this(null, nombre, codigo, null, null, null, null);
    }
}
