package com.motoyav2.cobranza.domain.service;

import com.motoyav2.cobranza.domain.enums.CicloVidaCaso;

import java.util.Map;
import java.util.Set;

/**
 * State machine del ciclo de vida de un caso de cobranza.
 * Rechaza transiciones inválidas; las válidas fluyen sin cambios en la lógica existente.
 *
 * Sólo agrega una capa de validación — no modifica ningún servicio existente
 * hasta que cada servicio adopte explícitamente la validación.
 */
public final class CicloVidaCasoMachine {

    private CicloVidaCasoMachine() {}

    private static final Map<CicloVidaCaso, Set<CicloVidaCaso>> TRANSICIONES = Map.of(
            CicloVidaCaso.ACTIVO,          Set.of(CicloVidaCaso.PROMESA_VIGENTE, CicloVidaCaso.ACUERDO_VIGENTE,
                                                    CicloVidaCaso.PAGADO_TOTAL, CicloVidaCaso.JUDICIAL,
                                                    CicloVidaCaso.CASTIGADO, CicloVidaCaso.CERRADO),
            CicloVidaCaso.PROMESA_VIGENTE,  Set.of(CicloVidaCaso.ACTIVO, CicloVidaCaso.PAGADO_TOTAL,
                                                    CicloVidaCaso.JUDICIAL, CicloVidaCaso.CERRADO),
            CicloVidaCaso.ACUERDO_VIGENTE,  Set.of(CicloVidaCaso.ACTIVO, CicloVidaCaso.PAGADO_TOTAL,
                                                    CicloVidaCaso.JUDICIAL, CicloVidaCaso.CERRADO),
            CicloVidaCaso.PAGADO_TOTAL,     Set.of(),
            CicloVidaCaso.JUDICIAL,         Set.of(CicloVidaCaso.CASTIGADO, CicloVidaCaso.CERRADO),
            CicloVidaCaso.CASTIGADO,        Set.of(CicloVidaCaso.CERRADO),
            CicloVidaCaso.CERRADO,          Set.of()
    );

    /**
     * Valida la transición. Lanza IllegalStateException si no es válida.
     * Llamar antes de persistir cualquier cambio de cicloVida.
     */
    public static void validar(CicloVidaCaso desde, CicloVidaCaso hacia) {
        Set<CicloVidaCaso> permitidos = TRANSICIONES.getOrDefault(desde, Set.of());
        if (!permitidos.contains(hacia)) {
            throw new IllegalStateException(
                    "Transición de cicloVida inválida: " + desde + " → " + hacia +
                    ". Permitidas desde " + desde + ": " + permitidos
            );
        }
    }

    public static boolean esTransicionValida(CicloVidaCaso desde, CicloVidaCaso hacia) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hacia);
    }

    public static Set<CicloVidaCaso> transicionesDesde(CicloVidaCaso estado) {
        return TRANSICIONES.getOrDefault(estado, Set.of());
    }
}
