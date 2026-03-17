package com.motoyav2.evaluacion.domain.model.riesgo;

public enum NivelRiesgo {
    BAJO,
    MEDIO,
    ALTO,
    CRITICO;

    public boolean esMayorQue(NivelRiesgo otro) {
        return this.ordinal() > otro.ordinal();
    }
}
