package com.motoyav2.riesgointerno.domain.enums;

public enum TipoSujeto {
    TITULAR,
    FIADOR,
    REFERENCIA,
    CONTACTO;

    public static TipoSujeto fromString(String v) {
        if (v == null) return CONTACTO;
        try { return valueOf(v.toUpperCase()); }
        catch (IllegalArgumentException e) { return CONTACTO; }
    }
}
