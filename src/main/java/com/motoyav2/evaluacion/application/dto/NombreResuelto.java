package com.motoyav2.evaluacion.application.dto;

/**
 * Nombres y apellidos resueltos aplicando la política de fuente de verdad:
 * si la verificación de identidad fue exitosa (RENIEC/Factiliza), se usan
 * los datos de la API; en caso contrario se usan los datos del formulario.
 */
public record NombreResuelto(
        String nombres,       // nombres de pila (ej. "JOSE PEDRO")
        String apellidos,     // apellido paterno + materno (ej. "CASTILLO TERRONES")
        boolean desdeReniec   // true → fuente RENIEC | false → fuente formulario
) {
    /** Nombre completo en formato "NOMBRES APELLIDOS". */
    public String nombreCompleto() {
        String n = nombres == null ? "" : nombres.trim();
        String a = apellidos == null ? "" : apellidos.trim();
        return (n + " " + a).trim();
    }
}
