package com.motoyav2.riesgointerno.domain.enums;

public enum NivelRiesgo {
    ROJO,
    AMARILLO,
    VERDE;

    public static NivelRiesgo fromString(String v) {
        if (v == null) return null;
        try { return valueOf(v.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
