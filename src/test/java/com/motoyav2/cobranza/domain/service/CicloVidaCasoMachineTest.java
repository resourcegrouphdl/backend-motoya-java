package com.motoyav2.cobranza.domain.service;

import com.motoyav2.cobranza.domain.enums.CicloVidaCaso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class CicloVidaCasoMachineTest {

    // ── Transiciones válidas ──────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "ACTIVO,          PROMESA_VIGENTE",
        "ACTIVO,          ACUERDO_VIGENTE",
        "ACTIVO,          PAGADO_TOTAL",
        "ACTIVO,          JUDICIAL",
        "ACTIVO,          CASTIGADO",
        "ACTIVO,          CERRADO",
        "PROMESA_VIGENTE, ACTIVO",
        "PROMESA_VIGENTE, PAGADO_TOTAL",
        "PROMESA_VIGENTE, JUDICIAL",
        "PROMESA_VIGENTE, CERRADO",
        "ACUERDO_VIGENTE, ACTIVO",
        "ACUERDO_VIGENTE, PAGADO_TOTAL",
        "ACUERDO_VIGENTE, JUDICIAL",
        "ACUERDO_VIGENTE, CERRADO",
        "JUDICIAL,        CASTIGADO",
        "JUDICIAL,        CERRADO",
        "CASTIGADO,       CERRADO",
    })
    void transicionesValidasNoLanzan(String desde, String hacia) {
        assertThatNoException().isThrownBy(() ->
            CicloVidaCasoMachine.validar(
                CicloVidaCaso.valueOf(desde),
                CicloVidaCaso.valueOf(hacia)
            )
        );
    }

    @Test
    void esTransicionValidaReturnsTrueParaValidasFalseParaInvalidas() {
        assertThat(CicloVidaCasoMachine.esTransicionValida(CicloVidaCaso.ACTIVO, CicloVidaCaso.PAGADO_TOTAL)).isTrue();
        assertThat(CicloVidaCasoMachine.esTransicionValida(CicloVidaCaso.PAGADO_TOTAL, CicloVidaCaso.ACTIVO)).isFalse();
        assertThat(CicloVidaCasoMachine.esTransicionValida(CicloVidaCaso.CERRADO, CicloVidaCaso.ACTIVO)).isFalse();
    }

    // ── Transiciones inválidas deben lanzar IllegalStateException ─────────────

    @ParameterizedTest(name = "{0} → {1} debe lanzar")
    @CsvSource({
        "PAGADO_TOTAL, ACTIVO",
        "PAGADO_TOTAL, JUDICIAL",
        "CERRADO,      ACTIVO",
        "CERRADO,      CASTIGADO",
        "CASTIGADO,    ACTIVO",
        "CASTIGADO,    JUDICIAL",
        "JUDICIAL,     ACTIVO",
        "JUDICIAL,     PROMESA_VIGENTE",
    })
    void transicionesInvalidasLanzan(String desde, String hacia) {
        assertThatThrownBy(() ->
            CicloVidaCasoMachine.validar(
                CicloVidaCaso.valueOf(desde),
                CicloVidaCaso.valueOf(hacia)
            )
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("→");
    }

    // ── Estados terminales no tienen transiciones ─────────────────────────────

    @Test
    void estadosTerminalesTienenConjuntoVacio() {
        assertThat(CicloVidaCasoMachine.transicionesDesde(CicloVidaCaso.PAGADO_TOTAL)).isEmpty();
        assertThat(CicloVidaCasoMachine.transicionesDesde(CicloVidaCaso.CERRADO)).isEmpty();
    }

    @Test
    void activoTieneTodasLasTransicionesPositivas() {
        var transiciones = CicloVidaCasoMachine.transicionesDesde(CicloVidaCaso.ACTIVO);
        assertThat(transiciones).contains(
            CicloVidaCaso.PROMESA_VIGENTE,
            CicloVidaCaso.ACUERDO_VIGENTE,
            CicloVidaCaso.PAGADO_TOTAL,
            CicloVidaCaso.JUDICIAL,
            CicloVidaCaso.CASTIGADO,
            CicloVidaCaso.CERRADO
        );
    }
}
