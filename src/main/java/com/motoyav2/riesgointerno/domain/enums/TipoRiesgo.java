package com.motoyav2.riesgointerno.domain.enums;

public enum TipoRiesgo {
    INCUMPLIMIENTO_PAGO,
    FRAUDE,
    DATOS_FALSOS,
    CONDUCTA_IRREGULAR,
    DEUDA_NEGOCIADA,
    REFERENCIAS_FALSAS,
    ABANDONO_VEHICULO,
    OTRO;

    public static TipoRiesgo fromString(String v) {
        if (v == null) return OTRO;
        try { return valueOf(v.toUpperCase()); }
        catch (IllegalArgumentException e) { return OTRO; }
    }
}
