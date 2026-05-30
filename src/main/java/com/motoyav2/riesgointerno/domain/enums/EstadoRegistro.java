package com.motoyav2.riesgointerno.domain.enums;

public enum EstadoRegistro {
    ACTIVO,
    BAJO_VIGILANCIA,
    NEGOCIADO,
    REHABILITADO;

    public static EstadoRegistro fromString(String v) {
        if (v == null) return null;
        try { return valueOf(v.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
