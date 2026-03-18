package com.motoyav2.evaluacion.domain.exception;

public class ExpedienteNotFoundException extends DomainException {
    public ExpedienteNotFoundException(String solicitudId) {
        super("Expediente no encontrado para solicitudId: " + solicitudId);
    }
}
