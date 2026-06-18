package com.motoyav2.cobranza.domain.service;

import com.motoyav2.cobranza.domain.enums.EstadoCaso;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class EstadoCasoMachineTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "EN_SEGUIMIENTO,        INTERVENCION_REQUERIDA",
        "EN_SEGUIMIENTO,        PROMESA_VIGENTE",
        "EN_SEGUIMIENTO,        PROMESA_VENCE_HOY",
        "EN_SEGUIMIENTO,        PROMESA_INCUMPLIDA",
        "INTERVENCION_REQUERIDA, EN_SEGUIMIENTO",
        "INTERVENCION_REQUERIDA, PROMESA_VIGENTE",
        "PROMESA_VIGENTE,        EN_SEGUIMIENTO",
        "PROMESA_VIGENTE,        PROMESA_VENCE_HOY",
        "PROMESA_VIGENTE,        PROMESA_INCUMPLIDA",
        "PROMESA_VENCE_HOY,      EN_SEGUIMIENTO",
        "PROMESA_VENCE_HOY,      PROMESA_INCUMPLIDA",
        "PROMESA_VENCE_HOY,      PROMESA_VIGENTE",
        "PROMESA_INCUMPLIDA,     EN_SEGUIMIENTO",
        "PROMESA_INCUMPLIDA,     INTERVENCION_REQUERIDA",
        "PROMESA_INCUMPLIDA,     PROMESA_VIGENTE",
    })
    void transicionesValidasNoLanzan(String desde, String hacia) {
        assertThatNoException().isThrownBy(() ->
            EstadoCasoMachine.validar(
                EstadoCaso.valueOf(desde),
                EstadoCaso.valueOf(hacia)
            )
        );
    }

    @ParameterizedTest(name = "{0} → {1} debe lanzar")
    @CsvSource({
        "EN_SEGUIMIENTO,    EN_SEGUIMIENTO",
        "PROMESA_VIGENTE,   INTERVENCION_REQUERIDA",
        "PROMESA_INCUMPLIDA, PROMESA_VENCE_HOY",
    })
    void transicionesInvalidasLanzan(String desde, String hacia) {
        assertThatThrownBy(() ->
            EstadoCasoMachine.validar(
                EstadoCaso.valueOf(desde),
                EstadoCaso.valueOf(hacia)
            )
        ).isInstanceOf(IllegalStateException.class);
    }
}
