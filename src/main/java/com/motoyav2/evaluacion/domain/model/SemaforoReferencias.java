package com.motoyav2.evaluacion.domain.model;

/**
 * Indicador visual del estado agregado de todas las referencias de una solicitud.
 * VERDE   → 2 o más referencias confirmadas positivamente.
 * AMARILLO → referencias pendientes, en proceso, o respuestas ambiguas.
 * ROJO    → al menos una referencia con respuesta negativa.
 */
public enum SemaforoReferencias {
    VERDE,
    AMARILLO,
    ROJO;

    public String toFirestoreValue() {
        return name().toLowerCase();
    }

    public static SemaforoReferencias fromFirestoreValue(String v) {
        if (v == null) return AMARILLO;
        return switch (v.toUpperCase()) {
            case "VERDE"   -> VERDE;
            case "ROJO"    -> ROJO;
            default        -> AMARILLO;
        };
    }
}
