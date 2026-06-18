package com.motoyav2.cobranza.domain.service;

import com.motoyav2.cobranza.domain.enums.EstadoCaso;

import java.util.Map;
import java.util.Set;

/**
 * State machine del estado operativo de un caso de cobranza (estadoCaso).
 * Diferente de CicloVidaCasoMachine que gestiona el ciclo de vida completo.
 *
 * EstadoCaso refleja el estado de gestión diaria del agente:
 * EN_SEGUIMIENTO → estado normal
 * INTERVENCION_REQUERIDA → atención urgente
 * PROMESA_VIGENTE → cliente con promesa activa
 * PROMESA_VENCE_HOY → promesa que vence hoy
 * PROMESA_INCUMPLIDA → cliente no cumplió promesa
 */
public final class EstadoCasoMachine {

    private EstadoCasoMachine() {}

    private static final Map<EstadoCaso, Set<EstadoCaso>> TRANSICIONES = Map.of(
            EstadoCaso.EN_SEGUIMIENTO,       Set.of(EstadoCaso.INTERVENCION_REQUERIDA, EstadoCaso.PROMESA_VIGENTE,
                                                     EstadoCaso.PROMESA_VENCE_HOY, EstadoCaso.PROMESA_INCUMPLIDA),
            EstadoCaso.INTERVENCION_REQUERIDA, Set.of(EstadoCaso.EN_SEGUIMIENTO, EstadoCaso.PROMESA_VIGENTE),
            EstadoCaso.PROMESA_VIGENTE,       Set.of(EstadoCaso.EN_SEGUIMIENTO, EstadoCaso.PROMESA_VENCE_HOY,
                                                      EstadoCaso.PROMESA_INCUMPLIDA),
            EstadoCaso.PROMESA_VENCE_HOY,    Set.of(EstadoCaso.EN_SEGUIMIENTO, EstadoCaso.PROMESA_INCUMPLIDA,
                                                     EstadoCaso.PROMESA_VIGENTE),
            EstadoCaso.PROMESA_INCUMPLIDA,   Set.of(EstadoCaso.EN_SEGUIMIENTO, EstadoCaso.INTERVENCION_REQUERIDA,
                                                     EstadoCaso.PROMESA_VIGENTE)
    );

    /**
     * Valida la transición. Lanza IllegalStateException si no es válida.
     */
    public static void validar(EstadoCaso desde, EstadoCaso hacia) {
        Set<EstadoCaso> permitidos = TRANSICIONES.getOrDefault(desde, Set.of());
        if (!permitidos.contains(hacia)) {
            throw new IllegalStateException(
                    "Transición de estadoCaso inválida: " + desde + " → " + hacia +
                    ". Permitidas desde " + desde + ": " + permitidos
            );
        }
    }

    public static boolean esTransicionValida(EstadoCaso desde, EstadoCaso hacia) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hacia);
    }

    public static Set<EstadoCaso> transicionesDesde(EstadoCaso estado) {
        return TRANSICIONES.getOrDefault(estado, Set.of());
    }
}
