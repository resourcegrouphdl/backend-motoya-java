package com.motoyav2.riesgointerno.domain.enums;

public enum TipoDocumento {
    DNI, CE, PASAPORTE;

    public static TipoDocumento fromString(String value) {
        if (value == null || value.isBlank()) return DNI;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DNI;
        }
    }
}
