package com.motoyav2.contrato.domain.service;

import com.motoyav2.contrato.domain.enums.EstadoContrato;

import java.util.Map;
import java.util.Set;

public final class ContratoStateMachine {

    private ContratoStateMachine() {
    }

    private static final Map<EstadoContrato, Set<EstadoContrato>> TRANSITIONS = Map.of(
            EstadoContrato.BORRADOR, Set.of(EstadoContrato.PENDIENTE_DOCUMENTOS),
            EstadoContrato.PENDIENTE_DOCUMENTOS, Set.of(EstadoContrato.EN_VALIDACION, EstadoContrato.CANCELADO),
            EstadoContrato.EN_VALIDACION, Set.of(EstadoContrato.GENERANDO_CONTRATO, EstadoContrato.RECHAZADO),
            EstadoContrato.GENERANDO_CONTRATO, Set.of(EstadoContrato.CONTRATO_GENERADO),
            EstadoContrato.CONTRATO_GENERADO, Set.of(EstadoContrato.DESCARGA_CONTRATO, EstadoContrato.FIRMA_PENDIENTE),
            EstadoContrato.DESCARGA_CONTRATO, Set.of(EstadoContrato.FIRMA_PENDIENTE),
            EstadoContrato.FIRMA_PENDIENTE, Set.of(EstadoContrato.FIRMADO, EstadoContrato.CANCELADO),
            EstadoContrato.FIRMADO, Set.of(EstadoContrato.ACTIVO)
    );

    public static boolean canTransition(EstadoContrato from, EstadoContrato to) {
        Set<EstadoContrato> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static void validateTransition(EstadoContrato from, EstadoContrato to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Transición inválida: " + from + " -> " + to
            );
        }
    }
}
